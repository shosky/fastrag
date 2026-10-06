# 运营分析与系统监控（operation-analytics）— 实现文档

> 记录既有代码如何实现 contract/interfaces.md `## operation-analytics` 四契约；模块划分镜像 design/operation-analytics.md §1。LOC 合计约 2.1k（38 文件）。

## 1. Code File Map

```
fastrag-modules/fastrag-operation/src/main/java/com/fastrag/module/operation/
├── controller/
│   ├── HomeController.java          # 工作台四区聚合（逻辑在控制器内，无 service）
│   ├── AnalyticsController.java     # 知识资产四指标+双排行（同上）
│   ├── AuditLogController.java      # 系统审计日志 list（21 行）
│   ├── SysLoginLogController.java   # 登录日志 list（41 行）
│   ├── UnifiedLogController.java    # 统一日志分页查询入口
│   ├── ChatSessionController.java   # 问答明细分页（控制器直用 Mapper）
│   ├── FeedbackController.java      # 反馈 8 端点（CRUD+statistics+overview+reply）
│   ├── DataMiningController.java    # 数据挖掘 5 端点（CRUD+run）
│   └── ModelMonitorController.java  # 模型监控 overview
├── service/                         # 6 接口：AuditLog/SysLoginLog/UnifiedLog/Feedback/DataMining/ModelMonitor
│   └── impl/                        # 6 实现（FeedbackServiceImpl 279 行为最大）
├── listener/SysAuditLogEventListener.java  # 缝 1 消费侧：事件→sys_audit_log
├── mapper/                          # 5 个 BaseMapper（无自定义 SQL/XML）
├── entity/                          # ChatSession/DataMiningTask/SysAuditLog/SysLoginLog/UserFeedback
└── model/                           # HomeData/AnalyticsData/ModelMonitorData/UnifiedLogQuery/UnifiedLogDTO
```
跨模块协作文件（非本模块，实现引用）：`common/annotation/Loggable.java`、`common/event/SysAuditLogEvent.java`、`publish/aspect/KbLogAspect.java`（缝 1 发布侧）、`platform/entity/ModelCallLog.java`、`security/service/KbAccessChecker.java`。

## 2. Interface Implementations

### DashboardAggregator.getHomeData / getKbAnalytics（from contract: operation-analytics.DashboardAggregator）

**Logic Flow（getHomeData）:**
1. `accessibleKbIds()`：user==null → 空列表；userId 以 `api-token:` 开头 → null（不过滤）；否则 `accessChecker.getAccessibleKbIds(userId, orgId)`〔HomeController.java:150-155〕
2. IF accessible 非 null 且 size=0 → RETURN 空 HomeData（四区空集短路）〔:68-75〕；与 getKbAnalytics 步骤 1 的 `__none__` 哨兵机制不同——此处空集短路直接返回空结果，彼处哨兵值规避空 IN 的 SQL，两机制不同。
3. recommendKBs：可见库 IN 过滤，createdAt 倒序 `LIMIT 5`，map 为 KbItem（tags 经手写 JSON 数组解析，失败容错空表）〔:77-90,164-181〕
4. hotDocs：`deleted_at IS NULL AND status='completed'`（+IN 过滤），viewCount 倒序 `LIMIT 10`，null viewCount→0、null updatedAt→""〔:92-106〕
5. hotApps：`canUseApp()`（`*` 通配 ∨ api-token ∨ `app:use`）为假 → 空表；真 → App createdAt 倒序 LIMIT 5〔:108-119,157-162〕
6. recentActivities：KbLog IN 过滤，timestamp 倒序 LIMIT 10〔:121-134〕
7. RETURN ApiResponse.success(HomeData)

**Logic Flow（getKbAnalytics）:**
1. `accessibleKbIds()` 变体：空结果以哨兵 `__none__` 返回（IN 空集合语义规避）〔AnalyticsController.java:55-61〕
2. 四次 COUNT：总库数／总文档（未删）／活跃（completed）／有分片完成文档（chunkCount>0）；引用率=有分片÷活跃，`%.1f%%`，活跃 0 → "0%"〔:67-95〕
3. hotKBs：kb_file GROUP BY kb_id + `last("ORDER BY COUNT(*) DESC LIMIT 5")`（规避 only_full_group_by），再**循环 5 次**逐库 COUNT 文档数，KB 名批量回填〔:116-149〕
4. hotDocs：completed 文档 viewCount 倒序 LIMIT 5，KB 名批量回填，rank 循环补位〔:151-181〕
5. RETURN ApiResponse.success(AnalyticsData)

**Module Interactions:** Calls：KnowledgeBaseMapper、KbFileMapper、AppMapper、KbLogMapper（四域只读直查）、KbAccessChecker、SecurityUtil。Called by：HTTP GET /api/home、/api/analytics/kb。Shared state：只读 kb/kb_file/app/kb_log。
**Decisions & Deviations:** ① 契约的行为语义完整实现，但**物理位置在控制器**（无 service 层）——与 design Module Map 的"Aggregator"命名存在实现差距；② 手写 parseTags 替代 Jackson（容错但非标）；③ hotKBs 的 N+1（5 次 COUNT）为已注释的接受取舍〔:136〕。
**Parent Trace:** HomeController/AnalyticsController → DashboardAggregator/AssetAnalyticsAggregator → REQ-H01~H06、K01~K04。

### AuditLogPipeline.onSysAuditLogEvent / list（from contract: operation-analytics.AuditLogPipeline）

**Logic Flow（监听）:**
1. 收 SysAuditLogEvent → 构造 SysAuditLog 逐字段拷贝（action 取枚举 name()）〔SysAuditLogEventListener.java:35-43〕
2. timestamp = `LocalDateTime.now()`（服务器落库时刻，非事件携带）〔:44〕
3. insert；IF 异常 → warn 日志吞掉（旁路不阻断）〔:45-48〕

**Logic Flow（list）:** module 非空 eq → timestamp 倒序 → `last("LIMIT " + limit)`（null → LIMIT 100）→ selectList〔AuditLogServiceImpl.java:35-43〕。addLog(9 参) 同构 insert+吞异常，但**全仓零调用方（死代码）**〔grep 实测〕。

**Module Interactions:** Calls：SysAuditLogMapper。Called by：Spring 事件总线（发布方=KbLogAspect，publish 域）；AuditLogController。Shared state：sys_audit_log 表。
**Decisions & Deviations:** ① 契约「异步写入」意图未实现——`@EventListener` 无 `@Async`（@EnableAsync 已开），实际同步执行于发布线程〔2026-09-24 深证升级 CERTAIN：SysAuditLogEventListener.java:32 + Spring 语义〕；② LIMIT 字符串拼接（Integer 绑定兜底不可注入，但违反参数化惯例）；③ addLog 死代码处置（2026-09-24 裁决）：**保留不删**——与监听器构成「事件/直写」双入口对称面（同 9 参写协议、同吞异常语义），删除将失去唯一同步直写入口；建议后续清理 PR 一并加 @Deprecated 标记；现状差距：全仓无调用方（死代码，见 interfaces.md 本节登记）（代裁，用户可推翻）；④ 日志表 append-only 无归档清理，保留期已裁决 180 天（spec §5，2026-09-24）——现状差距：无任何清理任务，登记债务。
**Parent Trace:** SysAuditLogEventListener/AuditLogServiceImpl → AuditLogPipeline（缝 1 消费侧）→ REQ-L01、L04、L05。

### UnifiedLogQueryEngine.pageQuery（from contract: operation-analytics.UnifiedLogQueryEngine）

**Logic Flow:**
1. IF category==null → RETURN `Page.of(0, pageSize)` 空页〔UnifiedLogServiceImpl.java:37-39〕
2. switch(category)：audit / login / operation；default → 空页（**update 分支未实现**）〔:40-45〕
3. audit 分支：keyword→`like(detail).or().like(target)`、module eq、operator→username eq、起止时间 ge/le、timestamp 倒序，selectPage 后 `convert(toUnifiedDTO)`〔:51-74〕
4. login 分支：keyword→username like、operator→username eq、loginTime 倒序（**无时间/模块条件**）〔:79-92〕
5. operation 分支：同 audit 但 module→kb_log.category、operator→operator、多 kbId eq、timestamp 倒序〔:97-123〕
6. 统一映射三重载：login→module 恒"登录认证"/action=status/detail=failReason/timestamp=loginTime；operation→module=category〔:127-168〕
7. RETURN IPage<UnifiedLogDTO>（⚠ 非 PageResult）

**Module Interactions:** Calls：SysAuditLogMapper、SysLoginLogMapper、KbLogMapper。Called by：UnifiedLogController（GET /api/logs）。Shared state：三日志表只读。
**Decisions & Deviations:** ① 返回 IPage 违反 PageResult 规范——契约如实登记为违规点，未修（本任务禁改码）；② keyword OR 与后续 AND 的优先级缺陷（未用 and(lambda) 嵌套），tdd 以现状锁定用例固化；③ category=update 的 javadoc 承诺未兑现。
**Parent Trace:** UnifiedLogServiceImpl → UnifiedLogQueryEngine（缝 2）→ REQ-L03、L05。

### FeedbackLedger 全接口（from contract: operation-analytics.FeedbackLedger）

**Logic Flow:**
1. page：三条件 eq + applyOrgScope（API Token 外恒 `org_id=当前组织`）+ createdAt 倒序 selectPage → `PageResult.of(records,total,page,pageSize)`〔FeedbackServiceImpl.java:59-68〕
2. list：kbId 过滤 + orgScope + 倒序 `LIMIT 200`〔:69-74〕
3. create：当前用户非空 → orgId 强制覆写（防伪造）；status 缺省 pending；feedback 缺省 like；insert〔:75-81〕
4. update/delete/reply：`requireOwnOrg(selectById(id))`（null→notFound"反馈不存在"；跨组织→forbidden"无权操作该反馈"）〔:42-49〕
5. update：fb.setId(id) → updateById（MP 非空字段部分更新）→ 回读返回〔:82-85〕
6. reply：置 reply/processedBy/processedAt=now/**status=resolved** → updateById → 返回（`if(fb==null) return null` 不可达——requireOwnOrg 已抛）〔:90-96〕
7. statistics：单行 selectMaps 聚合（COUNT/SUM CASE）→ 四比率（`Math.round(x*10000.0/total)/100.0` 保 2 位）+ byType/byStatus 两组 GROUP BY〔:97-138〕
8. getOverview 四段：① 指标卡——chat_session COUNT（kbId + ACL 可见库 IN，空则 `__none__`）+ 反馈聚合（pending=NOT IN resolved,ignored）+ 三比率（feedbackRate=反馈÷问答）；② questionCategories——category 分组+占比 int%；③ hotKeywords——query 列 `LIMIT 2000` 内存正则分词（`[^\u4e00-\u9fa5a-zA-Z0-9]+` 切分、长度≥2）词频 top20；④ appSatisfactionRanking——app_id 分组（cnt/likes/avgScore）+ selectBatchIds 批量回填应用名、rank 递增〔:140-278〕
9. 错误恢复：仅 E_FEEDBACK_NOT_FOUND / E_FEEDBACK_FORBIDDEN 两业务错误（HTTP 200 + body.code）

**Module Interactions:** Calls：UserFeedbackMapper、ChatSessionMapper（COUNT 只读）、AppMapper（名称回填）、KbAccessChecker、SecurityUtil。Called by：FeedbackController 8 端点。Shared state：user_feedback（读写）、chat_session（只读）、app（只读）。
**Decisions & Deviations:** ① 分页正确使用 PageResult（合规样板）；② 聚合全部下沉 SQL（代码注释明示"避免全量加载"），唯词云仍 2000 行内存分词——契约按实现如实登记；③ 反馈类型缺省与 DDL 默认值不一致（like vs none，见 data.md）。
**Parent Trace:** FeedbackServiceImpl → FeedbackLedger → REQ-F01~F05。

### ModelMonitorPanel.getOverview（from contract: 端点排除——现状记录）

**Logic Flow:**
1. `orgCond()`：API Token → ""；有 orgId → `" AND org_id='" + orgId + "'"`（**SQL 裸拼**）；无 orgId → " AND 1=0"〔ModelMonitorServiceImpl.java:34-44〕
2. 双时窗条件内插：`timestamp >= DATE_SUB(NOW(), INTERVAL {timeRange} DAY)` 与前一时窗（2×timeRange 至 timeRange）〔:49-50〕
3. 四指标：当期/前期的 COUNT、SUM(tokens)（COALESCE 0）、AVG(duration)、失败 COUNT（status='failed'）；`calcChange` 环比——prev=0 时 "新增"/"0%"，否则 `±x.x%`；trend 按 smallerIsBetter 翻转（耗时/失败 down=改善）〔:52-92,196-203〕
4. distribution：GROUP BY model_id 按 tokens 倒序（**无 LIMIT**），占比 1 位小数〔:95-114〕
5. topApps：GROUP BY caller 按 tokens 倒序 LIMIT 5；cost = tokens×5/1_000_000 元（**硬编码假汇率**），0 token 显示 "—"〔:117-143〕
6. stats：GROUP BY model_id（calls/fails/tokens）+ keyword `like("model_id")` + `selectMapsPage` 分页（**分页正确用 PageResult**）〔:146-177〕
7. RETURN ModelMonitorData{metrics, distribution, topApps, stats}

**Module Interactions:** Calls：ModelCallLogMapper（platform 域只读直查）、SecurityUtil。Called by：ModelMonitorController（GET /api/monitor/model/overview）。Shared state：model_call_log 只读——**该表无 Java 写入方且 DDL 缺陷（platform-config 节 REQ-C03 死表登记），面板当前恒空**。
**Decisions & Deviations:** ① 未立契约（design §2 端点排除说明）：单端点读聚合+两处实现缺陷，固化为契约前应先修；② org 条件裸拼 SQL 源于 QueryWrapper 跨 `.apply()` 组合的偷懒，orgId 来自 JWT 登录态故实际注入面窄；③ timeRange 无下界校验（0/负值生成非法 INTERVAL SQL）。
**Parent Trace:** ModelMonitorServiceImpl → ModelMonitorPanel（缝 2）→ REQ-M01、M02。

### 其余浅实现（无契约，登记要点）

- **ChatSessionFinder**：keyword like(query) + userId like(userId) + createdAt 倒序 selectPage → PageResult；控制器直用 Mapper；无归属校验〔ChatSessionController.java:36-51〕。数据面：chat_session 全仓无写入方——页面恒空〔grep 实测〕。
- **DataMiningTaskBoard**：create 双缺省（enabled/keyword）；run = `lastRunAt=now` + 随机 matchedCount(10~109) + 硬编码 topKeywords ["退款","物流","质量"] + runStatus=success 写入 result_summary；**不读取 rule_config、无真实挖掘**；序列化失败兜底 `{"error":"serialization failed"}`〔DataMiningServiceImpl.java:19-41〕。
- **LoginLogLedger**：addLoginLog 八参透传 + status 缺省 success + 吞异常；iam.AuthController 六调用点（login/logout/register 等）；list 同 AuditLog 模式（LIMIT 拼接同款）〔SysLoginLogServiceImpl.java:36-67〕。

## 3. Testing

现有覆盖：**零**（fastrag-operation 无 src/test 目录，conventions.md §2 六个无测试模块之一）。tdd/operation-analytics.md 十个切片全部为待实施；其中 S1（可见口径 HITL 确认）与三处「现状锁定」用例（UnifiedLog 的 update 分支/keyword OR 缺陷/LIMIT 200、DataMining 模拟 run）是补测时的第一优先级——它们把本链登记的缺陷固化为可检测回归。
