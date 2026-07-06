# 技能配置 — 设计文档

## 1. 功能概述

**目的：** 为应用启用/禁用可用的 AI 技能，扩展智能体的能力边界。

**当前状态：** 编辑器内联 mock 实现（纯前端勾选+开关，无保存/API）。

**改造目标：** 新建 `SkillConfig.vue` 组件，对接后端技能 API。

---

## 2. 数据模型

### 2.1 API 端点

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/skills` | 获取所有技能列表 | ✅ 已实现 |
| GET | `/api/skills/accessible` | 获取当前用户可访问的技能 | ✅ 已实现 |
| PUT | `/api/apps/{appId}/config` | 保存应用配置（含 `toolIds`） | ✅ 已实现 |

### 2.2 数据关联

技能绑定存储方式：应用配置中的 `toolIds` 字段（逗号分隔的 ID 字符串）引用技能。

```
AppConfig.toolIds = "skill_001,skill_002,skill_005"
```

**GET /api/skills 响应：**
```json
[
  {
    "id": "skill_001",
    "name": "联网搜索",
    "identifier": "web_search",
    "description": "当用户询问实时信息时，自动搜索互联网获取最新内容",
    "enabled": true,
    "isBuiltin": true
  }
]
```

### 2.3 本地状态定义

```typescript
// 所有可用技能（从 API 加载）
const availableSkills = ref<Skill[]>([])

// 当前应用已选中的技能 ID 列表（从 AppConfig.toolIds 解析）
const selectedSkillIds = ref<string[]>([])

// UI 状态
const loading = ref(false)
```

### 2.4 数据流

```
onMounted
  ├─ loadSkills()         → GET /api/skills/accessible
  └─ loadAppConfig()      → GET /api/apps/{id}/config
       └─ 解析 toolIds → selectedSkillIds

handleSave()
  ├─ 构造 toolIds = selectedSkillIds.join(',')
  └─ PUT /api/apps/{id}/config { toolIds }
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
  await Promise.all([loadSkills(), loadAppConfig()])
})
```

### 3.4 内部方法

| 方法 | 说明 | API 调用 |
|------|------|----------|
| `loadSkills()` | 加载可用技能 | `GET /api/skills/accessible` |
| `loadAppConfig()` | 加载应用配置，获取 `toolIds` | `GET /api/apps/{id}/config` |
| `handleSave()` | 保存所选技能 | `PUT /api/apps/{id}/config` |

---

## 4. UI 布局

```
┌─ 技能配置 ────────────────────────────────────────┐
│  配置应用可用的技能，扩展智能体的能力。            │
│                                                    │
│  ┌────────────────────────────────────────────────┐│
│  │ ☑ 联网搜索                       [开关]        ││
│  │    当用户询问实时信息时，自动搜索互联网        ││
│  ├────────────────────────────────────────────────┤│
│  │ ☑ 代码生成                       [开关]        ││
│  │    根据用户需求生成代码片段                    ││
│  ├────────────────────────────────────────────────┤│
│  │ ☐ 数据分析                       [开关]        ││
│  │    对用户提供的数据进行统计分析                ││
│  ├────────────────────────────────────────────────┤│
│  │ ...                                            ││
│  └────────────────────────────────────────────────┘│
│                                                    │
│  已选 3 个技能                    [保 存]          │
└────────────────────────────────────────────────────┘
```

### 4.1 关键交互

- **勾选 Checkbox** → 更新 `selectedSkillIds`（前端即时响应）
- **点击 Switch** → 更新该项的 `enabled` 状态
- **点击保存** → 调用 API 持久化，`ElMessage.success('技能配置已保存')`

---

## 5. 实现要点

### 5.1 数据合并策略
`toolIds` 字段可能同时包含技能 ID、工具 ID、MCP ID。保存时应：
1. 读取当前 `toolIds`，解析为技能/工具/MCP 三部分
2. 替换技能部分为新的 `selectedSkillIds`
3. 重新拼接后保存

### 5.2 边界条件
- 无可用技能 → 显示空状态：`暂无可用技能`
- 技能列表为空但 `toolIds` 有历史引用 → 显示 ID 但不展示详情
- 保存时网络断开 → 恢复原勾选状态

### 5.3 错误处理
- 加载失败 → `availableSkills.value = []`，显示空列表
- 保存失败 → 恢复勾选状态，`ElMessage.error('保存失败')`

### 5.4 性能
- 技能列表预期小于 50 项，不需要分页/虚拟滚动
- `onMounted` 中并行加载两组数据减少等待时间
