# 智能体运行时（agent） — 实现文档

> 记录既有代码如何实现 contract/interfaces.md `## agent` 接口族（2026-09-28 沙箱实装后为：ExecutionEngine / AgentRunGateway / MiddlewareChain / ContextAssembler / 沙箱四契约 SandboxConfigStore + SandboxLifecycle + SandboxProviderGateway + SandboxExecutionFacade）。红线：不贴整段源码，伪代码级。

## 1. Code File Map

模块根：`fastrag-modules/fastrag-agent/src/main/java/com/fastrag/module/agent/`

```
├── controller/
│   ├── AgentController.java          7 端点：list/default/create/get/update/delete/set_default（写操作 userCanManage 门禁）
│   ├── AgentConfigController.java    2 端点：config 读写（configurableItems 空占位；context→configJson 映射）
│   └── AgentRunController.java       5 端点：runs 创建/详情/取消/SSE events/active_run
├── service/
│   ├── AgentService.java (+impl/)    台账：CRUD/可见性三级/管理谓词/默认互斥/内置保护/slug 唯一化/序列化
│   └── AgentRunService.java (+impl/) 编排：幂等创建/@Async 执行/状态机/取消/SSE 轮询/直插
├── executor/
│   ├── AgentEngine.java              执行引擎（流式+同步统一循环）——契约 ExecutionEngine 的实现体；M2-3 起循环内 toolsDirty 重算、M3-3 起 install_skill 引擎侧条件过滤
│   ├── ~~AgentExecutor.java~~        旧执行器已删除（M2-4：编排层切 AgentEngine，装配单点闭合）
│   ├── AgentResult/LlmStreamResult   双模式结果载体
│   ├── ModelConfig.java              模型配置值对象（builder+default_ 兜底）
│   └── Sandbox/Database/SubAgentToolExecutor  三个专用执行器（type=sandbox/database/subagent）
├── middleware/                        链 + SPI + 12 中间件（order 0-11）+ ToolCallInterceptor
├── context/                           BaseContext 配置元模型/@ConfigField/ContextBuilder/User
├── backend/                           AgentBackend SPI + Manager 注册表 + Chatbot/SubAgent 两实现 + AgentGraph
├── state/                             BaseState/ChatBotState/SubAgentRunState（artifacts 与子运行台账合并模型）
├── sandbox/                           沙箱模块族（2026-09-28 实装，旧降级桩三件已删）
│   ├── SandboxWorkspaceLayout         /workspace 布局单一事实源：常量/isReadable/isWritable/clamp/hint（REQ-H12）
│   ├── security/EndpointDialGuard     endpoint SSRF 防护：validateEndpoint（保存时）/guardDial（建连时 IP 复查）（REQ-H16）
│   ├── SandboxHandle                  实例句柄（sandboxId/provider/configId/state/lastUsed；SandboxConnection 演进）
│   ├── SandboxBindingStore + BindingRecord          绑定存储接口（get/put/casRebind/markStaleByConfigId/delete/listByConfigId/listAll）
│   ├── memory/InMemorySandboxBindingStore           进程内实现（compute 原子 CAS；单实例开发/测试）
│   ├── redis/RedisSandboxBindingStore               Redis 实现（key=sandbox:binding:{userId}:{sessionId}，无 TTL，Lua CAS，SCAN 反查）
│   ├── SandboxLifecycleService (+impl/)             绑定与生命周期：acquire 四路径/reclaimIdle/reconcileOrphans/onSessionDeleted/onConfigIdentityChanged（REQ-H01/02/08~11）
│   ├── SandboxExecutionService                      执行门面：7 动作+stageAttachments+syncSkills+resolveDegradation（REQ-H03/12/13）
│   ├── provider/SandboxProviderClient               后端协议缝接口（create/connect/delete/list/health/capabilities+exec+文件面+lastActivity）
│   ├── provider/SandboxProviderRegistry             类型→适配器解析（require/find，缺失抛 UNSUPPORTED）
│   ├── provider/SandboxProviderException+ErrorKind  归一六分类错误（TIMEOUT/NOT_FOUND/UNAVAILABLE/AUTH/CAPACITY/INVALID_REQUEST/UNSUPPORTED）
│   ├── provider/CreateContext/ExecRequest/ExecResult/DirEntry     缝上的值对象
│   ├── provider/docker/DockerSandboxAdapter         Docker 适配器（docker-java Engine API 直连，ADR-0006；容器内零服务端）
│   ├── provider/cube|e2b/*SandboxAdapter            预留适配器（全操作抛 UNSUPPORTED，javadoc 登记实装锚 TODO-CUBE/E2B-1..4）
│   └── config/SandboxConfigService(+impl/) + EffectiveConfig       配置台账：CRUD/校验链/凭据引用解析/指纹/验证/库存/总开关（REQ-H04~07/15/16）
├── event/RunEventPublisher.java       运行事件内存存储（写而不读）
├── fs/                                VirtualFileSystem SPI（~~FileSystemManager 前缀路由 + SkillReadonlyBackend~~ 已删除，M3-6——技能读取走 SkillsMiddleware 拦截协议，虚拟文件系统全仓无生产调用方）
├── runner/AgentDataInitializer.java   启动播种内置智能体（ApplicationReadyEvent）
├── entity/ + mapper/ + dto/           Agent/AgentRun/SandboxConfig 实体、查询、DTO+VO（SandboxConfig 9 件，2026-09-28 新增）
└── config/                            AsyncConfig(agentTaskExecutor 线程池)/AgentWebMvcConfig(CurrentUser 解析器)
```

> 控制器：原 3 个 14 端点 + SandboxConfigController（/api/sandbox-configs 7 端点，HTTP 层为薄包装不立契约）。旧 `sandbox/SandboxService.java`（降级桩）/`SandboxConnection.java`/`SandboxBackend.java`（容器内 HTTP 客户端）已删除——ADR-0006 取代容器内 HTTP 协议，SandboxHandle/SandboxLifecycleService/SandboxExecutionService 演进承接。2026-10-06 技能域修复：`AgentExecutor.java`（M2-4）与 `fs/` 两件（M3-6）删除；BaseContext 新增 transient 技能缓存字段（runtimeSkillEntities/skillsLoaded/syncedSkillSlugs/toolsDirty）。

## 2. Interface Implementations

### ExecutionEngine（from contract: agent.ExecutionEngine）

**Logic Flow:**
`executeStream(context, emitter, modelConfig, run, onComplete)`：
1. resolveTools(context.tools) → toOpenAITools → 剔除 install_skill（M3-3：仅当 context.skills 非空或沙箱启用时保留）；发 init 事件
2. middlewareChain.executeBefore（正序；中间件可改消息/加工具）→ **重建** openaiTools
3. FOR round ∈ [0, maxSteps)（maxSteps=context.maxSteps>0 ? : 15）：
   - recomputeToolsIfDirty（M2-3）：context.toolsDirty 置位 → 重走 resolveTools→toOpenAITools 并清标志（技能激活追加的依赖工具自此轮生效，一轮至多重算一次）
   - llmService.streamChatWithTools → consumeStream（CountDownLatch 阻塞；THINKING→thinking 事件、CONTENT→message 事件、TOOL_CALL_DELTA→按 id/name 合并、FINISH→记 finishReason；超时 stream-timeout=240s 抛）
   - middlewareChain.executeAfter（逆序）
   - IF 无 toolCalls THEN break（fullAnswer 即最终答案）
   - 消息历史追加 assistant(content=null, toolCalls)——content 置 null 规避 SiliconFlow 对空串的拒绝
   - FOR 每个 toolCall：拦截器命中（isSkip）→ 拦截消息回灌 + tool_result(intercepted=true) 事件；否则 findTool 失败 → 错误消息回灌；否则 executeTool（参数 JSON 解析→tryRepairJsonArguments 状态机修复→JsonSchemaValidator→ToolContext(uid/runId/query/extensions)→executorFactory 分派）→ tool_result 事件 + 消息回灌（截 5000）+ pushEvent 留痕 + 失败计数
   - IF max(失败计数)≥3 THEN 注入「停止重试」system 消息并 clear 计数
   - pushAgentState（todos/artifacts → agent_state 事件）
4. IF fullAnswer 空且达上限 THEN 兜底文案 + 补发 content
5. onComplete(answer, thinking, toolCallsJson)（AppServiceImpl 在此做敏感词过滤+消息落库）→ end 事件 → emitter.complete
6. 任意异常 → error 事件 → complete（complete 失败再 completeWithError）

`executeSync(run, context)`：同构循环，差异：chatWithTools 同步调用（无 thinking）；modelConfig 缺省回退 ModelConfig.default_；拦截消息仅回灌不发事件；每调用 publishToolCallEvent + AgentResult.ToolCallRecord 留痕；终态 AgentResult.success/error。

**Module Interactions:**
- Calls：LlmService（fastrag-ai 系统边界）、ToolRegistry/ToolExecutorFactory（上游 tools-skills-mcp 契约）、AgentMiddlewareChain、RunEventPublisher.pushEvent、JsonSchemaValidator（common）
- Called by：AgentRunServiceImpl.executeRunAsync（M2-4 起直连 executeSync）、SubAgentMiddleware（executeSync 子任务委派）、AppServiceImpl（executeStream，fakeRun）
- Shared state：无（无状态组件；运行态全在 BaseContext/消息列表局部变量）

**Decisions & Deviations:**
- ① ~~契约偏差：编排层仍注入旧 AgentExecutor~~ **已消解（M2-4，2026-10-06）**：AgentRunServiceImpl 切 AgentEngine.executeSync，AgentExecutor 已删除。行为注记：达最大轮次的终态随引擎收敛——旧执行器该场景 error→run=failed，引擎 success("")→run=completed（保留引擎语义，SubAgent 路径同源）
- ② 失败熔断选择「提示注入」而非「硬中断」：把裁决权留给 LLM（代码取舍：保留自主性的同时阻止死循环）
- ③ 事件推送全部 try-catch 吞异常（sendSseEvent）：SSE 通道失败不反噬执行主链路（NFR02）
- ④ executeTool 的 runId 以 run.id 覆盖 context.runId（:641-643）：fakeRun（应用对话）场景下 runId=sessionId——留痕键与会话键合流的实现细节

**Parent Trace:** AgentEngine → agent.ExecutionEngine → ExecutionEngineModule → REQ-E01~E08、D04

### AgentRunGateway（from contract: agent.AgentRunGateway）

**Logic Flow:**
`createRun(dto, user)`：
1. getBySlug(dto.agentId)；null→404；userCanAccess=false→403
2. requestId 空白→UUID；selectByRequestId 命中→直接返回 buildRunData(既有)
3. 落库 AgentRun{id=UUID, status=pending, runType 缺省 chat, inputPayload={query, model_override}}
4. `self.executeRunAsync(run)`（@Lazy 自注入代理触发 @Async("agentTaskExecutor")）→ 返回 runData

`executeRunAsync(run)`：
1. status=running 落库 → 2. contextBuilder.buildContext（抛→catch 转终态）→ 3. agentEngine.executeSync(run, context)（M2-4 起直连引擎）→ 4. result.isSuccess ? completed : failed(execution_error, result.error) → finishedAt 落库
5. 任意异常 → failed(system_error, e.message) 落库

`cancelRun(runId, user)`：getRunForUser（=getRun，无属主校验）→ IF status ∉ {pending,running} THEN 400 → ELSE status=cancelled+finishedAt 落库（**无执行线程信号**）
`streamEvents(runId, afterSeq, user)`：SseEmitter(30min) + 回调挂日志 → sseExecutor 线程 WHILE true：查 run→null 则 complete→终态则发 end{status,errorType,errorMessage} 后 complete→否则 sleep(2000)。**afterSeq 未消费；RunEventPublisher 事件不回放**
`getActiveRunByThread`：selectByThreadIdAndStatus（pending/running LIMIT 1）
`createRunDirect(run)`：补 id/createdAt→insert（不触发异步）

**Module Interactions:**
- Calls：AgentService（存在性/可见性）、ContextBuilder、AgentEngine、AgentRunMapper、agentTaskExecutor（AsyncConfig）
- Called by：AgentRunController（5 端点）、SubAgentMiddleware（createRunDirect + 侧读 updateStatus/setTerminalStatus）
- Shared state：agent_run 表（唯一持久状态）；sseExecutor 为类内自建 cachedThreadPool（非 Spring 托管）

**Decisions & Deviations:**
- ① ~~走旧 AgentExecutor~~ 已消解（M2-4，见 ExecutionEngine Deviations①）；② 取消无信号传播：cancelled 可被执行线程随后的 completed/failed 覆盖（last-write-wins，无锁无版本号）；③ streamEvents 选择「DB 轮询」而非「事件订阅」——与 RunEventPublisher 写而不读互为因果（事件通道闭环未建，先以状态轮询保底）；④ @Lazy self 注入是为绕 @Async 自调用失效的 Spring 惯用法

**Parent Trace:** AgentRunServiceImpl → agent.AgentRunGateway → RunOrchestrationModule → REQ-C01~C08、D02~D03

### MiddlewareChain（from contract: agent.MiddlewareChain）

**Logic Flow:**
`@PostConstruct init`：Spring 注入全部 AgentMiddleware bean → sort(getOrder)（一次性定序）
`executeBefore`：FOR m ∈ 正序：try context=m.beforeModelCall(ctx, msgs, tools) catch→warn 继续
`executeAfter`：FOR i ∈ 逆序：try 同上
`getToolCallInterceptor`：FOR 正序：首个非 null 即返；null 兜底
12 中间件行为要点（order）：Context 0=模型加载桩（仅日志，等外部注入 modelConfig）；Filesystem 1=注册 read_file/write_file/edit_file/execute/list_directory/grep/glob，拦截路由 SandboxBackend，沙箱不可用仍注册但操作报错；Attachment 2=runtimeState.uploads→system prompt 追加；KnowledgeBase 3=注册 list_kbs/query_kb/find_kb_document（knowledges 非空时）；Skills 4=三段式渐进加载（M2 后）：首轮装配（绑定 id→listByIds→enabled 过滤→与 listAccessible 交集→expandSkillClosure 闭包→实体缓存入 context；run 级缓存，后续轮零查询 M2-5）→ prompt 注入（描述截 600 字符，M2-6）→ 沙箱 syncSkills（slug 集不变跳过）；拦截 read_file(SKILL.md) 返回全文（`# name+desc+---+content`，DB content 为源，M2-1）并按依赖闭包追加规范化工具 id（`tool:xxx`→裸 id，M3-2）+ 置 context.toolsDirty；SubAgent 5=注册 task 工具+拦截委派（见下）；Summarization 6=token 估算超阈值（默认 8000）压缩中段保留 system+尾 6 条；TodoList 7=拦截 write_todos 解析参数更新 runtimeState.todos；PatchToolCalls 8=after 修补空 ID/空名（参数名反推）/未闭合 JSON；ModelRetry 9=写 RetryConfig 入 runtimeState（默认 2 次指数退避，引擎侧未消费）；Database 10=每库注册 list_tables/describe_table/query_database；Mcp 11=mcp_tool 表 enabled 工具注册为 type=mcp 定义。
SubAgentMiddleware 拦截路径：校验 subagent_type 存在→建 childRun（threadId=新子线程、requestId=UUID、runType=subagent、input={query,description}）→createRunDirect 落库→childContext 构建→agentEngine.executeSync→updateSubAgentState（SubAgentRunState 入 ctx）→结果文本作拦截消息回灌。

**Module Interactions:**
- Calls：各中间件各自的下游——SkillService、AgentRunService、AgentEngine、DbInstanceService（tools 模块）、mcp_tool mapper、SandboxBackend、LlmService（Summarization 摘要调用）
- Called by：AgentEngine（三钩子调用位）
- Shared state：BaseContext.runtimeState（todos/artifacts/uploads/retryConfig/summaryPrompt 的运行期共享黑板）

**Decisions & Deviations:**
- ① 三钩子异常一律隔离（warn）——契约 E_MW_ISOLATED 的实现形态；② ModelRetryMiddleware 只写配置不执行重试（javadoc 自述由引擎读取执行，AgentEngine 实际未读 retryConfig——**配置写而不读**，与 RunEventPublisher 同型债务）；③ ContextMiddleware 桩化：跨模块 ModelRecordMapper 未接入，模型配置实际由调用方（AppServiceImpl/ContextBuilder 缺省链）注入

**Parent Trace:** AgentMiddlewareChain + 12 中间件 → agent.MiddlewareChain → MiddlewareChainModule → REQ-F01~F03、H03

### ContextAssembler（from contract: agent.ContextAssembler）

**Logic Flow:**
`buildContext(run)`：
1. agentMapper.selectById(run.agentId)；null→RuntimeException("Agent not found")
2. backendManager.getBackend(agent.backendId)；null→notFound
3. `backend.getContextSchema().getDeclaredConstructor().newInstance()` 反射实例化
4. configJson 非 null→updateFromMap（类层次遍历 @ConfigField 字段，containsKey 才 set，未知键忽略）
5. setRunId/setUid
6. ~~prepareRuntimeContext~~（M2-4 已删除）：技能装配唯一归属 SkillsMiddleware（绑定 id 解析+闭包+run 级缓存），本类仅保留配置装载/runId/uid
7. RETURN context

**Module Interactions:**
- Calls：AgentMapper、AgentBackendManager、SkillService（tools 模块）
- Called by：AgentRunServiceImpl.executeRunAsync；SubAgentMiddleware——子上下文**经注入的同一 ContextBuilder** 构建（SubAgentMiddleware.java:134 `contextBuilder.buildContext(childRun)`，复用本类实现而非平行实现）‹CERTAIN（2026-09-24 深证，改正原「backendManager 自建/平行实现」推断）›
- Shared state：无（每次新建实例）

**Decisions & Deviations:**
- ① ~~技能加载不按用户过滤~~ **已消解（M2-2，2026-10-06）**：SkillsMiddleware 以绑定 id→listByIds∩listAccessible（可见+启用）收敛可见性，被丢绑定 warn 日志含 id；② ~~技能路径硬编码 /home/gem/skills~~ 已随装配段删除消解（现行 `/workspace/skills/{slug}/SKILL.md`，SkillsMiddleware 单点）；③ modelConfig 不在本类填充（transient 字段，由 ContextMiddleware 桩/调用方注入）——REQ-G02 的 model_override **全仓无消费点**（仅落库，grep 实测；AppServiceImpl 构建 ModelConfig 取应用绑定模型，与 run 级覆盖无关，2026-09-24 深证改正原判断）

**Parent Trace:** ContextBuilder + BaseContext + AgentBackendManager → agent.ContextAssembler → ContextBackendModule → REQ-G01~G02

### SandboxLifecycle（from contract: agent.SandboxLifecycle）

> **2026-09-28 实装重写**：旧降级桩实现（~~`@PostConstruct` Class.forName 探测 / sha256 确定性 ID / mock 连接 / 600s 内存回收~~）已删除，由下列四个实现节承接；桩语义原文见 git 历史与 contract 节首废止注记。

### SandboxConfigStore（from contract: agent.SandboxConfigStore）— SandboxConfigServiceImpl

**Logic Flow:**
```
create(dto, user): user 判空 → 类型∈{docker,cube,e2b,disabled} → provider 块必填性
  → normalizeNetworkMode（docker 缺省 bridge）
  → validateChain：docker 带域名列表→41001；network_mode∉{bridge,none}→41001；
    EndpointDialGuard.validateEndpoint(host, allowPrivate)（SecurityException/IAE→41001）；引用名格式
  → 同 user 名唯一 → 组装实体（JSON 列 String 承载）→ fingerprint 初算 → insert
update(configId, patch, user): requireWritable（归属者或管理员，否则 403）
  → 块浅合并（空 api_key_ref 不覆盖，REQ-H06/ADR-0007）→ validateChain（同上）
  → oldFingerprint 暂存 → 重算 fingerprint
  → 换凭据/换底模 → cordon_at=now（data.md 语义）
  → fingerprint 变更 → bindingStore.markStaleByConfigId(configId)（REQ-H09 批量 stale 接线）
  → updateById
delete: requireWritable → listInstancesInternal 非空（含 paused）→ 40901；否则物理删
resolveCredentials: *ApiKeyRef → System.getenv（缺失→41003，use 时 fail-loud）→ EffectiveConfig
verify: resolveCredentials → probe CreateContext → registry.require(type).health
  （UNSUPPORTED/UNAVAILABLE→41002 附原因）
listInstances: registry.require(type).list(managed+configId metadata, running|paused|transitioning)
isGloballyEnabled: sys_config('sandbox.enabled') 只读 mapper → @Value yml 回退 → false
```

**Decisions & Deviations:** JSON 列以 String 承载、服务层 Jackson Map 视图转换（单测免真库）；Cube/E2B probe 凭据临时经 envVars 键 `com.fastrag.sandbox.api-url/-api-key` 携带（预留适配器收敛时收敛协议）；disabled 类型 verify→41004、库存恒空、delete 直删；平台配置 UI 接线缺口在实现类 javadoc 登记（当前三级取值的第一级读 sys_config 表已通）。

**Parent Trace:** SandboxConfigController + SandboxConfigService(+impl) + EffectiveConfig → agent.SandboxConfigStore → SandboxConfigModule → REQ-H04~H07、H15、H16

### SandboxLifecycle（from contract: agent.SandboxLifecycle）— SandboxLifecycleServiceImpl

**Logic Flow:**
```
acquire(userId, sessionId, cfg): binding=store.get
  binding 命中且 configId 一致且非 stale → provider.connect（exited/created 自愈）→ touch → 返回
  binding 命中但 staleAt 非空 或 provider NOT_FOUND → delete(旧) → create 新 → casRebind
  binding 命中且 provider 侧正常 → 原样返回（跨轮保持）
  无绑定 → registry.require(cfg.type).create(buildCreateContext(cfg, userId, sessionId))
    → store.put（失败不留半截绑定，异常按分类上抛）
reclaimIdle()（60s 定时）: provider.list(managed) 逐实例 ttl=provider.idleTtl(handle)（容器 label，
  进程内记录兜底）→ lastActivity 超 ttl → delete + store.delete；UNSUPPORTED 类型跳过（provider TTL 承担）
reconcileOrphans()（300s 定时）: list 全量 vs bindingStore.listAll → 无主且 lastActivity 早于 grace(600s)
  → delete；UNAVAILABLE 容忍 warn
onSessionDeleted: store.delete + provider.delete
onConfigIdentityChanged(configId): store.markStaleByConfigId（由 SandboxConfigServiceImpl.update 指纹变更触发）
@PreDestroy: 全量 release
```

**Module Interactions:** Calls：SandboxProviderRegistry/Client、SandboxBindingStore（Redis 生产/内存测试）、@Value agent.sandbox.*；Called by：SandboxExecutionService（唯一入口）、@Scheduled 两个回收任务、SandboxConfigServiceImpl.update（REQ-H09 钩子）；Shared state：绑定真身在 Redis（跨进程），进程内仅 lastUsed 缓存。

**Decisions & Deviations:** 时钟经 package 可见 setNow 注入（测试）；acquire 的 NOT_FOUND 处理覆盖「绑定在而实例失」（provider 侧回收先行于 Redis 的竞态）。

**Parent Trace:** SandboxLifecycleService(+impl) + SandboxBindingStore 双实现 → agent.SandboxLifecycle → SandboxLifecycleModule → REQ-H01、H02、H08~H11

### SandboxProviderGateway（from contract: agent.SandboxProviderGateway）— DockerSandboxAdapter（+Cube/E2B 预留桩）

**Logic Flow:**
```
client(endpoint): ConcurrentHashMap<host|tls|allowPrivate, DockerClient> 池；缺省探测 DOCKER_HOST→
  unix:///var/run/docker.sock；tcp/http(s) 建连前 EndpointDialGuard.guardDial；LinkageError→UNAVAILABLE
create(ctx): 容器名 fastrag-sbx-{sha256(configId|sessionKey) 前 12}（确定性，重连幂等）
  → ensureImage（inspect→pull→失败 INVALID_REQUEST——锚例：不判 NOT_FOUND 防误重建）
  → HostConfig 加固基线（REQ-H14）：CapDrop ALL + CapAdd 7 白名单 + no-new-privileges +
    Memory==MemorySwap + NanoCPUs + PidsLimit + NetworkMode + Init(true)
  → Labels=managedMetadata()+idle-ttl；Cmd=bash -c "sleep infinity"；WorkingDir=/workspace
  → 同名已存容器（dead 静默删重建，其余复用 connect）
exec: 包装脚本 = touch /workspace/.fastrag_activity; timeout -k 5 {sec} bash -c {单引号转义命令}
  → 客户端 deadline=timeout+10s grace；exit 124/137→killed=true；帧分离 stdout/stderr
文件面（全部 exec 实现，ADR-0006 禁 archive——root 解析 symlink 绕过容器内权限）:
  readFile=base64 -w0；writeFile=stdin base64 -d 灌入（父目录 mkdir -p）；
  editFile=read→Java 侧 str_replace→write；exists=test -e；makeDir=mkdir -p；remove=rm -rf（对 / 拒绝）；
  listDir=find -maxdepth 1 -printf '%y\t%s\t%f\n'（残行跳过）
lastActivity: 裸 exec stat -c %Y 标记文件（不经包装器——读动作本身不得 touch，否则空闲回收永不触发）
错误分类: NotFoundException→NOT_FOUND；镜像缺失→INVALID_REQUEST；daemon 不可达/Linkage→UNAVAILABLE；
  deadline→TIMEOUT；Conflict→INVALID_REQUEST（六分类无 CONFLICT 位的就近映射）
```

**Decisions & Deviations:** Cube/E2B 适配器全操作抛 UNSUPPORTED（design §4 实装次序，javadoc 登记实装锚）；remove 对 `/` 的拒绝是一行安全底线而非路径策略（策略归门面）；~~CreateContext 未携带 allowPrivateEndpoints——适配器暂取部署级属性~~ **已闭合（2026-09-29 Windows 验收驱动）**：CreateContext 增 `allowPrivateEndpoints`（null 回退部署级属性），config probe 与 lifecycle buildCreateContext 均传导配置值，EndpointSpec 身份含 allowPrivate——`tcp://localhost` 等回环 daemon 由配置级开关放行；**Windows 缺省 unix 短路**：Windows 上 unix daemon 直接抛可操作 UNAVAILABLE（指引启用 Docker Desktop tcp://localhost:2375 + 配置填地址 + 开私网开关），替代晦涩的 `Could not initialize class DomainSocket`；~~SandboxHandle 不携带 endpoint——进程内 sandboxId→endpoint 映射，跨重启回退缺省解析~~ **已闭合（2026-09-29 同日续）**：`SandboxProviderClient.list` 增 endpoint hint 三参（host/tls/allowPrivate）——库存查询按配置 daemon 定位，空闲回收/孤儿对账改为 **per-config sweep**（新增 `SandboxConfigService.listEnabledDockerEffective` 枚举启用中的 docker 配置逐 daemon 扫，对齐 WeKnora per-config sweep——单次全量扫会误伤兄弟配置的 daemon），adapter list 发现即登记 endpoint spec（进程重启后 exec/lastActivity 由周期扫描自愈，遗留项缩小为「扫描间隔内的新实例」）；**guava 版本钉死（2026-09-28 运行期回归修复）**：docker-java 3.4.0 直依赖 guava 19.0 在装配中击败 Milvus/grpc 所需新版，启动即 `Preconditions.checkArgument` NoSuchMethodError（milvusService init）——根 POM dependencyManagement 钉 guava 32.0.1-android（grpc 同款），docker-java 兼容新版；docker-java×新 guava 的运行期组合在可达 daemon 环境首跑时随 S11 集成用例一并实证。

**Parent Trace:** DockerSandboxAdapter + Cube/E2B 预留桩 + SandboxProviderRegistry → agent.SandboxProviderGateway → SandboxProviderClient 缝（缝 6）→ REQ-H02（机制位）、H05、H08、H11、H14、H15（执行位）、H16

### SandboxExecutionFacade（from contract: agent.SandboxExecutionFacade）— SandboxExecutionService

**Logic Flow:**
```
7 动作(userId, sessionId, sandboxConfigId, …) → String:
  resolveDegradation: ① !configService.isGloballyEnabled()
                        → "Error: Sandbox is disabled (global switch)"
                      ② sandboxConfigId 空/配置不存在/disabled/enabled=false
                        → "Error: Sandbox is not configured for this app"
                      ③ acquire 抛 SandboxProviderException
                        → "Error: Sandbox unavailable: {分类}"
  可用 → configService.get+resolveCredentials → lifecycle.acquire → SandboxWorkspaceLayout.clamp
    （穿越/越界/保留路径→Error 文本且 provider 零调用）→ provider 调用 → 原文返回
  execute 超时=参数>配置 defaultTimeoutSec>120s；输出截断 262144+"(truncated)"
  grep/glob = provider.exec 包装（shellQuote 单引号安全）
  恒不抛异常、恒不阻断运行（REQ-H03）
stageAttachments: uploads 逐文件读服务端内容 → provider.writeFile → /workspace/input/{file_name}
  → 返回 Map<file_name, 沙箱内路径>（单文件失败 warn 跳过）
syncSkills: SkillService 读内容 → /workspace/skills/{slug}/SKILL.md
```

**Module Interactions:** Calls：SandboxConfigService、SandboxLifecycleService、SandboxWorkspaceLayout、SkillService；Called by：SandboxToolExecutor（7 动作 SPI）、FilesystemMiddleware（描述 hint+resolveDegradation）、AttachmentMiddleware（stageAttachments）、SkillsMiddleware（syncSkills）。

**Decisions & Deviations:** stageAttachments 返回 Map 而非 List（逐文件失败对位需要，javadoc 注明）；附件二进制按 UTF-8 读写（provider 文本面对称限制，已知限制登记）；~~app 直聊链（纯 BaseContext）的 sandboxConfigId 注入属 app-center 链——未接前应用对话走分支②「not configured」~~ **app-center 链已接（同日后续增量）**：app 表新增 `sandbox_config_id` 列（schema.sql + SchemaInitializer 双处同步），AppServiceImpl.create/update 带 sandbox_config 存在性校验（空=解除引用），executeDialog 将应用引用注入 runtimeState.sandboxConfigId——FilesystemMiddleware 既有通道直接消费；会话直聊（无 app）保持分支②预期降级；fastrag-tools 的 InstallSkillToolExecutor 提示文案已随路径迁移同步 `/workspace/skills/`。

**Parent Trace:** SandboxExecutionService + SandboxToolExecutor + SandboxWorkspaceLayout → agent.SandboxExecutionFacade → SandboxExecutionModule → REQ-H03、H12、H13

## 3. Testing

引用 tdd/agent.md 切片。**2026-09-28 沙箱实装后状态（fastrag-agent 模块 surefire 实数）**：

- 模块全量：**Tests run: 148, Failures: 0, Errors: 0, Skipped: 11**——11 个 Skipped 为 DockerSandboxAdapterIntegrationTest 守护跳过（本机无可达 Docker daemon；Linux/CI 或 DOCKER_HOST 指 TCP daemon 时实跑，见 tdd/agent.md S11）
- 沙箱新增测试资产：SandboxWorkspaceLayoutTest(4)、EndpointDialGuardTest(6)、SandboxConfigServiceTest(14，含 REQ-H09 批量 stale 接线断言)、DockerSandboxAdapterTest(36)、DockerSandboxAdapterIntegrationTest(11 守护)、SandboxLifecycleServiceTest(11)、SandboxExecutionServiceTest(11)
- 存量测试同步修绿：SkillReadonlyBackendTest、SkillsMiddlewareTest（路径断言迁 /workspace/skills）；DatabaseToolExecutorTest（工作区遗留三元写反已修复）
- **fastrag-tools 既有失配已修复（2026-09-28 质量门收尾）**：McpServiceServiceTest(16)/SkillServiceImplTest(9) 原因 = Service 层经 `SecurityUtil.getCurrentUser()` 取当前用户而单测无认证过滤器——修法为测试类 `@BeforeEach` 装配 SecurityContextHolder（LoginUser userId=u1 + `*` 权限）+ `@AfterEach` 清理 + MCP 夹具补 `creator="u1"`（可见性判定）；原「登记不改」条目解除
- **全 reactor 质量门（2026-09-28）**：`mvn test` **BUILD SUCCESS**，16 模块全绿，合计 **run=641, failures=0, errors=0, skipped=11**（11 skipped 均为 Docker 集成守护）；注：修复过程中一次 reactor 运行在 tools 之后的模块出现过一次未复现的失败，复验两次均全绿，application 模块含异步/SSE 时序测试，若复现优先隔离该处
- HITL：S8 真容器验收（集成用例在可达 daemon 环境实跑后人工验收隔离性与镜像内容）、S9 应用↔引擎端到端
- 旧「红绿循环未执行」登记已解除：沙箱域按 tdd/agent.md S7/S10/S11 用例表完成红绿循环（Tracer Bullet = S10-2 配置校验链）
- **2026-10-06 技能域修复（skill-runtime-remediation M1~M3）**：模块最终 **160 run / 0 失败 / 11 skipped**（Docker 守护跳过，既往即如此）。净变化 = +AgentEngineTest(7，含 toolsDirty 重算正负控制/install_skill 过滤) +AgentRunServiceImplTest(3，委派/终态/异常→system_error) +SkillsMiddlewareTest 增量（拦截全文/id 解析/可见性 warn/三轮零查库/沙箱单次同步/600 截断）+ContextBuilderTest 重写(3) −SkillReadonlyBackendTest(13，随类删除)；`AgentExecutor.java` 与 `fs/` 两件删除经全仓 grep + 全仓 mvn test 编译运行验证。同轮全仓质量门：**16 模块 BUILD SUCCESS**（tools 226 / agent 160+11skip / application 76 / knowledge-retrieval-platform-graph-eval-publish-operation-iam-bootstrap 全绿）
