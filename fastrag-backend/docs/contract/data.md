# 数据契约 — 实体与数据模式（全后端共享工件）

> 本文件是 **fastrag-backend 全部特性共享**的数据契约工件，由各特性 IDD 链逐特性追加 `## <特性名>` 顶层分节；修改时只动本特性章节，不得破坏其他特性章节。
> 表结构实读 `init-scripts/schema.sql`（全量基线）与实体类/SchemaInitializer（运行时建表）；双处必须一致（conventions.md 铁律）。
> 证据标注：`schema.sql:行 ‹CERTAIN›` / 〔INFERRED：…〕/ `[GAP: …]`。

---

## iam

> 覆盖：iam/security 两模块拥有或写入的数据。`login_security_config` 表（schema.sql:681-690）DDL 存在但**无任何 Java 消费者**（全仓 grep 实测），属 DDL-only 预留，不入契约；其字段（max_fail_attempts/lock_duration/enable_2fa 等）与 spec §5 的「登录失败锁定 GAP」互为印证。

### sys_user — 用户

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | MyBatis-Plus ASSIGN_ID |
| username | VARCHAR(64) NOT NULL UNIQUE | 全局唯一；人员创建/注册前置查重；创建后不可改〔PersonnelServiceImpl.java:28-30,50-58 ‹CERTAIN›〕 |
| real_name | VARCHAR(64) | 微信登录可用 nickName 回填 |
| phone / email | VARCHAR(32) / VARCHAR(128) | email 注册时全小写化，注册查重 |
| password_hash | VARCHAR(256) NOT NULL | BCrypt；API 流程外的唯一凭据存储 |
| role_id | VARCHAR(32) | **遗留列**：仅注册/微信注册写入默认角色 id，鉴权实际读 sys_user_role 中间表〔AuthServiceImpl.java:120,240 写入、loadRoleKeys 读中间表 :161-168 ‹CERTAIN›〕 |
| status | VARCHAR(16) DEFAULT 'enabled' | 取值 enabled/disabled，登录与 KB 判定消费 |
| org_id | VARCHAR(32) | 组织隔离/KB 兜底判定的依据 |
| storage_quota / storage_used | BIGINT（默认 10737418240 / 0） | 注册与微信注册固定写 10GB |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 人员分页排序键 |

Lifecycle：注册/微信注册/人员创建（insert）→ 资料更新/启禁用/角色变更 → 无删除端点（人员不可物理删除）。
Relationships：1—N sys_user_role（经 user_id）；N—1 sys_org（经 org_id，无外键约束、应用层维护）；1—N kb_acl；authored 数据由各业务表 creator 字段引用。

### sys_role — 角色

id VARCHAR(32) PK；role_key VARCHAR(32) NOT NULL UNIQUE（内置：super_admin/kb_admin/kb_user/readonly；自定义：`custom_<毫秒时间戳>`〔RoleServiceImpl.java:14 ‹CERTAIN›〕）；name VARCHAR(64) NOT NULL；description VARCHAR(256)；is_default TINYINT DEFAULT 0（全局至多一个，setDefault 先清后置〔:23-26 ‹CERTAIN›〕）；is_system TINYINT DEFAULT 0（=1 不可删〔:22 ‹CERTAIN›〕）；created_at/updated_at。
Relationships：N—N sys_permission 经 sys_role_permission（permission_key 字符串关联，非外键）；N—N sys_user 经 sys_user_role。
种子：4 系统角色〔data.sql:7-12 ‹CERTAIN›〕。

### sys_permission — 权限项

id BIGINT AUTO_INCREMENT PK；perm_key VARCHAR(64) NOT NULL UNIQUE（命名空间 `menu:*`/`api:xxx:yyy`/页面操作键）；name VARCHAR(64) NOT NULL；type VARCHAR(16) NOT NULL；`group` VARCHAR(32)（树的第一层分组，常见 menu/kb/admin/app/workflow/review〔PermissionServiceImpl.java:124-133 ‹CERTAIN›〕）；parent_key VARCHAR(64)（组内第二层）；category VARCHAR(16) DEFAULT 'page_action'（menu=菜单/page_action=页面操作/api=API 接口，schema.sql:40 注释）；description VARCHAR(256)。
Lifecycle：创建（permKey 唯一校验）→ 更新（permKey 不可改）→ 删除（级联删 sys_role_permission 中该 perm_key）〔PermissionServiceImpl.java:50-85 ‹CERTAIN›〕。
Relationships：N—N sys_role 经 sys_role_permission。
种子：358 条（menu 34 / page_action 108 / api 219）〔data.sql:14-378 实测 ‹CERTAIN›〕。

### sys_role_permission — 角色-权限关联

id BIGINT PK；role_id VARCHAR(32) NOT NULL（INDEX idx_role_id）；permission_key VARCHAR(64) NOT NULL。**无 (role_id,permission_key) 唯一约束**——去重靠应用层 `distinct()`〔RoleServiceImpl.java:27 ‹CERTAIN›〕。删除角色时不级联清理本表（债务行为，spec 复核/契约 Invariants 已登记）；删除权限时级联清理（按 permission_key）。

### sys_user_role — 用户-角色关联

id BIGINT PK；user_id VARCHAR(32) NOT NULL（INDEX）；role_id VARCHAR(32) NOT NULL；UNIQUE KEY uk_user_role(user_id, role_id)。分配语义=全量替换（先删该用户全部再插）〔PersonnelServiceImpl.java:61-76 ‹CERTAIN›〕；super_admin 权限折叠发生在读取侧（AuthServiceImpl.java:178-179），本表仍存全部关联。

### sys_org — 组织

id VARCHAR(32) PK；name VARCHAR(128) NOT NULL；alias VARCHAR(64)；parent_id VARCHAR(32) DEFAULT 'root'（树；'root'=虚拟根）；level INT DEFAULT 1（**创建时硬编码**：有 parentId→2 否则 1，三层树会失真〔OrgServiceImpl.java:39 ‹CERTAIN›〕）；sort INT DEFAULT 0。删除受子节点保护。Relationships：自引用树；1—N sys_user（经 org_id）；kb.org_id 引用（组织隔离，migrate_org_isolation.sql）。

### kb_acl — 知识库 ACL

id BIGINT PK；kb_id VARCHAR(32) NOT NULL（INDEX）；user_id VARCHAR(32) NOT NULL（INDEX）；kb_role VARCHAR(16) NOT NULL（owner/editor/viewer，字符串存枚举名）；granted_by VARCHAR(32)；granted_at DATETIME DEFAULT CURRENT_TIMESTAMP。**无 (kb_id,user_id) 唯一约束**——upsert 靠应用层先删后插〔KbAclServiceImpl.java:23-25 ‹CERTAIN›〕。
Lifecycle：授权写入（addAclEntry upsert）/全量覆盖（setKbAcl）→ 移除；每步同步 Redis `kb:acl:{kbId}:{userId}`（TTL 24h，读穿回填）。
Relationships：N—1 sys_user、N—1 kb（均应用层引用）。role 语义与层级数值表见 interfaces.md `iam.KbAccessChecker`。

### sys_api_token — 平台 API 令牌

> **DDL 缺位警示（复-3）**：`init-scripts/schema.sql` 无此表，SchemaInitializer 亦不建；以下结构以 H2 测试 schema 为准〔schema-h2.sql:757-769 ‹CERTAIN›〕，MySQL 同构 DDL 另见 data-mysql.sql:26-37。〔已实证（2026-09-24 R3）：本表在现行脚本链中**无建表来源**——部署侧唯一入口 docker-entrypoint-initdb.d 仅挂载 schema.sql 与 data-hunan-telecom.sql（docker-compose.yml:52-53，均不含本表），运行时 SchemaInitializer 十表清单亦无本表；data-mysql.sql 因 `spring.sql.init.mode: never`（application.yml:21-23）不参与运行时。全新库部署本表缺建、API Token 功能失败，债务已登记（spec 复-3），补 DDL 待用户确认。〕

id VARCHAR(32) PK（ASSIGN_ID）；name VARCHAR(128) NOT NULL；token VARCHAR(256) NOT NULL（`frag_<32位uuid>`，INDEX idx_api_token_value）；permission VARCHAR(16) DEFAULT 'read'（read/write，**仅存储不参与鉴权**，spec 观测-2）；expires_at DATETIME NULL（NULL=永不过期）；revoked TINYINT/INT DEFAULT 0（findValidToken 过滤 revoked=false 且未过期；撤销=物理删除，revoked 标记实际不落库复用〔ApiTokenServiceImpl.java:71-91 ‹CERTAIN›〕）；created_by VARCHAR(32)；created_at/updated_at。
Lifecycle：创建（明文仅返回一次）→ 列表脱敏 → 撤销（物理删除，即时失效）。

### email_verification — 邮箱验证码

id VARCHAR(32) PK；email VARCHAR(128) NOT NULL；code VARCHAR(8) NOT NULL；purpose VARCHAR(16) NOT NULL（register/reset，用途隔离的查询条件）；expires_at DATETIME NOT NULL（签发+5 分钟）；used TINYINT DEFAULT 0（一次性消费开关）；created_at DATETIME DEFAULT CURRENT_TIMESTAMP；INDEX idx_email_purpose(email, purpose)。运行时建表：SchemaInitializer.java:286-299〔‹CERTAIN›；schema.sql 无此表——与 sys_api_token 同类双轨差异，但运行时有兜底，不构成功能缺失〕。
Lifecycle：sendCode 插入 → 校验命中（email+code+purpose+used=false+未过期，按 created_at 倒序取最新一条）→ used 置 1。〔决议 2026-09-24（R4 代裁）：过期/已用记录保留期取 **7 天**，超期可由定期任务清理；理由：验证码 5 分钟失效后记录仅剩排障审计价值，7 天为业界日志类数据常规下限且远超有效窗；现状差距：无任何清理（表缓慢增长，量级=发送次数，低危），登记为轻量债务。〕

### sys_login_log — 登录安全日志（iam 写入侧）

id BIGINT PK；user_id VARCHAR(32)（失败时可为 NULL）；username VARCHAR(64)；login_time DATETIME DEFAULT CURRENT_TIMESTAMP；ip VARCHAR(64)；device/os/browser VARCHAR（UA 解析，微信通道写死「小程序/WeChat/WeChat」〔AuthController.java:159-168 ‹CERTAIN›〕）；location VARCHAR(128)（iam 写入侧不填）；status VARCHAR(16) DEFAULT 'success'（success/failed/logout/register）；fail_reason VARCHAR(256)。
写入方：AuthController 四处埋点（登录成败/登出/注册/微信）〔AuthController.java:61-83,96-105,115-123,183-191 ‹CERTAIN›〕，经跨模块调用 operation 的 SysLoginLogService.addLoginLog(userId, username, ip, device, os, browser, status, failReason)〔SysLoginLogService.java:15-16 ‹CERTAIN›〕。查询/展示归 operation-analytics 特性（spec Out of Scope）。

### iam 的 Redis 数据结构（运行时状态，无表）

| 键模式 | 值 | TTL | 写/读方 |
|---|---|---|---|
| `jwt:blacklist:{完整token串}` | "1" | 24h | logout 写 / JwtAuthFilter 查〔AuthServiceImpl.java:66、JwtAuthFilter.java:67 ‹CERTAIN›〕 |
| `kb:acl:{kbId}:{userId}` | KBRole 枚举名 | 24h（写穿/读穿回填） | KbAclServiceImpl 写查〔:20-33 ‹CERTAIN›〕 |
| `email:rate:{email}:{purpose}` | "1" | 60s | sendCode 频控〔AuthServiceImpl.java:74-89 ‹CERTAIN›〕 |
| `wechat:qr:scene:{scene16}` | ""→token | 300s；确认后重置为 token 60s | 扫码状态机（空=waiting/值=可取 token/无键=expired）〔AuthServiceImpl.java:258-299 ‹CERTAIN›〕 |
| `wechat:access_token` | 微信 access_token | expires_in−300s | 小程序码生成〔AuthServiceImpl.java:302-326 ‹CERTAIN›〕 |

**Parent Trace:** 各实体 → interfaces.md 对应契约 → Design 模块 → REQ：sys_user/sys_user_role/sys_role/sys_role_permission/sys_permission → iam.RbacDataService/SecurityGate → REQ-018~025、REQ-032；kb_acl → iam.KbAclService/KbAccessChecker → REQ-026~030；sys_api_token → iam.ApiTokenService → REQ-014~017；email_verification/sys_login_log → iam.AuthService → REQ-005~013；Redis 结构 → iam.SecurityGate/AuthService/KbAclService → REQ-004、REQ-007、REQ-010~013、REQ-029。

---

## kb-core

> 覆盖：知识库主干五表 + 孤儿监听器两表。kb_acl 归 `## iam` 节（本特性的生命周期契约消费它，不重复定义）。种子数据：kb_sample 一条 + 分类 4 条〔data.sql:431-434,590-595 ‹CERTAIN›〕。

### kb — 知识库（聚合根）

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | MyBatis-Plus ASSIGN_ID（雪花）〔KnowledgeBase.java:35-36 ‹CERTAIN›〕 |
| name | VARCHAR(128) NOT NULL | 创建必填（@NotBlank），列表 keyword 匹配字段 |
| description | TEXT | 可空；keyword 第二匹配字段〔KbServiceImpl.java:84 ‹CERTAIN›〕 |
| category | VARCHAR(64) | 存分类 **id**（CategoryModule 计数按 id 比对，KbCategoryController.java:80）；但种子数据存的是分类**名称**字符串（data.sql:431 '技术文档'）——两种取值并存，getCategories 聚合以原值作 id+name 返回〔KbServiceImpl.java:260-265 ‹CERTAIN›〕，**口径漂移已登记** |
| tags | JSON | 标签**名称**数组的 JSON 串（非规范化的展示缓存；规范关联在 kb_tag_relation）〔KbServiceImpl.java:112,140 ‹CERTAIN›〕 |
| embedding_model / dimension | VARCHAR(128) / INT | 模型名与向量维度；dimension 仅创建读出、全仓无写入方〔grep 实测 ‹CERTAIN›〕 |
| creator / org_id | VARCHAR(32) | creator=创建者 userId；org_id=会话组织，组织隔离与可见性判定依据〔KbServiceImpl.java:121-122 ‹CERTAIN›〕 |
| type | VARCHAR(16) DEFAULT 'personal' | team/personal；**仅创建时由 permission 派生**（private→personal 否则 team），更新不重算〔KbServiceImpl.java:130 ‹CERTAIN›〕 |
| permission | VARCHAR(16) DEFAULT 'private' | private/team/public 三值（无枚举约束，任意字符串可落库）；private=指定人共享（组织兜底失效，KbAccessCheckerImpl.java:42） |
| used_size / total_size | BIGINT DEFAULT 0 | 创建置 0 后全仓无写入方——配额执行缺失〔grep 实测 ‹CERTAIN›〕 |
| retrieval_config / file_type_config / custom_attr_schema | JSON | 检索配置/允许上传类型/自定义属性定义，请求对象 JSONUtil 序列化落库、toDto 容错反序列化〔KbServiceImpl.java:119-120,343-345 ‹CERTAIN›〕 |
| parse_mode / split_mode | VARCHAR(16) DEFAULT 'auto' | 仅存储透传，语义归 parse-strategy 特性（ADR-0001 无涉） |
| graph_auto_build | TINYINT DEFAULT 1 | **DDL 缺省 1（注释 ADR-0002 默认开启）但代码创建未指定时显式写 0**——两处矛盾，代码生效值=0（design/kb-core.md §6 仲裁）〔schema.sql:99、KbServiceImpl.java:117 ‹CERTAIN›〕 |
| graph_llm_model | VARCHAR | 图谱抽取 LLM（fallback），消费方在 knowledge-graph |
| created_at / updated_at | DATETIME | INSERT / INSERT_UPDATE 自动填充〔KnowledgeBase.java:58-61 ‹CERTAIN›〕 |

Lifecycle：create（装配+owner ACL+打标）→ update（部分语义）→ delete（七级级联后物理删除，无软删）。
Relationships：1—N kb_file/kb_chunk/kb_qa_pair/kb_tag_relation(targetType=kb)/kb_acl（均经 kb_id，无外键、应用层维护）；N—1 sys_org（经 org_id）。

### kb_category — 知识库分类

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | ASSIGN_ID |
| name | VARCHAR(128) NOT NULL | 无唯一约束（同组织可重名）〔schema.sql:1327-1337 ‹CERTAIN›〕 |
| description / color / icon | VARCHAR(256)/VARCHAR(16)/VARCHAR(64) | 展示属性 |
| sort | INT DEFAULT 0 | 升序；创建缺省 0〔KbCategoryController.java:120 ‹CERTAIN›〕 |
| org_id | VARCHAR(32) | 组织隔离键（NULL=未分配）；非 API Token 创建强制=操作者组织、更新不可迁移〔KbCategoryController.java:118,142 ‹CERTAIN›〕 |
| created_by / created_at | VARCHAR(32)/DATETIME | created_by 当前无写入方（创建路径未赋值）〔KbCategoryController.java:116-130 实读 ‹CERTAIN›〕 |

Lifecycle：create（权限键 kb:category:create）→ update（kb:category:edit，跨组织 403）→ delete（kb:category:delete，物理删除，**不级联清理引用它的 kb.category 值**——删除后 kb 侧分类指针悬空，列表 count 自然归 0〔KbCategoryController.java:158-173 实读无级联 ‹CERTAIN›；悬空语义影响登记为债务〕）。
Relationships：被 kb.category 按值引用（无外键）；N—1 sys_org。

### kb_tag — 标签

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | ASSIGN_ID |
| kb_id | VARCHAR(32) NOT NULL | 归属 KB（创建时的隔离归属，NOT NULL）；KbServiceImpl 自建打标路径会落 kbId，KbTagController 强制要求〔schema.sql:1303 ‹CERTAIN›〕 |
| tag_type_id | VARCHAR(16) | 标签类型（T1 文档分类/T2 优先级/T3 状态，可空）〔KbTagRequest.java:22-23 ‹CERTAIN›〕 |
| name | VARCHAR(64) NOT NULL UNIQUE (uk_name) | **全局唯一**（跨 KB 也拒绝）+ 应用层前置查重〔schema.sql:1311、KbTagController.java:76-80 ‹CERTAIN›〕 |
| color / description | VARCHAR(16)/VARCHAR(256) | 可空；blank 归一为 null〔KbTagController.java:142-144 ‹CERTAIN›〕 |
| usage_count | INT DEFAULT 0 | 非规范化引用计数，不变式 =kb_tag_relation 中该标签行数（维护纪律见 interfaces.md `kb-core.TagConsistencyService`） |
| created_by / created_at | VARCHAR(32)/DATETIME | created_by 存用户名（非 userId）〔KbTagController.java:88-89 ‹CERTAIN›〕 |

Lifecycle：create（editor+ 判定）→ update（改名走全局查重）→ delete（仅零引用，物理删除）。KB 删除时零引用标签不随之删除（仅计数校正）〔KbServiceImpl.java:228-233 ‹CERTAIN›〕。
Relationships：1—N kb_tag_relation（经 tag_id）；N—1 kb（经 kb_id，仅语义归属）。

### kb_tag_relation — 标签关联（多对多）

id VARCHAR(32) PK；tag_id VARCHAR(32) NOT NULL（INDEX idx_tag_id）；target_type VARCHAR(16) NOT NULL DEFAULT 'kb'（`kb`/`file` 两常量，TagRelationHelper.java:36-37）；target_id VARCHAR(32) NOT NULL（INDEX idx_target(target_type,target_id)）；created_at DATETIME DEFAULT CURRENT_TIMESTAMP。**UNIQUE KEY uk_tag_target(tag_id,target_type,target_id)**——并发重复插入被应用层捕获忽略〔schema.sql:1315-1324、TagRelationHelper.java:89-96 ‹CERTAIN›〕。
Lifecycle：打标路径写入/移除（全部经 TagRelationHelper）；目标删除时级联移除（KB 删除逐条 removeTag，KbServiceImpl.java:228-233；文件删除链归 file-ingestion）。
Relationships：N—1 kb_tag；多态目标（kb→kb.id / file→kb_file.id，无外键、应用层维护）。

### kb_listener / kb_listener_log — 知识库监听器（孤儿表）

> **无运行时消费者警示**：两表仅有 DDL（schema.sql:904-925、migration-20260628.sql:196-226）、实体与空 mapper（KbListenerMapper/KbListenerLogMapper，无任何 Service/Controller 引用，全仓 grep 实测）；data.sql:531-537 有种子数据。`kb_listener_alert` 表仅在 migration 脚本中存在（schema.sql 无、无实体）。〔‹CERTAIN›〕〔决议 2026-09-24（R4 代裁）：产品意图（Webhook/定时检查语义）与激活计划不可考，取**维持休眠**——激活须按 IDD 以新特性立项，届时一并处置 kb_listener_log 种子脚本 `level` 列缺陷与 kb_listener_alert 缺实体问题；现状差距：无（休眠本体即登记）。〕

kb_listener：id VARCHAR(32) PK；kb_id VARCHAR(32)；name VARCHAR(128) NOT NULL；listen_type VARCHAR(32)（种子值 file_change/schedule）；target VARCHAR(256)（Webhook URL 或 cron 表达式）；config JSON；status VARCHAR(16) DEFAULT 'enabled'；last_run_at DATETIME；created_by/created_at。
kb_listener_log：id BIGINT AUTO_INCREMENT PK；listener_id VARCHAR(32) NOT NULL（INDEX）；event_type VARCHAR(32)；message TEXT；status VARCHAR(16)；created_at。种子脚本写入不存在的 `level` 列（data.sql:535 vs 两处 DDL 均无 level，实体标 @TableField(exist=false)）——**种子脚本在全新库执行会失败**〔data.sql:535、schema.sql:917-925、KbListenerLog.java:26-27 ‹CERTAIN›〕。

**Parent Trace:** kb → kb-core.KbLifecycleService → Design.KnowledgeBaseModule → REQ-A01~A09、B01~B05；kb_category → Design.CategoryModule → REQ-C01~C06（浅模块，无接口契约）；kb_tag/kb_tag_relation → kb-core.TagConsistencyService → Design.TagModule → REQ-D01~D07；kb_listener* → 无契约（孤儿，spec §2 Out of Scope）。

---

## parse-strategy

> 覆盖：解析策略主表 + 死配置合规规则表。文件绑定字段 kb_file.parse_strategy_id / processing_config 归 `## file-ingestion` 节（本特性的守卫契约消费它，不重复定义）。两表均仅存在于 `init-scripts/schema.sql`，SchemaInitializer 无对应运行时 DDL（grep 实测）——全新库建表依赖全量脚本执行。无种子数据（data.sql 无 kb_parse_strategy/kb_compliance_rule 记录，grep 实测）。

### kb_parse_strategy — 解析策略

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | MyBatis-Plus ASSIGN_ID（雪花）〔KbParseStrategy.java:34-35 ‹CERTAIN›〕 |
| kb_id | VARCHAR(32) NOT NULL（INDEX idx_kb_id） | 归属知识库；全部查询的第一过滤条件 |
| file_id | VARCHAR(32) DEFAULT NULL（INDEX idx_kb_parse_strategy_file） | **双态判别字段**：NULL=知识库级命名策略（参与列表/自动匹配/默认）；非空=该文件的专属自定义策略（不出现在策略管理列表、不参与自动匹配，随文件删除级联清理）〔schema.sql:191、ParseStrategyServiceImpl.java:29-32、FileServiceImpl.java:492-495 ‹CERTAIN›〕 |
| name | VARCHAR(128) NOT NULL | 三层必填性不一致（已登记债务）：知识库级必填且 ≤50〔ParseStrategyServiceImpl.java:256-262 ‹CERTAIN›〕；文件级可空、缺省「自定义」、无 50 上限（仅 DDL 128 兜底）〔:174 ‹CERTAIN›〕；DDL NOT NULL(128)〔schema.sql:192 ‹CERTAIN›〕 |
| description | VARCHAR(256) | 知识库级提供时 ≤200 超 200 拒绝〔:264-266 ‹CERTAIN›〕；文件级超 200 静默截断〔:175 StrUtil.maxLength ‹CERTAIN›〕——同约束两级行为相反 |
| extensions | JSON | 扩展名字符串数组（带点小写，如 [".pdf",".docx"]）；服务层 JSON 序列化落库；知识库级创建必填非空、逐个经 ParseMethodRegistry.isSupported 校验〔ParseStrategyServiceImpl.java:72、235-243 ‹CERTAIN›〕；文件级可空；预设路径写死宽集含 registry 外扩展名（.markdown/.html/.htm/.rtf/.xml，FileServiceImpl.java:531）——两处口径不一致已登记 |
| parse_method | VARCHAR(16) DEFAULT 'default' | 九值枚举字符串（ParseMethodRegistry code）；无 DB 级约束，合法性靠服务层校验〔ParseStrategyServiceImpl.java:224-229 ‹CERTAIN›〕 |
| is_default | TINYINT DEFAULT 0 | 默认标记；**无 (kb_id, is_default) 唯一约束**——唯一性靠 setDefault 先清后置两步（无显式事务）〔ParseStrategyServiceImpl.java:111-121、schema.sql:196 ‹CERTAIN›〕‹CERTAIN（2026-09-24 深证：:21-23 类无 @Transactional、:113-120 两次 updateById 各自自动提交）›异常窗口零默认为结构性事实：清空与置位间异常即零默认，下游回退「首个策略/null→系统默认配置」（FileServiceImpl.java:939-951） |
| advanced | JSON | 高级配置（parse/chunk/index 三分组或旧平铺格式）；自由 JSON 透传保存，读取侧 StrategyConfigResolver 容错合并；服务层仅校验 overlap<chunkLength 物理约束〔ParseStrategyServiceImpl.java:281-293 ‹CERTAIN›〕 |
| llm_model | VARCHAR(128) | 解析用 LLM 模型 code；消费方：解析增强（DocumentParserImpl.java:230-232）、图谱 LLM 路由优先于 KB 级（GraphBuildConsumer.java:1082-1096）〔‹CERTAIN›〕 |
| created_at / updated_at | DATETIME（INSERT / INSERT_UPDATE 自动填充） | 〔KbParseStrategy.java:46-49 ‹CERTAIN›〕 |

Lifecycle：创建（知识库级 create is_default 恒 0 / 文件级 saveFileStrategy upsert）→ 更新（部分语义）/ 设默认（先清后置）→ 删除（仅默认禁删；删除后悬空绑定由读路径回退自动匹配容错）；文件级随文件删除级联清理（FileServiceImpl.java:613-619）。
Relationships：N—1 kb（经 kb_id，无外键）；1—N kb_file（文件级经 file_id 一对一绑定、知识库级经 kb_file.parse_strategy_id 引用，无外键、应用层维护——悬空引用被 ParseStrategyBindingGuard 容错）。

### kb_compliance_rule — 合规规则（死配置）

> **死配置警示（CERTAIN）**：本表为 feature-map 引述「死配置」缺陷信号的核实结果——DDL、实体（KbComplianceRule.java:24-29）、mapper（KbComplianceRuleMapper.java）三件齐备，但全仓 grep 无任何 Service/Controller/消费者读写；无 API 可创建记录，data.sql 无种子。合规校验功能整体未实现，**不入任何功能契约**（spec §2 Out of Scope）。〔决议 2026-09-24：取「不激活、维持休眠」——无产品需求输入前不唤醒该表，不接入入库校验/发布审核；未来激活须走 IDD 新特性链重新立项。理由：全仓零消费者、零 API、零种子，激活即引入未定义行为；现状差距：无（代裁，用户可推翻）〕

id VARCHAR(32) PK；kb_id VARCHAR(32)；rule_name VARCHAR(128) NOT NULL；rule_type VARCHAR(32)；pattern TEXT；action VARCHAR(32)；severity VARCHAR(16)；enabled TINYINT DEFAULT 1；created_by VARCHAR(32)；created_at DATETIME DEFAULT CURRENT_TIMESTAMP〔schema.sql:857-868 ‹CERTAIN›〕。
Lifecycle：无（无写入方）。Relationships：仅语义上 N—1 kb。

**Parent Trace:** kb_parse_strategy → parse-strategy.StrategyCrudModule / ParseStrategyBindingGuard / StrategyConfigResolver → Spec REQ-A01~A05、B01、D01、D02、E01~E05、G01~G06；kb_compliance_rule → 无契约（死配置，spec §2 Out of Scope）。

---

## file-ingestion

> 覆盖：文件主干表 kb_file + 文件夹表 kb_folder + MQ 消息与 MinIO 对象键两类运行时结构。kb_chunk 的写入发生在本特性的 StorageModule，但该表数据契约归 chunk-management 链（届时另立；parse_strategy_id/processing_config 等绑定列已按上游裁决归 `## parse-strategy`/本节 kb_file 行说明）。kb_qa_pair 归 qa-pairs 链（本特性仅执行删除级联）。实体类：KbFile.java:39-87、KbFolder 实体。无种子数据依赖（data.sql 无 kb_file 业务种子，grep 实测）。

### kb_file — 知识库文件

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | MyBatis-Plus ASSIGN_ID（雪花）〔KbFile.java:42-43 ‹CERTAIN›〕 |
| kb_id | VARCHAR(32) NOT NULL（INDEX idx_kb_id） | 归属知识库；全部查询第一过滤条件 |
| name | VARCHAR(256) NOT NULL | 原始文件名；跨库移动时目标库同名冲突校验键〔FileServiceImpl.java:742-748 ‹CERTAIN›〕 |
| category | VARCHAR(16) DEFAULT 'document' | document/image/audio/video 四值；上传时 detectCategory 按扩展名识别，未识别兜底 document〔FileServiceImpl.java:906-914 ‹CERTAIN›〕 |
| extension | VARCHAR(16) | 不带点原样存储（大小写未归一——parse-strategy 链已登记匹配侧不归一债务） |
| size | BIGINT DEFAULT 0 | 字节；OnlyOffice 替换原文件时更新〔FileServiceImpl.java:308 ‹CERTAIN›〕 |
| object_key | VARCHAR(512) | MinIO 对象键 `{kbId}/{uuid}`；copy 场景多文件共享同一 objectKey〔FileServiceImpl.java:120,709 ‹CERTAIN›〕 |
| status | VARCHAR(16) DEFAULT 'pending' | pending/processing/completed/failed 四态状态机〔KbFile.java:50 ‹CERTAIN›〕 |
| progress | INT DEFAULT 0 | 0-100 里程碑推进（10/30/60/70/75/80/100）〔IngestionConsumer.java:159-520 ‹CERTAIN›〕 |
| stage | VARCHAR(64) | 当前阶段描述（downloading/parsing/chunking/slicing/extracting images/storing/done/retrying/error:…）；超 60 字符截断〔IngestionConsumer.java:736 ‹CERTAIN›〕 |
| duration / pages | INT | 音视频时长秒 / 文档页数；写入方在解析链路，当前主链路未见回填〔grep 实测 ‹CERTAIN›〕〔决议 2026-09-24：回填时机取「摄取完成时由 IngestionConsumer 回填」——pages 在 PDF 解析期已产出（DocumentParserImpl.java:136 totalPages 未写回），duration 需媒体探测（解析链无 ffprobe 类探测，需另建）。理由：摄取完成点是唯一具备完整解析产出的写入窗口；现状差距：已登记债务（implement/file-ingestion.md ③）——回填点未实现，两列暂恒空（代裁，用户可推翻）〕 |
| parse_strategy_id | VARCHAR(32) | 当前策略绑定（真源；语义归 parse-strategy.ParseStrategyBindingGuard） |
| parse_strategy_name | VARCHAR(128) | 冗余展示名（当前无写入方，FileDto 名称经实时查策略回填）〔FileServiceImpl.java:995-1008 grep 实测 ‹CERTAIN›〕 |
| chunk_count | INT DEFAULT 0 | 分片数；storeChunks 写 auto 数，消费端按 auto+manual 对账修正〔StorageServiceImpl.java:167-171、IngestionConsumer.java:772-776 ‹CERTAIN›〕 |
| processing_mode | VARCHAR(16) DEFAULT 'chunk' | chunk/qa；qa 模式拒绝重切〔FileServiceImpl.java:382-384 ‹CERTAIN›〕；qa 模式配套参数列 qa_generation_config 见 `## qa-generation` 节（消费端实现已立项，qa-pairs spec REQ-E03 清偿） |
| processing_config | JSON | 上传向导处理配置快照（FileProcessRequest 全字段）；retry/re-chunk 时重建请求，解析失败降级最小默认〔FileServiceImpl.java:230-247 ‹CERTAIN›〕 |
| enable_graph_build | TINYINT DEFAULT 0 | 图谱开关；处理触发时未显式设置过则按 KB 级解析〔FileServiceImpl.java:169-180、schema.sql:123 ‹CERTAIN›〕 |
| folder_id | VARCHAR(32) | 文件夹归属（引用 kb_folder.id，可为空=根） |
| view_count | BIGINT DEFAULT 0 | 查看计数；当前无递增写入方〔grep 实测 ‹CERTAIN›〕 |
| region / publish_date / doc_level / issuer / doc_number | VARCHAR(64) / DATE / VARCHAR(16) / VARCHAR(128) / VARCHAR(64) | 业务元数据固定字段（分册四）；region/publish_date/doc_level 三列事务内冗余回填 kb_chunk〔schema.sql:126-130、FileMetadataService.java:24 ‹CERTAIN›〕；region 有多值 JSON 数组串存法 |
| metadata_status | VARCHAR(16) DEFAULT 'none' | none/partial/full/revised 状态机；写即 revised，补抽仅覆盖 none/partial〔FileMetadataService.java:16-17,33-34 ‹CERTAIN›〕 |
| metadata_source | VARCHAR(16) | manual/auto/mixed 填充来源 |
| custom_attrs | JSON | 自定义属性取值 KV（schema 见 kb.custom_attr_schema） |
| deleted_at | DATETIME（INDEX idx_deleted_at） | 软删除标记；非空=回收站；恢复置 NULL |
| created_at / updated_at | DATETIME（INSERT / INSERT_UPDATE 自动填充） | updatedAt 晚于 createdAt 判定「用户显式设置过」〔FileServiceImpl.java:169-171 ‹CERTAIN›〕 |

Lifecycle：upload（pending 登记）→ process（快照固化+入队）→ processing（里程碑推进）→ completed/failed → （failed→retry；任意→re-chunk 重跑）→ 软删（deleted_at 置位，图谱先清）→ 恢复（deleted_at 清空）→ 永久删除/清空回收站（五路清理后物理删除）。跨库移动为「清理源库衍生 + 迁移 MinIO + 改归属 + 重处理」的事务复合操作。
Relationships：N—1 kb（经 kb_id，无外键）；1—N kb_chunk（经 file_id）；1—N kb_qa_pair（经 file_id）；1—0..1 kb_parse_strategy（文件级专属策略经 file_id，随文件删除级联清理，FileServiceImpl.java:613-619）；kb_tag_relation 多态目标 target_type='file'。

### kb_folder — 知识库文件夹

id VARCHAR(32) PK；kb_id VARCHAR(32) NOT NULL（INDEX idx_kb_id）；name VARCHAR(128) NOT NULL；parent_id VARCHAR(32) DEFAULT 'root'（树）；sort INT DEFAULT 0；created_at/updated_at〔schema.sql:144-153 ‹CERTAIN›〕。归属 FolderService 管理（本特性仅消费 folder_id 引用与移动落点校验）；Relationships：自引用树；1—N kb_file.folder_id（无外键）。‹CERTAIN（2026-09-24 深证）›悬空 folder_id 的写入面与后果已证：update/moveToKb 仅写值不校验存在性（FileServiceImpl.java:651、768-769）；删除文件夹仅允许空夹（FolderServiceImpl.java:116-122 有文件拒绝删），常规 UI 路径不产生悬空——API 直调可造。后果修正原断言：悬空值并非「由读取侧按空处理」，后端仅透传（FileServiceImpl.java:984），前端文件夹视图过滤下悬空文件既不入根目录（要求 folderId 为空）也不命中任何可选文件夹，**视图中不可见**（FileManager.vue:175-179）。已登记债务：update/moveToKb 的 folderId 存在性校验缺失。

### MQ 消息与 MinIO 对象键（运行时结构，无表）

**摄取消息**（`fastrag.direct` 交换机 → routing key `ingestion` → `fastrag.ingestion.queue`，Jackson JSON 序列化，持久化队列）〔RabbitMQConfig.java:34-61 ‹CERTAIN›〕：

| 字段 | 类型 | 说明 |
|---|---|---|
| fileId / kbId | string | 必填；幂等与定位键 |
| objectKey | string | MinIO 原件键（消息携带，消费端不重查） |
| strategyId | string | 守卫裁决值（可空=运行时仅系统默认配置） |
| operator | string | 操作人用户名，缺省 "system" |
| processingMode | string | chunk/qa |
| enableGraphBuild | Boolean/Number | 完成后是否投递图谱构建（三类型解析 :486-491） |
| processingConfig | string(JSON) | FileProcessRequest 快照（ParseOptions 与 retryCount 的来源）〔IngestionConsumer.java:552-616 ‹CERTAIN›〕 |

**MinIO 对象键约定**（全部 `{kbId}/{fileId}` 前缀，永久删除按前缀整删，FileServiceImpl.java:596）：

| 键模式 | 内容 | 写方 |
|---|---|---|
| `{kbId}/{uuid}` | 上传原文件（kb_file.object_key 指向） | upload〔FileServiceImpl.java:120,124 ‹CERTAIN›〕 |
| `{kbId}/{fileId}/parsed.md` | 解析 Markdown 全文（不落库，路径约定） | MarkdownService.persistParsedMarkdown / FileController.saveMarkdown〔MarkdownService.java:61、FileController.java:160,181 ‹CERTAIN›〕 |
| `{kbId}/{fileId}/segments/{i}.{ext}` | 音频 ASR 时间戳切片 | IngestionConsumer（仅分段数=分片数时）〔:248-252 ‹CERTAIN›〕 |
| `{kbId}/{fileId}/images/{imageKey}` | PDF/DOCX/PPT 嵌入图片（经装饰性过滤） | MediaExtractor.extractPdfImages / IngestionConsumer〔:292,355 ‹CERTAIN›〕 |

**Parent Trace:** kb_file → file-ingestion.IngestionPipeline → Design.IngestionPipelineModule/FileLifecycleModule/FileMetadataModule → Spec REQ-A01、B01~B02、C02、F01~F05、G01~G03；kb_folder → Design.FileLifecycleModule → REQ-F03/F05；MQ 消息 → IngestionPipeline → REQ-B01、C09；MinIO 键约定 → IngestionPipeline/FileReadApiModule → REQ-E01~E02、C04~C05、F02、F06。

---

## chunk-management

> 覆盖：检索最小单元表 kb_chunk + 两类运行时结构（分片 ID 段约定、Milvus collection 约定）。本表数据契约由本链正式落地（上游 `## file-ingestion` 节首注明划归）；写入方横跨三条链——摄取管线（本链 ChunkStoreService）、手工 CRUD（本链 ChunkLifecycleService）、AI 分片应用（ai-chunking 链，同款替换口径）——字段级写入方在 Lifecycle 中逐条标注。DDL 双轨：建表仅存于 `init-scripts/schema.sql`（SchemaInitializer 无 kb_chunk 建表，仅增列与 FULLTEXT 索引）。
> **登记债务（DDL 双处漂移）**：`idx_content_fulltext`（kb_chunk.content 的 ngram FULLTEXT 索引，KbChunkMapper.fulltextSearch 依赖）仅由 SchemaInitializer 运行时创建（SchemaInitializer.java:231-243），`init-scripts/schema.sql` 无对应 DDL（grep 实测）——全新库靠启动期增补兜底，但违反「两处必须一致」纪律（conventions.md §2），待补入全量脚本。〔‹CERTAIN›〕

### kb_chunk — 知识分片（检索最小单元，父子两级）

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(64) PK（应用生成 INPUT） | 三段式 ID 段约定见下「运行时结构」；embedding_id 与之同值 |
| kb_id | VARCHAR(32) NOT NULL（INDEX idx_kb_id） | 归属知识库；全部查询第一过滤条件 |
| file_id | VARCHAR(32) NOT NULL（INDEX idx_file_id） | 归属文件 |
| file_name | VARCHAR(256) | 冗余展示名（落库时从 kb_file.name 快照）〔StorageServiceImpl.java:244,290 ‹CERTAIN›〕 |
| parent_id | VARCHAR(64) DEFAULT NULL（INDEX idx_parent_id） | 子分片指向父分片 id；父分片本身为 NULL；**无外键**，悬空由读取侧按孤立分片混排处理〔ChunkServiceImpl.java:61-66 ‹CERTAIN›〕 |
| chunk_index | INT NOT NULL | 文档内位置序号；父子模式下父分片取组内首个子分片位置；**无 (file_id, chunk_index) 唯一约束**——重切对账窗口内 auto/manual 索引交错依赖完成后 reconcile 消除〔IngestionConsumer.java:753-778 ‹CERTAIN›〕 |
| content | MEDIUMTEXT | 分片正文；父分片内容=子分片按 "\n\n" 拼接〔ParentChunkAssembler.java:148-151 ‹CERTAIN›〕；FULLTEXT ngram 索引（运行时，见节首债务） |
| embedding_id | VARCHAR(64) | Milvus 向量 id（=分片 id）；父分片无向量则无意义 |
| vector_stored | TINYINT DEFAULT 0 | 0=纯文本分片（embedding 失败/Milvus 不可用降级），1=向量已在库 |
| start_time / end_time | DOUBLE DEFAULT NULL | 音视频分片起止秒；时间分段按文本长度比例线性分配〔ChunkingServiceImpl.java:379-428 ‹CERTAIN›〕 |
| page_number / page_range | INT / VARCHAR(16) | PDF 页码 / 页码范围（"3-4"，跨页分片由 ChunkBuilder.updatePage 维护）〔ChunkingServiceImpl.java:1385-1394 ‹CERTAIN›〕 |
| image_keys | JSON DEFAULT NULL | 关联 MinIO 图片 key 数组（规则分片从 [IMAGE:key] 占位符提取 / 结构分片从 IMAGE 节点收集）〔ChunkingServiceImpl.java:558-569,1319-1331 ‹CERTAIN›〕 |
| chunk_type | VARCHAR(16) DEFAULT 'text' | text/image/table/code/parent 五值（DDL 注释仅列 text/image/parent，实际代码另有 table/code——handleAtomicOverflow 按此分发拆分策略）〔schema.sql:170、ChunkingServiceImpl.java:1017-1036 ‹CERTAIN›〕 |
| origin | VARCHAR(16) NOT NULL DEFAULT 'auto' | auto=管线自动分片 / manual=用户手工创建（重分片与 AI 应用落库时保留）；**保留判别即读本字段**〔schema.sql:171、FileServiceImpl.java:424-435 ‹CERTAIN›〕 |
| title | VARCHAR(255) DEFAULT NULL | 所属最近标题（结构感知分片）；SchemaInitializer 增列轨道并存（:305-309） |
| heading_path | VARCHAR(1024) DEFAULT NULL | 层级路径 "第一章 > 1.1 背景"（" > " 连接）；父分片聚合分组键〔ChunkingServiceImpl.java:946-952、ParentChunkAssembler.java:105-113 ‹CERTAIN›〕 |
| graph_indexed | TINYINT DEFAULT 0（INDEX idx_graph_indexed） | 图谱提取状态：0=未提取/待重提取，1=已提取，2=提取失败待增量重试〔KbChunk.java:64-65 注释 ‹CERTAIN›；=2 的写入方在 knowledge-graph 链〕 |
| extraction_result | JSON DEFAULT NULL | 图谱提取结果缓存（graph_indexed=1 时非空，replay 零成本重放）；编辑/重切时置 null |
| region / publish_date / doc_level | VARCHAR(64) / DATE / VARCHAR(16)（三列各 INDEX） | 文件级元数据冗余列（分册四），落库时从 kb_file 回填，检索过滤免 JOIN〔StorageServiceImpl.java:259-264、schema.sql:176-178,1417-1420 ‹CERTAIN›〕 |

> 实体类 KbChunk.java:33-68 与 DDL 一致（grep 比对）；created_at/updated_at **本表无**（分片无时间戳审计列，排序只依赖 chunk_index）〔schema.sql:155-186 ‹CERTAIN›〕。

Lifecycle：
- **产生路 1（摄取管线，origin=auto/parent）**：IngestionConsumer 分发算法 → ChunkStoreService.storeChunks →（parent_child 时先 ParentChunkAssembler 聚合）→ 批插 MySQL + Milvus。〔IngestionConsumer.java:447-456、StorageServiceImpl.java:64-174 ‹CERTAIN›〕
- **产生路 2（手工创建，origin=manual）**：ChunkLifecycleService.create → ID 段 `{fileId}_manual_{n}` → 单条插 MySQL + 单条写 Milvus。〔ChunkServiceImpl.java:122-195 ‹CERTAIN›〕
- **替换路（重切/AI 应用）**：删 origin≠manual 行 + 按 embeddingId 删向量 → 管线重跑产生新 auto 分片 → 对账（manual index 重排至 auto 后、chunk_count=auto+manual）。〔FileServiceImpl.java:421-482、IngestionConsumer.java:753-782 ‹CERTAIN›〕
- **编辑**：update 部分字段；content 变更 → 向量删旧插新 + 父分片重拼 + graph_indexed=0/extraction_result=null + 增量图谱消息。〔ChunkServiceImpl.java:198-273 ‹CERTAIN›〕
- **删除**：单删（父分片级联子分片 + 索引前移）/批删（跨库 id 忽略、父级联、无索引重排）/文件删除链（file-ingestion）/KB 删除链（kb-core）。〔ChunkServiceImpl.java:276-388 ‹CERTAIN›〕

Relationships：N—1 kb（经 kb_id，无外键）；N—1 kb_file（经 file_id，无外键——文件软删期间分片仍在，永久删除时五路清理）；自引用父子（parent_id → id，同表两级，无外键）；kb_chunk 行 ≠ 向量库行的一一对应由 embedding_id + vector_stored 维护（弱一致，降级时 vector_stored=0）。

### 分片 ID 段约定（运行时结构，无表）

| ID 模式 | 产生方 | 语义 |
|---|---|---|
| `{fileId}_chunk_{index}` | 摄取管线 auto 分片〔StorageServiceImpl.java:239 ‹CERTAIN›〕 | index=全局批内位置；**重切后从 0 重新编号**，旧 id 消失 |
| `{fileId}_manual_{n}` | 手工创建〔ChunkServiceImpl.java:144-147 ‹CERTAIN›〕 | n=文件内 manual 序号（selectMaxManualSeq+1），跨重分片稳定 |
| `{fileId}_parent_{n}` | 父子装配〔ParentChunkAssembler.java:152 ‹CERTAIN›〕 | n=文件内父分片序号；随 auto 重切一并重建 |
| 索引位移连锁 | insertAfterIndex 创建 / 单删 | auto 分片 id 内嵌 index → 位移=删行重插+向量删旧重嵌+图谱 renameChunkId；manual 仅移位（ADR-CM1，design §6）〔ChunkServiceImpl.java:395-447 ‹CERTAIN›〕 |

### Milvus collection 约定（运行时结构，无表）

- 命名 `kb_{kbId}`（连字符转下划线），一库一集合，幂等创建。〔ChunkServiceImpl.java:172、StorageServiceImpl.java:76、MilvusService.java:120-131 ‹CERTAIN›〕
- Schema 五字段：id（VarChar PK 128）/ vector（FloatVector，维度=kb.dimension 缺省 1024）/ kb_id / file_id / chunk_index（Int64）；索引 IVF_FLAT + COSINE（nlist=128）。〔MilvusService.java:133-168,181-187、ChunkServiceImpl.java:482-485 ‹CERTAIN›〕
- 删除原语：deleteById / deleteByIds（按分片 id 表达式）/ deleteByFileId（按 file_id——重切**不**用它，避免误删保留分片向量）。〔MilvusService.java:290-360、FileServiceImpl.java:421-431 ‹CERTAIN›〕

**Parent Trace:** kb_chunk → chunk-management.ChunkStoreService（摄取写入）/ ChunkLifecycleService（手工维护）/ RechunkOrchestrator（替换对账）→ Design.ChunkStoreModule/ChunkLifecycleModule/RechunkOrchestrator → Spec REQ-B01~B06、E01~E05、F01~F06、G01~G05；ID 段约定 → ChunkLifecycleService → REQ-B02、G03；Milvus 约定 → ChunkStoreService/ChunkLifecycleService → REQ-G01~G02。

---

## onlyoffice-editing

> 覆盖：本特性**无自有数据表**——`init-scripts/schema.sql` 与 SchemaInitializer 均无 onlyoffice 痕迹（grep 实测 ‹CERTAIN›），一切持久化复用上游表与对象存储：kb_file 的 name/extension/size/object_key/status/parse_strategy_id/updated_at 字段（语义归 `## file-ingestion` 节，本特性不重复定义）与 MinIO 对象 `{kbId}/{uuid}`（原文件，replaceOriginalFile 原键覆盖）、`{kbId}/{fileId}/parsed.md`（Markdown 重落盘，写方 MarkdownService.persistParsedMarkdown〔MarkdownService.java:61 ‹CERTAIN›〕，键约定已登记于 `## file-ingestion` 节）。本节登记四类运行时结构与配置面。

### 活跃编辑会话表（运行时结构，进程内存）

`OnlyOfficeServiceImpl.activeSessionKeys: ConcurrentHashMap<String fileId, String documentKey>`〔OnlyOfficeServiceImpl.java:126 ‹CERTAIN›〕

| 操作 | 时机 | 语义 |
|---|---|---|
| put(fileId, key) | buildEditorConfig 每次签发时覆盖写〔:179 ‹CERTAIN›〕 | 会话开立登记；同文件重复开 config 以最新 key 为准 |
| get(fileId) | forceSave 定位会话 key〔:396 ‹CERTAIN›〕；handleCallback 兼容「同会话二次保存」〔:292-295 ‹CERTAIN›〕 | 消费端；无删除路径 |
| 逐出 | **无**（无 TTL、无清理）〔结构观测 ‹CERTAIN›〕 | 条目随文件数缓慢累积；多实例部署下各节点视图不一致〔决议 2026-09-24：按现状为准——ConcurrentHashMap 进程内语义为结构事实，多实例分歧为其必然推论；单实例部署无影响，已登记 spec NFR-4（代裁，用户可推翻）〕 |

### documentKey 派生规则（运行时约定，无存储）

`documentKey = SHA-256(UTF8(fileId) ⊕ UTF8(updatedAt.toString())) 的前 32 个十六进制字符`；updatedAt 为 null 时不掺入该字节序列；MessageDigest 异常退化为 `fileId + "_" + System.currentTimeMillis()`（长度不定）。〔OnlyOfficeServiceImpl.java:145-162 ‹CERTAIN›〕

- 稳定性：同 (fileId, updatedAt) 恒同 key——未保存的重复开 config 不触发 OO reload。
- 变化性：replaceOriginalFile 每次保存写 `updatedAt = now()`〔FileServiceImpl.java:309 ‹CERTAIN›〕 ⇒ key 必变 ⇒ 下次开 config 携新 key，Document Server 弃缓存重载新文件。
- 双 key 准入：callback 的 key 匹配「当前 updatedAt 派生 key」（保存后的新事实）或「登记会话 key」（保存前派生、会话内不变）二者之一；**key=null 容忍通过**〔OnlyOfficeServiceImpl.java:291-295 ‹CERTAIN›，宽松面债务〕。

### OO 回调 payload（inbound 结构，仅接口层）

`OnlyOfficeCallbackPayload`（`@JsonIgnoreProperties(ignoreUnknown=true)`，未知字段静默忽略）〔OnlyOfficeCallbackPayload.java:38-77 ‹CERTAIN›〕：

| 字段 | 类型 | 消费语义 |
|---|---|---|
| status | Integer（1/2/3/4/6/7） | 分发主键：1/4 ack、3/7 拒绝、2/6 保存编排、未知容忍〔OnlyOfficeServiceImpl.java:242-255,352-355 ‹CERTAIN›〕 |
| url | String | status=2/6 时 OO 临时存放的已保存文件下载 URL（短时有效〔决议 2026-09-24：采信实现注释 5-10 分钟口径——唯一来源为 OnlyOfficeServiceImpl.java:359-362 注释 ‹CERTAIN›，无实测；实际有效期由 OO Document Server 侧配置决定（代裁，用户可推翻）〕） |
| key | String | 与 config.document.key 一致的文档身份（准入因子之二） |
| token | String | OO 同密钥签名的回调 token（jwtEnabled 时验签 + claim 比对；jwtEnabled=false 时若携带则记日志忽略）〔:262-279 ‹CERTAIN›〕 |
| type / title / users / history / actionType / actions / userdata | String/List/Map | **仅声明接收，当前零消费**〔决议 2026-09-24：v1 维持仅接收不消费——OO 协作者/版本协作功能未排期；字段与接收面保留（防上游 400），消费立项走 IDD（代裁，用户可推翻）〕〔:51-76 ‹CERTAIN›〕 |

### OO 编辑器 config（outbound 结构，仅接口层）

buildEditorConfig 输出 Map 的全字段面见 interfaces.md `## onlyoffice-editing` 节 EditorSessionService 的 Output（此处不重复）；其序列化产物由前端直传 `DocsAPI.DocEditor`，jwtEnabled=true 时整包经 OoTrustToken.signPayload 签名后挂 `token` 字段。〔OnlyOfficeServiceImpl.java:210-222 ‹CERTAIN›〕

### 配置面（application.yml ↔ docker-compose 环境变量映射）

| 配置键 | 环境变量 | 缺省 | 语义 |
|---|---|---|---|
| onlyoffice.enabled | ONLYOFFICE_ENABLED | yml 缺省 true（本地）；docker-compose 未注入即生效 | 总开关；false → 503 业务错误 + isSupportedFile=false〔application.yml:153-154、OnlyOfficeController.java:61-62 ‹CERTAIN›〕 |
| onlyoffice.jwt-enabled | ONLYOFFICE_JWT_ENABLED | **false（开发现状）**，生产须 true | 与 OO 容器 token.enable 双端同步；false 时 config 不签名/callback 跳过 token 校验（document.key 校验保留）〔application.yml:164-167、docker-compose.yml:188-189 JWT_ENABLED=false ‹CERTAIN›〕 |
| onlyoffice.jwt-secret | ONLYOFFICE_JWT_SECRET | fastrag-onlyoffice-dev-secret-change-me | 与容器 JWT_SECRET 同源；生产必换〔application.yml:162-163、docker-compose.yml:187、OnlyOfficeJwtUtil.java:48-49 ‹CERTAIN›〕 |
| onlyoffice.url / internal-url | ONLYOFFICE_URL / ONLYOFFICE_INTERNAL_URL | http://localhost:8082 | 浏览器/FastRAG → OO（容器 80→宿主 8082）；internal-url 供 CommandService 命令〔application.yml:155-158、docker-compose.yml:181-182 ‹CERTAIN›〕 |
| onlyoffice.command-path | ONLYOFFICE_COMMAND_PATH | /coauthoring/CommandService.ashx | forcesave 命令端点（≥8.3 可切 /command）〔application.yml:159-161 ‹CERTAIN›〕 |
| onlyoffice.callback-base-url / storage-base-url | ONLYOFFICE_CALLBACK_BASE_URL / ONLYOFFICE_STORAGE_BASE_URL | http://host.docker.internal:8081 | OO 容器 → 宿主后端的回访基址（回调写入/原文件拉取）；Linux 需 extra_hosts host-gateway〔application.yml:168-171、docker-compose.yml:210-213 ‹CERTAIN›〕 |
| （容器侧）SECURE_LINK_SECRET | — | 硬编码于 compose | OO nginx 防盗链密钥（/cache/files 转换缓存拉取 403 防线），与挂载 local.json 同步〔docker-compose.yml:190-193 ‹CERTAIN›〕；`[GAP: 生产环境该密钥的轮换来源 — compose 内硬编码，代码外信息]` |
| （容器侧）插件挂载 | — | fastrag-chunk-boundary-v6 | 前端仓选区桥插件经 sdkjs-plugins 目录由 Document Server 托管〔docker-compose.yml:203-209 ‹CERTAIN›〕——前端实现不在本仓，后端零消费 |

**Parent Trace:** activeSessionKeys/documentKey → onlyoffice-editing.EditorSessionService（登记）/ CallbackSaveOrchestrator（双 key 准入）/ ForceSaveCommander（定位）→ Design.EditorSessionModule/CallbackOrchestrator/ForceSaveCommander → Spec REQ-A05、A06、D07、E04、C02；回调 payload → CallbackSaveOrchestrator → REQ-D02、E04；config 结构 → EditorSessionService → REQ-A03；配置面 → OoTrustTokenModule + 缝 1/缝 2 → REQ-E01~E03。

---

## ai-chunking

> 覆盖：本特性**无自有数据表**——`init-scripts/schema.sql` 与 SchemaInitializer 均无 ai-chunk 痕迹（grep 实测 ‹CERTAIN›）。落库复用 kb_chunk（数据契约归 `## chunk-management` 节；本链 ApplyOrchestrator 是其「替换路」第二写入方，id 段 `{fileId}_chunk_{i}` 与该节 ID 段约定一致）。本节登记四类运行时结构：**段落对齐模型**（本链数据核心——预览/分片/应用三阶段的单一事实源，CONTEXT.md 词条）、应用请求/预览结果载荷、SSE 事件面、MinIO 派生对象键。

### 段落对齐模型（运行时结构，`AiChunkParagraph`，无表）

模型不落 MySQL：随预览/流式响应返回前端，apply 时经 paragraphIds 映射写入 parsed.json 审计（MinIO）。〔AiChunkParagraph.java:9-13、AiChunkServiceImpl.java:1994-2012 ‹CERTAIN›〕

| 字段 | 类型 | 说明与约束 |
|---|---|---|
| id | string | 稳定唯一，`p_%04d` 按文档序编号；跨页合并后全序列重排；分片卡经 paragraphIds 引用它〔AiChunkServiceImpl.java:980-983,1044-1047 ‹CERTAIN›〕 |
| order | int | 文档顺序（1-based，与 id 编号同步） |
| type | string | paragraph/heading/table/code/list/image（paragraph 建模型；caption 仅布局层）〔AiChunkParagraph.java:24、AiChunkServiceImpl.java:918-937 ‹CERTAIN›〕 |
| text | string | 段落文本：table 为制表符分隔（首行表头）；code 为代码；heading 为标题行〔AiChunkParagraph.java:27-28 ‹CERTAIN›〕 |
| imageKey | string 可空 | 图片段落的 MinIO 键（`{kbId}/{fileId}/images/` 下相对键）〔AiChunkParagraph.java:30-31 ‹CERTAIN›〕 |
| rects | RectItem[] 可空 | PDF 文字块坐标：{page 1-based, x/y/width/height 归一化 0~1 顶左原点}；跨页合并块每页一个盒；归一化除数=cropBox〔AiChunkParagraph.java:33-45、AiChunkServiceImpl.java:1652-1688 ‹CERTAIN›〕 |
| page / pages / pageRange | int / int[] / string | 首次出现页 / 全部出现页（跨页合并块多页，升序去重）/ 展示串（"15-16" 或 "15"）〔AiChunkParagraph.java:47-54、AiChunkServiceImpl.java:1048-1054 ‹CERTAIN›〕 |
| headingPath | string | 章节层级路径（标题栈拼接，" > " 连接；纯文本回退路径为空串）〔AiChunkServiceImpl.java:912-919,993-1000,970 ‹CERTAIN›〕 |
| anchorText | string 可空 | PDF 锚定行文本（页眉脚已清理的所见即所得内容源；未锚定=null；手动分片优先内容源）〔AiChunkParagraph.java:59-64、AiChunkServiceImpl.java:1690-1699 ‹CERTAIN›〕 |
| chunkId | string 可空 | 所属分片 id（自动分片后回填；_sN/_oN 衍生片回填基段，取最后映射）〔AiChunkParagraph.java:66-67、AiChunkServiceImpl.java:1562-1576 ‹CERTAIN›〕 |
| crossPage / edited | boolean | 跨页合并标记（pages.size()>1 派生）/ 人工编辑标记（模型声明，写入方在前端态，后端不消费〔grep 实测 ‹CERTAIN›〕）〔AiChunkParagraph.java:69-73 ‹CERTAIN›〕 |

**衍生约束**：id 跨页合并后重排 ⇒ 预览会话内稳定、跨会话不保证（apply 审计以当次响应的 paragraphIds 为准）；二段式衍生 id `_sN`（二次切分片）/`_oN`（overlap 尾片）仅存在于分组中间态，回填后基段 chunkId 取最后映射〔AiChunkServiceImpl.java:1567-1572 ‹CERTAIN›〕。

### 应用请求与预览结果（inbound/outbound 载荷，无表）

**AiChunkApplyRequest**（apply 入参）〔AiChunkApplyRequest.java:14-28 ‹CERTAIN›〕：

| 字段 | 类型 | 约束 |
|---|---|---|
| chunks | Item[] | 必填非空；请求序=落库序（id=`{fileId}_chunk_{i}` 从 0 编号） |
| chunks[].text | string | 必填非空白——最终文本（用户编辑后），后端不校验与段落模型的一致性 |
| chunks[].paragraphIds | string[] 可空 | 覆盖段落 id（parsed.json 审计追溯） |
| chunks[].source | string 可空 | auto/manual，缺省 auto（仅审计） |
| chunks[].pageRange | string 可空 | 页码范围展示串 |

**AiChunkResult**（preview 响应/SSE done 汇总）〔AiChunkResult.java:15-49 ‹CERTAIN›〕：fileId/fileName/extension/kbId、document{type, pages=max(解析页数，段落最大页), mergedBlocks=跨页合并块数}、paragraphs（段落对齐模型）、imageBoxes（PDF 内容图片位置盒，旁路）、layoutBlocks（版面缓存命中时随返回）、chunks（AiChunkGroup[]：id/index/source/paragraphIds/text/pageRange）、chunkSource（llm/embedding/rule 降级路径标注）。

### SSE 事件面（边界协议，两条流）

**stream 流**（GET `/ai-chunk/stream`，超时 600s）〔AiChunkServiceImpl.java:180-240 ‹CERTAIN›〕：

| 事件 | data | 语义 |
|---|---|---|
| progress | {percent, stage, message?} | 里程碑：8=parse → 35=merge → 55=llm-chunk |
| fragment | {page, paragraphId, type, text≤400 截断, pageRange, crossPage} | 左侧原件片段；跨页合并块每页各推一个（共享 paragraphId） |
| chunk | AiChunkGroup | 右侧分片卡逐个推送 |
| done | {pages, mergedBlocks, chunks, chunkSource} | 收尾汇总 |
| error | {message} | 任何异常（消息缺省「AI分片 解析失败」） |

**layout 流**（GET `/ai-chunk/layout`，超时 600s）〔:576-734 ‹CERTAIN›〕：缓存命中 → 一次 layout（全量数组）+ done{cached:true, blocks}；非 PDF → done{unsupported:true}；正常 → layout（逐页数组，并发线程经 emitter synchronized 发送）+ progress{percent, stage:"layout", message:"n/total"} + done{pages, blocks}；异常 → error{message}。

### MinIO 派生对象键（运行时结构，无表；遵循 `{kbId}/{fileId}/` 前缀约定，见 `## file-ingestion` 节）

| 键模式 | 内容 | 写方/读方 |
|---|---|---|
| `{kbId}/{fileId}/layout.json` | 版面分析缓存 {v: 引擎版本(当前 3), blocks: AiChunkLayoutBlock[]}；全部页成功才写，版本不符重算 | LayoutAnalysisOrchestrator 写/读〔AiChunkServiceImpl.java:712-714,749-764 ‹CERTAIN›〕 |
| `{kbId}/{fileId}/parsed.json` | apply 审计快照：{fileId, kbId, fileName, appliedAt, chunks:[{id,index,source,pageRange?,paragraphIds[]}]}；写失败降级 | ApplyOrchestrator 写〔:1994-2012,2013-2015 ‹CERTAIN›〕 |
| `{kbId}/{fileId}/parsed.md` | 分片文本按序 "\n\n" 拼接（向后兼容 GET /markdown 读面）；**第二写方**（首写方为摄取管线 MarkdownService，键约定已登记 `## file-ingestion` 节） | ApplyOrchestrator 写〔:1988-1992 ‹CERTAIN›〕 |
| `{kbId}/{fileId}/images/region_p{page}_{ts}_{seq}.png` | 区域框选裁剪图（内容图原图 / OCR 全空时的区域截图）；上传失败仅缺图 | RegionExtractor 写〔:387,444-446,490-502 ‹CERTAIN›〕 |

**Relationships**：段落 1—N rects（每出现页一个盒）；段落 N—N AiChunkGroup（经 group.paragraphIds / paragraph.chunkId 双向，分组中间态衍生片 _sN/_oN 归并回基段）；apply Item 1—1 kb_chunk 行（序号 i 即 chunk_index 与 id 后缀）；layout.json/parsed.json 无表、对象存储自描述（v 版本号 / appliedAt 时间戳）。

**Parent Trace:** 段落对齐模型 → ai-chunking.ParagraphModelAssembler → Design.ParagraphModelAssembler → Spec REQ-B01~B05；请求/结果载荷 → ApplyOrchestrator/AiChunkWorkflowModule → REQ-E04、F01~F02；SSE 事件面 → AiChunkWorkflowModule → REQ-F01~F03；MinIO 派生键 → LayoutAnalysisOrchestrator（layout.json）/ApplyOrchestrator（parsed.*）/RegionExtractor（images）→ REQ-D05、E05、D03。

---

## retrieval

> 覆盖：本特性两张自有数据表（kb_retrieval_log / kb_update_remind，DDL 双轨一致：schema.sql:739-763 与实体类比对无漂移 ‹CERTAIN›）+ 两类运行时结构（检索配置载荷、检索结果条目）。消费面登记：Milvus collection 消费遵守 `## chunk-management` 节约定（不重复定义）；kb_log（category=retrieval）与 kb_update_log 的字段契约归各自归属链（publish/operation 域），本节只登记写/读口径；term_record.status 与 query_rule 的消费点在 `## platform-config` 链登记表结构，本节登记检索侧消费行为。

### kb_retrieval_log — 检索日志（效果指标）

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT PK | 数据库自增 |
| kb_id | VARCHAR(32)（INDEX idx_kb_time 联合 (kb_id, created_at)） | 归属知识库；组织隔离过滤键 |
| query | VARCHAR(512) | 用户原始查询文本（改写变体不入日志，REQ-D07） |
| user_id | VARCHAR(32) | 发起检索的用户 ID（SecurityUtil 当前身份） |
| hit_count | INT DEFAULT 0 | 本次命中结果条数 |
| top_score | DECIMAL(5,2) | 最高相似度分；**当前写方恒空**——recordLog 未赋值（RetrievalServiceImpl.java:1747-1756 无 setTopScore ‹CERTAIN›）〔决议 2026-09-24：按现状为准——预留列无写入方；保留不删（存量库兼容），效果分析需求立项时接线（代裁，用户可推翻）〕 |
| latency_ms | INT | 检索耗时毫秒 |
| has_result | TINYINT DEFAULT 1 | 是否有结果（无结果率统计键） |
| graph_entity_count | INT DEFAULT 0 | 图谱通道命中结果数（source='graph' 计数） |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 检索时间 |

Lifecycle：**产生** = 每次 search 编排完成（含空结果）由 RetrievalSearchEngine.recordLog 写入；**消费** = page 分页 / analysis 三条聚合 SQL（总数/无结果/延迟/命中均值 + 热门查询 Top10 + 无结果查询 Top10，KbRetrievalLogMapper.java:31-62 ‹CERTAIN›）；**外部写入路** = POST/PUT 端点直通 log/update（无权限校验，债务见 interfaces.md 本节）；**清理** = 无（审计记录长期保留，无 TTL/归档任务 [GAP: 日志保留期限与归档策略 — 代码无证据]）。

Relationships：N—1 kb（经 kb_id，无外键）；与 kb_log（category=retrieval）1—1 双写但无外键关联、两行独立存活（一侧写失败不影响另一侧）〔RetrievalServiceImpl.java:1738-1783 ‹CERTAIN›〕。

### kb_update_remind — 更新提醒配置（每库一条语义，无唯一约束）

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK（ASSIGN_ID 雪花） | 应用生成 |
| kb_id | VARCHAR(32) NOT NULL（INDEX idx_kb_id） | 归属知识库；**代码按 kbId 取首条（LIMIT 1）但 DDL 无唯一约束**——重复行可能并存，get 只认首条（UpdateRemindServiceImpl.java:36-38 ‹CERTAIN›） |
| enabled | TINYINT DEFAULT 1 | 1=启用 0=禁用；新增缺省补 1 |
| cron_expr | VARCHAR(64) DEFAULT '0 9 * * *' | cron 表达式；新增缺省补默认值；**仅存储回显、无调度器消费**（债务，REQ-F04） |
| channels | JSON | 提醒渠道配置；**当前零消费**〔grep 实测 ‹CERTAIN›；〔决议 2026-09-24：渠道枚举取 `inbox,mail`（站内信 + 邮件，与 sys_notification 通知中心对齐）——代裁默认值；现状零消费且无调度器（既有债务），消费立项时以本枚举为准（用户可推翻）〕〕 |
| last_remind_at | DATETIME | 上次提醒时间；remind() 触发后刷新为 now，是增量计数的基点 |
| created_at / updated_at | DATETIME（自动维护） | 创建/更新时间 |

Lifecycle：**产生/更新** = save（id 空插、非空改）；**删除** = delete 按 id；**消费** = remind 触发读配置 → 统计 kb_update_log 增量 → 回写 last_remind_at。Relationships：N—1 kb（经 kb_id，无外键）；读取关联 kb_update_log（经 kb_id + timestamp > last_remind_at 计数，无外键）〔UpdateRemindServiceImpl.java:57-70 ‹CERTAIN›〕。

### 检索配置载荷（运行时结构，`RetrievalConfig`，无表）

存储于 `kb.retrieval_config` JSON 列（字段契约归 `## kb-core` 节），反序列化为 RetrievalConfig；全部字段包装类型可空——「未传 = 不覆盖」是三级合并协议的数据前提〔RetrievalRequest.java:29-48 注释 ‹CERTAIN›〕。例外两处原语类型默认值：graphExpandDepth=1、graphMaxEntities=10（DTO 字段非包装，RetrievalRequest.java:41-42 ‹CERTAIN›）——mergeConfig 对应字段以 >0 判定规避〔INFERRED：由 mergeConfig :379-380 的 >0 防御推断是对该默认值的补偿〕。字段全集见 interfaces.md `## retrieval` 节 RetrievalSearchEngine Input，不重复。

### 检索结果条目（运行时结构，`SearchResultItem`，无表，outbound）

统一五来源（source: mysql/milvus/mysql_fulltext/graph/qa）与七通道（channel: vector/fulltext/graph/qa/clause/clause_neighbor/title）的结果表示；身份键 = fileId+chunkIndex（融合/去重/QA 排他键）；上下文放大后 content 为父块/窗口/整文档内容、chunkIndex 保留命中子片位置、chunkType 置 parent〔SearchResultItem.java:27-42、RetrievalServiceImpl.java:1354-1374 ‹CERTAIN›〕。highlights 字段声明但当前无写方〔grep 实测 ‹CERTAIN›〕。

### 消费点登记（跨域数据，消费行为归本链）

| 外部数据 | 消费口径 | 位置 |
|---|---|---|
| Milvus collection `kb_{kbId 下划线}` | 只读 search（候选 topK×3 → MySQL 回表）；schema/索引约定归 `## chunk-management` 节 | RetrievalServiceImpl.java:577-578 ‹CERTAIN› |
| term_record.status | 同义扩展仅消费 status=1 词条（禁用词条不参与扩展）；进程内缓存 enabledCache 首查回源〔TermServiceImpl.java:27-34 ‹CERTAIN›〕——缓存失效〔决议 2026-09-24 R3 实证：`invalidate()`（volatile 置 null）在全部写路径调用（TermServiceImpl.java:68,88,107,117 ‹CERTAIN›），**单实例词条变更即时生效**；多实例其他节点 stale 直至重启（volatile 单 JVM 语义，多实例债务已登记）〕 | QueryEnhanceServiceImpl.java:79 |
| query_rule 表 | **零消费**——applyQueryRules 占位返回原查询，字段契约断裂债务（REQ-D04） | QueryEnhanceServiceImpl.java:91-97 ‹CERTAIN› |
| sys_config（general_search_top_k / general_retrieval_mode / general_enable_rerank） | 系统级默认值第三优先级来源；键缺失/解析失败回退硬编码默认 | RetrievalServiceImpl.java:318-350 ‹CERTAIN› |
| model 表 | 嵌入/重排/NER/Multi-Query 四类模型的 apiUrl/apiKeyRef 解析（code + status=online，LIMIT 1）；查不到回退默认网关 | RetrievalServiceImpl.java:1150-1161,1582-1591、QueryEnhanceServiceImpl.java:125-138,222-234 ‹CERTAIN› |
| kb_log（category=retrieval） | 检索审计行写方：extra 七字段 mode/topK/hits/duration/graphExpanded/graphHits/graphChannel；status=success|degraded | RetrievalServiceImpl.java:1758-1779 ‹CERTAIN› |
| kb_update_log | 更新提醒计数读方（kb_id + timestamp 窗口） | UpdateRemindServiceImpl.java:57-70 ‹CERTAIN› |

**Parent Trace:** kb_retrieval_log → retrieval.RetrievalLogModule → Design.RetrievalLogModule → Spec REQ-E01~E05；kb_update_remind → retrieval.UpdateRemindModule → REQ-F01~F04；检索配置载荷 → RetrievalSearchEngine（缝 5）→ REQ-A03~A06；结果条目 → RetrievalSearchEngine → REQ-B08、C09；消费点登记 → QueryEnhancePipeline（缝 3）/RetrievalSearchEngine（缝 1/2）→ REQ-B02、B10、D03、D04。

---

## knowledge-graph

> 覆盖：MySQL 侧 4 张 kb_graph_* 表（构建索引状态 + 抽取单元缓存 + 缝合输出 + 合并审计）+ Neo4j 图谱本体（实体/关系/证据链的真实存储，ADR-0002）+ 两类运行时结构（抽取单元、构建消息）。
> **表数仲裁（CERTAIN）**：feature-map #10 与链任务书均记「kb_graph_* 11 张」，实测 `init-scripts/schema.sql` 仅 **4 张**（kb_graph_index :219-232、kb_graph_stitch :1432-1443、kb_graph_unit :1445-1459、kb_graph_merge_audit :1461-1472）；MySQL 图表四张（entity/relation/entity_mention/triple_mention）已按 ADR-0002 移除（schema.sql:235-236 注释 + migration-20260905-drop-mysql-graph-store.sql）。两处 DDL（schema.sql ↔ SchemaInitializer.java:302-370）逐列比对一致，唯 **`settings` 列仅存于全量脚本 ALTER（schema.sql:1423），SchemaInitializer 不补**（仅补 failed_chunks，:303）——存量库升级若缺该列，saveSettings 将失败，属登记债务（同 kb_chunk idx_content_fulltext 类）。
> **死代码警示（CERTAIN）**：6 个 MySQL 图表实体/mapper（KbGraphEntity/KbGraphRelation/KbGraphEntityMention/KbGraphTripleMention/KbGraphStitch/KbGraphMergeAudit 及对应 Mapper）三件齐备但**全仓无任何 Service/Consumer 消费**（grep 实测）；前四张表 DDL 已删（数据真身在 Neo4j），后两张表 DDL 在但无写入方（缝合 pass 于 v1.2 移除、合并审计未接线）。实体类保留仅作结构文档。

### kb_graph_index — 图谱构建索引状态（每库一条）

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| kb_id | VARCHAR(32) PK | 主键即知识库 ID（ASSIGN_ID 但代码显式 setKbId）〔KbGraphIndex.java:151、GraphServiceImpl.java:250 ‹CERTAIN›〕 |
| status | VARCHAR(16) DEFAULT 'idle' | idle/building/completed/failed 四态；stats 实时回退有数时自动修复为 completed〔GraphServiceImpl.java:171-173 ‹CERTAIN›〕 |
| build_progress | INT DEFAULT 0 | 0-100，按单元计数推进（每 5 单元刷新）〔GraphBuildConsumer.java:538-542,1237-1259 ‹CERTAIN›〕 |
| entity_extract_progress / relation_extract_progress | INT DEFAULT 0 | **已废弃列**，实体标 @Deprecated 无写入方〔KbGraphIndex.java:154-159 ‹CERTAIN›〕 |
| total_chunks / built_chunks / failed_chunks | INT DEFAULT 0 | KB 全量分片口径 / graph_indexed=1 数 / 失败单元数；进度更新不覆盖计数（完成时才以 live 值刷新）〔GraphBuildConsumer.java:1237-1253 ‹CERTAIN›〕 |
| entity_count / relation_count | INT DEFAULT 0 | stats 缓存优先读、实时回退自动修复、删除/合并后同步刷新〔GraphServiceImpl.java:157-177,403-437,459-471 ‹CERTAIN›〕 |
| index_version | INT DEFAULT 0 | 无写入方（预留）〔grep 实测 ‹CERTAIN›〕 |
| last_built_at | DATETIME | 〔决议 2026-09-24：按现状为准——预留字段：构建完成路径（GraphBuildConsumer.updateGraphStatus:1202-1231）不回写，全链唯一写方为删除清空置 null（GraphServiceImpl.java:419），/graph/index 返回的 lastBuiltAt 因此恒 null。若需「最后构建时间」语义，应在 updateGraphStatus 的 completed 分支补写（代裁，用户可推翻）；已登记见 implement/knowledge-graph.md §GraphManagementModule〕 |
| build_error | TEXT | 失败原因（LLM 未配置等可定位文案）〔GraphBuildConsumer.java:270-273 ‹CERTAIN›〕 |
| settings | TEXT（仅 schema.sql:1423） | JSON：maxNodes/searchDepth/excludeChunkNodes/entitySchema〔GraphServiceImpl.java:337-343 ‹CERTAIN›〕；SchemaInitializer 不补该列（漂移债务，见节首） |

Lifecycle：buildIndex/retryBuild 置 building → 进度推进 → completed/failed；文件图谱删除后图谱为空则整体重置 idle（计数/错误/时间全清零）〔GraphServiceImpl.java:410-419 ‹CERTAIN›〕。
Relationships：1—1 kb（经主键，无外键）；其 entitySchema 设置被构建白名单解析消费（GraphBuildConsumer.java:1160-1181）。

### kb_graph_unit — 图谱抽取单元（单元级缓存与 replay 载体）

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT PK | 数据库自增 |
| kb_id | VARCHAR(64) NOT NULL（联合唯一 uk_unit 首列） | 归属知识库 |
| file_id | VARCHAR(64) NOT NULL（uk_unit 第二列） | 归属文件 |
| unit_index | INT NOT NULL（uk_unit 第三列） | 单元在文件内按 chunkIndex 重组后的序号；唯一键 = (kb_id, file_id, unit_index)〔schema.sql:1458 ‹CERTAIN›〕 |
| content_hash | VARCHAR(64) NOT NULL（INDEX idx_unit_hash 联合 kb_id,file_id,content_hash） | 单元内容 SHA-256 截断哈希——缓存键：与本轮一致且 status=1 且结果非空则零 LLM 跳过〔GraphBuildConsumer.java:492-505 ‹CERTAIN›〕 |
| chunk_ids | TEXT NOT NULL | 组成单元的分片 ID JSON 数组（证据归属与状态回写依据）〔:658 ‹CERTAIN›〕 |
| extraction_result | MEDIUMTEXT | 抽取结果 JSON（ExtractionNormalizer.ExtractionResult 结构：entities[{text,label,description,attributes[]}]+relations[{source,target,label}]+metadata）；replay 零 LLM 重放的载体〔KbGraphUnit.java:34-35、GraphBuildConsumer.java:1342-1354 ‹CERTAIN›〕 |
| status | TINYINT NOT NULL DEFAULT 0 | 0=待抽取 1=已完成 2=失败〔:127-130、upsertUnitRow :656-679 ‹CERTAIN›〕 |
| created_at / updated_at | DATETIME DEFAULT CURRENT_TIMESTAMP / ON UPDATE | 创建/更新时间（代码侧显式 set）〔:665,676 ‹CERTAIN›〕 |

Lifecycle：构建时按文件 upsert（缓存命中不重写）→ 完成（status=1+结果持久化）/失败（status=2+结果 null）→ 文件分片变少导致单元数缩水时越界旧行物理删除〔loadUnitRows GraphBuildConsumer.java:447-460 ‹CERTAIN›〕。无删除端点；文件删除/重切后旧行残留至下次构建被越界清理或覆盖。
Relationships：N—1 kb/kb_file（经 kb_id/file_id，无外键）；N—N kb_chunk（经 chunk_ids JSON 引用，无外键——分片删除后引用悬空由越界清理兜底）；extraction_result 与 kb_chunk.extraction_result 列无关（后者为历史逐片缓存遗留列，现行写入方为空操作）。

### kb_graph_stitch — 图谱缝合输出与合并候选（**无写入方警示**）

> **休眠表（CERTAIN）**：DDL（schema.sql:1432-1443 + SchemaInitializer:313-326）、实体（KbGraphStitch.java:66-80）、mapper 三件齐备，但 v1.2 移除缝合 pass 后全仓无任何读写方（grep 实测）。字段语义按实体注释与 DDL 注释冻结如下，供未来重建缝合 pass 时对齐。〔决议 2026-09-24：缝合 pass 维持移除、不设重建时间表；kb_graph_stitch 表 DDL/实体/mapper 与 GraphStore 缝合原语（REQ-E01~E03）冻结保留不删；status 流转沿用实体注释语义（0 待应用/1 已应用可重放/2 跳过）。理由：v1.2 大抽取单元已天然覆盖原缝合职责（spec §4.5），重建属投机性投入，待大库出现跨文件关系缺口实证再评估。现状差距：无（有意休眠非缺陷，休眠警示即登记）（代裁，用户可推翻）〕

id BIGINT AUTO_INCREMENT PK；kb_id VARCHAR(64) NOT NULL；file_id VARCHAR(64)；stitch_type VARCHAR(32) NOT NULL（relation=缝合关系 / merge=合并候选）；payload TEXT NOT NULL（JSON 载荷：relation 为三元组+归属分片、merge 为 canonical/alias）；status TINYINT NOT NULL DEFAULT 0（0=待应用人工候选 / 1=已应用可 replay / 2=跳过）；chunk_source VARCHAR(64)（关系首现分片）；created_at。INDEX idx_stitch(kb_id,file_id,status)。

### kb_graph_merge_audit — 同义实体合并审计（**无写入方警示**）

> **契约先行、实现欠账（CERTAIN）**：DDL（schema.sql:1461-1472 + SchemaInitializer:351-370）、实体（KbGraphMergeAudit.java:98-114）、mapper 齐备，但 GraphServiceImpl.mergeEntities 与 Neo4jGraphStore.mergeEntity 均不写审计（grep 实测零消费方）。合并是破坏性操作，本表是 spec REQ-D05 的回滚/追溯载体——补线时字段语义如下。

id BIGINT AUTO_INCREMENT PK；kb_id VARCHAR(64) NOT NULL；src_entity_id VARCHAR(64) NOT NULL（被合并实体，INDEX idx_audit 联合 kb_id）；tgt_entity_id VARCHAR(64) NOT NULL（保留实体）；src_snapshot TEXT NOT NULL（source 完整属性 JSON，回滚重建用）；edge_snapshot TEXT NOT NULL（迁移边与 mentions 清单，回滚用）；operator VARCHAR(64)（system/用户 ID）；created_at。

### Neo4j 图谱本体（真实图数据存储，无 MySQL DDL）

命名空间隔离：全部节点/关系携带 kbId 属性实现多库共存〔Neo4jGraphStore.java:20-21 ‹CERTAIN›〕。

| 图元素 | 属性面（键加粗为 MERGE 身份） | 语义 |
|---|---|---|
| 节点 `(:Entity)` | **kbId, normalizedName**；entityId（确定性哈希）、name（首写展示名）、entityType（计票胜出类型）、description（择长）、typeCounts（类型票 JSON）、attributes（JSON）、embedding（float[]，1024 维）、createdAt | 实体节点；唯一身份=归一化名，同名跨单元合并〔:127-140 ‹CERTAIN›〕 |
| 节点 `(:Chunk)` | **kbId, chunkId**；fileId、content（≤200 摘要）、createdAt | 分片节点（可视化与 MENTIONS 端点）〔:633-636 ‹CERTAIN›〕 |
| 节点 `(:TripleMention)` | **kbId, tripleId, chunkId**；fileId、createdAt | 三元组↔分片溯源索引节点（RELATION 是 relationship 不能有出边，故独立建节点）〔:663-674 ‹CERTAIN›〕 |
| 边 `[:RELATION]` | **kbId, label**（端点对）；tripleId、sourceId/targetId（端点实体确定性 ID，回收前回填）、content（"源 -> 关系 -> 目标"）、createdAt | 实体间关系；去重键=(kbId, 端点对, label)〔:195-202,763-767 ‹CERTAIN›〕 |
| 边 `[:MENTIONS]` | kbId、createdAt | 实体→Chunk 提及（删除回收与 PPR 数据源）；按 entityId 匹配实体〔:651-655,234-235 ‹CERTAIN›〕 |
| 向量索引 `entity_embedding` | Entity.embedding，dim=1024（graph.entity-embedding-dimension），COSINE | 幂等创建（IF NOT EXISTS）、失败降级不可用〔:862-879 ‹CERTAIN›〕 |

孤儿口径（CERTAIN）：无任何 TripleMention 引用的 RELATION + 无任何 MENTIONS 的 Entity，回收前先回填存量边空端点 ID〔pruneOrphans :762-783 ‹CERTAIN›〕。

### 抽取单元（运行时结构，`ExtractionUnit`，无表）

`record ExtractionUnit(int index, String content, String contentHash, List<KbChunk> chunks)`〔ExtractionUnitAssembler.java:23 ‹CERTAIN›〕——不落库为独立结构：index/contentHash/chunkIds 持久化为 kb_graph_unit 行，content 不持久化（可由 chunk_ids 反查重组）。组装规则：按 chunkIndex 升序累积分片、累计超预算（graph.build.unit-max-chars，默认 2000）即切段、单分片超长独占单元不截断、空内容按 0 长度参与〔:34-56 ‹CERTAIN›〕。

### 图谱构建消息（运行时结构，MQ，无表）

`fastrag.direct` 交换机 → routing key `graph-build` → `fastrag.graph-build.queue`（持久化，监听并发 5）〔RabbitMQConfig.java:35,49-61、GraphBuildConsumer.java:184 ‹CERTAIN›〕；MQ 不可用时发布侧同步降级直调 handleGraphBuild〔MessagePublisher.java:106-125 ‹CERTAIN›〕。

| 字段 | 类型 | 说明 |
|---|---|---|
| kbId | string | 必填；缺失消息丢弃 |
| fileId / fileIds | string / string[] | 限定范围（增量路单文件 fileId；手动构建多文件 fileIds）；缺省=全库 |
| mode | string | incremental（增量触发缺省，IngestionConsumer.java:500）/ full（手动构建与 retry 缺省，GraphController.java:92、GraphServiceImpl.java:317）/ replay（手动，GraphController.java:113） |

### 消费点登记（跨域数据，消费行为归本链）

| 外部数据 | 消费口径 | 位置 |
|---|---|---|
| kb_chunk.graph_indexed | 图谱侧读写三态：构建完成置 1、失败置 2、retry/文件删图重置 0；增量构建查 1 计数 | GraphBuildConsumer.java:1149-1155、GraphServiceImpl.java:288-289,384-385 ‹CERTAIN› |
| kb_chunk.extraction_result | **无非空值生产者、图谱链不读**；编辑/重切/AI 应用三条路径会将其重置为 null（见本文件 chunk-management 节与 interfaces.md ApplyOrchestrator）；历史逐片缓存载体已迁 kb_graph_unit.extraction_result | grep 实测 ‹CERTAIN› |
| kb_chunk.content/chunk_index/file_id | 单元组装输入（按文件分组、组内排序） | GraphBuildConsumer.java:688-709 ‹CERTAIN› |
| kb_parse_strategy.llm_model | 抽取 LLM 路由第一优先（须 model 表 status=online） | GraphBuildConsumer.java:1082-1098 ‹CERTAIN› |
| kb.graph_llm_model | 抽取 LLM 路由兜底（策略未配置时） | :1100-1116 ‹CERTAIN› |
| kb.graph_auto_build | 不直接消费——由 file-ingestion 触发裁决消费（归 `## file-ingestion` 节） | 上游契约 ‹CERTAIN› |
| kb.embedding_model | 实体 embedding 模型路由（model 表 online 校验） | :1313-1335 ‹CERTAIN› |
| model 表 | LLM/Embedding 两类模型的 apiUrl/apiKeyRef 解析（code + status=online LIMIT 1） | :1086-1094,1321-1330 ‹CERTAIN› |
| kb_graph_index.settings.entitySchema | KB 级实体类型白名单（优先于 yml 全局） | :1160-1181 ‹CERTAIN› |

**Parent Trace:** kb_graph_index → knowledge-graph.GraphExtractionPipeline（状态面）/ GraphManagementModule → Design 两模块 → Spec REQ-B04、B08、C02、C04、D03、D04；kb_graph_unit → GraphExtractionPipeline → REQ-B03、B04；kb_graph_stitch/kb_graph_merge_audit → GraphStore 缝合/合并契约（休眠/欠账）→ REQ-D05、E03；Neo4j 本体 → GraphStore → REQ-B06、B07、D01、D02、D04、E01、E02、F02、F03；抽取单元 → ExtractionUnitModule → REQ-A01；构建消息 → GraphExtractionPipeline → REQ-B01；消费点登记 → GraphExtractionModule（缝 2/3）→ REQ-B02、A06、F01。

---

## benchmark-evaluation

> 覆盖：本特性四张自有数据表（kb_benchmark / kb_benchmark_question / kb_evaluation / kb_evaluation_result）。DDL 三轨：schema.sql 全量（⚠ 同文件出现两份定义：schema.sql:238-300 与 schema.sql:1474-1539，均 CREATE TABLE IF NOT EXISTS 幂等、内容一致——双份维护面登记为漂移风险 ‹CERTAIN›）、增量迁移 migration-20260704-eval-recall-columns.sql（recall 四列）与 migration-20260818-chunk-recall-enhance.sql（cross_boundary + context_completeness 两列）、SchemaInitializer.java:264-274（retrieval_metrics JSON→TEXT 运行时改列 + recall 四列兼容增补）。
> **已知双处漂移（登记债务）**：retrieval_metrics 在 schema.sql 定义为 JSON（schema.sql:290,1529），SchemaInitializer 运行时强制 MODIFY 为 TEXT（SchemaInitializer.java:262-268，自述「实际存的是普通字符串」）——新装库先 JSON 后被改 TEXT，行为一致但两处文本不一致，违 DDL 双处同步约定。且代码实际写入的是格式化指标串（"R@1 1.000  R@3 ..."，EvaluationExecutionHelper.java:335-343），从来不是 JSON。
> 状态语义登记：kb_evaluation.status 存储态只有 running/completed/failed 三值由代码写入（EvaluationServiceImpl.java:112,140；EvaluationExecutionHelper.java:158,242,896 ‹CERTAIN›）；DDL 缺省 'pending'（schema.sql:277）无写方；进度查询面的 "not_found" 是查询形态不是存储态（EvaluationController.java:69-75 ‹CERTAIN›）。

### kb_benchmark — 基准测试

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK（ASSIGN_ID 雪花） | 应用生成 |
| kb_id | VARCHAR(32) NOT NULL（INDEX idx_kb_id） | 归属知识库 |
| name | VARCHAR(128) NOT NULL | 基准名称 |
| description | TEXT | 描述，可空 |
| has_gold_chunks | TINYINT DEFAULT 0 | 是否含标准检索 chunk；生成路径恒写 1（BenchmarkServiceImpl.java:127），手动创建写 0（:101），JSONL 导入不更新此列 |
| has_gold_answer | TINYINT DEFAULT 0 | 是否含标准答案；生成路径恒写 1，JSONL 导入成功后写 1（:210） |
| is_auto_generated | TINYINT DEFAULT 0 | 1=LLM 自动生成（:122），0=手动创建 |
| question_count | INT DEFAULT 0 | 题数——**两段式协议的进度真相**：生成路径先写请求值（:124），异步完成后覆盖为实际题数（BenchmarkGenerationHelper.java:126），失败清零（:165-177）；JSONL 导入累加（:209） |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 创建时间；列表排序键（降序） |

Lifecycle：**产生** = 手动 create / generate 同步段；**更新** = 异步生成覆盖题数、JSONL 导入累加题数；**删除** = delete 级联删题目后删本行（BenchmarkServiceImpl.java:230-233）；**消费** = 列表/详情读面；评测侧只经 kb_benchmark.question 间接触达（run 校验计数经 question 表）。

Relationships：1—N kb_benchmark_question（经 benchmark_id，无外键，应用层级联删除）；N—1 kb（经 kb_id，无外键）。

### kb_benchmark_question — 基准测试题目

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT PK | 数据库自增 |
| benchmark_id | VARCHAR(32) NOT NULL（INDEX idx_benchmark_id） | 归属基准集 |
| question_index | INT | 题号；生成路径连续从 0（BenchmarkGenerationHelper.java:107），题目列表排序键（升序，BenchmarkServiceImpl.java:90） |
| cross_boundary | TINYINT NOT NULL DEFAULT 0 | 跨界切断标志；仅生成路径按配置写 1/0（BenchmarkGenerationHelper.java:102,110），JSONL 导入不写（落 DDL 缺省 0） |
| question | TEXT NOT NULL | 题面；JSONL 导入时空白行跳过（BenchmarkServiceImpl.java:179-180） |
| gold_chunks | VARCHAR(512) | 标准检索 chunk id 的 **JSON 数组字符串**；消费侧解析失败视为空列表（EvaluationExecutionHelper.java:869-876）。〔决议 2026-09-24：按现状为准——写入侧无长度防御（生成/导入两路径均直写，见 interfaces.md BenchmarkGenerator Post），是否发生过超长不可考（无运行数据证据）；MySQL 默认 strict mode 下超长插入报错而非静默截断，症状为该次生成整体失败→题数清零或导入事务回滚。512 容纳约 14 个满长（VARCHAR(32)）id，跨界题 prompt 约束 2 个 id，实际风险低（代裁，用户可推翻）。登记债务：导入路径（无 prompt 约束）缺长度校验，归入 implement 文档 BenchmarkGenerator Deviations③〕 |
| gold_answer | TEXT | 标准答案，可空；规则评判无可校验内容时恒判错（EvaluationExecutionHelper.java:850-853） |

Lifecycle：**产生** = LLM 生成批量插入（与题数覆盖同事务）/ JSONL 导入批量插入（与累加同事务，BenchmarkServiceImpl.java:202-214）；**删除** = 仅随基准集级联（无单题删除入口）；**消费** = 评测执行逐题读取 + 题目列表读面；结果行冗余题面文本（kb_evaluation_result.question），题目删除不影响既有结果行。

Relationships：N—1 kb_benchmark（经 benchmark_id，无外键）；与 kb_evaluation_result 无外键关联（评测结果经 evaluation_id 挂评测，不挂题目 id）。

### kb_evaluation — 评测任务

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK（ASSIGN_ID 雪花） | 应用生成 |
| kb_id | VARCHAR(32) NOT NULL（INDEX idx_kb_id） | 归属知识库；详情/删除均 kbId+id 双键校验（EvaluationServiceImpl.java:70-72,150-153） |
| name | VARCHAR(128) | 评测名称，可空 |
| benchmark | VARCHAR(128) | 引用的基准集 id（**列名无 _id 后缀**，无外键无存在性约束——run 时校验题目数，基准事后删除不影响已建评测） |
| benchmark_count | INT | 基准题数；仅完成路径写 = 总题数（EvaluationExecutionHelper.java:899），failed 拒绝路径写 0（EvaluationServiceImpl.java:143） |
| data_count | INT | 待评题数；执行开跑即写（EvaluationExecutionHelper.java:146-148），进度查询分母 |
| completed_count | INT | 已完成数；run 建时写 0，完成路径写总题数（:897）——**评测路径不逐题递增**（进度查询只见 0→终值跳变，非渐进）‹CERTAIN（2026-09-24 深证：grep setCompletedCount 全仓仅 4 处——EvaluationServiceImpl.java:113,141 建 0、EvaluationExecutionHelper.java:157 空题置 0、:896 完成写终值，无逐题递增调用）› |
| duration | BIGINT | 评测耗时毫秒（并行段起止，:217,898） |
| recall_at_1 / recall_at_3 / recall_at_5 / recall_at_10 | DECIMAL(5,4) | 汇总 R@K（命中题数/总题数）；仅完成路径写（:900-903） |
| answer_accuracy | DECIMAL(5,4) | 答案准确率（:904） |
| overall_score | DECIMAL(5,4) | 综合分 = R@10×0.7 + accuracy×0.3（:224,905） |
| status | VARCHAR(16) DEFAULT 'pending' | 状态机：running（run 建）→ completed（完成事务）/ failed（空题竞态 :158、全程异常 :242、run 拒绝 :140）；'pending' 缺省无写方 |
| run_id | VARCHAR(64) | **零写方废弃列**——实体标 @Deprecated（KbEvaluation.java:47-49），grep 无赋值 |
| answer_model / judge_model | VARCHAR(128) | 模型配置回显；judge 空时实际用 answerModel 执行合并调用（EvaluationExecutionHelper.java:284-285） |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 创建时间；列表排序键（降序） |

Lifecycle：**产生** = run（running 或拒绝即 failed）；**更新** = execute 终态事务（completed 全指标 / failed）；**删除** = delete 级联删结果行后删本行（EvaluationServiceImpl.java:154-156）；**消费** = 列表/详情/进度查询读面。

Relationships：1—N kb_evaluation_result（经 evaluation_id，无外键，应用层级联）；N—1 kb（经 kb_id，无外键）；N—1 kb_benchmark（经 benchmark 字符串引用，无约束）。实体另有非持久化 results 聚合字段（@TableField(exist=false)，KbEvaluation.java:58-59）。

### kb_evaluation_result — 评测结果明细

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT PK | 数据库自增；详情聚合排序键（EvaluationServiceImpl.java:79） |
| evaluation_id | VARCHAR(32) NOT NULL（INDEX idx_evaluation_id） | 归属评测 |
| question | TEXT | 题面快照（自基准题冗余） |
| generated_answer | TEXT | 生成答案；单题失败写「评估失败: 原因」（EvaluationExecutionHelper.java:367） |
| retrieval_metrics | TEXT（schema.sql 写 JSON——漂移见节首） | **格式化指标串非 JSON**："R@1 1.000  R@3 ... [COMPLETE x -> y]"（:335-343）；失败题写全零串（:368） |
| recall_at_1 / recall_at_3 / recall_at_5 / recall_at_10 | DECIMAL(5,4) | 单题 R@K（0 或 1）；失败题全零（:371-374） |
| context_completeness | DECIMAL(5,4) | 原始命中上下文完整度 0~1；仅跨界题非 NULL |
| context_completeness_extended | DECIMAL(5,4) | 父块扩展上下文完整度 0~1；仅跨界题非 NULL |
| is_correct | TINYINT | 1/0 判对标记（LLM 评判或规则降级产出） |
| judge_reason | TEXT | 评判理由/降级原因（「执行异常」「LLM 调用失败，规则评判降级」、命中率统计等） |

Lifecycle：**产生** = 执行聚合后逐条 insert（**非事务**，:230-232）；**删除** = 仅随评测级联；**消费** = 详情聚合读面（按 id 升序）；无更新路径。

Relationships：N—1 kb_evaluation（经 evaluation_id，无外键）。

### 消费点登记（跨域数据，消费行为归本链）

| 外部数据 | 消费口径 | 位置 |
|---|---|---|
| kb_chunk | 三路消费：① 评测检索回表（id/file_id/chunk_index/content/parent_id，含父块扩展按 parent_id 查全文）；② 全文检索（MATCH...AGAINST BOOLEAN→LIKE 兜底，无 chunkType='parent' 过滤——与 retrieval 引擎口径不同）；③ 出题采样（ORDER BY id 取前 N） | EvaluationExecutionHelper.java:514-548,713-735,801-813；BenchmarkGenerationHelper.java:218-220 ‹CERTAIN› |
| kb_graph_entity / kb_graph_relation | graph 出题语料只读（各取 20 条，name/entity_type/source/target/label） | BenchmarkGenerationHelper.java:262-266 ‹CERTAIN› |
| model 表 | llmModel/embeddingModel/rerankModel/answer/judge 五处路由：`SELECT api_url, api_key_ref FROM model WHERE code = ? LIMIT 1`——**无 status=online 过滤**（与 `## retrieval`/`## knowledge-graph` 消费口径不同，离线模型也会被路由）；default/查无/异常 → 默认网关 | BenchmarkGenerationHelper.java:195-205；EvaluationExecutionHelper.java:919-937 ‹CERTAIN› |
| Milvus collection `kb_{kbId}` | 评测向量检索（search topK×3，collection 名 "kb_"+kbId 拼接——与 `## chunk-management` 节命名约定一致性未在本链验证） | EvaluationExecutionHelper.java:448-449 ‹CERTAIN› |
| kb_log | 操作审计写方（经 publish 侧 LogService 三参重载）：benchmark_created/generated/imported/deleted、evaluation_run/deleted；全部 try-catch 不阻断 | BenchmarkServiceImpl.java:105-110,132-138,219-226,234-240；EvaluationServiceImpl.java:118-124,158-163 ‹CERTAIN› |

**Parent Trace:** kb_benchmark → BenchmarkFacade/BenchmarkGenerator → Spec REQ-A01~A06、B01；kb_benchmark_question → BenchmarkGenerator → REQ-A04、B02~B05；kb_evaluation → EvaluationFacade/EvaluationExecutor → REQ-C01~C05、D02；kb_evaluation_result → EvaluationExecutor → REQ-D01、D06、D07、C06；消费点登记 → 缝 1（图谱/Chunk 语料）、缝 2（Milvus/全文）、缝 3（model 路由）→ REQ-B02、B03、D03~D05。

---

## qa-pairs

> 覆盖：问答对表 kb_qa_pair（人工维护的标准问答知识，检索 qa 通道的数据源）。导入解析的行级结构（QaImportResult/ImportRowResult）为运行时对象非持久化，仅登记于 design/qa-pairs.md §2。无种子数据依赖（data.sql grep 实测）。
> 表结构来源：`init-scripts/schema.sql:205-216` ↔ 实体 KbQaPair.java:26-40；SchemaInitializer 无本表任何增补（grep 实测，本表仅存于全量脚本）——本表不存在 DDL 双处漂移。

### kb_qa_pair

| 字段 | 类型与约束 | 说明 / validation |
|---|---|---|
| id | VARCHAR(32) PRIMARY KEY；实体 @TableId(ASSIGN_ID) | 双轨生成：手工创建走 MyBatis-Plus 雪花；导入路径自拼「12 位毫秒时间戳 hex + 12 位 UUID hex」24 字符（generateSnowflakeId，QaPairServiceImpl.java:195-201——自造 ID 与 ASSIGN_ID 并存，登记） |
| kb_id | VARCHAR(32) NOT NULL；INDEX idx_kb_id（普通索引，schema.sql:215） | 宿主知识库；写路径不校验归属（update/delete/confirm 按 id 直取不比对 kbId，QaPairServiceImpl.java:70-95，登记于 interfaces.md `## qa-pairs` 债务③） |
| file_id | VARCHAR(32) 可空 | 关联源文件；create 可传且**不校验文件存在**；导入路径强制 null（:158）；null 记录逃过删库级联（孤儿缺陷，见消费点登记） |
| file_name | VARCHAR(256) 可空 | 冗余展示字段；**全仓无写方**（grep 实测：仅 toDto 读取透传，创建/导入均不设置），恒 NULL——死字段 |
| question | TEXT NOT NULL | 问题；create @NotBlank（QaCreateRequest）；导入空白行失败；去重判定基于归一化文本（全角→半角+trim+压缩空白，ExcelQaPairImporter.java:325-330），**落库存原值非归一化值** |
| answer | TEXT NOT NULL | 答案；create @NotBlank；导入缺省空串（QaPairServiceImpl.java:155）——DDL NOT NULL 靠应用层空串兜底 |
| source | VARCHAR(16) DEFAULT 'manual' | manual / ai；**ai 无写入方**（grep 实测全仓仅 setSource("manual")），问答对提取流水线未实现（interfaces.md `## qa-pairs` 上游分工）；**2026-09-26 起 ai 写入方已立项**（qa-generation 链，见 `## qa-generation` 节覆盖声明①） |
| status | VARCHAR(16) DEFAULT 'draft' | draft / confirmed；无 DB 约束，合法性由应用层维护（导入接受 draft/confirmed/草稿/已确认 四变体，ExcelQaPairImporter.java:367-374）；**与召回解耦**：召回不过滤 status（RetrievalServiceImpl.java:803-812；ai 引入后修订为 `source='manual' OR status='confirmed'` 准入，manual 口径不变——见 `## qa-generation` 节覆盖声明②与 ADR-0005） |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP；实体 FieldFill.INSERT | 列表按其倒序（QaPairServiceImpl.java:45）；update/confirm 不触碰 |

**Lifecycle:** create → 强制 status=draft（QaPairServiceImpl.java:62）→ confirm 单向转 confirmed（:89-95，无撤销操作，幂等于已确认态）→ update 改 question/answer 不改状态 → delete（物理删除，无软删列）或宿主级联删除（KB 删除/文件删除/跨库移动，行为契约见 interfaces.md `## qa-pairs` 消费点登记）。import 路径绕过 create：行级 status 直接落库（缺省 draft），新增行 source=manual、file_id=null。

**Relationships:** N—1 kb（经 kb_id，无外键、应用层维护）；N—0..1 kb_file（经 file_id，可空；文件删除/移动时该文件 QA 对被**物理删除而非置空或随迁**——跨库移动即丢失，FileServiceImpl.java:750-756）；与 kb_chunk 无任何引用关系（两者是检索的并列内容源，qa 通道不经 chunk）。

**Parent Trace:** kb_qa_pair → Design.QaPairModule → Spec REQ-A01~A05、B01~B03、C07、E01~E05；status/召回解耦 → Design 缝 3 消费点 → REQ-D04；file_id 级联口径 → 上游契约（`## kb-core` delete Post / `## file-ingestion` 清理面）→ REQ-E01~E05。

---

## publish-review

> 覆盖：发布治理 7 张运行时表——kb_version / kb_review_task / kb_log / kb_update_log（schema.sql:303-358）+ kb_publish_history / kb_publish_plan / kb_reset_config（schema.sql:810-844,927-935）。另登记 5 张 **DDL-only 预留表**（kb_review_strategy/kb_compliance_rule/kb_quality_rule/kb_review_template/kb_review_node，schema.sql:846-902）：实体+Mapper 已生成但全仓零消费者（grep 实测），不入契约、字段从略（同 iam 节 login_security_config 先例）。无种子数据依赖（data.sql grep 实测）。
> 表结构来源：`init-scripts/schema.sql` ↔ 实体类；kb_publish_history 在 SchemaInitializer.java:102-121 有**第二份等价 DDL**（逐列一致，双处同步合规——conventions.md 铁律的现状符合项）。
> 同表双实体登记：kb_publish_history 由 publish 模块 KbPublishHistory+KbPublishHistoryMapper 与 knowledge 模块 KbPublishHistory+KbPublishHistoryMapper2 **双份映射**（类名字冲突避让），两实体字段一致（逐字段比对 CERTAIN）——表契约以下述单节为准。

### kb_version — 版本（五态状态机载体）

| 字段 | 类型与约束 | 说明 / validation |
|---|---|---|
| id | VARCHAR(32) PRIMARY KEY；@TableId(ASSIGN_ID) | 雪花 ID |
| kb_id | VARCHAR(32) NOT NULL；INDEX idx_kb_id | 宿主知识库 |
| version | INT NOT NULL | 版本号；应用层 max+1 分配（PublishServiceImpl.java:40-41）——**无 (kb_id,version) 唯一约束**，并发创建可重号（债务④）；latest 查询按其倒序 |
| name / description | VARCHAR(128) / TEXT 可空 | 版本名/描述 |
| tags | JSON 可空 | 标签 JSON；实体 setter 兼容字符串与对象双入参（KbVersion.java:41-47） |
| file_count / chunk_count | INT DEFAULT 0 | 文件/分片计数；**由请求体声明、服务端不校验**（REQ-A02——快照粒度=元数据） |
| publish_status | VARCHAR(16) DEFAULT 'draft' | draft/pending_review/approved/published/rejected 五态；无 DB 约束、无状态机校验（REQ-A06） |
| change_summary / created_by | TEXT / VARCHAR(32) 可空 | 变更摘要/创建人；created_by 被 transitionStatus 复用为发布历史 operator（债务⑦） |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 列表排序键（倒序） |

**Lifecycle:** create（强制 draft + 自动 version）→ transition 任意态互转（无校验）→ published 时旁写一条 kb_publish_history。无删除/更新端点（版本不可改元数据、不可删）。

**Relationships:** N—1 kb（应用层引用）；1—0..N kb_publish_history（经 knowledge_id=version.id 的**语义复用**——publish 模块把 versionId 塞进 history.knowledge_id，PublishServiceImpl.java:50；与 knowledge 条目口径的 knowledge_id 同列不同义，见缝 4）。

### kb_publish_history — 发布历史（上线/下线/回滚的唯一事实表）

| 字段 | 类型与约束 | 说明 / validation |
|---|---|---|
| id | VARCHAR(32) PRIMARY KEY；ASSIGN_ID | 雪花 ID |
| kb_id | VARCHAR(32) NOT NULL；INDEX idx_kb_id | 宿主知识库 |
| knowledge_id | VARCHAR(32) NOT NULL；INDEX idx_knowledge_id | 发布对象：**双语义**——PublishManage 口径=知识条目 id（无实体表，弱引用）；publish 模块口径=kb_version.id‹CERTAIN（2026-09-24 深证：两份实体 javadoc 各执一词——knowledge 侧「发布的知识条目 ID」KbPublishHistory.java:11、publish 侧「关联的知识版本ID」KbPublishHistory.java:12；写方 PublishManageServiceImpl.java:27 透传条目 id、PublishServiceImpl.java:50 写 versionId；schema.sql:810-828 无列注释，无外键校验）› |
| version | INT 可空 | 版本号；PublishManage 口径缺省 1（PublishManageServiceImpl.java:31），重置沿用源记录（:125） |
| publish_type | VARCHAR(16) | publish / revoke（代码仅写此二值） |
| online_version / offline_version | LONGTEXT 可空 | 线上/线下版本配置快照；**全仓无 Java 写方与读方**‹CERTAIN（2026-09-24 深证：后端仅实体声明，grep 全模块无 getter/setter 调用；前端仅 mock/knowledge-review.ts:15,90 引用，非真实 API 消费）›〔决议 2026-09-24：裁定为**死列/预留列**，不承诺载荷结构（knowledge 侧实体 javadoc「用于版本对比和回溯」是唯一意图线索，未实现）；理由：零读写方，任何结构承诺均无代码依据；现状差距：无——死列保持原样，若未来启用需先定 schema（代裁，用户可推翻）〕 |
| status | VARCHAR(16)；INDEX idx_status | published / revoked；线上判定=status='published'（getOnlineVersion），线下判定=**非 published**（ne，PublishManageServiceImpl.java:88） |
| scheduled_at | DATETIME 可空 | 计划发布时间；发布路径无写方，恒 NULL‹CERTAIN（2026-09-24 深证：grep 全模块 scheduledAt 仅两份实体声明，无任何 setter 调用；四条写入路径 PublishServiceImpl.java:49-54、PublishManageServiceImpl.java:25-33,35-43,120-128 均不触该字段）› |
| published_at / created_at | DATETIME | 实际发布时间（显式 set now）/ 行创建时间；线上查询按 published_at 倒序取 1 |
| operator | VARCHAR(32) 可空 | 三来源：请求体原样 / version.createdBy（transition 路）/ "system"（重置路） |

**Lifecycle:** 只追加不修改不删除——publish 写 publish/published、revoke 写 revoke/revoked、transition(published) 写 publish/published、resetKnowledge 复制最近 published 为新行。删除宿主 KB 时不级联清理本表（全仓无按 kbId 的 history 清理，grep 实测——KB 删除后历史残留，登记债务）。

**Relationships:** N—1 kb；N—0..1 kb_version（经 knowledge_id 双语义，上）；与 kb_publish_plan 无外键（计划的 knowledge_ids JSON 逐项发布时理论上写本表，但执行器不存在——REQ-C03）。

### kb_publish_plan — 发布计划

id VARCHAR(32) PK（ASSIGN_ID）；kb_id VARCHAR(32) NOT NULL + INDEX idx_kb_id；name VARCHAR(256) NOT NULL；knowledge_ids JSON（条目 id 数组）；strategy VARCHAR(32)（策略键，自由文本无枚举校验）；scheduled_time DATETIME（**无执行器消费**）；executed_time DATETIME（无写方）；execution_status VARCHAR(16) + INDEX idx_execution_status（缺省 pending；running/completed/failed 无产生路径）；success_count/fail_count INT DEFAULT 0（恒 0）；execution_detail JSON（无写方）；created_by VARCHAR(32)；created_at DATETIME + 倒序排序键。
**Lifecycle:** create（缺省 pending）→ 查询。终态迁移、成功/失败计数全部无写入方（REQ-C03 GAP）。
**Relationships:** N—1 kb；对 knowledge_ids 引用的条目无任何约束与校验（JSON 直存）。

### kb_review_task — 审核任务

id VARCHAR(32) PK（ASSIGN_ID）；kb_id VARCHAR(32) NOT NULL；kb_name VARCHAR(128)（冗余展示，submit 不写、恒 NULL〔CERTAIN：ReviewServiceImpl.java:29 仅 set 五字段〕）；version_id VARCHAR(32)（弱引用 kb_version.id，无外键不校验）；version INT 可空（无写方）；applicant VARCHAR(64)；reviewer VARCHAR(64)（**无写方**——approve/reject 不记录审核人，恒 NULL，登记债务）；status VARCHAR(16) DEFAULT 'pending'（pending/approved/rejected；终态可覆写）；comment TEXT；created_at（提交时间，显式 set now）/reviewed_at DATETIME。
**Lifecycle:** submit（pending）→ approve/reject（记 comment/reviewedAt，可反复覆写）。无删除端点。
**Relationships:** N—1 kb；N—0..1 kb_version（经 version_id，弱引用）。
**注意：** 本表与 DDL-only 的 kb_review_strategy/kb_review_template/kb_review_node 三表无任何关联（多级审核流未实现）。

### kb_reset_config — 重置配置

id VARCHAR(32) PK（ASSIGN_ID）；kb_id VARCHAR(32) 可空 + 无索引；role_key VARCHAR(64)（适用角色键）；can_reset TINYINT DEFAULT 0（0/1）；max_reset_count INT DEFAULT 3；config JSON（〔决议 2026-09-24：裁定为自由扩展位，无 schema 承诺——全仓零消费方（实体仅声明 KbResetConfig.java:22，执行路径 resetKnowledge 不读本表 REQ-E04），任何结构定义均无代码依据；现状差距：配置与执行脱钩，债务⑥已登记（代裁，用户可推翻）〕）；created_at DATETIME。缺省填充：canReset=0、maxResetCount=3（PublishManageServiceImpl.java:102-103）。
**Lifecycle:** save（有 id→updateById / 无 id→insert，upsert 靠调用方携带 id）→ 查询。**执行路径零消费**（resetKnowledge 不读本表，REQ-E04）——配置与行为脱钩是本表唯一非平凡事实。
**Relationships:** N—0..1 kb；role_key 语义挂靠 iam 的 KB ACL 角色（owner/editor/viewer），无外键。

### kb_log — 操作日志（全后端审计底座）

id VARCHAR(32) PK（ASSIGN_ID）；kb_id VARCHAR(32) NOT NULL + INDEX idx_kb_id（切面兜底可写 "unknown" 弱约定值）；category VARCHAR(16) NOT NULL（operation/retrieval/publish，绕过 LogService 的直写方也落此三值）；action VARCHAR(64)（ActionType 枚举名，如 publish_published/retrieve_query）；target VARCHAR(256)（对象 id 或 SpEL 解析值）；detail TEXT；operator VARCHAR(64)（用户名，兜底 "system"）；status VARCHAR(16)（success/failed）；extra JSON 可空（扩展载荷）；timestamp DATETIME DEFAULT CURRENT_TIMESTAMP + 倒序排序键。
**Lifecycle:** 只插入（LogService 兼容/枚举入口 + 切面双路径 + 4 处 Mapper 直写），无更新/删除/归档——〔决议 2026-09-24：保留策略裁定为**长期保留、不自动归档**（审计语义 + 本仓全部日志表一致无归档任务，删审计痕迹风险大于存储成本）；查询性能预期=单库 10 万行级内 idx_kb_id 可支撑常规分页，keyword 模糊与深翻页随量线性退化；现状差距：与裁定一致，无归档任务；容量超限需人工清理或引入归档任务（代裁，用户可推翻）〕。
**Relationships:** N—1 kb（弱）；被 operation 模块只读消费（首页统计/统一日志视图）。

### kb_update_log — 更新日志（新旧值对比）

id VARCHAR(32) PK（ASSIGN_ID）；kb_id VARCHAR(32) NOT NULL + INDEX idx_kb_id；update_type VARCHAR(32) NOT NULL（自由文本，无枚举约束）；target VARCHAR(256)；detail TEXT；old_value/new_value TEXT 可空（对比语义）；operator VARCHAR(64)；timestamp DATETIME + 倒序排序键。
**Lifecycle:** 只插入（LogService.addUpdateLog 双重载 + retrieval UpdateRemind 直写 Mapper），无更新/删除；「已读」不落库（markUpdateLogRead 空实现）。
**Relationships:** N—1 kb；PublishManageController.knowledge-update-logs 端点手工分页读（LIMIT/OFFSET 拼接，返回裸 Map 非 PageResult——债务⑧）。

**Parent Trace:** kb_version → Design.VersionModule → REQ-A01~A06；kb_publish_history → Design.VersionModule+PublishManageModule（缝 4 双写）→ REQ-A05、B01~B05、E02~E03；kb_publish_plan → Design.PublishManageModule → REQ-C01~C03；kb_review_task → Design.ReviewModule → REQ-D01~D05；kb_reset_config → Design.PublishManageModule → REQ-E01、E04；kb_log/kb_update_log → Design.LogAuditModule（缝 2/缝 3）→ REQ-F01~F07。

---

## platform-config

> 覆盖：fastrag-platform 模块拥有或写入的 14 张表。种子数据：model 3 条（qwen3-72b / bge-m3 / bge-reranker-v2-m3，全 online，data.sql:439-442）；sys_config 13 键（brand 5 + general 7 + 其余，data.sql:485-499）〔‹CERTAIN›〕。字典/敏感词/术语/查询规则/通知/安全策略/发布策略七表无种子数据。
> **DDL 双处同步警示（conventions.md 铁律，本链实测两处漂移）**：① model 表 context_window/enable_thinking 两列仅在 SchemaInitializer.java:280-281 幂等增补，schema.sql:523-531 基线缺失；② model_call_log 的 status 列在 schema.sql:563 与 :566 重复定义——MySQL 执行该 CREATE 必失败，SchemaInitializer 亦无此表兜底建表语句，新环境部署该表缺失（org_id 列增补语句 :389 亦将随之失败）〔grep 实测 ‹CERTAIN›〕。

### model — AI 模型记录

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | MyBatis-Plus ASSIGN_ID |
| name | VARCHAR(128) NOT NULL | 列表 keyword 匹配字段〔ModelServiceImpl.java:27 ‹CERTAIN›〕 |
| code | VARCHAR(128) | **全局路由键**（消费方 code+status='online' LIMIT 1）；无唯一约束，同 code 多条在线记录取值不确定 |
| purpose | VARCHAR(32) | 自由字符串无值集校验；种子用 LLM/Embedding/Rerank（混合大小写），消费方按字面 'EMBEDDING' 等过滤‹CERTAIN（2026-09-24 深证：ChunkingServiceImpl.java:240 vs data.sql:441；schema.sql:531 仅 CHARSET=utf8mb4 未显式声明 collation，生产 MySQL 依赖服务器默认 ci 排序规则匹配成功；H2 测试库 MODE=MySQL（application-test.yml:10）字符串比较默认大小写敏感，字面不一致即失配——隐式依赖属实，收敛方向=显式 collation 或统一大小写）› |
| brand | VARCHAR(64) | 供应商展示 |
| api_url | VARCHAR(512) | 网关直连地址；空白时连通性测试拒绝（"模型未配置接口地址"）〔ModelServiceImpl.java:62-64 ‹CERTAIN›〕 |
| api_key_ref | VARCHAR(256) | **实际存储密钥值**而非引用 id（命名失真）；create/update 兼容 apiKey/apiKeyRef 双入参名，update 空白不覆盖〔:153-155,176-180 ‹CERTAIN›〕 |
| status | VARCHAR(16) DEFAULT 'offline' | 恒 online/offline 二值（toggle 唯一翻转入口 :203）；消费方过滤口径的成立前提 |
| context_window | INT DEFAULT 4096 | **仅 SchemaInitializer 增补**（:280），schema.sql 缺失 |
| enable_thinking | TINYINT(1) DEFAULT 0 | **仅 SchemaInitializer 增补**（:281），schema.sql 缺失；消费方透传 LlmService enableThinking 重载 |

Lifecycle：create（缺省 offline）→ update（部分语义）→ toggle ↔ → delete（物理删除，无引用检查）。
Relationships：被各消费链按 code 逻辑引用（无外键）；model_training/model_test_report/model_call_log 经 model_id 弱关联（删模型不级联）。
消费方：retrieval / knowledge（chunking/ai-chunk/graph/parsing/storage）/ graph-eval / application / agent（见 interfaces.md `## platform-config` 节消费点登记）。

### model_training — 模型训练记录

id VARCHAR(32) PK；model_id VARCHAR(32) NOT NULL；model_name VARCHAR(128)（冗余）；dataset VARCHAR(128)；epochs INT；status VARCHAR(16) DEFAULT 'running'；duration BIGINT；metrics JSON；created_by VARCHAR(32)；created_at DATETIME DEFAULT CURRENT_TIMESTAMP。
写入方：ConfigManageServiceImpl.train（:15-22）——status 直写 'running' 恒不流转、metrics 硬编码 `{"loss":0.32,"accuracy":0.91}`（假成功债务②）；created_by/duration 全仓无写入方。
Lifecycle：只插入与查询（listTrainings 按 model_id 过滤、createdAt 倒序），无更新/删除。

### model_test_report — 模型测试报告

id VARCHAR(32) PK；model_id VARCHAR(32) NOT NULL；model_name VARCHAR(128)；test_dataset VARCHAR(128)；test_count INT；score VARCHAR(32)；metrics JSON；status VARCHAR(16)；created_at DATETIME。
写入方：ConfigManageServiceImpl.test（:23-30）——score 硬编码 "0.92"、metrics 硬编码、status 直写 'completed'（假成功债务②）。注意与本特性**连通性测试**（ModelConnectivityTester，不落库、实时调网关）是两套互不相关的「测试」。
Lifecycle：只插入与查询。

### model_call_log — 模型调用日志

id BIGINT AUTO_INCREMENT PK；model_id VARCHAR(32) NOT NULL；caller VARCHAR(64)；status VARCHAR(16)（**DDL 重复列缺陷，见节首警示②**，实体单值语义 success/failed）；org_id VARCHAR(32)（仅 SchemaInitializer:389 增补，带 username→org 回填 UPDATE）；duration INT；tokens INT；timestamp DATETIME DEFAULT CURRENT_TIMESTAMP。
写入方：**全仓无**（grep 实测，ModelCallLog 全模块仅实体/Mapper/operation 读取方三处命中）——读取方 operation.ModelMonitorServiceImpl 做监控聚合（组织过滤 + API Token 放行），当前恒为空表（interfaces.md 消费点登记债务④）。〔决议 2026-09-24：设计写入点代裁为**业务侧网关调用完成点记账**（各消费链解析模型记录并完成 LLM/Embedding/Rerank 调用后写一行，含 model_id/caller/status/duration/tokens）——与本仓 mcp_call_log 的 McpToolExecutor.java:106-120 记账模式同构（一致性依据），网关侧（fastrag-ai）不落库保持其无状态；清理策略取**保留 90 天滚动清理**（业界调用日志惯例）；现状差距：写入方与清理任务均未实现（债务④已登记），表 DDL 重复列缺陷需先修复（代裁，用户可推翻）〕

### sys_config — 系统配置

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | ASSIGN_ID |
| config_key | VARCHAR(128) NOT NULL **UNIQUE** + INDEX | 业务键全局唯一——「默认值」是行标志而非并行行的结构前提〔schema.sql:786 ‹CERTAIN›〕 |
| config_value | TEXT | 字符串存储（通常 JSON），无格式校验 |
| config_type | VARCHAR(32) | 分组（种子：brand/general；端点固定键：publish/review/doc_guide） |
| description | TEXT | |
| is_default | TINYINT DEFAULT 0 | 「当前值即默认基准」标志（setDefault 置 1 不清他行，因 UNIQUE 无他行）〔ConfigManageServiceImpl.java:82-85 ‹CERTAIN›〕 |
| is_system | TINYINT DEFAULT 0 | 实体有字段，代码无任何读写消费（种子置 1）〔grep 实测 ‹CERTAIN›〕 |
| updated_by | VARCHAR(32) | 控制器硬编码 "admin"，非会话身份 |
| created_at / updated_at | DATETIME | updated_at ON UPDATE CURRENT_TIMESTAMP |

Lifecycle：saveConfig upsert（每次 create/update 强制写历史）→ setDefault 标记 → import（逐键 upsert）；无删除端点（配置不可物理删除）。
Relationships：1—N sys_config_history（config_id + 冗余 config_key）；消费方 StrategyConfigResolver（general_chunk_size/general_chunk_overlap 系统默认层）。
种子：system_name/system_slogan/copyright/logo_url/org_name（brand）、general_settings/general_chunk_size('2000')/general_chunk_overlap('100')/general_search_top_k/general_retrieval_mode/general_enable_rerank/general_max_tokens（general）〔data.sql:485-499 ‹CERTAIN›〕。

### sys_config_history — 配置变更历史

id BIGINT AUTO_INCREMENT PK；config_id VARCHAR(32) NOT NULL + INDEX；config_key VARCHAR(128) NOT NULL（冗余）；old_value/new_value TEXT；change_type VARCHAR(16)（代码写入 create/update/reset 三值，reset 因死逻辑实际不产生）；operator VARCHAR(32)；timestamp DATETIME DEFAULT CURRENT_TIMESTAMP。
Lifecycle：只插入（recordHistory 私有方法，saveConfig/resetToDefault 内部调用）与查询（LIMIT 100 截断）；〔决议 2026-09-24：保留期限裁定为**随主配置行长期保留、不独立清理**——配置变更历史属审计语义（业界惯例长保留），且查询面已 LIMIT 100 限流、单配置键变更频率低，增长速率可控；现状差距：与裁定一致（无清理任务），仅全表容量属运维监控面（代裁，用户可推翻）〕。

### sys_dictionary — 数据字典

id BIGINT AUTO_INCREMENT PK；dict_type VARCHAR(64) NOT NULL；dict_key VARCHAR(128) NOT NULL；dict_value TEXT；label VARCHAR(128) NULL（显示名，空回退 dict_key——**近期修复：真实字段落库**，此前为硬编码假值）；enabled TINYINT NOT NULL DEFAULT 1；remark VARCHAR(255)；sort_order INT NOT NULL DEFAULT 0（列表排序键）。无 UNIQUE 约束（同 type+key 可重复，getDictValue LIMIT 1 取首条）〔DictionaryServiceImpl.java:52-60 ‹CERTAIN›〕。
Lifecycle：CRUD 全开放；enabled 入参兼容布尔/数字/字符串〔DictionaryController.java:106-111 ‹CERTAIN›〕。
消费方：全仓无后端消费方（grep 实测），纯前端下拉数据面。

### sensitive_word — 敏感词

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | BIGINT AUTO_INCREMENT PK | |
| word | VARCHAR(128) NOT NULL | 无重复校验，同词可多条 |
| category | VARCHAR(64) | **JSON 字符串**（非枚举列）：`{"blockInput":bool,"blockSearch":bool,"replaceAnswer":bool}`；解析=子串包含匹配 `"{flag}":true`〔SensitiveWordServiceImpl.java:150-158 ‹CERTAIN›〕 |
| level | VARCHAR(16) | 仅存储回显，无消费语义 |
| replacement | VARCHAR(128) DEFAULT '***' | 指定回复；空值时 checkInput 用固定文案兜底、filterOutput 用 "***"〔:88-90,110 ‹CERTAIN›〕 |
| enabled | TINYINT DEFAULT 1 | 检查面过滤键（缓存只查 enabled=1） |
| created_at | DATETIME | save 时代码赋值 now |

Lifecycle：create/import（默认启用）→ update（category 全量重建——未传标志重置 false，债务②）→ delete；每次写操作缓存失效。
Relationships：无外键；缓存 `volatile List<SensitiveWord>`（进程内，多实例不联动）。
消费方：AppServiceImpl checkInput/filterOutput（对话输入拦截/输出替换，应用级开关在消费方）。

### term_library — 术语库

id VARCHAR(32) PK（ASSIGN_ID）；name VARCHAR(128) NOT NULL；description TEXT；term_count INT DEFAULT 0（**手工守恒计数**：create +1/delete −1/移动联动、钳 ≥0，无对账——非权威）；owner VARCHAR(32)（实体有、创建路径不写，恒 null）；created_at/updated_at（代码赋值）。
Lifecycle：create → update（name/description）→ delete（**级联物理删除**其下全部 term_record + 缓存失效，TermServiceImpl.java:65-69）。
Relationships：1—N term_record（library_id，无外键应用层维护）。

### term_record — 术语条目

id VARCHAR(32) PK；library_id VARCHAR(32) NOT NULL + INDEX idx_library_id；term VARCHAR(128) NOT NULL；alias VARCHAR(256)（逗号分隔别名组，`split("[,，]")` 中英文逗号）；definition TEXT；category VARCHAR(64)（实体有、创建路径不写）；status TINYINT NOT NULL DEFAULT 1（1=启用 0=禁用，**启用状态是同义扩展生效面**，近期修复口径）；created_at。
Lifecycle：create（缺省启用）→ update（部分语义/跨库移动）→ delete；写操作全部缓存失效。
Relationships：N—1 term_library；消费方 TermService.expandSynonyms → retrieval QueryEnhancePipeline（仅 status=1）。
启用词缓存：`volatile List<TermRecord>`（status=1 查询结果，进程内）。

### query_rule — 查询规则

id VARCHAR(32) PK；name VARCHAR(128) NOT NULL；description VARCHAR(256)（**无写入端点**，实体有字段）；rule_type VARCHAR(16) NOT NULL（create 入参名 type）；pattern VARCHAR(256)；action VARCHAR(256)（**无写入端点**）；enabled TINYINT DEFAULT 1（create 固定 1，toggle 翻转）；priority INT DEFAULT 0（**无写入端点、无排序消费**）；created_at。
Lifecycle：create → toggle ↔ → delete；无更新端点。
消费方：**零**——retrieval applyQueryRules 占位、执行引擎缺失（`## retrieval` 节已登记，本链不展开）。

### sys_notification — 系统通知

id VARCHAR(32) PK；title VARCHAR(256)；content TEXT；notify_type VARCHAR(32)（生产方写入 knowledge_update）；source_type VARCHAR(32)（'kb'）；source_id VARCHAR(32)（kbId）；target_user VARCHAR(32) + INDEX（**生产方不写=广播语义**，IngestionConsumer.java:470-481 实测未设）；status VARCHAR(16) DEFAULT 'unread' + INDEX（unread/read）；created_at + 倒序排序键。
Lifecycle：生产方 insert（knowledge 摄取完成）→ markRead 单条/read-all 批量（status=unread 条件更新）→ delete；查无静默成功。
Relationships：无外键；逻辑关联 kb（source_id）。
写入方：platform 侧仅 POST /api/notifications（测试/手工）；生产路径在 knowledge IngestionConsumer。

### sys_security_policy — 安全策略

id VARCHAR(32) PK；name VARCHAR(128) NOT NULL；policy_type VARCHAR(32) NOT NULL + INDEX；pattern VARCHAR(512)；action VARCHAR(16) DEFAULT 'block'；priority INT DEFAULT 0（创建缺省 0，列表升序）；enabled TINYINT DEFAULT 1（创建缺省 1）；description TEXT；created_at/updated_at。
Lifecycle：CRUD 全开放（update 全量覆写，含 enabled/priority）；〔决议 2026-09-24：执行语义裁定为「纯注册面，执行引擎不在 v1 交付范围」——pattern 预留为关键字/正则表达式、action 预留 block/warn/pass 三值语义（DDL 缺省 block），由未来执行引擎定义匹配顺序（priority 升序已备）；理由：全仓零消费方（grep 实测），无执行语义可考；现状差距：无消费方，design §4 深化项已登记（代裁，用户可推翻）〕。

### sys_publish_strategy — 发布策略

id VARCHAR(32) PK；name VARCHAR(128) NOT NULL；strategy_type VARCHAR(32) NOT NULL + INDEX；config JSON；priority INT DEFAULT 0；enabled TINYINT DEFAULT 1；description TEXT；created_at/updated_at。
Lifecycle：CRUD 全开放；〔决议 2026-09-24：config JSON 裁定为「结构由 strategy_type 约定、v1 无 schema、执行方不在交付范围」——若未来落地发布编排消费，消费点应在 publish-review 链 PublishManageModule 前置（与其发布计划执行器同批考虑）；理由：全仓零消费方，与 publish-review 链发布编排无代码关联（grep 实测）；现状差距：纯注册面无执行方，design §4 深化项已登记（代裁，用户可推翻）〕。

**Parent Trace:** model → platform-config.ModelRegistry（缝 1 生产方）→ REQ-A01~A06、REQ-C01~C02；model_training/model_test_report → Design.ModelTrainingFacade (S) → REQ-D01~D02；model_call_log → REQ-C03（死表登记）；sys_config/sys_config_history → platform-config.SysConfigStore（缝 4）→ REQ-E01~E06；sys_dictionary → Design.DictionaryModule (S) → REQ-G01~G03；sensitive_word → platform-config.SensitiveWordFilter（缝 2）→ REQ-H01~H08；term_library/term_record → platform-config.TermSynonymService（缝 3）→ REQ-I01~I05；query_rule → Design.QueryRuleModule (S) → REQ-J01；sys_notification → Design.NotificationModule (S) → REQ-K01~K03；sys_security_policy/sys_publish_strategy → Design.StrategyRegistryModule (S) → REQ-F01~F02。

---

## tools-skills-mcp

> 覆盖：fastrag-tools 模块拥有的 10 张表（tool / tool_http_config / skill / skill_dependency / skill_scope / mcp_service / mcp_tool / mcp_call_log / db_instance / db_table）。DDL 基线 schema.sql:411-520 与 :1268-1298；`creator/org_id/is_builtin` 等组织隔离列**仅存在于 SchemaInitializer 运行时增补**（SchemaInitializer.java:207-229,375-386），schema.sql 基线未同步——见文末「DDL 双处漂移登记」。技能磁盘形态（`{data-dir}/skills/{slug}/` 与 `skill_import_drafts/`）为运行时文件数据，无表结构，随 SkillFileVault 契约。

### tool — 自定义工具

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | ASSIGN_ID 雪花 ID〔Tool.java:43-44 ‹CERTAIN›〕 |
| name | VARCHAR(128) NOT NULL | 列表关键字筛选字段 |
| identifier | VARCHAR(128) | 工具标识；创建时缺省 `名称小写+空格转_`（ToolServiceImpl.java:126-128）；uploadPlugin 用 UUID 前 8 位 |
| description | TEXT | |
| type | VARCHAR(16) DEFAULT 'http' | http/plugin（importFromJson 可写任意值） |
| tags | JSON | 实体 String 存 JSON 串，API 层数组↔字符串自动转换〔Tool.java:74-98 ‹CERTAIN›〕 |
| icon | VARCHAR(512) | |
| enabled | TINYINT DEFAULT 1 | 启停开关；toggle 取反 |
| inputs / outputs | JSON | 输入/输出 JSON Schema（前端表单 + LLM function calling 双消费，ToolRegistry.java:69-70） |
| output_mapping | VARCHAR(512) | JSONPath（如 `$.data.temperature`），执行时提取 |
| creator / org_id / is_builtin | VARCHAR(32) / VARCHAR(32) / TINYINT DEFAULT 1 | **运行时增补列**（SchemaInitializer.java:375-377）；存量回填 creator='system'、is_builtin=1（:378） |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | schema.sql:424；实体有 createdAt |

Lifecycle：create/uploadPlugin/importFromJson（insert）→ update/toggleEnabled → delete（连带删 tool_http_config，ToolServiceImpl.java:186-192）。
Relationships：1—1 tool_http_config（经 tool_id=tool.id，应用层维护）；被 app_tool_binding 引用（app-center 域）；经 ToolRegistry 解析进 ToolDefinition。
种子：install_skill 等内置能力不在本表（静态定义，InstallSkillToolDefinition）。

### tool_http_config — 工具 HTTP 配置

tool_id VARCHAR(32) PK（与 tool.id 一对一）；method VARCHAR(8) DEFAULT 'GET'；url VARCHAR(512)（支持 `${}` 模板变量）；auth_type VARCHAR(16) DEFAULT 'none'（none/bearer/basic）；params / headers JSON（DB 存 JSON 串，API 层 `{key,value}` 数组转换，ToolHttpConfig.java:53-83）；body_type VARCHAR(32) DEFAULT 'none'（none/json/form-data/x-www-form-urlencoded/xml）；body TEXT；auth_value VARCHAR(512)（凭据，**仅属主可见**，非属主读取整个 httpConfig 置 null，ToolServiceImpl.java:52,62-63）。
Lifecycle：随工具 create/update 的 httpConfig 子对象 upsert（saveHttpConfigFromForm，ToolServiceImpl.java:231-254）；或经 api-config 端点独立 upsert；删工具连带删。
Relationships：1—1 tool。

### skill — 技能

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | ASSIGN_ID |
| slug | VARCHAR(128)（运行时增补列） | 唯一性约束：existsBySlug/selectBySlug 应用层查重（SkillMapper.java:8-9），**无 DB UNIQUE 索引**〔2026-10-06：白名单 `^[a-z0-9][a-z0-9._-]{0,63}$` 四入口强制（SlugValidator，M1-2）+ 普通索引 idx_slug（双处 DDL，非 UNIQUE——存量重复风险）；~~覆盖重装靠先删后插~~ 已改 update-in-place 保留主键（M3-5）〕 |
| name | VARCHAR(128) NOT NULL | 依赖校验/下拉选项按 name 匹配 |
| identifier | VARCHAR(128) | 旧版兼容 |
| description | TEXT | SKILL.md frontmatter 必填字段 |
| icon | VARCHAR(512) | |
| source / source_type | VARCHAR(16) DEFAULT 'custom'（source 基线；source_type 运行时增补） | 取值 builtin / remote / custom / upload |
| category | VARCHAR(64) | 列表筛选 |
| `trigger` | VARCHAR(256) | 触发关键词（保留字转义 @TableField，Skill.java:59-60） |
| content | TEXT | SKILL.md 原文 |
| code_type / code | VARCHAR(16) DEFAULT 'python' / TEXT | 旧版脚本位——写入方仅 update 表单透传（SkillServiceImpl.java:178-179），**执行消费方为零**（grep 全仓无任何执行器读取 getCode，技能运行时走 content/SKILL.md 与磁盘形态）‹CERTAIN（2026-09-24 深证）› |
| inputs / outputs | JSON（schema 基线）| 实体为 String（Skill.java:69-71）——类型口径不一致，未见消费方 |
| dir_path | VARCHAR(512)（运行时增补） | 内置技能磁盘目录‹CERTAIN（2026-09-24 深证）：SkillDataInitializer 复制目录但不回写 dirPath（grep 全文无 setDirPath），唯一写入方=update 表单透传（SkillServiceImpl.java:184），内置技能路径该列恒 NULL——slug 即磁盘关联键，本列实际冗余› |
| dependencies | JSON（运行时增补） | `type:name` 引用列表冗余列（updateDependencies 维护，SkillServiceImpl.java:288-296）；〔2026-10-06〕frontmatter tool/skill/mcp_dependencies 解析后亦写入（confirmDraft/启动播种，M3-2），运行时激活经规范化（`tool:xxx`→裸工具 id）后进 ToolRegistry 解析 |
| content_hash | VARCHAR(64)（运行时增补） | SKILL.md MD5，内置同步变更检测 |
| is_builtin | TINYINT DEFAULT 0（运行时增补） | =1 内置，禁止删除（SkillServiceImpl.java:248-250）；种子/存量回填置 1 |
| creator / org_id | VARCHAR(32)（运行时增补） | 存量回填 creator='system'（SchemaInitializer.java:382-384） |
| metadata | JSON（运行时增补） | frontmatter 全量元数据 |
| share_config | JSON（2026-10-06 M3-1 新增，schema.sql + SchemaInitializer 双处） | 三级分享 `{accessLevel: global/department/user, departmentIds, userUids}`；非管理员提交 global/department 强制降级 user；可见性 = DataScope 四态 ∪ 分享三级（SkillAccessManager.shareVisible 接入 SkillServiceImpl.visible） |
| enabled / recommended / usage_count | TINYINT DEFAULT 1 / TINYINT DEFAULT 0 / INT DEFAULT 0 | listAccessible 仅取 enabled=1 |
| author | VARCHAR(64) | 导入时写操作者（SkillDraftServiceImpl.java:195） |
| version | VARCHAR(16) | frontmatter 缺省 1.0.0 |
| updated_at / created_at | DATETIME（updated_at 基线带 ON UPDATE；created_at 运行时增补） | 列表排序键 |

Lifecycle：create（custom）→ update（含依赖/作用域全量替换；〔2026-10-06〕依赖写入先过 SkillDependencyValidator 校验，原「写入不校验」缺陷已修复）→ toggleEnabled/setEnabled → delete（内置拒绝，级联删 skill_dependency/skill_scope，SkillServiceImpl.java:244-255）；另一路径=安装流水线（upload 来源；~~覆盖重装删旧插新~~ 2026-10-06 起 **update-in-place 保留主键**（M3-5），新装写 creator/orgId（M1-3））；~~内置路径=启动增量同步（MD5 变化才 update，保留 enabled/shareConfig 用户字段，SkillDataInitializer.java:172-216）~~ 内置路径=**启动播种：DB 无该 slug 记录才 insert+复制目录，已有记录整体跳过**（2026-10-06 裁决 3A，取代 REQ-B05 MD5 更新语义——DB content 为唯一正文事实源，文件 API 编辑回写后不被启动覆盖）。
Relationships：1—N skill_dependency / skill_scope（经 skill_id，级联删除）；被 app_skill_binding 引用；磁盘目录 `skills/{slug}/` 与库记录同名对应（slug 为关联键）。
种子：classpath `builtin-skills/*`（code-generate / kb-qa / web-search 三目录实测）。

### skill_dependency — 技能依赖

id BIGINT AUTO_INCREMENT PK；skill_id VARCHAR(32) NOT NULL + INDEX idx_skill_id；type VARCHAR(16) NOT NULL（tool/model/mcp/skill）；name VARCHAR(128) NOT NULL（被依赖资源的 name，非 ID）；required TINYINT DEFAULT 1。
Lifecycle：随技能 create/update/updateDependencies 全量替换（deleteBySkillId 后逐条插，SkillServiceImpl.java:193-216）；删除技能级联清。
Relationships：N—1 skill；name 软引用 tool.name / mcp_service.name / skill.name（无外键，启用性由 SkillDependencyValidator 校验）。
校验口径（契约语义）：tool/mcp/skill 须存在且 enabled=1、skill 不得自引用、重复 type:name 报错、model 暂缓（SkillDependencyValidator.java:65-110）。

### skill_scope — 技能作用域

id BIGINT AUTO_INCREMENT PK；skill_id VARCHAR(32) NOT NULL + INDEX；scope_id VARCHAR(32)（作用域标识，如 Agent 配置 ID）；scope_name VARCHAR(128)；enabled TINYINT DEFAULT 1。
Lifecycle：与 skill_dependency 同构的全量替换（saveScopes）；级联删。
Relationships：N—1 skill。消费方：本模块内无读取消费（仅 getScopes 读回显，SkillServiceImpl.java:326-328）‹CERTAIN（2026-09-24 深证）：grep fastrag-agent/fastrag-application 零命中——作用域对运行时调用的实际约束未实现，app/agent 侧未消费本表（design §4 深化项登记）›。

### mcp_service — MCP 服务

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | ASSIGN_ID |
| slug | VARCHAR(128)（基线 VARCHAR(64)，Initializer 增补 VARCHAR(128)） | selectBySlug/existsBySlug 应用层查重；内置种子唯一键（slug+is_builtin=1） |
| name | VARCHAR(128) NOT NULL | 工具 ID 生成取 CamelCase（McpServiceServiceImpl.java:613-620） |
| transport | VARCHAR(16) DEFAULT 'sse'（运行时增补列） | stdio / sse；stdio 仅注册支持，连接被拒（McpProtocolClient.java:70-72） |
| mcp_url | VARCHAR(512) | sse/streamable HTTP 地址 |
| command / args | VARCHAR(512)（运行时增补）/ JSON | stdio 启动命令与参数 |
| env | JSON | 环境变量，**仅属主可见**（getWithTools/attachToolCounts 脱敏，McpServiceServiceImpl.java:90-94,516-521） |
| auth_type / auth_value | VARCHAR(16) DEFAULT 'none' / VARCHAR(256) | none/bearer/basic；authValue 凭据仅属主可见（同上） |
| status | VARCHAR(16) DEFAULT 'offline' | offline / online / error（refresh 成败流转） |
| enabled | TINYINT DEFAULT 1 | 种子更新不覆盖用户设置（McpBuiltinSeeder.java:104） |
| is_builtin | TINYINT DEFAULT 0（运行时增补） | =1 内置，禁止删除（McpServiceServiceImpl.java:265-267） |
| creator / org_id | VARCHAR(32)（运行时增补） | 存量回填 system/builtin（SchemaInitializer.java:379-381） |
| config_hash | VARCHAR(64)（运行时增补） | 同步检测预留‹CERTAIN（2026-09-24 深证）：全仓无写入方（grep 无 setConfigHash），唯一读取=serviceToMap 回显（McpServiceServiceImpl.java:546），恒 NULL——实际同步检测走种子器侧比对（McpBuiltinSeeder 元数据更新），不走本列› |
| metadata | JSON | description 等扩展‹CERTAIN（2026-09-24 深证）：实体 description 为 @TableField(exist=false) 非表字段（McpService.java:40-41），serviceToMap 仅输出 metadata 键不含 description（McpServiceServiceImpl.java:528-551）——description 前端可见性依赖其被写入 metadata JSON› |
| last_used | DATETIME | 执行/测试/刷新成功回写 |
| created_at / updated_at | DATETIME | 列表按 createdAt 倒序 |

Lifecycle：create（status=offline）→ update → toggleEnabled → refresh（tools 全量替换+状态流转）→ delete（内置拒绝）；内置路径=启动种子增量同步（McpBuiltinSeeder，新建/元数据更新）。
Relationships：1—N mcp_tool（service_id）；1—N mcp_call_log（service_id）；被 app_mcp_binding 引用。
种子：filesystem（npx server-filesystem，enabled=1）/ fetch（uvx，enabled=0）/ sequential-thinking（npx，enabled=0）三个内置服务〔McpBuiltinSeeder.java:42-70 ‹CERTAIN›〕。

### mcp_tool — MCP 工具

id BIGINT AUTO_INCREMENT PK；service_id VARCHAR(32) NOT NULL + INDEX idx_service_id；name VARCHAR(128) NOT NULL（MCP Server 原始工具名）；tool_id VARCHAR(128)（基线；Initializer 增补至 VARCHAR(256)）= `mcp__{服务Camel}__{工具Camel}` 格式化 ID；description TEXT；params JSON（工具参数 JSON Schema，前端数组自动归一）；enabled TINYINT DEFAULT 1（运行时增补列，toggleTool/解析过滤依赖）。
Lifecycle：refresh 全量替换（先删该服务全部再插新发现，McpServiceServiceImpl.java:342-356）；手动 addTool/updateTool/deleteTool 单条维护；名称变更时 tool_id 重算（:419-425）。
Relationships：N—1 mcp_service。运行时经 ToolRegistry 以 `mcp_{id}` 引用解析（enabled=1 过滤）。

### mcp_call_log — MCP 调用日志

id BIGINT AUTO_INCREMENT PK；service_id VARCHAR(32) NOT NULL + INDEX；caller VARCHAR(64)（调用者 appId，缺省 "unknown"）；tool VARCHAR(128)；status VARCHAR(16)（success/error）；duration INT（毫秒）；timestamp DATETIME DEFAULT CURRENT_TIMESTAMP。
Lifecycle：只写（McpToolExecutor.java:106-120，写失败仅告警）；‹CERTAIN（2026-09-24 深证，**改正原断言**）：**全仓无任何读取方**——grep 实测 fastrag-operation 零引用 McpCallLog/mcp_call_log（UnifiedLogServiceImpl 仅路由 audit/login/operation 三类，:40-44），原「查询归 operation-analytics」与 feature-map「Shared by」的说法与代码不符；本表当前为只写死表，消费方缺位登记为债务（operation 域如需 MCP 调用分析需先建查询端点）›。

### db_instance — 外部数据库实例

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | ASSIGN_ID |
| name | VARCHAR(128) NOT NULL | 列表关键字筛选 |
| description | TEXT | |
| db_type | VARCHAR(16) NOT NULL | mysql/postgresql|postgres/h2/oracle/sqlserver|mssql/其他（回退 mysql URL 拼接，DbInstanceServiceImpl.java:393-401） |
| host / port / db_name | VARCHAR(128) / INT / VARCHAR(128) | host 缺省 127.0.0.1；port 按类型缺省（mysql 3306/pg 5432/oracle 1521/mssql 1433/h2 -1） |
| username / password | VARCHAR(64) / VARCHAR(256) | 密码**仅属主可见**（非属主 `******`） |
| jdbc_url | VARCHAR(512) | 可手工传或自动拼接；**仅属主可见**（非属主 null）；host/port/dbName 变更时重拼 |
| pool_config | JSON | ‹CERTAIN（2026-09-24 深证）：实体有字段（DbInstance.java:24），全仓无读取方——连接池参数实际硬编码 max 3/min idle 1/5s 超时（createDataSource，DbInstanceServiceImpl.java:365-382），本列为不生效的预留位；若需用户态调池参数，消费点应在 createDataSource 读取并合并本列› |
| read_only | TINYINT DEFAULT 1 | =1 时连接设 readOnly（createDataSource） |
| status | VARCHAR(16) DEFAULT 'connected'（DDL 基线）/ disconnected（create 缺省） | connected/disconnected（test/sync 回写）——**DDL 缺省值与代码缺省值不一致**〔schema.sql:1282 vs DbInstanceServiceImpl.java:87 ‹CERTAIN›〕 |
| created_by / org_id | VARCHAR(32)（org_id 运行时增补） | 存量回填 created_by='system'（SchemaInitializer.java:385-386）；可见性按 created_by 判定 |
| created_at / updated_at | DATETIME | 列表按 createdAt 倒序 |

Lifecycle：create → update → testConnection/syncTables（状态回写）→ query（只读白名单）→ delete（级联删 db_table）。
Relationships：1—N db_table（db_id）；被 app_db_binding 引用。

### db_table — 外部数据库表元数据

id VARCHAR(32) PK（ASSIGN_ID）；db_id VARCHAR(32) NOT NULL + INDEX idx_db_id；table_name VARCHAR(128) NOT NULL（同库内 upsert 键，无 DB 唯一约束——应用层 selectOne 判重）；table_comment VARCHAR(256)；columns JSON（列信息数组：name/type/size/nullable/comment）；row_count BIGINT（`SELECT COUNT(*)` 实测值）；enabled TINYINT DEFAULT 1；synced_at DATETIME。
Lifecycle：createTable（手工登记）→ syncTables upsert（幂等刷新）→ 随实例删除级联删。
Relationships：N—1 db_instance。

### DDL 双处漂移登记（tools-skills-mcp 域，conventions.md 铁律现状）

- schema.sql 基线**缺失**、仅 SchemaInitializer 运行时增补的列：skill（slug/source_type/dir_path/dependencies/content_hash/is_builtin/metadata/creator/org_id/created_at，SchemaInitializer.java:207-214,382-384）、mcp_service（slug/transport/command/args/env/is_builtin/config_hash/metadata/creator/org_id/updated_at，:217-225,379-381）、mcp_tool（tool_id/enabled，:228-229）、tool（creator/org_id/is_builtin，:375-377）、db_instance（org_id，:385）。新建环境由 schema.sql 建表后 Initializer 补列，行为一致但**基线文件不是完整结构**〔CERTAIN〕。
- SkillAccessManager.selectCreatedBy 查询 `skill.created_by` 列——该列既不在 schema.sql 也未被 Initializer 增补（实际增补名为 `creator`），SQL 必失败并被 try/catch 吞为空串〔SkillMapper.java:14、SchemaInitializer.java:382、SkillAccessManager.java:91-97 ‹CERTAIN›；后果见 design §1 SkillAccessManager (S)〕。

**Parent Trace:** tool/tool_http_config → Design.HttpToolModule → REQ-A01~A05、REQ-A07；skill → Design.SkillModule → REQ-B01~B05、REQ-B09~B10；skill_dependency → Design.SkillDependencyValidator → REQ-B08；skill_scope → Design.SkillModule → REQ-B03（消费缺位见 design §4）；mcp_service/mcp_tool → Design.McpModule + McpProtocolClient（缝 2）→ REQ-C01~C04、REQ-C06~C07、REQ-C09；mcp_call_log → Design.McpToolExecutor（缝 1 适配器）→ REQ-C08；db_instance/db_table → Design.DbInstanceModule（DbQueryGateway）→ REQ-D01~D06。

---

## workflow

> 覆盖：application 模块工作流域全部 8 张表（1 主表 workflow + 7 张 wf_* 子表，schema.sql:399-410,1181-1265；feature-map 记「7 张」漏计主表）。DDL 全部仅在 schema.sql 基线（SchemaInitializer 无 workflow 专属增补，grep 实测），但存在漂移与种子缺陷，见文末登记。`wf_debug_log`/`wf_monitor_data` 为**死表**：DDL/实体/Mapper 齐备，全链无业务读写方（仅 AppModuleValidationTest 引用）〔WfDebugLogMapper.java:6、WfMonitorDataMapper.java:6、grep 实测 ‹CERTAIN›〕。

### workflow — 工作流定义（主表）

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | MyBatis-Plus ASSIGN_ID |
| name | VARCHAR(128) NOT NULL | 创建必填（控制器直取 form.name，无后端非空校验——null 落库由 DB 拒绝）〔WorkflowServiceImpl.java:15 ‹CERTAIN›〕 |
| description | TEXT | 可选 |
| status | VARCHAR(16) DEFAULT 'draft' | 取值 draft/published 二值；create 恒置 draft、publish 翻 published（无其他流转）〔:15,18 ‹CERTAIN›〕 |
| nodes | JSON | **画布整图序列化**：前端 JSON 数组字符串直存（create 恒 "[]"）；与 wf_node 行双轨并存无同步〔:15-16 ‹CERTAIN›〕 |
| edges | JSON | 边数组字符串直存（create 恒 "[]"）；无独立边表——边只存在于此列〔:15-16、schema.sql:1181-1265 无 wf_edge ‹CERTAIN›〕 |
| created_by | VARCHAR(32) | **无写入方**：create 不设置（种子数据手工赋值 user_admin）〔WorkflowServiceImpl.java:15、data.sql:561 ‹CERTAIN›〕 |
| created_at / updated_at | DATETIME DEFAULT CURRENT_TIMESTAMP（updated ON UPDATE） | — |

Lifecycle：create（draft 空图）→ update（name/nodes/edges 字段选择覆盖）→ publish（状态位翻转，幂等）→ delete（物理删，**不级联**子表）。
Relationships：1—N wf_node（workflow_id，无外键、应用层维护）；1—N wf_test_case/wf_optimization（同上）；不挂 KB/App（app 侧经 app.type 或 advancedOptions.workflow 弱引用）。
种子：wf_refund_demo（published，5 节点 4 边）〔data.sql:561-573 ‹CERTAIN›〕。

### wf_node — 画布节点

id VARCHAR(32) PK（ASSIGN_ID）；workflow_id VARCHAR(32) NOT NULL + INDEX idx_workflow_id；node_key VARCHAR(64) NOT NULL（复合唯一键之一，同工作流内业务标识）；node_type VARCHAR(32) NOT NULL（**无值域校验**——任意字符串，种子示例 start/intent/kb_retrieval/llm/end）；name VARCHAR(128)；config JSON（节点扩展配置**单列**——9 个 API 维度全部折叠到此列，序列化为 Map.toString() 非 JSON）；position_x/position_y INT DEFAULT 0（画布坐标）；next_nodes JSON（**无写入方**：实体有字段、代码从不设置）；enabled TINYINT DEFAULT 1（addNode 恒置 1，无启停端点）；created_at DATETIME。UNIQUE INDEX idx_wf_key (workflow_id, node_key)〔schema.sql:1182-1196、WorkflowServiceImpl.java:20-26,56-61、WfNode.java ‹CERTAIN›〕。
Lifecycle：addNode → updateNode/moveNode/saveNodeConfig（config 单列覆盖）→ deleteNode（物理删；父工作流删除时**不级联**，成孤儿）。
Relationships：N—1 workflow。

### wf_test_case — 工作流测试用例

id VARCHAR(32) PK（ASSIGN_ID）；workflow_id VARCHAR(32) NOT NULL（无索引）；name VARCHAR(256)；query TEXT；inputs JSON；expected_output TEXT；actual_output TEXT（**无写入方**——用例无执行入口）；matched TINYINT（**无写入方**——比对逻辑缺失，spec REQ-D03）；created_by VARCHAR(32)（无写入方）；created_at DATETIME〔schema.sql:1209-1220、WorkflowServiceImpl.java:31-33、grep 实测 ‹CERTAIN›〕。
Lifecycle：create（强制绑 workflow_id）→ list（按 workflow_id）→ delete（按主键直删，**不校验归属**——任意 tcId 可删任意工作流的用例）。
Relationships：N—1 workflow。

### wf_template — 工作流模板

id VARCHAR(32) PK（ASSIGN_ID）；name VARCHAR(128) NOT NULL；category VARCHAR(32)；description TEXT；canvas_data LONGTEXT（画布预设串，种子为 '{}'——模板不含节点行实例化）；is_builtin TINYINT DEFAULT 0（create 缺省 0；**=1 无删除保护**）；created_by VARCHAR(32)（无写入方）；created_at DATETIME〔schema.sql:1198-1207、WorkflowServiceImpl.java:35-38 ‹CERTAIN›〕。
Lifecycle：create → update（按 id 全量覆盖）→ delete（内置亦可删）。无「模板→工作流」展开消费。
Relationships：无外键关联（独立台账）。

### wf_debug_log — 工作流调试日志（**死表**）

id BIGINT AUTO_INCREMENT PK；workflow_id VARCHAR(32) NOT NULL；node_key VARCHAR(64)；level VARCHAR(16) DEFAULT 'debug'；message TEXT；context JSON；created_at DATETIME〔schema.sql:1222-1230 ‹CERTAIN›〕。
Lifecycle：**无**——Mapper 定义后全链零读写（getDebugInfo 返回硬编码假日志，WorkflowServiceImpl.java:40-44）；引擎实装时的激活点。
Relationships：N—1 workflow（设计意图）。

### wf_monitor_data — 工作流监控数据（**死表**）

id VARCHAR(32) PK（ASSIGN_ID）；workflow_id VARCHAR(32) NOT NULL；metric_type VARCHAR(32)；metric_value DECIMAL(18,4)；**dimension VARCHAR(32)**（⚠ 实体 WfMonitorData 与 schema-h2.sql:504 有此列，**schema.sql 基线缺失**——DDL 漂移，见文末登记）；period_start/period_end DATETIME；details JSON〔schema.sql:1232-1240、WfMonitorData.java、schema-h2.sql:499-508 ‹CERTAIN›〕。
Lifecycle：**无**——getMonitorData 返回硬编码指标（3200/1200/0.02，WorkflowServiceImpl.java:63，被 TC-MOCK-003 固化）。
Relationships：N—1 workflow（设计意图）。

### wf_optimization — 工作流优化建议

id VARCHAR(32) PK（ASSIGN_ID）；workflow_id VARCHAR(32) NOT NULL；suggestion_type VARCHAR(32)；title VARCHAR(256)；description TEXT；impact_score DECIMAL(5,2)（实体 Double）；status VARCHAR(16) DEFAULT 'pending'（取值 pending/applied；apply 翻转，无回退）；before_metric/after_metric JSON（无写入方）；created_at DATETIME〔schema.sql:1242-1252、WorkflowServiceImpl.java:49-51 ‹CERTAIN›〕。
Lifecycle：create（status 缺省 pending）→ list（按 workflow_id）→ apply（status→applied，**无副作用**——不改工作流定义）。无自动分析生成入口（区别 app-center optimizations 的 analyze）。
Relationships：N—1 workflow。

### wf_migration — 工作流迁移记录

id VARCHAR(32) PK（ASSIGN_ID）；source_workflow_id/target_workflow_id VARCHAR(32)（**不引用既有工作流**——无存在性校验）；target_env VARCHAR(32)；strategy VARCHAR(32) DEFAULT 'overwrite'；status VARCHAR(16) DEFAULT 'running'（**永不推进**——无后续写入口、无终态）；progress INT DEFAULT 0（同上永不推进）；validate_result JSON（无写入方）；operator VARCHAR(32)（无写入方）；created_at DATETIME〔schema.sql:1254-1265、WorkflowServiceImpl.java:53-54 ‹CERTAIN›〕。
Lifecycle：create（纯台账）→ list（created_at 倒序）。**无迁移执行逻辑**——source/target 数据不被搬运。
Relationships：逻辑上 N—2 workflow（source/target），实际无约束无校验。

### DDL 双处漂移与种子缺陷登记（workflow 域，conventions.md 铁律现状）

- **wf_monitor_data.dimension 列漂移**：实体 WfMonitorData 与测试基线 schema-h2.sql:504 均有 dimension 列，**init-scripts/schema.sql:1232-1240 基线缺失**——MySQL 下对 wf_monitor_data 的任何 MyBatis-Plus 查询将因未知列失败（现状因死表无查询而未引爆）〔‹CERTAIN›〕。
- **schema-h2.sql workflow 表漂移**：测试基线比 MySQL 基线多 category/version 两列（schema-h2.sql:9,13 vs schema.sql:399-410）——H2 测试环境与生产结构不一致〔‹CERTAIN›〕。
- **data.sql 种子列错位（必失败）**：wf_optimization 种子 INSERT 引用列 (name, suggestion, status, created_by)（data.sql:581），而表实有列含 suggestion_type/title/description/status/created_at——name/suggestion/created_by 三列不存在，**该种子语句在 MySQL 下必报 Unknown column**（与 kb-core 链登记的 data.sql:535 缺陷同类）〔data.sql:581 vs schema.sql:1242-1252 对照 ‹CERTAIN›〕。
- **api:workflow:* 权限种子无消费方**：data.sql:285-293 播种九个 API 级权限键，控制器实际使用 workflow:edit/create/delete/publish/run（data.sql:88-92）——两套键不同名，api:* 键挂空〔WorkflowController.java:35-124 对照 ‹CERTAIN›〕。

**Parent Trace:** workflow → Design.WorkflowLifecycleModule → REQ-A01~A05；wf_node → Design.CanvasNodeModule（契约 workflow.CanvasNodeStore）→ REQ-B01~B06；wf_test_case/wf_template/wf_optimization/wf_migration → Design.WfAssetModule → REQ-D01~D03、REQ-E01~E03、REQ-F01~F02；wf_debug_log/wf_monitor_data → Design.WfObservabilityStub + WorkflowExecutionEngine（死表登记）→ REQ-C04、REQ-F03~F04。

---

## agent

> 覆盖：智能体域全部 3 张表（agent + agent_run + sandbox_config，2026-09-28 增补）与 1 组 Redis 运行时结构（sandbox 绑定存储）。**DDL 双处同步缺口（本域特有）**：agent/agent_run 两表建表 DDL **仅存在于 SchemaInitializer.java:149-204**（运行时幂等 CREATE TABLE IF NOT EXISTS），init-scripts/schema.sql 全量基线 grep "agent" 零命中——违反「DDL 双处同步」硬约定；新环境若只跑 schema.sql 将缺表（运行时由 SchemaInitializer 兜底建表，故运行期无感）〔SchemaInitializer.java:10,146-204、schema.sql 全文 grep ‹CERTAIN›〕。**sandbox_config 为新表，DDL 必须双处同步落库（schema.sql + SchemaInitializer），勿重蹈 agent/agent_run 覆辙**（REQ-H04；2026-09-28 grill 裁决）。
> 另：SchemaInitializer javadoc 自述为 agent 添加组织隔离列 org_id（:11），实际代码只对 kb/kb_category 执行（:245-248）——**agent 表无 org_id**，javadoc 超述〔‹CERTAIN›〕。

### agent — 智能体定义

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(64) PK | 实体无显式 id 赋值点（insert 交由 MyBatis-Plus 全局策略）；全局 id 生成器 = **ASSIGN_ID（雪花）**——application.yml:69 `id-type: assign_id`（mybatis-plus.global-config.db-config）‹CERTAIN（2026-09-24 补证）›〔Agent.java:51、AgentServiceImpl.create 无 setId ‹CERTAIN›〕 |
| slug | VARCHAR(128) NOT NULL，UNIQUE uk_slug | 人读标识；创建时空值按名称驼峰转小写派生，冲突追加 -N 后缀；寻址双轨（slug 或 id）〔AgentServiceImpl.java:74-79,235-243、AgentController.java:107-117 ‹CERTAIN›〕 |
| name | VARCHAR(256) NOT NULL | 创建必填 |
| backend_id | VARCHAR(128)，INDEX idx_backend_id | Backend 类型引用（ChatbotAgent / SubAgentBackend）；无外键、无存在性校验（悬空值运行时 ContextBuilder 报 notFound）〔ContextBuilder.java:67-70 ‹CERTAIN›〕 |
| description | TEXT | 可选 |
| icon | VARCHAR(512) | 可选 |
| pics | JSON（实体 List<String>） | **死列**：create/update DTO 均无此字段，无写点〔Agent.java:64、AgentCreateDTO/AgentUpdateDTO 字段清单 ‹CERTAIN›〕 |
| config_json | JSON | 智能体配置；键集=BaseContext @ConfigField 字段名（systemPrompt/model/tools/skills/mcps/databases/knowledges/maxSteps/summaryThreshold/modelRetryTimes 等）；未知键经 updateFromMap 静默忽略，无 schema 校验〔BaseContext.java:47-87,115-139 ‹CERTAIN›〕 |
| share_config | JSON | {access_level: global/department/private, department_ids: [], user_uids: []}；非管理员写入强制降级 private 并清空名单〔AgentServiceImpl.java:246-262 ‹CERTAIN›〕 |
| is_default | TINYINT DEFAULT 0，INDEX idx_is_default | 默认标记；互斥由应用层维护（setDefault 先全表清旧再置位，非同一原子操作）〔:155-167 ‹CERTAIN›〕 |
| is_subagent | TINYINT DEFAULT 0 | 子智能体标记；create 恒置 false，仅内置初始化路径可传 true〔:90,205-232 ‹CERTAIN›〕 |
| created_by / updated_by | VARCHAR(64) | uid 或 "system"（内置）/"anonymous"（无用户上下文） |
| created_at / updated_at | DATETIME DEFAULT CURRENT_TIMESTAMP（updated ON UPDATE） | 应用层同时显式赋值 |

Lifecycle：create（slug 唯一化 + shareConfig 规范化）→ update（字段级选择覆盖）→ setDefault（全表清旧+置位）→ delete（物理删；内置 6 slug 拒绝 409；**不级联** agent_run 等从属数据）。启动自愈：ensureDefaultAgent（无默认即建 default-chatbot，access_level=global）+ AgentDataInitializer（ApplicationReadyEvent，Order 晚于 SchemaInitializer 建表）幂等补种〔AgentServiceImpl.java:35-46,205-232、AgentDataInitializer.java:34 ‹CERTAIN›〕。
Relationships：1—N agent_run（agent_id 值为 **slug 或 id 双形态**——外部创建落 dto.agentId（slug），子智能体落 subAgent.getId()；无外键）〔AgentRunServiceImpl.java:83、SubAgentMiddleware.java:177 ‹CERTAIN›〕；backend_id → AgentBackendManager 内存注册表（非表引用）。
内置种子：BUILTIN_SLUGS 白名单 6 项（default-chatbot/general-purpose/web-search/deep-research/research-explorer/fact-verifier）〔:29-32 ‹CERTAIN›〕；default-chatbot 有 ensureDefaultAgent 兜底播种〔:41-42 ‹CERTAIN›〕；**其余 5 项存在开机播种**——AgentDataInitializer.onApplicationReady（@EventListener(ApplicationReadyEvent) + @Order(10)，晚于 SchemaInitializer 建表）对全部 6 slug 逐一调用 ensureBuiltinAgent（:39-108），其中 web-search/deep-research/research-explorer/fact-verifier 播种为 isSubagent=true‹CERTAIN（2026-09-24 深证：AgentDataInitializer.java:33-108）›。

### agent_run — 智能体运行记录

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(64) PK | IdUtil.fastSimpleUUID（32 位）〔AgentRunServiceImpl.java:81 ‹CERTAIN›〕 |
| thread_id | VARCHAR(64) NOT NULL，INDEX idx_thread_id + idx_thread_status(thread_id,status) | 会话线程；「同线程至多一个活跃 run」由应用层查询约束（无 DB 唯一索引防并发双活跃）〔AgentRunMapper.java:30-31 ‹CERTAIN›〕 |
| agent_id | VARCHAR(64) NOT NULL，INDEX idx_agent_id | slug/id 双形态（见 agent 表 Relationships） |
| uid | VARCHAR(64) | 发起用户；缺省 "anonymous"〔:84 ‹CERTAIN›〕 |
| request_id | VARCHAR(128)，UNIQUE uk_request_id | 幂等键；缺省自动 UUID；子智能体直插时也生成〔:70-77、SubAgentMiddleware.java:180 ‹CERTAIN›〕 |
| input_payload | JSON | createRun 路径 {query, model_override}；子智能体路径 {query, description}；引擎查询提取回退链 query→message→input→toString〔:89-92、AgentEngine.java:815-828 ‹CERTAIN›〕 |
| status | VARCHAR(32) DEFAULT 'pending'，INDEX idx_status | 状态机 {pending,running,completed,failed,cancelled,interrupted}；终态集={completed,failed,cancelled,interrupted}〔AgentRunServiceImpl.java:253-256 ‹CERTAIN›〕 |
| run_type | VARCHAR(32) DEFAULT 'chat' | chat（外部创建，缺省）/ subagent（内部直插）〔:87、SubAgentMiddleware.java:182 ‹CERTAIN›〕 |
| parent_agent_run_id | VARCHAR(64) | **死列**：全链无 setParentAgentRunId 写点——父子运行关联只在内存 SubAgentRunState，落库后不可追溯〔grep setParentAgentRunId 零命中、SubAgentRunState.java:16-38 ‹CERTAIN›〕 |
| conversation_id | BIGINT | **死列**：无写点（应用对话走 fakeRun，亦不落库）〔grep setConversationId 零命中 ‹CERTAIN›〕 |
| checkpoint_thread_id | VARCHAR(128) | **死列**：无写点（检查点能力预留）〔grep setCheckpointThreadId 零命中 ‹CERTAIN›〕 |
| error_message / error_type | TEXT / VARCHAR(64) | 终态 failed 时成对写入；error_type ∈ {execution_error, system_error}〔AgentRunServiceImpl.java:207-226 ‹CERTAIN›〕 |
| created_at / updated_at / finished_at | DATETIME | finished_at 仅终态（completed/failed/cancelled）写入 |
| （无 output/result 列） | — | **最终回答不落库**：引擎产物只进内存事件留痕（RunEventPublisher）与应用侧消息表；run 可追溯的仅状态与错误〔实体字段清单 + grep 零命中 ‹CERTAIN›〕 |

Lifecycle：createRun（pending）→ running（异步线程首步）→ completed｜failed（+error 二元组）；cancelled 仅可从 pending/running 进入，为纯 DB 翻转（无执行线程中断）。createRunDirect 为内部直插旁路（子智能体，不触发异步执行）。终态无清理任务，历史 run 永久留存。
Relationships：N—1 agent（agent_id，无外键）；自引用 parent_agent_run_id（设计意图，当前零写点）；thread_id 对应用侧会话为字符串弱引用（应用对话**不产生** run 记录，见 interfaces.md `## agent` 跨链登记）。

**Parent Trace:** agent → Design.AgentRegistryModule → REQ-A01~A09、REQ-B01~B03、REQ-I01~I02；agent_run → Design.RunOrchestrationModule（契约 agent.AgentRunGateway）→ REQ-C01~C08；死列三件套（parent_agent_run_id/conversation_id/checkpoint_thread_id）与 pics → Design §4 Deepening → REQ-C07 关联。

### sandbox_config — 沙箱后端配置（新表，2026-09-28）

| 字段 | 类型/约束 | validation 与说明 |
|---|---|---|
| id | VARCHAR(32) PK | 雪花 ASSIGN_ID（对齐本仓全局 id 策略） |
| user_id | VARCHAR(32) NOT NULL，INDEX idx_user_id | 归属者（创建者）；写权限 = 归属者或管理员（REQ-H04）；跨链：app.sandbox_config_id 引用本表（弱引用无外键，字段归 app-center 链） |
| name | VARCHAR(128) NOT NULL | 人读名；同 user_id 下唯一（应用层校验，无 DB 唯一索引） |
| sandbox_type | VARCHAR(16) NOT NULL | ∈ {docker, cube, e2b, disabled}；disabled 为显式关闭态保留行（REQ-H04；host 不做——桌面客户端专属，2026-09-28 裁决） |
| enabled | TINYINT DEFAULT 1 | 配置级细粒度开关；平台总开关（REQ-H07）是独立上层 |
| default_timeout_sec | INT DEFAULT 0 | 单次执行超时；0 = 内置默认 60s（对齐 WeKnora DefaultTimeout） |
| allow_private_endpoints | TINYINT DEFAULT 0 | 私网 endpoint 显式放行（REQ-H16）；云 metadata/link-local 恒拒，与本位无关 |
| network_policy | JSON | Cube/E2B：{allow_internet, allow_domains[], deny_domains[], inbound_closed}；**docker 配置携带域名列表时保存被拒**（E_SANDBOX_CONFIG_INVALID，REQ-H15——Docker 仅 L3/L4，域名策略不可兑现） |
| env_vars | JSON | 注入沙箱的环境变量（非密钥类；密钥走 *_config 内引用字段） |
| docker_config | JSON | {image, host, tls_cert_path, cpu_limit, memory_limit_mb, pids_limit, network_mode ∈ {bridge,none}, idle_ttl_sec}；network_mode 缺省 bridge（REQ-H04、H15） |
| cube_config | JSON | {api_url, proxy_url, sandbox_domain, api_key_ref, template_id, http_timeout_sec, ttl_sec, dns_servers[]}（REQ-H04） |
| e2b_config | JSON | {api_url, sandbox_domain, api_key_ref, template_id, proxy_url, http_timeout_sec, ttl_sec}（REQ-H04） |
| identity_fingerprint | VARCHAR(64) | 身份要素（sandbox_type + endpoint + api_key_ref + 镜像/模板）确定性哈希；update 重算，供绑定侧 REQ-H09 变更重建比对；非身份字段变更不动此列 |
| cordon_at | DATETIME NULL | 隔离标记：换凭据/换底模时置位（该配置不再新建实例，存量下次 resolve 重建）；NULL = 正常 |
| created_at / updated_at | DATETIME DEFAULT CURRENT_TIMESTAMP（updated ON UPDATE） | |

Lifecycle：create（校验链：类型合法 → provider 块必填性 → 网络策略与类型匹配 → EndpointDialGuard.validateEndpoint → 凭据引用名格式）→ update（身份字段变更重算 fingerprint + 触发绑定 stale；空 api_key_ref 不覆盖既有引用，ADR-0007）→ delete（**库存 >0 含 paused 拒绝**（E_SANDBOX_CONFIG_IN_USE）；无实例物理删，历史实例由孤儿对账按宽限期回收，不级联删容器）。密钥值任何读取路径不出服务端（脱敏恒开，REQ-H06）。

Relationships：N—1 user（user_id，无外键）；1—N app.sandbox_config_id（弱引用，app-center 链）；1—N sandbox 绑定（Redis binding.configId，运行时结构）；绑定删除不级联本表、本表删除不级联绑定（孤儿对账兜底）。

**Parent Trace:** sandbox_config → Design.SandboxConfigModule（契约 agent.SandboxConfigStore）→ REQ-H04~H07、H15、H16。

### sandbox 绑定存储（Redis 运行时结构，无表；2026-09-28 新增）

- Key：`sandbox:binding:{userId}:{sessionId}`（String→JSON 值）。
- Value：`{version: 1, provider: docker|cube|e2b, sandboxId, configId, createdAt, staleAt?}`——version 为结构版本位；staleAt 非空表示绑定对应配置身份已变更，下次使用销毁重建（REQ-H09）。
- 写语义：rebind 走 CAS（compare-and-set 旧 sandboxId），防并发换绑丢更新（对齐 WeKnora session_binding）。
- TTL：**不设 Redis 过期**——绑定存活由 sandbox_config 的 idle_ttl 与 reclaimIdle 驱动删除；Redis 过期会造成「provider 侧实例仍在、绑定已失」的泄漏形态（由孤儿对账兜底回收）。
- Lifecycle：acquire 未命中时创建 → casRebind（身份变更重建）/ markStale（配置变更批量标记）/ delete（release、会话删除、空闲回收）。

**Parent Trace:** sandbox 绑定存储 → Design.SandboxLifecycleModule（附属缝 SandboxBindingStore：Redis + 内存双适配器）→ REQ-H01、H09、H11。

## app-center

> 对应 design 模块：AppLedgerModule / AppDialogOrchestrator / AppConversationLedger / AppConfigFacade / AppBindingRegistry / AppPolicyModule（design/app-center.md §1）。接口契约见 interfaces.md `## app-center` 节。
> 表结构实读 init-scripts/schema.sql（app 主表 :361-372、app_config/app_template :374-394、其余 app_* :938-1179）与实体类。**DDL 双处同步活例**：`app.org_id` 不在全量 schema.sql（:361-372 无此列），由 SchemaInitializer 运行时幂等增补（SchemaInitializer.java:373 `addColumnIfNotExists("app","org_id",...)`）〔‹CERTAIN›〕；实体 App.orgId 依赖该列（App.java:35、AppServiceImpl.java:127,148）。

### app（应用台账）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | VARCHAR(32) | PK（雪花 ASSIGN_ID） | 应用 ID |
| name | VARCHAR(128) | NOT NULL | 名称；list 支持 LIKE 筛选 |
| description | TEXT | | 描述；update 唯二可改字段之一 |
| type | VARCHAR(32) | DEFAULT 'ChatBot' | 应用类型（schema.sql:365）；javadoc 另举 chat_agent/workflow〔App.java:14 ‹CERTAIN›，值域无约束校验〕 |
| icon | VARCHAR(512) | | 图标 |
| tags | JSON | | 标签；list 按标签 LIKE；@JsonIgnore+@JsonProperty setter 兼容非 JSON 入参〔App.java:36-39 ‹CERTAIN›〕 |
| status | VARCHAR(16) | DEFAULT 'draft' | 创建固定写 'draft'〔AppServiceImpl.java:146 ‹CERTAIN›〕；无状态流转代码（发布写 app_publish_record 不改本列〔AppConfigServiceImpl.java:166 仅 setAppId/setPublishedAt 后 insert，无 app 表写入 ‹CERTAIN（2026-09-24 深证）›〕） |
| owner | VARCHAR(32) | | 属主用户 ID；DataScope 判定键 |
| org_id | VARCHAR(32) | **仅运行时增补**（SchemaInitializer.java:373） | 组织隔离键；list 的同组织可见分支 |
| created_at / updated_at | DATETIME | 默认当前时间/自动更新 | |

Lifecycle：create（draft）→ update（部分语义）→ delete（**物理删除，不级联**——config/绑定/会话等子表行残留为孤儿数据〔AppServiceImpl.java:165-170 仅 deleteById ‹CERTAIN›〕）。〔决议 2026-09-24：级联清理目标语义取「删除应用时级联物理删除全部 app_* 子表行（config/basic/dialog/policy/variable/trigger/五绑定/publish_record/dialog_test/optimization/auto_update/conversation+message）」，理由：与应用自身物理删语义及会话级联删消息的既有先例一致，防孤儿累积；现状差距：代码未实现级联（AppServiceImpl.java:165-170 仅 deleteById）——已登记债务见 implement/app-center.md §2（代裁，用户可推翻）〕
Relationships：1—N 全部 app_* 子表（经 app_id，全部无外键，应用层维护）；N—1 user（owner）。

**Parent Trace:** app → Design.AppLedgerModule → REQ-A01~A04、NFR-02。

### app_config（智能体配置，每应用一行）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | VARCHAR(32) | PK | |
| app_id | VARCHAR(32) | NOT NULL **UNIQUE**（schema.sql:376） | 一应用一配置 |
| model | VARCHAR(128) | | 模型路由 code；对话时解析 ModelRecord（code+status='online'） |
| prompt | TEXT | | 系统提示词 |
| temperature | DECIMAL(3,2) | DEFAULT 0.70 | |
| knowledge_ids / tool_ids | JSON | | 旧版直存 ID 列（**遗留字段**——运行时改读五类绑定表，本两列无消费者〔‹CERTAIN（2026-09-24 深证：全仓 grep getKnowledgeIds/getToolIds 零命中；AppServiceImpl.java 全文未读此两列，AppConfig.java:5 声明而不用）›〕） |
| max_turns | INT | DEFAULT 10 | |
| summary_threshold | INT | DEFAULT 8000 | 上下文摘要触发阈值（token） |
| summary_prompt | TEXT | | 摘要提示词 |
| max_steps | INT | DEFAULT 15 | |
| retry_times | INT | DEFAULT 2 | |
| max_tokens | INT | DEFAULT 2048 | |

Lifecycle：getOrCreate 六默认值缺省建行（AppConfigServiceImpl.java:37-42）→ 单项端点覆写 → 无删除。Relationships：1—1 app；引用 model.code（platform-config，弱引用无外键）。
**Parent Trace:** app_config → Design.AppConfigFacade → REQ-B02、REQ-E06/E09/E10。

### app_basic_config（基础配置，每应用一行）

字段：id PK；app_id NOT NULL UNIQUE（schema.sql:940）；memory_rounds INT DEFAULT 5（记忆轮数，SSE 历史窗口直接消费）；output_format VARCHAR(16) DEFAULT 'markdown'；response_language VARCHAR(16) DEFAULT 'zh-CN'；greeting/goodbye_message TEXT（greeting 被 SSE 短路径消费）；timeout_seconds INT DEFAULT 30、max_input_length INT DEFAULT 2000（**无消费者**〔‹CERTAIN（2026-09-24 深证：全仓 grep getMaxInputLength/getTimeoutSeconds 零命中，runStream 及配置面均未引用，见 design §4）›〕）；advanced_options JSON（多段共享容器：model/workflow/debug 段）；updated_by；created_at/updated_at。索引 idx_app_id。
Lifecycle：upsert（saveBasic 新行兜底 memoryRounds=5，AppConfigServiceImpl.java:27）→ 导入导出经 exportConfig/importConfig。Relationships：1—1 app。
**Parent Trace:** app_basic_config → Design.AppConfigFacade → REQ-B01/B05、REQ-E05/E09、REQ-B06（workflow/debug 段）。

### app_dialog_config（对话 UI 配置，每应用一行）

字段：id PK；app_id NOT NULL UNIQUE（schema.sql:957）；background_color VARCHAR(16)/background_image VARCHAR(512)/bubble_style VARCHAR(16)；show_avatar/show_feedback/show_suggestions TINYINT DEFAULT 1；updated_by；created_at/updated_at。纯前端展示配置，后端无运行时消费者〔CERTAIN：仅 AppConfigServiceImpl.java:29-30 存取〕。Lifecycle：upsert。Relationships：1—1 app。
**Parent Trace:** app_dialog_config → Design.AppConfigFacade → REQ-B03。

### app_global_policy（全局策略，每应用一行）

字段：id PK；app_id NOT NULL UNIQUE（schema.sql:988）；safety_enabled TINYINT DEFAULT 1（安全总开关——**消费口径：null 或无行均视为开启**，AppServiceImpl.java:85-90）；sensitive_word_mode VARCHAR(16) DEFAULT 'reject'；fallback_text TEXT；unmatched_enabled TINYINT DEFAULT 1；unmatched_action VARCHAR(32) DEFAULT 'fallback'；unmatched_config JSON；updated_by；created_at/updated_at。
Lifecycle：五个写入口（safety/fallback/sensitive-words/toggle/unmatched）同归 saveGlobalPolicy upsert（AppConfigServiceImpl.java:87-88,234-253）→ 被 SSE 编排只读（isSafetyEnabled）。Relationships：1—1 app；语义关联 platform-config 敏感词词库（词条匹配在 SensitiveWordService，本表只存开关与模式）。
**Parent Trace:** app_global_policy → Design.AppPolicyModule → REQ-D01/D02/D04。

### app_trigger（触发器）

字段：id PK；app_id NOT NULL（idx_app_id）；name VARCHAR(128) NOT NULL；trigger_type VARCHAR(32) DEFAULT 'keyword'；match_content TEXT；action_type VARCHAR(32)（值域含 'workflow'——上游 `## workflow` 节已登记：触发只回写台账不调用引擎）；action_config JSON（runTrigger 回写执行结果 JSON）；enabled TINYINT DEFAULT 1；priority INT DEFAULT 0；hit_count INT DEFAULT 0（test/run 各 +1）；created_by；created_at/updated_at。
Lifecycle：create（enabled/priority 兜底，AppConfigServiceImpl.java:77）→ update/delete（物理）→ test/run（hit_count 递增 + run 回写 actionConfig）〔:76-85 ‹CERTAIN›〕。**现状定性：占位台账**——match_content 不参与任何匹配判定。Relationships：N—1 app。
**Parent Trace:** app_trigger → Design.AppPolicyModule → REQ-D05。

### app_variable（全局策略变量）

字段：id PK；app_id NOT NULL（idx_app_id）；var_key VARCHAR(64) NOT NULL；var_type VARCHAR(16) DEFAULT 'string'；default_value TEXT；description TEXT；created_by；created_at。无 (app_id,var_key) 唯一约束，可重复〔CERTAIN：schema.sql:1000-1010 + createVariable 无查重，AppConfigServiceImpl.java:91〕。Lifecycle：CRUD（物理删）。Relationships：N—1 app；运行时无消费者〔‹CERTAIN（2026-09-24 深证：AppVariableMapper 仅注入 AppConfigServiceImpl（CRUD 存取）与测试类，AppServiceImpl 未注入，对话编排全程未读变量表）›〕。
**Parent Trace:** app_variable → Design.AppPolicyModule → REQ-D03。

### app_kb_binding / app_tool_binding / app_skill_binding / app_mcp_binding / app_db_binding（五类绑定）

| 表 | 资源键 | 附加列 | 约束 |
|---|---|---|---|
| app_kb_binding（schema.sql:1012） | kb_id | priority INT DEFAULT 0、filter_tags JSON | **UNIQUE(app_id,kb_id)** idx_app_kb——五类中唯一唯一约束 |
| app_tool_binding（:1160） | tool_id | tool_name VARCHAR(128)、config JSON | idx_tool_app |
| app_skill_binding（:1149） | skill_id | skill_name、params JSON | idx_skill_app |
| app_mcp_binding（:1171） | mcp_service_id | mcp_service_name | idx_mcp_app |
| app_db_binding（:1023） | db_id | alias VARCHAR(128)、allowed_tables JSON（对外 CSV/库内 JSON 双形状） | idx_app_id |

公共列：id PK（ASSIGN_ID）；app_id NOT NULL；enabled TINYINT DEFAULT 1；created_at。全部无软删列、无外键。
Lifecycle：bind（enabled 兜底 1；kb 另 priority 兜底 0）→ update（db/skill/tool/mcp；kb 无 update）→ unbind（物理删）。运行时：SSE 编排按 enabled=1 装配进 BaseContext（AppServiceImpl.java:570-624）；悬空资源 ID 由下游 ToolRegistry E_TOOL_UNRESOLVABLE 吸收。
Relationships：N—1 app；N—1 kb/tool/skill/mcp_service/db_instance（弱引用，无存在性校验）。
**Parent Trace:** 五表 → Design.AppBindingRegistry（缝 5）→ REQ-C01/C02。

### app_conversation（会话）

字段：id PK（**32 位去连字符 UUID 截断**，AppServiceImpl.java:250）；app_id NOT NULL（idx_conv_app）；session_id VARCHAR(64)（16 位截断，:247）；user_id/user_name；title VARCHAR(256)（首条消息截 30 字回填）；first_question TEXT（首条截 200 字）；answer_summary TEXT（首轮回答截 200 字）；message_count INT DEFAULT 0（成对 +2 记账）；token_count INT DEFAULT 0（**无生产者，恒 0**〔CERTAIN：:836 区间只加不减、消息 tokens 恒 0〕）；rating TINYINT（运营筛选维度，无写入点〔‹CERTAIN（2026-09-24 深证：全仓后端 grep setRating 零命中；AppConfigController 对话记录组仅 list/detail/delete/export 四端点，无评分写入入口；前端亦无该字段提交）›〕）；created_at（idx_conv_created）/updated_at。
Lifecycle：createSession（「新对话」）→ 每轮对话刷新 message_count/updated_at → deleteSession 物理删（连带消息）或运营面 deleteConversation 物理删。Relationships：N—1 app；N—1 user（user_id）；1—N app_conversation_message（conversation_id）。
**Parent Trace:** app_conversation → Design.AppConversationLedger → REQ-E02/E04/E12/E14、REQ-H01。

### app_conversation_message（会话消息）

字段：id PK（32 位截断）；conversation_id VARCHAR(32) NOT NULL（idx_conv_msg_conv）；role VARCHAR(16) NOT NULL（user/assistant 二值〔CERTAIN：:415,826〕）；content TEXT；tokens INT DEFAULT 0（恒 0，无 token 计数生产者）；latency_ms INT（助手消息记录耗时）；thinking_content TEXT；tool_calls TEXT（JSON 数组字符串，来自引擎回调）；feedback VARCHAR(16) NULL（like/dislike/null）；deleted_at DATETIME NULL（软删标记——历史查询 deletedAt IS NULL 过滤）；created_at。
Lifecycle：用户消息（模型调用前插入）→ 助手消息（流结束后/超时时插入，先输出过滤）→ 软删（deleteMessage）/随会话物理删。Relationships：N—1 app_conversation。
**Parent Trace:** app_conversation_message → Design.AppConversationLedger → REQ-E03/E04/E11/E12/E15。

### app_publish_record（发布记录）

字段：id PK；app_id NOT NULL（idx_app_id）；version INT（调用方给）；status VARCHAR(16) DEFAULT 'draft'（**消费口径**：Monitor 按 'released'/'rolled_back' 计数〔AppConfigServiceImpl.java:365-366 ‹CERTAIN›〕）。**值域与流转（2026-09-24 裁决）**：实证——唯一后端写点 publish 不填 status（:166 仅 setAppId/setPublishedAt ‹CERTAIN›），落库值恒为 DDL 默认 'draft'；前端 publishApp（api/index.ts:1721-1722）不传 status 且无现网组件调用该 API → 'released'/'rolled_back' 无生产者，Monitor 两计数恒 0。〔决议 2026-09-24：目标值域取 {draft, released, offline, rolled_back}，流转取「publish 写 'released' + 下线/回滚独立端点」（对齐 Monitor 消费口径与上线/下线语义的最小封闭集）；现状差距：无流转代码——已登记债务见 implement/app-center.md §2 与 spec/app-center.md §4.6（代裁，用户可推翻）〕；scope_type VARCHAR(32)/scope_value JSON；config_snapshot LONGTEXT（发布时配置快照——publish 端点不自动填充，信任调用方〔CERTAIN：:166 仅 setPublishedAt〕）；operator；published_at；created_at。
Lifecycle：publish 直接插入（无校验无状态机）→ 只读（列表/Monitor/DebugInfo）。Relationships：N—1 app。
**Parent Trace:** app_publish_record → Design.AppConfigFacade → REQ-F01。

### app_dialog_test（对话测试用例）

字段：id PK；app_id NOT NULL（idx_app_id）；name VARCHAR(256)；query TEXT；expected_answer TEXT；actual_answer TEXT（**无生产者**——无自动跑测端点，仅手填〔‹CERTAIN（2026-09-24 深证：AppConfigServiceImpl.java:168-171 仅 CRUD；控制器无执行端点；前端录制流程 DialogTest.vue:60-75 经 runApp 取回答后写入的是 expected_answer 非 actual_answer）›〕）；matched TINYINT；similarity DECIMAL(5,2)；tags JSON（实体 @JsonProperty setter 兼容裸串→包装 JSON 数组，AppDialogTest.java:14-21）；created_by；created_at。
Lifecycle：CRUD + CSV 导出（无自动跑测）。matched/similarity 供 analyze 的 unmatchedRate 聚合。
Relationships：N—1 app。
**Parent Trace:** app_dialog_test → Design.AppConfigFacade → REQ-G01。

### app_optimization（优化建议）

字段：id PK；app_id NOT NULL（idx_app_id）；suggestion_type VARCHAR(32)；title VARCHAR(256)；description TEXT；impact_score DECIMAL(5,2)；status VARCHAR(16) DEFAULT 'pending'（apply→'applied'，AppConfigServiceImpl.java:174,177）；before_metric/after_metric JSON；created_by；created_at。
Lifecycle：create（status=pending）→ apply（status=applied）→ CSV 导出。analyze 不写本表（只聚合测试/发布/绑定/触发计数）。Relationships：N—1 app。
**Parent Trace:** app_optimization → Design.AppConfigFacade → REQ-G02。

### app_template（应用模板）

字段：id PK；name VARCHAR(128) NOT NULL；description TEXT；type VARCHAR(32)；usage_count INT DEFAULT 0（**无自增消费点**——模板套用不改计数〔‹CERTAIN（2026-09-24 深证：AppServiceImpl.getTemplates 仅 selectList(null) :173-175，全仓 setUsageCount 命中均为 kb_tag 域与本表无关）›〕）；config_snapshot JSON。DDL 无时间列（schema.sql:390-394）。Lifecycle：只读消费（getTemplates 全量）；无 CRUD 端点。**数据来源（2026-09-24 裁决）**：实证——init-scripts/data.sql 无 app_template 种子、全仓无 INSERT INTO app_template、无管理端点 → 当前唯一维护方式为人工 SQL 直插，空表时 getTemplates 返回空列表。〔决议 2026-09-24：取「SQL 预置数据（data.sql 增补种子）」为维护方式；理由：模板为平台内置只读资产，无运营管理诉求，最小改动；现状差距：无任何种子数据，模板列表现网恒空——已登记债务见 implement/app-center.md §2（代裁，用户可推翻）〕。Relationships：独立（无 app_id）。
**Parent Trace:** app_template → Design.AppLedgerModule → REQ-A05。

### app_kb_auto_update_config（知识库自动更新配置，每应用一行）

字段：id PK；app_id VARCHAR(32) NOT NULL **UNIQUE**（schema.sql:1090）；enabled TINYINT DEFAULT 0；cron_expr VARCHAR(64)（无调度消费点〔‹CERTAIN（2026-09-24 深证：全仓唯一 @Scheduled 为 agent 域 SandboxService.java:157 fixedDelay，与本表无关；cronExpr 除实体声明与 AppModuleValidationTest:152 字段存在性断言外全仓零读取）›〕）；auto_publish TINYINT DEFAULT 0；notify_channels JSON；created_at/updated_at。
Lifecycle：getAutoKnowledgeUpdate 查无自动建 enabled=0 行（AppConfigServiceImpl.java:304-308）→ triggerKnowledgeUpdate 置 enabled=1（返回 triggered 文案，**无真实调度**，:293-301）→ saveAutoKnowledgeUpdate upsert。Relationships：1—1 app。
**Parent Trace:** app_kb_auto_update_config → Design.AppConfigFacade → REQ-B06。

### 死 DDL 表（本特性范围外登记）

`app_monitor_data`（schema.sql:1048-1059）与 `app_debug_log`（:1076-1086）两表**无任何 Java 实体/Mapper/引用**（全仓 grep 实测零命中）——monitor/debug 端点实际从会话与发布记录实时聚合（AppConfigServiceImpl.java:359-380,273-283）。与孤儿区 4 张 DDL-only 表同类。〔‹CERTAIN›〕

## operation-analytics

> 覆盖：fastrag-operation 模块拥有的 5 张表（user_feedback / chat_session / sys_audit_log / sys_login_log / data_mining_task，DDL：schema.sql:626-679、:767-779、user_feedback 扩展列 ALTER 块 :728-733）+ 2 个只读消费登记（kb_log 归 `## publish-review`、model_call_log 归 `## platform-config` 死表 REQ-C03——本节不重复定义表结构）。
> ⚠ 仲裁：feature-map 特性 #18 证据包所列「sys_operation_log」表**不存在**（schema.sql 与实体 grep 均无命中）——实际三日志为 sys_audit_log / sys_login_log / kb_log（发布域）〔grep 实测 ‹CERTAIN›〕。
> DDL 同步状态：user_feedback 基线 CREATE 仅 11 列，category/status/reply/processed_by/processed_at 五列经 schema.sql 内 ALTER 块（:728-733）补齐、org_id 仅 SchemaInitializer 运行时增补（SchemaInitializer.java:387 + 存量回填 :388）〔‹CERTAIN›〕；chat_session / sys_audit_log / sys_login_log / data_mining_task 四表双处一致。

### user_feedback（用户反馈）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT | |
| session_id | VARCHAR(32) | | 关联会话 ID（弱引用，无存在性校验） |
| user_id | VARCHAR(32) | | 提交者用户 ID |
| kb_id | VARCHAR(32) | | 关联知识库 ID；列表/统计过滤键 |
| app_id | VARCHAR(32) | | 关联应用 ID；总览应用排行分组键 |
| query / answer | TEXT | | 原始问答快照；query 兼作词云语料 |
| feedback | VARCHAR(16) | DEFAULT 'none'（DDL）/ create 缺省 'like'（代码） | 反馈类型（like/dislike…）；统计 satisfactionRate 分子；⚠ DDL 默认值与代码缺省不一致〔schema.sql:634 vs FeedbackServiceImpl.java:79 ‹CERTAIN›〕 |
| score | INT | | 评分；avgScore 仅计 score>0 行 |
| comment | TEXT | | 用户评论 |
| status | VARCHAR(16) | DEFAULT 'pending'（ALTER 块） | 处理状态；reply 强制置 'resolved'；overview pending 口径 = NOT IN ('resolved','ignored') |
| reply | TEXT | （ALTER 块） | 运营回复内容 |
| processed_by | VARCHAR(32) | （ALTER 块） | 处理人（reply.operator，缺省 'system'） |
| processed_at | DATETIME | （ALTER 块） | 处理时间（reply 时覆写 now） |
| category | VARCHAR(32) | （ALTER 块） | 反馈分类；总览问题分组键 |
| org_id | VARCHAR(32) | **仅运行时增补**（SchemaInitializer.java:387） | 组织隔离键；create 强制取提交者组织，全部查询/管理操作隔离依据 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | |

Lifecycle：create（orgId 服务器覆写 + 双缺省兜底）→ update（非空字段部分更新）→ reply（状态机 pending→resolved，可重复回复刷新 processedAt）→ delete（**物理删除**）。Relationships：N—1 kb/app/user/session（全部弱引用无外键）；统计面 1—N 自聚合分组。
**Parent Trace:** user_feedback → Design.FeedbackLedger → REQ-F01~F05。

### chat_session（聊天会话，问答明细）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | VARCHAR(32) | PK（ASSIGN_ID 雪花） | |
| user_id / kb_id / app_id | VARCHAR(32) | | 归属三元组（弱引用）；userId 过滤用 like |
| query / answer | TEXT | | 用户提问 / AI 回答；keyword 检索键=query |
| retrieved_chunks | JSON | | RAG 命中知识块 |
| model | VARCHAR(128) | | 使用模型 |
| duration | BIGINT | | 响应时长 ms |
| tokens | INT | | Token 消耗 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 列表排序键（倒序） |

Lifecycle：**只读死表**——本模块仅 SELECT（分页明细 + overview COUNT），全仓无任何写入方（app-center 实际会话存储为 app_conversation/app_conversation_message，见 `## app-center` 节；grep 实测零写入）〔‹CERTAIN›〕——问答明细页在当前代码下恒空，属占位数据面。Relationships：N—1 kb/app/user（弱引用）。
**Parent Trace:** chat_session → Design.ChatSessionFinder (S)（缝 2 只读消费）→ REQ-C01、C02。

### sys_audit_log（系统审计日志）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT | |
| user_id / username | VARCHAR(32)/VARCHAR(64) | | 操作人（事件字段透传） |
| module | VARCHAR(32) | | 模块名（KbLogAspect 按动作前缀映射，如"知识库"）；list 过滤键 |
| action | VARCHAR(64) | | ActionType 枚举 name() |
| target | VARCHAR(256) | | 操作目标（SpEL 解析或参数回退） |
| detail | TEXT | | 详情；统一日志 keyword 检索键之一 |
| ip | VARCHAR(64) | | 客户端 IP |
| status | VARCHAR(16) | DEFAULT 'success' | success/failed（切面按执行结果写入） |
| timestamp | DATETIME | DEFAULT CURRENT_TIMESTAMP | 监听器以落库时刻覆写；排序键（倒序） |

Lifecycle：**append-only**——唯一写入口为 SysAuditLogEventListener 消费 SysAuditLogEvent（写失败吞异常仅 warn）；无更新/删除端点，无归档清理。Relationships：无外键；与 kb_log（发布域）同源双写（切面一次执行两表各一行，互不保证原子——一侧失败不影响另一侧）〔KbLogAspect.java:102-116 ‹CERTAIN›〕。
**Parent Trace:** sys_audit_log → Design.AuditLogPipeline（缝 1 稳定侧）→ REQ-L01、L04、L05。

### sys_login_log（登录日志）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK AUTO_INCREMENT | |
| user_id / username | VARCHAR(32)/VARCHAR(64) | | 登录者；register 场景 userId 可为 null |
| login_time | DATETIME | DEFAULT CURRENT_TIMESTAMP | 排序键（倒序） |
| ip | VARCHAR(64) | | 来源 IP |
| device / os / browser | VARCHAR(128)/VARCHAR(64)/VARCHAR(64) | | 设备环境（UA 解析） |
| location | VARCHAR(128) | | 地理位置；**无写入方**（iam 调用未传此字段，恒 null）〔SysLoginLogServiceImpl.java:36-53 无 location 赋值 ‹CERTAIN›〕 |
| status | VARCHAR(16) | DEFAULT 'success' | success/failed/logout/register（代码实际四值，DDL 注释三值） |
| fail_reason | VARCHAR(256) | | 失败原因；统一日志 DTO detail 映射源 |

Lifecycle：append-only——唯一写方为 iam.AuthController 六处调用（登录成功/失败、logout、register、改密等）经 addLoginLog（status 缺省 success 兜底，写失败吞异常不阻断登录）；读侧 list（userId/status 过滤 + limit 缺省 100）。无更新/删除/归档。Relationships：无外键。
**Parent Trace:** sys_login_log → Design.LoginLogLedger (S) → REQ-L02、L05。

### data_mining_task（数据挖掘任务）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | VARCHAR(32) | PK（ASSIGN_ID） | |
| name | VARCHAR(128) | NOT NULL | 任务名；keyword 过滤用 like |
| kb_id | VARCHAR(32) | | 关联知识库（弱引用）；过滤键 |
| rule_type | VARCHAR(32) | | 规则类型（create 缺省 'keyword'）；自描述字段，无值域校验 |
| rule_config | JSON | | 规则配置；**无运行时消费者**（run 不读取）〔DataMiningServiceImpl.java:25-41 ‹CERTAIN›〕 |
| status | VARCHAR(16) | DEFAULT 'enabled' | create 缺省 enabled；无状态流转逻辑 |
| last_run_at | DATETIME | | run 时覆写 now |
| result_summary | JSON | | run 产物摘要（当前为模拟数据：matchedCount 随机数 + 硬编码 topKeywords + runStatus=success） |
| creator | VARCHAR(32) | | 创建者（无组织隔离——list 不做 org 过滤，全租户可见）〔‹CERTAIN（2026-09-24 深证：DataMiningServiceImpl.java:12-17 仅 kbId/keyword 两条件，无 DataScope/orgId 过滤，与同模块 FeedbackServiceImpl 的 applyOrgScope 形成口径反差）›〕 |
| created_at / updated_at | DATETIME | DEFAULT CURRENT_TIMESTAMP / ON UPDATE | |

Lifecycle：create（双缺省兜底）→ get/list → run（模拟执行：仅刷新 last_run_at + result_summary，不消费 rule_config、不产生真实挖掘）→ delete（物理）。Relationships：N—1 kb（弱引用）。
**Parent Trace:** data_mining_task → Design.DataMiningTaskBoard (S) → REQ-D01~D04。

### 只读消费登记（表结构归上游节）

| 上游表 | 归属节 | 本链消费点 | 消费行为 |
|---|---|---|---|
| kb / kb_file | `## kb-core` | HomeController、AnalyticsController | 计数/TOP N 选择，只读 |
| app | `## app-center` | HomeController（热门应用）、FeedbackServiceImpl（排行回填应用名） | 只读 |
| kb_log | `## publish-review`（LogAuditModule） | HomeController 最近动态、UnifiedLogQueryEngine category=operation | 只读 |
| model_call_log | `## platform-config`（**死表 REQ-C03**：schema.sql:563/:566 status 列重复定义，新环境建表失败） | ModelMonitorPanel 全部四段聚合 | 只读；死表 ⇒ 模型监控面板当前恒空 |
| mcp_call_log | `## tools-skills-mcp`（只写，查询归本链） | **当前无消费实现**（UnifiedLogQueryEngine 未路由此源）〔grep 实测 ‹CERTAIN›〕 | 口径登记：未来接入归 UnifiedLogQueryEngine |

**Parent Trace:** 消费登记 → Design.缝 2（跨域只读消费缝）→ REQ-H02~H05、K01~K03、L03、C01、M01 的数据合法性前提。

## new-conversation

> 对应 design 模块：SessionRequestStateStore（请求态形状唯一属主）/ 编排器跨链延伸（引用落库触点）/ 账本跨链延伸（实体扩列）（design/new-conversation.md §1）。接口契约见 interfaces.md `## new-conversation` 节。
> **DDL 变更清单（本节为权威登记，共 2 表 4 列 + 1 索引，全部须双处同步）**：`app_conversation.app_id` 放宽可空、`+request_state`、`+is_pinned`、`+INDEX idx_conv_user(user_id)`；`app_conversation_message +references`。同步义务：`init-scripts/schema.sql`（app_conversation 现状 :1115-1131、message 现状 :1133 起；列内 ALTER 先例 :1384-1386 thinking_content/tool_calls）↔ `SchemaInitializer`（addColumnIfNotExists 惯用法，SchemaInitializer.java:427 同款 + 索引幂等增补）。存量数据零迁移（REQ-G03）：放宽与加列对既有行均为 NULL/默认值。
> **证据约定**：同 interfaces.md——前瞻契约指 design/spec 裁决；‹CERTAIN› 处为既有代码/DDL 事实。

### app_conversation（扩展列）

| 字段 | 变更 | 类型/约束 | 说明 |
|---|---|---|---|
| app_id | **NOT NULL → 可空**（本节唯一对既有列的变更） | VARCHAR(32)，idx_conv_app 保留 | NULL = 知识库直聊会话（CONTEXT.md「知识库直聊」）；非空 = 挂载应用预设（REQ-D01）。存量行全部非空、零改写（REQ-G03） |
| request_state | **新增** | TEXT（JSON 字符串，形状 = ConversationRequestState），NULL 合法 | NULL = 无请求态（存量会话/未对话新会话），重开恢复按 D03 降级（挂应用用应用配置/直聊空范围）；固化时机 = 三落库点（正常完成/超时/停止，REQ-B01）；形状唯一属主 SessionRequestStateStore，坏 JSON 容错为空请求态 |
| is_pinned | **新增** | TINYINT DEFAULT 0 | 置顶标记（REQ-E05）；列表排序键之一（置顶优先 → updated_at 倒序） |
| INDEX idx_conv_user | **新增** | (user_id) | 会话中心按用户全量列表（REQ-A03）支撑；现表仅有 idx_conv_app/idx_conv_created（schema.sql:1129-1130 ‹CERTAIN›），无 user 维度索引 |

其余列不变。Lifecycle 增量：request_state 随三落库点覆写（会话存活期内可变）→ 会话删除时随行物理删除。Relationships 变更：app 关系由 N—1 放宽为 **N—0..1**（弱引用，应用删除不级联会话——列表来源标记按批量查询缺名显示「直聊/已删应用」容错）。
**Parent Trace:** app_conversation 扩展 → Design.账本跨链延伸 + SessionRequestStateStoreModule → REQ-A01/A03/A04/B01/D03/E05、REQ-G03、NFR-02。

### app_conversation_message（扩展列）

| 字段 | 变更 | 类型/约束 | 说明 |
|---|---|---|---|
| references | **新增** | TEXT（JSON 数组字符串，元素形状 = MessageReference），恒写（无检索命中 = `[]`） | 仅 assistant 消息写入；与流内 sources 事件**同源**（RetrievalContext.getSources()，AppServiceImpl.java:803-807 ‹CERTAIN›）的**投影子集**；两通道统一写入（REQ-F01） |

其余列不变（thinking_content/tool_calls 的运行时增补先例 :1384-1386）。Lifecycle：随助手消息落库写入 → 消息软删/级联物理删时随行。Relationships：不变（N—1 app_conversation）。
**Parent Trace:** app_conversation_message 扩展 → Design.编排器跨链延伸（消息引用内联触点）→ REQ-F01/F02、NFR-02。

### ConversationRequestState（request_state JSON 形状，非独立表）

| 字段 | 类型 | 约束 | 消费性 |
|---|---|---|---|
| appId | string ≤32 | 可空 | 消费（挂载应用；解析器 E_APP_NOT_VISIBLE 校验主体） |
| appEnabled | boolean | 缺省 true | 消费（挂载开关，对齐 WeKnora agent_enabled 旋钮语义） |
| kbIds | string[] ≤32/元素，去重，**数组上限 50** | 缺省 [] | 消费（直聊检索范围；超限走全局参数校验业务错误，不静默截断） |
| modelId | string ≤128 | 可空 | 消费（C05 五级回退最高优先源） |
| toolIds | string[] ≤100 | 缺省 [] | **直聊消费**（B02 演进 2026-09-25：装配 BaseContext.tools，与应用绑定同路径） |
| mcpIds | string[] ≤100 | 缺省 [] | **直聊消费**（同上，装配 mcps） |
| skillIds | string[] ≤100 | 缺省 [] | **直聊消费**（同上，装配 skills） |
| webSearchEnabled | boolean | 缺省 false | 仅存不消费（唯一死字段，web 搜索能力后补） |

形状纪律：本表即形状唯一权威（SessionRequestStateStore 属主）；新增字段必须先在 design 指认编排器消费行号才准入（防上帝对象，design §1 风险条款）。 Lifecycle：创建时随 form 初值 → 三落库点整包覆写 → 随会话删除消亡。**死字段清单的唯一证词在本表**。
**Parent Trace:** ConversationRequestState → Design.SessionRequestStateStoreModule + DialogPresetResolverModule → REQ-B01/B02、REQ-C05、REQ-A04。

### MessageReference（references JSON 元素形状，非独立表）

| 字段 | 类型 | 说明 |
|---|---|---|
| kbId | string ≤32 | 来源知识库 |
| fileId | string ≤32 | 来源文件 |
| fileName | string ≤256 | 文件名（历史回看展示） |
| chunkIndex | int | 分片序号（定位） |
| chunkType | string ≤16 | 分片类型 |
| score | number | 相似度/得分 |
| imageKeys | string[] | 图片键（可空） |

**投影澄清（2026-09-25 设计决议）**：本形状为 sources 事件的投影——**不含 content**（分片全文不随消息持久化，防膨胀）；REQ-F01「同源同内容」按「同源 + 字段投影」口径落地（代裁，用户可推翻）。
**Parent Trace:** MessageReference → Design.编排器跨链延伸 → REQ-F01/F02。

---

## qa-generation

> 覆盖：提炼任务账本表 kb_qa_generation_task（每文件一条）+ MQ 提炼消息（运行时结构）+ 两个既有表扩列（kb_qa_pair.source_chunk_id、kb_file.qa_generation_config——本特性为扩列所有者，宿主表契约仍在原章）。无种子数据依赖。
> **证据约定**：前瞻契约（代码未实现），证据指向 design/spec 裁决；‹CERTAIN› 仅标既有代码事实。
> **DDL 双处同步义务（宪法硬约定）**：本节新增表/列必须同时落 SchemaInitializer 幂等增补与 init-scripts/schema.sql 全量脚本，implement 验收双查——历史漂移教训：idx_content_fulltext 仅单侧（feature-map #5 登记）、agent/agent_run 表全缺（feature-map #17 登记）。
> **对 `## qa-pairs` 章两处现状断言的修订（实现后生效）**：①「source：ai 无写入方」——本特性即首个 ai 写入方（trigger→execute 链）；②「与召回解耦：召回不过滤 status」——ai 引入后准入规则修订为 `source='manual' OR status='confirmed'`（ADR-0005），manual 口径不变。

### kb_qa_generation_task — 提炼任务账本（每文件一条）

| 字段 | 类型与约束 | 说明 / validation |
|---|---|---|
| file_id | VARCHAR(32) PRIMARY KEY | 所属文件（全局雪花 id）；单行 upsert 语义的锚——新任务整行覆盖 |
| kb_id | VARCHAR(32) NOT NULL；INDEX idx_kb_id | 宿主知识库（冗余存储，支撑按库观测）；跨库移动不迁移本行（随源库文件清理，见 Lifecycle） |
| status | VARCHAR(16) DEFAULT 'idle' | idle/running/success/partial/failed 五值；partial=存在跳过分片的正常完结；状态机归 QaGenerationService（本表无业务逻辑） |
| total_chunks | INT DEFAULT 0 | 受理时定格的父分片总数 |
| succeeded_chunks / failed_chunks | INT DEFAULT 0 | 成功 / 失败（重试 1 次后跳过 + 闸门拦截）分片数；每片完结即回写（兼作陈旧心跳） |
| config | JSON | 参数快照 {count, customInstruction, mode, model}——执行期唯一依据（触发后修改文件配置不影响进行中任务） |
| error | TEXT | failed/partial 摘要（队列不可用 / 执行中断 / 分片失败计数） |
| created_at / updated_at | DATETIME（INSERT / INSERT_UPDATE 填充） | updated_at 即心跳；status=running 且超 qa.generation.stale-minutes（缺省 30）无心跳 → 清扫为 failed「执行中断」 |

**Lifecycle:** trigger 创建/覆盖（running、计数清零）→ execute 逐片心跳回写 → 终态 success/partial/failed → 陈旧 running 清扫为 failed → 宿主文件删除/KB 删除时**级联物理删除**（挂接 `## file-ingestion` 清理面与 `## kb-core` KbLifecycleService，与 QA 对级联同批）；跨库移动按「源库清理」口径随文件清理，目标库首次生成时自然重建。

**Relationships:** 1—0..1 kb_file（经 file_id 主键；删除级联）；N—1 kb（经 kb_id）；与 kb_qa_pair **无引用关系**——任务行是状态账本非产出索引，产出即 QA 对本身（经 file_id 间接关联，无 task_id 外键）。

**Parent Trace:** kb_qa_generation_task → Design.QaGenerationModule（任务账本数据面）→ REQ-A04/D01/D02、NFR 异步持久化。

### kb_qa_pair 扩列（本特性所有）

| 字段 | 类型与约束 | 说明 / validation |
|---|---|---|
| source_chunk_id | VARCHAR(32) 可空 | 源父分片引用（kb_chunk.id）；manual 对恒 NULL；ai 对写入；**悬空允许**——重切重建/手工删片不清理不置空（AI 对重生成烧 LLM 成本，属用户资产，D12 裁决），展示层降级为文件级溯源；无索引（消费路径为按 kb_chunk 主键单行回查） |

**Parent Trace:** source_chunk_id → Design.QaGenerationModule（溯源关联写入）→ REQ-B01/D03/E01。

### kb_file 扩列（本特性所有）

| 字段 | 类型与约束 | 说明 / validation |
|---|---|---|
| qa_generation_config | JSON 可空 | qa 模式文件的上传参数 {count(1-10), customInstruction(≤500 字符), model(≤128 字符)}——**无 mode 字段**（qa 模式自动触发恒 replace 语义，由摄入挂钩组装）；仅 processing_mode='qa' 时有值；上传时写入；跨库移动随文件行迁移；chunk 模式文件恒 NULL |

**Parent Trace:** qa_generation_config → Design.摄入挂钩 + QaGenerationModule.trigger → REQ-A03/E02。

### 提炼消息（运行时结构，MQ，无表）

| 字段 | 类型 | 说明 |
|---|---|---|
| kbId | string ≤32 | 宿主知识库；消费端非空校验，缺失丢弃 + warn（仿 GraphBuildConsumer.java:186-191 ‹CERTAIN›） |
| fileId | string ≤32 | 目标文件 |
| config | object | {count:int, customInstruction:string\|null, mode:"replace"\|"append", model:string\|null}；摄入挂钩路径 mode 恒 "replace" |

投递拓扑：持久化队列 `fastrag.qa-generation.queue` + routing key `qa-generation`（复用 DIRECT_EXCHANGE `fastrag.direct`；RabbitMQConfig 增队列/绑定声明、MessagePublisher 增 publishQaGeneration——与 graph-build 同构但**无同步降级 handler**，design 缝 2 偏差）。消费：`@RabbitListener(concurrency = "3")` + 应用内 qa-generation-worker 线程池，即时 ack。

**Parent Trace:** 提炼消息 → Design.缝 2 + QaGenerationConsumerModule → REQ-A02/A03、NFR 异步持久化/吞吐。
