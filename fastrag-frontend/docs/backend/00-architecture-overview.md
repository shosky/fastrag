# FastRAG 后端架构总览

> 本文档基于现有前端（`fastrag-demo`）的实际功能与数据契约反推后端设计。前端 mock 层（`src/mock/*.ts`）与类型定义（`src/types/*.ts`）是后端 DTO 与接口契约的权威来源。

---

## 一、设计原则

1. **契约驱动**：后端接口的字段、枚举、行为以现有前端 mock 函数签名为准，确保前端零改动即可切到真实后端。
2. **模块化**：按业务域拆分模块（module），每个模块自包含 Controller / Service / Repository / DTO。
3. **异步优先**：文件加工、评估、模型调用等长耗时任务统一走异步任务 + 状态轮询/SSE。
4. **权限分层**：系统级 RBAC（角色→权限点）+ 知识库级 ACL（owner/editor/viewer）双层鉴权，对应前端 `hasPermission` / `hasKBPermission`。
5. **统一响应**：所有接口返回 `ApiResponse<T>`，分页返回 `PageResult<T>`，与前端 `src/types/global.d.ts` 一致。

---

## 二、技术栈选型

| 层次 | 技术 | 选型理由 |
|------|------|----------|
| **基础框架** | Spring Boot 3.x (JDK 17) | 主体框架，要求指定 |
| **持久层** | MyBatis-Plus 3.5 + MySQL 8.0 | 业务 CRUD 灵活、代码生成友好；MySQL 承载关系型主数据 |
| **向量检索** | Milvus 2.x 或 Qdrant | 承载 Embedding 向量与 ANN 检索（vector channel） |
| **全文检索** | Elasticsearch 8.x | BM25 全文召回（fulltext channel）+ 高亮片段 |
| **图数据库** | Neo4j 5.x | 知识图谱实体/关系存储与图扩展召回（graph channel） |
| **缓存** | Redis 7.x | 会话/Token、热点配置、检索结果缓存、限流 |
| **消息队列** | RabbitMQ | 文件加工、评估等异步任务解耦 |
| **对象存储** | MinIO / OSS | 文件原文、分片内容、导出包存储 |
| **任务调度** | Spring Scheduling + 自研任务表 | 加工进度轮询、定时任务 |
| **API 文档** | SpringDoc OpenAPI 3 (Swagger) | 接口文档自动生成 |
| **鉴权** | Spring Security + JWT (无状态) | 对应前端 Bearer Token |
| **校验** | Jakarta Validation | DTO 参数校验 |
| **对象映射** | MapStruct | Entity ↔ DTO 转换 |
| **可观测** | Logback + Actuator + Prometheus | 日志、指标、健康检查 |

> **检索引擎选择说明**：前端 `RetrievalConfig` 同时支持 vector / fulltext / hybrid 三种模式，且多路召回（multi-retrieval）支持 vector + fulltext + graph + qa 四路，融合策略 rrf/weighted/interleave。因此必须同时具备向量库、全文引擎、图库三套存储。单用 PostgreSQL+pgvector 可作为轻量起步，但多路召回与生产规模建议 Milvus + ES + Neo4j 组合。

---

## 三、整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端 (Vue3 + Element Plus)                │
│   知识库 / 应用中心 / 管理中心 / 运营中心                          │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTPS + JWT (Bearer)
┌───────────────────────────▼─────────────────────────────────────┐
│                     API 网关层 (Spring Security)                  │
│            认证 / 鉴权(RBAC+ACL) / 限流 / 日志                     │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                       业务模块层 (Modules)                        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │   IAM    │ │ 知识库   │ │ 检索增强 │ │ 图谱评估 │ │ 发布审核│ │
│  │ 认证权限 │ │ 文件加工 │ │ 查询增强 │ │  RAG     │ │ 日志   │ │
│  └──────────┘ └────┬─────┘ └────┬─────┘ └──────────┘ └────────┘ │
│  ┌──────────┐ ┌────▼─────┐ ┌────▼─────┐ ┌──────────┐            │
│  │ 应用编排 │ │ 工具技能 │ │ 平台配置 │ │ 运营审计 │            │
│  │ 工作流   │ │   MCP    │ │ 模型字典 │ │  内容    │            │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘            │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│                        基础设施层                                 │
│  MySQL(主数据)  Redis(缓存)  MinIO(对象存储)  RabbitMQ(异步)       │
│  Milvus(向量)  Elasticsearch(全文)  Neo4j(图谱)                   │
└───────────────────────────┬─────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────┐
│              AI 能力层 (模型网关)                                 │
│   LLM(qwen3/DeepSeek)  Embedding(v4/v3/bge)  Rerank(bge)  OCR/ASR │
└─────────────────────────────────────────────────────────────────┘
```

---

## 四、模块划分

后端按业务域划分为 **9 大模块**，每个模块对应独立的 Controller 集与 Service：

| # | 模块 | 职责 | 对应前端路由 | 设计文档 |
|---|------|------|-------------|----------|
| 1 | **IAM 认证与权限** | 登录/Token、系统角色、权限点、人员、组织、团队、KB ACL | `/login`, `/admin/account/*`, `/admin/permissions` | [01-iam.md](./01-iam.md) |
| 2 | **知识库与文件加工** | KB CRUD、文件上传/解析/分片/向量化、文件夹、回收站、解析策略、QA 对 | `/knowledge/*`, `/process/*` | [02-knowledge-base.md](./02-knowledge-base.md) |
| 3 | **检索与查询增强** | 向量/全文/混合检索、多路召回融合、重排序、查询改写/纠错/同义词/图扩展 | 检索调试、SearchTestPanel | [03-retrieval.md](./03-retrieval.md) |
| 4 | **知识图谱与 RAG 评估** | 图谱节点/边、图扩展、基准集、评估运行、召回率指标 | 图谱面板、评估面板、评估基准 | [04-graph-evaluation.md](./04-graph-evaluation.md) |
| 5 | **发布审核与日志** | 版本快照、发布状态机、审核任务、统一日志(操作/检索/发布) | 发布管理、日志管理、审核中心 | [05-publish-audit-log.md](./05-publish-audit-log.md) |
| 6 | **应用编排** | 应用 CRUD、应用编排配置、工作流定义/节点/边 | `/application`, `/application/:id/editor` | [06-application.md](./06-application.md) |
| 7 | **工具 / 技能 / MCP** | HTTP 工具、技能、MCP 服务管理 | `/application/my-tools`, `/mcp-management`, `/skill-management` | [07-tools-skills-mcp.md](./07-tools-skills-mcp.md) |
| 8 | **平台配置** | 模型管理、敏感词、字典、术语、查询规则、内容(通知/标签/提示词/模板/下载)、三方平台、API 密钥 | `/admin/system/*`, `/admin/content/*`, `/admin/platform/*` | [08-platform-config.md](./08-platform-config.md) |
| 9 | **运营与审计** | 知识资产分析、模型监控、问答反馈、系统日志、设备登录、登录安全 | `/operation/*`, `/admin/audit/*` | [09-operation-audit.md](./09-operation-audit.md) |
| — | **横切：门户与工作台** | 首页聚合、工作台（最近访问/关注/收藏/容量/布局） | `/home`, `/workspace/*` | [11-gap-supplement.md](./11-gap-supplement.md) |

> 注：经二次审核（见 [11-gap-supplement.md](./11-gap-supplement.md)），另发现应用编辑器配置、团队成员、知识库级多轮对话等遗漏接口，已统一在补充文档中补全。

---

## 五、工程结构

采用 Maven 多模块单仓（monorepo），统一版本管理：

```
fastrag-backend/
├── pom.xml                          # 父 POM，统一依赖版本
├── fastrag-common/                  # 公共：DTO/枚举/异常/工具/API响应
├── fastrag-security/                # 认证鉴权：Spring Security + JWT + RBAC/ACL
├── fastrag-infra/                   # 基础设施接入：MySQL/Redis/ES/Milvus/Neo4j/MinIO/MQ
├── fastrag-ai/                      # AI 能力网关：LLM/Embedding/Rerank/OCR/ASR 统一封装
├── fastrag-modules/
│   ├── fastrag-iam/                 # 模块1：认证权限
│   ├── fastrag-knowledge/           # 模块2：知识库与文件加工
│   ├── fastrag-retrieval/           # 模块3：检索与查询增强
│   ├── fastrag-graph-eval/          # 模块4：图谱与评估
│   ├── fastrag-publish/             # 模块5：发布审核与日志
│   ├── fastrag-application/         # 模块6：应用编排
│   ├── fastrag-tools/               # 模块7：工具/技能/MCP
│   ├── fastrag-platform/            # 模块8：平台配置
│   └── fastrag-operation/           # 模块9：运营与审计
└── fastrag-bootstrap/               # 启动模块：聚合配置 + main
```

每个业务模块内部统一结构：
```
fastrag-xxx/
├── controller/        # REST 接口
├── service/           # 业务逻辑
│   └── impl/
├── mapper/            # MyBatis-Plus Mapper (或 ES/Milvus Repository)
├── entity/            # 数据库实体
├── model/             # DTO（请求/响应）+ enums
├── event/             # 领域事件（异步任务触发）
└── manager/           # 复杂业务编排（跨 Service）
```

---

## 六、统一约定

### 6.1 接口响应格式

所有接口统一返回 `ApiResponse<T>`（对齐 `src/types/global.d.ts`）：

```json
{
  "code": 200,
  "data": { },
  "message": "success"
}
```

| code | 含义 | HTTP 状态 |
|------|------|-----------|
| 200 | 成功 | 200 |
| 400 | 参数错误 | 200 |
| 401 | 未认证 | 200 |
| 403 | 权限不足（对应前端 `权限不足`） | 200 |
| 404 | 资源不存在 | 200 |
| 500 | 服务异常 | 200 |

> 前端拦截器按 `code` 判断，HTTP 层始终 200（与现有 mock axios 拦截器一致）。

### 6.2 分页

分页请求参数 `PageParams { page, pageSize }`，响应 `PageResult<T> { list, total, page, pageSize }`。列表接口统一支持 `keyword` 过滤（对应前端 `FilterParams`）。

### 6.3 鉴权

- 登录返回 `{ token, userInfo }`，前端存 `localStorage` 的 `ais_token` / `ais_userInfo`。
- 后续请求带 `Authorization: Bearer <token>`。
- `userInfo` 必须包含 `roles[]` 与 `permissions[]`，前端据此做菜单/按钮/路由鉴权。
- 权限点以冒号分隔（如 `kb:create`、`admin:system`、`review:approve`），`*` 表示超管通配。

### 6.4 软删除

文件、QA 对等支持回收站，使用 `deleted_at` 字段软删除（对齐 `KnowledgeFile.deletedAt`）。

### 6.5 时间格式

统一 `yyyy-MM-dd HH:mm:ss`（对齐前端 `formatDate` 默认格式）。

---

## 七、开发流程建议

1. **先公共后业务**：先落地 `fastrag-common`（响应/异常/枚举）+ `fastrag-security`（鉴权基座）+ `fastrag-infra`（存储接入）。
2. **模块并行**：9 大模块依赖关系弱，可并行开发；检索/图谱/评估模块依赖 `fastrag-ai`。
3. **契约先行**：每个模块先出接口定义（Swagger），前端联调时只需替换 axios baseURL，去掉 mock 拦截器。
4. **数据迁移**：前端 mock 的种子数据（KB、文件、模型、术语等）作为数据库初始化脚本 `data.sql`。
5. **接口审计**：每个模块文档末尾附「前端覆盖核对表」，确保 mock 函数逐一对应到后端接口。

---

## 八、文档导航

| 文档 | 内容 |
|------|------|
| [00-architecture-overview.md](./00-architecture-overview.md) | 本文：架构总览 |
| [01-iam.md](./01-iam.md) | 认证、RBAC、KB ACL、人员组织团队 |
| [02-knowledge-base.md](./02-knowledge-base.md) | 知识库、文件加工、分片、解析策略、QA |
| [03-retrieval.md](./03-retrieval.md) | 检索引擎、多路召回、重排序、查询增强 |
| [04-graph-evaluation.md](./04-graph-evaluation.md) | 知识图谱、图扩展、基准、RAG 评估 |
| [05-publish-audit-log.md](./05-publish-audit-log.md) | 版本、发布状态机、审核、统一日志 |
| [06-application.md](./06-application.md) | 应用编排、工作流引擎 |
| [07-tools-skills-mcp.md](./07-tools-skills-mcp.md) | HTTP 工具、技能、MCP 服务 |
| [08-platform-config.md](./08-platform-config.md) | 模型、敏感词、字典、术语、查询规则、内容、三方、密钥 |
| [09-operation-audit.md](./09-operation-audit.md) | 运营分析、系统日志、设备登录、登录安全 |
| [10-coverage-audit.md](./10-coverage-audit.md) | 覆盖审计（路由 + mock 核对） |
| [11-gap-supplement.md](./11-gap-supplement.md) | 二次审核遗漏接口补充（首页/工作台/团队/应用编辑器/对话） |
