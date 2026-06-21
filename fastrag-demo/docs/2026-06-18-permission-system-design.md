# 权限管理系统设计方案

## 一、现状分析

### 1.1 已有能力

| 能力 | 实现位置 | 状态 |
|---|---|---|
| 系统角色枚举 | `types/auth.ts` — `SYSTEM_ROLES` | ✅ 4 个固定角色 |
| 权限常量 | `types/auth.ts` — `PERMISSIONS` | ✅ 18 个权限点 |
| 角色→权限静态映射 | `types/auth.ts` — `ROLE_PERMISSIONS` | ✅ 硬编码 |
| 权限树（用于角色编辑） | `types/auth.ts` — `PERMISSION_TREE` | ✅ 3 层树 |
| 角色 CRUD | `mock/auth-roles.ts` | ✅ |
| 知识库 ACL | `mock/auth-acl.ts` | ✅ owner/editor/viewer |
| 权限判断 composable | `composables/useAuth.ts` | ✅ hasRole/hasPermission/hasKBPermission |
| 元素级权限指令 | `directives/v-permission.ts` | ⚠️ 仅 1 处使用 |
| 路由守卫 | `router/index.ts` — `meta.roles` | ✅ |
| 侧边栏过滤 | `Sidebar.vue` — `requirePerm` | ✅ |

### 1.2 缺失能力

| 缺失项 | 影响 | 优先级 |
|---|---|---|
| **权限管理独立页面** | 权限只能在角色编辑弹窗里看，无全局视图 | P0 |
| **菜单权限体系** | 侧边栏 `requirePerm` 是硬编码字符串，无统一管理 | P0 |
| **接口权限模拟** | mock API 层不检查权限，任何人可调任何接口 | P1 |
| **按钮权限覆盖不全** | `v-permission` 仅 1 处使用，大量按钮无权限守卫 | P1 |
| **权限分组管理** | 权限树是静态常量，无法通过 UI 增删改 | P2 |
| **角色-权限矩阵视图** | 无法一目了然看到各角色拥有的权限 | P2 |
| **数据权限** | 除知识库 ACL 外，无其他资源的数据权限 | P3 |

---

## 二、设计方案

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────┐
│                    权限管理系统                        │
├─────────────┬─────────────┬─────────────┬───────────┤
│  菜单权限    │  按钮权限    │  接口权限    │  数据权限  │
│ (Menu Perm) │ (UI Perm)   │ (API Perm)  │(Data Perm)│
├─────────────┼─────────────┼─────────────┼───────────┤
│ Sidebar.vue │ v-permission│ mock API    │ KB ACL    │
│ 路由守卫     │ v-if/v-show│ 拦截器检查   │ 行级过滤   │
└─────────────┴─────────────┴─────────────┴───────────┘
                    │
            ┌───────┴───────┐
            │  权限管理页面   │
            │ (admin/perm)  │
            ├───────────────┤
            │ • 权限列表     │
            │ • 角色-权限矩阵│
            │ • 菜单-权限映射│
            └───────────────┘
```

### 2.2 权限模型设计

#### 2.2.1 权限分三层

```
权限(Permission)
  ├── 菜单权限 (menu:*)     → 控制侧边栏/路由可见性
  ├── 操作权限 (action:*)   → 控制按钮/功能可用性
  └── 数据权限 (data:*)     → 控制数据访问范围（预留）
```

#### 2.2.2 扩展权限常量

```typescript
// src/types/auth.ts — 扩展 PERMISSIONS

export const PERMISSIONS = {
  // ===== 菜单权限 =====
  MENU_HOME:           'menu:home',
  MENU_KNOWLEDGE:      'menu:knowledge',
  MENU_APPLICATION:    'menu:application',
  MENU_WORKFLOW:       'menu:workflow',
  MENU_ADMIN:          'menu:admin',
  MENU_ADMIN_SYSTEM:   'menu:admin:system',
  MENU_ADMIN_ACCOUNT:  'menu:admin:account',
  MENU_ADMIN_AUDIT:    'menu:admin:audit',
  MENU_ADMIN_CONTENT:  'menu:admin:content',
  MENU_ADMIN_PLATFORM: 'menu:admin:platform',

  // ===== 操作权限（已有，保持不变） =====
  KB_CREATE:           'kb:create',
  KB_EDIT:             'kb:edit',
  KB_DELETE:           'kb:delete',
  KB_UPLOAD:           'kb:upload',
  KB_MANAGE_CHUNKS:    'kb:manage_chunks',
  KB_MANAGE_GRAPH:     'kb:manage_graph',
  KB_MANAGE_EVAL:      'kb:manage_eval',
  KB_MANAGE_STRATEGY:  'kb:manage_strategy',
  KB_VIEW:             'kb:view',
  KB_SEARCH:           'kb:search',
  KB_ACL_MANAGE:       'kb:acl_manage',
  ADMIN_ACCESS:        'admin:access',
  ADMIN_USER:          'admin:user',
  ADMIN_ROLE:          'admin:role',
  ADMIN_ORG:           'admin:org',
  ADMIN_SYSTEM:        'admin:system',
  ADMIN_AUDIT:         'admin:audit',
  APP_CREATE:          'app:create',
  APP_EDIT:            'app:edit',
  APP_USE:             'app:use',

  // ===== 新增操作权限 =====
  WORKFLOW_CREATE:     'workflow:create',
  WORKFLOW_EDIT:       'workflow:edit',
  WORKFLOW_DELETE:     'workflow:delete',
  WORKFLOW_PUBLISH:    'workflow:publish',
  REVIEW_APPROVE:      'review:approve',
  REVIEW_REJECT:       'review:reject',
  QA_MANAGE:           'qa:manage',
  TEST_CASE_MANAGE:    'testcase:manage',
} as const
```

#### 2.2.3 菜单-权限映射表

```typescript
// src/types/auth.ts — 新增

/** 菜单项定义（与 Sidebar.vue 的 navModules 对齐） */
export interface MenuPermission {
  /** 菜单路径（与路由 path 对应） */
  path: string
  /** 菜单标题 */
  title: string
  /** 访问所需权限（任一满足即可） */
  requiredPerms: Permission[]
  /** 子菜单 */
  children?: MenuPermission[]
}

/**
 * 菜单权限映射表。
 * 声明每个菜单项需要哪些权限才能看到。
 * Sidebar.vue 的过滤逻辑从此表读取，取代硬编码 requirePerm。
 */
export const MENU_PERMISSION_MAP: MenuPermission[] = [
  {
    path: '/home',
    title: '首页',
    requiredPerms: ['menu:home'],
  },
  {
    path: '/knowledge',
    title: '知识库',
    requiredPerms: ['menu:knowledge'],
  },
  {
    path: '/application',
    title: '应用',
    requiredPerms: ['menu:application'],
    children: [
      { path: '/application', title: '应用中心', requiredPerms: ['app:use'] },
      { path: '/application/my-tools', title: '我的工具', requiredPerms: ['app:use'] },
      { path: '/application/workflow', title: '业务流', requiredPerms: ['workflow:edit'] },
      { path: '/application/test-cases', title: '测试案例', requiredPerms: ['testcase:manage'] },
      { path: '/operation/kb-analytics', title: '运营中心', requiredPerms: ['admin:access'] },
    ],
  },
  {
    path: '/admin',
    title: '管理',
    requiredPerms: ['menu:admin'],
    children: [
      { path: '/admin/index', title: '管理中心概览', requiredPerms: ['admin:access'] },
      {
        path: '/admin/system',
        title: '系统管理',
        requiredPerms: ['menu:admin:system'],
        children: [
          { path: '/admin/system/general-settings', title: '通用设置', requiredPerms: ['admin:system'] },
          { path: '/admin/system/kb-config', title: '知识库配置', requiredPerms: ['admin:system'] },
          { path: '/admin/system/terminology', title: '术语管理', requiredPerms: ['admin:system'] },
          { path: '/admin/system/query-rules', title: '查询规则', requiredPerms: ['admin:system'] },
        ],
      },
      {
        path: '/admin/account',
        title: '账号权限',
        requiredPerms: ['menu:admin:account'],
        children: [
          { path: '/admin/account/roles', title: '角色管理', requiredPerms: ['admin:role'] },
          { path: '/admin/account/organization', title: '组织管理', requiredPerms: ['admin:org'] },
          { path: '/admin/account/personnel', title: '人员管理', requiredPerms: ['admin:user'] },
          { path: '/admin/permissions', title: '权限管理', requiredPerms: ['admin:role'] },
        ],
      },
      {
        path: '/admin/audit',
        title: '安全审计',
        requiredPerms: ['menu:admin:audit'],
        children: [
          { path: '/admin/audit/system-log', title: '系统日志', requiredPerms: ['admin:audit'] },
          { path: '/admin/audit/review-center', title: '审核中心', requiredPerms: ['review:approve'] },
        ],
      },
    ],
  },
]
```

### 2.3 权限管理页面设计

#### 2.3.1 页面结构

新增路由：`/admin/permissions`，位于"账号权限"分组下。

页面包含 **3 个 Tab**：

**Tab 1：权限列表**
- 表格展示所有权限点（权限标识 / 名称 / 类型 / 关联角色数）
- 支持按类型（菜单/操作）筛选
- 支持搜索权限名称
- 点击权限行 → 展开详情（哪些角色拥有此权限）

**Tab 2：角色-权限矩阵**
- 行 = 所有角色，列 = 权限分组
- 单元格 = checkbox，直接勾选/取消
- 支持批量操作（全选某列/全选某行）
- 修改后实时保存

**Tab 3：菜单-权限映射**
- 树形展示菜单结构（与侧边栏一致）
- 每个菜单节点旁显示所需权限标签
- 可编辑：为菜单节点分配所需权限
- 修改后侧边栏过滤立即生效

#### 2.3.2 数据模型

```typescript
// src/types/auth.ts — 新增

/** 权限详情（用于权限管理页展示） */
export interface PermissionDetail {
  /** 权限标识（如 'kb:create'） */
  key: string
  /** 权限名称（如 '创建知识库'） */
  name: string
  /** 权限类型 */
  type: 'menu' | 'action'
  /** 所属分组（如 '知识库'、'管理中心'） */
  group: string
  /** 权限描述 */
  description?: string
  /** 拥有此权限的角色 ID 列表 */
  roleIds: string[]
}
```

### 2.4 接口权限模拟

在 `src/mock/interceptor.ts` 中增加权限检查钩子：

```typescript
// src/mock/interceptor.ts — 新增权限检查

import { useUserStore } from '@/stores/user'

/**
 * 接口权限检查装饰器。
 * 在 mock API 函数中调用，模拟后端接口鉴权。
 *
 * 用法：
 *   export async function deleteKnowledgeBase(kbId: string) {
 *     checkApiPermission('kb:delete')
 *     // ... 实际逻辑
 *   }
 */
export function checkApiPermission(requiredPerm: Permission): void {
  const userStore = useUserStore()
  const perms = userStore.permissions || []

  if (perms.includes('*')) return // 超管放行

  if (!perms.includes(requiredPerm)) {
    throw new Error(`权限不足：需要 ${requiredPerm}`)
  }
}
```

### 2.5 按钮权限补全

对现有页面中缺失权限守卫的关键按钮补充 `v-permission`：

| 页面 | 按钮 | 需要的权限 |
|---|---|---|
| `knowledge/index.vue` | 创建知识库 | `kb:create` ✅ 已有 |
| `knowledge/form.vue` | 删除知识库 | `kb:delete` |
| `knowledge/detail/index.vue` | 编辑按钮 | `kb:edit` |
| `knowledge/detail/components/FileManager.vue` | 上传按钮 | `kb:upload` ✅ 已有 |
| `workflow/index.vue` | 创建业务流 | `workflow:create` |
| `workflow/editor.vue` | 发布按钮 | `workflow:publish` |
| `admin/audit/review-center.vue` | 通过/驳回按钮 | `review:approve` / `review:reject` |
| `application/test-cases.vue` | 运行测试 | `testcase:manage` |

---

## 三、实施计划

### 阶段 1：权限管理页面（P0）

**文件变更：**

| 操作 | 文件 | 说明 |
|---|---|---|
| 修改 | `src/types/auth.ts` | 新增 PERMISSIONS 扩展、MenuPermission 接口、MENU_PERMISSION_MAP、PermissionDetail 接口 |
| 修改 | `src/mock/auth-roles.ts` | 新增 getPermissionDetails()、updateMenuPermissionMap() |
| 新建 | `src/views/admin/permissions/index.vue` | 权限管理主页面（3 Tab） |
| 修改 | `src/router/routes.ts` | 注册 `/admin/permissions` 路由 |
| 修改 | `src/components/layout/Sidebar.vue` | 将 navModules 的 requirePerm 改为从 MENU_PERMISSION_MAP 读取 |

### 阶段 2：菜单权限体系（P0）

**文件变更：**

| 操作 | 文件 | 说明 |
|---|---|---|
| 修改 | `src/components/layout/Sidebar.vue` | 重构 filterMenuItems，从 MENU_PERMISSION_MAP 查找每个菜单的 requiredPerms |
| 修改 | `src/router/index.ts` | 路由守卫增加菜单权限检查（从 MENU_PERMISSION_MAP 查路由对应的权限） |
| 修改 | `src/types/auth.ts` | ROLE_PERMISSIONS 增加菜单权限条目 |

### 阶段 3：按钮权限补全（P1）

**文件变更：**

| 操作 | 文件 | 说明 |
|---|---|---|
| 修改 | `src/views/knowledge/form.vue` | 删除按钮加 `v-permission="'kb:delete'"` |
| 修改 | `src/views/knowledge/detail/index.vue` | 编辑按钮加 `v-permission="'kb:edit'"` |
| 修改 | `src/views/workflow/index.vue` | 创建按钮加 `v-permission="'workflow:create'"` |
| 修改 | `src/views/workflow/editor.vue` | 发布按钮加 `v-permission="'workflow:publish'"` |
| 修改 | `src/views/admin/audit/review-center.vue` | 通过/驳回加 `v-permission` |

### 阶段 4：接口权限模拟（P1）

**文件变更：**

| 操作 | 文件 | 说明 |
|---|---|---|
| 修改 | `src/mock/interceptor.ts` | 新增 checkApiPermission() 函数 |
| 修改 | `src/mock/knowledge-bases.ts` | createKB/updateKB/deleteKB 加权限检查 |
| 修改 | `src/mock/workflow.ts` | createWorkflow/deleteWorkflow/publishWorkflow 加权限检查 |
| 修改 | `src/mock/audit.ts` | approveReview/rejectReview 加权限检查 |

---

## 四、权限矩阵（默认角色配置）

| 权限分组 | 权限点 | super_admin | kb_admin | kb_user | readonly |
|---|---|---|---|---|---|
| **菜单** | menu:home | ✅ | ✅ | ✅ | ✅ |
| | menu:knowledge | ✅ | ✅ | ✅ | ✅ |
| | menu:application | ✅ | ✅ | ✅ | ✅ |
| | menu:workflow | ✅ | ✅ | ❌ | ❌ |
| | menu:admin | ✅ | ✅ | ❌ | ❌ |
| | menu:admin:system | ✅ | ✅ | ❌ | ❌ |
| | menu:admin:account | ✅ | ✅ | ❌ | ❌ |
| | menu:admin:audit | ✅ | ❌ | ❌ | ❌ |
| **知识库** | kb:view | ✅ | ✅ | ✅ | ✅ |
| | kb:search | ✅ | ✅ | ✅ | ✅ |
| | kb:create | ✅ | ✅ | ✅ | ❌ |
| | kb:edit | ✅ | ✅ | ❌ | ❌ |
| | kb:delete | ✅ | ✅ | ❌ | ❌ |
| | kb:upload | ✅ | ✅ | ✅ | ❌ |
| | kb:acl_manage | ✅ | ✅ | ❌ | ❌ |
| **应用** | app:create | ✅ | ✅ | ✅ | ❌ |
| | app:edit | ✅ | ✅ | ✅ | ❌ |
| | app:use | ✅ | ✅ | ✅ | ✅ |
| **业务流** | workflow:create | ✅ | ✅ | ❌ | ❌ |
| | workflow:edit | ✅ | ✅ | ❌ | ❌ |
| | workflow:delete | ✅ | ✅ | ❌ | ❌ |
| | workflow:publish | ✅ | ✅ | ❌ | ❌ |
| **审核** | review:approve | ✅ | ❌ | ❌ | ❌ |
| | review:reject | ✅ | ❌ | ❌ | ❌ |
| **管理** | admin:access | ✅ | ✅ | ❌ | ❌ |
| | admin:user | ✅ | ✅ | ❌ | ❌ |
| | admin:role | ✅ | ✅ | ❌ | ❌ |
| | admin:org | ✅ | ✅ | ❌ | ❌ |
| | admin:system | ✅ | ✅ | ❌ | ❌ |
| | admin:audit | ✅ | ❌ | ❌ | ❌ |

---

## 五、核心交互流程

### 5.1 权限管理页面操作流程

```
管理员进入 /admin/permissions
  ├── Tab 1: 权限列表
  │   ├── 查看所有权限点（搜索/筛选）
  │   └── 点击权限行 → 右侧抽屉显示"哪些角色拥有此权限"
  │
  ├── Tab 2: 角色-权限矩阵
  │   ├── 勾选/取消 checkbox → 实时保存到 mock
  │   └── 超管角色行置灰不可编辑
  │
  └── Tab 3: 菜单-权限映射
      ├── 树形展示菜单结构
      ├── 点击菜单节点 → 右侧显示/编辑所需权限
      └── 保存后侧边栏立即生效
```

### 5.2 菜单权限过滤流程

```
Sidebar.vue 渲染时：
  1. 读取 MENU_PERMISSION_MAP
  2. 对每个菜单项：
     a. 从映射表查找 requiredPerms
     b. 调用 hasPermission(perm) 检查
     c. 无权限 → 过滤掉该菜单项
  3. 渲染过滤后的菜单树
```

### 5.3 路由守卫权限检查流程

```
router.beforeEach(to):
  1. 检查 meta.requiresAuth
  2. 检查 meta.roles（已有）
  3. 新增：从 MENU_PERMISSION_MAP 查找路由对应的 requiredPerms
  4. 调用 hasPermission() 检查
  5. 无权限 → 重定向到 /403
```
