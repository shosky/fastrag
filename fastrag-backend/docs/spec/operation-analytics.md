# 运营分析与系统监控（operation-analytics）— 需求规格

## 1. 特性与范围

本特性是 FastRAG 后端的**运营消费端**：聚合全域业务数据与日志，为运营/管理页面提供工作台聚合、知识资产分析、审计三日志（系统审计/登录/统一日志）、用户反馈管理、问答明细、数据挖掘任务与模型调用监控七类只读为主的能力。它对各业务链的表（`chat_session`、`model_call_log`、`kb_log`、`kb`、`kb_file`、`app` 等）是**只读消费方**；自身拥有的写入面仅有：用户反馈 CRUD 与回复、数据挖掘任务 CRUD 与触发、以及经由 `@Loggable`+AOP 的操作审计自动留痕。显式排除：任何业务日志的产生端职责、告警推送、以及 Elasticsearch 相关检索。

## 2. Out of Scope

- **日志的产生端**：`chat_session` 由 app-center 写入、`mcp_call_log` 由 tools-skills-mcp 写入、`model_call_log` 由模型调用方写入——本特性只读，不新增/修改这些表的业务写入逻辑。
- **权限体系本身**：复用安全模块的 KB ACL（`@KbAuth`/`KbAccessChecker`）与登录态，不定义新的角色或授权模型。
- **告警与通知**：模型调用异常、登录失败等只提供数据查询，不做阈值告警、邮件/Webhook 推送。
- **日志归档与清理**：不提供日志表的轮转/归档/冷热分层接口。
- **全文检索**：不做 Elasticsearch 接入，日志/反馈筛选走 MySQL 条件查询（宪法禁 ES）。
- **数据可视化前端**：只提供聚合 JSON，不产出图表。
- **操作审计的业务语义扩展**：不为单个业务域定制审计字段，统一走 `@Loggable` 注解模型。

## 3. 领域术语

- **操作审计（Audit Log）** — 通过 @Loggable 注解 + AOP 切面自动记录的知识库操作日志（CONTEXT.md）。
- **知识库（Knowledge Base）**、**知识分片（Chunk）** — 分析指标「知识引用率」的分母/分子依据（CONTEXT.md）。
- **应用（App）** — 工作台「热门应用」聚合的对象（CONTEXT.md）。
- **统一响应（ApiResponse）** — 全部端点返回包装；业务错误 = HTTP 200 + body.code（CONTEXT.md）。
- **PageResult{list,total,page,pageSize}** — 分页统一结构（CONTEXT.md）。
- **知识库权限（KB ACL）** — 工作台/分析可见范围过滤所复用的权限机制（CONTEXT.md）。

## 4. 功能需求

### 4.1 工作台聚合（Home Dashboard）

首页一次请求返回运营/用户所需的四区数据，全部按当前登录用户的可见范围过滤；这是权限敏感度最高的聚合接口。

- **REQ-H01**：单次请求聚合返回推荐知识库、热门文档、热门应用、最近动态四个区块。〔证据：HomeController.java:64-144，CERTAIN〕
- **REQ-H02**：推荐知识库 = 用户可见的知识库按创建时间倒序取最新 5 个，含名称/描述/分类/标签/创建者。〔证据：HomeController.java:77-90，CERTAIN〕
- **REQ-H03**：热门文档 = 未删除且处理状态为 completed 的文档按浏览量倒序取 TOP 10。〔证据：HomeController.java:92-106，CERTAIN〕
- **REQ-H04**：热门应用 = 按创建时间倒序取最新 5 个应用；仅当用户具备 `app:use` 权限（或超管通配/API Token）时返回，否则该区为空。〔证据：HomeController.java:108-119,157-162，CERTAIN〕
- **REQ-H05**：最近动态 = 用户可见知识库范围内最新 10 条知识库操作日志（操作人/动作/对象/详情/时间）。〔证据：HomeController.java:121-134，CERTAIN〕
- **REQ-H06**：可见范围口径统一为三类：平台级 API Token 不过滤；普通用户（含超管）按「本组织 ∪ ACL 授权」过滤；无任何可见库时四区直接返回空列表。〔证据：HomeController.java:66-75,146-155，CERTAIN〕

### 4.2 知识资产分析（Knowledge Asset Analytics）

按知识库维度给出资产总量、活跃度与热度排行，支撑「知识资产盘点」页面；口径必须与工作台一致（同一可见范围函数）。

- **REQ-K01**：返回四项指标：总知识库数、总文档数（未删除）、活跃文档数（status=completed）、知识引用率；知识引用率 = 有分片（chunkCount>0）的完成文档数 ÷ 活跃文档数，百分比保留 1 位小数，活跃文档为 0 时显示 0%。〔证据：AnalyticsController.java:67-95，CERTAIN〕
- **REQ-K02**：热门知识库排行 TOP 5，按未删除文档数量降序，附文档数与名次。〔证据：AnalyticsController.java:116-149，CERTAIN〕
- **REQ-K03**：热门文档排行 TOP 5，按浏览量降序（仅 completed 未删除文档），附所属知识库名与名次。〔证据：AnalyticsController.java:151-181，CERTAIN〕
- **REQ-K04**：可见范围过滤与 REQ-H06 同口径（API Token 不过滤；普通用户按组织 ∪ ACL；无可见库时以不可能集合查询，各项归零）。〔证据：AnalyticsController.java:51-61，CERTAIN〕

### 4.3 审计三日志（Audit Trio）

面向安全审计与运维排查的三条日志查询线：系统审计日志（业务操作留痕）、登录日志（账号安全）、统一日志（跨源分页检索）。本特性对三张日志表**只读查询**（写入分别由 AOP 切面/登录流程等产生端完成）。

- **REQ-L01**：系统审计日志列表查询，支持按模块名过滤与返回条数上限。〔证据：AuditLogController.java:20，CERTAIN〕
- **REQ-L02**：登录日志列表查询，支持按用户 ID、状态（success/failed/logout）过滤与条数上限（默认 100）。〔证据：SysLoginLogController.java:35-40，CERTAIN〕
- **REQ-L03**：统一日志分页查询：把系统审计日志、登录日志等不同来源日志整合到同一分页结果中，支持统一查询对象的多条件筛选。〔证据：UnifiedLogController.java:37-40 及类注释 11-23，CERTAIN；具体可筛选字段见契约〕
- **REQ-L04**：知识库写操作（增删改类方法）经由 `@Loggable` 注解声明后由 AOP 切面自动记录操作审计，业务代码无需手工埋点。〔证据：CONTEXT.md「操作审计」条目 + operation/listener/SysAuditLogEventListener.java 存在，CERTAIN；注解声明式用法细节见实现文档〕
- **REQ-L05**：三条日志查询均为只读，不提供任何修改/删除日志的端点。〔证据：三个日志控制器均仅含 GET 端点（AuditLogController.java:20、SysLoginLogController.java:35、UnifiedLogController.java:37），CERTAIN〕

### 4.4 反馈管理（Feedback Management）

用户反馈（针对知识库问答质量）的完整生命周期：创建→处理（回复/状态更新）→统计→归档删除；是本特性少数具备写路径的域。

- **REQ-F01**：反馈分页列表，支持按知识库、反馈类型、处理状态过滤，默认 page=1、pageSize=20，返回 PageResult。〔证据：FeedbackController.java:30-33，CERTAIN〕
- **REQ-F02**：反馈全量列表（不分页），支持按知识库过滤。〔证据：FeedbackController.java:34，CERTAIN〕
- **REQ-F03**：反馈统计（按知识库维度聚合）与反馈概览（总量/待处理等总览口径）两类聚合查询。〔证据：FeedbackController.java:35-36，CERTAIN；概览具体字段见契约〕
- **REQ-F04**：反馈创建、更新、删除三个写操作。〔证据：FeedbackController.java:37-39，CERTAIN〕
- **REQ-F05**：反馈回复：对指定反馈写入回复内容与操作人（未显式指定操作人时默认 `system`）。〔证据：FeedbackController.java:40-42，CERTAIN〕

### 4.5 问答明细（Chat Session Detail）

为反馈处理提供上下文：分页检索用户的聊天会话（问答记录）明细，只读消费 app-center 域写入的 `chat_session` 表。

- **REQ-C01**：聊天会话分页查询，支持按关键词模糊匹配用户提问、按用户 ID 过滤，按创建时间倒序，返回 PageResult。〔证据：ChatSessionController.java:35-51，CERTAIN〕
- **REQ-C02**：问答明细为只读查询，不提供会话的创建/修改/删除端点。〔证据：ChatSessionController.java 仅含 GET list，CERTAIN〕

### 4.6 数据挖掘（Data Mining）

面向运营的批量数据加工任务（如从会话/反馈数据中挖掘 FAQ、多轮、意图等模式）：任务的定义、管理与触发执行。

- **REQ-D01**：数据挖掘任务列表查询（按知识库、关键词过滤）与单任务详情查询。〔证据：DataMiningController.java:25-26，CERTAIN〕
- **REQ-D02**：数据挖掘任务的创建与删除。〔证据：DataMiningController.java:27-28，CERTAIN〕
- **REQ-D03**：手动触发指定任务的执行，并返回执行结果或任务状态。〔证据：DataMiningController.java:29，CERTAIN；执行是否异步及产物落点见设计文档〕
- **REQ-D04**：任务类型/参数模型由任务记录自描述（类型字段+JSON 参数），支持不同挖掘目标的同一套 CRUD/触发流程。‹CERTAIN（2026-09-24 深证：DataMiningTask.java:26 含 ruleType/ruleConfig 自描述字段，DataMiningController.java:25-29 五端点无类型特化，不同 ruleType 走同一套 CRUD/run）›；字段明细见契约

### 4.7 模型监控（Model Monitor）

AI 模型调用维度的运行监控：按时间范围统计各模型的调用次数/成功率/耗时等总览指标，只读消费 `model_call_log`。

- **REQ-M01**：模型监控总览：默认统计近 7 天，支持按时间范围（天）、模型关键字过滤，模型维度的统计列表分页返回（默认 page=1、pageSize=10）。〔证据：ModelMonitorController.java:36-43，CERTAIN；总览指标明细见契约〕
- **REQ-M02**：模型监控为只读聚合，不写入、不修改调用日志。‹CERTAIN（2026-09-24 深证：ModelMonitorController.java:36-43 仅 GET /overview；ModelMonitorServiceImpl.java:47-186 全部聚合经 selectCount/selectObjs/selectMaps/selectMapsPage 只读完成，无任何 insert/update/delete）›

## 5. 非功能约束

- **统一响应**：全部端点返回 `ApiResponse` 包装；业务错误 = HTTP 200 + body.code，调用方必须判断 body.code。〔证据：7 个控制器全部返回 ApiResponse（各控制器 import 行），CERTAIN〕
- **分页规范**：所有分页端点统一 `PageResult{list,total,page,pageSize}`。〔证据：ChatSessionController.java:50 使用 PageResult.of，CERTAIN；其余分页端点是否符合 —— 已知缺陷信号「分页违规」待实现文档核实〕
- **权限过滤不可绕过**：工作台与分析聚合必须内联权限过滤，不允许返回越权知识库/文档数据。〔证据：HomeController.java:150-155、AnalyticsController.java:55-61，CERTAIN〕
- **空权限快速返回**：无任何可见知识库时聚合接口直接返回空集合而非报错。〔证据：HomeController.java:68-75，CERTAIN〕
- **性能阈值**〔决议 2026-09-24：聚合接口性能预算取 P95 ≤ 2s（首页四区与分析四指标均为 TOP N/LIMIT 小结果集或 COUNT 聚合，2s 覆盖十万行级表的常规索引扫描）；日志保留时长取 sys_audit_log / sys_login_log / kb_log 各 180 天；理由：安全审计日志「至少留存六个月」为网络安全法与等保惯例口径，聚合预算取保守常规值；现状差距：三日志表 append-only 无归档/清理任务（全仓无相关 @Scheduled/DELETE），超期数据无限累积——已登记债务见 implement/operation-analytics.md §2（代裁，用户可推翻）〕
- **审计日志敏感度**〔决议 2026-09-24：取「暂不脱敏、原文留存」；理由：审计留痕的合规价值在于完整可追溯，脱敏破坏追溯；现状 detail/target 为 @Loggable 注解 SpEL 解析的业务标识（KbLogAspect.java:96-115 直存，无掩码处理 ‹CERTAIN›），当前无敏感字段入注解的实证；现状差距：无脱敏机制亦无注解字段白名单——未来若 detail 引入敏感参数需补掩码机制并复核存量（代裁，用户可推翻）〕

## 6. 验收清单

- [ ] 无任何可见知识库的普通用户请求首页聚合，四区均为空列表且 code 成功（REQ-H06）
- [ ] 平台级 API Token 请求首页/分析接口，返回未被 ACL 过滤的全局数据（REQ-H06、REQ-K04）
- [ ] 推荐知识库数量 ≤5、热门文档 ≤10、热门应用 ≤5、最近动态 ≤10，且排序口径正确（创建时间/浏览量/时间倒序）（REQ-H01、REQ-H02、REQ-H03、REQ-H04、REQ-H05）
- [ ] 热门文档、热门文档排行不含已删除（deleted_at 非空）或未完成（status≠completed）的文档（REQ-H03、REQ-K03）
- [ ] 知识引用率在有分片完成文档 3/活跃文档 4 时显示 75.0%，活跃文档为 0 时显示 0%（REQ-K01）
- [ ] 无 `app:use` 权限的普通用户首页热门应用区为空（REQ-H04）
- [ ] 系统审计日志可按 module 过滤；登录日志可按 userId/status 过滤，limit 未传时默认 100（REQ-L01、REQ-L02）
- [ ] 统一日志查询返回分页结构且聚合了审计与登录两类日志来源（REQ-L03）
- [ ] 反馈列表分页默认 page=1、pageSize=20；statistics 与 overview 均可按 kbId 过滤（REQ-F01、REQ-F03）
- [ ] 创建反馈后可回复，未指定操作人时回复操作人为 system（REQ-F04、REQ-F05）
- [ ] 问答明细分页按创建时间倒序，keyword 命中用户提问的模糊匹配（REQ-C01）
- [ ] 数据挖掘任务创建后可查询、可触发 run、可删除（REQ-D01、REQ-D02、REQ-D03）
- [ ] 模型监控默认统计近 7 天，keyword 过滤与分页参数生效（REQ-M01）
- [ ] 所有日志/会话/监控端点均无写操作入口（只读性）（REQ-L05、REQ-C02、REQ-M02）
- [ ] 全部端点返回 ApiResponse 结构，分页端点返回 PageResult 四字段（§5 非功能约束）
- [ ] 热门知识库排行 TOP 5 按未删除文档数降序，附文档数与名次（REQ-K02）
- [ ] 知识库写操作经 @Loggable 声明后由 AOP 切面自动留痕，业务代码无需手工埋点（REQ-L04）
- [ ] 反馈全量列表（不分页）可按知识库过滤（REQ-F02）
- [ ] 不同 type/params 的挖掘任务走同一套 CRUD/触发流程（任务记录自描述）（REQ-D04）

## 7. 测试策略

经由公共接口（HTTP 层）验证，按风险排序：

1. **权限过滤正确性**（最高风险）：用 ACL 可见/不可见知识库矩阵测首页与分析聚合——越权数据一个字节都不能出现；API Token 与普通用户两条路径都要覆盖（REQ-H06/REQ-K04）。
2. **聚合口径正确性**：预置已知数据集验证 TOP N 截断、排序字段、引用率计算与除零（REQ-H02~H05、REQ-K01~K03）。
3. **统一日志多源聚合**：审计+登录双源数据下验证分页总数、来源标记与筛选条件透传（REQ-L03）。
4. **反馈生命周期**：创建→回复→统计变化→删除的状态流转（REQ-F01~F05）。
5. **只读性回归**：对日志/会话/监控端点断言无写路径（REQ-L05、REQ-C02、REQ-M02）。
6. **数据挖掘触发**：run 幂等性、不存在任务的行为（REQ-D03）。
