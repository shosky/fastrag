# 应用中心 — Implementation Document（app-center-frontend）

> 由 /idd-implement 于 2026-09-21 生成（onboard 特征化模式）。

## 1. Code File Map

```
src/views/application/
├── index.vue              — 应用列表（258）
├── editor/index.vue       — 编辑器壳（250：两级菜单挂 9 面板）
├── app-config.vue         — 5 Tab 配置（578；孤儿路由）
├── runtime.vue            — 对话运行时（1127；双路由复用）
└── editor/components/     — 15 面板：9 接线 + 6 孤儿（DebugChat/VmConfig/DialogTest/GlobalConfig/KnowledgeUpdate/DialogOptimize）
src/components/{ToolCallRenderer 199, ThinkingBlock 106} — 共享渲染件（runtime+AppChat）
src/composables/useAppChatStream.ts — SSE 唯一解析点（378）
src/api/index.ts         — apps 组 89 端点
```

## 2. Interface Implementations

### SSE 对话（契约核心）

**Logic Flow:**
```
sendMessage(appId, q, sessionId, 8 回调):
1. fetch POST chat/stream（Bearer + AbortController + body {query,sessionId}）
2. reader 循环: '\n\n' 分帧 → event:/data: 解析
3. switch 9 事件（message replace 整段替换/thinking/tool_call 合并 arguments/
   tool_result 回填/sources/runtime 专属/agent_state 悬空/end 取终值+cancel/
   error cancel/init 未知忽略）
4. AbortError → onEnd(fullContent,{aborted:true})
5. 无重连（GAP）
```

### KnowledgeConfig diff 保存

**Logic Flow:**
```
保存: selected（勾选）vs boundKbIds
  toRemove → findBindingByKbId（重拉 bindings 找绑定 id）→ unbindAppKb
  toAdd → bindAppKb({kbId, priority:0})
  任一失败 → ElMessage.error + loadBoundKbs 回滚
```

### runtime 会话管理

```
selectApp → 清会话 → 并发 loadSessions + loadGreeting(basic.greeting)
发送 → ensureSession（无活跃自动建）→ 乐观插 user+空 assistant → 8 回调增量
编辑重发 → 回填输入框 → splice 原消息及之后 → 重发（新消息，未调 updateAppMessage 死导出）
滚动: 距底<80px 跟随；切会话/发送强制滚底
```

### 页面登记表

| 能力 | 要点 | 证据 |
|---|---|---|
| BasicConfig | 串行 7 API；apiKey 防清空 | :116-138,233-236 |
| 导入导出 | Blob 下载 + 动态 file input（basic/dialog/触发器/KB 绑定/案例 CSV） | app-config:27-45 等 |
| 触发器测试 | 结果 JSON 原样 alert | :110-139 |
| ChatLogs | 筛选/分页/详情/删除/CSV | :43-123 |

## 3. Decisions & Deviations

- **6 孤儿面板**（INFERRED-1）与 **app-config 孤儿路由**（INFERRED-5）：与 11 篇 DESIGN-*.md 中 3 篇（debug-chat/ui-config/vm-config）现状脱节。
- **伪数据回填**：GlobalConfig 变量空填 3 条硬编码、KnowledgeUpdate 日志空填 3 条（含未来日期）——查询失败与真空数据不可区分（INFERRED-9）。
- **MemberConfig 死取数**：loadMembers 拉人员仅落局部变量，实际数据源是配置 JSON（INFERRED-3）。
- **useConversation.ts 不属本链**：消费方是 debug.vue（死入口页），其回答为前端规则拼接模拟（非真实生成）——登记待处置。
- 无 contract↔代码矛盾（特征化模式）。

## 4. Test Evidence

tdd 全局 blocked。解阻后优先切片：SSE 帧解析与 9 事件分发（共享 agent 链）、绑定 diff 计算、会话管理状态机（流式锁/回退）。

## 5. Implementation Trace

```
TASK-001 → 列表 → index.vue:65-187
TASK-002 → 编辑器 9 面板 → editor/index:24-148 + 各面板
TASK-003 → runtime → :56-331,333-545
TASK-004 → app-config 五 Tab → :27-476
TASK-005 → SSE 契约 → useAppChatStream:98-360
```
