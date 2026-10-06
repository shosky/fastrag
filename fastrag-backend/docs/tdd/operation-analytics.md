# 运营分析与系统监控（operation-analytics）— 测试设计

> 契约来源：contract/interfaces.md `## operation-analytics` 四接口；数据契约 data.md `## operation-analytics`。
> ⚠ 现状：fastrag-operation 模块**无任何测试**（src/test 不存在，conventions.md §2 已登记）——本文件为从零起步的测试设计，非既有测试的描述。

## 1. 切片规划

| 切片 | 契约接口 | AFK/HITL | 说明 |
|---|---|---|---|
| S1（HITL 前置） | DashboardAggregator 可见口径三分支的**预期口径确认** | HITL | 「无可见库返回全空」「API Token 不过滤」是安全敏感断言，需产品确认后再自动化 |
| S2 | DashboardAggregator.getHomeData 四区聚合 | AFK | Mock 无需——仅依赖 MySQL 与 SecurityContext 测试装配 |
| S3 | DashboardAggregator.getKbAnalytics 指标与排行 | AFK | 引用率除零、TOP N 截断为重点 |
| S4 | AuditLogPipeline 事件落库与静默失败 | AFK | SysAuditLogEvent 为真缝（缝 1），事件对象可测 |
| S5 | UnifiedLogQueryEngine 三源路由与统一映射 | AFK | 含 category 未实现值空页、keyword OR 缺陷的**现状锁定用例** |
| S6 | FeedbackLedger 生命周期与组织隔离 | AFK | 状态机与 E_FEEDBACK_FORBIDDEN 为重点 |
| S7 | FeedbackLedger 统计聚合口径 | AFK | statistics/getOverview 的比率计算矩阵 |
| S8 | ModelMonitorPanel 聚合口径（现状锁定） | AFK | 环比/成本估算；死表前提下以 fixture 数据测 SQL 正确性 |
| S9 | 只读性回归（跨接口） | AFK | 三日志/会话/监控端点无写路径断言 |
| S10 | LoginLogLedger 写协议（iam 集成） | AFK | 跨模块：登录成功/失败→四值 status 落库 |

Mock 边界：仅允许 mock 时间（LocalDateTime 注入点不存在——以固定 fixture 数据 + 落库后回读断言代替）与安全上下文装配；禁 mock 内部模块。全切片不依赖 MinIO/Milvus/Neo4j/RabbitMQ（本特性纯 MySQL 聚合）。

## 2. 测试用例（逐接口）

### DashboardAggregator（contract: operation-analytics.DashboardAggregator）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 无可见知识库的普通用户请求首页四区全空且成功 | 登录用户 A，KbAccessChecker 返回空列表 | GET /api/home | code 成功；recommendKBs/hotDocs/hotApps/recentActivities 均为空数组 | REQ-H06 | SecurityContext 装配 |
| API Token 请求首页返回未被 ACL 过滤的全局数据 | userId 前缀 api-token: 的登录态；库 X 不在其组织 | GET /api/home | recommendKBs 含库 X；四区上限 5/10/5/10 | REQ-H06 | SecurityContext 装配 |
| 推荐知识库按创建时间倒序截断 5 个 | 7 个可见库，创建时间已知 | GET /api/home | recommendKBs.size()=5 且为最新 5 个，tags 数组正确解析 | REQ-H02 | 时间列 fixture |
| 热门文档排除未完成与已删除文档 | 库内：2 个 completed 高浏览 + 1 个 status=parsing + 1 个 deleted_at 非空 | GET /api/home | hotDocs 仅含 2 个 completed；viewCount null 归 0 | REQ-H03 | 无 |
| 无 app:use 权限用户热门应用区为空 | 用户仅有基础权限 | GET /api/home | hotApps=空 | REQ-H04 | SecurityContext 装配 |
| 最近动态仅含可见库的日志且倒序 | 库 A（可见）2 条 kb_log + 库 B（不可见）3 条 | GET /api/home | recentActivities.size()=2 且时间倒序 | REQ-H05 | 无 |
| 四指标在已知数据集上数值正确 | 3 库 4 文档（3 completed、2 有分片） | GET /api/analytics/kb | totalKBs=3/totalFiles=4/activeFiles=3/引用率=66.7% | REQ-K01 | 无 |
| 活跃文档为 0 时知识引用率显示 0% | 全部文档未完成 | GET /api/analytics/kb | displayValue="0%"，无除零异常 | REQ-K01 | 无 |
| 热门知识库按文档数排序且名次连续 | 库 A 3 文档、库 B 1 文档、库 C 0 文档 | GET /api/analytics/kb | hotKBs 首位为 A，rank 从 1 连续 | REQ-K02 | 无 |
| 不可见库的数据不出现在任何聚合区 | 库 B 对用户不可见且有高浏览文档 | GET /api/home + /api/analytics/kb | 两响应中均无库 B 的任何行 | REQ-H06、K04 + spec §5 组织隔离约束（该节未编号；性能预算为 GAP 待补） | SecurityContext 装配 |

### AuditLogPipeline（contract: operation-analytics.AuditLogPipeline）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 发布审计事件落库一行且字段全映射 | 构造 SysAuditLogEvent（9 字段） | 发布事件 | sys_audit_log 新增 1 行，userId/module/action=枚举 name()/status 逐字段相等；timestamp 非空 | REQ-L04 | 时间 |
| 事件落库失败不影响发布方 | 表置不可写（如列截断超长 detail） | 发布事件 | 无异常抛出；日志出现 warn；发布方（模拟切面）正常返回 | REQ-L04 | MySQL 故障注入 |
| 审计日志按 module 过滤且倒序 limit 生效 | 两 module 各 3 条 | GET /api/audit/system-log?module=知识库&limit=2 | 返回 2 条、全为该 module、时间倒序 | REQ-L01 | 无 |
| limit 缺省返回最近 100 条 | 插入 120 条 | GET /api/audit/system-log（无 limit） | 恰 100 条、为最新 100 条 | REQ-L01 | 无 |

### UnifiedLogQueryEngine（contract: operation-analytics.UnifiedLogQueryEngine）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| category 缺省返回空分页不报错 | 无条件 | pageQuery(category=null) | records 空、total=0、HTTP 200 | REQ-L03 | 无 |
| category=audit 路由审计源且映射正确 | 审计行 1 条（module=知识库） | pageQuery(category=audit) | DTO category=audit、module/action/timestamp 与源行相等 | REQ-L03 | 无 |
| category=login 的 module 恒为「登录认证」 | 登录失败行（status=failed） | pageQuery(category=login) | DTO module=登录认证、action=failed、detail=failReason、timestamp=loginTime | REQ-L03 | 无 |
| category=operation 支持 kbId 过滤 | 两库各有 kb_log 行 | pageQuery(category=operation, kbId=A) | 仅库 A 的行 | REQ-L03 | 无 |
| category=update 落空页（现状锁定） | kb_update_log 有数据 | pageQuery(category=update) | 空分页——**锁定未实现现状**，实现后此用例应翻转为断言有数据 | REQ-L03 | 无 |
| keyword 与 module 同用时审计分支过滤语义 | detail 命中/module 不符 与 module 符合/detail 不命中的组合行 | pageQuery(category=audit, keyword, module) | **锁定 OR 优先级缺陷现状**（见 design §4）——修复后断言 `(detail OR target) AND module` | REQ-L03 | 无 |
| 登录源时间范围条件不生效（现状锁定） | login 行 2 条不同时间 | pageQuery(category=login, startTime, endTime) | **锁定**：时间过滤在 login 分支未实现，2 条全返 | REQ-L03 | 无 |
| 三源结果均为时间倒序分页 | 每源插入跨页数据 | 逐 category 分页查询 | 每页内时间倒序、total 正确 | REQ-L03 | 无 |

### FeedbackLedger（contract: operation-analytics.FeedbackLedger）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 创建反馈缺省值与组织归属正确 | 用户（org=O1）POST 反馈无 status/feedback | create | 落库 status=pending、feedback=like、orgId=O1（入参伪造 orgId=O2 被覆写） | REQ-F04 | SecurityContext 装配 |
| 跨组织管理操作被拒 | O1 的反馈 + O2 的登录用户 | update/delete/reply | E_FEEDBACK_FORBIDDEN（"无权操作该反馈"），数据不变 | REQ-F04 | SecurityContext 装配 |
| 回复将任意前置状态置 resolved 并记录处理人 | pending 反馈 | reply(id,"已处理","op1") | status=resolved、reply/processedBy=op1/processedAt 非空；再次 reply 以 operator 缺省 system 覆盖 | REQ-F05 | 时间 |
| 反馈列表三条件过滤与分页默认值 | 混合 kbId/feedback/status 的 25 行 | page（无参）| page=1/pageSize=20/total=25；三条件各测穿透 | REQ-F01 | 无 |
| 全量列表上限 200（现状锁定） | 插入 210 行 | list | 恰 200 条 | REQ-F02 | 无 |
| 统计比率矩阵 | total=4（like 3、resolved 2、score: 4,2,0,5） | statistics | satisfactionRate=75.0/resolvedRate=50.0/avgScore=3.67；byType/byStatus 计数正确 | REQ-F03 | 无 |
| 空数据统计全零不除零 | 无反馈 | statistics + getOverview | 各比率=0.0，hotKeywords/questionCategories/appRanking 空数组 | REQ-F03 | 无 |
| 总览问答量按 ACL 库过滤 | chat_session：可见库 3 行+不可见库 5 行（fixture） | getOverview | totalQaCount=3；feedbackRate=反馈数÷3 | REQ-F03、C01 | SecurityContext 装配 |
| 词云只计长度≥2 的中英数字词 | query="！！好 用AB a 不好"（fixture ≤2000 行内） | getOverview | hotKeywords 含 "好"/"用ab"/"不好" 类 token，单字符被剔除，top20 截断 | REQ-F03 | 无 |

### ModelMonitorPanel（contract: 端点排除——现状锁定聚合口径）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 死表前提下查询不崩溃 | model_call_log 空表 | GET /api/monitor/model/overview | metrics 四卡值为 0/新增、distribution/topApps 空数组、stats 空分页、HTTP 200 | REQ-M01 | 无 |
| 四指标环比在 fixture 上数值正确 | 当期 200 调用 1000 token，前当期窗口 100 调用 | overview(timeRange=7) | callsChange=+100%、token/耗时/失败环比符号与 trend 语义正确（失败/耗时 down=改善） | REQ-M01 | 时间 |
| keyword 过滤与统计分页下沉 SQL | 两模型各 10 窗口数据 | overview(keyword=A, page=1, pageSize=1) | stats.total=模型分组数（仅含 like 命中的模型）、首页 1 行 | REQ-M01 | 无 |
| 组织隔离：跨组织数据不可见 | org O1 的调用日志 + O2 用户 | overview | 所有四段均不含 O1 数据；无组织用户得全零（1=0 条件） | REQ-M01 | SecurityContext 装配 |

### 只读性回归（跨接口切片）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 日志三端点无写方法 | 路由表 | 枚举 /api/audit/**、/api/logs 的允许方法 | 仅 GET；POST/PUT/DELETE 405 或被拒 | REQ-L05、C02、M02 | 无 |
| chat_session 无任何写入口 | 全模块源码 | 静态断言（架构测试）：ChatSessionMapper 无本模块外注入、无 insert 调用 | 断言通过 | REQ-C02 | 无 |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| 不可见库的数据不出现在任何聚合区 | DashboardAggregator | DashboardAggregator+AssetAnalyticsAggregator（缝 2） | REQ-H06/K04 |
| 事件落库失败不影响发布方 | AuditLogPipeline | AuditLogPipeline（缝 1） | REQ-L04 |
| category=login 的 module 恒为「登录认证」 | UnifiedLogQueryEngine | UnifiedLogQueryEngine | REQ-L03 |
| 跨组织管理操作被拒 | FeedbackLedger | FeedbackLedger | REQ-F04（E_FEEDBACK_FORBIDDEN） |
| 组织隔离：跨组织数据不可见 | ModelMonitorPanel（现状锁定） | ModelMonitorPanel（缝 2） | REQ-M01 |

## 4. 待实施说明

- 红绿循环**未执行**（本任务禁止写测试代码）；执行时从 Tracer Bullet 起步：S1 口径确认 → S2 首页聚合打穿 → 按 S3~S10 展开。
- 三处「现状锁定」用例（category=update 空页、keyword OR 缺陷、list LIMIT 200）断言的是**当前缺陷行为**，修复对应缺陷时必须同步翻转断言——这是把已知缺陷（research 信号：分页违规 + SQL 拼接）固化为可检测回归的手段。
- 测试装配建议：`@DataMybatisTest`/`@SpringBootTest` + H2 或 Testcontainers-MySQL（**2026-09-24 裁决**：主选 H2 + schema-h2.sql——本仓测试既有惯例即 H2（AppModuleValidationTest TC-SCHEMA-001 校验 schema-h2.sql 表存在性）‹CERTAIN›；但 schema-h2.sql 现缺 operation 域 5 表（sys_audit_log/sys_login_log/user_feedback/chat_session/data_mining_task，grep 实测零命中）需先增补；MySQL 方言特性（`ALTER ... ADD COLUMN IF NOT EXISTS`、存储过程）的用例以 Testcontainers-MySQL 兜底。现状差距：operation 模块 src/test 树不存在、schema-h2 缺 5 表，两方案均未落地——待建）；operation 模块需新建 src/test 树（当前不存在）。
- SecurityContext 装配为横切 fixture（SecurityUtil 静态读取），建议公共测试基座提供 setLoginUser/setApiToken/setAnonymous 三档。
