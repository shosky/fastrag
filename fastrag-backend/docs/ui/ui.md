---
name: UI Contract
summary: 前端 UI 契约——首页门户、平台导航壳与 Widget 三链的页面、状态、数据映射与 API 依赖。
type: ui
id: UI-UI
status: draft
updated: 2026-11-04
satisfies: [SPEC-PORTAL-FRONTEND, SPEC-NAV-SHELL-FRONTEND, SPEC-WIDGET-FRONTEND]
decisions: [ADR-PORTAL-INDEPENDENT-SHELL, ADR-NAV-PERM-TREE-DECOUPLING, ADR-0010-WIDGET-TOOL-CHANNEL-SNAPSHOT-PROTOCOL, ADR-0011-WIDGET-TEMPLATE-DSL]
declares: []
profile: frontend
hash: "1ad1b6cacdcd"
summaryHash: "1ad1b6cacdcd"
---

# UI Contract — 首页门户与平台导航壳

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

锚点：SPEC-PORTAL-FRONTEND 的 PAGE-001（2026-11-03 Yuxi 式重构版，分区顺序 = hero → 知识引擎 → 应用与智能体 → 工作原理 → 为什么选 → 业务场景 → 技术栈 → 资源 → FAQ → CTA → 页脚）。

- hero 四层文案（徽章「私有化部署 · 开箱即用」/ 大标题「让企业知识 一问即答」含「一问即答」渐变 / 副标题「企业级检索增强生成（RAG）平台」/ 描述段）+ 浏览器窗框截图 — 源：静态常量 + `/portal/crop-chatcolumn.jpg`；显示：恒显；交互：无。
- hero 主按钮 — 源：登录态；显示：恒显；交互：游客「立即开始」跳 /login，已登录「进入平台」跳平台 /home。
- 次按钮「查看操作手册」 — 源：静态；显示：恒显；交互：跳 /portal/manual。
- 知识引擎子能力行 ×6（「知识图谱」行配实拍锚点 scene-graph.png） — 源：静态常量；显示：恒显；交互：无（行不可点击、无 pill）。
- 应用与智能体子能力行 ×5，每行挂 1-2 静态 pill（「应用与工作流编排」行配实拍锚点 scene-application.png） — 源：静态常量；显示：恒显；交互：无（行与 pill 均不可点击）。
- 工作原理四步卡（01 上传解析 → 02 知识加工 → 03 创建应用 → 04 对话溯源） — 源：静态常量；显示：恒显；交互：无。
- 为什么选 FastRAG 卖点卡 ×4 — 源：静态常量；显示：恒显；交互：无。
- 业务场景卡 ×4 — 源：静态常量 + `/portal/` 截图（crop-chatcolumn/crop-kb/crop-analytics/scene-application）；显示：恒显；交互：整卡 → 平台 /home。
- 技术栈标签 ×6（MySQL/Redis/RabbitMQ/Neo4j/Milvus/MinIO 纯文字 pill） — 源：静态常量；显示：恒显；交互：无（非交互，无 hover）。
- 资源卡「操作手册」 — 源：静态；显示：恒显；交互：跳 /portal/manual。
- 资源卡「产品动态」+ 徽标 — 源：`changelog[0].version`；显示：数组非空（STATE-002 反向）；交互：跳 /portal/changelog。
- FAQ 折叠面板 ×5 — 源：静态常量；显示：恒显；交互：展开/收起。
- CTA 深色横幅（副句「四步」口径） — 源：静态；显示：恒显；交互：主按钮同 hero 登录态规则。
- 页脚链接 — 源：静态；显示：恒显；交互：操作手册 / 产品动态 / 登录（或已登录「进入平台」）。

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

- 平台侧边栏 logo（Sidebar.vue logo 区；nav-shell 改造为单列宽侧栏后行为沿用）— 交互：点击跳 /portal（回门户）。
- 平台内「首页」文字 ×4（Sidebar.vue:84 侧边栏首项、Header.vue 面包屑根、office-edit.vue 与 chunks.vue 面包屑根）— 显示：改为「主页」；路由路径与功能不变。
- document.title 后缀（router/index.ts 守卫）— 显示：「- FastRAG」。
- 登录页左上角「回到首页」链接（login/index.vue `.login-brand__back`）— 显示：登录页恒显；交互：跳 /portal。

### 单列宽侧栏（平台壳页 · Sidebar.vue 原地重写，nav-shell 链）

锚点：SPEC-NAV-SHELL-FRONTEND「页面与路由」平台壳条目。

- 主页项（顶置） — 源：`nav-segments.ts` `home`；显示：恒显；交互：跳 /home，激活时高亮。
- 段标题 ×4（对话/构建/资源/管理） — 源：`nav-segments.ts` `segments[].title`；显示：段内 ≥1 项可见（STATE-102）；交互：无（非交互文本，不可聚焦）。
- 用户入口（内容卡头右上角头像 chip，铃铛右侧；修订 #14 同日三次修订） — 源：userStore（`realName || username` 回退）；显示：登录恒显；交互：点击弹出下拉（头部姓名+用户名，非交互）。
- 菜单项 ×14（侧边栏）— 源：`nav-segments.ts` + 权限过滤；显示：有权限（STATE-102）；交互：router-link 跳转，当前项高亮；成员/路径/权限依据以 SPEC-NAV-SHELL-FRONTEND「元素与交互」清单为权威（修订 #15/#18：对话 1 + 构建 1 + 资源 8 + 数据分析 4，管理域入下拉、知识库分类入知识库页）。
- 知识库列表页分类面板（修订 #18） — 源：CategoryPanel 组件（`views/knowledge/components/CategoryPanel.vue`）；结构：全部/未分类/各分类（前端计数徽标）+ 分类名搜索 + 面板折叠（40px 窄条）+ 行「…」操作菜单（编辑/删除，`kb:category:*` 门禁）+ 新增/编辑对话框（含所属组织[超管]）；分类选择经 `update:modelValue` 驱动父页过滤，「未分类」以 `__uncategorized__` 哨兵口径过滤。
- 折叠开关（修订 #15） — 源：静态 + localStorage（`ais_nav.sidebarCollapsed`）；显示：侧边栏底部恒显；交互：点击切换图标态（64px，段标题/菜单文字隐藏，菜单项原生 title 提示），刷新保持。
- 菜单高亮 — 源：`route.path` 派生（最长前缀回溯、叶子优先）；特例：/admin/platform/model-management 高亮资源段「模型管理」项。
- 下拉用户信息 — 头部正下方单项（既有六字段弹窗，组织名懒加载不变）。
- 下拉「门户资源」分组（修订 #16） — 源：`nav-segments.ts` `userMenuGroups[0]`；操作手册（跳 `/portal/manual`）/ 产品动态（跳 `/portal/changelog`），无权限键恒显。
- 下拉「管理」级联行（修订 #17） — 源：`nav-segments.ts` `userMenuGroups[1]`（cascade=true）；显示：下拉内一行（「管理」+ 左箭头），子面板项逐项按原权限键过滤；交互：悬停向左弹出 170px 子面板（自绘绝对定位，行展开/离行 150ms 收回/移入面板取消），点击子项跳转并关闭；侧边栏内不出现管理域菜单。
- 下拉退出系统 — 恒在最底（分隔线之下，danger）；交互：确认后清会话跳 `/login`。
- 折叠态悬停浮出（修订 #16） — 触发：折叠态鼠标悬停侧边栏；可见反馈：右侧 208px 完整菜单浮层（含段标题与文字项）；收回：鼠标离开或点击菜单项。
- 侧栏 logo — 显示：恒显；交互：跳 /portal（门户链行为沿用）。
- 不出现项：「业务流」「测试案例」「应用运行」菜单项与「运营中心」分组名；侧边栏内不出现管理域菜单（通用设置/角色管理等，修订 #15）。

### 通知铃铛（平台壳页 Header 内，NotificationBell.vue，nav-shell 链）

- 铃铛图标 — 源：静态；显示：登录恒显；交互：跳 /notification。
- 未读数徽标 — 源：API 依赖节 unread-count；显示：formatBadge ≠ ''（STATE-101）；交互：无。

### 通知中心页（通知中心路由 · 既有页零改动，nav-shell 链）

锚点：SPEC-NAV-SHELL-FRONTEND「页面与路由」通知中心条目。复用 `views/notification/index.vue` 全部元素与交互（未读/已读 tab、标记已读、全部已读、删除）；元素语义属页面自身，本契约不重述。

### 权限树展示同步（menu-perm-tree.ts，nav-shell 链）

- 角色权限配置页分组标题 — 源：`MENU_PERM_TREE` 分组节点；显示：随导航四段重排；页面节点标题与 `keys/actions/apis` 冻结（ADR-NAV-PERM-TREE-DECOUPLING）。


### Widget 工作台（列表/新建/编辑；widget 链）

锚点：PAGE-201~203（spec/widget-frontend.md 声明）。

列表页（PAGE-201）：
- 双 tab（我的 Widget / 官方模板） — 源：静态；显示：恒显；交互：切换 API-001 `builtin` 查询参数。
- 资产行（名称/描述/图标/更新时间） — 源：API-001 `WidgetSummary[]`；显示：按 tab 过滤；交互：行点击 → API-002 进编辑页。
- 「新建」按钮 — 源：静态；显示：我的 tab；交互：跳 /widget/create。
- 行操作 编辑/删除 — 源：静态；显示：我的 tab；交互：编辑进详情；删除 confirm → API-005，`ERR-WG-BOUND-DELETE`(409) toast 提示先解绑。
- 行操作 复制 — 源：静态；显示：官方 tab；交互：API-006 → 跳新资产编辑页。
- 空态引导 — 源：API-001 空数组；显示：我的 tab 空；交互：新建 / 去官方模板复制双按钮。

新建页（PAGE-202）与编辑页（PAGE-203）：
- 基础信息表单（名称*/描述/图标） — 源：表单态；交互：随保存提交 API-003/004 `WidgetSaveRequest`。
- 三件套三 tab JSON 文本域 + 格化按钮 — 源：API-002 三字段（template/schemaJson/defaultJson）；交互：本地格式化不调接口；保存 → API-003/004。
- 行级错误区 — 源：`ERR-WG-SCHEMA-INVALID`(400) message（
 分行）；显示：仅保存失败；交互：点击错误聚焦对应 tab。
- 预览面板 — 源：编辑态 template+defaultJson 经 `toPreviewSnapshot()`；显示：编辑区一侧恒显；交互：刷新重渲；preview=true（sys.submit 禁用+提示）。

### Widget 绑定面板（widget 链）

锚点：PAGE-204。

- 候选卡片列表（勾选） — 源：API-001（自建资产）与 API-007 现绑定求差；显示：全部候选；交互：勾选。
- 启用开关 — 源：API-007 `enabled`；显示：已勾选项；交互：切换。
- 「保存」 — 源：静态；交互：diff 增删（API-008/009/010）；`ERR-WG-BINDING-LIMIT`(409) toast 上限提示。

### 对话卡片渲染面（widget 链）

锚点：PAGE-205。

- 卡片容器 + 状态文本区 — 源：tool_result.data `WidgetSnapshot`（API-012）；显示：消息流工具位；交互：无。
- 组件树递归渲染 — 源：snapshot.template + data 经 `toWidgetSnapshot()`；交互：按组件语义（表单可输入、列表项可点）。
- 动作按钮行 — 源：模板动作配置；交互：go_to_url/download 本地处理；sys.submit → `buildWidgetAction()` → API-011。
- 调用中占位 — 源：tool_call(name=render_widget)（API-012）；显示：调用中；交互：无。
- 降级占位 — 源：渲染时判定；显示：未知组件/坏结构/缺快照；交互：无（不崩整卡）。

### 问答对面板批量确认（QaPanel · qa-pairs 链，2026-11-02）

锚点：知识库详情页「问答对」tab（QaPanel.vue；spec/qa-pairs.md §8 UI Decisions）。

- 行首复选框列 — 源：行数据；显示：editor 及以上（REQ-A05），viewer 不渲染；交互：勾选/取消，翻页保留。
- 表头全选 — 源：静态；交互：选中当前筛选结果（搜索/状态/来源过滤命中行）全部行；再点全取消。
- 批量操作栏（「已选 N 条」+ 批量确认） — 源：选中集合；显示：选中数 > 0；交互：批量确认 → 二次确认弹窗 → batch-confirm 提交。
- 「批量确认」可用性 — 源：选中集合派生；禁用：所选无任何 draft 行（无可确认项）。
- 二次确认弹窗 — 源：选中集合统计（共 N / 跳过 M / 本次确认 K）+ 不可逆提示；交互：确认 → 提交（loading 防重）；取消 → 关闭。
- 结果反馈 — 源：端点 confirmedCount/skippedCount；交互：成功 toast「已确认 X 条，跳过 Y 条」+ 刷新列表 + 清空选中；失败 toast（拦截器注入 message）保留选中可重试。
- 状态筛选项 — 源：静态 draft/confirmed 两值（本期清偿「启用/停用」死选项偏差，决议 D8）。

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

### 未读数查询态（铃铛徽标 · 平台壳页；nav-shell 链）

| STATE | 触发 | 可见反馈 | 恢复 |
|---|---|---|---|
| initial | 组件挂载拉取中 | 无徽标 | — |
| success = 0 | 响应 0 | 无徽标 | — |
| success 1–99 | 响应 n | 徽标显示 n | — |
| success ≥ 100 | 响应 n | 徽标显示 99+ | — |
| error | 请求失败或响应非数值 | 无徽标，无报错弹窗 | 下次路由离开 /notification 或刷新页面重拉 |
| refresh | 路由离开 /notification | 重拉，徽标随之更新 | — |

### 菜单权限过滤（侧边栏 · 平台壳页；nav-shell 链）

| STATE | 触发 | 可见反馈 | 恢复 |
|---|---|---|---|
| 过滤 | 权限集缺对应菜单键 | 菜单项不渲染；段内全部无权限时段标题不渲染 | 授予权限后重新登录可见 |

### 通知中心页内状态（通知中心路由；nav-shell 链）

loading/empty/error 由既有页面自持，本契约不重述；铃铛感知页内已读/删除操作的方式 = 路由离开 /notification 时重拉（无页内事件耦合）。

显式不适用（nav-shell 范围）：表单/提交态不存在（侧栏与铃铛零写操作）；disabled / offline 不适用；未读数边界值即上表五行（0/1/99/100/失败），无更大输入空间。


### Widget 工作台页面态（widget 链）

锚点：PAGE-201~204。

| STATE | 触发 | 可见反馈 | 恢复 |
|---|---|---|---|
| STATE-201 列表加载 | 进入页面拉取（API-001） | 骨架屏 | — |
| STATE-202 我的空态 | API-001 空数组 | 「新建/去官方模板复制」双引导 | 点击引导 |
| STATE-203 官方空态 | builtin 视图空（播种失败兜底） | 空态 + 重试 | 重拉 |
| STATE-204 接口失败 | 任意接口失败 / 非 200 code | toast（message 文案）；列表类附重试 | 重试或修正重存 |
| STATE-205 校验失败 | `ERR-WG-SCHEMA-INVALID`(400) | 行级错误落对应 tab，不弹窗 | 修正重存 |
| STATE-206 预览态 | 编辑内容派生 + 手动刷新 | preview=true 渲染，提交禁用 + 提示 | 进应用对话调试 |

### Widget 卡片生命周期（widget 链）

锚点：PAGE-205（spec/widget-frontend.md 声明）。

| STATE | 触发 | 可见反馈 | 恢复 |
|---|---|---|---|
| STATE-207 调用中 | tool_call(name=render_widget) | chip 隐藏 / 轻量占位 | 快照到达转渲染 |
| STATE-208 降级占位 | 未知组件 / 坏结构 / 缺快照 | 卡内占位 + console.warn，不崩整卡 | 无（数据不可修复） |
| STATE-209 可交互 | 卡片在最新一条助手消息 | 动作正常 | 用户操作 |
| STATE-210 置灰 | 其后新消息到达（含回传消息） | 动作 disabled；`alwaysEnabled` 豁免可点 | 无（窗口关闭） |
| STATE-211 提交中 | sys.submit 点击 | loading 防重 | 成功转下一轮 / 失败转 212 |
| STATE-212 回传失败 | API-011 错误（含 `ERR-WG-ACTION-OVERSIZE` 400） | 错误提示；消息序未推进、窗口未关 | 卡片保持可交互，直接重试 |

### 问答对批量确认态（QaPanel · qa-pairs 链）

锚点：spec/qa-pairs.md REQ-B04/B05（前端面）。

| STATE | 触发 | 可见反馈 | 恢复 |
|---|---|---|---|
| idle（无可选） | 选中集为空，或所选全为 confirmed | 操作栏隐藏 / 按钮禁用 | 勾选含 draft 行 |
| confirm-pending | 弹窗点击「确认」 | 提交按钮 loading 防重 | 成功转 done / 失败转 error |
| done | 端点 200 | toast「已确认 X 条，跳过 Y 条」+ 刷新列表 + 清空选中 | — |
| error | 端点失败 / 非 200 code | toast（message 由拦截器注入） | 保留选中，可直接重试 |
| 超限拒绝 | ids > 10000 业务错误 | toast 后端 message（常规交互不可达：全选受列表规模约束） | 分批操作 |

显式不适用：列表查询的 loading/empty/error 由面板既有逻辑自持，本契约不重述；无表单输入，输入边界即「选中集合规模 ≤ 10000」。

## 数据映射 {#data-mapping}

- `src/content/manual.md`（Vite ?raw）→ `parseManualToc()` @ `utils/manualToc.ts` → 目录树 → ManualToc
- `src/content/manual.md`（Vite ?raw）→ `renderMarkdown()` @ `utils/markdown.ts` → HTML → 内容区
- `src/constants/changelog.ts` → 直读（无转换）→ 时间线 + 首页徽标 `changelog[0].version`
- 首页分区内容（hero 四层文案 / 双支柱子能力与 pill 文案 / 四步 / 技术栈清单）→ `views/portal/index.vue` 常量数组直读（无转换；2026-11-03 Yuxi 式重构）
- 页面截图 → `public/portal/` 引用清单 5 张（crop-chatcolumn / crop-kb / crop-analytics / scene-application / scene-graph），构建期静态资源（2026-11-03 重构锁定；未引用 7 张随重构删除）
- userStore.username 计算属性（realName 缺失回退 username，store 内定义）→ 顶导航用户名（仅已登录显示）

无 View Model 适配层——唯一结构转换是 parseManualToc，其余为直读。

### nav-shell 链映射

- `GET /notifications/unread-count` 响应数值 n → 徽标文案（'' / n / 99+） — 转换：`formatBadge()` @ NotificationBell.vue（唯一命名转换点）
- `nav-segments.ts` NavConfig → 可见段树 — 转换：`filterSegments()` @ Sidebar.vue（MENU_PERMISSION_MAP 按 path 优先、requirePerm 兜底、空段隐标题；平移自原 Sidebar.vue:219-263）
- `route.path` → 高亮项 — 转换：`isActive()` @ Sidebar.vue（最长前缀回溯、叶子优先；平移自原 Sidebar.vue:336-352）
- userStore realName/username → 用户区姓名 — store 内计算属性直读
- `GET /org/flat` → 用户信息弹窗组织名 — 既有懒加载映射平移

无新增 View Model 适配层；通知列表等五个通知端点由通知中心页既有代码自消费，不进本契约。


### widget 链映射

- API-001 `WidgetSummary[]` → 列表行 — 转换：`toWidgetListItem()` @ `api/widget.ts`
- API-002 `WidgetDetail` → 编辑表单三件套 — 转换：`toWidgetDraft()` @ `api/widget.ts`
- API-012 tool_result `data`（实时）与消息 toolCalls `result.data`（历史）→ `WidgetSnapshot` — 转换：`toWidgetSnapshot()` @ `utils/widget/snapshot.ts`（实时/历史同构唯一转换点）
- 编辑态 template + defaultJson → 预览快照 — 转换：`toPreviewSnapshot()` @ `utils/widget/snapshot.ts`
- 卡片动作 + 表单收集值 → API-011 `widgetAction` — 转换：`buildWidgetAction()` @ `utils/widget/snapshot.ts`
- `ERR-WG-*` message → toast / 行级错误文案 — 语义引用 ERR，呈现文案前端自持

### qa-pairs 链映射

- 选中行 → `selectedIds: string[]` — 转换：QaPanel 内以 id 集合自维护（跨页保留，翻页不丢）
- 选中集合 → 弹窗统计（共 N / 跳过 M / 本次确认 K） — 转换：QaPanel computed（按 `status === 'confirmed'` 分派跳过数）
- 响应 `{confirmedCount, skippedCount}` → toast 文案「已确认 X 条，跳过 Y 条」 — 直读拼接，无适配层

## API 依赖 {#api-dependency}

无。门户一期零后端接口（CONTEXT.md 决策快照 23），不引用任何 `API-*` / `ERR-*`。

未来若产品动态升级为后端维护（运营 CRUD），收敛点：本节新增对应 `API-*`（Source：后端仓 `docs/interfaces.md`），数据消费方从 `changelog.ts` 直读切换为该接口——页面与组件不变。

### nav-shell 链

- `GET /notifications/unread-count` — 消费签名：无必填请求参数，响应 = 数值（未读条数）；前端封装 `getUnreadNotificationCount()`（`api/index.ts:2128`）。Source：运行时 springdoc OpenAPI（fastrag-bootstrap:8081 `/v3/api-docs`，即 CONTEXT.md IDD Metadata 的 API Contract Source）；仓库无静态 interfaces.md 故无 `API-*` 编号——后续生成静态契约时须回填编号并核对本签名。
- 兼容窗口：响应保持「数值」语义即可；对象化改版（如 `{count}`）属破坏性变更，须同步 NotificationBell 的 formatBadge。
- Mock/fixture：手工验收 mock 五例——0 / 1 / 99 / 100 / 请求失败（SPEC-NAV-SHELL-FRONTEND 测试策略）。
- 仅此一个新增消费端点；无 `ERR-*` 声明（失败态静默降级为无徽标，不产生错误码语义）；其余四个通知端点为通知中心页既有消费，未新增依赖。

### widget 链

| API | Source | Version | 兼容窗口 | Mock/fixture |
|---|---|---|---|---|
| API-001~010（资产 + 绑定） | `docs/interfaces.md`（本仓） | v1.0.0 | v1.x 只增不改 | spec/widget-frontend.md 测试策略，tdd 期落 fixture |
| API-011/012（SSE 扩展） | 同上 | v1.0.0 | 事件词汇零新增为硬约束（ADR-0010） | mock SSE 事件序列（沿用两 composable 测试模式） |
| `ERR-WG-SCHEMA-INVALID` / `ERR-WG-BINDING-LIMIT` / `ERR-WG-ACTION-OVERSIZE` / `ERR-WG-BOUND-DELETE` | 同上 declares | — | 语义只增不改 | 校验失败样例三档各一 |

### qa-pairs 链

- `POST /api/kb/{kbId}/qa-pairs/batch-confirm` — 消费签名：请求 `{ids: string[]}`（去重，≤10000），响应 `ApiResponse<{confirmedCount, skippedCount}>`；权限 editor（403 由拦截器统一提示，不登出）。Source：运行时 springdoc OpenAPI（同 nav-shell 链口径——仓库无静态 interfaces.md，且 qa-pairs 端点面按设计文档端点排除铁律不进 interfaces.md，见 design/qa-pairs.md §2）；后续生成静态契约时须回填 `API-*` 编号并核对本签名。
- 兼容窗口：响应两计数字段语义只增不改；「混选幂等跳过 + 单事务整批原子」为硬语义（REQ-B04）。
- Mock/fixture：前端 vitest 组件测试（勾选派生禁用态 / 弹窗统计 N-M-K / 成功清空选中，落 `QaPanel.test.ts`）；后端 Mockito（混选跳过 / 原子回滚 / 超限拒绝，落 `QaPairServiceImplTest`）。

注：`ERR-WG-NOT-IN-BINDING` / `ERR-WG-OVERSIZE` 为对话流内工具路径错误（非 HTTP），前端仅表现为「无卡片」，无独立错误态。
