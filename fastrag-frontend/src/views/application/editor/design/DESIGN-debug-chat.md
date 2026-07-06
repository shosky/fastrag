# 对话调试 — 设计文档

## 1. 功能概述

**目的：** 在应用发布前，提供交互式聊天界面供开发者测试对话效果，验证配置是否生效。

**当前状态：** 编辑器内联 mock 实现，使用 `setTimeout` 模拟回复（返回固定错误信息），完全不可用。

**改造目标：** 新建 `DebugChat.vue` 组件，对接 `POST /api/apps/{id}/run` 真实 API，实现真实的对话调试。

---

## 2. 数据模型

### 2.1 API 端点

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| POST | `/api/apps/{appId}/run` | 运行应用（发送消息获取回复） | ✅ 已实现 |
| GET | `/api/apps/{appId}/debug` | 获取调试信息（日志） | ⚠️ 存根 |
| POST | `/api/apps/{appId}/debug` | 保存调试配置 | ⚠️ 存根 |

### 2.2 请求/响应格式

**POST /api/apps/{id}/run 请求体：**
```json
{
  "query": "你好，请介绍一下你们的服务"
}
```

**POST /api/apps/{id}/run 响应：**
```json
{
  "sessionId": "sess_abc123",
  "answer": "您好！我们提供基于AI的知识库问答服务...",
  "sources": ["知识库A", "知识库B"]
}
```

### 2.3 本地状态定义

```typescript
// 消息列表
const messages = ref<ChatMessage[]>([
  { role: 'assistant', content: '您好,有什么我可以帮助您' },
])

// 输入状态
const inputText = ref('')
const loading = ref(false)

// 会话信息
const sessionId = ref('')
const totalTime = ref(0)

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  time?: string     // 响应耗时
  sources?: string[] // 知识库来源
}
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
  // 初始化聊天界面，展示欢迎语
  messages.value = [{ role: 'assistant', content: '您好,有什么我可以帮助您' }]
})
```

### 3.4 内部方法

| 方法 | 说明 | API 调用 |
|------|------|----------|
| `handleSend()` | 发送消息并获取回复 | `POST /api/apps/{id}/run` |
| `handleClear()` | 清空对话历史 | 前端操作 |
| `handleRetry()` | 重新发送上一条消息 | `POST /api/apps/{id}/run` |

---

## 4. UI 布局

### 4.1 对话调试布局

```
┌─ 对话调试 ─────────────────────────────────────────┐
│                                                     │
│  ┌── 提示区 ───────────────┐ ┌── 聊天窗口 ──────┐ │
│  │ 📋 当前参数调整后       │ │  ┌──────────────┐ │ │
│  │ 问答对话调试验证效果，  │ │  │ 标题    [↻]  │ │ │
│  │ 满意后点击发布配置生效。│ │  ├──────────────┤ │ │
│  │                        │ │  │ 🤖 您好...   │ │ │
│  │ [发布配置]              │ │  │              │ │ │
│  │                        │ │  │ 你好 🧑      │ │ │
│  │ 提示：若不发布不影响    │ │  │              │ │ │
│  │ 原有应用参数配置，离开  │ │  │ 🤖 我们...   │ │ │
│  │ 后临时调整将丢失。      │ │  │              │ │ │
│  │                        │ │  ├──────────────┤ │ │
│  │                        │ │  │ [输入问题] ▶ │ │ │
│  │                        │ │  │ Powered by.. │ │ │
│  │                        │ │  └──────────────┘ │ │
│  └────────────────────────┘ └────────────────────┘ │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 4.2 消息气泡交互

```
用户消息（右对齐）:
  ┌──────────────────────┐
  │ 你好                 │
  │           🧑 复制 ↻ │
  └──────────────────────┘

AI 回复（左对齐）:
  🤖 ┌──────────────────┐
      │ 您好！有什么需要  │
      │ 帮助您的吗？      │
      │    ⏱ 1.2s 📚 2源│
      └──────────────────┘
```

---

## 5. 实现要点

### 5.1 消息流

```typescript
async function handleSend() {
  if (!inputText.value.trim()) return

  const question = inputText.value.trim()
  inputText.value = ''

  // 添加用户消息
  messages.value.push({ role: 'user', content: question })
  loading.value = true

  const startTime = Date.now()
  try {
    const res: any = await api.runApp(appId(), question)
    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1)

    messages.value.push({
      role: 'assistant',
      content: res?.answer || '抱歉，暂时无法回答',
      time: `${elapsed}s`,
      sources: res?.sources,
    })
  } catch {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，AI服务暂时不可用，请稍后再试',
      time: '-',
    })
  } finally {
    loading.value = false
  }
}
```

### 5.2 边界条件
- 空输入 → 阻止发送
- 发送中 → 按钮 loading，禁止重复提交
- API 超时（>30s）→ 显示超时提示
- 返回内容过长 → 自动换行显示

### 5.3 错误处理
- API 调用失败 → 显示友好错误消息，不丢失已发送的用户消息
- 网络断开 → 显示连接失败提示
- 消息列表过长 → 自动滚动到底部

### 5.4 性能考虑
- 消息列表使用 `ref` 数组，Vue 的响应式会处理 DOM 更新
- 使用 `nextTick` 在消息添加后滚动到最新消息

### 5.5 提示区内容
提示区在编辑器左侧显示，提醒用户：
- 当前对话测试不会影响已发布的应用
- 调整参数后可在此验证效果
- 满意后需点击"发布配置"使配置生效
