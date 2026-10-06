# 检索与查询增强（retrieval）— 架构设计

## 1. Module Map

| 模块 | Responsibility | Files | Depth Rationale |
|---|---|---|---|
| **RetrievalSearchEngine**（混合检索编排器，深模块） | 把「一条查询」折叠为「一份去重、排序、上下文完整的检索结果」：配置合并、六通道召回调度、三策略融合、后处理、上下文组装、降级与日志记账全部藏在一个 `search()` 之后 | service/impl/RetrievalServiceImpl.java（1785 行）、service/RetrievalService.java、model/RetrievalRequest.java、model/SearchResultItem.java | 调用方（app-center 对话、benchmark 评测、前端检索页）只声明 knowledgeId+query+config，不知道存在几个通道、谁降级了、上下文怎么放大——变化频率最高的部分（通道增删、融合策略、组装策略）全部在模块内部演化 |
| **QueryEnhancePipeline**（查询增强管线，深模块） | 把「原始查询」折叠为「增强后的查询/实体/变体」：纠错、同义扩展、规则改写、图谱扩展、Multi-Query 变体改写五个策略收敛为一个服务面；LLM/NER/降级分词的选择对调用方不可见 | service/impl/QueryEnhanceServiceImpl.java、service/QueryEnhanceService.java、model/GraphExpansionResult.java | 调用方只拿 expandedQuery/entities/variants，不需要知道本次走了 LLM 还是 n-gram 降级；三个策略是占位实现（stub）但接口面已稳定，执行引擎落地时不改调用方 |
| **RetrievalLogModule**（检索日志与分析，中深） | 把「每次检索」折叠为「可查询的效果指标与组织隔离的读取面」：双写记账、组织隔离分页、聚合统计 | service/impl/RetrievalLogServiceImpl.java、service/RetrievalLogService.java、mapper/KbRetrievalLogMapper.java、entity/KbRetrievalLog.java | 组织隔离规则（api-token 全量、指定库不可达返回空页、空集合短路）是一致性要求不是透传；统计 SQL 聚合藏在 mapper 注解里 |
| **UpdateRemindModule**（更新提醒，浅 **(S)**） | 提醒配置 CRUD + 触发时统计更新条数 | service/impl/UpdateRemindServiceImpl.java、service/UpdateRemindService.java、mapper/KbUpdateRemindMapper.java、entity/KbUpdateRemind.java | **(S)** list/get/save/delete 是 mapper 直通包装，仅 remind() 含「计数 + 时间戳刷新」一点逻辑；建议长期可并入知识库管理或运营域，独立成模块的理由随定时调度器落地与否重估 |
| RetrievalController **(S)** | 15 个 HTTP 端点的参数搬运（HTTP 层，不写契约，见 contract 端点排除铁律） | controller/RetrievalController.java | 纯透传，无业务分支 |

债务登记（既有代码，非本设计引入）：① **查询规则改写执行引擎缺失**——QueryEnhancePipeline.applyQueryRules 占位返回原查询，platform 侧 query_rule 表（pattern/action/enabled/priority 字段齐全）零消费（QueryEnhanceServiceImpl.java:91-97 ‹CERTAIN›），字段契约断裂；② **LLM 重排占位**——RetrievalSearchEngine 的 llmRerankResults 直接 topK 截断（RetrievalServiceImpl.java:1193-1199 ‹CERTAIN›）；③ **suggest 纠错占位**（QueryEnhanceServiceImpl.java:69-75 ‹CERTAIN›）；④ **更新提醒无调度器**——cron_expr 仅存储回显（grep 全后端无消费 ‹CERTAIN›）。四者接口面已稳定，属「债在实现、不在契约」。

## 2. Interface Contracts

（HTTP 端点不在此列——端点排除铁律；完整 Input/Output/Error modes 见 contract/interfaces.md `## retrieval` 节。）

### RetrievalSearchEngine

Public interface（RetrievalService.java:15 ‹CERTAIN›）：
- `search(RetrievalRequest) → List<SearchResultItem>` — 执行一次完整检索编排（权限校验 → 配置合并 → 预处理 → 多通道召回融合 → 后处理 → 组装 → 记账）
- `getChunkCount(kbId) → long` — 知识库可检索分片计数（排除软删文件与父分片）

Hidden implementation：六通道召回（vector/fulltext/LIKE 降级/graph/qa/clause）、RRF(k=60)/加权/交叉三融合、Rerank/MMR/去重、五策略上下文组装与 auto 降级链、嵌入模型解析、kb:acl Redis 校验、双写日志。〔RetrievalServiceImpl.java 各私有段 ‹CERTAIN›〕

Depth rationale：search 返回值稳定（List&lt;SearchResultItem&gt;），内部从单路向量演化到六通道三融合无一次调用方变更。

### QueryEnhancePipeline

Public interface（QueryEnhanceService.java:21-38 ‹CERTAIN›）：
- `suggest(query) → {suggestedQuery, reason}` — 查询建议/纠错（占位）
- `expandSynonyms(query) → {expandedQuery, matchedTerms, addedTerms}` — 同义扩展（仅启用词条）
- `applyQueryRules(query) → {rewritten, appliedRules}` — 规则改写（占位）
- `expandGraph(kbId, query, depth, maxEntities[, nerModel]) → {originalQuery, expandedQuery, entities, relations}` — NER + 图谱展开
- `expandQueries(query, count, model, kbId) → List<String>` — Multi-Query 变体改写

Hidden implementation：NER 的 LLM 提示词与 JSON 解析、无模型时的 n-gram 降级分词、模型表解析、expandedQuery 拼接规则、KB 权限校验。

Depth rationale：五方法各自封装一种增强策略；降级链（LLM 失败 → 降级分词/原查询）不外泄。

### RetrievalLogModule

Public interface（RetrievalLogService.java:12-17 ‹CERTAIN›）：
- `log(KbRetrievalLog)` / `update(KbRetrievalLog)` — 记账与补记
- `page(kbId, hasResult, page, pageSize) → PageResult<KbRetrievalLog>` — 组织隔离分页
- `analysis(kbId) → Map` — 聚合统计 + 双 Top10

Hidden implementation：accessibleKbIds 解析（api-token=null 不过滤）、空页防泄露语义、三条聚合 SQL。

### UpdateRemindModule

Public interface（UpdateRemindService.java:12-18 ‹CERTAIN›）：
- `list(kbId)` / `get(kbId)` / `save(KbUpdateRemind)` / `delete(id)` — 配置 CRUD（save 兼新增/更新）
- `remind(kbId) → {kbId, enabled, lastRemindAt, cronExpr, updateCount, message}` — 触发统计并刷新 lastRemindAt

## 3. Seam Diagram

| 缝 | What varies | Adapters | Stable side | Placement rationale |
|---|---|---|---|---|
| 缝 1：AI 能力边界（Embedding/Rerank/LLM） | 嵌入/重排/生成模型的选型与部署地址（model 表动态路由） | fastrag-ai 的 EmbeddingService / RerankService / LlmService（唯一网关，但 API 形态已抽象出调用面）〔RetrievalServiceImpl.java:101-102、QueryEnhanceServiceImpl.java:50 ‹CERTAIN›〕 | RetrievalSearchEngine / QueryEnhancePipeline 只持「model code + apiUrl + apiKey → 调用」协议 | 外部系统边界，必须 mock 的测试缝；模型路由查 model 表放 caller 侧（宪法禁 AI 层查业务库） |
| 缝 2：GraphStore 读口 | 图谱存储引擎（ADR-0002 收敛 Neo4j 唯一实现） | Neo4jGraphStore（1 个 adapter——**假设缝**：接口由 ADR-0002 决策保留，MySQL 实现已删；不因单实现拆更多抽象）〔GraphStore.java:112,260 ‹CERTAIN›〕 | graphChannelRecall / expandGraph 依赖 expandGraph / searchEntitiesByVector 两读口 | ADR-0002 明示接口存续理由（防回摆）；运行期不可达 → 通道降级记账（REQ-B11），与启动 fail-fast 分层不冲突 |
| 缝 3：术语/规则数据消费 | 术语词条与查询规则的治理数据（platform 侧维护） | TermService.expandSynonyms（真消费）；query_rule 表（**零消费，stub 债**）〔QueryEnhanceServiceImpl.java:79,92-97 ‹CERTAIN›〕 | QueryEnhancePipeline 定义「只消费启用词条」口径（REQ-D03） | 数据归 platform、行为归 retrieval；规则执行引擎落地时在本缝内替换，调用方不变 |
| 缝 4：召回通道 | 参与召回的通道集合与各自实现（向量/全文/QA/图谱/条款/LIKE 降级） | 六个私有通道方法 + safeSearch 包装〔RetrievalServiceImpl.java:1689-1696 ‹CERTAIN›〕——代码上未抽接口，**结构上是约定缝**（每通道同签名 List&lt;SearchResultItem&gt;(kbId,query,cfg,count)、失败空/null 语义）‹CERTAIN（2026-09-24 深证：vectorSearch :561 / fulltextSearch :623 / graphChannelRecall :719 / qaChannelRecall :798 / clauseRecall :871 / LIKE 降级 :1712 六通道同构签名 + safeSearch(Supplier) 包装 :1689-1696，结构归纳为代码直读事实）› | 融合器（rrfFusionMulti/weightedFusion/interleaveFusion）只认「通道列表」 | 通道增删只改 channels 组装处；单通道失败不拖垮整体是该缝的核心不变式 |
| 缝 5：配置来源 | 生效检索参数的三个来源（sys_config 系统默认 / kb.retrievalConfig / 请求参数） | ConfigManageService.getConfig、KnowledgeBase.retrievalConfig JSON、RetrievalRequest.config〔RetrievalServiceImpl.java:284-370 ‹CERTAIN›〕 | mergeConfig 的「非 null 才覆盖」协议 | 新增可调参数只需扩 RetrievalConfig 字段 + mergeConfig 一行，三级结构不变 |

## 4. Deepening Opportunities

- **RetrievalSearchEngine**：可继续吞掉「通道健康度」的可观测口径（现状 degraded 记账已入日志，但通道命中数/降级次数无独立指标面）；融合策略若继续增加（如线性加权 RRF 混合）应把三融合器提取为策略对象，避免 switch 膨胀（现状 rrfFusionMulti/weightedFusion/interleaveFusion 三私有方法并存，RetrievalServiceImpl.java:1016-1101 ‹CERTAIN›）。
- **QueryEnhancePipeline**：三个占位策略（suggest/applyQueryRules/LLM 重排）是预留的深化位——执行引擎（纠错词典/规则匹配器/LLM 打分）落地时全部在本模块内实现，接口面零变更；expandGraph 的「拼 expandedQuery」产出当前仅剩直达端点消费（检索主流程已改走独立图谱通道不拼词，RetrievalServiceImpl.java:462-463 ‹CERTAIN›），下一步可把拼接职责收回通道内部，缩小公共面。
- **RetrievalLogModule**：可吸收 kb_log 双写的组装逻辑（现状 recordLog 内联在 RetrievalSearchEngine，RetrievalServiceImpl.java:1738-1783 ‹CERTAIN›），让搜索引擎只调一个记账方法。
- **UpdateRemindModule**：None（保持浅模块；若定时调度器落地，调度器也应是独立 job 而非塞入本模块）。

## 5. Parent Trace

| 设计单元 | 对应 Spec |
|---|---|
| RetrievalSearchEngine.search 编排主流程 | REQ-A01~A06、REQ-B01、REQ-B09 |
| 缝 4 召回通道族（vector/fulltext/LIKE 降级） | REQ-B02~B05 |
| 缝 4 召回通道族（multi-channel + 融合器 ×3） | REQ-B06~B08 |
| 缝 4 召回通道族（Multi-Query 编排） | REQ-B09、REQ-D05、REQ-D07 |
| 缝 4 召回通道族（graph 通道 + 降级记账） | REQ-B10、REQ-B11 |
| 缝 4 召回通道族（QA 通道） | REQ-B12 |
| 缝 4 召回通道族（clause 通道） | REQ-B13 |
| RetrievalSearchEngine 后处理（Rerank/LLM 重排/MMR/去重） | REQ-C01~C05 |
| RetrievalSearchEngine 上下文组装（五策略 + auto 降级链） | REQ-C06~C08 |
| RetrievalSearchEngine 结果收口（编号/fileName） | REQ-A07、REQ-C09 |
| 缝 1 AI 能力边界（模型解析） | REQ-B02、REQ-B03、REQ-C01、REQ-D05~D07 |
| 缝 2 GraphStore 读口 | REQ-B10~B11、REQ-D06 |
| 缝 3 术语/规则数据消费 | REQ-D03、REQ-D04 |
| 缝 5 配置来源 | REQ-A03~A06 |
| QueryEnhancePipeline（预处理链/五策略） | REQ-D01~D07 |
| RetrievalLogModule | REQ-E01~E06 |
| UpdateRemindModule | REQ-F01~F04 |
| RetrievalSearchEngine 上下文组装（五策略 + auto 降级链） | REQ-C07（2026-09-24 review 补锚：父分片仅作上下文放大——所有召回通道排除 chunkType='parent'，父块替换条目保留命中子分片 fileId/chunkIndex 并携带 200 字预览） |
| RetrievalLogModule | REQ-E02（2026-09-24 review 补锚：同步写 kb_log（category=retrieval, action=search）——status=success/degraded，extra 携带 mode/topK/hits/duration/graphExpanded/graphHits/graphChannel 七字段） |

## 6. ADR

**ADR-R1：图谱召回作为独立通道参与融合（方案 A），不把实体名拼入查询再向量化。**
- **背景**：图谱扩展最初以「实体名/关系标签拼接 expandedQuery → 再向量化」方式增强检索（该路径仍存于 expandGraph 直达接口，QueryEnhanceServiceImpl.java:349-361 ‹CERTAIN›）。
- **决策**：检索主流程中图谱固定为独立召回通道：NER+向量双路匹配实体 → 实体名 LIKE 召回分片 → 与主检索 RRF 融合；不再拼接查询（RetrievalServiceImpl.java:169-191,462-463 ‹CERTAIN›）。
- **代价与收益**：拼词会稀释查询 embedding（短实体名混入改变向量语义），独立通道保住主查询向量纯度、且通道健康度可记账（degraded）；代价是多一路召回开销与实体名质量闸门（≥3 字过滤）。
- **状态**：已落地（HEAD d8f30cd）；对标 LightRAG kg_query‹CERTAIN（2026-09-24 深证：实现注释直接载明——RetrievalServiceImpl.java:169-172「对标 LightRAG kg_query」、:355「对标 LightRAG 一等检索通道」；基准对比数据确无为事实陈述，非推断）›。与 ADR-0002（Neo4j 唯一实现、启动 fail-fast）一致：fail-fast 管「配置错误启动即暴露」，通道降级管「运行期不可达不阻断检索」——两层容错互补不冲突。

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
