# 发布、版本与知识审核（publish-review）— 架构设计

> 总评：本特性是**跨 3 模块的「一横多纵」结构**——纵向是三段各自独立的发布口径（版本状态机 / 知识条目发布历史 / 发布计划），横向是唯一的深模块 LogAuditModule（全后端审计写入口）。VersionModule 与 ReviewModule 是 MyBatis-Plus 透传的浅模块（无状态机校验、无鉴权注解），PublishManageModule 是带鉴权与审计的第二套发布口径。两套发布口径互不感知是最大的结构性事实（见缝 4）。

## 1. Module Map

### LogAuditModule — 唯一非 (S) 模块（深：接口小、实现吞掉全后端日志复杂度）
- **Responsibility**：全后端操作日志/更新日志的唯一写入口与查询面——@Loggable AOP 切面（SpEL 解析、kbId/操作人提取、模块名映射、成功/失败双路径、审计事件发布）+ 分页查询/分类统计/更新日志新旧值，写入失败降级不阻断宿主。
- **Files**：`fastrag-publish/.../aspect/KbLogAspect.java`、`controller/LogController.java`、`service/LogService.java`、`service/impl/LogServiceImpl.java`、`entity/KbLog.java`、`entity/KbUpdateLog.java`、`mapper/KbLogMapper.java`、`mapper/KbUpdateLogMapper.java`
- **Depth Rationale**：`addLog(kbId, category, action, target, detail, operator, status, extra)` 一个签名背后是：Loggable 元数据解析（KbLogAspect.java:91-207）、字符串/枚举双参兼容层（LogServiceImpl.java:88-103）、异常吞掉降级（:119-121）、枚举容错解析（:152-171）。消费方横跨 5 个 Maven 模块（见缝 3），实现可整体替换（如换审计中间件）而签名不动——深。 ‹CERTAIN›

### VersionModule — **(S)**（版本 CRUD + 五态流转）
- **Responsibility**：知识库版本的创建（自动版本号）、三形态查询与 publishStatus 流转（动作名→状态名映射在控制器完成），published 时附带写一条发布历史。
- **Files**：`fastrag-publish/.../controller/PublishController.java`（versions 族 5 端点）、`service/PublishService.java`、`service/impl/PublishServiceImpl.java`、`entity/KbVersion.java`、`mapper/KbVersionMapper.java`
- **Depth Rationale**：listVersions/getLatestVersion/getPublishedVersion 均为单查询透传，createVersion 是「查最大号 +1 + insert」两步（PublishServiceImpl.java:37-43），transitionStatus 无校验直写（:44-46）——浅。建议保持现状但补鉴权（债务，见 Parent Trace REQ-A06/A06 注）。 ‹CERTAIN›

### ReviewModule — **(S)**（审核任务三态）
- **Responsibility**：审核任务的提交（pending）、批准/驳回（记 comment/reviewedAt）、待审核/按库/详情三形态查询；与版本状态机零联动。
- **Files**：`fastrag-publish/.../controller/PublishController.java`（reviews 族 5 端点）、`service/ReviewService.java`、`service/impl/ReviewServiceImpl.java`、`entity/KbReviewTask.java`、`mapper/KbReviewTaskMapper.java`
- **Depth Rationale**：六方法全部单查询/单覆写，无状态前置校验（ReviewServiceImpl.java:27-32）——纯透传，浅。 ‹CERTAIN›

### PublishManageModule — **(S)**（第二套发布口径：历史/计划/重置/线上线下推导）
- **Responsibility**：以知识条目为粒度写发布/撤销历史；发布计划登记与查询；重置配置 CRUD 与重置执行（复制最近 published 记录）；从历史表推导线上版本/线下版本/策略效果三视图；分页读更新日志。
- **Files**：`fastrag-knowledge/.../controller/PublishManageController.java`（13 端点）、`service/PublishManageService.java`、`service/impl/PublishManageServiceImpl.java`、`entity/KbPublishHistory.java`、`entity/KbPublishPlan.java`、`entity/KbResetConfig.java`、`mapper/KbPublishHistoryMapper2.java`、`mapper/KbPublishPlanMapper.java`、`mapper/KbResetConfigMapper.java`
- **Depth Rationale**：publish/revoke/createPlan/listHistory 是单 insert/select；仅 resetKnowledge 含「查最近 published→复制」三步编排（PublishManageServiceImpl.java:108-129）、getOnlineVersion/getOfflineVersion/getStrategyEffect 含推导查询（:62-95）——仍属浅编排。 ‹CERTAIN›

### PublishConfigGate（platform 侧消费点）— **(S)**，归属 platform-config 链，本链仅登记
- **Responsibility**：发布/审核开关与流程配置的存取（publish_switch/review_switch/review_flow/review_flow_binding/publish_settings/review_settings 六键写入 sys_config，publish-status/review-status 读取）。
- **Files**：`fastrag-platform/.../controller/ConfigManageController.java:70-90`
- **Depth Rationale**：全部转发 ConfigManageService.saveConfig/getConfig，与模型/安全策略共用一个透传控制器——浅，且归属 platform-config 特性，本链不重复建模。 ‹CERTAIN›

### DdlOnlyReviewTables — 非 Java 模块（登记面）
- **Responsibility**：多级审核流的 DDL 预留（kb_review_strategy/kb_review_template/kb_review_node/kb_compliance_rule/kb_quality_rule，schema.sql:846-902）——实体+Mapper 已生成（fastrag-knowledge/.../entity|mapper 同名类）但零消费者。
- **Files**：schema.sql:846-902、`knowledge/.../entity/KbReviewStrategy|KbReviewTemplate|KbReviewNode|KbComplianceRule|KbQualityRule.java` 及同名 Mapper
- **Depth Rationale**：DDL-only，无行为。建议：实现审核流引擎前不扩展；或删除死代码实体。 ‹CERTAIN›

## 2. Interface Contracts

（HTTP 暴露面在此以公共接口形式列出；契约级定义只给 LogService，见 contract/interfaces.md `## publish-review`——它是唯一被跨模块消费的深接口。版本/审核/发布管理的 service 均为所属控制器的实现细节，按端点排除铁律不进 contract。）

**LogAuditModule 公共接口（LogService）** 〔LogService.java:26-76 ‹CERTAIN›〕
- `listLogs(kbId, category?, keyword?, page, pageSize) → PageResult<KbLog>` — 分页查询（category 精确 + keyword 三列模糊）
- `countByCategory(kbId) → Map{total, operation, retrieval, publish}` — 分类计数
- `listUpdateLogs(kbId, type?) → List<KbUpdateLog>` — 更新日志查询（时间倒序）
- `addLog(kbId, category: String, action: String, target, detail, operator)` — 兼容写入口（status 缺省 success）
- `addLog(kbId, category: LogCategory, action: ActionType, target, detail, operator, status[, extra])` — 枚举写入口（切面用）
- `addUpdateLog(kbId, updateType, target, detail, operator[, oldValue, newValue])` — 更新日志写入
- `markUpdateLogRead(id)` — 空实现（已读归前端）

**VersionModule 公共接口（PublishService）** 〔PublishService.java:13 ‹CERTAIN›〕
- `listVersions(kbId)` / `getLatestVersion(kbId)` / `getPublishedVersion(kbId)` — 三形态查询
- `createVersion(kbId, KbVersion)` — 自动版本号 + draft
- `transitionStatus(kbId, versionId, targetStatus)` — 直写流转（published 附写历史）

**ReviewModule 公共接口（ReviewService）** 〔ReviewService.java:12 ‹CERTAIN›〕
- `listReviews(kbId?)` / `getPendingReviews()` / `getReview(id)` — 三形态查询
- `submitForReview(kbId, versionId, applicant) → KbReviewTask` — 建 pending 单
- `approveReview(id, comment)` / `rejectReview(id, comment)` — 终态覆写

**PublishManageModule 公共接口（PublishManageService）** 〔PublishManageService.java:6-22 ‹CERTAIN›〕
- `publish(kbId, knowledgeId, history)` / `revoke(kbId, knowledgeId)` — 历史2写
- `listPublishHistory(kbId, knowledgeId?)` / `getPublishHistory(id)` — 历史查询
- `createPlan(plan)` / `listPlans(kbId)` / `getPlanExecution(planId)` — 计划面
- `getStrategyEffect(kbId)` / `getOnlineVersion(kbId, knowledgeId?)` / `getOfflineVersion(kbId, knowledgeId?)` — 推导三视图
- `listResetConfigs(kbId)` / `saveResetConfig(config)` / `resetKnowledge(kbId, knowledgeId)` — 重置面
- `getKnowledgeUpdateLogs(kbId, page, pageSize) → Map{list,total,page,pageSize}` — 更新日志分页读

**Hidden implementation**：动作名→状态名映射表（PublishController.java:41-43）、tags 的 JSON 序列化 setter（KbVersion.java:41-47）、SpEL 解析与模块名前缀表（KbLogAspect.java:56-83,144-174）、kbId 的参数名/首 String 参数双策略提取（:176-190）、同表双实体双 Mapper（KbPublishHistoryMapper 与 KbPublishHistoryMapper2 并存）。

**Depth rationale**：LogService 七方法皆单步领域动作但被 5 模块依赖，是本特性唯一「接口小于实现」且删除测试会失败的接口；其余三模块接口与实现一一对应。

## 3. Seam Diagram

### 缝 1：KB 鉴权缝（复用 iam，非本特性所立）——**半覆盖债务**
- **What varies**：谁有权查看/执行发布、审核、重置。
- **Adapters**：`@KbAuth(KBRole.viewer/editor)` 切面（PublishManageController 13 端点全覆盖，PublishManageController.java:47-123）——唯一判定实现在 iam（KbAccessChecker），本特性纯消费。**PublishController 的 10 个端点未挂 @KbAuth**（PublishController.java:31-52），版本/审核 HTTP 面游离于缝外——半覆盖是现状债务，修复方向=补注解（Parent Trace → REQ-B06 对齐）。
- **Stable side**：PublishManageController 端点签名。
- **Placement rationale**：鉴权横切面全后端统一在 fastrag-security。 ‹CERTAIN›

### 缝 2：审计事件缝（SysAuditLogEvent → operation 模块）
- **What varies**：操作日志归集到系统审计（sys_audit_log）的方式。
- **Adapters**：KbLogAspect 发布 Spring 应用事件（KbLogAspect.java:132-141）→ operation 侧 @EventListener 消费落 sys_audit_log——事件即适配器，单发布者接口多消费者，真缝。
- **Stable side**：SysAuditLogEvent 字段结构（userId/username/module/action/target/detail/status/ip）。
- **Placement rationale**：发布模块不应直接依赖 operation 模块——事件解耦保持依赖单向。 ‹CERTAIN›

### 缝 3：日志写缝（LogService / Loggable 注解）——本特性对外主缝
- **What varies**：谁要写日志（knowledge/retrieval/graph-eval/iam/operation 五模块）、写什么粒度（注解参数 vs 显式调用）。
- **Adapters**：两个入口——① @Loggable 注解 + KbLogAspect（声明式，21+ ActionType 前缀映射）；② LogService 直调（编程式，如 IngestionConsumer/RetrievalServiceImpl）。2+ 适配器，真缝。
- **Stable side**：LogService 签名 + @Loggable 注解属性（category/action/target/detail SpEL）+ kb_log 行语义（status success/failed、extra JSON）。
- **Placement rationale**：日志归属发布模块是历史布局，但接口稳定、消费面广，不建议搬迁——搬迁成本大于收益。 ‹CERTAIN›

### 缝 4：双发布口径缝（版本状态机 vs 知识条目历史）——**假设缝不立，登记耦合事实**
- **What varies**：「发布」的两种对象与两条写入路径——VersionModule 流转到 published 写 kb_publish_history（PublishServiceImpl.java:48-55），PublishManageModule 直接写同一张表（PublishManageServiceImpl.java:25-34）；两路径字段填充规则不同（operator=createdBy vs 请求体/operator=system）。
- **Adapters**：0 个——两模块互不引用，靠共享表 kb_publish_history 弱耦合；不立缝（无共同抽象），tdd 以「同表双写一致性」用例族覆盖。
- **Stable side**：kb_publish_history 表结构（见 data.md）。
- **Placement rationale**：合并两口径需要统一「发布对象」抽象（版本 id 或 knowledgeId 二选一），当前代码无此意图——〔决议 2026-09-24：暂不收敛，双口径按现状共存；理由：两口径各服务一个已成立的前端消费面（版本治理页走版本状态机、知识条目发布页走直接历史写入），合并需同时迁移前端调用与存量历史行语义，收益低风险高——按最小改动原则保留现状；现状差距：耦合事实已在本缝登记，无新增债务（代裁，用户可推翻）〕。

### 缝 5：配置开关缝（sys_config）——单消费者，不立缝
- **What varies**：审核/发布是否启用、审核流程形状。
- **Adapters**：仅前端读取（后端 review_switch 无代码消费者）——单适配器不立缝；登记为 platform-config 的数据消费点。
- **Stable side**：sys_config 行语义（configKey/configValue JSON/configType）。
- **Placement rationale**：后端若未来要 gate 审核流，消费点应在 ReviewModule.submitForReview 前置——当前无此实现。 ‹CERTAIN›

## 4. Deepening Opportunities

- **VersionModule**：可吞掉「发布对象」复杂度——统一两套发布口径、状态机合法性表（禁止 rejected→published 等非法迁移）、发布历史的原子写入（当前 createVersion 的 max+1 与 transition 的历史写入均可并发撕裂）。当前实现把这些复杂度留给了调用方与数据库缺省。
- **LogAuditModule**：可吞掉各模块绕过 LogService 直用 Mapper 的散乱（RetrievalServiceImpl.java:23-24、HomeController.java:35-36、UnifiedLogServiceImpl.java:13-14、UpdateRemindServiceImpl.java:21-22 四处直连 Mapper）——统一收敛到 LogService 后，日志表结构才有单点演进能力。
- **PublishManageModule**：可吞掉重置策略复杂度（canReset/maxResetCount 的执行期校验与次数计数，当前配置与执行互不消费）；发布计划执行器（scheduledTime 到期扫描）若落地也应内聚于此。
- **ReviewModule**：无——三态透传，若多级审核流（DDL 预留五表）落地，应新建 ReviewFlowEngine 而非加深本模块。

## 5. Parent Trace

| Design 单元 | Spec REQ |
|---|---|
| LogAuditModule（KbLogAspect + LogController + LogService） | REQ-F01~F07 |
| VersionModule（PublishController versions 族 + PublishService） | REQ-A01~A06、REQ-A05→kb_publish_history 写入 |
| ReviewModule（PublishController reviews 族 + ReviewService） | REQ-D01~D05 |
| PublishManageModule（PublishManageController + PublishManageService） | REQ-B01~B06、REQ-C01~C03、REQ-E01~E04 |
| PublishConfigGate（platform ConfigManageController 消费点） | REQ-D06 |
| DdlOnlyReviewTables（DDL 预留登记面） | §2 Out of Scope（审核流引擎排除项） |
| 缝 1（iam 鉴权复用） | REQ-B06（并暴露 REQ-A/D 面的鉴权债务） |
| 缝 2（审计事件） | REQ-F03 |
| 缝 3（日志写缝） | REQ-F04、F07 |
| 缝 4（双发布口径） | REQ-A05 与 REQ-B01 的同表双写 |
| 缝 5（配置开关） | REQ-D06 |
| VersionModule（PublishController versions 族 + PublishService） | REQ-A03（2026-09-24 review 补锚：版本查询三形态——按库列表（createdAt 倒序）、最新版本单条、当前已发布单条） |
| VersionModule（PublishController versions 族 + PublishService） | REQ-A04（2026-09-24 review 补锚：五态流转支持动作名自动映射（submit/approve/publish/reject/draft）或直接传状态名） |
| PublishManageModule（PublishManageController + PublishManageService） | REQ-B02（2026-09-24 review 补锚：编辑者撤销发布——写 publishType=revoke、status=revoked 历史记录，不修改任何既有记录） |
| PublishManageModule（PublishManageController + PublishManageService） | REQ-B03（2026-09-24 review 补锚：发布历史可按库查询（可选按 knowledgeId 过滤），createdAt 倒序） |
| PublishManageModule（PublishManageController + PublishManageService） | REQ-B04（2026-09-24 review 补锚：线上版本查询返回「最近一条 published+online 布尔」，线下返回「最近 10 条非 published+offlineCount」） |
| PublishManageModule（PublishManageController + PublishManageService） | REQ-B05（2026-09-24 review 补锚：策略效果统计三计数——totalPublish/successCount（publish 且 published）/revokeCount） |
| PublishManageModule（PublishManageController + PublishManageService） | REQ-C02（2026-09-24 review 补锚：计划列表（createdAt 倒序）与按 planId 执行详情可查） |
| PublishManageModule（PublishManageController + PublishManageService） | REQ-E02（2026-09-24 review 补锚：重置=复制最近一条 published 历史为新 published 记录（publishType=publish、operator=system、version 沿用），不改删既有记录） |
| PublishManageModule（PublishManageController + PublishManageService） | REQ-E03（2026-09-24 review 补锚：无可回退 published 记录时重置失败，抛 RuntimeException（全局兜底 500 语义）） |
| ReviewModule（PublishController reviews 族 + ReviewService） | REQ-D02（2026-09-24 review 补锚：审核人可批准或驳回任务，记录 comment 与 reviewedAt） |
| ReviewModule（PublishController reviews 族 + ReviewService） | REQ-D03（2026-09-24 review 补锚：审核任务查询三形态——待审核列表、按库过滤列表（createdAt 倒序）、单条详情） |
| ReviewModule（PublishController reviews 族 + ReviewService） | REQ-D04（2026-09-24 review 补锚：审核动作无前置状态校验——终态可覆写、任务 id 查无静默忽略（现状口径）） |
| LogAuditModule（KbLogAspect + LogController + LogService） | REQ-F02（2026-09-24 review 补锚：日志分类统计返回 total 与 operation/retrieval/publish 三枚举分类计数） |
| LogAuditModule（KbLogAspect + LogController + LogService） | REQ-F05（2026-09-24 review 补锚：更新日志可写入与查询（updateType/target/detail/oldValue/newValue/operator），支持按 updateType 过滤） |

ADR 符合性：本特性不触碰解析策略绑定（ADR-0001）与图存储（ADR-0002），无冲突。无「难逆转+费解+真权衡」三者齐备的新取舍，不新增 ADR。

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
