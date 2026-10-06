# IAM 认证与账号权限 — 需求规格（spec）

> 本文件由上位仓 docs/spec/iam.md 于 2026-09-21 搬入 fastrag-backend 文档树，作为后端 iam 链的事实源；原「14 条 INFERRED / 8 条 GAP」已于 2026-09-24 由 IDD 裁决代理逐条代裁闭环（R1 深证 / R2 现状裁定 / R3 实证 / R4 代裁，标注见正文与搬入复核节，用户可推翻）。

> 由 /idd-spec 于 2026-09-16 生成（onboard 模式：grilling 问题全部由代码证据回答）。
> 证据标注约定：`〔证据：文件:行 ‹CERTAIN›〕` = 代码直接可观测；`（INFERRED）` = 结构强推断、待用户确认；`[GAP: …]` = 代码外信息、不臆造。
> 配套前端规格：[iam-frontend.md](./iam-frontend.md)。端点路径与字段名仅在证据引用中出现，正式接口契约以 /idd-contract 产出为准。

## 1. 特性名称与范围

本特性描述 FastRAG 的身份认证与账号权限体系（IAM），它是全部 17 项其他特性的依赖根。范围覆盖：三种登录方式（用户名密码、微信扫码、API Token）、邮箱验证码驱动的注册与密码重置、会话管理（JWT 签发/校验/登出吊销）、RBAC 管理域（角色、权限项、人员、组织）、知识库级 ACL（owner > editor > viewer）、以及贯穿全系统的请求鉴权基建（安全过滤器链、方法级权限、统一异常映射）。后端实现位于 `fastrag-modules/fastrag-iam`（7 个控制器）与 `fastrag-security`（过滤器/切面/工具）。明确排除：登录日志的展示与分析界面（归运营域）、OnlyOffice 回调 JWT（归在线编辑特性）、知识库业务端点本身、微信小程序端实现（仓库内无小程序代码）、管理后台页面布局（归前端规格 iam-frontend.md）。

## 2. Out of Scope

- **登录日志消费面**：`SysLoginLogService` 由 iam 写入（跨模块调用 operation），但日志查询/展示/分析接口与页面归 operation-analytics 特性。
- **OnlyOffice 回调鉴权**：`OnlyOfficeJwtUtil` 与 OO 回调白名单虽位于 security 模块，其需求归 onlyoffice-editing 特性（本规格只认领"该白名单存在"这一暴露面事实）。
- **知识库 ACL 的实现侧数据源**：`KbAccessChecker` 的"同组织兜底 viewer"具体实现位于 fastrag-knowledge 模块（接口声明在 security）〔证据：fastrag-security/…/KbAccessChecker.java:27-29 ‹CERTAIN›〕，其实现细节归 kb-core 特性；本规格只约束接口契约与判定优先级。
- **开放平台"API 密钥"管理页的数据（字典驱动）**：归 platform-config 特性。本规格只覆盖 `sys_api_token` 表驱动的 API Token。
- **微信小程序端**：扫码确认由小程序调用，仓库内无小程序代码，其交互需求不在本仓库范围。
- **前端页面与交互**：全部归 docs/spec/iam-frontend.md。

## 3. 领域术语

引用 [CONTEXT.md](../../CONTEXT.md) 中已定义的术语：

- **知识库权限（KB ACL）** — 基于 Redis 缓存的知识库级 RBAC，角色分为 owner > editor > viewer。
- **知识库（Knowledge Base）** — ACL 的授权对象。

本规格新引入、建议回写 CONTEXT.md 的术语（IDD 链主控决定是否运行 /idd-grill）：

- **API Token** — 平台级程序化访问凭证，格式 `frag_<uuid>`，认证成功后获得固定的平台级身份与 `kb:manage` 权限。
- **登录安全日志** — 登录/登出/注册行为的留痕记录，含客户端 IP 与 UA 解析结果（设备/操作系统/浏览器）。
- **权限项（Permission Item）** — sys_permission 表中的一条权限记录，按 category 分为菜单（menu:*）/ 页面操作 / API 接口（api:*）三类。

## 4. 功能需求

### 4.1 认证与会话

系统以无状态 JWT 为会话载体：登录成功后签发携带用户身份、组织、角色与权限清单的 Token，后续请求凭 Bearer 头自证身份；登出通过 Redis 黑名单即时吊销。角色与权限在登录时一次性折叠进 Token，请求期不做二次查库——这是"权限变更需重新登录才生效"这一可观测行为的根源。

- **REQ-001** 用户提交用户名与密码登录，凭据有效时获得访问令牌与用户信息（含 id、用户名、姓名、手机、邮箱、组织、角色列表、权限列表）。〔证据：fastrag-iam/…/AuthServiceImpl.java:37-52 ‹CERTAIN›〕
- **REQ-002** 登录失败时对"用户不存在"与"密码错误"返回完全相同的错误文案（防账号枚举）。〔证据：AuthServiceImpl.java:39,41 ‹CERTAIN›〕
- **REQ-003** 状态为 disabled 的账号在任何登录方式下均被拒绝，错误信息明确指示账号被禁用。〔证据：AuthServiceImpl.java:40（密码登录）、212-214（微信登录）‹CERTAIN›〕
- **REQ-004** 用户登出后，其令牌立即失效：登出将令牌写入 Redis 黑名单（TTL 24 小时），后续携带该令牌的请求不再建立认证上下文。〔证据：AuthServiceImpl.java:65-67、fastrag-security/…/JwtAuthFilter.java:66-70 ‹CERTAIN›〕
- **REQ-005** 每次登录（成功/失败）、登出、注册、微信登录均记录登录安全日志，含用户、客户端 IP、设备/操作系统/浏览器（UA 解析）与结果。〔证据：fastrag-iam/…/AuthController.java:61-83,98-105,116-123,153-171 ‹CERTAIN›；日志展示归运营域〕
- **REQ-006** 已登录用户可实时获取自身信息与当前有效的角色/权限清单（从令牌主体解析用户，权限从库重算）。〔证据：AuthController.java:85-88、AuthServiceImpl.java:54-63 ‹CERTAIN›〕

**GWT-01 登录成功**
- Given 系统中存在状态为 enabled、密码为 BCrypt 哈希的用户 u
- When u 提交正确的用户名与密码
- Then 返回令牌与含角色/权限清单的用户信息，并记录一条 success 登录日志

**GWT-02 密码错误防枚举**
- Given 用户 u 存在
- When 以错误密码登录
- Then 返回与"用户不存在"完全相同的文案（"用户名或密码错误"），并记录一条 failed 登录日志

**GWT-03 禁用账号**
- Given 用户 u 的状态为 disabled
- When u 以正确凭据登录（密码或微信任一通道）
- Then 拒绝并返回"账号已被禁用"

**GWT-04 登出吊销**
- Given 用户 u 已登录并持有令牌 T
- When u 登出后，任何请求携带令牌 T
- Then 该请求不建立认证上下文（JwtAuthFilter 检测到黑名单键后跳过认证），受保护端点按未认证处理

### 4.2 邮箱验证码账号流程

自助账号流程以邮箱验证码为身份证明：注册与重置密码都要求先获取一次性验证码。验证码按用途隔离（注册码不能用于重置），有频控、有效期与一次性消费三重防线。

- **REQ-007** 用户可请求向指定邮箱发送 6 位数字验证码，用于注册（register）或重置密码（reset）两种用途；同一邮箱同一用途 60 秒内仅可发送一次（Redis 频控），验证码有效期 5 分钟，按用途通过对应邮件模板发送。〔证据：AuthServiceImpl.java:69-96 ‹CERTAIN›〕
- **REQ-008** 用户凭邮箱验证码注册：验证码校验通过即被消费（一次性）；用户名与邮箱均不得与现有用户重复；注册成功后自动获得默认角色（roleKey=kb_user）、enabled 状态与 10GB 初始存储配额。〔证据：AuthServiceImpl.java:98-130 ‹CERTAIN›；默认角色取 kb_user 为硬编码，与"设为默认角色"功能的联动缺陷见 §4.9 观测说明〕
- **REQ-009** 用户凭邮箱验证码重置密码：验证码校验通过并消费后更新密码哈希；邮箱未注册时拒绝。〔证据：AuthServiceImpl.java:132-143,145-158 ‹CERTAIN›〕

**GWT-05 发送频控**
- Given 邮箱 a@b.c 在 60 秒内已成功请求过 register 验证码
- When 再次请求 register 验证码
- Then 拒绝并提示 60 秒后重试（reset 用途的频控键独立，不受影响）

**GWT-06 验证码一次性**
- Given 邮箱 a@b.c 收到 5 分钟内有效的 register 验证码 123456
- When 先后两次以 123456 提交注册（第二次为不同用户名）
- Then 第一次成功，第二次返回"验证码无效或已过期"

**GWT-07 注册默认角色**
- Given 系统存在 roleKey=kb_user 的角色
- When 新用户以有效验证码完成注册
- Then 该用户被赋予 kb_user 角色、enabled 状态与 10GB 存储配额

**GWT-08 重置未注册邮箱**
- When 以从未注册的邮箱 + 有效 reset 验证码请求重置密码
- Then 拒绝并返回"该邮箱未注册"，密码不变

### 4.3 微信扫码登录

PC 端展示小程序码，微信小程序扫码确认后，PC 端轮询取得令牌完成登录。场景值与扫码状态全部由 Redis 承载，二维码 5 分钟有效。

- **REQ-010** PC 端可生成扫码登录场景：获得 16 位随机 scene、5 分钟有效期，以及（微信接口可用时的）小程序码 base64 图片；微信接口失败时降级为无图返回，由前端展示回退态。〔证据：AuthServiceImpl.java:258-272 ‹CERTAIN›；降级分支 263-268 ‹CERTAIN›〕
- **REQ-011** PC 端轮询扫码状态，返回三态之一：waiting（未扫码）、scanned（已确认，附带令牌）、expired（场景不存在或已过期）。〔证据：AuthController.java:140-150、AuthServiceImpl.java:274-288 ‹CERTAIN›〕
- **REQ-012** 小程序凭微信 code 登录：code 换取 openid；已绑定用户直接登录（disabled 拒绝），未绑定用户自动注册（用户名 wx_ 前缀 + 冲突时追加随机后缀、默认角色 kb_user、enabled、10GB 配额）。〔证据：AuthServiceImpl.java:199-256 ‹CERTAIN›〕
- **REQ-013** 小程序扫码确认：小程序以 scene + code 调用确认端点，系统完成登录并将令牌回写至该 scene 的 Redis 键（60 秒 TTL）供 PC 轮询取走；scene 已过期时确认失败。〔证据：AuthController.java:174-193、AuthServiceImpl.java:290-299 ‹CERTAIN›〕

**GWT-09/10/11 轮询三态**
- Given PC 端持有有效 scene S（未过期）
- When 小程序确认前轮询 → waiting；小程序确认后轮询 → scanned + 令牌；S 过期后轮询 → expired

**GWT-12 微信新用户自动注册**
- Given openid O 从未绑定任何用户
- When 小程序以有效 code（换取 O）登录
- Then 自动创建 wx_ 前缀用户名的账号（默认角色、enabled）并返回令牌；再次以 O 登录时不再新建账号

### 4.4 API Token（机器认证）

为第三方集成与系统间调用提供非人工认证通道。Token 一次性明文展示，服务端仅存值用于校验；认证成功后获得统一的平台级身份。

- **REQ-014** 授权用户可创建 API Token：系统生成 `frag_<uuid>` 格式令牌值，记录名称、权限标记（缺省 read）、创建者；可选过期时长（不传则永不过期）；明文令牌值仅在创建响应中返回一次。〔证据：fastrag-iam/…/ApiTokenServiceImpl.java:48-69 ‹CERTAIN›〕
- **REQ-015** Token 列表返回时令牌值一律脱敏（置空）不外泄；撤销为物理删除，撤销不存在的 Token 返回资源不存在。〔证据：fastrag-iam/…/ApiTokenController.java:50-58、ApiTokenServiceImpl.java:71-80 ‹CERTAIN›〕
- **REQ-016** 携带 `frag_` 前缀令牌的请求由 API Token 过滤器认证：令牌须存在、未撤销、未过期；认证成功后建立平台级身份（userId=api-token:{tokenId}、角色 api_token、权限 kb:manage），该身份在知识库 ACL 切面中全局放行。〔证据：fastrag-security/…/ApiTokenAuthFilter.java:49,69-97 ‹CERTAIN›、KbAuthAspect.java:65-66 ‹CERTAIN›〕
- **REQ-017** API Token 的创建与撤销端点要求 kb:manage 权限，且创建/撤销写审计日志。〔证据：ApiTokenController.java:52,61-77 ‹CERTAIN›〕

**GWT-13/14 创建与脱敏**
- When 授权用户创建名为 t1 的 Token
- Then 响应含明文令牌值（frag_ 前缀）；随后查询列表时 t1 的令牌值为空

**GWT-15 失效 Token 拒绝**
- Given Token T 已撤销（或已过过期时间）
- When 请求携带 Bearer T
- Then 认证不建立（验证器返回 null / 查不到有效记录），受保护端点按未认证处理

**GWT-16 平台级放行**
- Given 有效 Token T
- When 携带 T 访问任意 /api/** 端点与标注 @KbAuth 的知识库端点
- Then 建立权限为 kb:manage 的平台级身份，且知识库 ACL 切面对其全局放行

### 4.5 请求鉴权基建

安全过滤器链是所有端点的总闸：先 API Token 过滤器、后 JWT 过滤器，均置于用户名密码过滤器之前。暴露面收敛为一份白名单；方法级权限由注解驱动。

- **REQ-018** 端点暴露面：认证类端点（密码登录、验证码、注册、重置、微信四端点）、OnlyOffice 回调/拉取、Swagger/Actuator 免认证；/api/** 其余端点一律要求认证；/api 之外路径放行；全程无状态会话、禁用 CSRF；SSE 异步分发不重复鉴权。〔证据：fastrag-security/…/SecurityConfig.java:60-83 ‹CERTAIN›、JwtAuthFilter.java:53-58 ‹CERTAIN›〕
- **REQ-019** JWT 契约：令牌载荷含 userId（主体）、username、orgId、roles、permissions；HMAC 签名；有效期可配置（默认 24 小时）；校验含签名与过期检查。〔证据：fastrag-security/…/JwtUtil.java:42-67,81-88 ‹CERTAIN›；黑名单叠加见 REQ-004〕
- **REQ-020** 方法级权限：控制器/服务方法可用 `@perm.has('键')` / `@perm.hasAny(...)` 声明所需权限；权限判定支持 `*` 通配（持有通配权限或 super_admin 折叠权限的用户全通）。〔证据：fastrag-iam/…/PermissionChecker.java:21-24 ‹CERTAIN›、fastrag-security/…/LoginUser.java:48-59 ‹CERTAIN›、AuthServiceImpl.java:178-179（super_admin 折叠为 ["*"]）‹CERTAIN›〕
- **REQ-021** 异常到响应的统一映射：业务异常 → HTTP 200 + body.code（400/401/403/404/500 语义）；参数校验失败 → HTTP 200 + body.code=400 且消息含字段级错误；方法级鉴权拒绝 → HTTP 403 + "无权限执行该操作"；兜底异常 → HTTP 200 + body.code=500"服务异常"。〔证据：fastrag-common/…/GlobalExceptionHandler.java:12-45 ‹CERTAIN›〕
  - ‹CERTAIN（2026-09-24 深证 R1）›无凭据请求受保护 /api/** 端点的 HTTP 状态码 = **HTTP 403**：SecurityConfig 全文（SecurityConfig.java:59-83）无 exceptionHandling/AuthenticationEntryPoint/formLogin/httpBasic 任何注册，全仓 grep 无 EntryPoint 定制〔grep 实测 0 处〕；Spring Boot 3.2.5（pom.xml:16-18）→ Spring Security 6.2 在无任何 EntryPoint 注册时的确定性默认即 Http403ForbiddenEntryPoint。前端请求层对 HTTP 401/403 与 body.code 401/403 均有处理分支（fastrag-frontend/src/utils/request.ts:29-83），403 分支命中。

**GWT-17 白名单免认证**
- When 未携带任何令牌访问注册或验证码端点
- Then 请求到达业务逻辑并正常处理（不被认证闸拦截）

**GWT-18 无凭据访问受保护端点**
- When 未携带令牌访问人员管理等 /api/** 端点
- Then 请求被过滤器链拒绝，不进入业务逻辑（HTTP 403，REQ-021 已于 2026-09-24 深证）

**GWT-19 方法级拒绝**
- Given 用户 u 的权限清单不含 admin:role
- When u 调用角色列表接口
- Then HTTP 403，消息"无权限执行该操作"

**GWT-20 通配通过**
- Given 用户 u 的权限清单为 ["*"]（super_admin 登录折叠）
- When u 调用任意 @perm.has 保护的方法
- Then 放行

### 4.6 角色与权限管理

角色是权限的容器：角色-权限为多对多键值关联；角色分为系统内置（不可删）与自定义。权限项是平台全部可控权限的登记簿，按分组构成树，供角色配置页消费。

- **REQ-022** 授权管理员可对角色执行：列表、详情、创建、更新、删除、设为默认。约束：系统内置角色（isSystem=1）不可删除；自定义角色的 roleKey 由系统生成（custom_ 前缀 + 时间戳），不可自定义；"设为默认"全局唯一——新默认产生时旧默认自动取消；删除角色时其在中间表的角色-权限关联不级联清理（MyBatis-Plus 仅删主表）。〔证据：fastrag-iam/…/RoleController.java:42-51 ‹CERTAIN›、RoleServiceImpl.java:13-26 ‹CERTAIN›；关联不清理为 22 行 deleteRole 直接 deleteById 的直接观测 ‹CERTAIN›〕
- **REQ-023** 授权管理员可对权限项执行：列表（含反查关联角色）、分组树、创建、更新、删除。约束：权限标识全局唯一；树按 group 字段分组、组内按 parentKey 构成层级；删除权限项级联清除该权限在所有角色中的关联。〔证据：fastrag-iam/…/PermissionController.java:43-67 ‹CERTAIN›、PermissionServiceImpl.java:50-85 ‹CERTAIN›〕
  - 〔决议 2026-09-24：按现状为准——注册与微信注册恒按硬编码 roleKey=kb_user 取默认角色（AuthServiceImpl.java:113-114,224-226），is_default 仅由 setDefault 写入、仅作展示回显（RoleServiceImpl.java:24-25,29，全仓 grep 无注册路径读 is_default），"设为默认"不改变注册结果；是否改读 is_default 属产品决策，维持现状不修（代裁，用户可推翻）。脱节事实已登记债务：contract/interfaces.md iam 节 Post 条 + implement/iam.md。〕

**GWT-21 系统角色保护**
- Given 角色超级管理员（isSystem=1）
- When 授权管理员请求删除它
- Then 拒绝并返回"系统角色不可删除"

**GWT-22 默认角色唯一**
- Given 当前默认角色为 R1
- When 授权管理员将 R2 设为默认
- Then R2.is_default=1 且 R1.is_default=0（全局仅一个默认）

### 4.7 人员与组织管理

人员管理面向管理员的账号生命周期（创建、资料维护、角色分配、启禁用）；组织管理维护部门树，支撑 KB 组织隔离的数据基础。两类读接口为降低使用门槛开放给全部登录用户。

- **REQ-024** 授权管理员可对人员执行：分页列表（keyword 模糊匹配用户名或姓名、创建时间倒序、返回 PageResult）、创建（用户名唯一；初始密码可省略，缺省 123456；可同时挂多角色）、更新（姓名/手机/邮箱/组织/密码，用户名不可改）、角色分配（全量替换：先删旧关联再插新关联）、启禁用。低门槛读取口：按用户名查询与精简人员选项列表（仅启用人员、不含手机/邮箱敏感字段）仅需登录。〔证据：fastrag-iam/…/PersonnelController.java:49-100 ‹CERTAIN›、PersonnelServiceImpl.java:15-89 ‹CERTAIN›〕
- **REQ-025** 授权管理员可对组织执行：创建（可指定上级，缺省为顶级）、更名/别名更新、删除（存在子组织时拒绝）。全部登录用户可读：组织树（含各部门实时人数统计）、扁平列表、部门名列表、部门成员。〔证据：fastrag-iam/…/OrgController.java:44-57 ‹CERTAIN›、OrgServiceImpl.java:11-43 ‹CERTAIN›〕

**GWT-23 人员关键字分页**
- Given 系统有 25 名人员，其中 3 人姓名含"张"
- When 管理员以 keyword=张 请求第 1 页（每页 10 条）
- Then 返回 total=3 的分页结果，按创建时间倒序

**GWT-24 禁用联动登录**
- Given 人员 p 状态为 enabled
- When 管理员将 p 置为 disabled 后，p 尝试登录
- Then 拒绝并返回"账号已被禁用"（与 REQ-003 联动）

### 4.8 知识库 ACL 与数据范围

知识库级权限独立于全局 RBAC：按"知识库 × 用户"逐条授权（kb_acl 表），角色三档且严格分层。ACL 管理入口仅知识库所有者可用；判定优先级为 显式 ACL > 同组织兜底 viewer > 无权限（〔勘误标注 2026-09-24（复-1 裁决）〕：现状实现为分级兜底——private 库无兜底、非 private 库管理员兜底 owner、普通同组织成员兜底 editor，有效角色 = max(显式 ACL, 组织兜底)，见 KbAccessCheckerImpl.java:16-21,29-54 ‹CERTAIN›；本段 viewer 口径为过时断言，正文未改）。非知识库类数据（模板/应用配置等）另有统一的属主/组织可见性判定工具。

- **REQ-026** 知识库 ACL 的查询、批量设置、单条添加、单条移除四类端点均要求当前用户对该知识库具备 owner 角色（@KbAuth(owner) 切面强制）。〔证据：fastrag-iam/…/KbAclController.java:46-56 ‹CERTAIN›、KbAuthAspect.java:62-79 ‹CERTAIN›〕
- **REQ-027** 知识库角色层级为 owner(3) > editor(2) > viewer(1)：@KbAuth 声明的最低角色与用户实际角色按层级比较，不足即拒绝（403）；用户在该库无任何角色则直接拒绝。平台级 API Token 身份全局放行。〔证据：KbAuthAspect.java:55-56,62-79 ‹CERTAIN›〕
- **REQ-028** ACL 判定的数据源优先级：显式 ACL 记录 > 同组织成员兜底 viewer > null（无权限）；接口契约由 security 模块声明，实现在 knowledge 模块。〔证据：fastrag-security/…/KbAccessChecker.java:30-43 ‹CERTAIN›（接口契约）〕〔勘误标注 2026-09-24（复-1 裁决）：实现已为「max(显式 ACL, 组织兜底)」分级兜底（KbAccessCheckerImpl.java:29-54 ‹CERTAIN›，类注释即设计意图），本条 viewer 口径过时；按现状代码为准，正文语义未改。〕
- **REQ-029** ACL 读写与 Redis 缓存保持一致：授权写入/变更时同步写缓存（TTL 24h）；移除时删缓存；批量覆盖设置时先清旧记录的全部缓存再落库，避免被移除用户在 TTL 内仍可访问；查询走读穿（缓存未命中回源并回填）。〔证据：fastrag-iam/…/KbAclServiceImpl.java:18-34 ‹CERTAIN›〕
- **REQ-030** 用户自助查询口：查询"用户可访问的知识库列表"与"用户在某库的角色"仅允许本人或持通配权限者（超管）调用，越权返回 403。〔证据：KbAclController.java:57-67 ‹CERTAIN›〕
- **REQ-031** 非知识库数据的范围判定规则统一——可见性：API Token 全局可见、系统级数据（creator=system 或内置标记）全员可见、属主可见、同组织可见；管理权：API Token 全局可管理、通配权限可管理、属主可管理。（判定经 security 模块统一工具执行，机制细节归设计层。）〔证据：fastrag-security/…/DataScope.java:42-64 ‹CERTAIN›〕

**GWT-25 非 owner 不可管理 ACL**
- Given 用户 u 对知识库 K 仅有 editor 角色
- When u 请求读取/修改 K 的 ACL
- Then 拒绝（层级判定 owner 门禁不满足）

**GWT-26 层级判定**
- Given 用户 u 对 K 的角色为 viewer，端点要求 editor
- When u 调用该端点
- Then 拒绝并返回"知识库权限不足"

**GWT-27 同组织兜底**
- Given 用户 u 与知识库 K 同组织且 u 在 K 无显式 ACL 记录
- When u 以 viewer 级别访问 K
- Then 放行（resolveRole 返回 viewer）；以 editor 级别访问则拒绝
- 〔勘误标注 2026-09-24（复-1 裁决）：按现状代码本 Then 为假——非 private 库的普通同组织成员 resolveRole 兜底 editor，viewer/editor 级均放行（KbAccessCheckerImpl.java:37-47 ‹CERTAIN›）；断言主体未改，测试切片应按分级兜底口径编写。〕

**GWT-28 缓存一致性**
- Given 用户 u 在 K 的角色 viewer 已缓存
- When owner 将 u 从 K 的 ACL 中移除
- Then u 的缓存键同步删除，下一次判定不再命中旧角色

**GWT-29 自助查询越权**
- Given 用户 u 无通配权限
- When u 查询另一用户 v 的可访问知识库列表
- Then 拒绝并返回 403（"无权查看该用户的权限"）

### 4.9 通用约定与观测说明

- **REQ-032** iam 全部端点遵循仓库统一契约：响应体为统一三段结构（code/data/message），分页结果四字段（list/total/page/pageSize），业务错误以 HTTP 200 + body.code 表达。〔证据：fastrag-common/…/ApiResponse.java:19-39 ‹CERTAIN›、PageResult.java:17-28 ‹CERTAIN›、PersonnelController.java:50-55（分页样例）‹CERTAIN›〕
- **REQ-033** iam 全部管理类写操作（Token 创建/撤销、ACL 变更、组织增删改、人员增改/角色分配/状态变更、角色增删改/设默认）通过统一审计注解留痕。〔证据：各控制器 @Loggable 注解，ApiTokenController.java:61,71、KbAclController.java:49,52,55、OrgController.java:50-56、PersonnelController.java:56-72、RoleController.java:44-51 ‹CERTAIN›〕

**现状观测（非需求，供下游阶段与用户确认）：**

- **观测-1（CERTAIN）** 管理类查询/写入的鉴权粒度为少数粗键（admin:role、admin:user、admin:org、kb:manage），前端 menu-perm-tree 登记的 300+ 细粒度 api:* 键后端并不逐 API 校验〔证据：各控制器 @PreAuthorize、fastrag-frontend/src/config/menu-perm-tree.ts 全文件 451 行（45-161 ACTION_API_LINKS、163-451 MENU_PERM_TREE，2026-09-24 按复-2 修订引用口径）‹CERTAIN›〕。〔决议 2026-09-24：按现状为准——细粒度 api:* 键为前端菜单显隐与权限页登记面，后端仅粗键校验，不做逐 API 对齐；是否补齐属产品决策，维持现状（代裁，用户可推翻）。债务由本条与 contract 登记承载。〕
- **观测-2（CERTAIN）** API Token 的 permission 字段（read/write）仅存储不参与鉴权——所有 Token 认证后均获得同一组权限 kb:manage〔证据：ApiTokenServiceImpl.java:53、ApiTokenAuthFilter.java:77-87 ‹CERTAIN›〕。〔决议 2026-09-24：按现状为准——permission 仅创建存储与列表回显（全仓 grep 仅 ApiTokenServiceImpl.java:53,98 两处触达），不参与鉴权；按 read/write 收敛权限属产品决策，维持现状（代裁，用户可推翻）。债务已登记：contract/interfaces.md iam 节"已知脱节"。〕
- **观测-3（CERTAIN）** 前端对"默认角色权限不可改"有保护，后端 updateRole 无对应保护（任意 admin:role 持有者可直接改默认角色权限）〔证据：fastrag-frontend/src/views/admin/account/roles.vue:53-57,133、RoleServiceImpl.java:17-21 ‹CERTAIN›〕。〔决议 2026-09-24：按现状为准——后端不补默认角色防护，前端保护为唯一防线；是否补齐属产品决策，维持现状（代裁，用户可推翻）。债务已登记：contract/interfaces.md iam 节"已知脱节" + implement/iam.md。〕

## 5. 非功能约束

以下仅收录有代码证据或已成文约定的项；代码外信息一律 GAP 占位。

- **密码存储**：BCrypt 编码，禁止明文/可逆存储。〔证据：SecurityConfig.java:85-88 ‹CERTAIN›〕
- **JWT 密钥**：HMAC 密钥来自配置项，支持环境变量覆盖；代码内默认值仅限开发环境，生产必须更换〔证据：JwtUtil.java:42-43 及其注释 ‹CERTAIN›〕。〔决议 2026-09-24（R4 代裁）：保管取**环境变量注入、不入库不入 git**（现状配置链即如此：application.yml:71-73 `jwt.secret: ${JWT_SECRET:}`，docker-compose.yml:22 注入开发值；未设置时空串 → HMAC 抛异常 fail-fast，复-4 核实）；轮换取**直接更换 JWT_SECRET**——单密钥无密钥 ID，换钥即令全部存量令牌验签失效（等价全员强制重登），不设双密钥并存窗口；现状差距：无（流程性约定，无代码债务）。〕
- **微信凭据**：AppID/AppSecret 经环境变量注入后端配置〔证据：fastrag-bootstrap/src/main/resources/application.yml:145-148 ‹CERTAIN›]。已知风险信号：仓库内 fastrag-frontend/.env:3-4 存在明文 AppID/AppSecret（已提交入库面，2026-09-24 复核仍在），spec 将其列为必须处置的安全风险。〔决议 2026-09-24（R4 代裁）：处置取**轮换 AppSecret + .env 移出版本库（.gitignore + 部署环境注入）**；理由：凭据已入库视同泄露是安全惯例，最小改动即轮换+移除；现状差距：.env 明文仍在仓库（已登记债务，见本条），处置须用户执行轮换（代码外动作）。〕
- **验证码防线**：6 位随机数字、5 分钟有效期、60 秒发送频控、一次性消费、用途隔离。〔证据：AuthServiceImpl.java:69-96,145-158 ‹CERTAIN›〕
- **登出时效**：黑名单 TTL 24 小时，与令牌默认有效期等长。〔决议 2026-09-24：按现状为准（R2 代裁）——两值等长使登出吊销覆盖默认令牌全生命周期（JwtUtil.java:45-46、AuthServiceImpl.java:66 ‹CERTAIN›），代码无意图注释、该等长组合不构成可证意图；现状差距：若运维调大 jwt.expiration 而黑名单 TTL 未同步，登出将出现尾部吊销缺口（TTL 为硬编码 24h 不随配置联动），登记为配置联动债务（代裁，用户可推翻）。〕
- 〔决议 2026-09-24（R4 代裁）：登录失败锁定 / 验证码防爆破上限取**验证码校验失败 5 次即作废该验证码、须重新发送；密码登录暂不引入锁定**；理由：6 位码 5 分钟窗口内无限尝试可暴力枚举，OWASP 惯例为限 3-5 次，取 5 次保守上限；登录锁定与全仓「最小改动」现状一致暂缓；现状差距：均未实现，登记为安全债务（承接本 spec 测试策略待建义务）。〕
- 〔决议 2026-09-24（R4 代裁）：密码强度策略取**后端校验 8-64 位且须同时含字母与数字**；理由：与前端 6-64 规则（views/register/index.vue:47-50）衔接并升至 OWASP 常规下限；现状差距：后端注册/重置均无任何强度校验（AuthServiceImpl.java:98-130,132-143 实读），前端 6-64 无复杂度要求，登记为债务。〕
- 〔决议 2026-09-24（R4 代裁）：认证链路 SLA 取保守基线 **登录 P95 ≤ 800ms（BCrypt 校验约百毫秒级为主项）、令牌校验过滤器 P95 ≤ 50ms（本地 HMAC 验签 + Redis 黑名单一次往返）**；理由：单实例内网管理端场景业界常规量级；现状差距：无任何压测/监控证据，登记为观测义务。〕
- 〔决议 2026-09-24（R4 代裁）：**不引入刷新令牌机制**，维持 24 小时过期 + 重新登录为既定取舍；理由：与现状一致（无刷新端点，全仓 grep 无 refresh 实现），管理端场景 24h 重登可接受，刷新令牌属新特性按 IDD 另立；现状差距：无。〕

## 6. 验收清单

- [ ] 正确凭据登录返回令牌与含角色/权限的用户信息（REQ-001）
- [ ] 用户不存在与密码错误返回相同文案（REQ-002）
- [ ] disabled 账号在密码与微信两个通道均被拒绝（REQ-003）
- [ ] 登出后原令牌不再能建立认证上下文（REQ-004）
- [ ] 登录成功/失败、登出、注册均产生登录安全日志（含 IP/UA/结果）（REQ-005）
- [ ] userinfo 返回实时角色与权限（REQ-006）
- [ ] 同一邮箱同一用途 60 秒内二次请求验证码被拒（REQ-007）
- [ ] 验证码 5 分钟过期且使用一次后失效；注册码不能用于重置（REQ-007）
- [ ] 注册成功的新用户拥有 kb_user 角色、enabled 状态、10GB 配额；用户名/邮箱重复被拒（REQ-008）
- [ ] 重置密码后可用新密码登录；未注册邮箱被拒（REQ-009）
- [ ] 扫码登录三态轮询（waiting/scanned+token/expired）行为正确；二维码 5 分钟过期（REQ-010、REQ-011）
- [ ] scene 过期后小程序扫码确认被拒（"二维码已过期，请刷新"），PC 端取不到令牌（REQ-013）
- [ ] 微信新用户自动注册（wx_ 前缀、默认角色），老用户直接登录（REQ-012）
- [ ] API Token 明文仅创建响应返回一次；列表脱敏；撤销后令牌失效（REQ-014、REQ-015）
- [ ] API Token 的管理端点对无 kb:manage 权限用户返回 403（REQ-017）
- [ ] frag_ 令牌认证后获得 api-token:{tokenId} 身份与 kb:manage 权限，且 KB ACL 切面放行（REQ-016）
- [ ] 认证白名单外的一切 /api/** 无凭据不可达；SSE 异步分发不重复鉴权（REQ-018）
- [ ] 令牌载荷携带身份/组织/角色/权限且受签名与过期保护；篡改或过期令牌不建立认证上下文（REQ-019）
- [ ] super_admin 登录后权限折叠为通配并通过全部 @perm.has 检查（REQ-020）
- [ ] @perm.has 不满足时返回 HTTP 403"无权限执行该操作"（REQ-020、REQ-021）
- [ ] 业务错误统一 HTTP 200 + body.code；参数校验失败消息含字段名（REQ-021）
- [ ] 系统内置角色不可删除；全局同时只有一个默认角色（REQ-022）
- [ ] 权限标识唯一；删除权限项级联清除角色关联（REQ-023）
- [ ] 人员分页支持关键字模糊搜索；创建人员用户名唯一、缺省密码可登录（REQ-024）
- [ ] 角色分配为全量替换语义；禁用人员立即无法登录（REQ-024）
- [ ] 存在子组织的节点不可删除；组织树带实时人数（REQ-025）
- [ ] ACL 四类管理端点仅 owner 可用；viewer/editor 越层访问被拒（REQ-026、REQ-027）
- [ ] 与知识库同组织但无显式 ACL 的用户获得 viewer 兜底（可读不可写）（REQ-028）〔勘误标注 2026-09-24（复-1 裁决）：现状为分级兜底——普通成员兜底 editor（可读可写）、管理员兜底 owner、private 库无兜底；本验收项按现状应改写为分级口径，正文暂留待用户确认〕
- [ ] ACL 变更即时同步缓存，被移除用户不能凭缓存残留继续访问（REQ-029）
- [ ] 他人权限查询仅本人或超管可调用（REQ-030）
- [ ] API Token 创建/撤销、ACL 变更、组织/人员/角色全部管理写操作有审计留痕（REQ-033）
- [ ] iam 与 security 模块的测试从零开始补齐（现状无任何测试类，见 §7）
- [ ] 非知识库数据的可见性与管理权按统一规则判定（API Token 全局、系统级数据全员可见、属主、同组织）（REQ-031）
- [ ] iam 全部端点返回统一三段响应结构、分页返回四字段、业务错误以 HTTP 200 + body.code 表达（REQ-032）

## 7. 测试策略

**现状基线（如实记录，勿虚构为已存在）**：fastrag-iam 与 fastrag-security 模块当前没有任何测试类（两个模块不存在 src/test 目录）。测试是该特性从本 spec 向下传递的**待建义务**，不是既有事实。仓库已具备 H2 单测基建（fastrag-bootstrap/src/main/resources/schema-h2.sql 与 data-h2.sql，含 sys_role/sys_user 种子数据），iam 测试可直接复用。

优先测试面（按风险排序，全部通过公共接口——控制器/服务接口——验证，不测实现细节）：

1. **认证正确性**（最高风险）：登录三态（成功/密码错误/禁用）、登出黑名单、验证码频控与一次性、重置流程。
2. **鉴权闸**：白名单内外边界、无凭据/失效令牌/黑名单令牌被拒、@perm.has 与通配语义、@KbAuth 层级判定（owner/editor/viewer × 各档要求）。
3. **API Token 生命周期**：创建返回明文一次、脱敏、撤销/过期后认证失败、平台级身份放行。
4. **管理域约束**：系统角色保护、默认角色唯一、权限标识唯一与级联删除、组织子节点保护、人员用户名唯一与角色全量替换。
5. **缓存一致性**：ACL 变更后缓存失效（可用嵌入式 Redis 或抽象缓存门面测试）。

## 8. UI 决策

本特性 UI 的完整规格见 [iam-frontend.md](./iam-frontend.md)（backend+frontend 角色下前端规格独立成文）。后端规格仅约束业务可观测结果：无权限的用户访问受保护页面会看到 403 反馈页并被告知当前角色〔证据：fastrag-frontend/src/views/error/403.vue:17-31 ‹CERTAIN›〕；无权限的按钮/操作不出现而非置灰〔证据：fastrag-frontend/src/directives/v-permission.ts:27-38（无权限移除 DOM）‹CERTAIN›〕。

## 搬入复核（2026-09-21）

本节为搬入 fastrag-backend 文档树时对 HEAD d8f30cd 的逐条证据复核记录。33 条 REQ 的后端 file:line 引用已逐一实读核对（fastrag-iam 全部 7 控制器与 service/mapper/entity、fastrag-security 全部 14 文件、fastrag-common 响应/异常类、init-scripts 与 SchemaInitializer），除下列差异外全部与现状代码一致。7b1df0f（2026-09-02，权限模型重构：前端 meta.roles 白名单全删 → menu-perm-tree 权限页勾选驱动、admin/index.vue 删除、menu-perm-tree.ts 新增 451 行）早于本 spec 生成时间（2026-09-16）且 spec 正文已引用 menu-perm-tree.ts，故该重构不构成对本文后端证据的失效。以下按严重度列出差异与修订建议；原 REQ 不直接改写（REQ 稳定，修订须用户确认）。**2026-09-24 裁决**：复-1~复-5 已逐条代裁，裁决结果以〔裁决 2026-09-24 …〕行标注于本节各条及对应原文处；修订仅限标注/引用层，REQ 正文语义未改。

### 复-1（矛盾，须仲裁）同组织兜底角色：spec 写 viewer，实现已改为 editor/owner 分级

- spec 断言（REQ-028、GWT-27、§4.8 叙述、验收清单「同组织 viewer 兜底」条）：判定优先级为「显式 ACL > 同组织兜底 viewer > null」。
- 现状代码：接口 javadoc 仍写「同组织兜底 viewer」〔证据：fastrag-security/…/KbAccessChecker.java:14-19,33-35 ‹CERTAIN›〕，但实现已变为——同组织兜底按库 permission 与调用者身份分级：private（指定人共享）库无兜底；非 private 库管理员（持 `*` 通配或 kb_admin 角色）兜底 owner、普通同组织成员兜底 editor；有效角色 = max(ACL 显式角色, 组织兜底角色)，即「ACL 只能提升、不能降级」〔证据：fastrag-knowledge/…/KbAccessCheckerImpl.java:17-21,29-47,49-54 ‹CERTAIN›〕。
- 影响：GWT-27 按现状代码为假（普通同组织成员以 editor 级访问将放行）；验收清单「同组织无显式 ACL 的用户获得 viewer 兜底（可读不可写）」不成立。
- 修订建议（确认后改写 REQ-028 / GWT-27 / 对应验收项）：「判定数据源 = max(显式 ACL, 组织兜底)；组织兜底：private 库无兜底、非 private 库管理员→owner、普通成员→editor；两者皆无 → 拒绝」。实现侧接口 javadoc（KbAccessChecker.java）亦过期，属代码修改，须另行处理。
- 〔裁决 2026-09-24：按现状代码为准——分级兜底（private 无兜底 / 管理员 owner / 普通成员 editor，有效角色 = max(ACL,兜底)）为既成事实且 KbAccessCheckerImpl.java:16-21 类注释即明示该设计（意图可考，非偶然实现）；本文 REQ-028/GWT-27/§4.8 叙述/验收清单的 viewer 口径为过时断言，已在各原文处加勘误标注，正文语义未改（按任务纪律修订仅限标注层）。代码侧两处过期注释（KbAccessChecker.java:14-19,33-35、KbAuthAspect.java:21,71）登记为注释修正债务。（代裁，用户可推翻）〕

### 复-2（证据行号漂移，实质结论维持）观测-1 引用 menu-perm-tree.ts:45-161

- 现状该文件 451 行：45-161 仅覆盖 ACTION_API_LINKS 联动表，完整权限登记簿 MENU_PERM_TREE 位于 163-451〔证据：fastrag-frontend/src/config/menu-perm-tree.ts:46-160,163-451 ‹CERTAIN›〕。
- 「后端仅粗键校验、前端/种子登记细粒度键」的实质维持成立：iam 域后端 @PreAuthorize 仅 admin:role / admin:user / admin:org / kb:manage 四个粗键（各控制器实测）；权限种子 358 条（menu 34 / page_action 108 / api 219）〔证据：init-scripts/data.sql:14-378 ‹CERTAIN›〕。
- 修订建议：引用行号改为「menu-perm-tree.ts 全文件（451 行）」。
- 〔裁决 2026-09-24：核实成立（wc -l = 451，ACTION_API_LINKS:45-161、MENU_PERM_TREE:163-451），已按建议将观测-1 引用改为全文件口径——引用层修订完成，实质结论不变。〕

### 复-3（新增风险，spec 未记录）sys_api_token 表 DDL 缺位于全量 schema

- REQ-014、REQ-016、REQ-017 依赖的 sys_api_token 表：init-scripts/schema.sql 无此表，SchemaInitializer 运行时也不建（仅建 sensitive_word/sys_user_role/sys_dictionary/kb_publish_history/agent/agent_run/email_verification/kb_graph_* 等）〔证据：fastrag-bootstrap/…/SchemaInitializer.java:56-353 建表清单 ‹CERTAIN›〕；其 MySQL 版 DDL 仅见于 H2 测试 schema〔证据：fastrag-bootstrap/src/main/resources/schema-h2.sql:757-769 ‹CERTAIN›〕与 data-mysql.sql:26-37〔证据：‹CERTAIN›。2026-09-24 深证（R3）：data-mysql.sql 的实际执行方式 = **不执行**——application.yml:21-23 `spring.sql.init.mode: never`，docker-compose.yml:52-53 仅挂载 init-scripts 的 schema.sql 与 data-hunan-telecom.sql 两文件入 docker-entrypoint-initdb.d，全仓无其他装配引用 data-mysql.sql，该文件仅为参考 DDL。〕
- 后果：按 conventions.md「schema.sql 与 SchemaInitializer 双处一致」铁律衡量属现存漂移——全新库只执行 schema.sql 时 API Token 功能将因缺表失败。
- 修订建议：补 DDL 进 schema.sql（代码修改须确认）；contract/data.md 已按 H2 schema 记录字段结构并标注此 GAP。email_verification 表无此问题（SchemaInitializer.java:286-299 运行时建表）。
- 〔裁决 2026-09-24：现状核实成立（schema.sql 全文 grep 无 sys_api_token；SchemaInitializer 十表清单 56-353 无本表；docker-compose.yml:52-53 部署链仅挂载 schema.sql+data-hunan-telecom.sql）——缺位债务维持登记，补 DDL 属代码修改待用户确认；内嵌 GAP「data-mysql.sql 的实际执行方式」已 R3 实证关闭（sql.init.mode=never + 无挂载 → 不执行，见本条上方补证）。〕

### 复-4（非功能证据链变化）JWT 密钥默认值链

- spec §5「代码内默认值仅限开发环境」引用 JwtUtil.java:42-43 的长默认串。现状 application.yml:71-73 显式定义 `jwt.secret: ${JWT_SECRET:}`（env 未设置时解析为空串），docker-compose.yml:22 注入开发值；@Value 的长默认串在 yml 显式定义属性后不再生效——JWT_SECRET 未设置时密钥为空串，签发/验签将因 HMAC 密钥长度不合法抛异常（fail-fast，而非以弱密钥运行）〔证据：application.yml:71-73、docker-compose.yml:22、JwtUtil.java:42-50 ‹CERTAIN›〕。
- 修订建议：§5 补充该配置链事实；「生产密钥保管与轮换」GAP 维持不变。
- 〔裁决 2026-09-24：核实成立（application.yml:71-73、docker-compose.yml:22 实读），已按建议将该配置链事实补入 §5「JWT 密钥」条（证据层补充）；「保管与轮换」已由 R4 代裁闭环（见 §5 该条决议）。〕

### 复-5（核实通过，备案）前端证据与计数勘误

- 观测-3 引用 roles.vue:53-57,133：核实仍成立（isEditingDefault 保护 + 更新时条件回传 permissions）〔证据：fastrag-frontend/src/views/admin/account/roles.vue:52-58,130-135 ‹CERTAIN›〕；§8 引用 403.vue:17-31、v-permission.ts:27-38 均核实成立。
- 计数勘误：文件头引用块「14 条 INFERRED / 8 条 GAP」沿用 feature-map 口径原样保留；本文件正文实测为 **6 处（INFERRED）标注**（REQ-021、REQ-023、§5 登出时效、观测-1/2/3）+ **6 处 [GAP:] 占位**（§5 六项）。14/8 与 6/6 的差异疑为上位会话合并 iam-frontend.md 标注的统计口径，待主控澄清；本文档以正文实测计数为准。
- 〔裁决 2026-09-24：前端引用核实无冲突（roles.vue:52-58,130-135、403.vue、v-permission.ts 均实读成立）；上述 6+6 条已于 2026-09-24 全部代裁闭环（INFERRED：R1 1 条 / R2 5 条；GAP：R3 1 条〔含复-3 内嵌 1 处〕/ R4 5 条），不再有待确认项。〕
