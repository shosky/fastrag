# CONTEXT.md — FastRAG 领域术语表

> 本文件由 idd-grill 维护，所有模块文档共享此术语定义。

> **IDD Metadata**（由 idd-onboard 于 2026-09-16 检测写入；2026-09-21 刷新复核，Role 与 API Contract Source 不变）
> - **Repository Role:** `backend+frontend`（CERTAIN — 后端：Spring Boot 3.2.5 / 15 个 Maven 模块 / 55 个控制器 / 108 张表；前端：Vue 3 + vue-router + pinia + Element Plus / 60 条路由，双侧强信号）
> - **API Contract Source:** 本仓库运行时生成 — springdoc-openapi 2.5.0（`GET /v3/api-docs`、`/swagger-ui.html`，服务端口 8081）；仓库内无静态 OpenAPI 文件，控制器无手写 `@Tag`/`@Operation` 注解（fastrag-backend/pom.xml:157-162、fastrag-bootstrap/src/main/resources/application.yml:179-182）
> - **文档语言：** 中文

## 核心领域术语

**知识库（Knowledge Base）** — 用户创建的知识管理单元，包含文件、分片、QA 对、图谱等子资源。

**知识分片（Chunk）** — 文档经过解析和切分后的文本片段，是向量检索和图谱构建的最小单元。分为**父分片（Parent Chunk）**与**子分片（Child Chunk）**两级：子分片用于向量召回，父分片（按标题层级聚合）作为命中后的上下文返回。

**解析策略（Parse Strategy）** — 定义文档切分规则的配置，包含分片长度、重叠、分隔符、LLM/VLM 模型等参数。

**策略绑定（Strategy Binding）** — 文件与解析策略的关联。分为两级：知识库级默认绑定（按文件扩展名自动匹配）与文档级覆盖（用户显式指定，优先于自动匹配）。绑定必须与实际分片结果一致：变更绑定必然触发重新分片，不允许只改绑定不重切的漂移状态。

**自动匹配（Auto Match）** — 未指定文档级覆盖时，按文件扩展名在知识库内匹配解析策略的回退机制。

**重新分片（Re-chunk）** — 删除文档既有分片与向量后，按当前（或新指定的）策略绑定重跑「解析→分片→向量化」流水线的操作。原文档与 QA 对保留，图谱数据随分片重建。

**AI分片（AI Chunking）** — 知识库文件的一种加工工作流：预览原件 → OCR 解析 → 结构级跨页合并 → 自动（LLM）或手动分片 → 应用落库，从文件操作下拉「AI分片」进入，以用户确认的分片边界为唯一事实源替换落库。区别于"重新分片"（后者会重新解析原文件并重新切分，可能冲掉跨页合并与手工边界）。

**段落对齐模型（Paragraph Alignment Model）** — 后端产出的结构化段落序列，每段含 id/type/text/page/坐标/所属 chunk，作为 AI分片 的单一事实源，驱动「原件预览 ↔ 解析结果」的逐段联动（hover/点击双向高亮）。Markdown 是其序列化，Chunk 是其切分结果。

**结构级跨页合并（Structural Cross-page Merge）** — 解析层把被页边界/分页符割裂的同一逻辑块（表格、代码块、列表、段落、题注）并回完整结构的处理，使 Markdown 与 Chunk 不再被页边界割裂。

**向量检索（Vector Retrieval）** — 通过文本向量化后在 Milvus 中进行相似度搜索的检索方式。

**混合检索（Hybrid Retrieval）** — 向量检索 + 关键词检索 + 重排序（Rerank）组合的检索策略。

**知识图谱（Knowledge Graph）** — 从分片中抽取实体和关系构建的图结构，存储在 Neo4j Graph Store 中（唯一实现，启动 fail-fast）。

**抽取单元（Extraction Unit）** — 图谱抽取专用的重组文本块：把约 500 字的检索分片重组为约 2000 字符、逻辑完整的单元后送 LLM 抽取三元组，仅服务图谱抽取、不改变检索分片本身（ExtractionUnitAssembler，v1.2 架构）。

**实体合并（Entity Merge）** — 基于实体向量相似度发现合并候选（EntitySimilarity 余弦计算），将同义/同名实体合并入库并写入合并审计（kb_graph_merge_audit）；配套实体名清洗闸门（EntityNameGuard）拦截值型内容与 OCR 幻觉乱码入图。

**图谱评估（Graph Evaluation）** — 通过基准测试（Benchmark）和评测（Evaluation）衡量知识库检索质量。

**发布（Publish）** — 将知识库从编辑状态切换为线上服务状态，支持立即上线、下线、定时发布、版本回滚。

**应用（App）** — 面向终端用户的应用单元，绑定知识库、工具、MCP 服务等能力，提供对话式交互。

**工作流（Workflow）** — 应用内的可视化编排图，由节点（LLM、检索、重排序、模板、代码、条件、开始/结束）和边组成。

**智能体（Agent）** — 具有自主决策能力的 AI 实体，通过 Backend + Graph + Middleware + State 架构运行。

**工具（Tool）** — 可被应用或智能体调用的外部能力，分为内置工具、API 工具、MCP 工具、技能（Skill）四类。

**MCP 服务（MCP Service）** — 基于 Model Context Protocol 的外部服务，支持 stdio 和 SSE 两种传输方式。

**技能（Skill）** — 可导入导出的能力包，具有依赖声明和作用域（应用级/知识库级）。

**操作审计（Audit Log）** — 通过 @Loggable 注解 + AOP 切面自动记录的知识库操作日志。

**知识库权限（KB ACL）** — 基于 Redis 缓存的知识库级 RBAC，角色分为 owner > editor > viewer。

**查询增强（Query Enhancement）** — 对用户查询进行改写、同义扩展、规则应用等预处理，提升检索质量。

**更新提醒（Update Remind）** — 基于定时任务的文档新鲜度检查机制，提醒用户知识库内容可能过时。

## 门户术语（2026-10-24 首页门户特性，idd-grill 批次 1）

**首页门户（Portal）** — 产品的公开入口页：全宽、顶部主导航、无功能侧边栏，游客可浏览，登录后默认落地于此。Avoid: 营销首页、落地页、官网。

**平台（Platform）** — 现有登录后的全功能平台：AppLayout 壳（左侧边栏 + 顶部面包屑 + 卡片内容区）承载的功能路由整体，通过登录或门户按钮进入。Avoid: 工作台（已专指 /workspace 页面）、控制台、后台。

**操作手册（Manual）** — 门户资源菜单之一，内嵌渲染的用户手册页面，素材为 docs/manual.md（使用前须迁入版本控制并统一产品名为 FastRAG）。Avoid: 帮助文档、使用指南。

**产品动态（Changelog）** — 门户资源菜单之一，按版本倒序展示的功能更新记录页；一期内容为前端静态数据，无后端接口。Avoid: 更新日志、发版公告。

- 一个首页门户包含 N 个门户菜单（首页、操作手册、产品动态，菜单数组可扩展）；平台不设门户菜单——登录即进入平台（游客点「登录」，已登录在门户点「进入平台」按钮）
- 一个首页门户链接到一个平台（门户改造不触动平台内部；平台内部导航结构自 2026-10-26 起由「平台布局改造」特性链单独治理，见下方决策快照）

## 平台导航术语（2026-10-26 布局改造特性，idd-grill 批次 1）

**导航四段（Nav Segments）** — 平台侧边栏的顶级分组语义：对话（使用侧）→ 构建（生产侧）→ 资源（共享能力资产）→ 管理（治理侧），对标腾讯云 ADP「使用智能体/构建智能体/资源中心/平台管理」。Avoid: 功能域平铺（旧五模块「主页/会话中心/知识库/应用/管理」的组织方式）。

**资源中心（Resource Center）** — 导航四段第三段，汇聚被构建动作引用的共享能力资产：知识库、我的工具、MCP、技能、数据库、沙箱配置、模型管理、提示词模板。Avoid: 工具与服务（旧「应用>工具与服务」分组名）。

**通知中心（Notification Center）** — 平台内给用户推送系统消息的收件箱页（类型：审核提醒/发布完成/审核超时/知识更新/系统通知），页面 `views/notification/index.vue` 与后端 `/notifications` API 均已完整实现；2026-10-26 布局改造将其接线为顶栏铃铛。Avoid: 消息中心、站内信。

**单列宽侧栏（Single Sidebar）** — 平台导航目标形态：单列分组宽侧栏（分组标题+菜单项直排），替代旧「窄条 icon rail + 二级菜单面板」双列形态。

### 平台布局改造决策快照（idd-grill 2026-10-26，批次 1-3 锁定，盘问收尾）

1. 导航形态：窄条+二级面板双列 → **单列宽侧栏+分组标题**（ADP/Dify/Coze 主流式）；只换壳不动路由，门户链「60 条平台路由零改动」决策继续成立。
2. 顶级分组采纳**导航四段**，命名定为：**对话 / 构建 / 资源 / 管理**。映射：对话=会话中心；构建=应用中心（**2026-10-26 spec 期修正：原映射中的「业务流、测试案例」为权限树死条目**——`MENU_PERMISSION_MAP` 引用 `/application/workflow`、`/application/test-cases`，但路由与页面均不存在，工作流实际挂应用编辑器内 `/application/:id/workflow-manage`，二者不进菜单、权限键原样保留）；资源=知识库、知识库分类、我的工具、MCP、技能、数据库、沙箱配置、模型管理（自「管理>开放平台」移入，`admin:system` 门禁不变）、提示词模板；管理=系统管理、账号权限、安全审计、开放密钥 + 四处运营页（知识库分析/检索日志分析/反馈管理/模型监控）聚拢为「数据分析」。
3. 提示词模板（`/application/prompt-templates`）挂载到「资源 > 提示词」；孤儿路由 `/knowledge-review/*`、`/robot-operation/*` 本次**不挂载**，挂载留给各自特性链收尾。
4. 权限键零改动：`menu:*` 键、`MENU_PERMISSION_MAP` 键集、后端 `sys_permission` 存量全部原样保留；本次只动前端展示树（Sidebar 分组数据、`menu-perm-tree.ts` 分组节点标题），菜单分组名与权限键字面允许不再一一对应。
5. 主页顶置：侧边栏顶部（四段之上）单独一项「主页」（`/home`），四段从「对话」开始；登录落地 `/home` 维持门户链决策 #8 不变。
6. 通知中心接线（2026-10-26 批次 2）：新增 `/notification` 路由 + 顶栏铃铛（未读数徽标），全员可见、不设新菜单权限键，`api:notification:*` 留在权限树原位；页面复用 `views/notification/index.vue`。
7. 用户菜单扩容（2026-10-26 批次 2）：平台用户下拉扩为四项——用户信息 / 操作手册（→ `/portal/manual`）/ 产品动态（→ `/portal/changelog`）/ 退出系统；独立「反馈」入口不做（反馈提交留在应用对话内 `api:app:chat:feedback`）。
8. 工作空间切换不做（2026-10-26 批次 2，非目标）：FastRAG 无租户体系，组织管理为管理员侧组织树，不引入 ADP 式用户侧工作空间切换。
9. 演示工作台待清理（2026-10-26 批次 2）：`/workspace`、`/workspace/custom` 全硬编码演示数据且无任何入口引用，本次不删除不动，另行立项处置。
10. 链范围以壳为界（2026-10-26 批次 3）：侧栏、顶栏、用户菜单、通知接线进本链；任何页面内部结构（tab/列表/表单）不动，页面级形态优化另立特性链。
11. 「数据分析」层级（2026-10-26 批次 3）：管理段内的一个分组（管理 > 数据分析：知识库分析、检索日志分析、反馈管理、模型监控），四段总数不变，对齐 ADP「平台管理 > 数据报表」。
12. 非目标（2026-10-26 批次 3）：不引入 ADP 式文件级「生效范围」列（与 FastRAG 知识库级版本发布语义不同层，概念冲突）；知识库详情 8 tab 结构一期不收纳不图标化。
13. 库内组织维持文件夹（FileManager 现状），库间组织维持全局知识库分类页（`/knowledge/categories`，随资源段走）；不做 ADP 式库内左分类树。
14. 用户入口右上角与下拉收编（2026-10-26 实现期二次修订，ADP 截图 2 对标；同日三/四次迭代持续修订）：用户入口（头像 chip 点击弹出下拉）位于**内容卡头右上角**（通知铃铛右侧，ADP 式全局右上角），抽独立 `UserMenu` 组件承载；下拉 = 头部（姓名+@用户名）+ 固定四项（用户信息/操作手册/产品动态/退出系统）+ **「管理」分组**（管理域 12 项，2026-10-26 四次迭代自侧边栏移入，逐项权限过滤、全隐整组剔除）。权限键与权限树零改动。
15. 管理域下沉、数据分析回栏与侧栏折叠（2026-10-26 实现期四次迭代）：数据分析 4 项自下拉回到**侧边栏作为第四段**（段序：主页顶置 + 对话/构建/资源/数据分析）；侧边栏支持**折叠**——图标态 64px（复用 `$nav-strip-width`）+ 底部折叠/展开开关（Fold/Expand 图标），折叠态隐藏段标题与菜单文字仅留图标（原生 title 悬停提示），状态持久化 localStorage。权限键与权限树零改动。
16. 下拉语义分组与折叠悬停浮出（2026-10-26 实现期五次迭代）：用户下拉结构定为 头部（姓名+@用户名）→ 用户信息 → 「门户资源」分组（操作手册/产品动态，无权限键恒显）→ 「管理」分组（12 项权限门禁）→ 底部独立退出系统（danger，恒在最底）；折叠态交互为**悬停浮出**——鼠标悬停图标栏时在右侧浮出 208px 完整菜单（flyout 覆盖内容区，鼠标离开或点击菜单项即收回），替代图标 tooltip 方案。权限键与权限树零改动。
17. 管理分组级联子菜单（2026-10-26 实现期六次迭代，ADP「企业管理」式对标）：用户下拉内「管理」分组渲染为一行（标题+左箭头），悬停向左弹出 170px 子面板列出管理域 12 项（自绘绝对定位面板，行悬停展开、离行 150ms 收回、移入面板取消收回，点击子项跳转并关闭）；门户资源分组保持平铺。权限键与权限树零改动。
18. 知识库分类收敛进知识库页（2026-10-26 实现期七次迭代，ADP 知识库页对标）：侧边栏「资源」段移除「知识库分类」独立菜单项（资源段剩 8 项）；知识库列表页左侧分类面板升级为 ADP 式 CategoryPanel——全部/未分类/各分类（带计数徽标）+ 分类名搜索（独立于知识库搜索词）+ 面板折叠 + 行内分类管理（新增/编辑/删除，受 kb:category:* 权限，对话框含名称/描述/颜色/排序/所属组织[超管]）；原 `/knowledge/categories` 路由与页面保留但无菜单入口（待清理项，同演示工作台模式）。这是修订 #10「壳为界」的首个页面级例外，限知识库列表页。权限键与权限树零改动。

### 首页门户特性决策快照（idd-grill 2026-10-24，批次 1-5 锁定，供 /idd-spec 消费）

1. 「现在的入口」= 现有系统整体（AppLayout + 侧边栏 + 全部功能），成为门户的一个菜单（「进入平台」）；门户内不出现平台功能入口的第二通道。
2. 参考形态：通用开源项目「产品门户式」首页（hero 横幅 + 能力介绍 + 资源菜单区）；未指定具体对标项目。
3. 独立门户布局：新增 PortalLayout（全宽、顶部主导航、无侧边栏），与 AppLayout 平级并存、互不嵌套。
4. 操作手册：新增 `/portal/manual` 内嵌 Markdown 渲染；素材 docs/manual.md 使用前须迁入版本控制并统一产品名为 FastRAG。
5. 产品动态：新增 `/portal/changelog`，一期为前端静态数据，无后端接口；后续如需非开发角色维护再立项后端 CRUD。
6. 路由：顶层新增 `/portal` 路由族；`/` 改 redirect `/portal`；现有 60 条平台路由零改动。
7. 旧 `/home` 主页原样保留（含 `GET /api/home`、`menu:home`）；平台内 4 处「首页」显示文字改「主页」，「首页」一词专属门户。
8. 入口流跳转（2026-10-24 二次修订，「登录即进入平台」）：登录成功与已登录访问 /login 改指 `/home`（登录即落在平台内）；`/` 与 404 兜底落 `/portal`（游客与已登录用户的公共入口页）；平台内导航 6 处继续指 `/home`；回门户入口 = 平台侧边栏 logo 点击；`document.title` 后缀 `- AIS` 改 `- FastRAG`。
9. 门户三菜单公开访问（2026-10-24 用户改定，推翻当日批次 2 的「登录后才可见」）：未登录可浏览门户首页、操作手册、产品动态；顶导航仅三个菜单（首页/操作手册/产品动态，无「进入平台」菜单项），游客态显示「立即开始」按钮、已登录态显示「进入平台」按钮 + 用户名/退出（登录即进入平台）；「进入平台」不设为菜单项。门户不做菜单级权限门禁、不注册角色权限树。
10. 手册素材：一次性结构化为规范 Markdown（`##` 章 / `###` 节），单文件放 `fastrag-frontend/src/content/manual.md`（入 git），Vite `?raw` 打包 + marked 渲染，目录由标题生成。
11. PortalLayout 顶导航：左 logo+产品名（点击回 `/portal`）+ 菜单四项（首页、进入平台、操作手册、产品动态，当前路由高亮）；右侧用户名 + 退出登录（复用现有 logout 确认逻辑）。
12. hero 横幅：产品名 + 一句话价值主张 + 主按钮「进入平台」（→ 平台 `/home`）+ 次按钮「查看操作手册」；CSS 渐变背景，不引入图片素材。〔已被 2026-11-03 门户首页重构决策快照取代：白底左文右图真实截图 hero、四层文案〕
13. 能力介绍区：四张静态介绍卡（知识库/应用/智能体/工作流，图标+标题+一句话描述），点击统一进入平台 `/home`，不做按卡直跳。〔已被 2026-11-03 门户首页重构决策快照取代：双支柱行式呈现、行不可点击，进平台入口收敛四处〕
14. 资源区：操作手册卡与产品动态卡（含最新版本号徽标，取 changelog 静态数据首条），分别跳 `/portal/manual`、`/portal/changelog`。
15. 手册素材结构化为单文件规范 Markdown 后，手册页 = 左侧 sticky 目录（章/节两级、滚动联动高亮）+ 右侧内容；标题 id 用 `marked-gfm-heading-id` 生成；移动端目录折叠为顶部下拉。
16. 手册页复用单源渲染器 `renderMarkdown()`（src/utils/markdown.ts），只新增阅读排版样式类，不另开渲染路径。
17. 产品动态数据：`src/constants/changelog.ts` 导出数组，条目 `{ version, date, title, type, items[] }`，version 倒序，首条驱动首页徽标。
18. 产品动态页：el-timeline 版本时间线（版本号+日期+主题+类型标签+要点列表），v1 全量渲染不分页。
19. 产品动态 type 枚举四值：新功能/优化/修复/安全，前端常量 + Element Plus 语义色映射，不接后端字典。
20. 状态兜底：门户页面无 loading 态（零异步数据）；changelog 空数组 el-empty 兜底，种子数据非空列为 spec 验收项；手册渲染异常走 renderMarkdown 内建回退。
21. 响应式统一 768/1024 两档（跟随全仓主流断点）：1024 以下 hero 按钮换行、能力四卡 2×2、手册目录折叠；768 以下四卡单列、时间线收紧；顶导航窄屏横向滚动，不引入汉堡菜单。
22. 可达性基本规矩（仅约束门户新代码）：顶导航用真实 router-link、焦点顺序 = DOM 顺序、交互元素不用裸 div+click；不做 skip-link/ARIA 地标增强。
23. 数据映射终检：门户一期零后端接口、零 TBD 字段，API Contract 无新增契约；顶导航用户名沿用 `realName || username` 回退惯例（Sidebar.vue:24 同款）。

### 门户首页重构决策快照（idd-grill 2026-11-03 起，批次滚动，供 /idd-spec 修订 portal-frontend 消费）

1. 参考对标具体化（修订原快照 #2「未指定对标项目」）：门户首页（PAGE-001，/portal）重构对标 [Yuxi 官网首页](https://xerrors.github.io/Yuxi/)（VitePress 落地页，深青 #023944），按其分区逻辑重排信息架构。
2. 受众口径（批次 1）：使用者为主 + 轻量部署者内容——保留并强化「私有化部署」卖点，新增静态「技术栈」标签区；docker compose 快速开始代码块、模型供应商 icon 墙不上（开发者向重资产，模型接入面是运营配置非卖点）。
3. 重构范围（批次 1）：仅 PAGE-001 门户首页内容区（`views/portal/index.vue` 整页重写）；PortalTopNav/PortalLayout 壳零改动（ADR-PORTAL-INDEPENDENT-SHELL 拓扑不变）。
4. 重构深度（批次 1）：信息架构照 Yuxi 重排，非视觉换肤；顺带修复 0fc4cd1 三处既有缺陷——重复「核心能力」两分区（cap-grid+bento 各渲染同 7 卡）、资源区 v-for 引用未定义 `resources`（index.vue:246 整区渲染失败）、`@keyframes rise-in` 重复定义；现有分区与 public/portal 12 张真实截图作为素材池映射进新架构。
5. 视觉基调（批次 1）：保持平台蓝白底（$color-primary #2563EB、白底黑字克制用色、深色 CTA/页脚收尾纪律，modules/portal-frontend.md 视觉节）；借鉴 Yuxi 的分区逻辑与排版节奏，不搬深青色板；截图素材无需暗底改造，顶导航配色不动。
6. 分区清单（批次 2）：双支柱「知识引擎」+「应用与智能体」替代单一「核心能力」七卡堆（含重复渲染缺陷区）。知识引擎 6 条——多格式解析与 AI 分片 / 父子分片与引用溯源 / QA 对提炼 / 知识图谱 / 混合检索与重排 / 检索评估与运营分析；应用与智能体 5 条——应用与工作流编排 / 智能体 / 工具与技能生态（MCP 并入此条） / 沙箱隔离执行 / Widget 对话卡片；全部为 CONTEXT.md 已定义的真实领域能力，无虚构。
7. 既有分区去留（批次 2）：业务场景 ×4、为什么选 ×4、FAQ ×5、资源卡 ×2（修复 `resources` 未定义缺陷）、CTA、页脚全部保留；各分区最终呈现形式（截图卡/列表/pill 标签）批次 4 定。
8. 分区顺序（批次 3）：hero → 知识引擎 → 应用与智能体 → 工作原理（四步）→ 为什么选 → 业务场景 → 技术栈 → 资源卡 → FAQ → CTA → 页脚。双支柱内知识引擎在前（RAG-first，衔接 hero 主张「让企业知识 一问即答」，与 Yuxi 的 agent-first 序相反）；资源卡与 FAQ 的相对位置沿用现状。
9. 工作原理（批次 3）：三步扩四步——01 上传解析 → 02 知识加工 → 03 创建应用 → 04 对话溯源，每步一句话描述。被否：保持三步（上手叙事漏「知识加工」环节，与知识引擎支柱脱节）。
10. 技术栈标签区（批次 3）：6 项静态标签 **MySQL / Redis / RabbitMQ / Neo4j / Milvus / MinIO**（docker-compose.yml 硬依赖四项 + milvus standalone/minio 服务与 MINIO_HOST 注入实证）+ 一句定位文案；不做 icon 滚动动画（决策 2 轻量口径）；位置在业务场景之后、资源卡之前。
11. 双支柱呈现形式（批次 4）：Yuxi 式子能力行（标题+一句话描述），每支柱选 1 项配真实截图作「实拍锚点」（知识引擎：知识图谱或混合检索；应用与智能体：应用编排或 Widget；具体映射在 spec 期锁定）；整页真实截图集中由 hero（1 张）+ 业务场景区（4 张）承担。被否：全截图卡 bento（维护负担，Yuxi 无此形）。
12. pill 标签（批次 4）：仅应用与智能体 5 条各挂 1-2 个静态 pill（例：应用编排「可视化节点 / 零代码」、沙箱「多后端隔离」、Widget「19 组件」，终稿文案随 spec）；知识引擎 6 条不挂（Yuxi 分工：运行时挂 pill、知识引擎纯列表）。
13. 截图清理（批次 4）：新引用清单锁定后删除 public/portal 未引用截图（现知未引用 3 张：crop-chat.png、scene-analytics.png、scene-chat.png——scene-chat 为原 spec 旧 hero 图，已被 crop-chatcolumn.jpg 取代；git 历史即归档）；modules.md 的 cap-*.svg / step-*.svg 插画描述按事实修正（public/portal 目录无任何 SVG）。
14. 工作原理呈现（批次 4）：四步照现状纯编号卡（数字+标题+描述），不补插画（Yuxi 同形）。
15. 技术栈形态（批次 4）：纯文字 pill 自绘（复用现有设计语言，零新素材、零新依赖）。被否：官方 logo 墙（素材长期维护不值，与决策 2 轻量口径冲突）。
16. 双支柱行点击行为（批次 5）：双支柱行不可点击（纯展示，Yuxi 同形，整行长文本不做点击目标）；进平台入口收敛为——hero 主按钮、CTA 主按钮、顶导航按钮、业务场景卡与资源卡整卡点击；spec TASK-002「任一能力介绍卡直达 /home」口径随之收窄修订。
17. 动效基线（批次 5）：收敛四项——hero rise-in 交错入场、分区级 .reveal 滚动渐显（threshold 统一 0.15）+ 网格交错延迟、卡片 hover 语言、CTA 点阵 dots-drift；不实现 modules.md 视觉节宣称但代码不存在的 float-y/orb-drift/cta-pulse/打字点闪烁（漂移修正归批次 7）。`prefers-reduced-motion` 全禁用与焦点顺序=DOM 顺序延续 spec 既有决策，不放松。
18. hover 分工（批次 5）：卡片类（业务场景/资源/为什么选）沿用现 hover 词汇零改动；双支柱行 hover 底色微亮（#FAFAFA 级）；四步卡保持现状；pill 与技术栈标签无 hover（非交互元素不做交互反馈）。
19. hero 大标题（批次 6）：主句「让企业知识 一问即答」保留（与产品名/FAQ/CTA 叙事连贯）；补实现 modules.md 视觉节已宣示但代码缺失的「一问即答」蓝青渐变文字。备选未采纳：排比句式「可检索、可对话、可溯源」（Yuxi 味更浓但稀释即时性主张）。
20. hero 四层结构（批次 6）：新增副标题层「企业级检索增强生成（RAG）平台」承载品类定义；徽章改「私有化部署 · 开箱即用」（部署卖点提前首屏，呼应决策 2 受众口径）；描述段保留现骨架微调；具体字句 spec 定稿。
21. 双支柱分区标题（批次 6）：知识引擎——眉标「知识引擎」、标题「从文档到可问答的知识资产」；应用与智能体——眉标「应用与智能体」、标题「不止于对话，更能编排与执行」（Yuxi 的「执行与交付」换 FastRAG 真实词「编排」，对齐工作流叙事）。
22. 标题硬伤微调（批次 6）：工作原理标题与 CTA 副句「三步」→「四步」（决策 9 推论）；技术栈标题按决策 10 方向（字句 spec 定）；业务场景/为什么选/资源/FAQ 标题不变；子能力描述句与 pill 文案等具体字符串留 spec 定稿。
23. spec 修订范围（批次 7）：修订 spec/portal-frontend.md——PAGE-001 元素区按新 IA 重写、TASK-002 收窄为四处入口、响应式节按新布局重写（「能力四卡 2×2/单列」与代码 bento 3 列 7 卡的漂移一并修正）、验收清单增删（新增双支柱/技术栈区/四步区/资源卡恢复渲染等项，删 bento×7 相关项）；三页路由、公开访问、手册/动态、零接口数据映射、可达性各节零改动。走 /idd-spec 改既有文档，不新建。
24. ui.md 与 modules.md 同步（批次 7）：归本链——ui.md 首页门户节在 /idd-spec 期按新 IA 重写（现约 10 行，落后于 spec 与代码）；modules.md 在实现完成期修正漂移（布局图旧 IA、视觉节动效清单按决策 17 收敛、56px↔代码 64px 不一致、渐变宣示随决策 19 落地、SVG 插画描述删除、代码节 index.vue 描述更新）；不另立文档链。
25. 旧快照取代关系（批次 7）：门户特性决策快照 #12/#13 条目内加「已被本快照取代」标注、原文保留作历史（项目惯例：条目内修订注明，快照 #8/#9 先例）；#14 资源卡决策继续有效。
26. 缺陷归属（批次 7）：三处代码缺陷（重复核心能力区/`resources` 未定义/rise-in 重复定义）随整页重写消失，不立缺陷单；验收补「资源分区正常渲染、徽标显示 changelog 首条版本」专项防回归；分区清单以修订后 spec 元素区为唯一事实源。

## Widget 术语（2026-10-30 Widget 特性，idd-grill 批次 1 起）

**Widget** — 嵌入应用对话窗口的可交互自定义展示卡片：由模板/数据结构/默认值三件套定义资产，智能体输出结构化 JSON 填充渲染，用户在卡片上的操作以结构化数据回传对话以继续流程。对标腾讯云 ADP Widget。Avoid: 卡片（Card 是 Widget 内的组件类名，非 Widget 本身）、富文本、表单页。

**Widget 模板（Widget Builtin Template）** — 平台预置的官方只读 Widget 示例，仅可查看与复制，复制后生成独立 Widget 资产才可在应用中使用。Avoid: 模板（Template，指 Widget 内组件编排 JSON，另一物，见下）。

**模板（Template）** — Widget 三件套之一：以组件树编排 Widget 布局/样式/交互的 JSON，组件属性可通过变量引用由数据填充。Avoid: Widget 模板（指预置只读示例，另一物）、页面模板。

**数据结构（Schema）** — Widget 三件套之一：对模板中引用变量做类型与格式声明的 JSON，是运行时数据校验与默认值填充的依据。Avoid: JSON Schema（指标准规范全量能力，本特性仅取变量声明子集）、接口契约。

**默认值（Default）** — Widget 三件套之一：为模板变量提供占位数据的示例 JSON，未输入实际数据时用于预览与展示兜底。Avoid: 缺省值、初始值。

**render_widget 工具** — 内置工具：LLM 经工具调用输出 Widget，携带 {widgetId, widgetKey, data}，后端按 id 加载资产、Schema 校验后返回模板快照透传前端渲染。Avoid: widget 事件（本特性复用 tool_result.data 通道，无专用 SSE 事件）。

**Widget 回传（Widget Action）** — 用户在卡片上的操作以结构化负载回传：两通道请求体并行字段 `widgetAction{widgetId,widgetKey,action,payload}`，编排层合成可见用户消息并将结构化块注入引擎上下文作为后续任务输入。Avoid: 回调、按钮事件、onSubmit。

**交互窗口（Interaction Window）** — 仅最新一条助手消息上的 Widget 卡片可交互的时间窗：其后任何新消息到达即把更早卡片的交互动作置灰（`alwaysEnabled=true` 的动作豁免）；判定纯前端按消息序。需求原文「对话阶段」收窄为此可判定语义。Avoid: 对话阶段（不可判定的模糊表述）、会话级状态机。

**Widget 组件（Component）** — 构成 Widget 模板的最小功能单元，一期 19 个、分容器/布局/文本/内容/交互/表单六类；前后端各持一份名称白名单、测试钉住一致，未知组件渲染降级占位。Avoid: Vue 组件（实现载体，非领域词汇）、控件。

**Widget 动作（Widget Action）** — 卡片交互动作的统一词汇：`ActionConfig{type, payload, alwaysEnabled?}`，一期三枚举 `sys.go_to_url / sys.download / sys.submit`（submit 即 Widget 回传触发器）。Avoid: onClick 事件、按钮动作（混称 Card.confirm/cancel 同词汇）。

**卡片替换键（widgetKey）** — LLM 调用 render_widget 时自拟的卡片标识：同 key 的卡片在流内到达即替换旧卡（过程反馈场景的进度推进），跨消息则为新卡。Avoid: 卡片 ID（widgetId 是资产标识，另一物）、消息 ID。

**Widget 开发工作台（Widget Workspace）** — 资源段 `/widget` 页面族：列表页（我的 Widget / 官方模板二 tab）+ 新建页 + 编辑页（三件套 JSON 三 tab + 预览面板），照技能三路由惯例。Avoid: Widget 模板库（专指官方模板 tab）、可视化编辑器（一期不做）。

- 一个 Widget 资产归属一个创建者（属主隔离，管理员全可见）；一个 Widget 模板复制生成一个独立 Widget 资产；内置行不可直接绑定应用〔批次 2，2026-10-31〕

### Widget 特性决策快照（idd-grill 2026-10-30 起批次滚动，2026-10-31 盘问收尾，供 /idd-spec 消费）

1. 特性定位：全仓第 22 项特性，特性名 `widget`，跨前后端**单链**（spec/design/contract/tdd 同链覆盖双侧），文档落 `fastrag-backend/docs/` 既有文档树（spec/widget.md 等），术语进本表；前后端零存量 widget 代码，纯绿地。
2. 一期范围（中档）：Widget 资产 CRUD + 模板（Template）/数据结构（Schema）/默认值（Default）手写 JSON + 覆盖四场景（澄清询问/过程反馈/信息确认/结果展示）的组件子集 + 对话内渲染与操作回传 + 应用绑定 + 预置少量官方只读 Widget 模板（可复制）。**不做**：可视化拖拽编辑器、自然语言生成 Widget、Widget 文件导入导出。
3. 消费通道：Widget 跟随应用预设挂载生效——凡编排链上挂了应用的对话（应用调试对话 + 会话中心挂应用的会话，走同一 AppServiceImpl.executeDialog）都渲染；纯直聊（不挂应用）不生效；工作流通道一期不碰（执行引擎占位）。
4. 导航挂位：资源段第 9 项「Widget」（`/widget`），权限键三段式 `widget:*` 预留、暂不强校验（与技能页同待遇）。
5. 主表〔批次 2，2026-10-31〕：新表 `widget`——String ASSIGN_ID 主键、`template/schema/default` 三个 JSON 列（JacksonTypeHandler）、`is_builtin/creator/created_at/updated_at`；不建辅表；DDL 双处同步（schema.sql ↔ SchemaInitializer）。
6. 归属〔批次 2〕：属主隔离 v1——`creator` 归属 + 列表按属主过滤 + 管理员全可见（sandbox_config 范式）；分享治理（部门/全员）登记为后续扩展。
7. 生效语义〔批次 2〕：无版本无发布流，保存即生效，运行时按 widget id 实时读取；已发出消息的卡片按消息内嵌快照渲染，不受后续编辑影响（快照协议由批次 3 落地）。
8. 模板库〔批次 2〕：模板库 = `widget.is_builtin=1` + `creator='system'` 行，内容为代码内置常量，`WidgetBuiltinSeeder` 启动幂等播种（DB 无记录时插入、不覆盖，SkillDataInitializer/McpBuiltinSeeder 先例）；内置行只读仅提供「复制」，复制 = 新建 `is_builtin=0` 行拷贝三件套、属主当前用户；内置行不进应用绑定候选。
9. 保存校验〔批次 2〕：后端保存时三档结构校验——JSON 合法性；模板组件名在白名单内且必填属性齐全；schema 变量与模板引用一致、default 可按 schema 反序列化。组件白名单前后端各持一份常量、以测试钉住一致（SkillDdlParityTest 式守护）；校验失败 = 业务错误（HTTP 200 + body.code）行级报错。
10. 触发与装配〔批次 3，2026-10-31〕：`render_widget` 内置工具（静态 ToolDefinition 照 InstallSkillToolDefinition + BuiltinToolExecutor）+ 新 `WidgetMiddleware`——应用绑定 Widget 非空时注入「Widget 清单」system 块（id/名称/用途/Schema/默认值）并条件加工具；执行 = 按 widgetId 加载 → Schema 校验 → `structured={widgetId,widgetKey,name,template,data}` emit 时快照；校验失败回「Error:…」文本由 LLM 自纠。
11. 传输〔批次 3〕：复用 `tool_result.data` 通道，零新 SSE 事件；前端 toolDisplay 增 `render_widget` 路由 → WidgetCardRenderer；该工具 calling 态 chip 隐藏或轻量占位。专用 `widget` 事件弃选，事件语义膨胀时再立项升级。
12. 消息模型〔批次 3〕：快照内嵌 toolCalls JSON 列，零新消息列；历史经 ToolCallRenderer 重放自动渲染，与实时同构。
13. 回传〔批次 3〕：`AppChatRequest` 与会话流请求体各加 `widgetAction{widgetId,widgetKey,action,payload}`；编排层合成**可见用户消息**（文本摘要，如「（来自 Widget「订单确认」）选择：堂食，已确认」）落库展示，结构化负载以 `[WIDGET_ACTION]` 块注入本轮引擎上下文。
14. 交互窗口〔批次 3〕：仅最新一条助手消息上的卡片可交互；其后新消息到达即置灰（`alwaysEnabled=true` 豁免）；判定纯前端按消息序，后端零状态机、零失效表。
15. 组件清单〔批次 4，2026-10-31〕：一期 19 个——容器 3（Card/ListView/ListViewItem）、布局 4（Row/Col/Divider/Form）、文本 4（Title/Text/Caption/Markdown）、内容 3（Badge/Icon/Image）、交互 1（Button）、表单 4（Input/Select/RadioGroup/Checkbox）；Clickable 由 ListViewItem.onClickAction 替代、BasicRoot 由模板根隐式替代；其余（Box/Spacer/Label/Video/Audio/Countdown/Textarea/DatePicker/ImageSelect/Transition/Chart）后置，白名单与注册表预留扩展位；过程反馈 = Card.status 文本 + widgetKey 替换推进。
16. 模板 DSL〔批次 4〕：JSON 树 `{type, props, children[]}`；字符串 prop 支持 `{{varName}}` 插值；渲染数据 = default 先填充、data 覆盖；无表达式/条件模板语法（条件分支交 LLM 换模板或换数据）。
17. 渲染架构〔批次 4〕：`widgetComponents/` 目录每组件一个 SFC + 注册表 map，WidgetCardRenderer 递归渲染；未知组件名 fail-soft 占位不崩整卡；后端白名单 ↔ 前端注册表测试钉一致。
18. 安全〔批次 4〕：除 Markdown 组件经 renderMarkdown（DOMPurify 单源）外禁 v-html、一律文本节点/属性绑定；Image src 仅 http(s) 绝对地址（javascript:/data: 拒绝、渲染占位）；模板 props 值即数据、永不解释为代码。
19. 动作词汇〔批次 4〕：`ActionConfig{type, payload, alwaysEnabled?}`，一期三枚举 `sys.go_to_url`（新窗跳转）/`sys.download`（触发下载）/`sys.submit`（回传智能体、表单值并入 payload）；Card.confirm/cancel 与 Button 同词汇；未知 type 忽略 + console.warn，不崩卡不误跳。
20. 工作台页面〔批次 5，2026-10-31〕：三页独立路由 `/widget`、`/widget/create`、`/widget/:id`（Skill 三路由惯例，routes.ts:229-249 同型），共享表单组件；API 封装照 api/index.ts 命名导出惯例。
21. 编辑器〔批次 5〕：三件套 JSON 文本域三 tab（模板/数据结构/默认值）+ 格式化按钮，保存校验失败行级错误回显对应 tab（不弹窗）；右侧预览面板复用 WidgetCardRenderer、Default 填充静态渲染、sys.submit 禁用并提示「请在应用对话中调试」；不引入 monaco/codemirror 等代码编辑器依赖。
22. 官方模板呈现〔批次 5〕：`/widget` 列表页「我的 Widget / 官方模板」el-tabs 二选一；官方 tab 只读 + 「复制」按钮（成功跳转新行编辑页）；无独立模板路由页——模板库 = 同表的 is_builtin 过滤视图。
23. 删除保护〔批次 5〕：被应用绑定的 Widget DELETE 时服务端拒绝（业务错误提示「先在应用配置中解绑」）；前端删除前 confirm；运行时 widgetId 查不到 fail-soft 不渲染卡片，仅作脏数据兜底。
24. 工作台状态矩阵〔批次 5〕：列表骨架屏 loading；空态二分（我的 Widget = 「新建 / 从官方模板复制」双引导，官方 tab = 播种失败才可见的兜底空态）；接口失败 toast + 重试；权限键 `widget:*` 预留不强校验、页面不入权限树（与沙箱配置/提示词模板同待遇）。spec 期落 STATE-* 条目。
25. 绑定表〔批次 6，2026-10-31〕：第六类绑定 `app_widget_binding{id, app_id, widget_id, enabled, created_at}`，端点 `/{appId}/widgets` GET/POST/PUT/DELETE 照五兄弟惯例（AppConfigController.java:90-130 同型），权限 `app:edit`；绑定 N:N；DDL 双处同步。
26. 候选与上限〔批次 6〕：绑定候选 = 当前用户自建 Widget（is_builtin=0、属主可见，管理员另加全员可见，与工作台列表口径一致）；单应用绑定上限 10，超限业务错误提示（保「Widget 清单」system 块体积可控，对齐 skillNames 上限 10 先例）；per-binding `enabled` 开关，禁用不注入不渲染。
27. 编辑器挂点〔批次 6〕：应用编辑器「配置」组新增 `{key:'widget', label:'Widget'}` → `editor/components/WidgetConfig.vue`（SkillConfig 181 行模式：候选卡片列表 + checkbox + enabled 开关 + 保存 diff 增删）；菜单结构其余不动。
28. 运行时装配〔批次 6〕：`EffectiveDialogConfig.Bindings` 增加 widgets；挂应用的会话经 DialogPresetResolver 同链自动获得绑定（兑现快照 #3「跟随应用预设挂载」）；WidgetMiddleware 从 BaseContext 判定非空 → 注入清单 + 注册工具；纯直聊 widgets 恒空，中间件静默跳过。
29. 调试体验〔批次 6〕：应用编辑器调试对话与对话测试即真实环境（render_widget 注册/卡片渲染/交互窗口/回传全走同一链路），零新增调试组件；仅 AppChat.vue 补 widgetAction 字段透传（与快照 #13 同一次改动）；不另做 Widget 专项调试器（工作台静态预览 + 编辑器真实调试两级已覆盖）。
30. 渲染降级矩阵〔批次 7，2026-10-31 用户授权代裁〕：未知组件名/模板结构坏 → 卡内占位不崩整卡（console.warn，快照 #17 的 fail-soft 全卡化）；widget 资产已删仍可渲染历史卡（快照内嵌自包含，#12）；Image 加载失败渲染占位图；Markdown 异常走 renderMarkdown 内建回退；tool_result 中 data 缺失/结构不符 → 占位 + console.warn，不影响正文渲染。
31. 回传失败与防重〔批次 7，代裁〕：回传复用既有 SSE error 处理——失败不落新消息、消息序未推进，交互窗口未关闭，卡片保持可交互可重试；submit 点击后立即置 loading 禁止重复提交直到下一轮开始。
32. 体积护栏〔批次 7，代裁〕：render_widget 工具参数 data 序列化 ≤ 32KB，超限工具回 Error 由 LLM 自纠（对齐对话审计工具输出 32KB 口径）；widgetAction.payload ≤ 16KB 超限业务错误拒绝；「Widget 清单」system 块沿用注入截断 5000 字符口径（IF-003 同款，超限按序截断并 warn）。
33. 权限与越权〔批次 7，代裁〕：Widget CRUD 属主/管理员服务层判权（sandbox_config 范式，越权返回业务错误）；复制内置模板登录即可；绑定管理 app:edit；回传沿用两通道端点既有鉴权（app 通道 app:use / 会话通道登录），不新增鉴权面；**运行时 render_widget 校验 widgetId ∈ 本次绑定集合**，集合外拒绝并 Error 回灌 LLM（fail-closed，防 prompt 注入诱导渲染任意 widget，对齐 KB_HANDLE_MAP fail-closed 先例）。
34. 可达性〔批次 7，代裁〕：仅约束本特性新代码——卡片交互元素一律真实 button/input 元素（Element Plus 组件天然可达）、焦点顺序 = DOM 顺序、交互元素不用裸 div+click；不做 skip-link/ARIA 地标增强（门户链快照 #22 口径沿用）。

## 技术术语

**SSE（Server-Sent Events）** — 服务端推送事件流，用于 LLM 流式输出和 Agent 实时响应。

**Graph Store** — 知识图谱存储抽象接口，唯一实现为 Neo4jGraphStore（ADR-0002 已落地：MySQL 实现已于 2026-09 删除，Neo4j 为必需依赖，启动 fail-fast）。

**Vector Store** — 向量存储抽象接口，当前仅有 Milvus 实现。

**Ingestion Pipeline** — 文档摄入流水线：上传 → 解析 → 切分 → 向量化 → 存储，通过 RabbitMQ 异步执行。

## 命名冲突与勘误（idd-onboard 检测，2026-09-16）

- **产品命名漂移（INFERRED）**：manual.md 与前端 `document.title` 后缀使用「AIS」，index.html 标题为「AI知识仓」，package.json 包名为 `fastrag-demo-tmp`，代码与 AGENT.md 使用 FastRAG —— 四套命名并存，需统一。
- **菜单标题重复（CERTAIN）**：`/operation` 与 `/robot-operation` 的侧边栏标题均为「运营中心」（fastrag-frontend/src/router/routes.ts）。
- **孤儿路由（CERTAIN）**：`/knowledge-review/*` 与 `/robot-operation/*` 未接入侧边栏导航（fastrag-frontend/src/config/menu-perm-tree.ts:15-18 自述）。
- **一致性确认（CERTAIN）**：AGENTS.md 分页规范 ↔ 后端 `PageResult{list,total,page,pageSize}` ↔ 前端 `usePagination`（19 个视图在用）三方一致，无冲突。

### 勘误补充（idd-onboard refresh 2026-09-21）

- **feature-entry-map.md 分支归属（CERTAIN）**：`docs/feature-entry-map.md`（功能清单→前端入口对照表）描述的是 `gaosu` 分支（2044d86「功能点补齐 + 真实联调修复 + 演示数据初始化」，2026-09-17）的功能面；`baseline` 分支（IDD 地图基准）不存在 sync.vue / knowledge-manage.vue / smart-search.vue / tags-notes.vue 等页面。合并 gaosu 前不得据其更新特性地图。
- **docs/ 未纳入版本控制（CERTAIN）**：`.gitignore:33` 忽略整个 `docs/`——IDD 文档链（feature-map / spec / sessions 等）均无 git 保护。建议改为仅忽略 `docs/legacy/FastRAG-API-Doc-Tab-Implementation-Plan/node_modules/` 一类构建产物（处置待用户决策）。
- **分支态势（CERTAIN）**：`baseline` 领先 `master` 20 个提交（master 停在 06-27）；`gaosu` 在 09-15/09-17 另有功能补齐批次，合并时需与 baseline 对账。
