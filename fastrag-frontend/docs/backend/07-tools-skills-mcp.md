# 模块七：工具 / 技能 / MCP

> 对应前端：`my-tools.vue`（我的工具）、`skill-management.vue` + `skill/*`（技能）、`mcp-services.vue` + `mcp/*`（MCP 管理）
> 数据契约：`src/mock/tools.ts`、`src/mock/skills.ts`、`src/mock/mcp.ts`

---

## 一、业务概述

为智能体提供可调用的「能力三件套」：HTTP 工具、技能（提示词/代码能力）、MCP 服务（外部协议工具）。

| 子域 | 说明 |
|------|------|
| **工具（Tools）** | 内置工具 / 知识库工具 / 自定义 HTTP 工具。HTTP 工具可配置方法/URL/鉴权/Params/Body/Headers/输入参数 |
| **技能（Skills）** | 内置/自定义/插件技能。含代码、依赖（工具/模型/MCP/技能）、生效范围（全局/应用/KB）、上传/远程安装 |
| **MCP 服务** | Model Context Protocol 服务。含地址、鉴权、工具列表（自动解析）、连通状态、调用日志、刷新 |

---

## 二、数据模型（Entity）

### 2.1 `tool` 工具（对齐 `Tool`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| name / identifier / description | varchar | |
| type | enum('builtin','knowledge','http') | |
| tags | varchar(512) | |
| icon | varchar(32) | |
| enabled | tinyint | |
| created_at | datetime | |
| inputs | json | `ToolInputParam[]`（name/description/type/isToolParam） |

### 2.2 `tool_http_config` HTTP 工具配置（对齐 `HttpToolConfig`）

| 字段 | 类型 | 说明 |
|------|------|------|
| tool_id | varchar(32) PK/FK | |
| method | enum('GET','POST','PUT','DELETE') | |
| url | varchar(512) | |
| auth_type | enum('none','apiKey','bearer','oauth2') | |
| params / headers | json | `KeyValue[]` |
| body_type | enum('none','form-data','x-www-form-urlencoded','json','xml','raw-text') | |
| body | text | |

### 2.3 `skill` 技能（对齐 `Skill`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| name / identifier / description | varchar | |
| icon | varchar(32) | |
| source | enum('builtin','custom','plugin') | |
| category | varchar(32) | retrieval/generation/analysis/tool/document |
| trigger / content | text | 触发场景 / 技能说明 |
| code_type | enum('python','yaml','json','markdown') | |
| code | text | |
| inputs / outputs | varchar(512) | 示例 |
| enabled / recommended | tinyint | |
| usage_count | int | |
| author / version | varchar | |
| updated_at | datetime | |

### 2.4 `skill_dependency` 技能依赖（对齐 `SkillDependency`）

| 字段 | 类型 |
|------|------|
| id | varchar(32) PK |
| skill_id | varchar(32) |
| type | enum('tool','model','mcp','skill') |
| name | varchar |
| required | tinyint |

### 2.5 `skill_scope` 技能生效范围（对齐 `SkillScopeBinding`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| skill_id | varchar(32) | |
| scope_id | varchar(32) | 空=全局 |
| scope_name | varchar | |
| enabled | tinyint | |

### 2.6 `mcp_service` MCP 服务（对齐 `McpService`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| name | varchar | |
| mcp_url | varchar(512) | |
| auth_type | enum('Bearer','API Key','none') | |
| auth_value | varchar(255) | |
| status | enum('online','offline') | |
| enabled | tinyint | |
| last_used / created_at | datetime | |

### 2.7 `mcp_tool` MCP 工具（对齐 `McpTool`）

| 字段 | 类型 |
|------|------|
| id | varchar(32) PK |
| service_id | varchar(32) |
| name / description | varchar/text |
| params | json (`McpToolParam[]`) |

### 2.8 `mcp_call_log` MCP 调用日志（对齐 `McpCallLog`）

| 字段 | 类型 |
|------|------|
| id | varchar(32) PK |
| service_id | varchar(32) |
| caller / tool | varchar |
| status | enum('success','error') |
| duration | int (ms) |
| timestamp | datetime |

---

## 三、接口设计（API）

### 3.1 工具

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/tools` | 工具列表（`?keyword=&type=`） |
| GET | `/tools/{id}` | 详情（含 httpConfig） |
| POST | `/tools` | 创建（HTTP 工具） |
| PUT | `/tools/{id}` | 更新 |
| DELETE | `/tools/{id}` | 删除 |
| POST | `/tools/{id}/toggle` | 切换启用 |
| POST | `/tools/{id}/invoke` | 执行工具（HTTP 工具发实际请求） |

### 3.2 技能

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/skills` | 列表（`?keyword=&category=`） |
| GET | `/skills/{id}` | 详情（含依赖+生效范围） |
| POST | `/skills` | 创建 |
| PUT | `/skills/{id}` | 更新 |
| DELETE | `/skills/{id}` | 删除 |
| POST | `/skills/{id}/toggle-enabled` | 切换启用 |
| POST | `/skills/{id}/toggle-recommended` | 切换推荐 |
| POST | `/skills/upload` | 上传安装（multipart） |
| POST | `/skills/install-remote` | 远程安装 `{ url }` |
| GET | `/skills/scope-candidates` | 可绑定的应用/KB 候选 |
| GET | `/skills/dependency-candidates` | 依赖候选（工具/模型/MCP/技能） |

### 3.3 MCP 服务

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/mcp-services` | 列表（`?keyword=&status=`） |
| GET | `/mcp-services/{id}` | 详情（含工具+日志） |
| POST | `/mcp-services` | 创建 |
| PUT | `/mcp-services/{id}` | 更新 |
| DELETE | `/mcp-services/{id}` | 删除 |
| POST | `/mcp-services/{id}/toggle` | 切换启用 |
| POST | `/mcp-services/{id}/toggle-status` | 切换在线状态 |
| POST | `/mcp-services/{id}/refresh` | 刷新（重拉工具+连通检测+更新 lastUsed） |
| POST | `/mcp-services/parse-url` | 解析地址 `{ url }` → `{ tools[], message }` |
| POST | `/mcp-services/{id}/invoke` | 调用 MCP 工具 |

---

## 四、核心逻辑设计

### 4.1 HTTP 工具执行（`/tools/{id}/invoke`）

```java
@Service
public class HttpToolInvoker {
    public Object invoke(String toolId, Map<String, Object> args) {
        Tool tool = toolMapper.selectById(toolId);
        HttpToolConfig cfg = tool.getHttpConfig();
        // 1. 构造请求：method + url（替换 {path} 占位）+ params + headers + body
        // 2. 注入鉴权（bearer/apiKey → header）
        // 3. 发起 RestTemplate/WebClient 请求
        // 4. 按出参规范返回
        return webClient.method(cfg.getMethod()).uri(renderUrl(cfg, args))
            .headers(h -> applyAuth(h, cfg))
            .body(renderBody(cfg, args))
            .retrieve().bodyToMono(Object.class).block();
    }
}
```

### 4.2 MCP 地址解析与刷新（对齐 `parseMcpUrl`/`refreshMcpService`）

**解析** `parse-url`：
1. 校验 URL（http(s)://）。
2. 连接 MCP 服务端（按 MCP 协议 list tools）。
3. 返回工具列表 `{ name, description, params }`。
> 前端 mock 按地址关键词返回示例；后端按真实 MCP 协议（JSON-RPC over SSE/stdio）拉取。

**刷新** `refresh`：重新拉取工具 + 探活（更新 status online/offline）+ 更新 lastUsed。

### 4.3 技能安装（上传/远程）

- **上传**：接收技能包文件（zip）→ 解析元数据（name/identifier/version/code）→ 落库为 plugin 技能。
- **远程**：从 URL 拉取包 → 同上。
- 两者最终调 `createSkill`（source=plugin），对齐前端 `handleUploadSubmit`/`handleRemoteInstall`。

### 4.4 技能依赖与生效范围解析

- 依赖：运行技能前校验必选依赖（`required=true`）是否可用（模型在线、工具启用等）。
- 生效范围：技能调用时按当前应用/KB 上下文过滤可用技能（全局 + 该应用/KB 绑定且 enabled）。

### 4.5 候选项聚合

- `scope-candidates`：聚合所有应用 + 知识库，供技能绑定。
- `dependency-candidates`：聚合在线模型 + 启用工具 + MCP 服务 + 其他技能，按 type 分组。

---

## 五、前端覆盖核对表

| 前端 mock 函数 | 后端接口 |
|----------------|----------|
| `getTools/getTool/createTool/updateTool/deleteTool/toggleToolEnabled` | `/tools*` |
| `getSkills/getSkill/createSkill/updateSkill/deleteSkill/toggleSkillEnabled/toggleSkillRecommended` | `/skills*` |
| 上传/远程安装（skill-management 内联） | `/skills/upload`、`/skills/install-remote` |
| `SCOPE_CANDIDATES/DEPENDENCY_CANDIDATES` | `/skills/scope-candidates`、`/skills/dependency-candidates` |
| `getMcpServices/getMcpService/createMcpService/updateMcpService/deleteMcpService/toggleMcpEnabled/toggleMcpStatus` | `/mcp-services*` |
| `parseMcpUrl/refreshMcpService` | `/mcp-services/parse-url`、`.../refresh` |
