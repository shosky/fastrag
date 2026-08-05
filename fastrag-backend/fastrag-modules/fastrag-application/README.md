# fastrag-application

> 应用中心模块 — 面向终端用户的应用管理、对话交互、工作流编排。

## 模块职责

- **应用管理**：应用的创建、配置、发布
- **对话交互**：SSE 流式对话，绑定知识库+工具+模型进行 RAG 问答
- **应用配置**：基础配置（模型/提示词）、对话配置（开场白/建议问题/占位符）
- **工作流编排**：可视化工作流的创建、测试、发布，含多种节点类型（LLM、检索、重排序、模板、代码、条件）
- **应用绑定**：知识库、工具、MCP 服务、技能、数据库的绑定管理
- **会话管理**：对话 Session 的创建、消息记录

## 对外 REST 端点

### 应用管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/apps` | 创建应用 |
| GET | `/api/apps` | 应用列表 |
| GET | `/api/apps/{id}` | 应用详情 |
| PUT | `/api/apps/{id}` | 更新应用 |
| DELETE | `/api/apps/{id}` | 删除应用 |
| POST | `/api/apps/{id}/publish` | 发布应用 |
| GET | `/api/apps/{id}/dialogs` | 对话列表 |

### 应用配置

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/apps/{appId}/config` | 获取完整配置 |
| PUT | `/api/apps/{appId}/config` | 更新完整配置 |
| GET | `/api/apps/{appId}/config/basic` | 基础配置（模型/温度等） |
| PUT | `/api/apps/{appId}/config/basic` | 更新基础配置 |
| GET | `/api/apps/{appId}/config/dialog` | 对话配置 |
| PUT | `/api/apps/{appId}/config/dialog` | 更新对话配置 |

### 应用对话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/apps/{appId}/chat/stream` | SSE 流式对话 |
| POST | `/api/apps/{appId}/chat/sessions` | 创建会话 |
| GET | `/api/apps/{appId}/chat/sessions` | 会话列表 |
| DELETE | `/api/apps/{appId}/chat/sessions/{id}` | 删除会话 |
| GET | `/api/apps/{appId}/chat/sessions/{id}/messages` | 会话消息 |

### 工作流

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/apps/{appId}/workflows` | 创建工作流 |
| GET | `/api/apps/{appId}/workflows` | 工作流列表 |
| GET | `/api/apps/{appId}/workflows/{id}` | 工作流详情 |
| PUT | `/api/apps/{appId}/workflows/{id}` | 更新工作流 |
| DELETE | `/api/apps/{appId}/workflows/{id}` | 删除工作流 |
| POST | `/api/apps/{appId}/workflows/{id}/test` | 测试工作流 |
| POST | `/api/apps/{appId}/workflows/{id}/publish` | 发布工作流 |

## 模块依赖

| 方向 | 模块 | 说明 |
|------|------|------|
| 依赖 | common, security, infra, retrieval, agent, platform | — |
| 被依赖 | operation, bootstrap（2 个模块） | |

## 关键类说明

### 控制器

| 类 | 端点前缀 | 说明 |
|----|----------|------|
| `AppController` | `/api/apps` | 应用 CRUD、发布 |
| `AppChatController` | `/api/apps/{appId}/chat` | SSE 流式对话、会话管理 |
| `AppConfigController` | `/api/apps/{appId}/config` | 应用配置管理 |
| `WorkflowController` | `/api/apps/{appId}/workflows` | 工作流 CRUD、测试、发布 |

### 服务

| 类 | 说明 |
|----|------|
| `AppService` | 应用 CRUD、SSE 流式对话执行（LLM + 检索 + 工具编排） |
| `AppConfigService` | 应用配置管理（基础配置/对话配置/知识库绑定/工具绑定等） |
| `WorkflowService` | 工作流 CRUD、测试执行、发布管理 |

### 核心实体

| 实体 | 说明 | 关键字段 |
|------|------|----------|
| `App` | 应用 | name, type, status |
| `AppConfig` | 应用配置 | config(JSON) |
| `AppBasicConfig` | 基础配置 | modelName, temperature, systemPrompt |
| `AppDialogConfig` | 对话配置 | prologue, suggestedQuestions |
| `AppConversation` | 对话会话 | appId, userId, title |
| `AppConversationMessage` | 对话消息 | role, content, tokenCount |
| `AppKbBinding` | 知识库绑定 | kbId, searchMode, topK |
| `AppToolBinding` | 工具绑定 | toolId, enabled |
| `AppMcpBinding` | MCP 绑定 | mcpServiceId, enabled |
| `AppSkillBinding` | 技能绑定 | skillId, enabled |
| `AppDbBinding` | 数据库绑定 | dbType, connectionString |
| `AppGlobalPolicy` | 全局策略 | sensitiveWordEnabled, contentPolicy |
| `Workflow` | 工作流 | name, graphData(JSON), status |
| `WfNode` | 工作流节点 | type(NodeType), config(JSON), position |
| `WfTemplate` | 工作流模板 | name, graphData(JSON), category |

## 核心调用链

应用对话的执行链路：LLM 调用 → 知识库检索（通过 retrieval 模块）→ 工具调用（通过 tools 模块）→ 结果拼接 SSE 推流
