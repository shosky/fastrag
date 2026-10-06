# 问答对提炼（qa-generation）— 架构设计

> Spec：[../spec/qa-generation.md](../spec/qa-generation.md)（5 功能区 15 REQ）；裁决：`fastrag/.tmp/idd-qa-generation-grill-decisions.md`（D1-D14 + P1-P6）。
> 总评：异步富化流水线特性，与知识图谱构建（knowledge-graph）同构不同源——消息拓扑复刻 graph-build（含 2026-09-11 生产事故教训），但互斥介质、降级策略、执行归属三处有意偏离，见缝 2 与消费者模块。

## 1. Module Map

### 问答对提炼模块（QaGenerationModule）— 唯一深模块
- **Responsibility**：触发受理（参数校验 → 文件级互斥 → 任务落账 → 投递）+ 生成编排（父分片加载 → 逐片 LLM 提炼 → 防御解析 → 静态闸门 → replace/append 单事务落库 → 统计回写）。
- **Files**：`fastrag-knowledge/.../service/QaGenerationService.java`、`service/impl/QaGenerationServiceImpl.java`；任务账本数据面 `entity/KbQaGenerationTask.java`、`mapper/KbQaGenerationTaskMapper.java`（每文件一行 upsert，字段面仿 kb_graph_index，schema.sql:219-233）。
- **Depth Rationale**：调用方只见受理与查询两个动作，背后吞掉——prompt 三约束与自定义指令拼装、JSON 数组防御解析（复用 `BenchmarkResponseParser.parseQuestions`，knowledge→graph-eval 依赖已声明于 fastrag-knowledge/pom.xml:19，不新增耦合方向）、归一化判重（复用 `ExcelQaPairImporter.normalizeQuestion`，:325 已是 public static，导入与提炼同一口径零改造）、replace 模式判重基线分叉（同文件旧 ai 对不入基线）、长度护栏（问题<5 字/答案<2 字）、单事务收尾（replace=删该文件旧 ai 对+插新 / append=仅插新；删旧放在收尾事务而非任务开头，中途崩溃旧数据无损）、任务行计数心跳回写。删掉本模块，这条流水线会在控制器与消费者两处重演。

### 提炼任务消费者模块（QaGenerationConsumerModule）
- **Responsibility**：MQ 消费调度（即时 ack）→ 应用内执行器（`qa-generation-worker` 线程池）→ 进程内文件级互斥 → 陈旧 running 行恢复判定（超过 `qa.generation.stale-minutes:30` 未更新心跳的 running 行标 failed「执行中断」）。
- **Files**：`fastrag-knowledge/.../consumer/QaGenerationConsumer.java`。
- **Depth Rationale**：复刻 GraphBuildConsumer 骨架（:176-216——MQ 即时 ack + 长任务移出消费者 + 互斥，2026-09-11 生产事故的直接教训），复刻后调用 QaGenerationModule 的执行入口。与图谱模板的三处**有意偏离**：①互斥粒度文件级（非 KB 级）；②互斥介质 DB 任务行态为准 + 进程内集合防同 JVM 竞态（图谱纯内存集合重启即失效，spec REQ-A04 要求持久化判定）；③陈旧行恢复清扫（图谱无此需求，其 KB 级重投消息天然自愈）。

### 提炼入口控制器（QaGenerationController）— **(S)**，不建议内联（HTTP 面与鉴权横切）
- **Responsibility**：受理、查询两个端点透传，`@KbAuth` editor/viewer 分级 + `@Loggable` 审计。
- **Files**：`fastrag-knowledge/.../controller/QaGenerationController.java`。
- **Depth Rationale**：纯透传（与 QaPairController 同浅度）；不内联是因为 HTTP 暴露面 + KB ACL 横切需要独立挂载点。

### 摄入挂钩（IngestionConsumer 内嵌变更）— 非模块，登记变更点
- **Files**：`fastrag-knowledge/.../consumer/IngestionConsumer.java`（步骤 7 图谱自动触发同区，:494-515 旁）。
- **内容**：摄入收尾处判定 `processingMode == "qa"` → 读文件级 `qaGenerationConfig` → 调 QaGenerationModule 受理入口；MQ 不可用或互斥命中不阻断摄入主流程（任务行落 failed 供观测）。

### 检索准入过滤（RetrievalServiceImpl 内嵌变更）— 非模块，登记消费点
- **Files**：`fastrag-retrieval/.../service/impl/RetrievalServiceImpl.java`（qaChannelRecall，:798-839）。
- **内容**：查询条件加 `source='manual' OR status='confirmed'`——qa-pairs design「Deepening Opportunities」预言之兑现（原文：'在检索侧 qaChannelRecall 加一行过滤即可，无需新模块'）。

## 2. Interface Contracts

### QaGenerationModule

**Public interface**（调用方所见）：
- `trigger(kbId, fileId, QaGenerationConfig) → QaGenerationTaskView` — 受理一次提炼：校验参数（条数 1-10 等）→ 文件级互斥判定 → 任务行落账（running）→ 投递消息；互斥命中或参数非法抛业务错误
- `status(kbId, fileId) → QaGenerationTaskView?` — 最近一次任务：状态（idle/running/success/partial/failed）+ 总/成功/失败分片 + 参数快照 + 错误信息
- `execute(kbId, fileId, QaGenerationConfig)` — 消费者专用执行入口：跑完整编排流水线并回写任务终态（进程内互斥由消费者侧持有，此方法假定互斥已过）

**Hidden implementation**（调用方不可见）：父分片读取（经 KbChunkMapper，fileId+父级判定，共享表模式同 kb_chunk 既有读取假设）；prompt 拼装；解析容错；闸门两道与判重基线分叉；产出先缓冲内存、任务收尾单事务落库；计数心跳（每片完结即回写任务行，兼作陈旧判定心跳）。

**Depth rationale**：trigger/status/execute 三个动词背后是分钟级、几十次 LLM 调用、多阶段闸门的流水线；调用方（控制器、摄入挂钩、消费者）没有一个需要知道分片、闸门或事务的存在。

### QaGenerationConsumerModule

**Public interface**：`@RabbitListener(queues = "fastrag.qa-generation.queue", concurrency = "3")` 消息入口（消息体 kbId+fileId+config，schema 归 contract/data.md）；应用内执行器仅被本模块调度。

**Hidden implementation**：即时 ack（防 consumer_timeout 重投事故重演）；`qa-generation-worker` 线程池（默认 3，与 MQ 消费并发同量——一消息=一文件任务，无内部分片并行，分片在任务内串行）；进程内 `runningFiles` 集合；陈旧 running 行清扫。

**Depth rationale**：消费者只做「调度 + 生命周期」，全部领域行为在 QaGenerationModule——与 GraphBuildConsumer.runGraphBuild 内联业务（:219 起）相比，业务归属更收拢，代价是一次接口调用。

## 3. Seam Diagram

### 缝 1：LLM 网关缝（复用 fastrag-ai，非本特性所立）
- **What varies**：模型路由（默认网关 / model 表自定义路由）。
- **Adapters**：LlmService（Ollama 兼容网关 WebClient，唯一实现）——既有单适配器缝，本特性纯消费；模型选择走请求级参数（对齐 BenchmarkGenerationHelper.java:83-84,187-211 先例）。
- **Stable side**：LlmService.chat/chatWithTimeout。
- **Placement rationale**：模型接入是全后端统一能力面（feature-map 共享层），提炼不自带任何模型逻辑。

### 缝 2：异步投递边界（消息拓扑扩展，不立缝——单适配器）
- **What varies**：异步任务类型（ingestion / graph-build / qa-generation）。
- **Adapters**：1 个基础设施（RabbitMQ DIRECT_EXCHANGE + 新持久化队列 `fastrag.qa-generation.queue`，routing key `qa-generation`；RabbitMQConfig 增队列与绑定声明、MessagePublisher 增 publishQaGeneration）。
- **Stable side**：MessagePublisher 发布语义 + 持久化队列保证（spec NFR「重启不丢」的承载机制）。
- **Placement rationale**：复用既有 DIRECT_EXCHANGE 拓扑，零新增基础设施概念。**有意偏差**：与 publishGraphBuild 的同步降级 handler 不同，提炼**不做**同步降级——图谱降级是为保上传主流程不因 MQ 宕机中断，而提炼不在关键路径上，MQ 不可用时受理端直接业务失败并把任务行落 failed（错误=队列不可用），比在 HTTP 线程里静默跑分钟级同步任务诚实。

### 缝 3：触发来源汇点（汇点非缝）
- **What varies**：无——手动（控制器）与自动（摄入挂钩）是两个调用方汇入同一 `trigger` 接口，不存在可变维度。
- **Placement rationale**：两调用方一接口是深度收益（Locality），按判据不立缝；登记于此仅为 Parent Trace 可溯。

### 缝 4：检索 qa 通道数据缝（消费点登记，沿用 qa-pairs design 缝 3）
- **What varies**：QA 对如何被检索利用。
- **Adapters**：仅 1 个读取方 RetrievalServiceImpl 直连 KbQaPairMapper——不立缝，登记消费点。
- **Stable side**：kb_qa_pair 表语义。本特性对表语义的唯一变更 = ai 来源行的准入规则（ADR-0005 固化）与新增溯源关联列。
- **Placement rationale**：召回逻辑归检索特性所有；本特性只改查询条件一行，不动通道结构（权重 0.3、qaRecallCount 语义不变）。

## 4. Deepening Opportunities

- **QaGenerationModule**：可再吸收——分片级重生成（单父分片重跑，不必整文件）、闸门规则可配置化（护栏阈值、自定义校验）、任务历史多份保留（现单行覆盖）、qa 通道向量化（WeKnora 式问题独立索引，若立项）——接口三动词不变。当前不做：无需求信号。
- **QaGenerationConsumerModule**：可吸收 KB 级批量任务（一消息多文件）与并发参数分级（编辑库/大库差异化 worker 数）——当前单文件消息粒度已满足 REQ-A02 批量（每文件一消息，天然并行）。
- **检索侧**：准入规则若未来引入「 viewer 可见性分级的召回」，落点同 qaChannelRecall 一行条件——已有先例位。

## 5. Parent Trace

| Spec REQ | Design 落点 | 说明 |
|---|---|---|
| REQ-A01/A02 | QaGenerationModule.trigger + QaGenerationController | 参数校验（1-10 缺省 3）、三入口汇流 |
| REQ-A03 | IngestionConsumer 挂钩 + QaGenerationModule.trigger | qa 模式自动触发；参数文件级持久化（kb_file 扩列，DDL 归 contract） |
| REQ-A04 | QaGenerationConsumerModule（DB 行态互斥 + 进程内集合）+ trigger 互斥分支 | 持久化判定，重启不失效 |
| REQ-A05 | QaGenerationModule 隐藏实现（收尾事务 replace 分支） | 删旧在收尾事务，崩溃无损 |
| REQ-B01 | QaGenerationModule（父分片读取 + 溯源关联写入） | 提取单位与溯源 |
| REQ-B02 | QaGenerationModule 隐藏实现（prompt 三约束） | 锚定原文 |
| REQ-B03 | QaGenerationModule（复用 BenchmarkResponseParser） | 解析容错 |
| REQ-B04 | QaGenerationModule（逐片循环 + 重试 1 次 + 计数心跳） | 失败隔离与统计 |
| REQ-B05 | QaGenerationModule（闸门：normalizeQuestion 复用 + 护栏） | 零 LLM 成本静态闸门 |
| REQ-C01 | （复用 qa-pairs QaPairModule.confirm，零改造） | 确认状态机不新增 |
| REQ-C02 | 检索准入过滤（RetrievalServiceImpl 一行条件）+ ADR-0005 | 本特性唯一检索侧变更 |
| REQ-C03 | （QA 面板 ai 标记，前端） | 后端仅表语义支持 |
| REQ-D01/D02 | QaGenerationModule.status + 任务账本（每文件一行 upsert） | 四态+统计+参数快照；历史靠 @Loggable 审计 |
| REQ-D03 | QaGenerationModule（溯源关联写入 + 悬空降级查询语义） | 展示层降级，不清理 |
| REQ-E01/E02 | （既有级联沿用：FileServiceImpl 重切守卫/清理面 + 溯源列随行） | 零新增级联代码 |
| NFR 异步/持久化/吞吐 | 缝 2（持久化队列）+ QaGenerationConsumerModule（并发 3 + worker 池） | 不做同步降级的偏差已声明 |

## 6. ADR

- **ADR-0004**（[adr/0004-qa-pair-table-consolidation.md](adr/0004-qa-pair-table-consolidation.md)）：AI 问答对与人工问答对共用 kb_qa_pair 单表，source 列区分——数据拓扑的最高层决策。
- **ADR-0005**（[adr/0005-ai-draft-recall-admission.md](adr/0005-ai-draft-recall-admission.md)）：AI 问答对确认前不参与召回——检索语义修订的 WHY。
- 粒度（父分片 vs 整篇 Map-Reduce）、任务单行覆盖、无同步降级三处取舍**不立 ADR**（可逆：数据可再生/配置级调整），理由已分别记录于模块 Depth Rationale、任务账本字段面与缝 2 偏差声明。
