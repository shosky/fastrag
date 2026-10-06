# 评测与基准（benchmark-evaluation）— 测试设计

> on-board 模式产出：本文件是测试**设计**而非测试代码；既有测试（BenchmarkResponseParserTest 6 用例）已映射进切片 T1 并标注【已有】。红绿循环未执行；执行时按本文件从 Tracer Bullet（T1）起步。

## 1. 切片规划

| 切片 | 契约接口 | AFK/HITL | 说明 |
|---|---|---|---|
| S0 评测结论人评 | EvaluationExecutor | **HITL** | 「这组 R@K / 综合分是否反映真实检索质量」需人判定：跨界题完整度对比的结论有效性、规则评判判对是否真对——自动化只产数字不产结论；执行一次小基准集全链跑通即为验收演示 |
| S1 指标口径族 | EvaluationExecutor | AFK | **重点用例族**：R@K 判定（gold 位置矩阵）、汇总率与综合分公式逐位断言、规则评判 0.8 阈值边界、完整度钳制 0~1 |
| S2 状态机族 | EvaluationExecutor（run 协议） | AFK | **重点用例族**：校验拒绝→failed 不异步、running→completed 终态全指标、空题竞态→failed、全程异常→failed、进度 not_found 形态 |
| S3 出题防御族 | BenchmarkGenerator | AFK | **重点用例族**：解析器五类输入行为矩阵【已有 6 用例】、生成失败清零、成功覆盖题数、模型路由降级、空库出题降级 |
| S4 检索管线族 | EvaluationExecutor（缝 2） | AFK | 三模式分派、向量空/异常→fulltext、MATCH→LIKE 二级降级、RRF 融合次序、rerank 失败保序 |
| S5 生命周期族 | BenchmarkFacade + EvaluationFacade | AFK | 创建缺省值、删除级联（双向：基准→题目、评测→结果行）、列表排序、详情聚合 |

边界 mock 白名单（真缝/系统边界）：LlmService、EmbeddingService、RerankService、MilvusService、JdbcTemplate（MySQL 边界——kb_chunk/kb_graph_*/model 读取与结果落库）、TransactionTemplate（真实事务可直连测试库时不用 mock）、各 Mapper（KbBenchmarkMapper/KbBenchmarkQuestionMapper/KbEvaluationMapper/KbEvaluationResultMapper）、LogService。**禁 mock 内部模块**：BenchmarkResponseParser、规则评判、RRF 融合、指标聚合一律走真实实现；run→execute 用真实 Helper 串联（异步用同步直调 execute 断言终态，另设 1 用例验证 @Async 分派不阻塞）。

## 2. 测试用例（逐接口）

### BenchmarkGenerator（contract: benchmark-evaluation.BenchmarkGenerator）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| T1【已有】标准 JSON 数组解析出题目列表 | 合法 QA JSON 数组 | parseQuestions | 2 题，question/goldAnswer/goldChunks 字段完整 | REQ-B05 | 无（纯函数） |
| T2【已有】markdown 围栏包裹可解析 | ```json [...] ``` 包裹 | parseQuestions | 解出 2 题 | REQ-B05 | 无 |
| T3【已有】前后夹带说明文本可解析 | "以下是问答对：\n[...]\n共2个" | parseQuestions | 解出 2 题 | REQ-B05 | 无 |
| T4【已有】LLM 错误串返回空列表 | "模型调用失败: Timeout ..." | parseQuestions | 空列表不抛异常（回归锚定） | REQ-B05、E_GENERATION_EMPTY | 无 |
| T5【已有】空串/null/空白返回空列表 | null / "" / " \n " | parseQuestions | 空列表 | REQ-B05 | 无 |
| T6【已有】截断 JSON 上抛由调用方降级 | 缺 ] 的数组 | parseQuestions | 抛异常（调用方 catch→清零） | REQ-B05 | 无 |
| T7 生成成功覆盖题数 | generate 建记录题数=10，LLM 返回 6 题 | execute 生成路径完成 | question_count=6，6 行题目 questionIndex 0..5 连续，与题数更新同事务 | REQ-B01、REQ-B05 | LlmService/Mapper/JdbcTemplate |
| T8 生成失败清零计数 | LLM 抛异常 | execute 生成路径 | question_count=0，基准记录仍存在，无题目行 | REQ-B01、E_GENERATION_FAILED | 同上 |
| T9 空响应清零并保留记录 | LLM 返回空串 | execute 生成路径 | 空列表路径 → 题数清零、warn 记账 | REQ-B01、E_GENERATION_EMPTY | 同上 |
| T10 自定义模型路由失败降级默认网关 | llmModel="gpt-x"，model 表查无 | execute 生成路径 | 仍以默认网关发起 1 次 LLM 调用，生成继续 | REQ-B06、E_MODEL_ROUTE_FALLBACK | LlmService/JdbcTemplate(model) |
| T11 无 Chunk 知识库降级一般知识出题 | kb_chunk 空 | vector 模式生成 | prompt 为一般知识模板（goldChunks 约定空数组），LLM 被调用 1 次 | REQ-B02、E_VECTOR_CORPUS_EMPTY | JdbcTemplate(kb_chunk) |
| T12 跨界题配置传导 | crossBoundary=true | vector 模式生成 | 落库题目行 cross_boundary=1；prompt 含相邻片段双 id 约束 | REQ-B04 | LlmService/JdbcTemplate |
| T13 graph 模式只读图谱语料 | kb_graph_entity/relation 各 2 条 | graph 模式生成 | prompt 含实体与关系行；无任何图谱写调用 | REQ-B03 | JdbcTemplate(kb_graph_*) |

### EvaluationExecutor（contract: benchmark-evaluation.EvaluationExecutor，含 run 协议与缝 2/缝 3）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| T14 基准参数空白拒绝 | config.benchmark=null | run | 返回 status=failed、三计数 0 的记录；execute 未被调用、无结果行 | REQ-C01、E_BENCHMARK_MISSING | Mapper |
| T15 空基准拒绝 | benchmark 存在但 0 题 | run | 同上（failed 记录、不异步） | REQ-C01、E_BENCHMARK_EMPTY | Mapper |
| T16 有效启动返回 running | 基准 2 题 | run | 返回 status=running、completedCount=0 记录；dataCount 随后被置 2 | REQ-C02、REQ-C03 | Mapper/Embedding/Milvus/Llm |
| T17 完成终态全指标 | 2 题全部评估成功 | execute 同步直调 | status=completed、duration>0、R@K/accuracy/overall_score 按公式落库；结果行 2 条 | REQ-C02、REQ-D02 | 同上 |
| T18 空题竞态置 failed | execute 开跑时题目已空 | execute | status=failed、completedCount=0、dataCount=0；不产生 completed 0 分 | REQ-C03、E_BENCHMARK_EMPTY | Mapper |
| T19 全程异常落 failed 终态 | 批量 embedding 抛异常且逐条降级也抛 | execute | status=failed；无永久 running | REQ-C04、E_EXECUTION_FAILED | Embedding/Mapper |
| T20 进度 not_found 形态 | 评测 id 不存在 | status 查询 | HTTP 200 结构 {status:"not_found", completedCount:0, dataCount:0, progress:0} | REQ-C05 | 无 |
| T21 进度百分比计算 | completedCount=1, dataCount=4 | status 查询 | progress=25（整除截断） | REQ-C05 | Mapper |
| T22 单题失败隔离 | 题 2 检索抛异常，题 1 正常 | execute | 题 2 兜底行（R@K 全零、isCorrect=0、judgeReason=执行异常）；题 1 指标正常；整体 completed | REQ-C06、D_QUESTION_FAILED | Milvus（仅题 2 抛）/Llm/Mapper |
| T23 gold 命中位置矩阵 | gold=[cX]，检索序 10 条 | 逐位置执行 R@K 判定 | gold 在第 4 位 → R@1/3=0、R@5/10=1；第 1 位 → 全 1；第 11 位外 → 全 0 | REQ-D01 | JdbcTemplate(kb_chunk) |
| T24 gold 为空/损坏恒零分 | goldChunks=null / "not-json" | R@K 判定 | R@1/3/5/10 全 0 | REQ-D01 | 同上 |
| T25 综合分公式 | R@10=0.8, accuracy=0.5 | 聚合 | overall_score=0.8×0.7+0.5×0.3=0.71（±1e-9） | REQ-D02 | 无（聚合纯逻辑经受控场景驱动） |
| T26 规则评判数字命中率边界 | gold 含 2 个数字，生成答案含其中 1 个（50%）| ruleBasedJudge | isCorrect=false；含 2 个（100%）、1.6/2（80%）→ true | REQ-D05 | 无（经 LLM 错误串触发降级路径） |
| T27 评判 LLM 错误串降级 | chat 返回「模型调用失败: ...」 | 合并评判 | 规则评判产出 isCorrect + 降级 reason；结果行落库 | REQ-D05、D_JUDGE_RULE_FALLBACK | LlmService |
| T28 检索内容为空直接判错 | 检索返回空列表 | 合并评判 | isCorrect=0、reason=检索内容为空、未调 LLM | REQ-D05 | Milvus/JdbcTemplate |
| T29 向量异常降级全文 | milvusService.search 抛异常 | vector 模式单题 | 结果来自 MATCH/LIKE 路径，指标照算 | REQ-D03、D_RETRIEVAL_DEGRADED | Milvus 抛/JdbcTemplate |
| T30 hybrid RRF 融合次序 | 向量序 [a,b,c]，全文序 [b,d] | hybrid 单题 | 融合序 b（双路）> a > c > d；k=60 | REQ-D03 | 同上 |
| T31 rerank 失败保持原序 | rerankService 抛异常 | enableRerank=true | 返回原 topK 序，指标照算 | REQ-D03 | RerankService 抛 |
| T32 跨界题完整度双值 | crossBoundary=1 题，LLM 可用 | execute | 结果行 context_completeness 与 _extended 非 NULL 且 ∈[0,1]；非跨界题为 NULL | REQ-D06 | Llm/JdbcTemplate |
| T33 完整度规则兜底 | 完整度 LLM 抛异常 | 跨界题执行 | 走中文关键词命中比例兜底（或无关键词→NULL），不失败 | REQ-D06 | Llm 抛 |
| T34 删除评测级联 | 评测含 3 行结果 | delete | 结果行 0 条、评测行消失；他库同 id 不可删（kbId 校验） | REQ-C07 | Mapper |

### BenchmarkFacade / EvaluationFacade（生命周期，端点排除层经 Service 公共接口测）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| T35 手动创建缺省值 | form 仅 name | create | questionCount=0、isAutoGenerated=0、has_gold_*=0；日志失败不阻断（LogService 抛异常仍返回记录） | REQ-A02、REQ-A06 | Mapper/LogService 抛 |
| T36 删除基准级联删题 | 基准含 5 题 | delete | 题目 0 条、基准行消失 | REQ-A03 | Mapper |
| T37 列表排序 | 同库 3 基准不同 createdAt | list | createdAt 降序 | REQ-A01 | Mapper |
| T38 详情聚合结果行 | 评测含 3 行结果 | getDetail | results 按 id 升序 3 条挂载 | REQ-D07 | Mapper |
| T39 JSONL 导入事务与计数（死代码冻结测试） | 3 行合法 + 1 行坏 + 1 空行 | importFromJsonl | 插入 3 题、question_count+3、has_gold_answer=1；坏行跳过不中断 | REQ-A05 | Mapper |
| T40 @Async 分派不阻塞 | 真实 Spring 上下文 | generate / run | 同步段立即返回记录；异步在独立线程完成（awaitility 轮询终态） | REQ-B01、REQ-C02 | 全边界 mock |

## 3. 追溯（抽样完整链）

| 测试 | 契约接口 | Design 模块/缝 | Spec REQ |
|---|---|---|---|
| T17 | benchmark-evaluation.EvaluationExecutor | EvaluationExecutor（状态机） | REQ-C02、REQ-D02 |
| T23 | benchmark-evaluation.EvaluationExecutor | EvaluationExecutor（指标口径） | REQ-D01 |
| T29 | benchmark-evaluation.EvaluationExecutor | EvalRetrievalPipeline（缝 2 降级） | REQ-D03 |
| T27 | benchmark-evaluation.EvaluationExecutor | 评判策略缝（缝 3） | REQ-D05 |
| T7 | benchmark-evaluation.BenchmarkGenerator | BenchmarkGenerator（两段式协议） | REQ-B01 |
| T4 | benchmark-evaluation.BenchmarkGenerator | BenchmarkResponseParser | REQ-B05 |
| T36 | BenchmarkFacade（端点排除层） | BenchmarkFacade | REQ-A03 |
| T34 | EvaluationExecutor（run 协议） | EvaluationFacade | REQ-C07 |
| S0 | EvaluationExecutor | EvaluationExecutor 全链 | 验收演示（非 REQ 追溯） |

## 4. 待实施说明

- 红绿循环未执行；执行时从 T1（解析器回归组已绿）起步，先补 T7/T8/T14~T19（两段式与状态机是行为主干），再铺指标族 T23~T26 与检索降级族 T29~T31。
- EvaluationExecutionHelper 的 938 行单类是主要被测单元：并行路径测试用「同步直调 execute + 受控边界 mock」规避 @Async 线程不可控；仅 T40 单独验证分派语义。
- @Async 需真实代理验证（T40），其余测试建议绕过代理直调方法体。
- HITL 切片 S0 依赖真实模型与真实知识库，安排在 AFK 用例全绿后作为验收演示，不进 CI。
- 时间相关断言（duration、300s 超时）不进断言精度要求（仅 >0 / 可配置注入短超时）。
