# 检索与查询增强（retrieval）— 测试设计

> on-board 模式产出：本文件是测试**设计**而非测试代码；既有测试（RetrievalServiceImplTest 15 用例、QueryEnhanceServiceImplTest 2 用例）已映射进各切片并标注【已有】。红绿循环未执行；执行时按本文件从 Tracer Bullet（T1）起步。

## 1. 切片规划

| 切片 | 契约接口 | AFK/HITL | 说明 |
|---|---|---|---|
| S0 检索质量人评 | RetrievalSearchEngine | **HITL** | 混合检索 topK 结果的相关性、auto 组装上下文完整性、图谱通道贡献度——需人工判定「结果好不好」，自动化只产指标（hit 数/延迟/无结果率）不产结论 |
| S1 降级路径族 | RetrievalSearchEngine | AFK | **重点用例族**：每通道失败注入 → 主结果仍在 + 降级记账正确（Milvus 宕机→LIKE、FULLTEXT 异常→LIKE、Neo4j 不可达→degraded、Rerank 离线→topK 截断、MMR 失败→截断） |
| S2 配置合并族 | RetrievalSearchEngine | AFK | 三级优先级、0 值语义、默认值面、KB 配置保持 |
| S3 召回通道族 | RetrievalSearchEngine | AFK | 向量/全文/hybrid/多路融合三策略/QA 分词匹配/条款线索/图谱通道 |
| S4 上下文组装族 | RetrievalSearchEngine | AFK | 五策略 + auto 降级链（L1/L2/L3）+ token 预算 |
| S5 查询增强族 | QueryEnhancePipeline | AFK | 同义扩展启用词条口径、NER 降级分词、Multi-Query 清洗与降级、图谱扩展短路、stub 行为冻结 |
| S6 日志与分析族 | RetrievalLogModule | AFK | 组织隔离三分支、空页防泄露、聚合统计口径、双写失败不阻断 |
| S7 更新提醒族 | UpdateRemindModule | AFK | 配置缺省补全、触发计数与时间戳刷新、无配置默认态 |

边界 mock 白名单（真缝/系统边界）：MilvusService、EmbeddingService、RerankService、LlmService、GraphStore、StringRedisTemplate（kb:acl）、各 Mapper（MySQL）、TermService、ConfigManageService、ModelRecordMapper、KbLogMapper、KbUpdateLogMapper、SecurityContext。**禁 mock 内部模块**（RetrievalSearchEngine 与 QueryEnhancePipeline 之间、LogModule 与搜索引擎之间用真实实现串联）。

## 2. 测试用例（逐接口）

### RetrievalSearchEngine（contract: retrieval.RetrievalSearchEngine）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| T1【已有】vector 阈值传 0 时低分结果保留 | threshold=0.0，Milvus 返回 score=0.1 候选 | search | 返回 1 条内容正确（0 值不被默认 0.2 过滤） | REQ-A05 | Milvus/Embedding/Mapper |
| T2【已有】vector 阈值 0.3 过滤低分 | threshold=0.3，score=0.1 | search | 结果为空 | REQ-B02 | 同上 |
| T3【已有】fulltext 候选数取 bm25RecallCount | topK=5，bm25RecallCount=50 | search | 捕获 SQL limit=50 | REQ-B05 | KbChunkMapper |
| T4【已有】关键词命中 QA 置顶 | fulltext 1 条 + QA 命中 1 条 | search | 2 条且 qa 来源在首位 | REQ-B12 | Mapper |
| T5【已有】QA 分词模糊匹配 | query="DeepSeek V4参数"，question="DeepSeek V4 Pro 参数" | search | QA 命中且内容含全称 | REQ-B12 | Mapper |
| T6【已有】关键词匹配默认开启 | 不传 enableKeywordMatch | search | QA 命中置顶 | REQ-A06 | Mapper |
| T7【已有】图谱通道默认参与融合 | 不传 enableGraphExpand，expandGraph 返回实体 | search | 结果含 graph 来源项且主结果保留 | REQ-B10、A06 | GraphStore/QueryEnhance/Mapper |
| T8【已有】图谱通道显式关闭 | enableGraphExpand=false | search | 无 graph 来源结果 | REQ-B10 | Mapper |
| T9【已有】图谱向量实体匹配兜底 | 文本实体空 + searchEntitiesByVector 命中 | search | graph 来源结果融合且原始 similarity 保留（>0.9 而非 RRF 分） | REQ-B10、B08 | GraphStore/Embedding/Mapper |
| T10【已有】短实体名不触发图谱 LIKE | expandGraph 仅返回 2 字实体 | search | 无 graph 来源结果（噪音闸门） | REQ-B10 | Mapper |
| T11【已有】window 组装含图谱命中邻居 | 命中 index1 + 图谱命中 index2，windowSize=1 | search | index0..3 全部进入 context | REQ-B13、C08 | Mapper |
| T12【已有】KB 保存配置不被空请求覆盖 | KB retrievalConfig={fulltext,topK:3}，请求 config=null | search | limit=3 且 Milvus 未被调用 | REQ-A03 | Mapper |
| T13【已有】hybrid 加权融合双路保留 | 向量路 1 条 + 全文路 1 条 | search | 2 条内容都在结果中 | REQ-B06 | Milvus/Embedding/Mapper |
| T14【已有】auto L1 父块放大 | 命中子片 parentId 指向存在父块 | search | content=父块全文、chunkType=parent | REQ-C06 | Mapper |
| T15【已有】auto L2 无父块走窗口 | 命中块无 parentId，windowSize=1 | search | 前后 1 跳邻居进入、2 跳外不进入 | REQ-C06 | Mapper |
| T16【已有】auto L3 无 fileId 原样返回 | 命中块 fileId=null | search | 返回自身内容 | REQ-C06 | Mapper |
| T17【已有】条款召回锚点+邻居 | query 含「第三条」，锚点+前后邻居存在 | search | 第二/三/四条内容均出现 | REQ-B13 | Mapper |
| T18【已有】条款召回显式关闭 | enableClauseRecall=false | search | 结果仅主召回 1 条 | REQ-B13 | Mapper |
| T19 Milvus 异常降级 LIKE【S1·Tracer Bullet】 | milvusService.search 抛异常，LIKE 可命中 | search | 返回 LIKE 结果且 source=mysql，不抛错 | REQ-B04 | Milvus/Mapper |
| T20 FULLTEXT 异常降级 LIKE【S1】 | fulltextSearch 抛异常 | search | 返回 LIKE 结果 | REQ-B05 | Mapper |
| T21 图谱通道整路失败 degraded 记账【S1】 | expandGraph 抛异常（模拟 Neo4j 不可达） | search | 主结果正常返回；logService.log 收到 hasResult/hitCount 正确的条目；kbLogMapper 收到 status=degraded、extra.graphChannel=degraded | REQ-B11、E02 | GraphStore/QueryEnhance/KbLogMapper |
| T22 图谱向量实体匹配失败降级文本【S1】 | searchEntitiesByVector 抛异常，文本实体可命中 | search | 仍有 graph 来源结果 | REQ-B11 | GraphStore/Embedding/Mapper |
| T23 Rerank 模型离线回退 topK【S1】 | enableRerank+rerankModel，model 表查无 online 记录 | search | 返回截断 topK 的原排序结果，不抛错 | REQ-C01 | ModelRecordMapper/Rerank |
| T24 Rerank 成功重排回填分数【S1】 | rerank 返回 index+relevance_score | search（组装策略 concat） | 结果按 rerank 序、similarity=relevance_score | REQ-C01、C02 | Rerank/ModelRecordMapper |
| T25 MMR 失败回退 topK【S1】 | embedding 数量与候选不符 | search（enableMMR=true） | 返回 topK 截断结果 | REQ-C04 | Embedding |
| T26 Multi-Query 失败退化单查询【S1】 | expandQueries 抛异常 | search（enableMultiQuery=true） | 结果等同单查询检索、非空 | REQ-B09 | QueryEnhance |
| T27 Multi-Query 多变体 RRF 融合【S3】 | expandQueries 返回 3 变体，各变体命中不同 chunk | search | 跨变体命中全部出现且去重 | REQ-B09 | QueryEnhance/Mapper |
| T28 加权融合权重生效【S3】 | vectorWeight=0.9/bm25Weight=0.1，双路各 1 条同 chunk 不同序 | search | 排序按加权分、原始 similarity 未被覆盖 | REQ-B06、B08 | Milvus/Embedding/Mapper |
| T29 交叉融合 interleave【S3】 | fusionStrategy=interleave，双路各 2 条不同 chunk | search | 结果按路轮流交替、总条数=去重后全量 | REQ-B07 | Milvus/Embedding/Mapper |
| T30 同内容去重留最高分【S3】 | 两通道返回同 content 不同 similarity | search | 仅留 1 条且 similarity 为高者 | REQ-C05 | Mapper |
| T31 无权限检索被拒【S2】 | Redis kb:acl 键不存在、普通用户 | search | 抛 forbidden 业务错误、通道零调用 | REQ-A01 | Redis |
| T32 api-token 与超管放行【S2】 | userId=api-token:xxx / permissions=[*] | search | 不查 ACL 直接检索 | REQ-A01 | Redis |
| T33 空 query 快速返回【S2】 | query="  " | search | 空列表，零通道调用 | REQ-A02 | 无 |
| T34 KB 配置 JSON 损坏降级【S2】 | retrievalConfig="not-json" | search | 按系统默认继续检索不抛错 | REQ-A04 | Mapper |
| T35 sys_config 默认值覆盖硬编码【S2】 | general_search_top_k=7 | search | 生效 topK=7 | REQ-A03、A06 | ConfigManageService |
| T36 window token 预算裁剪【S4】 | maxContextTokens 小、邻居大块 | search | 命中块保留、超预算邻居丢弃、预算全局累积 | REQ-C08 | Mapper |
| T37 组装后重排生效【S4】 | strategy=auto + enableRerank | search | rerank 输入为父块组装后的 content（捕获 rerank 参数验证） | REQ-C02 | Rerank |
| T38 parent_document 整文档聚合【S4】 | strategy=parent_document，同文件多命中 | search | 按文件聚合、content=全片拼接、similarity 取最大 | REQ-C06 | Mapper |
| T39 条款邻居衰减下限【S4】 | clauseWindowSize=3 的远邻 | search | 相似度衰减但不低于 0.3 | REQ-B13 | Mapper |
| T40 图谱通道结果 QA 排他不重复【S3】 | 图谱命中与 QA 命中同 fileId+chunkIndex | search | 无重复条目 | REQ-B08、B12 | Mapper |

### QueryEnhancePipeline（contract: retrieval.QueryEnhancePipeline）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| T41【已有】无 NER 模型 n-gram 切分达图谱 | nerModel=null，中文长查询 | expandGraph | 「故障/根因/分析」2-gram、短块原样、原始长块保留全部传给 GraphStore | REQ-D06 | GraphStore |
| T42【已有】混合查询英文整词保留 | "DeepSeek 参数说明" | expandGraph | DeepSeek 整词 + 参数说明原样 | REQ-D06 | GraphStore |
| T43 同义扩展仅启用词条【S5】 | status=1 词条「大模型」alias「LLM」+ status=0 词条 | query 含「大模型」 | addedTerms 仅含启用词条的别名；禁用词条无贡献 | REQ-D03 | TermService |
| T44 同义扩展追加不改原文【S5】 | query 含术语 | expandSynonyms | expandedQuery=query+" "+alias、原查询前缀完整保留 | REQ-D03 | TermService |
| T45 规则改写 stub 行为冻结【S5】 | 任意 query | applyQueryRules | rewritten==query 且 appliedRules 空列表（引擎落地时本用例反转） | REQ-D04 | 无 |
| T46 suggest stub 行为冻结【S5】 | 任意 query | suggest | suggestedQuery==query | REQ-D02 | 无 |
| T47 Multi-Query 清洗与截断【S5】 | LLM 返回带编号/引号/超长/重复行 | expandQueries(count=3) | 输出 ≤3、全部去编号去引号、含原查询、无重复 | REQ-D05 | LlmService |
| T48 Multi-Query LLM 失败降级【S5】 | llmService.chat 抛异常 | expandQueries | 返回单元素 [原查询] | REQ-D05 | LlmService |
| T49 图谱扩展空查询短路【S5】 | query="" | expandGraph | 四键结构、entities 空、GraphStore 零调用 | REQ-D06 | GraphStore |
| T50 NER 模型离线降级分词【S5】 | nerModel 配置但 model 表查无 | expandGraph | 降级 n-gram 分词继续图谱展开、不抛错 | REQ-D06、D07 | ModelRecordMapper/GraphStore |
| T51 expandedQuery 拼接去重【S5】 | 图谱返回实体名已在原查询中 | expandGraph | 已含实体名不重复拼接 | REQ-D06 | GraphStore/ModelRecordMapper |
| T52 expandGraph 无权限拒绝【S5】 | ACL 无记录普通用户 | expandGraph | 抛 forbidden、GraphStore 零调用 | REQ-D06（E_NO_KB_ACCESS） | Redis |

### RetrievalLogModule（contract: retrieval.RetrievalLogModule）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| T53 组织隔离分页【S6】 | 用户可访问 {kb1}，日志跨 kb1/kb2 | page(null) | 结果仅 kb1、total 对应缩小 | REQ-E04 | Security/KbAccessChecker/Mapper |
| T54 指定越权库返回空页【S6】 | 可访问 {kb1}，请求 kbId=kb2 | page(kb2) | 空页 total=0（不抛错、不泄露） | REQ-E04 | 同上 |
| T55 api-token 不过滤【S6】 | userId=api-token:x | page(null) | 不加 kb_id IN 条件（捕获 wrapper 或返回全量） | REQ-E04 | Security/Mapper |
| T56 hasResult 过滤与排序【S6】 | hasResult=false | page | 仅无结果日志、createdAt 降序 | REQ-E04 | Mapper |
| T57 分析统计口径【S6】 | 聚合返回 total=10/no_result=2/latency=300/hit=4.5 | analysis | noResultRate=20.0、其余字段透传换型正确 | REQ-E05 | Mapper |
| T58 分析双 Top10 透传【S6】 | mapper 返回热门/无结果各 1 行 | analysis | topQueries/noResultQueries 原样携带 | REQ-E05 | Mapper |
| T59 无权限分析零值结构【S6】 | 可访问集合为空 | analysis | 全零 + 两个空列表（非异常） | REQ-E05 | Security |
| T60 检索双写失败不阻断【S6】 | logService.log 抛异常（串真实引擎验证） | search | 检索结果正常返回 | REQ-E03、E01 | RetrievalLogService/KbLogMapper |

### UpdateRemindModule（contract: retrieval.UpdateRemindModule）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| T61 新增缺省补全【S7】 | save 无 id、enabled/cronExpr 均空 | save | 落库 enabled=1、cronExpr="0 9 * * *" | REQ-F01 | Mapper |
| T62 带 id 更新【S7】 | save 带 id | save | updateById 被调、不触发缺省补全路径 | REQ-F01 | Mapper |
| T63 触发增量计数【S7】 | 配置 lastRemindAt=T0，之后 3 条更新 | remind | updateCount=3（捕获 wrapper 验证 gt 条件） | REQ-F02 | Mapper |
| T64 无 lastRemindAt 全量计数【S7】 | 配置存在但 lastRemindAt=null | remind | updateCount=全部更新数 | REQ-F02、F03 | Mapper |
| T65 触发后刷新时间戳【S7】 | 配置存在 | remind | updateById 携 lastRemindAt≈now | REQ-F02 | Mapper |
| T66 无配置默认态【S7】 | 该库无配置 | remind | enabled=false、cronExpr 默认、updateCount 全量、message 固定文案 | REQ-F03 | Mapper |
| T67 list 按 kb 过滤降序【S7】 | kbId 传入 | list | wrapper 含 eq 条件、createdAt 降序 | REQ-F01 | Mapper |

## 3. 追溯（抽样完整链）

| 测试 | 契约接口 | Design 模块（缝） | Spec REQ |
|---|---|---|---|
| T21 | retrieval.RetrievalSearchEngine.search | RetrievalSearchEngine（缝 4 graph 通道 + 缝 2 GraphStore） | REQ-B11、E02 |
| T28 | retrieval.RetrievalSearchEngine.search | RetrievalSearchEngine（缝 4 融合器） | REQ-B06、B08 |
| T36 | retrieval.RetrievalSearchEngine.search | RetrievalSearchEngine（上下文组装） | REQ-C08 |
| T45 | retrieval.QueryEnhancePipeline.applyQueryRules | QueryEnhancePipeline（缝 3 规则消费） | REQ-D04 |
| T53 | retrieval.RetrievalLogModule.page | RetrievalLogModule（组织隔离） | REQ-E04 |
| T63 | retrieval.UpdateRemindModule.remind | UpdateRemindModule | REQ-F02 |

## 4. 待实施说明
- 孤儿 REQ 豁免：REQ-C03（LLM 重排 enableLLMRerank 为占位实现——直接 topK 截断无 LLM 打分，stub 债务登记，行为冻结现状断言，无下游承接为有意；见 implement 文档）
- 孤儿 REQ 豁免：REQ-E06（检索日志外部写入/更新端点无权限校验——债务登记（修复方向见 design §5），现状无行为可测承接为有意；见 implement 文档）

- 红绿循环未执行（本任务禁改源码）。实施顺序：T19（Tracer Bullet：一个通道失败 + 主结果存活）→ S1 其余 → S2 → S3/S4 → S5 → S6/S7。
- 既有 17 用例（T1~T18、T41~T42）迁移时保持类名与包路径镜像（conventions.md §1）；T45/T46 是**行为冻结测试**——applyQueryRules/suggest 引擎落地时先反转断言再实现（红→绿语义正确使用）。
- S0（HITL）实施方式：跑 AFK 用例族产出的 hit 数/延迟/无结果率 + 真实库抽样查询，由人评定相关性；不自动化判定。
- mock 纪律复述：仅缝上 mock（§1 白名单）；T21/T60 需要串真实内部模块验证「失败不阻断」，禁用纯桩替身堆叠。
