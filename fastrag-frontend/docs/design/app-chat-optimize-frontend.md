# 应用与对话体验优化（app-chat-optimize） — 前端设计

> 2026-09-25。输入：WeKnora 前端页面对比侦察（AgentEditorModal.vue 6675 行 / chat/index.vue 2169 行 / Input-field.vue 4236 行，file:line 见正文锚点）、FastRAG ui.md 特性 15/19 章。
> 性质：优化包（非新特性），分五步独立交付。Step 1/2/4 纯前端；Step 3 含小后端增补与一项用户裁决；Step 5 依赖其他特性链，不在本轮。

## 0. 范围定义

- **应用页** = `views/application/editor/index.vue`（9 项接线面板 + 6 个目录孤儿：GlobalConfig/KnowledgeUpdate/VmConfig/DebugChat/DialogTest/DialogOptimize）
- **对话页** = `views/conversation/index.vue` 会话中心（本特性 #19 新建）；runtime.vue 旧页**不动**（G01 延续）
- 优化原则：**只做 WeKnora 已验证有效且 FastRAG 后端接得上的项**；需要后端新管线（意图/改写/记忆/句柄协议）的登记 backlog，不硬上

## Step 1 渲染安全与单源（P0 · 安全 · 纯前端 · ~0.5 天）

**问题**：消息 markdown 用 marked 裸渲染 + `v-html`，**无 sanitize**——WeKnora 同位置有 DOMPurify（botmsg.vue:357-423）。LLM 输出/知识库内容含 `<script>`/`onerror` 即注入，是差距清单中唯一带风险等级项。

**设计**：
- 新增 `src/utils/markdown.ts` 单源渲染器：`marked`（保留 gfm/breaks 配置）→ `DOMPurify.sanitize(html, { FORBID_TAGS: ['script','style','iframe'], FORBID_ATTR: ['onerror','onclick','onload'] })`，导出 `renderMarkdown(md: string): string`
- 新增依赖：`dompurify`（3.x 自带类型）
- 收敛范围：**仅会话中心 MessageBubble.vue** 切单源；runtime/AppChat 旧页不动（G01），登记深化
- KaTeX/Mermaid：**可选扩展**（懒加载插件，按需触发）——默认不含，避免首包膨胀；登记 backlog

**验收**：`utils/markdown.test.ts`——`<script>alert(1)</script>`/`<img onerror>`/`javascript:` 三类样例消毒后无残留；正常 md（表格/代码块/链接）渲染不变。

## Step 2 引用与导出体验（P1 · 纯前端 · ~1 天）

**问题**：来源只有折叠块；WeKnora 有按文档分组（docInfo.vue:14-53）+ 引用抽屉（ChatReferencesDrawer）+ 会话导出（ChatHeader buildSessionMarkdown:61-64）。

**设计**：
- SourcesBlock 升级：来源按 **fileName 分组**显示；块尾「查看全部 N 条来源」→ 右侧 `el-drawer` 列表（文件/得分/跳 chunks 页定位），流内与历史双形态同抽屉
- **来源角标 [1][2] 不做**：需后端引用句柄协议（模型输出受控编号），纯前端伪造不可靠——登记后端 backlog（对齐 WeKnora citations.go 句柄方案）
- 复制会话 Markdown：聊天区头部「⋯」菜单新增「复制会话」——拼装消息历史（角色前缀 + 内容 + 来源文件名列表）入剪贴板，纯函数 `buildSessionMarkdown(messages)` 便于测试

**验收**：分组/抽屉/跳转可用；`buildSessionMarkdown` 单测（空消息/含来源/含思考三种输入）。

## Step 3 配置面实效化（P1 · 前后端小改 + 一项用户裁决 · ~1-2 天）

**问题**：配置-运行时闭环率是结构差距——WeKnora 14 区每项都接通；FastRAG editor 有 6 个孤儿面板，且绑定悬空 ID 只在运行时静默失败（design/app-center.md §4 已登记 bind 无存在性校验）。

**3a 绑定有效性预览（对齐 WeKnora AgentEditorModal:1186-1208「有效工具预览」）**
- 后端小增补：`GET /api/apps/{appId}/bindings/effectiveness` → `[{type, id, name, exists, enabled}]`（AppConfigService 新方法：四类绑定逐条 selectById 存在性 + 名称；kb 同）
- 前端：四个绑定面板顶部「有效性」chip 条——有效 n（绿）/ 失效 m（红，hover 显示"资源已删除"），失效项可一键解绑
- 验收：删库后打开面板，失效绑定红 chip + 可解绑

**3b 孤儿面板处置（⚠ 需用户裁决，三选一）**
现状：GlobalConfig（全局策略/敏感词模式）、KnowledgeUpdate（自动更新）**目录存在但菜单未挂**（editor/index.vue:24-46 无此二项）；VmConfig（虚拟机配置，agent 域桩）、DebugChat（非流式调试，被 AppChat 取代）、DialogTest（对话测试，REQ-G01 真实功能）、DialogOptimize（优化建议，analyze 为纯计数占位）。
- **方案 A（推荐）**：接线 2 个活的——GlobalConfig 挂「配置」组（后端 isSafetyEnabled 真实消费敏感词开关）、DialogTest 挂「对话」组（spec REQ-G01 真实功能）；下架 4 个死的——VmConfig/DebugChat/DialogOptimize/KnowledgeUpdate 移入 `docs/legacy/` 备份并删源码
- 方案 B：全部接线（菜单 15 项，含占位功能）
- 方案 C：全部下架（最小改动，丢 GlobalConfig/DialogTest）
- **下架记录（2026-09-25，方案 A 已裁决执行）**：`VmConfig.vue` / `DebugChat.vue` / `DialogOptimize.vue` / `KnowledgeUpdate.vue` 已删除（git 历史即备份；`editor/design/DESIGN-editor-cleanup.md` 等为历史设计稿留存）；GlobalConfig 接线为「配置 · 安全策略」（key=global，icon=Lock），DialogTest 接线为「对话 · 对话测试」（key=dialog-test，icon=Checked）；关联 API 函数（optimizations / auto-knowledge-update 系列）按仓规保留于 `src/api/index.ts` 单文件

**3c 提示词模板库（轻量对齐 WeKnora PromptTemplateSelector:180-237）**
- 前端内置 6 个常用模板常量（RAG 问答/制度查询/多轮客服/摘要/对比分析/表格解读）下拉插入 PromptConfig；后端模板实体登记 backlog

## Step 4 会话体验增强（P2 · 纯前端 · ~0.5 天）

- 会话列表**日期分组**：今天 / 近 7 天 / 更早 分节头（对齐 WeKnora menu.vue:117-185 分桶）
- 以下登记 backlog 不做：追问推荐（需生成模型）、@提及扩展 MCP/技能/文件（后端 B02 死字段未消费）、会话重命名（v1 裁决维持）

## Step 5 依赖链项（不在本轮，锚定既有链）

steer 插话 / fork / rewind（agent 链 StreamManager）｜附件上传与预览抽屉（file-ingestion 临时文档链）｜检索参数 App 级面板（retrieval 链）｜长期记忆｜web 搜索。

## Parent Trace（步骤 → WeKnora 锚点 → 现状证据）

| 步骤 | WeKnora 锚点 | FastRAG 现状证据 |
|---|---|---|
| Step 1 | botmsg.vue:357-423（DOMPurify） | MessageBubble.vue v-html 裸渲染 |
| Step 2 | docInfo.vue:14-53、ChatHeader.vue:61-64 | SourcesBlock.vue 单折叠块 |
| Step 3a | AgentEditorModal.vue:1186-1208 | 四绑定面板无有效性校验（design/app-center §4） |
| Step 3b | —（WeKnora 无占位面板可对齐） | editor/index.vue:24-46 菜单 9 项 vs 目录 15 面板 |
| Step 3c | AgentEditorModal.vue:180-237 | PromptConfig 纯文本框 |
| Step 4 | menu.vue:117-185 | SessionListPanel 平铺列表 |
