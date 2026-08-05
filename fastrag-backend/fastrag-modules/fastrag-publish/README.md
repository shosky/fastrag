# fastrag-publish

> 知识库发布管理 + 操作审计日志模块。

## 模块职责

- **知识库发布**：上线/下线/定时发布/版本回滚
- **操作审计日志**：通过 `@Loggable` 注解 + `KbLogAspect` 切面自动记录所有业务操作
- **事件驱动解耦**：发布 `SysAuditLogEvent` 由 operation 模块异步消费

## 对外 REST 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/kb/{kbId}/publish/online` | 上线知识库 |
| POST | `/api/kb/{kbId}/publish/offline` | 下线知识库 |
| POST | `/api/kb/{kbId}/publish/schedule` | 定时发布 |
| GET | `/api/kb/{kbId}/publish/history` | 发布历史 |
| GET | `/api/kb/{kbId}/publish/current` | 当前状态 |
| POST | `/api/kb/{kbId}/publish/rollback/{version}` | 回滚到指定版本 |

## 模块依赖

| 方向 | 模块 | 说明 |
|------|------|------|
| 依赖 | fastrag-common | `BaseEntity`, `ApiResponse`, `@Loggable`, `SysAuditLogEvent`, `ActionType` |
| 依赖 | fastrag-security | `SecurityUtil` 获取当前用户 |
| 依赖 | fastrag-infra | — |
| 被依赖 | knowledge, retrieval, graph-eval, operation, bootstrap（5 个模块） |

## 关键类说明

| 类 | 说明 |
|----|------|
| `PublishController` | `/api/kb/{kbId}/publish` 发布端点 |
| `PublishService` | 发布业务逻辑：上线/下线/定时/回滚/版本管理 |
| `LogService` | 审计日志写入服务 |
| `KbLogAspect` | `@Aspect` 拦截 `@Loggable` 方法，解析 SpEL，发布 `SysAuditLogEvent` |
| `KbPublishHistory` | 记录发布操作的审批历史和版本信息 |
