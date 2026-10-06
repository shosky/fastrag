# IAM 认证与账号权限 — 实现文档

> 记录**既有代码**（HEAD d8f30cd）如何落地 contract/interfaces.md `## iam` 六接口与 contract/data.md `## iam` 数据模式。
> 证据标注：`path:line ‹CERTAIN›` / 〔INFERRED：…〕/ `[GAP: …]`。文件路径前缀：security = `fastrag-security/src/main/java/com/fastrag/security/`，iam = `fastrag-modules/fastrag-iam/src/main/java/com/fastrag/module/iam/`。

## 1. Code File Map

镜像 design/iam.md Module Map 的 8 模块；只列 iam 相关文件。

**SecurityFilterModule + KbAccessDecisionModule（fastrag-security，14 文件）**

| 文件（security/ 前缀） | 一行职责 |
|---|---|
| config/SecurityConfig.java | 过滤器链装配、暴露面白名单、无状态+禁 CSRF、BCrypt 编码器〔SecurityConfig.java:60-88〕 |
| config/RedisConfig.java | StringRedisTemplate Bean（黑名单/ACL 缓存共用）〔RedisConfig.java:23-26〕 |
| filter/JwtAuthFilter.java | JWT Bearer 认证：黑名单检查→解析→建立 LoginUser〔JwtAuthFilter.java:60-96〕 |
| filter/ApiTokenAuthFilter.java | `frag_` 前缀 API Token 认证：经 common 缝验证→建立平台级身份〔ApiTokenAuthFilter.java:54-99〕 |
| filter/LoginUser.java | 认证主体载体，`*` 通配权限语义〔LoginUser.java:48-59〕 |
| annotation/KbAuth.java | 声明方法所需最低 KB 角色，默认 viewer〔KbAuth.java:20-22〕 |
| aspect/KbAuthAspect.java | @KbAuth 环绕切面：api-token 放行→URI 提 kbId→层级比较〔KbAuthAspect.java:62-95〕 |
| service/KbAccessChecker.java | KB 判定接口（security 声明 / knowledge 实现）〔KbAccessChecker.java:30-43〕 |
| service/KbAclService.java | ACL 管理接口（security 声明 / iam 实现）〔KbAclService.java:22〕 |
| model/KbAclDto.java | ACL 传输对象（id/kbId/userId/userName/kbRole/grantedBy/grantedAt） |
| util/JwtUtil.java | JWT 签发（五要素载荷/HMAC/24h）与解析校验〔JwtUtil.java:42-88〕 |
| util/SecurityUtil.java | 当前用户唯一读口，无上下文抛 401〔SecurityUtil.java:29-47〕 |
| util/DataScope.java | 非 KB 数据可见性/管理权纯函数〔DataScope.java:42-64〕 |
| util/OnlyOfficeJwtUtil.java | OO Document Server 专用 JWT 签发/验签——**归属 onlyoffice-editing 特性，本特性只认领 SecurityConfig.java:73-74 的白名单放行面，不展开** |

**AuthModule / ApiTokenModule / KbAclModule / RbacDataModule（fastrag-iam）**

| 文件（iam/ 前缀） | 一行职责 |
|---|---|
| controller/AuthController.java | 10 个认证端点 + 登录/登出/注册/微信四类日志埋点〔AuthController.java:61-193〕 |
| service/AuthService.java + impl/AuthServiceImpl.java | 三通道登录折叠、验证码流程、扫码状态机、角色/权限折叠〔AuthServiceImpl.java:37-394〕 |
| config/WechatMiniAppProperties.java | `wechat.miniapp.app-id/app-secret` 绑定〔WechatMiniAppProperties.java:25-29〕 |
| controller/ApiTokenController.java + service/ApiTokenService.java + impl/ApiTokenServiceImpl.java | Token 签发/脱敏/撤销/有效性验证〔ApiTokenServiceImpl.java:40-104〕 |
| service/impl/ApiTokenValidatorImpl.java | 机器认证缝的 iam 侧适配器，委托 findValidToken〔ApiTokenValidatorImpl.java:18-22〕 |
| controller/KbAclController.java | ACL 四管理端点（owner 门禁）+ 两自助查询〔KbAclController.java:46-67〕 |
| service/impl/KbAclServiceImpl.java | ACL CRUD + Redis 写穿/读穿一致性〔KbAclServiceImpl.java:11-34〕 |
| manager/PermissionChecker.java | `@perm` SpEL Bean：has/hasAny〔PermissionChecker.java:21-24〕 |
| controller/{Role,Permission,Personnel,Org}Controller.java + service/同名 Service + impl/同名 Impl | 角色/权限项/人员/组织四类授权数据管理〔RoleServiceImpl.java:11-30 等见 §2.6〕 |
| entity/（9 文件） | SysUser/SysRole/SysPermission/SysRolePermission/SysUserRole/SysOrg/KbAcl/SysApiToken/EmailVerification 表映射 |
| mapper/（9 文件） | 上表对应 MyBatis-Plus BaseMapper |
| model/（17 文件） | LoginRequest/LoginResponse、RegisterRequest、ResetPasswordRequest、SendCodeRequest、WechatLoginRequest/WechatQrConfirmRequest/WechatQrScene、ApiTokenCreateRequest/ApiTokenDto、RoleDto/RoleCreateRequest、PermissionDto/PermissionCreateRequest、PersonnelDto/PersonnelCreateRequest、OrgNodeDto |

**跨模块依赖（实现侧，非本目录）**

| 文件 | 一行职责 |
|---|---|
| fastrag-common/…/service/ApiTokenValidator.java | 机器认证缝接口（common 声明）〔ApiTokenValidator.java:10-18〕 |
| fastrag-common/…/exception/GlobalExceptionHandler.java | 异常→统一响应映射〔GlobalExceptionHandler.java:14-45〕 |
| fastrag-knowledge/…/service/impl/KbAccessCheckerImpl.java | KB 判定实现：max(ACL, 组织兜底)〔KbAccessCheckerImpl.java:29-68〕 |
| fastrag-operation SysLoginLogService | 登录日志落库（iam 经控制器跨模块调用）〔AuthController.java:59,69-79〕 |

## 2. Interface Implementations

### iam.AuthService（from contract: iam.AuthService）

实现单元：`AuthServiceImpl` + `AuthController`（日志埋点端点层）。

**Logic Flow:**

- `login`（AuthServiceImpl.java:37-52）：
  1. 按 username 查 sys_user〔:38〕
  2. IF 无记录 THEN 400「用户名或密码错误」〔:39〕
  3. IF status=disabled THEN 403「账号已被禁用」〔:40〕
  4. IF !BCrypt.matches THEN 400「用户名或密码错误」（与步骤 2 同文案，防枚举）〔:41〕
  5. loadRoleKeys：sys_user_role → sys_role.roleKey〔:161-168〕
  6. loadPermsByUser：IF 角色含 super_admin THEN 返回 `["*"]`〔:178-179〕ELSE 逐角色合并 sys_role_permission.permissionKey 去重〔:181-187〕
  7. JWT 签发：载荷 sub=userId + username/orgId/roles/permissions，HMAC，exp=now+24h〔:46 → JwtUtil.java:56-67〕
  8. RETURN LoginResponse{token, userInfo{id,username,realName,phone,email,orgId,roles,permissions}}〔:47-51〕
  - 控制器层：登录日志 try/catch 包裹，成功写 success、异常写 failed 后原样重抛〔AuthController.java:61-83〕
- `logout`：控制器截取 Bearer〔AuthController.java:92-95〕→ `SET jwt:blacklist:{token}="1" EX 24h`〔AuthServiceImpl.java:65-67〕
- `getUserInfo`：查 sys_user（无则 404）→ 实时重算 loadRoleKeys/loadPermsByUser（非令牌快照）〔:54-63〕
- `sendCode`（:69-96）：
  1. email 小写化〔:71〕
  2. IF Redis `email:rate:{email}:{purpose}` 存在 THEN 400「验证码发送过于频繁」〔:74-77〕
  3. 生成 6 位随机码 → 插 email_verification（expiresAt=+5min，used=false）〔:82-87〕
  4. 写频控键 TTL 60s〔:89〕
  5. IF purpose=register → sendRegisterCode；ELSE IF reset → sendResetPasswordCode〔:91-95〕
- `register`（:98-130）：verifyAndConsumeCode(register)〔:103〕→ 用户名查重 400「用户名已被注册」〔:105-107〕→ 邮箱查重 400「邮箱已被注册」〔:109-111〕→ 按 roleKey=**硬编码 kb_user** 取默认角色〔:113-115〕→ 插 sys_user（BCrypt/enabled/10GB=10737418240）〔:117-122〕→ IF 默认角色存在 THEN 插 sys_user_role〔:125-129〕
- `resetPassword`（:132-143）：verifyAndConsumeCode(reset)〔:135〕→ 按邮箱查用户，无则 400「该邮箱未注册」〔:137-139〕→ 更新 passwordHash〔:141-142〕
- `verifyAndConsumeCode`（:145-158）：按 email+code+purpose+used=false+expiresAt>now 四条件查最新一条（created_at 倒序 LIMIT 1）；查无 → 400「验证码无效或已过期」；命中 → used=1 回写（一次性消费）
- `wechatLogin`（:199-256）：
  1. code2Openid（jscode2session HTTP）；null/异常 → 400「微信登录失败，请重试」〔:201-204,361-381〕
  2. 按 openid 查用户〔:207-208〕
  3. IF 已绑定：IF disabled → 403〔:212-214〕；IF nickName 非空 → 回填 realName〔:216-219〕；RETURN buildLoginResponse〔:220〕
  4. ELSE 自动注册：用户名 `wx_{openid前8}`，冲突追加 `_{uuid4位}`〔:228-234〕→ 插 sys_user（enabled/10GB/openid/kb_user）〔:236-244〕→ 插 sys_user_role〔:247-252〕→ RETURN〔:255〕
- `generateQrScene`（:258-272）：scene=UUID 去横线前 16 位 → `SET wechat:qr:scene:{scene}="" EX 300`〔:260-261〕→ HTTP 拉小程序码（access_token 带 Redis 缓存 expires_in−300s，:301-326），异常或非 image 响应 → qrImageBase64=null 降级〔:263-268,329-358〕
- `pollQrLoginStatus`（:274-288）：GET 键；无键 → "expired"〔:278-281〕；空串 → null（waiting）〔:282-284〕；非空 → token〔:286-287〕；控制器映射 `{status: waiting|scanned(+token)|expired}`〔AuthController.java:140-150〕
- `confirmQrLogin`（:291-299）：GET 键，无键 → 400「二维码已过期，请刷新」〔:293-296〕；`SET 键=token EX 60` 供 PC 轮询取走〔:298〕；控制器先 wechatLogin 再 confirm（code 一次性，确认即登录）〔AuthController.java:175-182〕

**Module Interactions:**

- Calls：5 个 Mapper（SysUser/SysRole/SysRolePermission/SysUserRole/EmailVerification）；JwtUtil（security）；StringRedisTemplate；EmailService（infra，两模板方法）；微信开放平台 HTTP（三端点）；PasswordEncoder（SecurityConfig.java:85-88 Bean）
- Called by：AuthController（唯一调用方）
- Shared state：Redis `jwt:blacklist:{token}`/`email:rate:{email}:{purpose}`/`wechat:qr:scene:{scene}`/`wechat:access_token`；表 sys_user/sys_role/sys_role_permission/sys_user_role/email_verification；经 operation 的 SysLoginLogService 写 sys_login_log（AuthController.java:69-79,102,121,159-168,186-190）

**Decisions & Deviations:**

- 与契约一致：三通道折叠为同构 LoginResponse；super_admin 折叠 `["*"]`；验证码四条件消费（含 purpose 隔离）。
- 默认角色硬编码 kb_user 不读 is_default〔:113-114,224-226〕——契约 Invariants 已登记（interfaces.md:41），对应 spec REQ-023（2026-09-24 已裁：按现状为准，维持不修），非本层偏离。
- **死代码**：sendCode 内查询 kb_user 角色但从未使用〔:79-80〕——实现噪音，无行为影响。
- sys_user.role_id 遗留列仍写入（register :120、微信 :240）而鉴权读 sys_user_role〔:161-168〕——与 data.md「遗留列」记录一致。
- 被移除用户查重前验证码已消费（:103 先于 :105-111）——与契约 E_DUPLICATE_IDENTITY「验证码消费后检查」一致，非偏离。

**Parent Trace:** AuthServiceImpl/AuthController → iam.AuthService → Design.AuthModule → REQ-001~013、REQ-005/006、REQ-032。

### iam.SecurityGate（from contract: iam.SecurityGate）

实现单元：SecurityConfig + JwtAuthFilter + ApiTokenAuthFilter + SecurityUtil + PermissionChecker + LoginUser（+ 附属纯函数 DataScope）。

**Logic Flow:**

1. 链装配：addFilterBefore(apiTokenAuthFilter) → addFilterBefore(jwtAuthFilter)，均置于 UsernamePasswordAuthenticationFilter 之前〔SecurityConfig.java:80-81〕；STATELESS + 禁 CSRF〔:61-62〕；ASYNC 分发豁免（SSE 完成回调不重复鉴权）〔JwtAuthFilter.java:53-58〕
2. ApiTokenAuthFilter：IF SecurityContext 已有认证〔:57-61〕或 ApiTokenValidator 未注入（可选缝，:63-67）或 Bearer 非 `frag_` 前缀〔:70-72〕→ 原样放行交后继
3. `frag_` token → validateAndGetTokenId〔:74〕：IF 返回 null 或抛异常（吞掉仅 warn 日志，:90-95）→ 不建立认证继续；ELSE 建立平台身份 LoginUser{userId=`api-token:{tokenId}`, username=api-token, roles=[api_token], permissions=[kb:manage]} 写入 SecurityContext〔:77-88〕
4. JwtAuthFilter：IF 有 Bearer〔:63-65〕：IF `jwt:blacklist:{token}` 存在 → 跳过认证直接放行（受保护端点按未认证处理）〔:67-70〕；ELSE IF isTokenValid（签名+过期，JwtUtil.java:81-88）→ 解析五要素 claims → LoginUser + authorities=permissions 逐条 → 写入 SecurityContext〔:71-92〕；解析失败静默不建立
5. 授权规则：8 认证端点 permitAll〔SecurityConfig.java:65-69〕；OnlyOffice raw/callback permitAll（归属 onlyoffice）〔:73-74〕；Swagger/Actuator permitAll〔:76〕；`/api/**` authenticated〔:77〕；其余 permitAll〔:78〕
6. 方法级：`@PreAuthorize("@perm.has('…')")` → PermissionChecker.has/hasAny〔PermissionChecker.java:21-24〕→ SecurityUtil.hasPermission → LoginUser.hasPermission（`*` 通配全通）〔SecurityUtil.java:41-47、LoginUser.java:48-59〕
7. 错误恢复：SecurityUtil.getCurrentUser 无认证 → 401「未登录」〔SecurityUtil.java:29-35〕；@PreAuthorize false → AccessDeniedException → HTTP 403「无权限执行该操作」〔GlobalExceptionHandler.java:18-23〕；BusinessException → HTTP 200 + body.code；参数校验 → 400 含字段；兜底 → 500「服务异常」〔GlobalExceptionHandler.java:14-45〕
8. 附属纯函数 DataScope（REQ-031）：isApiToken=userId 前缀 `api-token:`〔DataScope.java:42-44〕；visible=api-token ∨ 系统级（creator=system 或 isBuiltin=1）∨ 属主 ∨ 同组织〔:52-57〕；manageable=api-token ∨ `*` 通配 ∨ 属主〔:60-64〕

**Module Interactions:**

- Calls：JwtUtil；StringRedisTemplate（RedisConfig.java:23-26 Bean）；ApiTokenValidator（common 缝）；SecurityContextHolder
- Called by：全部控制器经 SecurityContext/SecurityUtil 消费；KbAuthAspect 取当前用户〔KbAuthAspect.java:64〕；DataScope 被各业务 Service 过滤消费
- Shared state：Redis `jwt:blacklist:{token}`；SecurityContext（请求级，无持久状态）

**Decisions & Deviations:**

- 两过滤器职责互斥（frag_ 前缀分流），即使注册顺序互换功能等价：JwtAuthFilter 对 frag_ 串 isTokenValid 必失败、ApiTokenAuthFilter 只认 frag_——契约声明的顺序（ApiToken → JWT，interfaces.md:51）由注册序〔SecurityConfig.java:80-81〕与两过滤器 javadoc〔ApiTokenAuthFilter.java:9、JwtAuthFilter.java:9〕为证。
- 权限判定全部基于令牌快照，过滤器内零 DB 依赖〔JwtAuthFilter.java:71-92〕——「权限变更需重新登录」不变式成立。
- 未认证请求未定制 AuthenticationEntryPoint（SecurityConfig.java:60-83 无相关配置）→ HTTP 403（2026-09-24 深证：Spring Security 6.2 默认 Http403ForbiddenEntryPoint，见 spec REQ-021）——实现保持原状，断言已定稿。
- 黑名单键以完整 token 串为键〔JwtAuthFilter.java:67、AuthServiceImpl.java:66〕——内存放大属实现取舍，design Deepening 已登记。

**Parent Trace:** SecurityConfig/JwtAuthFilter/ApiTokenAuthFilter/SecurityUtil/PermissionChecker/DataScope → iam.SecurityGate → Design.SecurityFilterModule → REQ-004、REQ-018~021、REQ-031、REQ-032。

### iam.ApiTokenService（from contract: iam.ApiTokenService）

实现单元：ApiTokenServiceImpl + ApiTokenController + ApiTokenValidatorImpl（缝适配器）。

**Logic Flow:**

- `createToken(operatorId, request)`（ApiTokenServiceImpl.java:50-69）：
  1. 构建实体：name；permission=入参缺省 read〔:53〕；createdBy=operatorId；revoked=false〔:54-55〕
  2. token = `frag_` + UUID 去横线（32 位）〔:57-59〕
  3. IF expiresIn>0 THEN expiresAt=now+expiresIn；ELSE（null）永不过期〔:62-65〕
  4. insert → toDto 含明文 token〔:67-68,93-104〕
  - 控制器：@PreAuthorize kb:manage〔ApiTokenController.java:63〕+ @Loggable(token_created)〔:61〕；operatorId=SecurityUtil.getCurrentUserId()〔:65〕
- `listTokens`（:41-46）：createdAt 倒序全查 → toDto（token 原值）→ 控制器统一 `setToken(null)` 脱敏〔ApiTokenController.java:54-57〕
- `revokeToken(tokenId)`（:73-80）：selectById；null → 404「Token 不存在」〔:74-77〕；ELSE deleteById **物理删除**〔:79〕；控制器侧 @Loggable(token_deleted) + kb:manage〔ApiTokenController.java:71-73〕
- `findValidToken(tokenValue)`（:83-91）：token=值 AND revoked=false AND (expiresAt IS NULL OR expiresAt≥now) 查单条；ApiTokenValidatorImpl 委托并取 id〔ApiTokenValidatorImpl.java:19-22〕→ 供 ApiTokenAuthFilter 步骤 3 消费

**Module Interactions:**

- Calls：SysApiTokenMapper
- Called by：ApiTokenController；ApiTokenValidatorImpl → （common 缝 ApiTokenValidator.java:10-18）→ ApiTokenAuthFilter
- Shared state：表 sys_api_token（DDL 缺位警示复-3，见 data.md）

**Decisions & Deviations:**

- 撤销=物理删除、revoked 恒 false 不复用〔:55,79 vs :87〕——与 data.md 记录一致，非偏离。
- permission 字段（read/write）仅存储不参与鉴权：认证后统一 kb:manage〔:53 与 ApiTokenAuthFilter.java:81〕——spec 观测-2（2026-09-24 已裁：按现状为准），契约已登记，实现如实保持。
- 明文只出现一次由**控制器层**脱敏实现（toDto 注释「调用方决定是否脱敏」:97）——与契约「控制器层统一置 token=null」一致。
- expired 为计算字段非表列（:100）。

**Parent Trace:** ApiTokenServiceImpl/ApiTokenValidatorImpl → iam.ApiTokenService → Design.ApiTokenModule → REQ-014~017；缝 ApiTokenValidator → REQ-016。

### iam.KbAclService（from contract: iam.KbAclService）

实现单元：KbAclServiceImpl + KbAclController（security 声明接口 KbAclService.java:22）。

**Logic Flow:**

- `getKbAcl(kbId)`（KbAclServiceImpl.java:11-17）：按 kbId 查 kb_acl 全部 → 逐条 KBRole.valueOf + **逐条**查 sys_user 回填 userName（查无回退 userId）〔:14-15〕
- `setKbAcl(kbId, entries)`（:18-22）：**先**查该库全部旧记录并逐条 `DEL kb:acl:{kbId}:{userId}`（防被移除用户 TTL 残留）〔:20〕→ DELETE 该库全部行 → 逐条调 addAclEntry（全量覆盖语义）〔:21〕
- `addAclEntry(kbId,userId,role,grantedBy)`（:23-27）：DELETE 同 (kbId,userId) 旧行（upsert 前半，表无唯一约束靠应用层）〔:24〕→ INSERT 新行（grantedAt=now）〔:25〕→ `SET kb:acl:{kbId}:{userId}=role.name() EX 24h`（写穿）〔:26〕
- `removeAclEntry`（:28）：DELETE 行 + `DEL` 缓存键（同步删除）
- `getAccessibleKbIds(userId)`（:29）：按 userId 查 kb_acl → distinct kbId（仅显式 ACL 库，不含组织库——组织侧并集在 KbAccessCheckerImpl.java:59-66）
- `getKbRole(userId,kbId)`（:30-34）：GET 缓存命中 → valueOf 返回〔:31〕；未命中 → 查表〔:32〕；有 → 回填缓存 EX 24h 并返回（读穿）〔:33〕；无 → null
- 控制器门禁：四管理端点 @KbAuth(KBRole.owner)〔KbAclController.java:46-56〕，三写端点加 @Loggable(config_changed)〔:49,52,55〕；两自助查询走 checkSelfOrAdmin：本人或 hasPermission("*")，否则 403「无权查看该用户的权限」〔:57-58,63-67〕

**Module Interactions:**

- Calls：KbAclMapper、SysUserMapper、StringRedisTemplate
- Called by：KbAclController；KbAccessCheckerImpl（resolveRole :31 / getAccessibleKbIds :59）
- Shared state：表 kb_acl；Redis `kb:acl:{kbId}:{userId}`（TTL 24h）

**Decisions & Deviations:**

- 缓存一致性纪律与契约四条 Post 不变式逐一对应（写穿/读穿/移除同删/覆盖先清全量），无偏离。
- E_KB_ROLE_INVALID 隐含路径成立：三处 KBRole.valueOf 无显式捕获〔:14,31,33〕——契约该条已于 2026-09-24 深证升级 CERTAIN（全文件无 try/catch，异常升至 GlobalExceptionHandler → 200+code 500）。
- getKbAcl 逐条查用户为 N+1〔:15〕——未批量化（人员侧已批量化，见 §2.6），design Deepening 方向一致，现状如实记录。
- kb_acl 无 (kb_id,user_id) 唯一约束，upsert 靠先删后插〔:24〕——并发窗口理论可重，data.md 已记。

**Parent Trace:** KbAclServiceImpl/KbAclController → iam.KbAclService → Design.KbAclModule → REQ-026、REQ-029、REQ-030。

### iam.KbAccessChecker + @KbAuth（from contract: iam.KbAccessChecker）

实现单元：KbAuthAspect + KbAuth（security）；判定实现 KbAccessCheckerImpl（knowledge 侧）。

**Logic Flow:**

- 切面（KbAuthAspect.java:62-80）：
  1. user = SecurityUtil.getCurrentUser()〔:64〕
  2. IF userId 以 `api-token:` 开头 THEN 直接 proceed（平台级身份全局放行，登录用户含超管/kb_admin 一律走判定无旁路）〔:66〕
  3. kbId = extractKbId()：URI 定位 `/api/kb/` 前缀取其后到下一个 `/` 之前〔:82-95〕；null → 400「缺少知识库ID」〔:68-69〕
  4. userRole = accessChecker.resolveRole(kbId, user)〔:72〕
  5. IF null THEN 403「无知识库访问权限」〔:73〕
  6. required = 层级表[kbAuth.value()]（缺省 viewer，KbAuth.java:21；表 owner=3/editor=2/viewer=1，KbAuthAspect.java:55-56）；actual = 层级表[userRole]；IF actual < required THEN 403「知识库权限不足」〔:75-77〕
  7. RETURN proceed〔:79〕
- resolveRole（KbAccessCheckerImpl.java:29-35，判定数据源现状 = 契约复-1 描述）：
  1. aclRole = aclService.getKbRole(userId, kbId)（Redis 读穿 → kb_acl）〔:31〕
  2. orgRole = orgFallbackRole：用户无 orgId → null〔:39〕；kb 不存在或 orgId 不匹配 → null〔:40-41〕；kb.permission=private → null（指定人共享库无兜底）〔:42〕；有 `*` 通配或角色含 kb_admin → owner〔:43-45〕；否则 editor〔:46〕
  3. RETURN max(aclRole, orgRole)——ACL 只能提升、不能降级组织默认权限〔:34,50-54〕
  4. getAccessibleKbIds：ACL 库 ∪ 本组织非 private 库 id 集合〔:57-68〕

**Module Interactions:**

- Calls：SecurityUtil；KbAccessChecker（security 接口）→ KbAclService（iam 实现）、KnowledgeBaseMapper（knowledge）
- Called by：全部标注 @KbAuth 的控制器方法（KbAclController.java:46-56 与 knowledge 域 KbController/FileController/ChunkController 等十余控制器）
- Shared state：Redis `kb:acl:*`（经 KbAclService）；表 kb_acl、kb

**Decisions & Deviations:**

- **接口 javadoc 过期（复-1）**：KbAccessChecker.java:33-35 与切面注释〔KbAuthAspect.java:21,71〕仍写「同组织兜底 viewer」，实现已为分级语义（private 无兜底/管理员 owner/普通成员 editor + max 合并）〔KbAccessCheckerImpl.java:38-54〕——契约现状描述与代码一致；javadoc 修订属代码修改，待用户仲裁后处理。
- 层级比较用 getOrDefault(…, 0)：未知角色按 0 兜底拒绝〔:75-76〕——与 E_KB_ROLE_INSUFFICIENT 语义一致的防御路径。
- E_KB_ID_MISSING 为防御路径：现存 @KbAuth 用点均在 /api/kb/ 前缀路径内。

**Parent Trace:** KbAuthAspect/KbAuth/KbAccessCheckerImpl → iam.KbAccessChecker → Design.KbAccessDecisionModule → REQ-027、REQ-028（复-1）、REQ-031。

### iam.RbacDataService（from contract: iam.RbacDataService）

实现单元：RoleServiceImpl / PermissionServiceImpl / PersonnelServiceImpl / OrgServiceImpl + 四控制器。

**Logic Flow:**

- 角色（RoleServiceImpl.java:11-30）：
  - createRole：roleKey=`custom_`+System.currentTimeMillis()（系统生成，入参不可指定）〔:14〕；isDefault=0/isSystem=0；insert + savePerms（distinct 逐条插入，null 直接返回）〔:15,27〕
  - updateRole：查无 404〔:18〕；仅更新 name/description（roleKey 不可改）〔:19〕；权限全量替换=先删该角色全部关联再 savePerms〔:20〕
  - deleteRole：isSystem=1 → 400「系统角色不可删除」〔:22〕；否则仅 deleteById，**不级联清理 sys_role_permission**〔:22〕
  - setDefault：全表 is_default=1 先清零 → 目标置 1（全局恒一个默认）〔:23-26〕
  - toDto 逐角色查关联〔:30〕——N+1 未批量化
- 权限（PermissionServiceImpl.java:15-134）：
  - listPermissions：全量权限 + 全量关联按 permKey 分组反查 roleIds（批量消除 N+1）〔:16-27〕
  - getPermissionTree：listPermissions → 按 group 分组（null 归 "other"，LinkedHashMap 保序）构造 `group:` 伪节点 → 组内按 parentKey 递归建子树〔:31-48,97-122〕；组标签中文映射〔:124-133〕
  - createPermission：permKey 查重 → 400「权限标识已存在」〔:52-54〕；category 缺省 page_action〔:59〕
  - updatePermission：permKey 不可改（更新分支不触碰）〔:66-74〕
  - deletePermission：级联删 sys_role_permission 中该 permKey 全部 → 删主表〔:78-85〕
- 人员（PersonnelServiceImpl.java:15-115）：
  - listPersonnel：keyword like username OR realName〔:17-18〕+ createdAt 倒序〔:19〕+ Page → `PageResult.of(list,total,page,pageSize)`〔:20-23〕
  - createPersonnel：username 查重 400「用户名已存在」〔:28-30〕；密码缺省 123456 BCrypt〔:34-36〕；status=enabled；插角色关联〔:40-46〕
  - updatePersonnel：不触碰 username；密码仅在提供时更新〔:50-59〕
  - assignRoles：先删该用户全部关联再插（全量替换）〔:61-76〕
  - updateStatus：改 status〔:78-83〕→ 登录禁用联动（AuthServiceImpl.java:40,212-214）
  - toDto：orgName 回填；角色批量查询（selectBatchIds 消除 N+1）〔:102-109〕
- 组织（OrgServiceImpl.java:11-43）：
  - getOrgTree：全量按 sort 升序 → 按 parentId 分组自 root 递归；memberCount 每节点实时 count sys_user〔:11-23〕
  - createOrg：parentId 缺省 root；**level 硬编码** parentId!=null?2:1〔:39〕
  - updateOrg：查无 404〔:42〕；deleteOrg：存在子组织 → 400「存在子组织」〔:43〕
- 控制器门禁：三粗键 @PreAuthorize——admin:role〔RoleController.java:42-51、PermissionController.java:43-63〕/ admin:user〔PersonnelController.java:49-73〕/ admin:org〔OrgController.java:49-55〕；低门槛读口无注解仅登录（PersonnelController.java:79,88、OrgController.java:44-47）；管理写操作 @Loggable（各控制器实测，REQ-033）

**Module Interactions:**

- Calls：SysRoleMapper/SysRolePermissionMapper/SysPermissionMapper/SysUserMapper/SysUserRoleMapper/SysOrgMapper
- Called by：四控制器；OrgServiceImpl.getDepartmentMembers 复用 PersonnelDto 精简映射〔OrgServiceImpl.java:33-37〕；AuthServiceImpl 读同一批表做登录期角色/权限折叠（读写分属两模块）
- Shared state：表 sys_user/sys_role/sys_permission/sys_role_permission/sys_user_role/sys_org；无缓存

**Decisions & Deviations:**

- deleteRole 不级联（:22）与 deletePermission 级联（:82-83）**不对称**——契约已登记为债务行为（interfaces.md:157，2026-09-24 已裁：按现状为准），实现如实保持。
- updateRole 无「默认角色权限不可改」的后端保护〔:17-21〕——spec 观测-3（2026-09-24 已裁：按现状为准，不补防护），契约已登记。
- org.level 硬编码三层树失真〔:39〕——data.md 已记。
- savePerms 在 perms=null 时跳过插入〔:27〕：updateRole 传 null = 清空权限——「全量替换」语义的边界表现，与契约一致。

**Parent Trace:** 四个 Impl + 四控制器 → iam.RbacDataService → Design.RbacDataModule → REQ-022~025、REQ-033。

## 3. Testing

引用 tdd/iam.md 的 10 切片规划（切片 0 HITL 仲裁 → 切片 9 DataScope 纯函数起步 → 1 鉴权闸 → 其余）。

- **现状覆盖：零。** fastrag-iam 无 `src/test` 目录，fastrag-security/src/test 无任何测试文件（2026-09-21 实测 find 为空）——tdd/iam.md「待建义务」基线核实成立。
- **缺口（按切片）**：切片 1~8 全部未建；其中切片 0（复-1 兜底语义仲裁）阻塞切片 7 断言定稿；切片 6（ACL 缓存一致性）依赖嵌入式 Redis。
- 实现侧为测试预留的可用锚点：DataScope 纯函数零依赖（DataScope.java:42-64）可作 Tracer Bullet；H2 基建含 sys_api_token（schema-h2.sql:757-769）支撑切片 5；ApiTokenValidator 可选注入缝允许在无 iam 依赖场景单测过滤器短路逻辑（ApiTokenAuthFilter.java:63-67）。
