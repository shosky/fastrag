# 智能体运行时 — Frontend Architecture Design（agent-frontend）

> 由 /idd-design 于 2026-09-21 生成（onboard 模式）。上游：`docs/spec/agent-frontend.md`。

## 1. Page Map

| PAGE | 路由/宿主 | 可达性 |
|---|---|---|
| 运行时对话 | `/application/runtime`（query.appId）+ `/:id/runtime`（AppRuntimeOld，:id 被忽略） | Sidebar「应用运行」/卡片运行钮 |
| AppChat | 编辑器菜单 chat | 随编辑器 |
| VmConfig/DebugChat | 无挂载 | **不可达** |

## 2. Page/Layout/Component Map

```
runtime.vue（agent-view 类名根）:
┌──────────────────────────────────────────────┐
│ 顶栏: [返回][会话▾][侧栏开关]                    │
│ 消息流: 用户/助手气泡 + ThinkingBlock(黄,自动收起) │
│         + ToolCallRenderer(工具卡:状态/耗时/JSON) │
│         + 知识来源(sources)+来源图片(blob 缓存)   │
│ 输入区: [附件][模型▾][应用▾][发送/停止]           │
│ 右侧: 会话列表面板                               │
└──────────────────────────────────────────────┘
AppChat: 同构精简（无 sources/编辑重发；多站内通知）
```

组件判定：ToolCallRenderer/ThinkingBlock 双消费者（runtime+AppChat，成立）；VmConfig/DebugChat 孤儿（不深化）。

## 3. State Ownership

全部共享 app-center 对话契约（消息流/流式光标/会话列表——所有者表见 contract 特性 15）。agent 特有：
| STATE | 所有者 |
|---|---|
| maxSteps/maxTurns/retryTimes | server（basic config）→ BasicConfig 表单 |
| agent_state todos/artifacts | server 事件 → **无接收方**（悬空） |
| vm 配置 | server（advancedOptions.vm）→ 孤儿组件 |

## 4. Data Flow

```
SSE: fetch POST chat/stream（AbortController）→ useAppChatStream switch 9 事件
     → runtime/AppChat 回调（8 路/6 路）→ 气泡/思考块/工具卡增量渲染
参数: BasicConfig 五段表单一次保存串行 7 API（含 maxSteps/retryTimes）
命名转换点: 同 app-center（useAppChatStream 唯一 SSE 解析点；ToolCallRenderer JSON 美化）
```

## 5. Visual System

ThinkingBlock 黄色折叠（isThinking/forceCollapsed 自动收起）；工具卡状态 tag+耗时 ms；streaming 光标动画。断点无。

## 6. Parent Trace

- 三重证据升级 CERTAIN → spec §1 核心结论
- sources/agent_state 不对等 → spec INFERRED-2
- VM 孤儿 → INFERRED-1
