# 智能体运行时（agent） — 架构设计

## 1. Module Map

| 模块 | Responsibility | Files | Depth Rationale |
|---|---|---|---|
| **ExecutionEngineModule**（执行引擎） | 单一实现统一流式/同步双模式的 LLM 工具调用循环：工具解析→中间件前置→循环（LLM 调用/流消费/拦截分派/结果回灌/熔断注入）→收尾事件 | executor/AgentEngine.java、LlmStreamResult.java、AgentResult.java、ModelConfig.java | **深**：调用方（编排层/应用层/子智能体中间件）只给 context+emitter 即获得完整自主循环；工具分派、JSON 修复、thinking 提取、失败熔断、事件词汇全部被隐藏 |
| **RunOrchestrationModule**（运行编排） | 运行状态机（pending→running→completed/failed/cancelled）、requestId 幂等、异步执行调度、SSE 状态流、同线程防重查询 | service/AgentRunService.java、service/impl/AgentRunServiceImpl.java | 中等深度：幂等/状态迁移/终态判定被折叠；但 SSE 流与取消语义尚浅（见 §4） |
| **AgentRegistryModule**（智能体台账） | 智能体 CRUD、slug 唯一化、可见性三级判定、管理权限、默认互斥、内置保护、序列化、内置数据初始化 | controller/AgentController.java、AgentConfigController.java、AgentRunController.java、service/AgentService(.impl).java、entity/Agent.java、AgentRun.java、mapper/*.java、runner/AgentDataInitializer.java、dto/*.java | 中等深度：可见性判定+分享降级+默认自愈是真逻辑；controller 层是薄包装（按端点排除规则不立契约） |
| **MiddlewareChainModule**（中间件链） | 按 order 排序的三阶段横切执行（before 正序/after 逆序/拦截取首个）；12 个内置中间件注册工具、注入消息、修补响应、接管调用 | middleware/ 全部（AgentMiddleware、AgentMiddlewareChain、ToolCallInterceptor、Context 0 / Filesystem 1 / Attachment 2 / KnowledgeBase 3 / Skills 4 / SubAgent 5 / Summarization 6 / TodoList 7 / PatchToolCalls 8 / ModelRetry 9 / Database 10 / Mcp 11） | **深**：引擎对中间件数量零感知；中间件对循环细节零感知；新增能力=新增一个实现类 |
| **ContextBackendModule**（上下文与后端） | 配置元模型（@ConfigField 反射填充/可配置项派生）+ Backend 插件点（context schema + 执行图模板注册表）+ 运行时上下文组装 | context/BaseContext.java、ChatBotContext.java、ConfigField.java、ConfigurableItem.java、ContextBuilder.java、User/CurrentUser；backend/AgentBackend.java、AgentBackendManager.java、AgentGraph.java、ChatbotAgentBackend.java、SubAgentBackend.java | 中等深度：ContextBuilder 折叠了「配置加载→backend 定位→反射实例化→技能闭包展开」；AgentGraph 是数据占位（buildGraph 产物当前未被引擎消费，见 §4） |
| ~~**SandboxModule**（沙箱，降级桩）~~ | ~~沙箱连接缓存生命周期（确定性 ID/保活/空闲回收/关停清理）+ 沙箱工具执行器 + 虚拟文件系统路由~~ | sandbox/SandboxService.java、SandboxConnection.java、SandboxBackend.java、executor/SandboxToolExecutor.java、fs/* | **已拆解（2026-09-28 实装裁决，REQ-H01~H16）**：桩拓扑由下列六模块承接；旧类处置见各行 Files 演进说明 |
| **SandboxConfigModule**（沙箱配置） | 命名沙箱配置的 CRUD/归属权限/探活验证/实例库存/凭据引用解析/平台总开关 | sandbox/config/SandboxConfigController、SandboxConfigService(.impl)、entity/SandboxConfig、mapper/SandboxConfigMapper、dto/*（新增）；platform-config 侧 SysConfig 键 sandbox.enabled | **深**：藏 provider 块 JSON 列映射、凭据引用→环境解析（ADR-0007）、保存校验链（EndpointDialGuard + 网络模式白名单 + docker 域名字段拒绝）、验证编排（ping + 模板列举）与库存过滤（含 paused）；调用方只见「配置对象 + 可用性布尔」（REQ-H04~H07） |
| **SandboxLifecycleModule**（沙箱绑定与生命周期） | (用户,会话)→实例绑定的获取/保活/释放、exited 自恢复、配置身份变更失效标记、空闲回收、孤儿对账、会话删除释放 | sandbox/SandboxLifecycleService.java（SandboxService.java 演进重写）、SandboxBindingStore.java + RedisSandboxBindingStore / InMemorySandboxBindingStore、SandboxOrphanReaper.java、SandboxHandle.java（SandboxConnection.java 演进）（新增为主） | **深**：藏 create-or-connect 决策、provider 非确定 ID 的绑定持久、配置身份指纹比对（stale 标记）、TTL 固定于创建时、grace 宽限判定；调用方一行 acquire 得可用实例句柄（REQ-H01、H02、H08~H11） |
| **SandboxProviderClient**（后端协议缝：接口 + 三适配器） | provider 中立实例操作：create/connect/delete/list/health/capabilities + exec + 文件操作（read/write/stat/mkdir/remove/listDir） | sandbox/provider/SandboxProviderClient.java（接口）、docker/DockerSandboxAdapter.java（docker-java Engine API 直连，ADR-0006）、cube/CubeSandboxAdapter.java、e2b/E2BSandboxAdapter.java（控制面 REST + envd 数据面）（新增） | 缝本体（3 适配器=真缝）：适配器内藏协议差异（Engine API vs envd）、exec 超时包装与输出流分离、`find -printf` 解析、活动标记折叠、managed label、错误分类归一（REQ-H14、H15 执行位） |
| **SandboxExecutionModule**（沙箱执行门面） | 7 工具动作 + 附件暂存 + 技能物化 → 解析绑定 → provider 调用 → 路径钳制/超时/截断/降级三分支文本 | sandbox/SandboxExecutionService.java（SandboxBackend.java 重写，接口等形）、executor/SandboxToolExecutor.java（保留瘦身）、暂存/物化逻辑并入本模块不另立类 | **深**：藏三分支降级判定（总开关/应用未引用配置/连接失败）与区分原因的错误文本、读写根策略执行（经布局谓词）、工具超时+10s grace、输出截断；FilesystemMiddleware/AttachmentMiddleware/SkillsMiddleware 三个调用方零感知后端差异（REQ-H03、H12、H13） |
| **SandboxWorkspaceLayout**（沙箱工作区布局） | 容器内路径单一事实源：根/input/output/skills 常量、读写根判定、路径清洗与保留路径拒绝 | sandbox/SandboxWorkspaceLayout.java（新增，常量 + 谓词） | 小而承重：删除则路径字面量在 ≥5 处复现（ContextBuilder 技能提示、执行门面、工具描述、镜像 Dockerfile、契约 invariant）；接口（常量 + isReadable/isWritable/clamp）远小于其防漂移复杂度（REQ-H12） |
| **EndpointDialGuard**（拨号防护） | 后端 endpoint 的 SSRF 防护：保存时 URL 校验 + 建连时解析 IP 复查；云 metadata 恒拒、私网须显式放行 | sandbox/security/EndpointDialGuard.java（新增） | 小而承重：3+ 消费方（配置保存校验、Docker TCP daemon dial、Cube/E2B endpoint dial），策略单点；删除则防护规则散落各调用处（REQ-H16） |
| **RunEventModule**（运行事件） | 运行事件内存存储与序列号附加 | event/RunEventPublisher.java | **(S)**：43 行单类、写而不读（getEvents 无任何调用方）。建议：要么补齐消费侧（SSE 桥接/Redis 转发）成为真模块，要么内联进 ExecutionEngineModule |
| **LegacyExecutorModule**（旧执行器，已废弃） | 早期 10 轮同步循环 + 纯文本流式 | executor/AgentExecutor.java | **(S)**：@Deprecated 且仍被 RunOrchestrationModule 注入使用（迁移未完成）。建议：编排层切到 AgentEngine.executeSync 后删除 |

**架构叙事（Backend + Graph + Middleware + State）**：CONTEXT.md 定义的智能体四件套在代码中的落点——**Backend** = AgentBackend 插件（决定 context schema 与图模板）；**Graph** = AgentGraph（model/systemPrompt/tools/stateSchema 的数据载体，ChatbotAgentBackend.java:100-111）；**Middleware** = 中间件链（真正的行为发生地）；**State** = BaseState/ChatBotState/SubAgentRunState（artifacts 与子智能体运行台账的合并模型，state/*.java）。注意：Graph 与 State 当前偏「数据结构就位、引擎消费浅」（引擎直接读 BaseContext 字段而非经 AgentGraph；State 由 SubAgentMiddleware 侧使用）。

**与既有 ADR 的关系**：ADR-0001（解析策略绑定变更必须触发重新分片）与 ADR-0002（图存储收敛 Neo4j、唯一实现、启动 fail-fast）均不触及本特性边界（智能体不消费分片与图数据）；SandboxModule 的 fail-fast 缺席（Docker 不可用时静默降级）与 ADR-0002 的 fail-fast 精神相反，但沙箱是可选增强而非唯一实现约束，不构成冲突。沙箱实装新增 **ADR-0006**（容器侧 Engine API 直连、容器内零服务端——取代容器内 HTTP 设计）与 **ADR-0007**（凭据引用式存储、不落库加密）；「可选增强、降级不 fail-fast」基调不变（REQ-H03），与 ADR-0002 的分层关系维持原判断。

## 2. Interface Contracts

**ExecutionEngineModule** — 公共接口：
- `executeStream(BaseContext, SseEmitter, ModelConfig, AgentRun, StreamCompleteCallback) → void`：流式执行，SSE 事件词汇 init/message/thinking/tool_call/tool_result/agent_state/end/error，完成回调回传（answer, thinking, toolCallsJson）
- `executeSync(AgentRun, BaseContext) → AgentResult{success, answer, toolCallRecords[], totalDurationMs, error?}`：同步执行，事件写 RunEventPublisher
- 隐藏实现：步数上限与兜底文案、JSON 参数修复状态机、工具失败计数熔断、流超时控制、tool_call 增量合并
- Depth rationale：调用方一行调用即得完整自主循环，Deep Module 典型

**RunOrchestrationModule** — 公共接口（AgentRunService）：
- `createRun(dto, user) → Map`（幂等创建 + 异步触发）/ `getRun / getRunForUser` / `cancelRun` / `streamEvents(runId, afterSeq, user) → SseEmitter` / `getActiveRunByThread` / `updateStatus` / `setTerminalStatus` / `createRunDirect(run)`
- 隐藏实现：requestId 幂等键、@Lazy 自注入触发 @Async、终态集合判定、状态轮询循环
- 契约细节见 contract/interfaces.md `## agent` 节 AgentRunGateway

**AgentRegistryModule** — 公共接口（AgentService）：
- `listVisible / getVisibleBySlug / getBySlug / listVisibleSubagents / getVisibleSubagentBySlug`（查询+可见性）、`create / update / delete / setDefault`（写入）、`userCanAccess / userCanManage / isBuiltin`（权限谓词）、`serialize`（VO 组装）、`ensureDefaultAgent / ensureBuiltinAgent / ensureUniqueSlug / normalizeShareConfig`（内置与规范）
- 隐藏实现：BUILTIN_SLUGS 白名单、部门匹配、非管理员降级、slug 冲突后缀
- HTTP 层（3 控制器 11 端点）为本模块实现细节，见 §2 末尾端点排除说明

**MiddlewareChainModule** — 公共接口：
- `AgentMiddlewareChain.executeBefore(context, messages, tools)`（正序）/ `executeAfter(context, response)`（逆序）/ `getToolCallInterceptor(context, toolCall)`（首中）
- `AgentMiddleware` SPI：`getOrder / beforeModelCall / afterModelCall / interceptToolCall / getName`
- 隐藏实现：单中间件异常隔离（warn 后继续）、链排序在 @PostConstruct 一次性完成
- 12 中间件的 order 契约（0-11）见 §5 追溯表

**ContextBackendModule** — 公共接口：
- `ContextBuilder.buildContext(AgentRun) → BaseContext`；`BaseContext.updateFromMap(Map)` / `getConfigurableItems(userRole)`；`AgentBackendManager.getBackend(backendId) / register(backend) / hasBackend / getAllBackends / getBackendInfoVO`；`AgentBackend.getId/getContextSchema/buildGraph/getInfo`
- 隐藏实现：类层次反射填充、技能闭包展开（slug 有效性过滤 + 依赖映射 + 固定路径 `/home/gem/skills/{slug}/SKILL.md`）、backend 注册表冲突覆盖告警

**SandboxConfigModule** — 公共接口（SandboxConfigService）：
- `create / update / delete / get / listByUser(user)`：归属（user_id）+ 管理员写权限（REQ-H04）
- `verify(configId) → VerifyResult`（探活：Docker ping daemon / Cube·E2B 控制面鉴权，可附模板清单）/ `listInstances(configId) → 实例清单（含 paused）`（REQ-H05）
- `resolveCredentials(config) → 有效配置`（凭据引用→环境值解析，缺失在验证/建连时业务错误暴露）/ `maskForResponse(config)`（只回显引用名，REQ-H06；更新空引用不覆盖在 update 内聚）
- `isGloballyEnabled()`：平台总开关三级取值（SysConfig > yml 回退 > 内置默认 false，REQ-H07）
- 隐藏实现：provider 块 JSON 列映射、配置身份指纹计算（供 REQ-H09 重建比对）、保存校验链（EndpointDialGuard.validateEndpoint + 网络模式 bridge/none 白名单 + docker 配置域名字段拒绝）
- HTTP 层（SandboxConfigController）为本模块实现细节，沿端点排除规则不写契约

**SandboxLifecycleModule** — 公共接口（SandboxLifecycleService）：
- `acquire(userId, sessionId) → SandboxHandle`（取绑定或建实例：exited 自恢复、身份变更先销毁重建）/ `lookup(userId, sessionId)`（只查不建）/ `release(userId, sessionId)` / `touch(handle)`
- `onSessionDeleted(userId, sessionId)`（REQ-H10）/ `onConfigIdentityChanged(configId)`（批量 stale 标记，REQ-H09）
- `reclaimIdle()`（@Scheduled，REQ-H02）/ `reconcileOrphans()`（@Scheduled + grace，REQ-H11）
- `SandboxBindingStore`：`get / put / casRebind / markStale / delete / listByConfigId`——RedisSandboxBindingStore（生产，多副本共享）+ InMemorySandboxBindingStore（单实例开发/测试）= 真缝
- 隐藏实现：provider 非确定 ID 的绑定持久（version/provider/sandboxId/configId/createdAt/staleAt）、TTL 固定于创建时（label）、活动标记读取、宽限期判定、create-or-connect 状态机
- 契约细节见 contract agent.SandboxLifecycle（2026-09-28 裁决后随实装重写）

**SandboxProviderClient** — 公共接口（缝 6 的稳定侧，三适配器实现）：
- `create(ctx) → Handle` / `connect(handle)`（exited→resume）/ `delete(handle)` / `list(filter)`（按 metadata 过滤，含 paused）/ `health()` / `capabilities()`（终端/桌面等能力位，本期预留）
- `exec(handle, command, timeout, workdir, env) → {stdout, stderr, exitCode, killed}`
- `readFile / writeFile / stat / makeDir / remove / listDir`——接口 provider 中立，适配器内经 exec+archive 实现，不暴露协议细节
- 隐藏实现：Docker 适配器 = Engine API（exec 的 in-container timeout 包装 + 10s grace、archive 流、`find -printf` 解析、路径清洗、活动标记折叠进 exec 包装器、managed label + TTL label）；Cube/E2B 适配器 = 控制面 REST + envd 数据面、provider TTL 传递、域名级网络策略
- 错误分类归一：timeout/notFound/unavailable/auth/capacity/invalidRequest——上层降级与重建判定只认分类不认协议报文

**SandboxExecutionModule** — 公共接口（SandboxExecutionService）：
- `readFile / writeFile / editFile / execute / listDirectory / grep / glob(userId, sessionId, …) → String`：7 动作与旧 SandboxBackend 等形（成功原文 / 失败 `Error: …` 文本），语义升级见 REQ-H03/H12
- `stageAttachments(userId, sessionId, uploads)`：附件实体拷入 `/workspace/input`（REQ-H13，AttachmentMiddleware 调用）
- `syncSkills(userId, sessionId, slugs)`：技能物化到 `/workspace/skills/{slug}`（SkillsMiddleware 调用；沙箱不可用时虚拟文件系统 SkillReadonlyBackend 仍提供降级读）
- `resolveDegradation(userId, sessionId, appConfigRef) → 分支判定`：总开关关 / 应用未引用或配置禁用 / 连接失败——供 FilesystemMiddleware 工具描述与错误文本区分三因（REQ-H03）
- 隐藏实现：路径钳制（经 SandboxWorkspaceLayout 谓词）、exec 超时（工具超时 + 10s grace）、输出截断（256KB）、降级文本三原因区分、暂存与物化的 provider 写入编排

**SandboxWorkspaceLayout** — 公共接口：常量 `ROOT=/workspace`、`INPUT_DIR=/workspace/input`、`OUTPUT_DIR=/workspace/output`、`SKILLS_ROOT=/workspace/skills`；谓词 `isReadable(path)`（/workspace 全部）/ `isWritable(path)`（拒 input 与 skills 子树）/ `clamp(path)`（清洗+规范化+保留路径拒绝）/ `hint()`（工具描述路径提示）

**EndpointDialGuard** — 公共接口：`validateEndpoint(url, allowPrivate)`（配置保存时）/ `guardDial(address)`（建连时对解析 IP 复查，防 hostname 先验后变）——云 metadata/link-local 恒拒，私网须显式 allowPrivate

**RunEventModule** — 公共接口：
- `RunEventPublisher.pushEvent(runId, eventType, payload)` / `getEvents(runId)`
- 隐藏实现：seq=毫秒时间戳、per-run 同步列表

**端点排除说明**：AgentController（7 端点）/ AgentConfigController（2 端点）/ AgentRunController（5 端点）共 11 个 HTTP 端点全部为 AgentRegistryModule / RunOrchestrationModule 的实现细节，不写契约；HTTP 暴露面已在各控制器 javadoc 列明（AgentController.java:25-34、AgentRunController.java:19-26、AgentConfigController.java:22-27）。

## 3. Seam Diagram

**缝 1：工具能力协议（消费 tools-skills-mcp 上游）**
- What varies：四类能力（内置/API 工具/MCP/技能）的定义与执行实现
- Adapters：ToolRegistry（解析）、ToolExecutorFactory（执行路由）——2 个 = 真缝
- Stable side：ExecutionEngineModule（只认 ToolDefinition + ToolResult）
- Placement rationale：引擎不感知工具存储差异；上游契约见 contract/interfaces.md `## tools-skills-mcp` 节 ToolRegistry/ToolExecutorRouter
- ⚠ 缝上偏差：Filesystem/Database/SubAgent 等中间件**绕过 ToolRegistry** 直接 new ToolDefinition 注册内置工具，其执行器类型（sandbox/database/knowledge/builtin）需在 ToolExecutorFactory 有对应注册——**已注册，R3 实证（2026-09-24）**：三执行器均为 @Component 且 getType() 分别返回 "sandbox"/"database"/"subagent"（SandboxToolExecutor.java:21,28-30、DatabaseToolExecutor.java:24,31-33、SubAgentToolExecutor.java:19,23-25）；ToolExecutorFactory 经 Spring 注入 `List<ToolExecutor>` 自动收集全部实现 Bean 按 getType() 匹配（ToolExecutorFactory.java:28-35），agent 模块类在组件扫描范围内即入清单——无需显式注册动作。执行器缺失时引擎报「No executor for type」（AgentEngine.java:651-654）

**缝 2：LLM 供应商（系统边界）**
- What varies：模型 API（流式/同步/工具调用/thinking）
- Adapters：LlmService（fastrag-ai，Ollama 兼容网关）——1 个 = 假设缝（当前单实现，立缝依据是外部系统必然多变，保留）
- Stable side：ExecutionEngineModule
- Placement rationale：网关协议、超时、重试归 fastrag-ai；引擎只出 ChatMessage 收 ChatResponse/Flux

**缝 3：智能体 Backend 插件点**
- What varies：智能体类型（Chatbot / SubAgent / 未来新类型）的上下文 schema 与图模板
- Adapters：ChatbotAgentBackend、SubAgentBackend——2 个 = 真缝
- Stable side：ContextBackendModule（AgentBackendManager 注册表）+ 编排层
- Placement rationale：新智能体类型 = 新实现类 + agent.backend_id 引用，零改核心

**缝 4：中间件扩展点**
- What varies：横切能力（知识库/技能/MCP/数据库/文件/总结/待办/修补/重试/子智能体/附件/模型路由）
- Adapters：12 个 AgentMiddleware 实现——2+ = 真缝（本特性最活跃的缝）
- Stable side：AgentMiddlewareChain 三阶段协议 + ExecutionEngineModule 调用位
- Placement rationale：能力注册以「工具定义」为统一货币，行为注入以「消息修改」为统一手段

**缝 5：虚拟文件系统**
- What varies：智能体可见的文件命名空间
- Adapters：SkillReadonlyBackend——1 个 = **假设缝不立**（§4 给出处理建议）
- Stable side：FileSystemManager 前缀路由
- **处置更新（2026-09-28）**：沙箱可用时技能物化到沙箱 `/workspace/skills/{slug}`（SandboxExecutionModule.syncSkills），技能读取走沙箱主路径；沙箱不可用时 SkillReadonlyBackend 仍从 SkillService 渲染降级读（SkillsMiddleware 激活拦截不变）。挂载点常量 `/home/gem/skills` → `/workspace/skills`（收进 SandboxWorkspaceLayout）。双路径由布局谓词路由，不抽新接口——「不立缝」结论维持。

**缝 6：沙箱后端协议（系统边界缝，2026-09-28 新增）**
- What varies：沙箱实例的宿——Docker daemon / Tencent CubeSandbox / E2B 兼容控制面（spec §4.8：三后端共享会话绑定、生命周期、工作区行为契约）
- Adapters：DockerSandboxAdapter（Engine API 直连，ADR-0006）、CubeSandboxAdapter、E2BSandboxAdapter（控制面 REST + envd 数据面）——3 个 = 真缝
- Stable side：SandboxLifecycleModule + SandboxExecutionModule（只认 SandboxProviderClient 接口与归一错误分类）
- Placement rationale：多后端差异全部压在协议适配层，绑定/对账/降级/路径策略在其上层单份实现；新宿（如 gVisor 变体、未来桌面模板）= 新适配器 + capabilities 能力位声明。附属缝：SandboxBindingStore（Redis/内存 2 适配器）——测试与单实例开发需要假件，WeKnora 同款先例。

**系统边界（非缝）**：Docker daemon 与 Cube·E2B 控制面/数据面（SandboxProviderClient 三适配器对接，实装后为真实系统边界；连接哪个 endpoint 由 EndpointDialGuard 守护）；Redis（沙箱绑定存储〔新增〕+ KB ACL 缓存；运行事件 pub/sub 转发**仍未落地**，RunEventPublisher.java:67 为 TODO——仲裁见 §6）；MySQL（agent/agent_run/sandbox_config 表）。

## 4. Deepening Opportunities

- **ExecutionEngineModule**：吸收 LegacyExecutorModule（AgentExecutor 已废弃但仍被编排层注入，AgentRunServiceImpl.java:55）——编排切 executeSync 后删除旧类；吸收 AgentGraph 消费（让引擎从 graph 而非散装 context 字段取参，兑现 Backend+Graph 叙事）。
- **RunOrchestrationModule**：吞掉「取消信号传播」复杂度（当前取消是纯 DB 翻转，REQ-C04 的 cancelled 可被引擎终态覆盖的竞态应内聚在模块内解决：执行线程感知取消 + 终态写保护）；吞掉 SSE 断点续传（afterSeq 已是契约位，事件回放应内聚于此而非前端）。
- **RunEventModule**：补齐「发布→存储→订阅推送」闭环（接 SSE 桥与 Redis pub/sub，兑现 CONTEXT.md SSE 术语与多实例部署），或降级内联——现状写而不读最差。
- **ContextBackendModule**：把 AgentConfigController 的 configurableItems 空占位接到 `AgentBackend.getInfo(includeConfigurableItems=true)`（能力已存在：ChatbotAgentBackend.java:84-93，仅控制器未接线，AgentConfigController.java:62-64）。
- **沙箱模块族（SandboxConfig/Lifecycle/Execution/Provider/Layout/DialGuard，2026-09-28 重写原 SandboxModule 条目）**：实装次序——Docker 适配器先行（Cube/E2B 接口就位、错误分类对齐、以契约测试桩先行，真环境接入属部署验收）；Lifecycle 吸收交互终端/桌面能力位（capabilities 已预留，二期）；Execution 吸收产物收集（output 快照比对 + 下载链路，二期，对齐 WeKnora output_links）；Config 吸收 workspace-policy（若未来引入租户工作空间）。BindingStore 的 Redis 实现先行、内存实现作测试假件。
- **MiddlewareChainModule**：None（结构已到位）。

## 5. Parent Trace

| Design 模块/缝 | Spec REQ |
|---|---|
| AgentRegistryModule | REQ-A01~A09、REQ-B01~B03、REQ-I01~I02 |
| RunOrchestrationModule | REQ-C01~C08 |
| RunEventModule | REQ-D01~D03（D04 共有） |
| ExecutionEngineModule（缝 1/2 消费方） | REQ-E01~E08、REQ-D04 |
| MiddlewareChainModule（缝 4） | REQ-F01~F03、REQ-H03（Filesystem 降级） |
| ContextBackendModule（缝 3） | REQ-G01~G02 |
| SandboxModule（系统边界 Docker） | REQ-H01~H02 |
| SandboxConfigModule（含 EndpointDialGuard 消费） | REQ-H04、REQ-H05、REQ-H06、REQ-H07、REQ-H15（保存校验位）、REQ-H16（2026-09-28 补锚） |
| SandboxLifecycleModule + SandboxBindingStore 附属缝 | REQ-H01、REQ-H02、REQ-H08、REQ-H09、REQ-H10、REQ-H11（2026-09-28 补锚） |
| SandboxExecutionModule（含 SandboxWorkspaceLayout 谓词、暂存/物化） | REQ-H03、REQ-H12、REQ-H13（2026-09-28 补锚） |
| SandboxProviderClient 缝（三适配器；Docker 适配器为主执行位） | REQ-H14（容器隔离基线）、REQ-H15（网络模式执行位）、REQ-H02（provider TTL 语义）、REQ-H08（exited 自恢复）、REQ-H16（建连复查位）（2026-09-28 补锚） |
| MiddlewareChainModule（Filesystem/Attachment/Skills 三中间件为沙箱调用方） | REQ-H03（降级注册与错误文本）、REQ-H13（AttachmentMiddleware 触发暂存）（2026-09-28 补锚） |
| 非功能：NFR01→RunOrchestrationModule（AsyncConfig 线程池）、NFR02→ExecutionEngineModule、NFR03→ExecutionEngineModule、NFR05→跨模块 DDL 偏差（登记见 contract/data.md） |
| AgentRegistryModule | REQ-A02（2026-09-24 review 补锚：写操作（更新/删除/设默认/改配置）须过管理权限校验（=创建者），无权返回 403 语义业务错误） |
| AgentRegistryModule | REQ-A03（2026-09-24 review 补锚：默认智能体永存保证——查询默认时若无则自动创建内置 default-chatbot） |
| AgentRegistryModule | REQ-A04（2026-09-24 review 补锚：设默认互斥——新默认生效前所有旧默认必须被清除） |
| AgentRegistryModule | REQ-A05（2026-09-24 review 补锚：内置智能体（slug 白名单）不可删除（409 语义），序列化标记 builtin 与 permissionLocked） |
| AgentRegistryModule | REQ-A06（2026-09-24 review 补锚：slug 全局唯一且自动去重——空 slug 按名称派生、冲突追加序号后缀） |
| AgentRegistryModule | REQ-A07（2026-09-24 review 补锚：删除仅移除 agent 记录本身，不级联清理运行记录（现状口径）） |
| AgentRegistryModule | REQ-A09（2026-09-24 review 补锚：配置写入把 context 字段映射为 configJson，与 shareConfig 一并更新） |
| AgentRegistryModule | REQ-B02（2026-09-24 review 补锚：非管理员写入 shareConfig 强制降级 private 并清空部门/用户名单） |
| AgentRegistryModule | REQ-B03（2026-09-24 review 补锚：子智能体默认不出现在普通列表，显式包含才返回且有「非子智能体」类型校验） |
| AgentRegistryModule | REQ-I02（2026-09-24 review 补锚：内置智能体集合恰为 6 个约定 slug） |
| ExecutionEngineModule（缝 1/2 消费方） | REQ-E04（2026-09-24 review 补锚：执行器缺失/工具未找到不中断循环——以错误文本消息回灌，LLM 据此继续决策） |
| MiddlewareChainModule（缝 4） | REQ-F03（2026-09-24 review 补锚：链组装顺序是可测试的确定性契约（既有顺序测试锁定）） |

## 6. 仲裁说明（与上游/feature-map 的冲突）

1. **「RunEventPublisher（Redis pub/sub→SSE）」不成立**（feature-map.md:199、跨特性表 :227；CONTEXT.md SSE 术语「跨实例经 Redis pub/sub 转发」）：代码证据为本地 `ConcurrentHashMap` 内存存储 + `// TODO: Redis pub/sub integration`（RunEventPublisher.java:44-67），且 `getEvents` 无消费方。Redis 直连散布 8 处的说法中 agent 侧实际为零。本文档按代码现状书写，feature-map 条目待主控勘误。
2. **「测试 9 个」实为 8 个**：src/test 下实测 8 个测试类（feature-map.md:199 记 9）。
3. **「应用↔智能体调用关系」INFERRED 升级 CERTAIN**：AppServiceImpl（fastrag-application）:665 以 fakeRun（会话 ID 冒充 runId、不落 agent_run）调用 `agentEngine.executeStream`，完成回调内做敏感词过滤与消息落库（AppServiceImpl.java:642-669）——应用对话是引擎流式模式的最大消费方，但**不经过** RunOrchestrationModule（无 run 记录）。
4. **DDL 双处同步缺口**：agent/agent_run 建表 DDL 仅在 SchemaInitializer.java:149-204，init-scripts/schema.sql 零 agent 表——违反后端硬约定，登记于 spec NFR05 与 contract/data.md，建议主控统一补齐 schema.sql。
5. **沙箱链勘误与目标态（2026-09-28）**：feature-map.md:199「sandbox/SandboxService（Docker 沙箱 + idleReaper；⚠ 实为降级桩 mock 连接）」——本设计按目标态书写（多后端实装，REQ-H01~H16），桩现状证据保留在 spec 各 REQ 的〔证据〕行内；feature-map 条目随实装落地后由 implement 链更新。旧「确定性 ID / 容器内 HTTP」两个设计面分别被 REQ-H01 修订与 ADR-0006 取代，原文以删除线保留可追溯。

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。

沙箱实装新增（2026-09-28）：**ADR-0006**（容器侧协议收敛 Engine API 直连、容器内零服务端——[adr/0006-sandbox-engine-api-direct.md](adr/0006-sandbox-engine-api-direct.md)）与 **ADR-0007**（沙箱凭据引用式存储、不落库加密——[adr/0007-sandbox-credential-by-reference.md](adr/0007-sandbox-credential-by-reference.md)）。
