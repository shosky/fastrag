# 知识库管理（kb-core）— 架构设计

> 依据：spec/kb-core.md（REQ-A01~D07）+ HEAD d8f30cd 代码实读。
> 证据标注：`path:line ‹CERTAIN›` / 〔INFERRED：…〕/ `[GAP: …]`。
> ADR 对接：ADR-0001（解析策略绑定变更必须触发重新分片）——本特性的 parse_mode/split_mode 仅作库级字段存储，不触碰绑定/重切语义，无冲突〔KbServiceImpl.java:115-116,157-158 ‹CERTAIN›〕。ADR-0002（图存储收敛 Neo4j，唯一实现，启动 fail-fast）——删除级联经 GraphStore 抽象清理图谱，符合唯一实现收敛〔KbServiceImpl.java:196-200 ‹CERTAIN›〕；一处漂移见 §6 前的仲裁说明（graph_auto_build 缺省值）。

## 1. Module Map

| 模块 | Responsibility（一行） | Files | Depth Rationale |
|---|---|---|---|
| **KnowledgeBaseModule（知识库生命周期模块）** | 知识库聚合根的创建装配（org 绑定/owner 授权/类型派生/缺省填充）、部分更新、可见性过滤分页列表、分类聚合，以及跨四类存储的删除级联 | fastrag-knowledge/…/controller/KbController.java、service/KbService.java、service/impl/KbServiceImpl.java、entity/KnowledgeBase.java、mapper/KnowledgeBaseMapper.java、model/KbCreateRequest.java、model/KbDto.java；级联消费：mapper/KbFileMapper、KbChunkMapper、KbQaPairMapper | **深**：一次 `delete()` 调用掩盖了「逐文件 ×（分片 DB 删/Milvus 删/图谱删/MinIO 双删/QA 删）+ 标签关联回收 + 计数校正 + 文件含软删清除」七级清理编排（KbServiceImpl.java:179-240）；一次 `create()` 吞掉 org 绑定、owner ACL 引导、type 派生、三组 JSON 序列化（:105-145）；调用方只见 KbDto |
| **TagModule（标签一致性模块）** | 标签 CRUD 与 kb/file 两类目标打标关联的唯一定点维护，保证 usage_count 与关联数强一致 | fastrag-knowledge/…/controller/KbTagController.java、service/TagRelationHelper.java、entity/KbTag.java、KbTagRelation.java、mapper/KbTagMapper.java、KbTagRelationMapper.java、model/KbTagRequest.java | **深**：「替换=差量、增删幂等、唯一键冲突容忍、零引用才可删、计数双校验」五条一致性纪律被 6 个方法吃完（TagRelationHelper.java:40-151）；KB 打标（KbServiceImpl）与文件打标（FileMetadataServiceImpl）两个场景零感知复用 |
| **CategoryModule（分类管理模块）(S)** | 组织隔离的分类 CRUD 与分类下知识库计数 | fastrag-knowledge/…/controller/KbCategoryController.java、entity/KbCategory.java、mapper/KbCategoryMapper.java | **浅**：控制器直连 mapper（无 Service 层，KbCategoryController.java:50-52），规则仅「组织过滤+计数」，CRUD 味重。**建议**：不内联删除——组织隔离与 count 口径是 KbCategoryController.java:58-110 的真规则，但其体量（单类 175 行）不足以立 Service；维持现状，深化方向见 §4 |
| **KbOperationAdapter（智能体工具适配）(S)** | 把 KbService 折叠成 common 接口供 tools 模块的智能体知识库工具消费 | fastrag-knowledge/…/service/impl/KbOperationServiceImpl.java；接口声明 fastrag-common/…/service/KbOperationService.java；消费方 fastrag-tools/…/executor/KnowledgeToolExecutor.java:31 | **浅**：listKnowledgeBases 是 KbService.list 的透传折叠（KbOperationServiceImpl.java:22-42）；queryKnowledgeBase 是 TODO 占位 stub（:45-62）。**建议**：维持浅透传——它存在的唯一理由是 common 接口让 tools 不依赖 knowledge 模块，深化应发生在被透传的 KbService 上 |

## 2. Interface Contracts

> 深契约（KbLifecycleService、TagConsistencyService）落在 contract/interfaces.md 的 `## kb-core` 节；此处列各模块公共接口的方法面。CategoryModule/KbOperationAdapter 为浅模块——按 conventions.md「(S) 模块口径」以表面注记单行列出（不立 Public/Hidden 完整契约节）。

**KnowledgeBaseModule — `KbService`**〔证据：KbService.java:3 ‹CERTAIN›〕
- `list(keyword, category, page, pageSize): Map{list,total,page,pageSize}` — 可见性过滤分页
- `get(id): KbDto` — 详情（不存在抛 RuntimeException）
- `create(KbCreateRequest, creator): KbDto` — 创建装配（org/owner/type/缺省）
- `update(id, KbCreateRequest): KbDto` — 部分语义更新 + 标签同步
- `delete(id): void` — 七级级联清理
- `getCategories(): List{id,name,count}` — 可访问库的分类聚合
- Hidden：级联的存储编排顺序与降级策略、syncTagRelations 内联同步、toDto 的 JSON 容错反序列化（KbServiceImpl.java:343-345）

**TagModule — `TagRelationHelper` + `KbTagController` 的写面**〔证据：TagRelationHelper.java:40-151、KbTagController.java:49-127 ‹CERTAIN›〕
- `listTags(targetType, targetId): List<KbTag>` — 目标的标签（按关联创建时间排序）
- `replaceTags(targetType, targetId, tagIds)` — 差量替换（校验存在性/唯一键容忍/计数差量）
- `addTag / removeTag(targetType, targetId, tagId)` — 幂等增删
- `deleteTag(tagId)` — 零引用门槛删除
- `countTagReferences(tagId): long` — 跨 targetType 引用计数
- `KbTagController.create/update/delete` — 标签本体 CRUD（归属 KB editor+ 手动判定，KbTagController.java:131-140）
- Hidden：usage_count 增减全部私有（incrementUsage/decrementUsage，TagRelationHelper.java:153-167），调用方无法绕过计数纪律

- **(S) CategoryModule 表面注记** — `KbCategoryController`（无 Service，控制器即接口面）：`list/get(id)/usage/create/update/delete`，组织隔离 + 权限键 kb:category:*；count 的组织内统计口径在 :70-91,99-110。不立契约节（理由：CRUD 味重且浅，§1 (S) 标注）。〔证据：KbCategoryController.java:58-173 ‹CERTAIN›〕
- **(S) KbOperationAdapter 表面注记** — `KbOperationService`（common 声明 / knowledge 实现）：`listKnowledgeBases(userId)` 折叠透传、异常吞掉返回空表；`queryKnowledgeBase(kbId, query, topK)` 为 TODO stub 返回占位假数据（:49-57）。不立契约节（理由：透传 + stub，§1 (S) 标注）。〔证据：KbOperationService.java:10、KbOperationServiceImpl.java:16-62 ‹CERTAIN›〕

## 3. Seam Diagram

| 缝 | What varies | Adapters | Stable side | Placement rationale |
|---|---|---|---|---|
| **KB 判定缝（上游 iam 已立，本特性消费+实现宿主）**：`KbAccessChecker`（security 声明） | 「用户在某库的有效角色」的数据来源（现 ACL+组织兜底） | 1：`KbAccessCheckerImpl`（本模块，查 kb 表组织归属，KbAccessCheckerImpl.java:24-68）——iam 链已论证其为假设缝，接口被切面与本模块 list/标签写判定多方消费 | `KbAuthAspect` 的层级比较协议（owner3>editor2>viewer1） | 判定需查 kb 表（本模块领域数据）而切面在 security，不立缝则 security→knowledge 反向依赖（iam design §3 结论，此处不重复展开） |
| **删除级联存储缝** | 各外部存储的清除方式（向量/图/对象） | 3：`MilvusService.deleteByFileId`、`GraphStore.deleteFileGraph`、`MinioService.delete/deleteByPrefix`（KbServiceImpl.java:191,197,203-206）——3 个适配器，真缝 | KnowledgeBaseModule 的清理编排（顺序、逐文件粒度、降级 warn） | 三种存储实现独立演化（infra 层），kb-core 只认「给 file 清掉」语义；DB 类清理不走此缝（直接 mapper） |
| **标签目标缝**：`targetType` 判别式（`kb`/`file` 字符串常量，TagRelationHelper.java:36-37） | 被打标的目标种类 | 2：KB 打标（KbServiceImpl.java:229-232,273-321）、文件打标（FileMetadataServiceImpl.java:109,183,202-203）——2 个消费方，真缝（但为字符串判别式弱缝） | TagRelationHelper 的关联+计数一致纪律 | 新增打标目标（如 QA 对）只需新 targetType 常量，一致性纪律自动继承 |
| **智能体工具缝**：`KbOperationService`（common 声明） | 智能体可调用的知识库能力面 | 1：`KbOperationServiceImpl`——单适配器属假设缝，但 common 接口让 tools/agent 免依赖 knowledge 模块（KnowledgeToolExecutor.java:31 消费） | tools 侧 KnowledgeToolExecutor 的调用协议 | 模块拓扑解耦：knowledge 不出现在 tools 的依赖里 |

## 4. Deepening Opportunities

- **KnowledgeBaseModule**：①删除级联七步散在一个 60 行方法里，可深化为 `KbPurgePlan`（清理计划对象：各存储清理器列表 + 降级策略），让「新增一种衍生数据」（如未来 kb_listener 触发记录）只注册一个清理器；②存储配额（used_size/total_size）目前无写入方，若激活应整体吞进本模块而非散在 file-ingestion；③`syncTagRelations`（KbServiceImpl.java:273-321）与 TagRelationHelper.replaceTags 是同职责双轨，应删除自建路径统一走 Helper（顺带修复计数漂移缺陷，见 implement §2）。
- **TagModule**：`replaceTags` 每个待删/待增标签各发一条 SQL，可批量化；`syncTagRelations` 按名称查找/建标签的语义（KbServiceImpl.java:299-308）可下沉为 Helper 的 `resolveOrCreateByName`，消除名称→ID 解析散落。
- **CategoryModule**：①单查 get(id) 无组织校验（KbCategoryController.java:95-97）应补齐与 list 一致的过滤；②count 双口径漂移（getCategories 以 category 原值作 id+name 返回 KbServiceImpl.java:260-265，而 CategoryModule 按 kb.category==分类 id 统计）应收敛为单一「分类 id」口径；③`/{id}/usage` 与 list 的 count 逻辑重复，可提私有方法。
- **KbOperationAdapter**：queryKnowledgeBase stub 接入 RetrievalService 后，本模块从「透传」升为「检索能力折叠」——届时再评估深度。

## 5. Parent Trace

| Design 单元 | Spec REQ |
|---|---|
| KnowledgeBaseModule（创建装配） | REQ-A01~A04 |
| KnowledgeBaseModule（更新/角色门槛/审计） | REQ-A05、A08、A09 |
| KnowledgeBaseModule（删除级联+降级） | REQ-A06、A07 |
| KnowledgeBaseModule（列表/筛选/分类聚合） | REQ-B01~B05 |
| TagModule（本体 CRUD+判定） | REQ-D01、D02、D07 |
| TagModule（一致性纪律） | REQ-D03、D04、D05、D06 |
| CategoryModule（组织隔离/权限键/计数） | REQ-C01~C06 |
| KbOperationAdapter | （无直接 REQ；服务智能体工具场景，spec §2 Out of Scope 相邻项）〔决议 2026-09-24：按现状为准——浅透传 + TODO stub（KbOperationServiceImpl.java:22-62 实读），属能力外溢面，维持无独立需求条目；待 queryKnowledgeBase 接入检索后按 design §4 评估是否补立需求（代裁，用户可推翻）〕 |
| 缝：删除级联存储缝 | REQ-A06、A07；标签目标缝 | REQ-D04~D06；KB 判定缝（消费） | REQ-A08、B02、D02 |
| KnowledgeBaseModule（创建装配） | REQ-A02（2026-09-24 review 补锚：创建成功后创建者自动成为 owner（写显式 ACL 记录），不被可见性过滤排除） |
| KnowledgeBaseModule（创建装配） | REQ-A03（2026-09-24 review 补锚：知识库归属创建者当前组织（orgId 取自会话身份，不收请求参数）） |
| KnowledgeBaseModule（更新/角色门槛/审计） | REQ-A09（2026-09-24 review 补锚：更新/删除自动记 @Loggable 审计；创建经显式日志调用且日志失败不影响创建结果） |
| KnowledgeBaseModule（列表/筛选/分类聚合） | REQ-B05（2026-09-24 review 补锚：分类聚合与列表使用同一套可见性口径（API Token 全量/普通用户组织∪ACL）） |
| TagModule（一致性纪律） | REQ-D06（2026-09-24 review 补锚：文件打标复用同一关联维护器（targetType=file），禁止另立计数口径） |
| TagModule（本体 CRUD+判定） | REQ-D07（2026-09-24 review 补锚：标签查询列表按名称升序、可按 kbId 过滤；单标签详情登录即可查） |

## 6. 与上游/ADR 的仲裁说明

- **ADR-0002 漂移登记**：schema.sql:99 的 DDL 默认 `graph_auto_build TINYINT DEFAULT 1`（注释称 ADR-0002 默认开启），但创建路径在请求未指定时显式写 0（KbServiceImpl.java:117），MyBatis-Plus 全字段插入使 DDL 缺省永不生效——**代码生效缺省=关闭，DDL 声明缺省=开启**，两者矛盾。按「以运行时行为为准」仲裁为代码缺省（关闭），登记为待用户确认的偏差；若产品意图确为默认开启（ADR-0002 口径），应改 KbServiceImpl.java:117 的缺省分支。
- **ADR-0001 无涉**：本特性不触碰解析绑定与重切，仅存储 parse_mode/split_mode 展示字段，无冲突。
- **与 iam 链衔接**：可见性/角色判定协议、API Token 放行语义均直接引用 contract/interfaces.md `## iam` 节结论（KbAccessChecker/KbAclService/KbAuthAspect），本设计未新增或修改任何判定规则；唯一新增事实是 KbAccessCheckerImpl 实现宿主在本模块（iam design §3 已登记，此处仅交叉引用）。

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
