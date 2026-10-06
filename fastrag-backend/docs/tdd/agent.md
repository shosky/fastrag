# 智能体运行时（agent） — 测试设计

> 对应契约：interfaces.md `## agent` 接口族（ExecutionEngine / AgentRunGateway / MiddlewareChain / ContextAssembler / 沙箱四契约 SandboxConfigStore + SandboxLifecycle + SandboxProviderGateway + SandboxExecutionFacade，2026-09-28 重写）。**本文件为测试设计，红绿循环未执行**（任务禁止改源码；现有 8 个测试类为既有资产，见 §3）。

## 1. 切片规划

| 切片 | 契约接口 | AFK/HITL | 说明 |
|---|---|---|---|
| S1 运行状态机与幂等 | agent.AgentRunGateway | AFK | 纯 DB + 异步编排，mock 引擎（真缝=ExecutionEngine 注入点） |
| S2 运行事件流 | agent.AgentRunGateway.streamEvents | AFK | SSE 生命周期/终态推送；afterSeq 现状锁定测试 |
| S3 执行引擎循环 | agent.ExecutionEngine | AFK | mock LlmService（系统边界，允许）；工具执行器用真实上游 ToolRegistry 桩数据 |
| S4 引擎异常语义 | agent.ExecutionEngine | AFK | 超时/步数上限/失败熔断/JSON 修复 |
| S5 中间件链协议 | agent.MiddlewareChain | AFK | 顺序/逆序/首中/异常隔离；order 契约 0-11 |
| S6 上下文组装 | agent.ContextAssembler | AFK | 反射填充/技能闭包/双错误路径 |
| S7 沙箱绑定生命周期与执行门面 | agent.SandboxLifecycle + agent.SandboxExecutionFacade | AFK | **2026-09-28 重写**（原桩 5 用例废止，见 §2）：mock SandboxProviderClient + InMemory 绑定存储 + 注入时钟；四类回收路径/身份重建/降级三分支/路径钳制/暂存物化 |
| S8 真容器与镜像验收 | SandboxProviderGateway（Docker 适配器）+ ExecutionEngine | **HITL** | 容器隔离性加固生效、镜像预装依赖（python/node/uv）正确性、跨轮状态保持人验收——需环境与人验收 |
| S9 应用↔引擎集成 | ExecutionEngine.executeStream（跨链消费点） | HITL | AppServiceImpl 经 fakeRun 的全链对话（敏感词过滤+消息落库），人工验收 |
| S10 沙箱配置面 | agent.SandboxConfigStore | AFK | **2026-09-28 新增**：CRUD 权限/保存校验链（错误码）/脱敏/指纹重算/验证与库存（mock provider）/总开关三级取值 |
| S11 Provider 适配器 | agent.SandboxProviderGateway | AFK（需 Docker runtime） | **2026-09-28 新增**：Docker 适配器对真实 daemon（Testcontainers 或本地 daemon）——加固基线 inspect 断言/exec/超时杀/文件面/exited 恢复/label；Cube/E2B 以 fake 控制面契约桩跑同一断言集（子集纯 AFK） |

HITL 在前提示：S8/S9 依赖真实环境，排在 AFK 切片全部通过后执行。S11 需要 Docker runtime（CI 或本地），不可用时降级为 S7 mock 面 + S8 HITL 兜底。

## 2. 测试用例（逐接口）

### ExecutionEngine（contract: agent.ExecutionEngine）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [直答] 无工具注册时 [引擎单轮返回最终答案] | context.tools 为空，LLM 返回纯文本 | executeSync | AgentResult.success=true；answer=LLM 文本；toolCallRecords 空；iterations=1 | REQ-E01/E05 | LlmService |
| [循环执行] LLM 先调工具后作答时 [工具结果回灌并终止循环] | 工具解析 1 个 builtin 定义；第 1 轮返回 tool_calls、第 2 轮纯文本 | executeSync | 消息历史含 assistant(tool_calls)→tool(截断结果)两条；最终 answer 为第 2 轮内容 | REQ-E02/E04 | LlmService |
| [步数熔断] 每轮都返回 tool_calls 时 [达上限出兜底文案] | maxSteps=2，LLM 恒返回 tool_calls | executeStream | 循环止于 2 轮；finalContent 含「最大步数限制」；补发 content 事件 | REQ-E01 | LlmService |
| [失败熔断] 同一工具连续失败 3 次时 [注入停止重试提示并清零] | 工具执行器恒败 | executeSync | 第 3 次失败后消息历史出现「连续失败 3 次」system 消息；计数器清空 | REQ-E03 | LlmService |
| [参数修复] 工具参数含未转义内嵌引号时 [修复后正常执行] | arguments 为 `{"sql":"SELECT * FROM t WHERE a="x""}` 型 | executeTool | 修复成功路径执行工具；修复失败时返回 Invalid arguments JSON 错误结果且循环继续 | REQ-E02 | LlmService |
| [超时终止] LLM 流挂起超过 stream-timeout 时 [推 error 事件终止] | flux 永不完成，stream-timeout=1s | executeStream | 抛超时→error 事件→emitter.complete | REQ-E06 | LlmService |
| [事件序列] 正常流式运行时 [事件词汇按序出现] | 工具+thinking 均触发 | executeStream | 事件名序列 ⊆ {init,thinking,message,tool_call,tool_result,agent_state,end} 且 init 首个、end 末个 | REQ-D04 | LlmService |
| [留痕完整性] 工具调用后 [记录含成败输出耗时] | 1 成 1 败两次调用 | executeSync | toolCallRecords 2 条，各含 toolName/success/output(error)/durationMs | REQ-E08 | LlmService |
| [thinking 提取] LLM 推送 THINKING 事件时 [单独累积并推送] | flux 含 THINKING+CONTENT 混合 | executeStream | onComplete 收到 thinkingContent 非空；thinking 事件逐段推送 | REQ-E05/D04 | LlmService |
| [工具截断] 工具输出超 5000 字符时 [回灌截断前端留全量] | 工具返回 6000 字符 | executeSync | 消息历史中 tool 内容=5000+截断标记；toolCallRecords.output 为原样 | REQ-E02/E08 | LlmService |

### AgentRunGateway（contract: agent.AgentRunGateway）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [创建运行] 智能体存在且可见时 [落 pending 并异步触发] | 可见 agent + query | createRun | 返回 status=pending；agent_run 落库 1 行；异步路径被触发 | REQ-C01 | AgentEngine（缝） |
| [幂等重放] 相同 requestId 重复创建时 [返回既有运行不新建] | 已存在 request_id=R 的 run | createRun(requestId=R) | 返回原 runId；表行数不变 | REQ-C02 | AgentEngine |
| [创建拒绝] 智能体不存在时 [404 语义业务错] | slug 查无 | createRun | body.code=404 | REQ-C01/E_AGENT_INVISIBLE | 无 |
| [创建拒绝] 智能体不可见时 [403 语义业务错] | private agent + 非属主 | createRun | body.code=403 | REQ-B01/C01 | 无 |
| [状态推进] 异步执行成功时 [pending→running→completed] | 引擎返回 success | executeRunAsync | status 终值 completed；finished_at 非空 | REQ-C03 | AgentEngine |
| [错误分类] 引擎返回失败结果时 [execution_error 终态] | AgentResult.error | executeRunAsync | status=failed 且 errorType=execution_error | REQ-C05 | AgentEngine |
| [错误分类] 上下文构建抛异常时 [system_error 终态] | agent 的 backend_id 悬空 | executeRunAsync | status=failed 且 errorType=system_error | REQ-C05/G01 | 无 |
| [取消] pending 状态时取消 [置 cancelled 记完成时间] | run.status=pending | cancelRun | status=cancelled；finished_at 非空 | REQ-C03/C04 | 无 |
| [取消拒绝] 终态运行时取消 [400 拒绝状态不变] | status=completed | cancelRun | body.code=400；status 仍 completed | REQ-C03/E_CANCEL_ILLEGAL_STATE | 无 |
| [防重查询] 同线程有 pending 运行时 [活跃查询命中该行] | thread=T 有 pending | getActiveRunByThread(T) | 返回该 run；无活跃时返回 null | REQ-C06 | 无 |
| [内部直插] 子智能体建运行时 [落库不触发异步] | runType=subagent | createRunDirect | 落库 1 行；executeRunAsync 未被调用 | REQ-C07 | 无 |
| [状态流] 运行达终态时 [SSE 收 end 事件后关闭] | status=failed + error 字段 | streamEvents | end 事件含 status/errorType/errorMessage；随后 complete | REQ-D03 | 无 |
| [续传现状] 携带 afterSeq 建流时 [行为与不携带一致（现状锁定）] | afterSeq=100 | streamEvents | 不回放任何历史事件（契约登记的未实装行为，实装时翻红） | REQ-D02 | 无 |

### MiddlewareChain（contract: agent.MiddlewareChain）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [顺序执行] 多中间件注册时 [before 按 order 升序 after 降序] | order 0/5/10 三个桩 | executeBefore + executeAfter | before 序列 0→5→10；after 序列 10→5→0 | REQ-F01/F03 | 无 |
| [拦截首中] 多中间件都能拦截时 [返回 order 最小者] | 两个中间件均返回拦截器 | getToolCallInterceptor | 返回 order 小的实例 | REQ-F02 | 无 |
| [异常隔离] 某中间件钩子抛异常时 [其余照常执行] | order 5 抛异常，0/10 正常 | executeBefore | 0 与 10 均被调用；链不抛 | REQ-F01/E_MW_ISOLATED | 无 |
| [工具合并] before 中间件追加工具时 [当轮 openaiTools 含新工具] | 中间件往 tools 加 1 个定义 | 引擎循环前置阶段 | toOpenAITools 重建后包含新定义 | REQ-F01 | LlmService |
| [接管短路] task 工具调用时 [SubAgentMiddleware 接管不真实执行] | 已注册子智能体 + task 调用 | 引擎工具分派 | interceptor.isSkip()=true；结果消息回灌；未走真实执行器 | REQ-F02/C07 | LlmService |
| [总结触发] 消息超 8000 token 阈值时 [中间消息被摘要压缩] | 长 history + 阈值默认 | beforeModelCall | system+最近 6 条保留，中间被摘要替换 | REQ-F01 | LlmService（摘要调用） |

### ContextAssembler（contract: agent.ContextAssembler）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [类型派发] ChatbotAgent 配置时 [实例化为 ChatBotContext] | backend_id=ChatbotAgent | buildContext | 返回 ChatBotContext 实例 | REQ-G01 | 无 |
| [配置填充] configJson 含合法键时 [反射逐字段就位] | config {systemPrompt, model, maxSteps} | buildContext | 对应 getter 返回同值 | REQ-G01 | 无 |
| [技能闭包] skills 含无效 slug 时 [无效项被过滤元数据缺失] | skills=[a, 不存在b]，库中仅 a 启用 | buildContext | promptSkills=[a]；runtimeSkillMetadata 无 b | REQ-G01 | SkillService |
| [缺失拒绝] agent 不存在时 [抛 Agent not found] | agentId 悬空 | buildContext | RuntimeException（→system_error 终态） | REQ-G01/E_CTX_AGENT_NOT_FOUND | 无 |
| [缺失拒绝] backend 未注册时 [抛 notFound] | backend_id 悬空 | buildContext | BusinessException notFound | REQ-G01/E_CTX_BACKEND_NOT_FOUND | 无 |
| [元模型] 字段标注 auth=admin 时 [user 角色不可见该项] | BaseContext.getConfigurableItems("user") | getConfigurableItems | summaryThreshold/modelRetryTimes 不在结果中 | REQ-A08 关联 | 无 |

### SandboxLifecycle + SandboxExecutionFacade（contract: agent.SandboxLifecycle / agent.SandboxExecutionFacade — S7，2026-09-28 重写）

> 旧桩用例~~[确定性寻址]~~/~~[降级返回]（单码版）~~/~~[缓存复用]~~/~~[空闲回收]（600s 内存版）~~/~~[关停清理]~~ 废止——被下列用例取代；桩契约细节见 contract agent.SandboxLifecycle 节首废止注记。

**SandboxLifecycle（mock SandboxProviderClient + InMemory 绑定存储 + 注入时钟）：**

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [绑定复用] 同会话两次 acquire 时 [恒得同一实例且保活] | 绑定已有存活实例 | acquire ×2 | 同一 sandboxId；lastUsed 刷新 | REQ-H01 | ProviderClient、时钟 |
| [新会话新箱] 不同 sessionId acquire 时 [得不同实例] | (u,s1) 已绑定 | acquire(u,s2) | sandboxId 与 s1 不同 | REQ-H01 | ProviderClient |
| [exited 自恢复] 绑定实例 inspect 为 exited 时 [acquire 先恢复再返回] | provider inspect=exited | acquire | connect 被调；返回 running 句柄 | REQ-H08 | ProviderClient |
| [身份重建] 绑定指纹≠当前配置时 [销毁重建并 casRebind] | 旧绑定 staleAt 非空 | acquire | delete(旧)+create(新) 被调；绑定指向新实例 | REQ-H09 | ProviderClient |
| [会话删除] onSessionDeleted 时 [绑定清除且实例删除] | 绑定存在 | onSessionDeleted | store 无绑定；provider.delete 被调 | REQ-H10 | ProviderClient |
| [空闲回收] 活动标记超创建时 TTL 时 [删容器清绑定] | 时钟推进 1801s | reclaimIdle | provider.delete 被调；绑定清除 | REQ-H02 | ProviderClient、时钟 |
| [TTL 固定] 配置 TTL 事后调小时 [在途实例按创建时 TTL] | 创建时 ttl=1800，配置改 60 | 推进 100s + reclaimIdle | 不回收 | REQ-H02 | ProviderClient、时钟 |
| [连接失败] acquire 时 create 失败 [分类上抛且无绑定残留] | mock create 抛 unavailable | acquire | E_SANDBOX_UNAVAILABLE 分类；store 无该会话绑定 | REQ-H01/H03 | ProviderClient |
| [孤儿对账] 无绑定且超 grace 的实例 [被删除，grace 内保留] | list 含无主实例（11min）+ 新实例（1min） | reconcileOrphans | 11min 者被删；1min 者保留 | REQ-H11 | ProviderClient、时钟 |
| [孤儿对账保留] 绑定库仍引用的实例 [不删] | 实例在绑定库 | reconcileOrphans | 实例保留 | REQ-H11 | ProviderClient |
| [重启可解析] 重建 Service 实例后 [lookup 仍解析同一实例] | Redis 存储有绑定（嵌入式/真实） | 新 Service lookup | 同一 sandboxId（进程重启语义） | REQ-H01 | SandboxBindingStore 用 Redis 实现 |

**SandboxExecutionFacade（mock Lifecycle + mock ProviderClient）：**

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [降级三分支] 总开关关/应用未配置/连接失败时 [三种可区分 Error 文本] | 三分支前置各一 | readFile | 返回文本互不相同且语义对应；均不抛异常 | REQ-H03 | Lifecycle |
| [路径钳制] 读 /etc/passwd 时 [拒绝且 provider 零调用] | 越界路径 | readFile | Error 文本；provider 未被调 | REQ-H12 | Lifecycle、ProviderClient |
| [写根限制] 写 input 拒、写 /workspace 根内过 | /workspace/input/a 与 /workspace/a | writeFile ×2 | 前者拒后者过 | REQ-H12 | 同上 |
| [skills 只读] 写 /workspace/skills 拒、读其下 SKILL.md 过 | 两操作 | write/read | 拒/过 | REQ-H12 | 同上 |
| [穿越拒绝] path 含 ../ 时 [拒绝不触 provider] | /workspace/../etc | readFile | Error 文本；provider 零调用 | REQ-H12 | 同上 |
| [输出截断] exec 输出超 256KB 时 [截断加标记返回] | mock 返回大输出 | execute | 长度≤256KB+截断标记；不抛 | REQ-H03 | 同上 |
| [附件暂存] uploads 传入时 [拷入 /workspace/input 并回填沙箱内路径] | 1 个附件 | stageAttachments | provider 写入 input 路径；prompt 路径=沙箱内路径 | REQ-H13 | 同上 |
| [技能物化] slugs 传入时 [写入 /workspace/skills/{slug}] | 2 个 slug | syncSkills | 对应路径写入被调 | REQ-H12 | 同上 |
| [门面不建实例] 任意动作执行时 [实例获取恒经 acquire] | 任意动作 | 执行 | provider.create 零调用 | 契约 Post | 同上 |

### SandboxConfigStore（contract: agent.SandboxConfigStore — S10，2026-09-28 新增）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [归属写权限] 非归属者非管理员更新时 [403 语义业务错] | config.user_id=u1 | update(u2) | body.code=403；记录不变 | REQ-H04 | 无 |
| [类型校验] sandbox_type=host 时 [CONFIG_INVALID 拒绝落库] | host 不做 | create | E_SANDBOX_CONFIG_INVALID | REQ-H04 | 无 |
| [网络校验] docker 配置携带域名列表时 [保存被拒] | allow_domains 非空 | create | E_SANDBOX_CONFIG_INVALID | REQ-H15 | 无 |
| [网络校验] network_mode=host 时 [保存被拒] | mode=host | create | E_SANDBOX_CONFIG_INVALID | REQ-H15 | 无 |
| [拨号校验] endpoint 为云 metadata 地址时 [显式放行亦恒拒] | 169.254.169.254 | create ×2（allowPrivate 0/1） | 均拒 | REQ-H16 | 无 |
| [拨号校验] 私网 endpoint 未放行时 [拒；放行后过] | 10.0.0.5:2376 | create ×2 | 前拒后过 | REQ-H16 | 无 |
| [脱敏] 读取配置时 [密钥只回显引用名不含值] | api_key_ref=ENV_KEY | get | 响应含引用名、不含环境值 | REQ-H06 | 无 |
| [空引用不覆盖] 更新传空 api_key_ref 时 [既有引用保留] | 既有 K1 | update(ref="") | 仍 K1 | REQ-H06/ADR-0007 | 无 |
| [指纹重算] 换镜像时 [指纹变；改超时不变] | image A→B / 仅改 timeout | update ×2 | 前者指纹变后者不变 | REQ-H09 | 无 |
| [验证失败] daemon 不可达时 [VERIFY_FAILED 且配置原状] | mock ping 失败 | verify | E_SANDBOX_VERIFY_FAILED；无写入 | REQ-H05 | ProviderClient（缝） |
| [删除守卫] 名下有实例（含 paused）时 [拒删 409] | listInstances 返回 paused 1 条 | delete | E_SANDBOX_CONFIG_IN_USE | REQ-H05 | ProviderClient |
| [凭据缺失] 引用环境变量缺失时 [resolve 时报错] | ref→缺失 env | resolveCredentials | E_SANDBOX_CREDENTIAL_MISSING | REQ-H06 | 无 |
| [总开关] 三级取值 [SysConfig > yml > 默认 false] | 逐层设置/清空 | isGloballyEnabled | 优先级生效；全空 false | REQ-H07 | 无 |

### SandboxProviderGateway（contract: agent.SandboxProviderGateway — S11，需 Docker runtime；Cube/E2B 子集纯 AFK）

| 测试名 | Given | When | Then（断言） | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [加固基线] 适配器创建容器时 [HostConfig 恰为白名单] | create | docker inspect | CapDrop=ALL；CapAdd⊆7 白名单；no-new-privileges；Memory==MemorySwap；Init=true；NanoCPUs/PidsLimit 按配置 | REQ-H14 | 无（真 daemon） |
| [元数据标记] 创建的容器 [带 managed label 与 configId/userId/sessionId] | create | list(managed 过滤) | 命中且 metadata 完整 | REQ-H11/H05 | 无 |
| [exec 往返] echo 执行时 [stdout/exitCode 正确] | running 容器 | exec("echo ok") | stdout=ok；exitCode=0 | ADR-0006 | 无 |
| [超时杀] sleep 超 timeout 时 [killed=true 且及时返回] | timeout=2s + sleep 10 | exec | killed=true；调用在 grace 内返回 | REQ-H03/ADR-0006 | 无 |
| [文件面往返] write→read→listDir [内容一致] | /workspace/out.txt | 三连调 | 内容一致；listDir 含该文件 | ADR-0006 | 无 |
| [状态自恢复] 手动 stop 后 exec [先恢复再执行成功] | inspect=exited | exec | 容器重启；exec 成功 | REQ-H08 | 无 |
| [跨轮真实保持] pip install 后新 exec [依赖仍可导入] | pip install requests | 新 exec import | 导入成功（真实状态保持，S7 mock 断言的实证层） | REQ-H08 | 无 |
| [错误分类·防误重建] 镜像不存在时 create [分类=invalidRequest] | image=no-such:latest | create | invalidRequest（非 notFound） | 契约锚例 | 无 |
| [错误分类] 已删容器上 exec [分类=notFound] | handle 失效 | exec | notFound | 契约锚例 | 无 |
| [空闲标记] 两次 exec 后 [活动标记 mtime 递增] | 间隔执行 | 读标记 mtime | 第二次更新 | REQ-H02 | 无 |
| [三后端一致性] 同一断言集对 fake 控制面跑 [Cube/E2B 桩行为一致] | fake 控制面 | 适配器全操作 | 与 Docker 同断言集通过（六分类/文件面/exec） | 缝 6 | fake 控制面（纯 AFK） |

## 3. 追溯（测试 → 契约接口 → Design 模块 → Spec REQ 抽样）

| 代表测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| [步数熔断] 达上限出兜底文案 | agent.ExecutionEngine | ExecutionEngineModule | REQ-E01 |
| [幂等重放] 相同 requestId 返回既有 | agent.AgentRunGateway | RunOrchestrationModule | REQ-C02 |
| [接管短路] task 工具被中间件接管 | agent.MiddlewareChain | MiddlewareChainModule | REQ-F02、C07 |
| [类型派发] ChatbotAgent→ChatBotContext | agent.ContextAssembler | ContextBackendModule | REQ-G01（缝 3） |
| [空闲回收] 超时连接出缓存 | agent.SandboxLifecycle | SandboxModule | REQ-H02 |
| [身份重建] 指纹不符销毁重建 | agent.SandboxLifecycle | SandboxLifecycleModule | REQ-H09（2026-09-28 补锚） |
| [降级三分支] 三因可区分文本 | agent.SandboxExecutionFacade | SandboxExecutionModule | REQ-H03（2026-09-28 补锚） |
| [加固基线] inspect 断言白名单 | agent.SandboxProviderGateway | SandboxProviderClient（Docker 适配器） | REQ-H14（2026-09-28 补锚） |
| [空引用不覆盖] 密钥引用保留 | agent.SandboxConfigStore | SandboxConfigModule | REQ-H06/ADR-0007（2026-09-28 补锚） |
| [续传现状] afterSeq 行为锁定 | agent.AgentRunGateway | RunOrchestrationModule | REQ-D02 |

## 4. 待实施说明
- 孤儿 REQ 豁免：REQ-E07（旧执行器 @Deprecated 仍被编排骨架引用的迁移中间态债务登记，无下游承接为有意；见 implement 文档）

- 红绿循环未执行（本任务禁改源码）。执行时按本文件从 S1（运行状态机，最稳的 Tracer Bullet）起步，S3/S4 引擎切片随后，S7 沙箱最后（需配置开关与时钟注入）。
- **现有 8 个测试类为既有资产**（src/test 下实测 8 个，feature-map 记 9 有误）：MiddlewareChainOrderTest（≈S5 顺序）、SkillsMiddlewareTest、DatabaseMiddlewareTest、SummarizationMiddlewareTest（≈S5 行为）、ContextBuilderTest、BaseContextTest（≈S6）、DatabaseToolExecutorTest（≈S3 工具执行器）、SkillReadonlyBackendTest（≈S7 相邻 fs）。实施时先跑通这 8 个再新增。
- **缺口**：S1/S2（编排状态机与事件流）零测试；S3/S4（引擎循环/异常语义）零测试；S7 沙箱零测试——三者是本设计文档的主要新增面。
- **沙箱实施前置（2026-09-28）**：docker-java 入 pom（SandboxProviderClient Docker 适配器依赖）；Testcontainers（或本地 daemon profile）承载 S11；绑定存储测试用嵌入式/容器化 Redis。S11 不可用环境下降级：S7 mock 面 + S8 HITL 兜底（spec §7 分层）。
- **Tracer Bullet 建议（沙箱域）**：首选 S10-2（配置校验链：create → 校验 → 错误码）——零环境依赖、打通 controller→service→EndpointDialGuard→mapper 全链；若环境有 Docker，S11-3（exec 往返）是更强的第一枪（直接证明 Engine API 直连通，ADR-0006 的可执行验证）。
- mock 纪律：仅 LlmService（外部 AI 网关）、SkillService 桩数据、时间时钟允许；引擎与编排间用真实 Spring 装配（ExecutionEngine 为真缝但属内部模块，S1 用真实引擎+mock LlmService 更优，上表 mock AgentEngine 为降级方案）；**沙箱域新增**：SandboxProviderClient 为真缝（缝 6）——S7/S10 允许 mock，S11 必须真 daemon（mock 过了就不算测过适配器）；SandboxBindingStore 为真缝——S7 允许 InMemory 实现，[重启可解析] 用例必须 Redis 实现。
- HITL 项：S8 真容器（Docker 环境就绪后人工验收隔离性与镜像内容）；S9 应用↔引擎端到端（真实模型对话质量、敏感词过滤生效、消息落库正确）。
