# 智能体运行时 — Implementation Document（agent-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/application/runtime.vue            — 运行时对话页（1127；双路由复用；agent-view 类名根）
src/views/application/editor/components/
├── AppChat.vue                              — 编辑器内 SSE 对话（823；回复完成发站内通知）
├── VmConfig.vue                             — ~~孤儿~~（251，VM 环境配置，不可达）
└── DebugChat.vue                            — ~~孤儿~~（286，runApp 非流式调试，不可达）
src/composables/useAppChatStream.ts          — SSE 唯一解析点（378；9 类事件）
src/components/{ToolCallRenderer 199, ThinkingBlock 106} — 与 app-center 共享
```

## 2. Interface Implementations

### 前端 agent 形态（CERTAIN 结论）

**Logic Flow:**
```
独立 agent 管理页: 不存在（无路由；api/index.ts 零 agent 函数；menu-perm-tree:15「暂无对应菜单页面」自证）
运行时: 应用对话（apps 组会话四件套 + SSE chatStream）——见 app-center 链共享契约
agent 参数面: BasicConfig maxSteps(默认15)/maxTurns/retryTimes（「Agent 最大执行步数」标注）
VM 面: VmConfig（advancedOptions.vm：镜像 5 选/CPU/内存/磁盘/网络/自动关机/预装包/环境变量）——孤儿不可达
```

### SSE 事件消费矩阵（agent 相关）

| 事件 | runtime | AppChat | 备注 |
|---|---|---|---|
| message/thinking/tool_call/tool_result/end/error | ✅ | ✅ | 共享契约 |
| sources | ✅（知识来源+图片） | ❌ 不传回调 | 两界面能力不对等（agent 域外记录） |
| agent_state `{todos,artifacts}` | ❌ | ❌ | **解析已实现、无消费方**（INFERRED-2） |

## 3. Decisions & Deviations

- **feature-map「INFERRED 前端面缺失」升级 CERTAIN**：无路由 + 无 API 组 + 权限表自证三重证据。
- **孤儿组件**：VmConfig/DebugChat 整体不可达（与 app-center 链孤儿清单一致）；DebugChat 硬编码「Powered by AIS」。
- **AppChat 回复完成静默发站内通知**（createNotification，空 catch）——高频对话污染通知中心（设计疑点，INFERRED）。
- **token 读取双轨**：runtime 手工解析 `ais_token` vs storage 工具 `token`——key 不一致（低危疑点）。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked（共享 app-center SSE 契约 fixtures）。

## 5. Implementation Trace

```
RUNTIME → runtime.vue + useAppChatStream（共享 app-center 契约）
PARAMS → BasicConfig maxSteps/maxTurns/retryTimes（:279-292）
VM → VmConfig（孤儿登记）
STATE 事件 → useAppChatStream:202-212（无消费方）
```
