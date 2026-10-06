# 新建对话与会话中心（new-conversation） — 前端需求规格

> 2026-09-25 由后端链（#19 spec/design/contract 已齐，见 fastrag-backend/docs/）派生前端单侧规格。产品决议见 `fastrag/.tmp/idd-new-conversation-grill-decisions.md`；后端接口契约 = fastrag-backend `contract/interfaces.md ## new-conversation` 节（HTTP 路径以后端 springdoc 实现为准，本文件 API 依赖为设计意图）。
> 对齐目标：WeKnora session-first 对话体验。

## 1. Scope

会话中心页（按用户全量的会话列表 + 聊天窗）、输入框范围选择（知识库多选 + 模型）、消息引用展示（流内 + 历史双形态）、停止生成、LLM 起标题、置顶/清空/批量删除，以及从应用卡片带预设发起新建对话。
**显式排除**：编辑器内「对话调试」Tab（AppChat.vue，走旧通道，行为不动）；runtime.vue 旧页内部逻辑重写（仅处置入口，见 TASK-009/INFERRED-2）；附件上传、fork/rewind、web 搜索（后端 Out of Scope 同步排除）；正文引文角标 [1][2]（登记后续，依赖 Markdown 渲染方案评估）。

## 2. User Tasks

- **TASK-001 新建对话**：作为用户，我在会话中心点「新建对话」，可选地挑选知识库范围（多选）与模型，直接输入问题即开始对话，无需预配应用；直聊还可选挂工具/MCP/技能（B02 演进：随 stream 请求体 toolIds/mcpIds/skillIds 装配执行上下文）。〔后端 REQ-A01/C01；新建不传参亦合法〕
- **TASK-002 会话中心列表**：我打开会话中心，看到我的全部会话——置顶的排最前，其余按最近更新倒序，每条显示标题、来源标记（「直聊」或「来自应用 X」）、消息数与更新时间，支持分页。〔REQ-A03、NFR-01〕
- **TASK-003 重开恢复范围**：我切回某个会话，输入框自动恢复上次使用的知识库范围、模型与应用预设，可以直接继续提问。〔REQ-A04〕
- **TASK-004 流式对话**：我发送问题后，实时看到回答增量、思考过程折叠块、工具调用卡片与「知识来源」折叠块，体验与现有应用运行一致。〔REQ-C02；复用 useAppChatStream 事件词汇〕
- **TASK-005 停止生成**：回答过程中我可点停止，已生成的部分保留并落库，气泡标注「（已停止）」，之后可继续提问。〔REQ-E02〕
- **TASK-006 LLM 起标题**：我可对会话触发「起标题」，标题由模型生成并更新列表；失败时回退为首问截 30 字（现状行为），不报错。〔REQ-E01〕
- **TASK-007 会话管理**：我可置顶/取消置顶会话、清空某会话的全部消息（会话保留）、按多选批量删除会话（级联删消息，需确认）。〔REQ-E03/E04/E05〕
- **TASK-008 历史引用回看**：我重开历史会话，每条回答下方有「知识来源」折叠块（来自消息引用），点「原文」可跳转知识库分片页定位。〔REQ-F02〕
- **TASK-009 带预设新建对话**：我在应用卡片点「▶运行」，进入会话中心并预挂该应用为预设（输入框显示预设标记，聊天按应用配置执行）；也可随时移除预设转为直聊。〔REQ-D01/D02；现卡片行为 `index.vue:115` 跳 runtime ‹CERTAIN›，改为跳会话中心带 appId〕
- **TASK-010 消息操作继承**：复制回答、点赞点踩、删除消息、重新编辑发送，与现有运行页一致。〔继承 runtime.vue:267-308 交互 ‹CERTAIN›〕

## 3. Pages & Routes

| 页面 | 路由 | 可达性 | 状态 |
|---|---|---|---|
| 会话中心 | `/conversation`（name `ConversationCenter`） | **新**：Sidebar 新增菜单入口；应用卡片「▶运行」改跳此页（带 `?appId=`） | 新建 |
| 应用运行（旧） | `/application/runtime` | 保留路由兼容旧链接；**菜单入口移除**（INFERRED-2） | 不重写 |
| 应用编辑器·对话调试 | `/application/:id/editor` | 不动（走旧通道 AppChat.vue） | 不动 |

路由遵循现状守卫（未登记权限键默认放行，`router/index.ts:72-105` ‹CERTAIN›）——会话中心后端仅要求登录（REQ-G04），不挂新权限键。

## 4. State Matrix

### 4.1 会话列表（左栏）

| 态 | 触发 | 显示 |
|---|---|---|
| loading | 首屏/翻页 | 骨架条 ×5 |
| empty | 无任何会话 | 引导文案「开始你的第一段对话」+ 新建按钮 |
| error | 接口失败 | 错误行 + 重试 |
| 分页 | total > pageSize | `usePagination` + `${block}__pagination` 模板（仓规） |

### 4.2 聊天区

| 态 | 触发 | 显示 |
|---|---|---|
| 无选中会话 | 新建未发首条 | 空态引导：KB 范围提示 + 输入框聚焦（沿 runtime ensureSession 模式 `runtime.vue:311-315` ‹CERTAIN›，改为随首条消息建会话） |
| 历史加载 | 选中会话 | loading 骨架 → 消息按时间正序；assistant 消息渲染 references 来源块 |
| 流式中 | sendMessage | 输入框禁发 + 停止按钮；thinking/tool_call/sources 事件同现有渲染；abort 标注「（已停止）」〔`runtime.vue:202-205` 交互 ‹CERTAIN›〕 |
| 历史空 | 清空消息后 | 「会话已清空，继续提问」空态 |
| error | SSE error 事件 | error 气泡 + 重试（沿现状） |

### 4.3 输入框范围选择

| 态 | 触发 | 显示 |
|---|---|---|
| KB 选择器 loading/empty | 打开弹层 | loading 骨架 / 「暂无知识库」+ 引导去建库 |
| 模型下拉 loading/empty | 打开下拉 | loading / 「暂无在线模型，请先在模型管理配置」（过滤 `status==='online'`，沿 `BasicConfig.vue:55-71` 惯例 ‹CERTAIN›） |
| 预设挂载 | 带 appId 进入 / 挂载 | 输入框上方预设 chip「预设：{应用名} ×」；移除即转直聊（D02：挂预设时 KB 多选禁用并提示「知识库范围由应用决定」） |
| 无预设 | 直聊 | KB 多选 + 模型可用 |

## 5. Data Mapping

| API（设计意图路径，以 springdoc 为准） | VM | 说明 |
|---|---|---|
| `POST /conversations` | 列表项 + 激活会话 | body 可空/`{appId,kbIds,modelId}` |
| `GET /conversations?page&pageSize` | 列表 | `PageResult{list,total,page,pageSize}`；项：id/title/sourceLabel/isPinned/messageCount/updatedAt/requestState |
| `GET /conversations/{id}` | 详情+输入框还原 | 含 requestState |
| `PUT /conversations/{id}/pin` `{pinned}` | 列表项重排 | |
| `DELETE /conversations/{id}/messages` | 清空 | 列表项摘要置空 |
| `POST /conversations/batch-delete` `{ids}` | 移除选中项 | |
| `POST /conversations/{id}/title` | 标题回填 | 失败静默保持原标题 |
| `POST /conversations/{id}/stop` | 流终止 | 配合前端 AbortController（沿用 `useAppChatStream.ts:98-116` 模式 ‹CERTAIN›） |
| `POST /conversations/{id}/stream`（SSE） | 消息流 | 事件词汇与 useAppChatStream 九类一致（REQ-C02 零新增） |
| `GET /models?purpose=LLM` | 模型下拉 | 复用 `api:1088` |
| `GET /knowledge-bases` | KB 选择器 | 复用 `getKnowledgeBases`（KnowledgeConfig 数据源惯例） |
| 历史消息 `references` 字段 | SourcesBlock（历史形态） | 投影子集无 content（见后端 data.md MessageReference） |

## 6. Responsive & Accessibility

- ≥1280px：左栏 280px（列表）+ 聊天区自适应；<1024px：左栏抽屉化（沿 runtime Side Panel 开关交互 `runtime.vue:526-545` ‹CERTAIN›）。
- 键盘：Enter 发送、Shift+Enter 换行（跟随 runtime 现状，INFERRED-3）；停止按钮流式期间可达。
- 消息滚动沿用「距底 <80px 才自动跟随」惯例（`runtime.vue:241-263` ‹CERTAIN›）。

## 7. Acceptance

- [ ] 不选任何范围可直接新建对话并完成多轮提问？（TASK-001）
- [ ] 列表置顶优先、updatedAt 倒序、带来源标记、分页正确？（TASK-002）
- [ ] 重开会话输入框还原上次的 KB/模型/预设？（TASK-003）
- [ ] 流式增量/思考块/工具卡/来源块渲染与现有运行页一致？（TASK-004）
- [ ] 停止后部分回答保留且标注「（已停止）」，可继续提问？（TASK-005）
- [ ] 起标题成功更新列表标题；失败静默回退首问截断？（TASK-006）
- [ ] 置顶即时重排；清空后消息区空态且列表摘要清空；批量删需确认且级联？（TASK-007）
- [ ] 历史回答的来源块可展开并跳转分片页？（TASK-008）
- [ ] 带 appId 进入显示预设 chip；移除预设后 KB 多选恢复可用？（TASK-009）
- [ ] 挂预设时 KB 多选禁用并提示范围由应用决定？（D02）
- [ ] 挂预设的对话行为（开场白/绑定能力）与旧运行页一致？（D01）
- [ ] 消息复制/点赞点踩/删除/编辑重发可用？（TASK-010）
- [ ] 旧 runtime 路由直达不 404？（INFERRED-2）
- [ ] 编辑器对话调试 Tab 行为零变化？（Scope 排除项回归）

## 8. INFERRED 裁决（✅ 已闭环 2026-09-25——用户「按推荐」逐条确认，记录见 [../decisions/inferred-adjudication.md](../decisions/inferred-adjudication.md) §19）

1. ✅ **菜单挂载位置**：会话中心作为 Sidebar **独立顶级菜单**「会话中心」（对话一等入口语义），不挂在应用中心组下。
2. ✅ **runtime 入口处置**：Sidebar 移除「应用运行」项，路由与页面保留（兼容旧链接）；应用卡片「▶运行」改跳会话中心。
3. ✅ **Enter 发送**：跟随 runtime 现状（Enter 发送、Shift+Enter 换行）。
4. ✅ **起标题入口**：会话列表项操作菜单（⋯）内「✨ 智能起标题」，不在聊天区加常驻按钮。
5. ✅ **批量删除交互**：列表多选模式（checkbox）+ 底部浮出「删除所选」，ElMessageBox 确认。
6. ✅ **v1 不做会话重命名**：标题仅经「智能起标题」变更（后端无独立 rename 端点）。
