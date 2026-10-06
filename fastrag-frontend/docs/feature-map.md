# Feature Map — fastrag-frontend 特性地图

> 由 idd-onboard 于 2026-09-21 生成（前端单侧接入；调研任务书 `.tmp/idd-frontend-survey-task.md`）。
> Chain status 取值：`shape → spec → design → contract → tdd → implement`，暂停时标 `blocked`。
> 证据分层：CERTAIN = 代码直接可观测（引 file:line）；INFERRED = 结构推断，阶段末向用户确认；GAP = 代码外信息，不臆造。
>
> **范围决议（默认推荐，随时可改）：** 聚类按下述 18 项领域特性（与领域语言对齐，目录聚合关系见「模块地图」）；构建顺序按文末 8 批；链深度默认 = **核心 8 项完整链**（iam、kb-core、parse-strategy、file-ingestion、chunk-management、onlyoffice-editing、ai-chunking、retrieval）+ 其余 10 项 shape-only。
> 前端链说明：`contract` 阶段产出 UI Contract（页面态矩阵 + API 数据映射）；API 契约以后端 springdoc 运行时（`/api → :8081`）为准，消费侧事实源 `src/api/index.ts`。文档命名带 `-frontend` 后缀。
>
> **代决执行记录（2026-09-21，用户授权「按推荐决策、不再询问」）：**
> ① **INFERRED 全量裁决**——约 118 条逐条裁决完毕，见 `docs/decisions/inferred-adjudication.md`（🔧 修复 8 / ✅ 确认现状 15 / 📋 保留登记 ~90 / ❓ GAP ~5）；各 spec 文末清单已加裁决指针。
> ② **代码修复 6 处**——ToolTestDialog `resp` 与 catch `params` 双 bug、SkillCreate reactive 崩溃、compliance 补 handleExecuteCheck、**PublishPanel 移除 `v-if="false"`（发布管理 Tab 恢复可达）**、QaPanel/QaImportDialog 死 import 清理；vue-tsc 验证无新增错误（存量类型错误系历史技术债，另行登记）。
> ③ **tdd 解阻**——vitest 已引入（`npm run test`），`vitest.config.ts`（node 环境）+ 首个契约测试 `src/types/audit.test.ts`（发布状态机 4 用例全绿）；各特性「tdd blocked」解除为 **partial**（基建就绪，逐特性切片按各 implement §4 清单推进）。
> ④ **PLAN.md 已归档** `docs/legacy/PLAN.md`（git mv）。
> ⑤ 孤儿资产（孤儿面板/页面/mock/死 API）按裁决**保留+登记**，未擅自删除或上线未验收入口。

## 模块地图（粗粒度）

```
fastrag-frontend（src 全部 .vue+.ts = 69,636 行；views 133 个 .vue = 52,964 行）
├── src/router/routes.ts    64 条页面路由（path: 记录 70 = 64 页面 + 1 布局 + 4 redirect 壳 + 1 catch-all）
├── src/router/index.ts     守卫：MENU_PERMISSION_MAP 驱动，未登记路径默认放行（:92-93）
├── src/api/index.ts        全部 API 单文件 2074 行、440 次调用（baseURL /api → 代理 8081；SSE fetch 直连 :249-263）
├── src/views/
│   ├── knowledge/          知识域：列表 385/分类 219/建 87/编 111 + detail 全家（index 526、chunks 2634、
│   │                       parse-strategy 1079、office-edit 363、debug 460、api-doc stub 8 + 36 组件 ≈19.5k）
│   ├── application/        应用域：index 258、editor 250 + 15 面板 ≈4.0k、app-config 578、runtime 1127（双路由复用）、
│   │                       workflow-manage 525、my-tools 312、tool/*、mcp-services 341 + mcp/*、
│   │                       skill-management 396 + skill/Skill*、database-management 924
│   ├── admin/              管理域：system×5（≈1.4k）、account×4（roles 285/role-permissions 598/org 159/personnel 326）、
│   │                       permissions 507、platform×2（model-management 567/api-keys 177）、audit/system-log 162
│   ├── operation/          运营 5 页（kb-analytics 195/model-monitor 181/feedback 564/qa-detail 147/retrieval-analysis 349）
│   │                       + components 3 件 —— Sidebar 跨模块拆挂（挂进「知识库」与「应用」两组）
│   ├── knowledge-review/   审核 7 页 ≈2.1k（侧挂：路由在、侧边栏与权限树外）——全部走真实 @/api
│   ├── robot-operation/    机器人运营 4 页 ≈324（侧挂 + 瘦壳：用 getKbAnalytics 伪造填充，faq-analysis.vue:3-30）
│   └── login|register|forgot-password|error|home|workspace|answer-kb|notification
├── src/components/         布局 4 件（Sidebar 835 权限过滤/AppLayout 144/Header 106/Logo 57）+ AI 对话 2 件
├── src/composables/        14 个 2040 行（useAppChatStream 378/useForceGraph 432/useOnlyOffice/usePagination 33…）
├── src/stores/             仅 2 个：user 116（token/userInfo + localStorage）、system 49（品牌配置）——页面态多在页内 ref
├── src/types/              10 文件 2259 行（knowledge 771、auth 692 双大头）
├── src/mock/               30 文件 6677 行（运行时真消费仅 3 处；16 模块 ≈2.8k 已零引用）
├── src/config/             menu-perm-tree.ts 451（权限树真源：MenuPermNode + ACTION_API_LINKS + MENU_PERM_TREE 四大组）、
│                           index.ts 38（OnlyOffice Server URL :8082）
└── src/utils|directives|services|data|assets|public（OO 插件 chunk-boundary*/、探针页）
```

---

# 特性清单（19 项 = 2026-09-21 决议 18 项 + 2026-09-25 新增 #19 new-conversation）

## 1. IAM 认证与账号权限 — iam

- **代码证据：** `views/login/index.vue`（537）、`register/index.vue`（602）、`forgot-password/index.vue`（661）、`error/403.vue`；`views/admin/account/{roles 285, role-permissions 598, organization 159, personnel 326}`、`admin/permissions/index.vue`（507）；`stores/user.ts`（116：token/userInfo + localStorage 持久化 + 真实 /api/auth/login）；`types/auth.ts`（692，MENU_PERMISSION_MAP :481-560）；`config/menu-perm-tree.ts`（451，权限树真源）；`directives/v-permission.ts`（38，无权限移除 DOM）；`router/index.ts:69-104`（守卫）；`composables/useAuth`（104，KB 角色缓存乐观放行）；api auth 组 8 端点；`components/layout/Sidebar.vue`（835，按 MENU_PERMISSION_MAP 过滤菜单 :213-257）
- **可能 REQ 区域：** 密码+验证码+微信扫码登录、注册/重置密码、RBAC 三层（角色-权限-菜单）、组织树、人员管理、KB 级 ACL、API Token 管理（ApiTokenManager 363）
- **Depends on:** 无（全系统前提）
- **Shared by:** 全部 17 项（守卫 + v-permission + useAuth + Sidebar 过滤四层消费）
- **Depth signal:** 相关面 ≈4.6k 行；admin/account 60 天 ×4 变更、router ×2、menu-perm-tree 为新真源（451 行）
- **Priority:** 高（依赖根）
- **Chain status:** implement — 全链文档已产出：spec ✅（`spec/iam-frontend.md`：13 TASK / 10 PAGE / 状态矩阵）→ design ✅（`design/iam-frontend.md` + ADR ×2）→ contract ✅（`contract/ui.md` 特性 1 节）→ **tdd blocked**（无测试基建，引入 vitest 待用户决策）→ implement ✅（`implement/iam-frontend.md`）。8 条 INFERRED 待批量确认后收链（2026-09-21）

## 2. 知识库管理 — kb-core

- **代码证据：** `views/knowledge/{index 385, categories 219, create 87, edit 111}` + 共享表单体 `knowledge/form.vue`（1501，create.vue:6/edit.vue:6 import，**非孤儿**——勘正首批调研）；`views/home/index.vue`（253，与 kb 真实耦合：getHomeData+推荐卡）；`views/workspace/{index 377, custom-workspace 270}`（**入口孤岛 + 纯本地原型**：零 API、custom-workspace 保存假成功）；api kb 域大头；`types/knowledge.ts`（771）；KB_TYPES 常量与 `getKnowledgeBaseCategories` 为死代码
- **可能 REQ 区域：** 知识库 CRUD/分类/标签、列表分页、工作台聚合入口（INFERRED：home/workspace 归属此处还是运营域待确认）
- **Depends on:** iam（KbAcl 消费）
- **Shared by:** 其余全部知识域特性
- **Depth signal:** 目录级基座；knowledge/index + categories 为 Sidebar「知识库」组主入口
- **Priority:** 高
- **Chain status:** implement — spec ✅（`spec/kb-core-frontend.md`：8 TASK / 5 PAGE / 6 INFERRED 待确认）→ design ✅ → contract ✅（`contract/ui.md` 特性 2 节）→ implement ✅（tdd 全局 blocked）。调研补强：form.vue 非孤儿（create/edit 共享表单体）；workspace 两页纯本地原型；KB_TYPES 与 `/kb/categories` 死代码（2026-09-21）

- **代码证据：** `views/knowledge/detail/parse-strategy.vue`（1079）、`composables/useParseStrategy`（87）、`detail/components/{StrategyChangeDialog 361, ChunkLengthSelect 66}`；api 策略组（resolve/conflicts 消费）
- **可能 REQ 区域：** 策略 CRUD/设默认/模板、扩展名自动匹配、文档级覆盖、绑定-重切一致性提示、冲突检测
- **Depends on:** kb-core
- **Shared by:** file-ingestion、chunk-management、ai-chunking
- **Depth signal:** 单页 1079 行 + 2 组件
- **Priority:** 高（摄取前置）
- **Chain status:** implement — spec ✅（`spec/parse-strategy-frontend.md`：7 TASK / 3 PAGE / 8 INFERRED 待确认）→ design ✅ → contract ✅（`contract/ui.md` 特性 3 节）→ implement ✅（tdd 全局 blocked）。调研勘正：resolve 端点实况零 UI 消费（仅死解构）；创建/更新无错误兜底；换绑对话框参数文案与主页面口径漂移（2026-09-21）

## 4. 文件上传与摄取流水线 — file-ingestion

- **代码证据：** `detail/components/{FileUploader 1706, FileManager 916, FileTable 658, FilePreviewDialog 620, DocumentPreviewPanel 488, VideoPlayer 679, AudioPlayer 380, ProcessStatusBar 148, RecycleBinDialog 129, MoveFileDialog 273, RenameFileDialog 102, FileMetadataEditor 188, FileMetadataDialog 61}`、`composables/useFiles`（171，FolderNode 类型来自 mock 层 :4）；api files 组
- **可能 REQ 区域：** 上传（分片/进度/取消）、8 格式预览（pdf.js/mammoth/pptx-preview/音视频）、回收站、移动/重命名/元数据、处理状态轮询
- **Depends on:** kb-core、parse-strategy
- **Shared by:** chunk-management、ai-chunking、onlyoffice-editing、retrieval、knowledge-graph
- **Depth signal:** 组件族 ≈6.3k 行；views/knowledge/detail 为 60 天热点第一位（54 次，components 45）
- **Priority:** 高
- **Chain status:** implement — spec ✅（`spec/file-ingestion-frontend.md`：9 TASK / 宿主 Tab / 8 INFERRED 待确认）→ design ✅ → contract ✅（`contract/ui.md` 特性 4 节）→ implement ✅（tdd 全局 blocked）。调研发现：上传绕过 api.uploadFile 死路径；3s 全量轮询（单文件状态端点闲置）；VideoPlayer/AudioPlayer 关键帧 mock 未接线；乐观 toast ×4；api 死导出 ×4（2026-09-21）

## 5. 分片管理 — chunk-management

- **代码证据：** `views/knowledge/detail/chunks.vue`（**2634 行，全项目最大页面**）、`detail/components/ChunkManagementPanel.vue`（1106）；api chunks 组
- **可能 REQ 区域：** 分片查看/编辑/批量删除、父子分片展示、重新分片入口、分片计数
- **Depends on:** file-ingestion、parse-strategy
- **Shared by:** retrieval（数据源展示）、knowledge-graph（抽取源）、ai-chunking（落库目标）
- **Depth signal:** 3.7k 行双文件
- **Priority:** 高
- **Chain status:** implement — spec ✅（`spec/chunk-management-frontend.md`：13 TASK / 1 PAGE 四分支 / 8 INFERRED 待确认）→ design ✅ → contract ✅（`contract/ui.md` 特性 5 节）→ implement ✅（tdd 全局 blocked）。调研发现：ChunkManagementPanel 为不可达 mock 死原型；下载/导出假成功 stub；分片列表无 loading 且失败静默；死导出 fetchChunkCount/getChunk（2026-09-21）

## 6. OnlyOffice 在线编辑 — onlyoffice-editing

- **代码证据：** `views/knowledge/detail/office-edit.vue`（363，路由 `/knowledge/:id/office/:fileId`）、`detail/components/OnlyOfficeViewer.vue`（327）、`composables/useOnlyOffice`（161）、`config/index.ts`（38，`VITE_ONLYOFFICE_URL` 缺省 `:8082`）、`public/onlyoffice-plugin/chunk-boundary*/`（选区桥插件 + 探针页 `__oo_probe.html`/`__oo_relay.html`）、`types/onlyoffice.ts`（147）、`scripts/oo-plugin-{probe,introspect}.mjs`（playwright-core 无头探针）
- **可能 REQ 区域：** 文档在线渲染/编辑、forcesave 回调、插件选区桥（AI 分片输入手段）
- **Depends on:** file-ingestion、iam（JWT）
- **Shared by:** ai-chunking（选区桥）
- **Depth signal:** 独立外部集成 + 自有插件资产 + 探针脚本（QA 探针唯一用例）
- **Priority:** 中高
- **Chain status:** implement — spec ✅（`spec/onlyoffice-editing-frontend.md`：10 TASK / 2 PAGE / 8 INFERRED）→ design ✅ → contract ✅ → implement ✅（tdd blocked）。发现：v1 插件 plugin.js 语法错误（死代码）、双插件版本挂载不可见（GAP）、chunk-created 死事件、保存基线失败必然超时（2026-09-21）

## 7. AI 分片 — ai-chunking

- **代码证据：** `detail/components/AiChunkPanel.vue`（**1965**）+ `AiChunkPdfOverlay.vue`（878，区域框选）；api SSE 直连（fetch 绕过 axios，api/index.ts:249-263）
- **可能 REQ 区域：** OCR 解析→跨页合并→段落对齐→LLM/手动分片→应用落库；区域框选（跨页拖拽/自动翻页/扫描件 OCR/图片直达分片）
- **Depends on:** file-ingestion、chunk-management、onlyoffice-editing（INFERRED：office 选区桥）、ai 层
- **Shared by:** 无下游（末端加工流）
- **Depth signal:** 2.8k 行双组件；components 热点 45 次中纯 ai-chunk 文件 6 次；08-27/08-30 两个特性提交主体
- **Priority:** 高（最高热度）
- **Chain status:** implement — spec ✅（`spec/ai-chunking-frontend.md`：9 TASK / 弹窗+Overlay / 8 INFERRED 待确认）→ design ✅ → contract ✅（`contract/ui.md` 特性 7 节）→ implement ✅（tdd 全局 blocked）。调研发现：SSE 双流手写分帧（重复实现）、自动分片流无 abort、回退链尾部无兜底、pageRange/SSE 字段类型漂移（2026-09-21）

## 8. 问答对管理 — qa-pairs

- **代码证据：** `detail/components/QaPanel.vue`（291；getFiles 消费 mock :5）、`QaImportDialog.vue`（363）；api qa 组
- **可能 REQ 区域：** QA 对 CRUD、Excel 导入、确认生效
- **Depends on:** kb-core
- **Shared by:** retrieval（INFERRED：QA 对参与召回方式待确认）
- **Depth signal:** 小（2 组件 654 行）
- **Priority:** 低
- **Chain status:** implement — spec ✅（`spec/qa-pairs-frontend.md`：6 TASK / 5 INFERRED）→ design ✅ → contract ✅ → implement ✅（tdd blocked）。发现：表单 9 字段仅提交 2 字段（其余静默丢弃）；AI 来源 QA 无创建/筛选入口；删除/确认静默失败；mock getFiles 系死 import（勘误）（2026-09-21）

## 9. 检索与查询增强 — retrieval

- **代码证据：** `detail/components/{SearchTestPanel 877, RetrievalSidebar 272, RetrievalSettingPanel 278}`、`views/operation/retrieval-analysis.vue`（349）、`services/query-preprocess.ts`（33，**委托 mock 实现**：`@/mock/search-correction`、`@/mock/query-rules`）、`composables/useSynonyms`（54）；api retrieval 组
- **可能 REQ 区域：** 混合检索测试、查询改写/同义扩展/规则应用、检索日志分析
- **Depends on:** chunk-management、platform-config（query_rule）
- **Shared by:** app-center（对话检索）
- **Depth signal:** ≈1.8k 行；前置服务层刻意走 mock（本地纠错/重写，后端为 stub）
- **Priority:** 高
- **Chain status:** implement — spec ✅（`spec/retrieval-frontend.md`：7 TASK / 3 PAGE / 8 INFERRED 待确认）→ design ✅ → contract ✅（`contract/ui.md` 特性 9 节）→ implement ✅（tdd 全局 blocked）。调研勘正：检索主链路直连真实后端（仅预处理两函数 mock 委托）；分析页无图表库（纯列表）；SearchTestPanel 死 emits；updateRetrievalLog 死代码；debug 页死入口（2026-09-21）

## 10. 知识图谱 — knowledge-graph

- **代码证据：** `detail/components/{KnowledgeGraphPanel 727, GraphSettingsPopup 213, NodeDetailPanel 225}`、`composables/{useKnowledgeGraph 124, useForceGraph 432（G6 力导）, useGraphExpansion 59}`；api graph 组；`@antv/g6` 依赖
- **可能 REQ 区域：** 图谱渲染/查询/扩展、实体合并操作、图设置
- **Depends on:** chunk-management
- **Shared by:** retrieval（graph expand 展示）、benchmark-evaluation
- **Depth signal:** ≈1.8k 行；G6 全站唯一重度可视化
- **Priority:** 高
- **Chain status:** implement — spec ✅（`spec/knowledge-graph-frontend.md`：9 TASK / 8 INFERRED）→ design ✅ → contract ✅ → implement ✅（tdd blocked）。发现：实体合并前后端均无（REQ 候选落空 GAP）；useKnowledgeGraph 平行实现（仅评测消费）；API 死代码 ×4；kbId 兜底三值；expand 双封装类型漂移（2026-09-21）

## 11. 评测与基准 — benchmark-evaluation

- **代码证据：** `detail/components/{EvaluationBenchmarkPanel 865, RagEvaluationPanel 830}`、`composables/{useEvaluation 149, useBenchmark 76}`；api 评测组
- **可能 REQ 区域：** 基准集管理、评测运行与结果展示
- **Depends on:** retrieval、knowledge-graph
- **Shared by:** 无明显下游
- **Depth signal:** ≈1.9k 行
- **Priority:** 中
- **Chain status:** implement — spec ✅（`spec/benchmark-evaluation-frontend.md`：8 TASK / 8 INFERRED）→ design ✅ → contract ✅ → implement ✅（tdd blocked）。发现：「上传基准」名实不符（文件不解析仅大小估算）；删除 catch 吞真实错误；孤儿 mock ×2 契约过期；详情状态 tag 硬编码 success（2026-09-21）

## 12. 发布、版本与知识审核 — publish-review

- **代码证据：** `detail/components/PublishPanel.vue`（779）、`views/knowledge-review/` 7 页 ≈2.1k（flows 494/flow-design 582/listeners 405/compliance 156/reports 161/quality 242/review-management 618——**侧挂孤儿路由，但全部走真实 @/api**）；`types/audit.ts`（118）
- **可能 REQ 区域：** 版本流转、定时发布、审核工作流（提交/批准/驳回）、审核流程/监听/合规/报告页面族
- **Depends on:** kb-core、platform-config（审核开关）
- **Shared by:** app-center（发布记录）
- **Depth signal:** ≈2.9k 行；7 页成族未接侧边栏（完成度与入口设计待确认 INFERRED）
- **Priority:** 中
- **Chain status:** implement — spec ✅（`spec/publish-review-frontend.md`：11 TASK / 6 INFERRED）→ design ✅ → contract ✅ → implement ✅（tdd blocked）。**重磅：PublishPanel 779 行被 `v-if="false"` 永久隐藏；7 页调用 27 个 src/api 不存在的函数（运行时 undefined）；compliance 有运行时 BUG；本特性无任何正常导航入口**——处置方案待用户裁决（2026-09-21）

## 13. 工具 / 技能 / MCP / 外部数据库 — tools-skills-mcp

- **代码证据：** `views/application/{my-tools 312, tool/create 61, tool/edit 106, tool/ToolForm, tool/ToolTestDialog}`、`{mcp-services 341, mcp/create 48, mcp/detail 542, mcp/edit 114, mcp/McpForm}`、`{skill-management 396, skill/SkillCreate 619, skill/SkillDetail 412, skill/SkillForm}`、`database-management.vue`（924）；api tools 11 / skills 21 / mcp 10 / databases 10 端点；mock 层多处**类型**消费（FolderNode、label 常量）
- **可能 REQ 区域：** HTTP 工具注册/测试、文件化技能（Markdown+frontmatter）、MCP 服务（stdio/SSE）与工具发现、外部 DB 挂载与 SQL 查询
- **Depends on:** iam
- **Shared by:** app-center（五类绑定的四类）、agent（工具执行）
- **Depth signal:** ≈3.9k 行 4 个管理页族；注意 `skill/create.vue`+`edit.vue` 为孤儿（路由用大写 `SkillCreate.vue`，技能无编辑路由）
- **Priority:** 高（是应用/智能体的能力底座）
- **Chain status:** implement — spec ✅（`spec/tools-skills-mcp-frontend.md`：5 TASK / 8 INFERRED）→ design ✅ → contract ✅ → implement ✅（tdd blocked）。**双高危 BUG：ToolTestDialog `resp` 未定义（工具测试成功必显失败）、SkillCreate reactive/value 崩溃（新建技能主流程不可用）**；SQL 工作台前端零校验；mock 常量/类型依赖 ×3（2026-09-21）

## 14. 模型与平台配置 — platform-config

- **代码证据：** `views/admin/platform/{model-management 567, api-keys 177}`、`views/admin/system/` 5 页（kb-config 253/general-settings 362/sensitive-words 231/dictionary 246/terminology 342）、`views/answer-kb/templates.vue`（108，路由 `/application/prompt-templates`——**整页运行时 mock + 权限表未登记**）、`stores/system.ts`（49，品牌配置）；api models 19 / config 15 / dictionaries 5 / sensitive-words 6 / terminology 8 端点
- **可能 REQ 区域：** 模型接入与连通性测试、系统参数、字典/敏感词/术语/查询规则四类治理、通知中心、提示词模板
- **Depends on:** iam
- **Shared by:** retrieval（query_rule）、app-center（模型选择）、publish-review（审核配置）
- **Depth signal:** ≈2.6k 行；admin/system 60 天 ×5 变更
- **Priority:** 中高
- **Chain status:** implement — spec ✅（`spec/platform-config-frontend.md`：8 TASK / 9 INFERRED）→ design ✅ → contract ✅ → implement ✅（tdd blocked）。发现：开放密钥页实管字典（前端生成密钥+明文展示 vs「不再显示」矛盾）；kb-config 两处假成功+行内编辑不生效；templates 整页 mock 且死链回退写错 store；models 死导出 ×5（2026-09-21）

## 15. 应用中心 — app-center

- **代码证据：** `views/application/index.vue`（258）、`editor/index.vue`（250）+ `editor/components/` 15 面板 ≈4.0k（AppChat 823/BasicConfig 344/KnowledgeConfig 315/DebugChat 286/DatabaseConfig 345/VmConfig 251/MemberConfig 243/McpConfig 212/DialogTest 214/ToolConfig 181/SkillConfig 178/ChatLogs 303/GlobalConfig 125/KnowledgeUpdate 74/DialogOptimize 77）、`app-config.vue`（578）、`runtime.vue`（1127，**双路由复用**）、`components/{ToolCallRenderer 199, ThinkingBlock 106}`；`composables/{useAppChatStream 378（SSE 流式 + tool_call 增量）, useConversation 174}`；api apps 组 **~89 端点（前端最大消费面）**
- **可能 REQ 区域：** 应用 CRUD/模板、对话配置、知识库/工具/技能/MCP/数据库绑定、触发器、对话测试与优化、SSE 对话与会话、应用发布
- **Depends on:** iam、retrieval、tools-skills-mcp、platform-config、workflow、agent（INFERRED：编辑器内智能体/VM 配置耦合度待确认）
- **Shared by:** agent（运行入口之一）、operation（会话分析）
- **Depth signal:** ≈6.5k 行 + 89 端点消费面 + 11 篇编辑器设计文档（views/application/editor/design/）
- **Priority:** 高
- **Chain status:** implement — spec ✅（`spec/app-center-frontend.md`：5 TASK / 10 INFERRED）→ design ✅ → contract ✅ → implement ✅（tdd blocked）。发现：**15 面板中 6 个零 import 孤儿**（含录制测试/VM 配置）；app-config 整页孤儿路由；11 个死 API 导出；应用发布 UI 缺位（publishApp 唯一消费在知识审核页）；SSE 无重连（2026-09-21）

## 16. 工作流编排 — workflow

- **代码证据：** `views/application/workflow-manage.vue`（525）、`types/workflow.ts`（108）；api workflows 组 33 端点
- **可能 REQ 区域：** 工作流 CRUD/发布、画布节点编排、执行/调试、测试用例、监控
- **Depends on:** platform-config
- **Shared by:** app-center（绑定）
- **Depth signal:** 33 端点消费 vs 前端仅 1 页（**UI 明显落后于 API**）
- **Priority:** 中
- **Chain status:** implement — spec ✅（`spec/workflow-frontend.md`：4 TASK / 5 INFERRED）→ design ✅ → contract ✅ → implement ✅（tdd blocked）。发现：33 端点中 **25 个死 API**（发布/执行/调试/用例/监控/模板/迁移/优化全无 UI）；逐节点保存吞错；路由/权限三处路径不一致；无侧边栏入口；types/workflow 死类型（2026-09-21）

## 17. 智能体运行时 — agent

- **代码证据：** INFERRED——`editor/components/VmConfig.vue`（251）、AppChat/DebugChat 对话组件；**独立 agent 管理页无路由**；api agent 消费并入 apps 组
- **可能 REQ 区域：** 智能体配置/运行/事件流展示
- **Depends on:** tools-skills-mcp、platform-config
- **Shared by:** app-center
- **Depth signal:** 前端面缺失（INFERRED，待 spec 阶段确认形态）
- **Priority:** 中高
- **Chain status:** implement — spec ✅（`spec/agent-frontend.md`：4 TASK / 5 INFERRED）→ design ✅ → contract ✅ → implement ✅（tdd blocked）。**结论升级 CERTAIN：无独立管理页 + api/index.ts 零 agent 端点 + 权限树自证「暂无对应菜单页面」**；agent_state 事件解析已实现无消费方；VmConfig/DebugChat 孤儿（2026-09-21）

## 18. 运营分析与系统监控 — operation-analytics

- **代码证据：** `views/operation/` 5 页（kb-analytics 195/model-monitor 181/feedback 564/qa-detail 147/retrieval-analysis 349）+ `operation/components/{MetricCards 58, WordCloud 40, RankList 38}`（消费方仅 feedback/model-monitor 两页）；`views/robot-operation/` 4 页 ≈324（**侧挂 + 瘦壳：getKbAnalytics 数据伪造填充**，faq-analysis.vue:3-30）；`admin/audit/system-log.vue`（162）；api feedback 5 / notifications 6 / audit-logs 3 端点；`types/{feedback 90, monitor 47}`
- **可能 REQ 区域：** 知识资产分析、模型监控、反馈管理、问答明细、检索分析、FAQ/多轮/意图分析、数据挖掘、审计日志
- **Depends on:** 全域（只读消费）
- **Shared by:** 无
- **Depth signal:** ≈2.0k 行；**Sidebar 跨模块拆挂**（kb-analytics/retrieval-analysis 挂「知识库」组，feedback/model-monitor 挂「应用」组，Sidebar.vue:104-108,143-147）；qa-detail 无菜单登记
- **Priority:** 中低
- **Chain status:** implement — spec ✅（`spec/operation-analytics-frontend.md`：7 TASK / 8 INFERRED）→ design ✅ → contract ✅ → implement ✅（tdd blocked）。发现：robot 三页伪数据（multi-turn 响应变量未使用、字段形状与 kb-analytics 冲突）+ data-mining 真实 API；指标卡颜色语义两处相反；死 API ×4；notifications/home 组零直接消费（勘正）（2026-09-21）

---

## 19. 会话中心与新建对话 — new-conversation

- **代码证据（复用面 + 改造点）：** `views/application/runtime.vue`（1127 行，旧运行页——保留兼容不重写）；`composables/useAppChatStream.ts`（378 行，SSE 唯一解析点，九类事件；**解析函数将抽取共享**）；`views/application/editor/components/AppChat.vue`（823 行，编辑器调试，旧通道不动）；KB 多选逻辑先例 `editor/components/KnowledgeConfig.vue`；模型下拉先例 `BasicConfig.vue:186-205`（online 过滤）；共享件 ThinkingBlock/ToolCallRenderer/usePagination。现状缺口：会话列表无分页/置顶/来源标记、createAppSession 无参、无起标题/停止端点、消息气泡与会话列表在 runtime/AppChat 两份平行内联
- **可能 REQ 区域：** 会话中心页（全量列表+置顶+来源标记+分页）、新建对话（KB 多选+模型+应用预设 chip）、请求态还原、停止生成、LLM 起标题、清空/批量删除、历史消息引用展示（双形态）
- **Depends on:** 后端 #19 new-conversation 契约（fastrag-backend contract/interfaces.md `## new-conversation`，2026-09-25 已齐）；特性 15 应用中心（入口改造：卡片▶运行、Sidebar 菜单）；特性 14（getModels 数据源）；特性 2（getKnowledgeBases 数据源）
- **Shared by:** 无（终端消费特性；runtime/AppChat 统一到新组件族登记为后续深化）
- **Depth signal:** 2026-09-25 spec+design 完成（10 TASK / 7 新组件 / 2 抽取共享件）；后端链同日 spec→contract 三阶齐
- **Priority:** 高
- **Chain status:** implement ✅（2026-09-25 全链闭环：spec ✅（6 INFERRED 已裁决）→ design ✅ → contract ✅（ui.md 特性 19 章，后端契约已实现）→ tdd ✅（vitest 17 用例：sse 分帧 7 + useConversationStream 5 + 既有 5；vitest.config 补 @ 别名）→ implement ✅（11 新文件 + 6 修改，vue-tsc 基线对比零新增错误、vite build 通过、10 TASK 全映射）。后端同日 M1+M2 落地（模块 71/71 绿）——**前后端代码齐备，待联调 + S0 人工验收**）

---

# 前端横切共享层

| 共享层 | 位置 | 消费方 |
|---|---|---|
| 请求解包/401 登出 | utils/request.ts（86 行；{code,data,message} 解包、401 清 storage 跳登录、403 仅提示） | 全部 API 调用 |
| API 单文件 | api/index.ts（2074 行、440 次调用）+ SSE fetch 直连（:249-263） | 全部页面 |
| 权限四层件 | types/auth.ts（MENU_PERMISSION_MAP）+ config/menu-perm-tree.ts + router 守卫 + v-permission/useAuth | 全部路由与操作按钮 |
| usePagination + BEM `__pagination` | composables/usePagination.ts（33 行）；18 个视图在用 | 全部分页列表 |
| 设计 token | assets/styles/variables.scss（九组）→ global.scss 全局注入（SFC 不直接消费） | 全局样式 |
| 自动导入 | vite.config.ts AutoImport + Components（ElementPlusResolver） | 全部 SFC |
| localStorage 封装 | utils/storage.ts（前缀 `ais_`） | user store 等 |
| mock 层 | src/mock/（30 文件；运行时真消费仅 3 处，16 模块死代码） | answer-kb/templates、QaPanel、query-preprocess |

# 孤儿与未接线资产（链外，待处置决策）

- **完全孤儿 11 文件 ≈2.5k 行**（无路由 + 全库零 import；2026-09-21 批次复核勘误：~~knowledge/form.vue~~ 非孤儿——create.vue:6 / edit.vue:6 import 共享表单体）：`admin/system/config-management.vue`（516）、`admin/content/{tags 402, templates 235, prompts 220, notification 160, download 47}`、`notification/index.vue`（197）、`admin/platform/third-party.vue`（177）、`admin/audit/login-security.vue`（170）、`application/skill/create.vue`（48）、`application/skill/edit.vue`（106）
- **死 mock 模块 16 个 ≈2.8k 行**（零外部引用，仅 mock 层内部自洽）：search-center 609、chunks 582、knowledge-review 248、test-cases 228、evaluation 227、benchmark 207、models 204、terminology 164、knowledge-produce 118、knowledge-update 102、knowledge-update-mgmt 86、auth-acl 77、robot-operation 68、graph-expansion 66、qa-pairs 63、kb-logs/interceptor（仅内部引用）
- **反向孤儿**：MENU_PERMISSION_MAP 登记 `/application/tools`、`/application/workflow`、`/application/test-cases`——路由不存在（菜单分组键遗留）
- **入口孤岛**：`/workspace` ↔ `/workspace/custom` 仅互链
- **杂项**：`detail/api-doc.vue` 8 行 stub（真实现 ApiDocPanel 挂 detail/index.vue）；`/operation/qa-detail` 无菜单；根目录 `nul`、`probe-shot-*.png` 探针遗留

# 路由→页面速览（按目录聚合；孤儿逐页判定见上节）

| 路由（前缀） | view | 菜单挂载 | 备注 |
|---|---|---|---|
| /login /register /forgot-password /403 | login|register|forgot-password|error | 无（登录前） | requiresAuth:false |
| /home | home/index | Sidebar「首页」 | |
| /workspace(/custom) | workspace/index, custom-workspace | **无** | 入口孤岛 |
| /knowledge, /knowledge/categories | knowledge/index, categories | Sidebar:98-99 | MAP:492-493 |
| /knowledge/create, /:id, /:id/{parse-strategy,edit,debug,chunks/:fileId,office/:fileId,api-doc} | knowledge/create, edit, detail/* | 无（hidden 详情族） | api-doc 为 8 行 stub |
| /application | application/index | Sidebar:126 | MAP:501 |
| /application/prompt-templates | answer-kb/templates | **无** | 整页 mock + MAP 未登记 |
| /application/my-tools(+create,/:id/edit) | my-tools, tool/* | Sidebar:135 | MAP:504 |
| /application/mcp-management(+…) | mcp-services, mcp/* | Sidebar:136 | MAP:505 |
| /application/skill-management(+create,/:slug) | skill-management, skill/Skill* | Sidebar:137 | MAP:506；create→大写 SkillCreate.vue |
| /application/database-management | database-management | Sidebar:138 | MAP:507 |
| /application/:id/{editor,config,workflow-manage}, /application/runtime, /:id/runtime | editor/index, app-config, workflow-manage, runtime | 无（Sidebar:127 挂 runtime） | runtime.vue 双路由复用 |
| /operation(→kb-analytics) + 4 页 | operation/* | 跨模块拆挂 | qa-detail 无菜单 |
| /admin(→system/kb-config) + system 5 页 | admin/system/* | Sidebar:167-171 | MAP:526-530 |
| /admin/account/*, /admin/permissions | account/*, permissions/index | Sidebar:180-183 | MAP:538-543 |
| /admin/platform/{model-management,api-keys}, /admin/audit/system-log | platform/*, audit/system-log | Sidebar:192,201-202 | MAP:551,559-560 |
| /knowledge-review/* 7 页 | knowledge-review/* | **无侧边栏** | 走真实 api |
| /robot-operation/* 4 页 | robot-operation/* | **无侧边栏** | 伪数据瘦壳 |

# 优先级与建议构建顺序（依赖前置 × 热度）

```
第 1 批（依赖根）:   1 iam
第 2 批（知识基座）: 2 kb-core → 3 parse-strategy → 4 file-ingestion → 5 chunk-management
第 3 批（当前热点）: 6 onlyoffice-editing → 7 ai-chunking
第 4 批（检索质量）: 9 retrieval → 10 knowledge-graph → 11 benchmark-evaluation
第 5 批（发布治理）: 8 qa-pairs → 12 publish-review
第 6 批（能力底座）: 14 platform-config → 13 tools-skills-mcp
第 7 批（应用层）:   16 workflow → 17 agent → 15 app-center
第 8 批（消费端）:   18 operation-analytics
```

## 20. 应用与对话体验优化 — app-chat-optimize（优化包，非标准链）

- **代码证据：** `utils/{markdown,sessionMarkdown,sessionGrouping}.ts` + `constants/promptTemplates.ts` + `editor/components/EffectivenessBar.vue`（新增 6）；MessageBubble/SourcesBlock/SessionListPanel/BasicConfig/四绑定面板/editor 菜单（改造）；**删除 4 孤儿面板**（VmConfig/DebugChat/DialogOptimize/KnowledgeUpdate，方案 A 裁决）；接线 GlobalConfig（安全策略）+ DialogTest（对话测试）
- **可能 REQ 区域：** 渲染安全（DOMPurify 单源）、来源分组+抽屉、复制会话 Markdown、绑定有效性预览（对接后端 effectiveness 端点）、提示词模板库、会话日期分组
- **Depends on:** 特性 15（editor/绑定面板）、特性 19（会话中心组件）、后端 bindings/effectiveness 端点（fastrag-backend app-chat-optimize Step 3a，已实现）
- **Shared by:** 无
- **Depth signal:** 2026-09-25 设计（五步）+ 实现单日完成；vitest 40/40（新增 23）；vue-tsc 基线净减 17；vite build 通过
- **Priority:** 中
- **Chain status:** implement ✅ 一批（design ✅ 五步蓝图 `design/app-chat-optimize-frontend.md`；tdd ✅ 23 新用例；Step 3b 方案 A 已裁决执行）→ 二批 ✅（2026-09-26 工具结果结构化渲染）：`utils/toolDisplay.ts` 三段路由 + `tool-results/{DatabaseQueryTable,KnowledgeChunksList}.vue` + ToolCallRenderer 卡片骨架（摘要行/三态/图标/标签回退），后端 structured 通道同步（agent/tools 模块）→ **三批 ✅（2026-09-26）**：会话汇总行（工具调用/轮数/耗时 chips）+ 来源角标 [N]（`utils/citeSources.ts` 标签段安全注入 + 抽屉受控 + 防幻觉编号）+ MCP/技能兜底形态（skill_md markdown 渲染/mcp 键值化）→ **收尾批 ✅（2026-09-26）**：EffectivenessBar 失效项一键解绑（资源 id→绑定行 id 换算）、历史轮数回显（iterations 列后端已落库）、组件级测试（@vue/test-utils + vue 插件入 vitest，MessageBubble 5 + EffectivenessBar 2）、孤儿 API 标记（6 函数保留待决策）。vitest 85/85、vue-tsc 零新增、G01 逐字节一致。剩余 backlog：角标句柄协议完整版/KaTeX+Mermaid/追问生成/@提及扩展。**前后端代码齐备，待联调 + S0 人工验收**

规则：每条链 spec → design → contract（UI 态矩阵 + API 数据映射）→ tdd → implement；孤儿与未接线资产不建链，待用户决策（接线 / 删除 / 归档 mock）。§20 为优化包例外：design 直达 implement，测试随步交付。
