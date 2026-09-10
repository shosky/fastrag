# fastrag-infra

> 基础设施层 — 封装所有外部中间件交互：MySQL（MyBatis-Plus）、Milvus 向量数据库、知识图谱存储（Neo4j/MySQL 双实现）、文件存储（本地文件系统）、RabbitMQ 消息队列、邮件发送。

## 模块职责

- **MyBatis-Plus 配置** — 分页插件、`createdAt`/`updatedAt` 自动填充
- **向量存储** — Milvus Collection 管理、向量插入、相似度搜索、按文件/chunk 删除
- **图存储** — `GraphStore` 抽象接口，提供 Neo4j（Cypher）和 MySQL（JDBC）两种实现
- **文件存储** — 本地文件系统上传/下载/删除/复制（类名 MinioService 但实际为本地存储）
- **消息队列** — RabbitMQ 队列/交换机声明、消息发布（带同步降级）
- **邮件发送** — 异步发送注册/重置密码验证码

## 对外 REST 端点

无。本模块不暴露任何 HTTP 端点。

## 模块依赖

| 方向 | 模块 | 说明 |
|------|------|------|
| 依赖 | fastrag-common | 使用 `IngestionHandler`/`GraphBuildHandler` 接口 |
| 被依赖 | iam, knowledge, retrieval, graph-eval, application, tools, agent, platform, operation, bootstrap（10 个模块） |

## 关键类说明

### 配置

| 类 | 说明 |
|----|------|
| `MyBatisPlusConfig` | 注册 MySQL 分页拦截器 + MetaObjectHandler（自动填充 createdAt/updatedAt） |
| `RabbitMQConfig` | 声明 `fastrag.direct` 交换机、`fastrag.ingestion.queue`（routingKey: `ingestion`）、`fastrag.graph-build.queue`（routingKey: `graph-build`）、Jackson JSON 消息转换器 |
| `Neo4jConfig` | 无条件创建 Neo4j Driver Bean，启动时校验连通性（不可达 fail-fast，ADR-0002） |

### 向量存储

| 类 | 说明 |
|----|------|
| `MilvusService` | Milvus 客户端。`createCollection(name, dim)` 自动建索引（IVF_FLAT + COSINE）；`insert()`/`search()`/`deleteByFileId()`/`deleteById()`/`deleteByIds()`。启动时检测连接可用性，不可用时优雅降级（所有操作变为空操作） |

### 图存储

| 类 | 说明 |
|----|------|
| `GraphStore` | 图存储抽象接口：`createEntity`/`createRelation`/`getGraphData`/`expandGraph`/`searchNodes`/`clearGraph`/`deleteFileGraph` + mention 追踪 + 实体向量检索 + 统计 |
| `Neo4jGraphStore` | 唯一实现（ADR-0002，MySQL 降级实现已移除）。Cypher + MERGE 去重，kbId 属性隔离，Neo4j 5.11+ 原生向量索引做实体语义检索。构建写方法失败向上抛出（消费端标记 chunk 待重试），查询/维护方法失败返回空结果 |

### 文件存储

| 类 | 说明 |
|----|------|
| `MinioService` | 本地文件系统存储。`upload()`/`download()`/`delete()`/`copy()`。配置项 `storage.local.path`（默认 `./uploads`） |

### 消息队列

| 类 | 说明 |
|----|------|
| `MessagePublisher` | 发布文档摄入和图谱构建消息。先检测 RabbitMQ 连接，不可用时同步降级直接调用 Handler |

### 邮件

| 类 | 说明 |
|----|------|
| `EmailService` | `@Async` 异步发送注册/重置密码验证码邮件 |

## 配置项

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `storage.local.path` | `./uploads` | 本地文件存储根路径 |
| `milvus.host` | `127.0.0.1` | Milvus 地址 |
| `milvus.port` | `19530` | Milvus 端口 |
| `neo4j.uri` | `bolt://localhost:7687` | Neo4j 连接 URI（必需依赖，启动时校验连通性） |
| `neo4j.user` | `neo4j` | Neo4j 用户名 |
| `neo4j.password` | (空) | Neo4j 密码 |
| `spring.mail.username` | (空) | 邮件发送地址 |
