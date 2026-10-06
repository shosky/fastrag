# 新建对话与会话中心（new-conversation） — 前端架构设计

> 2026-09-25。输入：[../spec/new-conversation-frontend.md](../spec/new-conversation-frontend.md)、后端契约 fastrag-backend `contract/interfaces.md ## new-conversation`、侦察证据（runtime.vue 1127 行 / useAppChatStream.ts 378 行 / AppChat.vue 823 行，行号均为 ‹CERTAIN› 实读）。

## 1. Page Map

```
/conversation                    会话中心（新，本特性主战场）
/application/runtime             旧运行页（保留兼容，菜单移除；内部不重写）
/application/:id/editor          编辑器（不动；「对话调试」Tab 走旧通道）
入口改造点：
  Sidebar.vue                    +「会话中心」顶级菜单（INFERRED-1）；−「应用运行」项（INFERRED-2）
  application/index.vue:115      「▶运行」push('/application/runtime?appId=') → push('/conversation?appId=')
```

## 2. Page / Layout / Component Map

### 2.1 会话中心线框（/conversation）

```
┌──────────────┬──────────────────────────────────────────────┐
│ [+ 新建对话]  │  会话头部：标题 / [✨起标题] [⭐置顶] [🧹清空] │
│──────────────│──────────────────────────────────────────────│
│ ⭐ 会话A 直聊  │                                              │
│    会话B      │            消息滚动区（.chat-main）           │
│    应用X ·10  │   ┌ MessageBubble ×N ─────────────────┐     │
│    会话C ·3   │   │ markdown(v-html)                   │     │
│   ──────────  │   │ ▸ ThinkingBlock  ▸ ToolCallRenderer│     │
│ ☑多选模式     │   │ ▸ SourcesBlock（流内/历史双形态）    │     │
│  …列表项…     │   └────────────────────────────────────┘     │
│ [分页]        │──────────────────────────────────────────────│
│              │  [预设 chip：应用X ×]（挂载时；移除=转直聊）    │
│              │  [📚KB 多选][模型▾] 输入框………… [发送/停止]     │
└──────────────┴──────────────────────────────────────────────┘
```

### 2.2 组件清单

| 组件 | 新建/复用 | 职责 | 关键实现依据 |
|---|---|---|---|
| `views/conversation/index.vue` | 新建 | 页面骨架三栏 + 页面态编排 | 布局沿 runtime 三段式（:336-548） |
| `views/conversation/components/SessionListPanel.vue` | 新建 | 列表：置顶图标/来源标记/多选批量删/分页/项操作菜单（起标题·置顶·清空·删除） | 分页用 usePagination（仓规）；列表项信息沿 runtime Side Panel 增列 |
| `views/conversation/components/ConversationChat.vue` | 新建 | 消息滚动区 + 智能滚动跟随 + 消息 v-for | 距底 <80px 跟随算法平移（runtime :241-263） |
| `views/conversation/components/MessageBubble.vue` | 新建 | 单气泡：markdown 渲染 + 操作条（复制/反馈/删除/编辑） | 交互平移 runtime :267-308；markdown 用共享渲染（见 2.3） |
| `views/conversation/components/SourcesBlock.vue` | 新建 | 来源折叠块**双形态**：流内 `SourceItem`（含 content，全文展开 + 图片预览）/ 历史 `MessageReference`（无 content，仅来源行 + 跳原文） | 平移 runtime :378-424 并参数化；跳转 jumpToChunk（:208-212）复用 |
| `views/conversation/components/ChatComposer.vue` | 新建 | 输入区：KB 多选 popover + 模型下拉 + 预设 chip + 发送/停止 | KB 弹层逻辑参考 KnowledgeConfig（分类+checkbox 多选，:137-188）收敛为 popover；模型下拉沿 BasicConfig 过滤 online |
| `views/conversation/components/KbScopePicker.vue` | 新建 | KB 多选弹层（挂预设时禁用） | 数据 `getKnowledgeBases({page:1,pageSize:100})` 惯例 |
| `composables/useConversationStream.ts` | 新建 | 新通道 SSE：`fetch POST /conversations/{id}/stream` + AbortController + 事件分发（九类同词汇） | **解析函数抽自** useAppChatStream.ts:334-378 → `utils/sse.ts` 共享（消灭第三份手写分帧——AiChunkPanel 已重复一次，feature-map :117 在案） |
| `utils/sse.ts` | 新建（抽取） | `parseSSEBuffer` / `parseSingleSSEEvent` | useAppChatStream 与 AiChunkPanel 的公共下游 |
| `styles/chat-markdown.scss` | 新建（抽取） | `.markdown-body` 样式单源 | 现 runtime:914-940 与 AppChat:719-762 双份，收敛一份 |
| ThinkingBlock / ToolCallRenderer | 复用 | 思考/工具卡 | 现成共享件 |
| usePagination | 复用 | 列表分页 | 仓规 |
| runtime.vue / AppChat.vue | 不动 | 旧通道 UI | G01；统一到新组件族登记为深化机会（不在本特性） |

## 3. State Ownership

- **组件局部 ref**（无新 Pinia，与仓现状一致——仅 system/user 两个 store）：页面级 `sessions/activeConversation/composerState/messages/isStreaming`；SessionListPanel 内 `selection/multiSelectMode`；ChatComposer 内 `kbScope/modelId/presetChip`。
- **单一事实源原则**：composerState（kbIds/modelId/appId）是页面的唯一范围状态，来源有三——URL `?appId=` 初始化、`getConversation` 还原（A04）、用户改动；三处都写同一次。发送时快照随消息提交，成功后**不本地回写**，以列表静默刷新（后端固化为准）保持单一事实源在服务端。
- **流式状态**归 useConversationStream（isStreaming/abort 引用），页面只读。

## 4. Data Flow

```
发送链路：
ChatComposer.submit ──快照{query, kbIds, modelId, appId?}──▶ useConversationStream.send
  ├─ 无会话：POST /conversations（随首条消息建会话，A01 惯例改造 ensureSession）
  ├─ fetch POST /conversations/{id}/stream（AbortController）
  │    事件→ onChunk/onThinking/onToolCall/onSources/onEnd/onError → ConversationChat 增量渲染
  ├─ onEnd：乐观 assistant 气泡定稿 → 静默刷新列表（标题/计数/updatedAt 服务端为准）
  └─ stop：AbortController.abort() + POST /conversations/{id}/stop（双保险：前端断流 + 后端幂等落库）

还原链路（A04）：
选中会话 → GET /conversations/{id} → requestState → composerState（kbIds/modelId/appId）
       → GET 消息历史 → assistant.references → SourcesBlock（历史形态）

预设链路（D01/D02）：
?appId= 进入 → 预设 chip + composerState.appId；移除 chip → appId=null 转直聊，KB 多选解禁；
挂载态下 kbIds 由应用绑定决定（前端禁用多选 + 提示），不做双源合并 UI
```

## 5. Visual System

- 沿用 Element Plus + 现有 `.chat-main`/`.input-area-wrapper` 布局语义；`.markdown-body` 收敛 `styles/chat-markdown.scss` 单源。
- 来源标记徽标：直聊 = 灰色 tag「直聊」；应用预设 = 蓝色 tag「{应用名}」（el-tag size=small）。
- 置顶：⭐ 图标激活态 + 排序首位（列表项右上角常驻小图钉，激活着色）。
- 不引入新 UI 依赖；引文角标 [1][2] 需 marked 渲染方案评估，明确排除在 v1（spec §1）。

## 6. Parent Trace

| Design 单元 | Spec TASK / 后端 REQ |
|---|---|
| views/conversation/index.vue + SessionListPanel | TASK-002/007 → REQ-A03/E03/E04/E05、NFR-01 |
| ConversationChat + MessageBubble | TASK-004/008/010 → REQ-C02/F02（+ 继承交互） |
| SourcesBlock（双形态） | TASK-004/008 → REQ-F01/F02 |
| ChatComposer + KbScopePicker | TASK-001/003/009 → REQ-A01/B01/C01/D01/D02、A04 |
| useConversationStream + utils/sse.ts | TASK-004/005 → REQ-C02/E02 |
| Page Map 入口改造 | TASK-009 → REQ-D01、G01（旧通道不动） |
| presets/模型下拉在线过滤 | REQ-C05（请求态模型优先的 UI 侧） |
