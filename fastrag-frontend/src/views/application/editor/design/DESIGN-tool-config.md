# 工具配置 — 设计文档

## 1. 功能概述

**目的：** 为应用绑定可用的 HTTP 工具，扩展智能体的外部执行能力。

**当前状态：** 编辑器内联 mock 实现（纯前端勾选+开关，无保存/API）。

**改造目标：** 新建 `ToolConfig.vue` 组件，对接后端工具 API。

---

## 2. 数据模型

### 2.1 API 端点

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/tools` | 获取所有工具列表 | ✅ 已实现 |
| PUT | `/api/apps/{appId}/config` | 保存应用配置（含 `toolIds`） | ✅ 已实现 |

### 2.2 数据关联

与技能配置文件相同 `AppConfig.toolIds` 字段，工具 ID 也存储在此字段中。

**GET /api/tools 响应：**
```json
[
  {
    "id": "tool_001",
    "name": "Tavily 网页搜索",
    "identifier": "tavily_search",
    "description": "A search engine optimized for comprehensive results",
    "type": "builtin",
    "enabled": true
  }
]
```

### 2.3 本地状态定义

```typescript
// 所有可用工具（从 API 加载）
const availableTools = ref<Tool[]>([])

// 当前应用已选中的工具 ID 列表
const selectedToolIds = ref<string[]>([])

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
  await Promise.all([loadTools(), loadAppConfig()])
})
```

### 3.4 内部方法

| 方法 | 说明 | API 调用 |
|------|------|----------|
| `loadTools()` | 加载可用工具 | `GET /api/tools` |
| `loadAppConfig()` | 加载应用配置 | `GET /api/apps/{id}/config` |
| `handleSave()` | 保存所选工具 | `PUT /api/apps/{id}/config` |

---

## 4. UI 布局

```
┌─ 工具配置 ────────────────────────────────────────┐
│  配置应用可用的 HTTP 工具，扩展智能体的执行能力。 │
│                                                    │
│  ┌────────────────────────────────────────────────┐│
│  │ ☑ 安装技能                       [开关]        ││
│  │    install_skill                               ││
│  │    安装新的 Skill 到当前用户的私有空间         ││
│  ├────────────────────────────────────────────────┤│
│  │ ☑ Tavily 网页搜索                [开关]        ││
│  │    tavily_search                               ││
│  │    A search engine optimized...                ││
│  ├────────────────────────────────────────────────┤│
│  │ ☐ 向用户提问                     [开关]        ││
│  │    ask_user_question                            ││
│  │    在执行过程中向用户提问                      ││
│  └────────────────────────────────────────────────┘│
│                                                    │
│  已选 3 个工具                    [保 存]          │
└────────────────────────────────────────────────────┘
```

### 4.1 关键交互

- 勾选/取消 → 即时更新 `selectedToolIds`
- 点击开关 → 切换该项 `enabled` 状态
- 保存 → 合并 toolIds 后调用 `PUT /api/apps/{id}/config`

---

## 5. 实现要点

### 5.1 与技能共享 toolIds

`AppConfig.toolIds` 同时存储工具 ID 和技能 ID，两者在前端需要合并处理：

```typescript
// 读取时拆分
const allToolIds = appConfig.toolIds?.split(',').filter(Boolean) || []
const skillIds = allToolIds.filter(id => id.startsWith('skill_'))
const toolIds = allToolIds.filter(id => id.startsWith('tool_'))
const mcpIds = allToolIds.filter(id => id.startsWith('mcp_'))

// 保存时合并
const merged = [...skillIds, ...toolIds, ...mcpIds].join(',')
```

### 5.2 边界条件
- 工具列表为空 → 显示空状态
- 工具标识符缺失 → 显示 `-`
- 历史引用已删除的工具 → 显示 ID 但标记为"不可用"

### 5.3 错误处理
- 加载失败 → 空列表降级
- 保存失败 → `ElMessage.error` + 恢复勾选状态

### 5.4 备注
- UI 与技能配置几乎相同，可考虑抽象为通用"可选列表"组件
- 工具与技能的差异仅在数据来源和 ID 前缀
