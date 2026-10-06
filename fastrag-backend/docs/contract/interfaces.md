# 契约 — 模块接口（全后端共享工件）

> 本文件是 **fastrag-backend 全部特性共享**的接口契约工件，由各特性 IDD 链逐特性追加 `## <特性名>` 顶层分节；修改时只动本特性章节，不得破坏其他特性章节。
> 端点排除铁律：单个 HTTP 端点、简单 CRUD 包装、透传模块**不写契约**（它们是所属模块的实现细节）；契约只写给「删除测试通过」的深模块接口。HTTP 层暴露面见 design/iam.md 的 Interface Contracts 节。
> 统一返回 `ApiResponse{code,data,message}`，业务错误 = HTTP 200 + body.code；分页 `PageResult{list,total,page,pageSize}`。
> 证据标注：`path:line ‹CERTAIN›` / 〔INFERRED：…〕/ `[GAP: …]`。

---

## iam

> 对应 design 模块：AuthModule / SecurityFilterModule / ApiTokenModule / KbAclModule / KbAccessDecisionModule / RbacDataModule。数据实体契约见 data.md `## iam` 节。

### iam.AuthService（认证会话契约）

**Purpose:** 系统一切「证明身份 → 换取可携带会话 → 吊销会话」的唯一起点；三种登录通道在此折叠为同一种 JWT 会话产物，下游（过滤器链）只认会话不认通道。

**Input / Output（按方法）:**
- `login(username: string, 必填; password: string, 必填) → LoginResponse{token: string, userInfo{ id, username, realName, phone, email, orgId, roles: string[], permissions: string[] }}`〔证据：AuthServiceImpl.java:37-52 ‹CERTAIN›〕
- `getUserInfo(userId: string) → UserInfoDto`（角色/权限从库实时重算，非令牌快照）〔AuthServiceImpl.java:54-63 ‹CERTAIN›〕
- `logout(token: string) → void`（幂等；写 Redis 黑名单 TTL 24h）〔AuthServiceImpl.java:65-67 ‹CERTAIN›〕
- `sendCode(email: string, 必填, 格式邮箱; purpose: enum{register,reset}, 必填) → void`〔AuthServiceImpl.java:69-96 ‹CERTAIN›〕
- `register(email, username, password, code: string, 必填) → void`〔AuthServiceImpl.java:98-130 ‹CERTAIN›〕
- `resetPassword(email, newPassword, code: string, 必填) → void`〔AuthServiceImpl.java:132-143 ‹CERTAIN›〕
- `wechatLogin(code: string, 必填; nickName: string, 可选) → LoginResponse`；`generateQrScene() → {scene: 16位, expiresIn: 300, qrImageBase64: string|null}`；`pollQrLoginStatus(scene) → string|null`（null=waiting，"expired"=过期，非空串=token）；`confirmQrLogin(scene, LoginResponse) → void`〔AuthServiceImpl.java:199-299 ‹CERTAIN›〕

**Error modes:**
- E_INVALID_CREDENTIALS — 触发：用户不存在或密码不匹配（两分支同文案「用户名或密码错误」，防枚举）。恢复：调用方提示重试。〔AuthServiceImpl.java:39,41 ‹CERTAIN›〕
- E_ACCOUNT_DISABLED — 触发：status=disabled 的账号经密码（:40）或微信（:212-214）通道登录。恢复：提示「账号已被禁用」，仅管理员解禁。〔‹CERTAIN›〕
- E_CODE_RATE_LIMITED — 触发：同邮箱同用途 60 秒内重复请求验证码（Redis 频控键）。恢复：60 秒后重试。〔AuthServiceImpl.java:74-77 ‹CERTAIN›〕
- E_CODE_INVALID — 触发：验证码不存在/已过期（5 分钟）/已消费/用途不匹配（查询四条件过滤，含 purpose 精确匹配——注册码不能用于重置）。恢复：重新获取验证码。〔AuthServiceImpl.java:145-158 ‹CERTAIN›〕
- E_DUPLICATE_IDENTITY — 触发：注册时用户名或邮箱已存在（分别在验证码消费后检查）。恢复：换用户名/邮箱。〔AuthServiceImpl.java:105-111 ‹CERTAIN›〕
- E_EMAIL_NOT_REGISTERED — 触发：重置密码时邮箱无对应用户（验证码已消费）。恢复：先注册。〔AuthServiceImpl.java:137-139 ‹CERTAIN›〕
- E_WECHAT_FAILED — 触发：code2Session 失败或未返回 openid（code 无效/微信不可用）。恢复：重试。〔AuthServiceImpl.java:201-204,361-381 ‹CERTAIN›〕
- E_QR_EXPIRED — 触发：扫码确认时 scene 键已不存在（5 分钟 TTL 过）。恢复：PC 端重新生成 scene。〔AuthServiceImpl.java:291-296 ‹CERTAIN›〕

**Invariants:**
- Pre：password 以 BCrypt 哈希落库比对，任何通道不出现明文（SecurityConfig.java:85-88）。
- Post：登录/微信登录成功 ⇒ 返回的 JWT 载荷含 userId(sub)、username、orgId、roles、permissions 五要素，签名 HMAC、默认 24h 有效（JwtUtil.java:56-67）；super_admin 用户的 permissions 折叠为 `["*"]`（AuthServiceImpl.java:178-179）。
- Post：登出 ⇒ `jwt:blacklist:{token}` 键存在期间该令牌不再建立认证上下文（JwtAuthFilter.java:66-70）。
- Post：注册/微信自动注册成功 ⇒ 用户 enabled、配额 10GB（10737418240）、绑定 kb_user 角色（AuthServiceImpl.java:117-129,224-252）——默认角色当前硬编码 kb_user 不读 is_default（spec REQ-023，2026-09-24 已裁：按现状为准维持）。

**Parent Trace:** 接口 iam.AuthService → Design.AuthModule → REQ-001~013、REQ-004、REQ-006、REQ-032。

### iam.SecurityGate（请求鉴权基建契约）

**Purpose:** 全部 /api/** 端点的认证总闸与当前身份唯一读口；把「这个请求是谁」从传输细节（Bearer 头、令牌格式）中解耦成 `LoginUser`。

**Public interface（语义）:**
- 暴露面白名单：8 个认证端点 + OnlyOffice raw/callback + Swagger/Actuator 免认证；`/api/**` 其余一律要求认证；`/api` 外放行；无状态、禁 CSRF、ASYNC 分发豁免〔证据：SecurityConfig.java:60-83、JwtAuthFilter.java:53-58 ‹CERTAIN›〕。
- 过滤器顺序：ApiTokenAuthFilter → JwtAuthFilter，均在用户名密码过滤器之前〔SecurityConfig.java:80-81 ‹CERTAIN›〕。
- `SecurityUtil.getCurrentUser()/getCurrentUserId()`：无认证上下文抛 401「未登录」〔SecurityUtil.java:29-39 ‹CERTAIN›〕。
- `PermissionChecker.has(p)/hasAny(ps)`：`@PreAuthorize` SpEL 落点，通配 `*` 全通〔PermissionChecker.java:21-24、LoginUser.java:48-59 ‹CERTAIN›〕。

**Input:** HTTP `Authorization: Bearer <token>`；token 以 `frag_` 开头走 API Token 通道，否则按 JWT 解析。〔ApiTokenAuthFilter.java:49,70-72、JwtAuthFilter.java:63-65 ‹CERTAIN›〕
**Output:** SecurityContext 中的 `LoginUser{userId, username, orgId, roles, permissions}` + GrantedAuthorities（JWT 通道=permissions 逐条；API Token 通道=固定 `kb:manage`）。〔JwtAuthFilter.java:81-92、ApiTokenAuthFilter.java:77-88 ‹CERTAIN›〕

**Error modes:**
- E_NOT_AUTHENTICATED — 触发：白名单外无凭据/令牌无效/黑名单令牌访问受保护端点（过滤器跳过认证建立）。恢复：先登录。HTTP 403（2026-09-24 深证：未定制 EntryPoint 时 Spring Security 6.2 默认 Http403ForbiddenEntryPoint；SecurityConfig.java:60-83 无 exceptionHandling 配置、全仓 grep 无 EntryPoint 定制；见 spec REQ-021）〔JwtAuthFilter.java:66-70,71-93 ‹CERTAIN›〕
- E_METHOD_FORBIDDEN — 触发：`@PreAuthorize` 判定 false。恢复：HTTP 403 +「无权限执行该操作」（GlobalExceptionHandler.java:18-23）〔‹CERTAIN›〕

**Invariants:**
- Pre：进入任何受保护业务方法前，SecurityContext 的 Authentication 已由两过滤器之一建立，或为空（未认证）。
- Post：一次请求内权限判定全部基于令牌快照，不回库复查——权限变更需重新登录生效（spec §4.1 叙述的机制根源，AuthServiceImpl.java:43-46 + JwtAuthFilter.java:72-92）。
- 不变式：业务异常统一 HTTP 200 + body.code（BusinessException→400/401/403/404/500 语义；校验失败→400 含字段名；兜底→500「服务异常」）〔GlobalExceptionHandler.java:12-45 ‹CERTAIN›〕。

**Parent Trace:** 接口 iam.SecurityGate → Design.SecurityFilterModule → REQ-004、REQ-018~021、REQ-032。

### iam.ApiTokenService（平台令牌生命周期契约）

**Purpose:** 为第三方集成签发/吊销非人工凭证；把「程序化身份」收敛为与用户会话同构的认证产物（LoginUser），使下游零感知。

**Input:**
- `createToken(operatorId: string; request{name: string, 必填; permission: enum{read,write}, 可选缺省 read; expiresIn: long 秒, 可选——null 永不过期})`〔证据：ApiTokenServiceImpl.java:48-69 ‹CERTAIN›〕
- `revokeToken(tokenId: string, 必填)`；`findValidToken(tokenValue: string, 必填)`〔ApiTokenServiceImpl.java:71-91 ‹CERTAIN›〕

**Output:**
- `createToken → ApiTokenDto{id, name, token(明文, 仅此一次), permission, expiresAt, expired, createdBy, createdAt}`；`listTokens → ApiTokenDto[]`（控制器层统一置 token=null 脱敏，ApiTokenController.java:53-57）〔‹CERTAIN›〕
- `findValidToken → SysApiToken|null`（存在且 revoked=false 且未过期的记录）〔ApiTokenServiceImpl.java:83-91 ‹CERTAIN›〕

**Error modes:**
- E_TOKEN_NOT_FOUND — 触发：撤销不存在的 tokenId。恢复：无（资源不存在语义 404）。〔ApiTokenServiceImpl.java:74-77 ‹CERTAIN›〕
- E_TOKEN_INVALID — 触发：findValidToken 查无有效记录（已撤销/过期/不存在）→ 认证不建立。恢复：重新签发。〔ApiTokenAuthFilter.java:74-95 ‹CERTAIN›〕

**Invariants:**
- Pre：创建/撤销端点要求 `kb:manage` 权限；两写操作有 @Loggable 审计（ApiTokenController.java:52,61-77）。
- Post：令牌值格式恒为 `frag_<32位uuid>`；明文只在 createToken 响应出现一次，列表一律脱敏〔ApiTokenServiceImpl.java:57-59、ApiTokenController.java:55-56 ‹CERTAIN›〕。
- Post：认证成功 ⇒ 固定平台身份 `userId=api-token:{tokenId}`、roles=[api_token]、permissions=[kb:manage]，且 KB ACL 切面对其全局放行〔ApiTokenAuthFilter.java:77-88、KbAuthAspect.java:64-66 ‹CERTAIN›〕。
- 已知脱节（spec 观测-2，2026-09-24 已裁：按现状为准）：permission 字段仅存储，不参与鉴权。

**Parent Trace:** 接口 iam.ApiTokenService → Design.ApiTokenModule → REQ-014~017；缝 ApiTokenValidator → REQ-016。

### iam.KbAclService（KB ACL 管理契约）

**Purpose:** 知识库×用户的显式授权的唯一读写口；把「谁在哪个库是什么角色」的持久化与缓存一致性封装为六个方法。

**Input:**
- `getKbAcl(kbId)`；`setKbAcl(kbId: string, entries: KbAclDto[]{userId, kbRole, grantedBy}, 全量覆盖语义)`；`addAclEntry(kbId, userId, role: KBRole, grantedBy)`（upsert 语义：先删同键旧记录）；`removeAclEntry(kbId, userId)`；`getAccessibleKbIds(userId)`；`getKbRole(userId, kbId)`〔证据：KbAclService.java:22、KbAclServiceImpl.java:11-34 ‹CERTAIN›〕

**Output:** `getKbAcl → KbAclDto[]{id, kbId, userId, userName(实时回填), kbRole, grantedBy, grantedAt}`；`getKbRole → KBRole|null`；`getAccessibleKbIds → string[]`（显式 ACL 库，distinct）〔KbAclServiceImpl.java:11-16,29-34 ‹CERTAIN›〕

**Error modes:**
- E_KB_ROLE_INVALID — 触发：缓存/库中 kbRole 字符串非法时 `KBRole.valueOf` 抛 IllegalArgumentException → 兜底 500（GlobalExceptionHandler.java:47-53 Exception 兜底 → HTTP 200 + code 500）。恢复：数据修复。‹CERTAIN（2026-09-24 深证）：KbAclServiceImpl.java:14,31,33 三处 valueOf，全文件无 try/catch›
- 缓存不一致防护：写路径全部先清/同写缓存（见 Invariants），无独立错误模式。

**Invariants:**
- Pre：全部管理方法经 `@KbAuth(KBRole.owner)` 门禁——仅知识库 owner 可读写 ACL（KbAclController.java:46-56）。
- Post：`addAclEntry` ⇒ 库记录 upsert 且 `kb:acl:{kbId}:{userId}` 缓存写为同值 TTL 24h〔KbAclServiceImpl.java:23-27 ‹CERTAIN›〕。
- Post：`removeAclEntry` ⇒ 库与缓存键同步删除〔:28 ‹CERTAIN›〕。
- Post：`setKbAcl` ⇒ 先清除该库全部旧用户缓存，再全删全插（防被移除用户 TTL 残留）〔:18-22 ‹CERTAIN›〕。
- Post：`getKbRole` 读穿：缓存未命中回源并回填〔:30-34 ‹CERTAIN›〕。
- 自助查询口约束：`/acl/users/*` 两查询仅本人或持 `*` 权限者，越权 403「无权查看该用户的权限」（KbAclController.java:63-67）。

**Parent Trace:** 接口 iam.KbAclService → Design.KbAclModule → REQ-026、REQ-029、REQ-030。

### iam.KbAccessChecker + @KbAuth（KB 授权判定契约）

**Purpose:** 知识库域全部端点的统一权限闸：把「该用户在该库的有效角色」的解析（多数据源）与「注解声明的最低角色」比较为单一协议。

**Input / Output（按方法/注解面）:**
- `@KbAuth(value: KBRole=viewer)` 标注方法；切面按 owner(3)>editor(2)>viewer(1) 层级比较〔证据：KbAuth.java:20-22、KbAuthAspect.java:55-56,62-79 ‹CERTAIN›〕。
- `resolveRole(kbId: string, user: LoginUser) → KBRole|null`；`getAccessibleKbIds(userId, orgId) → string[]`〔证据：KbAccessChecker.java:30-43 ‹CERTAIN›〕
- 判定数据源（**现状实现，spec 复-1 仲裁项**）：max(ACL 显式角色, 组织兜底)；组织兜底 = private 库无、非 private 库管理员（`*` 通配或 kb_admin）→owner、普通成员→editor；两者皆无 → null〔证据：KbAccessCheckerImpl.java:29-54 ‹CERTAIN›；接口 javadoc 仍写「同组织兜底 viewer」已过期，KbAccessChecker.java:33-35 ‹CERTAIN›〕

**Error modes:**
- E_NO_KB_ACCESS — 触发：resolveRole 返回 null。恢复：403「无知识库访问权限」。〔KbAuthAspect.java:72-73 ‹CERTAIN›〕
- E_KB_ROLE_INSUFFICIENT — 触发：实际层级 < 注解要求。恢复：403「知识库权限不足」。〔KbAuthAspect.java:75-77 ‹CERTAIN›〕
- E_KB_ID_MISSING — 触发：URI 无法解析出 kbId（非 /api/kb/ 前缀路径误用注解）。恢复：400「缺少知识库ID」。〔KbAuthAspect.java:68-69 ‹CERTAIN›〕

**Invariants:**
- Pre：切面执行前请求必已通过 SecurityGate 认证（getCurrentUser 非 null）。
- Post：平台级 API Token 身份（userId 前缀 `api-token:`）不经 ACL 判定全局放行〔KbAuthAspect.java:64-66 ‹CERTAIN›〕。
- Post：登录用户（含超管/kb_admin）一律走判定，无旁路〔KbAuthAspect.java:65 注释 ‹CERTAIN›〕。

**Parent Trace:** 接口 iam.KbAccessChecker → Design.KbAccessDecisionModule → REQ-027、REQ-028（复-1）、REQ-031（判定协议同源）。

### iam.RbacDataService（RBAC 授权数据契约）

**Purpose:** 角色-权限-人员-组织四类授权数据的约束面：把「系统角色保护 / 默认角色全局唯一 / 权限标识唯一与级联 / 子组织保护 / 角色全量替换」这些不可破坏的规则集中在一个服务面，前端权限页是唯一消费场景。

**Input / Output（约束可见的方法面）:**
- 角色：`listRoles→RoleDto[]{id,roleKey,name,description,isDefault,isSystem,permissions[]}`；`createRole{name,description,permissions[]}`（roleKey 系统生成 `custom_<时间戳>`，不可自定义）；`updateRole{id,...}`（权限全量替换：先删后插）；`deleteRole{id}`；`setDefault{id}`〔证据：RoleServiceImpl.java:11-30 ‹CERTAIN›〕
- 权限：`listPermissions→PermissionDto[]{id,permKey,name,type,group,parentKey,category,description,roleIds[]}`（反查关联角色）；`getPermissionTree`（group 分组 → 组内 parentKey 层级）；`createPermission{permKey,...}`；`updatePermission`（permKey 不可改）；`deletePermission{id}`〔证据：PermissionServiceImpl.java:15-95 ‹CERTAIN›〕
- 人员：`listPersonnel(page,pageSize,keyword)→PageResult<PersonnelDto>`（keyword 模糊匹配用户名或姓名、createdAt 倒序）；`createPersonnel{username,realName,phone,email,password?,orgId?,roleIds?}`（缺省密码 123456）；`updatePersonnel`（用户名不可改，实现在更新分支不触碰 username）；`assignRoles(userId, roleIds)`（全量替换）；`updateStatus(userId, status)`；`findByUsername`〔证据：PersonnelServiceImpl.java:15-89、PersonnelController.java:49-100 ‹CERTAIN›〕
- 组织：`getOrgTree→OrgNodeDto[]{id,name,alias,parentId,level,memberCount,children[]}`（实时人数）；`getFlatList/getDepartmentNames/getDepartmentMembers`；`createOrg{name,alias,parentId?}`（缺省顶级，level 硬编码 1/2）；`updateOrg{name,alias}`；`deleteOrg{id}`〔证据：OrgServiceImpl.java:11-43 ‹CERTAIN›〕

**Error modes:**
- E_SYSTEM_ROLE — 触发：删除 isSystem=1 角色。恢复：400「系统角色不可删除」。〔RoleServiceImpl.java:22 ‹CERTAIN›〕
- E_PERM_KEY_DUP — 触发：创建权限时 perm_key 已存在。恢复：400「权限标识已存在」。〔PermissionServiceImpl.java:52-54 ‹CERTAIN›〕
- E_ORG_HAS_CHILDREN — 触发：删除存在子组织的节点。恢复：400「存在子组织」。〔OrgServiceImpl.java:43 ‹CERTAIN›〕
- E_USERNAME_DUP — 触发：创建人员用户名已存在。恢复：400「用户名已存在」。〔PersonnelServiceImpl.java:28-30 ‹CERTAIN›〕
- E_NOT_FOUND — 触发：角色/权限/用户/组织按 id 查无。恢复：404 语义。〔各 Impl notFound 分支 ‹CERTAIN›〕

**Invariants:**
- Pre：本模块全部写端点要求 admin:role / admin:user / admin:org 之一；读端点中 personnel/by-username、personnel/simple、org/* 仅需登录（低门槛读取口）〔各控制器 @PreAuthorize 与无注解读端点实测 ‹CERTAIN›〕。
- Post：`setDefault` ⇒ 全表 is_default 先清零再置一，全局恒有一个默认角色〔RoleServiceImpl.java:23-26 ‹CERTAIN›〕。
- Post：`deletePermission` ⇒ 级联删除 sys_role_permission 中该 perm_key 全部关联〔PermissionServiceImpl.java:81-84 ‹CERTAIN›〕；`deleteRole` ⇒ **不**级联清理角色-权限关联（仅删主表，RoleServiceImpl.java:22）——已登记的债务行为〔决议 2026-09-24：按现状为准——与删除权限的级联不对称属实但无注释/测试佐证意图，维持现状不补级联，是否修复属产品决策（代裁，用户可推翻）〕。
- Post：全部管理写操作有 @Loggable 审计（spec REQ-033，各控制器实测）。
- 已知脱节（spec 观测-3，2026-09-24 已裁：按现状为准，后端不补防护）：默认角色权限的前端保护（roles.vue）无后端对应，updateRole 可改默认角色权限。

**Parent Trace:** 接口 iam.RbacDataService → Design.RbacDataModule → REQ-022~025、REQ-033。

---

## kb-core

> 对应 design 模块：KnowledgeBaseModule / TagModule。数据实体契约见 data.md `## kb-core` 节。
> 端点排除：KbController 的 list/get/update 端点、KbCategoryController 全部端点（组织隔离 CRUD+计数，浅模块）、KbOperationServiceImpl 透传适配均为实现细节，不写契约（HTTP 暴露面见 design/kb-core.md §2）；只写两个深模块契约。鉴权判定协议复用 `## iam` 节的 iam.KbAccessChecker + @KbAuth，本节不重复定义。

### kb-core.KbLifecycleService（知识库生命周期契约）

**Purpose:** 知识库聚合根从生到死的唯一定点：创建时一次装配「组织归属 + owner 授权 + 类型派生 + 缺省填充」，删除时把知识库在 DB、向量库、图库、对象存储中的全部衍生数据回收干净——这是「删除测试通过」的核心深接口，列表可见性只是它的读取面。

**Input / Output（按方法）:**
- `list(keyword: string, 可选; category: string, 可选; page: int, 可选缺省 1; pageSize: int, 可选缺省 20) → {list: KbDto[], total: long, page: int, pageSize: int}`，按 createdAt 倒序〔证据：KbController.java:37、KbServiceImpl.java:88-95 ‹CERTAIN›〕
- `get(id: string, 必填) → KbDto{id,name,description,category,tags[],embeddingModel,dimension,creator,orgId,type,parseMode,splitMode,permission,graphAutoBuild,graphLlmModel,usedSize,totalSize,fileTypeConfig,retrievalConfig,customAttrSchema,createdAt}`〔KbDto.java:17-40 ‹CERTAIN›〕
- `create(req{name: string, 必填; category/description/permission/embeddingModel/parseMode/splitMode/graphLlmModel: string, 可选; graphAutoBuild: boolean, 可选; tags: string[], 可选; fileTypeConfig/retrievalConfig/customAttrSchema: object, 可选}, creator: string, 必填) → KbDto`〔KbCreateRequest.java:16-32 ‹CERTAIN›〕
- `update(id: string, 必填; req 同 create 但全字段可选) → KbDto`（部分语义：仅非 null 字段覆盖）〔KbServiceImpl.java:152-166 ‹CERTAIN›〕
- `delete(id: string, 必填) → void`〔KbServiceImpl.java:179-240 ‹CERTAIN›〕

**Error modes:**
- E_NAME_REQUIRED — 触发：创建时 name 缺失（@Valid 校验）。恢复：400 补名称。〔KbCreateRequest.java:17-18 + GlobalExceptionHandler 校验语义 ‹CERTAIN›〕
- E_KB_NOT_FOUND — 触发：get/update 的 id 查无。恢复：**RuntimeException 走兜底 500 语义（非 404）**——与 CategoryModule 的 notFound(404) 不一致，属登记债务。〔KbServiceImpl.java:101,151 ‹CERTAIN›〕
- E_PURGE_PARTIAL — 触发：删除时 Milvus/GraphStore/MinIO 任一清理失败。恢复：不阻断、不回滚——warn 日志记账后继续，DB 级联照常完成；残留对象需人工或重跑清理。〔KbServiceImpl.java:190-217 ‹CERTAIN›〕

**Invariants:**
- Pre：get/update/delete 前请求已过 iam 判定缝（@KbAuth viewer/editor/owner，KbController.java:40,56,60）。
- Post：create ⇒ ① kb.org_id = 创建者会话 orgId；② kb_acl 新增 (kbId, creator, owner)；③ permission 缺省 private；④ type = private→personal 否则 team；⑤ graphAutoBuild 请求未指定时写 0（**与 schema.sql:99 DDL 缺省 1 矛盾，代码生效值=0**）；⑥ usedSize/totalSize=0；⑦ tags 数组同步为 targetType=kb 关联。〔KbServiceImpl.java:113-141 ‹CERTAIN›〕
- Post：delete ⇒ kb_chunk、kb_file（含软删）、kb_qa_pair 中无该 kbId 残留；每文件执行 Milvus `kb_{kbId}` 集合 deleteByFileId、GraphStore deleteFileGraph、MinIO objectKey 与 `{kbId}/{fileId}` 前缀双删；KB 级与文件级标签关联删除且 usage_count 逐条校正；kb 本体删除；全程 @Transactional。〔KbServiceImpl.java:178-240 ‹CERTAIN›〕**（已知例外：导入 QA 对 fileId 强制为 null（QaPairServiceImpl.java:158）而级联清理按 kbId+fileId 匹配，删库后导入 QA 对残留——见本文件 qa-pairs 节债务①；修复前本不变式为目标态，tdd 勿据其生成现状断言）**
- Post：list/getCategories ⇒ 可见集 = API Token 全量 ∪（普通用户：本组织非 private 库 ∪ ACL 授权库）；可见集为空直接返回空页不落 SQL。〔KbServiceImpl.java:56-80、KbAccessCheckerImpl.java:57-68 ‹CERTAIN›〕
- 更新不重算 type、不触碰 creator/orgId。〔KbServiceImpl.java:149-175 ‹CERTAIN›〕

**Parent Trace:** 接口 kb-core.KbLifecycleService → Design.KnowledgeBaseModule → REQ-A01~A09、REQ-B01~B05。

### kb-core.TagConsistencyService（标签关联一致性契约）

**Purpose:** 一切打标行为（知识库打标、文件打标、标签管理页）的唯一关联与计数定点：保证「kb_tag.usage_count == 该标签在 kb_tag_relation 中的关联数」这一不变式在任意操作序列下成立，并守住「零引用才可删」门槛。

**Input:**
- `listTags(targetType: enum{kb,file}, 必填; targetId: string, 必填) → KbTag[]`（按关联 createdAt 排序）〔证据：TagRelationHelper.java:40-51 ‹CERTAIN›〕
- `replaceTags(targetType, targetId, tagIds: string[], 可选空=清空) → void`（差量语义）〔TagRelationHelper.java:54-98 ‹CERTAIN›〕
- `addTag(targetType, targetId, tagId)` / `removeTag(targetType, targetId, tagId)`（幂等）〔TagRelationHelper.java:101-127 ‹CERTAIN›〕
- `deleteTag(tagId: string, 必填) → void`〔TagRelationHelper.java:130-145 ‹CERTAIN›〕
- `countTagReferences(tagId) → long`〔TagRelationHelper.java:148-151 ‹CERTAIN›〕
- 标签本体：`create(req{kbId: string, 创建必填; name: string, 必填; color/description/tagTypeId: string, 可选})`、`update(id, req)`、`delete(id)`——写路径要求归属 KB editor+（API Token 放行）〔KbTagController.java:70-127,131-140 ‹CERTAIN›〕

**Output:** 标签本体 `KbTag{id,kbId,tagTypeId,name,color,description,usageCount,createdBy,createdAt}`〔KbTag.java:23-37 ‹CERTAIN›〕

**Error modes:**
- E_TAG_NAME_DUP — 触发：创建/更新后的标签名与既有标签重名（**全局唯一，跨 KB 也拒绝**，DB uk_name 兜底）。恢复：400 换名。〔KbTagController.java:76-80,104-107；schema.sql:1311 ‹CERTAIN›〕
- E_TAG_NOT_FOUND — 触发：更新/删除不存在的标签 id（404）或 replaceTags/addTag 引入不存在的标签 id（400「标签不存在: {id}」——两处语义码不同，登记债务）。恢复：改用有效 id。〔KbTagController.java:100,123、TagRelationHelper.java:69-71,102-104 ‹CERTAIN›〕
- E_KB_EDITOR_REQUIRED — 触发：标签写操作时操作者对归属 KB 无 editor+ 角色（resolveRole null 或层级不足）。恢复：403「知识库权限不足（需要编辑权限）」。〔KbTagController.java:132-140 ‹CERTAIN›〕
- E_TAG_IN_USE — 触发：删除仍被引用的标签（关联数 >0）。恢复：400 提示引用数，先移除引用。〔TagRelationHelper.java:133-137 ‹CERTAIN›〕
- E_USAGE_COUNT_DRIFT — 触发：删除时 usage_count 与关联数不一致且 count=0 而 usage>0（二次防线）。恢复：400 提示清理引用。〔TagRelationHelper.java:139-141 ‹CERTAIN›〕

**Invariants:**
- Pre：任何写操作前 targetType ∈ {kb,file}；relation 以 uk_tag_target(tag_id,target_type,target_id) 唯一。〔schema.sql:1321 ‹CERTAIN›〕
- Post：replaceTags ⇒ 关联集合恰等于去重后的 tagIds，usage_count 只对差量 ±1。〔TagRelationHelper.java:54-98 ‹CERTAIN›〕
- Post：addTag 已存在直接返回（不重复计数）；removeTag 无关联直接返回（不误减）；并发插入唯一键冲突被捕获并跳过计数。〔TagRelationHelper.java:89-96,105-109,119-127 ‹CERTAIN›〕
- Post：deleteTag ⇒ 仅当关联数=0 且 usage_count=0 时物理删除标签与残余关联。〔TagRelationHelper.java:130-145 ‹CERTAIN›〕
- 已知偏离（实现侧，KB 打标自建路径）：KbServiceImpl.syncTagRelations 先删后查导致减计数死代码、整轮重加使 usage_count 虚增——见 implement/kb-core.md §2 偏差登记；契约不变式以此为修复目标。〔KbServiceImpl.java:273-291 ‹CERTAIN›〕

**Parent Trace:** 接口 kb-core.TagConsistencyService → Design.TagModule → REQ-D01~D07。

---

## parse-strategy

> 对应 design 模块：StrategyConfigResolver / ParseMethodRegistry / ParseStrategyBindingGuard（StrategyCrudModule、StrategyMetaModule 为浅模块，CRUD 与透传端点按端点排除铁律不立契约，HTTP 暴露面见 design/parse-strategy.md §2）。
> 架构约束：ADR-0001（绑定变更必须触发重新分片，legacy 归档已丢失仅引要点）由 ParseStrategyBindingGuard 承载。数据实体契约见 data.md `## parse-strategy` 节。

### parse-strategy.StrategyConfigResolver（策略配置三层合并契约）

**Purpose:** 摄取管线（解析/分片/索引）、AI分片、图谱构建获取「这份文档按什么参数处理」的唯一入口：把系统默认值与策略 advanced JSON 合并为类型化 `ParseStrategyConfig`，吞掉全部历史格式与损坏数据的容错复杂度。

**Input / Output（按方法）:**
- `resolve(strategyId: string, 可空——null 仅系统默认层) → ParseStrategyConfig{parse{tableMode, keyframeIntervalSeconds, keyframeHashThreshold}, chunk{strategy, chunkLength, overlap, titlePrefix, headingPath, delimiters, parentMaxChunkLength, parentAggLevel, semanticThreshold, embeddingModel}, index{embedFields}}`〔证据：StrategyConfigResolver.java:54-56、ParseStrategyConfig.java:18-72 ‹CERTAIN›〕
- `resolve(strategy: KbParseStrategy, 必填可为「未落库临时对象」) → ParseStrategyConfig`（预览/预设场景）〔StrategyConfigResolver.java:61-74 ‹CERTAIN›〕

**Error modes:**
- E_ADV_MALFORMED — 触发：advanced JSON 解析失败或结构异常。恢复：warn 记账后沿用系统默认层，不抛错、不阻断管线。〔StrategyConfigResolver.java:93-95 ‹CERTAIN›〕
- E_CONFIG_ITEM_INVALID — 触发：分组内单项值类型非法（如数字字段为字符串）。恢复：逐项跳过（warn），其余项照常合并。〔:210-218 ‹CERTAIN›〕
- E_SYSCONFIG_UNREADABLE — 触发：SysConfig 读取异常或值非数字。恢复：debug 记账，用代码兜底值（chunkSize=1000/overlap=100）。〔:179-189 ‹CERTAIN›〕

**Invariants:**
- Pre：合并优先级固定为 代码内置默认 → SysConfig（general_chunk_size/general_chunk_overlap）→ 策略 advanced；低层结果只在高层缺失该键时保留。〔:61-74、87-92 ‹CERTAIN›〕
- Post：新分组结构（parse/chunk/index）优先；旧平铺字段（chunkLength/overlap/delimiter/单数 delimiter/tableMode/strategy 等）仅在同组键缺失时回退映射。〔:122-175 ‹CERTAIN›〕
- Post：返回对象各字段恒有值（ never null 的类型化默认），消费方无需判空。〔ParseStrategyConfig.java:29-71 字段初始化 ‹CERTAIN›〕
- 已知偏离（登记债务）：类 javadoc 声称「上传请求参数 > 选中策略 > KB 默认 > 系统默认」四层，实际仅实现后两层合并（请求参数覆盖无落点、KB 默认选择在绑定层）；契约以实现为准，javadoc 修复为待办。〔:22 vs :61-74 ‹CERTAIN›〕

**Parent Trace:** 接口 parse-strategy.StrategyConfigResolver → Design.StrategyConfigResolver → Spec REQ-G01、G02、G03、G06。

### parse-strategy.ParseMethodRegistry（文档类型权威映射契约）

**Purpose:** 「文档类型（解析方法）↔ 扩展名 ↔ 兼容性」的唯一事实源：前端表单（经 meta 端点）、后端保存校验、扩展名推断三方共享同一兼容矩阵，增删文档类型只改枚举一处。

**Input / Output（按方法）:**
- `values() → Registry[]{code, label, extensions[]}` — 九种方法：default（自动识别，兼容全部受支持扩展名）/pdf/doc/docx/pptx/xlsx/video/audio/image 各带专属扩展名集〔ParseMethodRegistry.java:29-39 ‹CERTAIN›〕
- `of(code: string) → Registry|null`〔:53-56 ‹CERTAIN›〕
- `resolveByExtension(extension: string) → code`（未识别/空回退 "default"）〔:59-66 ‹CERTAIN›〕
- `isSupported(extension) → boolean`；`isCompatible(methodCode, extension) → boolean`（DEFAULT 兼容一切受支持扩展名，专用方法仅自身集）〔:69-80 ‹CERTAIN›〕
- `supportedExtensions() → string[]`（带点小写去重保序）〔:83-91 ‹CERTAIN›〕

**Error modes:**
- E_METHOD_UNKNOWN — 触发：of() 查无该方法代码（含 null 入参）。恢复：返回 null，调用方（保存校验）拒绝。〔:53-56 ‹CERTAIN›〕
- E_EXTENSION_UNSUPPORTED — 触发：isSupported=false。恢复：调用方拒绝并提示可选扩展名。〔消费面 ParseStrategyServiceImpl.java:235-238 ‹CERTAIN›〕

**Invariants:**
- Pre：全部比较前扩展名归一化（trim/小写/补点，normalize :94-97）。
- Post：DEFAULT.extensions 恒等于 supportedExtensions() 的全集；专用方法扩展名集互不重叠（pdf 专用 .pdf 与 DEFAULT 重叠属定义内）。〔:29-39 ‹CERTAIN›〕
- 已知偏离（登记债务）：归一化仅在本注册表内生效——自动匹配两实现（FileServiceImpl.resolveStrategy / ParseStrategyServiceImpl.resolveByExtension）直接字符串比较不归一，大写扩展名会失配〔FileServiceImpl.java:918、ParseStrategyServiceImpl.java:129 ‹CERTAIN›〕‹CERTAIN（2026-09-24 深证：FileServiceImpl.java:130,134 扩展名按原文件名大小写入库无归一）›实际影响确定：原文件名大写扩展名（如 Report.PDF → 库存 "PDF"）在自动匹配中 equals 失配，回退默认/首个策略；显式绑定不受影响。

**Parent Trace:** 接口 parse-strategy.ParseMethodRegistry → Design.ParseMethodRegistry → Spec REQ-B03、B04、G04。

### parse-strategy.ParseStrategyBindingGuard（绑定-重切一致性守卫契约）

**Purpose:** ADR-0001 的执行面：保证「kb_file.parse_strategy_id（当前绑定）与该文件分片结果一致」这一不变式在任意操作序列（上传指定/元数据编辑/重试/重切/文件级自定义保存/删除回退）下成立——绑定变更必然伴随重新分片，绑定真源唯一。

**Input / Output（语义接口，实现为 FileServiceImpl 方法）:**
- `resolveRequestedStrategy(kbId, fileId, extension, requestedId: string 可空) → strategyId|null` — 请求指定且属本库（知识库级或本文件文件级）则用之，否则 warn 回退四级自动匹配〔FileServiceImpl.java:207-220、916-952 ‹CERTAIN›〕
- `rebind(kbId, fileId, strategyId: 三态——null 沿用/非空换绑/空串清除覆盖) → 绑定写回` — 换绑校验归属，非法拒绝〔:405-419 ‹CERTAIN›〕
- `rebuildProcessRequest(file) → FileProcessRequest` — 快照重建时以文件当前绑定覆盖快照内嵌历史策略值〔:226-247 ‹CERTAIN›〕
- `rejectMetadataRebind(patch) → void|拒绝` — patch 携带与当前不同的 parseStrategyId 时拒绝并引导走 re-chunk〔:642-649 ‹CERTAIN›〕

**Output:** 摄取消息携带的 strategyId（绑定的最终裁决值）；文件行 kb_file.parse_strategy_id 更新。

**Error modes:**
- E_REBIND_NOT_OWNED — 触发：换绑目标策略不存在或不属本知识库（含他文件专属策略）。恢复：400「解析策略不存在或不属于该知识库」。〔:414-416 ‹CERTAIN›〕
- E_REBIND_VIA_METADATA — 触发：元数据接口尝试改绑。恢复：400「解析策略变更须与重新分片一起完成，请调用 re-chunk 接口」。〔:647 ‹CERTAIN›〕
- E_RECHUNK_STATE_CONFLICT — 触发：文件在回收站/处理中/QA 模式时请求重切。恢复：400 对应提示。〔:376-384 ‹CERTAIN›〕
- E_REQUESTED_STRATEGY_MISSING — 触发：请求指定的 strategyId 查无。恢复：warn + 回退自动匹配（不阻断）。〔:217-219 ‹CERTAIN›〕

**Invariants:**
- Pre：任何绑定写回前，目标策略归属已校验（kb_id 匹配且 file_id 为空或等于本文件）。
- Post：换绑 ⇒ 同一流程内旧 auto 分片+向量清理（manual 保留、仅重置图谱标记）→ 文件重置 pending → 重发摄取消息；不存在「已换绑未重切」的合法终态。〔:405-478 ‹CERTAIN›〕
- Post：文件级自定义策略保存 ⇒ 原子接续重切（saveFileStrategy → reChunkFile）。〔:555-560 ‹CERTAIN›〕
- Post：绑定的策略被删除 ⇒ 下次处理自动回退扩展名自动匹配（悬空绑定被读路径容错）。〔ParseStrategyServiceImpl.java:49-52 ‹CERTAIN›〕
- Post：文件级专属策略永不进入自动匹配候选，防止文档级覆盖污染他文件选路。〔FileServiceImpl.java:920-924、492-495 ‹CERTAIN›〕

**Parent Trace:** 接口 parse-strategy.ParseStrategyBindingGuard → Design.ParseStrategyBindingGuard → Spec REQ-C01、C02、C04、D03、D04、E01~E05。

---

## file-ingestion

> 对应 design 模块：IngestionPipelineModule / FormatParserModule。数据实体契约见 data.md `## file-ingestion` 节；MinIO 对象键约定与 MQ 消息结构属运行时数据面，同在该节。
> 端点排除：FileController 26 端点、UploadController、下载族、预览、元数据五端点均为实现细节，不写契约（HTTP 暴露面见 design/file-ingestion.md §2）；re-chunk/preview/strategy 三端点的绑定面契约已在 `## parse-strategy` 节登记，本节不重复。StorageModule（分片存储）暂以 design §2 为准，kb_chunk 数据契约归 chunk-management 链。

### file-ingestion.IngestionPipeline（摄取流水线编排契约）

**Purpose:** 「上传的文件变成可检索分片」的唯一通道：把触发（指定参数、快照复用）、异步执行（MQ，宕机同步降级）、幂等、进度可视、步骤级降级与失败终态收拢为一个契约——上游只表达「处理这个文件」，其余全部在缝内。

**Input:**
- `process(kbId: string, 必填; fileId: string, 必填; req: FileProcessRequest{processingMode: enum{chunk,qa}, 可选缺省 chunk; parseStrategyId: string, 可选——语义归 parse-strategy 守卫; language: enum{auto,zh,en,mixed}, 可选; encoding: enum{auto,utf-8,gbk,shift-jis}, 可选; priority: string, 可选——仅落库不消费; retryCount: int, 可选缺省 0; engineConfig: {ocrEngine, asrEngine, videoStrategy, keyframeInterval}, 可选; mediaConfig: {timeRanges: {fileName: [start,end]}}, 可选})`〔证据：FileProcessRequest.java:16-33、FileServiceImpl.java:151-200 ‹CERTAIN›〕
- `retryFile(kbId, fileId)` — 清理重置后复用快照〔FileServiceImpl.java:317-348 ‹CERTAIN›〕
- `reChunkFile(kbId, fileId, strategyId: 三态, presetStrategy: 可选)` — 换绑语义归 parse-strategy.ParseStrategyBindingGuard；本契约负责物理重跑〔FileServiceImpl.java:369-482 ‹CERTAIN›〕
- MQ 消息（Map）：`fileId/kbId/objectKey/strategyId/operator/processingMode/enableGraphBuild/processingConfig`〔FileServiceImpl.java:186-196 ‹CERTAIN›〕

**Output:**
- `process/retryFile/reChunkFile → void/FileDto`；文件行 status/progress/stage/processingMode/parseStrategyId/processing_config/enable_graph_build 更新。
- 消费完成产物：kb_chunk 行 + Milvus 向量（经 StorageModule）、MinIO 衍生对象（segments/images/parsed.md）、系统通知、可选图谱构建消息。

**Error modes:**
- E_FILE_GONE — 触发：消费时文件不存在或已软删（deleted_at 非空）。恢复：ack 跳过、不重投、不报错。〔IngestionConsumer.java:126-134 ‹CERTAIN›〕
- E_DUPLICATE_MESSAGE — 触发：status=completed 时重复消息。恢复：ack 跳过（幂等）。〔:135-138 ‹CERTAIN›〕
- E_MQ_UNAVAILABLE — 触发：投递前探活失败或投递异常。恢复：进程内同步直调流水线（双重降级），MQ 恢复后无需补偿。〔MessagePublisher.java:81-103 ‹CERTAIN›〕
- E_PROCESS_FAILED — 触发：流水线任一必需步骤异常且重试（processingConfig.retryCount 次，重试前置回 pending）耗尽。恢复：status=failed、stage=error 摘要、失败日志；消息 ack 不回队，恢复手段是人工 retryFile/re-chunk。〔IngestionConsumer.java:156,526-544、605-616 ‹CERTAIN›〕
- E_RECHUNK_STATE_CONFLICT / E_REBIND_NOT_OWNED — 归 `## parse-strategy` 节 ParseStrategyBindingGuard，本节不重复定义。

**Invariants:**
- Pre：进入流水线的文件必已过上传阶段（objectKey 可下载）；strategyId 必为 parse-strategy 守卫裁决值（请求指定优先或自动匹配）。
- Post：消费全程 status ∈ {pending,processing,completed,failed} 且 progress/stage 按里程碑单调推进（10→30→60→[70→75]→80→100），stage 超 60 字符截断。〔IngestionConsumer.java:159-520、736 ‹CERTAIN›〕
- Post：completed ⇒ 分片已存储、parsed.md 已落盘（文档类）、通知与日志已尝试、enableGraphBuild 为真时图谱消息已投递（尝试即算，失败不回滚）。〔:464-519 ‹CERTAIN›〕
- Post：任一辅助步骤失败（日志/通知/图谱触发/音频切片/图片 OCR/Markdown 落盘/清理）不改变主流程终态判定。〔:260-262,479-481,509-511 ‹CERTAIN›〕
- Post：音频切片仅当分段数与分片数一致时产生，segments 对象键 `{kbId}/{fileId}/segments/{i}.{ext}`。〔:242-263 ‹CERTAIN›〕
- Post：重分片保留的 manual 分片 chunkIndex 重排至 auto 之后、file.chunkCount = auto + manual。〔:753-782 ‹CERTAIN›〕
- Post：触发时图谱开关仅在用户未显式设置过（updatedAt==createdAt）时按 KB 级解析。〔FileServiceImpl.java:169-180 ‹CERTAIN›〕

**Parent Trace:** 接口 file-ingestion.IngestionPipeline → Design.IngestionPipelineModule → Spec REQ-B01~B05、C01~C10、E01。

### file-ingestion.DocumentParser（格式解析路由与降级契约）

**Purpose:** 一切字节流到结构化内容的唯一翻译口：把 8 类格式、各自的第三方库、各自的失败模式翻译成统一 ParseResult，并保证「能解析多少给多少」——除非整体无法产出，任何局部失败都以降级消化而非中断。

**Input:**
- `parse(fileStream: InputStream, 必填; extension: string, 必填; strategyId: string, 可空; options: ParseOptions, 可空)` — 管线入口；strategyId 装载策略后路由〔证据：DocumentParser.java:19-21、DocumentParserImpl.java:89-92 ‹CERTAIN›〕
- `parse(fileStream, extension, strategy: KbParseStrategy 可为未落库临时对象, options)` — 预览场景〔DocumentParser.java:26、DocumentParserImpl.java:97 ‹CERTAIN›〕
- ParseOptions：ocrEngine/asrEngine/language/encoding/videoStrategy/keyframeInterval/timeRanges/fileName（上传向导一次性覆盖，null 字段回落策略/系统默认）〔ParseOptions.java:18-35 ‹CERTAIN›〕

**Output:** `ParseResult{text: string(Markdown 全文), pages: int, nodes: DocNode[](结构节点), segments: ChunkTimeSegment[](时间分段,音视频), images: ParseImage[](内嵌图片字节,消费方上传), pageTitles: Map<页,标题>}`〔ParseResult.java:11-46 ‹CERTAIN›〕

**Error modes:**
- E_PARSE_FAILED — 触发：格式解析整体失败（文件损坏/库异常）。恢复：统一包装上抛，由 IngestionPipeline 置 failed；调用方无法也不必区分内部库。〔DocumentParserImpl.java:119-122 ‹CERTAIN›〕
- E_EXTENSION_UNMAPPED — 触发：扩展名不在注册表。恢复：回退 default 纯文本解析（编码探测链），不视为错误。〔:101-102,117、ParseMethodRegistry.resolveByExtension ‹CERTAIN›〕
- E_XLSX_ENCRYPTED — 触发：加密工作簿。恢复：识别为专用错误文案上抛（可定位的失败）。〔:1736,1751-1769 ‹CERTAIN›〕
- D_LLM_ENHANCE_SKIPPED（降级）— 触发：无 apiUrl / 文本 >5000 字符 / 调用失败或空响应 / 15 秒超时。恢复：原文本继续（增强永不改变主流程）。〔:2744-2774 ‹CERTAIN›〕
- D_FFMPEG_UNAVAILABLE（降级）— 触发：FFmpeg 二进制不可用或执行失败。恢复：提音轨退化为原始字节直传 ASR；关键帧返回空列表（视频退化为纯 ASR）；切片/裁剪失败按全长或跳过。〔MediaExtractor.java:90-92,113-116、DocumentParserImpl.java:2473-2475,2677-2679 ‹CERTAIN›〕
- D_SINGLE_PATH_DEGRADED（降级）— 触发：视频双路中一路失败（ASR 或关键帧 OCR）。恢复：另一路单独成片；两路皆空返回空分段。〔:2494-2496,2526-2528,2560-2579 ‹CERTAIN›〕

**Invariants:**
- Pre：路由双键——策略 parseMethod 非 default 时用之，否则扩展名经 ParseMethodRegistry 推断；options 为 null 时全部字段回落策略/系统默认。
- Post：返回恒为合法 ParseResult 或统一 E_PARSE_FAILED，无第三态；调用方无需判空字段以外的容错。
- Post：PDF 文字 <50 字符的页走渲染 OCR 兜底；几何行与 OCR 文本两链路各自完成页眉脚/页码清理；页标题跨页继承。〔:133-242 ‹CERTAIN›〕
- Post：嵌入图片必经 ImageFilter 装饰性五规则过滤（解析期）+ 消费端兜底复查；OCR 文本并入正文前必经 OcrNoiseFilter 行级清洗（仅嵌入图片，扫描页整页 OCR 豁免）。〔ImageFilter.java:11-63、OcrNoiseFilter.java:8-28 ‹CERTAIN›〕
- Post：时间裁剪仅命中 options.timeRanges 中与 fileName 精确匹配的区间，裁剪失败按全长解析。〔:2538-2543,2466-2476 ‹CERTAIN›〕

**Parent Trace:** 接口 file-ingestion.DocumentParser → Design.FormatParserModule → Spec REQ-D01~D09（缝 1/2/3）。

---

## chunk-management

> 对应 design 模块：ChunkAlgorithmModule / ParentChunkAssembler / ChunkStoreModule / ChunkLifecycleModule / RechunkOrchestrator（ChunkQueryModeDetector 为浅模块，HTTP 暴露面见 design/chunk-management.md §1；ChunkController 7 端点为 CRUD 包装，按端点排除铁律不立契约）。数据实体契约见 data.md `## chunk-management` 节。
> 与上游链的分工（正式声明）：① **绑定裁决归 parse-strategy**——re-chunk 请求的 strategyId 三态校验、元数据改绑拒绝已登记于 `## parse-strategy` 节 ParseStrategyBindingGuard（ADR-0001 的裁决面），本节 RechunkOrchestrator 只定义其**物理执行面**（删旧留 manual→清理→重跑→对账），两契约以「裁决/执行」衔接，不重复定义 E_REBIND_NOT_OWNED / E_RECHUNK_STATE_CONFLICT（见 `## parse-strategy` 节）。② **异步流水线归 file-ingestion**——重跑走 `## file-ingestion` 节 IngestionPipeline 的 process（MQ 异步、里程碑、降级），本节不重复其错误模式。③ **kb_chunk 数据契约归本链**（上游 `## file-ingestion` 节首注明「kb_chunk 数据契约划归本链」）——摄取侧的写入行为以本节 ChunkStoreService 契约正式收编（上游预留的 StorageModule）。

### chunk-management.ChunkingService（分片算法族契约）

**Purpose:** 「解析产物变成分片」的唯一翻译口：五族算法把 text / DocNode 树 / 时间分段翻译成统一的 ChunkData 列表，吞掉标题层级、不可切分单元、overlap、语义断点等全部切分细节，并保证「能切多少给多少」——算法内部任何能力缺失都以回退消化，绝不中断摄取。

**Input / Output（按方法）:**
- `chunk(text: string, 必填; strategyId: string, 可空——仅系统默认层) → ChunkData[]`〔证据：ChunkingService.java:28、ChunkingServiceImpl.java:68-70 ‹CERTAIN›〕
- `chunk(text: string, 必填; config: ParseStrategyConfig, 必填可为未落库临时对象) → ChunkData[]`（预览场景）〔ChunkingService.java:33、ChunkingServiceImpl.java:76-84 ‹CERTAIN›〕
- `recursiveChunk(text: string, 必填; strategyId: string, 可空) → ChunkData[]`（分隔符优先级降级→句子边界→硬切）〔ChunkingService.java:48 ‹CERTAIN›〕
- `semanticChunk(text: string, 必填; strategyId: string, 可空; kbEmbeddingModel: string, 可空——策略级缺省兜底) → ChunkData[]`〔ChunkingService.java:66 ‹CERTAIN›〕
- `chunkBySegments(segments: ChunkTimeSegment[], 必填; strategyId: string, 可空) → ChunkData[]`（携带 startTime/endTime）〔ChunkingService.java:74 ‹CERTAIN›〕
- `structuralChunk(nodes: DocNode[], 必填; strategyId: string, 可空) → ChunkData[]`（携带 title/headingPath/chunkType/imageKeys）〔ChunkingService.java:89 ‹CERTAIN›〕

**Output 约定：** ChunkData{id "chunk_{index}", index, content, startTime/endTime, pageNumber/pageRange, imageKeys, chunkType("text"|"image"|"table"|"code"), title, headingPath}。〔ChunkData.java:27-44 ‹CERTAIN›〕

**Error modes（本契约无异常错误模式，只有降级）：**
- D_SEMANTIC_FALLBACK（降级）— 触发：无 embedding 模型（策略级与 KB 级均缺）/ embed 调用失败 / 返回向量数与句数不符。恢复：整体回退规则分片，摄入不中断。〔ChunkingServiceImpl.java:273-277,306-315 ‹CERTAIN›〕
- D_EMBED_MODEL_UNRESOLVED（降级）— 触发：策略级模型在模型表无 purpose=EMBEDDING 的在线记录（如误配对话模型）。恢复：自动回退 KB 级模型（历史错误配置自愈）。〔:285-292 ‹CERTAIN›〕
- 空输入 — 触发：text/nodes/segments 为空。恢复：返回空列表，非错误。〔:96-98,258-260,368-370,804-805 ‹CERTAIN›〕

**Invariants:**
- Pre：分片参数恒经 StrategyConfigResolver 三层合并获得（类型化、never null），算法自身不读策略表。〔ChunkingServiceImpl.java:49 ‹CERTAIN›〕
- Post：标题永不独占成片——标题行挂起与下一正文合并；仅文档以标题结尾时挂起标题独立成片。〔:529-536,623-629,818-835,881-887 ‹CERTAIN›〕
- Post：tableMode=ignore 跳过 TABLE 节点，但纯表格文档（仅 HEADING+TABLE 或表格标题型短段落）豁免保留表格，不产生空结果。〔:794-802,968-997 ‹CERTAIN›〕
- Post：不可切分单元（TABLE/CODE_BLOCK/DISPLAY_MATH/INLINE_MATH/IMAGE）整体保留；超长表格按行拆分且子片复制表头、代码按函数/类边界拆分（退化为按行均分）、公式图片保持完整。〔:851-861,1005-1039 ‹CERTAIN›〕
- Post：overlap ≥ chunkLength 时强制隔离（effectiveLength=max(chunkLength, overlap+1)），无死循环。〔:761 ‹CERTAIN›〕
- Post：代码围栏（``` / ~~~）内不识别标题行，围栏状态跨 part/页块持久。〔:646-680 ‹CERTAIN›〕

**Parent Trace:** 接口 chunk-management.ChunkingService → Design.ChunkAlgorithmModule → Spec REQ-D01~D10；缝 1（策略配置）→ 上游 parse-strategy REQ-G01~G06。

### chunk-management.ParentChunkAssembler（父子装配契约）

**Purpose:** 「父分片/子分片」两级语义的唯一裁决点：按标题层级把子分片聚合成父分片（命中后的上下文），使子分片保持小块召回精度、父分片提供完整章节上下文；聚合在存储层执行，保证图片 OCR 分片与所属章节归入同一父分片。

**Input:**
- `assemble(chunks: ChunkData[], 必填按文档顺序; fileId: string, 必填; maxParentLength: int, 必填; aggLevel: string, 可空——"H1"/"H2"/"H3"/"auto"/null)`〔证据：ParentChunkAssembler.java:46-56 ‹CERTAIN›〕

**Output:** `AssemblyResult{parents: ChunkData[](chunkType="parent", id="{fileId}_parent_{n}", index=组内首子分片 index, content=组内子分片 content 按 "\n\n" 拼接), children: ChunkData[](parentId 已回填)}`〔ParentChunkAssembler.java:32-36,146-166 ‹CERTAIN›〕

**Error modes:** 无——纯函数、零依赖，任何输入返回合法 AssemblyResult（空/单分片输入不聚合）。〔:57-63 ‹CERTAIN›〕

**Invariants:**
- Pre：输入按文档顺序（chunkIndex 升序）；分组仅按**连续**相同分组键（同 headingPath 不连续则分组两段）。〔:69-83 ‹CERTAIN›〕
- Post：headingPath 为 null/空（文档前言）视为同一组；aggLevel 为 H1/H2/H3 时分组键截断到对应层级，auto/非法值按完整路径。〔:93-113 ‹CERTAIN›〕
- Post：每组至少生成 1 个父分片；组总长超 maxParentLength 时按子分片边界二次切分为多个父分片，共享标题上下文。〔:121-143 ‹CERTAIN›〕
- Post：children 列表被**原地变异**（parentId 回填）——调用方以返回的 children 为准落库。〔:153-155 ‹CERTAIN›〕
- Post：父分片本身不向量化、不进 Milvus（vector_stored=0，由 ChunkStoreService 落实）。〔StorageServiceImpl.java:282-298 ‹CERTAIN›〕

**Parent Trace:** 接口 chunk-management.ParentChunkAssembler → Design.ParentChunkAssembler → Spec REQ-E01~E05。

### chunk-management.ChunkStoreService（摄取落库契约）

**Purpose:** 摄取管线的分片落库定音锤（正式收编上游 file-ingestion 节预留的 StorageModule）：把「一批分片」变成「MySQL 行 + Milvus 向量 + 文件计数」三处一致的状态，吞掉分批 embedding、重试、父子装配触发、错位防护与元数据冗余回填的全部编排；上游只表达「存这些分片」。

**Input:**
- `storeChunks(kbId: string, 必填; fileId: string, 必填; chunks: ChunkData[], 必填非空)`——单层模式〔证据：StorageServiceImpl.java:50-52 ‹CERTAIN›〕
- `storeChunks(kbId, fileId, chunks, childChunkLength: int)` / `storeChunks(kbId, fileId, chunks, childChunkLength, maxParentLength: int, parentAggLevel: string)`——childChunkLength>0 启用父子装配（maxParentLength 缺省=childChunkLength×2）〔:57-65 ‹CERTAIN›〕
- `storeChunkBatch(kbId, fileId, chunks, batchStart: int)`——流式分批变体〔:178 ‹CERTAIN›〕

**Output:** kb_chunk 行（auto 子分片 + parent 父分片）、Milvus 向量、kb_file.chunk_count=本次 auto 数（对账前）。

**Error modes:**
- E_EMBED_BATCH_EXHAUSTED — 触发：某批 embedding 重试 3 次（1s/2s/4s 指数退避）均失败。恢复：该批以纯文本落库（vector_stored=0）、Milvus 跳过该批（ids/vectors 错位防护），不阻断。〔StorageServiceImpl.java:44-45,128-134,323-360 ‹CERTAIN›〕
- E_MILVUS_INSERT_FAILED — 触发：批量写 Milvus 异常。恢复：error 记账继续，MySQL 已提交不回滚——向量缺失由 vector_stored 与重切补救。〔:162-164 ‹CERTAIN›〕
- E_BATCH_INSERT_FAILED — 触发：MySQL 批插异常（BATCH SqlSession 提交失败）。恢复：RuntimeException 上抛 → IngestionPipeline 置文件 failed（唯一上抛路径）。〔:271-274 ‹CERTAIN›〕

**Invariants:**
- Pre：调用方已按 chunk.strategy 分发算法并完成图片分片归并（本契约不切分，只落库）。
- Post：auto 分片 id 恒为 `{fileId}_chunk_{globalIndex}`、origin=auto、embedding_id=id；父分片 id=`{fileId}_parent_{n}`、chunkType=parent、vector_stored=0、不进 Milvus。〔:239-258,282-298 ‹CERTAIN›〕
- Post：分片行冗余回填文件级元数据 region/publish_date/doc_level（检索过滤免 JOIN）。〔:259-264 ‹CERTAIN›〕
- Post：embedding 输入恒为 content + headingPath + fileName 拼接（结构层级进入向量语义）。〔:366-378 ‹CERTAIN›〕
- 事务边界：不用 Spring @Transactional（与 BATCH Executor 互斥），批插自管事务。〔:48-49,55-56,62-63 ‹CERTAIN›〕

**Parent Trace:** 接口 chunk-management.ChunkStoreService → Design.ChunkStoreModule → Spec REQ-E01~E05（装配触发）、G01、G05。

### chunk-management.ChunkLifecycleService（手工生命周期契约）

**Purpose:** 用户手工操作分片（查/建/改/删/批删）的唯一约束面：保证「MySQL 行、Milvus 向量、图谱 mention 三处一致」与「索引连续、父子内容一致、chunkCount 对账」两组不变式在任意手工操作序列下成立——外部系统的失败以降级记账消化，MySQL 主流程不回滚。

**Input / Output（按方法，HTTP 包装为 ChunkController 7 端点）:**
- `list(kbId: string, 必填; fileId: string, 可空; page/pageSize: int, 可选缺省 1/20) → Map{mode:"parent"|"single", list, total, page, pageSize, parentCount, tokenTotal}`〔证据：ChunkServiceImpl.java:46-100 ‹CERTAIN›；Map 形态偏离 PageResult 类型——NFR-6 登记债务〕
- `getCount(kbId: string, 必填) → long`（排除父分片）〔:103-107 ‹CERTAIN›〕
- `getById(kbId: string, 必填; id: string, 必填) → ChunkDto`〔:110-119 ‹CERTAIN›〕
- `create(kbId: string, 必填; req{fileId: string, 必填; content: string, 必填; insertAfterIndex: int, 可空——空=追加末尾; startTime/endTime: double, 可空; pageNumber: int, 可空; chunkType: string, 可空缺省 "text"}) → ChunkDto`〔ChunkCreateRequest.java:11-27、ChunkServiceImpl.java:122-195 ‹CERTAIN›〕
- `update(kbId: string, 必填; id: string, 必填; fields: Map{content/startTime/endTime/pageNumber/chunkType/imageKeys}, 必填) → ChunkDto`〔ChunkServiceImpl.java:198-273 ‹CERTAIN›〕
- `delete(kbId: string, 必填; id: string, 必填) → void`；`batchDelete(kbId: string, 必填; ids: string[], 必填) → void`〔:276-324,327-388 ‹CERTAIN›〕

**Error modes:**
- E_CHUNK_NOT_FOUND — 触发：getById/update/delete 的 id 查无。恢复：RuntimeException → 全局兜底 500 语义（**非 404**，NFR-5 登记债务）。〔ChunkServiceImpl.java:113,202,280 ‹CERTAIN›〕
- E_CHUNK_KB_MISMATCH — 触发：分片存在但 kb_id 不匹配（跨库访问他库分片）。恢复：同上 500 语义（债务）。〔:115-116,204-206,282-284 ‹CERTAIN›〕
- E_FILE_NOT_FOUND — 触发：create 的 fileId 查无。恢复：同上 500 语义（债务）。〔:128-130 ‹CERTAIN›〕
- E_VECTOR_SYNC_FAILED（降级）— 触发：Milvus 删/插/embedding 失败。恢复：warn 记账、vector_stored 如实置位，编辑/删除照常成功。〔:177-181,234-236,299-306 ‹CERTAIN›〕
- E_GRAPH_SYNC_FAILED（降级）— 触发：deleteChunkGraph/renameChunkId 失败。恢复：warn 记账，不回滚 DB 操作（mention 残留由图谱链清理）。〔:253-266,308-315,430-434 ‹CERTAIN›〕

**Invariants:**
- Pre：读操作 viewer+、写操作 editor+（@KbAuth）；四个写端点 @Loggable 审计。〔ChunkController.java:42-91 ‹CERTAIN›〕
- Post：create ⇒ id=`{fileId}_manual_{n}`（n=文件内最大 manual 序号+1）、origin=manual、向量按 KB 级模型生成；insertAfterIndex 指定时后续 auto 分片索引 +1 且 id/向量/图谱 chunkId 连锁重建（manual 分片仅移位不动 ID/向量）。〔ChunkServiceImpl.java:144-163,395-447 ‹CERTAIN›〕
- Post：delete ⇒ 父分片级联子分片；三处清理（MySQL deleteBatchIds / Milvus deleteByIds / GraphStore deleteChunkGraph 逐片）；后续分片索引 -1；chunkCount 重算。〔:289-321 ‹CERTAIN›〕
- Post：batchDelete ⇒ 跨库 id 静默忽略；父分片级联；**不**做索引重排〔:336-385 ‹CERTAIN›〕〔决议 2026-09-24：按现状为准——全路径无 shiftChunkIndices 调用、无注释/测试佐证「有意保留」，意图不可考；不对称已登记为已知偏离，批量删除后允许索引空洞，列表排序不受影响（代裁，用户可推翻）〕；涉及文件 chunkCount 逐一重算。
- Post：update content ⇒ 向量删旧插新、父分片内容重拼（子分片 "\n\n" 拼接）、图谱数据清理 + graphIndexed=0 + 增量构建触发（文件 enableGraphBuild=1 时）。〔:222-269 ‹CERTAIN›〕
- Post：任意写操作 ⇒ kb_file.chunk_count = 该文件非父分片行数。〔:490-500 ‹CERTAIN›〕

**Parent Trace:** 接口 chunk-management.ChunkLifecycleService → Design.ChunkLifecycleModule → Spec REQ-A01~A05、B01~B06、C01~C04、G02~G04。

### chunk-management.RechunkOrchestrator（重切物理编排契约）

**Purpose:** ADR-0001 的物理执行面：把「这份文件按当前绑定重切」翻译为一次安全的替换操作——auto 分片与向量定向清除、manual 分片原地保留、图谱随文件清空、文件重置后重跑标准摄取流水线，并在管线完成后对账保留分片。绑定裁决（换绑校验/元数据拒绝）在 parse-strategy 守卫，异步执行在 file-ingestion IngestionPipeline，本契约只对「替换的物理语义」负责。

**Input:**
- `reChunkFile(kbId: string, 必填; fileId: string, 必填; strategyId: string, 三态——null 沿用/非空换绑/空串清除覆盖; presetStrategy: string, 可空——预设策略名如 structure_aware)`〔证据：FileServiceImpl.java:364-369 ‹CERTAIN›；三态语义裁决归 `## parse-strategy` 节〕
- `reconcileManualChunks(kbId, fileId)` — 隐式契约：storeChunks 完成后由流水线调用（包内私有）〔IngestionConsumer.java:458-461,753-782 ‹CERTAIN›〕

**Output:** FileDto（文件重置 pending 后的快照）；重切终态由流水线异步达成。

**Error modes:**
- E_RECHUNK_STATE_CONFLICT / E_REBIND_NOT_OWNED — 归 `## parse-strategy` 节 ParseStrategyBindingGuard，本节不重复定义。〔FileServiceImpl.java:375-384,414-416 ‹CERTAIN›〕
- D_VECTOR_CLEAN_FAILED（降级）— 触发：auto 分量删除时 Milvus 异常。恢复：warn 记账继续——残留向量在新分片落库后仍指向旧 id，依赖重切完成后的整体一致性。‹CERTAIN（2026-09-24 深证：RetrievalServiceImpl.java:586-591、602-603）›残留过滤机制已证：检索侧向量召回后按 id 批量查 MySQL（selectByIds），旧 id 在 MySQL 无行 → `chunk == null → continue` 静默丢弃，且已删文件的分片亦被 isFileDeleted 过滤（:590）——「自然过滤」成立。〔FileServiceImpl.java:440-449 ‹CERTAIN›〕
- D_GRAPH_CLEAN_FAILED（降级）— 触发：deleteFileGraph 异常。恢复：warn 记账继续，重切照常。〔:450-455 ‹CERTAIN›〕

**Invariants:**
- Pre：重切只操作 origin≠manual 的分片；Milvus 按 auto 分片的 embeddingId 选择性删除（不用 deleteByFileId，防误删保留分片向量）。〔:421-449 ‹CERTAIN›〕
- Post：清理与重发在同一流程内完成（manual 分片 graphIndexed 重置 → 文件 pending/progress=0/chunkCount=0 → process(rebuildProcessRequest(f))），不存在「已清理未重跑」的合法终态——ADR-0001 无漂移态。〔:457-478 ‹CERTAIN›〕
- Post：管线 completed 后 ⇒ auto 分片全部为本次新生成（id 从 `{fileId}_chunk_0` 重新编号）、manual 分片行/ID/向量原样保留且 chunkIndex 重排至 auto 之后、chunk_count=auto+manual。〔IngestionConsumer.java:753-782 ‹CERTAIN›〕
- Post：AI 分片应用落库采用同款「删 auto 留 manual」替换口径（以用户确认边界为唯一事实源，CONTEXT.md AI分片词条）。〔AiChunkServiceImpl.java:804-835 ‹CERTAIN›〕

**Parent Trace:** 接口 chunk-management.RechunkOrchestrator → Design.RechunkOrchestrator → Spec REQ-F01~F06；上游衔接 parse-strategy.ParseStrategyBindingGuard（REQ-C01/C02/E01~E05）与 file-ingestion.IngestionPipeline（REQ-B01~B05、C01~C10）。

---

## onlyoffice-editing

> 对应 design 模块：EditorSessionModule / CallbackOrchestrator / ForceSaveCommander / OoTrustTokenModule。数据契约见 data.md `## onlyoffice-editing` 节（本特性无自有表，仅运行时结构与配置面）。
> 端点排除：`/onlyoffice/config`（浏览器面，@KbAuth viewer）、`/onlyoffice/forcesave`（浏览器面，@KbAuth viewer + editor 内判）、`/onlyoffice/raw` 与 `/onlyoffice/callback`（免登录面，SecurityConfig.java:73-74 permitAll + OO 自签 JWT 校验）四个端点是本特性模块的**外部面**——config 是给浏览器直传 `DocsAPI.DocEditor` 的 payload，raw/callback 是给 Document Server 机器调用的协议面——按端点排除铁律不立契约（HTTP 暴露面见 design/onlyoffice-editing.md §2）。契约写给内部深模块：文档令牌签发、编辑会话开立、保存回调编排、强制保存命令。
> 外部系统边界：OnlyOffice Document Server（docker documentserver:8.3.3，:8082/:8000）是独立容器进程；本节契约均以后端一侧为立场，OO 侧行为仅以协议约定出现。

### onlyoffice-editing.OoTrustToken（双向信任令牌契约）

**Purpose:** Document Server 与后端共享密钥（`onlyoffice.jwt-secret` ↔ 容器 `JWT_SECRET`）之下一切信任凭证的唯一签发/验签点：正向（config 整包签名、forcesave 命令 token）与反向（raw 查询 token、callback body token）共用同一格式与密钥，把与 OO Node 端 jsonwebtoken 的互操作细节（标准 3 段 JWS）收敛到 fastrag-security 单点。

**Input / Output（按方法）：**
- `signPayload(payload: Object, 必填任意可序列化对象; ttlMillis: long, 必填) → string（3 段 JWS，HS256，payload JSON 编码于 subject claim，iat/exp 写入）`〔证据：OnlyOfficeJwtUtil.java:65-78 ‹CERTAIN›〕
- `signRawFileToken(kbId: string, 必填; fileId: string, 必填; ttlSeconds: long, 必填) → string`（claims 固定 `{kbId, fileId, scope: "onlyoffice-raw"}` + exp）〔:84-90 ‹CERTAIN›〕
- `verify(token: string, 必填) → Map<String,Object>`（验签并从 subject 反解回签发时的 payload）〔:97-117 ‹CERTAIN›〕

**Error modes:**
- E_TOKEN_SIGN_FAILED — 触发：payload 序列化失败。恢复：OnlyOfficeTokenException 上抛，调用流程终断。〔:75-77 ‹CERTAIN›〕
- E_TOKEN_INVALID — 触发：签名不符 / 格式非法（非 3 段 JWS）/ 已过期（含 ±300 秒时钟漂移容忍）/ subject 为空。恢复：OnlyOfficeTokenException，调用方拒绝请求（raw → HTTP 401，callback → error=1）。〔:52,106-116 ‹CERTAIN›〕

**Invariants:**
- Pre：密钥 `onlyoffice.jwt-secret` 与 OO 容器 `JWT_SECRET` 部署时同值（application.yml:162-163、OnlyOfficeJwtUtil.java:48-49 ‹CERTAIN›）——本契约的正确性前提是密钥同步，属部署纪律而非代码可证。
- Post：签发恒为标准 3 段 JWS(HS256)（OO 实测：2 段自定义格式被 jsonwebtoken 拒绝，javadoc :32-36 ‹CERTAIN›）。
- Post：verify 通过 ⇒ 返回 Map 恰为签发 payload 的反解；任何篡改/过期在 ±300s 之外必被拒。〔:99-111 ‹CERTAIN›〕

**Parent Trace:** 接口 onlyoffice-editing.OoTrustToken → Design.OoTrustTokenModule + 缝 2 → Spec REQ-E02、REQ-E03。

### onlyoffice-editing.EditorSessionService（编辑会话开立契约）

**Purpose:** 「浏览器对这份文件开一个在线编辑会话」的唯一裁决点：判定文件可编辑性、派生文档身份（document.key——「保存后强制重载新版本」机制的根基）、组装 Document Server 直接可消费的 config，并登记活跃会话供 forcesave 定位与 callback 兼容。

**Input：**
- `isSupportedFile(fileName: string, 必填) → boolean`（enabled 开关 ∧ 六扩展名 doc/docx/xls/xlsx/ppt/pptx 之一）〔证据：OnlyOfficeServiceImpl.java:129-137 ‹CERTAIN›〕
- `buildDocumentKey(fileId: string, 必填; updatedAt: LocalDateTime, 可空) → string(32 hex)`〔:145-162 ‹CERTAIN›〕
- `signRawFileToken(kbId, fileId) → string`（委托 OoTrustToken，TTL 固定 1800 秒）〔:140-142 ‹CERTAIN›〕
- `buildEditorConfig(kbId: string, 必填; fileId: string, 必填; userId: string, 可空——null 记 "anonymous"; userName: string, 可空——null 记 "Anonymous"; editable: boolean, 必填——控制器由 KB 角色裁决) → Map`〔:165-227 ‹CERTAIN›〕

**Output（config Map 全字段）：** `document{fileType(doc→word 系约定), key, title, url(=storage-base-url + raw 路径 + ?token=…&key=…), permissions{edit=editable, download=true, print=true, copy=true}}` + `editorConfig{callbackUrl(=callback-base-url + callback 路径), mode(editable?"edit":"view"), user{id,name}, lang:"zh-CN", customization{autosave:true, forcesave:true, compactHeader:true, toolbar:true}}` + `documentType/type（doc/docx→word、xls/xlsx→cell、ppt/pptx→slide）` + `width/height:"100%"` + `token（仅 jwtEnabled=true 时）`。〔:180-222 ‹CERTAIN›〕

**Error modes:**
- E_OO_DISABLED — 触发：`onlyoffice.enabled=false`。恢复：控制器层 503 业务错误（HTTP 200 + body.code）；isSupportedFile 恒 false。〔OnlyOfficeController.java:71-73、OnlyOfficeServiceImpl.java:130-131 ‹CERTAIN›〕
- E_FILE_NOT_FOUND — 触发：fileId 在该 kbId 下查无。恢复：控制器前置 404；服务层 IllegalArgumentException 兜底（控制器已拦截，属防御性双检）。〔OnlyOfficeController.java:76-78、OnlyOfficeServiceImpl.java:169-171 ‹CERTAIN›〕
- E_EXT_UNSUPPORTED — 触发：扩展名不在六值集。恢复：控制器前置 400「OnlyOffice 不支持该文件类型」；服务层 IllegalArgumentException 兜底。〔OnlyOfficeController.java:79-81、OnlyOfficeServiceImpl.java:173-175 ‹CERTAIN›〕

**Invariants:**
- Pre：buildEditorConfig 前请求已过 @KbAuth(viewer)（浏览器面）；文件归属（kbId+fileId 联查）已核实。
- Post：buildEditorConfig 成功 ⇒ `activeSessionKeys[fileId] = documentKey` 登记（进程内存、无逐出）。〔:179 ‹CERTAIN›〕
- Post：documentKey 恒为 SHA-256(fileId + updatedAt.toString()) 前 32 个十六进制字符：同输入恒同输出；replaceOriginalFile 更新 updatedAt ⇒ key 必变 ⇒ Document Server 强制重载新文件；摘要异常退化为 `fileId_当前毫秒`（长度不定，理论分支）。〔:145-162 ‹CERTAIN›〕
- Post：editable=false ⇒ mode=view 且 permissions.edit=false；customization.forcesave 恒 true（自动保存与强制保存由 OO 侧开启）。〔:191-208 ‹CERTAIN›〕
- Post：jwtEnabled=false ⇒ config 不含 token 字段（OO 容器 token.enable 同步关闭时不签名）。〔:219-222 ‹CERTAIN›〕
- 已知偏离（登记债务）：config 的 role→editable 映射在控制器以权限字符串近似（`*`/kb:manage/kb:write/kb:edit → editor），与 iam.KbAccessChecker 的 ACL/组织兜底判定不同源；javadoc「KbAuthAspect 已把角色存入 LoginUser」与实现不符。〔OnlyOfficeController.java:182-191、KbAuthAspect.java:64 ‹CERTAIN›〕

**Parent Trace:** 接口 onlyoffice-editing.EditorSessionService → Design.EditorSessionModule → Spec REQ-A01~A06、REQ-B01、REQ-B03（editable 输入承接 REQ-A04/REQ-E05 的控制器裁决）。

### onlyoffice-editing.CallbackSaveOrchestrator（保存回调编排契约）

**Purpose:** Document Server 保存意图（status=2/6）落地的唯一编排点，也是免登录面上「谁有权改这个文件」的最终裁决者：双因子准入（OO token + document.key）之后执行「下载 → 覆盖原文件 → Markdown 重落盘 → 触发重分片」，并把「保存事实成功但检索联动失败」降级为对 OO 的成功应答——已覆盖的 MinIO 原文件是保存事实源。

**Input：**
- `handleCallback(kbId: string, 必填; fileId: string, 必填; payload: OnlyOfficeCallbackPayload, 整体可空、字段可空) → Map{error: 0|1[, actions]}`——OO 协议原样，不包 ApiResponse〔证据：OnlyOfficeServiceImpl.java:230-356、OnlyOfficeController.java:161-175 ‹CERTAIN›〕
- payload 字段面：status(Integer 1/2/3/4/6/7)/url/status=2 下载 URL/key/document.key/token/OO 签名回调 token 等，全字段见 data.md `## onlyoffice-editing` 节回调 payload 结构。

**Output：** `{error: 0}`（ack/受理成功）或 `{error: 1}`（拒绝/失败）；Document Server 据此决定保留或继续编辑。

**Error modes（全部以 error=1 应答，无异常外抛）：**
- E_PAYLOAD_MALFORMED — 触发：payload 为 null 或 status 为 null。恢复：error=1，OO 侧重试或放弃。〔:232-236 ‹CERTAIN›〕
- E_SAVE_REJECTED — 触发：status=3/7（OO 侧保存出错上报）。恢复：error=1，零动作。〔:249-253 ‹CERTAIN›〕
- E_TOKEN_MISMATCH — 触发：jwtEnabled 且 token 验签失败（E_TOKEN_INVALID 传导）或 kbId/fileId claim 与路径不一致。恢复：error=1，存储零改动。〔:262-276 ‹CERTAIN›〕
- E_KEY_MISMATCH — 触发：key 非 null 且既不等于「当前 updatedAt 派生 key」也不等于「登记会话 key」（文件在我们不知情时被改动）。恢复：error=1 保守拒绝，存储零改动。〔:291-302 ‹CERTAIN›；key=null 容忍通过属宽松面，登记债务〕
- E_DOWNLOAD_FAILED — 触发：url blank / 下载 HTTP≥400 / 连接(30s)/读(60s) 超时。恢复：error=1，存储零改动。〔:305-317,363-378 ‹CERTAIN›〕
- E_REPLACE_FAILED — 触发：replaceOriginalFile 异常（文件已进回收站 / MinIO 故障）。恢复：error=1（编排 catch 收口）。〔:342-346 ‹CERTAIN›〕
- D_AUX_STEP_DEGRADED（降级，非错误模式）— 触发：Markdown 重落盘失败或 re-chunk 触发失败（replace 已成功）。恢复：warn 记账，应答仍 error=0；检索一致性由人工 re-chunk 补救。〔:325-341 ‹CERTAIN›〕

**Invariants:**
- Pre：本契约运行于免登录面——调用方身份由 OoTrustToken 验签 + document.key 双因子裁决，不依赖 SecurityContext（permitAll 白名单，SecurityConfig.java:73-74）。
- Post：status∈{1,4} ⇒ error=0 且 MySQL/MinIO/MQ 零副作用；status∈{3,7} ⇒ error=1 且零副作用；status∉六值集 ⇒ warn + error=0 且零副作用。〔:242-255,352-355 ‹CERTAIN›〕
- Post：status∈{2,6} 且准入通过 ⇒ 终态二选一：「原对象已覆盖 + size/updatedAt 已更新 + parsed.md 已尝试 + 重切消息已尝试 + error=0」或「存储零改动 + error=1」——不存在「部分落盘却报错」中间态（replace 成功后只降级不回滚）。〔:319-349 ‹CERTAIN›〕
- Post：重切恒以 strategyId=null 触发（沿用当前策略绑定）——本契约不做绑定裁决（归 parse-strategy.ParseStrategyBindingGuard），物理执行归 chunk-management.RechunkOrchestrator；内容变更必触发重切承接 ADR-0001，不存在「已编辑未重切」的合法终态。〔:337 ‹CERTAIN›〕
- 幂等语义（重点声明）：**无按 key/status 的判重**——同一会话内多次 status=2/6（autosave 周期、手动 forcesave、关闭保存）各自完整执行编排；重复执行最终一致无害（MinIO 覆盖写 + 重切确定性），代价是重复资源消耗。〔结构观测 ‹CERTAIN›；无害性 ‹CERTAIN（2026-09-24 深证：MinIO 覆盖写幂等 + 重切为删全部 auto 分片后重建，FileServiceImpl.java:421-436，同内容重复执行终态收敛；多次保存受理为有意设计，OnlyOfficeServiceImpl.java:124-125,281-283 注释明示）〕
- 已知偏离（登记债务）：下载 URL 不做主机白名单校验——jwtEnabled=false 时该 URL 完全由回调方控制，构成 SSRF 面（30s/60s 超时与 ≥400 判定是仅有防护）。〔:363-378 ‹CERTAIN›〕

**Parent Trace:** 接口 onlyoffice-editing.CallbackSaveOrchestrator → Design.CallbackOrchestrator + 缝 1 → Spec REQ-D01~D07、REQ-E04。

### onlyoffice-editing.ForceSaveCommander（强制保存命令契约）

**Purpose:** 浏览器「保存」按钮到 Document Server CommandService 的唯一出口：定位活跃会话、下发 `c=forcesave` 命令、把 OO 错误码翻译为前端可读的 result 语义——本契约只发起不落盘，落盘由 CallbackSaveOrchestrator 在随后的 status=6 回调中完成（发起与落盘异步解耦）。

**Input / Output：**
- `forceSave(kbId: string, 必填; fileId: string, 必填) → Map{result: enum{initiated, no-changes, no-session, no-file, disabled, failed}, ooError?: int, message?: string}`〔证据：OnlyOfficeServiceImpl.java:381-425、OnlyOfficeService.java:60-68 ‹CERTAIN›〕

**Error modes（以 result 值表达，非异常）：**
- E_OO_DISABLED — 触发：enabled=false。恢复：result=disabled。〔:383-386 ‹CERTAIN›〕
- E_FILE_NOT_FOUND — 触发：文件查无。恢复：result=no-file。〔:387-393 ‹CERTAIN›〕
- E_COMMAND_FAILED — 触发：CommandService HTTP≥400 / 网络异常（10s/30s 超时）/ 响应不可解析（保守取 -1）。恢复：result=failed（附 ooError 或 message）。〔:414-423,449-458 ‹CERTAIN›〕
- OO 语义码映射（非错误）：error 0→initiated / 4→no-changes（会话存在但无修改）/ 1→no-session（key 无活跃会话）/ 其他→failed。〔:410-418 ‹CERTAIN›〕

**Invariants:**
- Pre：编辑者角色已由控制器裁决——viewer 调用被 403 语义拒绝，不到达本契约。〔OnlyOfficeController.java:107-110 ‹CERTAIN›〕
- Post：命令 key 恒为「activeSessionKeys 登记值优先，无登记时按当前 updatedAt 派生退化值」；jwtEnabled 时命令必附 60 秒有效签名 token（payload 镜像命令参数 `{c:"forcesave", key}`）。〔:396-407 ‹CERTAIN›〕
- Post：本契约成功（initiated）仅表示 OO 受理命令——文件内容变更必然经由 status=6 回调编排落地，本契约不触碰 MySQL/MinIO/MQ。〔OnlyOfficeService.java:59-67 javadoc ‹CERTAIN›〕

**Parent Trace:** 接口 onlyoffice-editing.ForceSaveCommander → Design.ForceSaveCommander → Spec REQ-C01~C04。

---

## ai-chunking

> 对应 design 模块：ParagraphModelAssembler / AutoChunkStrategy / RegionExtractor / LayoutAnalysisOrchestrator / ApplyOrchestrator。运行时数据结构（段落对齐模型、应用请求/结果、SSE 事件面、MinIO 派生对象键）见 data.md `## ai-chunking` 节——本特性**无自有数据表**，落库复用 kb_chunk（数据契约归 `## chunk-management` 节，本链为其「替换路」写入方之一）。
> 端点排除：AiChunkController 5 端点（stream/layout 两个 SSE + preview/apply/image-ocr）是 AiChunkWorkflowModule 的外部面，按端点排除铁律不立契约（HTTP/SSE 暴露面见 design/ai-chunking.md §2）。SSE 是**系统边界**：前端以原生 fetch + ReadableStream 直连消费、绕过 axios 拦截层（AiChunkController.java:41 javadoc ‹CERTAIN›），事件协议属前后端边界契约，在 data.md 以事件表登记、本节不另立接口。
> 上游分工（正式声明）：① 解析归 `## file-ingestion` 节 DocumentParser——本链恒以 null 策略调用以跳过 LLM Markdown 增强（AiChunkServiceImpl.java:892-893 ‹CERTAIN›），其错误模式不重复定义；② 落库写面复用 `## chunk-management` 节 ChunkStoreService（单层模式），本链只定义「替换编排」语义；③ 图谱清理经 GraphService.deleteFileGraph（knowledge-graph 域服务接口），本链不重复其错误模式。

### ai-chunking.ParagraphModelAssembler（段落对齐模型构建契约）

**Purpose:** AI分片 单一事实源（段落对齐模型，CONTEXT.md 词条）的唯一构建点：把解析产物装配为带稳定 id、类型、页码、章节路径的段落序列，修复页边界割裂（结构级跨页合并），并为 PDF 段落回填归一化坐标与锚定行文本——左侧原件片段、右侧分片卡、应用落库三方都以它的产出为对齐依据。

**Input / Output（按方法）:**
- `buildParagraphs(parseResult: ParseResult, 必填; extension: string, 必填) → AiChunkParagraph[]` — DocNode 树优先（标题栈维护 headingPath；HEADING/TABLE/CODE/LIST/IMAGE/MATH 各映射段落类型），无节点时按 PAGE_BREAK 标记 + 空行分段回退〔证据：AiChunkServiceImpl.java:901-978 ‹CERTAIN›〕
- `mergeCrossPage(paragraphs: AiChunkParagraph[], 必填按文档顺序) → void（就地变异）` — 段落/表格/代码三类型跨页合并 + 全序列重排〔:1028-1058 ‹CERTAIN›〕
- `fillPdfRects(file: KbFile, 必填; paragraphs, 必填) → RectItem[]` — 非 PDF 返回空集；PDF 副作用回填每段 rects（归一化 0~1 顶左）与 anchorText，返回值为内容图片位置盒（imageBoxes 旁路，不进段落模型）〔:1623-1764 ‹CERTAIN›〕

**Output:** 段落字段面见 data.md `## ai-chunking` 节段落对齐模型条。

**Error modes:**
- E_PARAGRAPH_EMPTY — 触发：解析产物无任何可装配段落。恢复：业务错误「文档解析结果为空，无法 AI分片」终止工作流。〔AiChunkServiceImpl.java:974-976 ‹CERTAIN›〕
- D_RECT_ANCHOR_MISS（降级）— 触发：段落在某页锚定失败（继承标题/OCR 文本差异/行盒为空）。恢复：连续未锚定段按「上/下锚点区间 × 预估行数比例」分兜底横带（x 固定 0.03~0.97 带）；整页无行盒则该页无框。〔:1664,1701-1739 ‹CERTAIN›〕
- D_RECT_PIPELINE_FAILED（降级）— 触发：坐标装配整体异常（下载/解析失败）。恢复：warn 记账，返回空 imageBoxes、段落无 rects（预览降级为无框模式）。〔:1760-1763 ‹CERTAIN›〕

**Invariants:**
- Pre：mergeCrossPage 前段落序列已按文档顺序装配；仅相邻且后段首页 > 前段末页时尝试合并。〔:1033 ‹CERTAIN›〕
- Post：合并判定保守——段落：前段页尾句末无终止符（。！？；.!?;）且后段非 `#`/`-`/`•` 开头才拼；表格：列数一致才并（表头重复丢续表表头、续表仅剩表头行不并）；代码：直接行拼。标题永不回并。〔:1060-1101,130 ‹CERTAIN›〕
- Post：合并后全序列 order/id（p_%04d）重排、pages 升序去重、page/crossPage/pageRange 由 pages 派生——段落 id 在合并后稳定且全局唯一。〔:1044-1055 ‹CERTAIN›〕
- Post：PDF rects 归一化除数恒为 cropBox 宽高（文本/图片坐标同为 cropBox 相对，与前端 pdf.js 渲染同基准）；锚定采用两指针内容匹配（行模糊相等或 ≥6 字符子串包含），孤儿行吸收须逐行内容匹配、禁止纯按位置回退。〔:1652-1656,1775-1834,1853-1857 ‹CERTAIN›〕
- Post：anchorText 仅在锚定成功段落非空；行文本来自已清理页眉脚的行链路——所见即所得内容源。〔:1690-1699 ‹CERTAIN›〕

**Parent Trace:** 接口 ai-chunking.ParagraphModelAssembler → Design.ParagraphModelAssembler（缝 2）→ Spec REQ-A02~A05、B01~B05。

### ai-chunking.AutoChunkStrategy（自动分组三级降级契约）

**Purpose:** 「建议边界」的自动产出器：LLM 语义分组为主、Embedding 相似度回退、长度累积兜底，三级共享同一收尾约束（chunkLength 硬上限 + 迷你组回收 + 统一编号），保证无论哪级胜出，分片卡拿到的是同一形态的建议边界，并如实标注降级路径供前端提示。

**Input / Output:**
- `autoChunk(kb: KnowledgeBase, 必填可 null 字段; file: KbFile, 必填; paragraphs: AiChunkParagraph[], 必填) → AutoChunkOutcome{groups: AiChunkGroup[], source: "llm"|"embedding"|"rule"}`〔证据：AiChunkServiceImpl.java:1121-1151 ‹CERTAIN›〕
- 参数来源：chunkLength/semanticThreshold 经 StrategyConfigResolver.resolve(file.parseStrategyId) 三层合并获取（衔接 `## parse-strategy` 节），算法自身不读策略表。〔:1125-1127 ‹CERTAIN›〕

**Output:** AiChunkGroup{id="chunk_{index}", index, source, paragraphIds[], text（段落 "\n\n" 拼接）, pageRange}；段落侧 chunkId 已回填（_sN/_oN 衍生片回填基段）。〔:1547-1576 ‹CERTAIN›〕

**Error modes（全为降级，无上抛错误模式）:**
- D_LLM_GROUP_INVALID（降级）— 触发：LLM 输出 JSON 不可解析 / 覆盖不全 / 编号重复 / 组间乱序 / 调用异常。恢复：warn 记账降级 embedding。〔:1132-1136,1240-1273 ‹CERTAIN›〕
- D_EMBED_GROUP_FAILED（降级）— 触发：无 embedding 模型配置 / 调用失败 / 返回向量数 ≠ 段数。恢复：warn 记账降级 rule。〔:1140-1147,1301-1303 ‹CERTAIN›〕
- D_MODEL_UNRESOLVED（降级）— 触发：模型 code 在模型表无 status=online 记录。恢复：resolveRecord 返回 null、apiUrl/apiKey 以 null 下传（由 AI 网关按默认路由处理），不中断。〔:1582-1592 ‹CERTAIN›；null 下传后的网关行为属 fastrag-ai 内部〕

**Invariants:**
- Pre：模型选择序固定——kb.graphLlmModel（LLM 级）→ kb.embeddingModel（Embedding 级）→ rule（无模型依赖兜底），逐级失败才降级。〔:1130-1150 ‹CERTAIN›〕
- Post：LLM 分批双约束（段数 ≤60 且截断后单段 200 字符计总 ≤9000）且每批附下一批开头两段作语义衔接参考（prompt 明令禁止编入输出）。〔:116-125,1190-1210 ‹CERTAIN›〕
- Post：chunkLength 为硬上限——超限组按段界装箱，单段超长按句界（正文/列表）/行界（代码）二次切分且片段 id 加 `_sN` 后缀；**表格原子不可切**（超长保持完整允许超限，代码注释明示覆盖设计文档 §6.3）；标题/图片原子。〔:1407-1460 ‹CERTAIN›〕
- Post：回退路径（embedding/rule）仅长度断片携带尾部 overlap（默认 100 字符、句界回退、有效重叠 <20 字符放弃、表格/代码尾部不参与）；语义断点处不重叠。〔:121-122,1324-1330,1367-1396 ‹CERTAIN›〕
- Post：迷你组（< min(300, chunkLength) 纯文本组）优先向后合并、装不下并入前一组；表格/代码/图片组永不参与合并。〔:119-120,1512-1545 ‹CERTAIN›〕

**Parent Trace:** 接口 ai-chunking.AutoChunkStrategy → Design.AutoChunkStrategy（缝 1、缝 3）→ Spec REQ-C01~C07；参数上游 parse-strategy.StrategyConfigResolver（REQ-G01~G03）。

### ai-chunking.RegionExtractor（区域框选内容提取契约）

**Purpose:** 用户在原件渲染图上框选区域后的内容提取器：结构化文本优先（精确文本零 OCR）、内容图片直达分片（裁原图 + 一次 OCR 图注）、整区域多级重试 OCR 兜底——保证任何形态的页面（文字层/图片/扫描/纯图形）框选都不空手而归，是手动分片与图片分片的输入手段。

**Input:**
- `ocrImageRegion(kbId: string, 必填; fileId: string, 必填; page: int 1-based, 必填; x/y/width/height: float, 必填——归一化 0~1 顶左原点，与 preview imageBoxes 同坐标系)`〔证据：AiChunkService.java:38-51、AiChunkController.java:84-89 ‹CERTAIN›〕

**Output:** `Map{blocks: [{type: "heading"|"paragraph"|"table"|"image", text: string, imageKey?: string}]}`——表格为 `\t` 行拼接、图片块携 MinIO imageKey 供分片卡渲染。〔AiChunkServiceImpl.java:458、AiChunkService.java:48 ‹CERTAIN›〕

**Error modes:**
- E_FILE_NOT_FOUND — 触发：fileId 在该 kbId 下查无。恢复：业务错误 404 语义「文件不存在」。〔:881-887 ‹CERTAIN›〕
- E_DOWNLOAD_FAILED — 触发：MinIO 下载异常。恢复：业务错误「文档下载失败：…」。〔:251-255 ‹CERTAIN›〕
- E_PAGE_OUT_OF_RANGE — 触发：page <1 或 > 总页数 / 页面尺寸无效。恢复：业务错误提示。〔:256-264 ‹CERTAIN›〕
- E_REGION_EXTRACT_FAILED — 触发：提取管线未分类异常。恢复：业务错误「区域内容提取失败：…」（BusinessException 原样穿透）。〔:459-464 ‹CERTAIN›〕
- D_REGION_IMAGE_UPLOAD_FAILED（降级）— 触发：裁剪图上传 MinIO 失败。恢复：返回 null 跳过该图，文本块不受影响。〔:490-502 ‹CERTAIN›〕

**Invariants:**
- Pre：坐标换算感知 /Rotate 90/270（视觉宽高交换）且以 cropBox 为基准——旋转页与裁剪坐标同视觉空间，防错位裁剪。〔:266-277,504-525 ‹CERTAIN›〕
- Post：三级路径按序尽力——①文字层行命中（行中心入区、水平覆盖 ≥40% 行宽、页眉脚边缘带 7%/93% 排除、区域内有标题行则从首标题行截断）→ 结构化块；②内容图片相交 ≥30% 图片面积 → 完整图框裁剪原图 + 一次 OCR 图注（识别空/失败保持纯图），扫描底图（≥85% 页宽高）跳过；③前两级皆空 → 整区域渲染重试 OCR（220 原图→300 原图→300 反相→300 红章增强），OCR 输入增强但展示/上传恒用原图。〔:283-352,356-403,411-447 ‹CERTAIN›〕
- Post：路径③ OCR 全部失败 → 返回「区域截图 + 占位说明」image 块，不返回空 blocks 空手而归；块序列按页面纵坐标穿插排序。〔:405-409,448-455 ‹CERTAIN›〕
- 已知偏离（登记债务）：路径②图片 OCR 失败仅 warn、无增强重试（有意取舍：内容图以视觉信息为主）；debug 日志 `[ocr-dbg]` 常驻 info 级输出。〔:390-394,359-360 ‹CERTAIN›〕

**Parent Trace:** 接口 ai-chunking.RegionExtractor → Design.RegionExtractor（缝 1）→ Spec REQ-D01~D03、D07。

### ai-chunking.LayoutAnalysisOrchestrator（版面分析编排契约）

**Purpose:** 原件渲染分块画框数据源的唯一生产者：按「有文字层走 PDFBox 几何聚类（零 VLM 成本）、扫描/图片页走 VLM」分层路由逐页分析，结果按文件缓存（引擎版本失效）——版面与分片无关、文件不变即有效，每个文件至多生成一次。

**Input / Output:**
- `streamLayout(kbId: string, 必填; fileId: string, 必填) → SseEmitter`（layout 事件逐页推送块数组 + done 收尾；事件面见 data.md）〔证据：AiChunkServiceImpl.java:575-734 ‹CERTAIN›〕
- 块产出：AiChunkLayoutBlock{page 1-based, type ∈ title|text|table|image|code|formula|caption（VLM 白名单）, x/y/width/height 归一化 0~1 顶左, text ≤120 字符摘要}〔AiChunkLayoutBlock.java:19-34、PdfLayoutAnalyzer.java:14-27 ‹CERTAIN›〕

**Error modes:**
- E_LAYOUT_FAILED — 触发：文件下载/PDF 加载等整体异常。恢复：error 事件（message）+ emitter.completeWithError。〔:721-731 ‹CERTAIN›〕
- D_LAYOUT_PAGE_FAILED（降级）— 触发：单页几何聚类或 VLM 分析异常。恢复：warn 记账跳过该页、进度继续；全部页成功才写缓存，部分失败不落盘（下次打开整份重算）。〔:658-660,694-697,710-715 ‹CERTAIN›〕
- E_LAYOUT_UNSUPPORTED（非错误终态）— 触发：非 PDF 扩展名。恢复：done 事件携 `unsupported:true` 正常收尾。〔:593-599 ‹CERTAIN›〕

**Invariants:**
- Pre：判层阈值 200 字符（TEXT_LAYER_CHARS）——低于视作扫描/图片页归 VLM；几何与 VLM 并存时 PDDocument 非线程安全以「几何单线程、VLM 每线程独立 load 副本」隔离，VLM 并发上限 5（信号量 + 固定线程池），事件发送经 emitter 级 synchronized。〔:110,615-616,664-666,736-741 ‹CERTAIN›〕
- Post：缓存键 `{kbId}/{fileId}/layout.json`、载荷携版本号 v=LAYOUT_CACHE_VERSION(3)；版本不符/结构损坏/读取异常一律视为未命中重算。〔:113,712-714,744-764 ‹CERTAIN›〕
- Post：几何链路先做跨页页眉脚/页码行清理（数字掩码频次 ≥30% 页 + 边缘区判定），分类优先级 table > code > title > toc > text；表格边缘漏行迭代吸收进相邻表格块；紧邻图片的短文本块标 caption（表格邻接豁免）。〔:637,164-195,443-500,504-521 ‹CERTAIN›〕
- 已知偏离（登记债务）：缓存有效性以「文件不变」为口径但缓存键不含文件 updatedAt——原件被替换后旧缓存仍命中，靠前端重新触发或版本号演进兜底‹CERTAIN（2026-09-24 深证）：layoutCacheKey 仅拼接 kbId/fileId（AiChunkServiceImpl.java:744-746）；replaceOriginalFile 原地覆盖同 objectKey 仅更新 updatedAt（FileServiceImpl.java:294,303,309），OnlyOffice 保存（OnlyOfficeServiceImpl.java:322）后旧缓存必然仍命中——债务实证成立，非推断›。

**Parent Trace:** 接口 ai-chunking.LayoutAnalysisOrchestrator → Design.LayoutAnalysisOrchestrator（缝 1）→ Spec REQ-D04~D06。

### ai-chunking.ApplyOrchestrator（应用落库替换编排契约）

**Purpose:** 「用户确认的分片边界为唯一事实源」（CONTEXT.md AI分片 词条）的执行面：把请求体里的分片列表直接变成「MySQL 行 + Milvus 向量 + parsed 派生产物 + 文件计数」一致的新状态——不调用任何分片器、不重新解析原文件；替换采用与 Re-chunk 同款「删 auto 留 manual」口径，是 kb_chunk 替换路的第二写入方（与 chunk-management.RechunkOrchestrator 语义对齐、实现独立）。

**Input:**
- `apply(kbId: string, 必填; fileId: string, 必填; request: AiChunkApplyRequest{chunks: Item[]}, 必填) → int（落库分片数）`；Item{paragraphIds: string[], 可空——审计追溯; text: string, 必填非空白——最终文本可经用户编辑; source: "auto"|"manual", 可空缺省 auto; pageRange: string, 可空}〔证据：AiChunkServiceImpl.java:770-780、AiChunkApplyRequest.java:14-28、AiChunkController.java:70-73 ‹CERTAIN›〕

**Output:** kb_chunk 新 auto 行（id=`{fileId}_chunk_{i}` 按请求序、chunkType=text、embedding_id=id）、Milvus 向量、MinIO parsed.md/parsed.json、kb_file 状态对账。〔:843-874 ‹CERTAIN›〕

**Error modes:**
- E_FILE_NOT_FOUND — 触发：fileId 在该 kbId 下查无。恢复：业务错误 404 语义。〔:881-887 ‹CERTAIN›〕
- E_APPLY_STATE_CONFLICT — 触发：文件在回收站 / status=processing / processing_mode=qa。恢复：对应业务错误拒绝（请先恢复/等待完成/不支持）。〔:784-792 ‹CERTAIN›〕
- E_APPLY_EMPTY — 触发：chunks 为 null/空 或任一 text 空白。恢复：业务错误「分片列表为空…」/「存在内容为空的分片…」。〔:793-800 ‹CERTAIN›〕
- E_APPLY_IN_PROGRESS — 触发：同文件并发 apply（进程内互斥表命中）。恢复：业务错误提示等待刷新、勿重复提交。〔:772-774 ‹CERTAIN›〕
- D_APPLY_AUX_DEGRADED（降级）— 触发：Milvus 定向删向量 / deleteFileGraph / manual graphIndexed 重置 / parsed 产物写入任一失败。恢复：warn 记账继续，MySQL 主流程不回滚（vector_stored 与图谱增量构建补救）。〔:820-841,2013-2015 ‹CERTAIN›〕

**Invariants:**
- Pre：五重准入（存在/未软删/非 processing/非 QA/分片非空且内容非空白）全过才动手；互斥在准入之前。〔:771-800 ‹CERTAIN›〕
- Post：替换口径恒为「删 origin≠manual 行 + 按 auto 分片 embeddingId 定向删 Milvus 向量（不用 deleteByFileId）+ deleteFileGraph 整文件清空 + 保留 manual 行/ID/向量并重置 graphIndexed=0/extraction_result=null」——与 RechunkOrchestrator 的清理面对齐。〔:804-841 ‹CERTAIN›〕
- Post：请求分片即最终分片：id 按请求顺序 `{fileId}_chunk_{i}` 从 0 编号、chunkType 恒 text、单层 storeChunks（无父子装配）——请求序即落库序。〔:845-855 ‹CERTAIN›〕
- Post：成功后 kb_file.status=completed/progress=100/stage 清空/chunk_count = 本次落库数 + 保留 manual 数（排除 parent）；parsed.md = 分片文本按序 "\n\n" 拼接、parsed.json 携 fileId/appliedAt/chunks[].{id,index,source,pageRange,paragraphIds} 审计面。〔:860-871,1986-2012 ‹CERTAIN›〕
- 互斥语义（重点声明）：APPLYING_FILES 为进程内 ConcurrentHashMap——多实例部署下互斥失效‹CERTAIN（2026-09-24 深证）：static 进程内 Map（AiChunkServiceImpl.java:768），:772 putIfAbsent/:778 finally remove，无跨实例协调手段；单实例内前端超时重试的并发 apply 会撞主键/污染向量为注释载明实证（:766-767）——本互斥为单实例债务性设计，多实例部署前须换持久化/分布式锁（登记债务）›。

**Parent Trace:** 接口 ai-chunking.ApplyOrchestrator → Design.ApplyOrchestrator → Spec REQ-E01~E06；替换口径衔接 chunk-management.RechunkOrchestrator（REQ-F01~F06 同款）、落库写面 chunk-management.ChunkStoreService（REQ-E01/E05）、图谱清理 knowledge-graph 域服务。

---

## retrieval

> 对应 design 模块：RetrievalSearchEngine / QueryEnhancePipeline / RetrievalLogModule / UpdateRemindModule。数据实体契约见 data.md `## retrieval` 节。
> 端点排除说明：`POST /api/retrieval/search` 与图谱/查询增强直达端点是本特性对前端的**外部面**（RetrievalController.java:41-58），契约写给其背后的四个服务接口；RetrievalController 15 端点的暴露面清单见 design/retrieval.md §2 与控制器 javadoc（:9-26）。

### retrieval.RetrievalSearchEngine（混合检索编排契约）

**Purpose:** RAG「读」半边的总闸：把「知识库 ID + 查询 + 可选配置」折叠为一份去重、排序、上下文完整的检索结果——六通道召回、三策略融合、后处理、上下文组装、逐通道降级与日志记账全部藏在一次 search 调用之后；app-center 对话、benchmark 评测、前端检索页共用此面。

**Input / Output（按方法）:**
- `search(request: RetrievalRequest{knowledgeId: string, 必填—业务上; query: string, 必填非空白—空则空结果; config: RetrievalConfig, 可空—逐字段可空覆盖}, 必填) → List<SearchResultItem>`〔证据：RetrievalService.java:15、RetrievalRequest.java:24-28、RetrievalServiceImpl.java:133-147 ‹CERTAIN›〕
  - RetrievalConfig 全字段面（均可空/可覆盖，mode: fulltext/vector/hybrid）：检索模式三元组（mode/topK/similarityThreshold）、预处理开关四项（enableAutoCorrection/enableQueryRewrite/enableGraphExpand/enableSynonymExpansion）、NER 与图谱参数（graphExpandDepth/graphMaxEntities/nerModel）、QA 与 Multi-Query（enableKeywordMatch/enableMultiQuery/multiQueryCount/multiQueryModel）、多路召回（enableMultiRetrieval/vectorRecallCount/fulltextRecallCount/graphRecallCount/qaRecallCount/fusionStrategy: rrf|weighted|interleave）、BM25（bm25RecallCount/vectorWeight/bm25Weight/bm25SparseDropRate）、重排（enableRerank/rerankModel/enableLLMRerank/enableMMR/mmrLambda）、上下文组装（contextAssemblyStrategy: concat|parent_chunk|parent_document|window|auto/contextWindowSize/maxContextTokens/contextOrder）、条款召回（enableClauseRecall/clauseWindowSize/clauseRecallCount）〔RetrievalRequest.java:30-80 ‹CERTAIN›；bm25SparseDropRate 仅存储未消费〔grep 实测 ‹CERTAIN›——字段契约断裂债务〕〕
  - SearchResultItem{index, similarity, distance, content, source: mysql|milvus|mysql_fulltext|graph|qa, channel: vector|fulltext|graph|qa|clause|clause_neighbor|title, fileId, fileName, chunkType, imageKeys, chunkIndex, parentId, highlights, previewSnippet}〔SearchResultItem.java:27-42 ‹CERTAIN›〕
- `getChunkCount(kbId: string, 必填) → long`（排除软删文件分片与父分片）〔RetrievalServiceImpl.java:263-278 ‹CERTAIN›〕

**Error modes:**
- E_NO_KB_ACCESS — 触发：当前用户（非超管、非 api-token）在 Redis 无 `kb:acl:{kbId}:{userId}` 记录。恢复：403 语义业务错误「无知识库访问权限」，检索不执行。〔RetrievalServiceImpl.java:113-124 ‹CERTAIN›〕
- 约定：**任何通道失败都不是错误模式**——向量失败降级 LIKE（:613-616）、FULLTEXT 失败降级 LIKE（:648-654）、图谱整路失败置 degraded 空回退（:174-191,783-788）、Rerank/MMR 失败回退 topK 截断（:1184-1187,1263-1266）、通道异常经 safeSearch 空列表（:1689-1696），全部 warn 记账后返回部分结果。〔‹CERTAIN›〕

**Invariants:**
- Pre：query 空白 ⇒ 直接空列表，不触碰任何通道（:144-147）。
- Pre：配置三级合并完成前不发起任何召回；合并协议 = 非 null 才覆盖（topK 另需 >0；similarityThreshold 的 0.0 是合法值不被当 null）〔:284-312,372-425 + RetrievalServiceImplTest.java:134-153 ‹CERTAIN›〕。
- Post：全部召回通道排除 chunkType='parent' 行；父分片只以上下文放大形态出现（buildResultItem 来源行均带 .ne(chunkType,"parent") 或父块替换，:277,770,909,1717 ‹CERTAIN›）。
- Post：融合去重键恒为 fileId+"_"+chunkIndex，融合分只排序不覆盖各路原始 similarity/distance〔:1032-1046 + 测试 :335-339 ‹CERTAIN›〕。
- Post：结果按 index 从 0 连续编号、fileName 按 fileId 批量回填、同内容（前 500 字符+长度键）只留 similarity 最高一条〔:249-252,1668-1686 ‹CERTAIN›〕。
- Post：每次 search（含空结果）写 kb_retrieval_log + kb_log(retrieval) 双行，失败仅 warn〔:1738-1783 ‹CERTAIN›〕。

**Parent Trace:** 接口 retrieval.RetrievalSearchEngine → Design.RetrievalSearchEngine（缝 1/2/4/5）→ Spec REQ-A01~A07、REQ-B01~B13、REQ-C01~C09。

### retrieval.QueryEnhancePipeline（查询增强契约）

**Purpose:** 检索质量的查询侧杠杆：把「原始查询」增强为纠错后/扩展后/改写后的查询、实体列表或变体列表——五种策略一个服务面，LLM/NER/降级分词的取舍对调用方不可见；同义扩展执行「仅消费启用词条」的术语治理口径。

**Input / Output（按方法）:**
- `suggest(query: string) → {suggestedQuery: string, reason: string}`（占位：原样返回）〔QueryEnhanceServiceImpl.java:69-75 ‹CERTAIN›〕
- `expandSynonyms(query: string) → {expandedQuery: string, matchedTerms: List, addedTerms: List<string>}`——仅消费 term_record.status=1 词条；query 含 term 补别名、含别名补术语；追加词空格拼接、原查询不动〔:78-89 + TermServiceImpl.java:27-34,138-176 ‹CERTAIN›〕
- `applyQueryRules(query: string) → {rewritten: string, appliedRules: List}`（占位：原样返回、appliedRules 恒空——query_rule 表零消费，执行引擎缺失）〔:91-97 ‹CERTAIN›〕
- `expandGraph(kbId: string, 必填—做 ACL; query: string, 必填非空白—空则短路; depth: int; maxEntities: int[, nerModel: string, 可空—空则降级分词]) → {originalQuery, expandedQuery, entities: List<Map>, relations: List<Map>}`〔QueryEnhanceService.java:25-26、QueryEnhanceServiceImpl.java:314-362 ‹CERTAIN›〕
- `expandQueries(query: string, count: int（2~5 截断）, model: string, 可空=default 网关; kbId: string, 可空) → List<string>`（至少含原查询）〔QueryEnhanceService.java:37、QueryEnhanceServiceImpl.java:110-182 ‹CERTAIN›〕

**Error modes:**
- E_NO_KB_ACCESS — 触发：expandGraph 直达调用时 KB ACL 校验不过（与检索同键同规则）。恢复：403 语义。〔QueryEnhanceServiceImpl.java:56-67,329 ‹CERTAIN›〕
- 约定：NER 失败/无模型 → 降级 n-gram 分词（token ≤20）；LLM 变体改写失败 → 单元素 [原查询]；**均不抛错**（降级链是行为不是错误）〔:194-302,177-181 ‹CERTAIN›〕。

**Invariants:**
- Pre：expandGraph 空查询短路返回四键结构（entities/relations 空列表）〔:323-326 ‹CERTAIN›〕。
- Post：expandSynonyms 不修改原查询语义（追加式 expandedQuery = query + " " + addedTerms）〔:80-83 ‹CERTAIN›〕。
- Post：expandQueries 返回列表恒含原查询且去重（LinkedHashSet）、变体长度 2~100〔:156-175 ‹CERTAIN›〕。
- Post：降级分词恒保留英文整词（≥2 字符）、≤4 字中文块原样、长中文块补 2~4 字 n-gram（每块 ≤8 个）〔:272-302 + QueryEnhanceServiceImplTest.java:49-82 ‹CERTAIN›〕。
- 债务：suggest/applyQueryRules 占位行为冻结于契约（接口面稳定，引擎后补不破契约）。

**Parent Trace:** 接口 retrieval.QueryEnhancePipeline → Design.QueryEnhancePipeline（缝 1/3）→ Spec REQ-D01~D07；术语消费口径衔接 platform-config（term_record）。

### retrieval.RetrievalLogModule（检索日志与分析契约）

**Purpose:** 检索效果的可观测面：把每次检索折叠为两行记账（效果指标 + 审计），把「哪些查询没结果、延迟多少」折叠为组织隔离的读取面与聚合统计——检索质量运营的唯一数据入口。

**Input / Output（按方法）:**
- `log(entry: KbRetrievalLog, 必填) → void`；`update(entry: KbRetrievalLog{id 必填}) → void`〔RetrievalLogService.java:13-14、RetrievalLogServiceImpl.java:54-62 ‹CERTAIN›〕
- `page(kbId: string, 可空—空则按可访问集合; hasResult: Boolean, 可空; page: int; pageSize: int) → PageResult<KbRetrievalLog{list,total,page,pageSize}>`（createdAt 降序）〔:64-84 ‹CERTAIN›〕
- `analysis(kbId: string, 可空) → Map{totalQueries, noResultCount, noResultRate(百分比 2 位), avgLatencyMs, avgHitCount, topQueries[10], noResultQueries[10]}`〔:86-125 ‹CERTAIN›〕

**Error modes:**
- 无显式错误模式——无权限/越权/无数据一律**空值语义**：指定库不可达返回空页/零值结构（不泄露库存在性），可访问集合为空短路空页，api-token（userId 前缀 `api-token:`）返回 null 表示不过滤〔:46-52,68-79,91-98,127-138 ‹CERTAIN›〕。

**Invariants:**
- Pre：page/analysis 依赖请求上下文（SecurityUtil.getCurrentUser），无认证上下文时空结果（user==null → 空集合）〔:47-52 ‹CERTAIN›〕。
- Post：可访问集合 = KbAccessChecker.getAccessibleKbIds(userId, orgId)，组织隔离与 iam.KbAccessDecisionModule 同源〔:51 ‹CERTAIN›〕。
- Post：统计口径三条 SQL（aggregateStats/selectTopQueries/selectNoResultQueries）与分页同用 kbId/kbIds 双参过滤〔KbRetrievalLogMapper.java:31-62 ‹CERTAIN›〕。
- 已知偏离（登记债务）：log/update 写入口无权限校验——外部可补记任意库日志〔RetrievalController.java:67-68 + RetrievalLogServiceImpl.java:54-62 结构观测 ‹CERTAIN›〕。

**Parent Trace:** 接口 retrieval.RetrievalLogModule → Design.RetrievalLogModule → Spec REQ-E01~E06。

### retrieval.UpdateRemindModule（更新提醒契约）

**Purpose:** 文档新鲜度的配置与触发面：每库一条提醒配置（开关+cron），触发时回答「自上次提醒以来这个库更新了多少」——把「内容可能过时」变成一个可查询的数字。

**Input / Output（按方法）:**
- `list(kbId: string, 可空过滤) → List<KbUpdateRemind>`（createdAt 降序）；`get(kbId: string) → KbUpdateRemind|null`（首条）〔UpdateRemindService.java:13-14、UpdateRemindServiceImpl.java:31-38 ‹CERTAIN›〕
- `save(remind: KbUpdateRemind{kbId 必填—业务上; enabled, cronExpr, channels 可空}) → KbUpdateRemind`——id 空为新增（缺省补 enabled=1、cronExpr="0 9 * * *"），非空为更新〔:39-47 ‹CERTAIN›〕
- `delete(id: string) → void`〔:47 ‹CERTAIN›〕
- `remind(kbId: string) → {kbId, enabled, lastRemindAt, cronExpr, updateCount: long, message}`——触发统计并刷新 lastRemindAt〔:48-73 ‹CERTAIN›〕

**Error modes:**
- 无显式错误模式——无配置时 remind 返回默认状态（enabled=false、cronExpr 默认、updateCount 全量计数），不报错〔:49-55,64-69 ‹CERTAIN›〕。

**Invariants:**
- Post：updateCount 统计口径 = kb_update_log 中 kb_id 匹配且 timestamp > lastRemindAt（无 lastRemindAt 则不设时间下限）〔:57-70 ‹CERTAIN›〕。
- Post：remind 调用有副作用——配置存在时 lastRemindAt 必被刷新为 now（拉取式触发的计量基点）〔:71 ‹CERTAIN›〕。
- 债务：cronExpr 仅存储回显，无调度器消费（定时推送未实现，grep 全后端无 @Scheduled 消费）〔‹CERTAIN›〕。

**Parent Trace:** 接口 retrieval.UpdateRemindModule → Design.UpdateRemindModule → Spec REQ-F01~F04。

---

## knowledge-graph

> 对应 design 模块：GraphStoreModule / GraphExtractionModule / GraphQueryServiceModule。数据契约见 data.md `## knowledge-graph` 节。
> 端点排除：GraphController 15 端点（`/api/kb/{kbId}/graph` data/stats/search/labels/index+build+retry+replay+build-status/file 删除/rank/settings 读写/merge-candidates/merge）是 GraphManagementModule 的外部面，按端点排除铁律不立契约（HTTP 暴露面见 design/knowledge-graph.md §2）；GraphService 门面方法与端点一一镜像，亦不另立。
> 上游分工（正式声明）：① 构建触发裁决（enable_graph_build 开关解析、摄取完成/分片编辑投递消息）归 `## file-ingestion` / `## chunk-management` 节，本节只定义消费消息后的构建行为；② 检索侧 graph 通道消费（expandGraph 的检索封装、graphHits 降级记账）归 `## retrieval` 节，本节只定义 `expandGraph` 原语本身；③ kb_chunk.graph_indexed / extraction_result 两列的字段契约归 `## chunk-management` 节，本节登记图谱侧读写口径。

### knowledge-graph.GraphStore（图存储引擎契约）

**Purpose:** 图数据的唯一读写口：把「同义实体跨单元/跨批次合并为同一节点」「删除后图谱不残留」「证据链（mention）完整」这些图谱正确性要求翻译为与调用方无关的存储操作——构建消费者、查询门面、PPR 排序器都只认本接口，不感知 Neo4j 方言（ADR-0002：唯一实现 Neo4jGraphStore，MySQL 实现已删除）。

**Input / Output（按方法族）:**
- 基础 CRUD：`createEntity(kbId, entityId, name, normalizedName, type[, description, attributes]) → void`（幂等 MERGE：节点唯一身份 = (kbId, normalizedName)；类型计票多数胜出、占位类型 UNKNOWN/Entity 无投票权、描述非空择长、attributes 首写保留）〔证据：Neo4jGraphStore.java:84-144,146-149 ‹CERTAIN›〕
- `createRelation(kbId, tripleId, sourceId?, source, sourceNorm?, targetId?, target, targetNorm?, label, content) → void`（端点按归一化名 MERGE，端点不存在创建无类型占位节点；边去重键 = (kbId, 端点对, label)，tripleId/content 端 ID 缺失时回填不清空既有值）〔:178-210 ‹CERTAIN›〕
- 查询：`getGraphData(kbId, maxNodes, excludeChunks) → {nodes[{id,name,entity_type,description,attributes}], edges[{id,source_id,target_id,source,target,label}]}`（节点按 RELATION 度数降序截断；excludeChunks=false 附加 Chunk 节点与 MENTIONS 边）〔:256-384 ‹CERTAIN›〕
- `expandGraph(kbId, entities, depth(1~2 钳制), maxEntities) → {entities[{id,name,entity_type,mentionCount}], relations, expandedQuery}`（实体名互为子串模糊匹配、mentionCount 加权）〔:387-511 ‹CERTAIN›〕
- `searchNodes(kbId, keyword, maxNodes) → {nodes, edges}`（name/normalizedName/entityType/description 四字段正则模糊）〔:514-580 ‹CERTAIN›〕；`getLabels(kbId) → string[]`（类型去重排序）〔:583-600 ‹CERTAIN›〕；`countEntitiesByType(kbId) → Map<type,count>`〔:603-622 ‹CERTAIN›〕
- `findEntityId(kbId, normalizedName) → string|null`（跨 chunk 引用解析，同名取任意一条）〔:213-227 ‹CERTAIN›〕；`listEntityMentions(kbId) → [{entityId, chunkId}]`（PPR 数据源，读 MENTIONS 边）〔:230-251 ‹CERTAIN›〕
- Mention 追踪：`createChunk(kbId, chunkId, fileId, content≤200 截断) → void`；`createEntityMention(kbId, entityName, entityId, chunkId, fileId) → void`（**按确定性 entityId 匹配实体，禁止按 name**——展示名是首写值，按 name 会丢 MENTIONS 进而误删实体，2026-09-07 修复）；`createTripleMention(kbId, tripleId, chunkId, fileId) → void`（独立 TripleMention 索引节点——RELATION 是 relationship 类型不能有出边）〔:627-674 ‹CERTAIN›〕
- 管理：`clearGraph(kbId)`；`deleteFileGraph(kbId, fileId)`（Chunk 节点 DETACH DELETE → TripleMention 删除 → 孤儿回收）；`deleteChunkGraph(kbId, chunkId)`（同口径单分片）；`renameChunkId(kbId, old, new)`（建新→迁 MENTIONS→改 TripleMention→删旧）；`cleanupOrphanNodes(kbId)`〔:679-812,958-970 ‹CERTAIN›〕
- 统计：`countEntities(kbId) → long`；`countRelations(kbId) → long`〔:817-850 ‹CERTAIN›〕
- 向量：`updateEntityEmbedding(kbId, entityName, embedding)`（维度 ≠ 配置值跳过告警）；`searchEntitiesByVector(kbId, embedding, topK) → [{name, entity_type, score}]`（原生向量索引余弦，跨库放大 topK×5 后按 kbId 过滤；索引创建失败降级不可用）；`listEntitiesWithoutEmbedding(kbId, limit) → string[]`〔:855-955 ‹CERTAIN›〕
- 合并：`findMergeCandidates(kbId, threshold, limit) → [{sourceEntityId,sourceName,targetEntityId,targetName,similarity}]`（embedding 两两余弦、实体数上限 2000、降序截断）；`mergeEntity(kbId, sourceId, targetId) → void`（出边/入边迁移→自环丢弃与清理→MENTIONS 迁移→描述择长+票数累加→删 source）〔:975-1012,1015-1088 ‹CERTAIN›；EntitySimilarity.java:236-279 ‹CERTAIN›〕
- 缝合（休眠原语）：`createDirectedRelation(kbId, tripleId, 端点对…, label, content, chunkIds, fileId) → tripleId|null`（同 label 反向边已存在则不新建、证据并入既有边溯源返回既有 tripleId——先建者胜；否则建边+批量 TripleMention）；`findEntityByNormalizedName` / `findChunkMembers` / `hasRelationBetween`〔GraphStore.java:311-339 ‹CERTAIN›；Neo4jGraphStore.java:1093-1219 ‹CERTAIN›；现无生产调用方，见 spec REQ-E03 缺口声明〕

**Error modes:**
- E_MERGE_ENDPOINT_MISSING — 触发：mergeEntity 的 sourceId/targetId 在图内查无。恢复：IllegalArgumentException 上抛，调用方拒绝合并请求。〔Neo4jGraphStore.java:1027-1030,1081-1082 ‹CERTAIN›〕
- E_MERGE_FAILED — 触发：合并事务其他异常（Neo4j 故障等）。恢复：IllegalStateException 上抛（本接口唯一非 IllegalArgumentException 的上抛路径）。〔:1083-1087 ‹CERTAIN›〕
- **约定（非错误模式）**：构建写入五方法（createEntity/createRelation/createChunk/createEntityMention/createTripleMention）失败**不捕获、向上抛**，由调用方标记重试；查询与维护类全部 catch 后返回空结果（空列表/空 Map/0/false/null）+ 记账——调用方无需也无法区分「无数据」与「图存储故障」。〔:31-34 类注释 + 逐方法核对 ‹CERTAIN›〕

**Invariants:**
- Pre：实体/三元组 ID 必须由调用方按确定性哈希传入（entityId=SHA-256(kbId:归一化名)、tripleId=SHA-256(kbId:源:源类型:关系:目标:目标类型) 截 32 位），本契约不生成 ID。〔GraphIdHashing.java:45-56 ‹CERTAIN›〕
- Post：同一 (kbId, normalizedName) 的实体在全库恒为单节点；重复写入只合并属性不新增节点，name 恒保留首次写入的展示名。〔:127-140 ‹CERTAIN›〕
- Post：任意删除路径（deleteFileGraph/deleteChunkGraph/cleanupOrphanNodes）后，「无任何 TripleMention 引用的 RELATION、无任何 MENTIONS 的 Entity」不存在；回收前存量边的空端点 ID 先从端点节点回填。〔:762-783 ‹CERTAIN›〕
- Post：合并完成后 source 节点不存在、其全部边与 MENTIONS 挂在 target、合并不产生 target 自环边。〔:1032-1077 ‹CERTAIN›〕
- Post：createDirectedRelation 对同一输入重复执行（replay 幂等）返回同一 tripleId，不产生重复边。〔:1172-1205 ‹CERTAIN›〕
- 已知偏离（登记债务）：mergeEntity 第 5 步删除 `EntityMention` 节点，但该 label 在 Neo4j 实现中从未创建（mention 以 MENTIONS 边表达）——空跑语句。〔:1057-1059 ‹CERTAIN›〕

**Parent Trace:** 接口 knowledge-graph.GraphStore → Design.GraphStoreModule（缝 1/缝 3 休眠原语）→ Spec REQ-B06、B07、D01、D02、D04、E01~E03、F02、F03；消费方 GraphExtractionModule → REQ-B01~B08、GraphManagementModule → REQ-C01~C05/D03、GraphQueryServiceModule → REQ-C06。

### knowledge-graph.GraphExtractionPipeline（图谱构建编排契约）

**Purpose:** 「分片内容变成图谱」的唯一编排通道：把触发（MQ 消息/同步降级）、KB 级互斥、三模式分流（full/incremental/replay）、单元级 LLM 抽取与质量防线、确定性写入、失败标记与收尾治理折叠为一个消息入口——触发方只表达「构建这个库（或这几个文件）的图谱」。

**Input:**
- `handleGraphBuild(message: Map{kbId: string, 必填—缺失丢弃; fileId: string, 可选; fileIds: string[], 可选—与 fileId 兼容旧格式; mode: string, 可选缺省 "incremental"——full=忽略缓存强制重抽 / incremental=单元缓存 / replay=零 LLM 重放})`〔证据：GraphBuildConsumer.java:183-241 ‹CERTAIN›；增量触发缺省值 IngestionConsumer.java:500 ‹CERTAIN›〕
- 配置面（application.yml `graph.build.*`）：concurrency=15 / llm-timeout=180 / llm-max-tokens=4096 / gleaning=true / unit-max-chars=2000 / workers=3；`graph.entity-type-whitelist`、`graph.entity-embedding-dimension=1024`〔GraphBuildConsumer.java:94-151,143-144 ‹CERTAIN›；application.yml:134-141 ‹CERTAIN›〕

**Output:**
- kb_graph_unit 行（单元级缓存与 replay 载体：content_hash/chunk_ids/extraction_result/status）。
- Neo4j 图数据（实体/关系/Chunk 节点/MENTIONS/TripleMention，经 GraphStore 契约）。
- kb_chunk.graph_indexed 三态回写（1=已提取 / 2=失败待增量重试）。
- kb_graph_index 状态面（status=building→completed|failed、progress、计数、buildError）与操作日志（graph_build_started/completed/failed）。

**Error modes:**
- E_MSG_NO_KBID — 触发：消息无 kbId。恢复：warn 丢弃（ack，不重投）。〔GraphBuildConsumer.java:186-191 ‹CERTAIN›〕
- E_DUPLICATE_BUILD — 触发：同 KB 已有构建在执行（MQ 重投/用户连点）。恢复：warn 跳过重复消息（已 ack，不排队）。〔:192-195 ‹CERTAIN›〕
- E_EXECUTOR_REJECTED — 触发：构建执行器队列满拒绝提交。恢复：释放互斥位、warn 跳过；由下次构建消息补偿。〔:212-215 ‹CERTAIN›〕
- E_LLM_NOT_CONFIGURED — 触发：非 replay 模式且解析出的 apiUrl 为空（策略与 KB 级均无在线模型）。恢复：索引置 failed + buildError 可定位文案 + 失败日志，构建终止（不静默跳过全部单元）。〔:264-281 ‹CERTAIN›〕
- E_UNIT_EXTRACT_FAILED — 触发：单元 LLM 调用失败/响应不可解析/图写入异常。恢复：所属分片标 graph_indexed=2、单元行标失败；构建内自动重试一轮（择优不回退）；仍失败留待下次增量构建自动重试。〔:507-543,332-348 ‹CERTAIN›〕
- E_REPLAY_RESULT_MISSING — 触发：replay 模式下单元无持久化抽取结果（历史失败被误标已构建）。恢复：分片标 graph_indexed=2、单元行标失败，由后续增量构建补抽。〔:480-491 ‹CERTAIN›〕
- D_EMBED_DEGRADED（降级）— 触发：embedding 模型未配置/离线、批量 embed 失败、维度不符。恢复：warn 记账跳过，构建主流程不受影响。〔:1265-1280,1285-1308,1313-1335 ‹CERTAIN›；维度守卫 Neo4jGraphStore.java:884-888 ‹CERTAIN›〕

**Invariants:**
- Pre：消息消费即 ack，长任务在应用内执行器执行（消费者线程永不承载构建——consumer_timeout 重投事故的修复不变式）。〔:175-216 ‹CERTAIN›〕
- Post：同一 KB 全程至多一个构建执行（互斥位 finally 释放）。〔:193-216 ‹CERTAIN›〕
- Post：单元缓存命中条件严格四条（行存在 + status=完成 + content_hash 一致 + 抽取结果非空且可反序列化），full 模式显式忽略缓存；越界旧单元行清理。〔:492-505,447-460 ‹CERTAIN›〕
- Post：入图实体必须过垃圾闸门与类型归一（UNKNOWN 兜底）；关系端点必须解析到实体（含 UNKNOWN 占位登记），自环（归一化名相同）丢弃。〔:553-597,1129-1143,583 ‹CERTAIN›〕
- Post：每个实体/关系写入必伴随证据链（MENTIONS/TripleMention，名字包含匹配 + 首分片兜底）。〔:615-653 ‹CERTAIN›〕
- Post：构建收尾序恒定：实时计数刷新状态 → embedding 回填（每批 20）→ 孤儿清理；进度只推进 progress 不覆盖计数。〔:351-378,1237-1259 ‹CERTAIN›〕
- Post：抽取 Prompt 契约冻结：类型白名单排序稳定化（防随机漂移）、表格主导内容切换专用规则、文本截断 8000、禁止 Formatter 拼接。〔:993-1051 ‹CERTAIN›〕

**Parent Trace:** 接口 knowledge-graph.GraphExtractionPipeline → Design.GraphExtractionModule + ExtractionUnitModule（缝 2/3/4/5）→ Spec REQ-A01~A07、B01~B08、F01、F02、F04。

### knowledge-graph.PprRanker（PPR 分片排序契约）

**Purpose:** 「查询实体 → 相关分片」的图语义排序口：把 Entity-Chunk 提及二分图折叠为一次排序调用，检索增强（retrieval 的 graph expand 通道）只给实体名与 topK，不感知 JGraphT 建图与迭代数值细节。

**Input / Output:**
- `rankChunksByPpr(kbId: string, 必填; entityNames: string[], 必填可空列表; topK: int, 必填) → [{chunkId: string, score: double, entityCount: long}]`（score 降序 Top-K；entityCount=该分片被提及数）〔证据：GraphQueryService.java:73-151 ‹CERTAIN›〕
- 数据源：`GraphStore.listEntityMentions`（Neo4j 模式读 MENTIONS 边）+ `findEntityId`（种子名归一化后映射）；PPR 参数：阻尼 0.85、最大迭代 100、收敛阈值 1e-8、种子均匀个性化向量。〔:49-53,84-122,159-193 ‹CERTAIN›〕

**Error modes:**
- 空语义（非错误）：entityNames 空 / 提及数据为空 / 种子全部未命中图谱 / 提及加载异常 → 一律返回空列表不抛错。〔:74-76,93-96,98-101,124-127 ‹CERTAIN›〕

**Invariants:**
- Pre：种子映射前实体名经 NameNormalizer 归一；仅存在于图中的实体进入种子集。〔:110-122 ‹CERTAIN›〕
- Post：图以双向边表达无向语义，entityCount = 度/2；迭代收敛（L1 < 1e-8）或达 100 次上限即返回。〔:105-108,138,170-192 ‹CERTAIN›〕

**Parent Trace:** 接口 knowledge-graph.PprRanker → Design.GraphQueryServiceModule → Spec REQ-C06；数据源衔接 knowledge-graph.GraphStore（listEntityMentions/findEntityId）；消费方归 `## retrieval` 节 graph 通道。

---

## benchmark-evaluation

> 对应 design 模块：BenchmarkGenerator / EvaluationExecutor / EvalRetrievalPipeline。数据实体契约见 data.md `## benchmark-evaluation` 节。
> 端点排除：BenchmarkController 6 端点（`/api/kb/{kbId}/benchmarks` list/get/questions/create/generate/delete，BenchmarkController.java:44-85）与 EvaluationController 5 端点（`/api/kb/{kbId}/evaluations` list/get/status/run/delete，EvaluationController.java:47-99）是两门面的外部面，其中 list/get/questions/create/delete/status 为 CRUD/透传包装按铁律不立契约；generate 与 run 的深语义由下方两契约承载（门面在其中的角色作为 Invariants 登记）。
> 上游分工（正式声明）：① 检索被测方——评测**不消费** `## retrieval` 节 RetrievalSearchEngine，自带精简检索管线（取舍见 design/benchmark-evaluation.md ADR-1），本契约定义该管线的口径；② 图谱只读——graph 出题模式对 kb_graph_entity/kb_graph_relation 仅 SELECT，构建行为归 `## knowledge-graph` 节；③ 模型路由——复用 model 表 api_url/api_key_ref 惯例，但**无 status=online 过滤**（与 retrieval/knowledge-graph 消费口径不同，见消费点登记）。

### benchmark-evaluation.BenchmarkGenerator（基准出题契约）

**Purpose:** 把「知识库语料 → 可评测题目集」整条链折叠为一次 generate 调用：语料采样、vector/graph 双模式出题、LLM 容错调用、防御解析、题数修正全部内藏——调用方只看到「记录立即返回、题数最终为实际成功数」。

**Input / Output（按方法）:**
- `generate(kbId: string, 必填; config: BenchmarkConfig{name: string; description: string; questionCount: int, 缺省 10; buildMethod: string, vector|graph 缺省 vector; llmModel: string, 可空=default 网关; candidateChunkCount: int, 缺省 5; crossBoundary: bool, 缺省 false}) → KbBenchmark`——同步段：insert 基准记录（is_auto_generated=1、has_gold_chunks=has_gold_answer=1、question_count=请求值）并立即返回；随后 @Async 异步出题〔证据：BenchmarkServiceImpl.java:117-143、BenchmarkConfig.java:25-43 ‹CERTAIN›〕
- `generateQuestions(benchmarkId, kbId, questionCount: int, buildMethod: string, config) → void`（@Async 内部入口，异常不外泄）〔BenchmarkGenerationHelper.java:79-139 ‹CERTAIN›〕
- 解析原语：`BenchmarkResponseParser.parseQuestions(response: string) → List<Map>`——空/错误串→空列表；markdown 围栏/前后夹带文本→剥壳截取；仅截断 JSON 上抛〔BenchmarkResponseParser.java:49-78 ‹CERTAIN›〕

**Output:** kb_benchmark_question 行（question/goldAnswer/goldChunks JSON 数组串/questionIndex 连续从 0/crossBoundary）；kb_benchmark.question_count 终值 = 实际解析成功题数，失败路径 = 0。

**Error modes:**
- E_GENERATION_EMPTY — 触发：LLM 响应为空/错误提示串/不含 JSON 数组。恢复：warn 记账（响应预览截 300 字符）、题目 0 条、题数清零，基准记录保留可重试。〔BenchmarkGenerationHelper.java:96-99,145-162,164-177 ‹CERTAIN›〕
- E_GENERATION_FAILED — 触发：LLM 调用异常或落库异常。恢复：error 日志 + 题数清零；基准记录不删除。〔:134-138 ‹CERTAIN›〕
- E_MODEL_ROUTE_FALLBACK — 触发：llmModel 非 default 且 model 表查无该 code / api_url 空 / 查询异常。恢复：warn 后降级默认网关继续生成。〔:187-213 ‹CERTAIN›〕
- E_VECTOR_CORPUS_EMPTY — 触发：vector 模式下知识库无 kb_chunk。恢复：降级为「基于一般知识出题」prompt，goldChunks 约定为空数组。〔:222-227 ‹CERTAIN›〕

**Invariants:**
- Pre：同步段先落基准记录再分派异步（存在「记录已可见、题目未到」的中间态，题数字段是唯一进度真相）。〔BenchmarkServiceImpl.java:129,141 ‹CERTAIN›〕
- Post：题目批量插入与题数覆盖在同一事务内。〔BenchmarkGenerationHelper.java:120-129 ‹CERTAIN›〕
- Post：解析器对空串/错误串恒返回空列表不抛异常；「截断 JSON」是唯一上抛面，由调用方降级。〔BenchmarkResponseParser.java:49-78 + BenchmarkResponseParserTest.java:43-61 ‹CERTAIN›〕
- Post：graph 模式对图谱表只读，不触发构建、不等待构建。〔BenchmarkGenerationHelper.java:262-266 仅 SELECT ‹CERTAIN›〕
- Post：跨界题 goldChunks 必为两个相邻片段 id（prompt 约束，非代码校验——LLM 不遵从时无强检）。〔:239-248；‹CERTAIN（2026-09-24 深证：生成路径 BenchmarkGenerationHelper.java:103-117 解析后直写库，JSONL 导入路径 BenchmarkServiceImpl.java:188-191 同样直写，全链无任何 id 数量/相邻性校验调用）›〕
- 生成 LLM 调用约定：流式收集 + 超时可配（`benchmark.llm-timeout`，缺省 300s）+ 输出上限 8192。〔:67-71 ‹CERTAIN›〕

**Parent Trace:** 接口 benchmark-evaluation.BenchmarkGenerator → Design.BenchmarkGenerator（缝 1）→ Spec REQ-B01~B06；同步建记录协议归 Design.BenchmarkFacade → REQ-B01。

### benchmark-evaluation.EvaluationExecutor（评测执行契约）

**Purpose:** 把「基准集 → 评测报告」的整套方法论（三模式检索对比、Recall@K、问答合并评判、跨界题上下文完整度、综合分）折叠为一个异步入口；状态机终态封闭与指标口径正确性由本契约承诺。

**Input / Output（按方法）:**
- `run(kbId: string, 必填; config: EvaluationConfig{name: string, 可空; benchmark: string, 必填—业务上（空白触发拒绝协议）; answerModel: string, 可空; judgeModel: string, 可空—与 answerModel 不同时由其执行合并调用; retrievalMode: string, vector|hybrid|fulltext 缺省 hybrid; embeddingModel: string, 可空; enableRerank: bool, 缺省 false; rerankModel: string, 可空}) → KbEvaluation`——校验通过：status=running、completedCount=0 记录 + @Async 分派；校验失败：status=failed、三计数全 0 记录，不派发〔证据：EvaluationServiceImpl.java:86-146、EvaluationConfig.java:24-43、EvaluationController.java:89-91（config 缺省补全）‹CERTAIN›〕
- `execute(evaluationId, kbId, config) → void`（@Async 内部入口，全程 catch 不外泄）〔EvaluationExecutionHelper.java:111-247 ‹CERTAIN›〕

**Output:** kb_evaluation 终态行（completed + duration + recall_at_1/3/5/10 + answer_accuracy + overall_score，或 failed）；kb_evaluation_result 每题一行（question/generated_answer/retrieval_metrics 指标串/recall_at_* 四列/context_completeness[_extended] 两列/is_correct/judge_reason）。

**Error modes:**
- E_BENCHMARK_MISSING — 触发：config.benchmark 空白。恢复：落 failed 记录返回，不异步、无结果行。〔EvaluationServiceImpl.java:92-95,132-146 ‹CERTAIN›〕
- E_BENCHMARK_EMPTY — 触发：run 时基准题目数为 0；或执行期发现题目为空（与删除/导入竞态）。恢复：前者 failed 记录返回；后者执行中置 failed（不产生 completed 0 分）。〔:98-104 + EvaluationExecutionHelper.java:150-163 ‹CERTAIN›〕
- E_EXECUTION_FAILED — 触发：执行全程任意异常（批量 embedding 整体失败且逐条降级再失败、DB 不可达等）。恢复：状态机强制落 failed 终态。〔:237-246 ‹CERTAIN›〕
- D_QUESTION_FAILED（降级）— 触发：单题检索/LLM/解析异常。恢复：兜底结果行（R@K 全零、isCorrect=0、judgeReason=「执行异常」），其余题与聚合继续。〔:362-376 ‹CERTAIN›〕
- D_JUDGE_RULE_FALLBACK（降级）— 触发：评判 LLM 返回错误串（「模型调用失败」前缀）/超时/JSON 解析失败/调用异常。恢复：规则评判——标准答案关键数字或 2~4 字中文关键词命中率 ≥0.8 判对；检索内容为空直接判错不调 LLM。〔:634-689,817-865 ‹CERTAIN›〕
- D_RETRIEVAL_DEGRADED（降级）— 触发：向量空/向量检索异常/hybrid 整体异常。恢复：一律降级全文检索（MATCH...AGAINST→LIKE 二级降级）；Rerank 失败保持原序。〔:443-445,493-496,507-509,520-548,615-618 ‹CERTAIN›〕

**Invariants:**
- Pre：run 校验（基准存在非空）通过前不建 running 记录、不派发异步。〔EvaluationServiceImpl.java:92-115 ‹CERTAIN›〕
- Pre：execute 开跑前先写 dataCount=题目数（进度分母及时可见）。〔EvaluationExecutionHelper.java:146-148 ‹CERTAIN›〕
- Post：状态机终态封闭——running 只能到 completed（finishEvaluation 单事务写终态+全指标）或 failed；无第三出口，不存在永久 running。〔:150-163,237-246,889-907 ‹CERTAIN›〕
- Post：指标口径——R@K = goldChunks（解析失败视为空）与前 K 条检索 id 有交集；gold 空恒 0；各率 = 命中题数 ÷ 总题数（总题数恒为题目列表长度，异常题计 0）；overall_score = R@10×0.7 + answer_accuracy×0.3。〔:216-235,869-885 ‹CERTAIN›〕
- Post：单题并行度恒 ≤4（信号量 + 4 守护线程池）。〔:89-109 ‹CERTAIN›〕
- Post：跨界题（crossBoundary=1）才有 context_completeness 两值，非跨界题恒 NULL；完整度两值各钳制 0~1。〔:316-343,770-773 ‹CERTAIN›〕
- 已知偏离（登记债务）：① 结果行逐条 insert 非事务，中途失败留「failed + 部分结果行」混合态〔:230-232 ‹CERTAIN›〕；② 评测检索为平行实现不消费 RetrievalSearchEngine（ADR-1，取舍见 design）；③ model 表路由无 status=online 过滤（消费点登记）；④ top-2 chunk 喂评判 LLM、每 chunk 截 300 字——指标对「上下文裁剪」敏感〔:287-290,691-693 ‹CERTAIN›〕。

**Parent Trace:** 接口 benchmark-evaluation.EvaluationExecutor → Design.EvaluationExecutor（缝 2/缝 3 + EvalRetrievalPipeline）→ Spec REQ-C01~C06、REQ-D01~D07；run 拒绝协议归 Design.EvaluationFacade → REQ-C01。

---

## qa-pairs

> 对应 design 模块：QaPairModule（(S)）/ ExcelParserModule / QaTemplateModule（(S)），见 design/qa-pairs.md。数据实体契约见 data.md `## qa-pairs` 节。
> **端点排除（本节无新契约接口，整节 = 排除理由 + 消费点登记）**：QaPairController 6 端点（`/api/kb/{kbId}/qa-pairs` list/create/update/delete/confirm/import，QaPairController.java:44-79）与 QaImportController 模板下载（`/import-template`，QaImportController.java:35-36）不立契约——list/create/delete 是 MyBatis-Plus 单条语句包装（QaPairServiceImpl.java:42-84），confirm 是一行状态转移（:89-95），update 是两字段 Map 拷贝（:70-77）；导入的实质复杂度已被纯函数 ExcelParserModule（parse/normalizeQuestion，无状态零触库）吸收，剩余编排为粘合代码；模板下载是流式写 Excel 的展示面。无一满足「删除测试通过」的深模块判据；HTTP 暴露面见 design/qa-pairs.md §2。
> **遗留疑问裁决（feature-map #8「QA 对是否参与召回未见独立端点」）**：参与召回 = CERTAIN，但经检索链内部 qa 通道实现，**无亦无需独立端点**——关键词命中前置（RetrievalServiceImpl.java:224-246）与多路融合通道（:703-706，加权权重 0.3 :1055）两路消费 kb_qa_pair；行为契约归 `## retrieval` 节 RetrievalSearchEngine（RetrievalConfig 的 enableKeywordMatch/qaRecallCount，interfaces.md:746），本节不重复定义。
> **上游分工**：kb_qa_pair 的删除级联行为由上游契约承载——KB 删除级联见 `## kb-core` 节 KbLifecycleService Post（interfaces.md:189）；文件删除/重试/跨库移动清理与重切口径（QA 模式拒绝重切 E_RECHUNK_STATE_CONFLICT、重切保留 QA 对）归 `## file-ingestion` 节（interfaces.md:290）；qa 模式文件的 AI 分片拒绝归 `## ai-chunking` 节（interfaces.md:719,725）。本特性对 kb_qa_pair 只拥有**写入口径与状态机**（见 data.md `## qa-pairs` 节 Lifecycle）。

### 消费点登记（kb_qa_pair 的跨域消费方，消费行为归消费方链）

| 消费方 | 消费口径 | 位置 |
|---|---|---|
| retrieval（qa 通道·前置路） | enableKeywordMatch=true 时：query 分词（英文/数字连续段≥2 字符｜中文连续汉字块）逐 token LIKE question 列，按命中 token 数降序取 qaRecallCount（缺省 5），结果以 source/channel=qa、content="Q:…\nA:…" 前置插入；**查询仅按 kbId 过滤、不过滤 status（draft 亦召回）**；召回异常降级空列表 | RetrievalServiceImpl.java:224-246,798-852 ‹CERTAIN› |
| retrieval（qa 通道·融合路） | qaRecallCount>0 时作为独立召回通道与向量/全文并列参与 rrf/weighted/interleave 融合；weighted 下 QA 权重硬编码 0.3 | RetrievalServiceImpl.java:703-706,1055 ‹CERTAIN› |
| kb-core（删库级联） | 逐文件按 kbId+fileId 物理删除——**fileId=null 的记录不在清理范围（孤儿缺陷，见下方登记）** | KbServiceImpl.java:210-217 ‹CERTAIN› |
| file-ingestion（文件删除/重试/移动级联） | 文件硬删按 kbId+fileId 清理；qa 模式重试清理该文件旧 QA 对；跨库移动删源库 QA 对且不随迁目标库 | FileServiceImpl.java:601-611,625-634,750-756 ‹CERTAIN› |
| file-ingestion（重切守卫/口径） | processing_mode=qa 文件拒绝重切；重切保留 QA 对（用户手工数据口径） | FileServiceImpl.java:350-353,382-384 ‹CERTAIN› |
| ai-chunking（应用守卫） | qa 模式文件拒绝 AI 分片 apply | AiChunkServiceImpl.java:790 ‹CERTAIN› |

**登记债务（实现侧现状，修复以本登记为准）**：① **孤儿 QA 对**——导入路径强制 fileId=null（QaPairServiceImpl.java:158）而删库级联只按 fileId 清理，删库后导入 QA 对全量残留，与 `## kb-core` 节 delete Post「kb_qa_pair 无该 kbId 残留」不变式冲突（仲裁：以代码实测为准，缺陷登记于两节）；② ~~召回不过滤 status，confirm 不具备「生效准入」检索语义~~（2026-09-26 立项清偿：ai 来源准入 `source='manual' OR status='confirmed'`，落点见 `## qa-generation` 变更点登记）；③ update/delete/confirm 不校验 kbId 归属（跨库 id 可操作），且错误语义三态不一——update 查无抛 RuntimeException 走 500 兜底、confirm 查无静默、delete 幂等；④ 模板 8 列中 5 列（分类/关键词/优先级/生效时间/失效时间）解析回显但表无对应列，永不落库；⑤ 解析器与编排层以魔法串「覆盖更新（原记录将被更新）」传递覆盖决策（ExcelQaPairImporter.java:185 ↔ QaPairServiceImpl.java:140）；⑥ 导入覆盖行跳过优先级/状态的行级失败校验（与新增行不对称，ExcelQaPairImporter.java:178-185 vs :191-219）。

**Parent Trace:** 本节无契约接口 → Design.QaPairModule/ExcelParserModule/QaTemplateModule → Spec REQ-A01~A05、B01~B03、C01~C09（契约排除）；消费点登记 → Design 缝 3 → Spec REQ-D01~D04、REQ-E01~E05。

---

## publish-review

> 对应 design 模块：LogAuditModule（唯一深模块，立契约）/ VersionModule、ReviewModule、PublishManageModule（均 (S)，端点排除）/ PublishConfigGate（platform 侧消费点，归属 platform-config 链），见 design/publish-review.md。数据实体契约见 data.md `## publish-review` 节。
> **端点排除**：PublishController 10 端点（versions 族 5 + reviews 族 5，PublishController.java:34-52）与 PublishManageController 13 端点（PublishManageController.java:47-123）不立契约——前者是 MyBatis-Plus 单查询/单覆写包装（PublishServiceImpl.java:34-56、ReviewServiceImpl.java:27-32），后者是「insert/select + 三视图推导」浅编排（PublishManageServiceImpl.java:19-142）；鉴权与审计由控制器注解（@KbAuth/@Loggable）承载，属 HTTP 层实现细节。HTTP 暴露面见 design/publish-review.md §2。
> **遗留 INFERRED 裁决（feature-map #12）**：① 版本快照粒度 = **纯元数据计数（name/description/tags/changeSummary/fileCount/chunkCount），不快照文件表/分片/QA 任何内容**——KbVersion.java:34-38 无内容字段、createVersion 无内容收集（PublishServiceImpl.java:37-43），CERTAIN；② 审核开关来源 = sys_config（platform 侧 ConfigManageController.java:72-86 六键），**后端发布/审核流程不读取该开关做拦截**（grep 全仓 review_switch 仅 ConfigManageController/SysConfig 引用），CERTAIN。

### publish-review.KbOperationLogService（全后端操作/更新日志写入口契约）

**Purpose:** 全后端「谁在什么时候对什么做了什么」的唯一记账定点：135 个 ActionType 枚举常量的声明式记账（@Loggable + KbLogAspect）与 5 个 Maven 模块的编程式记账（LogService 直调）在此折叠为同一张 kb_log/kb_update_log；写失败必须吞掉——审计永远不能挡住业务。

**Input / Output（按方法）:**
- `addLog(kbId: string, 必填; category: string, 可空→null; action: string, 可空→null; target/detail/operator: string, 可空; )` — 兼容写入口，status 固定缺省 "success"〔证据：LogServiceImpl.java:89-91 ‹CERTAIN›〕
- `addLog(kbId, category: LogCategory{operation,retrieval,publish}, action: ActionType, target, detail, operator, status: string{success,failed}[, extra: string, 可空])` — 枚举写入口（切面专用），category/action 落库存枚举名〔LogServiceImpl.java:101-122、KbLogAspect.java:103,113 ‹CERTAIN›〕
- `addUpdateLog(kbId, updateType: string, target, detail, operator[, oldValue: string, 可空; newValue: string, 可空])` — 更新日志（新旧值对比语义）〔LogServiceImpl.java:94-96,125-140 ‹CERTAIN›〕
- `listLogs(kbId, category?: string, keyword?: string, page: int 缺省1, pageSize: int 缺省10) → PageResult<KbLog>` — category 精确过滤；keyword 对 target/detail/operator 三列 OR 模糊；timestamp 倒序〔LogServiceImpl.java:53-64 ‹CERTAIN›〕
- `countByCategory(kbId) → Map{total: long, operation: long, retrieval: long, publish: long}` — 每分类一次 COUNT 的聚合〔LogServiceImpl.java:67-77 ‹CERTAIN›〕
- `listUpdateLogs(kbId, type?: string) → List<KbUpdateLog>`（timestamp 倒序，无分页）〔LogServiceImpl.java:80-84 ‹CERTAIN›〕
- `markUpdateLogRead(id) → void` — **空实现**（已读归前端本地状态，后端不持久化）〔LogServiceImpl.java:145-147 ‹CERTAIN›〕
- 声明式入口 `@Loggable(category, action, target SpEL, detail)`：切面解析 SpEL（#p0/#参数名 双变量法）、提取 kbId（参数名匹配→首个非空 String 参数兜底→"unknown"）、操作人（SecurityUtil→"system" 兜底）、模块名（ActionType 前缀表→LogCategory 表→"其他"），成功/异常双路径写库并发布 SysAuditLogEvent，异常原样上抛〔KbLogAspect.java:91-118,144-190 ‹CERTAIN›〕

**Error modes:**
- E_LOG_WRITE_FAILED — 触发：kb_log/kb_update_log insert 任意异常（表缺失/连接断）。恢复：捕获后仅记 error 日志，**宿主业务照常返回**——不重试、不上抛。〔LogServiceImpl.java:119-121,137-139 ‹CERTAIN›〕
- E_SPEL_EVAL_FAILED — 触发：@Loggable 的 target/detail SpEL 求值异常或结果 null。恢复：回落 fallback（target=按 fileId/folderId 等参数名兜底提取，detail=注解字面值），不阻断业务方法。〔KbLogAspect.java:144-174,192-207 ‹CERTAIN›〕
- E_ENUM_UNKNOWN — 触发：兼容入口传入未知 category/action 字符串。恢复：解析为 null 落库（非报错），日志行保留其余字段。〔LogServiceImpl.java:152-171 ‹CERTAIN›〕
- E_AUDIT_EVENT_FAILED — 触发：SysAuditLogEvent 发布异常。恢复：warn 日志后继续（审计事件尽力而为）。〔KbLogAspect.java:135-140 ‹CERTAIN›〕

**Invariants:**
- Pre：kbId 不可为空（切面兜底 "unknown" 字符串而非拒绝——弱约定）。
- Post：addLog ⇒ kb_log 行 status ∈ {success,failed}（null→"success"）、timestamp=now；**任何异常都不改变宿主方法的返回值与异常传播**。
- Post：@Loggable 方法异常 ⇒ 先写 status=failed 行（detail 兜底为异常消息）再原样上抛原异常。〔KbLogAspect.java:107-117 ‹CERTAIN›〕
- Post：countByCategory ⇒ total ≥ 各分类计数之和（category 非枚举值的行计入 total 不计入任何分类）。
- 已知偏离（登记债务）：① markUpdateLogRead 契约名承诺持久化、实现为空——前端自行管理已读；② operation/graph-eval/iam/knowledge/retrieval 五模块中 4 处**绕过本契约直用 KbLogMapper/KbUpdateLogMapper**（RetrievalServiceImpl.java:23-24、UpdateRemindServiceImpl.java:21-22、HomeController.java:35-36、UnifiedLogServiceImpl.java:13-14）——口径漂移风险，收敛方向=统一走 LogService。

**Parent Trace:** 接口 publish-review.KbOperationLogService → Design.LogAuditModule（缝 2/缝 3）→ Spec REQ-F01~F07。

### 消费点登记（跨模块/跨链消费方）

| 消费方 | 消费口径 | 位置 |
|---|---|---|
| knowledge（摄入/图谱/KB/文件/目录/QA/策略） | LogService.addLog/addUpdateLog 记录摄取完成、图谱构建、CRUD 审计；PublishManageController 注入 LogService（但 13 端点审计实际走 @Loggable 注解路） | IngestionConsumer、GraphBuildConsumer、KbController、FileController 等；PublishManageController.java:37,44 ‹CERTAIN› |
| retrieval（检索/更新提醒） | 直用 KbLogMapper 写检索日志（category=retrieval）；直用 KbUpdateLogMapper 写配置变更更新日志 | RetrievalServiceImpl.java:23-24、UpdateRemindServiceImpl.java:21-22 ‹CERTAIN› |
| graph-eval（评测/图谱/基准） | LogService.addLog 记录评测运行等操作 | GraphController、EvaluationController、BenchmarkController 及 3 个 ServiceImpl ‹CERTAIN› |
| iam（登录日志旁路澄清） | 认证日志**不消费本契约**——AuthController 用 operation 侧 SysLoginLogService 落 sys_login_log；publish-review 链对 iam 的关系仅是 KB 鉴权消费（缝 1），非日志消费 | AuthController.java:43,59 ‹CERTAIN› |
| operation（运营） | 直用 KbLogMapper 读 kb_log 做首页统计与统一日志视图 | HomeController.java:35-36、UnifiedLogServiceImpl.java:13-14 ‹CERTAIN› |
| platform-config（发布/审核开关） | sys_config 六键（publish_switch/review_switch/review_flow/review_flow_binding/publish_settings/review_settings）写入与读取；**无后端 gate 消费**（缝 5 登记面） | ConfigManageController.java:72-86 ‹CERTAIN› |
| kb_publish_history 双写方 | VersionModule.transitionStatus（operator=version.createdBy）与 PublishManageModule.publish/revoke/resetKnowledge（请求体/operator=system）同表双写、口径不同（缝 4） | PublishServiceImpl.java:48-55、PublishManageServiceImpl.java:25-44,108-129 ‹CERTAIN› |

**登记债务（实现侧现状，修复以本登记为准）**：① PublishController 10 端点无 @KbAuth（与 PublishManageController 全覆盖不一致）；② 版本流转无状态机合法性校验（任意态→任意态）且版本 id 查无静默；③ 审核终态可覆写（approved→rejected 可逆）且与版本状态零联动；④ createVersion 版本号 max+1 非原子，并发创建可产生重复版本号（表无唯一约束，schema.sql:303-317）；⑤ 发布计划无执行器（REQ-C03）；⑥ 重置不消费 canReset/maxResetCount（REQ-E04）；⑦ transitionStatus 写历史不经 @Loggable、operator 取 createdBy 非当前操作人；⑧ getKnowledgeUpdateLogs 返回裸 Map 而非 PageResult（字段名一致但类型不一致）。

**Parent Trace:** 消费点登记 → Design.缝 3/缝 4/缝 5 → Spec REQ-F07、REQ-A05↔REQ-B01、REQ-D06；债务登记 → Spec REQ-A06/D04/E04/C03 的现状口径断言。

---

## platform-config

> 对应 design 模块：ModelRegistryModule / ModelConnectivityTester / SysConfigStoreModule / SensitiveWordFilterModule / TermSynonymModule 五个深模块立契约；ModelTrainingFacade、StrategyRegistryModule、DictionaryModule、QueryRuleModule、NotificationModule、BrandUploadService 六个浅模块按端点排除铁律不立契约（HTTP 暴露面见 design/platform-config.md §1/§2）。数据实体契约见 data.md `## platform-config` 节。
> **端点排除**：ModelController 15 端点中的 presets 四端点（无持久化假成功回显）、presets 之外的 HTTP 包装、ConfigManageController 七个业务开关专用端点（固定键转调 saveConfig）、Dictionary/QueryRule/Notification/Upload 全部端点为实现细节不立契约。
> **上游分工（正式声明，model 表动态路由契约）**：model 表是全仓 AI 能力的共享路由事实源。① **本链是唯一生产方**——注册/启停/导入的写入口径（status 二值、code 路由键）由本节 ModelRegistry 契约承诺；② 消费方解析器在各消费链自建（AiChunkServiceImpl.resolveRecord:1582-1592、ChunkingServiceImpl:236-246 等，同口径 code+status='online' LIMIT 1），其消费行为归各消费链契约——`## retrieval` / `## knowledge-graph` / `## ai-chunking` 各节已按消费登记；③ `## benchmark-evaluation` 节登记的「无 status=online 过滤」（BenchmarkGenerationHelper.java:196 裸 SQL）与本节生产方口径**核对结论：口径不一致属实**——生产方 toggle 只产生 online/offline 二值，benchmark 在 offline 模型上仍会路由（生产方视角这是消费侧缺陷，收敛方向见 design/platform-config.md §4）；④ 缺省路由降级协议：解析 null → apiUrl/apiKey 以 null 下传 fastrag-ai → 默认网关 `ai.gateway.url`（缺省 http://localhost:11434，LlmService.java:77-78）。
> 术语治理口径衔接：`## retrieval` 节 QueryEnhancePipeline 的 expandSynonyms「仅消费 term_record.status=1 词条」与本节 TermSynonymService 的供给口径同源，本节定义供给面，消费侧不重复。

### platform-config.ModelRegistry（模型注册表契约）

**Purpose:** 全仓 AI 能力路由事实源的唯一读写口：把「平台接入了哪些模型、哪些在线、从哪里调」维护为各消费链可解析的一致状态——消费方只认 code+status，字段演化与密钥防误清空细节藏在注册表内。

**Input / Output（按方法）:**
- `list(keyword: string, 可选—名称 LIKE; purpose: string, 可选—精确过滤) → ModelRecord[]`（无分页）〔证据：ModelServiceImpl.java:25-30 ‹CERTAIN›〕
- `get(id: string) → ModelRecord|null`〔:33-35 ‹CERTAIN›〕
- `create(fields{name: string; code: string—全局路由键; purpose: string—自由串; brand/apiUrl: string, 可选; apiKey|apiKeyRef: string, 可选—双字段名归一; contextWindow: int, 可选; enableThinking: bool, 可选; status: string, 可选缺省 "offline"}) → ModelRecord`〔:145-161 ‹CERTAIN›〕
- `update(id, fields 同 create 但全可选) → ModelRecord|null`（部分语义：containsKey 才覆盖；apiUrl/apiKey 空白值不覆盖）〔:164-192 ‹CERTAIN›〕
- `delete(id) → void`（物理删除，无引用检查）〔:195-197 ‹CERTAIN›〕
- `toggle(id) → void`（status 在 online/offline 间翻转——全链唯一状态翻转入口）〔:200-206 ‹CERTAIN›〕
- `importModels(items: List<Map>) → ModelRecord[]`（逐条插入，purpose 缺省 "chat"、status 缺省 "offline"、无查重）〔:209-226 ‹CERTAIN›〕
- `exportModels(ids: string, 可选—**被忽略**; purpose: string, 可选) → {format:"json", count, items}`〔ConfigManageServiceImpl.java:33-38 ‹CERTAIN›〕

**Error modes:**
- E_MODEL_TEST_TARGET_INVALID — 触发：测试目标模型不存在（"模型不存在"）或 apiUrl 空白（"模型未配置接口地址"）。恢复：前端提示补全。〔ModelServiceImpl.java:58-64 ‹CERTAIN›〕
- 无显式错误模式 — CRUD/toggle/import 对不存在的 id 一律静默跳过或返回 null（update 返回 null、toggle/delete 无操作），不报错。〔:165,201-205,195-197 结构观测 ‹CERTAIN›〕
- 删除保护（R3 实证 2026-09-24）：无引用检查、无拦截——删除被消费中的模型后，消费方 `selectOne` 返回 null（resolveRecord 连异常一并吞为 null，AiChunkServiceImpl.java:1582-1592），调用点以 `rec != null ? rec.getApiUrl() : null` 透传（:1183-1185），apiUrl/apiKey 双 null 下传 fastrag-ai → **静默降级到默认网关** `ai.gateway.url`（缺省 http://localhost:11434，LlmService.java:77-78）：不报错、不告警，调用以默认网关的错误结果或非预期模型收场。运行时形态=静默降级（非崩溃），治理规程依赖「先 offline 再 delete」〔spec REQ-A04 同源决议〕。

**Invariants:**
- Pre：本契约是 model 表的唯一写入方（全仓 grep 实测，消费方只读）。
- Post：status 恒 ∈ {online, offline} 二值（create 缺省 offline、toggle 二值翻转）——消费方 `status='online'` 过滤口径的成立前提。〔:158,203 与 ChunkingServiceImpl.java:241 对照 ‹CERTAIN›〕
- Post：code 是消费方路由键（`code=? AND status='online' LIMIT 1`），本契约**不**强制同 code 唯一（DDL 无唯一约束，schema.sql:523-531）——同 code 多条在线记录时消费方取值不确定，责任在生产方数据质量。〔AiChunkServiceImpl.java:1588-1590 消费口径 ‹CERTAIN›〕
- Post：密钥写入恒落 api_key_ref 列（apiKey/apiKeyRef 双入参名归一），update 空白不覆盖（防误清空）。〔:153-155,176-180 ‹CERTAIN›〕
- 已知偏离（登记债务）：purpose 无值集校验，种子 'Embedding' 与消费方字面 'EMBEDDING' 过滤依赖 MySQL 排序规则匹配（ChunkingServiceImpl.java:240 vs data.sql:441）‹CERTAIN（2026-09-24 深证）：schema.sql:531 未显式声明 collation，生产 MySQL 默认（utf8mb4_0900_ai_ci 等 ci 规则）匹配成功；H2 MODE=MySQL 默认大小写敏感，字面不一致即失配——隐式依赖属实且环境敏感›；presets 四端点为无持久化回显（见 implement §2）；exportModels 的 ids 参数被忽略。

**Parent Trace:** 接口 platform-config.ModelRegistry → Design.ModelRegistryModule + 缝 1（生产方）→ Spec REQ-A01~A06、REQ-C01、REQ-C02；消费口径核对 → `## benchmark-evaluation` 节 EvaluationExecutor/BenchmarkGenerator 债务③。

### platform-config.ModelConnectivityTester（模型连通性测试契约）

**Purpose:** 模型投入业务前的可达性验证口：把「对指定模型发一次真实调用并报告结果」折叠为三个测试方法——三类网关（LLM/Embedding/Rerank）的签名差异、计时、失败归一化全部内藏，调用方拿到统一的成功/失败结构。

**Input / Output（按方法）:**
- `testChat(modelId: string, 必填; prompt: string, 可选缺省固定问候) → Map{success:true, modelName, modelCode, purpose:"LLM", prompt, answer, elapsedMs, apiUrl}`〔证据：ModelServiceImpl.java:40-42,119-135 ‹CERTAIN›〕
- `testEmbedding(modelId, text: string, 可选缺省"你好世界") → Map{success:true, …, purpose:"Embedding", input, dimensions, vectorPreview(前 10 维), elapsedMs, apiUrl}`〔:45-47,72-89 ‹CERTAIN›〕
- `testRerank(modelId, query: string, 可选缺省; documents: List<String>, 可选—空补缺省 3 文档) → Map{success:true, …, purpose:"Rerank", query, documentCount, results, elapsedMs, apiUrl}`〔:50-52,90-109 ‹CERTAIN›〕

**Error modes:**
- E_TEST_TARGET_INVALID — 归 ModelRegistry 契约 E_MODEL_TEST_TARGET_INVALID，本节不重复定义（同前置校验路径）。
- E_TEST_GATEWAY_FAILED — 触发：fastrag-ai 网关调用任意异常（网络/鉴权/网关报错）。恢复：catch 归一为 `{success:false, modelName, modelCode, error}` 返回，不抛 HTTP 错误。〔:137-139 ‹CERTAIN›〕

**Invariants:**
- Pre：测试类型由所调方法决定（testChat 恒走 LLM 通道），**不读取模型记录的 purpose 字段**——对 Embedding 模型调 testChat 将以其 apiUrl 发对话请求（类 javadoc「根据模型 purpose 路由」与实现不符，登记债务）。〔:57,71 方法入参硬编码 purpose、switch 以其为键 ‹CERTAIN›〕
- Post：成功结果恒含 elapsedMs 真实测时与 apiUrl 回显；向量预览恒 ≤10 维、维度不足时取实际长度。〔:85-87,132-133 ‹CERTAIN›〕
- Post：ASR/TTS/OCR 分支返回「暂不支持在线测试」失败结构，但 HTTP 面无入口（仅内部可达的死分支）。〔:110-118 ‹CERTAIN›〕

**Parent Trace:** 接口 platform-config.ModelConnectivityTester → Design.ModelConnectivityTester → Spec REQ-B01~B05；网关边界 → fastrag-ai 服务族（缝 1 stable side）。

### platform-config.SysConfigStore（系统配置存储契约）

**Purpose:** 平台级键值配置的唯一存储与审计口：upsert 语义、变更历史强制记账、默认值标记收敛在同一 saveConfig 路径——七类业务开关端点、导入、单键操作全部是它的参数化调用，新增配置键零新代码。

**Input / Output（按方法）:**
- `listConfigs(configType: string, 可选) → SysConfig[]`（configKey 升序）〔证据：ConfigManageServiceImpl.java:40-44 ‹CERTAIN›〕
- `getConfig(configKey: string) → SysConfig|null`〔:45 ‹CERTAIN›〕
- `saveConfig(configKey: string, 必填; configValue: string, 必填—String.valueOf 语义; configType/description: string, 可选—更新时不覆盖 null; operator: string) → SysConfig`——键不存在 insert（历史 changeType=create）、存在 update（历史 changeType=update，记录旧值）〔:46-66 ‹CERTAIN›〕
- `listHistory(configKey: string, 可选; configType: string, 可选—**被忽略**) → SysConfigHistory[]`（timestamp 倒序 LIMIT 100）〔:67-71 ‹CERTAIN›〕
- `exportConfig(configType) → {format:"json", count, items}`；`importConfig(items, operator) → SysConfig[]`（逐键 saveConfig）〔:72-80 ‹CERTAIN›〕
- `getDefaultConfigs() → SysConfig[]`（is_default=1）；`setDefault(configKeys) → SysConfig[]`（把当前行 is_default 置 1，语义=「标记当前值为默认基准」）〔:81-85 ‹CERTAIN›〕
- `resetToDefault(configKeys, operator) → SysConfig[]`〔:86-108 ‹CERTAIN›；现状为死逻辑，见 Invariants〕

**Error modes:**
- 无显式错误模式——键不存在时 setDefault/resetToDefault 静默跳过、resetToDefault 无默认行可回退时静默无操作，均不报错。〔:83,90-107 结构观测 ‹CERTAIN›〕
- 配置值格式校验（R4 决议 2026-09-24）：取「**不做集中校验，config_value 按不透明字符串存储**」——七类业务开关端点均先 toJson 再落库，结构契约由各键的消费方约定（如 StrategyConfigResolver 读 general_* 键），集中校验无统一 schema 可依据；理由：最小改动 + 与现有消费协议一致；现状差距：直连 saveConfig 的任意字符串可落库，损坏值的影响延迟到消费方读取时暴露——低风险登记（代裁，用户可推翻）。

**Invariants:**
- Pre：config_key 全局唯一（schema.sql:786 UNIQUE）——同键恒单行，「默认值」是行上的标志而非并行行。
- Post：任意 saveConfig ⇒ sys_config_history 新增一行（config_id/config_key/old_value/new_value/change_type∈{create,update,reset}/operator/timestamp），不存在无历史的配置变更。〔:51,59,63-66 ‹CERTAIN›〕
- 已知偏离（登记债务）：① **resetToDefault 为死逻辑**——当前行 is_default=1 时被判「已是默认」跳过（:90-92）；≠1 时查「同键另一条 is_default=1 行」，在 config_key UNIQUE 下恒空（:95），故任何输入都不产生实际重置（:86-108 结构推演 ‹CERTAIN›）；② operator 由控制器硬编码 "admin"，不接会话身份（ConfigManageController.java:56,60 ‹CERTAIN›）；③ listHistory 的 configType 参数被忽略；④ 发布/审核等七类开关只存储无后端 gate 消费（`## publish-review` 节缝 5 已登记，本节不重复）。

**Parent Trace:** 接口 platform-config.SysConfigStore → Design.SysConfigStoreModule + 缝 4 → Spec REQ-E01~E06；消费方 StrategyConfigResolver（系统默认层，`## parse-strategy` 节 E_SYSCONFIG_UNREADABLE 的上游）。

### platform-config.SensitiveWordFilter（敏感词过滤管线契约）

**Purpose:** 平台内容安全的过滤管线：把「这段文本能否放行/要不要替换」折叠为两个专用原语（输入拦截、输出替换）加一个通用底座——启用词表缓存、三级分类解析、命中词不回显的安全语义全部内藏，对话链逐消息调用只面对 `Optional<String>`。

**Input / Output（按方法）:**
- `checkInput(text: string, 可空—空返回放行) → Optional<String>`——命中任一启用词的 blockInput 标志 ⇒ present（值为词条指定回复，未配置用固定兜底文案「您的输入包含受限内容，无法处理」）；**命中词本身不回显**。未命中 ⇒ empty〔证据：SensitiveWordServiceImpl.java:79-94 ‹CERTAIN›〕
- `filterOutput(text: string, 可空—空返回 empty) → Optional<String>`——命中 replaceAnswer 标志的词全部替换为指定回复（未配置用 "***"）；有替换 ⇒ present（替换后全文），无 ⇒ empty〔:97-116 ‹CERTAIN›〕
- `checkAndFilter(text, mode: enum{reject,replace,mask}) → Optional<String>`——reject 命中返回「输入包含敏感词: {word}」；replace 用指定回复替换；mask 保留首字符其余打码。通用底座〔:40-76 ‹CERTAIN›〕
- `list() → List<SensitiveWord>`（仅 enabled=1，缓存读加载）〔:23-32 ‹CERTAIN›〕
- `save(SensitiveWord)` / `update(SensitiveWord)` / `delete(id)` / `invalidate()`——写操作后缓存强制失效〔:35-37,119-135 ‹CERTAIN›〕

**Error modes:**
- 无显式错误模式——空文本/无词表/未命中一律放行语义（empty 或原文），过滤失败不阻断主流程。〔:41-43,72-75 结构观测 ‹CERTAIN›〕

**Invariants:**
- Pre：检查面恒为 enabled=1 词表（缓存即启用词查询结果）；词表写操作（含 CSV 导入）与缓存失效在同一调用内完成，读不到写前旧值超过一次未命中缓存的时间。〔:27,119-135、SensitiveWordController.java:154 finally invalidate ‹CERTAIN›〕
- Post：分类标志解析口径 = category JSON 中含 `"{flag}":true` 子串（blockInput/blockSearch/replaceAnswer 三键）。〔:150-158 ‹CERTAIN›；子串匹配对键序/空格脆弱，design §4 深化项〕
- Post：控制器 list 输出适配面恒为 {id, word, reply(=replacement), blockInput, blockSearch, replaceAnswer, level, enabled(布尔), createdAt}，不外泄 replacement/category 原始字段。〔SensitiveWordController.java:50-68 + SensitiveWordControllerTest.java:60-70 ‹CERTAIN›〕
- 已知偏离（登记债务）：① blockSearch 标志全仓无消费方（纯存储）；② 控制器 update 全量重建 category——请求未传的标志被重置 false〔SensitiveWordController.java:89,158-164 ‹CERTAIN›〕；③ CSV 导入按逗号直切不支持引号转义；④ 缓存为进程内 volatile，失效域=单 JVM 进程‹CERTAIN（2026-09-24 深证：SensitiveWordServiceImpl.java:20 进程内字段，无跨进程通知机制）›；多实例部署下他实例不失效（部署形态属环境配置，多实例时需分布式失效方案）。

**Parent Trace:** 接口 platform-config.SensitiveWordFilter → Design.SensitiveWordFilterModule + 缝 2 → Spec REQ-H01~H08；消费方 AppServiceImpl（app-center 链，isSafetyEnabled 开关归其裁决）。

### platform-config.TermSynonymService（术语同义供给契约）

**Purpose:** 检索同义扩展的数据供给口：把「领域术语↔别名」知识组织为库-词条两级、以启用状态治理生效面，并保证「仅启用词条参与扩展」与 term_count 守恒——检索侧只调 expandSynonyms 拿追加词，治理细节不可见。

**Input / Output（按方法）:**
- `listLibraries() → TermLibrary[]`；`createLibrary({name, description}) → TermLibrary`（term_count=0 起步）；`updateLibrary(id, {name?, description?}) → TermLibrary|null`（查无返回 null）；`deleteLibrary(id) → void`（级联删除其下全部词条+缓存失效）〔证据：TermServiceImpl.java:37-69 ‹CERTAIN›〕
- `listTerms(libraryId: string, 可选) → TermRecord[]`；`createTerm({term, alias?, definition?, status?, libraryId}) → TermRecord`（status 缺省 1，term_count +1，缓存失效）；`updateTerm(id, fields) → TermRecord|null`（libraryId 变更时两库计数联动；status 兼容布尔/数字）；`deleteTerm(id) → void`（term_count −1 钳非负，缓存失效）〔:72-135 ‹CERTAIN›〕
- `expandSynonyms(query: string, 可空—空返回空列表) → List<String>`——仅启用词条；query 含术语 ⇒ 追加其全部未在 query 中的别名；query 含别名 ⇒ 追加该术语；别名按中英文逗号切分；LinkedHashSet 去重保序〔:27-34,138-176 ‹CERTAIN›〕

**Error modes:**
- 无显式错误模式——空/null 查询返回空列表；updateLibrary/updateTerm 查无返回 null；不存在的 libraryId 创建词条照常插入（无归属校验）。〔:139-141,56,79-90 结构观测 ‹CERTAIN›〕
- 重复词条与超长 alias（R4 决议 2026-09-24）：取「**不去重、不截断**」——同库重复 term/alias 允许共存（无唯一约束），扩展时 LinkedHashSet 去重兜底保证返回值无重复（:144-175 已有）；超长 alias（>256）依赖 MySQL strict mode 落库报错，应用层不预截断（截断会静默丢失别名语义）；理由：去重在消费侧已有兜底、预截断引入静默错误，二者均无实现收益；现状差距：无（与现状一致）；如需治理面强校验属未来增强（代裁，用户可推翻）。

**Invariants:**
- Pre：expandSynonyms 的候选集恒为 term_record.status=1（启用词条缓存）——与 `## retrieval` 节 QueryEnhancePipeline 消费口径同源。〔:30 ‹CERTAIN›〕
- Post：任意词条写操作（增/改/移库/删/删库）后缓存失效，扩展面即时反映治理结果。〔:68,88,107,117 ‹CERTAIN›〕
- Post：term_library.term_count 守恒——create +1、delete −1、跨库移动一减一增，且钳制 ≥0（Math.max）；计数与实际词条数可能漂移（直接 SQL 删数据时无联动），计数非权威〔:120-128 ‹CERTAIN›；「非权威」‹CERTAIN（2026-09-24 深证）：TermServiceImpl 全文无任何对账/recount 调用，计数仅在增删移三路径手工维护，漂移后无自愈机制›〕。
- Post：expandSynonyms 返回值不含原查询片段、不修改原查询；追加词按发现序去重。〔:144-175 ‹CERTAIN›〕

**Parent Trace:** 接口 platform-config.TermSynonymService → Design.TermSynonymModule + 缝 3 → Spec REQ-I01~I05；消费方 QueryEnhanceServiceImpl.expandSynonyms（`## retrieval` 节，interfaces.md:770 消费登记）。

### 消费点登记（platform-config 数据的跨链消费方，消费行为归消费方链）

| 消费方 | 消费口径 | 位置 |
|---|---|---|
| fastrag-ai 各消费链（模型路由） | `code=? AND status='online' LIMIT 1` 解析 apiUrl/apiKeyRef，null 下传默认网关 | AiChunkServiceImpl.java:1582-1592、ChunkingServiceImpl.java:236-246 等 ‹CERTAIN› |
| benchmark-evaluation（模型路由·例外口径） | 裸 SQL `SELECT api_url, api_key_ref FROM model WHERE code=? LIMIT 1`——**无 status 过滤**（上游已登记，本链核对属实） | BenchmarkGenerationHelper.java:196 ‹CERTAIN› |
| retrieval（同义扩展） | TermService.expandSynonyms 仅启用词条供给 | QueryEnhanceServiceImpl.java:52,79-89 ‹CERTAIN› |
| retrieval（查询规则·占位） | applyQueryRules 占位、query_rule 零消费 | QueryEnhanceServiceImpl.java:91-97 ‹CERTAIN› |
| parse-strategy（系统默认层） | ConfigManageService.getConfig 读 general_chunk_size/general_chunk_overlap | StrategyConfigResolver.java:65-67,179-189 ‹CERTAIN› |
| app-center（内容安全） | checkInput（:394）/filterOutput（:524,673），isSafetyEnabled 应用级开关在消费方 | AppServiceImpl.java:394,524,673 ‹CERTAIN› |
| knowledge（通知生产） | 摄取完成写 SysNotification（notify_type=knowledge_update，target_user 空=广播） | IngestionConsumer.java:470-481 ‹CERTAIN› |
| publish-review（开关存储） | sys_config 六键读写，无后端 gate 消费 | ConfigManageController.java:72-86 ‹CERTAIN› |
| operation（模型监控） | ModelMonitorServiceImpl 读 model_call_log——**全仓无写入方，数据源死表** | ModelMonitorServiceImpl.java:34,46-49 ‹CERTAIN›；grep 写入方实测 ‹CERTAIN› |

**登记债务（实现侧现状，修复以本登记为准）**：① presets 四端点假成功（create/update 仅回显不落库、deletePreset 空实现、list 硬编码，ModelServiceImpl.java:241-259）；② train/test 假成功（硬编码 metrics/score，ConfigManageServiceImpl.java:15-30）；③ resetToDefault 死逻辑（上文 Invariants）；④ model_call_log 无写入方且 DDL status 列重复定义（schema.sql:559-568，MySQL 下该 CREATE 必失败，SchemaInitializer 也无兜底建表）；⑤ model 表 context_window/enable_thinking 两列仅在 SchemaInitializer:280-281 增补、schema.sql 基线缺失（DDL 双处漂移）；⑥ Notification/Dictionary/Term/QueryRule/Upload 五控制器写操作无 @Loggable 审计（与 Model/ConfigManage 不对称）。

**Parent Trace:** 消费点登记 → Design 缝 1/2/3/4 → Spec REQ-C01~C03、REQ-H04~H05、REQ-I04、REQ-E01；债务登记 → Spec REQ-A06、REQ-D01~D02、REQ-E05、REQ-C03、REQ-H08 的现状口径断言。

---

## tools-skills-mcp

> 对应 design 模块：ToolRegistryModule / ToolExecutorRouter / McpModule / SkillModule / SkillFileModule / DbInstanceModule。数据实体契约见 data.md `## tools-skills-mcp` 节。
> 端点排除：ToolController（11 端点）/ SkillController（24 端点）/ McpController（17 端点）/ DbInstanceController（10 端点）全部为所属模块实现细节，不写契约（HTTP 暴露面见 design/tools-skills-mcp.md §2）；HTTP 工具域（CRUD+test-proxy）与 MCP 管理域的消费面仅在 design 立面，契约只写给五个深模块接口。鉴权复用 `## iam` 节 iam.SecurityGate 与 DataScope 判定协议（本节 Invariants 引用，不重复定义）。

### tools-skills-mcp.ToolRegistry（工具解析契约）

**Purpose:** 能力底座对消费方（fastrag-agent / app-center 绑定）的唯一出口：把「一串混合格式的工具 ID」折叠为一组同构的 ToolDefinition，使消费方对能力类型（HTTP/MCP/知识库/内置）的存储差异零感知。〔2026-10-06 修订：技能退出本契约——技能运行时收敛为 SkillsMiddleware 三段式渐进加载（prompt 注入 → read_file 拦截读全文 → 依赖闭包），不再以 ToolDefinition 形态经过 ToolRegistry；裁决见 docs/plan/skill-runtime-remediation.md〕

**Input / Output（按方法）:**
- `resolveTools(toolIds: List<String>, 可空) → List<ToolDefinition{toolId, name, description, type, inputSchema, outputSchema?, outputMapping?, config}>`；结果恒以 install_skill 内置定义开头；前缀 `mcp_`/`mcp:` → MCP 解析，~~`skill_`/`skill:` → 技能解析（先 ID 后 slug）~~（2026-10-06 删除，M3-6），其余按自定义工具解析；单个 ID 解析失败仅告警跳过〔证据：ToolRegistry.java:27-58 ‹CERTAIN›〕
- `toOpenAITools(tools: List<ToolDefinition>) → List<{type:"function", function:{name, description, parameters}}>`；inputSchema 含 `type` 键直接用、仅含 `properties` 则包 `type:object`、否则给空 Schema〔ToolRegistry.java:163-187 ‹CERTAIN›〕

**Error modes:**
- E_TOOL_UNRESOLVABLE — 触发：ID 查无记录、资源未启用（enabled≠1）、MCP 服务已删、数字 ID 格式非法（`Long.parseLong` 抛出）。恢复：该定义缺失、其余照常（log.warn）。〔ToolRegistry.java:40-42,62,92,91 ‹CERTAIN›〕
- E_EXECUTOR_NOT_FOUND — 触发：type 无匹配执行器（getExecutor 返回 null）。恢复：调用方（agent 循环）跳过该调用。〔ToolExecutorFactory.java:30-35 ‹CERTAIN›〕

**Invariants:**
- Pre：resolveTools 前调用方已过 iam.SecurityGate 认证；解析不校验用户对工具的可见性（配置面已过滤，运行面信任 toolIds 来源）‹CERTAIN（2026-09-24 深证）：ToolRegistry.java:27-58 全文无 SecurityUtil/DataScope/userId 任何引用，解析纯按 ID/前缀查库›。
- Post：仅 enabled=1 的 tool / mcp_tool 进入结果（~~skill~~ 2026-10-06 起退出本契约）；MCP 定义 config 必含 mcpServiceId/transport/rawName〔~~技能定义 config 必含 slug/content/dependencies~~ 随 skill 路由删除废止〕。
- Post：install_skill 定义在解析结果中恒出现且唯一（静态单例定义）；**消费侧不变式（2026-10-06 修订，M3-3）**：引擎装配 openai 工具列表时按条件剔除——仅当本次上下文存在技能挂载（context.skills 非空）或沙箱启用时保留（spec REQ-E03 已同步修订）〔ToolRegistry.java:30-31、AgentEngine 引擎侧过滤 ‹CERTAIN›〕。

**Parent Trace:** 接口 tools-skills-mcp.ToolRegistry → Design.ToolRegistryModule（缝 3 生产方）→ REQ-E01、REQ-E02、REQ-E03。

### tools-skills-mcp.ToolExecutorRouter（执行路由契约）

**Purpose:** 执行形态的分派协议：把「按类型选择执行器」与「执行结果统一封装」固化为两个稳定原语，使新工具类型接入不触碰消费循环。

**Input:**
- `getExecutor(type: String, 必填) → ToolExecutor|null`（http/mcp/knowledge/builtin 四型注册〔~~skill~~ 2026-10-06 删除（M3-6），REQ-E04 五类收敛四类〕；未知型返回 null）〔证据：ToolExecutorFactory.java:28-35、ToolExecutorFactory javadoc :9-16 ‹CERTAIN›〕
- `execute(tool: ToolDefinition, 必填; arguments: Map, 可空; ctx: ToolContext{userId,appId,runId,query,extensions}, 可空) → ToolResult{success, output, structured?, durationMs, error?}`〔ToolExecutor.java:15-18、ToolContext.java:9-26、ToolResult.java:20-38 ‹CERTAIN›〕

**Output:** ToolResult——success=true 时 output 为 LLM 可读文本；success=false 时 error 必填、durationMs 已计；执行器内部异常一律捕获转 error 结果，不向路由层外抛。〔HttpToolExecutor.java:119-126、McpToolExecutor.java:95-103、~~SkillToolExecutor.java:64-67~~（已删除）、KnowledgeToolExecutor.java:47-50 ‹CERTAIN›〕

**Error modes:**
- E_MISSING_PARAM — 触发：~~skill 执行缺 action 参数~~（2026-10-06 随 SkillToolExecutor 删除）/ knowledge query_kb 缺 kb_id 或 query。恢复：error 结果说明缺失项，success=false。〔KnowledgeToolExecutor.java:80-82 ‹CERTAIN›〕
- E_UNKNOWN_ACTION — 触发：action/工具名不在支持集合。恢复：error 结果。〔KnowledgeToolExecutor.java:45 ‹CERTAIN›；~~SkillToolExecutor.java:62~~ 已删除〕

**Invariants:**
- Post：任何执行器对同一 (tool, arguments, ctx) 不抛异常——全部以 ToolResult 承载失败〔四执行器 try/catch 实测 ‹CERTAIN›〕。
- Post：MCP 执行成败均写 mcp_call_log（status=success/error）并回写服务 lastUsed，日志写失败不影响执行结果〔McpToolExecutor.java:83-100,106-120 ‹CERTAIN›〕。

**Parent Trace:** 接口 tools-skills-mcp.ToolExecutorRouter → Design.ToolExecutorRouter（缝 1）→ REQ-E04、REQ-C08、REQ-E05。

### tools-skills-mcp.McpProtocolClient（MCP 协议客户端契约）

**Purpose:** 系统边界（外部 MCP server）的唯一触点：把 JSON-RPC 握手、会话保持、SSE 响应格式差异折叠为「连接→列工具→调工具」三步生命周期，供管理面（解析/刷新/测试）与运行面（执行器）共用。

**Input / Output（按方法）:**
- `connect() → this`；执行 initialize（协议版本 2025-11-25、clientInfo=fastrag/1.0.0）→ 提取 mcp-session-id（响应头优先、result.sessionId 回退、都无则无会话）→ 发 initialized 通知（fire-and-forget，失败仅告警）；transport=stdio 直接抛 UnsupportedOperationException；mcpUrl 空白抛 IllegalArgumentException〔证据：McpProtocolClient.java:28,65-120,70-72,74-76 ‹CERTAIN›〕
- `listTools() → List<McpToolInfo{name, description, inputSchema}>`；未连接抛 IllegalStateException〔:127-160,362-366 ‹CERTAIN›〕
- `callTool(toolName: string, 必填; arguments: Map, 可空) → String`（拼接 content 数组全部 text 项，空则 result.text 或 `[no text content]`）〔:165-198 ‹CERTAIN›〕
- `close() → void`（释放引用，幂等）〔:370-377 ‹CERTAIN›〕

**Error modes:**
- E_MCP_HTTP_ERROR — 触发：非 2xx 响应或空响应体。恢复：RuntimeException 上抛，调用方据此置 status=error / 记失败日志。〔:253-261 ‹CERTAIN›〕
- E_MCP_RPC_ERROR — 触发：JSON-RPC 响应含 error 字段。恢复：以 error.message 上抛。〔:269-273 ‹CERTAIN›〕
- E_MCP_MALFORMED — 触发：响应缺 result 字段 / JSON 解析失败。恢复：上抛。〔:276-278,284-286 ‹CERTAIN›〕
- E_MCP_TIMEOUT — 触发：单请求超时（缺省 30s，构造可调）。恢复：同上抛路径。〔:56,233 ‹CERTAIN›〕

**Invariants:**
- Pre：listTools/callTool 必须在 connect 成功后调用。
- Post：会话建立后所有后续请求（含通知）携带 `mcp-session-id` 头；请求头恒含自定义 headers（认证 bearer/basic 在调用方构造）与 `Accept: application/json, text/event-stream`〔:113-114,240-242,347-350,231-237 ‹CERTAIN›〕。
- Post：SSE 格式响应体（非 `{` 开头）按 data: 行提取 JSON，提取失败回退原始体〔:293-321 ‹CERTAIN›〕。
- 已知限制（登记债务）：stdio 传输仅有拒绝桩，无本地进程实现〔:70-72 ‹CERTAIN›；design §3 缝 2 GAP〕。

**Parent Trace:** 接口 tools-skills-mcp.McpProtocolClient → Design.McpProtocolClient（缝 2）→ REQ-C02、REQ-C03、REQ-C09。

### tools-skills-mcp.SkillFileVault（技能文件库契约）

**Purpose:** 技能「库记录」之外的磁盘形态唯一读写口：把目录布局（`{data-dir}/skills/{slug}`）、路径遍历防护、ZIP 导入导出与安装草稿生命周期封装为九个方法，控制器与安装流水线共享同一安全语义。

**Input / Output（按方法）:**
- `getTree(slug) → SkillFileTree{name,isDir,size,lastModified,children[]}`（目录不存在返回空根树；隐藏文件过滤）〔证据：SkillFileServiceImpl.java:68-79,95-97 ‹CERTAIN›〕
- `readFile(slug, path) → SkillFileContent{path,content,size,lastModified,exists,error?}`（不存在/目录返回 exists=false；路径越界返回 error 而非抛）〔:110-135 ‹CERTAIN›〕
- `createNode(slug, path, isDir, content, operator)`（目标已存在抛错；目录自动建父）〔:138-159 ‹CERTAIN›〕
- `updateFile(slug, path, content, operator)`（不存在或为目录抛错）〔:162-177 ‹CERTAIN›〕
- `deleteNode(slug, path, operator)`（目录递归删；根目录禁止删）〔:180-213,188-190 ‹CERTAIN›〕
- `exportZip(slug) → File`（临时 ZIP，排除隐藏项；调用方负责用后删除）〔:216-247、SkillController.java:250-252 ‹CERTAIN›〕
- `prepareUpload(filename, bytes, operator) → SkillInstallDraft{draftId, sourceType, source, items[], expiresAt, createdBy}`（仅收 .zip/.md/.markdown；无 SKILL.md 报错；草稿 TTL 1h；上传时惰性清理过期草稿）〔SkillDraftServiceImpl.java:41,54-108,387-408 ‹CERTAIN›〕
- `confirmDraft(draftId, shareConfig, operator) → List<DraftItem{slug,name,description,success,errorMsg?,metadata?}>`（过期草稿先丢弃再报错；slug 已存在覆盖重装；逐技能独立成败；成功后草稿目录删除）〔:110-225,122-127,160-169,215-216 ‹CERTAIN›〕
- `discardDraft(draftId, operator) → void`（幂等）〔:227-238 ‹CERTAIN›〕

**Error modes:**
- E_PATH_TRAVERSAL — 触发：resolve+normalize 后越出技能根目录（文件操作）或 ZIP 条目含 `..`/绝对路径/越界。恢复：SecurityException 拒绝（readFile 转 error 字段，其余上抛）。〔SkillFileServiceImpl.java:58-65、SkillDraftServiceImpl.java:247-254 ‹CERTAIN›〕
- E_SKILL_MD_INVALID — 触发：SKILL.md 缺失/无 frontmatter/缺 name 或 description/YAML 非法。恢复：prepareUpload 报错中止；confirmDraft 记入该项 DraftItem.errorMsg 继续其余。〔SkillMarkdownParser.java:29-31,36,65-70、SkillDraftServiceImpl.java:150-154,206-210 ‹CERTAIN›〕
- E_DRAFT_EXPIRED — 触发：confirm 时 expiresAt 已过。恢复：草稿删除 + 报「草稿已过期」。〔:122-127 ‹CERTAIN›〕
- E_DRAFT_NOT_FOUND — 触发：draftId 目录或 metadata.json 不存在。恢复：报错。〔:117-119,351-357 ‹CERTAIN›〕
- E_SLUG_INVALID — 触发：slug 不匹配白名单 `^[a-z0-9][a-z0-9._-]{0,63}$`（创建/导入/文件访问/草稿四入口统一强制；frontmatter slug 缺省由 name 派生，无法派生报错提示显式提供）。恢复：400 语义 BusinessException，消息含规则与实际值。〔2026-10-06 M1-2 增补 ‹CERTAIN›〕
- E_DRAFT_ID_INVALID — 触发：draftId 非 UUID 严格格式（confirm/discard/load 三路径，比 slug 白名单更严——draftId 本就由 UUID 生成）。恢复：报「非法的草稿 ID」。〔2026-10-06 M1-2 增补 ‹CERTAIN›〕
- E_SKILL_NOT_FOUND / E_SKILL_FORBIDDEN — 触发：slug 查无记录（404 语义）/ 当前用户对技能不可见（读类）或无管理权（写删类）（403 语义，DataScope 判定协议，同 `## iam`）。恢复：BusinessException 上抛。〔2026-10-06 M1-1 增补 ‹CERTAIN›〕

**Invariants:**
- Pre：文件与解压两路径共用 normalize 前缀校验，先校验后落盘〔双检实测 ‹CERTAIN›〕。
- Pre（2026-10-06 M1-1 增补，鉴权前置不变式）：读类方法（getTree/readFile/exportZip）要求 DataScope.visible(creator/orgId/isBuiltin) 或分享可见（REQ-B09 三级）；写类方法（createNode/updateFile/deleteNode）要求 DataScope.manageable(creator)；user 取 SecurityContext，operator 参数仅作日志不参与鉴权（防伪造）。
- Post：安装成功 ⇒ skill 表新记录（sourceType=upload、contentHash=SKILL.md MD5、enabled=1、**creator=operator 且 orgId 取当前用户（2026-10-06 M1-3）**）+ 磁盘 `{skills}/{slug}/` 目录就位，两者同时存在；~~覆盖重装 ⇒ 旧目录与旧记录（含级联）先删~~（2026-10-06 M3-5 起改为 **update-in-place 保留主键**，name/description/content/contentHash/version/category/metadata/author 随解析更新，app_skill_binding 因 id 不变存活）〔:180-197,166-168 ‹CERTAIN›〕。
- Post（2026-10-06 M3-4 增补，正文事实源）：根 SKILL.md 的 createNode/updateFile 成功 ⇒ DB skill.content/contentHash 与 frontmatter 元数据列同步更新（先解析后落盘，解析失败文件不写入）；结合启动播种「无记录才插入」语义（data.md skill Lifecycle），DB content 为唯一正文事实源。
- Post：confirmDraft 结束 ⇒ 草稿目录必不存在（成功清理 / 过期丢弃）；draftId 由 UUID 生成不可预测〔:57,215-216 ‹CERTAIN›〕。
- 已知脱节（spec REQ-B07 INFERRED 关联）：控制器将 prepare+confirm 串接为单步「上传即安装」且 shareConfig 固定 `accessLevel=user`，两步草稿端点成为冗余面〔SkillController.java:257-271 ‹CERTAIN›〕。

**Parent Trace:** 接口 tools-skills-mcp.SkillFileVault → Design.SkillFileModule → REQ-B06、REQ-B07。

### tools-skills-mcp.DbQueryGateway（外部数据库网关契约）

**Purpose:** 外部数据库挂载的唯一执行通道：把「连接管理 → 连通验证 → 元数据同步 → 受限查询」收敛为一个网关面，使只读安全约束与连接池生命周期不被调用方绕过。

**Input / Output（按方法）:**
- `list(keyword?, dbType?) → List<DbInstance>`（非属主密码置 `******`、jdbcUrl 置 null）〔证据：DbInstanceServiceImpl.java:57-71,48-54 ‹CERTAIN›〕
- `get(id) → DbInstance`（不可见即 403 语义）〔:73-80 ‹CERTAIN›〕
- `create(db) → DbInstance`（缺省 readOnly=1、status=disconnected、jdbcUrl 按类型自动拼接）〔:83-95 ‹CERTAIN›〕
- `update(id, db) → DbInstance`（连接三要素 host/port/dbName 变化时重拼 URL）〔:98-110 ‹CERTAIN›〕
- `delete(id) → void`（级联删 db_table）〔:124-132 ‹CERTAIN›〕
- `listTables(dbId) → List<DbTable>`（按表名排序）〔:135-141 ‹CERTAIN›〕
- `createTable(t: DbTable) → DbTable`（登记元数据，缺省 enabled=1）〔:144-148 ‹CERTAIN›〕
- `testConnection(dbId) → {connected:bool, latencyMs, error?}`（执行 `SELECT 1`；回写 status=connected/disconnected）〔:151-189 ‹CERTAIN›〕
- `query(dbId, sql: string, 必填) → {columns: string[], rows: Map[], rowCount, elapsedMs, error?}`（行上限 500）〔:192-259,34-35 ‹CERTAIN›〕
- `syncTables(dbId) → {syncedCount, error?}`（DatabaseMetaData 全表 upsert，同库同表名唯一）〔:264-358,313-335 ‹CERTAIN›〕

**Error modes:**
- E_SQL_NOT_READONLY — 触发：语句前缀非 SELECT/SHOW/DESCRIBE/DESC/EXPLAIN。恢复：result.error 说明仅允许只读语句，columns/rows 空、不抛异常。〔:199-205,418-426 ‹CERTAIN›〕
- E_SQL_EXECUTE_FAILED — 触发：目标库连接失败/语法错/超时（连接 5s）。恢复：result.error 携带底层消息 + status 回写 disconnected。〔:249-257,177-188 ‹CERTAIN›〕
- E_DB_NO_MANAGE — 触发：非属主发起 query/syncTables/update/delete。恢复：BusinessException 403 语义。〔:42-46,194,267 ‹CERTAIN›〕
- E_DB_NOT_FOUND — 触发：id 查无（可见性校验先于 null 判定的分支返回 403 语义）。恢复：无。〔:77,155-159 ‹CERTAIN›〕

**Invariants:**
- Pre：query/syncTables 必须属主；list/get/testConnection/listTables 只需可见（DataScope 协议，见 `## iam`）。
- Post：查询连接恒为动态 HikariCP（max 3 / min idle 1 / 5s 超时 / readOnly 按 db.readOnly），用后关闭，不常驻〔:365-382,215 ‹CERTAIN›〕。
- Post：syncTables 幂等——重复同步不产生重复 db_table 记录，columns/rowCount/syncedAt 刷新〔:313-335 ‹CERTAIN›〕。
- Post：`SELECT COUNT(*)` 行数统计的表名来自 DatabaseMetaData 返回值并以反引号包裹，非用户输入〔:303-304 ‹CERTAIN›；SQL 注入面收敛于 query 入口的前缀白名单〕。
- 已知限制（登记债务）：只读白名单为前缀匹配，不解析注释/子查询——前导空白由 trim 处理，`/*...*/` 前缀可绕过前缀判断‹CERTAIN（2026-09-24 深证）：isReadOnlySql 仅 toUpperCase+trim 后 startsWith 大写比对（DbInstanceServiceImpl.java:418-426 实读确认），绕过路径成立；深层防御依赖 Hikari readOnly 连接属性与目标库账号权限›。〔决议 2026-09-24：生产外部库账号取「**部署规程强制只读账号**」——连接层 readOnly 属性为软约束（依赖驱动支持），真正的写封锁须由目标库账号权限保证，属运维交付物而非代码保证；理由：业界外部库挂接惯例（最小权限账号）+ 代码层无法验证远端账号；现状差距：无部署校验机制，账号权限合规性依赖人工保障（代裁，用户可推翻）〕

**Parent Trace:** 接口 tools-skills-mcp.DbQueryGateway → Design.DbInstanceModule → REQ-D01~D06。

---

## workflow

> 对应 design 模块：WorkflowLifecycleModule / CanvasNodeModule / WorkflowExecutionEngine / CanvasStructureValidator / WfAssetModule / WfObservabilityStub 六模块。数据实体契约见 data.md `## workflow` 节。
> **端点排除**：WorkflowController 32 端点（实测：feature-map 记 ~35）中，工作流 CRUD+publish 六端点、测试用例/模板/优化/迁移四组台账端点、调试与监控两占位端点为实现细节或台账包装不立契约；HTTP 暴露面清单见 design/workflow.md §2。**特性现状定性**：管理面为真实落库实现，执行引擎为占位实现（WorkflowServiceImpl.java:28-29，占位行为被 AppModuleValidationTest TC-MOCK-002/003 源码级断言固化，:318-333）——契约按现状如实书写，引擎实装时本节即为翻版基线。
> **跨链登记（app-center 消费点，消费行为归 app-center 链）**：app.type 可为 'workflow'（App.java:14 javadoc）；app_trigger.action_type='workflow' 触发时只回写触发台账**不调用** WorkflowService.execute（AppConfigServiceImpl.java:80-84）；app_basic_config.advancedOptions.workflow 段读写（AppConfigServiceImpl.java:254-270）。

### workflow.CanvasNodeStore（画布节点存储契约）

**Purpose:** 画布节点的唯一持久化口：把「工作流图的一行节点」的生命周期（放置/更新/移动/移除/配置读写）折叠为按 (workflowId, nodeKey) 复合键的七个小操作——复合定位、唯一键兜底、配置单列折叠语义藏在操作之后，前端画布与未来的引擎都只面对节点行。

**Input / Output（按方法）:**
- `addNode(wfId: string, 必填; nodeKey: string, 必填—同工作流唯一; nodeType: string, 必填—无值域校验; name: string, 可选; x/y: int, 可选缺省 0—控制器侧 Number 判型) → WfNode{enabled=1}`〔证据：WorkflowController.java:54、WorkflowServiceImpl.java:20-22 ‹CERTAIN›〕
- `updateNode(wfId, nodeKey: string, 均必填; node: WfNode—全量实体) → WfNode|null`（按复合键条件更新非空字段后回查；查无返回 null）〔:23 ‹CERTAIN›〕
- `deleteNode(wfId, nodeKey) → void`（物理删，查无不报错）〔:24 ‹CERTAIN›〕
- `moveNode(wfId, nodeKey, x: int, y: int—缺省 0) → WfNode|null`（仅改坐标）〔:25、WorkflowController.java:65 ‹CERTAIN›〕
- `listNodes(wfId) → WfNode[]`（created_at 升序）〔:26 ‹CERTAIN›〕
- `getNodeConfig(wfId, nodeKey) → Map{config: string}`（config 为存储原串；节点无或 config 空 → "{}"）〔:56 ‹CERTAIN›〕
- `saveNodeConfig(wfId, nodeKey, dimension: string, config: Map) → {nodeKey, dimension, saved:true}`——**dimension 不参与存储**：任意维度保存整体覆盖 wf_node.config 单列；序列化为 Java Map.toString()（`{k=v}`）**非 JSON**；节点不存在时静默不写但仍返回 saved:true〔:57-61 ‹CERTAIN›〕

**Error modes:**
- E_NODE_KEY_DUPLICATE — 触发：同工作流添加重复 nodeKey（应用层不查重）。恢复：数据库唯一索引 idx_wf_key 拒绝，DB 异常经 GlobalExceptionHandler 兜底为「服务异常」（无专门错误码）。〔schema.sql:1194、WorkflowServiceImpl.java:21 无前置查重 ‹CERTAIN›〕
- 无显式错误模式 — updateNode/moveNode/getNodeConfig 对不存在的 (wfId,nodeKey) 静默返回 null/空，saveNodeConfig 静默不写。〔:23,25,56-61 结构观测 ‹CERTAIN›〕

**Invariants:**
- Pre：节点行的 (workflowId, nodeKey) 全库唯一（DDL 唯一索引，应用层不维护）。
- Post：任意维度保存 ⇒ 先保存维度的内容被整体冲掉（单列覆盖），wf_node.config 恒为最近一次保存的 Map.toString() 串——**维度不是存储概念，只是 API 形状**。〔:57-59 ‹CERTAIN›；spec REQ-B04〕
- Post：节点行与 workflow.nodes/edges 画布 JSON 双轨并存、互不同步——增删节点行不改画布 JSON，保存画布 JSON 不改节点行。〔WorkflowServiceImpl.java:16 vs :20-26 ‹CERTAIN›；spec REQ-B03〕
- 已知偏离（登记债务）：① config 序列化非 JSON（spec REQ-B05）；② E_NODE_KEY_DUPLICATE 无业务错误码、以 500 语义兜底形态出现；③ saveNodeConfig 节点不存在仍报 saved:true（假成功）。

**Parent Trace:** 接口 workflow.CanvasNodeStore → Design.CanvasNodeModule → Spec REQ-B01~B06。

### workflow.WorkflowExecutionEngine（工作流执行引擎契约 — 占位契约）

**Purpose:** 工作流「可执行」承诺的总闸：把「触发一次工作流/试运行一个节点」折叠为两个方法，节点遍历、类型路由、变量传递、错误传播全部藏在其后——**现状为占位实现**：两方法各自返回硬编码完成结构，不读取工作流定义、不遍历节点、不调用 fastrag-ai/retrieval 任何上游。本契约会写字现状，同时锁定引擎实装不得破坏的调用面。

**Input / Output（按方法）:**
- `execute(wfId: string, 必填; inputs: Map, 可选—内容不参与任何逻辑) → Map{status:"completed", output:"执行完成", executionId:"exe_"+8位随机}`——wfId 不存在同样返回完成（不校验工作流存在性）；executionId 不落库〔证据：WorkflowServiceImpl.java:28 ‹CERTAIN›；占位断言 AppModuleValidationTest.java:318-324 ‹CERTAIN›〕
- `executeNode(wfId, nodeKey: string, 均必填; inputs: Map, 可选) → Map{status:"completed", nodeKey, output:"节点执行完成"}`——不校验节点存在性，nodeKey 原样回显〔:29 ‹CERTAIN›〕

**Error modes:**
- 无错误模式 — 两方法对任意输入（含不存在的工作流/节点、空 inputs）恒成功返回，无异常路径、无失败语义。〔:28-29 结构观测 ‹CERTAIN›〕
- **目标语义（2026-09-24 代裁定稿）**：〔决议 2026-09-24：①错误传播 = **fail-fast 单节点**——任一节点失败即中断整图，失败节点记 wf_debug_log error 行后终态 failed，不做跳过/降级；②终止性 = 拓扑序单遍遍历 + 防御性步数上限 = 图节点数（当前值域 8 类无循环节点，天然有限步；成环图由 CanvasStructureValidator 在 publish/execute 前硬失败拒绝）；③超时 = 单节点 30s、整图 300s（与 app_basic_config.timeout_seconds 缺省 30 同源的保守默认）；④并发 = 不设限（同步阻塞形态，调用方节流）。理由：与 agent 引擎 max-steps 同源的最小语义；现状差距：引擎占位未实装（REQ-C01 债务）；**引擎实装时若取不同语义须回改本契约**（代裁，用户可推翻）〕目标用例族见 tdd/workflow.md §2 E1~E5（已按本裁决填实）。

**Invariants:**
- Pre：无前置校验（不查工作流/节点存在性、不查发布状态、无权限外校验）。
- Post：执行不产生任何持久化痕迹——无执行历史表写入、wf_debug_log/wf_monitor_data 零写入（两表为死表）、workflow 定义不变。
- Post：executionId 仅存在于返回值，跨请求无关联。
- 引擎实装门禁（本契约的翻版条件）：① 占位断言测试（TC-MOCK-002/003）必须翻红并替换为真实语义用例；② 本节 Invariants 中「无持久化痕迹」等占位条款随实装重写；③ 缝 2/3（ModelRegistry 模型路由、RetrievalSearchEngine 检索面）消费登记补入本节。

**Parent Trace:** 接口 workflow.WorkflowExecutionEngine → Design.WorkflowExecutionEngine（缝 1 规划缝、缝 4 登记缝）→ Spec REQ-C01~C05；数据面 → data.md `## workflow` 节 wf_debug_log/wf_monitor_data（死表登记）。

---

## agent

> 对应 design 模块：ExecutionEngineModule / RunOrchestrationModule / MiddlewareChainModule / ContextBackendModule / 沙箱模块族 SandboxConfigModule + SandboxLifecycleModule + SandboxProviderClient + SandboxExecutionModule（design/agent.md §1，2026-09-28 重写；SandboxWorkspaceLayout 与 EndpointDialGuard 两个小承重件不立契约，语义并入下列接口 Invariants——沿端点排除规则）。数据实体契约见 data.md `## agent` 节。
> **端点排除**：AgentController（7 端点）/ AgentConfigController（2 端点）/ AgentRunController（5 端点）共 11 个 HTTP 端点为 AgentRegistryModule 与 RunOrchestrationModule 的实现细节，不立契约（HTTP 暴露面见 design/agent.md §2 末）；智能体 CRUD/可见性为薄台账逻辑，契约只写给五个深模块接口。
> **跨链登记（app-center/application 消费点，消费行为归应用域链）**：AppServiceImpl（fastrag-application）:665 构造 fakeRun（sessionId 冒充 runId、不落 agent_run）调用 agent.ExecutionEngine.executeStream——应用对话走引擎流式模式但**不产生**运行记录、不过 RunOrchestrationModule 状态机〔AppServiceImpl.java:642-669 ‹CERTAIN›〕。

### agent.ExecutionEngine（执行引擎契约）

**Purpose:** 智能体自主性的总闸：调用方给一份上下文与（流式模式下的）SSE 通道，即获得「LLM↔工具」多轮循环直至最终答案的完整能力——工具分派、JSON 修复、thinking 提取、失败熔断、事件词汇全部在其后。

**Input / Output（按方法）:**
- `executeStream(context: BaseContext, 必填—需含 systemPrompt/tools/modelConfig; emitter: SseEmitter, 必填; modelConfig: ModelConfig, 必填; run: AgentRun, 可空—null 时仅少事件留痕; onComplete: StreamCompleteCallback(answer, thinkingContent, toolCallsJson), 可空) → void`；事件序列 init → (thinking/message/tool_call/tool_result/agent_state)* → end｜error〔证据：AgentEngine.java:112-116,139-377,862-868 ‹CERTAIN›〕
- `executeSync(run: AgentRun, 必填—id 与 inputPayload.query 参与执行与留痕; context: BaseContext, 必填) → AgentResult{success, answer, toolCallRecords[{toolName,success,output,error,durationMs}], totalDurationMs, error?}`〔:395-507、AgentResult.java ‹CERTAIN›〕

**Error modes:**
- E_ENGINE_STREAM_TIMEOUT — 触发：单次 LLM 流式调用超过 ai.gateway.stream-timeout（默认 240s）。恢复：RuntimeException 中止循环，executeStream 推 error 事件后 complete；executeSync 转 AgentResult.error。〔AgentEngine.java:571-583,372-382,501-506 ‹CERTAIN›〕
- E_ENGINE_MAX_ITERATIONS — 触发：循环达 maxSteps（context 覆盖或默认 15）仍无最终回答。恢复：返回兜底文案（流式模式补发 content 事件）而非报错。〔:90,149-151,343-349,426-427 ‹CERTAIN›〕
- E_TOOL_ARGS_INVALID — 触发：工具参数 JSON 解析失败（含自动修复后仍失败）或 Schema 验证不过。恢复：ToolResult.error 回灌消息历史，循环继续。〔:599-634 ‹CERTAIN›〕
- E_TOOL_NOT_FOUND / E_EXECUTOR_NOT_FOUND — 触发：工具名不在解析集 / 类型无执行器。恢复：错误文本消息回灌，循环继续（上游 tools-skills-mcp.ToolExecutorRouter 的 E_EXECUTOR_NOT_FOUND 在引擎侧的承接形态）。〔:240-262,651-654 ‹CERTAIN›〕
- E_TOOL_FAIL_STORM — 触发：任一工具累计失败 ≥3 次。恢复：向消息历史注入「停止重试」系统提示并清零计数，非终止。〔:318-332 ‹CERTAIN›〕

**Invariants:**
- Pre：context.modelConfig 非空（流式；同步模式缺省时回退 ModelConfig.default_）〔:404-405 ‹CERTAIN›〕。
- Post：工具输出回灌 LLM 前恒截断至 5000 字符；完整输出只出现在事件与留痕中〔:281-289 ‹CERTAIN›〕。
- Post：事件词汇封闭于 {init, message, thinking, tool_call, tool_result, agent_state, end, error}；send 失败仅告警不中断执行〔:862-868 ‹CERTAIN›〕。
- Post：中间件注册的工具在当轮 openaiTools 重建后对 LLM 可见（before 钩子先于 toOpenAITools 二次调用）〔:141-146,417-422 ‹CERTAIN›〕。

**Parent Trace:** 接口 agent.ExecutionEngine → Design.ExecutionEngineModule（缝 1 消费方、缝 2 消费方）→ REQ-E01~E08、REQ-D04。

### agent.AgentRunGateway（运行编排契约）

**Purpose:** 运行生命周期的唯一合法通道：把「创建即幂等、执行即异步、终态有分类、同线程至多一个活跃 run」固化为调用方无须操心的不变量。

**Input / Output（按方法）:**
- `createRun(dto: AgentRunCreateDTO{agentId*, threadId*, query*, requestId?, runType?, modelOverride?}, user) → Map{id, threadId, agentId, status, runType, createdAt}`；requestId 缺省自动生成；命中既有 requestId 直接返回既有运行〔证据：AgentRunServiceImpl.java:64-104 ‹CERTAIN›〕
- `getRun(runId) → AgentRun` / `getRunForUser(runId, user) → AgentRun`（当前与 getRun 等价，user 未参与校验）〔:107-116 ‹CERTAIN›〕
- `cancelRun(runId, user) → Map`：仅 pending/running 可取消→cancelled+finishedAt；否则业务错 400〔:119-129 ‹CERTAIN›〕
- `streamEvents(runId, afterSeq?, user) → SseEmitter`：30 分钟超时；轮询运行状态（2s 间隔）至终态发 end{status,errorType,errorMessage}；run 消失静默完成；afterSeq 当前未消费〔:132-162 ‹CERTAIN›〕
- `getActiveRunByThread(threadId, user) → AgentRun|null`：status IN (pending,running)〔:165-167、AgentRunMapper.java:30-31 ‹CERTAIN›〕
- `updateStatus(runId, status)` / `setTerminalStatus(runId, status, errorType, errorMessage)`：状态写入口（run 不存在仅告警不抛）〔:170-188 ‹CERTAIN›〕
- `createRunDirect(run)`：内部直插（子智能体场景，不触发异步执行）〔:230-240 ‹CERTAIN›〕

**Error modes:**
- E_AGENT_INVISIBLE — 触发：createRun 时智能体不存在（404 语义）或 userCanAccess=false（403 语义）。恢复：BusinessException，HTTP 200 + body.code。〔:66-68 ‹CERTAIN›〕
- E_CANCEL_ILLEGAL_STATE — 触发：取消非 pending/running 的运行。恢复：业务错 400，状态不变。〔:121-123 ‹CERTAIN›〕
- E_RUN_SYSTEM_ERROR — 触发：异步执行链路任一异常（上下文构建/引擎执行）。恢复：终态 failed + errorType=system_error + errorMessage。〔:218-226 ‹CERTAIN›〕
- E_RUN_EXECUTION_ERROR — 触发：引擎返回失败结果。恢复：终态 failed + errorType=execution_error。〔:207-211 ‹CERTAIN›〕
- 已知缺口（登记债务）：① 取消为纯 DB 翻转，无执行线程信号，引擎终态可能覆盖 cancelled（REQ-C04 竞态，2026-09-24 深证坐实）；② getRunForUser 无属主校验（REQ-C08，2026-09-24 深证坐实）。

**Invariants:**
- Pre：createRun 前智能体可见性已过；requestId 全库唯一（uk_request_id）。
- Post：状态机封闭于 {pending, running, completed, failed, cancelled, interrupted}；终态集 = {completed, failed, cancelled, interrupted}〔:253-256 ‹CERTAIN›〕。
- Post：createRun 返回后运行必已落库（pending），执行必在 agentTaskExecutor 线程池〔:96,190-191 ‹CERTAIN›〕。
- 已知竞态：执行线程与 cancelRun 并发写同行的最终状态未加锁，last-write-wins〔‹CERTAIN›—无同步措施；影响判定见 REQ-C04〕。

**Parent Trace:** 接口 agent.AgentRunGateway → Design.RunOrchestrationModule → REQ-C01~C08、REQ-D02~D03。

### agent.MiddlewareChain（中间件链契约）

**Purpose:** 智能体能力的装配协议：横切关注点（工具注册/消息注入/响应修补/调用接管）以统一三阶段 SPI 挂载，链的组装与异常隔离对引擎与中间件双侧透明。

**Input / Output:**
- SPI `AgentMiddleware`：`getOrder() → int`（升序，建议 0-100）/ `beforeModelCall(context, messages, tools) → context`（可改消息、可加工具）/ `afterModelCall(context, response) → context` / `interceptToolCall(context, toolCall) → ToolCallInterceptor|null`〔证据：AgentMiddleware.java:18-78 ‹CERTAIN›〕
- `executeBefore`：按 order **正序**逐个执行；`executeAfter`：**逆序**执行；`getToolCallInterceptor`：返回**第一个**非 null〔AgentMiddlewareChain.java:39-82 ‹CERTAIN›〕
- 内置 12 中间件 order 契约：Context=0（模型路由，当前桩）、Filesystem=1、Attachment=2、KnowledgeBase=3、Skills=4、SubAgent=5、Summarization=6、TodoList=7、PatchToolCalls=8、ModelRetry=9、Database=10、Mcp=11〔各中间件 getOrder 实测 ‹CERTAIN›〕

**Error modes:**
- E_MW_ISOLATED — 触发：任一中间件钩子抛异常。恢复：log.warn 后继续执行其余中间件（链不断、执行不中断）。〔AgentMiddlewareChain.java:42-49,57-63,72-79 ‹CERTAIN›〕

**Invariants:**
- Post：单中间件异常不影响链中其余中间件（三钩子均隔离）。
- Post：SummarizationMiddleware 阈值 8000 tokens（中文 1.5 字符/token、英文 4 字符/token 估算），保留 system+最近 6 条；可经 context.summaryThreshold 覆盖〔SummarizationMiddleware.java:16-27 ‹CERTAIN›〕。
- Post：SubAgentMiddleware 对名为 task 的工具调用接管（真实子运行经 createRunDirect + executeSync），注册的 task 工具定义必含 description/subagent_type 必填参数〔SubAgentMiddleware.java:75-88,101,141,189 ‹CERTAIN›〕。
- 已知限制：ContextMiddleware（order=0）模型加载为桩（跨模块 ModelRecordMapper 未接入），模型配置实际由 ContextBuilder/调用方注入〔ContextMiddleware.java:15-22 javadoc ‹CERTAIN›〕。

**Parent Trace:** 接口 agent.MiddlewareChain → Design.MiddlewareChainModule（缝 4）→ REQ-F01~F03。

### agent.ContextAssembler（上下文组装契约）

**Purpose:** 运行世界观的折叠点：把「agent 持久化配置 + backend 类型 + 技能库现状」折叠为一个类型正确的 BaseContext，使引擎与中间件对配置存储形态零感知。

**Input / Output（按方法）:**
- `buildContext(run: AgentRun, 必填—agentId/uid/id 必有) → BaseContext`；流程：加载 Agent（不存在抛）→ 定位 Backend（不存在抛）→ 反射实例化 context schema → configJson 经 updateFromMap 批量填充 → 置 runId/uid → 技能闭包展开〔证据：ContextBuilder.java:59-92 ‹CERTAIN›〕
- `BaseContext.updateFromMap(map)`：遍历类层次 @ConfigField 字段反射赋值〔BaseContext.java:115-139 ‹CERTAIN›〕
- `BaseContext.getConfigurableItems(userRole) → List<ConfigurableItem>`：按 configurable/hide/auth 过滤派生 UI 元数据〔:141-164 ‹CERTAIN›〕

**Error modes:**
- E_CTX_AGENT_NOT_FOUND — 触发：run.agentId 查无记录。恢复：RuntimeException → 编排层转 system_error 终态。〔ContextBuilder.java:61-64 ‹CERTAIN›〕
- E_CTX_BACKEND_NOT_FOUND — 触发：backendId 未注册。恢复：BusinessException notFound → 同上。〔:67-70、AgentBackendManager.java:76-82 ‹CERTAIN›〕

**Invariants:**
- Post：context 实例类型恒等于 backend.getContextSchema()（ChatbotAgent→ChatBotContext、SubAgentBackend→BaseContext）〔:71-73 ‹CERTAIN›〕。
- Post：技能展开只保留「已启用且 slug 有效」的技能；路径现状 `/home/gem/skills/{slug}/SKILL.md`〔:103-137 ‹CERTAIN›〕——**目标态修订（REQ-H12，2026-09-28）**：随沙箱实装迁 `/workspace/skills/{slug}/SKILL.md`（与沙箱工作区布局一致，常量收 SandboxWorkspaceLayout；ContextBuilder.java:124、SkillReadonlyBackend.java:25-27 同步迁移）。
- Pre：技能加载不按用户过滤（listAccessible(null,null,null) 全量启用集）〔:103 ‹CERTAIN›；‹CERTAIN（2026-09-24 深证）：运行面无任何可见性收敛点——ContextBuilder.java:103 与 SkillsMiddleware.java:97 均传 null 全量，两处皆无用户维度过滤，可见性收敛依赖上层（当前上层亦未收敛）›〕。

**Parent Trace:** 接口 agent.ContextAssembler → Design.ContextBackendModule（缝 3）→ REQ-G01~G02。

### agent.SandboxLifecycle（沙箱生命周期契约 — 降级桩契约）

> **2026-09-28 整体重写**：本节原「降级桩契约」废止，被下列四个接口（SandboxConfigStore / SandboxLifecycle / SandboxProviderGateway / SandboxExecutionFacade）取代。废止要点（原文以删除线保留追溯）：~~`generateSandboxId(uid, fileThreadId)` 确定性 ID（sha256 前 12 位）~~（多后端下实例 ID 由 provider 生成，绑定键改 (userId, sessionId)，REQ-H01）；~~get 返回 mock 连接、release 仅清缓存、无任何真实 Docker API 调用~~（实装兑现，docker-java 入 pom）；~~idle-timeout 默认 600s~~（上调 1800s 且回收须真实删容器，REQ-H02）；~~E_SANDBOX_DISABLED 单码降级~~（拆三码，REQ-H03）；~~实装门禁①②③~~（①现状条款本次重写清算、②容器删除路径入 REQ-H02 契约、③HTTP API 契约改写为 SandboxProviderGateway 双适配器契约）。桩现状代码证据保留于 spec REQ-H01/H02〔证据〕行与 design 模块图旧行。

### agent.SandboxConfigStore（沙箱配置契约 — 目标态）

**Purpose:** 沙箱资源的命名配置台账：把「哪个后端、连哪里、用什么镜像/模板、给多少资源、准不准出网」折叠为一行配置，供应用引用（app.sandbox_config_id，字段定义归 app-center 链）、生命周期消费；凭据以引用形态存在（ADR-0007）。

**Input / Output（按方法）:**
- `create(config: SandboxConfig{userId*, name*, sandboxType* ∈ {docker,cube,e2b,disabled}, dockerConfig? | cubeConfig? | e2bConfig?, networkPolicy?, envVars?, defaultTimeoutSec?, allowPrivateEndpoints?}) → SandboxConfig`——按类型校验对应 provider 块必填性〔REQ-H04〕
- `update(configId, patch, user)`：身份字段（type/endpoint/凭据引用/镜像或模板）变更时重算 identity_fingerprint 并触发生命周期侧批量 stale（REQ-H09 的上游信号）；空凭据引用不覆盖既有引用〔REQ-H06、H09〕
- `delete(configId, user)`：库存 >0（含 paused）时拒绝；无实例物理删，历史实例交孤儿对账按宽限期回收〔REQ-H05〕
- `get / listByUser(user)`：读取恒经脱敏——密钥字段只回显引用名〔REQ-H06〕
- `verify(configId) → VerifyResult{reachable, providerInfo?, templates?}`：Docker = daemon ping；Cube/E2B = 控制面鉴权〔REQ-H05〕
- `listInstances(configId) → [{sandboxId, state ∈ {running,paused,transitioning,terminal}, createdAt}]`（provider list 按 managed label + configId 过滤）〔REQ-H05〕
- `resolveCredentials(config) → EffectiveConfig`（引用 → 环境值解析）/ `isGloballyEnabled()`（SysConfig > yml 回退 > 默认 false）〔REQ-H06、H07〕

**Error modes:**
- E_SANDBOX_CONFIG_INVALID — 触发：类型越界；docker 配置携带域名 allow/deny 列表；network_mode ∉ {bridge, none}；endpoint 未过 EndpointDialGuard.validateEndpoint（云 metadata/link-local 恒拒、私网未显式放行）。恢复：业务错误（HTTP 200 + body.code），不落库。〔REQ-H15、H16〕
- E_SANDBOX_VERIFY_FAILED — 触发：探活不可达或鉴权失败。恢复：业务错误，配置保持原状；错误体携带 provider 原因分类。〔REQ-H05〕
- E_SANDBOX_CREDENTIAL_MISSING — 触发：resolveCredentials 时引用指向的环境变量缺失。恢复：验证/建连时 fail-loud 业务错误；保存时只校验引用名格式、不解析值。〔REQ-H06、ADR-0007〕
- E_SANDBOX_CONFIG_IN_USE — 触发：删除时 listInstances >0。恢复：业务错误（409 语义），提示先清实例（复用库存端点确认）。〔REQ-H05〕

**Invariants:**
- Pre：写操作 user 为归属者或管理员〔REQ-H04〕。
- Post：identity_fingerprint 恒为身份要素（type/endpoint/凭据引用/镜像或模板）的确定性哈希；非身份字段变更不动指纹〔REQ-H09〕。
- Post：任何读取路径密钥值不出服务端（脱敏恒开）〔REQ-H06〕。
- HTTP 层（SandboxConfigController）为薄实现细节，不立契约（端点排除规则）。

**Parent Trace:** 接口 agent.SandboxConfigStore → Design.SandboxConfigModule（EndpointDialGuard 消费位）→ REQ-H04~H07、H15、H16。

### agent.SandboxLifecycle（沙箱绑定与生命周期契约 — 目标态）

**Purpose:** 会话级沙箱实例的唯一合法通道：(用户, 会话) → 实例绑定的获取/保活/释放；跨轮状态保持与四类回收路径（空闲 / 配置变更 / 会话删除 / 孤儿对账）全部内聚于此。

**Input / Output（按方法）:**
- `acquire(userId*, sessionId*, config: EffectiveConfig*) → SandboxHandle{id, provider, state}`：绑定命中 → touch 返回；未命中 → create-or-connect（exited/created 自恢复为 running）；绑定存在但 configId 身份指纹 ≠ 当前配置 → 销毁重建并 casRebind〔REQ-H01、H08、H09〕
- `lookup(userId, sessionId) → SandboxHandle|null`：只查不建（不触发 provision）〔REQ-H01〕
- `release(userId, sessionId)`：删绑定 + provider.delete〔REQ-H01〕
- `onSessionDeleted(userId, sessionId)`：会话删除钩子（app-center 侧触发调用）〔REQ-H10〕
- `reclaimIdle()`（@Scheduled）：活动标记超 TTL（创建时配置固定，默认 1800s）→ provider.delete + 绑定清除；Cube/E2B 由 provider TTL 承担，本方法对其等价于无操作〔REQ-H02〕
- `reconcileOrphans()`（@Scheduled）：provider.list（managed label + configId 过滤，含 paused）对比绑定存储，删除「无绑定且超 grace（默认 10 分钟）」实例〔REQ-H11〕
- 附属接口 `SandboxBindingStore`：`get / put / casRebind / markStale / delete / listByConfigId`——RedisSandboxBindingStore（生产，多副本共享）+ InMemorySandboxBindingStore（单实例开发/测试）双适配器真缝；绑定值结构见 data.md「sandbox 绑定存储」节〔REQ-H01〕

**Error modes:**
- E_SANDBOX_UNAVAILABLE — 触发：acquire 时 provider 连接失败（镜像拉取失败/端口冲突/daemon 不可达——连接期失败一律归此分类）。恢复：不中断运行——调用方（执行门面）转「Error: …」文本回灌 LLM；绑定不变，下次调用重试。〔REQ-H03 分支③；2026-09-24 代裁「连接期失败降级不中断」继续有效〕

**Invariants:**
- Pre：acquire 前配置已过 resolveCredentials 且总开关开启（否则属执行门面降级分支，不达本接口）。
- Post：同一 (userId, sessionId) 在绑定存活期内 acquire 恒得同一 sandboxId；跨轮文件/依赖/进程状态保持〔REQ-H01、H08〕。
- Post：绑定持久于外部存储——进程重启后 lookup 可解析；多副本部署下任意副本解析同一会话得同一实例〔REQ-H01〕。
- Post：TTL 固定于实例创建时的配置；配置后续修改不追溯在途实例〔REQ-H02〕。

**Parent Trace:** 接口 agent.SandboxLifecycle → Design.SandboxLifecycleModule（+SandboxBindingStore 附属缝）→ REQ-H01、H02、H08~H11。

### agent.SandboxProviderGateway（后端协议缝契约 — 目标态）

**Purpose:** 多后端差异的收口：生命周期与执行模块只认 provider 中立操作与归一错误分类；Docker = Engine API 直连（ADR-0006），Cube/E2B = 控制面 REST + 容器内 envd 数据面。

**Input / Output（按方法）:**
- `create(ctx: CreateContext{configId, sessionKey, imageOrTemplate, resources{cpu, memoryBytes, pidsLimit}, networkMode | networkPolicy, envVars, idleTtl, labels{managed, configId, userId, sessionId}}) → Handle`〔REQ-H01、H14〕
- `connect(handle)`：exited/created → running（自恢复语义）；`delete(handle)`；`list(filter{metadata, states})`；`health()`；`capabilities() → {terminal, desktop, snapshot}`（本期恒 false 占位，终端/桌面二期）〔REQ-H08、H05〕
- `exec(handle, command*, timeout, workdir?, env?) → ExecResult{stdout, stderr, exitCode, killed}`：超时由适配器 in-container 包装 + 客户端 grace（10s）实现，killed=true 表超时被杀〔ADR-0006〕
- `readFile / writeFile / stat / makeDir / remove / listDir(handle, path, …)`：provider 中立文件面；适配器内经 exec + archive 实现，协议细节不出适配器〔ADR-0006〕

**Error modes（归一分类，跨适配器恒定）:**
- timeout / notFound / unavailable / auth / capacity / invalidRequest — provider 原生错误映射为六分类，上层（Lifecycle/Execution）只认分类不认协议报文。判定锚例：exec 超时 = timeout；运行中实例消失 = notFound；create 时镜像缺失 = invalidRequest（**不判 notFound**，防生命周期误重建）；daemon/控制面不可达 = unavailable。恢复：由上层降级/重建策略决定，网关自身不降级。〔REQ-H03；对齐 WeKnora RemoteErrorKind〕

**Invariants:**
- Pre：Docker 适配器 create 恒满足容器加固基线——CapDrop ALL + 仅白名单 caps（CHOWN/DAC_OVERRIDE/FOWNER/FSETID/SETGID/SETUID/KILL）、no-new-privileges、memory = memorySwap（禁 swap）、Init = true、资源三上限（默认 2 核/2GiB/512 pids，配置可覆盖）〔REQ-H14〕。
- Pre：Docker TCP daemon 建连与 Cube/E2B endpoint 拨号恒经 EndpointDialGuard.guardDial（建连时解析 IP 复查）〔REQ-H16〕。
- Post：本网关创建的实例恒带 managed 标记 + configId/userId/sessionId metadata（孤儿对账与库存过滤的唯一依据）〔REQ-H11、H05〕。
- Post：活动标记由适配器折叠进 exec 包装器，调用方零感知〔REQ-H02 机制位〕。
- 网关内不做路径策略（读写根/钳制归执行门面 + 布局谓词）、不做降级决策（归执行门面）。

**Parent Trace:** 接口 agent.SandboxProviderGateway → Design.SandboxProviderClient 缝（三适配器；EndpointDialGuard 消费位）→ REQ-H02（机制位）、H05、H08、H11、H14、H15（执行位）、H16。

### agent.SandboxExecutionFacade（沙箱执行门面契约 — 目标态）

**Purpose:** 工具/中间件侧的唯一入口：7 工具动作 + 附件暂存 + 技能物化，统一应用路径策略与降级语义，把 provider 差异与不可用转写为 LLM 可消费的文本。

**Input / Output（按方法）:**
- `readFile / writeFile / editFile / execute / listDirectory / grep / glob(userId*, sessionId*, …) → String`：成功 = 操作结果原文；失败 = 「Error: …」文本且**区分三因**（总开关关 / 应用未引用配置或配置禁用 / provider 连接失败）——恒不抛异常、恒不阻断运行〔REQ-H03〕
- `execute(…, timeout?, cwd?)`：超时 = 工具超时 + 10s grace，输出截断 256KB（承接旧 SandboxBackend 语义）〔REQ-H03〕
- `stageAttachments(userId, sessionId, uploads)`：附件实体拷入 `/workspace/input`，同名覆盖幂等；附件在沙箱内只读〔REQ-H13〕
- `syncSkills(userId, sessionId, slugs)`：技能物化到 `/workspace/skills/{slug}`（只读子树）；沙箱不可用时技能读取回退虚拟文件系统（SkillReadonlyBackend，挂载点随迁）〔REQ-H12〕
- `resolveDegradation(userId, sessionId, sandboxConfigId?) → {available, reason?}`：三分支判定，供 FilesystemMiddleware 工具描述与错误文本生成〔REQ-H03；配置缺失分支消费 app.sandbox_config_id（app-center 链字段）〕

**Error modes（文本化，不上抛）:**
- E_SANDBOX_DISABLED / E_SANDBOX_NOT_CONFIGURED / E_SANDBOX_UNAVAILABLE 三码对应的降级文本——触发见 resolveDegradation 三分支；恢复：LLM 收到区分原因的 Error 文本后自行调整。三码同时供配置管理面业务错误复用（HTTP 200 + body.code）。〔REQ-H03；取代旧 E_SANDBOX_DISABLED 单码〕

**Invariants:**
- Pre：全部路径参数经 SandboxWorkspaceLayout.clamp；可读 = `/workspace` 全部；可写 = `/workspace` 下除 input 与 skills 子树；保留路径拒绝〔REQ-H12〕。
- Post：实例获取恒经 Lifecycle.acquire——本门面不直连 provider 创建实例。
- Post：暂存/物化只写 input 与 skills 子树，永不越界〔REQ-H12、H13〕。

**Parent Trace:** 接口 agent.SandboxExecutionFacade → Design.SandboxExecutionModule（+SandboxWorkspaceLayout 谓词）→ REQ-H03、H12、H13。

## app-center

> 对应 design 模块：AppDialogOrchestrator / AppConversationLedger / AppConfigFacade / AppBindingRegistry（design/app-center.md §1）。数据实体契约见 data.md `## app-center` 节。
> **端点排除**：AppController 7 端点（台账 CRUD+templates+run）、AppChatController 8 端点中 stream 之外的会话/消息面、AppConfigController ~60 端点（全后端最大配置面）全部为所属模块实现细节，不逐端点立契约（HTTP 暴露面见 design/app-center.md §2 与控制器 javadoc：AppController.java:10-26、AppChatController.java:18-36、AppConfigController.java:8-29）。契约写给四个深模块接口。
> **上游消费登记**：本链消费 retrieval.RetrievalSearchEngine（缝 2）、agent.ExecutionEngine（缝 1，fakeRun 模式已在 `## agent` 节跨链登记）、platform-config.ModelRegistry 生产数据（缝 4，假设缝）、tools-skills-mcp.ToolRegistry（缝 5 下游）；消费行为归本节各接口契约。

### app-center.AppDialogOrchestrator（SSE 对话编排契约）

**Purpose:** 应用对话的总闸：一次调用折叠「可见性校验→会话定位→输入拦截→消息落库→RAG 检索→绑定/配置装配→引擎流式生成→输出过滤→落库记账」全链，调用方（前端/API Token）对检索、五类绑定、敏感词、记忆窗口与模型路由零感知。

**Input / Output（按方法）:**
- `runStream(appId: string, 必填—须当前用户可见; query: string, 必填; sessionId: string, 可空—非空时须归属当前用户（API Token 例外），空则自动建会话; userId/userName: string, 必填—取自 SecurityUtil) → SseEmitter`；事件序列：`message{blocked:true}` + `end`（拦截短路径）｜`sources → {引擎事件词汇 init/message/thinking/tool_call/tool_result/agent_state} → message{replace:true}?（输出命中时）→ end`｜`error{message}`（执行异常）〔证据：AppServiceImpl.java:371-409,546-553,665-705 ‹CERTAIN›；引擎词汇继承 contract `## agent` ExecutionEngine 封闭集〕。本域新增事件仅 `sources`（回答前推送知识来源列表 fileName/score/fileId/kbId/chunkIndex/chunkType/imageKeys/content）与 `message` 事件的 blocked/replace/isGreeting 标记位〔:397-405,488-498,675-685 ‹CERTAIN›〕。
- `run(appId, query) → {sessionId, answer}`（@Deprecated 同步旧通道：内置 QA_RULES 关键词计分匹配，不落库不调用模型）〔AppController.java:42、AppServiceImpl.java:201-239 ‹CERTAIN›〕

**Error modes:**
- E_APP_NOT_VISIBLE — 触发：应用不存在（"应用不存在"）或当前用户不可见（DataScope 三分支外，"无权访问该应用"）。恢复：HTTP 200 + body.code 业务错误，SSE 不建立。〔AppServiceImpl.java:73-77,371-374 ‹CERTAIN›〕
- E_SESSION_NOT_OWNED — 触发：sessionId 对应会话不属于当前用户且调用方非 API Token。恢复：forbidden 业务错误。〔:93-104 ‹CERTAIN›〕
- E_INPUT_BLOCKED — 触发：安全开关开启且 checkInput 命中 blockInput 词条。恢复：SSE 推送拦截文案（blocked=true）后正常 end；**不保存用户消息、不产生模型调用**。〔:391-409 ‹CERTAIN›〕
- E_STREAM_EXEC_FAILED — 触发：异步编排任意异常（检索/装配/引擎）。恢复：error 事件推送「对话执行失败: <原因>」后 complete；SecurityContext 在 finally 清理。〔:692-705 ‹CERTAIN›〕
- E_STREAM_TIMEOUT — 触发：SSE 连接达 5 分钟超时。恢复：onTimeout 将已生成部分答案（先输出过滤）落库。〔:507,520-528 ‹CERTAIN›〕
- 非错误约定：单个知识库检索失败仅 warn 继续其余绑定〔:755-757〕；model code 查无 online 记录降级默认网关〔:472-474〕——两者均不进入 Error modes（‹CERTAIN›）。

**Invariants:**
- Pre：安全开关判定先于一切模型动作；开关缺省（无策略记录/字段空）= 开启〔:85-90,393 ‹CERTAIN›，对应 data.md app_global_policy.safety_enabled DEFAULT 1〕。
- Pre：用户消息先落库后调模型；首条消息回填 firstQuestion（≤200 字）与 title（≤30 字+"..."）〔:411-425 ‹CERTAIN›〕。
- Pre：greeting 短路径仅在「配置了开场白 ∧ 会话消息数为 0 ∧ 输入命中打招呼词表」时触发，开场白作为助手消息落库、不调模型〔:482-502,812-817 ‹CERTAIN›〕。
- Post：仅 enabled=1 的五类绑定进入 BaseContext；检索 config 以 KB 保存配置为基底仅覆盖 topK=5（透传 contextAssemblyStrategy/enableClauseRecall/contextWindowSize/maxContextTokens/enableRerank/rerankModel/enableMultiQuery/enableSynonymExpansion）〔:570-624,773-800 ‹CERTAIN›〕。
- Post：历史窗口 = 最近 memoryRounds（缺省 5）*2 条消息按时间正序〔:431-432,556-561 ‹CERTAIN›〕。
- Post：输出命中替换词条时，SSE 补发 replace 事件且**落库与替换同文**（先过滤后落库）；超时路径同协议〔:520-528,671-687 ‹CERTAIN›〕。
- Post：助手消息落库必伴随会话 messageCount +2、updatedAt 刷新；第一轮回答回填 answerSummary（≤200 字）〔:822-844 ‹CERTAIN›〕。
- Post：异步线程内 SecurityContext 传播自提交前快照、finally 清理，线程池（无界 cachedThreadPool）不复用污染上下文〔:64,533-537,702-705 ‹CERTAIN›〕。

**Parent Trace:** 接口 app-center.AppDialogOrchestrator → Design.AppDialogOrchestrator（缝 1/2/3/4 消费方）→ REQ-E01~E13、REQ-C03/C04、REQ-D01、NFR-03。

### app-center.AppConversationLedger（会话账本契约）

**Purpose:** 应用侧会话与消息的唯一存取口：把「谁拥有哪个会话、消息是否可见、会话删了消息怎么办」折叠为一组归属受控操作——SSE 编排与配置面对话记录查询共用同一账本，归属不变量单点维护。

**Input / Output（按方法）:**
- `createSession(appId, userId, userName) → {sessionId:16位, conversationId:32位, title:"新对话", createdAt}`〔证据：AppServiceImpl.java:243-268 ‹CERTAIN›〕
- `listSessions(appId, userId) → List<AppSessionDTO>`（该应用+该用户，updatedAt 倒序；DTO 含 conversationId/sessionId/title/firstQuestion/answerSummary/messageCount/tokenCount/createdAt/updatedAt）〔:270-298 ‹CERTAIN›〕
- `getSessionMessages(appId, sessionId) → List<Map>`（软删过滤 deletedAt IS NULL，createdAt 正序；含 role/content/tokens/latencyMs/feedback/thinkingContent/toolCalls）〔:300-327 ‹CERTAIN›〕
- `deleteSession(appId, sessionId) → void`（消息**物理**删除后会话物理删除）〔:329-339 ‹CERTAIN›〕
- `deleteMessage(appId, messageId) → void`（**软**删除，置 deletedAt）〔:341-348 ‹CERTAIN›〕
- `feedbackMessage(appId, messageId, feedback: like|dislike|"") → void`；`updateMessage(appId, messageId, content) → void`〔:350-366 ‹CERTAIN›〕

**Error modes:**
- E_CONVERSATION_NOT_FOUND / E_MESSAGE_NOT_FOUND — 触发：sessionId/messageId 查无（级联：消息查无或其会话查无均报"会话不存在"）。恢复：notFound 业务错误。〔:93-116 ‹CERTAIN›〕
- E_CONVERSATION_NOT_OWNED — 触发：会话/消息属主非当前用户且非 API Token。恢复：forbidden 业务错误。〔:100-103,112-115 ‹CERTAIN›〕

**Invariants:**
- Pre：消息级三操作（软删/反馈/编辑）均先过 requireOwnMessage（消息→会话→属主链式校验）。
- Post：软删消息对历史查询永久不可见（deletedAt 过滤），行保留〔:306-310 ‹CERTAIN›〕。
- Post：feedback 语义 = like/dislike 写值、空串清除〔AppChatController.java:113 getOrDefault("feedback","") ‹CERTAIN›〕。
- 已知断裂（如实登记，修复归后续）：tokenCount 无生产者（user/assistant 消息 tokens 恒 0、conversation.tokenCount 恒 0）〔AppServiceImpl.java:417,831,836 区间 ‹CERTAIN›〕。

**Parent Trace:** 接口 app-center.AppConversationLedger → Design.AppConversationLedger → REQ-E01/E02/E14/E15。

### app-center.AppConfigStore（配置面存储契约）

**Purpose:** 应用全部配置维度（基础/对话/智能体/策略/变量/触发器/测试/优化/发布/自动更新）的统一写读口：把「每应用一行的 upsert、缺省值兜底、JSON 列的前端友好形状」三个惯用法固化，~60 个 HTTP 端点只是它的薄片包装。

**Input / Output（按方法，签名折叠为语义组）:**
- `getBasic(appId) → AppBasicConfig|null`；`saveBasic(appId, config) → config`（新记录兜底 memoryRounds=5；update 按 appId 覆写同 id 行）〔证据：AppConfigServiceImpl.java:26-27 ‹CERTAIN›〕
- `getConfig(appId) → AppConfig|null`；`save{Prompt|Summary|MaxSteps|MaxTurns|RetryTimes|MaxTokens}`（getOrCreate 缺省六元组 maxSteps=15/maxTurns=10/retryTimes=2/summaryThreshold=8000/maxTokens=2048/temperature=0.70，再单项覆盖，id 空则 insert 否则 update）〔:33-74 ‹CERTAIN›〕
- `getDialog/saveDialog`（同 upsert 模板，无缺省填充）〔:29-30 ‹CERTAIN›〕
- `getGlobalPolicy/saveGlobalPolicy`（新记录兜底 safetyEnabled=1；安全/兜底/敏感词/toggle/unmatched 五个写入口最终同归此 upsert）〔:87-88,234-253 ‹CERTAIN›〕
- `listVariables/createVariable/updateVariable/deleteVariable`（无查重）〔:90-92,233 ‹CERTAIN›〕
- `listTriggers/createTrigger`（兜底 enabled=1、priority=0）/`updateTrigger/deleteTrigger`；`testTrigger/runTrigger`（**占位实现**：hitCount+1；runTrigger 另回写 actionConfig=执行结果 JSON、output 为静态文案——不触发任何真实动作）〔:76-85 ‹CERTAIN›；与 contract `## workflow` 节跨链登记一致〕
- `listDialogTests/createDialogTest/updateDialogTest/deleteDialogTest`；`listOptimizations/createOptimization`（status 缺省 pending）/`update/delete/applyOptimization`（status→applied）/`analyze`（纯计数聚合：totalTests/matchedTests/unmatchedRate/totalPublish/totalKbBindings/totalTriggers/analyzedAt，无 LLM 参与无相似度计算）〔:168-193 ‹CERTAIN›〕
- `listPublishRecords(appId) → List<AppPublishRecord>`；`publish(appId, record) → record`（publishedAt=now 直接插入；version/status/scope/configSnapshot 信任调用方）〔:165-166 ‹CERTAIN›〕
- `getAutoKnowledgeUpdate`（查无自动建 enabled=0 记录）/`saveAutoKnowledgeUpdate`（upsert）/`triggerKnowledgeUpdate`（置 enabled=1 并返回 triggered 文案，无真实更新调度）〔:293-313 ‹CERTAIN›〕
- `saveAdvanced/exportConfig/importConfig/exportDialogConfig/importDialogConfig`（导出 11 组；导入仅 basic/dialog/policy/triggers/variables/kbs 六组，skills/tools/mcps/dbs 出而不入）〔:196-232 ‹CERTAIN›〕
- `getWorkflowConfig/saveWorkflowConfig`（读写 basic.advancedOptions JSON 的 workflow 段；坏 JSON 容错为空 Map）〔:254-272 ‹CERTAIN›〕

**Error modes:**
- 无显式错误模式 — 全契约对不存在的 appId 不校验应用存在性（不校验 requireVisible/requireManage，与 AppService 台账半边不同）；upsert 家族对 null 既有记录走 insert。
- **异常吞噬面（如实登记）**：listSkillBindings/listToolBindings/listMcpBindings 失败返回空列表〔:152,156,160〕；listConversations/getConversationDetail/deleteConversation/exportConversationsCsv 捕获全部异常返回空壳/"table not ready"/静默〔:316-356〕。恢复策略：无——调用方无法区分「空数据」与「存储故障」。〔‹CERTAIN›〕

**Invariants:**
- Pre：写路径不感知应用可见性（鉴权职责在 HTTP 层注解）；saveX 家族以 appId 为唯一业务键定位行。
- Post：同一 appId 的 app_config/app_basic_config/app_dialog_config/app_global_policy/app_kb_auto_update_config 恒至多一行（UNIQUE(app_id) 或 upsert 语义保证）〔schema.sql:376,940,957,988,1090 ‹CERTAIN›〕。
- Post：app_db_binding.allowedTables 对外恒为逗号分隔 CSV、库内恒为 JSON 数组（csvToJsonArray/jsonArrayToCsv 双向门；非 JSON 原样透传容错）〔:100-147 ‹CERTAIN›〕。
- Post：advancedOptions 是多段共享 JSON 容器（workflow/debug 等段共存，保存单段先 parse 后合并写回，不冲掉他段）〔:265-271,284-292 ‹CERTAIN›〕。

**Parent Trace:** 接口 app-center.AppConfigStore → Design.AppConfigFacade → REQ-B01~B06、REQ-D02~D05、REQ-F01、REQ-G01/G02、REQ-H01。

### app-center.AppBindingRegistry（五类绑定注册表契约）

**Purpose:** 应用与外部能力（知识库/工具/技能/MCP/外部数据库）之间唯一的耦合面：绑定行的增删改查即应用能力的增删改查，运行侧按 enabled=1 装配、下游 tools-skills-mcp.ToolRegistry 按 ID 解析，两侧对资源语义零耦合。

**Input / Output（按方法，五类同构）:**
- `list{Kb|Db|Skill|Tool|Mcp}Bindings(appId) → List<App*Binding>`（db 读出时 allowedTables JSON→CSV）〔证据：AppConfigServiceImpl.java:94-103,152,156,160 ‹CERTAIN›〕
- `bind{Kb|Db|Skill|Tool|Mcp}(appId, binding) → binding`（appId 强制覆写；enabled 兜底 1；kb 另兜底 priority=0；db 的 allowedTables CSV→JSON）〔:95,104-109,153,157,161 ‹CERTAIN›〕
- `update{Db|Skill|Tool|Mcp}Binding(id, binding) → binding|null`（按 id 全量覆写后回查；kb 绑定**无 update 端点**）〔:110-118,154,158,162 ‹CERTAIN›〕
- `unbind{Kb|Db|Skill|Tool|Mcp}(id) → void`（物理删除，查无不报错）〔:96,119,155,159,163 ‹CERTAIN›〕

**Error modes:**
- 无显式错误模式 — bind 不校验资源存在性（kbId/toolId/skillId/mcpServiceId/dbId 均信任调用方，悬空绑定静默存活，运行时被 ToolRegistry 的 E_TOOL_UNRESOLVABLE 或检索空结果吸收）。
- E_KB_BINDING_DUPLICATE — 触发：同 (app_id, kb_id) 重复绑定（应用层不查重）。恢复：数据库 UNIQUE INDEX idx_app_kb 拒绝，异常经 GlobalExceptionHandler 兜底。〔schema.sql:1020 + AppConfigServiceImpl.java:95 无前置查重 ‹CERTAIN›；仅 kb 有唯一约束，其余四类可重复绑同一资源〕

**Invariants:**
- Pre：绑定行仅经本契约增删改；运行侧（AppDialogOrchestrator）只读。
- Post：库内 enabled 域 = {0,1}（bind 兜底 1，无独立 toggle 端点——启停需走 update 语义或先解绑再绑；kb 绑定无 update 端点，启停只能解绑重绑）〔‹CERTAIN（2026-09-24 深证：AppConfigController.java:88-130 绑定组仅 list/bind/update(db|skill|tool|mcp)/unbind，无 enabled 切换端点；update 端点为全量覆写可携带 enabled）›〕。
- Post：解绑即物理删除，五张绑定表均无软删列（schema.sql:1012-1179）〔‹CERTAIN›〕。

**Parent Trace:** 接口 app-center.AppBindingRegistry → Design.AppBindingRegistry（缝 5 写侧）→ REQ-C01/C02。

## operation-analytics

> 对应 design 模块：DashboardAggregator / AssetAnalyticsAggregator / AuditLogPipeline / UnifiedLogQueryEngine / LoginLogLedger / FeedbackLedger / ChatSessionFinder / DataMiningTaskBoard / ModelMonitorPanel（design/operation-analytics.md §1）。数据实体契约见 data.md `## operation-analytics` 节。
> **端点排除**：DataMiningController 5 端点（CRUD+模拟 run）、ChatSessionController 单端点、AuditLogController/SysLoginLogController 的单表 list 端点均为浅实现细节，不立契约（HTTP 暴露面见 design/operation-analytics.md §2）。契约写给四个深模块接口。
> **上游消费登记**：本链为纯只读消费方——kb/kb_file（`## kb-core`）、app（`## app-center`）、kb_log（`## publish-review` LogAuditModule）、model_call_log（`## platform-config`，已登记死表 REQ-C03）；与 tools-skills-mcp「mcp_call_log 只写不读、查询归本链」口径互为镜像。鉴权复用 `## iam` 登录态；⚠ 本域控制器无 @PreAuthorize/@KbAuth 注解，权限过滤仅聚合逻辑内联实现〔grep 实测 ‹CERTAIN›〕。

### operation-analytics.DashboardAggregator（工作台/资产聚合契约）

**Purpose:** 运营首页与知识资产盘点的一次请求聚合总闸：把「用户能看到哪些知识库」的三类口径（API Token 全量/组织∪ACL/无权限短路）折叠进四个区块与四项指标，调用方对跨域表结构与权限判定零感知。

**Input / Output（按方法）:**
- `getHomeData() → ApiResponse<HomeData>`；HomeData 四区：recommendKBs（≤5，创建时间倒序，含 id/name/description/category/tags[]/creator）｜hotDocs（≤10，viewCount 倒序，仅 completed 未删除，含 id/name/kbId/viewCount/updatedAt("yyyy-MM-dd HH:mm")）｜hotApps（≤5，创建时间倒序，含 id/name/type/description）｜recentActivities（≤10，kb_log 时间倒序，含 id/operator/action/target/detail/timestamp）〔HomeController.java:64-144 ‹CERTAIN›〕
- `getKbAnalytics() → ApiResponse<AnalyticsData>`；metrics×4（总知识库数量/总文档数/活跃文档数量/知识引用率——引用率 displayValue=百分比 1 位小数，活跃=0 时 "0%"）｜hotKBs≤5（按未删除文档数 desc，name/docCount/rank）｜hotDocs≤5（viewCount desc，仅 completed，name/kbName/viewCount/rank）〔AnalyticsController.java:63-190 ‹CERTAIN›〕

**Error modes:**
- 无业务错误分支：两方法无参数、无前置校验，任何查询异常走 GlobalExceptionHandler 兜底（HTTP 200 + serverError）〔‹CERTAIN›：两方法体无 throw〕
- 非错误约定：无登录态按「无任何可见库」处理（Home 四区空集合；Analytics 以 `__none__` 哨兵查空）〔HomeController.java:152、AnalyticsController.java:57 ‹CERTAIN›〕

**Invariants:**
- Pre：可见口径三分支——API Token（userId 前缀 `api-token:`）不过滤；登录用户按 KbAccessChecker.getAccessibleKbIds(userId, orgId)；无可见库时 Home 直接返回四区空、Analytics 各项归零〔HomeController.java:150-155,66-75；AnalyticsController.java:55-61 ‹CERTAIN›〕
- Pre：热门应用仅当 `hasPermission("*") ∨ API Token ∨ hasPermission("app:use")` 时返回，否则空〔HomeController.java:157-162 ‹CERTAIN›〕
- Post：hotDocs 两处均排除 `deleted_at IS NOT NULL` 与 `status ≠ 'completed'` 的文档〔HomeController.java:93-95；AnalyticsController.java:152-155 ‹CERTAIN›〕
- Post：tags 解析容错——非数组 JSON/解析异常一律空列表，不抛错〔HomeController.java:164-181 ‹CERTAIN›〕

**Parent Trace:** 接口 operation-analytics.DashboardAggregator → Design.DashboardAggregator + AssetAnalyticsAggregator（缝 2 消费方）→ REQ-H01~H06、REQ-K01~K04。

### operation-analytics.AuditLogPipeline（系统审计日志管线契约）

**Purpose:** 系统级操作留痕的唯一落库通道：业务切面发布审计事件，本管线把它折叠为「字段映射→服务器时间→静默落库」，发布方对存储零依赖；读侧提供模块过滤的近况查询。

**Input / Output（按方法）:**
- `onSysAuditLogEvent(SysAuditLogEvent) → void`（Spring 事件监听）；事件字段=userId/username/module/action(ActionType 枚举→name())/target/detail/ip/status，timestamp 由监听器以服务器当前时间覆写〔SysAuditLogEventListener.java:32-45、SysAuditLogEvent.java:14-38 ‹CERTAIN›〕
- `list(module: string?, limit: int?) → List<SysAuditLog>`（module 精确过滤；timestamp 倒序；limit 缺省 100）〔AuditLogServiceImpl.java:35-43 ‹CERTAIN›〕
- `addLog(9 参) → void`：⚠ 死代码——全仓无调用方（写侧实际只走事件监听）〔grep 实测 ‹CERTAIN›〕

**Error modes:**
- E_AUDIT_WRITE_SILENT — 触发：insert 任意异常。恢复：吞异常仅 warn 日志，**主业务流程不受影响**（审计为旁路）〔SysAuditLogEventListener.java:46-48 ‹CERTAIN›〕
- 非错误约定：监听为同步执行（@EventListener 无 @Async），事件发布线程内完成落库〔‹CERTAIN（2026-09-24 深证：SysAuditLogEventListener.java:32 仅 @EventListener；Spring 语义下无 @Async 即发布线程内联执行；FastRagApplication.java:32 @EnableAsync 不改变无注解方法行为）›〕

**Invariants:**
- Pre：事件字段可为 null（均直接透传落库，无非空校验）；action 非法值在枚举层被拒（发布侧编译期保证）。
- Post：每事件恰一行 sys_audit_log（无去重/幂等键）；timestamp 非Event 自带值而是落库时刻〔:44 ‹CERTAIN›〕。
- Post：只读不变量——本管线对外查询只读，无更新/删除入口〔AuditLogController.java:20 仅 GET ‹CERTAIN›〕。

**Parent Trace:** 接口 operation-analytics.AuditLogPipeline → Design.AuditLogPipeline（缝 1 消费侧）→ REQ-L01、L04、L05。

### operation-analytics.UnifiedLogQueryEngine（统一日志查询契约）

**Purpose:** 跨日志源的单一检索口：把审计/登录/操作三类异构日志的筛选语义与字段差异折叠为一个查询对象和一个统一 DTO，运维检索无需知道源表。

**Input / Output（按方法）:**
- `pageQuery(query) → IPage<UnifiedLogDTO>`（⚠ 违反 PageResult{list,total,page,pageSize} 规范，全后端唯一违规点——契约如实登记，待修）〔UnifiedLogService.java:15 ‹CERTAIN›〕
- query 字段：category（**必填语义**：null → 空页 size=pageSize；audit→sys_audit_log｜login→sys_login_log｜operation→kb_log｜update→**未实现**，落 default 空页）、keyword（audit/operation：detail OR target like；login：username like）、module（audit：module eq；operation：kb_log.category eq；login：无此条件）、operator（三源统一按操作人字段 eq）、kbId（仅 operation 生效）、startTime/endTime（audit/operation 按 timestamp；login 无时间过滤）、page 默认 1/pageSize 默认 20〔UnifiedLogQuery.java:22-46、UnifiedLogServiceImpl.java:36-123 ‹CERTAIN›〕
- 统一 DTO 映射：id（login/audit 转 string）｜category｜userId｜username｜module（login 恒"登录认证"；operation=kb_log.category）｜action（login=status）｜target｜detail（login=failReason）｜status｜ip｜timestamp（login=loginTime）〔UnifiedLogServiceImpl.java:127-168 ‹CERTAIN›〕

**Error modes:**
- E_CATEGORY_REQUIRED — 触发：category 为 null 或非三实现值。恢复：返回空分页（records 空/total=0），非报错〔:37-39,44 ‹CERTAIN›〕
- 非错误约定：keyword 与 module/operator 同用时的 OR 优先级缺陷见 design §4（`(detail LIKE ? OR target LIKE ? AND …)` 错误括号化）〔‹CERTAIN（2026-09-24 深证：UnifiedLogServiceImpl.java:53-59,99-108 未用 and(nested) 嵌套；MyBatis-Plus 串联条件默认 AND 连接、.or() 仅切换下一连接符不自动加括号，框架文档化行为）›〕

**Invariants:**
- Pre：三源均强制 orderByDesc 时间列（timestamp/loginTime）。
- Post：单源查询——每页只含一个 category 的记录，不跨源混排（**聚合非合并**：这是分类路由而非真全局日志流）〔‹CERTAIN（2026-09-24 深证：UnifiedLogServiceImpl.java:40-45 switch 各 case 直接 return，Java switch 语义下单次调用恰执行一个分支）›〕。
- Post：读侧只读，三源无任何写入口。

**Parent Trace:** 接口 operation-analytics.UnifiedLogQueryEngine → Design.UnifiedLogQueryEngine（缝 2）→ REQ-L03、L05。

### operation-analytics.FeedbackLedger（反馈账本契约）

**Purpose:** 用户反馈的唯一存取口：把「反馈归哪个组织、谁能处理、回复后状态如何流转、统计口径怎么算」折叠为一组组织受控操作，管理面与统计面共用同一账本。

**Input / Output（按方法）:**
- `page(kbId?, feedback?, status?, page=1, pageSize=20) → PageResult<UserFeedback>`（createdAt 倒序；三条件精确匹配）〔FeedbackServiceImpl.java:59-68 ‹CERTAIN›〕
- `list(kbId?) → List<UserFeedback>`（不分页，**上限 LIMIT 200**，createdAt 倒序）〔:69-74 ‹CERTAIN›〕
- `create(UserFeedback) → void`：orgId 强制取当前登录用户组织（防伪造覆写）；status 缺省 "pending"；feedback 缺省 "like"〔:75-81 ‹CERTAIN›〕
- `update(id, fb) → UserFeedback`：先过组织校验，MyBatis-Plus 非空字段部分更新后回读〔:82-85 ‹CERTAIN›〕
- `delete(id) → void`：组织校验后物理删除〔:86-89 ‹CERTAIN›〕
- `reply(id, reply, operator="system") → UserFeedback`：写入 reply/processedBy/processedAt=now，**状态强制置 "resolved"**〔:90-96 ‹CERTAIN›〕
- `statistics(kbId?) → Map`：total/satisfactionRate(like 占比 %)/resolvedRate/avgScore（score>0 计数均摊）+ byType/byStatus 分组计数〔:97-138 ‹CERTAIN›〕
- `getOverview(kbId?) → Map`：metrics（totalQaCount=chat_session COUNT 按 ACL 库过滤/totalFeedbackCount/satisfactionRate/feedbackRate=反馈÷问答/unresolvedRate=未 resolved 未 ignored 占比）+ questionCategories（category 分组百分比）+ hotKeywords（≤2000 行 query 正则分词词频 top20）+ appSatisfactionRanking（app_id 分组，批量回填应用名，rank 按反馈数）〔:140-278 ‹CERTAIN›〕

**Error modes:**
- E_FEEDBACK_NOT_FOUND — 触发：id 查无（update/delete/reply 均触发）。恢复：notFound 业务错误（"反馈不存在"）〔:42-44 ‹CERTAIN›〕
- E_FEEDBACK_FORBIDDEN — 触发：反馈 orgId ≠ 当前用户 orgId 且非 API Token。恢复：forbidden 业务错误（"无权操作该反馈"）〔:45-48 ‹CERTAIN›〕

**Invariants:**
- Pre：全部读写路径挂组织隔离（applyOrgScope 查询侧 / requireOwnOrg 管理侧）；API Token 例外不过滤〔:27-39 ‹CERTAIN›〕。
- Pre：create 的 orgId 以服务器登录态覆写入参（客户端伪造 orgId 无效）〔:76-77 ‹CERTAIN›〕。
- Post：reply 幂等地把任意前置状态置 resolved（含重复回复——processedAt 刷新）〔:94 ‹CERTAIN›〕。
- Post：删除为物理删除，无级联（无关联子表）。
- 只读不变量：chat_session 在本契约内只 COUNT 不写〔:153 ‹CERTAIN›〕。

**Parent Trace:** 接口 operation-analytics.FeedbackLedger → Design.FeedbackLedger → REQ-F01~F05。

> ModelMonitorPanel 未立契约：其 getOverview 为单端点聚合读（timeRange/keyword/分页透传 + 四段 SQL 聚合），无跨模块写协议、无状态机；其两个实现级缺陷（org_id SQL 拼接、成本假汇率）已在 design §4 与 data.md 登记为缺陷，修复前不值得以契固化为长期承诺。〔决议 2026-09-24：按现状为准——维持不立契约；理由：立契即把已登记缺陷固化为长期承诺，修复（org 条件参数化 + 汇率配置化）后再立契才有价值（代裁，用户可推翻）〕。DataMiningTaskBoard.run 为模拟实现，同样不立契约。

## new-conversation

> 对应 design 模块：DialogPresetResolver / SessionCenter / SessionRequestStateStore / StopRegistry / 编排器跨链延伸（AppDialogOrchestrator）/ 账本跨链延伸（AppConversationLedger）（design/new-conversation.md §1）。数据实体契约见 data.md `## new-conversation` 节。
> **端点排除**：SessionCenterController 全部端点（创建/列表/详情/置顶/清空/批量删/起标题/停止 + 流式对话）为会话中心模块的薄片包装，不逐端点立契约。**旧通道关系**：`## app-center` AppDialogOrchestrator / AppConversationLedger 契约对旧应用对话通道**继续完全适用**（REQ-G01 废弃保留、行为不变），本节不重复其内容，只写新通道增量与两通道共享语义。
> **上游消费登记**：security.KbAccessChecker（编程式逐 KB 校验，本链新消费者）、fastrag-ai.LlmService（起标题）、retrieval.RetrievalSearchEngine（缝 2，协议零改动）、agent.ExecutionEngine（缝 1，事件词汇封闭集零新增）、platform-config ModelRegistry 生产数据（缝 4 仍假设缝）。
> **证据约定**：本节为前瞻契约（代码未实现），证据指向 design/spec 裁决而非代码行号；标注 ‹CERTAIN› 处为既有代码事实的引用（沿 app-center 章）。

### new-conversation.DialogPresetResolver（会话预设解析契约）

**Purpose:** 会话对话的唯一配置解析口：把〔应用预设 │ 会话请求态 │ 内置缺省〕三源折叠为单一有效配置对象，调用方（编排执行核、起标题、重开恢复）对配置来源、优先级矩阵、模型回退与 KB 授权零感知。四个消费方是它通过删除测试的依据（design §1）。

**Input:**
- `conversation`（AppConversation, 必填）— 目标会话；其 `request_state` 可空（存量会话 D03 降级解析）
- `turnState`（ConversationRequestState | PRESET_ONLY 哨兵, 必填）— 本轮请求态覆盖；PRESET_ONLY 供旧通道（仅应用预设源+内置缺省，行为与现行逐字节一致）
- `user`（LoginUser, 必填）— 当前用户，逐 KB ACL 判定主体

**Output:**
- `EffectiveDialogConfig`（只读值对象）— `kbScope: List<String>`（ACL 过滤后唯一 KB 列表；空 = 不调检索直接生成）｜`bindings{tools,skills,mcps,databases: List<String>}`（挂载应用=enabled=1 五表装配；直聊=四个空集）｜`modelRoute{model, apiUrl, apiKey, enableThinking}`（五级回退必有一级命中，apiUrl/apiKey 恒非空）｜`systemPrompt: String`（缺省常量，同现行 AppServiceImpl 缺省 prompt）｜`temperature: double`（缺省 0.70）｜`maxTokens: int`（缺省 2048）｜`memoryRounds: int`（缺省 5）｜`summaryThreshold/modelRetryTimes/maxSteps: Integer`（可空，引擎侧既有容忍）｜`summaryPrompt: String|null`｜`safetyEnabled: boolean`（直聊恒 true＝安全缺省开）｜`greeting: String|null`（直聊恒 null，问候短路径自然短路）｜`source{APP_PRESET|DIRECT_CHAT, mountedAppId?}`（溯源一档，不含中间解析痕迹）

**Error modes:**
- E_CONVERSATION_NOT_FOUND / E_CONVERSATION_NOT_OWNED — 触发：会话查无 / 非属主且非 API Token（复用 `## app-center` AppConversationLedger 既有错误码，经共享归属守卫）。恢复：notFound / forbidden 业务错误，SSE 不建立。
- E_APP_NOT_VISIBLE — 触发：turnState 挂载的应用查无或当前用户不可见（挂载应用存在性校验下沉至解析器；复用 `## app-center` 既有错误码）。恢复：业务错误，SSE 不建立。
- 非错误约定（不进入 Error modes）：KB 无 ACL 权限 → 跳过 + warn 告警（B03，两来源统一过滤）；模型 code 查无 online 记录 → 降级默认网关；request_state 坏 JSON → 空请求态；advancedOptions 坏 JSON → 空 Map（全部沿现行容错口径）。

**Invariants:**
- Pre：PRESET_ONLY 哨兵路径与 `## app-center` 现行 runStream 配置读取语义逐字节等价（G01 旧通道行为不变的契约面）。
- Post：挂载应用分支**不读取** turnState.kbIds——D02 互斥由解析器矩阵结构保证（挂 App 分支无该字段消费行），非运行时比对。
- Post：kbScope 内每个 KB 均通过 `resolveRole ≥ viewer`，**无论来源**（请求态或应用绑定）——REQ-B03 无来源限定；旧通道（PRESET_ONLY）不过滤（G01 行为不变优先，design §2 裁决）。
- Post：~~turnState 的 toolIds/mcpIds/skillIds/webSearchEnabled（B02 死字段）绝不进入 EffectiveDialogConfig~~ **演进（2026-09-25 用户裁决）**：tool/mcp/skill ID 于**直聊装配**进 Bindings（与绑定同路径），webSearchEnabled 成为唯一死字段不进入有效配置——字段面钉死规则不变，新增字段仍须先指认消费行号。
- Post：内置缺省常量与 AppConfigServiceImpl 六默认值同源（单一常量持有者，禁止第二处字面量）。
- Post：EffectiveDialogConfig 不含 BaseContext/ModelConfig 的替代物——config→(BaseContext, ModelConfig) 薄映射保留在执行核（缝 1 稳定侧属 agent 域）。

**Parent Trace:** 接口 new-conversation.DialogPresetResolver → Design.DialogPresetResolverModule → REQ-B02/B03/C01/C04/C05/D01/D02/D03、REQ-A02。

### new-conversation.SessionCenter（会话中心契约）

**Purpose:** 用户聚合轴的会话生命周期与对话入口：把「新建对话不依赖应用、全量列表带来源、停止/清空/置顶/批量删的级联语义、请求态固化时机」折叠为一组归属受控操作。流式方法物理落点为编排器属主（AppService.runStreamByConversation），经本模块消费。

**Input / Output（按方法）:**
- `createConversation(form{appId?, kbIds?, modelId?}) → ConversationDTO`（app/kb 均可选，缺省空请求态直聊）〔REQ-A01〕
- `listConversations(page, pageSize) → PageResult<ConversationDTO>` — 置顶优先 → updatedAt 倒序；DTO 含来源标记（直聊 / 应用名——每页一次批量查询，不冗余列）〔REQ-A03、NFR-01〕
- `getConversation(conversationId) → ConversationDetailDTO`（含 requestState；存量 NULL → 空请求态）〔REQ-A04〕
- `pin(conversationId, pinned: boolean)`〔REQ-E05〕
- `clearMessages(conversationId)` — 删除该会话全部消息（**含已软删**），message_count 归零，first_question/answer_summary 一并置空〔REQ-E03；置空为设计决议 design §2〕
- `deleteConversations(ids: List<String>)` — 逐个物理级联删（语义同 `## app-center` deleteSession）〔REQ-E04〕
- `generateTitle(conversationId) → title` — LLM 起标题（模型取会话请求态 modelId、缺省 default 网关，与 C05 路由一致）；失败/空返回回退现行截断首问 30 字；**不抛错**〔REQ-E01〕
- `stop(conversationId)` — 取消活跃流；无活跃流时幂等 no-op〔REQ-E02〕
- `runStreamByConversation(conversationId, turnState, user) → SseEmitter` — 事件序列与 `## app-center` AppDialogOrchestrator 完全同词汇（sources + init/message/thinking/tool_call/tool_result/agent_state/replace/blocked/isGreeting + end/error），**零新增事件类型**〔REQ-C02〕

**Error modes:**
- E_CONVERSATION_NOT_FOUND / E_CONVERSATION_NOT_OWNED — 触发与恢复同解析器节（共享归属守卫；API Token 旁路沿用 `## app-center` NFR-02 语义）。
- E_INPUT_BLOCKED / E_STREAM_EXEC_FAILED / E_STREAM_TIMEOUT — 语义与恢复完全继承 `## app-center` AppDialogOrchestrator 既有定义（拦截不落库不调用 / error 事件后 complete / 超时部分落库），错误码复用不新设。
- 非错误约定：无权 KB 跳过告警；generateTitle 降级；stop 无活跃流。**本节零新增错误码。**

**Invariants:**
- Pre：全部方法先过共享归属守卫——**会话 ID 主键定位 + (app_id 非空时等值前置) + API Token 旁路**（app 可空后的定位键迁移不变量，design §1 账本扩展）。
- Post：listConversations 返回恒为 `PageResult{list,total,page,pageSize}`（禁止复现 listConversations 违例，NFR-01）。
- Post：每次对话落库（正常 onComplete / 超时 onTimeout / 停止部分落库三处）**同点固化** turnState 到 request_state〔REQ-B01；固化时机 design §2 裁决〕。
- Post：stop 后 emitter complete、已生成部分答案过滤后落库**恰一次**（AtomicBoolean 幂等守卫），置位后引擎推送失败不产生 error 事件（NFR-03 无孤儿流）。
- Post：clearMessages / deleteConversations 的级联语义与 `## app-center` 账本单会话删除完全一致（消息物理删）。
- Post：助手消息落库伴随 references 写入（形状见 data.md，两通道统一）〔REQ-F01〕。

**Parent Trace:** 接口 new-conversation.SessionCenter → Design.SessionCenterModule + 编排器跨链延伸 → REQ-A01/A03/A04/B01/C02/E01~E05/F01/G02/G04、NFR-01/03。

### new-conversation.StopRegistry（停止注册表契约）

**Purpose:** 单实例进行中对话的取消协议：把「谁在取消、取消后谁负责落库、重复取消怎么办」折叠为三操作，使流式执行核与控制器解耦。

**Input / Output:**
- `register(conversationId, handle{cancelled: AtomicBoolean, partialSave: Runnable, saved: AtomicBoolean}) → void` — **覆盖语义**：后注册者取代前者的取消目标
- `cancel(conversationId) → boolean` — 首次 true：置位 + 触发 partialSave **恰一次**；其后调用返回 false（幂等）
- `unregister(conversationId) → void` — 流终态（完成/超时/异常/停止）清理

**Error modes:** 无——并发行为按契约语义而非错误信号。

**Invariants:**
- Pre：partialSave 内部自行完成输出过滤 + 落库 + 请求态固化（复用超时路径语义），注册方不重复落库。
- Post：同会话并发双流的互斥治理**不在本契约**（现行系统未防，覆盖语义下 stop 作用于最后注册者）——已登记债务，归后续链。
- Post：字段面钉死 handle 三件套，禁止演进为事件重放/多副本结构（StreamManager 归 agent 域，届时本模块整体替换为适配）。

**Parent Trace:** 接口 new-conversation.StopRegistry → Design.StopRegistryModule → REQ-E02、NFR-03。

> **未立契约说明**：SessionRequestStateStore（load/persist 两方法）按薄包装排除规则不立接口章——其契约实质是 request_state 的 JSON 形状，唯一属主语义落 data.md `ConversationRequestState`；消息引用落库与 LLM 起标题均为内联 (S)（design §1），触点语义已并入 SessionCenter 不变式，不单独立契。

---

## qa-generation

> 对应 design 模块：QaGenerationModule（深模块）/ QaGenerationConsumerModule（消费者）/ QaGenerationController（(S) 排除）/ IngestionConsumer 与 RetrievalServiceImpl 两处内嵌变更点，见 design/qa-generation.md。数据实体契约见 data.md `## qa-generation` 节。
> **端点排除**：QaGenerationController 受理/查询两端点为 QaGenerationService 的薄片包装（@KbAuth + @Loggable + 透传），按排除规则不立契约；HTTP 暴露面归 design/qa-generation.md §1。
> **证据约定**：本节为前瞻契约（代码未实现），证据指向 design/spec 裁决（D 编号见 `fastrag/.tmp/idd-qa-generation-grill-decisions.md`）而非新代码行号；标注 ‹CERTAIN› 处为既有代码事实引用（沿 new-conversation 章约定）。
> **上游复用**：确认状态机零新增（复用 `## qa-pairs` 写入口径与 data.md `## qa-pairs` Lifecycle）；归一化判重复用 ExcelParserModule.normalizeQuestion（ExcelQaPairImporter.java:325 public static ‹CERTAIN›）；解析容错复用 BenchmarkGenerator 链的 BenchmarkResponseParser（knowledge→graph-eval 依赖已声明 pom.xml:19 ‹CERTAIN›）；消息拓扑复刻 graph-build（持久化队列 + 即时 ack + 长任务移出消费者，GraphBuildConsumer.java:176-216 ‹CERTAIN›）。

### qa-generation.QaGenerationService（问答对提炼编排契约）

**Purpose:** 问答对提炼的唯一业务入口：触发受理（参数校验→文件互斥→任务落账→投递）、任务状态查询、异步执行（父分片迭代→LLM 提炼→防御解析→静态闸门→replace/append 单事务落库→统计回写）三方法；三个调用方（控制器、摄入挂钩、消费者）对 prompt、闸门口径、事务边界零感知（design §2）。

**Input / Output（按方法）:**
- `trigger(kbId, fileId, config: QaGenerationConfig) → QaGenerationTaskView` — 受理一次提炼；config 形状见 data.md `## qa-generation` 节（count 1-10 缺省 3 / customInstruction ≤500 字符可空 / mode replace│append 由调用方填缺省 replace / model 可空走默认网关）；返回创建的任务视图（status=running、计数全 0）〔REQ-A01/A02/A03〕
- `status(kbId, fileId) → QaGenerationTaskView?` — 该文件最近一次任务（running/success/partial/failed 四态 + 分片统计 + 参数快照 + 错误信息）；从未触发返回 null〔REQ-D01/D02〕
- `execute(kbId, fileId, config) → void` — 消费者专用执行入口，跑完整流水线并回写任务终态；调用前须持有进程内文件互斥（消费者职责）〔REQ-B01~B05〕

**Error modes:**
- E_QA_GEN_PARAM_INVALID — 触发：count 越界 / customInstruction 超 500 字符 / mode 非枚举 / model 超 128 字符。恢复：调用方修正参数；trigger **不校验 model 存在性**（与 `## benchmark-evaluation` BenchmarkGenerator 同口径，未知模型表现为分片级失败入统计）。
- E_QA_GEN_FILE_INVALID — 触发：fileId 查无或不属于 kbId。恢复：业务错误提示。
- E_QA_GEN_NOT_READY — 触发：文件无父分片（解析未完成）。恢复：先完成解析再触发；解析完成但无父分片的空文档属正常完成（零分片零产出，status=success）。
- E_QA_GEN_TASK_RUNNING — 触发：同文件任务行 status=running 且未过陈旧阈值。恢复：手动路径返回业务错误（等待终态或 30 分钟陈旧恢复后重试）；摄入自动触发撞上时**静默跳过**（不阻断摄入主流程，不覆盖既有任务行）。〔REQ-A04〕
- E_QA_GEN_QUEUE_UNAVAILABLE — 触发：MQ 投递失败或不可用。恢复：任务行立即落 failed（error=队列不可用）+ 调用方收到业务错误；**无同步降级**（design 缝 2 偏差声明，区别于 `## file-ingestion` publishGraphBuild 的降级行为）。

**Invariants:**
- Pre(trigger)：调用方已过 KB ACL editor（控制器层）；任务行为每文件单行 upsert——新受理覆盖旧记录〔REQ-D01〕。
- Post(trigger)：恰存在一个 status=running 的任务行；kb_qa_pair **零增删**（落库只发生在 execute 收尾事务）。
- Pre(execute)：行态门——任务行 status≠running（已终态或被新任务覆盖）时消息静默丢弃（MQ 重投双跑防线，与进程内互斥构成双保险）。〔REQ-A04〕
- Post(execute success)：status=success（failedChunks=0）或 partial（failedChunks>0）；replace=该文件旧 ai 对全删且 manual 对零触碰、append=既有对全保留仅追加；产出每条 source=ai、status=draft、携带 source_chunk_id；闸门（归一化判重复用 normalizeQuestion 口径 + 问题<5 字/答案<2 字护栏）拦截计入 failedChunks。〔REQ-A05/B05/C01/D03〕
- Post(execute failure)：status=failed + error 摘要；kb_qa_pair 零变更；不向 MQ 抛异常（消息已即时 ack——GraphBuildConsumer.java:176-181 生产事故教训 ‹CERTAIN›）。
- Post：每分片完结即回写任务行计数与 updated_at（兼作陈旧心跳，阈值 qa.generation.stale-minutes 缺省 30）；收尾事务原子——replace 的删旧放在收尾事务而非任务开头，执行中途崩溃旧 QA 对无损。
- Post：悬空容忍——source_chunk_id 指向的分片因重切重建/手工删片消失时不清理、不报错，溯源降级为文件级（展示层口径）。〔REQ-D03/E01〕

**Parent Trace:** 接口 qa-generation.QaGenerationService → Design.QaGenerationModule → REQ-A01~A05、B01~B05、C01（复用）、D01/D03、NFR 异步持久化/吞吐。

### 变更点登记（内嵌变更，非新契约接口）

| 变更点 | 契约内容 | 位置 |
|---|---|---|
| 检索准入过滤 | qaChannelRecall 查询条件增加 `source='manual' OR status='confirmed'`——`## qa-pairs` 消费点登记债务②的清偿落点；qa 通道其余语义零变更（token LIKE 匹配、命中数降序、qaRecallCount、权重 0.3、异常降级空列表）〔REQ-C02；ADR-0005〕 | RetrievalServiceImpl.java:798-839 ‹CERTAIN› |
| 摄入挂钩 | IngestionConsumer 收尾 processing_mode='qa' 分支：读 kb_file.qa_generation_config → 组装 mode=replace 的 config → trigger；E_QA_GEN_TASK_RUNNING / E_QA_GEN_QUEUE_UNAVAILABLE 不阻断摄入主流程（任务行落 failed 供观测）；兑现 kb_file.processing_mode='qa' 消费端实现（qa-pairs spec REQ-E03 GAP 清偿）〔REQ-A03〕 | IngestionConsumer.java:494-515 图谱触发同区 ‹CERTAIN› |

**Parent Trace:** 变更点登记 → Design.检索准入过滤 / 摄入挂钩 → REQ-A03/C02、qa-pairs spec REQ-D04 修订与 REQ-E03 清偿。
