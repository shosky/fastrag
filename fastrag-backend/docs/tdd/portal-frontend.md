# 首页门户前端（portal-frontend） — 测试设计

> 对应契约：docs/ui/ui.md（UI-UI：UI 契约 / 状态契约 / 数据映射 / API 依赖）与 docs/modules/portal-frontend.md `## Contract` 单块。**本文件为测试设计，红绿循环未执行**（由 /idd-implement 按本设计执行）。

## 1. 切片规划

| 切片 | 用户任务 | 测试层 | 覆盖状态 | 执行性 |
|---|---|---|---|---|
| S1 路由与入口流 | TASK-001 | Page/路由守卫 | 公开访问 + 入口流重定向 + title | AFK |
| S2 顶导航与门户首页 | TASK-002 | Component | 页面元素 + 高亮 + 菜单跳转 + 徽标派生 | AFK |
| S3 操作手册页 | TASK-003 | VM/adapter + Component | STATE-001 + 目录联动 + 素材规范 | AFK |
| S4 产品动态页 | TASK-004 | Component | STATE-002 + 时间线 | AFK |
| S5 平台侧回归 | TASK-005 | Component | logo 回门户 + 文字改名 + 路由不变量 + 权限零注册 | AFK |
| S6 浏览器关键路径 | TASK-001/002 | Browser | 登录→门户→平台 round trip + 断点档位 | 条件（HITL） |
| S7 视觉与键盘走查 | — | Visual/a11y | hero 视觉比对 + Tab/Enter | HITL |

选择原则：每切片取「能证明该行为的最浅层」——数据转换下沉到 VM/adapter 层（S3.1），交互与状态用组件挂载（S2-S5），仅关键用户路径进真实浏览器（S6）。

## 2. 测试用例（逐切片）

### S1 路由与入口流（契约锚点：docs/ui/ui.md 状态契约「显式不适用」；SPEC-PORTAL-FRONTEND 验收 1-3、14）

文件：`src/router/routes.test.ts`

- T1.1 已登录访问 /login → 重定向 /home（登录即进入平台）
- T1.2 未登录访问 /portal → 正常渲染门户首页（公开访问，不重定向）
- T1.3 任意未知路径 → 重定向 /portal
- T1.4 `/` → redirect /portal
- T1.5 路由切换后 document.title 后缀为「- FastRAG」

Mock：仅 `@/utils/storage`（node 测试环境无 localStorage，沿用现仓 `vi.mock` 惯例）；路由器用 createWebHistory 换 memory history 的真实实例，不 mock vue-router。

### S2 顶导航与门户首页（契约锚点：docs/ui/ui.md「顶导航」「PAGE-001」）

文件：`src/components/portal/PortalTopNav.test.ts`、`src/views/portal/index.test.ts`

- T2.1 渲染三个菜单项，文字依次为 首页 / 操作手册 / 产品动态（无「进入平台」菜单项）
- T2.2 当前路由对应菜单项带高亮类，其余不带
- T2.3 logo 点击 → /portal
- T2.4 已登录显示「进入平台」按钮、用户名（realName 缺失回退 username）与「退出登录」；游客态显示「立即开始」按钮、不显示用户名
- T2.5 退出：先弹确认框；确认 → 调 userStore.logout 且跳 /login；取消 → 停留原地
- T2.6 hero 主按钮按登录态：游客显示「立即开始」→ /login；已登录显示「进入平台」→ /home；次按钮 → /portal/manual
- T2.7 四张能力卡点击行为一致 → /home
- T2.8 资源卡「操作手册」→ /portal/manual；「产品动态」→ /portal/changelog 且徽标 = `changelog[0].version`；changelog 为空时徽标不渲染（STATE-002 派生）
- T2.9 遍历 portalMenus 数组：每个菜单项渲染出的 router-link 目标 `to` 与模块文档定义的 path 一一对应（/portal、/portal/manual、/portal/changelog）
- T2.10 游客点击顶导航「立即开始」→ /login（进入平台的登录前置）
- T3.8 分组目录交互：部分分组可折叠/展开、章箭头展开节列表、滚动联动激活章自动展开、版本头卡显示 changelog 首条版本号

Mock：无新增——真实 pinia store + memory history router。

### S3 操作手册页（契约锚点：docs/ui/ui.md「PAGE-002」+ STATE-001 + 数据映射）

文件：`src/utils/manualToc.test.ts`、`src/views/portal/manual.test.ts`

- T3.1 parseManualToc：`##` 章 / `###` 节文本 → 两级标题树，每节点含文字与锚点 id
- T3.2 边界：空串、无标题文本 → 返回空树，不抛异常
- T3.3 目录项点击 → 对应标题元素 scrollIntoView 被调用（jsdom stub `Element.prototype.scrollIntoView`）
- T3.4 内容区渲染 HTML，`##` 标题带锚点 id（renderMarkdown 走真实路径）
- T3.5 STATE-001：marked.parse 抛异常 → 内容区回退显示 Markdown 原文，组件不抛错
- T3.6 ≤1024px 目录折叠为下拉（断言折叠结构的类名/条件渲染存在；视觉正确性归 S7）
- T3.7 真实素材规范断言（对 `src/content/manual.md` 本体）：`##` 章数 = 15、全文不含「AIS」字样、首行为 `# FastRAG 用户手册`（验收 17）

Mock：T3.5 用 `vi.mock('marked')` 注入失败；其余用例走真实 `utils/markdown.ts`。

### S4 产品动态页（契约锚点：docs/ui/ui.md「PAGE-003」+ STATE-002）

文件：`src/views/portal/changelog.test.ts`

- T4.1 按数组顺序（version 倒序）渲染全部条目
- T4.2 type 四枚举 → el-tag 语义色一一对应（新功能=success / 优化=primary / 修复=warning / 安全=danger）
- T4.3 changelog 为空数组 → 渲染 el-empty 空态，无时间线（STATE-002）
- T4.4 每条目的 items 要点逐条渲染

### S5 平台侧回归（契约锚点：SPEC-PORTAL-FRONTEND 验收 13、19；TASK-005）

文件：`src/components/layout/Sidebar.test.ts`、`src/components/layout/Header.test.ts`、`src/router/routes.test.ts`

- T5.1 平台侧边栏 logo 点击 → /portal
- T5.2 侧边栏首项与面包屑根显示文字为「主页」（原「首页」，共 4 处）
- T5.3 平台既有路由表快照不变量：既有 path/name/component 映射零变化（防门户迁移误伤平台路由）
- T5.4 权限零注册断言：MENU_PERMISSION_MAP 与 MENU_PERM_TREE 的键/路径集合不含 /portal 族（验收 18）

挂载注意：Sidebar/Header 体量大，用 shallowMount + 图标组件 stub。

### S6 浏览器关键路径（条件执行，HITL 前置：playwright 浏览器 + 后端 8081 + 种子数据）

- T6.1 登录 → 落地 /portal → 「进入平台」→ /home → 侧边栏 logo → /portal 全链路
- T6.2 断点档位视口：1024px 与 768px（含 1025/769 边界对照）下页面可用、能力卡列数与时间线内边距符合约定、控制台零报错

### S7 视觉与键盘走查（HITL）

- T7.1 hero 与首页布局对照模块文档 ASCII 线框人工比对
- T7.2 顶导航 Tab 顺序 = DOM 顺序、Enter 可触发；手册目录键盘可达

## 3. Mock 边界（只 mock 真缝位）

- `@/utils/storage` — node 测试环境无 localStorage（现仓惯例）
- `marked` — 仅 T3.5 失败注入；其余用例走真实 `utils/markdown.ts`
- `Element.prototype.scrollIntoView` — jsdom 未实现的浏览器 API stub
- 不 mock：vue-router（memory history 真实例）、pinia store、changelog.ts 与 manual.md 真实模块、renderMarkdown 正常路径

## 4. 追溯（测试 → UI 契约 → SPEC）

| 测试 | UI 契约锚点 | SPEC 锚点 |
|---|---|---|
| T1.1 | 状态契约「显式不适用」（已登录访问 /login） | 验收 2 |
| T1.2 | —（公开访问，决策 9 修订） | 验收 3 |
| T1.3、T1.4 | 状态契约「未知路径兜底 /portal」 | 验收 5 |
| T1.5 | ui.md「平台侧改动」title 行 | 验收 17 |
| T2.1-T2.5 | ui.md「顶导航」 | 验收 6、7、15 |
| T2.6-T2.8 | ui.md「PAGE-001」 | 验收 8、13、14；STATE-002 派生 |
| T2.9 | ui.md「顶导航」菜单项 | 验收 6 |
| T2.10 | ui.md「顶导航」登录按钮 | 验收 4 |
| T3.1-T3.4、T3.6 | ui.md「PAGE-002」 | TASK-003、验收 10、18 |
| T3.5 | ui.md STATE-001 | 验收 11、STATE-001 |
| T3.7 | —（素材规范断言） | 验收 20 |
| T4.1-T4.4 | ui.md「PAGE-003」+ STATE-002 | TASK-004、验收 12、13 |
| T5.1 | ui.md「平台侧改动」logo 行 | 验收 9、TASK-005 |
| T5.2 | ui.md「平台侧改动」改名行 | 验收 16 |
| T5.3 | —（路由表快照） | 验收 22 |
| T5.4 | —（权限零注册） | 验收 21 |
| T6.1 | 全页（浏览器层） | 验收 1、8、9 |
| T6.2 | —（断点档位） | 验收 18 |
| T7.1-T7.2 | —（视觉/键盘走查） | 验收 19 |

## 5. 待实施说明

- 红绿顺序建议：S1（守卫最薄，先通）→ S3.1（VM 纯函数）→ S2 / S4（组件）→ S5（平台回归兜底）→ S6 / S7（环境就绪后补）
- 素材已落地：`src/content/manual.md`（15 章，产品名 FastRAG），T3.7 直接对真实文件断言
- 组件测试统一 jsdom 环境（文件头 `// @vitest-environment jsdom` 或按现仓既有组件测试先例）
- S5 的 Sidebar/Header 体量大，用 shallowMount + 图标组件 stub
- T3.6 仅断言折叠机制存在；视觉正确性归 S7
- S6/S7 不阻塞其余切片验收；执行者为 /idd-implement
