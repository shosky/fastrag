# IAM 认证与账号权限 — 架构设计

> 依据：spec/iam.md（REQ-001~033，含搬入复核复-1~复-5）+ HEAD d8f30cd 代码实读。
> 证据标注：`path:line ‹CERTAIN›` / 〔INFERRED：…〕/ `[GAP: …]`。
> ADR 对接：本特性无专属 ADR。上位既有 ADR-0001（解析策略绑定-重切一致性）、ADR-0002（Neo4j 唯一 GraphStore）均与 IAM 无涉，不引用。

## 1. Module Map

| 模块 | Responsibility（一行） | Files | Depth Rationale |
|---|---|---|---|
| **认证会话模块（AuthModule）** | 三通道认证（密码/微信扫码/微信小程序）+ 邮箱验证码账号流程 + JWT 签发与登出吊销，把「证明你是谁」的全部复杂度收进一个入口 | fastrag-iam/…/controller/AuthController.java、service/AuthService.java、service/impl/AuthServiceImpl.java、config/WechatMiniAppProperties.java | **深**：一个 `login()` 调用掩盖了角色/权限折叠、super_admin 通配折叠、防枚举文案、禁用拦截三条业务规则（AuthServiceImpl.java:37-52）；调用方只见 `LoginResponse` |
| **请求鉴权基建（SecurityFilterModule）** | 每个请求建立（或不建立）认证上下文：过滤器链、白名单、JWT 解析、平台级 Token 识别、当前用户访问入口 | fastrag-security/…/config/SecurityConfig.java、config/RedisConfig.java、filter/JwtAuthFilter.java、filter/ApiTokenAuthFilter.java、filter/LoginUser.java、util/JwtUtil.java、util/SecurityUtil.java、fastrag-iam/…/manager/PermissionChecker.java | **深**：全系统 55 控制器消费它而无需知道 JWT 载荷结构/黑名单键/frag_ 前缀约定；`SecurityUtil.getCurrentUser()` 是唯一读口（SecurityUtil.java:29-39） |
| **API Token 模块（ApiTokenModule）** | 平台级程序化凭证的签发、脱敏查询、撤销与有效性验证 | fastrag-iam/…/controller/ApiTokenController.java、service/ApiTokenService.java、service/impl/ApiTokenServiceImpl.java、service/impl/ApiTokenValidatorImpl.java、entity/SysApiToken.java、model/ApiToken*.java | **深**：「明文只出现一次 + 服务端全值比对 + 过期/撤销判定 + 验证器可插拔」被 4 个方法吃完（ApiTokenServiceImpl.java:48-91） |
| **KB ACL 管理模块（KbAclModule）** | 知识库×用户的显式授权 CRUD 及 Redis 读穿缓存一致性 | fastrag-iam/…/controller/KbAclController.java、service/impl/KbAclServiceImpl.java、entity/KbAcl.java；接口声明 fastrag-security/…/service/KbAclService.java、model/KbAclDto.java | **深**：`setKbAcl`/`addAclEntry`/`removeAclEntry` 各自内联了「先清缓存再落数据」的一致性纪律（KbAclServiceImpl.java:18-28）；调用方不感知缓存键结构 |
| **KB 访问判定模块（KbAccessDecisionModule）** | 把「这个用户在这个知识库是什么角色」判定为统一的层级比较闸门 | fastrag-security/…/annotation/KbAuth.java、aspect/KbAuthAspect.java、service/KbAccessChecker.java；实现（依赖侧）fastrag-knowledge/…/service/impl/KbAccessCheckerImpl.java | **深**：注解一行 `@KbAuth(KBRole.editor)` 吞掉 URI 解析、api-token 放行、max(ACL,组织兜底) 解析、三层级比较（KbAuthAspect.java:62-80）；全部 /api/kb/** 端点复用 |
| **RBAC 授权数据模块（RbacDataModule）** | 角色/权限项/人员/组织四类管理数据的约束执行（系统角色保护、默认角色全局唯一、权限标识唯一与级联、组织子节点保护、人员角色全量替换） | fastrag-iam/…/controller/{RoleController,PermissionController,PersonnelController,OrgController}.java、service/{RoleService,PermissionService,PersonnelService,OrgService}.java、service/impl/同名 Impl、entity/Sys*.java、mapper/Sys*Mapper.java、model/{Role*,Permission*,Personnel*,OrgNode}*.java | **中**：按控制器拆为四个浅面，但约束规则（默认角色唯一翻转 RoleServiceImpl.java:23-26、删除权限级联清关联 PermissionServiceImpl.java:78-85）是真业务规则；单控制器本身 CRUD 味重，整体作为「授权数据的一个聚合面」供权限页消费 |
| **数据范围判定（DataScope）(S)** | 非知识库数据的可见性/管理权纯函数判定 | fastrag-security/…/util/DataScope.java | **浅**：无状态静态工具（DataScope.java:38-64）。不内联建议保留：它是全仓 6 处消费方共享的单一判定词典，消灭它会让规则散进各 Service |
| **OnlyOffice JWT 工具（OnlyOfficeJwtUtil）(S)** | OO Document Server 间 JWT 签发/验签 | fastrag-security/…/util/OnlyOfficeJwtUtil.java | **浅**：独立外部集成工具。spec 明确其需求归 onlyoffice-editing 特性，本特性只认领「该白名单放行面存在于 SecurityConfig.java:73-74」这一事实；深化建议随 onlyoffice-editing 链处理 |

## 2. Interface Contracts

> 深契约（跨模块共享、带错误模式与不变式）落在 contract/interfaces.md 的 `## iam` 节；此处只列各模块公共接口的方法面。

**AuthModule — `AuthService`**〔证据：fastrag-iam/…/service/AuthService.java:4-21 ‹CERTAIN›〕
- `login(LoginRequest): LoginResponse` — 密码登录，签发含角色/权限的 JWT
- `getUserInfo(String userId): UserInfoDto` — 实时角色/权限重算
- `logout(String token)` — 令牌入 Redis 黑名单
- `sendCode / register / resetPassword` — 验证码账号流程三步
- `wechatLogin / generateQrScene / pollQrLoginStatus / confirmQrLogin` — 微信两通道
- Hidden：角色折叠与 `*` 通配折叠（AuthServiceImpl.java:170-188）、验证码消费原子语义（:145-158）、微信 HTTP 细节（:301-381）

**SecurityFilterModule — `SecurityUtil`（静态）+ `PermissionChecker`（`@perm` Bean）**〔证据：SecurityUtil.java:28-47、PermissionChecker.java:21-24 ‹CERTAIN›〕
- `getCurrentUser() / getCurrentUserId() / hasPermission(String)` — 当前身份唯一读口
- `has(String) / hasAny(String...)` — `@PreAuthorize("@perm.has('…')")` 的 SpEL 落点
- Hidden：LoginUser 的通配语义（LoginUser.java:48-59）、过滤器顺序与 ASYNC 分发豁免（JwtAuthFilter.java:53-58）

**ApiTokenModule — `ApiTokenService` + `ApiTokenValidator`（common 接口）**〔证据：ApiTokenService.java:17-24、common/service/ApiTokenValidator.java:18 ‹CERTAIN›〕
- `listTokens() / createToken(operatorId, request) / revokeToken(tokenId) / findValidToken(tokenValue)`
- `ApiTokenValidator.validateAndGetTokenId(String): String|null` — security 层与 iam 实现的解耦点
- Hidden：`frag_` 生成、脱敏策略在控制器层（ApiTokenController.java:53-57）

**KbAclModule — `KbAclService`**（接口在 security，实现在 iam）〔证据：KbAclService.java:22、KbAclServiceImpl.java:9 ‹CERTAIN›〕
- `getKbAcl / setKbAcl / addAclEntry / removeAclEntry / getAccessibleKbIds / getKbRole`
- Hidden：`kb:acl:{kbId}:{userId}` 缓存键与 24h TTL（KbAclServiceImpl.java:20-33）

**KbAccessDecisionModule — `@KbAuth` + `KbAccessChecker`**〔证据：KbAuth.java:20-22、KbAccessChecker.java:30-43 ‹CERTAIN›〕
- `@KbAuth(value=KBRole)` — 声明最低知识库角色，默认 viewer
- `resolveRole(kbId, user): KBRole|null` / `getAccessibleKbIds(userId, orgId)` — 判定数据源
- Hidden：URI 提取 kbId（KbAuthAspect.java:82-95）、层级表 owner3>editor2>viewer1（:55-56）；实现侧 max(ACL, 组织兜底) 在 knowledge 模块（见复-1 仲裁项）

**RbacDataModule — `RoleService` / `PermissionService` / `PersonnelService` / `OrgService`**〔证据：四个 Service 接口文件 ‹CERTAIN›〕
- 角色：list/get/create/update/delete/setDefault；权限：list/tree/create/update/delete；人员：list/create/update/assignRoles/updateStatus/findByUsername；组织：tree/flat/departments/members/create/update/delete
- Hidden：roleKey 生成规则、权限树两段式构建（group → parentKey，PermissionServiceImpl.java:31-48,97-122）

## 3. Seam Diagram

| 缝 | What varies | Adapters | Stable side | Placement rationale |
|---|---|---|---|---|
| **机器认证缝：`ApiTokenValidator`**（fastrag-common） | 机器凭证的验证方式（查表校验 / 未来 HMAC、OAuth 等） | 2：`ApiTokenValidatorImpl`（iam 查 sys_api_token 表）；`ApiTokenAuthFilter` 作为消费侧（fastrag-common/service/ApiTokenValidator.java、ApiTokenValidatorImpl.java:14-23、ApiTokenAuthFilter.java:51-52 `@Autowired(required=false)`） | `ApiTokenAuthFilter` 与 SecurityConfig（认证协议不变） | security 模块不依赖 fastrag-iam 即可编译部署（可选注入 + 双向 null 检查），是模块拓扑上真正的可拆卸缝 |
| **KB 判定缝：`KbAccessChecker`**（security 声明 / knowledge 实现） | 「有效角色」的数据来源与兜底策略（现 ACL+组织；可演化租户策略） | 1：`KbAccessCheckerImpl`（knowledge，查 kb 表组织归属）——**单适配器，按 2+ 标准属假设缝**，但接口已被 KbAuthAspect 与 operation/retrieval 等多模块消费方验证 〔证据：KbAccessCheckerImpl.java:24、KbAuthAspect.java:53-59 ‹CERTAIN›〕 | `KbAuthAspect` 层级比较协议（owner>editor>viewer 数值序） | 判定需要查 kb 表（knowledge 的领域数据），而切面必须留在 security——不立缝则形成 security→knowledge 反向依赖 |
| **KB ACL 数据缝：`KbAclService`**（security 声明 / iam 实现） | ACL 存储与缓存实现 | 1：`KbAclServiceImpl`（iam）——假设缝（消费方 KbAccessCheckerImpl/KbServiceImpl 在 knowledge）〔证据：KbAclServiceImpl.java:9 ‹CERTAIN›〕 | ACL 语义（kbId×userId→KBRole） | security 只需要「能问 ACL」，不需要知道表与 Redis；接口声明在 security 使 knowledge 可消费而不依赖 iam |
| **邮件发送缝：`EmailService`**（infra） | 邮件通道（SMTP/第三方） | 1：infra 实现——假设缝，未单列契约，验证码邮件模板经 `sendRegisterCode/sendResetPasswordCode` 两方法固定 〔证据：AuthServiceImpl.java:91-95 ‹CERTAIN›〕 | AuthModule 的验证码流程 | 外部 IO 边界，标准降级点（失败即抛 BusinessException，无重试语义） |
| **微信开放平台边界**（外部 API） | 微信接口可用性 | `code2Openid`/`getAccessToken`/`generateMiniAppQrCode` 三个私有方法即适配器（AuthServiceImpl.java:301-381），失败降级为 null/无图返回 | AuthModule 的扫码状态机（Redis scene 键三态） | 系统边界；唯一 mock 合法点（TDD 纪律） |

## 4. Deepening Opportunities

- **AuthModule**：微信三端点（qr-scene/qr-status/qr-confirm + login）的 Redis 状态机（scene 键空值=waiting、值=token、无键=expired，AuthServiceImpl.java:274-299）可下沉为 `QrLoginSession` 值对象，把「60 秔回写 TTL」等数值语义收拢；当前散在两个方法里。
- **SecurityFilterModule**：黑名单键 `jwt:blacklist:{token}` 以全令牌串为键（AuthServiceImpl.java:66、JwtAuthFilter.java:67），令牌串长导致 Redis 内存放大；可深化为以 jti/摘要为键——属实现演化，不动接口。
- **KbAccessDecisionModule**：`extractKbId()` 以字符串前缀 `/api/kb/` 解析 URI（KbAuthAspect.java:82-95），对非 `/api/kb/` 路径的知识库操作端点（如 `/api/acl/...`）天然不适用；可深化为注解参数显式传 kbId（SpEL 取路径变量）。
- **RbacDataModule**：`toDto` 逐角色查关联（RoleServiceImpl.java:30）与人员 N+1 已部分消除（PersonnelServiceImpl.java:107-109 注释「消除 N+1」）；角色侧可同样批量化。
- **DataScope / OnlyOfficeJwtUtil**：None（维持现状，见 Module Map 的 (S) 说明）。

## 5. Parent Trace

| Design 单元 | Spec REQ |
|---|---|
| AuthModule（三通道+验证码+登出） | REQ-001~013、REQ-005/006（登录日志、userinfo 实时性） |
| SecurityFilterModule（过滤器链/JWT/方法级权限/异常映射） | REQ-004、REQ-018~021、REQ-032 |
| ApiTokenModule | REQ-014~017、观测-2 |
| KbAclModule（ACL 管理+缓存一致性） | REQ-026、REQ-029、REQ-030 |
| KbAccessDecisionModule（层级判定+数据源优先级） | REQ-027、REQ-028（复-1 仲裁项）、REQ-031（判定协议同源） |
| RbacDataModule（角色/权限/人员/组织） | REQ-022、REQ-023（脱节项 2026-09-24 已裁：按现状为准）、REQ-024、REQ-025、REQ-033（审计注解） |
| DataScope | REQ-031 |
| OnlyOfficeJwtUtil（白名单暴露面事实） | REQ-018（OO 回调免认证项） |
| 各缝：ApiTokenValidator | REQ-016；KbAccessChecker | REQ-027/028；KbAclService | REQ-029 |
| AuthModule（三通道+验证码+登出） | REQ-011（2026-09-24 review 补锚：PC 端轮询扫码状态三态——waiting/scanned（附令牌）/expired） |
| SecurityFilterModule（过滤器链/JWT/方法级权限/异常映射） | REQ-020（2026-09-24 review 补锚：方法级权限 @perm.has/hasAny 声明所需权限，判定支持 * 通配与 super_admin 折叠全通） |

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
