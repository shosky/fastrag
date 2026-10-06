# FastRAG 后端 — 项目总览与文档索引

> 由 idd-onboard 于 2026-09-21 初始化（范围 = `fastrag-backend/`，Role: backend）。
> 上位文档：[../CONTEXT.md](../CONTEXT.md)（后端术语表）、[../../docs/README.md](../../docs/README.md)（全仓总览，backend+frontend）。

## 1. 项目概览

FastRAG 后端：基于 RAG（Retrieval-Augmented Generation）架构的知识库管理平台服务端。支持文档/图片/音视频多模态文件的上传、解析、分片、向量化与图谱化，提供混合检索、知识图谱、基准评测、发布审核，并在此基础上构建应用（可视化工作流编排 + 对话式交互）与智能体运行时。前端实现见上位仓 `fastrag-frontend/`（Vue 3 + Element Plus），API 经 `/api` 代理到本服务 8081 端口。

## 2. 技术栈

- **语言/框架：** Java 17、Spring Boot 3.2.5、MyBatis-Plus 3.5.5（15 个 Maven 模块，系统 Maven 无 mvnw）
- **中间件：** MySQL 8 / Redis 7 / RabbitMQ 3 / Milvus v2.3.4 / Neo4j 5（Graph Store 唯一实现，必需依赖）/ MinIO / OnlyOffice Document Server 8.3.3
- **AI 网关：** Ollama 兼容 API（本地 `127.0.0.1:11434`，可切 SiliconFlow 云端）；模型地址/密钥查 `model` 表动态路由
- **API 契约：** springdoc-openapi 2.5.0 运行时生成（无静态契约文件、无手写注解）

## 3. Quick Start

```bash
# 后端（端口 8081；需先起中间件或用 docker-compose）
mvn clean package -f fastrag-backend/pom.xml
mvn spring-boot:run -f fastrag-backend/fastrag-bootstrap/pom.xml

# 后端测试（63 个 *Test.java 测试类；iam/publish/operation/common/security/infra/bootstrap 无测试）
mvn test -f fastrag-backend/pom.xml

# 基础设施全家桶（8 服务：app/mysql/redis/rabbitmq/milvus/neo4j/minio/onlyoffice）
docker compose up -d

# API 契约（启动后）
# http://localhost:8081/swagger-ui.html
```

- 表结构：`init-scripts/schema.sql` 全量 + `SchemaInitializer` 启动时幂等增补（替代 Flyway），两者须保持一致。
- 已知缺口：**[GAP]** 后端未配置 checkstyle/spotless 等静态检查，质量门仅 `mvn test`。

## 4. 文档索引

| 路径 | 内容 | 状态 |
|------|------|------|
| [../CONTEXT.md](../CONTEXT.md) | 后端领域术语表 + IDD 元数据（Role: backend） | CERTAIN |
| [feature-map.md](./feature-map.md) | 19 项特性聚类 + 证据包 + 链状态（**链进度唯一事实源**） | CERTAIN + INFERRED |
| [conventions.md](./conventions.md) | 后端开发约定（命名/模式/反模式/错误处理） | CERTAIN + INFERRED |
| [research/weknora-vs-fastrag.md](./research/weknora-vs-fastrag.md) | WeKnora v0.8.2 对比调研（5 维度源码级，new-conversation 特性输入） | 2026-09-25 |
| `spec/*.md` ×19 | 全部特性需求规格（REQ/验收清单/测试策略，CERTAIN 引证）；#19 new-conversation 链 spec+design 2026-09-25 | #1-18 链齐 2026-09-24 |
| `design/*.md` ×19 | 架构设计（Module Map/Seam/Parent Trace；含 ADR-OO1、ADR-R1、ADR-KG1 等；#19 含 ADR-0003） | #1-18 链齐 2026-09-24 |
| `contract/interfaces.md` + `contract/data.md` | 共享契约工件（19 特性章节；深模块接口/数据模式/错误模式/不变式/Parent Trace；#19 new-conversation 2026-09-25） | 持续生长 |
| `tdd/*.md` ×18 | 测试设计（切片/用例/追溯；**红绿循环未实施**） | 链齐 2026-09-24 |
| `implement/*.md` ×18 | 实现记录（伪代码级 Logic Flow/模块交互/偏离登记/债务登记） | 链齐 2026-09-24 |
| `tdd/new-conversation.md` | 特性 19 测试设计（8 切片 S0 HITL + 44 用例；**红绿循环未执行**） | 2026-09-25 |
| [sessions/](./sessions/) | 对话归档（根宪法 AGENT.md 要求） | 持续追加 |
| ~~../../docs/legacy/~~ | ⚠ 根 `docs/` 树已于 2026-09-21 被并行活动删除（legacy 99 篇等，恢复线索：IDEA Local History / 外部备份），死链待处置定论后改写 | 丢失 |

当前链状态：**18/18 = `implement`（文档链齐；tdd 为测试设计，红绿循环未执行）**。INFERRED/GAP 已于 2026-09-24 全量代裁闭环（230 条：R1 深证升级 96 / R2 现状裁定 40 / R3 实证消解 21 / R4 代裁决议 73——各标注「代裁，用户可推翻」）；待用户事项仅剩：根 docs/ 树处置、tdd 红绿循环是否执行、40+ 代码缺陷/债务处置（含裁决新证的 scheduledAt 字段失配缺陷）、文档入库提交。

## 5. 关键技能

本项目最常用：`/idd-autopilot`（下一步推荐）、`/idd-spec`（需求）、`/idd-diagnose`（调试）、`/idd-review`（一致性抽查）；完整清单见根 [../AGENTS.md](../AGENTS.md)「关键技能」。

## 验收对照

- 新成员 2 分钟内可理解后端用途（第 1 节）。✅
- 命令与数字均来自 2026-09-21 实测（HEAD d8f30cd）；无法确认的已标 `[GAP]`。✅
- 文档索引与 `fastrag-backend/docs/` 实际内容一致（2026-09-21 快照）。✅
