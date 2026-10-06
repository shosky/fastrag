# 智能体运行时 — Frontend Spec（agent-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 17 证据包；术语 ← `CONTEXT.md`（智能体）。

## 1. Scope

覆盖智能体的**前端实际形态**：应用对话运行时（runtime/AppChat 共享 SSE 契约）、agent 运行参数（BasicConfig maxSteps/maxTurns/retryTimes）、VM 配置（孤儿组件登记）、agent_state 事件（已解析无消费）。**核心结论（CERTAIN）：无独立 agent 管理页、api/index.ts 零 agent 端点、权限树 `api:agent:*` 为纯后端权限项**（feature-map「INFERRED 前端面缺失」升级）。

## 2. User Tasks

- **TASK-001 对话运行时** — runtime.vue（双路由复用）+ AppChat：会话生命周期/发送（Enter）/停止（abort 标「(已停止)」）/重试（回滚重发；runtime 另支持消息编辑重发）；SSE 9 类事件消费（与 app-center 链共享契约，见 contract/ui.md 特性 15/17）〔‹CERTAIN›〕。
- **TASK-002 运行参数** — BasicConfig「Agent 最大执行步数」（默认 15）/maxTurns/retryTimes → saveAppMaxSteps/MaxTurns/RetryTimes〔BasicConfig:279-292,42,106 ‹CERTAIN›〕。
- **TASK-003 VM 运行环境（不可达）** — VmConfig：启用开关/镜像 5 选/CPU/内存/磁盘/网络/自动关机+超时/预装包 tag/环境变量键值表（保存过滤空行）→ `advancedOptions.vm`；**孤儿组件不可达**（INFERRED-1）。
- **TASK-004 agent_state 事件** — 解析已实现（`{todos,artifacts}`），**无消费方传回调 → 无 UI 呈现**（useAppChatStream:202-212 ‹CERTAIN›，INFERRED-2）。

## 3. Pages & Routes

无独立路由。runtime 双路由复用（query.appId 驱动，:id 参数被忽略——app-center 链记录）；AppChat 挂编辑器菜单 chat。

## 4. State Matrix

共享 app-center SSE 契约状态矩阵；补充：流式中禁止切换/删除会话（AppChat:163-166,176-198 ‹CERTAIN›）。

## 5. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| chat stream | fetch POST `/apps/{id}/chat/stream`（手写 Bearer；无重连 GAP） | TBD — pending API Contract |
| 会话四件套 | api:822-837 | TBD |
| 运行参数 | saveAppMaxSteps/MaxTurns/RetryTimes（api:810-816） | TBD |
| ~~agent 端点~~ | **不存在** | — |
| VmConfig | getAppBasicConfig.advancedOptions.vm / saveAppAdvanced（api:1652,1905） | TBD（孤儿，备查） |

## 6. Responsive & Accessibility

同 app-center 对话形态 [GAP]。

## 7. Acceptance

- [ ] 运行时对话全链路（共享 app-center 验收）
- [ ] maxSteps/maxTurns/retryTimes 保存生效（默认 15）
- [ ] 现状验收：无独立管理页、VmConfig/DebugChat 不可达、agent_state 无 UI（INFERRED 裁决前不变）

## 8. INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。以下原清单保留作对照。

### 原清单（已被裁决覆盖）

INFERRED-1 VmConfig/DebugChat 孤儿组件处置（接线/删除）？INFERRED-2 agent_state 事件（todos/artifacts）无 UI——接 UI or 后端停发？INFERRED-3 AppChat 回复完成静默发站内通知（污染通知中心）——去留？INFERRED-4 token 读取双轨（ais_token 手工 vs storage 工具）——统一？INFERRED-5 独立 agent 管理页是否立项（后端有 AgentController 三控制器而前端零消费）？
