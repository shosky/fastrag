# 模型与平台配置 — 需求规格

## 1. 特性与范围

本特性是 FastRAG 后端的**平台级配置与数据治理底座**：管理 AI 模型的注册、启停与连通性验证（model 表是全仓 AI 能力的动态路由事实源）；提供系统参数（sys_config）、安全策略与发布策略、数据字典、敏感词、术语库、查询规则六类平台级数据的管理面；运营通知中心与通用图片上传也归此特性。这些数据被检索（query_rule、term 启用词条）、fastrag-ai 各消费链（model 动态路由）、应用中心（敏感词输入/输出过滤）、发布治理（审核/发布开关）跨域消费。显式排除：知识库文件的两阶段上传与摄取（归 file-ingestion）、检索对 query_rule/term 的**消费**行为（归 retrieval）、发布/审核流程本身（归 publish-review）、模型监控大屏（归 operation-analytics）、对话中的安全开关裁决（归 app-center）。

## 2. Out of Scope

- **知识库文件上传**：`/api/kb/{kbId}/files` 26 端点与摄取流水线归 file-ingestion；本特性的 `/api/upload` 仅服务品牌 Logo 类静态图片（单阶段、1MB、jpg/png）〔UploadController.java:45-73 ‹CERTAIN›〕。
- **模型调用的执行**：LLM/Embedding/Rerank 的 HTTP 网关调用细节（超时、流式、重试）归 fastrag-ai；本特性只发起连通性验证与维护路由数据〔ModelServiceImpl.java:75,96,123 只调 fastrag-ai 服务方法 ‹CERTAIN›〕。
- **模型路由的解析与选择**：哪个业务用哪个模型（KB 级 embeddingModel、图谱 graphLlmModel、评测 answerModel 等）由各消费链裁决；本特性不维护「某库用某模型」的绑定关系〔消费方实例：ChunkingServiceImpl.java:239-242 ‹CERTAIN›〕。
- **query_rule 的执行引擎**：检索侧 applyQueryRules 为占位、规则零消费（retrieval 链已登记）；本特性只提供规则数据的增删启停〔QueryRuleServiceImpl.java:10-13 仅 CRUD ‹CERTAIN›〕。
- **发布/审核开关的拦截执行**：publish_switch/review_switch 六键只存储不消费（publish-review 链已登记缝 5）；本特性只提供读写端点〔ConfigManageController.java:72-86 ‹CERTAIN›〕。
- **敏感词的对话开关**：应用级 isSafetyEnabled 开关由 app-center 裁决；本特性提供过滤原语本身〔AppServiceImpl.java:394,524,673 消费方侧开关 ‹CERTAIN›〕。
- **模型监控统计**：model_call_log 的读取分析归 operation-analytics（ModelMonitorServiceImpl）；本特性不提供监控端点〔grep 全仓实测，platform 模块无 model_call_log 查询端点 ‹CERTAIN›〕。

## 3. 领域术语

引用 `CONTEXT.md`：统一响应（ApiResponse）、SchemaInitializer、SSE（不适用本特性）。本特性特有术语（代码可观测）：

- **模型记录（Model Record）** — model 表一行，含 code（全局路由键）、purpose（用途分类）、apiUrl/apiKeyRef（网关直连参数）、status（online/offline）。〔ModelRecord.java:17-21 ‹CERTAIN›〕
- **模型预置（Model Preset）** — 预置模型套餐（llm/embedding/rerank 三组），当前为硬编码清单，无持久化。〔ModelServiceImpl.java:230-238 ‹CERTAIN›〕
- **三级拦截策略** — 敏感词的三种处置标志：阻止输入（blockInput）、联网检索屏蔽（blockSearch）、回答替换（replaceAnswer），以 JSON 存 category 字段。〔SensitiveWordController.java:6-8 ‹CERTAIN›〕
- **启用词条（Enabled Term）** — term_record.status=1 的术语条目，是同义扩展的唯一数据面。〔TermServiceImpl.java:30 ‹CERTAIN›；CONTEXT.md 查询增强词条〕
- **配置键（Config Key）** — sys_config 的唯一业务键（config_key UNIQUE），值以字符串（通常 JSON）存储，变更必留历史。〔schema.sql:786、ConfigManageServiceImpl.java:63-66 ‹CERTAIN›〕

## 4. 功能需求

### 4.1 模型接入管理

模型注册表是全仓 AI 能力的路由事实源：各消费链（摄取向量化、检索、图谱抽取、评测、对话）按 code+status 解析出 apiUrl/apiKey 直连网关。本域负责把「平台接入了哪些模型、哪些可用」维护为可查询的一致状态。

- **REQ-A01**：用户可按 keyword（名称模糊）与 purpose（精确）过滤模型列表，并可查看单条详情。〔证据：ModelServiceImpl.java:25-35 ‹CERTAIN›〕
- **REQ-A02**：用户可创建模型记录，提供名称、code、purpose、品牌、接口地址、API 密钥；status 未指定时落 offline（不上线即不被消费方路由到）。〔证据：ModelServiceImpl.java:158 ‹CERTAIN›；入参兼容 apiKey/apiKeyRef 双字段名 :153-155 ‹CERTAIN›〕
- **REQ-A03**：用户可部分更新模型记录（仅提供的字段覆盖），接口地址与密钥传空白值不覆盖既有值（防误清空）。〔证据：ModelServiceImpl.java:171-180 ‹CERTAIN›〕
- **REQ-A04**：用户可删除模型记录与切换 online/offline 状态；toggle 是 status 的唯一翻转入口。〔证据：ModelServiceImpl.java:195-206 ‹CERTAIN›〕〔决议 2026-09-24：删除保护取「不拦截、不级联提示」——物理删除为现状口径，治理规程依赖「先 offline 再 delete」；理由：引用面为按 code 的弱引用，删除后消费方解析落空**静默降级到默认网关**（resolveRecord null → apiUrl/apiKey null 下传，AiChunkServiceImpl.java:1183-1185 + LlmService.java:77-78 缺省 http://localhost:11434），无崩溃风险，拦截检查收益低于实现成本；现状差距：静默降级无告警（interfaces.md 本链消费点登记债务④关联）（代裁，用户可推翻）〕
- **REQ-A05**：用户可批量导入模型（数组逐条插入，purpose 缺省 chat、status 缺省 offline），并可导出模型清单（可按 purpose 过滤）。〔证据：ModelServiceImpl.java:209-226 ‹CERTAIN›〕〔决议 2026-09-24：导入查重取「纯追加、不查重」——导入语义=批量 create，同 code 重复由导入方数据质量负责；理由：与 ModelRegistry 契约既定口径一致（本契约不强制 code 唯一，schema.sql:523-531 无唯一约束），导入时静默合并/覆盖用户数据的风险大于重复行；现状差距：重复 code 在线记录使消费方 LIMIT 1 取值不确定（契约 Post 条件已声明，责任在生产方）（代裁，用户可推翻）〕
- **REQ-A06**：用户可查看预置模型套餐（llm/embedding/rerank 三组）。〔证据：ModelServiceImpl.java:230-238 ‹CERTAIN›〕〔决议 2026-09-24：预置套餐定位裁定为**只读静态推荐清单**（curated registry），create/update/delete 不在交付范围——三写端点应视为超配的假成功残面；理由：硬编码静态回显 + 前端仅消费 GET（fastrag-frontend/src/api/index.ts:1982-1991 四端点已接线但写路径无落库诉求证据），按最小改动收敛为只读；现状差距：presets 四端点假成功已登记债务①（interfaces.md platform-config 登记债务），收敛方向=下线三写端点或补持久化（代裁，用户可推翻）〕

### 4.2 模型连通性测试

接入模型后、投入业务前，用户需要验证模型真实可达：对话模型要能应答、Embedding 模型要能出向量、Rerank 模型要能排序。测试经 fastrag-ai 网关以该模型自身的 apiUrl/apiKey 直连发起。

- **REQ-B01**：用户可对指定模型发起对话测试（自定义 prompt，缺省固定问候语），返回应答文本与耗时。〔证据：ModelController.java:39-42、ModelServiceImpl.java:119-135 ‹CERTAIN›〕
- **REQ-B02**：用户可发起向量化测试（自定义文本，缺省「你好世界」），返回向量维度与前 10 维预览、耗时。〔证据：ModelServiceImpl.java:72-89 ‹CERTAIN›〕
- **REQ-B03**：用户可发起重排序测试（query + 文档列表，空列表时补缺省 3 文档），返回排序结果与耗时。〔证据：ModelServiceImpl.java:90-109 ‹CERTAIN›〕
- **REQ-B04**：测试类型由所调用的测试端点决定（test-chat 恒走 LLM 通道），不读取模型记录的 purpose 字段路由；模型不存在或未配置接口地址时返回明确的失败结果而非异常。〔证据：ModelServiceImpl.java:57-64（purpose 参数由 testChat/testEmbedding/testRerank 硬编码传入）、:58-64 前置校验 ‹CERTAIN›；javadoc「根据模型 purpose 路由」与实现不符——类注释失真已登记〕
- **REQ-B05**：任何测试失败（网络异常、网关报错）以统一失败结构返回（success=false + 错误消息），不抛出为 HTTP 错误。〔证据：ModelServiceImpl.java:137-139 ‹CERTAIN›〕

### 4.3 模型路由数据供给（生产方义务）

本特性是 model 表的**唯一写入方**，而消费方遍布各链——生产方写入的字段口径直接决定全仓 AI 调用能否路由成功。此域从「数据被谁、按什么口径消费」的角度约束写入行为。

- **REQ-C01**：模型记录的 status 取值须与消费方过滤口径一致（online/offline 二值；消费方按 status='online' 过滤），toggle 在二值间翻转。〔证据：ModelServiceImpl.java:203 ‹CERTAIN›；消费方口径 ChunkingServiceImpl.java:241、AiChunkServiceImpl.java:1588-1589 ‹CERTAIN›〕
- **REQ-C02**：code 是全局路由键——同 code 多条在线记录时消费方按 LIMIT 1 任取一条，生产方不提供同 code 唯一性保证。〔证据：消费方 selectOne+LIMIT 1 AiChunkServiceImpl.java:1590 ‹CERTAIN›；model 表 DDL 无 code 唯一约束 schema.sql:523-531 ‹CERTAIN›〕〔决议 2026-09-24 ‹CERTAIN（深证）›：purpose 值集为自由字符串（无枚举校验），消费方按字面 EMBEDDING/LLM 等精确过滤；种子 'Embedding'（data.sql:441）与查询 'EMBEDDING'（ChunkingServiceImpl.java:240）在**生产 MySQL 默认排序规则（utf8mb4_0900_ai_ci/generic_ci，大小写不敏感）下匹配成功**——schema.sql:523-531 仅声明 CHARSET=utf8mb4 未显式指定 collation，故依赖的是服务器默认 collation 而非表定义；**H2 测试库（MODE=MySQL，application-test.yml:10）字符串比较默认大小写敏感**，若 H2 种子与查询字面大小写不一致即失配。隐式依赖属实：显式声明 collation 或统一大小写是低成本收敛方向〕
- **REQ-C03**：测试与业务调用产生的模型调用记录可供监控分析消费（model_call_log）。〔决议 2026-09-24（R3 实证）：**全仓确认零写入方**——grep 全模块 ModelCallLog 仅实体/Mapper/operation 侧读取方（ModelMonitorServiceImpl）三处命中，无任何 Service/AOP/拦截器写库，数据源为死表、监控大屏恒空；写入时机问题随写入方缺失一并悬置，见 data.md 本表 GAP 裁决（设计写入点代裁）；现状差距：债务④已登记（interfaces.md platform-config 登记债务）〕

### 4.4 模型训练与测试报告

- **REQ-D01**：用户可发起模型训练并产生训练记录，可查看模型的训练历史。〔证据：ConfigManageController.java:47,50 ‹CERTAIN›〕〔决议 2026-09-24：按现状为准——现状为硬编码指标的假成功实现（ConfigManageServiceImpl.java:15-22，metrics 固定 {"loss":0.32,"accuracy":0.91}、status 直写 running），真实训练编排不存在且代码无任何意图注释/测试佐证「分期」解读；现状即 v1 交付边界（代裁，用户可推翻），实现偏离见 implement 文档假成功债务②〕
- **REQ-D02**：用户可发起模型测试并产生测试报告，可查看报告历史。〔证据：ConfigManageController.java:49,51 ‹CERTAIN›〕〔决议 2026-09-24：按现状为准——同 REQ-D01 假成功（ConfigManageServiceImpl.java:23-30，score 固定 "0.92"），无意图佐证（代裁，用户可推翻）〕

### 4.5 系统参数配置

系统参数是平台级键值对（品牌信息、通用默认参数、发布/审核开关等），供前端展示面与后端各链读取默认值；每次变更留审计历史。

- **REQ-E01**：用户可按 configType 查询配置项列表，可保存配置（upsert：键不存在新建、存在覆盖），每次保存产生一条变更历史（含旧值/新值/变更类型/操作人）。〔证据：ConfigManageServiceImpl.java:40-66 ‹CERTAIN›〕
- **REQ-E02**：用户可查询配置变更历史（按 configKey 过滤，最多最近 100 条，时间倒序）。〔证据：ConfigManageServiceImpl.java:67-71 ‹CERTAIN›〕
- **REQ-E03**：用户可将配置导出为 JSON 清单、批量导入配置（逐键 upsert）。〔证据：ConfigManageServiceImpl.java:72-80 ‹CERTAIN›〕
- **REQ-E04**：用户可将配置项标记为默认（is_default=1），查询默认配置清单。〔证据：ConfigManageServiceImpl.java:81-85 ‹CERTAIN›〕‹CERTAIN（2026-09-24 深证）：语义=「标记当前值为默认基准」而非独立保存一份默认值——config_key 全局唯一（schema.sql:786 UNIQUE 约束）使同键不可能存在并行的默认值行，setDefault 只能置位当前行（:82-85），结构上排除了另一解读›
- **REQ-E05**：用户可将配置重置为默认值。〔证据：ConfigManageController.java:66-69 端点存在 ‹CERTAIN›〕‹CERTAIN（2026-09-24 深证）：当前实现在 config_key UNIQUE 约束下不可能产生实际重置——当前行 is_default=1 时判「已是默认」跳过（ConfigManageServiceImpl.java:90-92）；≠1 时查询「同键且 is_default=1」的行（:95），在 UNIQUE 下命中的只能是自己（与自己 is_default≠1 矛盾）故恒空、静默跳过（:93-106 结构推演）——行为与需求名不符，登记为实现缺陷（implement 假成功债务③）›
- **REQ-E06**：发布开关、审核开关、审核流程、审核流程绑定、发布设置、审核设置、文档导读七类业务开关有专用读写端点，值以 JSON 存储于固定 configKey。〔证据：ConfigManageController.java:71-86 ‹CERTAIN›〕

### 4.6 安全策略与发布策略

- **REQ-F01**：用户可管理平台安全策略（按 policyType 过滤、增改删），创建时 enabled 缺省启用、priority 缺省 0，列表按 priority 升序。〔证据：ConfigManageServiceImpl.java:111-118 ‹CERTAIN›〕〔决议 2026-09-24：执行语义裁定为「v1 定位=纯注册面（策略元数据管理），执行引擎不在交付范围」；action 值域按 DDL 缺省 block 预留 block/warn/pass 三值语义（拦截/告警/放行），pattern 语义预留为关键字或正则表达式、由未来执行引擎定义；理由：全仓零消费方（grep 实测，前端仅管理页 CRUD），无执行语义可考，按端点排除铁律 StrategyRegistryModule (S) 不立契约；现状差距：执行引擎未实现，design §4 深化项登记（代裁，用户可推翻）〕
- **REQ-F02**：用户可管理发布策略（按 strategyType 过滤、增改删），缺省值与排序同 REQ-F01。〔证据：ConfigManageServiceImpl.java:120-127 ‹CERTAIN›〕〔决议 2026-09-24：config JSON 裁定为「结构由 strategy_type 约定、v1 无 schema、执行方不在交付范围」；若未来落地发布编排消费，消费点应在 publish-review 链 PublishManageModule 前置（与其发布计划执行器同批考虑）；理由：全仓零消费方，与 publish-review 链发布编排无代码关联（grep 实测）；现状差距：纯注册面无执行方，design §4 深化项登记（代裁，用户可推翻）〕

### 4.7 数据字典

- **REQ-G01**：用户可查询字典数据：全量按类型分组返回，或按 type 过滤单组；条目含 key/label/value/enabled/remark，label 为空时回退显示 key（兼容存量数据）。〔证据：DictionaryController.java:41-67 ‹CERTAIN›〕
- **REQ-G02**：用户可查询全部字典类型清单（去重）。〔证据：DictionaryServiceImpl.java:42-49 ‹CERTAIN›〕
- **REQ-G03**：用户可增改删字典条目；enabled 入参兼容布尔/数字/字符串；列表按 sortOrder 与 id 排序。〔证据：DictionaryController.java:78-117、DictionaryServiceImpl.java:25 ‹CERTAIN›〕

### 4.8 敏感词治理与内容过滤

敏感词是平台内容安全的数据面：每个词带三级拦截标志与替换文本，启用词表被对话链路逐消息消费——输入侧拦截、输出侧替换。词表变更频率低而检查频率极高，需要缓存。

- **REQ-H01**：用户可维护敏感词（增改删查），列表返回前端适配格式（reply 映射 replacement、category JSON 解析为三个布尔字段、enabled 布尔化）。〔证据：SensitiveWordController.java:49-101 ‹CERTAIN›〕
- **REQ-H02**：创建/导入的敏感词默认启用；更新时三级标志按请求体重建（未传标志视为 false）。〔证据：SensitiveWordController.java:77,89,158-164 ‹CERTAIN›〕〔决议 2026-09-24：按现状为准——「全量重建 category」为既定实现，测试断言仅覆盖 list 适配面（SensitiveWordControllerTest.java:60-70）、代码无意图注释，无法佐证「有意为之」；现状风险=部分更新误关拦截面，修复方向=update 改为未传标志沿用旧值（代裁，用户可推翻；债务②已登记 interfaces.md 本链）〕
- **REQ-H03**：用户可下载 CSV 导入模板并批量导入（跳过表头行，逐行解析词/回复/三标志），导入完成词表缓存失效。〔证据：SensitiveWordController.java:107-156 ‹CERTAIN›；空文件返回业务错误 :124-126 ‹CERTAIN›〕
- **REQ-H04**：输入检查：文本命中任一启用词的 blockInput 标志时，返回该词配置的指定回复（未配置则用固定兜底文案），命中词本身不回显给用户。〔证据：SensitiveWordServiceImpl.java:79-94 ‹CERTAIN›〕
- **REQ-H05**：输出过滤：文本命中任一启用词的 replaceAnswer 标志时，将该词替换为指定回复（未配置用 ***），有替换才返回新文本。〔证据：SensitiveWordServiceImpl.java:97-116 ‹CERTAIN›〕
- **REQ-H06**：启用词表缓存读加载、任何写操作（增改删导入）失效；仅 enabled=1 的词进入检查面。〔证据：SensitiveWordServiceImpl.java:20-37,119-135 ‹CERTAIN›〕
- **REQ-H07**：通用过滤原语支持 reject（命中即拒绝）/replace（替换）/mask（首字保留余字打码）三种模式。〔证据：SensitiveWordServiceImpl.java:40-76 ‹CERTAIN›〕〔决议 2026-09-24：按现状为准——生产链路仅消费 checkInput/filterOutput 两专用口，checkAndFilter 现无生产调用方（grep 实测仅 SensitiveWordServiceTest 五用例消费）。「保留为通用底座」的意图解读与「废弃候选」均无代码证据，按现状即事实源处理：保留方法与测试（代裁，用户可推翻）〕
- **REQ-H08**：联网检索屏蔽（blockSearch）标志可存储可回显。〔决议 2026-09-24（R3 实证）：**blockSearch 确认为纯存储标志，无任何拦截行为**——后端全仓 grep 零消费方（仅 category JSON 子串解析回显，SensitiveWordServiceImpl.java:150-158）；前端仅管理页存储/回显（sensitive-words.vue），联网检索执行链（retrieval）未读取该标志；现状差距：债务已登记（interfaces.md 本链 Invariants 偏离①），联网屏蔽语义未实现（代裁，用户可推翻）〕

### 4.9 术语库

术语库组织领域专业词汇（术语→别名组），供检索侧同义扩展消费；仅启用词条参与扩展（CONTEXT.md 查询增强词条口径）。

- **REQ-I01**：用户可维护术语库（增删改查），删除库级联删除其下全部词条。〔证据：TermServiceImpl.java:37-69 ‹CERTAIN›〕
- **REQ-I02**：用户可维护术语条目（增删改查、按 libraryId 过滤），词条在库间移动时两库的 term_count 计数联动增减且不为负。〔证据：TermServiceImpl.java:72-128 ‹CERTAIN›〕
- **REQ-I03**：词条 status 支持启用/禁用（缺省启用，入参兼容布尔/数字）。〔证据：TermServiceImpl.java:83,99,131-135 ‹CERTAIN›〕
- **REQ-I04**：同义扩展：查询含某术语时追加其全部别名、查询含某别名时追加该术语；别名支持中英文逗号分隔多值；已在查询中出现的词不重复追加；结果去重保序；仅消费启用词条。〔证据：TermServiceImpl.java:27-34,138-176 ‹CERTAIN›〕
- **REQ-I05**：词条缓存读加载、任何词条写操作失效。〔证据：TermServiceImpl.java:21-34,88,107,117 ‹CERTAIN›〕

### 4.10 查询规则

- **REQ-J01**：用户可维护查询规则：按 type 查询、创建（name/type/pattern，默认启用）、删除、启停切换。〔证据：QueryRuleServiceImpl.java:10-13 ‹CERTAIN›〕〔GAP: 规则 pattern/action 的匹配执行语义 — 消费方缺失（retrieval 链已登记 applyQueryRules 占位、query_rule 零消费），当前为纯数据面〕

### 4.11 通知中心

- **REQ-K01**：用户可按 userId 与 status 过滤通知列表（创建时间倒序）、查询未读计数。〔证据：NotificationController.java:37-47 ‹CERTAIN›〕
- **REQ-K02**：系统各链可产生通知（如摄取完成的知识更新提醒，target_user 为空即全员广播）。〔证据：IngestionConsumer.java:470-481 ‹CERTAIN›〕
- **REQ-K03**：用户可标记单条已读、全部已读（可按 userId 限定范围）、删除通知；标记不存在的通知静默成功。〔证据：NotificationController.java:48-55 ‹CERTAIN›〕

### 4.12 通用上传

- **REQ-L01**：用户可上传品牌类图片（仅 jpg/png、不超过 1MB），存储后返回可访问 URL。〔证据：UploadController.java:45-72 ‹CERTAIN›〕‹CERTAIN（2026-09-24 深证）：任务指派中「/api/upload 两阶段上传」与代码不符——该端点为单阶段直传（校验类型/大小 → MinIO 直传 → 返 URL，:45-72 全路径无 pending 状态与 MQ），两阶段（pending→MQ 异步）属 file-ingestion 的 FileController，以代码为准仲裁完毕›

## 5. 非功能约束

- 全部端点统一返回 ApiResponse，业务错误 = HTTP 200 + body.code；写操作经 @Loggable 审计（Model/ConfigManage/SensitiveWord 之外的控制器无审计注解——Notification/Dictionary/Term/QueryRule/Upload 全部无 @Loggable）。〔各控制器实测 ‹CERTAIN›〕〔决议 2026-09-24：按现状为准——无审计非「有意取舍」（代码无说明、无注释佐证），按现状即事实源处理：五控制器写操作暂不入审计面；现状差距：不对称性已登记债务⑥（interfaces.md platform-config 登记债务），修复方向=补 @Loggable（代裁，用户可推翻）〕
- 词表/词条缓存为进程内 volatile 引用，写失效读加载，无 TTL——缓存失效域=单 JVM 进程‹CERTAIN（2026-09-24 深证：SensitiveWordServiceImpl.java:20、TermServiceImpl.java:21 volatile 字段，失效即本进程置 null 重载，无跨进程通知机制）›；多实例部署下其他实例的缓存不失效（部署形态属环境配置，代码外信息——若多实例部署需引入分布式失效或共享缓存，属运维约束非代码缺陷）。
- 配置历史查询固定 LIMIT 100 截断。〔ConfigManageServiceImpl.java:70 ‹CERTAIN›〕
- 敏感词检查为逐词 String.contains 全文扫描，O(词数×文本长)。〔SensitiveWordServiceImpl.java:48-70 结构观测 ‹CERTAIN›〕〔决议 2026-09-24：取保守性能预期——词表 ≤1000 条且文本 ≤1 万字符时单次同步检查 <50ms（现有实现可承载，对话逐消息调用不构成瓶颈）；超此规模（词表万级或长文本批量扫描）应换 Aho-Corasick/双数组 Trie；理由：代码外 SLA 按业界内容安全过滤惯例保守取值；现状差距：无监控指标埋点，超规模征兆不可观测（代裁，用户可推翻）〕

## 6. 验收清单

- [ ] 新建模型未指定 status 时为 offline，不出现在任何消费方的路由结果中（REQ-A02）
- [ ] toggle 后模型 status 在 online/offline 间精确翻转（REQ-A04、REQ-C01）
- [ ] 更新模型时传空 apiUrl/apiKey 不清空既有值（REQ-A03）
- [ ] test-chat/test-embedding/test-rerank 对同一模型分别产生 LLM/向量/排序三类结果，且都带耗时字段（REQ-B01、REQ-B02、REQ-B03）
- [ ] 对不存在或无 apiUrl 的模型发起测试返回 success=false 的结构化结果（HTTP 200）（REQ-B04）
- [ ] 模型测试网络失败返回统一失败结构，不产生 HTTP 4xx/5xx（REQ-B05）
- [ ] 每次保存配置（新建/更新）都在 sys_config_history 留一条含新旧值的记录（REQ-E01）
- [ ] 配置历史按 configKey 过滤且不超过 100 条（REQ-E02）
- [ ] 敏感词列表的 reply 字段来自 replacement、三个布尔字段来自 category JSON 解析（REQ-H01）
- [ ] 命中 blockInput 词的输入返回指定回复且不回显命中词（REQ-H04）
- [ ] 命中 replaceAnswer 词的输出被替换为回复（缺省 ***），无命中时原文本返回（REQ-H05）
- [ ] 导入 CSV 跳过表头，返回导入条数，导入后新词立即生效于检查（REQ-H03）
- [ ] 禁用（enabled=0）的敏感词不参与输入检查与输出过滤（REQ-H06）
- [ ] 仅 status=1 的术语词条参与同义扩展；禁用词条即时退出（缓存失效）（REQ-I03、REQ-I04）
- [ ] 查询含术语追加别名、含别名追加术语，双向且去重（REQ-I04）
- [ ] 术语跨库移动后两库 term_count 一增一减；删除到 0 不出现负数（REQ-I02）
- [ ] 删除术语库后其下词条无残留（REQ-I01）
- [ ] 保存配置的历史含变更类型 create/update 区分（REQ-E01）
- [ ] 查询规则 toggle 在启用/禁用间翻转（REQ-J01）
- [ ] 通知未读计数仅统计 status=unread；read-all 后计数归零（REQ-K01、REQ-K03）
- [ ] 非 jpg/png 或超 1MB 的上传被拒绝并返回明确错误（REQ-L01）
- [ ] 字典条目 label 为空时列表回退显示 key（REQ-G01）
- [ ] 模型列表按 keyword 模糊与 purpose 精确过滤，单条详情可查（REQ-A01）
- [ ] 批量导入模型逐条插入且 purpose/status 缺省 chat/offline，导出可按 purpose 过滤（REQ-A05）
- [ ] 预置模型套餐 llm/embedding/rerank 三组可查看（现实现为无持久化的假成功回显）（REQ-A06；豁免：占位/债务登记，见 implement 文档）
- [ ] 同 code 多条在线记录时消费方按 LIMIT 1 任取，生产方无唯一性保证（REQ-C02 现状断言）
- [ ] model_call_log 全仓无写入方、监控大屏数据源为死表（REQ-C03 债务登记现状；豁免：占位/债务登记，见 implement 文档）
- [ ] 发起模型训练产生训练记录并可在历史中查看（现为实现假成功、指标硬编码）（REQ-D01；豁免：占位/债务登记，见 implement 文档）
- [ ] 发起模型测试产生测试报告并可在历史中查看（同为实现假成功、评分固定）（REQ-D02；豁免：占位/债务登记，见 implement 文档）
- [ ] 配置可导出 JSON 清单、批量导入逐键 upsert（REQ-E03）
- [ ] 配置项标记默认后出现在默认配置清单（REQ-E04）
- [ ] 重置为默认端点现状不产生实际重置（config_key UNIQUE 约束下静默跳过，已登记实现缺陷）（REQ-E05；豁免：占位/债务登记，见 implement 文档）
- [ ] 发布开关/审核开关/审核流程等七类业务开关专用端点读写生效，值以 JSON 存固定 configKey（REQ-E06）
- [ ] 平台安全策略可按 policyType 过滤增改删，enabled/priority 缺省生效、列表按 priority 升序（纯注册面、无消费方现状）（REQ-F01；豁免：占位/债务登记，见 implement 文档）
- [ ] 发布策略可按 strategyType 过滤增改删（全仓无执行方现状）（REQ-F02；豁免：占位/债务登记，见 implement 文档）
- [ ] 字典类型清单去重返回（REQ-G02）
- [ ] 字典条目增改删生效，enabled 入参兼容布尔/数字/字符串，列表按 sortOrder 与 id 排序（REQ-G03）
- [ ] 更新敏感词未传三级标志时标志按请求体重建为 false（全量重建现状断言）（REQ-H02）
- [ ] checkAndFilter 通用原语 reject/replace/mask 三模式正确（当前无生产调用方，保留为通用底座）（REQ-H07；豁免：占位/债务登记，见 implement 文档）
- [ ] blockSearch 标志可存储可回显（全仓无消费方现状）（REQ-H08；豁免：占位/债务登记，见 implement 文档）
- [ ] 词条缓存读加载、任何词条写操作失效（REQ-I05）
- [ ] 系统各链可产生通知，target_user 为空即全员广播（REQ-K02）

## 7. 测试策略

经由公共接口最重要要测的：**敏感词过滤管线的边界**（分类标志解析的脆弱字符串匹配、空替换兜底、缓存失效时效、禁用词退出、mask 中文多字节边界）与**模型测试代理**（fastrag-ai 网关是真缝——测试失败不外抛、三类测试各自的返回结构、前置校验分支）。其次：sys_config 的 upsert+历史一致性（同键连续保存的历史链）、术语 term_count 的移动/删除守恒、模型 status 口径（offline 模型不可被路由——与消费方联动验证）。数据治理三族（字典真实字段/术语启用状态/敏感词 check-filter）为近期修复面，回归价值最高；假成功族（presets/train/test/reset-to-default）在真实实现落地前只测「契约形态」不测「业务效果」。
