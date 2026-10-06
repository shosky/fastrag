# 运营分析与系统监控（operation-analytics）— 架构设计

> 对应 spec：spec/operation-analytics.md（REQ-H/K/L/F/C/D/M 七域）；数据契约：contract/data.md `## operation-analytics` 节；接口契约：contract/interfaces.md `## operation-analytics` 节。

## 1. Module Map

| 模块 | Responsibility（一行） | Files | Depth Rationale |
|---|---|---|---|
| **DashboardAggregator**（工作台聚合器） | 一次请求聚合四区首页数据并强制三类权限口径 | `operation/controller/HomeController.java`、`operation/model/HomeData.java` | **深**：三类可见口径（API Token/组织∪ACL/空权限短路）×四区聚合（TOP 5/10/5/10）收敛于单端点，调用方零感知；⚠ 逻辑物理上直接写在控制器内、无 service 层〔HomeController.java:64-144 ‹CERTAIN›〕 |
| **AssetAnalyticsAggregator**（知识资产分析器） | 四指标+双排行+同口径权限过滤的知识资产盘点 | `operation/controller/AnalyticsController.java`、`operation/model/AnalyticsData.java` | **深**：引用率口径（分片文档/活跃文档）、GROUP BY 聚合排行、`__none__` 不可能集合技巧；⚠ 同样无 service 层〔AnalyticsController.java:63-190 ‹CERTAIN›〕 |
| **AuditLogPipeline**（审计日志管线） | sys_audit_log 的事件驱动写侧与查询读侧 | `operation/listener/SysAuditLogEventListener.java`、`operation/service/AuditLogService.java`+`impl/AuditLogServiceImpl.java`、`operation/entity/SysAuditLog.java`、`operation/mapper/SysAuditLogMapper.java` | **深**（写侧）：把「发布方无需知道存储」的事件解耦折叠为单监听器，写失败吞异常不阻断业务〔SysAuditLogEventListener.java:32-49 ‹CERTAIN›〕；读侧（list）浅 |
| **UnifiedLogQueryEngine**（统一日志查询引擎） | 按 category 把审计/登录/操作三类日志路由为统一 DTO 分页结果 | `operation/service/UnifiedLogService.java`+`impl/UnifiedLogServiceImpl.java`、`operation/model/UnifiedLogQuery.java`、`operation/model/UnifiedLogDTO.java` | **深**：三源异构字段→统一 DTO 的映射字典（module/action/timestamp 语义对齐）单点维护〔UnifiedLogServiceImpl.java:127-168 ‹CERTAIN›〕；⚠ 返回 MyBatis-Plus `IPage` 而非 `PageResult`（分页规范违规，见 §4） |
| **LoginLogLedger** (S)（登录日志账本） | sys_login_log 的写入（被 iam 调用）与过滤查询 | `operation/service/SysLoginLogService.java`+`impl/SysLoginLogServiceImpl.java`、`operation/entity/SysLoginLog.java`、`operation/mapper/SysLoginLogMapper.java` | **浅**：insert+单表双条件过滤；跨模块价值仅在写协议（iam 8 参调用、吞异常）；建议并入 AuditLogPipeline 统一为一个日志存取模块 |
| **FeedbackLedger**（反馈账本） | 反馈全生命周期：组织隔离 CRUD、回复状态机、统计与总览聚合 | `operation/controller/FeedbackController.java`、`operation/service/FeedbackService.java`+`impl/FeedbackServiceImpl.java`、`operation/entity/UserFeedback.java`、`operation/mapper/UserFeedbackMapper.java` | **深**：org 归属不变量（requireOwnOrg）、pending→resolved 状态转移、四段总览聚合（指标卡/分类/词云/应用排行）全部单点收口〔FeedbackServiceImpl.java:42-49,90-96,140-278 ‹CERTAIN›〕 |
| **ChatSessionFinder** (S)（问答明细查询） | chat_session 的只读分页检索 | `operation/controller/ChatSessionController.java`、`operation/entity/ChatSession.java`、`operation/mapper/ChatSessionMapper.java` | **浅**：单表双 like 条件分页，控制器直用 Mapper 无 service；建议内联或并入 UnifiedLogQueryEngine（同为只读检索面） |
| **DataMiningTaskBoard** (S)（数据挖掘任务板） | 挖掘任务 CRUD 与触发执行 | `operation/controller/DataMiningController.java`、`operation/service/DataMiningService.java`+`impl/DataMiningServiceImpl.java`、`operation/entity/DataMiningTask.java`、`operation/mapper/DataMiningTaskMapper.java` | **浅**：标准 CRUD；run 为**模拟执行**（随机数+硬编码关键词摘要，DataMiningServiceImpl.java:29-33 ‹CERTAIN›）——无真实挖掘引擎，模块深度是假的 |
| **ModelMonitorPanel**（模型监控面板） | model_call_log 的时窗聚合总览（环比/分布/排行/分页统计） | `operation/controller/ModelMonitorController.java`、`operation/service/ModelMonitorService.java`+`impl/ModelMonitorServiceImpl.java`、`operation/model/ModelMonitorData.java` | **深**：环比口径（前一时窗对比+smallerIsBetter 趋势语义）、成本估算口径（¥5/百万 token）、org 过滤——但 org 过滤用 SQL 字符串拼接实现（§4 缺陷） |

共享层引用（不重复设计）：`ApiResponse`/`PageResult`/`GlobalExceptionHandler`（fastrag-common）、`KbAccessChecker`+`SecurityUtil`（fastrag-security）、`KbLogAspect`+`LogService`+`KbLog`（fastrag-publish，归 publish-review 链 Design.LogAuditModule）、`ModelCallLog`+Mapper（fastrag-platform，归 platform-config 链）。

## 2. Interface Contracts

（HTTP 暴露面在此立面；逐接口契约见 contract/interfaces.md `## operation-analytics` 节。）

### DashboardAggregator
- Public：`getHomeData() → ApiResponse<HomeData>`（四区：recommendKBs≤5/hotDocs≤10/hotApps≤5/recentActivities≤10）；`getKbAnalytics() → ApiResponse<AnalyticsData>`（metrics×4/hotKBs≤5/hotDocs≤5）〔HomeController.java:64、AnalyticsController.java:63 ‹CERTAIN›〕
- Hidden：`accessibleKbIds()` 三口径解析、`canUseApp()`、`parseTags()` 手写 JSON 数组解析、kb 名/文档数回填循环
- Depth rationale：权限口径与聚合口径不泄漏给前端

### AuditLogPipeline
- Public：`onSysAuditLogEvent(SysAuditLogEvent)`（监听器入口，事件字段=契约）；`AuditLogService.list(module, limit) → List<SysAuditLog>`；`AuditLogService.addLog(9 参)`（⚠ 全仓无调用方，死代码〔grep 实测 ‹CERTAIN›〕）
- Hidden：异常吞没（warn 后返回）、timestamp 服务器时间覆写
- Depth rationale：发布方（KbLogAspect，publish 域）对 sys_audit_log 表结构零依赖，事件即契约

### UnifiedLogQueryEngine
- Public：`pageQuery(UnifiedLogQuery) → IPage<UnifiedLogDTO>`；query 8 字段（category/keyword/module/operator/kbId/startTime/endTime/page/pageSize）〔UnifiedLogQuery.java:22-46 ‹CERTAIN›〕
- Hidden：category→源表路由 switch、三组字段映射（login→module="登录认证"/action=status、operation→module=kb_log.category）、keyword OR 条件构造
- Depth rationale：三源异构→单一查询面的语义对齐只此一处

### LoginLogLedger
- Public：`addLoginLog(userId, username, ip, device, os, browser, status, failReason)`（iam.AuthController 6 处调用：login 成功/失败/logout/register/改密等）；`list(userId, status, limit) → List<SysLoginLog>`〔SysLoginLogServiceImpl.java:36-67；iam/AuthController.java:69-186 grep 实测 ‹CERTAIN›〕
- Hidden：status 缺省 success 兜底、异常吞没
- Depth rationale：浅——写协议跨模块（iam→operation），建议合并进统一日志存取模块

### FeedbackLedger
- Public：`page(kbId,feedback,status,page,pageSize) → PageResult`；`list(kbId) → List`（LIMIT 200 上限）；`create(UserFeedback)`；`update(id,fb) → UserFeedback`；`delete(id)`；`reply(id,reply,operator) → UserFeedback`；`statistics(kbId) → Map`；`getOverview(kbId) → Map`〔FeedbackService.java 全部 ‹CERTAIN›〕
- Hidden：applyOrgScope 双载（Lambda/QueryWrapper）、requireOwnOrg 归属校验、SQL 聚合 selectMaps（statistics/overview 四段）、词频内存计算（正则分词 top20）
- Depth rationale：归属不变量+状态机+四段聚合折叠为单账本

### ChatSessionFinder
- Public：`list(keyword, userId, page, pageSize) → ApiResponse<PageResult>`〔ChatSessionController.java:36-51 ‹CERTAIN›〕
- Hidden：无（控制器直用 Mapper）
- Depth rationale：浅——无归属校验、无 service 层

### DataMiningTaskBoard
- Public：`list(kbId,keyword)`、`get(id)`、`create(task)`、`delete(id)`、`run(id) → DataMiningTask`〔DataMiningService.java ‹CERTAIN›〕
- Hidden：create 兜底（status=enabled/ruleType=keyword）、run 模拟摘要生成
- Depth rationale：浅——run 的模拟性使「执行」语义名不副实

### ModelMonitorPanel
- Public：`getOverview(timeRange, keyword, page, pageSize) → ModelMonitorData{metrics×4, distribution, topApps≤5, stats=PageResult}`〔ModelMonitorService.java、ModelMonitorServiceImpl.java:47-186 ‹CERTAIN›〕
- Hidden：orgCond() SQL 片段拼接、双时窗环比 SQL、cost 估算、calcChange 趋势语义
- Depth rationale：环比/成本/分布三套口径单点收口；⚠ 物理实现以 `.apply()` 拼 SQL 片段

## 3. Seam Diagram

### 缝 1：审计事件缝（真缝）
- **What varies**：哪些业务动作需要系统级留痕、发布方如何描述动作（module/action/target/detail）
- **Adapters**：① `KbLogAspect`（fastrag-publish，@Loggable 切面→发布 SysAuditLogEvent，KbLogAspect.java:91-118）——发布侧适配器；② `SysAuditLogEventListener`（operation，事件→sys_audit_log 行，SysAuditLogEventListener.java:32-49）——消费侧适配器
- **Stable side**：`SysAuditLogEvent` 9 字段契约（common/event/SysAuditLogEvent.java:14-38）+ `@Loggable` 注解词汇（LogCategory/ActionType 枚举）
- **Placement rationale**：事件放 common、切面放 publish（kb_log 写入方）、监听器放 operation（sys_audit_log 写入方）——避免 publish→operation→publish 循环依赖（Loggable.java:24-26、SysAuditLogEvent.java:10-11 javadoc 明示此动机 ‹CERTAIN›）。⚠ 设计注释称「异步写入」，但监听方法无 `@Async` 注解，实际**同步执行于发布线程**〔‹CERTAIN（2026-09-24 深证：SysAuditLogEventListener.java:32 仅 @EventListener；Spring 语义下无 @Async 即发布线程内联执行；FastRagApplication.java:32 @EnableAsync 仅启用注解处理，不改变无注解方法的行为）›——注释误导，见 §4 修正建议。
- 符合 ADR 要点：无冲突；ADR-0001（策略绑定→重分片）/ADR-0002（Neo4j 唯一图存储）与本链无交集

### 缝 2：跨域只读消费缝（真缝）
- **What varies**：聚合数据来自哪个业务域（knowledge/app/publish/platform 四域 5 张表）
- **Adapters**：① KnowledgeBaseMapper+KbFileMapper（knowledge 域）；② AppMapper（application 域）；③ KbLogMapper（publish 域）；④ ModelCallLogMapper（platform 域）；⑤ ChatSessionMapper（本域孤儿表）——5 个适配器=真缝
- **Stable side**：各域 data.md 契约（`## kb-core`/`## app-center`/`## publish-review`/`## platform-config` 节）；本链承诺**只读不写**
- **Placement rationale**：operation 作为纯消费端直接注入上游 Mapper（越过 service 层），以「只读」纪律代替接口隔离——与 tools-skills-mcp 链登记的「mcp_call_log 只写不读，查询归 operation-analytics」口径互为镜像（data.md `## tools-skills-mcp` mcp_call_log 条目）
- ⚠ 缺陷信号：chat_session 全仓无写入方、model_call_log 无 Java 写入方（platform-config 节已登记为死表 REQ-C03）——两个只读消费面在当前代码下数据恒缺〔grep 实测 ‹CERTAIN›〕

### 缝 3：登录日志写缝（假设缝，不立缝）
- iam→operation 只有 `SysLoginLogService` 一个调用点（AuthController 6 处），单生产方单消费方——按规则不立缝，作为 LoginLogLedger 的跨模块写协议登记；若未来出现第二生产方（如 SSO 回调）再升真缝

## 4. Deepening Opportunities

- **DashboardAggregator/AssetAnalyticsAggregator**：聚合逻辑物理在控制器内（无 service），权限口径 `accessibleKbIds()` 在 HomeController/AnalyticsController/FeedbackServiceImpl 三处重复实现且口径微差（空权限：Home 返回空集合短路、Analytics 用 `__none__` 哨兵、Feedback overview 用 ACL）——应下沉为单一 VisibilityScope 服务吞掉三口径差异
- **UnifiedLogQueryEngine**：① 返回 `IPage` 违反 PageResult 规范（ spec §5 非功能约束，全后端唯一违规点）〔UnifiedLogService.java:15 ‹CERTAIN›〕；② keyword OR 条件与后续 AND 条件存在优先级缺陷：`like(detail).or().like(target).eq(module)` 生成 `detail LIKE ? OR target LIKE ? AND module = ?`（AND 优先级高于 OR，module 过滤只作用于 target 分支）而非预期的 `(detail LIKE ? OR target LIKE ?) AND module = ?`〔‹CERTAIN（2026-09-24 深证：UnifiedLogServiceImpl.java:53-59,99-108 未用 and(nested) 嵌套；MyBatis-Plus 条件串联默认 AND 连接、.or() 仅切换下一连接符且不自动加括号，为框架文档化行为）›——audit/operation 两分支同病〔UnifiedLogServiceImpl.java:53-59,99-108〕；③ category=update 在 Query javadoc 声称支持（kb_update_log）但 switch 无此分支，落入 default 空页〔UnifiedLogQuery.java:19 vs UnifiedLogServiceImpl.java:40-45 ‹CERTAIN›〕
- **AuditLogPipeline/LoginLogLedger**：两处 `last("LIMIT " + limit)` SQL 拼接——limit 为 Integer 类型绑定，实际不可注入，但违反参数化惯例且 limit≤0/超大值无校验〔AuditLogServiceImpl.java:41、SysLoginLogServiceImpl.java:65 ‹CERTAIN›〕；`AuditLogService.addLog` 死代码应删除；监听器「异步」语义应显式 @Async 或改正注释
- **FeedbackLedger**：词云 2000 行加载+内存分词应下沉（或缓存）；`reply` 中 `if(fb==null) return null` 不可达（requireOwnOrg 先抛 notFound）〔FeedbackServiceImpl.java:42-44,90-93 ‹CERTAIN›〕
- **DataMiningTaskBoard**：run 模拟执行（Random+硬编码关键词）应接真实引擎或明示占位；结果摘要含中文硬编码
- **ModelMonitorPanel**：`orgCond()` 返回 `" AND org_id = '" + orgId + "'"` 字符串拼接进 `.apply()`——orgId 源自 JWT 登录态非用户输入，实际注入面窄，但属裸拼 SQL 反模式〔ModelMonitorServiceImpl.java:34-44 ‹CERTAIN›〕；timeRange 亦直接内插（int 型，风险同上）；timeRange≤0 无校验；成本估算 ¥5/百万 token 为硬编码假汇率；无 keyword 时 distribution 无 LIMIT（全模型分布内存展开）
- **ChatSessionFinder**：无产生端——问答明细页面在当前代码下恒空；需接通 app-center 写入（app_conversation_message 亦无反馈外泄通道）或明确 chat_session 为废弃表

## 5. Parent Trace

| 模块/缝 | Spec REQ |
|---|---|
| DashboardAggregator | REQ-H01~H06（H06=三口径）、REQ-K04（同口径复用） |
| AssetAnalyticsAggregator | REQ-K01~K04 |
| AuditLogPipeline（缝 1） | REQ-L01、L04（写侧管线）、L05；缝 1 稳定侧=L04 |
| UnifiedLogQueryEngine（缝 2） | REQ-L03、L05 |
| LoginLogLedger | REQ-L02、L05 |
| FeedbackLedger | REQ-F01~F05 |
| ChatSessionFinder（缝 2） | REQ-C01、C02 |
| DataMiningTaskBoard | REQ-D01~D03（D04=ruleType 自描述字段） |
| ModelMonitorPanel（缝 2） | REQ-M01、M02 |
| 缝 2（跨域只读消费） | REQ-H02~H05、REQ-K01~K03、REQ-L03（kb_log 源）、REQ-C01、REQ-M01 的数据合法性 |
| AssetAnalyticsAggregator | REQ-K03（2026-09-24 review 补锚：热门文档排行 TOP 5 按浏览量降序（仅 completed 未删除文档），附所属知识库名与名次） |
| DataMiningTaskBoard | REQ-D02（2026-09-24 review 补锚：数据挖掘任务的创建与删除） |
| DataMiningTaskBoard | REQ-D03（2026-09-24 review 补锚：手动触发指定任务执行，返回执行结果或任务状态） |
| DataMiningTaskBoard | REQ-D04（2026-09-24 review 补锚：任务类型/参数由任务记录自描述（类型字段+JSON 参数），不同挖掘目标走同一套 CRUD/触发流程） |
| ModelMonitorPanel（缝 2） | REQ-M02（2026-09-24 review 补锚：模型监控为只读聚合，不写入、不修改调用日志） |

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
