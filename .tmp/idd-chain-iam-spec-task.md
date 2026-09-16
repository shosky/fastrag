# IDD 链任务书 — 特性 #1 iam / 阶段 spec

## 任务

为本特性执行 IDD 链的第一阶段 **spec**（需求规格）。必须先加载 `/idd-spec` 技能：优先用 Skill 工具调用 `idd-spec`；若不可用，直接读 `C:\Users\63179\.zcode\skills\idd-spec\SKILL.md` 并严格遵循其文档格式、grilling 流程与验收条件。所有产出用**中文**。

## 输入（先读再动笔）

1. 本任务书（含证据包）
2. `D:\Workspace\java\github\rag\fastrag\CONTEXT.md` — 领域术语表 + IDD 元数据（Role: backend+frontend）
3. `D:\Workspace\java\github\rag\fastrag\docs\feature-map.md` — 特性 #1「IAM 认证与账号权限」小节 + 构建顺序
4. `D:\Workspace\java\github\rag\fastrag\docs\conventions.md` — 统一返回/异常/分页约定
5. 既有设计参考（legacy，只作证据不照抄）：`docs/legacy/design/modules/fastrag-iam.md`、`docs/legacy/design/modules/fastrag-security.md`

## 证据包 — iam

**后端（fastrag-modules/fastrag-iam，58 文件 2.6k LOC；fastrag-security，14 文件 0.9k LOC）：**

| 控制器 | base path | 端点 |
|---|---|---|
| AuthController | `/api/auth` | login、userinfo、logout、send-code、register、reset-password、wechat/qr-scene、wechat/qr-status、wechat/login、wechat/qr-confirm |
| ApiTokenController | `/api/api-tokens` | list、create、revoke DELETE /{tokenId} |
| KbAclController | `/api` | GET/PUT/POST `/kb/{kbId}/acl`、DELETE `/kb/{kbId}/acl/{userId}`、GET `/acl/users/{userId}/kbs`、GET `/acl/users/{userId}/kbs/{kbId}/role` |
| OrgController | `/api/org` | tree、flat、departments、{id}/members、CRUD |
| PermissionController | `/api/permissions` | list、tree、CRUD（全部 `@PreAuthorize("@perm.has('admin:role')")`） |
| PersonnelController | `/api/personnel` | 分页 list、create、update、assign-roles、status、by-username、simple |
| RoleController | `/api/roles` | list、get、CRUD、set-default |

- security 模块：JwtAuthFilter、ApiTokenAuthFilter、SecurityConfig、RedisConfig、KbAuthAspect + @KbAuth、KbAccessChecker、KbAclService、JwtUtil、SecurityUtil、OnlyOfficeJwtUtil、DataScope、LoginUser
- 相关表：sys_user、sys_role、sys_role_permission、sys_permission、sys_user_role、sys_org、sys_api_token、email_verification、login_security_config、kb_acl
- 统一返回 `ApiResponse{code,data,message}`（fastrag-common/response/ApiResponse.java）；分页 `PageResult{list,total,page,pageSize}`；异常 `BusinessException` + GlobalExceptionHandler（业务错误=HTTP 200 + body.code）
- 测试：**iam 与 security 模块无任何测试类**（这是现状，spec 如实反映，勿虚构测试需求为已实现）

**前端（fastrag-frontend）：**

- 页面：`views/login/index.vue`、`views/register/index.vue`、`views/forgot-password/index.vue`、`views/error/403.vue`；`views/admin/account/{organization,personnel,role-permissions,roles}.vue`、`views/admin/permissions/index.vue`
- 路由：/login、/register、/forgot-password、/403（requiresAuth:false）；/admin/account/*、/admin/permissions（守卫 `src/router/index.ts:69-104`：MENU_PERMISSION_MAP 权限驱动，meta.roles 白名单已移除）
- 状态：`stores/user.ts`（token/userInfo/roles/permissions，localStorage 持久化，微信扫码 setLoginData）
- 权限三层：路由守卫（`types/auth.ts` 692 行 MENU_PERMISSION_MAP）+ `directives/v-permission.ts`（无权限移除 DOM，super_admin/* 通配）+ Sidebar 菜单过滤（`components/layout/Sidebar.vue` 835 行）；权限键规范 `menu:*` / `api:xxx:yyy`（`config/menu-perm-tree.ts`）
- API 消费：`src/api/index.ts:38-68` /auth/*；/acl/users/*、/roles、/permissions、/personnel、/org/*、/api-tokens

**已知信号（写 spec 时如实纳入）：**
- 登录方式三种：密码+验证码（send-code/email_verification 表）、微信扫码（qr-scene 轮询 qr-status + qr-confirm）、API Token（机器调用）
- `.env` 中微信 AppID/AppSecret 明文入库（安全信号，spec 可列为风险/非功能需求）
- kb_acl 角色序 owner > editor > viewer（CONTEXT.md 术语，代码 KbAccessChecker 可验证）
- bootstrap 有 schema-h2.sql/data-h2.sql 支持 H2 单测，但 iam 未写测试

## Grilling 纪律（onboard 模式）

spec 阶段的 grilling 问题**只从代码证据回答**，并标注置信度：

- **CERTAIN**：代码直接可观测 → 自动回答，必须引 `file:line`
- **INFERRED**：结构强推断 → 自动回答但标注 `（INFERRED）`，汇总成清单供用户阶段末批量确认
- **GAP**：代码外信息（意图/SLA/被否决的备选）→ **绝不臆造**，在文档相应位置写内联占位 `[GAP: 问题 — 代码中无证据]` 并继续

## 产出与回报

1. spec 文档按 /idd-spec 技能自身的路径约定落盘（预期 `docs/spec/` 下，中文）
2. 最终回报必须包含：文档绝对路径、需求数、GWT 用例数、CERTAIN/INFERRED/GAP 各计数、**INFERRED 清单原文**（供批量确认）、**GAP 清单原文**、与 /idd-spec 验收条件的逐条对照
3. 不得修改任何源代码；不得改写 CONTEXT.md / feature-map.md（链状态由主控更新）
