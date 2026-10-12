# 实现任务：工具结果专用渲染器 + 卡片骨架升级（app-chat-optimize 第二批）

## 背景
后端已完成结构化通道（模块测试 72/72 绿）：`tool_result` 事件与历史 toolCalls 现在可携带 `data` 字段（结构化数据）。已落地的两种结构：
- 数据库查询（工具名 `*_query_database`）：`data = {display_type:'database_query', columns:[...], rows:[{...}], row_count, elapsed_ms}`
- 知识库检索（工具名 `query_kb`）：`data = {display_type:'knowledge_chunks', kb_id, query, chunks:[{content,score,source,...}]}`
- 其他工具：无 data（通用回退）

工作目录 D:\Workspace\java\github\rag\fastrag\fastrag-frontend。G01：runtime.vue/AppChat.vue 零改动（共享组件 ToolCallRenderer 允许展示增强，但不得改事件数据流）。

## Step A 渲染路由 + 类型
1. `src/utils/toolDisplay.ts`：
   - `resolveToolDisplayType(toolName, result?): 'database_query' | 'knowledge_chunks' | 'generic'`——三段优先级（对齐 WeKnora resolveToolDisplayType）：result?.data?.display_type → 工具名后缀 `_query_database` → 工具名 `query_kb` → generic
   - `summarizeToolCall(toolName, result?)`：一行摘要——database_query="查询返回 {row_count} 行"；query_kb="命中 {chunks.length} 条分片"；pending="执行中…"；失败=error 文本截 40；成功 generic="执行完成"
2. `src/types/conversation.ts` 或 `useAppChatStream.ts`：ToolResultEvent 增加 `data?: Record<string, unknown>`（透传即可，无需加工）；`ToolCallItem.result` 同步

## Step B 两个专用渲染器（新建 `src/views/conversation/components/tool-results/`）
1. `DatabaseQueryTable.vue`：props {data}——三分支：正常（columns→thead，rows→tbody，单元格 max-width 省略+title，容器横向滚动）、空结果（虚线空态）、异常；不显示 SQL
2. `KnowledgeChunksList.vue`：props {data}——头部一行摘要「命中 N 条分片 · 查询：{query}」；列表项=score 百分比 + content 截 200 字 + source；空态
3. 两者样式沿用 MessageBubble 的配色体系（$primary #046a82 等）

## Step C ToolCallRenderer 卡片骨架升级
1. 按 `resolveToolDisplayType` 分发：database_query → DatabaseQueryTable；knowledge_chunks → KnowledgeChunksList；generic → 现有 `<pre>` 但加「原始输出」标签头
2. 卡片骨架：标题行 = 按工具名前缀取图标（database→Coin / query_kb→Collection / mcp→Connection / skill→MagicStick / 其他→SetUp）+ 动态文案（pending=「正在调用 {name}…」/完成=工具名）；三态样式（执行中蓝描边/失败红字/默认）
3. **收起状态一行摘要**：外层 collapse 头部（或卡片头下）常驻 `summarizeToolCall` 结果——收起也有信息量
4. 历史回看：`conversation/index.vue` 的 `normalizeToolCalls` 保留 result.data 透传（检查现收编逻辑不丢 data 字段）

## 文档同步
- `docs/contract/ui.md` 特性 19 章：State Contract 加「工具结果结构化渲染」行；Data Mapping 加 data 字段行

## 验收（报告必附）
1. `npx vitest run` 全绿：新增 `toolDisplay.test.ts`（resolveToolDisplayType 三段优先级 + summarizeToolCall 各态）+ 既有 41 不回归
2. `npx vue-tsc -b --force` 与基线 159 对比零新增
3. `npx vite build` 通过
4. G01 核对：runtime.vue / AppChat.vue 零 diff
5. 报告（中文）：Step A-D 交付清单、自验结果、偏离及理由、遗留问题
