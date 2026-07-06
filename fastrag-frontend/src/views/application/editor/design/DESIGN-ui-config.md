# 界面配置 — 设计文档

## 1. 功能概述

**目的：** 配置应用聊天界面的外观样式，包括欢迎语、颜色主题、占位文本等，提供实时预览。

**当前状态：** 编辑器内联 mock 实现（完整表单+实时预览，无保存/API）。

**改造目标：** 新建 `UiConfig.vue` 组件，对接 `getAppDialogConfig`/`saveAppDialogConfig` API。

---

## 2. 数据模型

### 2.1 API 端点

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/apps/{appId}/dialog` | 获取对话界面配置 | ✅ 已实现 |
| PUT | `/api/apps/{appId}/dialog/background` | 保存对话界面配置 | ✅ 已实现 |
| GET | `/api/apps/{appId}/dialog/export` | 导出对话配置（JSON） | ✅ 已实现 |
| POST | `/api/apps/{appId}/dialog/import` | 导入对话配置（JSON） | ✅ 已实现 |

**注意：** 现有 `AppDialogConfig` 实体字段只包含背景色等 UI 样式，不包含文本配置（欢迎语/标题等）。需要在对话框中扩展字段或使用 `advancedOptions`。

### 2.2 本地状态定义

```typescript
// UI 配置（文本 + 颜色）
const uiConfig = ref({
  // 文本配置
  welcome: '您好,有什么我可以帮助您',
  signature: '',
  title: '标题',
  placeholder: '请输入您的问题',
  textInfo: '帮助中心',
  brandText: 'Powered by AIS',

  // 颜色配置
  colorTemplate: 'default',
  chatWindowColor: 'rgb(1, 103, 229)',
  userMessageColor: 'rgba(1, 103, 229, 0.12)',
  questionFontColor: 'rgb(51, 51, 51)',
  replyMessageColor: 'rgb(238, 238, 238)',
  replyFontColor: 'rgb(51, 51, 51)',
})

const colorTemplates = [
  { label: '默认', value: 'default', color: 'rgb(1, 103, 229)' },
  { label: '生态绿', value: 'green', color: 'rgb(34, 139, 34)' },
  { label: '蜜蜡黄', value: 'yellow', color: 'rgb(218, 165, 32)' },
  { label: '琥珀橙', value: 'orange', color: 'rgb(255, 140, 0)' },
  { label: '中国红', value: 'red', color: 'rgb(220, 20, 60)' },
  { label: '炫酷黑', value: 'black', color: 'rgb(30, 30, 30)' },
  { label: '自定义', value: 'custom', color: '' },
]
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
onMounted(() => { loadUiConfig() })
```

### 3.4 内部方法

| 方法 | 说明 | API 调用 |
|------|------|----------|
| `loadUiConfig()` | 加载界面配置 | `getAppDialogConfig` |
| `handleSave()` | 保存界面配置 | `saveAppDialogConfig` |

---

## 4. UI 布局

```
┌─ 界面配置 ─────────────────────────────────────────┐
│                                                     │
│  ┌── 表单区域 ────────────┐ ┌── 预览区域 ────────┐ │
│  │ 文本配置               │ │  ┌──────────────┐  │ │
│  │ 欢迎语：[_________]    │ │  │ 标题    [↻][×]│  │ │
│  │ 签名：  [_________]    │ │  │              │  │ │
│  │ 标题：  [_________]    │ │  │ 欢迎语       │  │ │
│  │ 占位文本：[_________]  │ │  │              │  │ │
│  │ 文本信息：[_________]  │ │  │ 你好         │  │ │
│  │ 技术品牌：[_________]  │ │  │              │  │ │
│  │                       │ │  │ [输入问题...] │  │ │
│  │ 颜色配置               │ │  │    ▶         │  │ │
│  │ 模板：[默认][绿][黄]..│ │  │ Powered by...│  │ │
│  │ 窗口颜色：[______]     │ │  └──────────────┘  │ │
│  │ 用户消息：[______]     │ │  帮助中心          │ │
│  │ ...                    │ │                     │ │
│  └────────────────────────┘ └─────────────────────┘ │
│                                                     │
│                                   [保 存]            │
└─────────────────────────────────────────────────────┘
```

### 4.1 关键交互

- **选择颜色模板** → 自动填充对应颜色值，关闭"自定义"颜色输入框
- **颜色模板=自定义** → 启用颜色输入框
- **实时预览** → 表单输入即时反映在右侧预览窗口
- **保存** → 调用 `saveAppDialogConfig`

---

## 5. 实现要点

### 5.1 颜色模板联动

当用户选择预定义模板（非"自定义"）时：

```typescript
function applyColorTemplate(template: string) {
  if (template === 'custom') return  // 保持用户自定义颜色
  const tpl = colorTemplates.find(t => t.value === template)
  if (tpl) {
    uiConfig.value.chatWindowColor = templateColors[template].window
    uiConfig.value.userMessageColor = templateColors[template].userMsg
    // ... 填充其他颜色
  }
}
```

### 5.2 预览组件

预览区域使用纯 CSS 模拟聊天窗口，需要：
- 动态绑定 `:style` 到预览 DOM 元素
- 欢迎语、标题、品牌文本从表单实时读取
- 示例消息作为静态占位

### 5.3 边界条件
- 颜色值为空 → 使用默认值 `rgb(1, 103, 229)`
- 模板选择"自定义"但颜色未填 → 显示提示
- 欢迎语过长 → 预览区域截断

### 5.4 数据存储
- 文本配置字段（welcome/title/placeholder 等）后端 `AppDialogConfig` 实体无对应列
- 方案一：扩展 `AppDialogConfig` 表增加文本字段
- 方案二：将文本配置存储在 `AppBasicConfig.advancedOptions` JSON 中
- **建议方案二**，避免数据库迁移，前端透明读写

### 5.5 错误处理
- 加载失败 → 使用默认 UI 配置
- 保存失败 → `ElMessage.error('界面配置保存失败')`
