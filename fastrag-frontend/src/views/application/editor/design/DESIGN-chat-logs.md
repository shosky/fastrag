# 对话记录 — 设计文档

## 1. 功能概述

**目的：** 查看用户与应用的对话历史记录，支持搜索、筛选、详情查看和导出。

**当前状态：** 编辑器内联 mock 实现（4 条硬编码假数据）。

**改造目标：** 新建 `ChatLogs.vue` 组件。后端当前无应用级对话日志 API（仅有 KB 检索日志 `GET /api/retrieval/logs`），需要新增 API 或暂用前端 mock 数据。

---

## 2. 数据模型

### 2.1 API 端点

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/retrieval/logs` | 获取检索日志（分页） | ✅ 已实现（但为 KB 日志） |
| GET | `/api/retrieval/logs/analysis` | 日志分析 | ✅ 已实现 |

> **注意：** 检索日志记录的是知识库查询日志，而非应用对话日志。应用对话日志需要新增后端接口或通过应用运行时埋点收集。

### 2.2 建议 API 扩展（待后端实现）

| 方法 | 端点 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/apps/{appId}/chat-logs` | 获取对话记录列表（分页） | ❌ 待新增 |
| GET | `/api/apps/{appId}/chat-logs/{id}` | 获取单条对话详情（含消息列表） | ❌ 待新增 |
| DELETE | `/api/apps/{appId}/chat-logs/{id}` | 删除对话记录 | ❌ 待新增 |
| GET | `/api/apps/{appId}/chat-logs/export` | 导出对话记录 | ❌ 待新增 |

### 2.3 临时方案

在后端 API 就绪前，使用前端 `advancedOptions` 中存储的"示例对话记录"作为 mock 展示，或完全在前端 mock 数据：

```typescript
// 对话记录
const chatLogs = ref<ChatLog[]>([
  {
    id: '1',
    user: '匿名用户A',
    sessionId: 'sess_abc123',
    question: '你们的服务有哪些功能？',
    answer: '我们提供AI知识库问答...',
    rating: 5,
    tokens: 256,
    time: '2026-06-27 14:32:10',
    messages: [
      { role: 'user', content: '你们的服务有哪些功能？', tokens: 12, latency: '0.1s' },
      { role: 'assistant', content: '我们提供AI知识库问答...', tokens: 244, latency: '1.2s' },
    ],
  },
])

// 筛选状态
const keyword = ref('')
const dateRange = ref<[Date, Date] | null>(null)
const ratingFilter = ref('')

// 详情弹窗
const showDetail = ref(false)
const selectedLog = ref<ChatLog | null>(null)
```

### 2.4 接口定义

```typescript
interface ChatLog {
  id: string
  user: string
  sessionId: string
  question: string
  answer: string
  rating: number      // 1-5
  tokens: number
  time: string
  messages: ChatMessage[]
}

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  tokens?: number
  latency?: string    // 响应耗时
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
onMounted(() => { loadChatLogs() })
```

### 3.4 内部方法

| 方法 | 说明 | API 调用 |
|------|------|----------|
| `loadChatLogs()` | 加载对话记录 | 暂为 mock |
| `handleQuery()` | 按条件筛选 | 前端过滤 |
| `handleReset()` | 重置筛选条件 | 前端操作 |
| `handleViewDetail(row)` | 查看对话详情 | 前端展示 |
| `handleDelete(id)` | 删除记录 | `DELETE`（待实现） |
| `handleExport()` | 导出记录 | `GET export`（待实现） |

---

## 4. UI 布局

### 4.1 对话记录列表

```
┌─ 对话记录 ─────────────────────────────────────────┐
│  查看和搜索用户与应用的对话历史记录                │
│                                                    │
│  [搜索对话内容...🔍] [开始日期 → 结束日期]        │
│  [评分过滤 ▾]  [查询] [重置]       [📥 导出记录]   │
│                                                    │
│  ┌────┬──────┬────────┬────────────────┬────┬───┐ │
│  │ #  │ 用户  │ 会话ID │ 消息摘要       │评分│ 时间│ │
│  ├────┼──────┼────────┼────────────────┼────┼───┤ │
│  │ 1  │匿名A  │sess…   │Q:你们的服务…  │★★★★│06-27│ │
│  │    │      │        │A:我们提供…     │    │    │ │
│  │ 2  │匿名B  │sess…   │Q:如何配置…    │★★★★│06-27│ │
│  │    │      │        │A:在知识库配…   │    │    │ │
│  └────┴──────┴────────┴────────────────┴────┴───┘ │
│                                                    │
│  共 4 条记录            ◀ 1 2 3 ▶                  │
└────────────────────────────────────────────────────┘
```

### 4.2 对话详情抽屉

```
┌─ 对话详情 (Drawer) ───────────────────────────────┐
│  用户：匿名用户A | 会话ID：sess_abc123 | 06-27     │
│                                                     │
│  ┌────────────────────────────────────────────────┐ │
│  │ [用户] 你们的服务有哪些功能？                   │ │
│  │        Token: 12 | 耗时: 0.1s                  │ │
│  ├────────────────────────────────────────────────┤ │
│  │ [AI] 我们提供AI知识库问答、智能客服、文档助手  │ │
│  │      等功能，支持多种模型和知识库配置。        │ │
│  │       Token: 244 | 耗时: 1.2s                  │ │
│  └────────────────────────────────────────────────┘ │
│                                                     │
│                                      [关 闭]        │
└─────────────────────────────────────────────────────┘
```

---

## 5. 实现要点

### 5.1 后端 API 缺失应对方案

- **短期方案**：使用前端静态 mock 数据展示，确保 UI 交互完整
- **中期方案**：应用运行时埋点，通过消息队列写入对话日志表
- **长期方案**：对接后端 `GET /api/apps/{appId}/chat-logs` 分页接口

### 5.2 筛选逻辑

```typescript
const filteredLogs = computed(() => {
  let list = chatLogs.value
  if (keyword.value) {
    list = list.filter(l => l.question.includes(keyword.value) || l.answer.includes(keyword.value))
  }
  if (ratingFilter.value) {
    list = list.filter(l => l.rating === Number(ratingFilter.value))
  }
  if (dateRange.value) {
    const [start, end] = dateRange.value
    list = list.filter(l => new Date(l.time) >= start && new Date(l.time) <= end)
  }
  return list
})
```

### 5.3 边界条件
- 无对话记录 → 显示空状态"暂无对话记录"
- 筛选无结果 → 显示"未找到匹配记录"
- 消息内容过长 → 列表截断显示，详情完整展示
- 评分过滤：好评(4-5) / 中评(3) / 差评(1-2)

### 5.4 错误处理
- 加载失败 → 显示"加载失败，请重试"
- 删除失败 → `ElMessage.error('删除失败')`
- 导出失败 → `ElMessage.error('导出失败')`

### 5.5 性能考虑
- 对话记录可能数量较大（上万条），需分页
- 消息详情使用 Drawer 抽屉加载，不占用页面空间
- 导出操作在后台进行，不阻塞用户操作

### 5.6 后续扩展
- 支持对话记录统计分析（平均评分、Token 消耗趋势等）
- 支持标记特定对话用于优化训练
- 支持对话回放功能
