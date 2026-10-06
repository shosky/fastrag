# IAM 认证与账号权限 — 测试设计

> 契约来源：contract/interfaces.md `## iam` 六接口；追溯锚点 spec/iam.md REQ-001~033。
> **现状基线（如实记录）**：fastrag-iam 模块无 `src/test` 目录，fastrag-security 的 `src/test` 为空包（实测，2026-09-21）——本文件是**待建义务**，不是既有事实。仓库已有 H2 单测基建可复用：`fastrag-bootstrap/src/main/resources/schema-h2.sql`（含 sys_api_token:757-769）与 `data-h2.sql` 种子。
> mock 纪律：只在真缝/系统边界 mock——微信开放平台 API、邮件发送（EmailService）、Redis（优先嵌入式/抽象缓存门面）、时间（TTL/过期断言）；**禁止 mock 内部模块**。

## 1. 切片规划

| # | 切片（按契约接口分组） | AFK/HITL | 说明 |
|---|---|---|---|
| 0 | **判定语义仲裁**（复-1 前置） | **HITL** | KbAccessChecker「同组织兜底」语义（viewer vs editor/owner 分级）待用户仲裁；仲裁前切片 7 的断言无法定稿 |
| 1 | iam.SecurityGate — 白名单边界与过滤器链 | AFK | MockMvc 级；无需任何 mock（无凭据请求即覆盖） |
| 2 | iam.AuthService — 密码登录三态/登出/userinfo | AFK | H2 种子用户 + 嵌入式 Redis |
| 3 | iam.AuthService — 验证码账号流程 | AFK | mock EmailService；频控依赖 Redis TTL |
| 4 | iam.AuthService — 微信扫码状态机 | AFK | mock 微信 HTTP（HttpClient 边界）；时间可控 |
| 5 | iam.ApiTokenService — 令牌生命周期 | AFK | H2（含 sys_api_token 表） |
| 6 | iam.KbAclService — ACL 管理与缓存一致性 | AFK | 嵌入式 Redis 必需（一致性是断言主体） |
| 7 | iam.KbAccessChecker/@KbAuth — 层级判定 | AFK（切片 0 后） | 依赖复-1 仲裁结果 |
| 8 | iam.RbacDataService — 管理约束 | AFK | H2；约束断言为主 |
| 9 | DataScope — 可见性/管理权纯函数 | AFK | 纯单元，零依赖，Tracer Bullet 首选 |

Tracer Bullet 起步建议：切片 9（纯函数最快点亮）→ 切片 1（鉴权闸是全系统前提）→ 其余按序。

## 2. 测试用例（逐接口）

### iam.SecurityGate（contract: iam.SecurityGate）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 白名单端点_无凭据_直达业务逻辑 | 系统就绪 | 未携带令牌 POST /api/auth/register | 请求进入业务层（返回业务响应而非认证拒绝） | REQ-018 | 无 |
| 受保护端点_无凭据_被闸拦截 | 系统就绪 | 未携带令牌 GET /api/personnel | 不进入业务逻辑，HTTP 403（REQ-021 已于 2026-09-24 深证：Spring 默认 Http403ForbiddenEntryPoint） | REQ-018/021 | 无 |
| 黑名单令牌_已登出_不建立上下文 | 令牌 T 已写入 jwt:blacklist | 携带 Bearer T 访问受保护端点 | 按未认证处理 | REQ-004 | 嵌入式 Redis |
| 过期令牌_不建立上下文 | 令牌 exp < now | 携带该令牌访问受保护端点 | 按未认证处理 | REQ-019 | 时间 |
| frag_前缀令牌_建立平台身份 | 有效 sys_api_token 记录 | 携带 Bearer frag_xxx 访问 /api/personnel | 认证主体 userId=api-token:{tokenId}，权限含 kb:manage | REQ-016 | 无 |
| 异常映射_方法级拒绝_403 | 权限不含 admin:role 的身份 | 调用 @perm.has('admin:role') 端点 | HTTP 403 +「无权限执行该操作」 | REQ-021 | 无 |
| 异常映射_业务错误_200加body码 | 系统就绪 | 触发任一 BusinessException | HTTP 200 且 body.code 为业务码 | REQ-032 | 无 |

### iam.AuthService（contract: iam.AuthService — 密码通道）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 正确凭据_登录_返回令牌与用户信息 | enabled 用户（BCrypt 口令） | login(正确用户名密码) | token 非空；userInfo.roles/permissions 与库一致 | REQ-001 | 嵌入式 Redis |
| 用户不存在与密码错误_文案完全一致 | 存在用户 u | 分别以不存在用户名/错误密码登录 | 两次错误文案均为「用户名或密码错误」 | REQ-002 | 嵌入式 Redis |
| 禁用账号_两通道_均拒绝 | status=disabled 用户 | 密码登录 | 「账号已被禁用」 | REQ-003 | 嵌入式 Redis |
| 登出_令牌写入黑名单 | 有效登录令牌 T | logout(T) | redis 出现 jwt:blacklist:T 键，TTL=24h；再次认证被拒 | REQ-004 | 嵌入式 Redis、时间 |
| userinfo_权限实时重算 | 用户角色已变更但持旧令牌 | getUserInfo | 返回库中新角色/权限（非令牌快照） | REQ-006 | 无 |
| 登录成败_均落登录日志 | 系统就绪 | 成功/失败各登录一次 | sys_login_log 各增一条 success/failed，含 ip/device/os/browser | REQ-005 | 无 |

### iam.AuthService（验证码通道）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 60秒内重复请求_验证码被拒 | 同邮箱同用途已请求过 | 再次 sendCode 同参数 | 「验证码发送过于频繁」 | REQ-007 | 嵌入式 Redis |
| 不同用途_频控键独立 | register 用途已频控 | sendCode(reset) | 发送成功 | REQ-007 | 嵌入式 Redis |
| 验证码一次性_二次注册失败 | register 验证码已消费一次 | 以同 code 再次注册（不同用户名） | 「验证码无效或已过期」 | REQ-007/008 | mock EmailService |
| 注册码不能用于重置 | register 验证码未消费 | 以其调用 resetPassword | 「验证码无效或已过期」 | REQ-007 | mock EmailService |
| 注册成功_默认角色与配额 | kb_user 角色存在 | 有效验证码注册 | 用户 enabled、storageQuota=10737418240、绑 kb_user；用户名/邮箱重复分别被拒 | REQ-008 | mock EmailService、嵌入式 Redis |
| 重置未注册邮箱_拒绝 | 邮箱从未注册 | resetPassword | 「该邮箱未注册」且验证码已消费 | REQ-009 | mock EmailService |
| 重置成功_新密码可登录 | 注册用户 | 重置后以新密码登录 | 登录成功 | REQ-009 | mock EmailService、嵌入式 Redis |

### iam.AuthService（微信通道）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 轮询三态__waiting_scanned_expired | scene S 已生成（Redis 空值） | 确认前/确认后/过期后各 poll 一次 | null→waiting；token→scanned；无键→expired | REQ-010/011/013 | 嵌入式 Redis、时间 |
| scene过期_确认_拒绝 | scene 键已消除 | confirmQrLogin | 「二维码已过期，请刷新」 | REQ-013 | 嵌入式 Redis |
| 新openid_自动注册 | openid O 未绑定 | wechatLogin(有效code) | 建 wx_ 前缀用户（冲突加后缀）、默认角色、enabled、10GB；再登不重建 | REQ-012 | mock 微信 HTTP |
| 已绑定禁用用户_拒绝 | openid 绑定 disabled 用户 | wechatLogin | 「账号已被禁用」 | REQ-003/012 | mock 微信 HTTP |
| 微信接口失败_二维码降级 | getwxacodeunlimit 失败 | generateQrScene | scene+expiresIn 正常返回，qrImageBase64=null | REQ-010 | mock 微信 HTTP |

### iam.ApiTokenService（contract: iam.ApiTokenService）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 创建_明文仅此一次 | 授权用户 | createToken 后 listTokens | 创建响应含 frag_ 明文；列表该行 token=null | REQ-014/015 | 无 |
| 缺省permission_read与永不过期 | 请求不带 permission/expiresIn | createToken | permission=read、expiresAt=null | REQ-014 | 无 |
| 撤销_即时失效 | Token T 有效 | revokeToken 后 findValidToken(T) | 返回 null | REQ-015 | 无 |
| 撤销不存在_404 | — | revokeToken(随机id) | 「Token 不存在」 | REQ-015 | 无 |
| 过期token_验证失败 | expiresIn 已过 | findValidToken | null（认证不建立） | REQ-016 | 时间 |
| 创建撤销_审计留痕 | 授权用户 | create/revoke 各一次 | 两条 operation 审计（@Loggable 生效） | REQ-017/033 | 无 |

### iam.KbAclService（contract: iam.KbAclService）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| addAclEntry_库与缓存同写 | 知识库 K | addAclEntry(u, editor) | 表新增记录且 redis 键 kb:acl:K:u=editor（TTL 24h） | REQ-029 | 嵌入式 Redis、时间 |
| removeAclEntry_缓存同步删除 | u 在 K 有授权且已缓存 | removeAclEntry | 表与缓存键均消失 | REQ-029/GWT-28 | 嵌入式 Redis |
| setKbAcl_先清全量旧缓存再覆盖 | K 有 u1/u2 旧授权缓存 | setKbAcl(仅 u3) | u1/u2 缓存键被删，表只剩 u3 | REQ-029 | 嵌入式 Redis |
| getKbRole_读穿回填 | 表有记录、缓存空 | getKbRole | 返回角色且缓存回填 | REQ-029 | 嵌入式 Redis |
| getKbAcl_回填 userName | K 有授权且用户存在 | getKbAcl | dto.userName=realName | REQ-026 | 无 |
| 自助查询_越权_403 | 无通配权限用户 u | 查询他人 accessibleKbIds | 「无权查看该用户的权限」 | REQ-030 | 无 |

### iam.KbAccessChecker / @KbAuth（contract: iam.KbAccessChecker — 切片 0 后定稿）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| api-token身份_切面全局放行 | api-token: 前缀身份 | 经 @KbAuth(owner) 方法 | 直接放行 | REQ-016/027 | 无 |
| 无任何角色_拒绝 | resolveRole=null | 经 @KbAuth(viewer) 方法 | 403「无知识库访问权限」 | REQ-027 | 无 |
| 层级不足_拒绝 | 有效角色 viewer，注解 editor | 调用该方法 | 403「知识库权限不足」 | REQ-027/GWT-26 | 无 |
| 层级满足_放行 | 有效角色 owner，注解 editor | 调用该方法 | 放行 | REQ-027 | 无 |
| 缺kbId_URI不可解析_400 | 非 /api/kb/ 路径误用注解 | 调用该方法 | 400「缺少知识库ID」 | （实现防御） | 无 |
| 组织兜底语义_按仲裁定稿 | 同组织/跨组织/private 库 × 管理员/普通成员矩阵 | resolveRole | **断言待切片 0 仲裁**（复-1：现状=private无兜底/管理员owner/成员editor/max合并） | REQ-028 | 无 |

### iam.RbacDataService（contract: iam.RbacDataService）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| 删除系统角色_被拒 | isSystem=1 角色 | deleteRole | 「系统角色不可删除」 | REQ-022/GWT-21 | 无 |
| 设默认_全局唯一翻转 | R1 为默认 | setDefault(R2) | R2.is_default=1 且 R1=0 | REQ-022/GWT-22 | 无 |
| 创建角色_roleKey系统生成 | 任意合法请求 | createRole | roleKey=custom_ 前缀，入参不可指定 | REQ-022 | 无 |
| 删除角色_关联不级联（债务行为断言） | 角色 R 有权限关联 | deleteRole(R) | sys_role_permission 残留（按现状代码断言；修复则改断言） | REQ-022 | 无 |
| permKey重复_被拒 | 已存在同名键 | createPermission | 「权限标识已存在」 | REQ-023 | 无 |
| 删除权限_级联清关联 | 权限 P 被两角色引用 | deletePermission(P) | sys_role_permission 中 P 全部消失 | REQ-023 | 无 |
| 权限树_两段式构建 | 多 group 多 parentKey 权限 | getPermissionTree | group 节点在顶层、组内按 parentKey 成层级 | REQ-023 | 无 |
| 人员分页_关键字倒序 | 25 人中 3 人含「张」 | listPersonnel(keyword=张, page=1, pageSize=10) | PageResult{total=3}，createdAt 倒序 | REQ-024/GWT-23 | 无 |
| 创建人员_缺省密码可登录 | 不传密码 | createPersonnel 后以 123456 登录 | 登录成功 | REQ-024 | 嵌入式 Redis |
| assignRoles_全量替换 | 用户原挂 R1 | assignRoles([R2]) | 仅剩 R2 | REQ-024 | 无 |
| 禁用人员_登录被拒 | p 被 updateStatus(disabled) | p 登录 | 「账号已被禁用」 | REQ-024/GWT-24 | 嵌入式 Redis |
| 删除有子组织的节点_被拒 | 组织 O 有子节点 | deleteOrg(O) | 「存在子组织」 | REQ-025 | 无 |
| 组织树_实时人数 | O 下 2 名用户 | getOrgTree | memberCount=2 | REQ-025 | 无 |

### DataScope（纯函数切片，Tracer Bullet）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| api-token身份_全局可见可管 | userId=api-token:x | visible/manageable | 均为 true | REQ-031 | 无 |
| 系统级数据_全员可见 | creator=system 或 isBuiltin=1 | visible(任意用户) | true | REQ-031 | 无 |
| 属主可见可管_同组织仅可见 | creator=c, orgId=g, 用户=c/g 成员 | visible/manageable 分别判定 | visible=true；manageable 仅属主与持 * 者 | REQ-031 | 无 |

## 3. 追溯（抽样完整链）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| 黑名单令牌_已登出_不建立上下文 | iam.SecurityGate | SecurityFilterModule | REQ-004 |
| 注册成功_默认角色与配额 | iam.AuthService | AuthModule | REQ-008 |
| 创建_明文仅此一次 | iam.ApiTokenService | ApiTokenModule | REQ-014/015 |
| setKbAcl_先清全量旧缓存再覆盖 | iam.KbAclService | KbAclModule | REQ-029 |
| 层级不足_拒绝 | iam.KbAccessChecker | KbAccessDecisionModule | REQ-027 |
| 设默认_全局唯一翻转 | iam.RbacDataService | RbacDataModule | REQ-022 |
| 系统级数据_全员可见 | DataScope | DataScope | REQ-031 |

## 4. 待实施说明

- **红绿循环未执行**——本文件仅为设计；执行时按切片 9 → 1 → 2… 顺序从 Tracer Bullet 起步，每片先红后绿。
- **切片 0（HITL）阻塞切片 7**：复-1 的同组织兜底语义仲裁必须先于 KB 判定断言定稿；仲裁若维持现状实现，spec REQ-028/GWT-27 须同步修订。
- 两处「债务行为断言」（删除角色不级联、permission 字段不参与鉴权）按**现状代码**写断言并加注释标记；若用户确认修复，断言随 REQ 修订翻转。
- 未认证请求的 HTTP 状态码断言（REQ-021）已于 2026-09-24 裁定为 HTTP 403（Spring 默认 EntryPoint，深证见 spec REQ-021），切片 1 可直接断言 403。
- **切片 0 裁决注记（2026-09-24）**：复-1 已代裁为「按现状代码为准」（分级兜底：private 无兜底/管理员 owner/普通成员 editor，max 合并，KbAccessCheckerImpl.java:16-21 类注释即设计意图）——切片 7 断言按该口径定稿，切片 0 的 HITL 仲裁解除。
- iam 域不 mock：任何 Service/Mapper/切面（内部模块）；Redis 用嵌入式或按 spec §7 抽象缓存门面。
