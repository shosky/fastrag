# 模块一：IAM 认证与账号权限

> 对应前端：`src/views/login/`、`src/views/admin/account/*`、`src/views/admin/permissions/`
> 数据契约：`src/mock/auth-roles.ts`、`src/mock/auth-acl.ts`、`src/mock/org.ts`、`src/stores/user.ts`、`src/types/auth.ts`

---

## 一、业务概述

支撑全系统的身份认证与授权，包含两层权限：

1. **系统级 RBAC**：系统角色（super_admin / kb_admin / kb_user / readonly）→ 权限点（35+，如 `kb:create`、`admin:system`、`review:approve`）。
2. **知识库级 ACL**：每个 KB 独立的 owner / editor / viewer 授权。

| 子域 | 能力 |
|------|------|
| 认证 | 账号密码登录、签发 JWT、获取当前用户信息、登出 |
| 系统角色 | 角色 CRUD、角色↔权限分配、默认角色、权限点字典 |
| 人员账号 | 人员 CRUD、分配角色、按用户名查询（登录用） |
| 组织 | 组织树（多级）、增删改、按部门查成员 |
| 团队 | 团队管理 |
| KB ACL | KB 级授权条目增删改查、查询用户可访问的 KB、解析用户在某 KB 的角色 |

---

## 二、数据模型（Entity）

### 2.1 `sys_user` 人员账号（对齐 `PersonnelRecord`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| username | varchar(64) UQ | 登录账号 |
| real_name | varchar(64) | 真实姓名 |
| phone | varchar(20) | |
| email | varchar(128) | |
| password_hash | varchar(128) | BCrypt 加密 |
| role_id | varchar(32) | 主角色 ID |
| status | enum('enabled','disabled') | |
| org_id | varchar(32) | 所属部门 |
| created_at | datetime | |

### 2.2 `sys_role` 系统角色（对齐 `RoleMeta`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| role_key | varchar(32) UQ | super_admin/kb_admin/kb_user/readonly/custom_N |
| name | varchar(64) | 显示名 |
| description | varchar(255) | |
| is_default | tinyint | 是否默认角色（全局唯一） |
| is_system | tinyint | 是否系统内置（不可删） |
| created_at / updated_at | datetime | |

### 2.3 `sys_role_permission` 角色-权限关联

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| role_id | varchar(32) | |
| permission_key | varchar(64) | 如 `kb:create`、`*` |

### 2.4 `sys_permission` 权限点字典（对齐 `PERMISSIONS` / `PERMISSION_TREE`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| perm_key | varchar(64) UQ | |
| name | varchar(64) | |
| type | enum('menu','action') | |
| group | varchar(32) | menu/kb/app/workflow/review/admin |
| parent_key | varchar(64) | 构成权限树 |
| description | varchar(255) | |

### 2.5 `sys_org` 组织（对齐 `OrgNode`，邻接表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| name | varchar(64) | |
| alias | varchar(64) | |
| parent_id | varchar(32) | |
| level | int | |
| sort | int | |

### 2.6 `sys_team` / `sys_team_member` 团队

团队表 + 团队成员关联表（team_id, user_id）。

### 2.7 `kb_acl` 知识库授权（对齐 `KBAclEntry`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint PK | |
| kb_id | varchar(32) | |
| user_id | varchar(32) | 支持特殊值 `*`（公开） |
| kb_role | enum('owner','editor','viewer') | |
| granted_by | varchar(32) | |
| granted_at | datetime | |

---

## 三、接口设计（API）

> Base path：`/api`。所有接口统一返回 `ApiResponse<T>`。

### 3.1 认证

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/auth/login` | 登录，返回 token + userInfo | 公开 |
| GET | `/auth/userinfo` | 获取当前用户（含 roles/permissions） | 已认证 |
| POST | `/auth/logout` | 登出（吊销 token） | 已认证 |

**POST `/auth/login`**
- 请求：`{ username, password }`（对齐 `LoginParams`）
- 响应：`{ token, userInfo: { id, username, realName, avatar, phone, email, roles[], permissions[] } }`（对齐 `LoginResult` / `UserInfo`）
- 逻辑：校验密码（BCrypt）→ 查角色 → 聚合 permissions（含 `*` 通配）→ 签发 JWT → 返回。未注册账号默认 `readonly`（对齐前端 store.login）。

### 3.2 角色管理

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/roles` | 角色列表（含 permissions） | `admin:role` |
| GET | `/roles/{id}` | 角色详情 | `admin:role` |
| POST | `/roles` | 创建角色（role_key = custom_N） | `admin:role` |
| PUT | `/roles/{id}` | 更新角色（name/description/permissions） | `admin:role` |
| DELETE | `/roles/{id}` | 删除角色（系统内置不可删） | `admin:role` |
| POST | `/roles/{id}/set-default` | 设为默认角色（全局唯一） | `admin:role` |

### 3.3 权限点

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/permissions` | 权限点列表（含 type/group） | `admin:role` |
| GET | `/permissions/tree` | 权限树（对齐 `PERMISSION_TREE`） | `admin:role` |
| GET | `/permissions/menu-map` | 菜单路径→所需权限映射（对齐 `MENU_PERMISSION_MAP`） | `admin:role` |

### 3.4 人员账号

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/personnel` | 人员列表（分页/keyword） | `admin:role` |
| POST | `/personnel` | 新增人员 | `admin:role` |
| PUT | `/personnel/{id}` | 更新人员 | `admin:role` |
| POST | `/personnel/{id}/assign-role` | 分配角色 `{ roleId }` | `admin:role` |
| GET | `/personnel/by-username/{username}` | 按用户名查询（登录内部用） | 内部 |

### 3.5 组织 / 团队

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/org/tree` | 组织树（含 memberCount） | `admin:role` |
| GET | `/org/flat` | 扁平部门列表 `{ id, name, path, level }` | `admin:role` |
| GET | `/org/departments` | 叶子部门名列表 | `admin:role` |
| GET | `/org/{deptId}/members` | 部门下成员（子树） | `admin:role` |
| POST/PUT/DELETE | `/org/{id}` | 组织增删改（有子节点不可删） | `admin:role` |
| GET/POST/PUT/DELETE | `/teams` | 团队 CRUD | `admin:role` |

### 3.6 知识库 ACL

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/kb/{kbId}/acl` | KB 授权列表 | KB owner |
| PUT | `/kb/{kbId}/acl` | 整体替换 ACL `{ entries[] }` | KB owner |
| POST | `/kb/{kbId}/acl` | 新增单条授权 | KB owner |
| DELETE | `/kb/{kbId}/acl/{userId}` | 删除授权 | KB owner |
| GET | `/acl/users/{userId}/kbs` | 用户可访问的 KB id 列表 | 已认证 |
| GET | `/acl/users/{userId}/kbs/{kbId}/role` | 用户在某 KB 的角色 | 已认证 |

---

## 四、核心逻辑设计

### 4.1 登录与 Token 签发

```java
@Service
public class AuthServiceImpl implements AuthService {
    public LoginResult login(LoginParams params) {
        SysUser user = userMapper.findByUsername(params.getUsername());
        if (user == null || !BCrypt.checkpw(params.getPassword(), user.getPasswordHash())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == DISABLED) throw new BizException("账号已禁用");
        SysRole role = roleMapper.selectById(user.getRoleId());
        List<String> permissions = collectPermissions(role);  // 含 * 通配
        String token = jwtUtil.sign(user.getId(), role.getRoleKey());
        // 记录登录设备/时间（供审计模块）
        loginEventPublisher.publish(new LoginEvent(user, deviceInfo));
        return new LoginResult(token, toUserInfo(user, role, permissions));
    }
}
```

- Token 采用无状态 JWT（Redis 维护黑名单用于登出/吊销）。
- `permissions` 聚合逻辑：若角色含 `*` → 直接返回 `["*"]`（对齐前端 super_admin 通配）。

### 4.2 鉴权拦截（Spring Security + 自定义 Filter）

```java
// 1. JwtAuthFilter 解析 token → 注入 SecurityContext(user, roles, perms)
// 2. @PreAuthorize("@perm.has('kb:create')") 方法级权限
// 3. KB 级：@KbAuth(role="owner") 注解，AOP 校验当前用户在 pathVariable kbId 的 ACL 角色
```

权限校验工具（对齐前端 `hasPermission` / `hasKBPermission`）：
```java
@Component("perm")
public class PermissionChecker {
    public boolean has(String permKey) {
        UserContext ctx = SecurityUtil.current();
        return ctx.getPermissions().contains("*") || ctx.getPermissions().contains(permKey);
    }
    public boolean hasKbRole(String kbId, String... requiredRoles) {
        String role = aclService.resolveKBRole(SecurityUtil.currentUserId(), kbId);
        return Arrays.asList(requiredRoles).contains(role);
    }
}
```

### 4.3 KB 角色解析

对齐 `auth-acl.ts#getKBRole`：先精确匹配 user_id，命中返回；否则匹配 `user_id='*'`（公开 viewer）。

### 4.4 组织树构建

递归 CTE 或一次全量查询 + 内存组装（对齐 `org.ts#getOrgTree` 一次性返回完整树并计算 memberCount）。删除节点校验：有子组织禁止删除（对齐 `deleteOrg`）。

---

## 五、前端覆盖核对表

| 前端 mock 函数 | 后端接口 |
|----------------|----------|
| `store.login` | `POST /auth/login` |
| `getRoles/getRole/createRole/updateRole/deleteRole/setDefaultRole` | `/roles*` |
| `getPersonnel/createPersonnel/updatePersonnel/assignRole/findByUsername` | `/personnel*` |
| `getOrgTree/getFlatOrgList/getDepartmentNames/getDepartmentMembers/createOrg/updateOrg/deleteOrg` | `/org*` |
| `getKBAcl/getKBRole/getAccessibleKBIds/setKBAcl/addAclEntry/removeAclEntry` | `/kb/{kbId}/acl*`、`/acl/*` |
| `PERMISSIONS / PERMISSION_TREE / MENU_PERMISSION_MAP` | `/permissions*` |
