# 智能体运行时（agent） — 需求规格

## 1. 特性与范围

智能体运行时是 FastRAG 后端中具有自主决策能力的 AI 实体（智能体，见 CONTEXT.md「智能体」）的完整支撑层：提供智能体定义的管理（CRUD/默认智能体/内置智能体/子智能体）、运行任务的编排（创建/取消/状态机/幂等）、执行引擎（LLM 工具调用循环 + 流式/同步双模式）、链式中间件扩展点（工具注册、上下文增强、响应拦截）、运行时上下文构建、运行事件发布（进程内存储——跨实例转发未实装，见 REQ-D01）与多后端沙箱（docker/cube/e2b/disabled，对齐 WeKnora）生命周期管理。**显式排除**：应用（App）对智能体的调用编排在 app-center 特性；工具/技能/MCP 的解析与执行协议在 tools-skills-mcp 特性（本特性是它们的消费方）；模型注册与探活在 platform-config 特性。

## 2. Out of Scope

- **应用↔智能体的调用编排**：应用侧发起对话时如何选择智能体、保存会话消息，属 app-center；本特性只提供引擎级执行入口与运行记录。
- **工具/技能/MCP 的定义与管理**：四类能力（内置/API/MCP/技能）的 CRUD、测试、安装导入全部在 tools-skills-mcp；智能体只通过工具解析契约消费 ToolDefinition。
- **模型供应商接入与健康监控**：LlmService 的网关协议、ModelRegistry 的模型注册属 fastrag-ai / platform-config；智能体只消费模型配置（apiUrl/apiKey/model/temperature/enableThinking）。
- **聊天会话与消息持久化**：会话/消息表由 app-center 与 operation 域管理；智能体侧只有 run 记录（agent_run），不管理消息历史。
- **前端管理面**：智能体管理 UI 不在本特性‹CERTAIN（2026-09-24 深证：fastrag-frontend/src/views 全树无智能体管理页面，src/api 无任何 agent 端点客户端——grep 实测）›，后端 11 个端点零前端消费方。
- **工作流编排**：可视化节点编排（wf_*）属 workflow 特性；智能体的「图」是 Backend 内部执行图（AgentGraph），非用户可编排画布。
- **沙箱外围能力（二期后置，2026-09-28 grill 裁决）**：交互终端（PTY over WebSocket）、图形桌面（noVNC）、技能快照（安装时提交为镜像）、产物收集与下载链路（一期产物出口 = read_file 读取与 execute 输出）、工作区 git 检查点与会话 rewind、沙箱重建 SSE 提醒事件。后端抽象仅在 provider 能力位预留声明。
- **沙箱前端页面实现**：页面/组件的布局与交互规格属 fastrag-frontend 工作区（IDD role=frontend）；本特性以「管理面可观察行为」（REQ-H17、REQ-H18）与既有消费契约（REQ-H04~H07 端点行为）为界。对话界面**零新增**：降级/错误文本经既有消息流呈现（REQ-H03 已定义文本区分三因），不设沙箱专属对话 UI。
- **沙箱配置的应用侧接线**：App 实体的 sandbox_config_id 字段定义归 app-center 特性（REQ-I01，见 docs/spec/app-center.md）；本特性定义「应用引用沙箱配置」的运行面消费语义（见 REQ-H03）。

## 3. 领域术语

引用 `fastrag-backend/CONTEXT.md`：
- **智能体（Agent）**：本特性主体；Backend + Graph + Middleware + State 架构运行，支持多后端沙箱。
- **工具（Tool）/ MCP 服务（MCP Service）/ 技能（Skill）**：智能体可消费的外部能力四类（含内置）。
- **沙箱（Sandbox）**：工具代码执行的隔离环境，多后端抽象（docker/cube/e2b/disabled）；容器侧协议按后端各自实现。
- **沙箱配置（Sandbox Config）/ 沙箱绑定（Sandbox Binding）/ 孤儿沙箱对账（Orphan Sandbox Reconciliation）**：命名后端配置实体 / 会话到实例的持久映射 / 无主实例周期回收。
- **沙箱工作区布局（Sandbox Workspace Layout）/ 沙箱容器加固基线（Sandbox Container Hardening）/ Endpoint 拨号防护（Dial Guard）**：容器内路径与读写根约定 / 容器创建安全参数基线 / 后端 endpoint 的 SSRF 防护。
- **附件暂存（Sandbox Attachment Staging）/ 沙箱降级（Sandbox Degradation）**：附件每轮拷入 /workspace/input / 不可用时错误文本三分支降级。
- **SSE（Server-Sent Events）**：运行事件流推送通道；事件存储为进程内（跨实例转发未经 Redis 实装——2026-09-24 深证改正）。
- **统一响应（ApiResponse）**：全部控制器返回包装；业务错误 = HTTP 200 + body.code。

特性内特有概念（代码可观测）：**运行（Run）**= 一次智能体执行的记录与状态机（agent_run 表）；**线程（Thread）**= run 归属的会话线程（thread_id），同线程至多一个活跃 run；**Backend**= 智能体的执行图模板（Chatbot/SubAgent 等）；**中间件（Middleware）**= 环绕执行循环的横切扩展。

## 4. 功能需求

### 4.1 智能体管理（CRUD/默认/内置）

叙述段：智能体是可被列举、检索、创建、修改、删除的资源，以 slug 为人读标识（slug 与数字 ID 双寻址）；系统内置一组预置智能体并在首次访问时保证默认智能体存在。管理面是运行面的前置：智能体的工具清单、模型、系统提示词都存于 configJson，由管理面写入、运行面读取。

- **REQ-A01**：智能体支持全量 CRUD；路径参数同时接受 slug 与 ID 寻址。〔证据：AgentController.java:66-181、AgentServiceImpl.java:68-70（selectBySlug）‹CERTAIN›〕
- **REQ-A02**：写操作（更新/删除/设默认/改配置）必须通过管理权限校验，无权时返回 403 语义的业务错误；管理权限 = 当前用户是创建者。〔证据：AgentController.java:136-138,156-158,175-177、AgentServiceImpl.java:142-146 ‹CERTAIN›〕
- **REQ-A03**：系统保证「默认智能体」永远存在：查询默认智能体时若无则自动创建内置 default-chatbot。〔证据：AgentServiceImpl.java:35-46（ensureDefaultAgent）‹CERTAIN›〕
- **REQ-A04**：设为默认是互斥操作：新默认生效前所有旧默认必须被清除。〔证据：AgentServiceImpl.java:155-167 ‹CERTAIN›〕
- **REQ-A05**：内置智能体（slug 白名单 6 个）不可删除（409 语义），序列化时标记 builtin 与 permissionLocked。〔证据：AgentServiceImpl.java:29-32,119-122,148-151,188-189 ‹CERTAIN›〕
- **REQ-A06**：slug 全局唯一且自动去重：创建时空 slug 按名称派生，冲突时追加序号后缀。〔证据：AgentServiceImpl.java:74-79,235-243 ‹CERTAIN›〕
- **REQ-A07**：删除仅移除 agent 记录本身，无级联清理运行记录/沙箱等从属资源——历史 run 保留为孤儿数据。〔证据：AgentServiceImpl.java:119-122（deleteById 单表）‹CERTAIN›；‹CERTAIN（2026-09-24 深证：AgentServiceImpl 仅注入 AgentMapper（:27），无 AgentRunMapper/沙箱依赖，全类无任何关联清理调用）›〕
- **REQ-A08**：配置读取端点返回 configJson 与可配置项清单；可配置项当前为空占位。〔证据：AgentConfigController.java:54-67（configurableItems 恒空 Map）‹CERTAIN›〕**派生源已实证**：BaseContext.getConfigurableItems(userRole)（BaseContext.java:141，遍历 @ConfigField 注解字段生成 ConfigurableItem 列表，注解位 :47-86）‹CERTAIN（2026-09-24 深证）›——缺口仅在控制器接线（:62-63 注释自述「when AgentBackendManager is fully wired」）。〔决议 2026-09-24：接线取最小改动路径——AgentConfigController 注入 AgentBackendManager，经 `backend.getContextSchema().newInstance().getConfigurableItems(user.getRole())` 生成清单；现状差距：恒空 Map（登记债务，AgentConfigController.java:64）（代裁，用户可推翻）〕
- **REQ-A09**：配置写入把 context 字段映射为 configJson，与 shareConfig 一并更新。〔证据：AgentConfigController.java:77-93 ‹CERTAIN›〕

### 4.2 可见性与分享

叙述段：智能体是用户级资源，通过 shareConfig 的 access_level 控制可见性三级：global（所有人）、department（部门命中）、private/默认（仅创建者）。分享配置的写入权限与管理权限分离：非管理员不能把智能体设为 global/department。

- **REQ-B01**：列表/详情/运行入口统一经过可见性过滤：无 access_level 字段时按 private 处理，匿名用户只见 global。〔证据：AgentServiceImpl.java:49-57,125-139 ‹CERTAIN›〕
- **REQ-B02**：非管理员用户写入 shareConfig 时强制降级为 private 并清空部门/用户名单。〔证据：AgentServiceImpl.java:246-262 ‹CERTAIN›〕
- **REQ-B03**：子智能体（is_subagent）默认不出现在普通列表，需显式包含；子智能体有独立可见性查询与「非子智能体」类型校验。〔证据：AgentServiceImpl.java:51-53,264-282、AgentController.java:68 ‹CERTAIN›〕

### 4.3 运行编排与状态机

叙述段：运行（Run）是智能体执行的一等公民：外部以「创建运行」触发，后端落库 pending 记录后异步执行，状态沿 pending → running → completed/failed 推进，取消产生 cancelled。requestId 幂等键防止重复提交；同线程的活跃 run 查询支撑前端防重。

- **REQ-C01**：创建运行时校验智能体存在性与可见性，随后立即返回运行数据，执行在独立线程池异步进行。〔证据：AgentRunServiceImpl.java:64-104,190-191（@Async("agentTaskExecutor")）‹CERTAIN›〕
- **REQ-C02**：requestId 幂等：携带相同 requestId 的重复创建返回既有运行而非新建；未提供时自动生成。〔证据：AgentRunServiceImpl.java:70-77 ‹CERTAIN›〕
- **REQ-C03**：运行状态机：pending（已创建未执行）→ running（异步执行开始）→ completed（引擎成功）/ failed（引擎失败或异常）；cancelled 仅能从 pending/running 进入；终态集合 = completed/failed/cancelled/interrupted。〔证据：AgentRunServiceImpl.java:86,121-128,196,207,220,253-256 ‹CERTAIN›〕
- **REQ-C04**：取消是纯数据库状态翻转：仅当状态为 pending/running 时允许，直接置 cancelled 并记录完成时间；不向正在执行的引擎线程发送中断信号。〔证据：AgentRunServiceImpl.java:119-129 ‹CERTAIN›〕‹CERTAIN（2026-09-24 深证：AgentEngine 两条执行循环 :151-336/:428-493 均无任何取消状态检查，取消后引擎仍跑完；executeRunAsync :207 执行完毕无条件以 completed/failed 覆盖写回，cancelled 可被翻写）›——竞态坐实，债务已登记（contract 本节已知缺口①）。
- **REQ-C05**：引擎失败区分两类错误：执行错误（execution_error，引擎返回失败结果）与系统错误（system_error，编排层异常）；两者都落终态 failed 并记录 errorType/errorMessage。〔证据：AgentRunServiceImpl.java:207-226 ‹CERTAIN›〕
- **REQ-C06**：同线程防重：可查询指定线程中当前活跃（pending/running）的运行。〔证据：AgentRunController.java:117-123、AgentRunServiceImpl.java:165-167 ‹CERTAIN›〕
- **REQ-C07**：内部场景（子智能体）可绕过异步编排直接创建运行记录。〔证据：AgentRunService.java:21-24、AgentRunServiceImpl.java:230-240 ‹CERTAIN›〕
- **REQ-C08**：运行查询无属主校验：任何认证用户可读取任意 runId 的运行记录。〔证据：AgentRunServiceImpl.java:114-116（getRunForUser 直接透传 getRun，无 user 比对）‹CERTAIN›〕‹CERTAIN（2026-09-24 深证：user 形参在方法体内零消费，签名保留仅为契约位）›——权限缺口坐实（contract 已登记②）。

### 4.4 运行事件流

叙述段：运行过程中的可观测性通过两条通道：(1) 引擎把工具调用等事件经 RunEventPublisher 发布——**现状为进程内存储**（ConcurrentHashMap，无 Redis pub/sub，2026-09-24 深证改正，原「Redis pub/sub 跨实例转发」断言不成立）；(2) 运行事件端点提供 SSE 长连接。两条通道的现状能力不对等（见 REQ-D02、REQ-D03），如实约束。

- **REQ-D01**：事件为**进程内发布-存储**：RunEventPublisher 以 ConcurrentHashMap 按 runId 存事件（无 Redis、无订阅侧桥接、多实例转发能力不成立——代码内含「TODO: Redis pub/sub integration」注释自证未实现）。〔证据：AgentEngine.java:756-770（pushEvent）、RunEventPublisher.java:44-45,67 ‹CERTAIN（2026-09-24 深证，推翻原「Redis pub/sub 主题连接」断言并改正本条本体）›；SSE 端点不消费该事件存储（streamEvents 仅轮询 run 状态，AgentRunServiceImpl.java:140-159 未调用 getEvents）‹CERTAIN›〕
- **REQ-D02**：运行事件 SSE 端点接受 afterSeq 断点续传参数，但当前实现未消费该参数：连接建立后仅轮询运行状态直至终态并发送 end 事件，历史事件不回放。〔证据：AgentRunController.java:102-108、AgentRunServiceImpl.java:132-162 ‹CERTAIN›〕‹CERTAIN（2026-09-24 深证：afterSeq 透传至 service 后方法体零消费；RunEventPublisher 事件带 seq（毫秒时间戳，:60）但 getEvents 无任何调用方）›——afterSeq 为预留契约位、回放能力未实装，判定坐实。
- **REQ-D03**：SSE 连接生命周期 30 分钟，终态时推送含 status/errorType/errorMessage 的 end 事件后主动完成；运行记录消失时静默完成。〔证据：AgentRunServiceImpl.java:134-153 ‹CERTAIN›〕
- **REQ-D04**：引擎在流式模式下向前端推送统一事件词汇：init/message/thinking/tool_call/tool_result/agent_state/end/error。〔证据：AgentEngine.java:139,207,230,279,334-335,348,366,377,531-538 ‹CERTAIN›〕

### 4.5 执行引擎与工具循环

叙述段：执行引擎是智能体的心脏：解析工具定义 → 前置中间件 → LLM 调用循环（流式或同步）→ 逐个执行工具调用 → 结果回灌 → 直至 LLM 给出最终答案或达到步数上限。引擎对工具类型零感知（分派给执行器路由），对失败有熔断式引导（连续失败注入停止重试提示）。

- **REQ-E01**：执行循环有步数上限（默认 15 轮，可按上下文覆盖）；达到上限且无最终回答时返回兜底提示文本。〔证据：AgentEngine.java:90,149-151,343-349 ‹CERTAIN›〕
- **REQ-E02**：工具执行管线：参数 JSON 解析（失败自动修复）→ JSON Schema 验证 → 执行器分派 → 结果截断（5000 字符）回灌消息历史。〔证据：AgentEngine.java:599-657,286-289 ‹CERTAIN›〕
- **REQ-E03**：工具连续失败 ≥3 次时向消息历史注入系统提示，要求 LLM 停止重试直接作答，并重置计数。〔证据：AgentEngine.java:318-332 ‹CERTAIN›〕
- **REQ-E04**：执行器缺失/工具未找到不中断循环：以错误文本消息回灌，LLM 可据此调整。〔证据：AgentEngine.java:240-262,651-654 ‹CERTAIN›〕
- **REQ-E05**：引擎双模式：流式（SSE 推送 + thinking 提取，用于应用对话）与同步（返回结构化结果 + 工具调用记录，用于智能体运行与子智能体）。〔证据：AgentEngine.java:112-116,395-399 javadoc :44-46 ‹CERTAIN›〕
- **REQ-E06**：流式调用有独立超时（默认 240s），超时或流错误终止循环并推送 error 事件。〔证据：AgentEngine.java:85-87,571-583,372-382 ‹CERTAIN›〕
- **REQ-E07**：旧执行器已废弃但仍在编排骨架中被引用（运行编排当前走旧执行器实例）。〔证据：AgentExecutor.java:24-42（@Deprecated）、AgentRunServiceImpl.java:55,204 ‹CERTAIN›〕‹CERTAIN（2026-09-24 深证：AgentExecutor.java:27 javadoc 自述「保留此类仅为向后兼容，后续版本将移除」且 :40 仍 @Component 注册——双实现并存为迁移中间态有类注释直接佐证，废弃类仍在依赖图中）›
- **REQ-E08**：工具调用全量留痕：每次调用的名称/参数/成败/输出/耗时记录进运行事件与结果对象。〔证据：AgentEngine.java:216-225,294-310,483-491 ‹CERTAIN›〕

### 4.6 中间件链

叙述段：中间件是执行循环的横切扩展点，链式编排：前置钩子（注册新工具、注入消息）、后置钩子（响应加工）、工具调用拦截器（某中间件完全接管某类工具调用，如子智能体 task 工具）。链条覆盖：总结（上下文压缩）、技能激活、数据库挂载、知识库检索、MCP、文件系统、附件、待办清单、模型重试、工具调用修补、子智能体、上下文等。

- **REQ-F01**：中间件链在每次 LLM 调用前后确定性执行；前置中间件注册的工具必须合并进本轮可用工具集。〔证据：AgentEngine.java:141-146,168-169,417-422,435 ‹CERTAIN›〕
- **REQ-F02**：工具调用拦截优先于常规执行：命中拦截器时跳过真实工具执行，拦截结果作为工具消息回灌。〔证据：AgentEngine.java:196-227,451-461 ‹CERTAIN›〕
- **REQ-F03**：链的组装顺序是可测试的确定性契约（存在顺序测试）。〔证据：src/test/.../middleware/MiddlewareChainOrderTest.java 存在 ‹CERTAIN›；顺序定义见 design〕

### 4.7 上下文构建

叙述段：运行启动时由 ContextBuilder 把 run 记录 + 智能体配置折叠为 BaseContext（模型配置、系统提示词、工具清单、用户身份、运行时状态），供引擎与中间件消费。配置字段体系（ConfigField/ConfigurableItem）定义可配置项元模型。

- **REQ-G01**：异步执行前必须先构建上下文：上下文失败 = 系统错误终态。〔证据：AgentRunServiceImpl.java:200-204,218-226 ‹CERTAIN›〕
- **REQ-G02**：模型配置的运行级覆盖（model_override 随运行输入落库）**当前不生效**——仅落库、全仓无消费点（预留契约位）。〔证据：AgentRunServiceImpl.java:89-92（写入 inputPayload）‹CERTAIN›；‹CERTAIN（2026-09-24 深证：grep 全仓仅此写入点与 DTO 声明，无任何读取方；ContextBuilder.buildContext :76-79 仅读 agent.configJson，AgentEngine.extractQuery :815-828 仅读 query/message/input 键）——推翻原「消费点在 ContextBuilder/引擎缺省链」推断并改正本条本体›；模型实际取值：运行面 = agent.configJson，应用对话面 = AppServiceImpl.java:646-653 由应用绑定模型构建 ModelConfig〕

### 4.8 沙箱与代码执行隔离（多后端）

叙述段：代码执行类工具（read_file/write_file/edit_file/execute/list_directory/grep/glob）在隔离沙箱中运行。沙箱支持三种后端类型——Docker（容器）、Cube 与 E2B（MicroVM）——加 disabled（关闭），全部后端共享同一行为契约：会话级实例复用、跨轮状态保持、空闲回收与降级语义〔2026-09-28 grill 裁决，对齐 WeKnora；现状为降级桩，docker-java 不在 classpath、整条链路惰性，实装即本节 REQ 的兑现〕。沙箱资源由命名配置描述：应用定义引用沙箱配置，运行时按配置获取实例；容器内遵循统一工作区布局。技能执行不引入新机制：技能激活链路不变，技能指引的脚本经既有 execute 工具落沙箱执行（镜像自带 python/node/uv 运行时）。沙箱与后端 endpoint（Docker daemon / Cube·E2B 控制面与数据面）的交互是系统边界。

#### 4.8.1 沙箱配置管理

- **REQ-H04**：沙箱配置是用户级资源（user_id 归属，仅归属者与管理员可写），支持全量 CRUD 与列举。字段覆盖三类：类型（docker/cube/e2b/disabled）；通用参数（单次执行超时、allow_private_endpoints、网络策略、注入沙箱的环境变量）；各后端专属连接块（Docker：daemon 地址、TLS 证书目录、镜像、CPU/内存/PIDs 上限、网络模式；Cube/E2B：endpoint、凭据引用、模板、TTL）。〔决议 2026-09-28〕
- **REQ-H05**：配置支持探活验证与实例库存查询：验证 = Docker ping daemon / Cube·E2B 控制面鉴权（可附模板清单列举）；库存 = 该配置名下现存沙箱实例清单（含暂停态），用于更换凭据或删除配置前确认无遗留实例。验证失败与非法配置（网络模式越界、endpoint 不安全等）= 业务错误（HTTP 200 + body.code）。〔决议 2026-09-28〕
- **REQ-H06**：凭据引用式存储：配置中密钥类字段（API key）存引用（环境变量名），运行时解析为实际值；任何读取端点对密钥只回显引用名、不回显值；更新时空引用不覆盖既有引用。〔决议 2026-09-28：走本仓 model.api_key_ref 同款惯例，不引入落库加密——与 WeKnora 加密落库不同，属刻意偏差〕
- **REQ-H07**：平台配置提供沙箱子系统总开关，默认关闭；关闭时不创建新沙箱实例，工具执行走降级文本。取值优先级：平台配置 > application.yml 回退键 > 内置默认。〔决议 2026-09-28；延续原 REQ-H01 默认关闭基调〕
- **REQ-H17**：沙箱配置管理面以「用户级资源」形态可用（2026-09-28 二段增量）：用户以创建者身份经管理面列出/创建/编辑/删除/验证自己的配置并查询其名下沙箱实例库存；归属者与管理员可写、非归属者不可见不可改（与 REQ-H04 权限模型一致）。管理面能力 = 消费契约 agent.SandboxConfigStore 的端点行为（已实现并全绿），页面实现属前端工作区——本条为前端接线的可观察行为锚点：任一用户登录后，经由其归属配置集合可完成「建配置 → 验证通过 → 被应用引用 → 对话内沙箱执行」全链，无需后端改动。〔决议 2026-09-28 代裁，用户可推翻〕
- **REQ-H18**：平台总开关在系统设置面呈现与管理（2026-09-28 二段增量）：管理员经系统设置读写 `sandbox.enabled`（sys_config 键，REQ-H07 三级取值的最高层）；总开关关闭时管理面仍可浏览/编辑配置，仅对话内沙箱执行走降级文本。〔决议 2026-09-28 代裁，用户可推翻；挂点 = fastrag-platform SysConfig 存储（SandboxConfigServiceImpl 已直读该键）〕

#### 4.8.2 会话绑定与生命周期

- **REQ-H01**（修订 2026-09-28）：沙箱实例以「(用户, 会话) → 实例」的绑定提供获取/保活/释放生命周期：同一会话多轮对话绑定同一实例，新会话获得新实例。绑定记录持久于外部存储：后端进程重启后绑定仍可解析；多副本部署下任意副本解析同一会话得到同一实例。~~沙箱服务以「uid+fileThreadId → 确定性 sandboxId」的连接缓存提供生命周期~~（确定性 ID 模型废止——多后端下实例 ID 由 provider 生成）。~~真实 Docker 容器的创建与删除当前未实现（连接为 mock 桩，release 仅清缓存）~~（债务由本节实装兑现）。〔证据：SandboxService.java:88-127,139-144 桩现状 ‹CERTAIN›〕〔决议 2026-09-28：Redis 版本化绑定（version/provider/sandboxId/configId/createdAt/staleAt）；~~2026-09-24「真容器最小接线（init 探测分支创建 DockerClient）」决议~~被本次整体实装裁决取代〕
- **REQ-H02**（修订 2026-09-28）：空闲沙箱实例自动回收：无活动达到空闲 TTL（默认 1800s，沙箱配置可覆盖）即回收——删除容器并清除绑定侧可达性；TTL 随实例创建时的配置固定，配置后续修改不追溯在途实例。Cube/E2B 后端由 provider 侧 TTL 承担同等回收语义。~~每分钟扫描按空闲超时（默认 600s）回收~~（超时值上调并对齐 WeKnora DefaultDockerIdleTTL；回收须真实删除容器，补齐 contract 实装门禁②）。〔证据：SandboxService.java:46-47,157-179 桩现状 ‹CERTAIN›〕
- **REQ-H08**：跨轮状态保持：同一会话后续轮次的工具执行可见此前轮次在沙箱内的产物——创建的文件、安装的依赖、后台进程。实例 stopped/exited 不是终态：再次使用时自动恢复而非重建。〔决议 2026-09-28，对齐 WeKnora「exited=可暂停态」〕
- **REQ-H09**：配置变更重建：沙箱配置的身份要素（后端类型/endpoint/凭据/镜像或模板）变更后，既有绑定标记失效；相关会话下一次使用沙箱时销毁旧实例、按新配置重建，对用户透明（无提醒事件，工具层表现为新空实例）。非身份字段（超时/TTL/资源上限）变更不触发重建。〔决议 2026-09-28〕
- **REQ-H10**：会话删除释放：会话被删除时，其沙箱绑定一并清除、沙箱实例被停止并删除。〔决议 2026-09-28〕
- **REQ-H11**：孤儿对账：周期任务比对后端侧实例清单（按托管标记 + 配置标识过滤，含暂停态）与绑定存储，删除「无绑定且超过宽限期（默认 10 分钟）」的实例，覆盖进程重启、绑定丢失、换绑遗留三类泄漏。〔决议 2026-09-28；对齐 WeKnora orphan_reaper〕

#### 4.8.3 执行、工作区、安全与降级

- **REQ-H03**（修订 2026-09-28）：沙箱不可用时文件系统中间件仍注册工具定义，实际文件与命令操作返回错误文本、不阻断运行；不可用区分三因——① 总开关关闭；② 会话所属应用未引用沙箱配置或配置禁用；③ 后端连接失败（含镜像拉取失败、端口冲突等连接期失败，一律降级不 fail-fast）。错误文本须区分三种原因。沙箱可用时文件与命令操作路由到沙箱后端执行。〔证据：FilesystemMiddleware.java:47,185 现状单分支 ‹CERTAIN›〕〔决议 2026-09-28 扩展；2026-09-24 代裁「连接期失败降级不中断运行」继续有效；E_SANDBOX_DISABLED 拆三条码见 contract〕
- **REQ-H12**：工作区布局与路径权限：所有后端的容器内统一布局——`/workspace` 根 + `/workspace/input`（只读附件）+ `/workspace/output`（产物收集）+ `/workspace/skills/{slug}`（技能只读）。工具面权限：可读 = `/workspace` 全部；可写 = `/workspace` 下除 input 与 skills 子树；路径经清洗、规范化与保留路径拒绝（防穿越）。布局为单一事实源（常量类），工具描述中的路径提示由它生成。~~/home/gem/user-data、/home/gem/skills 布局~~废止（含契约技能路径 invariant 的同步修订）。〔决议 2026-09-28〕
- **REQ-H13**：附件暂存：每轮对话开始，用户上传附件实体被拷入沙箱 `/workspace/input`；附件中间件注入 system prompt 的附件路径为沙箱内路径。附件在沙箱内只读。〔决议 2026-09-28；现状 AttachmentMiddleware 仅注入服务端路径、沙箱内不可见（AttachmentMiddleware.java:40-46 ‹CERTAIN›），本条为实装要求〕
- **REQ-H14**：容器隔离基线：Docker 后端创建的沙箱容器必须满足——CapDrop ALL 且仅加白名单 caps（CHOWN/DAC_OVERRIDE/FOWNER/FSETID/SETGID/SETUID/KILL）、no-new-privileges、禁 swap（memory = memorySwap）、init 进程收养（防僵尸进程耗尽 PIDs 上限）、资源三上限默认 2 核/2GiB/512 pids（沙箱配置可覆盖）。不引入命令内容审查（危险命令黑名单/参数注入检测）——隔离责任在容器层与路径钳制（REQ-H12），对齐 WeKnora 现代会话路径行为。〔决议 2026-09-28，对齐 WeKnora hostConfig 与 docker_engine.go:108-124〕
- **REQ-H15**：网络模式约束：Docker 后端网络模式仅允许 bridge/none——host、container:、命名网络在配置保存时拒绝；「不允许出网」配置映射为 none。域名级出网策略仅 Cube/E2B 支持：docker 配置携带域名 allow/deny 列表时保存被拒。〔决议 2026-09-28〕
- **REQ-H16**：后端 endpoint 拨号防护：对 Docker daemon（TCP）与 Cube·E2B endpoint 的连接实施 SSRF 防护——云 metadata/link-local 地址恒拒绝（显式允许私网亦拒）；私网地址默认拒绝、须配置显式放行（REQ-H04 的 allow_private_endpoints）；校验发生在配置保存时与建连时两处（建连时对解析 IP 复查，防 hostname 先验后变）。〔决议 2026-09-28〕

### 4.9 内置智能体与数据初始化

- **REQ-I01**：应用启动时初始化内置智能体集合（存在性幂等）。〔证据：runner/AgentDataInitializer.java 存在、AgentServiceImpl.ensureBuiltinAgent :205-232 幂等（先查后插）‹CERTAIN›〕
- **REQ-I02**：内置集合含 6 个 slug（default-chatbot/general-purpose/web-search/deep-research/research-explorer/fact-verifier）。〔证据：AgentServiceImpl.java:29-32 ‹CERTAIN›〕

## 5. 非功能约束

- **NFR01 异步隔离**：运行执行不占用请求线程，使用专用线程池 agentTaskExecutor。〔证据：AgentRunServiceImpl.java:190-191、config/AsyncConfig.java 存在 ‹CERTAIN›〕
- **NFR02 事件非关键路径**：运行事件发布失败仅记调试日志，不影响执行结果。〔证据：AgentEngine.java:766-769 ‹CERTAIN›〕
- **NFR03 流超时可配置**：网关超时/流超时经配置项注入（ai.gateway.timeout / ai.gateway.stream-timeout）。〔证据：AgentEngine.java:82-87 ‹CERTAIN›〕
- **NFR04 性能阈值**：〔决议 2026-09-24：取保守默认并与既有机制同源——运行创建 P95 ≤ 500ms（同步单次落库 + 异步触发）；SSE 终态推送延迟 ≤ 3s（下限由 streamEvents 2s 轮询间隔决定，AgentRunServiceImpl.java:154）；沙箱（真容器目标态）冷启动 ≤ 10s、单次工具调用 ≤ 30s（SandboxBackend 现有 block 上限，SandboxBackend.java:63）。理由：业界惯例保守值 + 本仓既有超时/轮询常量一致性；现状差距：无任何指标采集设施，登记债务（代裁，用户可推翻）〕
- **NFR05 DDL 双处同步**：agent/agent_run 建表 DDL 仅存在于 SchemaInitializer 运行时幂等增补，init-scripts/schema.sql 全量脚本中无 agent 相关表——违反「DDL 双处同步」硬约定，属已知偏差。〔证据：SchemaInitializer.java:149-204 存在完整 DDL；schema.sql 全文 grep "agent" 零命中 ‹CERTAIN›〕

## 6. 验收清单

- [ ] 创建智能体时空 slug 自动派生且冲突自动加后缀，slug 全局唯一？（REQ-A06）
- [ ] 非创建者更新/删除/设默认他人智能体被 403 拒绝？（REQ-A02）
- [ ] 内置智能体删除被 409 拒绝且序列化带 permissionLocked？（REQ-A05）
- [ ] 删除唯一默认智能体后，再次查询默认能自动重建 default-chatbot？（REQ-A03）
- [ ] 非管理员提交 access_level=global 的 shareConfig 被强制降级 private？（REQ-B02）
- [ ] 相同 requestId 重复创建运行返回同一条记录且不重复执行？（REQ-C02）
- [ ] 运行状态流转完整覆盖 pending/running/completed/failed/cancelled，非法取消被 400 拒绝？（REQ-C03、REQ-C04）
- [ ] 同线程活跃 run 查询在无活跃运行时返回空而非报错？（REQ-C06）
- [ ] 引擎在 15 轮上限耗尽且无答案时返回兜底文案？（REQ-E01）
- [ ] 工具参数非法 JSON 触发自动修复，修复失败以错误结果回灌而非中断？（REQ-E02）
- [ ] 工具连续失败 3 次后消息历史出现停止重试系统提示？（REQ-E03）
- [ ] 中间件前置注册的工具对当轮 LLM 可见？（REQ-F01）
- [ ] SSE 连接在运行进入终态时收到 end 事件并关闭，30 分钟超时兜底？（REQ-D03）
- [ ] 空闲沙箱在 TTL（默认 1800s）内无活动后被回收且容器真实删除，无容器泄漏累积？（REQ-H02）
- [ ] 列表/创建/更新/删除全链可用，且 slug 与数字 ID 双寻址均命中同一记录？（REQ-A01）
- [ ] 设新默认后旧默认自动清除，任意时刻至多一个默认？（REQ-A04）
- [ ] 删除智能体仅移除 agent 记录本身，其运行记录不被级联清理（现状口径）？（REQ-A07）
- [ ] 配置读取端点返回 configJson 与恒为空的可配置项清单？（REQ-A08；豁免：占位/债务登记，见 implement 文档）
- [ ] 配置写入把 context 字段与 shareConfig 一并映射进 configJson？（REQ-A09）
- [ ] 匿名用户列表仅见 global、部门用户见 department 命中项、创建者见自己 private？（REQ-B01）
- [ ] 子智能体默认不出现在普通列表，显式包含时才返回且类型校验拒绝非子智能体？（REQ-B03）
- [ ] 创建运行先落 pending 记录并立即返回，执行在独立线程池异步进行？（REQ-C01）
- [ ] 引擎执行错误与编排层系统异常分别记 execution_error/system_error 且终态均 failed？（REQ-C05）
- [ ] 子智能体内部场景可绕过异步编排直接创建运行记录？（REQ-C07）
- [ ] 任意认证用户可读取任意 runId 的运行记录（现状口径，无属主校验）？（REQ-C08）
- [ ] 引擎发布的事件存入进程内 RunEventPublisher 存储（无 Redis 转发——2026-09-24 改正后的现状口径）？（REQ-D01）
- [ ] SSE 端点接受 afterSeq 参数但历史事件不回放（预留契约位现状）？（REQ-D02；豁免：占位/债务登记，见 implement 文档）
- [ ] 流式运行依次推送统一事件词汇（init/message/thinking/tool_call/tool_result/end 等）？（REQ-D04）
- [ ] 执行器缺失或工具未找到时循环不中断，错误文本回灌由 LLM 继续决策？（REQ-E04）
- [ ] 同一运行可用流式与同步两种模式执行，同步模式返回结构化结果与工具调用记录？（REQ-E05）
- [ ] 流式调用超时（默认 240s）或流错误时循环终止并推送 error 事件？（REQ-E06）
- [ ] 旧执行器仍在编排骨架中被引用（迁移中间态现状）？（REQ-E07；豁免：占位/债务登记，见 implement 文档）
- [ ] 每次工具调用的名称/参数/成败/输出/耗时均记录进运行事件与结果对象？（REQ-E08）
- [ ] 命中拦截器的工具调用跳过真实执行，拦截结果作为工具消息回灌？（REQ-F02）
- [ ] 中间件链组装顺序由既有顺序测试锁定（确定性契约）？（REQ-F03）
- [ ] 上下文构建失败时运行置 system_error 终态而非静默失败？（REQ-G01）
- [ ] 运行输入携带 model_override 时随 run 落库？（REQ-G02）
- [ ] ~~沙箱默认关闭或 Docker 不可用时全部方法静默降级返回 null/mock 连接（真实容器未实现）？（REQ-H01；豁免：占位/债务登记，见 implement 文档）~~（2026-09-28 废止：降级桩契约由实装兑现，见下列沙箱条目）
- [ ] 同一会话第二轮工具执行可见第一轮创建的文件与安装的依赖；实例 stopped/exited 后再次使用自动恢复而非重建？（REQ-H01、REQ-H08）
- [ ] 沙箱总开关关闭时不创建新实例，工具执行返回降级文本？（REQ-H07）
- [ ] 沙箱配置 CRUD 仅归属者与管理员可写，非归属者被拒？（REQ-H04）
- [ ] 配置探活验证失败与非法配置（网络模式越界等）返回业务错误而非 HTTP 5xx？（REQ-H05、REQ-H15）
- [ ] 配置密钥字段只回显引用名不回显值，更新时空引用不覆盖既有引用？（REQ-H06）
- [ ] 同一会话多轮绑定同一沙箱实例，新会话获得新实例；后端重启后绑定仍可解析？（REQ-H01）
- [ ] 配置身份要素变更后，相关会话下次使用沙箱时旧实例被销毁并按新配置重建？非身份字段变更不触发重建？（REQ-H09）
- [ ] 会话删除后其沙箱实例被停止并删除？（REQ-H10）
- [ ] 无绑定且超过宽限期的沙箱实例（含暂停态）被周期对账删除？（REQ-H11）
- [ ] 沙箱不可用时文件系统中间件仍注册工具定义，实际操作返回区分三种原因的错误文本且不阻断运行？（REQ-H03）
- [ ] 容器内可读 /workspace 全部、可写除 input 与 skills 外全部；路径穿越与保留路径被拒？（REQ-H12）
- [ ] 附件在每轮开始被拷入沙箱 /workspace/input，注入 prompt 的路径为沙箱内路径？（REQ-H13）
- [ ] Docker 沙箱容器以 CapDrop ALL+白名单 caps、no-new-privileges、禁 swap、init 收养、2C/2GiB/512pids 缺省创建？（REQ-H14）
- [ ] Docker 配置携带域名 allow/deny 列表或非法网络模式时保存被拒？（REQ-H15）
- [ ] 私网 endpoint 默认拒绝连接、显式放行后可用；云 metadata 地址任何情况下不可达？（REQ-H16）
- [ ] 用户经管理面可完成「建配置 → 验证通过 → 查库存」全链，非归属者不可见不可改？（REQ-H17、REQ-H04）
- [ ] 管理员在系统设置面可读写 sandbox.enabled，关闭后管理面仍可编辑配置、对话执行走降级？（REQ-H18、REQ-H07）
- [ ] 应用重启后内置智能体集合不重复创建（存在性幂等初始化）？（REQ-I01）
- [ ] 内置智能体集合恰为 6 个约定 slug？（REQ-I02）

## 7. 测试策略

经由公共接口（智能体 CRUD 端点、运行端点、SSE 端点）与引擎内部接口（AgentEngine.executeStream/executeSync）分层验证：
1. **管理面（可自动化）**：CRUD + slug 唯一性 + 默认智能体自愈 + 内置保护 + 可见性三级矩阵 + 非管理员降级，全部经 HTTP 接口断言 ApiResponse.code。
2. **编排状态机（可自动化）**：requestId 幂等、状态迁移、非法取消、错误分类（execution_error vs system_error）、同线程防重查询。
3. **执行引擎（可自动化，mock 系统边界 LlmService）**：零工具直答、单工具/多工具循环、步数上限兜底、失败熔断注入、JSON 修复、事件词汇序列（init→message/thinking→tool_call→tool_result→end）。
4. **中间件链（可自动化，现有 5 个测试类覆盖：顺序/技能/数据库/总结）**：前置注册合并、拦截器短路。
5. **沙箱（分层：可自动化 + 测试容器环境 + HITL）**：配置管理面（CRUD 权限/验证失败业务错误/密钥脱敏/总开关）与降级三分支经 HTTP 接口断言 ApiResponse.code；绑定与生命周期（跨轮保持、exited 恢复、配置变更重建、会话删除释放、空闲回收、孤儿对账）需真实 Docker 环境跑集成测试；容器隔离性（caps/禁 swap/资源上限生效）与镜像内预装依赖（python/node/uv）的正确性需人验收（HITL）；Cube/E2B 后端依赖外部服务，以契约测试桩覆盖行为一致性，真环境接入属部署验收。
