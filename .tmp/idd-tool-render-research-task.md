# 调研任务：工具调用与知识来源的渲染现状 vs WeKnora 专用渲染器

## 背景
用户问：工具调用、知识来源，以及应用里配置的数据库/知识库/技能等，是否都挤在"工具调用"里显示？样式能否参考 WeKnora 优化？需要先把双侧渲染现状摸清。只读。

## 任务 A：FastRAG 现状渲染链（D:\Workspace\java\github\rag\fastrag）
1. `fastrag-frontend/src/components/ToolCallRenderer.vue`（199 行，全读）：当前通用卡渲染什么字段（name/arguments/result/status?）、折叠策略、成功失败视觉、结果怎么展示（JSON 原样?）
2. `fastrag-frontend/src/views/conversation/components/SourcesBlock.vue`：分组/抽屉/双形态现状摘要
3. **后端各能力在 tool_result.output 里的实际形状**（决定专用渲染器可行性）：
   - DatabaseMiddleware 注册的 `{dbId}_query_database` 等工具：执行结果 output 是什么 JSON 结构（行数组? 列名?）——找 fastrag-agent DatabaseToolExecutor/DatabaseMiddleware 的结果组装代码
   - KnowledgeBaseMiddleware 的 query_kb/list_kbs：结果结构
   - SkillsMiddleware 的技能读取：结果结构
   - McpToolExecutor：结果结构
   - 各自 fastrag-agent/.../middleware/*.java + tools 模块 executor 的结果组装处，file:line
4. 前端能拿到什么：tool_result 事件的 output 字段是否原样透传（useConversationStream/AppChatStream 的 onToolResult → index.vue → message.toolCalls[].result）

## 任务 B：WeKnora 专用渲染器交互模式（D:\Workspace\python\github\WeKnora\frontend）
读 `frontend/src/views/chat/components/tool-results/` 下与本次最相关的 6-8 个渲染器，总结**交互模式**（不是逐行）：
- DatabaseQuery：表格/SQL/行数怎么展示
- KnowledgeChunksList + ChunkDetail：命中分片列表与详情联动
- McpToolResult / ReadSkillResult：结构化摘要方式
- SearchResults / WebSearchResults：来源分组与外链
- ThinkingDisplay：思考折叠
- 通用回退渲染器（若有）：兜底形态
- 每个渲染器如何按 tool name 路由（注册表/switch?）、折叠/展开策略、耗时/状态徽标

## 输出（中文）
- A：渲染链现状图 + 每能力 output 形状表（能力→事件→output 结构→前端渲染方式）
- B：WeKnora 渲染器交互模式表（渲染器→关键交互→值得抄的点）file:line
- 禁止臆测；读不到标 GAP
你的最终回复就是调研报告全文。
