# 评测与基准（benchmark-evaluation）— 架构设计

## 1. Module Map

| 模块 | Responsibility | Files | Depth Rationale |
|---|---|---|---|
| **BenchmarkFacade**（基准集门面，浅 **(S)** 偏中） | 基准集与题目的生命周期编排：列表/详情/题目查询、手动创建、删除级联、操作日志记账；generate 请求的同步建记录 + 异步分派 | controller/BenchmarkController.java、service/BenchmarkService.java、service/impl/BenchmarkServiceImpl.java | 门面本身是 mapper 编排（6 方法皆短），深逻辑在生成器；但「先写请求题数、异步后覆盖/清零」的两段式语义和级联删除顺序是调用方不该知道的约束，故独立成模块而非 controller 直通 mapper |
| **BenchmarkGenerator**（基准问题生成器，深模块） | 把「一个知识库 + 出题配置」折叠为「一批带 gold 数据的题目」：vector/graph 双语料源出题、跨界题构造、LLM 调用（流式长超时 + 模型表路由 + 降级）、防御解析、事务落库与计数修正 | service/impl/BenchmarkGenerationHelper.java、util/BenchmarkResponseParser.java | LLM 输出不可控的全部应对（五类畸形输入、超时、模型路由失败）与语料源差异都藏在 `generateQuestions` 之后；调用方只得到「记录已建、题目稍后出现」的稳定语义 |
| **EvaluationFacade**（评测门面，中深） | 评测生命周期：前置校验（基准参数/空基准）、running 记录创建、异步分派、详情聚合（评测 + 全部结果行）、删除级联、进度读面、操作日志 | controller/EvaluationController.java、service/EvaluationService.java、service/impl/EvaluationServiceImpl.java | 「校验失败也落一条 failed 记录」与「详情双表聚合」是状态机一致性的守护逻辑，不是透传；进度百分比计算（含 not_found 形态）收敛在 controller 一处 |
| **EvaluationExecutor**（评测执行器，深模块） | 把「一个评测 id + 配置」折叠为「一份完整评测报告」：批量向量化 → 4 并发逐题（检索 → Recall@K → 合并 LLM 问答评判 → 跨界题完整度）→ 指标聚合 → 结果落库 → 终态转移 | service/impl/EvaluationExecutionHelper.java（938 行） | 并发控制、降级链（向量→全文、LLM→规则）、指标公式、失败隔离全部内藏；调用方（EvaluationFacade）只表达「执行它」，状态机终态由本模块保证 |
| EvalRetrievalPipeline（评测检索管线，ExecutionExecutor 内藏的私有函数族） | 评测专用精简检索：vector/hybrid/fulltext 三模式 + RRF(k=60) 融合 + 可选 Rerank，统一签名供单题调用 | EvaluationExecutionHelper.java 私有方法（:413-619） | 不独立成 Bean 是现状事实：与 EvaluationExecutor 同生命周期、无第二调用方；独立成模块的价值仅在切换到复用 retrieval 检索引擎时出现（见 ADR-1） |
| BenchmarkResponseParser **(S)** | LLM 响应 → 题目列表的无状态静态解析（剥围栏/截区间/空防御） | util/BenchmarkResponseParser.java | **(S)** 纯函数工具，仅被 BenchmarkGenerator 一处消费；不建议内联（有独立测试资产 BenchmarkResponseParserTest 6 用例锚定行为） |

债务登记（既有代码，非本设计引入）：① **JSONL 导入无入口**——BenchmarkServiceImpl.importFromJsonl（:158-227）全后端零调用方，benchmark_imported 动作仅其消费；② **门面 KB 越权面**——BenchmarkFacade 的 getDetail/listQuestions/delete 不校验 kbId（selectById 直查，:81-83,230-241），EvaluationFacade 全路径校验 kbId（:70-72,150-153），两门面防护不一致且均无 @KbAuth；③ **控制器死字段**——两 Controller 注入的 LogService 从未使用（日志实际在 service 层记录，BenchmarkController.java:50、EvaluationController.java:53）；④ **评测结果落库非事务**——结果行逐条 insert 无事务包裹（EvaluationExecutionHelper.java:230-232），中途失败将留下「failed 状态 + 部分结果行」的混合态。

## 2. Interface Contracts

（HTTP 端点不在此列——端点排除铁律；完整 Input/Output/Error modes 见 contract/interfaces.md `## benchmark-evaluation` 节。）

### BenchmarkFacade

HTTP 暴露面：`/api/kb/{kbId}/benchmarks` list/get/questions/create/generate/delete 6 端点〔BenchmarkController.java:44-85 ‹CERTAIN›〕。

Public interface（BenchmarkService.java:32-44 ‹CERTAIN›）：
- `list(kbId) → List<KbBenchmark>` — 按创建时间倒序的本库基准集列表
- `getDetail(kbId, id) → KbBenchmark` — 单条详情
- `listQuestions(kbId, benchmarkId) → List<KbBenchmarkQuestion>` — 题目列表（题号升序）
- `create(kbId, form) → KbBenchmark` — 手动创建空基准集（含日志）
- `generate(kbId, config) → KbBenchmark` — 两段式生成入口：同步建记录并返回，异步出题
- `delete(kbId, id) → void` — 级联删除题目后删基准集

Hidden implementation：题数先写后修正协议、gold 标志推导（生成路径恒 1/1）、JSONL 导入（无入口死代码）、全部操作日志 try-catch。

Depth rationale：调用方（controller/前端）不需要知道「异步生成何时完成、失败了怎么办」——题数字段就是唯一进度真相。

### EvaluationFacade

HTTP 暴露面：`/api/kb/{kbId}/evaluations` list/get/status/run/delete 5 端点〔EvaluationController.java:47-99 ‹CERTAIN›〕。

Public interface（EvaluationService.java:26-34 ‹CERTAIN›）：
- `list(kbId) → List<KbEvaluation>` — 按创建时间倒序
- `getDetail(kbId, id) → KbEvaluation` — 详情（聚合 results 结果行列表）
- `run(kbId, config) → KbEvaluation` — 校验 + 建 running 记录 + 异步分派；校验失败返回 failed 记录
- `delete(kbId, id) → void` — 级联删除结果行

Hidden implementation：空基准拒绝协议（failed 记录形态）、kbId+id 双键查询、进度百分比换算与 not_found 结构（EvaluationController.java:65-85）。

Depth rationale：run 返回值稳定为「一条评测记录」，调用方无需区分「已启动」与「被拒绝」——status 字段承载全部语义。

### EvaluationExecutor

Public interface（EvaluationExecutionHelper.java:111-112 ‹CERTAIN›）：
- `execute(evaluationId, kbId, config) → void`（@Async）— 异步执行完整评测并把状态机推到 completed/failed 终态

Hidden implementation：批量 embedding 与模型路由、4 并发信号量调度、三模式检索管线与 RRF/Rerank、Recall@K 判定、合并 LLM 问答评判与规则降级、跨界题父块扩展与完整度双值、指标聚合公式、结果批量落库、终态事务更新。

Depth rationale：这是全特性唯一「以副作用为输出」的深模块——入参三个标量，出参是 4 张表的状态变化；评测方法论（Ragas 思路、RRF、合并调用）的每次演化都不触碰门面与契约。

## 3. Seam Diagram

| 缝 | What varies | Adapters | Stable side | Placement rationale |
|---|---|---|---|---|
| 缝 1：出题语料源缝 | 出题依据：文档 Chunk / 图谱实体+关系 | `buildVectorPrompt`（kb_chunk 采样，含跨界变体）、`buildGraphPrompt`（kb_graph_entity+relation+LIKE 命中 Chunk）——2 个 adapter，**真缝**〔BenchmarkGenerationHelper.java:215-259,261-344 ‹CERTAIN›〕 | 稳定侧 = 「Prompt 文本 → LLM → 题目列表 → 落库」的生成主干（generateQuestions :79-139）与响应解析器；新增语料源（如 QA 对出题）只加一个 buildXxxPrompt | 语料源随知识库内容形态演化（图谱治理、分片策略都在变），而生成主干与落库协议稳定；按 buildMethod 字符串分派（:87-91） |
| 缝 2：评测检索模式缝 | 单题检索通道：vector / hybrid / fulltext | 三个 executeXxxSearch 函数 + RRF 融合 + Rerank 装饰——3+ adapter，**真缝**〔EvaluationExecutionHelper.java:413-439 switch 分派、:441-619 实现 ‹CERTAIN›〕 | 稳定侧 = `retrieveChunksWithVector(kbId, query, topK, 预计算向量, 配置) → List<Map>` 统一签名；降级（向量空/异常→fulltext）藏在缝内 | 评测方法论要求「同一基准集可在不同检索模式下对比跑分」，模式增删不触碰指标计算与状态机 |
| 缝 3：评判策略缝 | 答案正确性判定：LLM 合并评判 / 规则评判（数字命中率、中文关键词命中率） | LLM 路径（generateAnswerWithJudgment+parseJudgmentResponse）与规则路径（ruleBasedJudge/buildRuleJudgmentResponse）——2 个 adapter，**真缝**（运行时降级切换，非编译期二选一）〔EvaluationExecutionHelper.java:621-689,817-865 ‹CERTAIN›〕 | 稳定侧 = `{answer, isCorrect, reason}` 三元组语义；两边输出同构（规则路径产同格式 JSON 串） | 评判模型离线/超时/输出畸形是常态而非异常，降级必须对指标口径透明（isCorrect 语义不变，仅 reason 追溯差异） |

不立缝说明：LlmService/EmbeddingService/RerankService/MilvusService 是 fastrag-ai/infra 的既有多消费依赖（各自单实现，属假设缝——抽象收益归上游特性），本特性只做消费方登记，不重复抽象；model 表动态路由（api_url/api_key_ref 解析，BenchmarkGenerationHelper.java:195-205、EvaluationExecutionHelper.java:919-937）作为 LLM 消费的公共惯例直接复用。

## 4. Deepening Opportunities

- **BenchmarkFacade**：可吞入 JSONL 导入的 HTTP 入口（现方法已是完整实现：逐行防御解析 + 事务落库 + 计数更新，只差一个端点与表单参数），消灭死代码；可吞入单题手工增删改（需求侧尚未出现）。
- **BenchmarkGenerator**：可吞入生成进度可观测（现在生成中无进度面——题数要么是请求值要么是终值，UI 无法区分「生成中」与「已完成」；加一个生成状态字段或状态机即可）；可吞入题目质量防线（EntityNameGuard 式的题面清洗，借鉴 knowledge-graph 的治理线）。
- **EvaluationFacade**：可吞入评测对比视图（跨次评测的同基准指标 diff——数据已齐，缺聚合查询）。
- **EvaluationExecutor**：最大深化机会是吞并或转接 EvalRetrievalPipeline（见 ADR-1）；其次是吸收批量结果落库的事务化与失败补偿（现逐条 insert）。

## 5. Parent Trace

| 设计单元 | 追溯到 Spec |
|---|---|
| BenchmarkFacade | REQ-A01、A02、A03、A04、A05、A06；REQ-C05（进度读面对称物：基准侧题数即进度） |
| BenchmarkGenerator | REQ-B01~B06 |
| EvaluationFacade | REQ-C01、C02、C05、C07、C08；REQ-D07（详情聚合半边） |
| EvaluationExecutor | REQ-C03、C04、C06；REQ-D01~D07 |
| EvalRetrievalPipeline（缝 2） | REQ-D01（topK=10 载体）、REQ-D03 |
| 缝 1（出题语料源） | REQ-B02、B03、B04 |
| 缝 3（评判策略） | REQ-D04、D05、REQ-C06（失败隔离的评判半边） |
| 非功能：并发/批量/异步代理/全文合规 | §5 全部条目 ← EvaluationExecutor + BenchmarkGenerator + EvaluationFacade |
| EvaluationFacade | REQ-C08（2026-09-24 review 补锚：评测启动/删除记 evaluation_run/evaluation_deleted 操作日志，写失败不阻断） |

## 6. ADR

**ADR-1：评测自带精简检索管线（EvalRetrievalPipeline），不消费 retrieval.RetrievalSearchEngine。**

- **背景**：任务口径与 feature-map 均把 retrieval 列为本特性依赖（「评测跑检索、算 recall@k」），但代码事实是 EvaluationExecutionHelper 直连 EmbeddingService/MilvusService/RerankService + JdbcTemplate 全文检索（:42-45,99-102 import 与字段 ‹CERTAIN›，grep 全模块无 RetrievalService 引用），与上游契约 `## retrieval` 节的 RetrievalSearchEngine 形成平行实现。
- **决策**：评测维护自己的三模式检索函数族（固定 topK=10、预计算向量复用、RRF(k=60)、Rerank 可选），不引入 RetrievalRequest/RetrievalConfig 六通道配置面。
- **理由**：评测检索要求**跨次可比性**——配置面越窄，两次跑分的差异越可归因于知识库内容而非检索参数漂移；且批量预计算向量与六通道引擎的逐查询配置合并模型不匹配。代价是两处检索行为可能漂移（如 chunkType 过滤、graph 通道能力差异），已作为债务登记。
- **后果与状态**：已落地（HEAD d8f30cd）。若未来评测需要 graph/qa/clause 通道对比，或两处漂移造成指标口径争议，应反转决策为消费 RetrievalSearchEngine（缝 2 的稳定签名使该替换不触碰指标计算）。
- **关联**：与 ADR-0001（绑定-重切一致性）无冲突；与 ADR-0002（Neo4j 唯一实现）无冲突——本特性对图谱只读（graph 出题语料）。

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
