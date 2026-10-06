# CONTEXT.md — FastRAG 前端领域术语表

> 本文件由 idd-onboard 维护，`fastrag-frontend/docs/` 下所有模块文档共享此术语定义。
> 范围：仅覆盖 `fastrag-frontend`（前端单侧）。仓库根另有全仓文档体系，不在本树范围内。

> **IDD Metadata**（由 idd-onboard 于 2026-09-21 检测写入）
> - **Repository Role:** `frontend`（CERTAIN — package.json 含 vue 3.5 / vue-router 4 / pinia / element-plus 强信号，vue-router 路由表 64 条页面路由；本目录无任何后端代码）
> - **API Contract Source:** 后端运行时生成 springdoc（dev 代理 `/api → http://localhost:8081`，vite.config.ts:33-38；运行时 `GET /v3/api-docs`）。本仓库内无静态契约文件，前端消费侧事实源 = `src/api/index.ts`（2074 行、440 次调用）+ `src/types/`（2259 行）
> - **文档语言：** 中文

## 核心领域术语

（前端代码中出现的领域概念，与后端契约同源；命名一律采用下列词条，避免同义词。）

**知识库（Knowledge Base）** — 知识管理单元，前端主入口 `/knowledge`（列表→分类→详情详情族）。

**知识分片（Chunk）** — 文档解析切分后的文本片段，分**父分片/子分片**两级；前端主界面 `detail/chunks.vue`。

**解析策略（Parse Strategy）** — 文档切分规则配置；前端 `detail/parse-strategy.vue` + `useParseStrategy`。

**策略绑定（Strategy Binding）** — 文件与策略的关联（库级默认 + 文档级覆盖）；绑定变更必然重切。

**自动匹配（Auto Match）** — 未显式指定时按扩展名回退匹配。

**重新分片（Re-chunk）** — 重跑「解析→分片→向量化」流水线。

**AI分片（AI Chunking）** — 加工工作流：OCR→结构级跨页合并→段落对齐→LLM/手动分片→应用落库；前端 `AiChunkPanel`（1965 行）+ `AiChunkPdfOverlay`（区域框选）。

**段落对齐模型（Paragraph Alignment Model）** — 结构化段落序列，驱动「原件预览 ↔ 解析结果」联动高亮。

**结构级跨页合并（Structural Cross-page Merge）** — 把被页边界割裂的逻辑块并回完整结构。

**向量检索 / 混合检索（Retrieval / Hybrid Retrieval）** — 向量 + 关键词 + 重排序；前端 `SearchTestPanel`、`RetrievalSidebar`。

**知识图谱（Knowledge Graph）** — 实体关系图；前端 G6 力导渲染（`useForceGraph`）+ `KnowledgeGraphPanel`。

**发布（Publish）** — 知识库编辑态→线上态（含版本/审核）；前端 `PublishPanel` + `knowledge-review/` 7 页族。

**应用（App）** — 面向终端用户的应用单元；前端 `application/editor`（15 配置面板）+ `runtime.vue`。〔2026-09-25 注（WeKnora 对齐）：应用降级为会话可选预设——会话中心里以预设 chip 呈现，移除即转直聊。〕

**新建对话（New Conversation）** — 会话优先的对话入口：不预配应用，直接选知识库范围与模型开聊；前端主战场 `views/conversation/`（新建）。Avoid: 应用运行、免应用问答。

**会话中心（Session Center）** — 按用户全量列出会话的统一页面（置顶优先、来源标记、分页）；取代「应用运行」成为对话唯一入口（runtime 保留兼容路由）。Avoid: 会话列表页、聊天历史页。

**会话请求态（Session Request State）** — 会话记住的最后一次输入范围（知识库/模型/挂载应用）；前端体现为重开会话时输入框自动还原，composer 状态单一事实源在服务端。Avoid: 输入框快照、会话配置。

**消息引用（Message References）** — 随助手消息持久化的检索来源（无 content 投影）；前端 SourcesBlock 据此渲染历史来源块，与流内 sources 事件双形态共用一组件。Avoid: sources 事件（专指流内推送）。

**工作流（Workflow）** — 可视化编排图；前端仅 `workflow-manage.vue` 1 页（UI 落后于 33 个 API 消费）。

**智能体（Agent）** — 自主决策 AI 实体；前端无独立管理页，仅 `VmConfig` 等编辑器内组件（INFERRED）。

**工具 / MCP 服务 / 技能（Tool / MCP / Skill）** — 三类外部能力 + 外部数据库；前端 4 个管理页族。

**知识库权限（KB ACL）** — owner > editor > viewer；前端 `useAuth` + `v-permission` 指令。

**操作审计（Audit Log）** — 前端 `admin/audit/system-log.vue`。

## 前端技术术语

**MENU_PERMISSION_MAP** — `types/auth.ts:481-560` 的路由权限映射表；路由守卫逐路由查它，**未登记路径默认放行**（router/index.ts:92-93）。

**MENU_PERM_TREE / ACTION_API_LINKS** — `config/menu-perm-tree.ts`（451 行）的「菜单→页面操作→API 联动组」结构，权限管理页（role-permissions.vue）的渲染真源。

**解包约定（request.ts）** — axios 响应统一解包 `{code,data,message}`；code 401 → 清 storage 跳 `/login`，403 仅提示不登出（utils/request.ts:41-54）。后端业务错误 = HTTP 200 + body.code。

**SSE 直连** — ai-chunk 的 stream/layout 用 fetch 硬编码 `/api` 前缀，绕过 axios baseURL（api/index.ts:249-263）。

**mock 层** — `src/mock/`（30 文件 6677 行）模拟数据/实现。运行时真消费仅 3 处（answer-kb/templates 整页、QaPanel getFiles、query-preprocess 服务）；**16 个模块 ≈2.8k 行已零引用（死代码）**。

**设计 token** — `assets/styles/variables.scss`（66 行九组 SCSS 变量），经 `global.scss` 全局注入；**SFC 内不直接消费 token**（0 个 .vue 引入 variables）。

**BEM `__pagination` + usePagination** — 分页统一风格：`composables/usePagination` + `${block}__pagination` 容器类（当前 18 个视图在用）。

## 命名冲突与勘误（idd-onboard 检测，2026-09-21）

- **产品命名漂移（CERTAIN）**：package.json 包名 `fastrag-demo-tmp`、index.html 标题「AI知识库」、router 守卫覆写 `document.title = '${title} - AIS'`（router/index.ts:71-72）——三套命名并存。
- **孤儿文件 11 个（CERTAIN，≈2.5k 行）**：无路由挂载且全库零 import——`admin/system/config-management.vue`（516）、`admin/content/` 5 页（1164）、`notification/index.vue`（197）、`admin/platform/third-party.vue`（177）、`admin/audit/login-security.vue`（170）、`application/skill/create.vue`（48）+ `edit.vue`（106；路由实际用大写的 `SkillCreate.vue`，技能无编辑路由）。~~knowledge/form.vue（1501）~~ **勘误（2026-09-21 批次复核）**：form.vue **非孤儿**——create.vue:6 与 edit.vue:6 均 `import KnowledgeForm from './form.vue'`，为知识库建/编共享表单体（1501 行）；首批调研 grep 模式漏配相对导入 `./form.vue`。
- **死 mock 模块（CERTAIN）**：16 个模块 ≈2823 行零外部引用（search-center 609、chunks 582、knowledge-review 248、test-cases 228、evaluation 227、benchmark 207、models 204 等）。
- **反向孤儿权限路径（CERTAIN）**：MENU_PERMISSION_MAP 登记 `/application/tools`、`/application/workflow`、`/application/test-cases`，routes.ts 无此三路径（菜单分组键遗留）。
- **入口孤岛（CERTAIN）**：`/workspace` 与 `/workspace/custom` 仅互相链接，全库无第三入口。
- **占位 stub（CERTAIN）**：`detail/api-doc.vue` 路由页仅 8 行（el-empty「开发中」），真实现 `ApiDocPanel` 挂在 `detail/index.vue` 内；`/operation/qa-detail` 无菜单登记；`/application/prompt-templates`（answer-kb/templates.vue）在路由但侧边栏与权限表均未登记，且是唯一整页消费运行时 mock 的已接线页面。
- **敏感项（CERTAIN）**：`.env` 含 `WECHAT_APP_SECRET` 明文（该文件已被 .gitignore 忽略不会入库；前端不应持有 Secret，建议改后端代理签发）。
- **测试资产（GAP→确认不存在）**：无 lint / test / e2e / storybook 配置；playwright-core 仅用于 `scripts/oo-plugin-probe|introspect.mjs` 探针。
- **PLAN.md**（12 阶段「AIS Mock 演示系统」计划）为前 IDD 原始文档，特性地图确认后归档 `docs/legacy/`。
