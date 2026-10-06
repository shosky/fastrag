# 发布、版本与知识审核（publish-review）— 需求规格

## 1. 特性与范围

本特性为知识库提供「编辑态 → 线上服务态」的切换治理：版本快照与状态流转（草稿→待审核→已通过→已发布→已驳回，支持立即上线/下线/回滚）、发布历史与线上线下版本查看、定时发布计划、审核工作流（提交/批准/驳回）、知识库重置，以及贯穿全后端的操作日志与更新日志审计（@Loggable 切面 + LogService）。代码跨 3 模块分布：版本/审核/日志在 fastrag-publish，发布管理（历史/计划/重置/线上线下版本）在 fastrag-knowledge 的 PublishManageController，发布与审核开关配置在 fastrag-platform 的 ConfigManageController。显式排除：应用级发布记录（app_publish_record，归 app-center 链）、审核策略/合规规则/审核模板五张 DDL 预留表的后端逻辑（当前 DDL-only）、发布开关对流程的实际拦截（开关只存不读，见 REQ-D06）。

## 2. Out of Scope

- **应用发布记录**：`app_publish_record`（schema.sql:1034）记录的是 App 的发布，归 app-center 特性；本特性只管知识库侧。
- **审核流引擎**：`kb_review_strategy`/`kb_review_template`/`kb_review_node`/`kb_compliance_rule`/`kb_quality_rule` 五张表 DDL 存在（schema.sql:846-902）但全仓无任何 service/controller 消费（grep 实测）——多级审核流、合规扫描、质量门禁均未实现。
- **发布计划的定时执行**：`kb_publish_plan.scheduled_time` 只落库，后端无任何 @Scheduled/定时器消费（grep 实测），「定时发布」实际是「计划登记」。
- **审核开关的流程拦截**：`review_switch`/`review_flow` 配置存入 sys_config 后，后端发布/审核代码不读取它——提交审核无条件可用，开关语义由前端自行解释。
- **版本内容快照**：版本不冻结文件列表、分片、QA 对中的任何内容数据（见 REQ-A02），不做差分对比（diff）计算。
- **更新日志已读状态持久化**：标记已读由前端本地处理，后端不存储（REQ-F06）。

## 3. 领域术语

- **发布（Publish）**：将知识库从编辑状态切换为线上服务状态，支持立即上线、下线、定时发布、版本回滚。——CONTEXT.md
- **知识库（Knowledge Base）**：发布与审核的作用域聚合根，表 `kb`。——CONTEXT.md
- **知识分片（Chunk）**：版本记录中 chunkCount 计数的指称对象（仅计数，不冻结内容）。——CONTEXT.md
- **操作审计（Audit Log）**：通过 @Loggable 注解 + AOP 切面自动记录的知识库操作日志。——CONTEXT.md
- **知识库权限（KB ACL）**：发布管理端点的鉴权面（viewer 可读 / editor 可写）。——CONTEXT.md
- **统一响应（ApiResponse）**：全部端点返回包装；业务错误 = HTTP 200 + body.code。——CONTEXT.md
- 本特性新增术语：**版本（KbVersion）**——知识库的发布快照元数据记录，五态 publishStatus〔KbVersion.java:34-38 ‹CERTAIN›〕；**审核任务（KbReviewTask）**——版本发布前的人工审批单，三态 status〔KbReviewTask.java:32-35 ‹CERTAIN›〕；**知识条目（knowledgeId）**——发布历史中的发布对象标识‹CERTAIN（2026-09-24 深证：schema.sql 全文无 knowledge 表；PublishManageServiceImpl.java:25-34 透传请求体值不校验存在性、无外键；PublishServiceImpl.java:50 复用该列写 versionId——同一列双语义，详见 data.md publish-review 节）›。

## 4. 功能需求

### 4.1 版本快照与状态流转

（叙述）版本是发布治理的载体：编辑者为知识库创建版本（自动递增版本号），版本经五态流转最终 published 上线。版本记录的是元数据（名称/描述/标签/文件数/分片数/变更摘要），不是内容快照。状态流转接口同时接受动作名（submit/approve/publish/reject/draft）与状态名，后端统一映射为状态存储。

- **REQ-A01**：用户可创建版本，版本号由后端按当前最大版本号 +1 自动分配，初始状态一律 draft。〔证据：PublishServiceImpl.java:37-43 ‹CERTAIN›〕
- **REQ-A02**：版本仅记录元数据——name/description/tags/changeSummary/fileCount/chunkCount；不冻结任何文件、分片或 QA 内容；fileCount/chunkCount 由请求体声明，服务端不校验其真实性。〔证据：KbVersion.java:34-38（无内容字段）、PublishServiceImpl.java:37-43（不覆盖计数）、grep 全仓无 createVersion 的内容收集调用方 ‹CERTAIN›——**此即 feature-map 遗留 INFERRED「版本快照粒度」的核实结论：粒度=纯元数据计数，无内容快照**〕
- **REQ-A03**：版本查询三形态：按库列表（createdAt 倒序）、最新版本（version 最大单条）、当前已发布版本（publishStatus=published 单条）。〔证据：PublishServiceImpl.java:34-36 ‹CERTAIN›〕
- **REQ-A04**：状态流转支持五态 draft/pending_review/approved/published/rejected，请求可传动作名 submit/approve/publish/reject/draft（自动映射）或直接传状态名。〔证据：PublishController.java:38-46（actionMap）‹CERTAIN›〕
- **REQ-A05**：版本流转到 published 时自动写一条发布历史（publishType=publish、status=published、operator=创建人、publishedAt=当前时间）。〔证据：PublishServiceImpl.java:48-55 ‹CERTAIN›〕
- **REQ-A06**：流转无状态机合法性校验（任意态可到任意态，如 rejected→published）；版本 id 查无时静默忽略不报错。〔证据：PublishServiceImpl.java:44-46（直接 set + updateById，无前置校验）‹CERTAIN›〕

### 4.2 发布执行与历史（知识条目粒度）

（叙述）PublishManage 侧提供第二套发布口径：以知识条目（knowledgeId）为单位直接写发布/撤销历史，不经过版本状态机。发布历史同时承担「上线/下线/回滚」三种事实的记录，线上线下版本查询与策略效果统计都从这一张历史表推导。

- **REQ-B01**：编辑者可发布指定知识条目：写 publishType=publish、status=published 的历史记录，version 未指定时缺省 1。〔证据：PublishManageServiceImpl.java:25-34 ‹CERTAIN›〕
- **REQ-B02**：编辑者可撤销发布：写 publishType=revoke、status=revoked 的历史记录（撤销不修改任何既有记录）。〔证据：PublishManageServiceImpl.java:35-44 ‹CERTAIN›〕
- **REQ-B03**：可按库（可选按 knowledgeId 过滤）查询发布历史，createdAt 倒序。〔证据：PublishManageServiceImpl.java:19-24 ‹CERTAIN›〕
- **REQ-B04**：线上版本查询返回「最近一条 published 记录 + online 布尔」；线下版本查询返回「最近 10 条非 published 记录 + offlineCount」。〔证据：PublishManageServiceImpl.java:76-95 ‹CERTAIN›〕
- **REQ-B05**：策略效果统计返回三计数：totalPublish（全部历史）、successCount（publish 且 published）、revokeCount（revoke 类型）。〔证据：PublishManageServiceImpl.java:62-73 ‹CERTAIN›〕
- **REQ-B06**：发布管理全部端点挂 @KbAuth（读 viewer / 写 editor），写操作带 @Loggable 审计（publish_published/publish_revoked/publish_plan_created/publish_reset/reset_config_saved）。〔证据：PublishManageController.java:47-123 ‹CERTAIN›〕

### 4.3 定时发布计划

（叙述）发布计划支持把多个知识条目的发布打包为计划并声明执行时间与策略，系统记录计划的执行状态与成功/失败计数。当前实现止步于「登记与查询」。

- **REQ-C01**：编辑者可创建发布计划：name 必填、knowledgeIds/strategy/scheduledTime 随体落库，executionStatus 未指定时缺省 pending。〔证据：PublishManageServiceImpl.java:48-52、schema.sql:831（name NOT NULL）‹CERTAIN›〕
- **REQ-C02**：可按库查询计划列表（createdAt 倒序）与按 planId 查询执行详情。〔证据：PublishManageServiceImpl.java:53-61 ‹CERTAIN›〕
- **REQ-C03**：后端不存在计划执行器——scheduledTime 到期后无任何组件将计划置为 running/completed，successCount/failCount/executionDetail 恒为创建时缺省值。〔证据：grep 全仓 @Scheduled 无 KbPublishPlan 消费、PublishManageServiceImpl 无执行方法 ‹CERTAIN›〕〔决议 2026-09-24：定时发布按「分期未排期」裁定——v1 交付口径=计划登记+查询，计划执行器为未排期目标；理由：代码无任何执行器/排期承诺痕迹，交付边界按现状最小改动原则圈定；现状差距：债务⑤已登记（interfaces.md publish-review 登记债务），另深证发现前端创建计划传 `scheduledAt` 而后端绑定字段为 `scheduledTime`（PublishPanel.vue:239 vs KbPublishPlan.java:29），JSON 静默失配——计划表 scheduled_time 实际亦恒 NULL（代裁，用户可推翻）〕

### 4.4 审核工作流

（叙述）审核任务独立于版本状态机存在：申请人提交审核单（pending），审核人批准或驳回并留言。审核单与版本通过 versionId 弱关联——审核动作不自动改变版本状态，联动需调用方（前端）再显式触发版本流转。

- **REQ-D01**：申请人可提交审核任务（kbId/versionId/applicant），初始状态 pending。〔证据：ReviewServiceImpl.java:29 ‹CERTAIN›〕
- **REQ-D02**：审核人可批准或驳回任务，记录 comment 与 reviewedAt。〔证据：ReviewServiceImpl.java:30-31 ‹CERTAIN›〕
- **REQ-D03**：审核任务查询三形态：待审核列表（全库 status=pending）、按库过滤列表（createdAt 倒序）、单条详情。〔证据：ReviewServiceImpl.java:27-28,32、PublishController.java:48-49 ‹CERTAIN›〕
- **REQ-D04**：审核动作无前置状态校验——已批准的任务可再驳回、已驳回的可再批准（终态可覆写）；任务 id 查无时静默忽略。〔证据：ReviewServiceImpl.java:30-31（selectById 后直接覆写 status，无 status 检查）‹CERTAIN›〕
- **REQ-D05**：审核与版本状态不联动：approveReview/rejectReview 不触碰 kb_version.publishStatus。〔证据：ReviewServiceImpl.java:30-31（仅写 kb_review_task）‹CERTAIN›〕
- **REQ-D06**：审核/发布开关与流程配置（review_switch/review_flow/review_flow_binding/review_settings/publish_switch/publish_settings）由平台管理员写入 sys_config，并可查询当前开关值；后端发布/审核流程不读取这些配置做拦截。〔证据：ConfigManageController.java:72-86 ‹CERTAIN›；grep 全仓 review_switch 无 publish/review 模块消费者 ‹CERTAIN›——**此即 feature-map 遗留 INFERRED「审核开关来源」的核实结论：来源=sys_config（platform 侧），但当前无后端 gate**〕

### 4.5 知识库重置

（叙述）重置把指定知识条目回退到其最近一次已发布状态：找到最新一条 published 历史，复制为新记录（operator=system）。重置前可按角色配置是否允许重置与最大次数，但执行路径当前不消费该配置。

- **REQ-E01**：可保存/查询重置配置（roleKey/canReset/maxResetCount/config），canReset 缺省 0、maxResetCount 缺省 3；有 id 则更新、无 id 则插入。〔证据：PublishManageServiceImpl.java:98-107 ‹CERTAIN›〕
- **REQ-E02**：重置执行 = 复制该条目最近一条 published 历史为新的 published 记录（publishType=publish、operator=system、version 沿用），不修改/不删除任何既有记录。〔证据：PublishManageServiceImpl.java:108-129 ‹CERTAIN›〕
- **REQ-E03**：无可回退的已发布记录时重置失败，抛 RuntimeException（走全局兜底 500 语义）。〔证据：PublishManageServiceImpl.java:117 ‹CERTAIN›〕
- **REQ-E04**：重置执行不检查 canReset/maxResetCount，也无重置次数计数——配置表与执行路径互不消费。〔证据：resetKnowledge 全方法无 rcMapper 引用 ‹CERTAIN›〕

### 4.6 操作日志与更新日志（全后端审计底座）

（叙述）日志能力是本特性的横切底座：@Loggable 注解 + KbLogAspect 切面为全后端标注方法自动记录操作日志（成功/失败双路径）并发布系统审计事件；LogService 是全部模块共用的日志写入口（当前被 knowledge/retrieval/graph-eval/iam/operation 五模块消费）。更新日志记录配置类变更的新旧值对比。

- **REQ-F01**：可分页查询知识库操作日志（PageResult），支持 category 过滤与 keyword 对 target/detail/operator 的模糊搜索，按时间倒序。〔证据：LogServiceImpl.java:53-64、LogController.java:35-42 ‹CERTAIN›〕
- **REQ-F02**：可查询日志分类统计：total + 三个枚举分类（operation/retrieval/publish）各自计数。〔证据：LogServiceImpl.java:67-77、LogController.java:44-47 ‹CERTAIN›〕
- **REQ-F03**：标注 @Loggable 的方法被切面自动记录：SpEL 解析 target/detail、自动提取 kbId 与当前操作人、按 ActionType 前缀映射业务模块名，成功写 status=success、异常写 status=failed 后原样上抛，并发布 SysAuditLogEvent 供系统审计归集。〔证据：KbLogAspect.java:91-118,120-141 ‹CERTAIN›〕
- **REQ-F04**：日志写入失败不阻断主业务流程（捕获后仅记 error 日志）。〔证据：LogServiceImpl.java:119-121,137-139 ‹CERTAIN›〕
- **REQ-F05**：可写入与查询更新日志（updateType/target/detail/oldValue/newValue/operator），支持按 updateType 过滤。〔证据：LogServiceImpl.java:80-84,125-140、LogController.java:49-79 ‹CERTAIN›〕
- **REQ-F06**：更新日志已读标记由前端本地处理，后端 markUpdateLogRead 为空实现。〔证据：LogServiceImpl.java:145-147、LogController.java:81-85 ‹CERTAIN›〕
- **REQ-F07**：操作日志与更新日志是共享数据面：retrieval（检索日志写入、更新提醒写入 update 日志）、operation（首页/统一日志读统计）、graph-eval、iam、knowledge 各模块直接消费 LogService 或 Mapper。〔证据：RetrievalServiceImpl.java:23-24、UpdateRemindServiceImpl.java:21-22、HomeController.java:35-36、UnifiedLogServiceImpl.java:13-14、grep LogService 消费清单 ‹CERTAIN›〕

## 5. 非功能约束

- **鉴权**：PublishManageController 全端点经 @KbAuth RBAC 判定（viewer/editor）；PublishController 的版本/审核端点当前无 @KbAuth〔PublishController.java:31-52 无注解 ‹CERTAIN›——鉴权不一致，登记为债务〕。
- **审计**：发布管理写操作与平台配置写操作均挂 @Loggable；发布历史写入本身不经 @Loggable（由调用端点注解覆盖）〔KbLogAspect.java:91 ‹CERTAIN›〕。
- **优雅降级**：日志写失败不阻断主流程（REQ-F04）。
- **性能**：〔决议 2026-09-24：取保守预期——单库日志 ≤10 万行级内，现有 idx_kb_id 索引 + timestamp 倒序可支撑常规分页查询；keyword 对 detail 的 LIKE 模糊与深翻页（大 OFFSET）随表线性退化，预期上限=单库 10 万行/全表百万行；归档策略=审计语义取长期保留、不自动清理。理由：代码外 SLA 信息，按业界审计惯例保守取值并与本仓「全部日志表均无归档任务」一致；现状差距：无自动归档与容量监控，表无限增长（data.md 本节已登记），超限需人工清理或引入归档任务（代裁，用户可推翻）〕

## 6. 验收清单

- [ ] 创建版本后版本号 = 库内该 KB 最大版本号 +1，状态 draft（REQ-A01）
- [ ] 版本记录不含任何文件/分片/QA 内容数据，仅有元数据与计数（REQ-A02）
- [ ] 传 action=submit 流转后状态为 pending_review；传 action=publish 后状态为 published 且发布历史新增一条（REQ-A04、REQ-A05）
- [ ] 对 rejected 版本执行 publish 流转会成功（现状口径，无状态机校验）（REQ-A06）
- [ ] 发布知识条目后历史表新增 publish/published 记录，version 未传时为 1（REQ-B01）
- [ ] 撤销后历史表新增 revoke/revoked 记录，原记录不变（REQ-B02）
- [ ] 线上版本查询在有 published 历史时返回该记录，无则 online=false（REQ-B04）
- [ ] 创建计划未指定状态时 executionStatus=pending，且到期后无自动执行（REQ-C01、REQ-C03；C03 豁免：占位/债务登记，见 implement 文档）
- [ ] 提交审核后任务出现在待审核列表；批准后带 comment/reviewedAt（REQ-D01、REQ-D02）
- [ ] 已批准任务再次驳回会成功（现状口径，终态可覆写）（REQ-D04）
- [ ] 修改 review_switch 配置后，提交审核仍然无条件成功（开关无后端拦截）（REQ-D06；豁免：占位/债务登记，见 implement 文档）
- [ ] 无 published 历史时执行重置返回业务失败（500 兜底语义）（REQ-E03）
- [ ] 重置成功后历史表新增一条 operator=system 的 published 记录，原记录不变（REQ-E02）
- [ ] 标注 @Loggable 的方法抛异常后，日志表存在一条 status=failed 记录且异常继续上抛（REQ-F03）
- [ ] 日志写入口（LogService/Mapper）人为制造失败时宿主业务仍成功返回（REQ-F04）
- [ ] 更新日志可记录 oldValue/newValue 并按 updateType 过滤（REQ-F05）
- [ ] 版本查询三形态：按库列表（createdAt 倒序）、最新版本单条、当前已发布单条（REQ-A03）
- [ ] 发布历史可按库查询（可选按 knowledgeId 过滤）且 createdAt 倒序（REQ-B03）
- [ ] 策略效果统计三计数（totalPublish/successCount/revokeCount）与历史记录一致（REQ-B05）
- [ ] 发布管理全部端点挂 @KbAuth（读 viewer/写 editor），写操作带 @Loggable 审计（REQ-B06）
- [ ] 计划列表（createdAt 倒序）与按 planId 执行详情可查（REQ-C02）
- [ ] 审核任务待审核列表、按库过滤列表、单条详情三形态可查（REQ-D03）
- [ ] 批准/驳回审核任务不触碰 kb_version.publishStatus（审核与版本不联动现状断言）（REQ-D05）
- [ ] 重置配置可保存/查询，canReset 缺省 0、maxResetCount 缺省 3（REQ-E01）
- [ ] 重置执行不检查 canReset/maxResetCount 也无次数计数（配置与执行互不消费现状断言）（REQ-E04；豁免：占位/债务登记，见 implement 文档）
- [ ] 操作日志分页支持 category 过滤与 keyword 对 target/detail/operator 的模糊搜索（REQ-F01）
- [ ] 日志分类统计返回 total 与 operation/retrieval/publish 三类枚举计数（REQ-F02）
- [ ] 更新日志已读标记接口为空实现，由前端本地处理（现状断言）（REQ-F06）
- [ ] retrieval/operation/graph-eval/iam/knowledge 各模块可经 LogService/Mapper 消费共享日志面（REQ-F07）

## 7. 测试策略

经由公共接口（HTTP 层 + LogService 跨模块接口）验证：① 版本号分配与五态流转（含动作名映射）是第一优先——流转写入的每条发布历史都要对账；② 发布历史推导查询（线上/线下/策略效果）与写入事实的一致性；③ 审核任务生命周期与其「不联动版本」的边界断言；④ 重置回退的复制语义与无可回退错误路径；⑤ @Loggable 切面双路径（成功/失败）与日志写失败的降级——用真实的 LogService + 测试库验证，mock 仅允许时间与 SecurityContext 边界。非法迁移（REQ-A06、REQ-D04）与并发发布（版本号分配、历史重复写入）是重点用例族（见 tdd/publish-review.md）。
