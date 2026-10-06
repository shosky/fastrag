# 知识图谱（knowledge-graph）— 需求规格

## 1. 特性与范围

知识图谱特性把知识库的分片内容变成一张可查询、可治理的实体关系图：从分片重组的「抽取单元」中用 LLM 抽取三元组（异步 MQ 构建），以确定性 ID 与归一化名把跨分片、跨单元的同一实体合并进图存储，并提供可视化查询、子图搜索、PPR 分片排序（检索增强）、实体合并候选与合并执行、实体向量检索等消费面；同时提供全量重建、增量构建与零 LLM 成本的 replay 重建三种构建口径。图数据存储收敛于 Neo4j Graph Store（唯一实现，启动 fail-fast，ADR-0002 已落地〔CONTEXT.md 知识图谱/Graph Store 词条；Neo4jConfig.java:41-58 ‹CERTAIN›〕）。**显式排除**：图谱数据的检索侧消费（graph expand / graphHits 通道归 retrieval 链）、基准与评测（Benchmark/Evaluation 两控制器归 benchmark-evaluation 特性）、文件/分片删除链里对图谱的清理调用（删除裁决归上游链，本特性只提供被调用的清理能力）。

## 2. Out of Scope

- **检索通道集成**：`RetrievalConfig.enableGraphExpand`、`graphHits`、`graphChannelDegraded` 等「图谱作为检索一路」的消费口径归 retrieval 链（interfaces.md `## retrieval` 节已定义）；本特性只保证 `expandGraph`/`rankChunksByPpr` 两个被消费原语的正确性。
- **基准与评测**：`BenchmarkController`、`EvaluationController`、`BenchmarkService`、`EvaluationService` 及 kb_benchmark*/kb_evaluation* 表（GraphController.java 同模块但不同特性，feature-map #11）。
- **构建触发裁决**：文件摄取完成后是否触发图谱构建（`enable_graph_build` 开关解析）归 file-ingestion 链（interfaces.md `## file-ingestion` 节 Invariants 末条）；分片编辑/删除触发的图谱清理与增量重建消息归 chunk-management 链 ChunkLifecycleService。本特性只定义消费消息后的构建行为。
- **OCR 噪声过滤与结构级解析**：入图文本的质量上游防线（OcrNoiseFilter 等）归 file-ingestion 链；本特性只负责「已入库分片」的入图质量闸门（EntityNameGuard）。
- **MySQL 图存储**：任何 MySQL 图表 DDL 复活（ADR-0002 禁止；schema.sql:235 注释明示四张 MySQL 图表已移除）。
- **自动合并实体**：合并候选只建议、不自动执行（GraphStore.java:283-285 javadoc「只做候选建议，不做自动合并」‹CERTAIN›）；无 LLM 自动判同义的需求。
- **图谱版本化/回滚产品化**：合并审计表设计了回滚快照字段，但「一键回滚」操作不存在于代码（债务，见 REQ-D05 登记与 data.md kb_graph_merge_audit 节）。

## 3. 领域术语

引用 `fastrag-backend/CONTEXT.md`：**知识图谱**、**抽取单元**（ExtractionUnit，v1.2 架构，~500 字分片重组为 ~2000 字符单元送 LLM）、**实体合并**（EntitySimilarity 余弦候选 + EntityNameGuard 清洗闸门）、**Graph Store**（Neo4j 唯一实现）、**知识分片（Chunk）**、**重新分片（Re-chunk，图谱数据随分片重建）**。本链补充的链内术语：**抽取单元缓存**（kb_graph_unit，content_hash 命中零 LLM 跳过）、**replay 重建**（重放持久化抽取结果的零 LLM 重建模式）、**mention 三表防线**（Entity-Chunk 提及 / TripleMention 三元组溯源，支撑删除回收与 PPR）、**缝合（stitch，跨分片补边）**。

## 4. 功能需求

### 4.1 三元组单元化抽取

把一个文件的全部分片按 chunkIndex 顺序重组为语义完整的中大文本块（抽取单元），单次 LLM 调用抽取一个单元的实体与关系；跨分片关系在单元内直接抽出，不再依赖任何补丁层。这是图谱质量的第一道生产环节，全部质量防线（垃圾闸门、类型归一、退化重试、gleaning 补捞）都发生在「LLM 输出 → 写入图存储」之间。

- **REQ-A01**：抽取前必须把文件内分片按 chunkIndex 升序重组为不超过字符预算的抽取单元，单元携带内容哈希与组成分片清单；单个超长分片独占单元且不截断其内容。〔证据：ExtractionUnitAssembler.java:34-56 ‹CERTAIN›〕
- **REQ-A02**：内容过短（<50 字符）的单元不调用 LLM，直接记为已完成的空抽取。〔证据：GraphBuildConsumer.java:120,471-476 ‹CERTAIN›〕
- **REQ-A03**：LLM 抽取必须携带结构化约束的 Prompt：实体/属性/关系 JSON 契约、禁止值型与单据号实体、名称保持原文、类型从白名单集合选择（白名单经排序稳定化、排除值型/时间型类型）；表格主导的内容切换为表格专用规则（数值降级为属性、表头词禁入、套餐→设备方向）。〔证据：GraphBuildConsumer.java:993-1051,133-135,999-1003 ‹CERTAIN›〕
- **REQ-A04**：LLM 输出必须经规范化后才能入图：实体按（归一化名+类型）去重合并（属性并集、描述择长）、关系端点解析为实体、缺失默认值填充（实体类型默认 Entity、关系类型默认 RELATED_TO）。〔证据：ExtractionNormalizer.java:86-160 ‹CERTAIN›〕
- **REQ-A05**：垃圾实体必须在写入图存储前被代码侧闸门拦截：值型内容（纯数值/价格/日期/电话单据号/URL）、键值对残留、中日假名/西里尔乱码、金额后缀伪名、表头泛化词、代码标识符、截断句碎片；归一化类型为「属性」的实体整体拦截。关系两端同样过闸。〔证据：EntityNameGuard.java:48-97 ‹CERTAIN›；关系端点过闸 GraphBuildConsumer.java:579 ‹CERTAIN›〕
- **REQ-A06**：实体类型必须归一到可控集合：白名单精确命中保留 → 编辑距离 ≤1 归并 → 未命中归 UNKNOWN；白名单来源优先级为 KB 级 settings.entitySchema → 全局 yml 配置 → 内置默认集合。〔证据：EntityTypeNormalizer.java:196-211 ‹CERTAIN›；白名单解析 GraphBuildConsumer.java:1160-1197 ‹CERTAIN›〕
- **REQ-A07**：抽取退化必须有兜底：有关系但全部缺端点（输出截断症状）时重试一次并按实体+关系总数择优；抽到实体但零关系时触发 gleaning 补捞一轮（只补漏不重复，输入超 16000 字符护栏跳过）。〔证据：GraphBuildConsumer.java:837-845,848-853,736-750,827-831,859-873 ‹CERTAIN›〕

### 4.2 图谱构建编排与 replay

构建是异步的：触发方（手动端点/摄取完成/分片编辑）发 MQ 消息，消费者即时 ack 后在应用内执行器上运行单元级并行构建；三种模式（full 全量强制重抽 / incremental 单元缓存 / replay 重放持久化结果）共享同一编排。历史生产事故（同 KB 并发构建互相踩踏）催生了 KB 级互斥与「消费者只调度」的形态，这些是构建正确性的前提。

- **REQ-B01**：构建消息必须被即时 ack，实际构建移入应用内执行器；同一知识库同时只允许一个构建在执行（重复消息直接跳过不排队）。〔证据：GraphBuildConsumer.java:183-216 ‹CERTAIN›；事故背景注释 :175-181 ‹CERTAIN›〕
- **REQ-B02**：LLM 配置必须按「文件解析策略 llm_model → KB 级 graphLlmModel → 无」顺序解析（模型须在模型表 status=online）；全库重建（无 fileIds）回退到该库任意未删文件解析；LLM 不可用时构建必须显式置 failed 并记录错误，不得静默跳过全部单元。〔证据：GraphBuildConsumer.java:1059-1120,264-281 ‹CERTAIN›〕
- **REQ-B03**：单元级缓存必须生效：单元行 content_hash 与当前内容哈希一致、状态为完成、抽取结果非空时零 LLM 跳过；full 模式忽略缓存强制重抽。越界的旧单元行（文件分片变少导致单元数缩水）必须清理。〔证据：GraphBuildConsumer.java:492-505,238,447-460 ‹CERTAIN›〕
- **REQ-B04**：replay 模式必须零 LLM 成本：仅重放单元行持久化的抽取结果写入图存储；无持久化结果的单元（历史失败被误标已构建）必须标记失败并重置所属分片为待提取，由后续增量构建补抽。〔证据：GraphBuildConsumer.java:236,253-254,480-491 ‹CERTAIN›；端点语义 GraphController.java:105-115 ‹CERTAIN›〕
- **REQ-B05**：抽取失败的单元必须构建内自动重试一轮；仍失败的必须把所属分片标记为「提取失败待增量重试」（graph_indexed=2），下次增量构建自动重试，不得静默丢失。〔证据：GraphBuildConsumer.java:332-348,511-521,122-125 ‹CERTAIN›〕
- **REQ-B06**：图谱写入以确定性身份幂等合并：实体 ID=SHA-256(kbId:归一化名)（不含类型）、三元组 ID=SHA-256(kbId:源:类型:关系:目标:类型) 截断 32 位；同名实体跨单元/跨批次合并为同一节点，类型按计票多数胜出（占位类型无投票权），描述择长。〔证据：GraphIdHashing.java:45-56 ‹CERTAIN›；类型计票 Neo4jGraphStore.java:103-119 ‹CERTAIN›〕
- **REQ-B07**：每次写入必须同步建立证据链：实体→分片的 MENTIONS（单元内名字包含命中的分片都挂，全不命中挂首个分片兜底）、三元组→分片的 TripleMention（两端名字都被分片包含）；关系端点实体在任何单元都未被提取时必须登记为带证据的 UNKNOWN 占位实体，保证边引用不悬空。〔证据：GraphBuildConsumer.java:615-653,1129-1143 ‹CERTAIN›；MENTIONS 按确定性 ID 匹配的修复 Neo4jGraphStore.java:647-655 ‹CERTAIN›〕
- **REQ-B08**：构建收尾必须完成三件事：以实时查询刷新索引统计（实体/关系数）、为缺失 embedding 的存量实体分批回填向量（每批 20）、清理无引用的孤立实体与关系。〔证据：GraphBuildConsumer.java:351-378,1285-1308 ‹CERTAIN›；孤立回收 Neo4jGraphStore.java:958-970 ‹CERTAIN›〕

### 4.3 图谱查询与 PPR 排序

查询面服务两类消费方：前端可视化（全量/子图/统计/标签）与检索增强（PPR 分片排序）。查询必须只读、失败降级为空结果而非报错，保证画布与检索主流程不被图存储故障阻断。

- **REQ-C01**：图谱可视化必须返回节点+边数据，节点按关系度数降序截断（默认 500、参数钳制 [1,5000]）；零边孤立节点默认从画布过滤（非破坏性，库内保留，统计口径不受影响）；边端点匹配优先确定性 ID、缺失回退名称（兼容存量边）。〔证据：GraphServiceImpl.java:70-91,101-132 ‹CERTAIN›；度数截断 Neo4jGraphStore.java:263-270 ‹CERTAIN›〕
- **REQ-C02**：图谱统计必须优先读索引缓存计数，缓存缺失或为零时回退图存储实时查询，且实时查询有数时自动修复缓存记录；必须同时返回实体类型分布。〔证据：GraphServiceImpl.java:147-196 ‹CERTAIN›〕
- **REQ-C03**：关键词子图搜索必须多字段模糊匹配（名称/归一化名/类型/描述），返回命中的节点及其内部边；实体类型标签列表必须去重排序返回。〔证据：Neo4jGraphStore.java:514-580,583-600 ‹CERTAIN›〕
- **REQ-C04**：构建状态查询必须返回统一状态面（status/progress/实体/关系/总片/已建/失败/错误/最后构建时间），无索引记录时返回 idle 零值面而非报错。〔证据：GraphServiceImpl.java:199-229,323-325 ‹CERTAIN›〕
- **REQ-C05**：全量重试（retry）必须按「清空图数据 → 重置全库分片 graph_indexed=0 → 重置索引状态 → full 模式重触发」四步执行；清理步骤失败降级记账不阻断。〔证据：GraphServiceImpl.java:275-320 ‹CERTAIN›〕
- **REQ-C06**：PPR 排序必须基于 Entity-Chunk 二分提及图，以查询实体（归一化名映射）为种子运行个性化 PageRank（阻尼 0.85），返回按分数降序的 Top-K 分片（含 chunkId/score/提及数）；种子未命中图谱或提及数据加载失败时返回空列表。〔证据：GraphQueryService.java:49-53,73-151 ‹CERTAIN›〕

### 4.4 实体合并与图谱治理

同义/异写实体（"小微 ICT"/"小微ICT"漏网的异名同义对）通过向量相似度发现候选、人工确认后合并。合并是破坏性操作（source 节点删除、边与提及迁移），其正确性直接决定图谱可信度。

- **REQ-D01**：合并候选发现必须基于实体名称 embedding 的两两余弦相似度，只做建议不自动合并；参与比对的实体数有上限防御（2000），向量缺失/全零/维度不一致的实体跳过，结果按相似度降序截断。〔证据：EntitySimilarity.java:236-266,269-279 ‹CERTAIN›；GraphStore.java:283-292 ‹CERTAIN›〕
- **REQ-D02**：合并执行必须：校验两端存在（不存在即失败上抛）→ 出边与入边迁移到 target（指向 target 的自环直接丢弃）→ 清理残余自环 → MENTIONS 迁移 → 描述择长与类型票数累加 → 删除 source；合并产生的 target 自环边不得入图。〔证据：Neo4jGraphStore.java:1015-1088 ‹CERTAIN›〕
- **REQ-D03**：合并完成后必须刷新索引缓存的实体/关系计数（刷新失败不影响合并结果）；sourceId/targetId 相同或任一为空的请求必须被拒绝。〔证据：GraphServiceImpl.java:453-472 ‹CERTAIN›〕
- **REQ-D04**：删除文件/分片后图谱不得残留：按文件删除时清理其 Chunk 节点、MENTIONS、TripleMention 并回收孤立关系/实体；孤立回收口径 = 无任何 TripleMention 引用的关系 + 无任何 MENTIONS 的实体，且回收前先回填存量边的端点 ID。〔证据：Neo4jGraphStore.java:694-750,762-783 ‹CERTAIN›；文件删除后索引缓存同步 GraphServiceImpl.java:381-437 ‹CERTAIN›〕
- **REQ-D05**：每次合并必须留下审计记录（source/target 与合并前快照），支撑人工回滚与问题追溯。〔证据：设计意图 KbGraphMergeAudit.java:84-114、schema.sql:1461-1472 ‹CERTAIN›；**当前实现缺口：合并执行链路无任何审计写入方**——GraphServiceImpl.mergeEntities(:453-472) 未写审计，全仓无 KbGraphMergeAuditMapper 消费者（grep 实测 ‹CERTAIN›），属契约先行、实现欠账〕

### 4.5 跨分片缝合框架

历史版本（v1.1 及之前）存在文件级缝合 pass 补抽跨分片关系；v1.2 以「更大的抽取单元」天然覆盖其职责后移除了该 pass，但其底层原语（带冲突消解的有向补边、按归一化名查实体、查分片成员、判两实体间已有边）保留为图存储接口能力，且缝合输出表（kb_graph_stitch）DDL 已建。本域需求刻画这套框架能力必须维持的语义，供未来变体（如重建缝合 pass）直接复用。

- **REQ-E01**：缝合写边必须确定性且可重入：同 label 反向边已存在时不新建边、把本次证据分片并入既有边溯源并返回既有边 ID（先建者胜）；无反向边时按与常规建边一致的 MERGE 语义建边并建立 TripleMention 溯源。〔证据：GraphStore.java:326-339 ‹CERTAIN›；Neo4jGraphStore.java:1161-1219 ‹CERTAIN›〕
- **REQ-E02**：缝合决策所需的查询原语必须可用：按归一化名查实体完整上下文、查某分片提及的全部实体、判两实体间是否已有任意方向任意标签的边。〔证据：Neo4jGraphStore.java:1093-1158 ‹CERTAIN›〕
- **REQ-E03**：缝合产出（补出的跨分片关系 / 合并候选）必须可持久化并支持 replay 重放（status 语义：0 待应用 / 1 已应用可重放 / 2 跳过）。〔证据：设计意图 KbGraphStitch.java:43-80、schema.sql:1432-1443 ‹CERTAIN›；**当前实现缺口：v1.2 移除缝合 pass 后无写入方**——全仓无 KbGraphStitchMapper 消费者（grep 实测 ‹CERTAIN›）〕

### 4.6 实体向量检索

实体名称向量是合并候选发现与语义检索的地基：构建时为新实体生成 embedding，收尾回填存量缺口，图存储提供向量索引检索。

- **REQ-F01**：实体 embedding 必须在构建写入时生成（KB 级 embedding 模型解析，模型表 status=online），失败仅告警不阻断构建。〔证据：GraphBuildConsumer.java:1265-1280,1313-1335,607 ‹CERTAIN›〕
- **REQ-F02**：embedding 回填必须只补缺失向量（每批 20、单批失败不中断）；维度与配置不符的写入必须跳过并告警。〔证据：GraphBuildConsumer.java:1285-1308 ‹CERTAIN›；Neo4jGraphStore.java:884-888 ‹CERTAIN›〕
- **REQ-F03**：实体向量检索必须基于 Neo4j 原生向量索引（余弦相似度），索引幂等创建、创建失败降级为不可用（不阻塞写入）；跨库索引按 kbId 过滤放大取数后截断。〔证据：Neo4jGraphStore.java:862-935 ‹CERTAIN›〕
- **REQ-F04**：未配置 embedding 模型时实体向量能力整体降级（不生成、不回填、不检索），图谱构建与文本检索不受影响。〔证据：GraphBuildConsumer.java:1316-1318,1267-1268 ‹CERTAIN›〕

## 5. 非功能约束

- **Neo4j 必需依赖**：应用启动时校验连通性，不可达则启动失败（fail-fast），错误信息含修复指引；不存在「静默降级」形态。〔Neo4jConfig.java:41-58 ‹CERTAIN›〕
- **异步与时延**：构建消费并发 5（MQ 监听）、应用内构建工作线程默认 3、单元级并行默认上限 15；LLM 抽取超时默认 180s、输出上限默认 4096 tokens（可配）。〔GraphBuildConsumer.java:184,150-151,94-106 ‹CERTAIN›；application.yml:134-141 ‹CERTAIN›〕性能阈值：〔决议 2026-09-24：单元抽取耗时以单次 LLM 超时上限（graph.build.llm-timeout 缺省 180s，application.yml:140）为事实上界——P95 目标取 ≤180s，超时即落入退化重试路径（REQ-A07）；整库构建不设硬性时长预算，时长按「单元数 ÷ 单元级并行（缺省 15）」线性扩展。理由：与现有超时配置口径一致、最小改动，引入更细 SLA 需监控面先行。现状差距：无 Micrometer/指标埋点度量抽取耗时，仅完成日志记单元成败——登记债务：构建耗时可观测性（代裁，用户可推翻）〕
- **进度可视**：构建进度按单元计数推进（每 5 个单元或最后一个单元刷新一次），进度更新不覆盖实体/关系计数（避免构建中误导性小数字）。〔GraphBuildConsumer.java:538-542,1237-1259 ‹CERTAIN›〕
- **操作可观测**：手动构建/重试/replay/设置更新/文件图谱删除均有操作日志埋点；构建成败亦有日志记录（失败仅 warn 不抛出）。〔GraphController.java:88,98,110,124,149,168 ‹CERTAIN›；GraphBuildConsumer.java:244-249,381-388,404-410 ‹CERTAIN›〕
- **降级纪律**：图存储查询/维护类操作失败一律空结果+记账，不向调用方抛错（仅构建写入类操作失败上抛以触发重试标记）。〔Neo4jGraphStore.java:31-34 类注释 ‹CERTAIN›，行为逐方法核对一致 ‹CERTAIN›〕
- **数据规模防御**：可视化节点上限 5000、合并候选比对上限 2000 实体、gleaning 输入护栏 16000 字符、Prompt 文本截断 8000 字符。〔GraphServiceImpl.java:70、EntitySimilarity.java:236、GraphBuildConsumer.java:113,995 ‹CERTAIN›〕

## 6. 验收清单

- [ ] 同一实体在不同单元以不同写法出现时，落库为同一节点（归一化名合并、确定性 ID 相同）（REQ-B06）
- [ ] 单元内容未变化时增量构建不产生任何 LLM 调用（缓存命中）（REQ-B03）
- [ ] replay 模式全程零 LLM 调用，且无持久化结果的单元被标记失败、分片可被增量构建补抽（REQ-B04、REQ-B05）
- [ ] 抽取产出含「445元/月」「商品数量：10」「总价」「假名乱码」等垃圾实体时，任何一项都不入图（REQ-A05）
- [ ] LLM 输出全部关系缺端点时，自动重试一次并保留实体+关系总数更高的一版（REQ-A07）
- [ ] 抽到实体但零关系的单元触发 gleaning，补充的关系不与首轮重复（REQ-A07）
- [ ] 同一 KB 并发投递两条构建消息时，仅一条被执行，另一条被跳过且不致消息丢失报错（REQ-B01）
- [ ] 合并 A→B 后：A 不存在、A 的边与 MENTIONS 全部挂在 B 上、无新增自环边、描述取更长一方（REQ-D02）
- [ ] 删除文件后，仅被该文件提及的关系与实体被回收，共享实体保留（REQ-D04）
- [ ] 前端画布不出现零边孤立节点，但 /graph/stats 的实体总数包含它们（REQ-C01、REQ-C02）
- [ ] LLM 配置缺失时构建终态为 failed 且错误信息可定位（非静默空构建）（REQ-B02）
- [ ] 全量重试后图谱不残留旧数据（清空在前、重建在后）（REQ-C05）
- [ ] PPR 以查询实体为种子生效（种子分片得分高于无关分片），种子未命中时返回空列表（REQ-C06）
- [ ] Neo4j 停机时应用启动失败，错误信息指向 fastrag-neo4j 服务与配置项（§5 非功能约束）
- [ ] 分片按 chunkIndex 重组为不超字符预算的抽取单元，超长单分片独占单元且不截断内容（REQ-A01）
- [ ] 内容过短（<50 字符）的单元不调用 LLM，直接记为已完成的空抽取（REQ-A02）
- [ ] 抽取 Prompt 携带 JSON 契约与类型白名单，表格主导内容切换表格专用规则（REQ-A03）
- [ ] LLM 输出经规范化入图：实体按（归一化名+类型）去重合并、关系端点解析、缺失默认值填充（REQ-A04）
- [ ] 白名单外实体类型归 UNKNOWN、编辑距离 ≤1 归并，白名单按 KB settings→yml→内置优先（REQ-A06）
- [ ] 每次写入同步建立证据链：实体 MENTIONS、三元组 TripleMention、未提取端点实体落带证据的 UNKNOWN 占位（边不悬空）（REQ-B07）
- [ ] 构建收尾刷新索引统计、为缺失 embedding 的实体分批回填向量、清理孤立实体与关系（REQ-B08）
- [ ] 关键词子图搜索多字段模糊匹配返回命中节点及内部边，实体类型标签去重排序（REQ-C03）
- [ ] 无索引记录的知识库查询构建状态返回 idle 零值面而非报错（REQ-C04）
- [ ] 合并候选仅基于 embedding 余弦相似度建议、不自动执行，超 2000 实体防御生效（REQ-D01）
- [ ] 合并后刷新索引缓存计数（失败不影响结果）；sourceId=targetId 或任一为空的请求被拒（REQ-D03）
- [ ] 每次合并在 kb_graph_merge_audit 留下审计快照（当前合并链路无审计写入方）（REQ-D05；豁免：占位/债务登记，见 implement 文档）
- [ ] 缝合写边确定性可重入：反向边已存在时并入溯源并返回既有边 ID（先建者胜）（REQ-E01）
- [ ] 缝合决策查询原语可用：按归一化名查实体、查分片提及实体、判两实体间已有边（REQ-E02）
- [ ] 缝合产出可持久化并支持 replay 重放（当前 v1.2 移除缝合 pass 后无写入方）（REQ-E03；豁免：占位/债务登记，见 implement 文档）
- [ ] 构建写入时为新实体生成 embedding，失败仅告警不阻断构建（REQ-F01）
- [ ] embedding 回填只补缺失向量（每批 20），维度与配置不符的写入跳过并告警（REQ-F02）
- [ ] 实体向量检索走 Neo4j 原生向量索引（余弦），索引幂等创建、创建失败降级不阻塞写入（REQ-F03）
- [ ] 未配置 embedding 模型时实体向量能力整体降级，图谱构建与文本检索不受影响（REQ-F04）

## 7. 测试策略

经由公共接口测：① 纯函数域（单元组装/名称归一/类型归一/垃圾闸门/抽取规范化/相似度/退化判定）全部无 mock 单测——现有 6 个 util 测试 + GraphBuildConsumerUnitTest 已覆盖，是最稳的回归面；② Prompt 契约域（buildExtractionPromptText/buildGleaningPrompt）断言核心规则与格式稳定（防 Formatter 引入 % 崩溃）；③ 构建编排域经 handleGraphBuild 公共入口测 KB 互斥、三模式分流、失败标记（GraphStore/LLM 在系统边界 mock）；④ 查询/合并/PPR 域以 Neo4j 为系统边界（testcontainers 或 mock GraphStore）测图语义：合并幂等与自环丢弃、孤儿回收口径、PPR 种子生效；⑤ replay 确定性与缓存命中是重点用例族——同一输入两次 replay 产出一致、缓存命中不重写单元行。合并不写审计（REQ-D05 缺口）与缝合 API 无调用方（REQ-E03）须以契约测试冻结预期语义，防实现漂移。
