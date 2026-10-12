# 调研任务：WeKnora 整体架构与技术栈（维度 1/5）

## 背景
我们在为 FastRAG（Java Spring Boot 多模块 RAG 平台，15 Maven 模块，Milvus/MinIO/Neo4j/RabbitMQ/MySQL，统一 ApiResponse/PageResult，业务错误=HTTP 200+body.code，SchemaInitializer 幂等 DDL 而非 Flyway，Redis 无封装各模块直连）寻找对标项目 WeKnora 的实现优点。本维度只管**整体架构与技术栈**，解析/检索/Agent/工程化由其他任务负责，不要展开。

## 调研对象（只读，勿改任何文件）
- 根目录：`D:\Workspace\python\github\WeKnora`
- 重点：`go.mod`、`cmd/`、`internal/`（container/config/event/middleware/router/handler/application/models/database/errors/stream/runtime/datasource/filetransport/textconv 等）、`migrations/`、`config/`、`deploy/`、`docker/`、`docker-compose.yml`、`docker-compose.dev.yml`、`Makefile`、`client/`、`cli/`、`mcp-server/`、`docreader/`（只看进程边界与 proto，不管解析细节）

## 必答问题
1. Go 进程的分层结构：router→handler→application(service)→repository/models→infrastructure 如何组织？依赖方向是否清晰？
2. `internal/container/` 是什么 DI/组装机制？启动流程（cmd 入口）如何串起来？
3. 配置管理用什么（viper? env?），配置结构如何分层？
4. 数据库是什么（PostgreSQL? MySQL?），向量用什么存（pgvector? 独立向量库?），`migrations/` 的迁移管理机制与纪律如何？
5. `internal/event/` 事件系统：进程内事件总线还是 MQ？异步任务（文档处理等）用什么承载？
6. `internal/stream/` 流式基础设施（SSE/WebSocket）如何抽象？
7. 部署形态：docker-compose 服务拓扑、profile 划分、helm、可插拔组件（向量库/存储/模型可替换性如何落地）？
8. docreader 为何独立成 Python 进程？与 Go 主进程的接口（proto/gRPC?）与部署关系？
9. `client/` 与 `cli/` 提供了什么（SDK? 多语言?）？
10. 错误处理与统一响应约定（errors/ 包）？

## 输出要求（中文 markdown）
- **模块地图**：目录→职责一览表
- **核心机制**：每条带 `文件路径:行号` 证据，关键代码可摘 3-5 行
- **优点清单**：按"机制→证据→为什么好"组织，每条注明对 FastRAG 的借鉴意义（FastRAG 现状锚点见背景）
- **弱点/风险**：客观列出（若有）
- 禁止臆测：读不到的就说读不到；结论必须来自你实际读到的代码
