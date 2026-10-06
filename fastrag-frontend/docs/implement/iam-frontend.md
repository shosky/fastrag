# IAM 认证与账号权限 — Implementation Document（iam-frontend）
> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式：特性已实现，本文档描述契约如何被现有代码实现；行为以代码为准，偏差即勘误线索）。
> 上游：`docs/spec/iam-frontend.md` → `docs/design/iam-frontend.md` → `docs/contract/ui.md`（特性 1 节）。

## 1. Code File Map

```
src/
├── router/
│   ├── routes.ts                  — 64 条页面路由声明（含 4 条认证前页 requiresAuth:false）
│   └── index.ts                   — 全局守卫（判权唯一入口）
├── stores/
│   └── user.ts                    — 认证模型适配器：token/userInfo、持久化、login/logout/fetchUserInfo
├── utils/
│   └── request.ts                 — axios 实例：解包 {code,data,message}、401/403 处理
├── directives/
│   └── v-permission.ts            — 按钮级权限指令（无权限移除 DOM）
├── config/
│   └── menu-perm-tree.ts          — 权限树真源：MenuPermNode + ACTION_API_LINKS + MENU_PERM_TREE
├── types/
│   └── auth.ts                    — MENU_PERMISSION_MAP 路由权限映射（守卫数据源）
├── composables/
│   └── useAuth.ts                 — 权限/角色判断 + KB 级 ACL 缓存包装
├── components/layout/
│   ├── Sidebar.vue                — 菜单按 MENU_PERMISSION_MAP 过滤
│   ├── AppLayout.vue              — 登录后根布局（router 唯一嵌套父级）
│   └── Header.vue                 — 顶栏用户区（登出入口）
└── views/
    ├── login/index.vue            — PAGE-001（密码/微信扫码；预填 admin/admin123）
    ├── register/index.vue         — PAGE-002
    ├── forgot-password/index.vue  — PAGE-003（分步重置 [GAP: 步骤结构未逐行核实]）
    ├── error/403.vue              — PAGE-004
    ├── admin/account/roles.vue            — PAGE-005
    ├── admin/account/role-permissions.vue — PAGE-006（权限树配置核心页）
    ├── admin/account/organization.vue     — PAGE-007
    ├── admin/account/personnel.vue        — PAGE-008
    ├── admin/permissions/index.vue        — PAGE-009
    └── knowledge/detail/components/ApiTokenManager.vue — 内嵌 Token 面板
```

测试文件：**无**（tdd 阶段 blocked——测试基建决策待用户，见 §6）。

## 2. Interface Implementations

### 路由守卫（from contract: 守卫横切规则 / spec TASK-005、012）

**Logic Flow:**
```
1. IF 目标路由 requiresAuth === false THEN
   1.1 IF 已登录 AND 目标 === /login THEN redirect /home
   1.2 ELSE 放行
2. IF 无 token（user store / localStorage）THEN redirect /login
3. 递归 findRoutePerms(目标路由) 查 MENU_PERMISSION_MAP
4. IF 未登记（无权限记录）THEN 放行（默认放行，index.ts:92-93）
5. IF 用户权限命中任一（通配 * / 直接权限 / 角色继承，super_admin 恒真）THEN 放行
6. ELSE redirect /403
7. afterEach: document.title = '${route.title} - AIS'（index.ts:71-72）
```

**Module Interactions:** 读 stores/user（登录态）；读 types/auth（MENU_PERMISSION_MAP）；写 document.title。**Called by:** vue-router 全局 beforeEach。

**Decisions & Deviations:** 无偏差（行为即契约；「默认放行」架构理由见 ADR static-permission-map-default-allow）。

### 认证模型适配器 user store（from contract: API-AUTH-001 数据映射 / spec TASK-001）

**Logic Flow:**
```
login(凭据):
1. POST /api/auth/login（经 request.ts）
2. IF body.code !== 成功 THEN 抛业务错误（页面捕获 → 表单提示，输入保留）
3. setToken(body.data.token) → localStorage（ais_ 前缀，:10-11）
4. setUserInfo(body.data.userInfo) → localStorage
5. RETURN 成功 → 页面跳 /home
logout():
1. 清 store 内 token/userInfo
2. 清 localStorage
3. 跳 /login
（响应层兜底：任何 API 返回 code 401 → request.ts 清 storage 跳 /login，与登出共用同一清理语义）
```

**Module Interactions:** 调 api/index.ts auth 组；被 login 页、Header 登出、路由守卫消费；共享 localStorage（`ais_` 前缀 storage.ts 封装）。

**Decisions & Deviations:** 唯一写入/清理点纪律（design §3）；token 存储取舍见 ADR token-in-localstorage。

### 权限树回显与保存（from contract: PAGE-006 / spec TASK-007）

**Logic Flow:**
```
加载:
1. 并行拉取 后端角色存量权限集 + 渲染真源 MENU_PERM_TREE（前端静态）
2. IF 后端树/存量加载失败 THEN 回退：以 MENU_PERM_TREE 全量渲染（不过滤），可继续编辑（fallback 态）
3. 回显:
   IF 存量含 '*' THEN 全选
   ELIF 存量为空数组（从未配置）THEN 默认全选（INFERRED-4）
   ELSE 回显存量键 + 联动补齐：组内（ACTION_API_LINKS）任一键在 → 整组勾选
保存:
1. 收集勾选键 → 去重 → 全量键数组
2. IF 保存中 THEN 忽略（按钮禁用防双提交）
3. IF 默认角色 THEN 不可达（入口已禁用 + 树只读双保险）
4. 提交保存 API；失败 → ElMessage + 勾选态保留可重试
```

**Module Interactions:** 读 config/menu-perm-tree.ts；调 api roles 组；被 PAGE-005 路由携带 :id 进入。

**Decisions & Deviations:** 树映射为页内具名函数（contract Data Mapping 指定位置），未抽公共 composable——单消费者不抽象（design 深度检查）。

### 响应解包 request.ts（from contract: 统一响应约定）

**Logic Flow:**
```
响应拦截:
1. IF HTTP 层异常 THEN 走 axios 错误分支（网络提示）
2. 解包 body {code,data,message}
3. IF code === 401 THEN 清 storage → redirect /login
4. IF code === 403 THEN ElMessage 提示（不登出）
5. IF code !== 成功 THEN 拒绝业务错误（页面捕获）
6. ELSE 返回 data
```

**Parent Trace:** 各自 → `docs/contract/ui.md` 特性 1 对应节 → design 对应模块 → spec TASK。

### v-permission 指令（spec TASK-013）

**Logic Flow:**
```
mounted/updated:
1. 取绑定值（权限键或键数组）
2. IF 用户权限不命中（同守卫判权语义）THEN el.parentNode.removeChild(el)
```

### 页面级实现（登记制）

| 页面 | 状态归属 | 关键实现点 | 证据 |
|---|---|---|---|
| PAGE-001 | form + user store | 预填、loading、方式切换 | login/index.vue:28-31,252-296 |
| PAGE-005 | 页内 ref + server | 默认角色禁改入口 | roles.vue:53-57,133 |
| PAGE-006 | 页内 form | 回显/联动/fallback/去重保存 | role-permissions.vue:7,18-193,264-327 |
| PAGE-008 | 页内 ref + server | 默认角色索引 2 / id='3' | personnel.vue:150 |
| PAGE-009 | 页内 ref + server | 分类变形对话框、标识禁改 | permissions/index.vue:206-284,386-462 |
| PAGE-002/003/007、ApiTokenManager | 页内 | **[GAP: 未逐行核实，细化项]** | 文件存在（602/661/159/363 行） |

## 3. Deviation Records

- 无「契约说 X 代码做 Y」类偏差（特征化模式：契约即按代码行为书写）。
- 待裁决项（非偏差，设计悬而未决）：spec INFERRED-1..8（未登记放行、预填凭据、默认角色保护、默认全选、人员默认角色、「所属页面」不落库、微信形态、Header 结构）。
- 已知风险连线：前端判权可绕过 × 后端粗粒度键（根仓库 iam spec 观测-1）——本链仅记录，修复属后端/联合决策。

## 4. Test Evidence

- **tdd 阶段 blocked**：项目无测试基建（无 vitest/test script）。引入 vitest（+ @vue/test-utils、happy-dom）属新增依赖决策，待用户批准后按 /idd-tdd 流程补：优先切片 = 守卫判权矩阵（TASK-005/012）、权限树回显三规则（TASK-007）、user store 401 清理（TASK-001）。契约状态矩阵已具备直接生成 fixtures 的条件（contract §2）。
- 手工验收锚点 = spec §8 Acceptance 10 项（浏览器可核对）。

## 5. Implementation Trace

```
TASK-001/013 → PAGE-001 / 横切 → API-AUTH-001 + request 解包 → stores/user.ts, request.ts
TASK-005/012 → 守卫 + Sidebar → MENU_PERMISSION_MAP → router/index.ts:69-104, Sidebar.vue:213-257
TASK-006/007/008/010 → PAGE-005/006/008/009 → 角色权限保存/回显映射 → roles.vue, role-permissions.vue, personnel.vue, permissions/index.vue
状态所有权 → design §3 → stores/user.ts（唯一 token 清理点）
```
