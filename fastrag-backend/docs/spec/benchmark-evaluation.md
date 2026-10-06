# 评测与基准（benchmark-evaluation）— 需求规格

## 1. 特性与范围

本特性是知识库检索质量的度量闭环：**基准测试（Benchmark）** 管理一组带标准答案与标准检索 Chunk 的测试题目（可 LLM 自动生成、可手动创建），**评测（Evaluation）** 基于某个基准集异步跑完「逐题检索 → 算 Recall@K → LLM 生成答案并评判正确性 → 聚合综合分」，产出可查询的评测报告。显式排除：检索本身的质量增强手段（归 retrieval）、图谱的构建与治理（归 knowledge-graph）、评测报告的图表化展示（前端）、评测任务的调度重跑/定时评测（代码无调度器证据）。

## 2. Out of Scope

- **不提供检索增强能力**：本特性消费检索结果算指标，不修改检索管线本身（混合检索编排归 `retrieval` 特性）；评测请求里选的检索模式/rerank 只作用于本次评测。
- **不做答案生成的对话编排**：评测中的 LLM 问答是一次性调用，不含会话、上下文记忆、流式输出（归 app-center/agent）。
- **不做图谱构建**：graph 出题模式只**读** `kb_graph_entity`/`kb_graph_relation`，不触发、不等待图谱构建（归 knowledge-graph）。
- **不做基准题目的单题手工编辑端点**：代码只有基准集粒度的 CRUD 与题目列表查看（BenchmarkController.java:52-65），无单题增删改入口。
- **不做跨知识库的基准集共享/导入导出**：基准集按 kbId 隔离。
- **不做评测指标的在线告警/质量门禁**：无任何阈值判断或告警代码。

## 3. 领域术语

引用 `CONTEXT.md`：**图谱评估（Graph Evaluation）**（父术语，本特性即其落地）、**知识分片（Chunk）**（评测的检索单元与 goldChunks 载体）、**知识图谱（Knowledge Graph）**（graph 出题模式的语料源）、**混合检索（Hybrid Retrieval）**（评测默认检索模式）、**统一响应（ApiResponse）**。本特性特有：

- **基准测试（Benchmark）** — 一组测试题目及其元信息（题数、是否含标准 chunk/答案、是否自动生成），表 `kb_benchmark`；其题目存 `kb_benchmark_question`。
- **评测（Evaluation）** — 对一个基准集的一次跑分执行单元，表 `kb_evaluation`，逐题结果存 `kb_evaluation_result`。
- **Recall@K** — 标准 chunk 出现在检索结果前 K 条中的命中判定（K=1/3/5/10）。
- **跨界切断题（Cross-boundary Question）** — 答案刻意横跨两个相邻 Chunk 的题目，用于度量「分片边界切断后上下文能否被完整召回」。
- **上下文完整度（Context Completeness）** — 标准答案拆要点后检索上下文能支撑的比例（0~1），对标 Ragas context_recall 思路。

## 4. 功能需求

### 4.1 基准集管理（Benchmark Lifecycle）

基准集是评测的前提资产：没有带标准答案/标准 Chunk 的题目集，检索质量就只能人工感觉。本域负责基准集与题目的生命周期。

- **REQ-A01**：基准集按知识库隔离，支持列表（创建时间倒序）、按 id 查详情、按基准集查题目列表（按题号升序）。〔证据：BenchmarkServiceImpl.java:74-78,86-91 ‹CERTAIN›〕
- **REQ-A02**：支持手动创建空基准集：题数缺省 0、标记为非自动生成、不含标准 Chunk 与标准答案，创建动作记操作日志（日志失败不阻断创建）。〔证据：BenchmarkServiceImpl.java:94-112 ‹CERTAIN›〕
- **REQ-A03**：删除基准集必须级联删除其全部题目（先删题目后删基准集）。〔证据：BenchmarkServiceImpl.java:230-241 ‹CERTAIN›〕
- **REQ-A04**：题目作为从属资源持久化，每题携带：题面、题号、标准答案、标准检索 Chunk 标识列表（JSON 字符串）、跨界切断标志。〔证据：KbBenchmarkQuestion.java:30-38、schema.sql:251-260 ‹CERTAIN›〕
- **REQ-A05**：支持从 JSONL 文本导入题目（每行一题：question 必填、goldAnswer/goldChunks 可选；坏行跳过；批量插入与基准集题数更新在同一事务内，导入后标记含标准答案）。〔证据：BenchmarkServiceImpl.java:158-227 ‹CERTAIN›；〔决议 2026-09-24：维持死代码冻结——本版不新增 HTTP 入口；若未来产品需要，暴露为 POST /api/kb/{kbId}/benchmarks/{id}/import 并沿用「逐行跳坏行 + 批量插入与题数累加同事务」语义。理由：全后端零调用方（grep 复测 2026-09-24 仅 ：158 定义处），导入面未进入任何版本需求，最小改动=保持现状。现状差距：无（有意冻结，债务已登记见 design 债务①、implement §BenchmarkGenerator Deviations）（代裁，用户可推翻）〕〕
- **REQ-A06**：基准集的创建/生成/删除均记操作日志（类别 operation、动作 benchmark_created/generated/deleted），日志写失败仅告警不阻断主流程。〔证据：BenchmarkServiceImpl.java:105-110,132-138,234-240、ActionType.java:108-111 ‹CERTAIN›〕

### 4.2 LLM 生成基准问题（Benchmark Generation）

手工出题成本高且覆盖不了真实语料分布；用 LLM 从知识库自身语料出题是本特性的核心杠杆。生成是异步的：接口立即返回基准集记录，题目后台落库。

- **REQ-B01**：自动生成采用两段式——先同步创建基准集记录（标记自动生成、题数先写请求值）立即返回，再异步生成题目；生成失败时把题数清零，避免界面显示「N 道题」而实际为 0。〔证据：BenchmarkServiceImpl.java:117-143、BenchmarkGenerationHelper.java:134-138,164-177 ‹CERTAIN›〕
- **REQ-B02**：vector 模式基于知识库文档 Chunk 出题：取 Chunk（每题候选数缺省 5，取数上限 = 候选数×3 且 ≤30 条），把候选片段内容（每条截 500 字符）喂给 LLM，要求返回 JSON 数组问答对；知识库无 Chunk 时降级为基于一般知识出题。〔证据：BenchmarkGenerationHelper.java:215-259（空库降级 :222-227）‹CERTAIN›〕
- **REQ-B03**：graph 模式基于图谱实体（取 20 条）与关系（取 20 条）出题，并用实体名 LIKE 检索包含实体的 Chunk（前 5 个实体名、10 条）作为 goldChunks 候选；有候选 Chunk 时要求 LLM 填入完整 Chunk id。〔证据：BenchmarkGenerationHelper.java:261-344 ‹CERTAIN›〕
- **REQ-B04**：支持跨界切断测试集：开关打开时要求每题答案横跨两个编号相邻的文档片段，goldChunks 必须同时含这两个片段的完整 id。〔证据：BenchmarkGenerationHelper.java:239-248、BenchmarkConfig.java:38-42 ‹CERTAIN›〕
- **REQ-B05**：LLM 输出必须防御解析——空响应/错误提示串返回空题目列表；markdown 围栏与前后夹带说明文本自动剥壳；损坏 JSON 抛出由调用方降级；实际题目数以解析成功数为准，覆盖先写入的请求题数。〔证据：BenchmarkResponseParser.java:49-78、BenchmarkGenerationHelper.java:120-129,145-162 ‹CERTAIN›〕
- **REQ-B06**：生成用 LLM 走流式收集 + 长超时（秒数可配置，缺省 300）与 8192 输出上限（防长 JSON 截断）；指定非默认模型时按模型 code 从模型表解析 API 地址与密钥，解析失败降级默认网关。〔证据：BenchmarkGenerationHelper.java:67-71,187-213 ‹CERTAIN›；‹CERTAIN（2026-09-24 深证：bootstrap 的 application.yml 与 application-test.yml 全文均无 benchmark: 段，`@Value("${benchmark.llm-timeout:300}")` 恒取缺省 300s）›〕

### 4.3 评测运行异步编排（Evaluation Run Orchestration）

评测一次跑一个基准集，逐题检索+问答+评判，题量与 LLM 延迟决定了它必须异步且可查进度。状态机是本域的正确性核心。

- **REQ-C01**：启动评测前必须校验：基准集参数缺失/空白，或所选基准集题目数为 0 时，**不启动执行**，而是创建一条 status=failed 的评测记录返回，避免跑出「completed 0 分」的误导结果。〔证据：EvaluationServiceImpl.java:92-104,132-146 ‹CERTAIN›〕
- **REQ-C02**：校验通过后创建 status=running、已完成数 0 的评测记录并立即返回，实际评测异步执行；评测未配置时使用全缺省配置（检索模式缺省 hybrid）。〔证据：EvaluationServiceImpl.java:106-130、EvaluationController.java:87-91、EvaluationConfig.java:35-42 ‹CERTAIN›〕
- **REQ-C03**：异步执行开始时先把待评测题数写入 dataCount；若此刻发现题目为空（与删除/导入的竞态），置 status=failed 而非 completed。〔证据：EvaluationExecutionHelper.java:146-163 ‹CERTAIN›〕
- **REQ-C04**：评测执行全程任意异常必须把评测记录落到 status=failed 终态，不允许永远停留在 running。〔证据：EvaluationExecutionHelper.java:237-246 ‹CERTAIN›〕
- **REQ-C05**：支持实时进度查询：返回已完成数/总题数/进度百分比；评测记录不存在时返回 status=not_found 的查询成功结构而非报错。〔证据：EvaluationController.java:65-85 ‹CERTAIN›〕
- **REQ-C06**：单题执行失败不拖垮整体：该题落一条全零指标、判错、原因为「执行异常」的兜底结果行，其余题照常。〔证据：EvaluationExecutionHelper.java:362-376 ‹CERTAIN›〕
- **REQ-C07**：删除评测级联删除其全部结果行，且仅删除当前知识库下存在的记录。〔证据：EvaluationServiceImpl.java:149-164 ‹CERTAIN›〕
- **REQ-C08**：评测的启动与删除记操作日志（evaluation_run/evaluation_deleted），写失败不阻断。〔证据：EvaluationServiceImpl.java:118-124,158-163、ActionType.java:104-105 ‹CERTAIN›〕

### 4.4 指标计算与结果（Metrics & Results）

指标的正确性是本特性的存在理由：数字错了，一切质量决策都被误导。本域定义检索指标、答案指标、聚合口径与结果持久化。

- **REQ-D01**：Recall@K 判定规则：把题目的标准 Chunk 解析为 id 列表（解析失败视为空），检索结果前 K 条中含任一标准 Chunk 即该题 R@K 记 1，否则记 0；标准 Chunk 为空的题恒为 0。K∈{1,3,5,10}，检索统一取前 10 条。〔证据：EvaluationExecutionHelper.java:267-280,869-885 ‹CERTAIN›〕
- **REQ-D02**：汇总口径：各 R@K 与答案准确率 = 命中题数 ÷ 总题数；综合分 = R@10×0.7 + 答案准确率×0.3；完成时写入 status=completed、耗时毫秒、全部汇总指标。〔证据：EvaluationExecutionHelper.java:216-235,889-912 ‹CERTAIN›〕
- **REQ-D03**：评测检索支持 vector/hybrid/fulltext 三模式（缺省 hybrid）与可选重排序：向量结果为空或检索异常一律降级全文检索；hybrid = 向量 2×topK + 全文 2×topK 经 RRF（k=60）融合；重排序失败保持原序。〔证据：EvaluationExecutionHelper.java:413-439,441-497,499-510,514-549,553-579,583-619 ‹CERTAIN›〕
- **REQ-D04**：答案生成与正确性评判合并为一次 LLM 调用（评判模型与答案模型不同时用评判模型执行），只喂前 2 条最相关 Chunk，输出最小 JSON（答案 + 是否正确 + 理由）。〔证据：EvaluationExecutionHelper.java:282-291,621-658 ‹CERTAIN›〕
- **REQ-D05**：LLM 评判链路任何失败（超时错误串/解析失败/调用异常）降级为规则评判：标准答案中的关键数字或 2~4 字中文关键词在生成答案中的命中率 ≥0.8 判对，否则判错；检索内容为空直接判错不调 LLM。〔证据：EvaluationExecutionHelper.java:634-637,660-689,817-865 ‹CERTAIN›〕
- **REQ-D06**：跨界切断题额外计算上下文完整度两个值：原始命中上下文（每 Chunk 截 600 字）与父块扩展上下文（每 Chunk 截 1200 字）各自对标准答案的要点支撑比例（0~1，上限截断 8000 字符）；LLM 失败降级为中文关键词命中比例；两个值随结果行落库并进检索指标串。〔证据：EvaluationExecutionHelper.java:316-343,713-735,741-797 ‹CERTAIN›〕
- **REQ-D07**：逐题结果行持久化题面、生成答案、检索指标串、结构化 R@K 四列、上下文完整度两列、是否判对、评判理由；评测详情查询按评测聚合并按结果 id 升序返回全部结果行。〔证据：EvaluationExecutionHelper.java:345-359、EvaluationServiceImpl.java:69-83 ‹CERTAIN›〕

## 5. 非功能约束

- **并发上限**：评测单题并行度固定 4（信号量 + 4 线程守护池），防止 LLM/Embedding API 限流。〔证据：EvaluationExecutionHelper.java:89-109 ‹CERTAIN›〕
- **批量向量化**：所有题目一次性批量 embedding（自定义模型路由可用时），减少 HTTP 往返；失败降级逐条并向量化，仍失败以空向量占位（空向量触发全文检索降级）。〔证据：EvaluationExecutionHelper.java:384-409,443-445 ‹CERTAIN›〕
- **异步代理约束**：@Async 方法必须落在独立 Spring Bean 上经代理调用，禁止同类自调用（两处 Helper 均为独立 Bean；应用已开 @EnableAsync）。〔证据：BenchmarkGenerationHelper.java:56-58、EvaluationExecutionHelper.java:83-85、FastRagApplication.java:32 ‹CERTAIN›〕
- **全文检索合规**：不使用 Elasticsearch；评测全文检索走 MySQL `MATCH...AGAINST`（BOOLEAN 模式）+ LIKE 兜底。〔证据：EvaluationExecutionHelper.java:520-548 ‹CERTAIN›〕
- **性能阈值**（生成 10 题耗时、评测题数上限等）：〔决议 2026-09-24：取「生成 questionCount ≤50、单次评测题数 ≤500，超限拒绝」为建议上限——生成输出上限 8192 tokens 按 10 题≈3K tokens 估算容纳约 25 题，50 为留量拒绝线；评测 4 并发 × 单题秒级 LLM 耗时，500 题约数十分钟，无调度器/断点续跑能力前不宜更大。理由：异步跑分批量的业界保守默认 + 与既有防御上限（可视化 5000、候选 2000）同一量级思路。现状差距：两入口均无任何上限校验（BenchmarkConfig.java:31 无 @Max、EvaluationController.run 无题数检查）——登记债务：入口上限校验缺失（代裁，用户可推翻）〕
- **鉴权面**：两控制器均无 KB 级 ACL 注解、服务层无权限校验（grep 实测无 @KbAuth/@PreAuthorize/KbAccessChecker），知识库资源越权防护缺口登记为债务，非本规格要求。〔证据：BenchmarkController.java:44-50、EvaluationController.java:47-53 ‹CERTAIN›；影响面‹CERTAIN 补证（2026-09-24）：SecurityConfig.java:60-83 全局 Spring Security 过滤器链对 /api/** 强制 authenticated（白名单外），JwtAuthFilter/ApiTokenAuthFilter 兜底登录态，未认证请求 401 拦截——但兜底仅到「登录用户」粒度，KB 级 ACL 无全局兜底（越权债务维持登记）›〕

## 6. 验收清单

- [ ] 新建空基准集后，列表可见该记录且题数为 0、两个 gold 标志均为「无」？（REQ-A02）
- [ ] 删除基准集后，其题目在题目表不可再查得？（REQ-A03）
- [ ] 对无题目基准集发起评测，返回的评测记录 status=failed 且未产生任何异步执行与结果行？（REQ-C01）
- [ ] 对有效基准集发起评测，立即返回的记录 status=running，稍后进度查询能看到 completedCount/dataCount/progress 递增？（REQ-C02、REQ-C05）
- [ ] 评测完成（异常路径除外）后记录必为 completed 且含 R@1/3/5/10、答案准确率、综合分、耗时？（REQ-D02、REQ-C04）
- [ ] 生成请求立即返回的记录题数等于请求值；生成失败后同一记录题数变为 0？（REQ-B01）
- [ ] 用带 markdown 围栏/前后说明文本的 LLM 响应喂解析器，仍能解出题目数组；空串/错误串解出 0 题？（REQ-B05）
- [ ] 某 10 条检索结果中第 4 条是标准 Chunk：该题 R@1/R@3 为 0、R@5/R@10 为 1？（REQ-D01）
- [ ] LLM 评判模型离线时评测仍能完成：受影响题目的 isCorrect 由规则评判给出并带降级 reason？（REQ-D05、REQ-C06）
- [ ] 跨界题的结果行含 context_completeness 与 context_completeness_extended 两列且非跨界题为 NULL？（REQ-D06）
- [ ] 删除评测后，kb_evaluation_result 中该评测的结果行全部消失？（REQ-C07）
- [ ] 基准集列表按创建时间倒序、按 id 查详情与按集查题目列表（题号升序）可用？（REQ-A01）
- [ ] 题目持久化携带题面/题号/标准答案/goldChunks JSON 串/跨界切断标志？（REQ-A04）
- [ ] JSONL 导入逐行解析（坏行跳过）且批量插入与题数更新同事务（当前无 HTTP 入口，REQ-A05 已裁决维持死代码冻结）？（REQ-A05；豁免：占位/债务登记，见 implement 文档）
- [ ] 基准集创建/生成/删除各记操作日志，日志写失败仅告警不阻断？（REQ-A06）
- [ ] vector 模式取候选 Chunk（截 500 字符）喂 LLM 出题，知识库无 Chunk 时降级一般知识出题？（REQ-B02）
- [ ] graph 模式基于实体/关系出题并以实体名 LIKE 检索 goldChunks 候选，有候选时要求填入完整 Chunk id？（REQ-B03）
- [ ] 跨界切断开关打开时每题答案横跨两个编号相邻片段且 goldChunks 同时含两个完整 id？（REQ-B04）
- [ ] 生成走流式收集+长超时（缺省 300s）+8192 输出上限，非默认模型按 code 解析失败降级默认网关？（REQ-B06）
- [ ] 异步执行开始发现题目为空（与删除/导入竞态）时置 status=failed 而非 completed？（REQ-C03）
- [ ] 评测启动与删除各记 evaluation_run/evaluation_deleted 操作日志？（REQ-C08）
- [ ] vector 模式向量空/检索异常一律降级全文检索；hybrid 为双路 2×topK 的 RRF（k=60）融合；重排序失败保持原序？（REQ-D03）
- [ ] 答案生成与正确性评判合并为一次 LLM 调用（必要时用评判模型），只喂前 2 条最相关 Chunk？（REQ-D04）
- [ ] 逐题结果行持久化题面/答案/指标串/R@K 四列/上下文完整度两列/判对/理由，详情按结果 id 升序返回全部？（REQ-D07）

## 7. 测试策略

经由公共接口（BenchmarkService / EvaluationService / 进度查询面）验证，优先级从高到低：

1. **指标计算正确性**（最重要）：构造已知 goldChunks 位置与已知 LLM 响应的受控场景，断言 R@K 四值、准确率、综合分公式（R@10×0.7+acc×0.3）逐位正确；规则评判的 0.8 命中率阈值边界（0.8 恰好判对）。
2. **异步状态机**：running→completed / running→failed（全程异常、空题竞态）/ 校验失败→failed（不进入异步）/ not_found 读面；每个终态都可由公共接口观测。
3. **LLM 输出防御**：解析器的标准 JSON/围栏/夹带文本/错误串/截断五类输入的行为矩阵（已有 6 个单测覆盖解析器本体）。
4. **检索模式与降级**：三模式选择、向量空/异常→fulltext 降级、hybrid RRF 融合次序、rerank 失败保序——全部只 mock 系统边界（Milvus/Embedding/Rerank/LLM/Mapper）。
5. **失败隔离**：单题异常产生兜底结果行、其余题不受影响；删除级联完整性。
