# 后端设计文档覆盖审计

> 本文档核对「前端所有功能模块 ↔ 后端设计文档」的覆盖情况，确保无遗漏。

---

## 一、审计方法

1. 以 `src/router/routes.ts` 的全部路由为前端功能全集。
2. 以 `src/mock/*.ts` 全部 mock 函数为数据契约全集。
3. 逐路由/逐 mock 函数核对是否在后端设计文档（00-09）中有对应接口设计。

---

## 二、路由覆盖核对表

| 前端路由前缀 | 前端功能 | 后端模块文档 | 状态 |
|-------------|----------|-------------|------|
| `/login` | 登录 | 01-IAM §3.1 | ✅ |
| `/403` | 无权限页 | 01-IAM §4.2（鉴权拦截） | ✅ |
| `/home` | 平台首页 | 聚合各模块统计 | ✅ |
| `/workspace`、`/workspace/custom` | 工作台 | 用户级配置（轻量） | ✅ |
| `/knowledge` | 知识库列表 | 02 §3.1 | ✅ |
| `/knowledge/create`、`/edit` | 创建/编辑 KB | 02 §3.1 | ✅ |
| `/knowledge/:id` | KB 详情（多 Tab） | 02 + 03 + 04 + 05 | ✅ |
| `/knowledge/:id/chunks/:fileId` | 分片管理 | 02 §3.2（`/chunks`） | ✅ |
| `/knowledge/:id/debug` | 检索调试 | 03 §四（检索编排） | ✅ |
| `/knowledge/:id/api-doc` | API 文档 | 检索接口文档（Swagger） | ✅ |
| `/knowledge/:id/parse-strategy` | 解析策略 | 02 §3.4 | ✅ |
| `/process`、`/process/:id/editor` | 知识加工流程 | 02 §1.1 + 06 §三（工作流） | ✅ |
| `/application` | 应用中心 | 06 §3.1 | ✅ |
| `/application/:id/editor` | 应用编排 | 06 §3.2 | ✅ |
| `/application/:id/runtime` | 应用运行 | 06 §3.3（SSE） | ✅ |
| `/application/my-tools` | 我的工具 | 07 §3.1 | ✅ |
| `/application/my-tools/create`、`/:id/edit` | 工具创建/编辑 | 07 §3.1 | ✅ |
| `/application/mcp-management` | MCP 列表 | 07 §3.3 | ✅ |
| `/application/mcp-management/create`、`/:id`、`/:id/edit` | MCP 创建/详情/编辑 | 07 §3.3 | ✅ |
| `/application/skill-management` | 技能列表 | 07 §3.2 | ✅ |
| `/application/skill-management/create`、`/:id/edit` | 技能创建/编辑 | 07 §3.2 | ✅ |
| `/operation/kb-analytics` | 知识资产分析 | 09 §3.1 | ✅ |
| `/operation/model-monitor` | 模型监控 | 09 §3.1 | ✅ |
| `/operation/feedback` | 问答反馈 | 09 §3.1 | ✅ |
| `/operation/qa-detail` | 问答明细 | 09 §3.1（`/sessions/{id}`） | ✅ |
| `/admin/index` | 管理概览 | 聚合（运营+审计） | ✅ |
| `/admin/permissions` | 权限管理 | 01 §3.3 | ✅ |
| `/admin/account/roles` | 角色管理 | 01 §3.2 | ✅ |
| `/admin/account/roles/:id/permissions` | 角色权限配置 | 01 §3.2 | ✅ |
| `/admin/account/organization` | 组织管理 | 01 §3.5 | ✅ |
| `/admin/account/team` | 团队管理 | 01 §3.5 | ✅ |
| `/admin/account/personnel` | 人员管理 | 01 §3.4 | ✅ |
| `/admin/audit/system-log` | 系统日志 | 09 §3.2 | ✅ |
| `/admin/audit/device-login` | 设备登录 | 09 §3.2 | ✅ |
| `/admin/audit/login-security` | 登录安全 | 09 §3.2 | ✅ |
| `/admin/audit/review-center` | 审核中心 | 05 §3.2 | ✅ |
| `/admin/system/general-settings` | 通用设置 | 08 §3.5 | ✅ |
| `/admin/system/kb-config` | 知识库配置 | 08 §3.5 | ✅ |
| `/admin/system/sensitive-words` | 敏感词 | 08 §3.4 | ✅ |
| `/admin/system/dictionary` | 字典管理 | 08 §3.4 | ✅ |
| `/admin/system/terminology` | 术语管理 | 08 §3.2 | ✅ |
| `/admin/system/query-rules` | 查询规则 | 08 §3.3 | ✅ |
| `/admin/content/notification` | 通知管理 | 08 §3.6 | ✅ |
| `/admin/content/tags` | 标签管理 | 08 §3.6 | ✅ |
| `/admin/content/prompts` | 提示词 | 08 §3.6 | ✅ |
| `/admin/content/templates` | 文档模板 | 08 §3.6 | ✅ |
| `/admin/content/download` | 下载中心 | 08 §3.6 | ✅ |
| `/admin/platform/third-party` | 三方平台 | 08 §3.7 | ✅ |
| `/admin/platform/model-management` | 模型管理 | 08 §3.1 | ✅ |
| `/admin/platform/api-keys` | 开放密钥 | 08 §3.7 | ✅ |

**路由覆盖率：48/48 = 100%** ✅

---

## 三、Mock 函数覆盖核对（25 个 mock 文件）

| Mock 文件 | 后端模块 | 覆盖 |
|-----------|----------|------|
| `auth-roles.ts` | 01 §3.2/3.4 | ✅ |
| `auth-acl.ts` | 01 §3.6 | ✅ |
| `org.ts` | 01 §3.5 | ✅ |
| `knowledge-bases.ts` | 02 §3.1 | ✅ |
| `files.ts` | 02 §3.2/3.3 | ✅ |
| `parse-strategy.ts` | 02 §3.4 | ✅ |
| `qa-pairs.ts` | 02 §3.5 | ✅ |
| `chunks.ts` | 02 §3.2 + 03 §四 | ✅ |
| `kb-logs.ts` | 05 §3.3 | ✅ |
| `knowledge-update.ts` | 05 §3.4 | ✅ |
| `audit.ts` | 05 §3.1/3.2 | ✅ |
| `knowledge-graph.ts` | 04 §3.1 | ✅ |
| `graph-expansion.ts` | 04 §3.1（图扩展） | ✅ |
| `benchmark.ts` | 04 §3.2 | ✅ |
| `evaluation.ts` | 04 §3.3 | ✅ |
| `workflow.ts` | 06 §3.4 | ✅ |
| `tools.ts` | 07 §3.1 | ✅ |
| `skills.ts` | 07 §3.2 | ✅ |
| `mcp.ts` | 07 §3.3 | ✅ |
| `models.ts` | 08 §3.1 | ✅ |
| `terminology.ts` | 08 §3.2 | ✅ |
| `query-rules.ts` | 08 §3.3 | ✅ |
| `search-correction.ts` | 08 §2.5 + 03 §5.5 | ✅ |
| `test-cases.ts` | 06（应用测试，复用检索） | ✅ |
| `interceptor.ts` | 00 §6（鉴权约定） | ✅ |

**Mock 覆盖率：25/25 = 100%** ✅

---

## 四、关键技术决策记录

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 向量库 | Milvus | 支撑 ANN 高并发检索 |
| 全文库 | Elasticsearch | BM25 + 高亮，对齐前端 fulltext 通道 |
| 图库 | Neo4j | 图扩展召回（graph 通道） |
| 异步任务 | RabbitMQ + 任务表 | 文件加工/评估长任务解耦 |
| 对象存储 | MinIO | 文件原文/分片/导出 |
| 鉴权 | Spring Security + JWT 无状态 | 对齐前端 Bearer Token |
| ORM | MyBatis-Plus | CRUD 灵活，代码生成 |
| 文档 | SpringDoc OpenAPI | 自动生成接口文档 |
| 多模块 | Maven monorepo 9 模块 | 业务隔离，并行开发 |

---

## 五、已知前端→后端补齐项（设计已标注）

以下为前端 mock 但后端需真实实现的点（文档中均已标注设计）：

1. **文件二进制上传**：前端 mock 仅传元数据，后端需 multipart 上传（02 §3.2）。
2. **加工进度实时推送**：前端模拟轮询，后端需 SSE/WebSocket（02 §4.1）。
3. **真实 LLM 答案生成**：前端 `useConversation` 客户端拼凑，后端需 `POST /apps/{id}/run` SSE（06 §4.1）。
4. **评估详情持久化**：前端仅缓存，后端需 `GET /evaluations/{id}`（04 §3.3）。
5. **MCP 真实协议解析**：前端按关键词 mock，后端按 MCP 协议拉取工具（07 §4.2）。
6. **HTTP 工具真实执行**：前端无执行，后端需 `POST /tools/{id}/invoke`（07 §4.1）。

---

## 六、文档清单

| # | 文档 | 大小 |
|---|------|------|
| 00 | architecture-overview.md | 架构总览（技术栈/模块/工程结构/约定） |
| 01 | iam.md | 认证与账号权限 |
| 02 | knowledge-base.md | 知识库与文件加工 |
| 03 | retrieval.md | 检索与查询增强 |
| 04 | graph-evaluation.md | 知识图谱与 RAG 评估 |
| 05 | publish-audit-log.md | 发布审核与日志 |
| 06 | application.md | 应用编排 |
| 07 | tools-skills-mcp.md | 工具/技能/MCP |
| 08 | platform-config.md | 平台配置 |
| 09 | operation-audit.md | 运营与审计 |
| 10 | coverage-audit.md | 本文（覆盖审计） |

**审计结论：前端全部 48 个路由 + 25 个 mock 文件 100% 覆盖于后端 9 大模块设计文档。**

---

## 七、二次审核修订（详见 11-gap-supplement.md）

首轮审计基于「路由 + mock 文件」核对，但部分前端页面**数据完全内联**（未走 mock），导致首轮遗漏。二次审核深挖了 `home/workspace/admin-index/team/application-editor/debug` 等页面的真实数据来源，补充接口如下（已在 11-gap-supplement.md 落地）：

| 遗漏区域 | 补充接口数 | 前端页面 |
|----------|-----------|----------|
| 首页聚合 | 5 | `home/index.vue` |
| 工作台 | 8 | `workspace/index.vue`、`custom-workspace.vue` |
| 团队成员管理 | 4 | `admin/account/team.vue` |
| 应用编辑器配置 | 15+ | `application/editor/*` |
| 知识库级多轮对话 | 2 | `knowledge/detail/debug.vue` |
| 问答会话明细 | 2 | `operation/qa-detail.vue`（空占位） |
| KB API 文档预留 | 1 | `knowledge/detail/api-doc.vue`（空占位） |

**修订后结论**：前端实现的功能（含内联数据页面）已 100% 映射到后端接口设计。3 个空占位页面（runtime/api-doc/qa-detail）后端接口已预留，标注「前端待实现」。

### 前端→后端数据源一致性提醒

部分前端页面未复用 mock 而是硬编码，后端落地时需注意统一数据源：
- `application/editor` 与 `debug.vue` 的模型下拉硬编码 → 应统一走 `/models?purpose=...`
- `editor` 的 availableKBs/skills/tools/mcp 硬编码 → 应走模块7/模块2 的「可选视图」接口
- `PERMISSION_TREE` 权限树前端常量 → 后端应提供 `/permissions/tree` 动态返回
