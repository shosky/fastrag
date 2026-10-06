# 新建对话与会话中心（new-conversation） — 架构设计

> **ADR 符合性核对**（2026-09-25）：**ADR-0001**（解析策略绑定变更必须重切）——本特性不触碰解析策略面，无冲突；**ADR-0002**（图存储收敛 Neo4j）——本特性不经图谱通道，无冲突。本特性新增 **ADR-0003**（会话一等公民落在 app_conversation 原表扩展，不建独立会话表，见 [adr/0003-session-state-in-app-conversation.md](./adr/0003-session-state-in-app-conversation.md)）。
> 输入：[../spec/new-conversation.md](../spec/new-conversation.md)（27 REQ）、grill 裁决记录（`fastrag/.tmp/idd-new-conversation-grill-decisions.md`）、[app-center.md](./app-center.md)（跨链复用其编排器与账本）。双约束接口对比（最小表面 vs 显式解析器）的裁决记录见文末附注。

## 1. Module Map

> 根路径 = `fastrag-modules/fastrag-application/src/main/java/com/fastrag/module/application/`。**跨链延伸**标注的模块物理上属于 app-center 域（AppServiceImpl），本特性不复制其逻辑、只做受控扩展。

### DialogPresetResolverModule（会话预设解析）← 本特性深核心
- **Responsibility:** 把〔应用预设 │ 会话请求态 │ 内置缺省〕三源按优先级矩阵解析为单一有效配置对象 `EffectiveDialogConfig`，含模型五级路由与逐 KB ACL 过滤
- **Files:** `service/preset/DialogPresetResolver.java`、`service/preset/EffectiveDialogConfig.java`（含 `ModelRoute` 内嵌值对象）
- **Depth Rationale:** 调用方给（会话，本轮请求态，用户）拿回可执行配置全集。隐藏：D02 范围互斥矩阵、C05 五级模型回退（请求态模型 → app_config.model → advancedOptions → online ModelRecord → 默认网关，迁移自 AppServiceImpl.java:434-475）、advancedOptions JSON 容错手解（:440-448 同构）、内置缺省常量（**与 AppConfigServiceImpl 六默认值共用一个常量持有者**，防两处漂移）、逐 KB `KbAccessChecker.resolveRole` ≥ viewer 过滤 + 跳过告警。**删除测试通过**：删掉它，解析复杂度复现于新通道入口 / 旧通道（PRESET_ONLY 哨兵）/ 起标题取模型 / 重开恢复的降级判定（A04 本身就是一次解析）≥4 处

### SessionCenterModule（会话中心）
- **Responsibility:** 用户聚合轴的会话生命周期：创建（app/kb 均可选）、统一列表（置顶优先 + updatedAt 倒序 + 来源标记）、详情（回传请求态）、置顶、清空消息、批量删除、LLM 起标题、停止生成
- **Files:** `service/session/SessionCenterService.java`、`controller/SessionCenterController.java`（`/api/conversations/**`，仅登录鉴权）、`dto/ConversationDTO.java`
- **Depth Rationale:** 聚合轴是**用户**而非应用——现存账本全部按 appId 作用域（AppServiceImpl.java:93-104,241-366），归属不变量不同（无应用可见性门禁；会话 ID 主键定位 + app 可空前置条件）。**删除测试通过**：并入 AppServiceImpl 则该类（已 844 行）再吞 7 个生命周期方法 + 用户全量分页 + 来源标记拼装，且 app 可空归属变体无处安放。归属校验惯用法**提取为共享包私有组件**供编排器与本模块双消费（两消费者 → 真共享，非包装层）

### SessionRequestStateStoreModule（会话请求态存取）
- **Responsibility:** `request_state` JSON 形状的唯一属主：对话成功后固化、重开时还原、存量 NULL → 空请求态
- **Files:** `service/session/SessionRequestStateStore.java`、`dto/ConversationRequestState.java`
- **Depth Rationale:** **单一形状原则**——`ConversationRequestState` 一个值对象两用（持久化形状 = 每轮请求覆盖形状），否决"SessionScope + RequestState 双对象"（形状漂移风险）。字段一次建齐全形状（app 挂载及开关/kbIds/modelId/tool/mcp/skill ids/webSearchEnabled），**死字段清单的唯一证词在此**（REQ-B02 只存不消费，解析器绝不读取）。删除测试通过：字段形状知识将复现于固化点/还原点/创建端点 ≥3 处——`advancedOptions` 手解散落 AppServiceImpl.java:440-448 即前车之鉴

### StopRegistryModule（停止注册表）
- **Responsibility:** 单实例进行中对话的取消标志与部分落库接管句柄的注册/取消/幂等清理
- **Files:** `service/session/StopRegistry.java`（`ConcurrentHashMap<conversationId, CancelHandle{AtomicBoolean cancelled, Runnable partialSave}>`）
- **Depth Rationale:** 接近浅但**必须独立**：并发可见性策略（控制器线程 vs 流线程池线程）与**双落库幂等守卫**（complete 触发 onCompletion 与引擎正常结束回调可能双写，须 AtomicBoolean saved 单点裁决）是可单测的真实不变量。硬边界：字段面钉死 handle 三件套，**不得长成 StreamManager**（事件模型归 agent 域，届时本模块整体替换为适配）

### AppDialogOrchestratorModule（对话编排器）〔跨链延伸〕
- **Responsibility:** 既有 12 步 SSE 总闸不变；改造为**双入口、单执行核**：新旧通道都收敛到私有共享核 `executeDialog`，编排体内 15 处隐式配置读取（查表 + 兜底）全部替换为消费 `EffectiveDialogConfig`
- **Files:** `service/impl/AppServiceImpl.java`（runStream :368-844 改造）、`service/AppService.java`（新增 `runStreamByConversation`）
- **Depth Rationale:** 15 处替换清单（安全开关 :393 / 模型两级源 :434-448 / 在线解析 :450-475 / prompt-temperature :477-480 / maxTokens :644-645 / memoryRounds :431-432 / greeting :483-486 / 检索范围 :716-724 / 五表装配 :570-624 / 预算 :626-645 / 落库 :822-844）。**旧壳纪律：runStream 方法体 ≤5 行**（resolve + delegate），防双实现漂移。边界：`EffectiveDialogConfig` 止步应用域，**不吞并 BaseContext/ModelConfig**（缝 1 稳定侧属 agent 域）

### AppConversationLedgerModule（会话账本）〔跨链延伸〕
- **Responsibility:** 既有归属校验/级联删/软删/反馈全链不变；实体扩列（app_id 可空、request_state、is_pinned、消息 references）、归属校验 app 可空变体提取共享
- **Files:** `entity/AppConversation.java`、`entity/AppConversationMessage.java`、`service/impl/AppServiceImpl.java`（:93-104 校验变体）
- **Depth Rationale:** 沿袭 app-center §1 裁决物理留在 AppServiceImpl。**关键不变量（DDL 牵一发）**：`app_id` 可空后 `requireOwnConversation(appId,…)` 等值查询对直聊行失效——新旧通道收敛到**会话 ID 主键定位**，旧通道保留 `(appId, sessionId)` 等值作前置条件；同类 `eq(app_id,…)` 查询共 7 处（:94-97,:245-246,:717-720,:570-624 等）逐一条件化，漏改即首请求失败（显性暴露，无静默错数据）

### 消息引用落库 **(S)** ← 内联
- **Responsibility:** 助手消息落库引用集合 + 历史返回引用
- **Files:** 无新文件——`AppConversationMessage` 加 `references` 列（DDL 双处同步，NFR-02）；三触点：sources 事件（:743-752）、落库（:822-844）、历史组装（:312-325）
- **Depth Rationale:** (S)——落库 = 回调一次 set、返回 = 一处 map.put；数据源复用 `RetrievalContext.getSources()`（:803-807，已从 :539 携带至 :666 回调），天然满足 F01 同源同内容。**两通道统一落引用**（旧通道纯增列，可观测行为不变）；无命中存空

### LLM 起标题 **(S)** ← 内联
- **Responsibility:** 显式触发生成标题，失败回退截断首问 30 字（:424 同语义）
- **Files:** 无新文件——SessionCenterModule 私有方法，消费 fastrag-ai `LlmService.chat`（LlmService.java:85-106）
- **Depth Rationale:** (S)——单方法单消费者，假设缝；起标题模型取会话请求态 modelId、缺省 default 网关（与 C05 路由一致），异常/空返回即回退、不重试

**否决的候选模块**（删除测试不通过）：直聊编排器（C02 明令禁止，直聊与挂载应用只差配置来源——正是解析器的输入）；KB 权限包装层（`KbAccessChecker` 已是编程式入口，包装是零深度传递）；配置源适配器（假设缝，见 §3）。

## 2. Interface Contracts

### DialogPresetResolverModule

**Public interface**:
- `resolve(conversation, turnState, user) → EffectiveDialogConfig` — 唯一入口；`turnState` 为本轮请求态（旧通道传 `PRESET_ONLY` 哨兵）；`conversation.requestState` 为 NULL 时按存量降级解析（D03）
- `EffectiveDialogConfig`（只读）— `kbScope`（ACL 过滤后唯一 KB 列表，空 = 不检索）/ `bindings`（tools/skills/mcps/databases；挂载应用=五表装配，直聊=四个空集）/ `modelRoute{model,apiUrl,apiKey,enableThinking}` / `systemPrompt` / `temperature` / `maxTokens` / `memoryRounds` / 预算四项 / `safetyEnabled` / `greeting` / `source{APP_PRESET|DIRECT_CHAT, mountedAppId}`

**Hidden implementation**: 三源优先级矩阵（D02 互斥由"挂 App 分支不读 turnState.kbIds"的**结构**保证，非运行时比对）；五级模型回退；缺省常量持有者（与 AppConfigServiceImpl 六默认值同源）；逐 KB ACL 过滤。**设计裁决**：ACL 过滤覆盖有效 KB 范围**无论来源**（请求态或应用绑定）——REQ-B03 语义无来源限定，且 G02 的 API Token × 挂载应用组合下应用绑定库现状无 ACL 不应在**新通道**复刻；**旧通道零改动**（G01 行为不变优先）。**边界**：中间解析痕迹（哪级回退命中/哪个 KB 被跳过）归日志，不进配置对象。

**Depth rationale**: 编排器从 15 处查表+兜底降为一次 resolve + 纯消费；未来出现第三配置源只改解析器内部矩阵，编排器零改动。

### 编排器扩展（AppService 新增公共方法）

**Public interface**:
- `runStream(appId, query, sessionId, userId, userName)` — 现行签名原样保留，控制器侧标 `@Deprecated`（G01）；方法体 ≤5 行：resolve(PRESET_ONLY) + delegate
- `runStreamByConversation(conversationId, turnState, user) → SseEmitter` — 新通道流式入口（会话中心控制器直接消费，不设第三层委托）

**Hidden implementation**: 共享执行核 `executeDialog`（私有）——消费 `EffectiveDialogConfig` 的全部编排体；`retrieveContext` 签名改 `(kbIds, query)`（:716），`buildRetrievalConfig`（:773-800）原地保留（缝 2 稳定侧，检索域协议非配置来源）；`config→(BaseContext, ModelConfig)` 薄映射保留原地（:564-567,:646-654）。

**Depth rationale**: 双入口保证 G01 旧契约零变化，单执行核保证 C02 不漂移——漂移风险被"旧壳 ≤5 行"纪律结构性封死。

### SessionCenterModule

**Public interface**:
- `createConversation(form)` — A01：app/kb 均可选
- `listConversations(page, pageSize) → PageResult<ConversationDTO>` — A03：置顶优先 + updatedAt 倒序 + 来源标记；来源标记经**每页一次批量 app 名查询**（不冗余列，应用改名即时生效）
- `getConversation(id)` — A04：回传请求态供输入框还原；存量 NULL → 空请求态
- `pin(id, pinned)` / `clearMessages(id)` / `deleteConversations(ids)` — E05/E03/E04；**清空消息同时置空 first_question/answer_summary**（列表摘要一致性，设计决议）
- `generateTitle(id)` / `stop(id)` — E01/E02

**Hidden implementation**: 归属校验共享组件（会话 ID 主键定位 + app 可空 + API Token 旁路复用 :100）；级联删复用 deleteSession 语义（:330-339）；停止接线（向 StopRegistry 注册、取消时触发部分落库并 complete emitter，复用 :520-528 过滤落库语义，**幂等守卫防双写**）。

### SessionRequestStateStoreModule

**Public interface**: `load(conversationId)` / `persistOnSuccess(conversationId, turnState)`

**Hidden implementation**: 固化时机 = **与消息落库同点**（正常完成 onComplete、超时部分落库 onTimeout :520-528、停止部分落库三处），对齐 WeKnora "persisted on every successful POST"；JSON 编解码内联（hutool JSONUtil，:4,:781 先例），坏 JSON 容错为空请求态。

### StopRegistryModule

**Public interface**: `register(conversationId, handle)` / `cancel(conversationId)` / `unregister(conversationId)`

**Hidden implementation**: cancel 置位 + 触发 `partialSave` 一次（AtomicBoolean 幂等）+ `emitter.complete()`；置位后引擎 send 失败走既有 error 路径但**抑制 error 事件**（NFR-03 无孤儿流）；已知边界：取消感知只在引擎 send 间隙，引擎可能多耗一次 LLM 调用——v1 接受，登记为 agent 域 StreamManager 改造输入。

## 3. Seam Diagram

### 缝 1：对话执行通道（继承，不动）
- **What varies:** 引擎能力集（工具循环/thinking/多步）
- **Adapters:** agent.ExecutionEngine.executeStream（:665，唯一生产适配器）；本地关键词 run()（:203，@Deprecated）
- **Stable side:** BaseContext + ModelConfig + onComplete 回调——`EffectiveDialogConfig` 止步应用域，不替换二者
- **Placement rationale:** C02 禁止新增事件词汇；直聊沿用执行缝，不新增适配器

### 缝 2：检索供给（继承，位置不动）
- **What varies:** (kbIds, query) 如何召回组装
- **Adapters:** RetrievalSearchEngine 唯一实现；测试用 mock 适配器
- **Stable side:** RetrievalRequest 协议 + SearchResultItem（F01 引用源）
- **Placement rationale:** 本特性只换 kbIds 来源（绑定表 → 有效配置），检索域协议零改动

### 缝 3：敏感词过滤（继承，接入点不变）
- **What varies:** 输入拦截/输出替换判据
- **Adapters:** SensitiveWordService 唯一实现
- **Stable side:** `Optional<String>` 协议
- **Placement rationale:** C04 直聊缺省开 = `isSafetyEnabled(null)` 落现行缺省表达式（:85-90），两接入点零改动、开关改读 `cfg.safetyEnabled()`

### 配置来源（不立缝——假设缝）
- **What varies:** 应用预设 │ 会话请求态 │ 内置缺省
- **Adapters:** 仅矩阵内三源，无接口适配器
- **升级条件（登记）:** 出现第三配置源（如未来系统预设 App，裁决批次 2-3 排除项）或"双源合并"需求时，再抽 ConfigSource 适配器
- **Placement rationale:** D02 互斥下每次对话恰一个源活跃，无并存组合；分支总量为解析器矩阵内一个 if

### 模型路由（仍为假设缝——前置源新增）
- 沿用 app-center §3 缝 4 判定：单实现（消费 ModelRegistry 数据）。请求态模型只是解析器内的前置源赋值，非新缝。

### 停止传播（不立缝）
- v1 单实例语义（spec §2 裁决）；多副本化时由 agent 域 StreamManager 整体替换，本模块届时转适配。

## 4. Deepening Opportunities

- **DialogPresetResolverModule:** B02 解禁时吞入工具/MCP/技能的有效装配（字段已备，只改解析矩阵）；web 搜索激活同理。
- **SessionCenterModule:** fork/rewind（spec 登记的后续增强）落入本模块——会话级复制/回退与用户聚合轴同构。
- **StopRegistryModule:** agent 域 StreamManager 落地后整体替换为其适配器——字段面钉死即为该迁移预留。
- **编排器扩展:** tokenCount 恒 0 的记账（app-center §4 已登记）可在 executeDialog 内顺带闭合，但不属本特性验收面。

## 5. Parent Trace

| Design 单元 | Spec REQ |
|---|---|
| DialogPresetResolverModule | REQ-B02（死字段不入有效配置）、B03（逐 KB 过滤+跳过告警，覆盖两来源）、C01（缺省常量+空范围不检索）、C04（安全缺省开）、C05（请求态模型五级优先）、D01（应用预设整体注入）、D02（互斥的结构化保证）、D03（存量降级解析）、A02（会话不依赖应用） |
| 编排器扩展（双入口单执行核） | REQ-C02（复用编排链零新增事件）、C03（每次都检索）、G01（旧通道行为不变+@Deprecated）、A01（appId 可空创建） |
| SessionCenterModule | REQ-A01/A03/A04、E01（内联起标题）/E02（停止接线）/E03（清空+摘要置空）/E04（批量删）/E05（置顶）、G02（Token 旁路）、G04（登录即可）、NFR-01（PageResult） |
| SessionRequestStateStoreModule | REQ-B01（成功后固化全形状）、A04（重开还原）、G03（存量零迁移） |
| StopRegistryModule | REQ-E02、NFR-03（幂等落库+无孤儿流） |
| 会话账本扩展 | REQ-G03（app_id 可空零迁移）、B03/G04（归属变体共享）、NFR-02（实体扩列双处同步） |
| 消息引用落库 (S) | REQ-F01/F02（两通道统一） |
| LLM 起标题 (S) | REQ-E01（模型路由与 C05 一致） |
| 缝 1/2/3（继承不动） | REQ-C02/C03、B03+C01、C04 |
| 停止传播（不立缝） | REQ-E02（v1 单实例边界即 spec §2 排除项） |

---

## 附注：双约束接口对比裁决（2026-09-25）

两路并行设计（最小表面 vs 显式解析器）的关键分歧与裁决：

| 分歧点 | 最小表面方案 | 显式解析器方案 | 裁决 |
|---|---|---|---|
| 解析逻辑位置 | AppServiceImpl 私有方法 resolveEffectiveConfig | 独立深模块 DialogPresetResolver | **独立模块**——删除测试确凿：消费方 ≥4（新/旧通道、起标题、重开降级），私有热点方法在 844 行类内无局部性 |
| 会话中心归属 | 并入 AppServiceImpl（沿袭 app-center 裁决） | 独立 SessionCenterService | **独立模块**——用户聚合轴 + app 可空归属变体是本链新不变量；归属校验提取共享组件防复制 |
| 请求态载体 | SessionScope 值对象（编排入参） | ConversationRequestState（持久化形状） | **单值对象两用**——持久化形状与请求覆盖形状本就是 B01 同一形状，双对象是漂移源 |
| StopRegistry | 私有 Map 字段内联 | 独立小类 | **独立小类**——并发可见性 + 双落库幂等是可单测不变量；字段面钉死防长成 StreamManager |
| 通道入口形态 | 单 runStream + 可选 scope 参数 | 双入口 + 共享执行核 | **双入口单执行核**——旧契约零变化（G01）优于少一个方法；"旧壳 ≤5 行"纪律封死漂移 |
| 一致结论 | 三条既有缝不动、配置来源不立缝、互斥靠结构不靠运行时比对、编排器不吞引擎协议 | 同左 | 采纳为共同结论 |

采纳过程中同时吸收两案的独立发现：A 的 15 处穿透点/7 处 eq(app_id) 条件化清单、stop 双落库竞态；B 的 JSON 形状属主论证、上帝对象三防线（死字段渗入/越界吞引擎协议/溯源字段堆积）、会话定位键迁移不变量。
