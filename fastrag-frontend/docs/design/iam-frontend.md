# IAM 认证与账号权限 — Frontend Architecture Design（iam-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/iam-frontend.md`（TASK-001..013 / PAGE-001..009）；术语：`CONTEXT.md`。
> Component 为一等术语（可复用 UI 单元）；Module 指非 UI 封装。

## 1. Page Map

| PAGE | 路由 | 权限 | 入口 | 退出 |
|---|---|---|---|---|
| PAGE-001 登录 | `/login` | requiresAuth:false | 守卫重定向、登出、直达 | 登录成功 → `/home` |
| PAGE-002 注册 | `/register` | requiresAuth:false | 登录页页脚 | → `/login` |
| PAGE-003 忘记密码 | `/forgot-password` | requiresAuth:false | 登录页页脚 | → `/login` |
| PAGE-004 无权限 | `/403` | requiresAuth:false | 守卫判定 | → `/home` |
| PAGE-005 角色管理 | `/admin/account/roles` | MAP:538-542 | Sidebar 管理→账号权限 | 侧边栏导航 |
| PAGE-006 角色权限配置 | `/admin/account/roles/:id/permissions` | MAP（hidden） | PAGE-005 行操作 | → PAGE-005 |
| PAGE-007 组织管理 | `/admin/account/organization` | MAP:538-542 | Sidebar:181 | 侧边栏导航 |
| PAGE-008 人员管理 | `/admin/account/personnel` | MAP:538-542 | Sidebar:182 | 侧边栏导航 |
| PAGE-009 权限项管理 | `/admin/permissions` | MAP:543 | Sidebar:183 | 侧边栏导航 |
| 内嵌 ApiTokenManager | （宿主 detail/index.vue） | v-permission/useAuth | 知识库详情「API 文档」域 | 关闭面板 |

## 2. Page/Layout/Component Map

### PAGE-001 登录页
```
┌───────────────────────────────────────┐
│  品牌区（Logo + 系统名, system store）   │
├───────────────────────────────────────┤
│  ┌─────────────────────────────────┐  │
│  │ 登录卡片                          │  │
│  │  [账号登录|微信扫码]  ← 方式切换    │  │
│  │  用户名  ┌───────────────────┐   │  │
│  │  密  码  ┌───────────────────┐   │  │
│  │  验证码  ┌────────┐ ┌──────┐    │  │
│  │  ┌────────────┐ ┌──────────┐    │  │
│  │  │  登 录     │ │  (loading)│    │  │
│  │  └────────────┘ └──────────┘    │  │
│  │  忘记密码?  ·  注册账号  ← 页脚   │  │
│  └─────────────────────────────────┘  │
└───────────────────────────────────────┘
```
组件：`views/login/index.vue`（页面）；方式切换与验证码行为内聚页内（单消费者，不抽 Component）。

### PAGE-002 / PAGE-003 注册页 / 忘记密码页
```
┌───────────────────────────────────────┐
│  品牌区                                │
├───────────────────────────────────────┤
│  ┌─────────────────────────────────┐  │
│  │ 表单卡片（字段族 + 校验提示）      │  │
│  │  ┌───────────────────────────┐  │  │
│  │  │  提交（loading 态）        │  │  │
│  │  └───────────────────────────┘  │  │
│  │  ← 返回登录（页脚链接）           │  │
│  └─────────────────────────────────┘  │
└───────────────────────────────────────┘
```
忘记密码为分步流（步骤条 + 步骤表单；步骤结构 [GAP: 未逐行核实，contract 阶段锚定]）。

### PAGE-004 无权限页
```
┌───────────────────────────────────────┐
│        403 插图 + 「无访问权限」文案      │
│        ┌──────────────┐               │
│        │  返回首页     │  → /home      │
│        └──────────────┘               │
└───────────────────────────────────────┘
```

### PAGE-005/007/008 管理列表页（同一布局族）
```
┌─────────────────────────────────────────────┐
│ Sidebar（权限过滤）  │  页头：标题 + [新增]     │
│  · 管理             ├───────────────────────┤
│    - 角色           │  筛选区（关键字/状态下拉）│
│    - 组织           │  ┌───────────────────┐ │
│    - 人员           │  │ 表格（行操作：编辑/  │ │
│    - 权限项          │  │ 删除/角色→PAGE-006）│ │
│                     │  └───────────────────┘ │
│                     │  roles__pagination     │
└─────────────────────────────────────────────┘
```
组件：页面 + 新增/编辑对话框（页内，单消费者）；删除确认走 `useConfirm`。

### PAGE-006 角色权限配置（本特性核心页）
```
┌─────────────────────────────────────────────┐
│ 页头：角色名 + [全选] [保存]                   │
├─────────────────────────────────────────────┤
│ el-tree（MENU_PERM_TREE 四大组渲染）           │
│  ▾ 首页                                      │
│  ▾ 知识库   ▸ 文件  ▸ 分片  ▸ AI分片 …        │
│      勾页面操作 → ACTION_API_LINKS 自动补 API 键│
│  ▾ 应用                                      │
│  ▾ 管理                                      │
├─────────────────────────────────────────────┤
│ 回显规则提示（* 全选 / 空 → 默认全选）           │
└─────────────────────────────────────────────┘
```
组件：`role-permissions.vue`；树数据映射函数页内具名（见 §4）。默认角色进入 → 树禁用态（与 roles.vue 保护一致）。

### PAGE-009 权限项管理
```
┌─────────────────────────────────────────────┐
│ 页头：标题 + [新增]      分类筛选（API/页面操作）│
├─────────────────────────────────────────────┤
│ 表格：标识 / 名称 / 分类 / 所属页面 / 操作      │
│  新增对话框（按分类变形）：                     │
│   API 类：方法▾ + 路径 → 标识自动生成（禁改）    │
│   页面操作类：标识 + 名称 + 所属页面▾（不提交）   │
└─────────────────────────────────────────────┘
```

### 内嵌 ApiTokenManager（宿主详情页）
```
┌───────────────────────────┐
│ [创建 Token]  Token 列表    │
│  名称 / 创建时间 / 吊销     │
│  创建成功一次性展示明文       │
└───────────────────────────┘
```
（明细 [GAP: 未逐行核实，contract 阶段锚定]）

## 3. State Ownership

| STATE | 所有者（唯一） | 说明 |
|---|---|---|
| 登录态（token/userInfo） | **client store**（stores/user.ts）+ localStorage 持久化（`ais_` 前缀） | 登出/401 均由 store 清理，唯一清理点 |
| 当前用户权限集 | client store（user store permissions）+ 静态 MENU_PERMISSION_MAP（代码内常量） | 判权纯前端查表 |
| 登录/注册/重置表单值 | **form**（el-form model） | 提交失败保留 |
| 列表数据（角色/人员/组织/权限项/Token） | **server (API)** → 页内 ref 镜像 | 不入 pinia（项目约定：页面态留页内） |
| 列表 loading/empty/error | client（页内 ref） | 矩阵见 spec §5 |
| 分页 state | client（usePagination composable） | 全站唯一分页实现 |
| 当前路由/查询参数 | **URL**（vue-router） | PAGE-006 的 :id 即数据键 |
| 权限树勾选态 | form（页内，保存前不落库） | 后端树加载失败回退前端全量树，仍属页内态 |

无任何 STATE 双所有者：token 写入只经 user store（setToken/setLoginData），页面不得直接写 localStorage。

## 4. Data Flow

```
API (api/index.ts auth/roles/personnel/org/permissions/api-tokens 组)
  → 传输适配：utils/request.ts（唯一解包点 {code,data,message}；401 清理跳登录）
  → 模型适配：stores/user.ts（login/fetchUserInfo/logout —— auth 域唯一模型适配器）
  → ViewModel：页内具名映射（角色行、人员行、权限树节点）
  → Component：表格/树/表单渲染
```

具名转换位置（不许散落组件内联）：
- **认证模型适配器** = `stores/user.ts`（token/userInfo 归一、持久化、清理）。
- **权限树映射** = `role-permissions.vue` 页内 `MENU_PERM_TREE(+ACTION_API_LINKS) → el-tree data` 具名函数（回显规则同处实现，spec TASK-007）。
- **列表行映射** = 各管理页页内 `API row → 表格 row` 具名函数；字段级映射待 contract 锚定 API-* 后回填。

## 5. Visual System

- **Design Tokens：** 单一事实源 `assets/styles/variables.scss`（66 行九组：主题色/文本/边框/背景/侧栏/间距/圆角/阴影/布局尺寸），经 `global.scss` 全局注入；公共类 `.page-container` / `.card-panel`（global.scss:1-186）。**SFC 不直接 @use variables**（现状 0 引用，保持）。
- **组件变体：** Element Plus 默认体系（el-table / el-dialog / el-form / el-tree / el-pagination）；自动导入（vite.config AutoImport + Components）。登录/门户页用独立卡片布局（非 AppLayout 嵌套）。
- **断点：** [GAP] 现状无响应式断点约定，桌面优先（管理后台形态）；如需移动端，另立 ADR。
- **动效/反馈：** 提交按钮 loading 态；操作反馈 ElMessage（成功/错误）；删除类操作 useConfirm 二次确认。表格骨架不引入（现状直接 loading 态）。

## 6. Deepening Opportunities

- **user store**：可再吸收「用户信息过期刷新」「多标签页登录态同步」而不扩接口——当前不实现，留作加深点。
- **权限四层件**：可吸收「权限变更热更新」（现在依赖重新登录刷新）——待 INFERRED-1 裁决后评估。
- 其余页面组件均为单消费者，无加深计划（避免过早抽象）。

## 7. Parent Trace

- 守卫/菜单/按钮三层消费结构 → spec TASK-005/012/013
- user store 单一清理点 → spec TASK-001、Acceptance「401 响应」
- role-permissions 树映射 + 回显规则 → spec TASK-007（INFERRED-4）
- roles/personnel/permissions 页内对话框 → spec TASK-006/008/010（INFERRED-3/5/6）
- token localStorage 持久化 → spec §3 状态所有权（安全权衡见 ADR token-in-localstorage）

## ADR 索引

- `docs/design/adr/static-permission-map-default-allow.md` — 静态权限映射 + 未登记默认放行的守卫架构
- `docs/design/adr/token-in-localstorage.md` — 令牌存储于 localStorage（非 cookie）
