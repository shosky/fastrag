# 实现任务：new-conversation 前端（会话中心与新建对话）

## 任务
在 D:\Workspace\java\github\rag\fastrag\fastrag-frontend 实现特性 #19 前端全部代码，按 design 组件清单交付，vue-tsc 通过、无孤儿文件。

## 必读（按序）
1. `docs/design/new-conversation-frontend.md` —— 组件清单/线框/数据流（实现蓝图，严格照办）
2. `docs/spec/new-conversation-frontend.md` —— 10 TASK / 状态矩阵 / 14 验收（§8 六条 INFERRED 已裁决 ✅）
3. `AGENTS.md` + `docs/conventions.md` —— 仓规（分页 usePagination、禁 mock 消费、无孤儿文件、SSE fetch 直连先例）
4. 代码先例：`src/composables/useAppChatStream.ts`（SSE 解析与事件分发）、`src/views/application/runtime.vue`（聊天交互/智能滚动/消息操作）、`src/views/application/editor/components/KnowledgeConfig.vue`（KB 多选）、`src/api/index.ts`（API 封装惯例）

## 交付物（design §2.2 清单）
- `src/views/conversation/index.vue`（页面三栏）
- `src/views/conversation/components/{SessionListPanel,ConversationChat,MessageBubble,SourcesBlock,ChatComposer,KbScopePicker}.vue`
- `src/composables/useConversationStream.ts`（fetch POST `/conversations/{id}/stream`，事件九类同 useAppChatStream）
- `src/utils/sse.ts`（从 useAppChatStream.ts:334-378 抽取 parseSSEBuffer/parseSingleSSEEvent，原文件改为引用共享实现）
- `src/styles/chat-markdown.scss` 或等价（.markdown-body 样式单源）
- `src/api/index.ts` 追加：createConversation/listConversations/getConversation/pinConversation/clearConversationMessages/deleteConversations/generateConversationTitle/stopConversation（路径按后端设计 `/api/conversations**`，注释标注「springdoc 实现后为准」）
- `src/router/routes.ts`：`/conversation`（name ConversationCenter）；Sidebar.vue：+「会话中心」顶级菜单、−「应用运行」项（路由保留）
- `src/views/application/index.vue:115`：「▶运行」改跳 `/conversation?appId=`

## 关键行为要点（spec 已裁决，勿再发散）
- 单值对象：会话范围（kbIds/modelId/appId）页面级单一状态；发送成功不本地回写，静默刷新列表
- 挂预设时 KB 多选禁用 + 提示「知识库范围由应用决定」；预设 chip 可移除转直聊
- SourcesBlock 双形态：流内 SourceItem（含 content 全文展开+图片预览）/ 历史 references（无 content，仅来源行+跳原文 `/knowledge/{kbId}/chunks/{fileId}?chunkId=`）
- 停止 = AbortController.abort() + POST stop（双保险）；已生成部分保留标「（已停止）」
- Enter 发送 / Shift+Enter 换行；距底 <80px 自动跟随滚动；Enter 发送时禁流式重复提交
- 列表：置顶优先+updatedAt 倒序+来源 tag（直聊灰/应用名蓝）+checkbox 多选批量删（ElMessageBox 确认）+分页 usePagination
- 起标题：列表项 ⋯ 菜单内「✨ 智能起标题」，成功更新标题、失败静默
- 编辑器 AppChat.vue 与 runtime.vue 旧逻辑**不改**（仅 Sidebar/卡片入口两处改动）

## 验收（自我检查后再报告）
1. `npx vue-tsc --noEmit` 无新增错误（基线见 git diff 前后对比）
2. 无孤儿文件（每个新组件被 import）
3. 10 TASK 对应交互路径可走通（代码层面自查并在报告中标 TASK→文件映射）
4. 报告：交付文件清单 + 每文件职责一行 + vue-tsc 结果 + 任何偏离 design 的决定及理由
