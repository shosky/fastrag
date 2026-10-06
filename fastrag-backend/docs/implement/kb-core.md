# 知识库管理（kb-core）— 实现文档

> 记录 HEAD d8f30cd 既有代码如何实现契约（contract/interfaces.md `## kb-core`）。伪代码级，不贴整段源码。
> 证据标注：`path:line ‹CERTAIN›` / 〔INFERRED：…〕/ `[GAP: …]`。

## 1. Code File Map（镜像 design Module Map）

```
fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/
├── controller/
│   ├── KbController.java            /api/kb 六端点；角色注解与审计埋点（浅壳，逻辑全在 Service）
│   ├── KbCategoryController.java    /api/kb-categories 分类 CRUD+计数；直连 mapper（浅模块）
│   └── KbTagController.java         /api/kb-tags 标签本体 CRUD；手动 editor 判定
├── service/
│   ├── KbService.java               生命周期接口（6 方法签名一行）
│   ├── TagRelationHelper.java       标签关联一致性维护器（深逻辑）
│   └── impl/
│       ├── KbServiceImpl.java       生命周期全部实现（装配/可见性/级联）
│       ├── KbAccessCheckerImpl.java iam 判定缝的实现宿主（判定=ACL∪组织兜底）
│       └── KbOperationServiceImpl.java  common 接口透传适配（浅，含检索 stub）
├── entity/
│   ├── KnowledgeBase.java  KbCategory.java  KbTag.java
│   ├── KbTagRelation.java  KbListener.java  KbListenerLog.java（孤儿）
├── mapper/
│   ├── KnowledgeBaseMapper.java  KbCategoryMapper.java  KbTagMapper.java
│   ├── KbTagRelationMapper.java  KbListenerMapper.java  KbListenerLogMapper.java（孤儿）
└── model/
    ├── KbCreateRequest.java（创建请求，仅 name @NotBlank）
    ├── KbDto.java（出参 21 字段）
    └── KbTagRequest.java（标签请求，@NotBlank 未被触发，见偏差 6）
```

## 2. Interface Implementations

### KbLifecycleService（from contract: kb-core.KbLifecycleService）

**Logic Flow:**

`list(kw, cat, page, pageSize)`〔KbServiceImpl.java:61-96 ‹CERTAIN›〕
1. 取会话 LoginUser；IF userId 前缀 `api-token:` THEN admin=true（全局可见）。
2. IF 非 admin THEN accessible=getAccessibleKbIds(userId, orgId)；IF accessible 为空 THEN 直接 RETURN {list:[],total:0,page,pageSize}（空集短路，防 SQL IN ()）；ELSE wrapper.in(id, accessible)。
3. IF kw 或 cat 非空 THEN wrapper.and(q→{kw: name like q OR description like q；cat: category eq cat})（同空不拼条件）。
4. orderByDesc(createdAt) → selectPage → RETURN {list: records.map(toDto), total, page, pageSize}。

`get(id)`〔:98-103〕1. selectById；2. IF null THEN throw RuntimeException("KB not found")（→兜底 500，偏差 2）；3. RETURN toDto。

`create(req, creator)`〔:105-145，@Transactional〕
1. new KnowledgeBase，逐字段装配：tags 数组→JSON 串；permission 缺省 "private"；graphAutoBuild→(req true?1:0)（null 也 0，偏差 3）；fileTypeConfig/retrievalConfig/customAttrSchema 对象→JSON 串。
2. orgId=会话 orgId（会话 null 则 null）；usedSize/totalSize=0。
3. type = "private".equals(permission) ? "personal" : "team"（一步派生）。
4. insert → aclService.addAclEntry(kbId, creator, owner, creator)（owner 引导，跨模块调 iam 实现写 kb_acl + 缓存）。
5. IF tags 非空 THEN syncTagRelations(kbId, tags, creator)（见下）→ 回写 tags JSON → updateById。
6. RETURN toDto。

`update(id, req)`〔:147-175，@Transactional〕1. selectById，null 则 RuntimeException。2. 对 13 个字段逐个 IF req.X != null THEN e.setX(...)（JSON 字段先序列化）；**type 不重算**。3. updateById。4. IF req.tags != null THEN syncTagRelations(id, tags, null)。5. RETURN toDto。

`delete(id)`〔:177-240，@Transactional〕
1. files=fileMapper.selectList(kbId=id)（含软删）；collection="kb_"+id。
2. FOR EACH file：① chunkMapper.delete(kbId+fileId)；② TRY milvusService.deleteByFileId(collection, fileId) CATCH → warn 继续；③ TRY graphStore.deleteFileGraph(kbId, fileId) CATCH → warn；④ TRY {objectKey 非空则 minioService.delete；minioService.deleteByPrefix(id+"/"+fileId)} CATCH → warn；⑤ TRY qaPairMapper.delete(kbId+fileId) CATCH → warn。
3. 标签回收：kbRels=relation(targetType=kb,targetId=id)；fileRels=relation(targetType=file,targetId∈fileIds)；FOR EACH → tagRelationHelper.removeTag（内部校正式计数递减）。
4. fileMapper.delete(kbId=id)（物理删，含软删行）。
5. mapper.deleteById(id)；log.info 汇总。

`getCategories()`〔:242-268〕1. admin（API Token）→ selectList(null)；否则 accessible 空则空列表。2. filter(category 非空).groupBy(category).count()。3. RETURN [{id:k, name:k, count:v}]（id=name=k，偏差 4）。

`syncTagRelations(kbId, tagNames, creator)`〔:273-321〕1. relation(targetType=kb,targetId=kbId) **先删除**。2. 再查旧关联用于减计数——**因第 1 步已删，查询恒为空，减计数为死代码**（:291 注释自认「已在上一步删除，重新查为空列表」，偏差 1）。3. FOR EACH name：按 name 查 kb_tag（**全局名匹配**），无则建（kbId=kbId, usage=0）；插关联(targetType=kb)；usage_count+1 updateById。

**Module Interactions:**
- Calls：KbAclService.addAclEntry/getAccessibleKbIds（iam 实现，写 kb_acl 与 Redis 缓存）；KbAccessChecker.getAccessibleKbIds（本模块 KbAccessCheckerImpl）；MilvusService/GraphStore/MinioService（infra，级联 ②③④）；TagRelationHelper.removeTag（删除路径）；LogService（publish 模块，KbController.java:48 创建审计）。‹CERTAIN（2026-09-24 深证，原标注〔INFERRED：同库表共享〕修订）›：kb 行的跨模块**读面消费方实测**为 application（AppServiceImpl）、operation（AnalyticsController/HomeController）、retrieval（RetrievalServiceImpl）——均注入 KnowledgeBaseMapper；publish 侧 PublishManageController/ServiceImpl **不读 kb 表**（仅弱引用 kb_id 写 kb_publish_history/kb_publish_plan，PublishManageServiceImpl.java:1-40 实读无 KnowledgeBaseMapper），原「publish 侧消费 kb 行（读面）」断言不成立，以本条为准。
- Called by：KbController；KbOperationServiceImpl（透传）；无队列/缓存主题直连（Redis 仅经 iam ACL 间接）。
- Shared state：表 kb/kb_file/kb_chunk/kb_qa_pair/kb_tag/kb_tag_relation/kb_acl；Redis 键 `kb:acl:*`（经 iam）；Milvus 集合 `kb_{kbId}`；MinIO 前缀 `{kbId}/{fileId}`。

**Decisions & Deviations:**
1. **偏差（缺陷）**：syncTagRelations 先删后查致减计数死代码 → KB 更新打标每轮 usage_count 虚增（KbServiceImpl.java:275-291 ‹CERTAIN›）。契约不变式（usage==关联数）在此路径被破坏；TagRelationHelper.replaceTags 是现成正确实现，修复=删除自建路径改走 Helper。
2. **偏差**：KB not found 用 RuntimeException（:101,151）→ 兜底 500 而非 404；CategoryModule 同场景用 BusinessException.notFound（KbCategoryController.java:139,161）——同特性内两种语义，登记债务。
3. **偏差（ADR-0002 相关）**：graphAutoBuild 请求未指定时写 0（:117），而 DDL 缺省 1 且注释称 ADR-0002 默认开启（schema.sql:99）——运行时行为以代码为准（见 design §6 仲裁）。
4. **偏差**：getCategories 返回 {id:name=k, name:k}，以 category 原值（可能是分类 id 也可能是种子名称串）充当 id+name，与 CategoryModule 的「kb.category==分类 id」口径漂移（:260-265 vs KbCategoryController.java:80）。
5. **取舍**：级联清理四类外部存储全部 best-effort（try/warn）而非失败回滚——保持删除可用性优先，代价是可能残留孤儿向量/对象。‹CERTAIN（2026-09-24 深证）›：四处 try/catch warn 为直接观测（KbServiceImpl.java:190-217）；与全仓主流形态一致的判断有文档实证——conventions.md §3 明文「全仓 435 处 catch 的主流形态：外部依赖调用优雅降级——失败不阻断主流程」。
6. **偏差**：KbTagRequest 的 @NotBlank 注解存在但控制器未标 @Valid，校验实际未触发；name 空白靠手动检查（KbTagController.java:73-75），kbId 空白落入 resolveRole(null)→403 的隐式路径〔KbTagController.java:70-71 无 @Valid 实读 ‹CERTAIN›〕。
7. **取舍**：列表可见性在 Service 层过滤而非 SQL 子查询——先取 id 集再 IN，空集短路；数据量大时全量 ACL/组织查询有 N 险。‹CERTAIN（2026-09-24 深证）›：结构观察为代码实读（KbServiceImpl.java:67-76：getAccessibleKbIds 全量取集 → IN → 空集短路）；「规模未证」维持原状——全仓无任何针对该路径的压测/基准，风险陈述本身即现状事实。

**Parent Trace:** KbServiceImpl → kb-core.KbLifecycleService → KnowledgeBaseModule → REQ-A01~A09、B01~B05。

### TagConsistencyService（from contract: kb-core.TagConsistencyService）

**Logic Flow:**

`replaceTags(targetType, targetId, tagIds)`〔TagRelationHelper.java:54-98〕
1. want=tagIds 去重去 null。2. oldRels=当前关联（**先查后删**，与偏差 1 相反的正确顺序）。3. toAdd=want−oldIds；toRemove=oldIds−want。4. FOR EACH toAdd：selectById 为 null → badRequest("标签不存在")。5. FOR EACH toRemove：删该关联行；decrementUsage（0 下保护）。6. FOR EACH toAdd：插关联，TRY CATCH 唯一键冲突 → debug 跳过（不加计数）；成功则 incrementUsage。

`addTag`〔:101-116〕1. 标签不存在→badRequest。2. selectCount 关联>0 → RETURN（幂等）。3. 插关联+incrementUsage。

`removeTag`〔:119-127〕1. delete 关联行；2. IF deleted>0 THEN decrementUsage（不误减）。

`deleteTag(tagId)`〔:130-145〕1. 标签 null→notFound。2. relationCount>0 → badRequest("仍被 N 个目标引用")。3. usageCount>0 → badRequest("usage_count 异常非零")。4. 删残余关联+删标签本体。

`listTags`〔:40-51〕1. 查关联。2. 空则空表。3. batchIds 查标签，按关联 createdAt 升序排（null 记 0）。

`KbTagController` 本体面〔KbTagController.java:70-127〕：create：requireKbEditor(req.kbId) → name 空白校验 → 全局名查重（不带 kbId 条件）→ blankToNull 归一 → usageCount=0、createdBy=用户名 → insert。update：查原标签（notFound）→ requireKbEditor(tag.kbId) → name 提供则全局查重（ne id）→ 逐字段覆盖 → updateById。delete：查原→requireKbEditor→tagRelationHelper.deleteTag。requireKbEditor〔:131-140〕：user null→403；`api-token:` 前缀→放行；resolveRole(kbId,user) 为 null 或 ordinal()>editor → 403。

**Module Interactions:**
- Calls：仅两 mapper（KbTagMapper/KbTagRelationMapper）；无外部模块。
- Called by：KbTagController、KbServiceImpl（removeTag，删除路径）、FileMetadataServiceImpl（listTags/replaceTags/addTag/removeTag，文件打标，FileMetadataServiceImpl.java:109,183,202-203）。
- Shared state：表 kb_tag/kb_tag_relation；无缓存/队列。

**Decisions & Deviations:**
1. 无偏离：Helper 是契约不变式的忠实实现（先查后删、差量、幂等、双门槛全部到位）；实现注释明确「方法不开事务，由调用方 @Transactional 控制」〔TagRelationHelper.java:25 ‹CERTAIN›〕——KbTagController 写路径**未开事务**（控制器直调无 @Transactional），单条操作原子性靠单 SQL 保证，多步 replaceTags 若经此路径无事务包裹。‹CERTAIN（2026-09-24 深证）›：KbTagController.java 全文无 @Transactional（grep 实测 0 处）；replaceTags 当前唯一调用方为 FileMetadataServiceImpl.java:183（文件打标），控制器确不经此路径——「风险潜伏」表述属实。
2. 取舍：usage_count 用「读-改-写」而非 SQL 原子自增——并发下可能丢计数（uk 冲突容忍部分缓解）。‹CERTAIN（2026-09-24 深证）›：incrementUsage/decrementUsage 为实体读出 → setUsageCount(n±1) → updateById 的读-改-写三步（TagRelationHelper.java:153-160），无 SQL 原子自增；结构观察成立。
3. 债务承接：契约登记的 E_TAG_NOT_FOUND 双语义码（badRequest vs notFound）在实现中真实存在（Helper:70 vs Controller:100）。

**Parent Trace:** TagRelationHelper + KbTagController → kb-core.TagConsistencyService → TagModule → REQ-D01~D07。

### CategoryModule / KbOperationAdapter（浅模块，无契约——实现事实记录）

- **CategoryModule**：KbCategoryController 直连双 mapper 实现组织过滤列表（count=遍历本组织 kb 统计 category 值）、usage（同口径 selectCount，分类不存在返回 0）、create（非 API Token 强制 orgId=会话组织、sort 缺省 0）、update（跨组织 403+归属不可迁移）、delete（跨组织 403，**不级联清理 kb.category 引用值**）；三写端点 @PreAuthorize kb:category:*；审计 addLog("global", config_changed) try/ignore〔KbCategoryController.java:58-173 ‹CERTAIN›〕。get(id) 无组织校验（spec REQ-C06，2026-09-24 已裁：按现状为准）。
- **KbOperationAdapter**：listKnowledgeBases=kbService.list(null,null,1,100) 折叠为 4 字段 map 列表，异常吞掉返回空表；queryKnowledgeBase=TODO stub 返回占位假数据（KbOperationServiceImpl.java:22-62）。

## 3. Testing

引用 tdd/kb-core.md 的 S1~S6 切片：

- **现有覆盖**：零。fastrag-knowledge 现有 22 个测试类全部位于 parser/chunking/consumer/config/util（实读 test 树），KbServiceImpl、KbTagController、KbCategoryController、TagRelationHelper、KbOperationServiceImpl 无任何直接或间接测试〔‹CERTAIN›〕。
- **缺口**（按切片）：S1 删除级联全清/降级（最高风险，零覆盖）；S2 创建装配（owner ACL 引导、type 派生）；S3 可见性三态；S4 计数不变式（含 ⚠锁定用例——现状应红）；S5 全局唯一名与手动判定；S6 组织隔离。全部待补，执行顺序与 Tracer Bullet 见 tdd §4。
