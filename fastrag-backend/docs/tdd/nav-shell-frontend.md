# 平台导航壳前端（nav-shell-frontend） — 测试设计

> 对应契约：docs/ui/ui.md「nav-shell 链」四节（UI 契约 / 状态契约 / 数据映射 / API 依赖）与 docs/modules/nav-shell-frontend.md `## Contract` 单块。**本文件为测试设计，红绿循环未执行**（由 /idd-implement 按本设计执行）。

## 1. 切片规划

| 切片 | 用户任务 | 测试层 | 覆盖状态 | 执行性 |
|---|---|---|---|---|
| S1 路由与权限静态不变量 | TASK-101/105 底座 | VM/adapter + 静态断言 | 路由快照 + 权限键冻结 + 权限树分组 | AFK |
| S2 导航四段数据与过滤 | TASK-101 | VM/adapter + Component | STATE-102 + 数据完整性 + 不出现项 | AFK |
| S3 菜单高亮回溯 | TASK-101 | Component | 高亮特例与前缀回溯 | AFK |
| S4 通知铃铛 | TASK-102 | VM/adapter + Component | 未读数六态（ui.md 状态契约） | AFK |
| S5 用户入口与回归锚点 | TASK-103/104 | Component | 右上角下拉（头部/四项/分组）+ 通知中心接线 + 回归 | AFK |
| S6 浏览器关键路径 | TASK-101/102 | Browser | 双账号矩阵 + 铃铛真实链路 + 视口 | 条件（HITL 前置：双账号 + 后端 8081） |
| S7 视觉与键盘走查 | — | Visual/a11y | 线框比对 + Tab/Enter + 动效降级 | HITL |

选择原则：每切片取「能证明该行为的最浅层」——formatBadge 等纯函数下沉 VM 层（S4.1），数据完整性与过滤用真实 nav-segments + 真实 MENU_PERMISSION_MAP 断言（S1/S2），交互与状态用组件挂载（S2-S5），仅双账号矩阵与真实通知链路进浏览器（S6）。

## 2. 测试用例（逐切片）

### S1 路由与权限静态不变量（契约锚点：ui.md「权限树展示同步」；SPEC-NAV-SHELL-FRONTEND 验收 17-20）

文件：`src/router/routes.test.ts`（扩展）、`src/config/menu-perm-tree.test.ts`（新增）

- T1.1 路由表快照：既有 60 条 path/name/component 映射零变化（快照对照，防壳改造误伤路由）
- T1.2 新增路由恰一条：`/notification` 挂 AppLayout 子路由族、要求登录、复用 `views/notification/index.vue`
- T1.3 权限键冻结：`MENU_PERMISSION_MAP` 全部键集 + `MENU_PERM_TREE` 页面节点 `keys/actions/apis` 与基线快照一致（基线数组内嵌测试文件，注明取自改造前提交）
- T1.4 权限零注册：`/notification` 不出现在 `MENU_PERMISSION_MAP` 与 `MENU_PERM_TREE`
- T1.5 `MENU_PERM_TREE` 分组节点标题覆盖四段语义（对话/构建/资源/管理 + 数据分析），页面节点集合不变

### S2 导航四段数据与过滤（契约锚点：ui.md「单列宽侧栏」+ STATE-102；验收 3、7、8、9、11）

文件：`src/config/nav-segments.test.ts`、`src/components/layout/Sidebar.test.ts`

- T2.1 数据完整性：`NavConfig` 恰含顶置主页 + 四段（顺序 对话/构建/资源/数据分析）；侧边栏 14 项 + 下拉管理 12 项；每项 `path` 均存在于路由表（结构化对账，不数数）
- T2.8 知识库分类面板（修订 #18）：CategoryPanel 树渲染与前端计数口径（全部/未分类/各分类）、搜索过滤、选择 emit（含 `__uncategorized__` 哨兵与取消语义）、折叠窄条、行「…」操作菜单权限显隐与编辑分流、新增/删除 CRUD（见 CategoryPanel.test.ts）
- T2.2 权限依据对齐：每项 path 从 `MENU_PERMISSION_MAP` 解析出的键与 spec「元素与交互」清单的权限依据列一致（含模型管理 `menu:admin:platform:model` + `admin:system`）
- T2.3 全权限账号：四段全渲染、菜单项 27 全可见
- T2.4 受限账号（仅知识库权限）：资源段仅知识库/知识库分类可见，对话/构建段项按其权限裁剪，管理段全隐（STATE-102）
- T2.5 空段隐标题：某段全部项被过滤 → 该段标题不渲染
- T2.6 不出现项：侧边栏渲染树不含「业务流」「测试案例」「应用运行」「运营中心」与管理域文案（管理域仅在用户下拉，修订 #15）
- T2.7b 折叠态（修订 #15）：默认展开；toggle 后图标态（无 nav-text/段标题）+ localStorage 持久化；预置折叠挂载即图标态；底部开关点击可切换
- T2.7 用户区恒显，姓名按 `realName || username` 回退

Mock：仅 `@/utils/storage`（现仓惯例）；过滤用真实 `MENU_PERMISSION_MAP` + stub `useAuth` 权限集。

### S3 菜单高亮回溯（契约锚点：ui.md「菜单高亮」行；验收 4、7）

文件：`src/components/layout/Sidebar.test.ts`

- T3.1 `/home` 激活 → 主页项高亮
- T3.2 `/knowledge/:id` 激活 → 「知识库」项高亮（前缀回溯）
- T3.3 `/application/:id/editor` 激活 → 「应用中心」项高亮
- T3.4 `/admin/platform/model-management` 激活 → 资源段「模型管理」项高亮，管理段无高亮（核心特例）
- T3.5 `/operation/kb-analytics` 激活 → 高亮「知识库分析」（修订 #15：数据分析回栏）；`/admin/system/general-settings` 激活 → 无侧栏高亮（管理域在用户下拉）
- T3.6 叶子优先：`/knowledge/categories` 激活时「知识库分类」高亮而非「知识库」

### S4 通知铃铛（契约锚点：ui.md「通知铃铛」+ 状态契约未读数六态表 + 数据映射 formatBadge；验收 13-16）

文件：`src/components/layout/NotificationBell.test.ts`、`src/components/layout/Header.test.ts`

- T4.1 formatBadge 纯函数五例：0 → ''、1 → '1'、99 → '99'、100 → '99+'、Promise reject → ''
- T4.2 组件挂载即调用一次 `getUnreadNotificationCount`
- T4.3 路由由 `/notification` 切出 → 再次调用（重拉）；非通知中心路由间切换不触发
- T4.4 点击铃铛 → 跳 `/notification`
- T4.5 徽标文案 '' 时不渲染徽标节点；接口失败无 ElMessage/ElNotification 弹出
- T4.6 Header 渲染 NotificationBell，且位于 `#header-actions` teleport 与 actions 槽之前
- T5.6b 用户下拉交互：受控可见性直驱渲染（头部+固定四项+数据分析），handleUserMenu 后关闭；槽位注入行为不变

Mock：`@/api` 的 `getUnreadNotificationCount`（vi.mock 控制五例取值与 rejection）；路由用 memory history 真实例。

### S5 用户区与回归锚点（契约锚点：ui.md「用户区弹层」「通知中心页」；验收 10-12、15、16、21-22 的组件可测部分）

文件：`src/components/layout/UserMenu.test.ts`（用户入口动作流）、`src/router/routes.test.ts`

- T5.1 用户入口（UserMenu，右上角）：下拉头部姓名+@用户名、用户信息单项、门户资源平铺分组、管理级联行（修订 #17：主下拉体不平铺管理功能项、cascade 行渲染、受控子面板挂载/卸载；受限账号管理组仅剩恒显项沙箱设置、门户资源恒显）
- T5.6c 折叠态悬停浮出（修订 #16）：折叠 + mouseenter → 浮出层含完整菜单文字；mouseleave/点击菜单项收回；展开态悬停无浮出层
- T5.2 操作手册 → `/portal/manual`；产品动态 → `/portal/changelog`（router-link 目标断言）
- T5.3 退出确认流：确认 → 调 `userStore.logout` 且跳 `/login`；取消 → 停留
- T5.4 用户信息弹窗六字段齐全；组织名懒加载（`getOrgFlat` 一次）
- T5.5 侧栏 logo 点击 → `/portal`
- T5.6 `/notification` 挂载渲染通知列表（mock `api.getNotifications` 返回两态：有数据 / 空数组），页内已读/删除功能存在性断言（既有行为冒烟，不重述页面契约）
- T5.7 登录落地 `/home` 与面包屑根「主页」不受壳改造影响（守卫级回归）

挂载注意：Sidebar/Header 体量大，shallowMount + 图标组件 stub（沿 portal S5 惯例）。

### S6 浏览器关键路径（条件执行，HITL 前置：playwright 浏览器 + 后端 8081 + 双账号种子数据）

- T6.1 双账号矩阵：管理员四段全可见；受限账号按 T2.4 期望裁剪（浏览器层复核 STATE-102）
- T6.2 铃铛真实链路：进入平台徽标出现 → 通知中心标记已读 → 切出 → 徽标数值减少
- T6.3 视口 1024/1025 与 768/769 对照：侧栏常驻 208px 不折叠、无汉堡菜单；控制台零报错

### S7 视觉与键盘走查（HITL）

- T7.1 侧栏对照 modules/nav-shell-frontend.md ASCII 线框人工比对（208px、段标题 11px 灰字、激活 `$sub-menu-active-bg` 蓝底、用户区底部分隔线）
- T7.2 菜单项 Tab 聚焦 + Enter 跳转可达；段标题与「数据分析」标签不进入焦点序
- T7.3 `prefers-reduced-motion: reduce` 下菜单过渡禁用

## 3. Mock 边界（只 mock 真缝位）

- `@/utils/storage` — node 测试环境无 localStorage（现仓惯例）
- `@/api` 的 `getUnreadNotificationCount` / `getNotifications` / `getOrgFlat` — 控制返回值与 rejection（S4/S5）；其余 api 不 mock
- `useAuth` 权限集 — S2 受限账号场景 stub 权限集；S1/S2 数据对齐用真实 `MENU_PERMISSION_MAP`
- `Element.prototype.scrollIntoView` 类浏览器 API — jsdom 未实现时按需 stub
- 不 mock：vue-router（memory history 真实例）、pinia store、`nav-segments.ts` 真实数据、路由表本体

## 4. 追溯（测试 → UI 契约 → SPEC）

| 测试 | UI 契约锚点 | SPEC 锚点 |
|---|---|---|
| T1.1、T1.2 | ui.md「通知中心页」+「单列宽侧栏」不变量 | 验收 17 |
| T1.3、T1.4 | ui.md「权限树展示同步」 | 验收 18、20 |
| T1.5 | ui.md「权限树展示同步」 | 验收 18 |
| T2.1、T2.2 | ui.md「单列宽侧栏」菜单项行 | 验收 3、4、5 |
| T2.3-T2.5 | ui.md「菜单权限过滤」状态表（STATE-102） | 验收 9 |
| T2.6 | ui.md「单列宽侧栏」不出现项行 | 验收 8 |
| T2.7 | ui.md「用户区」姓名行 | 验收 11（部分） |
| T3.1-T3.6 | ui.md「菜单高亮」行 | 验收 4、7 |
| T4.1-T4.5 | ui.md「通知铃铛」+ 未读数六态表 | 验收 13、14、16 |
| T4.6 | ui.md「通知铃铛」（Header 内位置） | 验收 13 |
| T5.1-T5.5 | ui.md「用户区弹层」「单列宽侧栏」logo 行 | 验收 10、11、12、15 |
| T5.6 | ui.md「通知中心页」 | 验收 15（通知中心部分） |
| T5.7 | —（门户链回归锚点） | 验收 21 前置（登录落地不变） |
| T6.1-T6.3 | 状态契约 + 响应式节 | 验收 9、13、16、22 |
| T7.1-T7.3 | —（视觉/键盘走查） | 验收 21、22 |

## 5. 待实施说明

- 红绿顺序建议：S1（静态最薄，先通）→ S4.1（VM 纯函数）→ S2 / S3（数据 + 组件）→ S5（回归兜底）→ S6 / S7（环境就绪后补）
- 权限键冻结基线（T1.3）：实现期从改造前提交导出键集数组内嵌测试文件，注明来源提交号
- 组件测试统一 jsdom 环境（沿现仓既有组件测试先例）；路由器用 memory history 真实例，不 mock vue-router
- Sidebar/Header 体量大：shallowMount + Element Plus 图标 stub；filterSegments 若抽为可导出纯函数则优先直测（S2.3-T2.5 可降为 VM 层）
- T5.6 仅冒烟既有通知中心行为，页面内部契约不重述（ui.md 已声明）
- S6/S7 不阻塞其余切片验收；执行者为 /idd-implement
