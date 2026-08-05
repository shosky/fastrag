# fastrag-common

> 基础公共模块 — 提供全项目共享的基类、枚举、异常、注解、工具和跨模块服务契约。

## 模块职责

- 定义所有实体基类 `BaseEntity`（id, createdAt, updatedAt）
- 提供统一 API 响应封装 `ApiResponse<T>` 和分页模型 `PageResult<T>`
- 定义全局业务异常 `BusinessException` 及 `GlobalExceptionHandler` 统一异常处理
- 声明共享枚举：`ActionType`、`NodeType`、`PublishStatus`、`ToolType` 等 11 个
- 提供 `@Loggable` 注解用于知识库操作审计日志
- 定义跨模块服务契约接口（`KbOperationService`、`IngestionHandler`、`GraphBuildHandler`）
- 提供工具类：ID 生成、IP 提取、JSON Schema 校验、模板引擎、User-Agent 解析

## 对外 REST 端点

无。本模块不暴露任何 HTTP 端点。

## 模块依赖

| 方向 | 模块 | 说明 |
|------|------|------|
| 依赖 | 无 | 零内部依赖，仅依赖 Spring Boot / Lombok / Hutool / MapStruct |
| 被依赖 | 全部 14 个模块 | 所有模块都依赖 common |

## 关键类说明

### 基础设施

| 类 | 说明 |
|----|------|
| `BaseEntity` | 抽象基类，提供 `id`(String)、`createdAt`、`updatedAt` 三个公共字段 |
| `ApiResponse<T>` | 统一 API 响应包装，含 `code`、`data`、`message`，提供 `success()`/`error()`/`badRequest()` 等静态工厂 |
| `PageParams` | 分页请求参数，`page`(默认1)、`pageSize`(默认20)、`keyword` |
| `PageResult<T>` | 分页响应，含 `list`、`total`、`page`、`pageSize` |

### 异常处理

| 类 | 说明 |
|----|------|
| `BusinessException` | 业务异常，携带 `code` 和 `message`，提供 `badRequest()`/`unauthorized()`/`forbidden()`/`notFound()`/`serverError()` 静态工厂 |
| `GlobalExceptionHandler` | `@RestControllerAdvice`，统一处理 `BusinessException`、校验异常、通用 `Exception` |

### 枚举（enums/）

| 枚举 | 值 | 说明 |
|------|-----|------|
| `ActionType` | `kb_created`, `file_uploaded`, `chunk_created`, `graph_built`, `publish_online` 等 23 种 | 知识库操作动作类型 |
| `NodeType` | `start`, `llm`, `retrieval`, `rerank`, `template`, `code`, `condition`, `end` | 工作流节点类型 |
| `PublishStatus` | `online`, `offline`, `scheduled` | 知识库发布状态 |
| `ToolType` | `builtin`, `api`, `mcp`, `skill` | 工具类型 |
| `ModelPurpose` | `chat`, `embedding`, `rerank`, `vlm`, `asr`, `ocr` | AI 模型用途 |
| `ProcessStatus` | `pending`, `processing`, `completed`, `failed` | 异步处理状态 |
| `LogCategory` | `operation`, `login`, `system` | 日志分类 |
| `KBRole` | `owner`, `editor`, `viewer` | 知识库角色 |
| `FileCategory` | `document`, `image`, `audio`, `video` | 文件类别 |
| `ReviewStatus` | `pending`, `approved`, `rejected` | 审核状态 |
| `UpdateType` | `full`, `incremental` | 更新类型 |

### 注解与事件

| 类 | 说明 |
|----|------|
| `@Loggable` | 标记需要审计日志的 Controller 方法，支持 SpEL 表达式 |
| `SysAuditLogEvent` | Spring ApplicationEvent，由切面发布，由 operation 模块监听写入审计日志 |

### 跨模块契约

| 接口 | 说明 |
|------|------|
| `KbOperationService` | 定义 `listKnowledgeBases()`/`queryKnowledgeBase()` 契约，避免 tools 直接依赖 knowledge |
| `IngestionHandler` | 文档摄入处理接口，接受 `Map<String, Object>` 消息 |
| `GraphBuildHandler` | 知识图谱构建处理接口，接受 `Map<String, Object>` 消息 |