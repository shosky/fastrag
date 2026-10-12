# 实现任务：对话页三项优化（应用选择器 / 检索等待指示 / 直聊工具消费 UI）

## 背景
后端已完成 B02 演进（直聊消费 tool/mcp/skill ID）并全量测试通过（72/72）。契约变化：
- `POST /conversations/{id}/stream` 请求体现在接受 `toolIds`/`mcpIds`/`skillIds`（数组，直聊装配进执行上下文）；`webSearchEnabled` 仍仅存
- 设计蓝图 `docs/design/app-chat-optimize-frontend.md`；契约 fastrag-backend `contract/interfaces.md ## new-conversation`

工作目录 D:\Workspace\java\github\rag\fastrag\fastrag-frontend。G01 铁律：runtime.vue / AppChat.vue 零改动。

## ① 输入框应用选择器（对齐 WeKnora AgentSelector）
1. `ChatComposer.vue` 控制栏左侧新增「应用」下拉（el-select，数据源 `getApps`）：
   - 选项含首项「直聊（不挂应用）」（value 空）+ 当前用户可见应用列表
   - 选中应用 = 设 composerState.appId（预设 chip 随之显示）；选直聊 = 清 appId（chip 消失）
   - 与现有预设 chip 单一事实源合一：chip 与下拉双向同步（下拉显示当前 appId，chip × 移除后下拉回「直聊」）
2. `conversation/index.vue`：接线下拉 change → composerState.appId；KB 多选的禁用逻辑保持（挂应用=禁用+提示）
3. 不新增后端调用（getApps 已存在 api:757）

## ② 检索等待指示
1. `conversation/index.vue`：新增 `searching` ref——发送时置 true（仅当 kbIds 非空），收到 sources 事件 / 首个 message 增量 / end / error 时置 false
2. `ConversationChat.vue`：searching=true 且尚无助手内容时，在流式气泡上方显示检索指示（el-tag 或轻量动画：「正在检索知识库…」）；有内容后消失
3. 纯前端状态，不新增后端事件

## ③ 直聊工具/MCP/技能消费 UI
1. `useConversationStream.ts`：`ConversationTurnPayload` 增加 `toolIds?/mcpIds?/skillIds?: string[]`，body 透传（空数组发 undefined）
2. `ChatComposer.vue`：直聊时（挂预设时隐藏——能力走应用绑定）控制栏加「能力」popover（复用 KbScopePicker 的弹层模式）：
   - 三个分组多选：工具 / 技能 / MCP——数据源用 api/index.ts 中现成的列表接口（自查：getTools/getSkills/getMcpServices 或相近命名，grep 确认）
   - 已选 ID 以 chip 内联显示在控制栏（同 KB chip 风格），可单个移除
3. `conversation/index.vue`：composerState 扩展 toolIds/mcpIds/skillIds → 随 payload 发送；重开会话从 requestState 还原这三个字段
4. `src/types/conversation.ts`：RequestState 类型补三字段注释（直聊消费，B02 演进）

## 文档同步
- `docs/contract/ui.md` 特性 19 章：输入区表格加「应用下拉」「能力 popover」两行；State Contract 加「检索等待」行
- `docs/spec/new-conversation-frontend.md` TASK-001 范围补一句「直聊可选工具/MCP/技能（B02 演进）」

## 验收（报告必附）
1. `npx vitest run` 全绿（既有 40 不回归；为 buildSessionMarkdown 之外的新纯函数补测试则一并计）
2. `npx vue-tsc -b --force` 与当前基线 159 对比零新增
3. `npx vite build` 通过
4. G01 核对：runtime.vue / AppChat.vue 零 diff
5. 报告（中文）：逐项交付（①②③→文件→要点）、自验结果、偏离及理由、遗留问题
