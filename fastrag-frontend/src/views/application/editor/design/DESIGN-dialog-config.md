# 对话配置 — 设计文档

## 1. 功能概述

**目的：** 配置对话窗口的展现样式（背景色、头像、反馈按钮等）以及管理应用的触发器（关键词/正则/意图触发规则）。

**当前状态：** 现有 `DialogConfig.vue` 组件已完整实现且对接 API。

**改造目标：** 直接复用 `DialogConfig.vue`，不做功能增减，仅清理 `editor/index.vue` 中的相关死代码。

---

## 2. 数据模型

### 2.1 API 端点

#### 对话样式配置

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/apps/{appId}/dialog` | 获取对话配置 | ✅ 已实现 |
| PUT | `/api/apps/{appId}/dialog/background` | 保存对话配置 | ✅ 已实现 |
| GET | `/api/apps/{appId}/dialog/export` | 导出对话配置 | ✅ 已实现 |
| POST | `/api/apps/{appId}/dialog/import` | 导入对话配置 | ✅ 已实现 |

#### 触发器管理

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/apps/{appId}/triggers` | 获取触发器列表 | ✅ 已实现 |
| POST | `/api/apps/{appId}/triggers` | 创建触发器 | ✅ 已实现 |
| PUT | `/api/apps/{appId}/triggers/{id}` | 更新触发器 | ✅ 已实现 |
| DELETE | `/api/apps/{appId}/triggers/{id}` | 删除触发器 | ✅ 已实现 |
| POST | `/api/apps/{appId}/triggers/{id}/test` | 测试触发器 | ✅ 已实现 |
| POST | `/api/apps/{appId}/triggers/{id}/run` | 运行触发器 | ✅ 已实现 |

### 2.2 本地状态定义

```typescript
// 对话样式
const dialogForm = ref({
  backgroundColor: '#f5f5f5',
  showAvatar: 1,       // 显示头像 (0/1)
  showFeedback: 1,     // 显示反馈 (0/1)
  showSuggestions: 1,  // 显示推荐 (0/1)
})

// 触发器 CRUD
const triggerList = ref<Trigger[]>([])
const showTriggerDialog = ref(false)
const isEditingTrigger = ref(false)
const editingTriggerId = ref('')
const triggerForm = ref({
  name: '',
  triggerType: 'keyword',
  matchContent: '',
  actionType: 'reply',
  actionConfig: '',
})
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
onMounted(() => {
  loadDialog()
  loadTriggers()
})
```

### 3.4 内部方法

| 方法 | 说明 | API 调用 |
|------|------|----------|
| `loadDialog()` | 加载对话样式 | `getAppDialogConfig` |
| `saveDialog()` | 保存对话样式 | `saveAppDialogConfig` |
| `handleExportDialog()` | 导出对话配置 | `exportAppDialog` |
| `handleImportDialog()` | 导入对话配置 | `importAppDialog` |
| `loadTriggers()` | 加载触发器列表 | `getAppTriggers` |
| `openAddTrigger()` / `openEditTrigger()` | 打开新增/编辑触发器弹窗 | — |
| `handleSaveTrigger()` | 保存触发器（新增/编辑） | `createAppTrigger` / `updateAppTrigger` |
| `handleDeleteTrigger()` | 删除触发器 | `deleteAppTrigger` |
| `handleToggleTrigger()` | 启用/禁用触发器 | `updateAppTrigger` |
| `handleTestTrigger()` | 测试触发器匹配 | `testAppTrigger` |
| `handleRunTrigger()` | 运行触发器动作 | `runAppTrigger` |

---

## 4. UI 布局

### 4.1 对话样式区域

```
┌─ 对话配置 ─────────────────────────────────────────┐
│  ┌────────────────────────────────────────────────┐│
│  │ 对话框背景: [■ 颜色选择器]                     ││
│  │ 显示头像:   [开关]                             ││
│  │ 显示反馈:   [开关]                             ││
│  │ 显示推荐:   [开关]                             ││
│  │                          [导出] [导入] [保 存]  ││
│  └────────────────────────────────────────────────┘│
└────────────────────────────────────────────────────┘
```

### 4.2 触发器管理区域

```
┌─ 触发器管理 ───────────────────────────────────────┐
│                                     [新增触发器]    │
│  ┌────┬──────┬──────┬────┬──────────────────────┐  │
│  │名称│类型  │动作  │启用│ 操作                 │  │
│  ├────┼──────┼──────┼────┼──────────────────────┤  │
│  │规则1│关键词│回复  │[✓] │[编辑][测试][运行][×]│  │
│  │规则2│正则  │API   │[  ]│[编辑][测试][运行][×]│  │
│  └────┴──────┴──────┴────┴──────────────────────┘  │
│  暂无触发器（空状态）                               │
└────────────────────────────────────────────────────┘
```

### 4.3 触发器新增/编辑弹窗

```
┌─ 新增触发器 ──────────────────────────────────────┐
│  名称:     [_________]                             │
│  类型:     [关键词 ▾]                               │
│  匹配内容: [________________]                       │
│  动作类型: [回复 ▾]                                 │
│  动作配置: [________________] (JSON 格式)          │
│                                                    │
│           [取消] [创建]                              │
└────────────────────────────────────────────────────┘
```

---

## 5. 实现要点

### 5.1 组件复用

- `DialogConfig.vue` 已完整实现，直接保留
- 无需修改组件代码
- 清理 `editor/index.vue` 中的相关内联代码

### 5.2 边界条件
- 触发器列表为空 → 显示空状态 `暂无触发器`
- 触发器测试/运行时无输入 → 通过 `ElMessageBox.prompt` 获取输入
- `actionConfig` 为空 → 转换为 JSON null（MySQL JSON 列限制）

### 5.3 错误处理
- 所有 API 调用通过 try/catch 包裹
- 加载失败 → 空列表降级
- 保存/删除失败 → `ElMessage.error('操作失败')`
- 用户取消弹窗 → catch 静默处理

### 5.4 备注
- 测试和运行端点在服务端实现相同（仅增加命中计数）
- 对话配置的导入/导出通过独立 API 处理
