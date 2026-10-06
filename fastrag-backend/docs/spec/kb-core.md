# 知识库管理（kb-core）— 需求规格

> 依据：HEAD d8f30cd 代码实读。证据标注：`path:line ‹CERTAIN›` / 〔INFERRED：…〕/ `[GAP: …]`。
> 上游：iam 链（鉴权判定协议 `@KbAuth`/`KbAccessChecker`/`KbAclService` 已在 docs/contract/interfaces.md `## iam` 节定义，本 spec 只消费其结论）。

## 1. 特性与范围

kb-core 是知识域全部特性的基座：定义「知识库」这一聚合根的完整生命周期（创建、详情、更新、删除级联）、面向登录用户的列表可见性与分页检索（关键词/分类筛选、分类聚合）、组织隔离的分类管理，以及标签与关联（kb_tag/kb_tag_relation）的一致性维护。它只管「库本身的元数据与结构」，不管库内内容——文件、解析、分片、图谱、问答、发布均归下游特性。〔范围依据：feature-map.md #2；KbController.java:2-19 ‹CERTAIN›〕

## 2. Out of Scope

- **库内内容管理**：文件上传/解析/分片/回收站（file-ingestion、chunk-management）；本特性只负责删除知识库时把它们清理掉〔KbServiceImpl.java:179-240 ‹CERTAIN›〕。
- **解析策略与绑定**：kb 表上的 parse_mode/split_mode 字段仅存储展示，扩展名自动匹配、文档级覆盖、绑定-重切一致性（ADR-0001）归 parse-strategy 特性〔KbServiceImpl.java:115-116 仅透传存储 ‹CERTAIN›〕。
- **检索**：向量/关键词检索、查询增强归 retrieval 特性。
- **图谱构建**：graph_auto_build 开关与 graph_llm_model 字段仅存储；构建执行归 knowledge-graph 特性〔KnowledgeBase.java:54-55 ‹CERTAIN›〕。
- **权限判定协议本身**：@KbAuth/KbAccessChecker/KbAclService 的定义与缓存归 iam 链；本特性是消费方与 KbAccessCheckerImpl 实现宿主〔KbAccessCheckerImpl.java:24 ‹CERTAIN›〕。
- **工作台聚合页面**：工作台/主页聚合（HomeController 等）归 operation-analytics；本特性只提供 `/api/kb/categories` 分类聚合这一数据面〔feature-map.md #18；KbServiceImpl.java:242-268 ‹CERTAIN›〕。
- **知识库监听器（kb_listener）运行时**：表、实体、mapper 存在但全仓无任何 Service/Controller 消费（全仓 grep 实测，2026-09-24 复核仍成立），无运行时需求可提炼；其激活属未来特性。〔决议 2026-09-24（R4 代裁）：产品意图不可考，取**维持休眠、不入当前版本范围**；理由：零消费方即零运行时风险，激活须按 IDD 以新特性立项（顺带处置种子脚本 `level` 列缺陷，见 data.md kb_listener_log）；现状差距：无（休眠本体即登记）。〕
- **存储配额执行**：used_size/total_size 仅创建时置 0 展示，全仓无写入方（实读 grep），配额校验不构成本特性需求〔KbServiceImpl.java:123-124 ‹CERTAIN›〕。

## 3. 领域术语（引用 CONTEXT.md）

- **知识库（Knowledge Base）**：本特性核心聚合根，表 `kb`，端点前缀 `/api/kb`。
- **知识库权限（KB ACL）**：owner > editor > viewer 三层级 RBAC，判定协议归 iam；本特性消费。
- **发布（Publish）**：kb.permission 的 private/team/public 是库级可见性字段，不是「发布」流程（后者归 publish-review）。
- **操作审计（Audit Log）**：@Loggable 注解 + AOP；本特性写操作审计归入此机制。
- 分类（KbCategory）、标签（KbTag）、标签关联（KbTagRelation）为本特性域内词汇（表名即术语）。

## 4. 功能需求

### 4.1 知识库生命周期（创建/详情/更新/删除级联）

这部分是 kb-core 的深水区：创建要把「归属组织、owner 授权、类型派生、初始配置」一次装配完成；删除则必须把一个知识库在 DB、向量库、图库、对象存储中的全部衍生数据回收干净，否则产生跨存储孤儿。〔叙述依据：KbServiceImpl.java:105-240 ‹CERTAIN›〕

- **REQ-A01**：创建知识库仅需名称必填，其余配置缺省——可见性缺省 private、图谱自动构建缺省关闭、已用/总容量置 0。〔证据：KbCreateRequest.java:17-18；KbServiceImpl.java:113,117,123-124 ‹CERTAIN›〕
- **REQ-A02**：创建成功后创建者自动成为该库 owner（写显式 ACL 记录），保证创建者不被可见性过滤排除。〔证据：KbServiceImpl.java:134 ‹CERTAIN›〕
- **REQ-A03**：知识库归属创建者当前组织（orgId 取自会话身份，不收请求参数）。〔证据：KbServiceImpl.java:122 ‹CERTAIN›〕
- **REQ-A04**：库类型（type）由可见性派生：private→personal，否则→team；仅在创建时派生。〔证据：KbServiceImpl.java:130 ‹CERTAIN›；更新路径不重算 type：KbServiceImpl.java:149-175 ‹CERTAIN›——〔决议 2026-09-24：按现状为准——type 为创建时一次性派生，更新 permission 不重算；创建处注释「类型跟随共享设置」（KbServiceImpl.java:129）与本行为存在张力但更新路径无任何意图注释，不构成遗漏实证；是否改为联动重算属产品决策，维持现状（代裁，用户可推翻）。已登记为轻量债务：本条即登记处，验收清单以「现状口径」措辞承接。〕〕
- **REQ-A05**：更新为部分语义：仅请求中非 null 的字段被覆盖，未提供字段保持不变。〔证据：KbServiceImpl.java:152-166 ‹CERTAIN›〕
- **REQ-A06**：删除知识库必须级联清理全部衍生数据：逐文件清理分片（DB）、向量（Milvus 按文件）、图谱（GraphStore 按文件）、MinIO 原文件与前缀对象、QA 对；清理 KB 级与文件级标签关联并校正 usage_count；删除全部文件记录（含已软删除）与 kb 本体。〔证据：KbServiceImpl.java:179-240 ‹CERTAIN›〕
- **REQ-A07**：级联中外部存储（Milvus/GraphStore/MinIO/QA）清理失败降级为 warn 日志继续，不阻断删除主流程；DB 级联在事务内。〔证据：KbServiceImpl.java:190-217（四处 try/catch warn）、@Transactional :178 ‹CERTAIN›〕
- **REQ-A08**：角色门槛：详情 viewer 及以上、更新 editor 及以上、删除仅 owner。〔证据：KbController.java:40,56,60 + iam 判定协议 ‹CERTAIN›〕
- **REQ-A09**：更新与删除自动记录操作审计（@Loggable）；创建经显式日志调用记录且日志失败不影响创建结果。〔证据：KbController.java:46-53,57,61-62 ‹CERTAIN›〕

### 4.2 列表检索与可见性（分页/筛选/分类聚合）

列表是全部知识域特性的入口面：同一接口要同时服务「普通用户只看到该看的」与「程序化访问看全部」两种语义，可见性判定复用 iam 的组织∪ACL 协议，不另立规则。〔叙述依据：KbServiceImpl.java:56-96 ‹CERTAIN›〕

- **REQ-B01**：列表分页返回 `{list,total,page,pageSize}`，按创建时间倒序。〔证据：KbServiceImpl.java:88-95 ‹CERTAIN›〕
- **REQ-B02**：可见性：平台级 API Token 全局可见；其余登录用户仅见「本组织非 private 库 ∪ ACL 显式授权库」；无可访问库时直接返回空页（不落 SQL）。〔证据：KbServiceImpl.java:56-80；KbAccessCheckerImpl.java:57-68 ‹CERTAIN›；‹CERTAIN（2026-09-24 深证）›KbServiceImpl.java:64 行内注释「超管/kb_admin/API Token 可见全部」为过时残句——同文件 :56-58 javadoc 与 isPlatformAdmin 实现（仅认 `api-token:` 前缀）均为正确口径，行为以代码为准，注释矛盾实锤，已登记注释修正债务〕
- **REQ-B03**：keyword 对名称与描述做模糊匹配，category 精确等值，两条件可同时生效；两者皆空时不拼筛选条件。〔证据：KbServiceImpl.java:81-87 ‹CERTAIN›〕
- **REQ-B04**：分类聚合仅统计当前用户可访问的知识库，按 category 值分组计数，返回 `{id,name,count}`（id 与 name 同值）。〔证据：KbServiceImpl.java:242-268 ‹CERTAIN›〕
- **REQ-B05**：分类聚合与列表使用同一套可见性口径（API Token 全量/普通用户组织∪ACL）。〔证据：KbServiceImpl.java:245-256 ‹CERTAIN›〕

### 4.3 分类管理（组织隔离）

分类是知识库的分组维度，独立成表且严格按组织隔离：不同组织互不可见、互不可改；统计口径（分类下知识库数）也必须组织一致，否则跨组织成员看到不同数字。〔叙述依据：KbCategoryController.java:21-46 ‹CERTAIN›〕

- **REQ-C01**：分类读列表按组织过滤：普通登录用户（含超管/kb_admin）仅见本组织分类，无组织者返回空列表；API Token 全量。〔证据：KbCategoryController.java:58-68 ‹CERTAIN›〕
- **REQ-C02**：创建分类归属强制为操作者组织（API Token 可指定任意组织），orgId 缺失拒绝（400「请选择所属组织」），sort 缺省 0。〔证据：KbCategoryController.java:116-121 ‹CERTAIN›〕
- **REQ-C03**：更新/删除其他组织的分类被拒绝（403）；更新不可迁移组织归属（强制回写原 orgId）。〔证据：KbCategoryController.java:140-143,158-164 ‹CERTAIN›〕
- **REQ-C04**：分类写操作需平台权限键：kb:category:create / kb:category:edit / kb:category:delete。〔证据：KbCategoryController.java:116,136,158 ‹CERTAIN›〕
- **REQ-C05**：分类计数口径：列表 count 与 /{id}/usage 均统计「本组织内 category 指向该分类的知识库数」（API Token 统计全量）；分类不存在时 usage 返回 0 而非报错。〔证据：KbCategoryController.java:70-91,99-110 ‹CERTAIN›〕
- **REQ-C06**：单分类详情按 id 直查〔证据：KbCategoryController.java:95-97 ‹CERTAIN›；无组织校验——〔决议 2026-09-24：按现状为准——get(id) 不做组织校验，任何登录用户可按 id 探测他组织分类名（仅名称/排序元数据，非库数据）；与组织隔离原则的张力属实但无意图证据，维持现状（代裁，用户可推翻）。已登记债务：本条 + implement CategoryModule + 验收清单「现状口径」项。〕〕。

### 4.4 标签与关联一致性

标签服务两处打标场景（知识库打标、文件打标），共用一张关联表和一个计数缓存（usage_count）。一致性是本域核心：关联与计数漂移会让「按标签筛选」「零引用才可删」全部失真，因此所有增删必须收敛到同一维护器。〔叙述依据：TagRelationHelper.java:20-26 ‹CERTAIN›〕

- **REQ-D01**：标签名称全局唯一（DB 唯一键 + 创建/更新前置查重），标签归属其创建时的 KB（kb_tag.kbId），不做跨 KB 共享。〔证据：schema.sql:1311；KbTagController.java:76-80,102-108；KbTagRequest.java:9-11 ‹CERTAIN›〕
- **REQ-D02**：标签写操作（创建/更新/删除）要求操作者对归属 KB 具备 editor 及以上角色；平台级 API Token 放行；因路径不在 /api/kb/ 下，采用与 @KbAuth 切面同策略的手动判定。〔证据：KbTagController.java:129-140 ‹CERTAIN›〕
- **REQ-D03**：标签删除仅允许零引用标签：关联数 >0 拒绝（提示引用数），usage_count 异常非零二次拦截。〔证据：TagRelationHelper.java:130-145 ‹CERTAIN›〕
- **REQ-D04**：usage_count 必须与关联数保持一致：替换为差量更新（新增/移除各算一次）、增量添加/移除幂等（已存在不重复计数、不存在不误减）、并发唯一键冲突忽略不累加。〔证据：TagRelationHelper.java:54-127 ‹CERTAIN›〕
- **REQ-D05**：知识库打标（创建/更新请求携带 tags 名称数组）同步为 KB 级标签关联（targetType=kb），标签不存在则自动创建并归属该 KB。〔证据：KbServiceImpl.java:136-142,168-172,293-320 ‹CERTAIN›；注意该自建同步路径存在计数漂移缺陷，见 implement 登记〕
- **REQ-D06**：文件打标复用同一关联维护器（targetType=file），禁止另立计数口径。〔证据：FileMetadataServiceImpl.java:109,183,202-203 消费 TagRelationHelper ‹CERTAIN›；文件打标业务规则本身归 file-ingestion 特性〕
- **REQ-D07**：标签查询列表按名称升序，可按 kbId 过滤；单标签详情按 id 直查（登录即可，无额外权限）。〔证据：KbTagController.java:49-65 ‹CERTAIN›〕

## 5. 非功能约束（仅代码/配置可证者）

- **统一响应**：全部端点返回 `ApiResponse`，业务错误 = HTTP 200 + body.code；列表分页形状为 `{list,total,page,pageSize}`〔KbController.java:37-62；KbServiceImpl.java:90-95 ‹CERTAIN›〕。
- **事务性**：create/update/delete 标注 @Transactional，DB 级联原子〔KbServiceImpl.java:106,148,178 ‹CERTAIN›〕。
- **降级纪律**：删除级联的外部存储失败只 warn 不回滚；创建审计日志失败只 warn〔KbServiceImpl.java:190-217；KbController.java:50-52 ‹CERTAIN›〕。
- **鉴权一致性**：/api/kb/** 端点角色门槛由 iam 判定协议统一执行；非 /api/kb/ 路径（kb-tags）手动复用同一判定器〔KbTagController.java:132-140 ‹CERTAIN›〕。
- **性能阈值**：〔决议 2026-09-24（R4 代裁）：取保守基线 **列表 P95 ≤ 500ms（万级库、千级 ACL 授权）、级联删除 P95 ≤ 30s（千文件级，瓶颈在 Milvus/MinIO 逐文件外部调用）**；理由：Service 层过滤 + IN 直查的单实例常规量级，删除为外部 IO 主导；现状差距：无压测证据，且列表可见性先取全量 id 集在大授权量下有放大风险（implement 取舍 7），登记为观测义务。〕
- **质量门**：`mvn test -f fastrag-backend/pom.xml`（conventions.md §6）；本特性当前无任何测试（knowledge 模块测试全为 parser/chunking/strategy，实读 test 树）。

## 6. 验收清单

- [ ] 仅名称必填即可创建知识库，未传配置时 permission=private、type=personal、graphAutoBuild=0、容量为 0？（REQ-A01）
- [ ] 创建后 kb_acl 中存在「创建者→owner」记录，kb.org_id=创建者组织？（REQ-A02、REQ-A03）
- [ ] 更新只覆盖非 null 字段，未提及字段与配置 JSON 原样保留？（REQ-A05）
- [ ] 删除知识库后：kb_chunk/kb_file/kb_qa_pair/kb_tag_relation 中无该库残留，Milvus 集合 `kb_{id}` 对应文件向量、Neo4j 文件图谱、MinIO 对象均已清理？（REQ-A06）
- [ ] 删除时 Milvus/Neo4j/MinIO 任意一方不可用，删除仍成功且 DB 数据被清理（warn 日志可查）？（REQ-A07）
- [ ] viewer 不能更新、editor 不能删除（403「知识库权限不足」语义）？（REQ-A08）
- [ ] 普通用户列表只含本组织非 private 库与其 ACL 授权库；API Token 返回全部？（REQ-B01、REQ-B02）
- [ ] keyword 命中名称或描述、category 精确匹配、两条件叠加生效？（REQ-B03）
- [ ] /api/kb/categories 与分类列表页 count 在同组织两个账号下数字一致？（REQ-B04、REQ-B05）
- [ ] B 组织用户创建/更新/删除 A 组织分类均被 403；更新无法改变分类归属组织？（REQ-C03）
- [ ] 重复标签名创建/更新被 400「标签名称已存在」（跨 KB 也拒绝）？（REQ-D01）
- [ ] 仍被引用的标签删除被 400 且提示引用数；零引用标签删除成功且关联与计数无残留？（REQ-D03）
- [ ] 知识库打标若干次后 kb_tag.usage_count 等于该标签关联总数（当前实现此处存在已知漂移，见 implement §2 偏差登记——修复后此断言应通过）？（REQ-D04、REQ-D05）
- [ ] 更新可见性后 type 保持创建时派生值不重算（现状口径）？（REQ-A04）
- [ ] 更新与删除自动记 @Loggable 审计；创建显式日志失败不影响创建结果？（REQ-A09）
- [ ] 分类列表按组织过滤：普通登录用户仅见本组织、无组织者返回空列表、API Token 全量？（REQ-C01）
- [ ] 创建分类归属强制为操作者组织，orgId 缺失被 400「请选择所属组织」拒绝、sort 缺省 0？（REQ-C02）
- [ ] 分类写操作要求 kb:category:create/edit/delete 对应权限键？（REQ-C04）
- [ ] 分类列表 count 与 /{id}/usage 同口径（本组织内指向该分类的库数）；分类不存在时 usage 返回 0 而非报错？（REQ-C05）
- [ ] 单分类详情按 id 直查、无组织校验（现状口径）？（REQ-C06）
- [ ] 标签写操作要求归属 KB 的 editor 及以上角色，平台级 API Token 放行？（REQ-D02）
- [ ] 文件打标与知识库打标共用同一关联维护器（usage_count 单一口径）？（REQ-D06）
- [ ] 标签列表按名称升序、可按 kbId 过滤；单标签详情登录即可查？（REQ-D07）

## 7. 测试策略

经由两个契约接口（kb-core.KbLifecycleService、kb-core.TagConsistencyService，见 contract/interfaces.md `## kb-core`）测试：

1. **生命周期域（最重要）**：删除级联是全特性最高风险行为——逐存储验证清理（Milvus/GraphStore/MinIO 在系统边界 mock，DB 用真实内存库），外加「外部清理失败不阻断」的降级断言；创建装配（owner ACL、org 绑定、type 派生）次之。
2. **可见性域**：列表过滤三态（API Token 全局 / 组织∪ACL / 空集短路）+ 筛选组合，判定器在 iam 缝上 mock（合法系统边界）。
3. **标签一致性域**：usage_count 不变式（任意操作序列后 count==关联数）、幂等、零引用删除门槛、全局唯一名。
4. **分类域**：组织隔离四操作 + 计数口径一致性；次优先级（浅模块，主要测权限与过滤规则）。
