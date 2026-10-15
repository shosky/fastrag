# 对话审计（Conversation Trace）— Grill 裁决记录

> 2026-10-14，idd-grill 会话产物；供 /idd-spec 起草 spec 使用。
> 决议状态标记：〔已决〕/〔代裁〕。**本次会话用户未逐项作答，全部决议按调查推荐项代裁**（对齐仓库「代裁，用户可推翻」惯例）——每条附依据，推翻任一条只需在 spec 阶段改判并回改本文件与 CONTEXT.md。

## 背景结论（代码/文档调查，非决议）

### 已有三个「半个」设施

| 设施 | 现状（证据） | 与本特性关系 |
|---|---|---|
| `app_conversation_message` | 每条助手消息已存 `content`/`thinking_content`/`tool_calls` JSON（工具输出**截断 5000 字**，AgentEngine.java:463-478 收集）/`references` | 「轮级」载体已有，无「步级」明细；保持现状不动 |
| `model_call_log` | **半死表**：读端活跃（ModelMonitorServiceImpl.java:28-156 聚合查询），写端零实现；列仅 modelId/caller/status/duration/tokens，**无内容列**（schema.sql:612） | 统计语义归模型监控，本特性不激活（D5） |
| AgentEngine 工具循环 + RunEventPublisher | 引擎内已就地收集每次工具调用参数/输出/耗时（agent spec REQ-E08「全量留痕」）；SSE 推送后 60s 清理不落库不可回放（agent spec REQ-D01/D02 如实登记） | 本特性恰好补「事后无法回看」空缺；toolCallRecords 是现成采集点 |

### 采集点全部集中

- **LLM 单点**：`LlmService`（fastrag-ai，唯一 LLM 客户端），全系统 12 个调用入口收敛于此；对话主路径 `streamChatWithTools`（LlmService.java:283），流式事件分 thinking/content/tool_call_delta/finish。现状仅 SLF4J 日志（debug 级全量请求体 LlmService.java:334/348），**零落库**。
- **工具执行单点**：`AgentEngine.executeTool`（AgentEngine.java:929）→ `ToolExecutorFactory.getExecutor`（四型执行器 + agent 侧 database/sandbox/subagent）；输出截断 5000 字回灌 messages（:451-454）。
- **对话编排单点**：双入口（会话中心 `SessionCenterController:88` → `runStreamByConversation` :441；旧应用通道 `AppChatController:48` → `runStream` :430）汇聚 `AppServiceImpl.executeDialog`（:471）。
- **独立智能体运行**：`AgentRunController` 创建 run 直达引擎（不走会话）。

### 三个必须知道的坑

1. **对话里 query_kb 是占位桩**：`KbOperationServiceImpl.queryKnowledgeBase`（:49-56）为 TODO 桩，返回「检索服务集成中」伪造文本，未接 RetrievalService。审计只能如实记录桩调用；**断链修复归 app-center/retrieval 域既有债务，不在本特性范围**。
2. **引擎上下文不带会话标识**：`BaseContext` 仅 threadId/uid/runId 等（BaseContext.java:46-132），AppServiceImpl 构建 context 时**从未 setThreadId**；会话标识在编排器手里（executeDialog 的 `conv`）。引擎埋点需编排器传入会话标识（最小改动：runtimeState 键，先例 HISTORY_KEY/sandboxConfigId）。
3. **依赖方向**：application → agent（AppServiceImpl.java:684 调引擎；application pom.xml:19）；agent 不依赖 application → 步骤表实体必须落 **agent 模块**（写入方），conversation_id 作普通列存储即可，无需实体依赖。

### 可复刻的审计范式

@Loggable→AOP→`SysAuditLogEvent`→@Async 监听落表→UnifiedLog 统一 DTO 查询（operation 域）；operation spec 明确其定位为**只读消费端**、日志产生端归各业务域（spec/operation-analytics.md:5-15）。既有决议可对齐：日志留存 180 天、暂不脱敏原文留存（spec/operation-analytics.md:97-98）。

---

## 批次 1：范围与埋点（2026-10-14，代裁）〔已决〕

- **D1 特性定位 = 新建特性 #21「对话审计（Conversation Trace）」**，落 feature-map 新条目 + CONTEXT.md 新术语（对话审计/对话步骤/追踪标识），不并入 agent 或 new-conversation 既有 spec。
  依据：跨 agent/application/operation 三模块，塞进任一域都使其 spec 失焦；与「操作审计」（@Loggable 操作级）「运行事件」（内存易失）语义必须区分。
- **D2 记录范围 = 双对话入口 + 独立智能体 run 全记**。
  依据：埋点在引擎层（D3），三条路径全过引擎，边际成本≈0；审计完整性优先。非引擎路径的 LLM 调用另议（D4）。
- **D3 埋点位置 = AgentEngine 引擎层**（executeStream/chatWithTools 工具循环内：每次 LLM 调用 + 每次工具执行各记一行）。
  依据：能直接拿到会话上下文与工具执行细节，一次埋点覆盖全部内容级数据。拒绝 LlmService 全局单点：拿不到会话上下文（需 ThreadLocal 穿透），且记不到工具执行——覆盖广但对话审计语义不完整；LlmService 侧旁路激活 model_call_log 归模型监控域另行立项（不在本特性）。
  衍生改动（登记给 design）：编排器把 conversationId 经上下文传入引擎（runtimeState 键，先例 HISTORY_KEY）；引擎每 run 生成 traceId（D7）。
- **D4 辅助 LLM 调用不记**（起标题 SessionCenterService.java:193、查询改写、QA 提炼、图谱抽取、评测等），v1 审计语义 = 「对话轮执行」：用户问题 → 引擎循环（LLM+工具）→ 回答。
  依据：保持「轮」语义纯净；辅助调用日志走 SLF4J 即可。后置可选：step_type=auxiliary 扩展。

## 批次 2：表设计与写入时机（2026-10-14，代裁）〔已决〕

- **D5 落表 = 新表 `agent_step_log`（agent 域）**，一 step 一行。
  拒绝激活 model_call_log（列不够且统计语义归模型监控页）；拒绝扩 app_conversation_message.tool_calls JSON（轮级聚合载体保持现状，截断 5000 语义不变）；拒绝 chat_session 死表复活（问答明细统计语义，另有归属）。
- **D6 行模型（字段草案，design 细化）**：`id / conversation_id(可空，独立 run 无) / run_id / trace_id / step_seq / step_type(llm_call|tool_call) / model / tool_name / arguments_json / request_increment_json / response_json(content+thinking+tool_calls+usage+finish_reason) / output_text / truncated / success / error / duration_ms / created_at`；索引 `(trace_id, step_seq)` / `(conversation_id, created_at)` / `(created_at)`（留存清理用）。内容列 MEDIUMTEXT。DDL 双处同步（schema.sql + SchemaInitializer + 守护测试，先例 SkillDdlParityTest / NewConversationSchemaSyncTest）。
- **D7 关联键 = traceId 每 run 一枚（UUID）**：串起本次 run 全部 step 行；**助手消息加列 `trace_id`**（run 终态落库时回填，与 persistAnswer 同点），「轮→步」查询链 = conversation → messages → trace_id → steps。多轮 = 每轮各自 traceId，天然支持。
- **D8 内容策略 = 增量存 + 双截断护栏**：
  - LLM 请求**增量存**：首轮 step 存完整请求基线（system+历史投影+用户消息），后续 step 只存本迭代新增消息；每步存元数据（消息条数/总字符）供快速浏览；查看端按 step_seq 重放还原每次真实请求。
    依据：15 次迭代每次携带全量历史为 O(n²) 存储放大；增量模型仍如实还原（中间件改写后的实际新增消息）。
  - 工具输出存**引擎截断前原始值**，单工具 32KB 上限、超出截断并标 truncated；LLM 请求增量单步 64KB 上限同口径。
    依据：审计价值在原始输出；回灌 LLM 的 5000 字截断是编排语义，不应成为审计上限。
- **D9 写入时机 = 引擎内存收集 + run 终态批量 INSERT**（与 persistAnswer onComplete 同点；同步执行，非 @Async——量级为单 run 数十行批量）。
  失败容忍：落表失败仅 warn，**绝不影响对话**（对齐 agent NFR02「事件非关键路径」先例）。已知代价：进程崩溃丢当轮审计（可接受——SSE 事件本就易失；登记债务）。拒绝逐条同步 INSERT（对话延迟入关键路径）。

## 批次 3：查看模块与权限（2026-10-14，代裁）〔已决〕

- **D10 模块归属：写入端 agent 模块（引擎埋点+实体+Mapper）；查询端 operation 模块（只读）**。
  依据：operation spec 自我定位「只读消费各域日志、不新增产生端职责」——边界不破；agent 不依赖 application，实体落 agent 域无障碍（背景坑 3）。
- **D11 权限 = v1 管理端**（运营/管理员），数据范围过滤对齐 ModelMonitor org 过滤先例（ModelMonitorServiceImpl.java:34-44）；**会话属主自看后置**（属主已有消息级回看 REQ-F02，步骤级自看待真实需求）。
- **D12 查询形状三级**：
  ① 会话审计列表：分页（PageResult），筛选时间/用户/关键词/模型/来源（直聊|应用 X|独立 run）；
  ② 会话详情：多轮消息 + 每轮关联 traceId（复用既有会话消息查询 + trace 关联）；
  ③ 步骤时间线：trace 内 steps 按 step_seq 交错展开（llm_call/tool_call），逐步可看请求增量/返回/思考/参数/输出/耗时。
  REST 草案（design 定稿）：`GET /api/conversation-traces`（列表）、`GET /api/conversation-traces/{conversationId}/messages`、`GET /api/conversation-traces/{traceId}/steps`；统一 ApiResponse/PageResult 宪法。
- **D13 不并入 UnifiedLog**：操作级（谁对什么做了什么）与内容级（模型/工具说了什么）粒度不同、消费场景不同（合规审计 vs 调试回看）；独立「对话审计」查询面，运营菜单新页。

## 批次 4：留存/安全/打印（2026-10-14，代裁）〔已决〕

- **D14 留存 = 180 天 + 本次交付定时清理任务**（每日批删超期行，按 created_at 索引 LIMIT 批删）。
  依据：留存时长对齐三日志决议（spec/operation-analytics.md:97）；但打破「全仓无清理任务」既有债务先例——内容级日志体积远超操作日志，无限累积不可接受，清理任务成本低必须随特性交付。
- **D15 脱敏 = 暂不脱敏、原文留存**（对齐 2026-09-24 决议，spec/operation-analytics.md:98）；继承技能凭据红线：技能 env 凭据值本就不进 prompt/事件/日志（CONTEXT.md「技能凭据通道」），审计自然不含，落表侧不做额外处理。
- **D16 内容口径 = 引擎视角真实收发**：输入消息 = 敏感词输入过滤**后**（引擎只见过滤后内容，executeDialog:478 拦截在引擎之前）；模型输出 = 输出过滤**前**原始（输出过滤发生在编排器 onComplete）。审计因此能回答「模型实际输出了什么、哪些被过滤」。埋点在引擎层则此口径零额外成本。
- **D17 SLF4J 打印 = 不动现状**：LlmService debug 级全量请求体打印已存在（:334/348）；实时打印开关不做——落表 + 查看模块已满足「详细查看」诉求，控制台打印不作为交付面。

---

## 待 /idd-spec 澄清（不阻塞，起草时定）

1. 表名/实体名终稿（本记录用 `agent_step_log` 占位）；`step_type` 枚举是否预留 auxiliary（D4 后置位）。
2. 独立 agent run 的 conversation_id 为空时，列表「来源」如何呈现（独立 run 无会话标题，取 run input query 截断？）。
3. 步骤时间线是否需要「重放组装完整请求」的服务端接口，还是前端按增量拼装（建议服务端组装，避免增量协议泄漏到前端）。
4. 清理任务配置（保留天数可配？放 sys_config 还是常量）。
5. 权限键/菜单种子（对齐 iam 权限页勾选驱动模式）。
