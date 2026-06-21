# 模块六：应用编排

> 对应前端：`src/views/application/index.vue`（应用中心）、`src/views/application/editor/`（应用编辑）、`src/views/application/runtime.vue`（运行）、`src/views/knowledge-process/`（知识加工流程）
> 数据契约：`src/mock/workflow.ts`、`src/types/workflow.ts`、应用中心内联数据（`views/application/index.vue`）

---

## 一、业务概述

将知识库、模型、工具编排成可运行的智能体应用，并提供可视化工作流编排。

| 子域 | 能力 |
|------|------|
| 应用管理 | 应用 CRUD、应用市场、我的应用、模板市场、复制应用 ID |
| 应用编排 | 基础配置（名称/模型/Prompt）、知识库绑定、工具绑定 |
| 应用运行 | 调试运行、运行时监控 |
| 工作流 | 可视化流程定义（节点+连线）、发布、运行控制 |

### 1.1 应用类型（对齐 `APP_TYPES`）

- ChatBot 智能问答
- Editor 写作助手
- LiteAgent 智能体

### 1.2 工作流节点类型（对齐 `NodeType`）

| 类型 | 说明 | 默认参数 |
|------|------|----------|
| start | 开始节点 | 触发类型 |
| end | 结束节点 | 输出配置 |
| llm | 大模型调用 | model/prompt/temperature |
| knowledge_retrieval | 知识检索 | kbId/topK |
| intent | 意图识别 | 多输出口 |
| selector | 条件路由 | 多输出口 |
| plugin | 插件/工具调用 | toolId |

### 1.3 工作流状态（对齐 `WorkflowStatus`）

draft / published / running / stopped

---

## 二、数据模型（Entity）

### 2.1 `app` 应用

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| name / description | varchar | |
| type | varchar(32) | ChatBot/Editor/LiteAgent |
| icon | varchar(32) | 颜色 |
| tags | varchar(512) | |
| status | enum('draft','published') | |
| owner | varchar(32) | |

### 2.2 `app_config` 应用编排配置

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| app_id | varchar(32) | |
| model | varchar(64) | LLM |
| prompt | text | 系统 Prompt |
| temperature | decimal | |
| knowledge_ids | varchar(512) | 绑定 KB（逗号） |
| tool_ids | varchar(512) | 绑定工具 |
| max_turns | int | |

### 2.3 `app_template` 应用模板（模板市场）

| 字段 | 类型 |
|------|------|
| id | varchar(32) PK |
| name / description / type | varchar |
| usage_count | int |
| config_snapshot | json |

### 2.4 `workflow` 工作流定义（对齐 `WorkflowDefinition`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar(32) PK | |
| name / description | varchar | |
| status | enum('draft','published','running','stopped') | |
| nodes | json | `WorkflowNode[]` |
| edges | json | `WorkflowEdge[]` |
| created_by | varchar(32) | |
| created_at / updated_at | datetime | |

**节点结构**（对齐 `WorkflowNode`）：
```typescript
{
  id, type, label,
  x, y,                  // 画布坐标
  params: {},            // 节点参数（DEFAULT_NODE_PARAMS）
  inputs: NodePort[],    // 输入端口
  outputs: NodePort[]    // 输出端口（intent/selector 多输出口）
}
```

**边结构**（对齐 `WorkflowEdge`）：
```typescript
{ id, sourceNodeId, sourcePortId, targetNodeId, targetPortId, condition? }
```

---

## 三、接口设计（API）

### 3.1 应用管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/apps` | 应用列表（支持 `?keyword=&tag=`） |
| GET | `/apps/{id}` | 应用详情（含 config） |
| POST | `/apps` | 创建应用 |
| PUT | `/apps/{id}` | 更新应用 |
| DELETE | `/apps/{id}` | 删除（同步清理历史） |
| GET | `/apps/templates` | 模板市场 |
| POST | `/apps/from-template/{tplId}` | 从模板创建 |

### 3.2 应用编排

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/apps/{id}/config` | 获取编排配置 |
| PUT | `/apps/{id}/config` | 保存编排配置（model/prompt/KB绑定/工具绑定） |

### 3.3 应用运行

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/apps/{id}/run` | 运行应用（对话/执行） |
| GET | `/apps/{id}/runtime` | 运行时状态 |

**POST `/apps/{id}/run`**：SSE 流式返回。按 config 调用绑定 KB 检索（模块3）+ LLM 生成答案（`fastrag-ai`）。对齐前端 `runtime.vue`。

### 3.4 工作流

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/workflows` | 列表 | 已认证 |
| GET | `/workflows/{id}` | 详情（含 nodes/edges） | 已认证 |
| POST | `/workflows` | 创建（含 start/end 默认节点） | `workflow:create` |
| PUT | `/workflows/{id}` | 更新 | `workflow:edit` |
| DELETE | `/workflows/{id}` | 删除 | `workflow:delete` |
| POST | `/workflows/{id}/publish` | 发布 | `workflow:publish` |
| POST | `/workflows/{id}/nodes` | 新增节点 `{ type, x, y }` | `workflow:edit` |
| PUT | `/workflows/{id}/nodes/{nodeId}/position` | 移动节点 `{ x, y }` | `workflow:edit` |
| DELETE | `/workflows/{id}/nodes/{nodeId}` | 删除节点（含关联边） | `workflow:edit` |
| PUT | `/workflows/{id}/nodes/{nodeId}/params` | 更新节点参数 | `workflow:edit` |
| POST | `/workflows/{id}/edges` | 新增边（去重/禁自环） | `workflow:edit` |
| DELETE | `/workflows/{id}/edges/{edgeId}` | 删除边 | `workflow:edit` |

---

## 四、核心逻辑设计

### 4.1 应用运行（RAG 对话）

```java
@Service
public class AppRunService {
    public SseEmitter run(String appId, String query) {
        AppConfig cfg = configMapper.selectByApp(appId);
        SseEmitter emitter = new SseEmitter(120_000L);
        executor.execute(() -> {
            // 1. 检索绑定 KB
            List<SearchResultItem> context = retrievalService.search(
                new RetrievalRequest(cfg.getFirstKnowledgeId(), query, cfg.getRetrievalConfig()));
            // 2. 构造 Prompt
            String prompt = PromptBuilder.build(cfg.getPrompt(), context, query);
            // 3. 流式生成
            aiClient.streamChat(cfg.getModel(), prompt)
                .doOnNext(chunk -> emit(emitter, chunk))
                .doOnComplete(() -> emitDone(emitter, context))  // 附溯源
                .subscribe();
        });
        return emitter;
    }
}
```

### 4.2 工作流编辑校验（对齐 `workflow.ts`）

- `addEdge`：去重（相同 source→target）、禁自环（source==target）、无重复 port 连接。
- `addNode`：按类型注入默认参数与端口（intent/selector 多输出口）。
- `deleteNode`：级联删除关联边。
- 节点坐标直接持久化（前端画布坐标系）。

### 4.3 工作流发布

`publish`：状态 draft→published，校验存在 start/end 节点、无孤立节点。

### 4.4 工作流执行引擎（发布后运行）

拓扑排序节点 → 按边流转执行 → 每节点按类型执行（llm 调模型、knowledge_retrieval 调检索、intent 分类、selector 路由、plugin 调工具）→ 汇总输出。可基于条件边（`condition`）动态路由。

---

## 五、前端覆盖核对表

| 前端能力 | 后端接口 |
|----------|----------|
| 应用 CRUD/模板市场（`views/application/index.vue` 内联） | `/apps*` |
| 应用编辑（`BasicConfig`/`KnowledgeConfig`） | `/apps/{id}/config` |
| 应用运行（`runtime.vue`） | `POST /apps/{id}/run`（SSE） |
| `getWorkflows/getWorkflow/createWorkflow/updateWorkflow/deleteWorkflow/publishWorkflow` | `/workflows*` |
| `addNode/updateNodePosition/deleteNode/updateNodeParams` | `/workflows/{id}/nodes*` |
| `addEdge/deleteEdge` | `/workflows/{id}/edges*` |
