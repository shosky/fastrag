# 应用中心（app-center） — 实现文档

## 1. Code File Map

```
fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/
├── controller/
│   ├── AppController.java          — 应用台账 HTTP 面（7 端点，CUD 带 @Loggable）
│   ├── AppChatController.java      — SSE 对话与会话消息 HTTP 面（8 端点）
│   ├── AppConfigController.java    — 配置面 HTTP 面（~60 端点，全后端最大）
│   └── WorkflowController.java     — 工作流域（特性 #16，本文件不计入本特性实现）
├── config/
│   ├── AppDataScopeInterceptor.java— 数据范围拦截器注册
│   └── AppWebConfig.java           — Web 配置
├── dto/
│   ├── AppChatRequest.java         — SSE 对话请求体（query/sessionId）
│   └── AppSessionDTO.java          — 会话列表出参 DTO
├── entity/                         — 22 实体中 16 个属本特性（App/AppConfig/AppBasicConfig/
│   │                                 AppDialogConfig/AppGlobalPolicy/AppVariable/App*Binding×5/
│   │                                 AppTrigger/AppPublishRecord/AppTemplate/AppDialogTest/
│   │                                 AppOptimization/AppKbAutoUpdateConfig/AppConversation/
│   │                                 AppConversationMessage）；Wf* 7 个属工作流域
│   └── （对应 mapper/ 下 16 个 Mapper）
└── service/
    ├── AppService.java             — 台账+会话+流式对话三合一接口
    ├── AppConfigService.java       — 配置面全量接口
    └── impl/
        ├── AppServiceImpl.java     — 台账/会话账本/SSE 编排（845 行，本特性最深单元）
        └── AppConfigServiceImpl.java— 配置面 upsert 惯用法（381 行）
```
镜像 design Module Map：AppLedgerModule=AppController+AppServiceImpl:66-239；AppDialogOrchestrator=AppChatController+AppServiceImpl:368-844；AppConversationLedger=AppServiceImpl:92-116,241-366；AppConfigFacade=AppConfigController+AppConfigService(Impl)；AppBindingRegistry=AppConfigServiceImpl:93-163+AppServiceImpl:569-624；AppPolicyModule=AppServiceImpl:85-90+AppConfigServiceImpl:75-89,234-253。

## 2. Interface Implementations

### runStream（from contract: app-center.AppDialogOrchestrator）

**Logic Flow:**
1. selectById(appId)；requireVisible：null→notFound，DataScope.visible(owner/org/system) 不过→forbidden。
2. IF sessionId 非空 THEN requireOwnConversation（appId+sessionId 查行；null→notFound；非属主且非 API Token→forbidden）。
3. IF 会话为 null THEN createSession（生成 16 位 sessionId/32 位 conversationId，插「新对话」行）。
4. isSafetyEnabled（无策略行或 safetyEnabled null→true）为 true 时 checkInput(query)；命中→新建 60s emitter，send message{content=拦截文案, blocked:true}+end，complete，RETURN（不落库不调模型）；send 失败→RuntimeException。
5. 插用户消息行（32 位 id，role=user，tokens=0）；IF 会话消息数为 0 THEN 回填 firstQuestion（≤200）与 title（≤30+"..."）。
6. 读配置：AppConfig（prompt/temperature/model）与 AppBasicConfig（greeting/memoryRounds/advancedOptions）；model 四级回退：appConfig.model→advancedOptions.model→按 code 查 ModelRecord(status=online) 取 apiUrl/apiKeyRef/enableThinking→查无则默认网关+warn；systemPrompt 缺省文案；temperature 缺省 0.7。
7. IF greeting 非空 ∧ 消息数为 0 ∧ isGreetingQuery THEN 60s emitter 发 message{greeting,isGreeting:true}+end，开场白存为助手消息，RETURN。
8. 建 5 分钟 emitter；注册 onTimeout（fullAnswer 非空→filterOutput 后 saveAssistantMessage）、onCompletion/onError（仅日志）。
9. 捕获 SecurityContext→提交 streamExecutor（无界 cachedThreadPool）异步：
   - 传播 SecurityContext；retrieveContext：逐 enabled=1 KB 绑定 buildRetrievalConfig（topK=5+KB 保存配置透传 8 字段）→RetrievalService.search→拼接【参考资料N】与 sources 列表；单库异常 warn 继续。
   - send sources 事件（失败仅 warn）。
   - 取最近 memoryRounds*2 条历史倒转；BaseContext 装 systemPrompt/model/五类绑定 ID 集（tools/skills/mcps/knowledges/databases）/summaryThreshold/retryTimes/maxSteps/summaryPrompt(runtimeState)。
   - ModelConfig.builder 装模型参数（maxTokens 缺省 2048）。
   - 构造 fakeRun（id=sessionId、uid、inputPayload.query）→agentEngine.executeStream(context, emitter, modelConfig, fakeRun, 回调)。
   - 回调（end 事件前）：filterOutput(finalAnswer) 命中→answerToSave=替换文+send message{replace:true}；saveAssistantMessage（role=assistant，latencyMs，tokens=0）；conv.messageCount+2、首轮 answerSummary、updatedAt 刷新。
   - 异常→send error{「对话执行失败: …」}+complete（二次异常 completeWithError）；finally→SecurityContextHolder.clearContext()。
10. RETURN emitter。

**Module Interactions:**
- Calls：RetrievalService.search（缝 2）；AgentEngine.executeStream（缝 1）；SensitiveWordService.checkInput/filterOutput（缝 3）；ModelRecordMapper（缝 4，读 platform model 表）；KnowledgeBaseMapper（读 kb.retrievalConfig）；LlmService 注入未用（遗留字段〔CERTAIN：AppServiceImpl.java:58 声明，全文无调用〕）。
- Called by：AppChatController.chatStream。
- Shared state：app/app_conversation/app_conversation_message/app_kb_binding/app_tool_binding/app_skill_binding/app_mcp_binding/app_db_binding/app_config/app_basic_config/app_global_policy/model/kb 表；无缓存/队列/主题。

**Decisions & Deviations:**
- fakeRun 借用 AgentRun 类型但**不落 agent_run**（sessionId 冒充 runId）——复用引擎换取运行留痕缺失，已在 contract `## agent` 跨链登记，本链消费侧如实承接。
- 事件词汇超集：引擎封闭词汇外新增 sources 事件与 blocked/replace/isGreeting 标记位——契约已按超集书写。
- 无偏离项：拦截短路径、超时落库、SecurityContext 传播均与契约一致。

**Parent Trace:** runStream → app-center.AppDialogOrchestrator → Design.AppDialogOrchestrator（缝 1/2/3/4）→ REQ-E01~E13、REQ-C03/C04、REQ-D01。

### 会话账本七操作（from contract: app-center.AppConversationLedger）

**Logic Flow:**
1. createSession：requireVisible→生成 ID→插行（title=新对话，计数 0）→返回四元组。
2. listSessions：requireVisible→appId(+userId) 查询 updatedAt 倒序→逐行转 AppSessionDTO。
3. getSessionMessages：requireOwnConversation→查 deletedAt IS NULL 按 createdAt 正序→转 Map 列表（含 thinkingContent/toolCalls）。
4. deleteSession：requireOwnConversation→先物理删全部消息行→再物理删会话行。
5. deleteMessage：requireOwnMessage（消息→会话→属主链，API Token 旁路）→置 deletedAt=now→updateById。
6. feedbackMessage/updateMessage：requireOwnMessage→set feedback/content→updateById；行 null 静默返回。

**Module Interactions:** Calls：无外部模块（纯 Mapper）；Called by：AppChatController 五端点、AppDialogOrchestrator（runStream 步骤 2/3/5/9 回调）。Shared state：app_conversation/app_conversation_message。

**Decisions & Deviations:** 会话删除=物理、消息删除=软删——**不对称是有意为之**（会话级联物理清库，单条消息保留可恢复性）；tokenCount/tokens 恒 0 无生产者，契约已登记为已知断裂。

**Parent Trace:** 会话账本 → app-center.AppConversationLedger → Design.AppConversationLedger → REQ-E01/E02/E14/E15。

### 配置面全量（from contract: app-center.AppConfigStore）

**Logic Flow（惯用法，以 savePrompt 为代表）:**
1. getOrCreateConfig：按 appId 查行；null→new AppConfig 填六默认值（maxSteps=15/maxTurns=10/retryTimes=2/summaryThreshold=8000/maxTokens=2048/temperature=0.70）。
2. 单项 setter 覆盖请求字段（saveSummary 按 containsKey 逐项）。
3. IF id==null THEN insert ELSE updateById；RETURN 实体。
- 变体：saveBasic/saveDialog/saveGlobalPolicy/saveAutoKnowledgeUpdate 同 upsert 模板（各自兜底值）；publish=直接 insert(publishedAt=now)；analyze=四表计数聚合；importConfig=按键 convertValue 逐组转调写方法（六组）；workflow/debug 配置=parse advancedOptions→put 段→序列化回写；对话记录三方法=try 全捕获→空壳/"table not ready"/静默。

**Module Interactions:** Calls：无外部模块（16 Mapper + ObjectMapper）；Called by：AppConfigController ~60 端点。Shared state：app_config/app_basic_config/app_dialog_config/app_global_policy/app_variable/app_trigger/app_kb_binding/app_db_binding/app_skill_binding/app_tool_binding/app_mcp_binding/app_publish_record/app_dialog_test/app_optimization/app_kb_auto_update_config/app_conversation(+message) 表。

**Decisions & Deviations:**
- **分页偏离**：listConversations 返回 {total,items,page,size} 而非宪法 PageResult{list,total,page,pageSize}——如实登记，收敛需前端同步改。
- **异常吞噬**：三组 list/detail/delete/export 捕获 Exception 返回空壳（"table may not exist yet" 注释表明是建表时序防御）——调用方无法区分空数据与故障，契约登记为吞噬面。
- 导入导出不对称：11 组导出、6 组导入（skills/tools/mcps/dbs 出而不入）。
- triggerKnowledgeUpdate 只置 enabled=1 不调度（cron_expr 无 @Scheduled 消费者〔2026-09-24 深证升级 CERTAIN：全仓唯一 @Scheduled 为 agent 域 SandboxService，与本表无关〕）。
- **发布链路无状态流转**（2026-09-24 裁决登记债务）：publish 不写 status（落库恒 DDL 默认 'draft'）、无下线/回滚端点，Monitor released/rollback 计数恒 0——目标值域 {draft,released,offline,rolled_back} 见 spec §4.6。
- **app 删除不级联**（2026-09-24 裁决登记债务）：delete 仅 deleteById 主表（AppServiceImpl.java:165-170），config/绑定/会话等子表行残留孤儿——级联清理目标语义见 data.md app 节。
- **SSE 线程池无界**（2026-09-24 裁决登记债务）：streamExecutor 为无界 cachedThreadPool（AppServiceImpl.java:64）、无并发连接上限——SLA 目标值见 spec §5。
- **app_template 零种子**（2026-09-24 裁决登记债务）：data.sql 无 INSERT、无管理端点，模板列表现网恒空；usage_count 亦无自增点——维护方式裁决见 data.md app_template 节。

**Parent Trace:** 配置面 → app-center.AppConfigStore → Design.AppConfigFacade → REQ-B01~B06、REQ-D02~D05、REQ-F01、REQ-G01/G02、REQ-H01。

### 五类绑定管理面（from contract: app-center.AppBindingRegistry）

**Logic Flow:**
1. list：appId 等值查询；db 绑定逐行 jsonArrayToCsv。
2. bind：appId 强制覆写→enabled null→1（kb 另 priority null→0；db 的 allowedTables csvToJsonArray）→insert。**skill 绑定例外（M3-5/D11）**：先校验挂载预算（M2-6，enabled 绑定 ≥20 拒绝）再 selectById 校验技能存在且 enabled=1，否则 BusinessException——悬空/停用技能绑定不再静默存活（SkillsMiddleware 运行时本就丢弃此类绑定，入口侧前置拦截）。
3. update：setId(id)→db 转换→updateById→回查（db 再转 CSV 返回）。
4. unbind：deleteById（物理，查无不报错）。

**Module Interactions:** Calls：无外部模块（skill 校验经 fastrag-tools 的 SkillMapper 只读 skill 表）；Called by：AppConfigController 绑定组端点；运行侧消费在 AppDialogOrchestrator（只读 enabled=1）。Shared state：五张绑定表。

**Decisions & Deviations:** 工具/MCP/KB/DB 资源存在性仍不校验（悬空绑定静默存活，与 skill 校验口径不一致——待后续统一）；skill/tool/mcp list 吞异常返空列表。无契约偏离。

**Parent Trace:** 绑定管理面 → app-center.AppBindingRegistry → Design.AppBindingRegistry（缝 5 写侧）→ REQ-C01/C02。

## 3. Testing

现有 3 个测试文件（引用 tdd/app-center.md 切片）：

- **AppModuleValidationTest**（502 行，16 用例，源码级/结构断言）：实体-Mapper 22↔22 全覆盖（TC-ENTITY-MAPPER-001/002）、实体字段防复制粘贴错误（TC-ENTITY-FIELD-001~005）、服务接口完整性（TC-SERVICE-001~004）、**占位行为固化**（TC-MOCK-001 固化 AppServiceImpl.run() 纯关键词匹配无 LLM/RAG——即 S7 遗留通道断言；TC-MOCK-002/003 固化 workflow 占位）、schema-h2 表存在性（TC-SCHEMA-001）、runTrigger 回归（TC-REG-004 防再退化调 testTrigger）→ 覆盖 S5/S6 的结构面与 S7 的遗留固化。
- **AppConfigServiceTest**（189 行，7 用例，纯 mock 单测）：getConfig、savePrompt create/update 等 upsert 语义 → 覆盖 S5 部分。
- **AppConfigIntegrationTest**（173 行，7 用例，**名为 Integration 实为 mock 单测**，ArgumentCaptor 断言）：saveMaxSteps/saveMaxTokens/saveRetryTimes 首存默认值填充、update 路径不新建行、getConfig 全字段 → 覆盖 S5 核心（tdd 表「[缺省][upsert]」两行的现成实现）。

**缺口**（对应 tdd 切片）：S1 SSE 会话状态机（blocked/greeting/首条回填/计数/超时/异常）**零测试**；S2 绑定装配与检索透传零测试；S3 模型路由与安全双接入点零测试；S4 会话账本归属与级联零测试；S6 运行时装配半边零测试。AppDialogOrchestrator（845 行中约 600 行编排逻辑）完全无自动化覆盖，是本特性最大质量风险点。
