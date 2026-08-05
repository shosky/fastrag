# fastrag-agent -- 智能体(Agent)模块

提供智能体的 CRUD 管理、多后端架构(ChatbotAgent/SubAgent)、运行时上下文构建、中间件链引擎、子智能体委派、SSE 流式对话与事件推送。

## 模块职责

- 管理智能体(Agent)的创建/更新/删除、默认智能体保障、slug 唯一性
- 实现可插拔后端架构，支持 ChatbotAgent(通用对话)和 SubAgentBackend(子任务)
- 提供运行时上下文构建(ContextBuilder)，从 Agent 配置 + 技能依赖生成完整 BaseContext
- 实现三级权限(global/department/private)的智能体访问控制
- 提供 AgentRun 的生命周期管理(创建/执行/取消/SSE 事件流)
- 实现 AgentEngine 统一流式执行引擎(LLM 调用循环 + 工具执行 + SSE 推送)
- 支持中间件链机制(beforeModelCall/afterModelCall/interceptToolCall 三阶段)
- 提供子智能体委派能力，主 Agent 可通过 task 工具分发子任务

## 对外 REST 端点

### 智能体 CRUD `/api/agent`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 列出所有可见智能体(includeSubagents) |
| GET | `/default` | 获取默认智能体 |
| POST | `/` | 创建智能体 |
| GET | `/{agentId}` | 获取智能体详情(含完整 backend 信息) |
| PUT | `/{agentId}` | 更新智能体 |
| DELETE | `/{agentId}` | 删除智能体(内置不允许) |
| POST | `/{agentId}/set_default` | 设为默认智能体 |
| GET | `/{agentId}/config` | 获取配置(configJson + configurableItems) |
| PUT | `/{agentId}/config` | 更新配置(context + shareConfig) |

### 运行管理 `/api/agent`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/runs` | 创建运行(异步执行) |
| GET | `/runs/{runId}` | 获取运行详情 |
| POST | `/runs/{runId}/cancel` | 取消运行 |
| GET | `/runs/{runId}/events` | SSE 事件流(TEXT_EVENT_STREAM) |
| GET | `/thread/{threadId}/active_run` | 获取线程中活跃运行 |

## 模块依赖

| 依赖模块 | 用途 |
|----------|------|
| `fastrag-common` | ApiResponse, BusinessException, JsonSchemaValidator 等公共组件 |
| `fastrag-security` | SecurityUtil, LoginUser, @Loggable 注解 |
| `fastrag-infra` | MyBatis-Plus 数据访问层基础设施 |
| `fastrag-tools` | ToolRegistry, ToolExecutor, ToolDefinition, SkillService 等工具和技能服务 |
| `fastrag-ai` | LlmService, ChatMessage, ChatResponse, StreamEvent 等大模型接口 |

## 关键类说明

### 控制器层

| 类名 | 路径前缀 | 职责 |
|------|----------|------|
| `AgentController` | `/api/agent` | 智能体 CRUD + 设为默认 |
| `AgentConfigController` | `/api/agent` | 配置获取与更新 |
| `AgentRunController` | `/api/agent` | 运行管理 + SSE 事件流 |

### 实体与 DTO 层

| 类名 | 表名/说明 |
|------|-----------|
| `Agent` | `agent` -- 智能体(backendId, configJson, shareConfig, isDefault, isSubagent) |
| `AgentRun` | `agent_run` -- 运行记录(status, runType, inputPayload, errorType) |
| `AgentCreateDTO` | 创建请求 DTO(@Valid, backendId + name 必填) |
| `AgentUpdateDTO` | 更新请求 DTO(所有字段可选) |
| `AgentRunCreateDTO` | 运行创建 DTO(threadId + agentId 必填) |
| `AgentConfigDTO` | 配置更新 DTO(context + shareConfig) |
| `AgentSerializeVO` | 序列化 VO(含 canManage, isBuiltin, capabilities) |

### 后端架构

| 类名 | 说明 |
|------|------|
| `AgentBackend` (接口) | 后端契约: getId, getCapabilities, getContextSchema, buildGraph |
| `AgentBackendManager` | 后端注册表，启动时自动发现所有 AgentBackend bean |
| `ChatbotAgentBackend` | ChatbotAgent 后端: capabilities=[file_upload, files] |
| `SubAgentBackend` | 子智能体后端: capabilities=[] |
| `AgentGraph` | 执行图描述(model, systemPrompt, tools, middlewareChain, stateSchema) |

### 上下文体系

| 类名 | 说明 |
|------|------|
| `BaseContext` | 基础运行时上下文(@ConfigField 注解驱动配置字段) |
| `ChatBotContext` | Chatbot 扩展上下文(增加 subagents 列表) |
| `ContextBuilder` | 运行时上下文构建器(Agent配置 + 技能依赖填充) |
| `ConfigField` | 配置字段注解(name, type, kind, auth, hide) |
| `ConfigurableItem` | 可配置项 DTO(用于前端动态渲染) |
| `User` | 用户 POJO(uid, role, username, departmentIds) |
| `CurrentUser` | 参数注入注解 |

### 执行引擎

| 类名 | 说明 |
|------|------|
| `AgentEngine` | 核心引擎: 流式(executeStream) + 同步(executeSync) |
| `AgentExecutor` | (已废弃) 旧版引擎，保留兼容 |
| `AgentResult` | 执行结果(success, answer, toolCallRecords, durationMs) |
| `ModelConfig` | 模型运行时配置(model, apiUrl, apiKey, temperature, enableThinking) |
| `LlmStreamResult` | 流式消费结果(response + hasThinkingTags) |

### 中间件链

| 类名 | Order | 说明 |
|------|-------|------|
| `AgentMiddleware` (接口) | - | 三阶段: beforeModelCall / afterModelCall / interceptToolCall |
| `AgentMiddlewareChain` | - | 按排序执行，before 正序，after 逆序，interceptor 取首个 |
| `ContextMiddleware` | 0 | 模型路由，设置 ModelConfig |
| `KnowledgeBaseMiddleware` | 3 | 注册知识库工具(list_kbs, query_kb, find_kb_document) |
| `SkillsMiddleware` | 4 | 技能运行时加载 + 拦截 read_file 激活技能 |
| `SubAgentMiddleware` | 5 | 子智能体 task 工具注册与委派执行 |
| `DatabaseMiddleware` | 10 | 数据库工具注册(list_tables, describe_table, query_database) |
| `McpMiddleware` | 11 | MCP 服务工具注册 |

### 状态与事件

| 类名 | 说明 |
|------|------|
| `BaseState` | 基础运行状态(artifacts 列表) |
| `ChatBotState` | Chatbot 状态(增加 subagentRuns) |
| `SubAgentRunState` | 子智能体运行状态 |
| `RunEventPublisher` | 内存事件存储(ConcurrentHashMap) + 推送 |

### 配置

| 类名 | 说明 |
|------|------|
| `AsyncConfig` | 异步线程池配置(agentTaskExecutor: core=2, max=5, queue=50) |
| `CurrentUserArgumentResolver` | 从 SecurityContext 解析 @CurrentUser 参数 |
| `AgentWebMvcConfig` | 注册 CurrentUserArgumentResolver |

### 沙箱与虚拟文件系统

| 类名 | 说明 |
|------|------|
| `SandboxService` | Docker 沙箱容器生命周期(获取/释放/保活/定时回收) |
| `SandboxConnection` | 沙箱连接信息(sandboxId, containerId, apiUrl) |
| `VirtualFileSystem` | 虚拟文件系统接口(mount point 路由) |
