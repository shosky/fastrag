# 任务书：第 5-8 批特性链证据调研（2026-09-21）

## 背景

`fastrag-frontend`（Vue3 + TS，路径相对本目录）IDD 特性链收尾批次。每特性按统一框架返回结构化 markdown（全部 file:line 证据；查不到标 GAP；CERTAIN/INFERRED/GAP 分层）：

`## 页面与路由`（路由/入口/页内结构）｜`## 任务清单`（动作→成功→失败恢复）｜`## 状态与反馈`（loading/empty/error 实况）｜`## API 消费`（函数+URL+字段消费点）｜`## 交互要点`（伪代码级）｜`## 异常与疑点`（stub/mock/死代码/不一致）

**硬性边界：只读 `src/**` 与 `docs/feature-map.md` 对应特性小节；不读仓库根 docs/。**

---

## A. qa-pairs（问答对管理）+ publish-review（发布、版本与知识审核）

**qa-pairs 文件**：`src/views/knowledge/detail/components/{QaPanel.vue 291, QaImportDialog.vue 363}`。专项：CRUD/确认生效/Excel 导入交互；getFiles mock 消费（死 import?）；api qa-pairs 组。

**publish-review 文件**：`src/views/knowledge/detail/components/PublishPanel.vue`（779）、`src/views/knowledge-review/` 7 页（flows 494/flow-design 582/listeners 405/compliance 156/reports 161/quality 242/review-management 618，孤儿路由但走真实 api）。专项：版本流转（草稿→发布→下线→回滚）UI；定时发布；审核流（提交/批准/驳回）；7 页族各自功能与真实 API 消费；孤儿入口现状。

## B. platform-config（模型与平台配置）+ tools-skills-mcp（工具/技能/MCP/外部数据库）

**platform-config 文件**：`src/views/admin/platform/{model-management.vue 567, api-keys.vue 177}`、`src/views/admin/system/` 5 页（kb-config 253/general-settings 362/sensitive-words 231/dictionary 246/terminology 342）、`src/views/answer-kb/templates.vue`（108，整页 mock）、`src/stores/system.ts`（49）。专项：模型 CRUD/连通性测试交互；五页数据治理各自功能；api-keys 页实管什么（字典?）；templates 整页 mock 实况；api 组（models/config/dictionaries/sensitive-words/terminology/notifications）。

**tools-skills-mcp 文件**：`src/views/application/{my-tools.vue 312, tool/*, mcp-services.vue 341, mcp/*, skill-management.vue 396, skill/Skill*}`, `database-management.vue`（924）。专项：工具注册/测试代理；技能文件树管理（Markdown+frontmatter）；MCP parse-url/工具发现；外部 DB tables/test-conn/query（**直接执行 SQL** 的 UI 形态）；mock 类型消费点；api 四组端点消费。

## C. workflow（工作流编排）+ agent（智能体运行时）

**workflow 文件**：`src/views/application/workflow-manage.vue`（525）、`src/types/workflow.ts`（108）。专项：工作流 CRUD/发布 UI；画布编排形态（有无画布库?）；执行/调试/测试用例/监控 UI 有无；33 端点消费 vs 单页差距实况。

**agent 文件**：`src/views/application/editor/components/VmConfig.vue`（251）、AppChat/DebugChat 对话组件（editor/components 内）。专项：智能体配置/运行/事件流在前端的实际形态；独立管理页是否存在（INFERRED）；api agent 消费。

## D. app-center（应用中心）

文件：`src/views/application/{index.vue 258, editor/index.vue 250, app-config.vue 578, runtime.vue 1127}`、`editor/components/` 15 面板（AppChat 823/BasicConfig 344/KnowledgeConfig 315/ChatLogs 303/DebugChat 286/DatabaseConfig 345/VmConfig 251/MemberConfig 243/McpConfig 212/DialogTest 214/ToolConfig 181/SkillConfig 178/GlobalConfig 125/KnowledgeUpdate 74/DialogOptimize 77）、`ToolCallRenderer 199/ThinkingBlock 106`、`src/composables/{useAppChatStream.ts 378, useConversation.ts 174}`、`editor/design/` 11 篇 DESIGN-*.md（仅登记篇目）。专项：应用 CRUD/模板；15 面板各自配置域；SSE chatStream 消费（事件类型/tool_call 增量/断线）；会话管理；runtime 双路由复用实况；api apps 组 ~89 端点按面板聚类。

## E. operation-analytics（运营分析与系统监控）

文件：`src/views/operation/` 5 页（kb-analytics 195/model-monitor 181/feedback 564/qa-detail 147/retrieval-analysis 349——retrieval-analysis 已在批次 4 调研可略）+ `operation/components/{MetricCards 58, WordCloud 40, RankList 38}`、`src/views/robot-operation/` 4 页（faq-analysis 60/multi-turn 60/intent 59/data-mining 145，伪数据瘦壳）、`src/views/admin/audit/system-log.vue`（162）。专项：各页数据源（真实 api vs 伪造）；feedback 管理（回复/统计）；robot-operation 4 页伪填充实况；api 消费（feedback/notifications/audit/home/analytics/data-mining/model-monitor）。
