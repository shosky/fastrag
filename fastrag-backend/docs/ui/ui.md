---
name: UI Contract
summary: 首页门户（portal）的页面、状态、数据映射与 API 依赖契约——三页全静态，零后端依赖。
type: ui
id: UI-UI
status: draft
updated: 2026-10-24
satisfies: [SPEC-PORTAL-FRONTEND]
decisions: [ADR-PORTAL-INDEPENDENT-SHELL]
declares: []
profile: frontend
hash: "65a2a388939d"
summaryHash: "65a2a388939d"
---

# UI Contract — 首页门户

## UI 契约 {#ui-contract}

### 顶导航（PortalLayout，三页共用）

- logo + 产品名 — 源：静态常量；显示：恒显（游客/已登录）；交互：跳 /portal。
- 菜单项 ×4 — 源：`portalMenus.ts` 数组；显示：恒显；交互：router-link 跳转。
- 菜单高亮 — 源：`route.path`；显示：当前路由项；交互：无。
- 立即开始按钮（游客态）— 源：静态；显示：未登录恒显；交互：跳 /login。
- 进入平台按钮（已登录态）— 源：静态；显示：仅已登录；交互：跳平台 /home。
- 用户名 — 源：userStore.username 计算属性（realName 缺失回退 username）；显示：仅已登录；交互：无。
- 退出登录 — 源：userStore.logout；显示：仅已登录；交互：确认框 → 清会话 → /login。

### 首页门户（/portal）

锚点：SPEC-PORTAL-FRONTEND 的 PAGE-001。

- hero 标题/副文案 — 源：静态常量；显示：恒显；交互：无。
- 主按钮「进入平台」— 源：静态；显示：恒显；交互：跳平台 /home。
- 次按钮「查看操作手册」— 源：静态；显示：恒显；交互：跳 /portal/manual。
- 能力卡 ×4 — 源：静态常量；显示：恒显；交互：四卡一致 → 平台 /home。
- 资源卡「操作手册」— 源：静态；显示：恒显；交互：跳 /portal/manual。
- 资源卡「产品动态」+ 徽标 — 源：`changelog[0].version`；显示：数组非空（STATE-002 反向）；交互：跳 /portal/changelog。

### 操作手册页（/portal/manual）

锚点：SPEC-PORTAL-FRONTEND 的 PAGE-002。

- 目录头卡（FastRAG 用户手册 + v* 版本徽标）— 源：changelog 首条；显示：恒显；交互：无。
- 目录（部分分组可折叠 → 章带展开箭头 → 激活章自动展开节列表）— 源：`parseManualGroups(manualMd)`；显示：恒显；交互：点击滚动至锚点，分组/章可折叠。
- 目录当前高亮 — 源：滚动位置派生；显示：恒显；交互：随滚动切换。
- 内容区 — 源：`renderMarkdown(manualMd)`；显示：恒显；交互：标题带锚点 id。
- 回退原文 — 源：manualMd 原串；显示：STATE-001；交互：无（不白屏）。

### 产品动态页（/portal/changelog）

锚点：SPEC-PORTAL-FRONTEND 的 PAGE-003。

- 时间线节点 — 源：`changelog.ts` 数组；显示：数组非空；交互：展示 version/date/title/type/items。
- type 标签 — 源：条目 `type` 字段；显示：恒显；交互：四枚举各自固定一种语义色且全局一致（权威定义见 modules/portal-frontend.md「视觉」节）。
- 空态 — 源：无；显示：STATE-002；交互：el-empty「暂无产品动态」。

### 平台侧改动（非门户页面，随本特性交付）

锚点：SPEC-PORTAL-FRONTEND 验收清单（平台侧无元素级 UI 契约，以 spec 验收为准）。

- 平台侧边栏 logo（Sidebar.vue `.strip-logo`）— 交互：点击跳 /portal（回门户）。
- 平台内「首页」文字 ×4（Sidebar.vue:84 侧边栏首项、Header.vue 面包屑根、office-edit.vue 与 chunks.vue 面包屑根）— 显示：改为「主页」；路由路径与功能不变。
- document.title 后缀（router/index.ts 守卫）— 显示：「- FastRAG」。
- 登录页左上角「回到首页」链接（login/index.vue `.login-brand__back`）— 显示：登录页恒显；交互：跳 /portal。

## 状态契约 {#state-contract}

### 手册渲染回退态

锚点：STATE-001（所属页面 PAGE-002）。

| STATE | 触发 | 可见反馈 | 恢复 |
|---|---|---|---|
| 回退 | renderMarkdown 解析异常 | 内容区显示 Markdown 原文，不白屏、无弹窗 | 无（构建期缺陷，非运行态） |

### 产品动态空态

锚点：STATE-002（所属页面 PAGE-003）；首页资源卡徽标随之隐藏。

| STATE | 触发 | 可见反馈 | 恢复 |
|---|---|---|---|
| 空态 | `changelog.length === 0` | el-empty；首页徽标隐藏 | 维护者补种子数据（spec 验收项） |

显式不适用：loading / error / retry / disabled / offline。门户页面数据零异步请求——唯一例外是退出登录，它复用现有 logout 的 fire-and-forget 后端请求，失败静默，属既有平台行为而非新增接口。此外门户无表单、无用户输入。未知路径兜底 /portal，未登录点击「进入平台」由路由守卫重定向 /login（两者均为 URL 层行为，非页面态）。输入边界与并发语义不适用（零写操作）；changelog 与 manual.md 为构建期不可变数据，更新即改源码发版。

## 数据映射 {#data-mapping}

- `src/content/manual.md`（Vite ?raw）→ `parseManualToc()` @ `utils/manualToc.ts` → 目录树 → ManualToc
- `src/content/manual.md`（Vite ?raw）→ `renderMarkdown()` @ `utils/markdown.ts` → HTML → 内容区
- `src/constants/changelog.ts` → 直读（无转换）→ 时间线 + 首页徽标 `changelog[0].version`
- userStore.username 计算属性（realName 缺失回退 username，store 内定义）→ 顶导航用户名（仅已登录显示）

无 View Model 适配层——唯一结构转换是 parseManualToc，其余为直读。

## API 依赖 {#api-dependency}

无。门户一期零后端接口（CONTEXT.md 决策快照 23），不引用任何 `API-*` / `ERR-*`。

未来若产品动态升级为后端维护（运营 CRUD），收敛点：本节新增对应 `API-*`（Source：后端仓 `docs/interfaces.md`），数据消费方从 `changelog.ts` 直读切换为该接口——页面与组件不变。
