# 工具 / 技能 / MCP / 外部数据库（tools-skills-mcp） — 架构设计

## 1. Module Map

| 模块 | Responsibility | Files | Depth Rationale |
|---|---|---|---|
| **ToolRegistryModule** | 能力底座解析口：把 toolIds 列表（混合 ID 前缀）解析为统一 ToolDefinition，并输出 OpenAI function calling 格式 | registry/ToolRegistry.java、registry/ToolDefinition.java | 深模块——`mcp_`/`skill_` 前缀分派、ID/slug 双查、enabled 过滤、「单点失败跳过」的容错、Schema 包装归一（type:object 缺失时补全）全部藏在 resolveTools/toOpenAITools 两个方法之后；fastrag-agent 只见 ToolDefinition（AgentEngine.java:126,402） |
| **ToolExecutorRouter** | 执行分派缝：按 type 把执行请求路由到唯一 ToolExecutor 实现，统一 ToolResult 返回包 | executor/ToolExecutorFactory.java、executor/ToolExecutor.java、executor/ToolResult.java、executor/ToolContext.java | 深模块——Spring 注入全部实现的列表式分派（新执行器零改动接入）；success/output/durationMs/error 的统一结果协议是 agent 消费侧的唯一依赖面 |
| **HttpToolModule** | 自定义 HTTP 工具域：CRUD/启停/凭据脱敏/API 配置/测试代理/插件导入 | controller/ToolController.java、service/ToolService.java、impl/ToolServiceImpl.java、entity/Tool.java、ToolHttpConfig.java | 深模块——可见性四态判定（DataScope）、非属主凭据置空、params/headers 双格式（数组↔JSON 串）转换收敛在 service；test-proxy 把 CORS/超时/异常归一为统一响应包（ToolController.java:87-185） |
| **HttpToolExecutor** | HTTP 工具运行时：模板渲染→请求构造→执行→outputMapping 提取 | executor/HttpToolExecutor.java | 深模块——`${inputs.x}`/`${x}`/`${config.x}`/`${context.*}` 四类模板变量、bodyType→Content-Type 映射、JSONPath 提取失败回退，全部折叠进 execute 一入口 |
| **SkillModule** | 技能库域：CRUD/启停/依赖/作用域/分享配置/内置同步 | controller/SkillController.java、service/SkillService.java、impl/SkillServiceImpl.java、config/SkillDataInitializer.java、skill/SkillMarkdownParser.java、entity/Skill.java、SkillDependency.java、SkillScope.java | 深模块——「依赖与作用域全量替换」「内置技能 MD5 增量同步保留用户字段」「内置不可删」三条不变式由 create/update/delete 与 Initializer 共同维护；SKILL.md 解析（frontmatter 分割/字段校验/缺省回填）独立可复用 |
| **SkillFileModule** | 技能文件域：磁盘目录的树/读/写/删/导出与 ZIP 安装流水线 | service/SkillFileService.java、impl/SkillFileServiceImpl.java、service/SkillDraftService.java、impl/SkillDraftServiceImpl.java | 深模块——路径遍历校验（validateAndResolve 单点收口）、根目录删除保护、ZIP 解压双检（`..` 与 normalize 后前缀）、草稿 TTL 与过期清理，全部安全语义内藏 |
| **SkillDependencyValidator** | 技能依赖校验器：tool/mcp/skill/model 四类依赖的存在性/启用性/自引用/去重校验与下拉选项供给 | skill/SkillDependencyValidator.java | 深模块——跨三张表的校验规则与「model 暂缓」的治理口径收敛于 validate/getDependencyOptions 两方法 |
| **SkillAccessManager (S)** | 技能三级分享权限判定（global/department/user）与配置规范化 | skill/SkillAccessManager.java | **(S)** 准浅模块——规则本身清晰，但 canAccess 在生产代码零调用方（仅测试消费，grep 实测；实际访问控制走 DataScope，见 §3 缝 4 仲裁）；department 分支为占位恒 false（:103-115）；selectCreatedBy 查询的 `created_by` 列与运行时增补的 `creator` 列名不一致（SkillMapper.java:14 vs SchemaInitializer.java:382）‹CERTAIN（2026-09-24 深证）：创建者判定在生产路径静默失效——该列既不在 schema.sql 也不被 Initializer 增补，SQL 必失败并被 try/catch 吞为空串（SkillAccessManager.java:91-97，data.md tools 节 DDL 漂移登记同源）›。建议：要么让 SkillServiceImpl.listAccessible 接入本判定补全缝，要么将规范仅保留 normalizeShareConfig 并内联 |
| **McpModule** | MCP 服务域：注册/parse-url/刷新发现/工具增删改测/凭据脱敏/内置种子 | controller/McpController.java、service/McpServiceService.java、impl/McpServiceServiceImpl.java、config/McpBuiltinSeeder.java、entity/McpService.java、McpTool.java、McpCallLog.java | 深模块——「刷新=全量替换工具」「凭据属主脱敏」「内置不可删」「status 三态流转」「参数数组→JSON Schema 归一」「CamelCase 工具 ID 生成」六条规则收敛在一个 service 面 |
| **McpProtocolClient** | MCP 协议客户端：JSON-RPC over Streamable HTTP 的 initialize/listTools/callTool 生命周期 | mcp/McpProtocolClient.java | 深模块——会话头管理、SSE 响应体 JSON 提取、JSON-RPC error 归一、认证头注入，对外只暴露 connect/listTools/callTool/close 四方法；**外部 MCP server 是系统边界**，本类是唯一触点 |
| **McpToolExecutor** | MCP 工具运行时：按服务配置建连调用、调用记账、lastUsed 回写 | executor/McpToolExecutor.java | 深模块——rawName 兼容（格式化名 vs 原始名）、认证头构造、日志失败不阻断主流程的降级语义内藏 |
| **DbInstanceModule** | 外部数据库域：连接管理/连通测试/表结构同步/只读 SQL 查询 | controller/DbInstanceController.java、service/DbInstanceService.java、impl/DbInstanceServiceImpl.java、entity/DbInstance.java、DbTable.java | 深模块——只读白名单、行数上限、动态 HikariCP 池（用后即弃）、JDBC URL 拼接、凭据脱敏、表结构 upsert 全部收敛；`SELECT COUNT(*) FROM \`表名\`` 的表名反引号包裹来自 DatabaseMetaData（非用户输入，注入面封闭，DbInstanceServiceImpl.java:303-304） |
| **KnowledgeToolExecutor** | 知识库查询工具运行时：list_kbs/query_kb 两动作转调 KbOperationService | executor/KnowledgeToolExecutor.java | 深模块（薄依赖）——参数别名归一（kb_id/knowledge_base、query/question、top_k 缺省 10）与 LLM 可读文本拼装内藏；检索实现在 retrieval 域 |
| **InstallSkillToolModule (S)** | install_skill 内置工具：对话中技能安装引导（静态定义 + 引导文本执行器） | executor/InstallSkillToolDefinition.java、executor/InstallSkillToolExecutor.java | **(S)** 浅模块——远程分支只返回安装指引文本（不真正执行安装，InstallSkillToolExecutor.java:96-99），存在性检查是唯一逻辑；建议未来接 SkillDraftService 真正落安装 |

与 ADR 要点的关系：ADR-0001（解析策略绑定-重切一致）不涉及本特性；ADR-0002（图存储收敛 Neo4j 唯一实现）**方法论同构**——本特性的 McpProtocolClient 是 MCP 连接的唯一实现（执行器/刷新/测试三处共用同一客户端），未出现第二协议实现，符合「唯一实现 + fail-fast 语义」（此处表现为错误即时上抛：McpServiceServiceImpl.java:371）。

## 2. Interface Contracts

> 契约正文在 contract/interfaces.md `## tools-skills-mcp` 节（ToolRegistry / ToolExecutorRouter / McpProtocolClient / SkillFileVault / DbQueryGateway 五个）；本节列公共接口暴露面。(S) 模块不立契约，端点即公共接口。HTTP 暴露面：ToolController 11 端点 / SkillController 24 端点（javadoc 列 18，实数见 implement §1） / McpController 17 端点 / DbInstanceController 10 端点——均属实现细节不立契约（端点排除铁律）。

### ToolRegistryModule（contract: tools-skills-mcp.ToolRegistry）

- Public interface：`resolveTools(toolIds: List<String>) → List<ToolDefinition>` / `toOpenAITools(tools) → List<ChatRequest.ToolDefinition>`。
- Hidden implementation：前缀分派表（mcp_/skill_/其余）、MCP 定义 config 组装（10 个连接字段含 rawName）、技能 Schema 硬编码（action 枚举 read_skill/activate）、`{key,value}` 数组与 JSON 对象双格式 headers/params 解析（parseJsonMap 三分支）。
- Depth rationale：agent 引擎对「一个工具如何取配置/是否启用/叫什么名」零感知；新增工具类型只需新增实体+解析分支，消费侧不动。

### ToolExecutorRouter（contract: tools-skills-mcp.ToolExecutorRouter）

- Public interface：`getExecutor(type: String) → ToolExecutor|null`；`ToolExecutor.execute(tool, arguments, ctx) → ToolResult`。
- Hidden implementation：Spring List 注入式注册（ToolExecutorFactory.java:28）；ToolResult 静态工厂归一；ToolContext 携带 userId/appId/runId/query/extensions 五要素跨执行器传递。
- Depth rationale：调用方（AgentEngine:651）只见 type 字符串与 ToolResult；执行器内异常各自捕获，路由层永不抛。

### McpProtocolClient（contract: tools-skills-mcp.McpProtocolClient）

- Public interface：`connect()` / `listTools() → List<McpToolInfo{name,description,inputSchema}>` / `callTool(name, args) → String` / `close()`。
- Hidden implementation：协议版本常量 2025-11-25、requestId 自增、mcp-session-id 头提取（响应头优先/body 回退）、initialized 通知 fire-and-forget（失败仅告警）、SSE data 行拼接提取、非 2xx/空体/缺 result 三类错误归一 RuntimeException。
- Depth rationale：McpModule 的 parseUrl/refresh/testTool 与 McpToolExecutor 四处消费同一生命周期协议，传输细节（会话/SSE/错误格式）不可见。

### SkillFileVault（contract: tools-skills-mcp.SkillFileVault）

- Public interface：`getTree(slug)` / `readFile(slug, path)` / `createNode(slug, path, isDir, content, operator)` / `updateFile(slug, path, content, operator)` / `deleteNode(slug, path, operator)` / `exportZip(slug) → File`；安装流水线 `prepareUpload(filename, bytes, operator) → SkillInstallDraft` / `confirmDraft(draftId, shareConfig, operator) → List<DraftItem>` / `discardDraft(draftId, operator)`。
- Hidden implementation：`{data-dir}/skills/{slug}` 根与 `skill_import_drafts` 草稿根的布局；validateAndResolve 路径校验；ZIP 双检解压；单技能 ZIP reorganize；MD5 contentHash；覆盖重装（删旧目录+删旧记录再装）；TTL 1h 过期清理。
- Depth rationale：控制器对磁盘布局、遍历防护、草稿生命周期零感知；「上传即安装」（prepare+confirm 单步串接）与「草稿两步」共用同一 service 面。

### DbQueryGateway（contract: tools-skills-mcp.DbQueryGateway）

- Public interface：`list(keyword, dbType)` / `get(id)` / `create(db)` / `update(id, db)` / `delete(id)` / `listTables(dbId)` / `createTable(t)` / `testConnection(dbId) → Map{connected,latencyMs,error?}` / `query(dbId, sql) → Map{columns,rows,rowCount,elapsedMs,error?}` / `syncTables(dbId) → Map{syncedCount,error?}`。
- Hidden implementation：只读前缀白名单、MAX_ROWS 500、动态 HikariCP（池名带时间戳防冲突）、五类 JDBC URL 拼接、DatabaseMetaData 全表扫描 + COUNT 行数、upsert 同步、级联删表记录、状态回写。
- Depth rationale：调用方只见「给 SQL 得结果包」；连接池生命周期、只读约束、元数据同步策略全部在缝内。

## 3. Seam Diagram

### 缝 1：工具执行类型路由缝（本链核心生产缝）

- **What varies**：工具的执行形态——http/mcp/skill/knowledge/builtin（未来 database 等新类型）。
- **Adapters**：① HttpToolExecutor；② McpToolExecutor；③ SkillToolExecutor；④ KnowledgeToolExecutor；⑤ InstallSkillToolExecutor——5 个真实适配器，真缝（ToolExecutorFactory.java:28-35）。
- **Stable side**：`ToolExecutor{getType(), execute(ToolDefinition, args, ToolContext) → ToolResult}` 双方法协议（ToolExecutor.java:15-18）。
- **Placement rationale**：agent 执行循环在运行时按 LLM 选择的工具类型动态分派，路由必须与执行解耦；列表式注入使新执行器零修改接入。

### 缝 2：MCP 传输协议缝（系统边界缝）

- **What varies**：MCP 服务器的连接方式（stdio 子进程 / SSE / Streamable HTTP）与服务器实现（Firecrawl 等的 SSE 响应格式差异）。
- **Adapters**：当前仅 Streamable HTTP 一个真实适配器（McpProtocolClient.java:65-120）；stdio 在 connect 处显式抛 UnsupportedOperationException（:70-72）——**假设缝不立**。〔决议 2026-09-24：stdio 需求裁定为**真实需求、v1 未交付、无排期承诺**；需求证据在代码内：三个内置种子服务均以 `transport=stdio` + npx/uvx 启动命令播种（McpBuiltinSeeder.java:47-66），即官方主流 MCP server 的本地进程形态，而当前客户端对它们一律拒绝连接（:70-72）——意图（支持 stdio）与实现（拒绝桩）断裂；排期属产品决策，按最小改动不做承诺；现状差距：内置 stdio 服务（含 enabled=1 的 filesystem）实际不可连接，refresh 将以异常收场；落地时缝 2 升级为真缝（design §4 深化项已登记）（代裁，用户可推翻）〕。SSE 差异以响应体嗅探（extractJsonFromSse）内化，未单列适配器。
- **Stable side**：`connect/listTools/callTool/close` 生命周期协议与 JSON-RPC result 提取。
- **Placement rationale**：协议握与会话状态只应存在一份；四处消费方（parseUrl/refresh/testTool/McpToolExecutor）共用单客户端印证单点收敛正确。

### 缝 3：能力交付缝（tools → agent/app-center 消费）

- **What varies**：应用/智能体绑定了哪些工具、工具集合如何组合。
- **Adapters**：① 生产方 ToolRegistryModule（本链）；② fastrag-agent 消费方（AgentEngine.resolveTools:126,402 + getExecutor:651，AgentExecutor.java:45；SkillsMiddleware/McpMiddleware/DatabaseMiddleware 直读 Skill/McpService 实体与 mapper）；③ app-center 五类绑定（app_tool/skill/mcp/db_binding，存 toolIds 列表）。≥2 适配器，真缝。
- **Stable side**：`resolveTools(toolIds) → List<ToolDefinition>` 与 ToolDefinition 字段面（toolId/name/type/inputSchema/config）。
- **Placement rationale**：能力注册与发现归 tools 域唯一事实源（tool/mcp_tool/skill 三表）；消费方按 ID 集合拉取，避免绑定关系倒灌注册表。

### 缝 4：资源可见性缝（tools → iam/DataScope）

- **What varies**：谁可见/可管理 tool、skill、mcp_service、db_instance 四类资源。
- **Adapters**：① DataScope.visible/manageable（security 模块，四类 service 统一调用：ToolServiceImpl.java:27-35、SkillServiceImpl.java:37-56、McpServiceServiceImpl.java:30-39、DbInstanceServiceImpl.java:38-46）；② SkillAccessManager 的三级 share_config 判定（生产零调用，仅 normalizeShareConfig 的降级规则在概念上影响 confirmDraft 缺省值 SkillDraftServiceImpl.java:130-132）。2 个登记 1 个缺位——缝由 DataScope 真实承载。
- **Stable side**：`DataScope.visible(user, creator, orgId, isBuiltin)` / `manageable(user, creator)` 判定协议（iam 链上游契约）。
- **Placement rationale**：与 iam 的组织/属主模型同源，避免四域各自实现 ACL；share_config 三级模型的裁决归技能域（SkillAccessManager），但其生产接线缺失已在 §1 仲裁。

## 4. Deepening Opportunities

- **SkillAccessManager**：把 share_config 三级判定真正接入 `listAccessible`（当前 listAccessible 只按 DataScope+enabled 过滤，SkillServiceImpl.java:73-86，user 级分享配置对可见性无实际效果）——补全 §3 缝 4 的缺位适配器；同时修复 selectCreatedBy 列名错位。
- **McpProtocolClient**：吞掉 stdio 子进程生命周期（当前拒绝桩），届时缝 2 成为真缝；连接池化（当前每次调用 new client + Java HttpClient，McpToolExecutor.java:70-71 每工具调用全握手一轮）。
- **ToolRegistryModule**：吞掉 `database` 类型解析（ToolDefinition.java:22 已预留类型枚举但 resolveSingleTool 无对应分支——前缀分派仅 mcp_/skill_/HTTP 兜底三路，ToolRegistry.java:44-58，外部 DB 能力未进统一注册面；agent 侧 DatabaseToolExecutor 另行走 DbInstanceMapper 直查，两套路径并存‹CERTAIN（2026-09-24 深证）：能力面分裂属实，收敛方向=resolveSingleTool 增加 database 分支复用 DbQueryGateway›）。
- **HttpToolModule**：吞掉 upload/import-json 的文件内容处理（当前 uploadPlugin 只取文件名落库、文件体丢弃，ToolServiceImpl.java:273-282——假成功面）。
- **DbInstanceModule**：吞掉连接池复用（当前每次 query/test/sync 建池销毁，高频查询开销大）；`createTable` 语义澄清（登记元数据 or 真建表）。

## 5. Parent Trace

| Design 模块/缝 | Spec REQ |
|---|---|
| HttpToolModule（含 test-proxy） | REQ-A01~A07 |
| HttpToolExecutor（缝 1 适配器） | REQ-A08、REQ-E05 |
| SkillModule + SkillDataInitializer + SkillMarkdownParser | REQ-B01~B05、REQ-B10 |
| SkillFileVault（SkillFileModule） | REQ-B06、REQ-B07 |
| SkillDependencyValidator | REQ-B08 |
| SkillAccessManager (S)（缝 4 缺位侧） | REQ-B09 |
| McpModule + McpBuiltinSeeder | REQ-C01~C04、REQ-C06、REQ-C07 |
| McpProtocolClient（缝 2） | REQ-C02、REQ-C03、REQ-C09 |
| McpToolExecutor（缝 1 适配器） | REQ-C05、REQ-C08 |
| DbInstanceModule（DbQueryGateway） | REQ-D01~D06 |
| ToolRegistryModule（缝 3 生产方） | REQ-E01~E03 |
| ToolExecutorRouter（缝 1 稳定侧） | REQ-E04 |
| KnowledgeToolExecutor（缝 1 适配器） | REQ-E01、REQ-E04 |
| HttpToolModule（含 test-proxy） | REQ-A03（2026-09-24 review 补锚：HTTP 配置与工具本体一对一存储；params/headers 接受 {key,value} 数组并统一序列化为 JSON 字符串） |
| SkillModule + SkillDataInitializer + SkillMarkdownParser | REQ-B02（2026-09-24 review 补锚：技能列表区分全量（含禁用）/可访问（仅 enabled=1）/内置三种视图，可按 sourceType 再筛） |
| McpModule + McpBuiltinSeeder | REQ-C04（2026-09-24 review 补锚：MCP 工具手动增/删/改/启停；参数数组自动归一为 JSON Schema；工具 ID 统一生成 mcp__{服务Camel}__{工具Camel}） |

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
