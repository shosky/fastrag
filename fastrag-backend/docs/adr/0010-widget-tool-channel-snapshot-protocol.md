---
name: Widget 卡片经工具通道传输与快照内嵌持久化
summary: 卡片数据走 render_widget 工具结构化通道并随 toolCalls 落快照，不新增 SSE 事件与消息列。
type: adr
id: ADR-0010-WIDGET-TOOL-CHANNEL-SNAPSHOT-PROTOCOL
status: draft
updated: 2026-10-31
hash: "572a36dd3966"
summaryHash: "572a36dd3966"
---

## 背景 {#context}

Widget 卡片数据需要一条「智能体产出 → 前端渲染 → 历史回看」的传输与持久化通道（spec REQ-WG-D03/D06/D07）。两条候选路径：

1. **专用 `widget` SSE 事件 + 消息级 widgets JSON 列**：事件词汇显式、卡片生命周期独立建模；但引擎事件面（`sendSseEvent` 单点 + 既有 9 类事件词汇）被两条前端流全量消费，新增事件要同时动引擎、两条流处理、消息持久化三处，且与既有消息模型形成双源。
2. **复用既有工具结构化通道**：`ToolResult.structured` 已自动进 `tool_result` SSE 的 `data` 键（AgentEngine.java:438-441）并随 toolCallRecords 落 `toolCalls` 列（:470-478）；前端 `toolDisplay.ts` 已按工具名/display_type 路由专用渲染器（database_query/knowledge_chunks 先例）——「后端透传 JSON、前端专卡渲染」的模式已被验证两次。

历史稳定要求快照在 emit 时刻定格（快照 #7：资产修改不影响已发出消息），这要求模板随消息持久化而非运行时按 id 活取。

## 决策 {#decision}

render_widget 以**独立 `type="widget"` 注册**（`builtin` 型已被 InstallSkillToolExecutor 占用且工厂按 type `findFirst`，撞型路由不确定）；卡片渲染快照五要素（widgetId/widgetKey/name/template/data）经 `ToolResult.structured` 传输、随 toolCallRecords 落 `toolCalls` 列。**零新增 SSE 事件类型、零新增消息级卡片列**；同 widgetKey 替换为前端语义（同消息流内去重），历史回放经 ToolCallRenderer 重放天然同构。

## 后果 {#consequences}

- **得到**：引擎与协议面零改动；实时与历史同构（同一渲染路由）；消息模型无双源漂移；事件词汇不被 UI 概念污染。
- **代价**：工具调用语义承载了 UI 概念——前端须对该工具隐藏 calling 态 chip（STATE-207）；卡片体积计入 toolCalls 列（32KB 护栏 REQ-WG-D05 缓解）；tool_call「calling」态的空窗由轻量占位弥补。
- **显式扩展点**：未来若出现非工具触发的卡片（编排层主动注入、工作流节点产出），工具通道不再覆盖，届时升级专用事件面——本 ADR 的复用决策在该场景下重新评估。
