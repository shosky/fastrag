# 应用中心 — Frontend Spec（app-center-frontend）

> 由 /idd-spec 于 2026-09-21 生成（onboard 特征化模式）。Parent Trace ← `docs/feature-map.md` 特性 15 证据包；术语 ← `CONTEXT.md`（应用/工作流）。

## 1. Scope

覆盖应用列表、编辑器（9 已接线面板）、app-config 页（**孤儿路由**）、runtime 对话页、共享渲染件。**排除**：6 个孤儿面板（DebugChat/VmConfig/DialogTest/GlobalConfig/KnowledgeUpdate/DialogOptimize——全有完整实现但零 import，INFERRED-1 处置）；SSE 契约细节（与 agent 链共享 contract 特性 17）；workflow 编排（workflow 链）。

## 2. User Tasks

- **TASK-001 应用管理** — `/application` 卡片网格（类型三选一 ChatBot/Editor/LiteAgent 创建）+ 搜索/标签筛选（标签来自已有应用聚合，非独立体系）；卡片：运行/复制 ID/删除（confirm「同步清理历史记录」）；卡体点击进编辑器。**无模板入口**（getAppTemplates 死导出）（index.vue:65-187 ‹CERTAIN›；INFERRED-2）。
- **TASK-002 编辑器配置** — 左侧两级菜单（配置 7 项/对话 2 项）挂 9 面板：BasicConfig（五段表单一次保存**串行调 7 个 API**，模型参数 apiKey 非空才进 payload 防清空）/KnowledgeConfig（左分类右列表勾选绑定，保存 diff 出增删逐条 bind/unbind，失败回滚）/SkillConfig/ToolConfig/McpConfig（三类勾选绑定同 diff 模式）/DatabaseConfig（dbId/alias/allowedTables，类型色点+连接状态）/MemberConfig（成员持久化在 basic.advancedOptions.members JSON——**非独立实体**，INFERRED-3）/ChatLogs（关键词/评分/日期筛选+分页+详情+删除+CSV 导出）/AppChat（SSE 对话调试）（editor/index:24-148 ‹CERTAIN›）。
- **TASK-003 对话运行时** — `/application/runtime`（query.appId）：应用切换（清会话+拉问候语）/会话管理（流式中禁切换删除；删除当前回退第一个）/发送（乐观插两条消息，首条截 30 字作标题）/停止/编辑重发（splice 原消息及之后）/消息删除与点赞点踩/知识来源+来源图片（Bearer blob 缓存）/溯源跳 chunks 页/滚动跟随（距底<80px）（runtime:56-331 ‹CERTAIN›；`:id/runtime` 路径参数被组件忽略，INFERRED-4）。
- **TASK-004 app-config 页（孤儿路由，无站内入口）** — 5 Tab：基础配置（导出/导入 JSON）/对话设置（导出/导入）/全局策略（触发器 CRUD+启停+测试运行（结果 JSON alert）、变量 CRUD、未匹配策略、策略启停）/知识库绑定（增删+JSON 导入导入）/对话测试（案例 CRUD+CSV 导出）（app-config:27-287,294-476 ‹CERTAIN›；INFERRED-5）。
- **TASK-005 SSE 对话契约** — fetch POST chat/stream（Bearer/AbortController）→ 9 类事件（message replace=敏感词修正整段替换/thinking/tool_call 同 id 合并 arguments/tool_result/sources/end 不一致取 end.content/error/init 忽略/未知忽略）；AbortError→onEnd(aborted)；**无断线重连**（useAppChatStream:98-360 ‹CERTAIN›；GAP）。

## 3. Pages & Routes

| 路由 | 可达性 |
|---|---|
| `/application` | Sidebar:126 ✅ |
| `/application/:id/editor` | 卡片点击 ✅ |
| `/application/:id/config` | **无站内入口（孤儿路由）** |
| `/application/runtime` | Sidebar:127 + 卡片播放 ✅ |
| `/application/:id/runtime` | 无站内入口（复用组件，:id 被忽略） |

## 4. State Matrix

| 数据流 | loading | empty | error | 证据 |
|---|---|---|---|---|
| 应用列表 | v-loading | el-empty「暂无应用」 | catch 显 e.message | index:120-157 |
| runtime | 全局 spinner | 三态（loading/未选应用/开场白卡） | **错误写进气泡**「抱歉，AI 服务暂时不可用：{msg}」 | runtime:333-462,185 |
| 面板保存 | 按钮 loading | 面板内 el-empty | ElMessage.error | BasicConfig:133-135 等 |
| SSE | streaming 光标/isThinking | — | 气泡错误文案；无重连 | useAppChatStream |
| 编辑器加载 | — | — | catch 静默 | editor/index:77-79 |

## 5. Data Mapping

| 视图数据 | 消费代码（CERTAIN） | API Contract |
|---|---|---|
| 应用 CRUD | getApps/createApp/deleteApp/getAppDetail（api:757-775） | TBD — pending API Contract |
| 会话四件套/消息 | api:822-855（delete/feedbackAppMessage 仅 runtime） | TBD |
| M16 配置 35 端点 | basic/dialog/triggers/global-policy/variables/kb-bindings/databases/dialog-tests（api:1652-1756） | TBD |
| 五类绑定 12 端点 | Skill/Tool/Mcp/Database Config（api:1761-1796） | TBD |
| M16 扩展 15 端点 | advanced 导入导出/dialog 导入导出/policy/unmatched（api:1905-1949） | TBD |
| 对话记录 4 端点 | ChatLogs（api:2018-2029） | TBD |
| ~~死导出 11 个~~ | getAppTemplates/saveAppConfig/updateApp/runApp(仅孤儿)/updateAppMessage/getAppPublishRecords/getAppMonitor/getAppDebugInfo/saveAppDebugConfig/getAppWorkflowConfig/saveAppWorkflowConfig（INFERRED-6） | — |
| ~~publishApp~~ | **app-center 内无发布 UI**（唯一消费方在 knowledge-review/review-management）（INFERRED-7） | — |

## 6. Responsive & Accessibility

runtime 滚动跟随（距底 80px）是唯一交互细节；断点 [GAP]。

## 7. Acceptance

- [ ] 应用创建三类型/删除 confirm/卡片导航正确
- [ ] 编辑器 9 面板加载与保存（BasicConfig 串行 7 API；KnowledgeConfig diff 回滚）
- [ ] runtime：会话管理（流式锁）/发送停止重试编辑重发/来源图片/溯源跳转/滚动跟随
- [ ] SSE 九事件按契约消费；停止标记「(已停止)」；错误入气泡
- [ ] app-config 五 Tab 现状可用（孤儿路由现状，INFERRED-5 裁决前不变）

## 8. INFERRED 裁决（✅ 已全量裁决 2026-09-21，用户授权代决）

> 逐条结论见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md)。要点：六孤儿面板保留登记（推荐优先接线 DialogTest「录制测试」，需产品验收）。以下原清单保留作对照。

### 原清单（已被裁决覆盖）

INFERRED-1 **6 孤儿面板处置**（DebugChat/VmConfig/DialogTest(含录制测试)/GlobalConfig/KnowledgeUpdate/DialogOptimize——接线（菜单加项）/删除）？INFERRED-2 模板系统死导出（getAppTemplates 无 UI）——立项 or 清理？INFERRED-3 成员存 basic JSON 非独立实体——后端建模 or 维持？INFERRED-4 `:id/runtime` 参数被忽略（AppRuntimeOld）——删路由 or 改组件读 params？INFERRED-5 app-config 孤儿路由——接线（编辑器加菜单）or 并入 editor？INFERRED-6 死导出 11 个清理？INFERRED-7 应用发布 UI 缺位（publishApp 唯一消费在知识审核页）——补发布入口？INFERRED-8 SSE 无重连——补？INFERRED-9 GlobalConfig/KnowledgeUpdate 伪数据回填（空时填硬编码示例含未来日期）——去留？INFERRED-10 AppChat 编辑器调试对话发站内通知（与 agent 链 INFERRED-3 同源）？
