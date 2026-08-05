# fastrag-iam -- 身份认证与权限管理模块

提供用户注册/登录、角色/权限管理、组织架构、知识库 ACL 等核心 IAM 能力。

## 模块职责

- 用户名/密码登录，JWT Token 签发与黑名单登出
- 邮箱验证码注册与重置密码
- 微信小程序扫码登录（scene 生成、轮询、确认）
- 角色 CRUD 与角色-权限绑定
- 权限点 CRUD 与分组树形展示
- 人员管理（创建、编辑、启禁用、角色分配、分页查询）
- 组织架构树管理（创建、编辑、删除、成员列表）
- 知识库级别 ACL（owner/editor/viewer），带 Redis 缓存
- 登录/登出/注册操作日志记录（委托 operation 模块）

## 对外 REST 端点

### 认证 `/api/auth`

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/auth/login` | 用户名密码登录 |
| GET  | `/api/auth/userinfo` | 获取当前登录用户信息 |
| POST | `/api/auth/logout` | 登出（Token 加入黑名单） |
| POST | `/api/auth/send-code` | 发送邮箱验证码 |
| POST | `/api/auth/register` | 邮箱验证码注册 |
| POST | `/api/auth/reset-password` | 通过验证码重置密码 |
| GET  | `/api/auth/wechat/qr-scene` | 生成微信扫码登录场景值 |
| GET  | `/api/auth/wechat/qr-status?scene=` | 轮询扫码状态 |
| POST | `/api/auth/wechat/login` | 微信小程序登录 |
| POST | `/api/auth/wechat/qr-confirm` | 小程序扫码确认登录 |

### 人员管理 `/api/personnel`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET    | `/api/personnel` | 分页查询人员列表 |
| POST   | `/api/personnel` | 创建人员 |
| PUT    | `/api/personnel/{id}` | 更新人员信息 |
| POST   | `/api/personnel/{id}/assign-roles` | 分配角色 |
| PUT    | `/api/personnel/{id}/status` | 启用/禁用账号 |
| GET    | `/api/personnel/by-username/{username}` | 按用户名查询 |
| GET    | `/api/personnel/simple` | 简要列表（id + name） |

### 角色管理 `/api/roles`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET  | `/api/roles` | 查询所有角色 |
| GET  | `/api/roles/{id}` | 查询角色详情 |
| POST | `/api/roles` | 创建角色 |
| PUT  | `/api/roles/{id}` | 更新角色 |
| DELETE | `/api/roles/{id}` | 删除角色（系统角色不可删） |
| POST | `/api/roles/{id}/set-default` | 设为默认角色 |

### 权限管理 `/api/permissions`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET    | `/api/permissions` | 查询所有权限 |
| GET    | `/api/permissions/tree` | 权限分组树 |
| POST   | `/api/permissions` | 创建权限 |
| PUT    | `/api/permissions/{id}` | 更新权限 |
| DELETE | `/api/permissions/{id}` | 删除权限 |

### 组织架构 `/api/org`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET    | `/api/org/tree` | 组织树 |
| GET    | `/api/org/flat` | 扁平化组织列表 |
| GET    | `/api/org/departments` | 所有部门名称 |
| GET    | `/api/org/{id}/members` | 部门成员 |
| POST   | `/api/org` | 创建组织节点 |
| PUT    | `/api/org/{id}` | 更新组织 |
| DELETE | `/api/org/{id}` | 删除组织（有子组织时禁止） |

### 知识库 ACL `/api/kb`

| 方法 | 路径 | 描述 |
|------|------|------|
| GET    | `/api/kb/{kbId}/acl` | 查询知识库 ACL 列表 |
| PUT    | `/api/kb/{kbId}/acl` | 批量设置 ACL |
| POST   | `/api/kb/{kbId}/acl` | 新增 ACL 条目 |
| DELETE | `/api/kb/{kbId}/acl/{userId}` | 移除 ACL 条目 |
| GET    | `/api/acl/users/{userId}/kbs` | 用户可访问的知识库列表 |
| GET    | `/api/acl/users/{userId}/kbs/{kbId}/role` | 用户在知识库中的角色 |

## 模块依赖

| 方向 | 模块 | 说明 |
|------|------|------|
| 依赖 | fastrag-common | 通用响应、枚举、注解、异常、工具类 |
| 依赖 | fastrag-security | JWT 工具、SecurityContext 工具 |
| 依赖 | fastrag-infra | 邮件发送服务（EmailService） |
| 依赖 | fastrag-operation | 登录日志记录（SysLoginLogService） |
| 被依赖 | fastrag-bootstrap | 仅被 bootstrap 模块引入 |

## 关键类说明

### Controller

| 类名 | 路径前缀 | 职责 |
|------|----------|------|
| AuthController | `/api/auth` | 认证：登录/登出/注册/重置密码/微信登录 |
| PersonnelController | `/api/personnel` | 人员 CRUD、角色分配、状态管理 |
| RoleController | `/api/roles` | 角色 CRUD、默认角色设置 |
| PermissionController | `/api/permissions` | 权限点 CRUD、树形展示 |
| OrgController | `/api/org` | 组织架构树管理 |
| KbAclController | `/api` | 知识库 ACL 管理 |

### Service

| 类名 | 职责 |
|------|------|
| AuthService / AuthServiceImpl | 认证核心：登录、注册、重置密码、微信登录流程 |
| PersonnelService / PersonnelServiceImpl | 人员管理：CRUD、分页、角色分配 |
| RoleService / RoleServiceImpl | 角色管理：CRUD、权限绑定、默认角色 |
| PermissionService / PermissionServiceImpl | 权限管理：CRUD、分组树构建 |
| OrgService / OrgServiceImpl | 组织架构：树构建、CRUD、成员查询 |
| KbAclService / KbAclServiceImpl | 知识库 ACL：CRUD、Redis 缓存、可访问 KB 查询 |

### Entity

| 类名 | 表名 | 说明 |
|------|------|------|
| SysUser | sys_user | 系统用户 |
| SysRole | sys_role | 系统角色 |
| SysPermission | sys_permission | 权限点 |
| SysUserRole | sys_user_role | 用户-角色关联 |
| SysRolePermission | sys_role_permission | 角色-权限关联 |
| SysOrg | sys_org | 组织架构 |
| KbAcl | kb_acl | 知识库访问控制 |
| EmailVerification | email_verification | 邮箱验证码记录 |

### Mapper

| 类名 | 说明 |
|------|------|
| SysUserMapper | 用户表 MyBatis-Plus Mapper |
| SysRoleMapper | 角色表 Mapper |
| SysPermissionMapper | 权限表 Mapper |
| SysUserRoleMapper | 用户角色关联 Mapper |
| SysRolePermissionMapper | 角色权限关联 Mapper |
| SysOrgMapper | 组织表 Mapper |
| KbAclMapper | 知识库 ACL Mapper |
| EmailVerificationMapper | 邮箱验证码 Mapper |

### Config

| 类名 | 说明 |
|------|------|
| WechatMiniAppProperties | 微信小程序 appId/appSecret 配置（`wechat.miniapp.*`） |

### Model

| 类名 | 说明 |
|------|------|
| LoginRequest | 登录请求（username, password） |
| LoginResponse | 登录响应（token, UserInfoDto） |
| RegisterRequest | 注册请求（username, email, password, code） |
| ResetPasswordRequest | 重置密码请求（email, code, newPassword） |
| SendCodeRequest | 发送验证码请求（email, purpose） |
| WechatLoginRequest | 微信登录请求（code, nickName） |
| WechatQrScene | 扫码场景（scene, expiresIn, qrImageBase64） |
| WechatQrConfirmRequest | 扫码确认请求（scene, code, nickName） |
| PersonnelDto / PersonnelCreateRequest | 人员 DTO 与创建请求 |
| RoleDto / RoleCreateRequest | 角色 DTO 与创建请求 |
| PermissionDto / PermissionCreateRequest | 权限 DTO 与创建请求 |
| OrgNodeDto | 组织节点（含 children 递归结构） |
| KbAclDto | 知识库 ACL 条目 |

### Manager

| 类名 | 说明 |
|------|------|
| PermissionChecker | Spring Bean `perm`，用于 SpEL 表达式 `@perm.has('xxx')` 权限判断 |
