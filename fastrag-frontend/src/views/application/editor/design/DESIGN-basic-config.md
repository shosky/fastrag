# 基础配置 — 设计文档

## 1. 功能概述

**目的：** 管理应用的基础运行参数，包括对话记忆、输出格式、超时时间、开场白/结束语等核心配置。

**用户场景：** 用户进入编辑器后首先配置基础参数，定义应用的对话行为和输出风格。

**范围：** 复用现有 `BasicConfig.vue` 组件，不做功能增减，仅清理死代码。

---

## 2. 数据模型

### 2.1 API 端点

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/apps/{appId}/basic` | 获取基础配置 | ✅ 已实现 |
| PUT | `/api/apps/{appId}/basic` | 保存基础配置 | ✅ 已实现 |
| PUT | `/api/apps/{appId}/basic/advanced` | 保存高级选项 | ✅ 已实现 |
| GET | `/api/apps/{appId}/basic/export` | 导出配置（JSON） | ✅ 已实现 |
| POST | `/api/apps/{appId}/basic/import` | 导入配置（JSON） | ✅ 已实现 |

### 2.2 本地状态定义

```typescript
// 基础配置表单
const basicForm = ref({
  memoryRounds: 5,       // 对话记忆轮数
  outputFormat: 'markdown',  // 输出格式: markdown | html | text
  timeoutSeconds: 30,    // 超时秒数
  greeting: '',          // 开场白
  goodbyeMessage: '',    // 结束语
})

// 高级选项（可展开/收起）
const advancedForm = ref({
  model: '',             // 模型选择
  temperature: 0.7,      // 温度
  maxTokens: 2048,       // 最大 Token 数
  topP: 1,               // Top P
  frequencyPenalty: 0,   // 频率惩罚
  presencePenalty: 0,    // 存在惩罚
})

const showAdvanced = ref(false)   // 高级选项展开/收起
const showDetailDialog = ref(false) // 配置详情对话框
const detailData = ref('')         // 详情内容
```

---

## 3. 组件设计

### 3.1 Props

```typescript
defineProps<{
  appInfo: {
    id: string
    name: string
    type: string
    description: string
    accessToken: string
  }
}>()
```

### 3.2 Emits

无（组件直接调用 API，不向父组件传递事件）

### 3.3 生命周期

```typescript
onMounted(() => { loadBasic() })
```

### 3.4 内部方法

| 方法 | 说明 | API 调用 |
|------|------|----------|
| `loadBasic()` | 加载基础配置 + 高级选项 | `getAppBasicConfig` |
| `saveBasic()` | 保存基础配置 | `saveAppBasicConfig` |
| `saveAdvancedOpts()` | 保存高级选项 | `saveAppAdvanced` |
| `handleViewDetail()` | 查看配置 JSON 详情 | `getAppBasicConfig` |
| `handleExportBasic()` | 导出 JSON | `exportAppConfig` |
| `handleImportBasic()` | 导入 JSON | `importAppConfig` |
| `copyToClipboard(text)` | 复制 AccessToken | 前端 API |

---

## 4. UI 布局

### 4.1 区域划分

```
┌─ 基础信息配置 ────────────────────────────────┐
│  [查看配置详情] [导出配置] [导入配置]           │
│  对话记忆轮数:  [5      ▾]                     │
│  输出格式:      [Markdown ▾]                   │
│  超时秒数:      [30     ▾]                     │
│  开场白:        [________________________]     │
│  结束语:        [________________________]     │
│  [保存基础配置]                                 │
└────────────────────────────────────────────────┘

┌─ 高级选项 ──────────────────────────────────────┐
│  [展开 ▼]                                       │
│  (展开后显示模型/温度/Token/TopP/惩罚等参数)    │
└────────────────────────────────────────────────┘

┌─ 基本信息 ──────────────────────────────────────┐
│  应用类型: [ChatBot] (disabled)                 │
│  应用名称: [智能问答助手] (disabled)            │
│  应用简介: [________________] (disabled)        │
└────────────────────────────────────────────────┘

┌─ 开发信息 ──────────────────────────────────────┐
│  AccessToken: [sk-ais-xxxxxx] [复制]            │
└────────────────────────────────────────────────┘
```

### 4.2 关键交互

- **保存基础配置** → 调用 `saveAppBasicConfig` → `ElMessage.success('基础配置已保存')`
- **展开高级选项** → `showAdvanced = true` → 显示高级表单
- **导出/导入** → 文件上传/下载 JSON
- **查看配置详情** → 弹窗展示完整 JSON

---

## 5. 实现要点

### 5.1 边界条件
- 导入 JSON 格式错误 → `ElMessage.error('导入失败，请检查文件格式')`
- API 无响应 → silent catch（`loadBasic` 中）
- 高级选项保存独立于基础配置，需分开调用

### 5.2 错误处理
- 所有用户触发的 API 调用用 try/catch 包裹
- 加载失败静默处理（组件用默认值降级）
- 导出/导入失败给出明确错误提示

### 5.3 备注
- 组件已完整实现且通过测试，无需改造
- 清理 `editor/index.vue` 中的内联死代码（`basicForm`、`handleSaveBasic` 等）
- 高级选项使用 `v-if="showAdvanced"` 条件渲染，避免 DOM 堆积
