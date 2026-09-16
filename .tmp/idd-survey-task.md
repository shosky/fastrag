# IDD Onboard — 调研任务书（Phase 1: Survey）

## 背景

FastRAG 是一个 RAG 知识库管理系统，位于 `D:\Workspace\java\github\rag\fastrag`，为前后端同仓（monorepo）：

- `fastrag-backend/` — Java 17 + Spring Boot 3.2.5 + MyBatis-Plus 多模块 Maven 工程
- `fastrag-frontend/` — Vue 3 + Vite + Element Plus 前端
- 基础设施：MySQL 8 / Redis 7 / RabbitMQ / Milvus / MinIO / Neo4j / Ollama 兼容 AI 网关 / OnlyOffice / FFmpeg

本次调研是 IDD（Inventory-Driven Documentation）流程的第一阶段，目标是为「特性地图（feature map）」聚类收集证据。已知项目文档语言为**中文**，所有产出文档用中文。

现有 `AGENT.md`（项目宪法）、`AGENTS.md`（前端分页规范）、`CONTEXT.md`（领域术语表）已读，无需复述其内容，但需报告与其冲突或补充的信号。

## 各子任务范围

### A. 后端调研（fastrag-backend/）

1. 构建系统：根 pom.xml 模块列表、Java/Spring Boot 版本、测试/构建命令、是否有 springdoc/swagger/OpenAPI 依赖或注解（决定 API Contract Source）
2. 模块树：列到 `src/main/java` 下第 3~4 层包结构，标注各模块大致 LOC
3. **控制器全量清单**：每个 `@RestController`/`@Controller` —— 类名、模块、base path、每个端点（HTTP 方法 + 路径），这是特性聚类的首要证据
4. 入口补充：RabbitMQ 消费者（@RabbitListener）、定时任务（@Scheduled）、SSE/WebSocket 端点
5. 服务层组织：各模块的主要包与关键 Service（名字级即可，不必逐方法）
6. 异常体系：自定义异常类、全局异常处理器、统一返回包装（Result/R 类）
7. 数据层：实体/表清单（kb_ 前缀等），init-scripts 中的 DDL
8. 外部集成点：Milvus、MinIO、Neo4j、RabbitMQ、Redis、AI 网关、OnlyOffice、FFmpeg 各自在哪个模块接入
9. 测试：src/test 下的测试类清单（按模块分组）
10. 可见约定：分层模式、命名、事务/降级模式

### B. 前端调研（fastrag-frontend/）

1. package.json：框架与关键依赖、scripts（dev/build/lint/test）、包管理器
2. **路由全量清单**：router 配置中每条路由 —— path、name、对应 view 组件文件、守卫/权限标记；这是前端特性聚类的首要证据
3. views 目录树 + 各页面区域划分
4. API 客户端层：`src/api/`（或同等）各模块文件及其调用的后端端点（baseURL/路径）
5. 状态管理：pinia store 清单及职责
6. 共享组件/composables 清单（粗粒度，含 usePagination）
7. 测试：单元/E2E 测试是否存在
8. 可见约定：auto-import（unplugin）、BEM、scss、目录分层

### C. 文档 + Git 历史 + 环境调研

1. `docs/` 全量清单：每个文件/子目录一句话概括范围（adr、comparison、design、document-processing、research、FastRAG-API-Doc-Tab-Implementation-Plan、document-processing-pipeline.md）
2. `manual.md`（129KB）：读开头判断性质（用户手册？API 文档？）与大致章节
3. `scripts/`、`docker-compose.yml`、`.env.example`：各自定义了什么（服务、脚本用途）
4. **Git 历史活跃度**：最近 60 天按目录的提交数（重点：backend 各模块、frontend 各 views 子目录），列出 top 15 高频变更目录与近期 commit 主题（判断特性热点与活跃区域）
5. README.md 现状

## 产出要求

三个子任务各自返回结构化的**结论清单**（不要贴大段代码），所有条目尽量带 `file:line` 或路径证据。该清单将直接用于特性聚类与证据包构建，宁可细勿漏。
