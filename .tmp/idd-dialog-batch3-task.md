# 实现任务：app-chat-optimize 第三批（会话汇总行 / 来源角标 / MCP·技能兜底形态）

## 背景
二批已落地（toolDisplay 路由 + DatabaseQueryTable/KnowledgeChunksList + 卡片骨架）。后端已加引用标注协议：检索命中时系统提示词要求模型以 `[N]`（N=【参考资料N】序号）在句末标注来源。工作目录 D:\Workspace\java\github\rag\fastrag\fastrag-frontend。G01：runtime.vue / AppChat.vue 零改动。

## A 会话汇总行
1. `src/types/conversation.ts`：ConversationChatMessage 加 `iterations?: number`
2. `conversation/index.vue` onEnd：`messages.value[mi].iterations = data.iterations`（onEnd 第二参 data；后端 end 事件含 iterations/durationMs）
3. `MessageBubble.vue`：assistant 完成且（toolCalls?.length 或 iterations）时，footer 上方显示汇总 chip 行：`🔧 {toolCalls.length} 次工具调用 · {iterations} 轮 · 耗时 {time}`（缺失项跳过）

## B 来源角标 [N]
1. `MessageBubble.vue`：assistant 且（sources?.length 或 references?.length）时，渲染后处理——`renderMarkdown(content)` 产出的 HTML 里，将 `[N]`（N 为 1..来源数）替换为 `<sup class="cite-ref" title="来源 N">[N]</sup>`（自有安全 HTML，替换在 sanitize 之后进行）；点击角标 emit `(e:'open-sources')`
2. `SourcesBlock.vue`：抽屉开关支持受控——新增 prop `drawerVisible` + emit `update:drawerVisible`（内部 el-drawer v-model 绑定）
3. `ConversationChat.vue`：透传 open-sources → index.vue：`sourcesDrawerVisible` ref，置 true 打开抽屉
4. 角标样式：上标、$primary 色、hover 下划线、cursor:pointer
5. 边界：N > 来源数时不替换（防模型幻觉编号误判为引用）；无来源时不替换

## C MCP / 技能兜底形态（ToolCallRenderer 内）
1. 工具名 `mcp` 前缀或含 `mcp`：卡片加「MCP 工具」tag；结果 output 若可 JSON.parse 且含数组/对象 → 简单键值/列表化展示，否则维持 pre（MCP 形状取决于远端，不强解析）
2. 工具名含 `skill` 或 `read_file`/`read`：结果用 `renderMarkdown` 渲染（后端技能读取返回 Markdown；已过 DOMPurify 单源）+「技能」tag
3. 两者走 ToolCallRenderer 内部分支（基于 resolveToolDisplayType 扩展 'mcp' | 'skill_md' 或直接在 generic 分支内判断），不改 ToolResultRenderer 数据流

## 文档同步
- `docs/contract/ui.md` 特性 19 章：State Contract 加「引用角标」「会话汇总行」两行

## 验收（报告必附）
1. `npx vitest run` 全绿（41+ 既有不回归；角标替换 `[N]` 边界逻辑若抽纯函数则补测试）
2. `npx vue-tsc -b --force` 与基线 159 对比零新增
3. `npx vite build` 通过
4. G01 核对：runtime.vue / AppChat.vue 零 diff
5. 报告（中文）：A/B/C 交付清单、自验结果、偏离及理由、遗留问题
