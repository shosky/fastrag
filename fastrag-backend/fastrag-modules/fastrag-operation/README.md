# fastrag-operation -- 运营监控与审计日志模块

运营监控模块，负责系统审计日志、登录日志、用户反馈、数据分析、模型监控、会话管理及数据挖掘等运营支撑能力。

## 模块职责

- 系统操作审计日志的记录与查询（SysAuditLog），通过事件监听机制自动采集
- 用户登录日志的记录与分析（SysLoginLog）
- 用户反馈的收集与处理（UserFeedback）
- 系统运营数据分析，提供首页仪表盘数据（AnalyticsData / HomeData）
- 模型运行监控，包括调用量、延迟、错误率等指标（ModelMonitorData）
- 会话管理，查询与分析用户对话会话（ChatSession）
- 统一日志查询，跨审计日志、登录日志、模型调用日志的统一检索（UnifiedLog）
- 数据挖掘任务管理（DataMiningTask）

## 对外 REST 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/audit-logs` | 审计日志分页查询 |
| GET  | `/api/audit-logs/{id}` | 审计日志详情 |
| GET  | `/api/login-logs` | 登录日志分页查询 |
| GET  | `/api/login-logs/{id}` | 登录日志详情 |
| GET  | `/api/feedback` | 用户反馈列表 |
| POST | `/api/feedback` | 提交用户反馈 |
| PUT  | `/api/feedback/{id}` | 更新反馈处理状态 |
| GET  | `/api/analytics/dashboard` | 仪表盘数据概览 |
| GET  | `/api/analytics/trend` | 运营趋势数据 |
| GET  | `/api/analytics/stats` | 统计数据汇总 |
| GET  | `/api/home/data` | 首页数据 |
| GET  | `/api/model-monitor/metrics` | 模型监控指标 |
| GET  | `/api/model-monitor/{id}` | 单个模型监控详情 |
| GET  | `/api/chat-sessions` | 会话列表 |
| GET  | `/api/chat-sessions/{id}` | 会话详情 |
| GET  | `/api/unified-logs` | 统一日志查询 |
| POST | `/api/data-mining/tasks` | 创建数据挖掘任务 |
| GET  | `/api/data-mining/tasks/{id}` | 查询挖掘任务状态 |

## 模块依赖

| 方向 | 模块 | 说明 |
|------|------|------|
| 依赖 | fastrag-common | ApiResponse、@Loggable 注解、通用工具类 |
| 依赖 | fastrag-security | 认证鉴权、用户上下文 |
| 依赖 | fastrag-infra | 数据库访问、缓存、消息队列 |
| 依赖 | fastrag-knowledge | 知识库统计数据 |
| 依赖 | fastrag-application | 应用会话统计数据 |
| 依赖 | fastrag-publish | 发布统计数据 |
| 依赖 | fastrag-platform | 模型调用日志、审计事件定义 |

## 关键类说明

### 控制器

| 类名 | 路径前缀 | 职责 |
|------|----------|------|
| AuditLogController | `/api/audit-logs` | 审计日志查询 |
| SysLoginLogController | `/api/login-logs` | 登录日志查询 |
| FeedbackController | `/api/feedback` | 用户反馈 CRUD |
| AnalyticsController | `/api/analytics` | 运营数据分析 |
| HomeController | `/api/home` | 首页仪表盘 |
| ModelMonitorController | `/api/model-monitor` | 模型运行监控 |
| ChatSessionController | `/api/chat-sessions` | 会话管理 |
| UnifiedLogController | `/api/unified-logs` | 统一日志查询 |
| DataMiningController | `/api/data-mining` | 数据挖掘任务管理 |

### 实体

| 类名 | 说明 |
|------|------|
| SysAuditLog | 系统审计日志 |
| SysLoginLog | 用户登录日志 |
| UserFeedback | 用户反馈 |
| ChatSession | 对话会话 |
| DataMiningTask | 数据挖掘任务 |

### 事件监听器

| 类名 | 说明 |
|------|------|
| SysAuditLogEventListener | 监听 SysAuditLogEvent，自动写入审计日志 |

### 服务层

| 接口 | 实现类 | 职责 |
|------|--------|------|
| AuditLogService | AuditLogServiceImpl | 审计日志查询 |
| SysLoginLogService | SysLoginLogServiceImpl | 登录日志查询 |
| FeedbackService | FeedbackServiceImpl | 用户反馈管理 |
| ModelMonitorService | ModelMonitorServiceImpl | 模型监控指标 |
| DataMiningService | DataMiningServiceImpl | 数据挖掘任务 |
| UnifiedLogService | UnifiedLogServiceImpl | 统一日志跨表查询 |

### DTO

| 类名 | 说明 |
|------|------|
| AnalyticsData | 分析数据聚合 |
| HomeData | 首页仪表盘数据 |
| ModelMonitorData | 模型监控指标 |
| UnifiedLogDTO | 统一日志传输对象 |
| UnifiedLogQuery | 统一日志查询条件 |
