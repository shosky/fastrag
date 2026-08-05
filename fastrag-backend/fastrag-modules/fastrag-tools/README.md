# fastrag-tools -- 工具集成与技能管理模块

提供 HTTP/MCP/知识库/数据库等多类型工具注册、MCP 协议客户端通信、技能(Skill)全生命周期管理、以及统一的工具执行引擎。

## 模块职责

- 管理 HTTP 类型工具的 CRUD、输入/输出 JSON Schema 定义与 API 插件配置
- 集成 MCP (Model Context Protocol) 服务发现、工具列表同步、工具调用执行与调用日志
- 管理数据库实例(DbInstance)的连接配置、表元数据同步与只读 SQL 查询
- 提供技能(Skill)的内置种子初始化、CRUD、文件树管理、ZIP 导入导出、草稿安装流水线
- 实现统一的 `ToolExecutor` 策略模式引擎，按工具类型(http/mcp/knowledge/builtin/skill)分派执行
- 通过 `ToolRegistry` 统一解析所有工具定义并转换为 LLM function calling 格式
- 支持技能三级权限模型(global/department/user)与依赖验证(tool/mcp/skill/model)

## 对外 REST 端点

### 工具管理 `/api/tools`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 分页列表(keyword, type 筛选) |
| GET | `/{id}` | 获取详情(含 httpConfig) |
| POST | `/` | 创建工具(含 httpConfig) |
| PUT | `/{id}` | 更新工具 |
| DELETE | `/{id}` | 删除工具 |
| POST | `/{id}/toggle` | 切换启用状态 |
| GET | `/{id}/api-config` | 获取 API 插件配置 |
| PUT | `/{id}/api-config` | 保存 API 插件配置 |
| POST | `/test-proxy` | 工具测试代理(转发 HTTP 请求，避免 CORS) |
| POST | `/upload` | 上传插件文件 |
| POST | `/import-json` | JSON 批量导入插件 |

### MCP 服务管理 `/api/mcp-services`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 列出所有 MCP 服务 |
| GET | `/enabled` | 列出已启用服务 |
| GET | `/builtin` | 列出内置服务 |
| GET | `/{id}` | 获取服务详情(含工具列表) |
| GET | `/slug/{slug}` | 按 slug 获取 |
| POST | `/` | 创建 MCP 服务 |
| POST | `/parse-url` | 解析 MCP URL(不持久化，创建前预览工具) |
| PUT | `/{id}` | 更新服务 |
| DELETE | `/{id}` | 删除服务(内置不允许) |
| POST | `/{id}/toggle` | 切换服务启用状态 |
| POST | `/{id}/refresh` | 刷新服务(连接发现工具) |
| GET | `/{id}/tools` | 获取工具列表 |
| POST | `/{serviceId}/tools` | 手动添加工具 |
| PUT | `/tools/{toolId}` | 更新工具 |
| DELETE | `/tools/{toolId}` | 删除工具 |
| POST | `/tools/{toolId}/toggle` | 切换工具启用 |
| POST | `/tools/{toolId}/test` | 测试工具调用 |

### 技能管理 `/api/skills`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 列表(keyword, category) |
| GET | `/accessible` | 可访问技能列表(用于 Agent 配置) |
| GET | `/builtin` | 内置技能列表 |
| GET | `/{id}` | 获取技能详情 |
| GET | `/slug/{slug}` | 按 slug 获取 |
| POST | `/` | 创建技能 |
| PUT | `/{id}` | 更新技能 |
| DELETE | `/{id}` | 删除技能(内置不允许) |
| POST | `/{id}/toggle` | 切换启用 |
| PUT | `/{id}/dependencies` | 更新依赖 |
| PUT | `/{id}/share-config` | 更新分享配置 |
| GET | `/{id}/dependencies` | 获取依赖列表 |
| GET | `/{id}/scopes` | 获取作用域列表 |
| GET | `/dependency-options` | 依赖选项(前端下拉) |
| GET | `/{slug}/tree` | 获取技能文件树 |
| GET | `/{slug}/file` | 读取技能文件 |
| POST | `/{slug}/file` | 创建文件/目录 |
| PUT | `/{slug}/file` | 更新文件 |
| DELETE | `/{slug}/file` | 删除文件/目录 |
| GET | `/{slug}/export` | 导出 ZIP |
| POST | `/import/prepare` | 上传 ZIP/SKILL.md 并自动安装 |
| POST | `/install-drafts/{draftId}/confirm` | 确认安装草稿 |
| DELETE | `/install-drafts/{draftId}` | 丢弃草稿 |

### 数据库管理 `/api/databases`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 列表(keyword, dbType) |
| GET | `/{id}` | 获取详情 |
| POST | `/` | 创建实例 |
| PUT | `/{id}` | 更新实例 |
| DELETE | `/{id}` | 删除实例 |
| GET | `/{id}/tables` | 获取表列表 |
| POST | `/{id}/tables` | 创建表记录 |
| POST | `/{id}/test-conn` | 测试连接 |
| POST | `/{id}/query` | 执行只读 SQL 查询 |
| POST | `/{id}/sync-tables` | 同步表元数据 |

## 模块依赖

| 依赖模块 | 用途 |
|----------|------|
| `fastrag-common` | ApiResponse, TemplateEngine, JsonSchemaValidator, KbOperationService 等公共组件 |
| `fastrag-security` | SecurityUtil 获取当前用户, @Loggable 操作日志注解 |
| `fastrag-infra` | MyBatis-Plus 数据访问层基础设施 |
| `fastrag-ai` | ChatRequest.ChatRequest.ToolDefinition 等模型接口定义 |

## 关键类说明

### 控制器层

| 类名 | 路径前缀 | 职责 |
|------|----------|------|
| `ToolController` | `/api/tools` | HTTP 工具 CRUD、测试代理、插件上传/导入 |
| `McpController` | `/api/mcp-services` | MCP 服务/工具全生命周期管理 |
| `SkillController` | `/api/skills` | 技能 CRUD、文件管理、ZIP 导入导出 |
| `DbInstanceController` | `/api/databases` | 数据库实例 CRUD、连接测试、SQL 查询 |

### 实体层

| 类名 | 表名 | 说明 |
|------|------|------|
| `Tool` | `tool` | 工具主表，含 JSON Schema inputs/outputs |
| `ToolHttpConfig` | `tool_http_config` | HTTP 工具配置(method, url, headers, params) |
| `McpService` | `mcp_service` | MCP 服务(transport: stdio/sse, 连接参数) |
| `McpTool` | `mcp_tool` | MCP 服务下的工具(参数 JSON Schema) |
| `McpCallLog` | `mcp_call_log` | MCP 调用日志 |
| `Skill` | `skill` | 技能(sourceType: builtin/remote/custom) |
| `SkillDependency` | `skill_dependency` | 技能依赖(tool/mcp/skill/model) |
| `SkillScope` | `skill_scope` | 技能作用域 |
| `DbInstance` | `db_instance` | 数据库实例连接信息 |
| `DbTable` | `db_table` | 数据库表元数据 |

### 服务层

| 接口 | 实现 | 说明 |
|------|------|------|
| `ToolService` | `ToolServiceImpl` | HTTP 工具 CRUD + API 插件配置管理 |
| `McpServiceService` | `McpServiceServiceImpl` | MCP 服务发现/刷新/工具同步 |
| `SkillService` | `SkillServiceImpl` | 技能 CRUD + 依赖/分享配置管理 |
| `SkillFileService` | `SkillFileServiceImpl` | 技能文件树/读写/ZIP 导出 |
| `SkillDraftService` | `SkillDraftServiceImpl` | 技能安装草稿流水线 |
| `DbInstanceService` | `DbInstanceServiceImpl` | 数据库连接/测试/SQL 查询/表同步 |

### 执行引擎

| 类名 | type 值 | 说明 |
|------|---------|------|
| `ToolExecutor` (接口) | - | 统一执行接口: execute(tool, args, ctx) |
| `ToolExecutorFactory` | - | 按 type 查找执行器的工厂 |
| `HttpToolExecutor` | `http` | 模板渲染 + WebClient HTTP 调用 |
| `McpToolExecutor` | `mcp` | McpProtocolClient 连接 + tools/call + 日志 |
| `KnowledgeToolExecutor` | `knowledge` | 知识库 list_kbs / query_kb |
| `SkillToolExecutor` | `skill` | read_skill / activate |
| `InstallSkillToolExecutor` | `builtin` | Agent 安装技能能力 |

### 工具注册与协议

| 类名 | 说明 |
|------|------|
| `ToolRegistry` | 从 DB 加载所有工具定义，转为 LLM function calling 格式 |
| `ToolDefinition` | 运行时工具定义(toolId, name, inputSchema, config) |
| `McpProtocolClient` | MCP JSON-RPC over HTTP 客户端(initialize + tools/list + tools/call) |

### 配置与初始化

| 类名 | 说明 |
|------|------|
| `McpBuiltinSeeder` | 启动时增量同步内置 MCP 服务(filesystem, fetch, sequential-thinking) |
| `SkillDataInitializer` | 启动时从 classpath 扫描 SKILL.md 并同步内置技能到 DB 和磁盘 |

### 技能辅助

| 类名 | 说明 |
|------|------|
| `SkillAccessManager` | 三级权限校验(global/department/user) |
| `SkillDependencyValidator` | 依赖类型验证(tool/mcp/skill/model) |
| `SkillMarkdownParser` | SKILL.md YAML frontmatter 解析器 |
