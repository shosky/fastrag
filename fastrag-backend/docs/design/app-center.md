# 应用中心（app-center） — 架构设计

> **ADR 符合性核对**（2026-09-24 review 增补）：**ADR-0001**（解析策略绑定变更必须触发重新分片）——本特性五类绑定与知识库引用不触碰策略绑定面（换绑裁决在 parse-strategy.ParseStrategyBindingGuard），无冲突；**ADR-0002**（图存储收敛 Neo4j，唯一实现、启动 fail-fast）——对话编排只经 retrieval 的 graph 扩展通道间接触图，不直接依赖 GraphStore 实现，无冲突。（全文见 adr/0001-*.md、adr/0002-*.md。）

## 1. Module Map

| 模块 | Responsibility（一行） | Files | Depth Rationale |
|---|---|---|---|
| **AppLedgerModule**（应用台账） | 应用聚合根的 CRUD/模板/可见性判定与操作审计挂接 | AppController.java、AppService.java、AppServiceImpl.java:66-239、entity/App.java、entity/AppTemplate.java | **(S)** — 薄台账：create 固定 draft、update 仅两字段、list 拼装 DataScope 条件，逻辑直白。建议：保留独立类（聚合根入口 + 三控制器共用），不再增厚。同步旧版 run()（关键词匹配 QA_RULES）**非死代码**：前端 DebugChat.vue:33（编辑器调试对话）与 DialogTest.vue:60（对话测试录制，录制答案写入 expected_answer）仍经 api.runApp 消费 POST /{id}/run〔‹CERTAIN（2026-09-24 深证）›〕。〔决议 2026-09-24：按现状为准——/run 通道保留为「调试器 + 测试录制」兼容面，不随 SSE 切换删除；AppServiceImpl.java:202 的 @Deprecated 弃用意向成立但无删除排期，删除前提是两组件先改走 SSE 且录制逻辑重接（代裁，用户可推翻）〕 |
| **AppDialogOrchestrator**（对话编排器） | SSE 对话总闸：校验→会话定位→安全拦截→消息落库→RAG 检索→上下文/模型配置装配→委派引擎→输出过滤→落库记账 | AppChatController.java、AppServiceImpl.java:368-844、dto/AppChatRequest.java、dto/AppSessionDTO.java | 深模块——12 步编排、三条短路径（blocked/greeting/error）与两条异常路径（timeout/error）全部藏在一次 runStream 调用后；调用方只给 appId+query+sessionId 即获得完整对话。与 AppLedgerModule 同类但职责厚度悬殊，分置合理 |
| **AppConversationLedger**（会话账本） | 会话与消息的归属受控存取：创建/列表/历史/级联删/软删/反馈/编辑 | AppServiceImpl.java:92-116,241-366、entity/AppConversation.java、entity/AppConversationMessage.java | 中等深度——归属校验（requireOwnConversation/requireOwnMessage + API Token 旁路）是唯一非平凡逻辑；被 AppDialogOrchestrator 与 AppConfigFacade 对话记录面共用。建议：物理上留在 AppServiceImpl，不再拆分 |
| **AppConfigFacade**（配置门面） | 应用全部配置面（基础/对话/智能体/触发器/策略/变量/测试/优化/发布/对话记录/监控调试）的统一读写口，upsert+默认值兜底 | AppConfigController.java、AppConfigService.java、AppConfigServiceImpl.java、entity/AppConfig.java 等 16 实体 | 深（宽而不深）——~60 端点 × 16 实体被「getOrCreate + 单项 setter + upsert」三个惯用法吸收；单项端点全是同一模板。**注意**：listConversations 返回 `{total,items,page,size}`（AppConfigServiceImpl.java:325）**违反 PageResult{list,total,page,pageSize} 全局约定**〔CERTAIN，仲裁见 §5〕 |
| **AppBindingRegistry**（五类绑定注册表） | 知识库/工具/技能/MCP/数据库五类资源的绑定生命周期与默认值填充 | AppServiceImpl.java:569-624（运行时装配）、AppConfigServiceImpl.java:93-163（管理面）、entity/App*Binding.java ×5 | **(S)** — 每类绑定 = list/bind/update/unbind 四个同构 MyBatis-Plus 调用；唯一非平凡处是 app_db_binding.allowedTables 的 CSV↔JSON 双向转换（AppConfigServiceImpl.java:100-147）。建议：不新增第六类绑定时保持现状 |
| **AppPolicyModule**（策略与触发器台账） | 全局安全/兜底策略与应用触发器的存取、安全开关缺省判定 | AppServiceImpl.java:85-90（isSafetyEnabled）、AppConfigServiceImpl.java:75-89,234-253、entity/AppGlobalPolicy.java、entity/AppTrigger.java、entity/AppVariable.java | **(S)** — 策略是单记录 upsert；触发器 test/run 为命中计数占位（testTrigger 只 +1 hitCount，runTrigger 回写静态 output）〔CERTAIN：AppConfigServiceImpl.java:80-85〕，真实触发执行未实现，与 workflow 链「引擎占位」定性一致 |

## 2. Interface Contracts

### AppLedgerModule（公共接口 = AppService 台账半边 + HTTP 面）

- `list(keyword?, tag?) → List<App>` — 关键字/标签筛选 + DataScope 三分支（属主/同组织/system）+ API Token 全量。
- `get(id) → App` / `create(form) → App`（status=draft、type 缺省 ChatBot）/ `update(id, form) → App`（containsKey 语义，仅 name/description）/ `delete(id)`。
- `getTemplates() → List<AppTemplate>` — 全量模板。
- `run(id, query) → {sessionId, answer}` — @Deprecated 关键词匹配旧通道。
- Hidden implementation：QA_RULES 表、matchAnswer 评分（关键词长度计分）、DataScope 拼装。
- HTTP 面（7 端点）：GET/POST `/api/apps`、GET/PUT/DELETE `/api/apps/{id}`、GET `/templates`、POST `/{id}/run`；权限 app:use/create/edit/delete，CUD 带 @Loggable〔AppController.java:30-42〕。
- Depth rationale：可见性规则一处判定（visible/requireVisible/requireManage）三处消费。

### AppDialogOrchestrator（公共接口 = AppService 流式半边）

- `runStream(appId, query, sessionId?, userId, userName) → SseEmitter` — 事件序列：`message(blocked)`｜`sources → init/thinking/message/tool_call/tool_result/agent_state（引擎词汇）→ message(replace)? → end`｜`error`；完整词汇继承 agent.ExecutionEngine 契约（init/message/thinking/tool_call/tool_result/agent_state/end/error，另加本域 sources 事件）〔AppServiceImpl.java:546-553 + contract/interfaces.md `## agent` 事件封闭集 CERTAIN〕。
- Hidden implementation：RetrievalContext 组装、buildRetrievalConfig KB 配置透传、SecurityContext 异步传播、memoryRounds*2 历史窗口、模型路由四级回退（app_config.model → basic.advancedOptions.model → online ModelRecord → 默认网关）。
- HTTP 面：POST `/api/apps/{appId}/chat/stream`（TEXT_EVENT_STREAM）等 8 端点〔AppChatController.java:47-125〕。
- Depth rationale：调用方（前端/API Token）对检索、绑定装配、敏感词、记忆、落库零感知。

### AppConversationLedger（公共接口 = AppService 会话半边）

- `createSession(appId, userId, userName) → {sessionId, conversationId, title, createdAt}`；`listSessions(appId, userId) → List<AppSessionDTO>`（updatedAt 倒序）；`getSessionMessages(appId, sessionId) → List<Map>`（软删过滤、时间正序）；`deleteSession(appId, sessionId)`（消息物理级联删）；`deleteMessage(appId, messageId)`（软删）；`feedbackMessage(appId, messageId, feedback)`；`updateMessage(appId, messageId, content)`。
- Hidden implementation：requireOwnConversation/requireOwnMessage 归属校验、16/32 位 ID 截断、messageCount 成对 +2 与 answerSummary 首轮回填。
- Depth rationale：归属不变量单点维护，两个上层模块共用。

### AppConfigFacade（公共接口 = AppConfigService 全量）

- 配置组：`getBasic/saveBasic/memory/output-format`（AppBasicConfig）、`getDialog/saveDialog`（AppDialogConfig）、`getConfig/savePrompt/saveSummary/saveMaxSteps/saveMaxTurns/saveRetryTimes/saveMaxTokens`（AppConfig，getOrCreate 六默认值：maxSteps=15/maxTurns=10/retryTimes=2/summaryThreshold=8000/maxTokens=2048/temperature=0.70）〔AppConfigServiceImpl.java:37-42〕。
- 策略组：`getGlobalPolicy/saveGlobalPolicy`（safetyEnabled 缺省 1）、`listVariables/createVariable/updateVariable/deleteVariable`、`saveSensitiveWords/togglePolicy/saveUnmatchedConfig`（三个薄端点转同一 upsert）。
- 触发器组：`listTriggers/createTrigger（enabled=1、priority=0 兜底）/updateTrigger/deleteTrigger/testTrigger/runTrigger`（后两者为 hitCount+1 占位）。
- 绑定组：`list*/bind*/update*/unbind*` ×5 类（bind 兜底 enabled=1，KB 另有 priority=0；db 绑定 CSV↔JSON 转换）。
- 台账组：`listPublishRecords/publish`（直接插入记录、publishedAt=now）、`listDialogTests/create/update/delete`、`listOptimizations/create（status=pending）/update/delete/applyOptimization（status→applied）/analyze`（纯计数聚合：totalTests/matchedTests/unmatchedRate/totalPublish/totalKbBindings/totalTriggers）〔AppConfigServiceImpl.java:178-193〕。
- 扩展组：`saveAdvanced/exportConfig/importConfig/exportDialogConfig/importDialogConfig`（导入仅覆盖 basic/dialog/policy/triggers/variables/kbs 六类，skills/tools/mcps/dbs 导出而不导入〔CERTAIN：AppConfigServiceImpl.java:212-222〕）、`getWorkflowConfig/saveWorkflowConfig`（读写 advancedOptions.workflow 段）、`getDebugInfo/saveDebugConfig`、`triggerKnowledgeUpdate/getAutoKnowledgeUpdate/saveAutoKnowledgeUpdate`、`listConversations/getConversationDetail/deleteConversation/exportConversationsCsv`、`getMonitorData`（真实会话数据聚合：发布/回滚/成功率/调用量/token 量）。
- Hidden implementation：csvToJsonArray/jsonArrayToCsv、parseJsonMap 容错（坏 JSON 返回空 Map）、对话记录三方法的全捕获吞异常（返回空结果/"table not ready"）。
- HTTP 面：`/api/apps/{appId}/**` ~60 端点按端点排除铁律不立契约，清单见 AppConfigController.java:33-236。
- Depth rationale：宽配置面被 upsert 惯用法吸收；每加一种配置只增「实体 + getOrCreate + 单项端点」。

### AppBindingRegistry / AppPolicyModule

公共接口即 AppConfigFacade 的绑定组/策略组（上文），无独立接口；运行时装配面（五表 enabled=1 → BaseContext.tools/skills/mcps/knowledges/databases）是 AppDialogOrchestrator 的私有实现，不对外。Hidden implementation：五类绑定异常吞噬（skill/tool/mcp list 失败返回空列表〔AppConfigServiceImpl.java:152,156,160〕）。

## 3. Seam Diagram

**缝 1：对话执行通道缝**
- What varies：一段话怎么变成流式答案（引擎能力集：工具循环/thinking/多步）。
- Adapters：(a) agent.ExecutionEngine.executeStream（活跃，唯一生产适配器）；(b) 本地关键词匹配 run()（遗留同步通道，@Deprecated）。2 个适配器 = 真缝。
- Stable side：BaseContext + ModelConfig + onComplete 回调 + fakeRun（sessionId 冒充 runId，不落 agent_run——与 contract `## agent` 跨链登记一致〔AppServiceImpl.java:657-665 CERTAIN〕）。
- Placement：AppDialogOrchestrator 末端。理由：应用层只负责装配（配置/绑定/检索/记忆），执行复杂度全部下沉引擎，避免双实现漂移。

**缝 2：检索供给缝**
- What varies：给定 (kbId, query) 如何召回与组装上下文。
- Adapters：retrieval.RetrievalSearchEngine（RetrievalServiceImpl 唯一实现）；协议=RetrievalRequest{knowledgeId, query, config}，App 侧 topK=5 覆盖、其余字段透传 KB 保存配置，与检索链「请求参数 > KB 配置 > 系统默认」三级合并协议对齐〔AppServiceImpl.java:773-800 CERTAIN〕。
- Stable side：RetrievalRequest.RetrievalConfig 字段面 + SearchResultItem（含 sources 事件所需 fileName/similarity/fileId/chunkIndex/chunkType/imageKeys）。
- Placement：AppDialogOrchestrator 内 retrieveContext。理由：按绑定逐库检索并拼接，失败仅告警继续〔:755-757〕，这是应用域语义而非检索域语义。

**缝 3：敏感词过滤缝**
- What varies：输入拦截判据（checkInput）与输出替换判据（filterOutput）。
- Adapters：platform-config SensitiveWordService（唯一实现）。isSafetyEnabled（应用级总开关，缺省开）在稳定侧先行短路〔AppServiceImpl.java:85-90,393 CERTAIN〕。
- Stable side：`Optional<String>` 协议——checkInput 命中给拦截文案、filterOutput 命中给替换全文。
- Placement：AppDialogOrchestrator 两接入点（输入前置 :391-409、输出回调内 :668-690）。理由：拦截语义（不落库不调用）与替换语义（补发 replace 事件再落库）是应用域决策。

**缝 4：模型路由缝**
- What varies：model code 解析为 (apiUrl, apiKey, enableThinking)。
- Adapters：无第二实现——**假设缝**（1 适配器不立缝），按「消费 platform-config.ModelRegistry 生产的数据」登记：code+status='online' LIMIT 1 查询与 platform-config 契约的生产口径一致；查无记录降级默认网关不阻断〔AppServiceImpl.java:455-475 CERTAIN；platform-config 节缺省降级协议 CERTAIN〕。advancedOptions.model 回退是应用域特有的第二解析源（非缝）。
- Stable side：ModelRecord.code 路由键 + status 二值。

**缝 5：绑定装配数据缝（数据契约，非接口缝）**
- What varies：五类资源 ID 集合（行数据）。
- Adapters：写侧 AppConfigFacade（bind/unbind/update），读侧 AppDialogOrchestrator（enabled=1 装配）；下游 tools-skills-mcp.ToolRegistry.resolveTools 按 `## tools-skills-mcp` 契约消费 ID〔AppServiceImpl.java:570-624 CERTAIN〕。
- Stable side：五张绑定表的 (app_id, *_id, enabled) 形状。
- Placement：表即缝。理由：绑定是应用域与能力底座域的唯一耦合面，ID 透传使两侧对资源语义零耦合。

## 4. Deepening Opportunities

- **AppDialogOrchestrator**：可继续吞入——(a) tokenCount 目前恒 0（saveAssistantMessage :831 setTokens(0)，conversation token 统计无生产者）〔CERTAIN〕，token 记账收进来即可闭合 Monitor 的 totalTokens 口径；(b) memoryRounds 缺省 5 的读取已吞，但 maxInputLength/timeoutSeconds 字段尚无运行时消费者〔‹CERTAIN（2026-09-24 深证：AppServiceImpl.java 与 AppConfigServiceImpl.java 全文无 getMaxInputLength/getTimeoutSeconds 调用，全仓 grep 零命中，字段仅存储/回显）›——补齐后 basic 配置面才有运行语义。
- **AppConfigFacade**：导入导出可吞「字段白名单校验」（现 importConfig 直接 convertValue，未知字段静默丢弃）；对话记录三方法的吞异常应改为显式业务错误（现 catch(Exception) 返回空壳）。
- **AppLedgerModule**：无——保持浅；/run 遗留通道按 2026-09-24 裁决保留（前端调试/录制仍在消费，见 §1），「更纯」的删除项搁置。
- **AppBindingRegistry**：可吞「绑定引用完整性」（bind 时不校验 kbId/toolId 存在性，当前信任调用方）〔‹CERTAIN（2026-09-24 深证：bindKb/bindDb/bindSkill/bindTool/bindMcp 五方法均为 defaults 兜底后直接 insert，无资源存在性查询，AppConfigServiceImpl.java:95,104-109,153,157,161）›——悬空 ID 由运行期 ToolRegistry E_TOOL_UNRESOLVABLE 或检索空结果吸收（bind 侧校验为增强项非缺陷修复项）。
- **AppPolicyModule**：真实触发引擎（trigger_type/match_content/action_type 字段已备而未用）——但执行归 workflow 域，应用侧只应吞「命中判定」，不宜吞「动作执行」。

## 5. Parent Trace

| Design 单元 | Spec REQ |
|---|---|
| AppLedgerModule | REQ-A01~A07、NFR-01/02/04 |
| AppDialogOrchestrator | REQ-C03/C04、REQ-E01~E13、REQ-D01、NFR-03（缝 1→E10；缝 2→C03/C04/E08；缝 3→E03/E11；缝 4→E06） |
| AppConversationLedger | REQ-E01/E02/E14/E15 |
| AppConfigFacade | REQ-B01~B06、REQ-F01、REQ-G01/G02、REQ-H01 |
| AppBindingRegistry | REQ-C01/C02（缝 5） |
| AppPolicyModule | REQ-D01~D05 |
| AppLedgerModule | REQ-A02（2026-09-24 review 补锚：创建时状态固定初始化 draft、类型缺省 ChatBot、属主与组织取当前登录用户） |
| AppLedgerModule | REQ-A03（2026-09-24 review 补锚：更新仅接受显式提交字段（containsKey 语义），删除/更新要求当前用户有管理权） |
| AppLedgerModule | REQ-A04（2026-09-24 review 补锚：应用不存在返回 notFound、可见性不通过返回 forbidden 业务错误） |
| AppLedgerModule | REQ-A06（2026-09-24 review 补锚：创建/更新/删除自动记录 app_created/app_updated/app_deleted 审计日志） |
| AppConfigFacade | REQ-B04（2026-09-24 review 补锚：查看类配置端点要求 app:use、修改类端点要求 app:edit） |
| AppConfigFacade | REQ-G03（2026-09-24 review 补锚：analyze 为纯计数聚合，不调用 LLM、不计算相似度） |
| AppDialogOrchestrator | REQ-E07（2026-09-24 review 补锚：系统提示词取智能体配置 prompt（缺省兜底），检索命中时拼接参考资料上下文与作答约束） |
| AppDialogOrchestrator | REQ-E09（2026-09-24 review 补锚：多轮记忆取最近 memoryRounds（缺省 5）轮（×2 条）历史消息按时间正序拼入上下文） |
| AppDialogOrchestrator | REQ-E10（2026-09-24 review 补锚：五类绑定与执行预算（摘要阈值/maxSteps/retryTimes/maxTokens/temperature）统一装配后委派 AgentEngine 执行全部 SSE 推送，应用层不自实现模型流） |
| AppPolicyModule | REQ-D04（2026-09-24 review 补锚：敏感词配置与策略开关作为独立端点写入全局策略面） |

**仲裁说明**：AppConfigServiceImpl.listConversations 返回 `{total, items, page, size}`（AppConfigServiceImpl.java:325）与宪法统一分页 `PageResult{list,total,page,pageSize}` 不一致——按代码实况如实记录为偏离（implement §2 Deviations），文档不隐饰、代码不擅改。
