# 实现任务：收尾批（一键解绑 / 轮数回显 / 组件级测试 / 孤儿 API 标记）

## 背景
后端已就绪：`app_conversation_message` 新增 `iterations` 列（已在真实库生效），历史消息接口（getConversation messages 数组、旧通道 getSessionMessages）现返回 `iterations` 字段；绑定有效性端点 `GET /api/apps/{appId}/bindings/effectiveness` 已实现。工作目录 D:\Workspace\java\github\rag\fastrag\fastrag-frontend。G01：runtime.vue / AppChat.vue 零改动。

## 1 一键解绑（EffectivenessBar）
- 后端 unbind 端点已存在（自查 src/api/index.ts 的 unbindAppKb/unbindAppSkill/unbindAppTool/unbindAppMcp/unbindAppDb 及路径；缺哪个补哪个封装）
- `EffectivenessBar.vue`：失效项（exists=false）chip 加「解绑」小按钮——按 type 调用对应 unbind API（kb: DELETE knowledge-bases/{kbId} 绑定语义、tool/skill/mcp/db 各按现有 unbind 封装），成功后重新拉取有效性数据 + ElMessage.success；失败 ElMessage.error
- 注意 unbind 参数形状（有的按绑定行 id、有的按资源 id）——以 api/index.ts 现有函数签名为准，缺的封装按后端路径补

## 2 轮数回显（历史消息）
- `conversation/index.vue` `mapHistoryMessages`：读取 `m.iterations` 写入消息模型（types/conversation.ts 已有 iterations 字段）
- `MessageBubble.vue` summaryChips：历史消息 iterations 有值时同样显示「M 轮」（现有逻辑已支持，只需数据到位）；无值跳过

## 3 组件级测试（补齐小尾巴 3）
- 安装 devDep：`npm i -D @vue/test-utils`
- 新建 `src/views/conversation/components/MessageBubble.test.ts`（文件头 `// @vitest-environment jsdom`，jsdom 已装）：
  - 用 @vue/test-utils mount，全局 stub 三个子组件（ThinkingBlock/ToolCallRenderer/SourcesBlock 置为简单 div stub）+ Element Plus 组件按需 stub
  - 用例：①assistant 带 retrievalHint 渲染提示文本；②带 sources 渲染 SourcesBlock stub；③summaryChips：带 toolCalls+iterations 显示 chips、无则不显示；④cite 角标点击 emit open-sources（构造 content 含 [1] + sources 一条）
- 新建 `src/views/application/editor/components/EffectivenessBar.test.ts`（jsdom）：①exists=false 项渲染解绑按钮；②点击解绑调用对应 API 并刷新（mock api 模块）
- 若 mount 因 Element Plus 依赖受阻，允许改为「导出可测纯逻辑 + 浅挂载 stub」组合，目标是两条行为断言真实存在

## 4 孤儿 API 标记
- `src/api/index.ts`：optimizations 组（5 函数）与 auto-knowledge-update 组（3 函数）加统一注释：`// [孤儿标记 2026-09-26] 面板已下架（app-chat-optimize 3b），后端端点保留；恢复消费或删除待产品决策`
- 不删除函数

## 验收（报告必附）
1. `npx vitest run` 全绿（新增组件测试 + 既有 78 不回归）
2. `npx vue-tsc -b --force` 与基线 159 对比零新增
3. `npx vite build` 通过
4. G01 核对：runtime.vue / AppChat.vue 零 diff
5. 报告（中文）：逐项交付、自验结果、偏离及理由、遗留问题
