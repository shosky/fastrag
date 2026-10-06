# 工具 / 技能 / MCP / 外部数据库（tools-skills-mcp） — 需求规格

## 1. 特性与范围

本特性是应用与智能体的**能力底座**：把四类外部能力统一注册、发现、测试并交付给运行时消费——① 自定义 HTTP 工具（注册/启停/测试代理/API 配置/插件导入）；② 文件化技能 Skill（SKILL.md + frontmatter，含文件树编辑、ZIP 导入导出、依赖声明、分享配置）；③ MCP 服务（stdio/SSE 传输注册、URL 解析、工具发现与增删改测、调用日志）；④ 外部数据库实例（连接管理、表结构同步、只读 SQL 查询）。四类能力经 ToolRegistry 解析为统一的 ToolDefinition、由 ToolExecutorFactory 路由到对应执行器，供 fastrag-agent 等下游以 OpenAI function calling 格式调用。显式排除：工具在智能体对话中的编排与执行循环（agent 特性）、四类能力到应用的五类绑定关系（app-center 特性）、知识库检索本身（retrieval 特性——本特性仅经 KbOperationService 调用其查询口）。

## 2. Out of Scope

- **智能体工具调用循环**：多轮 tool-call 决策、参数由 LLM 生成、结果回填对话——属 agent 特性；本特性只保证 `execute(definition, arguments, ctx)` 单次调用语义正确（ToolExecutor.java:15-18）。
- **应用侧绑定管理**：app_tool_binding / app_skill_binding / app_mcp_binding / app_db_binding 四张绑定表的读写（schema.sql:1149-1182）归 app-center。
- **知识库检索实现**：KnowledgeToolExecutor 仅经 `KbOperationService.listKnowledgeBases/queryKnowledgeBase` 消费（KnowledgeToolExecutor.java:31,54,89），向量检索与混合检索质量归 retrieval。
- **MCP 服务的 stdio 本地进程执行**：McpProtocolClient 显式抛 UnsupportedOperationException（McpProtocolClient.java:70-72）——stdio 传输的注册/种子数据支持，但实际连接仅实现 Streamable HTTP（JSON-RPC over HTTP POST）。
- **数据库写操作**：外部 DB 查询仅允许 SELECT/SHOW/DESCRIBE/DESC/EXPLAIN 前缀语句（DbInstanceServiceImpl.java:418-426）；建表端点仅写 db_table 元数据登记，不在外部库执行 DDL（DbInstanceServiceImpl.java:144-148）。
- **运营日志查询**：mcp_call_log 只写不读（写入 McpToolExecutor.java:106-120；fastrag-tools 内无查询消费方），查询面归 operation-analytics。

## 3. 领域术语

- **工具（Tool）**——可被应用或智能体调用的外部能力，分为内置工具、API 工具、MCP 工具、技能（Skill）四类（CONTEXT.md）。本特性中「工具」狭义指注册于 `tool` 表的自定义 HTTP 工具。
- **MCP 服务（MCP Service）**——基于 Model Context Protocol 的外部服务，支持 stdio 和 SSE 两种传输方式（CONTEXT.md）。
- **技能（Skill）**——文件化（Markdown+frontmatter）的可导入导出能力包，具有依赖声明和作用域（应用级/知识库级）（CONTEXT.md）。

## 4. 功能需求

### 4.1 自定义 HTTP 工具注册与测试

本域管理用户自定义的 REST API 工具：从表单注册到连通性验证再到插件批量导入，形成「注册 → 配置 → 测试 → 启用」闭环。工具配置含凭据（authValue），可见性与凭据脱敏是本域的安全底线。

- **REQ-A01**：工具支持按名称关键字与类型筛选列出，列表仅含当前用户可见条目（属主 / 同组织 / 系统级内置 / API Token 全量）。〔证据：ToolServiceImpl.java:38-54 ‹CERTAIN›〕
- **REQ-A02**：工具创建时 creator/orgId 取自当前登录用户，identifier 缺省由名称自动生成（小写+空格转下划线），enabled 缺省启用。〔证据：ToolServiceImpl.java:78-97,126-128 ‹CERTAIN›〕
- **REQ-A03**：HTTP 配置（method/url/authType/authValue/bodyType/body/params/headers）与工具本体一对一存储；params/headers 接受前端 `{key,value}` 数组并统一序列化为 JSON 字符串。〔证据：ToolServiceImpl.java:231-254、ToolHttpConfig.java:61-68 ‹CERTAIN›〕
- **REQ-A04**：非属主用户读取工具时，HTTP 配置（含 URL 与 authValue 凭据）不返回。〔证据：ToolServiceImpl.java:52,62-63 ‹CERTAIN›〕
- **REQ-A05**：修改/删除/启停工具要求管理权（属主或持 `*` 权限），非属主操作被拒绝。〔证据：ToolServiceImpl.java:31-35,186-202 ‹CERTAIN›〕
- **REQ-A06**：测试代理把前端构造的 HTTP 请求（method/url/headers/body/timeout）转发到目标地址，原样返回状态码/响应头/响应体/耗时，4xx/5xx 与网络异常均不作为业务失败（返回含 error 字段的成功响应包）。〔证据：ToolController.java:87-185 ‹CERTAIN›；转发目的为规避浏览器 CORS，注释见 :86〕
- **REQ-A07**：插件支持单文件上传创建（type=plugin）与 JSON 数组批量导入，导入条目缺省启用。〔证据：ToolServiceImpl.java:272-298 ‹CERTAIN›〕
- **REQ-A08**：工具输出可配置 JSONPath 映射（outputMapping），执行结果按映射路径提取值，提取失败回退原始响应。〔证据：Tool.java:59-60、HttpToolExecutor.java:113-116,129-145 ‹CERTAIN›〕

### 4.2 文件化技能（Skill）

技能以 SKILL.md（YAML frontmatter + Markdown 正文）为载体，兼具库记录（skill 表）与磁盘目录（`{data-dir}/skills/{slug}/`）两种形态：库记录服务列表/依赖/分享治理，磁盘目录服务文件编辑与 ZIP 导入导出。内置技能随应用启动增量同步。

- **REQ-B01**：SKILL.md 必须以 `---` frontmatter 开头且含非空 name 与 description，否则解析失败并给出具体缺失原因；slug 缺省取 name，version 缺省 1.0.0。〔证据：SkillMarkdownParser.java:25-73 ‹CERTAIN›〕
- **REQ-B02**：技能列表区分全量列表（含禁用）与可访问列表（仅 enabled=1，按 sourceType 可再筛），内置技能单独列出。〔证据：SkillServiceImpl.java:59-91、SkillMapper.java:11-12 ‹CERTAIN›〕
- **REQ-B03**：技能创建/更新时依赖（skill_dependency）与作用域（skill_scope）随表单全量替换（先删后插），依赖同时冗余写入 skill.dependencies JSON 列。〔2026-10-06 增补：依赖写入前经 SkillDependencyValidator 校验——原「校验器仅服务前端下拉、写入路径不校验」为登记缺陷，已修复（M3-2）〕〔证据：SkillServiceImpl.java:193-241,284-298 ‹CERTAIN›〕
- **REQ-B04**：内置技能（isBuiltin=1）不允许删除；删除自定义技能级联清除依赖与作用域记录。〔证据：SkillServiceImpl.java:244-255 ‹CERTAIN›〕
- **REQ-B05**：启动时扫描 classpath `builtin-skills/*/SKILL.md` 建立内置技能播种。〔2026-10-06 grill 修订（裁决 3A）：语义由「按内容 MD5 增量同步（变化更新且保留用户可改字段）+ 整目录 REPLACE_EXISTING 复制」改为「**DB 无该 slug 记录才 insert+复制目录，已有记录整体跳过**（不更新 DB、不覆盖磁盘）」——DB `skill.content` 为唯一正文事实源（术语见 CONTEXT.md「技能正文事实源」），用户经文件 API 编辑回写 DB 后不被启动覆盖，原 MD5 增量同步表述废止。证据：SkillDataInitializer.java（现行实现）‹CERTAIN›；裁决见 docs/plan/skill-runtime-remediation.md〕
- **REQ-B06**：技能文件树支持递归浏览、读/建/改/删文件与目录，所有路径先经规范化校验、越出技能根目录即拒绝（防路径遍历）；禁止删除技能根目录本身。〔证据：SkillFileServiceImpl.java:58-65,188-190 ‹CERTAIN›〕
- **REQ-B07**：技能目录可导出为 ZIP（排除隐藏文件与目录）；上传 ZIP/SKILL.md 经「解析→草稿→确认安装」流水线落库（多技能包逐一安装、单技能 ZIP 自动归位到 slug 子目录），slug 冲突时覆盖重装；草稿 1 小时过期并在下次上传时清理。〔证据：SkillFileServiceImpl.java:216-247、SkillDraftServiceImpl.java:54-108,160-169,41-42,387-408 ‹CERTAIN›〕
- **REQ-B08**：依赖校验覆盖四类——tool（须存在且启用）、mcp（须存在且启用）、skill（须存在启用且不得依赖自身）、model（暂缓校验）；重复依赖与未知类型报错；提供启用项下拉选项（可排除自身）。〔证据：SkillDependencyValidator.java:39-110,115-146 ‹CERTAIN›〕
- **REQ-B09**：技能分享配置为三级模型——global（全员）/ department（指定部门）/ user（指定用户，缺省）；非管理员提交的配置强制降级为 user 级。〔证据：SkillAccessManager.java:61-67,129-148、SkillShareConfig.java ‹CERTAIN›〕〔2026-10-06 grill 裁决：维持三级模型并补齐接线——现状 shareConfig 未落库（Skill.java:93 exist=false）、AccessManager 零生产调用、SkillMapper 查错列名（SkillMapper.java:19-20 created_by→实际列 creator），均列修复方案 M3；见 docs/plan/skill-runtime-remediation.md〕
- **REQ-B10**：技能依赖的实体（tool/mcp）被校验后随技能元数据交付执行器；技能正文交付走「技能正文拦截读」协议——read_file 命中 `/workspace/skills/{slug}/SKILL.md` 时由 SkillsMiddleware 以 DB `skill.content` 全文返回（不依赖沙箱存活）。〔2026-10-06 grill 修订：原 read_skill/activate 执行器路径无任何 `skill_*` 注入入口，为死代码，随 M3 清理；术语见 CONTEXT.md「技能正文拦截读」；裁决见 docs/plan/skill-runtime-remediation.md。证据：ToolRegistry.java:123-161、SkillToolExecutor.java:46-63、SkillsMiddleware.java:73-99 ‹CERTAIN›〕

### 4.3 MCP 服务与工具发现

MCP 域管理外部 MCP 服务器的注册与工具发现：注册时可选「解析」（连接探查不落库）或直接保存；保存后经「刷新」完成 initialize 握手 + tools/list 发现，工具落库为 mcp_tool 记录并提供逐工具启停与调用测试。外部 MCP server 是系统边界。

- **REQ-C01**：MCP 服务注册含传输协议（缺省 sse）、URL 或 stdio 命令/参数/环境变量、认证方式（none/bearer/basic）；创建后初始 status=offline。〔证据：McpServiceServiceImpl.java:109-134 ‹CERTAIN›〕
- **REQ-C02**：parse-url 在不持久化的前提下连接目标服务器完成握手并列出工具，供创建前预览；mcpUrl 与 command 至少提供一个，否则报错。〔证据：McpController.java:87-91、McpServiceServiceImpl.java:160-212 ‹CERTAIN›〕
- **REQ-C03**：刷新操作重新连接、以新发现结果**全量替换**该服务旧工具记录（先删后插），成功则 status=online 并记录 lastUsed，失败则 status=error 且向上抛出失败原因。〔证据：McpServiceServiceImpl.java:303-375 ‹CERTAIN›〕
- **REQ-C04**：MCP 工具支持手动增/删/改/启停；手动添加与更新时前端「参数数组」格式自动归一为 JSON Schema；工具 ID 统一生成为 `mcp__{服务Camel}__{工具Camel}`。〔证据：McpController.java:133-150、McpServiceServiceImpl.java:378-434,564-607,613-620 ‹CERTAIN›〕
- **REQ-C05**：工具测试对真实服务器执行 tools/call 并返回 {success, output, durationMs}，服务器异常归一为 success=false 而非业务异常。〔证据：McpController.java:152-158、McpServiceServiceImpl.java:437-501 ‹CERTAIN›〕
- **REQ-C06**：内置 MCP 服务（filesystem/fetch/sequential-thinking）由启动种子器增量同步：新建置 isBuiltin=1，已存在则更新元数据但不覆盖用户可改字段（enabled/status/lastUsed）；内置服务不允许删除。〔证据：McpBuiltinSeeder.java:42-70,88-123、McpServiceServiceImpl.java:262-269 ‹CERTAIN›〕
- **REQ-C07**：认证凭据（authValue/env）仅属主可见，列表与详情对非属主脱敏。〔证据：McpServiceServiceImpl.java:90-94,516-521 ‹CERTAIN›〕
- **REQ-C08**：MCP 工具经运行时调用时按服务配置自动附认证头（bearer/basic），调用成败均记 mcp_call_log 并回写服务 lastUsed，日志失败不影响主流程。〔证据：McpToolExecutor.java:57-68,83-87,106-133 ‹CERTAIN›〕
- **REQ-C09**：MCP 客户端对已注册服务完成 initialize→tools/list→tools/call 完整生命周期后工具发现与调用可用（会话保持与流式响应解析按协议约定，报文契约见 contract/interfaces.md tools-skills-mcp 节 McpProtocolClient）；stdio 传输显式不支持。〔证据：McpProtocolClient.java:26-57,65-120,293-321,70-72 ‹CERTAIN›〕

### 4.4 外部数据库挂载与 SQL 查询

外部数据库实例面向「智能体查业务库」场景：注册连接（凭据属主脱敏）、连通测试、表结构同步落库、受限只读查询。**直接执行用户 SQL 是本特性最大的安全面**。

- **REQ-D01**：数据库实例注册时缺省只读（readOnly=1）与 disconnected 状态，JDBC URL 可缺省按类型自动拼接（mysql/postgresql/h2/oracle/sqlserver 五类+缺省回退 mysql）。〔证据：DbInstanceServiceImpl.java:83-95,387-402 ‹CERTAIN›〕
- **REQ-D02**：密码与 JDBC URL 属主脱敏——非属主读取时密码置 `******`、URL 置 null。〔证据：DbInstanceServiceImpl.java:48-54,69,78 ‹CERTAIN›〕
- **REQ-D03**：SQL 查询仅属主可发起，且语句前缀必须是 SELECT/SHOW/DESCRIBE/DESC/EXPLAIN，否则拒绝执行；查询行数上限 500、fetchSize 100。〔证据：DbInstanceServiceImpl.java:192-205,34-35,216-218,418-426 ‹CERTAIN›〕
- **REQ-D04**：查询结果统一为 {columns, rows, rowCount, elapsedMs}，执行异常归入 error 字段并回空列/行而非抛业务异常；每次连接动态创建 HikariCP 池（max 3 连接、5s 超时）用后即弃。〔证据：DbInstanceServiceImpl.java:214-258,365-382 ‹CERTAIN›〕
- **REQ-D05**：连接测试执行 `SELECT 1` 并回 {connected, latencyMs[, error]}，同时回写实例状态 connected/disconnected。〔证据：DbInstanceServiceImpl.java:151-189 ‹CERTAIN›〕
- **REQ-D06**：表结构同步从 JDBC DatabaseMetaData 读取全部用户表（表名/注释/列信息/行数）upsert 到 db_table（同库同表名唯一），失败回 error 与已同步计数；删除实例级联删除其表记录。〔证据：DbInstanceServiceImpl.java:264-358,124-132 ‹CERTAIN›〕

### 4.5 统一工具解析与执行路由

四类能力（含知识库查询与内置安装技能）收敛为同一种运行时表示 ToolDefinition，下游（fastrag-agent）按 toolIds 列表解析、按类型路由执行，以 OpenAI function calling 格式交付 LLM。这是「能力底座」的汇合点。

- **REQ-E01**：resolveTools 按 ID 前缀路由——`mcp_`/`mcp:` 走 MCP 工具解析，其余按自定义工具解析；单个解析失败仅告警跳过不阻断其余。〔2026-10-06 修订：~~`skill_`/`skill:` 走技能解析（先按 ID 后按 slug）~~ 删除——技能运行时收敛为 SkillsMiddleware 三段式渐进加载（prompt 注入 → read_file 拦截读全文 → 依赖闭包），不再经 ToolRegistry（M3-6，见 REQ-B10 修订）。证据：ToolRegistry.java:27-58（现行）‹CERTAIN›〕
- **REQ-E02**：解析结果仅含启用（enabled=1）且服务在线配置完整的定义；MCP 定义携带服务连接信息与原始工具名（rawName），~~技能定义携带 slug/内容/依赖~~（2026-10-06 删除，随 REQ-E01），HTTP 定义携带完整请求配置。〔证据：ToolRegistry.java:60-121,132（现行）‹CERTAIN›〕
- **REQ-E03**：install_skill 内置工具按条件注入——仅当本次解析上下文存在技能挂载（或沙箱启用）时注册，执行内容为真实技能导入流水线引导（ZIP/SKILL.md 上传→草稿→确认）。〔2026-10-06 grill 修订：原「无条件注入」使桩工具污染全部会话的工具列表，改为条件注入 + 内容坐实；证据：ToolRegistry.java:30-31、InstallSkillToolDefinition.java、InstallSkillToolExecutor.java ‹CERTAIN›；裁决见 docs/plan/skill-runtime-remediation.md〕
- **REQ-E04**：执行按 type 精确路由到唯一执行器（http/mcp/knowledge/builtin 四类；〔2026-10-06 修订：~~skill~~ 随 SkillToolExecutor 死代码清理删除（M3-6），五类收敛四类〕），无匹配返回 null 由调用方处理；执行结果统一为 {success, output, structured?, durationMs, error?}，执行器内部异常自行捕获不外抛。〔证据：ToolExecutorFactory.java:30-35（现行）、ToolResult.java、HttpToolExecutor.java:119-126 ‹CERTAIN›〕
- **REQ-E05**：HTTP 工具执行时模板变量支持 `${inputs.x}`/`${x}`/`${config.x}`/`${context.*}` 四类写法，URL/请求头/查询参数/请求体统一渲染；bodyType 决定 Content-Type（json 缺省/form-data/x-www-form-urlencoded/xml）。〔证据：HttpToolExecutor.java:37-75,92-98 ‹CERTAIN›〕

## 5. 非功能约束

- **安全面（可证）**：技能文件与 ZIP 解压均做路径遍历校验（SkillFileServiceImpl.java:58-65、SkillDraftServiceImpl.java:242-264）；SQL 只读前缀白名单（DbInstanceServiceImpl.java:418-426）；MCP/工具/DB 凭据属主脱敏（McpServiceServiceImpl.java:90-94、ToolServiceImpl.java:52、DbInstanceServiceImpl.java:48-54）。
- **健壮性（可证）**：工具解析单点失败不阻断（ToolRegistry.java:40-42）；MCP 调用日志/lastUsed 回写失败仅告警（McpToolExecutor.java:117-119,130-132）；种子同步逐条 try/catch（McpBuiltinSeeder.java:76-82）。
- **超时（可证）**：MCP 客户端请求超时缺省 30s（refresh 15s）（McpProtocolClient.java:56、McpServiceServiceImpl.java:332）；HTTP 工具执行 60s（HttpToolExecutor.java:103）；测试代理缺省 30s 且上限+5s 兜底（ToolController.java:101,147-148）。
- **性能阈值**：〔决议 2026-09-24：取保守 SLA——①外部 DB 查询沿用既有代码约束：连接 5s/查询行上限 500（DbInstanceServiceImpl.java:34-35,215），整体 P95 <3s；②MCP 调用沿用请求超时 30s（McpProtocolClient.java:56）为上限，正常响应 P95 <5s；③HTTP 工具沿用执行超时 60s（HttpToolExecutor.java:103），P95 <10s；④并发上限：无全局并发闸，MCP 客户端每次调用新建（无池化）、DB 池 max 3——单实例并发由线程池与下游承载，暂不设硬上限；理由：代码外 SLA 按现有超时参数与业界网关惯例保守取值；现状差距：无吞吐/延迟指标埋点，超限不可观测（代裁，用户可推翻）〕
- **审计**：MCP 调用记账（mcp_call_log，写失败仅告警）；DB 实例写操作带 @Loggable（DbInstanceController.java:37-49，全模块唯一审计埋点）‹CERTAIN（2026-09-24 深证并改正原断言）：grep 实测 **McpController 同样无 @Loggable（0 处）**，ToolController/SkillController 亦为 0——四控制器中仅 DB 域有审计，tool/skill/mcp 三个管理面均缺审计埋点›（原「DB 实例与 MCP 写操作带 @Loggable」中 MCP 半句与代码不符，以本条为准；不对称性登记为审计面债务）。

## 6. 验收清单

- [ ] 非属主用户列工具可见但读不到 HTTP 配置与 authValue（REQ-A04）
- [ ] 前端经测试代理向 4xx/5xx 目标发请求，收到含真实状态码与 error 详情的成功响应包而非业务错误（REQ-A06）
- [ ] 缺 name 或 description 的 SKILL.md 上传被拒并提示具体缺失字段（REQ-B01）
- [ ] 删除内置技能被拒绝，删除自定义技能后 skill_dependency/skill_scope 无残留（REQ-B04）
- [ ] 技能文件接口传 `../../etc/passwd` 类路径被路径遍历校验拒绝（REQ-B06）
- [ ] ZIP 内含 `../` 条目条目被拒并中止解压（REQ-B07）
- [ ] 技能依赖引用禁用工具/未启用 MCP 服务/自身时报错，引用启用项通过（REQ-B08）
- [ ] 非管理员提交 shareConfig.accessLevel=global 被强制降为 user（REQ-B09）
- [ ] parse-url 探查有效 MCP 地址返回工具列表且 mcp_service 表无新记录（REQ-C02）
- [ ] 刷新失败的 MCP 服务 status=error，刷新成功后工具清单与服务器 tools/list 一致（REQ-C03）
- [ ] 非属主读 MCP 服务详情不含 authValue/env（REQ-C07）
- [ ] 内置 MCP 服务种子重跑不覆盖用户对 enabled 的修改（REQ-C06）
- [ ] 对外部库执行 `DROP TABLE` / `UPDATE` 被前缀白名单拒绝（REQ-D03）
- [ ] 非属主读数据库实例密码显示 `******` 且 jdbcUrl 为 null（REQ-D02）
- [ ] 表结构同步重复执行不产生重复 db_table 记录且 syncedAt 刷新（REQ-D06）
- [ ] toolIds 混合含无效 ID 时其余工具仍被解析注入（REQ-E01）
- [ ] 未注册任何执行器类型字符串时 getExecutor 返回 null 不抛异常（REQ-E04）
- [ ] 工具列表按名称关键字与类型筛选生效，且仅含当前用户可见条目（REQ-A01）
- [ ] 创建工具时 creator/orgId 取当前用户、identifier 自动生成、enabled 缺省启用（REQ-A02）
- [ ] params/headers 以 {key,value} 数组提交后统一序列化为 JSON 字符串（REQ-A03）
- [ ] 非属主修改/删除/启停他人工具被拒绝（REQ-A05）
- [ ] 插件单文件上传创建与 JSON 数组批量导入均成功且导入条目缺省启用（REQ-A07）
- [ ] 配置 outputMapping 后执行结果按映射路径提取值，提取失败回退原始响应（REQ-A08）
- [ ] 技能列表的全量/可访问/内置三种视图口径正确（禁用项仅出现在全量列表）（REQ-B02）
- [ ] 更新技能后依赖与作用域全量替换（先删后插），dependencies JSON 列同步冗余（REQ-B03）
- [ ] 启动种子重跑：DB 已有记录的内置技能整体跳过（不更新 DB、不覆盖磁盘目录）；仅 DB 缺失记录时播种并复制目录（REQ-B05，2026-10-06 修订）
- [ ] read_file 命中 `/workspace/skills/{slug}/SKILL.md` 返回含正文指令的全文（DB content 为源，不依赖沙箱存活）；技能依赖的 tool/mcp 实体经校验后随元数据交付（REQ-B10，2026-10-06 修订）
- [ ] 创建 MCP 服务缺省传输 sse、初始 status=offline（REQ-C01）
- [ ] 手动添加 MCP 工具时参数数组自动归一为 JSON Schema，工具 ID 形如 mcp__{服务Camel}__{工具Camel}（REQ-C04）
- [ ] 工具测试对真实服务器执行 tools/call 返回 {success, output, durationMs}，服务器异常归一为 success=false（REQ-C05）
- [ ] MCP 工具运行时调用自动附认证头，调用成败均记 mcp_call_log 并回写 lastUsed（REQ-C08）
- [ ] 对真实 MCP 服务器完成 initialize→tools/list→tools/call 后工具可用；stdio 命令被显式拒绝（REQ-C09）
- [ ] 数据库实例注册缺省 readOnly=1 与 disconnected，JDBC URL 缺省按类型自动拼接（REQ-D01）
- [ ] 只读查询返回 {columns, rows, rowCount, elapsedMs}，执行异常归入 error 字段并回空列行（REQ-D04）
- [ ] 连接测试执行 SELECT 1 回 {connected, latencyMs} 并回写实例状态（REQ-D05）
- [ ] MCP 定义仅服务在线时解析且携带连接信息与 rawName（REQ-E02；~~技能定义携带 slug/内容/依赖~~ 条款 2026-10-06 删除）
- [ ] 仅当解析上下文存在技能挂载（或沙箱启用）时 install_skill 出现且唯一，无挂载且沙箱未启用时不出现（REQ-E03，2026-10-06 修订）
- [ ] HTTP 工具执行时四类模板变量（${inputs.x}/${x}/${config.x}/${context.*}）均正确渲染且 bodyType 决定 Content-Type（REQ-E05）

## 7. 测试策略

经由公共接口（controllers + ToolRegistry/ToolExecutorFactory 两个内部深接口）分层验证：

1. **安全面优先**（任务重点）：SQL 注入面——只读前缀白名单的绕过尝试（注释尾随、前导空白、大小写、多语句）与越权查询（非属主）；路径遍历面——技能文件 CRUD 与 ZIP 解压的 `..`/绝对路径/规范化逃逸；凭据脱敏面——三类资源非属主读取。
2. **依赖与一致性**：技能依赖校验四类分支、重复/自引用/未知类型；MCP 刷新全量替换的幂等性（重复刷新不产生重复工具）；种子同步的「保留用户字段」语义；内置资源删除保护。
3. **协议边界（外部 MCP server = 系统边界，mock 服务器）**：initialize 握手、session 头回传、SSE 响应提取、JSON-RPC error 透传、未连接调用拒绝、stdio 拒绝；传输降级（连接失败/超时）的 status 流转与调用日志落库。
4. **执行路由**：四类执行器分派（~~五类~~，2026-10-06 收敛）、无效工具 ID 跳过、模板变量四类写法渲染、outputMapping 提取与回退。
5. **既有测试基线**：fastrag-tools 16 个测试类 137 个用例（McpServiceServiceTest 16、SkillAccessManagerTest 14、McpControllerTest 13、SkillDependencyValidatorTest 12、SkillFileServiceImplTest 12 等）为本链回归基线（见 tdd 文档切片规划）。
