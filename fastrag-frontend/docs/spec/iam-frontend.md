# IAM 认证与账号权限 — Frontend Spec（iam-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 模式：grilling 由代码证据代答，证据分层 CERTAIN=引 file:line / INFERRED=待确认 / GAP=代码外信息）。
> Parent Trace：TASK/PAGE/STATE ← `docs/feature-map.md` 特性 1 证据包；领域术语 ← `CONTEXT.md`。

## 1. Scope

覆盖认证门户（登录/注册/忘记密码/403）、访问控制横切件（路由守卫、菜单过滤、按钮级权限）、管理后台账号域（角色/角色权限/组织/人员/权限项）以及知识库详情内嵌的 API Token 管理面板的前端行为。

**明确排除：** 后端认证/鉴权实现与 JWT 语义（属后端链）；知识库详情页本身的页面框架（属 kb-core 链，本链只定义其中 ApiTokenManager 面板）；`admin/content/*` 等孤儿页面（见 feature-map 孤儿清单，不建链）。

## 2. User Tasks

- **TASK-001 密码登录** — 在 `/login` 输入用户名/密码（可选图形验证码）提交 → 成功：写入令牌并跳 `/home`；失败：表单内错误提示，可重试。入口：未认证访问任意受保护路由被重定向、登录页直达。〔证据：login/index.vue:252-296、stores/user.ts:42-64 ‹CERTAIN›；验证码是否必填 [GAP: 未逐行核实验证码分支]〕
- **TASK-002 微信扫码登录** — 登录页切换微信扫码方式，扫码确认后登录。〔证据：.env 含 WECHAT_APP_ID/SECRET、后端有微信端点消费 ‹INFERRED›；前端二维码轮询/回调细节 [GAP: 未逐行核实]〕
- **TASK-003 注册账号** — `/register` 填写注册信息提交 → 成功后进入登录态或跳登录页。〔证据：register/index.vue 602 行 ‹CERTAIN›(文件存在)；成功后去向 [GAP: 未逐行核实]〕
- **TASK-004 忘记密码重置** — `/forgot-password` 走「身份验证 → 设置新密码」流程 → 成功后回登录页。〔证据：forgot-password/index.vue 661 行 ‹CERTAIN›(文件存在)；分步结构 [GAP: 未逐行核实]〕
- **TASK-005 无权限访问反馈** — 已登录但无权限访问登记路由 → 落 `/403` 页，可返回。〔证据：router/index.ts:69-104、error/403.vue 84 行 ‹CERTAIN›〕
- **TASK-006 角色管理** — `/admin/account/roles` 增删改查角色、设默认角色。〔证据：roles.vue 285 行，默认角色前端禁改保护 roles.vue:53-57,133 ‹CERTAIN›〕
- **TASK-007 角色权限配置** — `/admin/account/roles/:id/permissions` 按 MENU_PERM_TREE 勾选权限（含 ACTION_API_LINKS 联动组），保存提交去重后的全量权限键。〔证据：role-permissions.vue:7 消费 MENU_PERM_TREE+ACTION_API_LINKS、:18-193,264-327 ‹CERTAIN›〕
- **TASK-008 人员管理** — `/admin/account/personnel` 新增/编辑/删除人员、分配角色。〔证据：personnel.vue 326 行，新增默认角色取列表第 3 项或 id='3'（:150）‹CERTAIN›〕
- **TASK-009 组织管理** — `/admin/account/organization` 维护组织树。〔证据：organization.vue 159 行 ‹CERTAIN›(文件存在)；树编辑交互细节 [GAP: 未逐行核实]〕
- **TASK-010 权限项管理** — `/admin/permissions` 按分类维护权限键：API 类输入方法+路径后自动生成标识（`api:method:path`，标识框禁用）；页面操作类可附「所属页面」下拉（仅存表单不提交）；编辑模式标识禁改。〔证据：permissions/index.vue:206-284,386-462 ‹CERTAIN›〕
- **TASK-011 API Token 管理** — 知识库详情「API 文档」面板内创建/查看/吊销 Token。〔证据：ApiTokenManager.vue 363 行，由 detail/index.vue 引用 ‹CERTAIN›；面板交互细节 [GAP: 未逐行核实]〕
- **TASK-012 菜单可见性过滤** — 侧边栏仅渲染当前用户有权限的菜单项。〔证据：Sidebar.vue:213-257 按 MENU_PERMISSION_MAP 过滤 ‹CERTAIN›〕
- **TASK-013 按钮级权限控制** — 操作按钮经 `v-permission` 指令，无权限时从 DOM 移除。〔证据：directives/v-permission.ts:28-37，main.ts:26 全局注册 ‹CERTAIN›〕

## 3. Pages & Routes

- **PAGE-001 登录页** — `/login`；`requiresAuth:false`（routes.ts）；已登录访问送 `/home`；入口：守卫重定向 + 直达；退出路径：登录成功 → `/home`。〔router/index.ts、routes.ts ‹CERTAIN›〕
- **PAGE-002 注册页** — `/register`；`requiresAuth:false`；页脚来自登录页链接；退出：→ `/login`。
- **PAGE-003 忘记密码页** — `/forgot-password`；`requiresAuth:false`；退出：→ `/login`。
- **PAGE-004 无权限页** — `/403`；`requiresAuth:false`；入口：守卫判定无权限；退出：返回 `/home`。
- **PAGE-005 角色管理** — `/admin/account/roles`；入口：Sidebar「管理→账号权限」组（Sidebar:180-182，MAP:538-542）；退出：面包屑/侧边栏导航。
- **PAGE-006 角色权限配置** — `/admin/account/roles/:id/permissions`；hidden 路由；入口：PAGE-005 行操作；退出：返回 PAGE-005。
- **PAGE-007 组织管理** — `/admin/account/organization`；Sidebar:181。
- **PAGE-008 人员管理** — `/admin/account/personnel`；Sidebar:182。
- **PAGE-009 权限项管理** — `/admin/permissions`；Sidebar:183（MAP:543）。
- **内嵌面板：API Token 管理** — 挂在知识库详情 `detail/index.vue` 内（ApiDocPanel 域），无独立路由；其宿主页路由归 kb-core 链。

守卫横切规则（CERTAIN，router/index.ts:69-104）：`requiresAuth:false` 直放（已登录访问 `/login` 除外 → `/home`）；无令牌一律送 `/login`；有令牌按 MENU_PERMISSION_MAP 查权限，命中任一即过；**未登记路径默认放行**（:92-93，是否为最终设计 INFERRED-1）。

## 4. Elements & Interaction

**PAGE-001 登录页**（login/index.vue，537 行）
- 用户名/密码输入（回车提交）、登录按钮（loading 态）、页脚忘记密码/注册链接。表单默认预填 `admin/admin123`（:28-31,252-296 ‹CERTAIN›；是否生产移除 INFERRED-2）。
- 登录方式切换（密码 / 微信扫码）〔‹INFERRED›，细节 GAP〕。

**PAGE-002/003 注册页 / 忘记密码页** — 表单族 + 提交/返回按钮（结构细节 [GAP: 未逐行核实]，design/contract 阶段补齐）。

**PAGE-005 角色管理**（roles.vue）— 角色表格（分页）、新增/编辑/删除、设默认角色；**默认角色的权限编辑入口前端禁用**（:53-57,133 ‹CERTAIN›；后端无对应保护，是否补齐 INFERRED-3）。

**PAGE-006 角色权限配置**（role-permissions.vue，598 行）
- 按 MENU_PERM_TREE 四大组（首页/知识库/应用/管理）渲染树形勾选（:7、:18-193 ‹CERTAIN›）。
- 联动组：勾选页面操作自动补齐对应 API 键（ACTION_API_LINKS，如 kb:create ↔ api:kb:create）‹CERTAIN›。
- 加载回显规则：存量含 `*` → 全选；空数组（从未配置）→ 默认全选；否则回显并对联动组补齐（组内任一在则整组勾上）（:81-193,264-327 ‹CERTAIN›；「从未配置默认全选」是否合意 INFERRED-4）。
- 保存提交去重后的全量权限键；后端权限树加载失败时回退展示前端映射表全量（不过滤）（‹CERTAIN›）。

**PAGE-007 组织管理**（organization.vue）— 组织树展示与维护（细节 [GAP]）。

**PAGE-008 人员管理**（personnel.vue）— 人员表格（分页，BEM `__pagination`）、新增/编辑/删除对话框、角色分配；新增默认角色取角色列表第 3 项（索引 2）或回退 `id='3'`（:150 ‹CERTAIN›；该默认值是否合意 INFERRED-5）。

**PAGE-009 权限项管理**（permissions/index.vue，507 行）— 权限键表格（分页）+ 分类筛选；新增对话框按分类变形：API 类（HTTP 方法 + 路径后自动生成标识，标识框禁用）/ 页面操作类（附「所属页面」下拉，仅存表单不提交）；编辑模式标识禁改（:206-284,386-462 ‹CERTAIN›；「所属页面」不落库是否为待完善 INFERRED-6）。

**内嵌 ApiTokenManager**（363 行）— Token 列表 + 创建/吊销；明细 [GAP: 未逐行核实]。

**横切元素** — 顶栏用户区（components/layout/Header.vue，106 行）与退出登录〔‹INFERRED›：Header 结构未逐行核实〕。

## 5. State Matrix

STATE 通用集：`initial / loading / empty / error / retry / disabled`（按页适用者列出）。触发 → 可见反馈 → 恢复路径：

| 页面 | initial | loading | empty | error | retry | disabled |
|---|---|---|---|---|---|---|
| PAGE-001 登录 | 表单预填 admin/admin123 | 提交按钮 loading | — | 表单内错误提示（如凭据错误）〔后端错误文案映射 [GAP]〕 | 可重试 | 提交中按钮禁用 |
| PAGE-002/003 注册/忘记密码 | 空表单 | 提交 loading | — | 字段级/表单级提示 | 可重试 | 提交中禁用 |
| PAGE-004 无权限 | 静态提示 + 返回入口 | — | — | — | — | — |
| PAGE-005/007/008/009 管理四页 | 拉取列表 | 表格 loading | 「暂无数据」空态 | 错误提示（ ElMessage）+ 保留空表 | 重新拉取 | 行操作在无权限时经 v-permission 移除 |
| PAGE-006 权限配置 | 树加载 | 树 loading（树加载失败回退前端全量树，不阻塞编辑） | — | 保存失败提示，勾选态保留 | 可重新保存 | 默认角色进入时权限项禁改（roles.vue 保护一致） |
| ApiTokenManager 面板 | 拉取列表 | loading | 空态 | 提示 | 重试 | — |

规则：**任何页面不得出现未定义的 error 态**（本矩阵即契约）；表格空态、提交失败保留输入是统一要求。

## 6. Data Mapping

数据映射引用**前端消费代码实况**（FRONTEND 角色规则：client code 为 CERTAIN 源）；正式 `API-*`/`ERR-*` 编号待本链 contract 阶段锚定后回填替换 `TBD` 标记：

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 登录凭据 → 令牌+用户信息 | `stores/user.ts` login action（:42-64，POST /api/auth/login；setToken/setUserInfo/setLoginData；localStorage 持久化 :10-11） | TBD — pending API Contract（auth 组 8 端点） |
| 当前用户信息刷新 | `user.ts` fetchUserInfo；路由守卫读 localStorage 用户信息判权（router/index.ts:42-66） | TBD |
| 角色列表/CRUD | api roles 组（6 端点） | TBD |
| 角色权限读取/保存 | role-permissions.vue 提交去重全量键（:264-327） | TBD |
| 人员/组织/权限项 | api personnel 7 / org 7 / permissions 5 端点 | TBD |
| API Token | api api-tokens 端点（kb 域内） | TBD |
| 登出 | `user.ts` logout（清 storage → `/login`）；响应层 401 同效（request.ts:41-54） | TBD |

规则：不虚构后端字段；contract 阶段以 `src/api/index.ts` 实况 + 后端 springdoc 运行时锚定字段后，回填本表并消除 TBD。

## 7. Responsive & Accessibility

- 目标断点：**[GAP: 现有代码无响应式断点约定，桌面优先]**（后台管理形态，无 media query 体系 ‹INFERRED›）。
- 键盘路径：登录表单回车提交（login/index.vue ‹CERTAIN›）；其余页面键盘路径未成体系 [GAP]。
- 焦点顺序/读屏语义：**[GAP: 无无障碍约定与 axe 资产]**——按 2026-09-16 会话决议列为待用户定义项。

## 8. Acceptance

- [ ] 未认证访问任意受保护路由 → 落 `/login`；已登录访问 `/login` → `/home`
- [ ] 已登录无权限访问登记路由 → `/403`；未登记路由放行（守卫实况）
- [ ] 密码登录成功 → 令牌持久化（localStorage `ais_` 前缀）且跳 `/home`；失败 → 表单内提示可重试
- [ ] 侧边栏只显示有权限的菜单；无权限按钮不存在于 DOM（v-permission 移除而非隐藏）
- [ ] 角色权限页：勾选联动组自动补齐 API 键；保存提交去重全量键；后端树加载失败回退前端全量树且仍可编辑
- [ ] 存量 `*` → 全选回显；空数组 → 默认全选；部分配置 → 回显 + 联动补齐
- [ ] 默认角色：前端禁改权限（roles.vue 入口禁用）；新增人员默认角色取第 3 项或 id='3'
- [ ] 权限项管理：API 类标识自动生成且禁改；编辑模式标识禁改
- [ ] 401 响应 → 清 localStorage 并跳 `/login`；403 → 仅提示
- [ ] 列表页具备 loading/empty/error 三态；提交失败保留输入

## INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。要点：现状合意项确认入契约；缺陷/清理/接线类保留登记（backlog，代码不动）；后端/产品依赖为 GAP。以下原清单保留作对照。

## Stage INFERRED 清单（原清单，已被裁决覆盖）

INFERRED-1 未登记路由默认放行是否为最终设计；INFERRED-2 登录预填 admin/admin123 为开发便利、生产应移除；INFERRED-3 默认角色保护是否需后端补齐（前端已禁改）；INFERRED-4 「从未配置默认全选」是否合意；INFERRED-5 新增人员默认角色取第 3 项/id='3' 是否合意；INFERRED-6 权限「所属页面」仅存表单不落库是否为待完善；INFERRED-7 微信扫码登录形态（二维码轮询/回调）；INFERRED-8 Header 用户区结构。
