# 工具 / 技能 / MCP / 外部数据库（tools-skills-mcp） — 实现文档

## 1. Code File Map

```
fastrag-modules/fastrag-tools/src/main/java/com/fastrag/module/tools/
├── config/
│   ├── McpBuiltinSeeder.java          # 内置 MCP 服务启动种子器（CommandLineRunner，3 个内置服务增量同步）
│   └── SkillDataInitializer.java      # 内置技能启动种子器（DB content 唯一事实源：无该 slug 记录才播种+复制目录，已有整体跳过——M3-4 裁决 3A，取代 REQ-B05 MD5 更新语义）
├── controller/
│   ├── ToolController.java            # /api/tools 11 端点（CRUD/toggle/test-proxy/api-config/upload/import-json）
│   ├── SkillController.java           # /api/skills 24 端点（javadoc 列 18，另含 enabled/scopes/dependency-options 等实数）
│   ├── McpController.java             # /api/mcp-services 17 端点（CRUD/parse-url/refresh/tools 增删改测）
│   └── DbInstanceController.java      # /api/databases 10 端点（CRUD/tables/test-conn/query/sync-tables）
├── entity/                            # Tool、ToolHttpConfig、Skill、SkillDependency、SkillScope、
│                                      # McpService、McpTool、McpCallLog、DbInstance、DbTable + 7 个 DTO
├── executor/                          # ToolExecutor 接口 + 4 实现 + Factory + ToolDefinition/Result/Context
│   ├── HttpToolExecutor.java          # type=http：模板渲染→WebClient 执行→outputMapping
│   ├── McpToolExecutor.java           # type=mcp：建连调用+调用记账
│   ├── KnowledgeToolExecutor.java     # type=knowledge：list_kbs/query_kb（转调 KbOperationService）
│   ├── InstallSkillToolExecutor.java  # type=builtin：install_skill 真实导入流水线指引（M3-3 坐实）
│   └── InstallSkillToolDefinition.java# install_skill 静态定义（@PostConstruct 单例）
│                                      # M3-6：SkillToolExecutor（type=skill）已删除，执行器五类收敛为四类
├── mapper/                            # 10 个 MyBatis-Plus mapper（SkillMapper 含 4 个 @Select 自定义）
├── mcp/McpProtocolClient.java         # MCP JSON-RPC over Streamable HTTP 客户端（AutoCloseable）
├── registry/
│   ├── ToolRegistry.java              # toolIds→ToolDefinition 解析 + OpenAI function 格式输出（M3-6 起无 skill_/skill: 分支）
│   └── ToolDefinition.java            # 运行时统一定义
├── skill/
│   ├── SlugValidator.java             # slug 白名单 ^[a-z0-9][a-z0-9._-]{0,63}$（M1-2：validate/isValid/normalize，创建/导入/文件访问/草稿四入口统一强制）
│   ├── SkillAccessManager.java        # 三级分享权限判定（M3-1 已接线：SkillServiceImpl.visible 分享通道）+ normalizeShareConfig 降级
│   ├── SkillDependencyValidator.java  # tool/mcp/skill/model 四类依赖校验 + 下拉选项
│   └── SkillMarkdownParser.java       # SKILL.md frontmatter 解析（M3-2 起含 tool/mcp/skill 三依赖字段 + dependencyRefs 规范串）
└── service/ (+impl/)                  # ToolService、SkillService、SkillFileService、SkillDraftService、
                                       # McpServiceService、DbInstanceService 六对接口/实现
fastrag-bootstrap/.../config/SchemaInitializer.java  # 工具域组织隔离列运行时增补（:207-229,375-386）
src/test/java/.../ 19 个测试类（226 用例，2026-10-06 技能域修复后；修复前基线 16 类 137 用例全绿）
```

镜像说明：与 design Module Map 一一对应——HttpToolModule=controller/Tool+service/ToolServiceImpl+entity/Tool*；SkillFileModule=service/SkillFile*+SkillDraft*；McpModule=controller/Mcp+service/McpServiceServiceImpl+config/McpBuiltinSeeder；其余同名。

## 2. Interface Implementations

### ToolRegistry.resolveTools / toOpenAITools（from contract: tools-skills-mcp.ToolRegistry）

**Logic Flow:**
1. 结果列表先放 `InstallSkillToolDefinition.getDefinition()`（静态单例，@PostConstruct 兜底懒建）。
2. toolIds 为空即返回（仅含 install_skill）；逐个 trim 后进 resolveSingleTool。
3. IF id 以 `mcp_`/`mcp:` 开头 → resolveMcpTool：`Long.parseLong(id)` 解析 mcp_tool 主键（非法数字→异常→外层 catch 跳过）；查 mcp_tool（enabled≠1 → null）→ 查所属 mcp_service（无 → null）→ 组装 config（mcpServiceId/Name、transport、mcpUrl、command、args、env、authType/Value、enabled、**rawName**=mcp_tool.name）。
4. ELSE resolveHttpTool：查 tool（enabled≠1 → null）→ 查 tool_http_config → config 五字段 + parseJsonMap(params/headers)（对象/`{key,value}` 数组/解析失败三分支）。
5. 单个解析任何异常 → log.warn 跳过，继续下一个。
6. toOpenAITools：inputSchema 有 `type` 键直接用；仅 `properties` 包 `type:object`；否则空 Schema。
7. M3-6：原第 4 步 `skill_`/`skill:` 前缀路由与技能定义构建已删除——技能经 SkillsMiddleware 三段式渐进加载（prompt 注入 → read_file 激活 → 依赖闭包），不再是单工具形态；install_skill 的去留改由 AgentEngine 引擎侧条件过滤（M3-3，见 agent.md）。

**Module Interactions:** Calls — ToolMapper/ToolHttpConfigMapper/McpToolMapper/McpServiceMapper 四 mapper（M3-6 移除 SkillMapper 依赖）；ChatRequest.ToolDefinition（fastrag-ai）。Called by — AgentEngine（:126,402）、AgentExecutor、app-center 绑定链（消费 toolIds）。Shared state — 只读 tool/tool_http_config/mcp_tool/mcp_service 四表。

**Decisions & Deviations:**
- 与契约一致；两处偏离登记：① resolveTools **不做可见性过滤**（契约 Invariants-Pre 已声明，运行面信任 toolIds）；② `database` 类型在 ToolDefinition 注释中预留但无解析分支——agent 侧 DatabaseToolExecutor 另行直查 db_instance（路径分裂，design §4 登记收敛方向）。
- **M3-6 契约偏离（REQ-E01/E02）**：`skill_`/`skill:` 前缀路由与 type=skill 工具定义构建已随 SkillToolExecutor 一并删除；技能运行时形态收敛为 SkillsMiddleware 三段式渐进加载。契约 REQ-E01/E02 待下次契约同步修订。

**Parent Trace:** [ToolRegistry] → [tools-skills-mcp.ToolRegistry] → [ToolRegistryModule（缝 3）] → [REQ-E01~E03]。

### ToolExecutorFactory.getExecutor（from contract: tools-skills-mcp.ToolExecutorRouter）

**Logic Flow:**
1. Spring 构造注入 `List<ToolExecutor>`（四个实现自动收集，M3-6 起五类收敛为四类）。
2. getExecutor(type)：stream filter `getType().equals(type)` → findFirst → 无匹配返回 null。
3. 各执行器 execute 内部全 try/catch：失败构造 ToolResult{success=false, error, durationMs}，绝不外抛。

**Module Interactions:** Calls — 无（纯分派）。Called by — AgentEngine:651。Shared state — 无。
**Decisions & Deviations:** 与契约一致。执行器内部：HttpToolExecutor 60s 超时 + maxInMemory 10MB；McpToolExecutor 30s、成败均写 mcp_call_log（写失败 log.warn 吞）、成功回写 lastUsed；KnowledgeToolExecutor 参数别名归一（kb_id/knowledge_base、query/question）后转调 KbOperationService（实现类在 fastrag-knowledge，跨模块共享接口位于 fastrag-common）。
**M3-6 契约偏离（REQ-E04）**：契约的五类执行器（http/mcp/skill/knowledge/builtin）收敛为四类——type=skill 的 SkillToolExecutor 已删除（孤儿代码：read_file 激活路径使 read_skill/activate 文本协议无生产调用方），契约 REQ-E04 待下次契约同步修订。

**Parent Trace:** [ToolExecutorFactory+5 执行器] → [tools-skills-mcp.ToolExecutorRouter] → [ToolExecutorRouter（缝 1）] → [REQ-E04、REQ-C05、REQ-C08、REQ-E05、REQ-A08]。

### McpProtocolClient.connect / listTools / callTool（from contract: tools-skills-mcp.McpProtocolClient）

**Logic Flow:**
1. connect：connected 短路；stdio → 抛 UnsupportedOperationException；mcpUrl 空 → IllegalArgumentException。
2. 发 initialize JSON-RPC（protocolVersion=2025-11-25、clientInfo=fastrag/1.0.0）→ 从响应头取 `mcp-session-id`，取不到再读 result.sessionId，再取不到视为无状态服务。
3. 发 notifications/initialized（fire-and-forget，try/catch 内 log.warn）。
4. listTools/callTool：ensureConnected（未连抛 IllegalStateException）→ sendJsonRpc → 解析 tools 数组 / 拼接 content 全部 text 项（空则 result.text 或 `[no text content]`）。
5. sendJsonRpcWithHeaders：请求体 jsonrpc=2.0 + 自增 id；恒带头 Content-Type/Accept + 自定义 headers + 会话头；非 2xx → 抛「HTTP xxx」；空体 → 抛；响应体经 extractJsonFromSse（非 `{` 开头则拼 data: 行，空回退原文）→ error 字段存在抛 message → 缺 result 抛 → 返回 (result, headers)。
6. close：置空 httpClient/connected（Java 17 HttpClient 非 AutoCloseable，仅释放引用）。

**Module Interactions:** Calls — java.net.http.HttpClient、Jackson。Called by — McpServiceServiceImpl（parseUrl:188/refresh:325/testTool:465）、McpToolExecutor:70。Shared state — 无表；实例内 sessionId/requestId 状态，生命周期=单次调用（每次 new）。
**Decisions & Deviations:** 与契约一致。实现取舍：① 注释自称「SSE 客户端」实际为 Streamable HTTP POST 单请求协议（SSE 仅作响应体格式嗅探）；② stdio 仅拒绝桩（GAP 已登记）；③ 连接不池化——每工具调用全握手一轮（design §4 深化项）。

**Parent Trace:** [McpProtocolClient] → [tools-skills-mcp.McpProtocolClient] → [McpProtocolClient（缝 2）] → [REQ-C02、REQ-C03、REQ-C09]。

### SkillFileVault 八方法（from contract: tools-skills-mcp.SkillFileVault）

**Logic Flow（SkillFileServiceImpl）:**
0. 鉴权+slug 校验（M1-1/M1-2）：六公开方法入口先 SlugValidator.validate(slug) → skillMapper.selectBySlug（查无抛「技能不存在」404 语义）→ 读类（getTree/readFile/exportZip）DataScope.visible、写类（createNode/updateFile/deleteNode）DataScope.manageable，不过抛 403 语义 BusinessException；user 取 SecurityUtil.getCurrentUser()（operator 参数仅作日志，不参与鉴权，防伪造）。
1. 根定位 `Paths.get(dataDir, "skills", slug)`；读/写/删前一律 validateAndResolve：resolve→normalize→startsWith(根.normalize)，越界抛 SecurityException。
2. getTree：目录不存在返回空根树；存在递归 buildTree（隐藏文件过滤、排序）。
3. readFile：越界→error 字段；不存在/目录→exists=false；否则读全文+元数据。
4. createNode：已存在抛；isDir→createDirectories，否则建父+writeString；根 SKILL.md 先解析后落盘并回写 DB（M3-4）。
5. updateFile：不存在或目录抛；覆写；relativePath 为根 SKILL.md 时先经 SkillMarkdownParser 解析再写盘并回写 DB（content/contentHash/name/description/version/category/metadata 随 frontmatter 更新，解析失败不落盘，M3-4 正文事实源）。
6. deleteNode：不存在抛；目标=根抛「不能删除技能根目录」；目录 walk 逆序删。
7. exportZip：目录不存在抛；临时文件打包（隐藏项排除、`/` 统一）；控制器 finally 删临时文件。

**Logic Flow（SkillDraftServiceImpl）:**
1. prepareUpload：先惰性清理过期草稿 → UUID draftId 建草稿目录 → .zip 走 extractZip（条目含 `..`/绝对路径/越界抛 SecurityException）、.md 单文件 slug（文件名派生）先过 SlugValidator 再归位 `{slug}/SKILL.md`（M1-2，异常通路 best-effort 删残留草稿目录）→ reorganizeSingleSkill（ZIP 根直有 SKILL.md 时按解析 slug 移入子目录；目录用复制+删避 Windows Files.move 限制）→ scanSkillItems 逐 SKILL.md 解析（失败项带 errorMsg）→ items 空抛「未找到 SKILL.md」→ 写 metadata.json（TTL 1h）。
2. confirmDraft：draftId 先过 UUID 严格格式校验（M1-2，非 UUID 抛「非法的草稿 ID」）→ 目录/metadata 不存在抛 → 过期则 discard+抛「草稿已过期」→ shareConfig 经 SkillAccessManager.normalizeShareConfig 归一（M3-1：非管理员提交 global/department 强制降级 user）→ 遍历草稿子目录：无 SKILL.md 记失败；解析失败记失败；成功路径=slug 已存在则 **update-in-place**（M3-5：保留主键 updateById，name/description/content/contentHash/version/category/metadata/author 随解析更新，app_skill_binding 因 id 不变天然存活；不再 deleteById+insert）→ 依赖经 SkillDependencyValidator 校验后先删后插 skill_dependency + dependencies JSON（M3-2）→ 清残留目录+copyDirectory 到 `skills/{slug}/` → 新装才建 Skill 记录（sourceType=upload、contentHash=MD5、enabled=1、author=operator、creator=operator、orgId 取当前用户，M1-3）→ 逐项独立成败 → 删草稿目录。
3. discardDraft：draftId 同 UUID 校验；存在则递归删，幂等。

**Module Interactions:** Calls — SkillMapper、SkillMarkdownParser、ObjectMapper。Called by — SkillController（文件五端点+export+import 三端点）。Shared state — 磁盘 `{data-dir}/skills/{slug}` 与 `skill_import_drafts/`；skill 表（confirmDraft 写）。
**Decisions & Deviations:** 与契约一致。偏离登记：① 控制器把 prepare+confirm 串为单步「上传即安装」且固定 shareConfig={accessLevel:user}（SkillController.java:257-271），两步草稿端点冗余（契约 Invariants 已登记）；② SkillDataInitializer 内置同步复制目录但不回写实体 dirPath 列（列存在无写入方）；③ extractZip 对 entry 含 `..` 直接抛（中止全部解压）——比 contract 描述的「逐条拒绝」更严格，属实现从紧。

**Parent Trace:** [SkillFileServiceImpl + SkillDraftServiceImpl] → [tools-skills-mcp.SkillFileVault] → [SkillFileModule] → [REQ-B06、REQ-B07]。

### DbQueryGateway 十方法（from contract: tools-skills-mcp.DbQueryGateway）

**Logic Flow:**
1. 可见性：list/get/listTables/testConnection 走 DataScope.visible（creator/orgId/system 存量四态）；update/delete/query/syncTables 走 requireManage（manageable）。
2. create：createdBy/orgId 注入；readOnly/status 缺省（1/disconnected）；jdbcUrl 空则 buildJdbcUrl 按五类型拼接（host 缺省 127.0.0.1、端口按类型表）。
3. update：host/port/dbName 任一变化重拼 URL。
4. delete：事务内先删该库全部 db_table 再删实例。
5. testConnection：动态 HikariDataSource → `SELECT 1` → {connected, latencyMs} + 回写 status=connected；任何异常 → {connected:false, error} + status=disconnected。
6. query：requireManage → trim 后 isReadOnlySql（大写 startsWith 白名单五词）不通过 → result.error 只读提示空包（**不抛**）→ 通过则 JdbcTemplate（maxRows=500, fetchSize=100）执行 → ResultSetMetaData 提取列名 → 行循环≤500 组 Map → {columns, rows, rowCount, elapsedMs}；异常 → error 空包。
7. syncTables：requireManage → DatabaseMetaData.getTables（mysql 用 catalog 作 schemaPattern）→ 每表 getColumns 列信息 + `` SELECT COUNT(*) FROM `表名` `` 行数（失败仅 warn 计 0）→ 按 (dbId,tableName) upsert db_table → status=connected；异常 → {error, syncedCount} + status=disconnected。
8. 脱敏：maskCredentials 非属主 password="******"、jdbcUrl=null（list/get 两路径）。

**Module Interactions:** Calls — DbInstanceMapper/DbTableMapper、HikariCP、Spring JdbcTemplate、cn.hutool JSONUtil。Called by — DbInstanceController。Shared state — db_instance/db_table 两表；外部库连接（每次动态建池销毁）。
**Decisions & Deviations:** 与契约一致。偏离登记：① `status` DDL 缺省 'connected' 与代码缺省 'disconnected' 不一致（data.md 已登记，新建走代码路径无实害）；② pool_config 字段无消费方（池参数硬编码）；③ 只读防线=前缀白名单单层，注释粘连形态（`SELECT/*…*/;DROP` 类）行为待 HITL 裁决（tdd S1），深层依赖 Hikari readOnly 属性与外部账号权限（GAP）。

**Parent Trace:** [DbInstanceServiceImpl] → [tools-skills-mcp.DbQueryGateway] → [DbInstanceModule] → [REQ-D01~D06]。

### SkillModule / McpModule 管理面（from contract: data.md 实体契约 + design §2 端点面）

**Logic Flow（要点）:**
- SkillServiceImpl：list/listAccessible 内存收口 filterVisible（M3-1：列表查询全量取回后逐条 visible()=DataScope 四态 + SkillAccessManager.shareVisible 三级分享，分享可见为 JSON 语义无法下推 SQL，结果为原 SQL 语义纯超集）；get/getBySlug 同口径可见性校验；create/update 事务内 saveDependencies（M3-2 起先过 SkillDependencyValidator，非法抛 BusinessException）/saveScopes 全量替换（deleteBySkillId→逐条插）；updateDependencies 同步冗余 `type:name` 列表到 skill.dependencies；updateShareConfig 经 normalizeShareConfig 降级后落 share_config 列（M3-1 持久化）；delete 内置拒绝+级联。
- McpServiceServiceImpl：list/attachToolCounts 附 toolCount 并列表脱敏（authValue/env 非属主移除）；getWithTools 详情脱敏同规则；parseUrl/refresh/testTool 共享「认证头构造→try(McpProtocolClient)」模式；refresh 全量替换工具+status 双态流转；create/update 接受 toolsList 手动工具（update 为替换语义）；normalizeParams 数组→JSON Schema 归一；toCamelCase 生成 `mcp__X__Y`。
- 双种子器：McpBuiltinSeeder（slug+is_builtin=1 查重，更新不碰 enabled/status/lastUsed）；SkillDataInitializer（**M3-4 裁决 3A：DB content 唯一事实源**——DB 无该 slug 记录才 insert+整目录复制，已有记录整体跳过不更新 DB 不覆盖磁盘；Deviation：取代 spec REQ-B05 的「MD5 变化更新」语义）。

**Module Interactions:** SkillService Called by — SkillController、agent SkillsMiddleware（直读实体）；McpServiceService Called by — McpController、agent McpMiddleware（直读 mapper）。Shared state — skill/skill_dependency/skill_scope/mcp_service/mcp_tool 五表；磁盘 skills/ 目录（Initializer）。
**Decisions & Deviations:**
- ~~**SkillAccessManager 生产零调用**~~ **M3-1 已消解（2026-10-06）**：shareVisible 接入 SkillServiceImpl.visible 五通道（global/department/user 三级 + DataScope 四态），selectCreatedBy 列名修正为 `creator`；normalizeShareConfig 降级规则接入 updateShareConfig 与 confirmDraft 两入口。department 分支=user.orgId ∈ departmentIds（原占位恒 false 语义被替换）。
- skill_scope 表无运行时消费方（写读回显闭环，无约束作用）。
- SkillServiceImpl.importSkillDir 恒返回 fail「请使用 SkillDataInitializer 直接处理」——死代码路径。
- **M3-4 Deviation（对 spec REQ-B05）**：启动播种语义由「MD5 变化更新 DB + REPLACE_EXISTING 复制」改为「无记录才播种、已有整体跳过」——正文唯一事实源从磁盘 classpath 移交 DB content（用户经文件 API 的修改经 SkillFileServiceImpl 根 SKILL.md 回写 DB 后不再被启动覆盖）。裁决 2026-10-06 3A。
- **M1 偏离登记（2026-10-06）**：① 文件端点鉴权置于 SkillFileVault 实现内部（SecurityContext 判定，接口签名不动，防 operator 伪造）——契约 Invariants 补前置不变式「读=visible/写删=manageable」待契约同步；② draftId 校验采用 UUID 严格格式（比 slug 白名单更严，draftId 本就 UUID 生成）；③ slug 缺省派生为新增行为（normalize(name)，无法派生 400 提示显式提供 slug）；④ discardDraft（删草稿非删技能）不埋审计；⑤ 审计 12 埋点落 SkillController（skill_created/updated/deleted + 新增枚举 skill_file_created/updated/deleted + confirm→skill_created）。

**Parent Trace:** [SkillServiceImpl/SkillDependencyValidator/SkillAccessManager/McpServiceServiceImpl/McpBuiltinSeeder/SkillDataInitializer] → [data.md 实体契约 + design 端点面] → [SkillModule/McpModule] → [REQ-B01~B05、REQ-B08~B10、REQ-C01~C04、REQ-C06~C07]。

## 3. Testing

引用 tdd/tools-skills-mcp.md 切片。**现有测试覆盖**（19 类 226 用例，2026-10-06 技能域修复后；修复前基线 16 类 137 用例全绿，零回归）：
- **M1 安全面（+54）**：SlugValidatorTest 18（合法/非法矩阵）、SkillFileServiceImplTest 28（403/404/穿越/内置技能 API Token 放行/写删不改文件）、SkillDraftServiceImplTest 17（draftId 穿越/上传文件名穿越/creator+orgId 回填/失败无残留草稿目录）、SkillControllerAuditTest 14（12 埋点注解反射校验）、SkillServiceImplTest/SkillMarkdownParserTest 增量（slug 派生与非法 slug 拒绝）。
- **M3 接线（+44）**：SkillServiceImplTest 23（分享五通道/降级/依赖过校验器）、SkillAccessManagerTest 22、SkillDdlParityTest 5（schema.sql↔SchemaInitializer 列集合+idx_slug+四表对齐守护，防再漂移）、SkillDataInitializerTest 2（无记录播种/已有整体跳过）、SkillFileServiceImplTest 增量（根 SKILL.md 回写 DB）、InstallSkillToolExecutorTest 9（真实导入指引）。
- 既有面：McpProtocolClientTest 6 + IntegrationTest 5（S3）、McpServiceServiceTest 16、McpControllerTest 13、SkillDependencyValidatorTest 12、SkillShareConfigTest 5、McpBuiltinSeederTest 4。

**缺口**（= tdd 新增主战场）：S1 全部（DbInstanceServiceImpl 零测试——SQL 只读门/行上限/脱敏/同步幂等无任何守护）；S6 全部（ToolServiceImpl/ToolController 零测试）；ToolRegistry/ToolExecutorFactory 无直接测试（AgentEngineTest 经引擎间接覆盖解析入口）。原「SkillDataInitializer 零测试」缺口已闭合（M3-4）。红绿循环：技能域已随 M1~M3 完成（每项先红后绿），非技能域切片仍未执行。
