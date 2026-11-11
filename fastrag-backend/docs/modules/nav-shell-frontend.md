---
name: Nav Shell Frontend
summary: 平台壳导航层前端——单列宽侧栏承载对话/构建/资源/管理四段，用户入口顶置下拉（含数据分析分组），顶栏铃铛接线既有通知中心；权限键冻结。
type: module
id: MOD-NAV-SHELL-FRONTEND
profile: frontend
status: draft
updated: 2026-10-26
satisfies: [SPEC-NAV-SHELL-FRONTEND]
decisions: [ADR-NAV-PERM-TREE-DECOUPLING]
hash: "f1019b0304db"
summaryHash: "f1019b0304db"
---

# Nav Shell Frontend — 平台壳导航层

## 职责 {#responsibility}

平台壳（AppLayout）的导航层：把「窄条+二级面板」双列侧边栏改造为单列宽侧栏（对话/构建/资源/管理四段 + 顶置主页）。用户入口顶置到侧栏 logo 行（左上角下拉，含数据分析分组）。内容卡头接入通知铃铛，并新增 `/notification` 路由接线既有通知中心页。菜单四段重组但路由、权限键、既有页面文件零改动。

## 契约 {#contract}

**属性：**

```ts
// Sidebar 与 NotificationBell 均为路由/环境驱动，不接收任何 props
interface SidebarProps {}
interface NotificationBellProps {}
```

**事件：** 无对外事件——菜单跳转由 router-link 完成，退出登录只消费现有 userStore，铃铛跳转用 router.push。

**插槽：** 无。铃铛是 `Header.vue` header-right 区的固定子元素（渲染于既有 `#header-actions` teleport 与 actions 槽之前，槽位语义不变）。

**类型：**

```ts
// 导航四段单一事实源（src/config/nav-segments.ts）
interface NavItem {
  key: string      // 菜单标识，如 'knowledge-list'
  title: string    // 显示文字，如 '知识库'
  path: string     // 既有路由路径（零新增，/notification 除外）
  icon?: string    // Element Plus 图标名（沿用现状映射）
}
interface NavSubgroup {
  title: string        // 子分组标签，如 '数据分析'（非交互文本）
  items: NavItem[]
}
interface NavSegment {
  key: 'conversation' | 'build' | 'resource' | 'analytics'
  title: string        // 对话 / 构建 / 资源 / 数据分析（修订 #15）
  items: NavItem[]     // 直排项
}
interface NavConfig {
  home: NavItem                       // 顶置主页项（段结构之外）
  segments: NavSegment[]              // 四段，顺序即渲染顺序
  userMenuProfile: UserMenuItem       // 下拉头部正下方单项（用户信息）
  userMenuGroups: NavSubgroup[]       // 下拉语义分组（修订 #16/#17：门户资源[恒显平铺] + 管理[级联子菜单，权限门禁 12 项]）
  // 修订 #18：侧边栏资源段 8 项（知识库分类移除，收敛进知识库页 CategoryPanel 面板）
  userMenuLogout: UserMenuItem        // 下拉底部独立退出（danger，恒在最底）
}
```

**不变量：**

- 菜单渲染完全由 `NavConfig` 静态数组驱动：增删菜单只改数组（及复用其既有权限键），不改布局组件。
- 权限键零改动（ADR-NAV-PERM-TREE-DECOUPLING）：权限过滤只读 `MENU_PERMISSION_MAP` 与 `requirePerm`，不新增、不重命名、不重排任何键；`menu-perm-tree.ts` 仅分组节点标题随四段重排，页面节点 `keys/actions/apis` 冻结。
- 既有 60 条路由的 path/name/component 零变化；新增路由仅 `/notification`（AppLayout 子路由族，不注册权限树，守卫放行）。
- 既有页面文件零触碰：改动面 = `Sidebar.vue`（原地重写）、`Header.vue`（注入铃铛）、`routes.ts`（+1 条）、`menu-perm-tree.ts`（分组标题）、`variables.scss`（+1 token）、新增 `nav-segments.ts` 与 `NotificationBell.vue`。
- 用户入口位于**内容卡头右上角**（铃铛右侧，头像 chip，修订 #14 三次修订）；下拉结构按语义分组（修订 #16）：头部（姓名+用户名）→ 用户信息 → 「门户资源」分组（操作手册/产品动态，恒显）→ 「管理」分组十二项（逐项按原权限键过滤，全隐时标签不渲染）→ 底部独立退出系统（恒在最底）；侧边栏内不出现管理域菜单。
- 折叠态**悬停浮出**（修订 #16）：鼠标悬停图标栏时右侧浮出 208px 完整菜单（覆盖内容区、带投影），鼠标离开或点击菜单项即收回；展开态悬停无浮出层。
- 侧边栏支持**折叠**（修订 #15）：折叠态 64px 图标模式（`$nav-strip-width` 复用），段标题与菜单文字隐藏、菜单项以原生 title 提示；底部折叠/展开开关，状态持久化 localStorage（`ais_nav.sidebarCollapsed`）。
- 手册与动态指向门户公开路由 `/portal/manual`、`/portal/changelog`（门户层零改动）。
- 侧栏 logo 点击回 `/portal`（门户链决策 #8）。

## 接口 {#interface}

无
<!-- 普通接口：每个接口一个 `###` 单元，写 `Purpose:` 与 `Signature:`；没有则保留「无」。 -->

## 页面 {#page}

- **PAGE-101 平台壳** — 全部既有平台路由共用（AppLayout）。权限：各页面既有权限不变。入口：登录落地 `/home`（门户链决策 #8）。退出：侧栏 logo（回 `/portal`）、顶部用户下拉「退出系统」。
- **PAGE-102 通知中心** — 路由 `/notification`（唯一新增）。权限：登录即可。入口：顶栏铃铛。返回：侧边栏任一菜单项。页面复用 `views/notification/index.vue` 零改动。

## 布局 {#layout}

**PAGE-101 平台壳（desktop，常驻布局无响应式变体）：**

```
┌──────────────┬───────────────────────────────────────────────────────┐
│ NavSidebar   │ ┌───────────────────────────────────────────────────┐ │
│ (208px)      │ │ Header：标题/面包屑  [🔔99+] [页面操作按钮] [👤▾]  │ │
│ ┌──────────┐ │ ├───────────────────────────────────────────────────┤ │
│ │  [logo]  │ │ │                                                   │ │
│ ├──────────┤ │ │                                                   │ │
│ │ 🏠 主页   │ │ │   <router-view />                                 │ │
│ │ ──────── │ │ │   （既有页面内容，零改动）                           │ │
│ │ 对话      │ │ │                                                   │ │
│ │  会话中心 │ │ │                                                   │ │
│ │ 构建      │ │ │                                                   │ │
│ │  应用中心 │ │ │                                                   │ │
│ │ 资源      │ │ │                                                   │ │
│ │  知识库 … │ │ │                                                   │ │
│ │ 数据分析   │ │ │                                                   │ │
│ │  知识库分析…│ │ │                                                  │ │
│ ──────────── │ │ │                                                   │ │
│ │ « 收起菜单│ │ │                                                   │ │
│ └──────────┘ │ └───────────────────────────────────────────────────┘ │
└──────────────┴───────────────────────────────────────────────────────┘
```

<!-- ≤1024px：展开 208px / 折叠 64px 两态常驻，无汉堡菜单（修订 #15 折叠开关见下方折叠态线框） -->
<!-- 修订 #14（三次修订）：用户入口在内容卡头右上角（铃铛右侧），侧栏顶部/底部均无用户区 -->
<!-- 修订 #15：数据分析第四段回侧边栏；底部「收起菜单」开关；折叠态线框如下 -->

**折叠态（collapsed，64px 图标模式）：**

```
┌──────┐
│[logo]│
├──────┤
│ 🏠   │ ← title="主页"
│ ──   │
│ 💬   │
│ ▦    │
│ 📚 … │ ← 菜单项仅图标，原生 title 悬停提示
│ 📊   │
├──────┤
│  »   │ ← 展开/收起开关（Expand/Fold）
└──────┘
```

**用户入口下拉（UserMenu 组件，el-popover bottom-end，右上角头像 chip 触发；修订 #16 语义分组）：**

```
┌──────────────┐
│ 王翔          │ ← 下拉头部：姓名 + 用户名（非交互）
│ @admin        │
├──────────────┤
│ 👤 用户信息   │ → 既有六字段弹窗（组织名懒加载不变）
├──────────────┤
│ 门户资源      │ ← 恒显分组（修订 #16）
│  操作手册     │ → /portal/manual
│  产品动态     │ → /portal/changelog
├──────────────┤
│ 管理       ‹  │ ← 级联行（修订 #17）：悬停向左弹出子面板
├──────────────┤
│ ⏻ 退出系统   │ → 确认框 → 清会话 → /login（恒在最底）
└──────────────┘

  「管理」悬停弹出子面板（向左，170px，自绘）：
  ┌────────────┐
  │ 管理        │
  │  通用设置   │ → /admin/system/general-settings
  │  沙箱设置 … │ → 逐项权限过滤（全隐整组不渲染）
  │  开放密钥   │ → /admin/platform/api-keys
  └────────────┘
```

**折叠态悬停浮出层（.nav-flyout，修订 #16）：**

```
          ┌──────────────┐
│  »   │  │ 🏠 主页        │ ← 鼠标悬停图标栏时浮出（覆盖内容区）
│  🏠  │  │ ──────────── │    鼠标离开 / 点击菜单项 → 收回
│  💬  │  │ 对话          │
│  ▦   │  │  会话中心 …   │
│  📚  │  │ （完整菜单）   │
          └──────────────┘
```

组件映射（单行职责）：

- `components/layout/Sidebar.vue` — 单列宽侧栏（原地重写）：顶置主页、四段渲染、权限过滤、高亮回溯（用户入口已移出，三次修订）。
- `components/layout/UserMenu.vue` — 右上角用户入口（新）：头像 chip + 下拉（头部/固定四项/数据分析分组）+ 用户信息弹窗（行为深度：状态+权限过滤+动作流，成立为独立组件）。
- `config/nav-permissions.ts` — 导航权限过滤共享工具（新）：findRequiredPerms/canShowPath，Sidebar 与 UserMenu 共用（键集冻结 ADR 的过滤实现单点）。
- `components/layout/NotificationBell.vue` — 通知铃铛（新）：未读数拉取/重拉时机/徽标格式化，行为深度成立为独立组件。
- `components/layout/Header.vue` — 内容卡头（微改）：header-right 区注入铃铛，标题/面包屑/actions 槽位全部不变。
- `components/layout/Logo.vue`、`AppLayout.vue` — 零改动复用。
- `config/nav-segments.ts` — 导航四段单一事实源（新，`NavConfig` 常量）。
- `views/notification/index.vue` — PAGE-102 既有页，零改动。
- `config/menu-perm-tree.ts` — `MENU_PERM_TREE` 分组节点标题随四段重排（页面节点与键冻结）。

## 状态 {#states}

- **STATE-101 通知未读数** — 归属：client（NotificationBell 组件局部 ref）。服务器是数据源而非状态属主；拉取时机 = 组件挂载（进入平台一次）+ 路由离开 `/notification` 时重拉；`formatBadge` 派生徽标文案。
- **STATE-102 菜单权限过滤** — 归属：client store（userStore 权限集）上的派生 computed，无独立可变状态；权限集变化（重登录/改配角色）自动反映。
- 当前高亮 — 归属：URL。由 `route.path` 派生（最长前缀回溯 + 叶子优先），非独立状态。

无 server / form 状态；PAGE-102 内部状态由既有页面自持（本模块不重述）；disabled / offline 不适用。

## 数据 {#data}

```
GET /notifications/unread-count（getUnreadNotificationCount，api/index.ts:2128）
  → NotificationBell.refresh()              // 唯一命名转换点：
  → formatBadge(n): n<=0 → ''（隐藏）；1–99 → String(n)；n>=100 → '99+'
  → el-badge 铃铛徽标

nav-segments.ts（NavConfig 静态常量）
  → filterSegments()（Sidebar 内平移自 Sidebar.vue:219-263：
    MENU_PERMISSION_MAP 按 path 查键优先 → requirePerm 兜底 → 递归过滤，空段隐标题）
  → visibleSegments → 四段渲染
  → isActive(route.path)（叶子优先平移，Sidebar.vue:336-352）→ 高亮

userStore（realName || username 回退，Sidebar.vue:24 同款）→ 顶部用户入口姓名与下拉头部
GET /org/flat（既有懒加载映射）→ 用户信息弹窗「所属组织」
GET /notifications 等五个通知端点（api/index.ts:2124-2140）→ PAGE-102 既有代码自消费，本模块不接新线
```

唯一新增消费端点 = `GET /notifications/unread-count`（既有接口）；零新契约、零 View Model 适配层（唯一纯函数转换是 formatBadge）。

## 视觉 {#visual}

```scss
// variables.scss 增补一个 token，其余全部复用现有 token
$sidebar-width: 208px;   // 单列宽侧栏（替代 $nav-strip-width 64px + $sub-menu-width 176px 双列合计 240px）
```

- 侧栏：底色 `$nav-strip-bg`、右边框 `$border-base`、内边距 `$spacing-sm 0`；顶部 logo 行 = logo（点击回门户）+ 右侧用户头像 chip（修订 #14）。
- 顶部用户入口：头像 24px 圆形（既有 avatarColor 哈希色）+ 姓名 13px；下拉头部 13px/600 姓名 + 12px `$text-secondary` 用户名，分组标签同段标题款式。
- 段标题 / 「数据分析」子分组标签（同款式）：11px、`$text-placeholder`、letter-spacing 0.5px、`$spacing-sm $spacing-base` 内边距、非交互不可聚焦。
- 菜单项：13px、行高 32px、圆角 `$radius-base`、水平内边距 `$spacing-base`；默认 `$text-regular`；hover `$bg-hover` + `$text-primary`；active `$sub-menu-active-bg` + `$sub-menu-active-color` + 500 字重；图标 16px `$text-placeholder`（active 同激活色）。
- 主页项：与菜单项同款式，底部 `$border-light` 分隔线与四段隔开。
- 下拉菜单项：复用既有 `.user-menu` 全局样式（8px 10px 行、hover `$bg-hover`、退出 `$color-danger`）；分组项与固定项之间 `$border-light` 分隔线。
- 铃铛：`el-icon Bell` 18px `$text-regular` hover `$color-primary`；`el-badge` 徽标 `$color-danger` 底白字 10px；徽标文案为 `''` 时 `v-if` 隐藏（含拉取失败态）。
- 折叠态（修订 #15）：宽度复用 `$nav-strip-width`（64px）；菜单项收敛为 40px 居中图标钮，段标题渲染为居中短分隔线；底部折叠开关 40px 高、hover `$bg-hover`。
- 断点：<1024px 侧栏常驻（展开 208px / 折叠 64px 两态，均为常驻），无汉堡菜单。
- 动效：菜单 hover/active `transition 0.2s ease`（平移现状）；`prefers-reduced-motion: reduce` 下过渡禁用。
- 图标沿用现状映射：主页 HomeFilled、会话中心 ChatLineRound、应用中心 Grid、知识库 Collection、知识库分类 Grid、我的工具 Tools、MCP Connection、技能 MagicStick、沙箱配置 Box、数据库 Coin、模型管理 Cpu、提示词 ChatDotRound、通用设置/沙箱设置 Setting、知识库配置 Collection、敏感词 WarningFilled、字典 Notebook、术语 Reading、角色/人员 User/UserFilled、组织 OfficeBuilding、权限/密钥 Key、日志 Document、知识库分析 DataAnalysis、检索日志分析 Search、反馈 ChatLineSquare、模型监控 Monitor。

## 边界设计 {#seam}

**菜单集合（变化点）** — 四段成员增删是未来最可能的变化。适配方式是 `nav-segments.ts` 数组常量：当前只有一个"实现"（静态配置），按 one-adapter 原则不抽象接口；稳定侧是 Sidebar 渲染器只消费 `NavConfig`。

**未读数来源（变化点）** — 当前为拉取式（挂载 + 离开 `/notification` 重拉）；若未来升级 SSE 推送，替换点收敛在 `NotificationBell.refresh()`，徽标格式化与展示不变。

**权限体系（稳定侧，冻结）** — 菜单分组与权限分组解耦后，`MENU_PERMISSION_MAP`/`menu-perm-tree.ts`/`sys_permission` 成为只读稳定侧；过滤实现按 path 查键、与分组无关，菜单再重排不再触碰权限体系。理由与被否决方案见 ADR-NAV-PERM-TREE-DECOUPLING。

**双列 → 单列（稳定）** — AppLayout 壳槽位（Sidebar + Header + router-view）零改动，Sidebar 是唯一结构改造点；视觉基调（底色/激活色/圆角）沿用现有 token，不引入新色彩体系。

**折叠状态（变化点）** — 折叠态为纯客户端状态，持久化于 localStorage（`ais_nav.sidebarCollapsed`，经 `@/utils/storage` 前缀封装）；未来若需「按角色默认折叠」或「服务端偏好同步」，替换点收敛在 Sidebar 的 collapsed 读写两行。

## 追溯 {#trace}

- 四段分组/顶置主页/铃铛/用户菜单四项/数据分析分组 → SPEC-NAV-SHELL-FRONTEND「功能需求」+ CONTEXT.md 决策快照 #1、#2、#5、#6、#7、#11。
- 权限键冻结与展示树/权限树解耦 → CONTEXT.md 决策 #4 + ADR-NAV-PERM-TREE-DECOUPLING。
- PAGE-101/PAGE-102 → spec「页面与路由」同编号条目。
- STATE-101/STATE-102 → spec「状态矩阵」同编号条目。
- 壳为界（页面零触碰）→ CONTEXT.md 决策 #10、#12。
- 数据流唯一端点 → spec「数据映射」（`GET /notifications/unread-count`，API Contract Source = springdoc 运行时）。
- 视觉 token 复用纪律 → spec「响应式与可达性」（桌面优先、可达性四条）。

### 实现追溯（/idd-implement 2026-10-26）

- 测试证据：tdd/nav-shell-frontend.md S1-S5 全量落地，`vitest run` 全仓 17 文件 128 用例全绿（本链新增 42），`vue-tsc --noEmit` 零错误；S6（双账号浏览器矩阵）与 S7（视觉/键盘走查）未执行，留待环境就绪（执行者为验收环节）。
- T1.3 键全集守卫按「改造前 HEAD 的 keys 并集 = 新树 keys 并集（去重排序全等）」实现，基线内嵌 menu-perm-tree.test.ts（41 键）；路由快照同法内嵌 routes.test.ts（77 对 = HEAD 全量，断言 HEAD 对逐一保留 + 恰增 notification 一对）。

### 实现决策与偏差
- 修订 #14（2026-10-26 二次迭代）：用户入口自侧栏底部移出，下拉新增头部（姓名+用户名）与「数据分析」权限门禁子分组（自管理段移入）；`NavSegment.subgroups` 字段随侧边栏子分组废弃、`NavSubgroup` 复用为 `NavConfig.userMenuGroups` 类型；权限树（menu-perm-tree.ts）零改动——数据分析四节点的键仍随树冻结。
- 修订 #14 三次迭代（同日）：用户入口位置修正为内容卡头右上角（铃铛右侧，ADP 式窗口右上角）；自 Sidebar 抽出独立 `UserMenu.vue` 组件，权限过滤抽 `config/nav-permissions.ts` 共享工具（Sidebar/UserMenu 单点共用）。
- 修订 #15（同日四次迭代）：管理段 12 项收进用户下拉「管理」分组（NavSegment key 'admin' → 'analytics'，数据分析第四段回侧边栏）；侧边栏折叠（图标态 64px + 底部 Fold/Expand 开关 + localStorage 持久化）；恒显项语义在折叠/下拉两种形态下一致（沙箱设置无权限键，对无权限账号仍可见）。
- 修订 #16（同日五次迭代）：用户下拉语义分组（头部 → 用户信息 → 门户资源 → 管理 → 底部独立退出，NavConfig 拆为 userMenuProfile/userMenuGroups/userMenuLogout 三字段）；折叠态悬停浮出完整菜单（.nav-flyout 覆盖内容区，替代图标 tooltip 方案），Sidebar 容器 mouseenter/mouseleave 驱动。
- 修订 #17（同日六次迭代）：下拉「管理」分组改级联子菜单（NavSubgroup.cascade 标记；自绘绝对定位子面板——行悬停展开、离行 150ms 收回、移入面板取消，不用嵌套 el-popover 受控 popper 的 jsdom 不可测实现）；门户资源组保持平铺。
- 修订 #18（同日七次迭代）：知识库分类收敛进知识库列表页左侧 CategoryPanel 面板（全部/未分类/分类树 + 搜索 + 折叠 + 行「…」操作菜单 + CRUD 对话框，自 categories.vue 迁入）；侧边栏资源段 9→8 项；`/knowledge/categories` 路由保留无入口；修订 #10「壳为界」的首个页面级例外（限知识库列表页）。

- 受限账号下「主页」受 `menu:home` 门禁隐藏——权限过滤平移的自然结果（T2.4 期望据此校准为 5 项）。
- MENU_PERM_TREE 的「管理」节点 keys 保留 4 个旧中间分组键（`menu:admin:system/account/audit/platform`），满足键集冻结的完整性（旧分组节点消失后这些键仍可授予）。
- 权限树「首页」节点标题改「主页」——对齐门户链决策 7 的展示文字（keys/actions/apis 不变）。
- routes.ts 的 `/notification` 追加落在 portal-frontend 链名下（DUP004 所有权唯一），本模块以本追溯节记录该改动；`meta.icon: 'Bell'` 遵循路由表惯例。
- formatBadge 经平行普通 `<script>` 块导出（script setup 不允许 export），供 T4.1 纯函数直测。
- Sidebar 增加 defineExpose 测试缝（visibleSegments/isActive/userInfoDialog/处理器）——popover 与 dialog 在 jsdom 内不可驱动。
- 通知重拉时机实现为 `watch(route.path)`：from 匹配 `/notification` 前缀且 to 不匹配时 refresh；组件挂载 refresh 一次。

## 代码 {#code}

<!-- routes.ts 所有权归 portal-frontend 链（DUP004），本模块不登记，见上方实现追溯；
     views/notification/index.vue 为既有页零改动、非本模块实现归属，不登记。 -->

```yaml
- path: ../fastrag-frontend/src/config/nav-segments.ts
  description: 导航四段单一事实源（NavConfig 常量：顶置主页 + 对话/构建/资源/管理四段 + 用户菜单四项）
- path: ../fastrag-frontend/src/config/nav-segments.test.ts
  description: NavConfig 数据完整性、路由表对账、权限哨兵解析与恒显项、不出现项、用户菜单结构
- path: ../fastrag-frontend/src/components/layout/Sidebar.vue
  description: 单列宽侧栏（原地重写）——canShowPath 权限过滤、叶子优先高亮、顶置主页与四段渲染（用户入口已移出）
- path: ../fastrag-frontend/src/components/layout/Sidebar.test.ts
  description: 双账号过滤矩阵、空段隐标题、不出现项、高亮回溯五例与 /operation 无高亮断言、logo 回门户
- path: ../fastrag-frontend/src/components/layout/UserMenu.vue
  description: 右上角用户入口——头像 chip 下拉（头部/固定四项/数据分析权限分组）、用户信息弹窗、退出确认流
- path: ../fastrag-frontend/src/components/layout/UserMenu.test.ts
  description: 下拉受控渲染、分组权限过滤（受限账号整组剔除）、四条动作流（弹窗/门户跳转/退出确认/分组跳转）
- path: ../fastrag-frontend/src/config/nav-permissions.ts
  description: 导航权限过滤共享工具——findRequiredPerms/canShowPath（Sidebar 与 UserMenu 单点共用）
- path: ../fastrag-frontend/src/components/layout/NotificationBell.vue
  description: 顶栏通知铃铛——formatBadge 徽标格式化、挂载拉取 + 离开通知中心重拉、点击跳转、失败静默
- path: ../fastrag-frontend/src/components/layout/NotificationBell.test.ts
  description: formatBadge 五例边界、拉取时机（挂载/离开重拉/普通切换不触发）、徽标渲染与静默失败
- path: ../fastrag-frontend/src/components/layout/Header.vue
  description: 内容卡头（微改）——header-right 注入 NotificationBell（操作按钮区之前），槽位语义不变
- path: ../fastrag-frontend/src/components/layout/Header.test.ts
  description: header-right 顺序断言（铃铛→操作区→用户入口最右）、面包屑根「主页」回归锚点、挂载触发未读数拉取
- path: ../fastrag-frontend/src/config/menu-perm-tree.ts
  description: MENU_PERM_TREE 分组随四段重排（页面节点 keys/actions/apis 冻结；旧中间分组键并入管理节点 keys）
- path: ../fastrag-frontend/src/config/menu-perm-tree.test.ts
  description: 权限键全集冻结对账（41 键）、页面节点键绑定哨兵、通知零注册、四段分组与数据分析子分组
- path: ../fastrag-frontend/src/router/routes.test.ts
  description: 路由表快照对账（HEAD 77 对逐一保留 + 恰增 notification 一对）与 /notification 挂载形态
- path: ../fastrag-frontend/src/views/knowledge/components/CategoryPanel.vue
  description: 知识库列表页左侧分类面板（修订 #18）——全部/未分类/分类树与前端计数、搜索、折叠窄条、行「…」操作菜单与 CRUD 对话框（自 categories.vue 迁入）
- path: ../fastrag-frontend/src/views/knowledge/components/CategoryPanel.test.ts
  description: 面板树渲染与计数口径、搜索过滤、选择/取消 emit、折叠切换、操作权限显隐、编辑分流与新增/删除 CRUD 流
- path: ../fastrag-frontend/src/assets/styles/variables.scss
  description: 设计令牌增补 sidebar-width（208px，其余复用现有 token）
```
