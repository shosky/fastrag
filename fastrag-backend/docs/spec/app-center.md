# 应用中心（app-center） — 需求规格

## 1. 特性与范围

应用中心是 FastRAG 面向终端用户的应用（App）全生命周期管理域：从模板/空白创建应用，配置其对话参数、系统提示词与运行预算，将知识库、工具、技能、MCP 服务、外部数据库五类资源绑定到应用，设置触发器与全局安全/兜底策略，发布上线，并通过 SSE 流式对话对外提供 RAG 增强的多轮会话（含会话与消息管理、消息反馈）。显式排除：工作流编排本身的画布与执行引擎（特性 #16 workflow，应用侧仅消费其配置面）、智能体引擎内部机制（特性 #17 agent，应用对话最终委派给 AgentEngine 执行）、工具/技能/MCP/外部数据库的注册与协议实现（特性 #13 tools-skills-mcp，应用侧仅持有绑定关系）、检索策略实现（特性 #9 retrieval，应用侧仅按绑定调用）。

## 2. Out of Scope

- 拒绝在应用中心内实现工作流节点编排/执行/调试：那是 workflow 域的职责，应用侧只有 workflow-config 的读写入口。
- 拒绝在应用中心内实现工具执行器、MCP 协议客户端、技能解析器：绑定表只存 ID 与启用态，执行一律下穿到 tools-skills-mcp / agent 域。
- 拒绝在应用中心内维护敏感词词库本身：词库 CRUD 与词条匹配属 platform-config 域的敏感词服务，应用侧只有"是否启用安全策略"开关与模式/兜底文案配置。
- 拒绝跨用户的会话互访：会话与消息严格归属创建者（API Token 场景除外）。
- 拒绝在 App 层重新实现模型接入与连通性测试：模型选择仅消费 platform-config 域 model 表中 status=online 的记录。
- 拒绝为单条 HTTP 端点建立独立契约：~60 个配置端点是配置面的实现细节（见 contract 端点排除规则）。

## 3. 领域术语

- **应用（App）**：CONTEXT.md「应用」条目 — 面向终端用户的应用单元，绑定知识库、工具、MCP 服务等能力，提供对话式交互。表 `app`。
- **SSE（Server-Sent Events）**：CONTEXT.md「SSE」条目 — 服务端推送事件流（SseEmitter），用于 LLM 流式输出。
- **统一响应（ApiResponse）**：CONTEXT.md 条目 — 业务错误 = HTTP 200 + body.code。
- **混合检索（Hybrid Retrieval）**：应用对话经绑定的知识库调用检索服务（向量+关键词+重排），App 侧仅设 topK 预算。
- ‹CERTAIN（2026-09-24 深证：schema.sql:1012,1023,1149,1160,1171）›「五类绑定」= app_kb_binding / app_tool_binding / app_skill_binding / app_mcp_binding / app_db_binding 五张绑定表，DDL 实存五表；feature-map #13「四类绑定」系工具域视角只计 tool/skill/mcp/db（不含 kb），#15 同样称五类——本文件统一「五类绑定」，两处以本口径为准。

## 4. 功能需求

### 4.1 应用 CRUD 与模板

应用是本域的聚合根。列表/详情按数据范围（属主/同组织/系统级/API Token）过滤，创建写入属主与组织，管理操作要求管理权限并记操作审计。

- **REQ-A01**：应用列表支持按名称关键字与标签模糊筛选，且非 API Token 调用者只能看到属主为自己、同组织或 owner=system 的应用。〔证据：AppServiceImpl.java:119-130〕
- **REQ-A02**：创建应用时状态固定初始化为 draft，类型缺省 ChatBot，属主与组织取自当前登录用户。〔证据：AppServiceImpl.java:140-151〕
- **REQ-A03**：更新仅接受 name/description 字段的显式提交（containsKey 语义），删除与更新要求当前用户对该应用有管理权（manageable）。〔证据：AppServiceImpl.java:153-170〕
- **REQ-A04**：应用不存在返回 notFound 业务错误，可见性不通过返回 forbidden 业务错误。〔证据：AppServiceImpl.java:73-83〕
- **REQ-A05**：提供应用模板列表供创建时套用（模板为全量只读列表）。〔证据：AppServiceImpl.java:172-175〕
- **REQ-A06**：创建/更新/删除应用自动记录操作审计日志（类别 operation，动作 app_created/app_updated/app_deleted）。〔证据：AppController.java:32-40〕
- **REQ-A07**：遗留同步运行通道已废弃，仅作兼容保留的旧版问答：按内置关键词规则匹配答案，不产生模型调用、不入会话库；新实现走现行 SSE 对话通道（端点细节见契约）。〔证据：AppController.java:42、AppServiceImpl.java:201-239〕

### 4.2 对话配置面

应用的可调配置分三层：基础配置（记忆轮数/输出格式/开场白等 UI 与运行参数）、对话配置（背景等 AppDialogConfig）、智能体配置（prompt/摘要阈值/maxSteps/maxTurns/retryTimes/maxTokens 等执行预算）。配置项既有整体读写也有单项直改端点。

- **REQ-B01**：基础配置按应用一对一存储，涵盖输出格式、响应语言、开场白、结束语、记忆轮数、超时秒数、最大输入长度与 advancedOptions JSON；未建记录时读返回空、单项直改自动建记录。〔证据：AppBasicConfig.java:31-34、AppConfigController.java:35-41〕
- **REQ-B02**：智能体配置存储 prompt、temperature、maxTurns、summaryThreshold（默认 8000）、summaryPrompt、maxSteps（默认 15）、retryTimes（默认 2）、maxTokens（默认 2048），并支持按单项端点以默认值兜底写入。〔证据：AppConfig.java:3-18、AppConfigController.java:49-61〕
- **REQ-B03**：对话配置（背景）独立于基础配置存储与读写。〔证据：AppConfigController.java:43-46〕
- **REQ-B04**：查看类配置端点要求 app:use，修改类端点要求 app:edit。〔证据：AppConfigController.java:34-61 等全部端点注解〕
- **REQ-B05**：基础配置与对话配置支持导出/导入（配置迁移），高级选项以整体 JSON 提交保存。〔证据：AppConfigController.java:179-189〕
- **REQ-B06**：应用还可读写工作流配置、监控数据、调试配置、知识库自动更新配置——这些是应用对 workflow 域与知识更新机制的挂载点，应用侧只存配置不实现引擎。〔证据：AppConfigController.java:203-222〕

### 4.3 五类资源绑定

应用通过五张绑定表挂载资源：知识库（app_kb_binding）、工具（app_tool_binding）、技能（app_skill_binding）、MCP 服务（app_mcp_binding）、外部数据库（app_db_binding）。绑定是运行时能力装配的唯一事实源：SSE 对话启动时按 enabled=1 加载全部绑定注入执行上下文。

- **REQ-C01**：五类绑定均支持列表（app:use）、绑定（app:edit）、解绑（app:edit）端点；数据库/技能/工具/MCP 绑定还支持更新端点。〔证据：AppConfigController.java:88-130〕
- **REQ-C02**：对话执行只装配 enabled=1 的绑定，未启用的绑定不进入执行上下文。〔证据：AppServiceImpl.java:570-624（五类绑定查询均带 eq(enabled,1)）〕
- **REQ-C03**：知识库绑定的检索行为继承知识库自身保存的检索配置（上下文组装策略、句子召回、重排、多查询、同义扩展等），App 侧仅覆盖 topK=5 预算；KB 配置解析失败时回退默认配置并继续。〔证据：AppServiceImpl.java:716-800〕
- **REQ-C04**：单个知识库检索失败不中断整体对话——记录告警日志后继续处理其余绑定。〔证据：AppServiceImpl.java:755-757〕

### 4.4 触发器与全局策略

全局策略是应用级的安全与兜底总控：安全策略启用开关、敏感词模式与兜底文案、未匹配策略、变量管理。触发器提供应用级触发规则的定义、测试与运行。

- **REQ-D01**：应用「安全策略启用」开关缺省为开启（未配置记录、字段为空均视为开启）——安全默认。〔证据：AppServiceImpl.java:85-90〕
- **REQ-D02**：全局策略覆盖敏感词模式（sensitiveWordMode）、兜底文案（fallbackText）、未匹配动作与配置（unmatchedAction/unmatchedConfig/unmatchedEnabled）、最后更新人，安全/兜底两个端点写入同一记录。〔证据：AppGlobalPolicy.java:3-6、AppConfigController.java:76-81〕
- **REQ-D03**：全局策略变量（AppVariable）支持列表/创建/更新/删除。〔证据：AppConfigController.java:82-87、191-192〕
- **REQ-D04**：敏感词配置（sensitive-words）与策略开关（toggle）作为独立端点写入全局策略面。〔证据：AppConfigController.java:193-198〕
- **REQ-D05**：触发器支持列表/创建/更新/删除，以及按输入进行测试（test）与运行（run）。〔证据：AppConfigController.java:62-74〕

### 4.5 SSE 对话与会话管理

> **2026-09-25 注**：会话体验已按 WeKnora 式"新建对话"立项为新特性 [new-conversation.md](./new-conversation.md)——会话升一等公民、应用降级为会话可选预设，**本节通道标记废弃保留**（行为不变，下线另行排期）。本节 REQ 继续适用于旧通道；新通道复用本域编排器与会话账本，其行为面以 new-conversation spec 为准。

这是本域的核心运行通道：客户端经 SSE 建立流式对话，服务端完成可见性校验→会话定位/创建→输入敏感词拦截→用户消息落库→RAG 检索→上下文装配→委派 AgentEngine 流式生成→输出敏感词过滤→助手消息落库。会话与消息提供完整的归属受控管理面。

- **REQ-E01**：SSE 对话要求应用对当前用户可见；带 sessionId 时校验会话归属（仅会话属主可访问，API Token 例外），未带 sessionId 时自动创建新会话。〔证据：AppChatController.java:47-53、AppServiceImpl.java:371-389〕
- **REQ-E02**：会话 ID 为 16 位、消息 ID 为 32 位的去连字符 UUID 截断；新会话标题为「新对话」，消息与 token 计数从 0 起累计。〔证据：AppServiceImpl.java:247-260、413、824〕
- **REQ-E03**：安全开关开启时，用户输入命中 blockInput 词条则直接经 SSE 返回拦截文案（blocked=true）并结束，不保存用户消息、不产生模型调用。〔证据：AppServiceImpl.java:391-409〕
- **REQ-E04**：用户消息先于模型调用落库；若为该会话第一条消息，同步回填会话的 firstQuestion（截断 200 字）与 title（截断 30 字加省略号）。〔证据：AppServiceImpl.java:411-425〕
- **REQ-E05**：会话首条消息为打招呼类输入且配置了开场白时，直接返回开场白（isGreeting=true）并把开场白存为助手消息，不调用模型。〔证据：AppServiceImpl.java:482-502、812-817〕
- **REQ-E06**：模型路由：优先取智能体配置的 model，其次基础配置 advancedOptions JSON 中的 model；按 model code 在 model 表中查 status=online 记录取 apiUrl/apiKeyRef/enableThinking，查不到时回退默认网关并告警，不阻断对话。〔证据：AppServiceImpl.java:434-475〕
- **REQ-E07**：系统提示词 = 智能体配置 prompt（缺省「你是一个智能助手…」），检索命中时在其后拼接参考资料上下文与作答约束。〔证据：AppServiceImpl.java:477-480、541-544〕
- **REQ-E08**：检索来源（文件名/相似度/文件与分片定位/图片键/内容）以独立 sources 事件在回答生成前推送。〔证据：AppServiceImpl.java:546-553、742-752〕
- **REQ-E09**：多轮记忆取最近 memoryRounds（缺省 5）轮（即 memoryRounds*2 条）历史消息按时间正序拼入上下文。〔证据：AppServiceImpl.java:431-432、556-561〕
- **REQ-E10**：五类绑定、摘要阈值/摘要提示词/最大步数/重试次数、maxTokens（缺省 2048）、temperature（缺省 0.7）统一装配进执行上下文与模型配置后，委派 AgentEngine 执行全部 SSE 事件推送；应用层不自实现模型流。〔证据：AppServiceImpl.java:564-665〕
- **REQ-E11**：流结束后对最终答案执行输出敏感词过滤（受安全开关控制）：命中替换词条时整段替换，并补发 replace 标记的 message 事件让前端替换气泡；过滤后文本才落库。〔证据：AppServiceImpl.java:668-690〕
- **REQ-E12**：助手消息与用户消息成对累计会话 messageCount（+2），第一轮回答回填 answerSummary（截断 200 字）并刷新 updatedAt；SSE 超时时若已有部分答案，同样过滤后落库。〔证据：AppServiceImpl.java:520-528、822-844〕
- **REQ-E13**：SSE 执行在独立线程池异步进行，SecurityContext 在提交前捕获、异步线程内传播、finally 中清理以防线程池污染；执行异常以 error 事件推送并完成流。〔证据：AppServiceImpl.java:64、533-537、692-705〕
- **REQ-E14**：会话管理面：创建会话、按应用+当前用户列会话（updatedAt 倒序）、取会话消息历史（排除软删除、按时间正序）、删除会话（连带物理删除其消息）。〔证据：AppChatController.java:58-95、AppServiceImpl.java:270-339〕
- **REQ-E15**：消息级管理：单条消息软删除（deletedAt 标记）、点赞/点踩反馈（like/dislike/空=清除）、编辑消息内容；三者均校验消息所属会话归属当前用户。〔证据：AppChatController.java:100-125、AppServiceImpl.java:106-116、341-366〕

### 4.6 应用发布

- **REQ-F01**：应用支持查询发布记录列表与上线发布（发布要求 app:publish 权限），发布落 app_publish_record：仅设置 publishedAt 并直接插入，版本/状态/快照信任调用方，无状态流转代码。〔证据：AppConfigController.java:131-135、AppConfigServiceImpl.java:165-166〕
- **status 值域与流转（2026-09-24 裁决）**：实证——DDL 默认 'draft'（schema.sql:1038 ‹CERTAIN›），publish 端点对 status 无缺省填充、全由调用方透传（AppConfigServiceImpl.java:166 仅 setPublishedAt ‹CERTAIN›），后端无任何流转写入代码，前端现网无组件调用 app 发布 API（api/index.ts:1721-1722 仅函数定义，无组件消费）→ 现状所有记录 status 恒 'draft'，Monitor 的 'released'/'rolled_back' 计数恒 0（AppConfigServiceImpl.java:365-366 ‹CERTAIN›）。〔决议 2026-09-24：目标值域取 {draft, released, offline, rolled_back}（对齐 Monitor 既有消费口径与前端「上线/下线」语义的最小封闭集）；流转入口取「publish 落库时写 'released'，下线/回滚由新增独立端点触发」；现状差距：publish 不写 status、无下线/回滚端点——已登记债务见 implement 文档 §2（代裁，用户可推翻）〕

### 4.7 对话测试与优化建议

- **REQ-G01**：对话测试用例（AppDialogTest：名称/问题/期望答案/实际答案/是否命中/相似度）支持 CRUD 与 CSV 导出（列 name,query,expected,actual,matched,similarity,createdAt）。〔证据：AppConfigController.java:136-152〕
- **REQ-G02**：优化建议（AppOptimization：标题/建议类型/描述/状态/影响分）支持 CRUD、应用（apply，status→applied）、分析（analyze）与 CSV 导出（列 title,suggestionType,description,status,impactScore,createdAt）。〔证据：AppConfigController.java:153-176、AppConfigServiceImpl.java:173-177〕
- **REQ-G03**：analyze 为纯计数聚合（totalTests/matchedTests/unmatchedRate/totalPublish/totalKbBindings/totalTriggers），不调用 LLM、不计算相似度。〔证据：AppConfigServiceImpl.java:178-193〕

### 4.8 对话记录运营面

- **REQ-H01**：应用维度对话记录支持按关键字/评分/时间段筛选的分页查询（page/size）、单条详情、删除与 CSV 导出，供运营分析域消费。〔证据：AppConfigController.java:223-236〕

### 4.9 应用引用沙箱（2026-09-28 二段增量）

- **REQ-I01**：应用创建/编辑入口接受沙箱配置引用（`sandboxConfigId`）：非空须命中 agent 特性的 sandbox_config 表（否则 400 业务错误「沙箱配置不存在」），空/null = 解除引用（对话执行回落 agent REQ-H03 的「not configured」降级）；引用后对话执行链经 runtimeState 透传沙箱配置，应用侧不自实现沙箱判定。前端入口 = 应用编辑页沙箱配置选择器（下拉数据消费 agent 特性 /api/sandbox-configs 列表，页面实现属前端工作区）。〔证据：App.java sandboxConfigId 字段、AppServiceImpl.requireSandboxConfigExists/executeDialog 注入 ‹CERTAIN（已实现，2026-09-28）›；app 表列双处同步（schema.sql + SchemaInitializer）〕〔决议 2026-09-28 代裁，用户可推翻〕

**Parent Trace:** REQ-I01 → Design.AppLedgerModule（跨特性：agent.SandboxExecutionFacade 分支②消费）→ agent 特性 REQ-H03。

## 5. 非功能约束

- **NFR-01**：权限模型——全部端点经 @PreAuthorize 声明式鉴权：app:use（查看/对话）/ app:create / app:edit（配置）/ app:delete（删除）/ app:publish（发布）。〔证据：AppController.java:30-42、AppChatController.java:47-125、AppConfigController.java:34-236〕
- **NFR-02**：数据范围——应用可见性沿用 DataScope（属主/同组织/owner=system），API Token 绕过属主过滤与会话归属校验（机器对机器访问）。〔证据：AppServiceImpl.java:68-83、100、113、124-128〕
- **NFR-03**：SSE 流超时 5 分钟；拦截/问候语短路径 emitter 超时 60 秒。〔证据：AppServiceImpl.java:397、488、507〕
- **NFR-04**：全部响应经 ApiResponse 包装，业务错误为 HTTP 200 + body.code（notFound/forbidden/业务校验）。〔证据：AppController.java:30-42、AppServiceImpl.java:75-76、98、101〕
- **并发与性能 SLA**〔决议 2026-09-24：取「SSE 并发连接 ≤200/实例、流线程池有界化（core 8 / max 32 / 队列 200、拒绝策略 Abort→error 事件）」；理由：单实例 SSE 长连接受内存与文件句柄约束，200 为中小规模部署的保守业界默认值，有界池防慢客户端无限占线程；现状差距：当前为无界 cachedThreadPool（AppServiceImpl.java:64 ‹CERTAIN›）、无连接数上限——已登记债务见 implement 文档 §2（代裁，用户可推翻）〕
- **敏感词匹配性能阈值**〔决议 2026-09-24：取「单次 checkInput/filterOutput 耗时预算 100ms、词库规模上限 10,000 词条」；理由：纯内存字符串/正则匹配的保守常规容量，超限应走 DFA/AC 自动机优化；现状差距：无实测数据、无阈值校验——该阈值归属 platform-config 域（词库与匹配实现所在），应用侧仅消费其结果，债务登记于 platform-config 链（代裁，用户可推翻）〕

## 6. 验收清单

- [ ] 非 API Token 用户列表只能看到自己/同组织/系统级应用，API Token 可见全部？（REQ-A01）
- [ ] 创建的应用初始 status=draft、type 缺省 ChatBot、owner/orgId 取当前用户？（REQ-A02）
- [ ] 更新仅改变显式提交的字段，未提交字段保持原值？（REQ-A03）
- [ ] 无管理权用户更新/删除应用得到 forbidden（HTTP 200 + body.code）？（REQ-A04）
- [ ] 安全开关缺省（无策略记录）时敏感词拦截生效？（REQ-D01）
- [ ] 命中 blockInput 词条的输入：返回 blocked=true、不落用户消息、无模型调用？（REQ-E03）
- [ ] 带 sessionId 对话时非属主（非 API Token）被拒绝？（REQ-E01）
- [ ] 未带 sessionId 对话自动建会话，首条消息回填 firstQuestion/title？（REQ-E01、REQ-E04）
- [ ] 打招呼+配置开场白时直接返回开场白且不调用模型？（REQ-E05）
- [ ] 优先取智能体配置 model、其次 advancedOptions.model，均无 online 记录时回退默认网关？（REQ-E06、REQ-E07）
- [ ] 系统提示词取智能体配置 prompt，检索命中时拼接参考资料上下文与作答约束？（REQ-E07）
- [ ] sources 事件在回答内容前推送且含文件名/相似度/定位信息？（REQ-E08）
- [ ] 禁用（enabled=0）的绑定不出现在执行上下文？（REQ-C02）
- [ ] KB 保存的上下文组装策略在 App 会话检索中生效（非退化为 concat）？（REQ-C03）
- [ ] 记忆轮数=5 时上下文最多取 10 条历史消息？（REQ-E09）
- [ ] 输出命中替换词条时前端收到 replace 事件且落库为过滤后文本？（REQ-E11）
- [ ] SSE 超时时已生成的部分答案被过滤并落库？（REQ-E12）
- [ ] 删除会话连带删除其全部消息；软删除的消息不再出现在历史中？（REQ-E14、REQ-E15）
- [ ] 异步执行异常时客户端收到 error 事件且线程 ThreadLocal 被清理？（REQ-E13）
- [ ] 五类绑定各自 CRUD 后对话能力随之增减？（REQ-C01）
- [ ] 发布端点要求 app:publish 权限，其他 edit 权限不足时被拒？（REQ-F01）
- [ ] 应用模板列表为全量只读且可供建应用时套用？（REQ-A05）
- [ ] 创建/更新/删除应用各留一条 app_created/app_updated/app_deleted 审计？（REQ-A06）
- [ ] 遗留同步运行通道按关键词规则返回答案、不产生模型调用、不入会话库（废弃兼容现状）？（REQ-A07；豁免：占位/债务登记，见 implement 文档）
- [ ] 基础配置未建记录时读返回空、单项直改自动建记录？（REQ-B01）
- [ ] 智能体配置单项直改缺省值兜底（summaryThreshold=8000/maxSteps=15/retryTimes=2/maxTokens=2048）？（REQ-B02）
- [ ] 对话配置（背景）与基础配置独立存储读写互不影响？（REQ-B03）
- [ ] 查看类配置端点要求 app:use、修改类端点要求 app:edit？（REQ-B04）
- [ ] 基础+对话配置导出再导入后往返一致，高级选项整体 JSON 提交保存生效？（REQ-B05）
- [ ] 工作流配置/监控数据/调试配置/知识库自动更新配置四类挂载点可读写且应用侧不实现引擎？（REQ-B06）
- [ ] 单个知识库检索失败时对话继续，仅记录告警日志？（REQ-C04）
- [ ] 敏感词模式/兜底文案/未匹配动作写入全局策略且安全/兜底两端点共享同一记录？（REQ-D02）
- [ ] 全局策略变量（AppVariable）支持列表/创建/更新/删除？（REQ-D03）
- [ ] 敏感词配置与策略开关作为独立端点写入全局策略面？（REQ-D04）
- [ ] 触发器支持 CRUD 与按输入 test/run？（REQ-D05）
- [ ] 新会话 ID 为 16 位、消息 ID 为 32 位去连字符 UUID 截断？（REQ-E02）
- [ ] 五类绑定与执行预算（摘要阈值/maxSteps/retryTimes/maxTokens/temperature）统一装配进执行上下文后委派 AgentEngine，应用层不自实现模型流？（REQ-E10）
- [ ] 对话测试用例 CRUD 与 CSV 导出列序正确？（REQ-G01）
- [ ] 优化建议 CRUD/apply/analyze/CSV 可用，apply 后状态 applied？（REQ-G02）
- [ ] analyze 仅返回纯计数聚合、不调用 LLM、不计算相似度？（REQ-G03）
- [ ] 应用维度对话记录按关键字/评分/时间段分页、可查详情、删除与 CSV 导出？（REQ-H01）
- [ ] 应用创建/编辑接受 sandboxConfigId：非空须命中沙箱配置否则 400，空=解除引用；引用后对话内沙箱执行生效？（REQ-I01）

## 7. 测试策略

经公共接口（AppController / AppChatController / AppConfigController）测试，优先级：

1. **SSE 会话状态机**（最高）：blocked 短路、greeting 短路、首条消息回填、消息计数成对累计、超时部分落库、异常 error 事件——每个分支的可观测结果（库中消息行 + SSE 事件序列）。
2. **绑定一致性**：enabled=1 才入上下文；解绑后能力消失；KB 检索配置透传（可用 mock RetrievalService 断言 RetrievalRequest 的 topK=5 与组装策略字段）。
3. **敏感词安全双接入点**：输入拦截与输出替换，开关关/开两态对照（mock SensitiveWordService）。
4. **权限与数据范围**：五类权限注解生效；DataScope 三分支 + API Token 旁路。
5. **配置面回归**：配置单项端点的默认值兜底写入；导入导出往返一致；CSV 导出格式。
6. **会话消息管理**：软删除过滤、反馈写入、编辑、会话级联删除。
