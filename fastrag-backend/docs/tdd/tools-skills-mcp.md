# 工具 / 技能 / MCP / 外部数据库（tools-skills-mcp） — 测试设计

> 本文件为测试**设计**（onboard 模式）：不写测试代码。切片按 contract/interfaces.md `## tools-skills-mcp` 节五个契约接口分组；既有测试（fastrag-tools 16 个测试类 137 个用例）作为基线标注在各片。mock 只允许在真缝/系统边界：外部 MCP server、外部数据库、外部 HTTP 目标、磁盘文件系统（临时目录）、时间。

## 1. 切片规划

| 切片 | 契约接口 | AFK/HITL | 既有基线 |
|---|---|---|---|
| S1 SQL 注入与只读门（**HITL 优先**：白名单绕过判定的取舍需人确认） | DbQueryGateway | HITL→AFK | 无（DbInstanceServiceImpl 零测试——最大缺口） |
| S2 技能文件安全与安装流水线 | SkillFileVault | AFK | SkillFileServiceImplTest 12、SkillDraftServiceImplTest 7、SkillMarkdownParserTest 8 |
| S3 MCP 协议与传输降级（系统边界 mock 服务器） | McpProtocolClient | AFK | McpProtocolClientTest 6、McpProtocolClientIntegrationTest 5、McpServiceServiceTest 16、McpControllerTest 13 |
| S4 工具解析与执行路由 | ToolRegistry + ToolExecutorRouter | AFK | 四执行器 Test 5+7+7+7；ToolRegistry/Factory 无直接测试‹CERTAIN（2026-09-24 深证并改正）：fastrag-tools 测试树无 ToolRegistry/Factory 任何引用；agent 侧 DatabaseToolExecutorTest 测的是 DbInstanceMapper 直查路径、**不经 ToolRegistry**，原「间接覆盖」说法不成立——本缺口比原判更深，解析/路由面整体无测试守护› |
| S5 依赖校验与分享配置 | SkillDependencyValidator + SkillAccessManager (S) | AFK（S5b 接线后回归） | SkillDependencyValidatorTest 12、SkillAccessManagerTest 14 |
| S6 HTTP 工具域与凭据脱敏（含 test-proxy） | HttpToolModule（design 面，端点即接口） | AFK | 无（ToolServiceImpl/Controller 零测试） |
| S7 外部 DB 连接与元数据同步 | DbQueryGateway | AFK（外部 DB 用 H2/容器） | 无 |
| S8 内置种子同步幂等 | McpModule 种子 + SkillModule 内置同步 | AFK | McpBuiltinSeederTest 4；SkillDataInitializer 无测试 |

执行序：S1 → S2 → S3 → S4 → S5 → S6 → S7 → S8（安全面先行；S1 的 HITL 裁决结果回填 S7 的深层防御断言）。

## 2. 测试用例

### SkillFileVault（contract: tools-skills-mcp.SkillFileVault）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [越界路径] 读技能文件时传 `..\..\secret` [产出] error 而非文件内容 | 技能目录已存在 | readFile(slug, "../../secret") | exists=false、error 含「路径遍历」、目标文件内容不可达 | REQ-B06 | 临时目录 |
| [越界路径] 解压含 `../` 条目的 ZIP [产出] SecurityException 且落盘中止 | prepareUpload 收到恶意 ZIP | prepareUpload | 抛 SecurityException、草稿目录内无越界文件 | REQ-B07 | 临时目录 |
| [越界路径] 绝对路径条目 `/etc/passwd` [产出] 拒绝 | 同上 | prepareUpload | 同上 | REQ-B07 | 临时目录 |
| [删除保护] 删除技能根目录 [产出] 报错且目录保留 | slug 目录存在 | deleteNode(slug, "") | 抛「不能删除技能根目录」、目录仍在 | REQ-B06 | 临时目录 |
| [上传安装] 合法多技能 ZIP [产出] 逐技能 success 且库+磁盘双落位 | ZIP 内 2 个合规 SKILL.md 子目录 | prepareUpload→confirmDraft | 返回 2 个 success=true 项；skill 表 2 记录（contentHash=MD5）；磁盘 skills/{slug}/ 存在；草稿目录已删 | REQ-B07 | 临时目录 |
| [上传安装] 缺 name 的 SKILL.md [产出] 该项失败其余成功 | ZIP 内 1 合规 1 缺 name | confirmDraft | 失败项 errorMsg 含「name」；合规项成功 | REQ-B01、REQ-B07 | 临时目录 |
| [覆盖重装] 同 slug 重复安装 [产出] 旧记录旧目录被替换且无级联残留 | slug 已有 skill+dependency 记录 | confirmDraft 同 slug | skill 表仍 1 条（新 contentHash）；旧 dependency 清空；旧磁盘目录内容被替换 | REQ-B07 | 临时目录 |
| [草稿过期] TTL 过期后 confirm [产出] 报「草稿已过期」且草稿已删 | expiresAt < now（注入时间） | confirmDraft | RuntimeException「草稿已过期」、草稿目录不存在 | REQ-B07 | 时间 |
| [导出] 含隐藏文件目录导出 ZIP [产出] 隐藏项被排除 | 目录含 `.hidden` 与 `a.md` | exportZip | ZIP 仅含 a.md 条目 | REQ-B07 | 临时目录 |

### DbQueryGateway（contract: tools-skills-mcp.DbQueryGateway）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [只读门] 前导注释的 SELECT `/*x*/DROP TABLE t` [产出] 语义裁决 | H2 实例就绪 | query(dbId, "/*x*/DROP TABLE t") | **HITL**：现状实现 startsWith("SELECT") 不匹配前导注释→被拒（前缀非白名单词）但 `SELECT/*x*/…` 类粘连形态行为待裁决；断言按裁决结论固定，目标=任何非只读语义不执行 | REQ-D03 | H2 |
| [只读门] UPDATE/INSERT/DELETE/CREATE [产出] 拒绝且空结果包 | H2 实例就绪 | query 分别传四种语句 | error 含「仅允许执行」、columns/rows 空、rowCount=0、不抛异常 | REQ-D03 | H2 |
| [只读门] `select` 小写与 `EXPLAIN ` 前缀 [产出] 放行 | H2 实例就绪 | query("select 1") / query("EXPLAIN select 1") | 返回 columns/rows 正常包 | REQ-D03 | H2 |
| [越权] 非属主调 query/syncTables/update/delete [产出] 403 语义 | 实例属主=他人 | 四操作分别调用 | BusinessException「无权管理该数据库实例」 | REQ-D03 | 无（SecurityUtil 注入 LoginUser） |
| [脱敏] 非属主读实例 [产出] 密码掩码 URL 置空 | 属主创建含密码实例 | get/list（非属主上下文） | password="******"、jdbcUrl=null；属主读全量 | REQ-D02 | 无 |
| [行数上限] 查询返回 >500 行 [产出] 截断至 500 | H2 表预置 600 行 | query("SELECT * FROM t") | rowCount=500、rows.size()=500 | REQ-D03、REQ-D04 | H2 |
| [执行失败] 目标库语法错 [产出] error 包 + 状态回写 disconnected | 实例指向不可用/错语句 | query 非法 SQL | error 非空、rows 空、db_instance.status=disconnected | REQ-D04 | H2（或拒连 mock） |
| [连通测试] 可达库 [产出] connected=true 且状态回写 | H2 就绪 | testConnection | connected=true、latencyMs≥0、status=connected | REQ-D05 | H2 |
| [元数据同步] 重复 sync [产出] 幂等不重复 | 首次 sync 后再 sync | syncTables 两次 | 两次 syncedCount 相等；db_table 无重复 (db_id,table_name)；syncedAt 刷新 | REQ-D06 | H2 |
| [级联删除] 删实例 [产出] 表记录清空 | 实例含 2 条 db_table | delete(dbId) | db_table 中 db_id 无残留 | REQ-D06 | H2 |

### McpProtocolClient（contract: tools-skills-mcp.McpProtocolClient）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [握手] initialize 响应带 mcp-session-id 头 [产出] 后续请求携带该头 | mock MCP 服务器回 initialize+sessionId | connect→listTools | listTools 请求头含 mcp-session-id=会话值 | REQ-C09 | HttpServer mock |
| [握手] sessionId 仅在 result.body [产出] 从 body 提取 | 服务器回 body.sessionId | connect | 后续请求携带该 sessionId | REQ-C09 | HttpServer mock |
| [响应格式] SSE 体 `data:` 行 [产出] 提取 JSON 成功 | tools/list 回 SSE 格式体 | listTools | 解析出工具数组、name/description/inputSchema 正确 | REQ-C09 | HttpServer mock |
| [错误透传] JSON-RPC error 响应 [产出] RuntimeException 含 message | tools/call 回 error 对象 | callTool | 抛出且 message=服务器 error.message | REQ-C09 | HttpServer mock |
| [传输降级] 目标 5xx/超时 [产出] status=error 且失败日志落库 | 服务指向不可达 URL | refresh / McpToolExecutor.execute | service.status=error；mcp_call_log 新增 status=error 记录；refresh 上抛失败原因 | REQ-C03、REQ-C08 | 不可达端点 |
| [传输降级] stdio 传输 [产出] 连接即拒 | transport=stdio + command | connect | UnsupportedOperationException（管理面 parseUrl/refresh 转「解析失败」错误包） | REQ-C09 | 无 |
| [未连接] 跳过 connect 直调 listTools/callTool [产出] IllegalStateException | new 后未 connect | listTools | 抛「not connected」 | REQ-C09 | 无 |
| [认证] authType=bearer [产出] Authorization 头注入 | 服务配置 bearer+token | connect（捕获请求头） | 请求头 Authorization=Bearer <token>；basic 同理 Base64 | REQ-C08 | HttpServer mock |

### ToolRegistry + ToolExecutorRouter（contract: tools-skills-mcp.ToolRegistry / ToolExecutorRouter）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [解析] 混合 toolIds（含无效+禁用） [产出] 有效项全解析且 install_skill 恒在 | toolIds=[httpId, "mcp_99", "skill:slug", "bad-id"]（其中一项禁用） | resolveTools | 结果含有效 http/mcp/skill 定义与 install_skill；禁用/无效项缺失；无异常 | REQ-E01、REQ-E02 | 无（H2 或 mapper stub 层为真缝外，禁 mock 内部模块——用内存库） |
| [解析] skill 按 slug 兜底 [产出] ID 查不到走 slug | skill 表有 slug 记录 | resolveTools(["skill_"+不存在ID, slug]) | slug 路径解析成功、config 含 content/dependencies | REQ-E02 | 同上 |
| [Schema 归一] 仅 properties 的 inputSchema [产出] 包成 type:object | 构造缺 type 键的定义 | toOpenAITools | parameters.type="object"；空 Schema 定义给空 object | REQ-E03 | 无 |
| [路由] type 分派与未知型 [产出] 唯一执行器/null | 注入五执行器 | getExecutor 五型 + "nope" | 各返回对应实例；"nope" 返回 null | REQ-E04 | 无 |
| [执行统一包] 任一执行器抛异常场景 [产出] error ToolResult 不外抛 | MCP 目标不可达 / KB 参数缺失 | execute | success=false、error 非空、durationMs≥0 | REQ-E04 | 外部端点拒连 |
| [模板渲染] `${city}` 与 `${inputs.city}` 双写法 [产出] 同值替换 | HTTP 工具 config url=`/q=${city}&i=${inputs.city}` | HttpToolExecutor.execute(args={city:"hangzhou"}) | mock 目标收到的 URL 两占位均为 hangzhou；headers/params/body 同规则 | REQ-E05 | HttpServer mock |
| [outputMapping] 命中路径与回退 [产出] 提取值/原文回退 | 响应 `{"data":{"temp":30}}`；mapping=`$.data.temp` / `$.no.such` | execute | 前者 output="30"；后者 output=原文 | REQ-A08 | HttpServer mock |
| [MCP rawName] 格式化名与原始名并存 [产出] 用原始名调用 | mcp_tool.name="web_search"、toolId="mcp__X__WebSearch" | McpToolExecutor.execute | callTool 收到 "web_search" | REQ-C05、REQ-E02 | HttpServer mock |

### SkillDependencyValidator + SkillAccessManager (S)（contract 面见 data.md 校验口径；design (S) 模块）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [依赖] 引用禁用工具 [产出] 报「不存在或未启用」 | tool enabled=0 | validate(type=tool) | errors 含该依赖、validDeps 不含 | REQ-B08 | 内存库 |
| [依赖] 技能依赖自身 [产出] 报「不能依赖自身」 | skillId 对应 name=依赖名 | validate(type=skill) | errors 含「不能依赖自身」 | REQ-B08 | 内存库 |
| [依赖] 重复 type:name [产出] 报重复 | 同依赖出现两次 | validate | 第二条入 errors | REQ-B08 | 内存库 |
| [依赖] model 类型 [产出] 暂缓通过 | type=model 任意名 | validate | 进 validDeps 无错误 | REQ-B08 | 内存库 |
| [选项] getDependencyOptions 排除自身 [产出] 下拉无自身 | excludeSkillId=self | getDependencyOptions | skills 列表不含自身 name；tools/mcp 仅启用项 | REQ-B08 | 内存库 |
| [分享] 非管理员提交 global [产出] 降级 user | normalizeShareConfig(config=global, role=user) | normalize | accessLevel=user、departmentIds 清空 | REQ-B09 | 无 |
| [分享·缺口] user 级 userUids 命中 [产出] canAccess=true（**接线后生效**） | shareConfig={user, [uid]} | canAccess(uid) | true——现状生产路径不调用本判定，S5b 接线（design §4）前本用例仅守护类语义 | REQ-B09 | 无 |

### HttpToolModule（design 面：端点即接口）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [脱敏] 非属主读工具 [产出] httpConfig=null | 属主创建含 authValue 工具 | get/list（非属主上下文） | 返回体 httpConfig 为 null；属主读到全量 | REQ-A04 | 无 |
| [管理权] 非属主 update/delete/toggle [产出] 403 | 同上 | 三写操作 | BusinessException forbidden | REQ-A05 | 无 |
| [测试代理] 目标 404 [产出] 成功包含 404 详情 | test-proxy 指向返回 404 的 mock | testProxy | code=200、data.status=404、body=目标响应体、error 含 HTTP 详情 | REQ-A06 | HttpServer mock |
| [测试代理] 目标拒连/超时 [产出] status=0 包 | 不可达 URL / timeout=1 | testProxy | data.status=0、error 非空、duration 已计 | REQ-A06 | 不可达端点 |
| [identifier] 创建缺省 identifier [产出] 名称自动生成 | create 只传 name="My Tool" | create | identifier="my_tool"、creator/orgId=当前用户 | REQ-A02 | 无 |
| [批量导入] JSON 数组 [产出] 全部启用入库 | import-json 2 条 | importFromJson | 2 条 enabled=1、缺 identifier 用 UUID | REQ-A07 | 无 |

### 内置同步（SkillModule/McpModule 种子）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [种子幂等] 重跑 Seeder [产出] enabled 用户值保留 | fetch 已被用户禁用 | McpBuiltinSeeder.run | fetch 仍 enabled=0；name/command 已更新 | REQ-C06 | 内存库 |
| [种子防删] 删内置 MCP [产出] 拒绝 | is_builtin=1 服务 | delete | 抛「内置 MCP 服务不允许删除」 | REQ-C06 | 无 |
| [内置同步] SKILL.md 未变 [产出] 跳过写库 | contentHash 相同 | SkillDataInitializer.run | 无 update 执行、磁盘目录仍就位 | REQ-B05 | classpath（临时目录） |
| [内置防删] 删内置技能 [产出] 拒绝 | is_builtin=1 技能 | delete | 抛「内置技能不允许删除」 | REQ-B04 | 无 |

## 3. 追溯（抽样）

| 测试（S 切片） | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| [只读门] UPDATE 被拒（S1） | DbQueryGateway.E_SQL_NOT_READONLY | DbInstanceModule | REQ-D03 |
| [越界路径] ZIP `../` 拒绝（S2） | SkillFileVault.E_PATH_TRAVERSAL | SkillFileModule | REQ-B07 |
| [握手] session 头回传（S3） | McpProtocolClient.Invariants-Post | McpProtocolClient（缝 2） | REQ-C09 |
| [解析] 混合 ID 容错（S4） | ToolRegistry.E_TOOL_UNRESOLVABLE | ToolRegistryModule（缝 3） | REQ-E01 |
| [依赖] 自引用拒绝（S5） | data.md skill_dependency 校验口径 | SkillDependencyValidator | REQ-B08 |
| [脱敏] 工具 httpConfig 置空（S6） | tool_http_config 凭据约束 | HttpToolModule | REQ-A04 |
| [种子幂等] enabled 保留（S8） | mcp_service.enabled 契约语义 | McpModule + McpBuiltinSeeder | REQ-C06 |

## 4. 待实施说明

- 红绿循环**未执行**（本任务禁改源码）。执行时从 S1 Tracer Bullet（`[只读门] UPDATE 拒绝`）起步：先写失败测试（红）→ 确认现状实现已使其通过（ DbInstanceServiceImpl.java:418-426 现成）→ 转为对绕过形态的补强测试。
- 现状基线：16 测试类 137 用例可先跑通（`mvn test -f fastrag-backend/pom.xml -pl fastrag-modules/fastrag-tools`）作为 S2~S5、S8 的回归护栏；S1/S6/S7 为零覆盖区，是本设计新增主战场。
- HITL 项（S1 第一例）：前缀白名单 vs 注释/多语句/粘连形态的取舍需产品/安全确认后再固化断言；深层防御（外部库只读账号）为部署约定，测试不可达，登记 GAP。
- mock 边界纪律：外部 MCP server 一律用内嵌 HttpServer（参照 McpProtocolClientIntegrationTest 既有模式）；外部 DB 用 H2 MODE=MySQL（buildJdbcUrl 原生支持 h2，DbInstanceServiceImpl.java:397）；禁 mock mapper/service 内部模块——工具解析切片用内存库装配真实 ToolRegistry。
