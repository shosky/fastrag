# MCP 配置 — 设计文档

## 1. 功能概述

**目的：** 为应用绑定 MCP（Model Context Protocol）服务，使智能体能调用外部 MCP 工具（如网页搜索、数据查询等）。

**当前状态：** 编辑器内联 mock 实现（纯前端勾选+开关，无保存/API）。

**改造目标：** 新建 `McpConfig.vue` 组件，对接后端 MCP 服务 API。

---

## 2. 数据模型

### 2.1 API 端点

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/mcp-services` | 获取所有 MCP 服务 | ✅ 已实现 |
| GET | `/api/mcp-services/enabled` | 获取已启用的 MCP 服务 | ✅ 已实现 |
| PUT | `/api/apps/{appId}/config` | 保存应用配置（含 `toolIds`） | ✅ 已实现 |

### 2.2 数据关联

MCP 服务也通过 `AppConfig.toolIds` 字段引用。

**GET /api/mcp-services 响应：**
```json
[
  {
    "id": "mcp_001",
    "name": "leowang",
    "transport": "sse",
    "mcpUrl": "https://mcp.example.com/...",
    "tools": ["bing_search", "crawl_webpage"],
    "enabled": true,
    "status": "online"
  }
]
```

### 2.3 本地状态定义

```typescript
// 所有可用 MCP 服务
const availableMcpServices = ref<McpService[]>([])

// 当前应用已选中的 MCP 服务 ID 列表
const selectedMcpIds = ref<string[]>([])

const loading = ref(false)
```

---

## 3. 组件设计

### 3.1 Props

```typescript
defineProps<{ appInfo: { id: string } }>()
```

### 3.2 Emits

无

### 3.3 生命周期

```typescript
onMounted(async () => {
  await Promise.all([loadMcpServices(), loadAppConfig()])
})
```

### 3.4 内部方法

| 方法 | 说明 | API 调用 |
|------|------|----------|
| `loadMcpServices()` | 加载 MCP 服务 | `GET /api/mcp-services` |
| `loadAppConfig()` | 加载应用配置 | `GET /api/apps/{id}/config` |
| `handleSave()` | 保存所选 MCP 服务 | `PUT /api/apps/{id}/config` |

---

## 4. UI 布局

```
┌─ MCP 配置 ─────────────────────────────────────────┐
│  配置应用可用的 MCP 服务，扩展智能体的外部能力。    │
│                                                     │
│  ┌─────────────────────────────────────────────────┐│
│  │ ☑ leowang                        [开关]         ││
│  │    https://mcp.example.com/...                  ││
│  │    [bing_search] [crawl_webpage]                ││
│  ├─────────────────────────────────────────────────┤│
│  │ ☐ code_executor                   [开关]         ││
│  │    https://mcp.example.com/code-exec            ││
│  │    [execute_python] [execute_javascript]        ││
│  ├─────────────────────────────────────────────────┤│
│  │ ☐ database_query                  [开关]         ││
│  │    https://mcp.example.com/db-query             ││
│  │    [query_sql] [query_nosql]                    ││
│  └─────────────────────────────────────────────────┘│
│                                                     │
│  已选 2 个 MCP 服务               [保 存]           │
└─────────────────────────────────────────────────────┘
```

### 4.1 关键交互

- **勾选** → 更新 `selectedMcpIds`
- **点击开关** → 切换该项 `enabled`（仅前端，不独立调用 toggle API）
- **点击 URL** → 可复制到剪贴板
- **工具标签** → 只读展示，不可交互

---

## 5. 实现要点

### 5.1 toolIds 合并

与技能/工具共用 `toolIds` 字段：

```typescript
// MCP ID 约定以 "mcp_" 为前缀
const mcpIds = selectedMcpIds.value.filter(id => id.startsWith('mcp_'))
```

### 5.2 边界条件
- 无可用 MCP 服务 → 引导用户先去"MCP 管理"页面创建
- MCP 服务状态为 offline → 显示状态标签（红色），提示可能不可用
- MCP 工具列表为空 → 不显示标签区域

### 5.3 错误处理
- 加载失败 → 空列表降级
- 保存失败 → 恢复勾选 + 错误提示

### 5.4 扩展考虑
- 后续可增加 MCP 连接测试按钮（`POST /api/mcp-services/{id}/test`）
- 可显示 MCP 的在线/离线状态指示器
