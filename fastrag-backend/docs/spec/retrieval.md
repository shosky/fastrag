# 检索与查询增强（retrieval）— 需求规格

## 1. 特性与范围

检索与查询增强特性是 RAG 系统的「读」半边：以知识分片（上游 chunk-management 产出）为最小召回单元，提供一次完整的检索执行链路——查询预处理（纠错/改写/同义扩展）、多通道召回（向量 Milvus / 全文 MySQL FULLTEXT / QA 问答对 / 知识图谱子图 / 条款标题线索）、多路融合（RRF / 加权 / 交叉）、后处理（Rerank / MMR / 去重）、上下文组装（父分片放大 / 窗口 / 自动降级链），并配套检索日志与分析、更新提醒两个运营面，以及查询增强独立接口（建议 / 同义扩展 / 规则改写 / 图谱扩展 / Multi-Query 改写）。显式排除：分片与向量如何写入（归 chunk-management）、图谱如何构建（归 knowledge-graph，本特性只消费其 GraphStore 读口）、Embedding/Rerank/LLM 网关本身（fastrag-ai 是系统边界）、对话应用如何编排检索（归 app-center）、检索结果如何被 LLM 引用生成答案（消费方职责）。

## 2. Out of Scope

- **分片与向量的写入**：分片切分、父子装配、Milvus 写入、重切对账——归 chunk-management。本特性只做读侧消费（MilvusService.search、kb_chunk 查询，RetrievalServiceImpl.java:578-591 ‹CERTAIN›），并遵守其 Milvus collection 命名约定 `kb_{kbId}`（连字符转下划线，RetrievalServiceImpl.java:577 ‹CERTAIN›）。
- **图谱构建**：三元组抽取、实体合并、索引重建——归 knowledge-graph。本特性只消费 GraphStore.expandGraph / searchEntitiesByVector 两个读口（QueryEnhanceServiceImpl.java:342、RetrievalServiceImpl.java:745 ‹CERTAIN›）。
- **AI 能力实现**：Embedding/Rerank/LLM 的模型网关、重试、动态路由——fastrag-ai 系统边界。本特性只负责「查 model 表解析 apiUrl/apiKey 再调用」（RetrievalServiceImpl.java:1150-1165、QueryEnhanceServiceImpl.java:222-234 ‹CERTAIN›）。
- **QA 对管理**：kb_qa_pair 的 CRUD/导入/确认——归 qa-pairs。本特性只做召回消费（RetrievalServiceImpl.java:798-839 ‹CERTAIN›）。
- **kb_log / kb_update_log 的生产**：这两张表的主写入方在 publish/operation 域；本特性只追加 category=retrieval 类别的日志行（RetrievalServiceImpl.java:1758-1779 ‹CERTAIN›）与按时间读 kb_update_log 计数（UpdateRemindServiceImpl.java:58-69 ‹CERTAIN›）。
- **对话编排与答案生成**：检索结果如何拼入 prompt、多轮改写、引用标注——归 app-center / agent。本特性的交付物是 SearchResultItem 列表为止。
- **平台配置的治理**：sys_config、query_rule、term_record 三张表的管理端点——归 platform-config。本特性只声明消费口径（见 REQ-D03、REQ-D04）。
- **权限体系**：@KbAuth 切面判定协议归 iam（复用）；本特性因 body 传 kbId / 非 `/api/kb` 前缀路径而把校验下沉服务层（RetrievalServiceImpl.java:109-124 ‹CERTAIN›），协议与 iam.KbAccessDecisionModule 同构。

## 3. 领域术语

引用 `fastrag-backend/CONTEXT.md`：**向量检索（Vector Retrieval）**、**混合检索（Hybrid Retrieval）**、**知识图谱（Knowledge Graph）**、**查询增强（Query Enhancement）**、**更新提醒（Update Remind）**、**知识分片（Chunk）**、**父分片（Parent Chunk）**、**子分片（Child Chunk）**、**Vector Store**、**Graph Store**、**统一响应（ApiResponse）**、**SSE**（不适用本特性，无流式端点）。本特性是「向量检索 / 混合检索 / 查询增强 / 更新提醒」四个术语的定义主体：混合检索 = 向量 + 关键词（MySQL LIKE/FULLTEXT）+ 重排序的组合策略由本链检索编排落实；查询增强的「同义扩展仅消费启用状态术语词条」口径由本链消费行为落实。

## 4. 功能需求

### 4.1 检索编排与配置合并

检索入口接收「知识库 ID + 查询文本 + 可选检索配置」，按「请求参数 > 知识库保存配置 > 系统默认值」三级合并出本次生效配置，随后按固定阶段推进：预处理 → 核心检索（可含 Multi-Query）→ 图谱通道融合 → 条款线索融合 → 后处理 → 去重 → 上下文组装 →（组装后重排）→ QA 优先插入 → 统一编号与来源回填 → 日志记账。任何单通道失败都不得中断整体检索。

- **REQ-A01**：检索前执行知识库访问权限校验：超管（`*` 权限）与平台级 API Token 直接放行，其余用户要求 Redis `kb:acl:{kbId}:{userId}` 存在记录，否则拒绝。〔证据：RetrievalServiceImpl.java:113-124,140 ‹CERTAIN›〕
- **REQ-A02**：查询为空（null/空白）时直接返回空结果，不进入任何召回通道。〔证据：RetrievalServiceImpl.java:144-147 ‹CERTAIN›〕
- **REQ-A03**：生效配置按三级合并：系统默认（可被 sys_config 键 general_search_top_k / general_retrieval_mode / general_enable_rerank 覆盖）→ 知识库保存的 retrievalConfig JSON → 请求参数；合并只覆盖非 null 字段（topK 另要求 >0）。〔证据：RetrievalServiceImpl.java:150,284-312,372-425 ‹CERTAIN›〕
- **REQ-A04**：知识库检索配置加载失败（库不存在/JSON 损坏/查询异常）时降级使用已合并配置继续检索，不报错。〔证据：RetrievalServiceImpl.java:302-304 ‹CERTAIN›〕
- **REQ-A05**：similarityThreshold 支持显式传 0（0.0 不等于未传）：请求或 KB 配置传 0 时不做相似度过滤，未被合并逻辑当作 null 忽略。〔证据：RetrievalServiceImpl.java:377（仅判 null）+ 测试 RetrievalServiceImplTest.java:134-153 ‹CERTAIN›〕
- **REQ-A06**：系统默认值面：topK=10、mode=hybrid、similarityThreshold=0.2、图谱通道开（depth=1/maxEntities=10/recallCount=5）、关键词 QA 匹配开、Multi-Query 关（count=3）、上下文组装策略=auto、条款线索召回开（windowSize=3/recallCount=3）。〔证据：RetrievalServiceImpl.java:318-370 ‹CERTAIN›；取值依据 ‹CERTAIN（2026-09-24 深证）：多项默认已由 :355-370 行内注释载明权衡——Multi-Query 关因「每查询多 N 次 LLM 改写 + N-1 路召回，延迟更高」、auto 组装为「弥补分片切断」、图谱开「对标 LightRAG 一等检索通道」、关键词开「命中问答对时优先返回」›；〔决议 2026-09-24：「调参来源/SLA 依据」取工程默认口径——默认值不承载 SLA 承诺，是保守起点（业界惯例值 + 成本/能力权衡注释口径），全部可经三级合并在线覆盖（REQ-A03）；调参实验记录确无，登记文档债务。理由：最小改动 + 在线可调降低默认值敏感度；现状差距：无实验/基准调参记录（代裁，用户可推翻）〕
- **REQ-A07**：分片计数接口返回知识库可检索分片总数：排除软删除文件的分片与父分片（父分片是上下文聚合非独立可检索单元）。〔证据：RetrievalServiceImpl.java:263-278 ‹CERTAIN›〕

### 4.2 混合检索与召回通道

核心检索支持三种基础模式与两个增强编排：vector（Milvus 余弦近邻）、fulltext（MySQL FULLTEXT 索引）、hybrid（双路加权融合，默认）；enableMultiRetrieval 开启多路召回（向量/全文/QA 三通道可配数量 + 三种融合策略）；enableMultiQuery 开启 LLM 变体改写多路召回。图谱通道固定参与一路（方案 A），QA 关键词命中优先返回，条款/标题线索补救政策文件交叉引用漏召。所有通道共享「跳过软删除文件分片」与「失败降级不阻断」纪律。

- **REQ-B01**：mode 三值分发：vector / fulltext / hybrid（默认）；enableMultiRetrieval=true 时进入多路召回模式（优先于单模式）。〔证据：RetrievalServiceImpl.java:486-500 ‹CERTAIN›〕
- **REQ-B02**：向量检索：按 KB 绑定的嵌入模型 code 查 model 表（status=online）解析 apiUrl/apiKey → 查询文本向量化 → Milvus 搜索（collection=`kb_{kbId 下划线}`，取 topK×3 候选）→ MySQL 回表取完整内容 → 相似度 ≥ 阈值过滤 → 取前 topK。〔证据：RetrievalServiceImpl.java:561-617（collection 拼装 :577）‹CERTAIN（2026-09-24 深证：与 chunk-management 侧 ChunkServiceImpl.java:172、StorageServiceImpl.java:76 同规则「kb_{kbId 连字符转下划线}」独立实现，data.md `## chunk-management` 节 Milvus collection 约定已以 CERTAIN 载明）〕
- **REQ-B03**：模型表查不到该 code 或其离线时，嵌入调用回退默认网关（apiUrl/apiKey 传 null），不报错。〔证据：RetrievalServiceImpl.java:1587-1591 ‹CERTAIN›〕
- **REQ-B04**：向量检索任一环节失败时降级 MySQL LIKE 检索（整词 like content，按 chunkIndex 降序，topK 截断），并记 error 日志。〔证据：RetrievalServiceImpl.java:613-616,1712-1732 ‹CERTAIN›〕
- **REQ-B05**：全文检索以 FULLTEXT 索引执行，候选数 = max(topK, bm25RecallCount)，排名分按位次线性衰减；FULLTEXT 查询失败降级 LIKE。〔证据：RetrievalServiceImpl.java:623-654 ‹CERTAIN›〕
- **REQ-B06**：hybrid 模式 = 向量路 + 全文路各召回 max(topK×3, 50) 条后加权融合；权重 vectorWeight/bm25Weight 可配（默认各 0.5），加权分 = Σ 权重 × 位次衰减分，仅用于排序、不覆盖原始 similarity。〔证据：RetrievalServiceImpl.java:660-679,1051-1078 ‹CERTAIN›〕
- **REQ-B07**：多路召回模式：向量/全文通道数量可配（默认 topK），QA 通道 qaRecallCount>0 时参与；融合策略 rrf（默认，k=60）/ weighted / interleave 三选一。〔证据：RetrievalServiceImpl.java:685-714,127,1024-1046 ‹CERTAIN›〕
- **REQ-B08**：融合以 (fileId, chunkIndex) 为条目身份键去重合并；融合分只决定排序，各路原始 similarity/distance 保留给消费方。〔证据：RetrievalServiceImpl.java:1032-1046 + 测试 RetrievalServiceImplTest.java:335-339 ‹CERTAIN›〕
- **REQ-B09**：Multi-Query：enableMultiQuery=true 时 LLM 将查询改写为 N 个语义互补变体（含原查询，N=multiQueryCount 默认 3，截断 2~5），每个变体独立走完整召回后按 RRF 融合（融合上限 max(topK×3, 50)）；改写失败或可用变体 <2 时退化为单查询检索。〔证据：RetrievalServiceImpl.java:163-167,511-537 ‹CERTAIN›〕
- **REQ-B10**：图谱通道（方案 A）固定参与：enableGraphExpand=true（默认）时作为独立召回通道与主检索结果 RRF 融合，不把实体名拼入查询向量化的输入；实体匹配 = 文本（NER/降级分词 + CONTAINS）+ 向量（GraphStore.searchEntitiesByVector top5）双路合并去重，短于 3 字的文本实体名不参与 LIKE 召回（防 n-gram 泛词噪音）。〔证据：RetrievalServiceImpl.java:169-191,719-789 ‹CERTAIN（2026-09-24 深证：「不拼 query」动机由实现注释直接证实——:169-172「不再把实体名/关系标签拼入 query 再向量化（避免稀释 embedding），而是作为独立召回通道（对标 LightRAG kg_query）」，另有 :355「对标 LightRAG 一等检索通道」；基准对比数据确无为事实陈述，非推断）〕
- **REQ-B11**：图谱通道降级显式化：整路失败（如 Neo4j 不可达）时以 null 标记区分「正常零命中」，调用侧空回退继续主流程，warn 告警，且在检索日志记账 graphChannel=degraded / status=degraded；向量实体匹配失败单独降级为文本匹配。〔证据：RetrievalServiceImpl.java:174-191,754-757,783-788,1774-1776 ‹CERTAIN›〕
- **REQ-B12**：QA 关键词通道：enableKeywordMatch=true（默认）时按分词模糊匹配（英文/数字按词 ≥2 字符、中文按连续汉字块）命中 kb_qa_pair.question，任一 token 命中即候选、按命中 token 数降序取前 qaRecallCount（默认 5），以「Q:/A:」全文形式置顶返回；与主结果按 fileId+chunkIndex 去重。〔证据：RetrievalServiceImpl.java:225-246,798-839,842-861 + 测试 RetrievalServiceImplTest.java:218-236 ‹CERTAIN›〕
- **REQ-B13**：条款/标题线索召回：enableClauseRecall=true（默认）时，query 含「第X条/款/章/节」类条款提及（中文数字与阿拉伯数字均支持）则以条款号为锚 LIKE 召回锚点分片并联动前后 clauseWindowSize 个相邻分片（衰减计分，下限 0.3）；无条款命中时退化为标题/headingPath LIKE 召回且只保留 content 未命中查询 token 的互补项；命中结果与主检索 RRF 融合（上限 topK×2）。〔证据：RetrievalServiceImpl.java:193-203,871-989 ‹CERTAIN›〕

### 4.3 后处理与上下文组装

融合后的候选依次经过重排序（Rerank 模型 / LLM / MMR 可叠加）、内容去重、上下文组装三种后处理。上下文组装是「子分片召回、父片上下文」消费口径的执行者：命中子分片后按策略放大为父块/窗口/整文档，auto 策略（默认）按 L1 父块 → L2 窗口 → L3 原样三级降级链自动带回被切断的上下文。

- **REQ-C01**：Rerank：enableRerank=true 且配置 rerankModel 时，以 model 表 online 记录解析 API 配置调用 Rerank 服务，返回 index+relevance_score 回填 similarity/distance；模型未配置/离线/调用失败/空返回一律回退 topK 截断，不阻断。〔证据：RetrievalServiceImpl.java:1135-1188 ‹CERTAIN›〕
- **REQ-C02**：组装后重排：上下文策略为 parent_chunk / window / auto 时 Rerank 延后到上下文组装之后执行（对放大后的完整内容重排），其余策略在组装前执行。〔证据：RetrievalServiceImpl.java:205-222,546-555,1107-1116 ‹CERTAIN›〕
- **REQ-C03**：LLM 重排（enableLLMRerank）当前为占位实现：直接 topK 截断，无 LLM 打分。〔证据：RetrievalServiceImpl.java:1193-1199 ‹CERTAIN›——stub 债务登记〕
- **REQ-C04**：MMR 多样性控制（enableMMR）：λ 默认 0.7，按候选向量余弦相似度矩阵贪婪选择；embedding 数量与候选不符或失败时回退 topK 截断。〔证据：RetrievalServiceImpl.java:1124-1127,1205-1267 ‹CERTAIN›〕
- **REQ-C05**：内容去重：以内容前 500 字符 + 总长度为去重键，同一内容仅保留 similarity 最高的一条，并按 similarity 降序重排。〔证据：RetrievalServiceImpl.java:211,1668-1686 ‹CERTAIN›〕
- **REQ-C06**：上下文组装五策略：concat（默认语义的原样返回）/ parent_chunk（命中子分片替换为父块全文）/ parent_document（按文件聚合成整篇）/ window（前后 N 邻居）/ auto（默认，三级降级链：L1 父块放大且 token 预算内 → L2 窗口 → L3 无法定位文件时原样）。〔证据：RetrievalServiceImpl.java:1273-1352 ‹CERTAIN›〕
- **REQ-C07**：父分片仅作上下文放大：所有召回通道排除 chunkType='parent' 行；父块替换条目保留命中子分片的 fileId/chunkIndex（位置可追溯）并携带 200 字预览。〔证据：RetrievalServiceImpl.java:277,770,909,1717,1354-1374,1377-1413 ‹CERTAIN›——与 chunk-management「父分片不参与召回」口径闭环〕
- **REQ-C08**：window 组装受 token 预算约束：命中块始终保留（不受预算），邻居按离命中块由近及远累积、超预算即停止该层扩展（预算全局累积）；contextOrder=document_order 时按文件+索引重排。〔证据：RetrievalServiceImpl.java:1446-1531 ‹CERTAIN›〕
- **REQ-C09**：返回前统一设置结果序号 index（0 起），并按 fileId 批量回填来源文件名 fileName；回填失败降级不阻断。〔证据：RetrievalServiceImpl.java:249-252,1612-1633 ‹CERTAIN›〕

### 4.4 查询增强（Query Enhancement）

查询增强提供独立可调用的五件套：查询建议（suggest）、同义扩展（expandSynonyms）、规则改写（applyQueryRules）、图谱扩展（expandGraph）、Multi-Query 变体改写（expandQueries）；其中前三者同时作为检索预处理链的可选阶段（顺序：纠错 → 改写 → 同义扩展）。术语消费口径：同义扩展仅消费启用状态（status=1）的术语词条。

- **REQ-D01**：预处理链顺序固定：自动纠错 → 查询规则改写 → 同义词扩展；每步独立 try-catch，失败记 warn 并沿用上一步结果，不阻断检索。〔证据：RetrievalServiceImpl.java:431-480 ‹CERTAIN›〕
- **REQ-D02**：查询建议（suggest/自动纠错）当前为占位实现：原样返回查询（suggestedQuery=query, reason 空）。〔证据：QueryEnhanceServiceImpl.java:69-75 ‹CERTAIN›——stub 债务登记；「错别字+拼音纠错的预期行为」〔决议 2026-09-24：纠错目标行为取——对查询做拼写级纠错（错别字替换 + 拼音/同音字候选），输出 correctedQuery 与逐项修改 reason，不改变「日志记 originalQuery」口径（REQ-D07）；实现路径取先词典/拼音规则、后 LLM 兜底（可配）。理由：搜索引擎 query correction 业界惯例 + 接口面已定（stub 签名不变）最小改动；现状差距：suggest 仍为原样返回占位——stub 债务已登记（REQ-D02 及 design §1）（代裁，用户可推翻）〕〕
- **REQ-D03**：同义扩展仅消费启用状态术语词条（term_record.status=1）：query 含术语 term 时补入其全部别名、含别名时反向补入术语，追加词以空格拼接到原查询尾部（expandedQuery），不修改原查询。〔证据：QueryEnhanceServiceImpl.java:78-89 + TermServiceImpl.java:27-34,138-176 ‹CERTAIN›〕
- **REQ-D04**：查询规则改写（applyQueryRules）当前为占位实现：原样返回查询且 appliedRules 恒为空——platform 侧 query_rule 表（rule_type/pattern/action/enabled/priority 字段齐全）在检索链零消费，改写执行引擎缺失。〔证据：QueryEnhanceServiceImpl.java:91-97 ‹CERTAIN› + schema.sql:592-602 + grep 全模块无 QueryRule 消费 ‹CERTAIN›——stub 债务/字段契约断裂登记〕
- **REQ-D05**：Multi-Query 变体改写：以固定中文提示词调用 LLM 生成 N-1 个变体，逐行清洗（去编号/引号、长度 2~100、去重、异于原查询）后与原查询合并，超目标数截断；LLM 失败或无可用变体时返回仅含原查询的单元素列表。〔证据：QueryEnhanceServiceImpl.java:110-182 ‹CERTAIN›〕
- **REQ-D06**：图谱扩展：先 NER 提取实体（配置 nerModel 时经 model 表解析调用 LLM 返回 JSON 数组；未配置/模型离线/解析失败降级为正则分词：英文词整词保留、中文块 ≤4 字原样、长块补 2~4 字 n-gram 且总量 ≤20）→ GraphStore.expandGraph 按深度/最大实体数展开 → 匹配实体名与关系标签拼接为 expandedQuery；空查询短路返回；直达接口执行 KB 权限校验。〔证据：QueryEnhanceServiceImpl.java:194-362,272-302 ‹CERTAIN›〕
- **REQ-D07**：改写变体仅用于召回，不改动用户原始查询的日志与展示（日志记录 originalQuery）。〔证据：RetrievalServiceImpl.java:137,1749 + QueryEnhanceServiceImpl.java:104-106 ‹CERTAIN›〕

### 4.5 检索日志与分析

每次检索完成后双写两类日志（写失败不影响检索结果返回）：kb_retrieval_log 承载可量化的效果指标，kb_log（category=retrieval）承载带图谱通道健康状态的审计行。日志读取面按组织隔离，统计分析给出无结果率、延迟、热门/无结果查询 Top10，支撑检索质量运营。

- **REQ-E01**：检索完成写 kb_retrieval_log：kbId、原始查询、命中数、hasResult、耗时 latencyMs、图谱通道命中数 graphEntityCount、userId、createdAt。〔证据：RetrievalServiceImpl.java:1738-1756 + 实体 KbRetrievalLog.java:25-33 ‹CERTAIN›〕
- **REQ-E02**：同步写 kb_log（category=retrieval, action=search）：status=success/degraded（图谱通道降级时），extra JSON 携带 mode/topK/hits/duration/graphExpanded/graphHits/graphChannel 七字段。〔证据：RetrievalServiceImpl.java:1758-1779 ‹CERTAIN›〕
- **REQ-E03**：日志写入失败仅记 warn，检索结果正常返回。〔证据：RetrievalServiceImpl.java:1780-1782 ‹CERTAIN›〕
- **REQ-E04**：日志分页按组织隔离：普通用户仅见其可访问知识库集合（KbAccessChecker）内的日志；平台级 API Token 不过滤；指定 kbId 不在可访问集合时返回空页（不泄露库存在性）；支持 hasResult 过滤、按 createdAt 降序、PageResult 三元组。〔证据：RetrievalLogServiceImpl.java:46-84 ‹CERTAIN›〕
- **REQ-E05**：分析统计返回：总查询数、无结果数、无结果率（百分比保留 2 位）、平均延迟、平均命中数、热门查询 Top10、无结果查询 Top10；组织隔离口径与分页一致，无权限/无数据返回零值结构而非报错。〔证据：RetrievalLogServiceImpl.java:86-138 + KbRetrievalLogMapper.java:31-62 ‹CERTAIN›〕
- **REQ-E06**：日志支持外部写入与更新（供消费方补记/标注）。〔证据：RetrievalController.java:67-68、RetrievalLogServiceImpl.java:54-62 ‹CERTAIN›；⚠ 写入/更新端点无权限校验——债务登记见 §5〕

### 4.6 更新提醒（Update Remind）

更新提醒为知识库提供可配置的新鲜度检查：每库一条配置（启用开关 + cron 表达式），触发时统计自上次提醒以来的更新日志条数并回传给调用方。现状实现为拉取式（调用即触发统计与时间戳刷新），cron 表达式仅存储。

- **REQ-F01**：提醒配置按知识库增删改查；新增时默认 enabled=1、cronExpr="0 9 * * *"（每天 9 点）；带 id 保存为更新。〔证据：UpdateRemindServiceImpl.java:39-47 ‹CERTAIN›〕
- **REQ-F02**：触发提醒：统计 kb_update_log 中 timestamp 晚于 lastRemindAt 的条数（无 lastRemindAt 则统计全量），返回 kbId/enabled/lastRemindAt/cronExpr/updateCount 与固定文案 message；触发后将 lastRemindAt 刷新为当前时间。〔证据：UpdateRemindServiceImpl.java:48-73 ‹CERTAIN›〕
- **REQ-F03**：无配置的知识库触发提醒时返回默认状态：enabled=false、cronExpr 默认值、updateCount 为全量计数，不报错。〔证据：UpdateRemindServiceImpl.java:49-55,64-69 ‹CERTAIN›〕
- **REQ-F04**：cron 表达式当前仅有存储与回显，后端无任何调度器消费它——定时推送未实现，提醒节奏实际由调用方控制。〔证据：grep 全后端无 @Scheduled 消费 cronExpr/UpdateRemind ‹CERTAIN›——stub 债务登记；「产品预期的定时推送行为」〔决议 2026-09-24：取「应用内拉取式推送」——渠道=前端/调用方按 cronExpr 自行定时调用 trigger 接口（cronExpr 定位为调用方的节奏参考值，后端不消费），不做邮件/IM 主动推送；后续如需邮件可复用 iam 既有邮件通道另行立项。理由：最小改动（拉取式统计已闭环，REQ-F02）+ 避免引入调度器与外部通知依赖；现状差距：无任何调度器消费 cronExpr——stub 债务已登记（REQ-F04）（代裁，用户可推翻）〕〕

## 5. 非功能约束

- **降级完备性**：任一召回通道（向量/全文/图谱/QA/条款/Rerank/MMR）失败均不阻断检索主流程，全部有 catch-回退路径。〔证据：RetrievalServiceImpl.java:1689-1696 safeSearch + 各通道 catch ‹CERTAIN›〕
- **权限一致性**：检索与图谱扩展的权限判定与 iam @KbAuth 切面同构（同 Redis 键、同放行规则），因 body 传参下沉服务层。〔证据：RetrievalServiceImpl.java:109-124、QueryEnhanceServiceImpl.java:56-67 ‹CERTAIN›〕
- **性能阈值**：检索延迟、召回率的量化 SLA——〔决议 2026-09-24：取保守 SLA——基础检索（topK=10、hybrid、无 Multi-Query/Rerank）P95 ≤ 1.5s；含 Rerank/Multi-Query/图谱通道的增强路径 P95 ≤ 3s；召回质量不设硬指标，以 kb_retrieval_log 的无结果率/平均命中数/延迟运营观测替代（REQ-E05 分析面）。理由：业界惯例（交互式 RAG 检索秒级返回）+ 与既有 latency_ms 埋点能力一致；现状差距：无阈值断言与告警，登记债务——随测试/监控建设偿还（代裁，用户可推翻）〕。
- **多轮调参**：检索配置全部可经 KB 保存配置与请求参数在线调整，无需改代码。〔证据：RetrievalRequest.RetrievalConfig 全字段可空覆盖设计 RetrievalRequest.java:29-80 ‹CERTAIN›〕

## 6. 验收清单

- [ ] 空 query 检索返回空列表而非错误（REQ-A02）
- [ ] 无 KB 访问权限的用户检索被拒绝；超管与 api-token 放行（REQ-A01）
- [ ] 请求只传 similarityThreshold=0 时低分结果不被 0.2 默认阈值过滤（REQ-A05）
- [ ] 请求未传 mode/topK 时 KB 保存配置生效（不被覆盖）（REQ-A03）
- [ ] vector 模式下 Milvus 不可用时降级返回 MySQL LIKE 结果（REQ-B04）
- [ ] fulltext 模式候选数等于 max(topK, bm25RecallCount)（REQ-B05）
- [ ] hybrid 模式向量路与全文路结果同时出现在最终结果（REQ-B06）
- [ ] 融合后各路原始 similarity 不被融合排名分覆盖（REQ-B08）
- [ ] enableGraphExpand 默认开启时图谱命中参与 RRF 融合；显式关闭后无 graph 来源结果（REQ-B10）
- [ ] 图谱通道整路失败时检索仍返回主结果，且日志 status=degraded、graphChannel=degraded（REQ-B11）
- [ ] 短于 3 字的文本实体名不触发图谱 LIKE 召回（REQ-B10）
- [ ] enableKeywordMatch 默认开启时分词模糊匹配命中 QA 对且置顶返回（REQ-B12）
- [ ] query 含「第三条」时条款锚点及前后相邻分片被召回（REQ-B13）
- [ ] auto 组装下命中子分片返回父块全文且 chunkType=parent、chunkIndex 保留子片位置（REQ-C06、REQ-C07）
- [ ] window 组装在 token 预算耗尽时停止邻居扩展但保留全部命中块（REQ-C08）
- [ ] enableRerank 且模型离线时回退 topK 截断而非报错（REQ-C01）
- [ ] 同义扩展对禁用（status=0）词条不生效（REQ-D03）
- [ ] 规则改写接口当前返回原查询（REQ-D04 stub 行为冻结，直至执行引擎落地；豁免：占位/债务登记，见 implement 文档）
- [ ] Multi-Query 改写失败时退化为单查询检索且结果非空（REQ-B09、REQ-D05）
- [ ] 无权限用户查询日志分析返回零值结构而非他人库数据（REQ-E05）
- [ ] 检索日志写入失败不影响检索结果返回（REQ-E03）
- [ ] 新增提醒配置不传 enabled/cronExpr 时落库为 1 与 "0 9 * * *"（REQ-F01）
- [ ] 触发提醒后 lastRemindAt 被刷新，下次 updateCount 只统计增量（REQ-F02）
- [ ] KB 检索配置加载失败（库不存在/JSON 损坏）时降级继续检索不报错（REQ-A04）
- [ ] 无任何覆盖时系统默认值面生效（topK=10/hybrid/阈值 0.2/图谱通道开/Multi-Query 关/组装 auto 等）（REQ-A06）
- [ ] 分片计数接口排除软删除文件分片与父分片（REQ-A07）
- [ ] mode 三值分发正确，enableMultiRetrieval=true 时优先进入多路召回（REQ-B01）
- [ ] 向量检索按 KB 绑定模型解析网关，取 topK×3 候选并按阈值过滤取前 topK（REQ-B02）
- [ ] 模型 code 查无或离线时嵌入回退默认网关不报错（REQ-B03）
- [ ] 多路召回通道数量可配，融合策略 rrf/weighted/interleave 三选一生效（REQ-B07）
- [ ] 融合以 (fileId, chunkIndex) 为条目身份键去重合并（REQ-B08）
- [ ] 组装后重排：parent_chunk/window/auto 策略下 Rerank 对放大后的完整内容执行（REQ-C02）
- [ ] LLM 重排开关打开时仍直接 topK 截断（REQ-C03 占位现状冻结；豁免：占位/债务登记，见 implement 文档）
- [ ] MMR 以 λ=0.7 贪心选择增加多样性，embedding 异常时回退 topK 截断（REQ-C04）
- [ ] 内容去重以前 500 字符+总长度为键，仅保留 similarity 最高一条并按其降序重排（REQ-C05）
- [ ] parent_document 按文件整篇聚合、window 按前后 N 邻居组装均生效（REQ-C06）
- [ ] 父块替换条目保留命中子分片 fileId/chunkIndex 并携带 200 字预览（REQ-C07）
- [ ] 返回前统一设置 index 序号并按 fileId 批量回填 fileName，回填失败降级不阻断（REQ-C09）
- [ ] 预处理链顺序固定为纠错→规则改写→同义扩展，单步失败沿用上一步结果不阻断（REQ-D01）
- [ ] 查询建议原样返回查询（REQ-D02 占位现状冻结；豁免：占位/债务登记，见 implement 文档）
- [ ] 图谱扩展在 NER 模型未配置/离线/解析失败时降级正则分词并按深度展开子图（REQ-D06）
- [ ] 改写变体仅用于召回，日志与展示记录 originalQuery（REQ-D07）
- [ ] 检索完成写 kb_retrieval_log（查询/命中数/耗时/图谱通道命中数等）（REQ-E01）
- [ ] 同步写 kb_log（category=retrieval）extra 携带七字段，图谱通道降级时 status=degraded（REQ-E02）
- [ ] 日志分页按组织隔离，指定越权 kbId 返回空页不泄露库存在性（REQ-E04）
- [ ] 检索日志支持外部写入与更新（REQ-E06 现状无权限校验，债务登记；豁免：占位/债务登记，见 implement 文档）
- [ ] 无配置知识库触发提醒返回默认状态与全量计数不报错（REQ-F03）
- [ ] cron 表达式仅有存储与回显、无调度器消费（REQ-F04 stub 债务登记现状；豁免：占位/债务登记，见 implement 文档）

## 7. 测试策略

经由四个公共接口（RetrievalService / QueryEnhanceService / RetrievalLogService / UpdateRemindService）测试。最重要：**混合召回与降级路径用例族**（每通道失败注入 → 主结果仍在 + 日志记账正确）、**配置合并优先级**（三级覆盖 + 0 值语义）、**上下文组装降级链**（auto 的 L1/L2/L3 与 token 预算）。外部边界（Milvus/Embedding/Rerank/LLM/GraphStore/Redis/MySQL Mapper）一律 mock——它们是声明缝；内部模块之间禁止 mock。现有 RetrievalServiceImplTest（15 用例）已覆盖阈值 0 值、bm25RecallCount、QA 置顶、图谱通道默认开/关/向量实体匹配/短实体过滤/window 组装、KB 配置保持、hybrid 融合、auto 三级降级、条款召回；QueryEnhanceServiceImplTest（2 用例）覆盖降级分词。缺口：降级路径（Milvus/Rerank 异常注入）、日志组织隔离、更新提醒、Multi-Query 降级、RRF/加权/交叉三融合策略对比——见 tdd/retrieval.md。
