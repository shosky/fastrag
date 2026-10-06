# 知识图谱（knowledge-graph）— 测试设计

> 对应契约：contract/interfaces.md `## knowledge-graph` 节（GraphStore / GraphExtractionPipeline / PprRanker）与 data.md 同名节。合并幂等与 replay 确定性是本链两大重点用例族（§2 切片 3/4 加粗行）。

## 1. 切片规划

| 切片 | 接口（contract） | AFK/HITL | 说明 |
|---|---|---|---|
| 切片 1：合并治理闭环 | GraphStore.findMergeCandidates/mergeEntity | **HITL** | 候选是否真同义需人工判断（契约明示「只建议不自动合并」）；审计缺口（REQ-D05 欠账）的人工核验 |
| 切片 2：抽取语义质量抽验 | GraphExtractionPipeline（LLM 边界真实调用） | **HITL** | 垃圾实体漏网率/关系方向正确性只能人审样本；自动化只覆盖 Prompt 契约与闸门规则（切片 5） |
| 切片 3：GraphStore 图语义族 | GraphStore 全族 | AFK | Neo4j 为系统边界（testcontainers 优先，CI 无容器时 mock Driver）；**合并幂等/孤儿回收/缝合幂等在切片 3** |
| 切片 4：构建编排族 | GraphExtractionPipeline.handleGraphBuild | AFK | mock GraphStore/LlmService/EmbeddingService/MQ；**replay 确定性与缓存命中在切片 4** |
| 切片 5：抽取质量防线纯函数族 | GraphExtractionPipeline 包级静态面 + ExtractionUnitModule | AFK | 无 mock；现有 8 个测试类的地盘，按缺口补 |
| 切片 6：PPR 排序族 | PprRanker.rankChunksByPpr | AFK | mock GraphStore 两个读方法 |
| 切片 7：门面查询治理族 | GraphManagementModule（经 GraphService） | AFK | 统计缓存修复/删除后同步/孤立点过滤（过滤已有测试） |

## 2. 测试用例（逐接口）

### GraphStore（contract: knowledge-graph.GraphStore）——切片 3，mock 边界：Neo4j

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [同名实体] [不同原始写法写入时] [合并为单节点且类型计票胜出] | 库内已有 Entity(normalizedName="小微ict", type=产品, typeCounts{产品:2}) | createEntity(同名, type=套餐) | 节点仍 1 个；type=票数高者；typeCounts 累加；name 保持首写值；description 取更长 | REQ-B06 | Neo4j（testcontainers） |
| **[合并执行] [出边入边与提及迁移后] [source 消失且无自环]** | source 有出边→o1、入边←o2、边→target；source/target 各有 MENTIONS | mergeEntity(kb, sourceId, targetId) | source 节点不存在；o1→target、o2→target 边存在且 tripleId 保留；原 source→target 边被丢弃（自环）；MENTIONS 并入 target；描述择长、票数累加 | REQ-D02 | 同上 |
| **[合并重复执行] [对已合并的 sourceId 再次合并时] [报端点缺失]** | 上例已合并完成 | 再次 mergeEntity(kb, sourceId, targetId) | 抛 IllegalArgumentException（E_MERGE_ENDPOINT_MISSING），图状态不变（幂等终态） | REQ-D02 | 同上 |
| [删除文件] [仅被该文件提及的实体关系存在时] [全部回收且共享实体保留] | 文件 A 的 chunk 提及实体 X（仅 A）；实体 Y 同时被文件 B 提及 | deleteFileGraph(kb, A) | Chunk(A)/MENTIONS/TripleMention 消失；X 及其无溯源关系被回收；Y 保留；边空端点 ID 先回填再判定 | REQ-D04 | 同上 |
| **[缝合写边] [同 label 反向边已存在时] [不新建且证据并入既有边]** | 已存在 target→label→source（tripleId=T1） | createDirectedRelation(..., chunkIds=[c2], ...) | 返回 T1；无新边；TripleMention(T1, c2) 建立；重复调用返回仍 T1（幂等） | REQ-E01 | 同上 |
| [缝合写边] [无反向边时] [按 MERGE 语义建边并溯源] | 端点对无边 | createDirectedRelation(...) | 返回新 tripleId；端点占位实体被创建（无类型）；每个 chunkId 一条 TripleMention | REQ-E01、B07 | 同上 |
| [实体提及] [实体以不同展示名重复提及时] [MENTIONS 按确定性 ID 命中] | 实体首写 name="小微 ICT"（normalizedName="小微ict"） | createEntityMention(entityName="小微ICT 服务商", entityId=E1, chunkId=C1) | MENTIONS(E1→C1) 存在（按 entityId 非 name 匹配）；实体不被孤儿回收误删 | REQ-B07 | 同上 |
| [向量写入] [维度与配置不符时] [跳过且不报错] | 配置维度 1024 | updateEntityEmbedding(维度 768 的向量) | 节点 embedding 属性不变；无异常；索引未创建 | REQ-F02 | 同上 |
| [向量检索] [索引创建失败时] [降级返回空列表] | Neo4j < 5.11（建索引语句失败） | searchEntitiesByVector(kb, emb, 10) | 返回空列表；后续写入不阻塞 | REQ-F03 | 同上 |
| [expandGraph] [实体名互为子串且 2 跳深度时] [按提及数加权返回邻居] | 实体"故障根因分析（RCA）"提及数最高 | expandGraph(kb, ["故障根因分析"], 2, 10) | 返回该实体+1~2 跳邻居；mentionCount 降序；expandedQuery 空格拼接 | 消费口径（retrieval 链） | 同上 |

### GraphExtractionPipeline（contract: knowledge-graph.GraphExtractionPipeline）——切片 4，mock 边界：GraphStore / LlmService / EmbeddingService / RabbitMQ / 时间

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [构建消息] [同一 KB 已有构建执行时] [重复消息被跳过且已确认] | runningKbs 含 kb1 | handleGraphBuild({kbId=kb1}) | 不提交新构建任务；无异常外抛；消息不重投 | REQ-B01 | RabbitMQ |
| [构建消息] [缺少 kbId 时] [丢弃并告警] | message 无 kbId | handleGraphBuild(message) | 直接返回；零副作用 | 契约 E_MSG_NO_KBID | RabbitMQ |
| [构建编排] [LLM 配置缺失时] [终态 failed 且错误可定位] | 策略无 llm_model、KB 无 graphLlmModel | runGraphBuild(full) | kb_graph_index.status=failed、buildError 含指引文案；不调用单元抽取 | REQ-B02、E_LLM_NOT_CONFIGURED | LlmService/DB |
| [LLM 路由] [策略模型离线时] [回退 KB 级模型] | 策略 llm_model 无 online 记录；kb.graphLlmModel 有 | 解析 LLM 配置 | 采用 KB 级模型的 apiUrl/apiKeyRef | REQ-B02 | DB |
| **[增量构建] [单元内容未变化时] [零 LLM 调用且单元行不重写]** | kb_graph_unit 行 status=1、contentHash 一致、结果非空 | runGraphBuild(incremental) | LlmService.chatWithTimeout 零调用；单元行 update 零调用；cachedUnits 计数增加 | REQ-B03 | GraphStore/LlmService |
| **[replay 重建] [两次执行同一库时] [图写入序列确定且零 LLM]** | 单元行均有持久化结果 | runGraphBuild(replay) 两次 | 两次 LlmService 零调用；对 GraphStore 的 createEntity/createRelation 参数序列一致（确定性） | REQ-B04 | GraphStore/LlmService |
| [replay 重建] [单元无持久化结果时] [标失败待增量补抽] | 单元行 extraction_result 为空 | runGraphBuild(replay) | 该单元分片 graph_indexed=2；单元行 status=2；计入失败 | REQ-B04、E_REPLAY_RESULT_MISSING | 同上 |
| [单元抽取] [LLM 超时失败后] [构建内重试一轮且仍败标 graph_indexed=2] | 首轮 1 个单元失败 | runGraphBuild(incremental) | 失败单元被重试一次；仍败则分片=2、status=2；终态 completed 且 failedChunks=1 | REQ-B05 | LlmService |
| [退化兜底] [LLM 首答全关系缺端点时] [重试一次并择优] | 首答 1 实体 4 空关系；重答 3 实体 2 完整关系 | extractWithNormalization | 采用重答（score 更高）；不采纳首答 | REQ-A07 | LlmService |
| [gleaning 补捞] [有实体零关系且输入超护栏时] [跳过补捞沿用首轮] | text+首答 JSON > 16000 字符 | extractWithNormalization | 不发起第二次 LLM 调用 | REQ-A07 | LlmService |
| [质量闸门] [垃圾实体与属性型实体入图时] [全部拦截且计数] | 抽取结果含「445元/月」「商品数量：10」「客户联系（属性）」 | writeUnitGraph | createEntity 仅收到合法实体；valueSkipped 计数=3 | REQ-A05 | GraphStore |
| [端点解析] [关系端点未在单元抽取时] [登记带证据 UNKNOWN 占位] | 关系引用"X"但实体列表无 X、图内也无 | writeUnitGraph | createEntity(X, UNKNOWN) + MENTIONS 已建立；边端点指向占位 ID | REQ-B07 | GraphStore |
| [收尾治理] [构建完成时] [按序回填向量并清理孤儿] | 全单元成功 | runGraphBuild 完成 | listEntitiesWithoutEmbedding→分批 embed→updateEntityEmbedding；cleanupOrphanNodes 恰一次；状态 completed+live 计数 | REQ-B08、F01 | GraphStore/EmbeddingService |
| [越界清理] [文件分片变少导致单元缩水时] [旧单元行删除] | 旧行 unitIndex=5，本轮仅 3 单元 | runGraphBuild | unitIndex≥3 的旧行被删除 | REQ-B03 | DB |

### ExtractionUnitModule + 抽取质量防线（切片 5，纯函数，无 mock；现有测试基线：GraphBuildConsumerUnitTest/PromptTest、6 个 util 测试）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [单元组装] [超预算边界恰好相等时] [不提前切段] | 预算 100；分片 60+40 | assemble | 单个单元（len==maxChars 不触发 >maxChars） | REQ-A01 | 无 |
| [名称归一] [全角/尾部数量/CJK 空格混合时] [归一到同一结果] | "（云化版）TP LINK CT4WS-P V2*4个" 等 | NameNormalizer.normalize | 与半角无后缀版本结果相同 | REQ-B06 前置 | 无 |
| [类型归一] [编辑距离 1 与白名单未命中时] [归并/落 UNKNOWN] | "故障类别"、"瞎写的类型" | EntityTypeNormalizer.normalize | "故障类型" / UNKNOWN | REQ-A06 | 无 |
| [垃圾闸门] [金额后缀伪名与乱码时] [拦截；带中文型号名保留] | "头标准包2999元"、"移动окрайвер"、"华为B671-S2" | EntityNameGuard.isJunkName | true / true / false | REQ-A05 | 无 |
| [抽取规范化] [同名实体属性并集与描述择长时] [合并为一且默认值填充] | 两同(归一化)名实体不同属性；无 label 关系 | ExtractionNormalizer.normalize | 实体 1 个属性并集；关系 label=RELATED_TO；metadata 注入 | REQ-A04 | 无 |
| [Prompt 契约] [表格主导文本时] [切换表格规则且类型提示排序稳定] | 同一白名单两次构建 | buildExtractionPromptText | 含"套餐→设备"规则；类型提示串两次一致；文本截断 8000；无裸 %s | REQ-A03 | 无 |

### PprRanker（contract: knowledge-graph.PprRanker）——切片 6，mock 边界：GraphStore

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [PPR 排序] [种子实体的提及分片存在时] [种子邻接分片得分高于孤立分片] | 提及图 E1→C1,C2；E2→C2 | rankChunksByPpr(kb, ["实体1"], 10) | 首位 C1；score>0；entityCount=度/2 | REQ-C06 | GraphStore.listEntityMentions/findEntityId |
| [PPR 排序] [种子全部未命中时] [空列表] | findEntityId 返回 null | rankChunksByPpr | List.of()；不迭代 | REQ-C06 | 同上 |
| [PPR 排序] [提及加载异常时] [空列表不抛错] | listEntityMentions 抛异常 | rankChunksByPpr | List.of() + warn | REQ-C06 | 同上 |

### GraphManagementModule（经 GraphService，切片 7，mock 边界：GraphStore/DB/MQ）

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [孤立点过滤] [零边实体存在时] [画布剔除且兼容存量边名称匹配] | 边无 source_id/target_id 仅有名称 | getGraphData | 零边节点剔除；名称命中的节点保留（存量兼容） | REQ-C01 | DB（现有 GraphServiceImplOrphanFilterTest 基线） |
| [统计口径] [索引计数为 0 但图有数时] [实时回退并自动修复缓存] | idx.entityCount=0；graphStore 计数 7/4 | getGraphStats | 返回 7/4；idx 被更新且 status 修复为 completed | REQ-C02 | GraphStore/DB |
| [统计口径] [孤立实体存在时] [实体总数含孤立点] | 图内 5 实体其中 2 个零边 | getGraphStats vs getGraphData | stats=5；画布节点=3 | REQ-C01、C02 | 同上 |
| [全量重试] [旧图存在时] [先清空再重置再重触发] | 旧图 3 实体；全库分片 graph_indexed=1 | retryBuild | clearGraph→UPDATE kb_chunk graph_indexed=0→索引重置→full 消息，顺序不变式 | REQ-C05 | GraphStore/DB/MQ |
| [文件删图] [图谱清空后] [索引整体归 idle] | 删除后 countEntities=0 | deleteFileGraph | status=idle、计数/错误/lastBuiltAt 全清零 | REQ-D04 | 同上 |
| [合并门面] [sourceId=targetId 时] [拒绝且不触图] | 相同 ID | mergeEntities | IllegalArgumentException；graphStore.mergeEntity 零调用 | REQ-D03 | GraphStore |
| [构建状态] [无索引记录时] [idle 零值面] | indexMapper 返回 null | getIndexStatus | status=idle、九字段零值/null、不抛错 | REQ-C04 | DB |
| [设置读写] [JSON 损坏时] [回退默认四键] | settings 列非法 JSON | getSettings | {maxNodes:500, searchDepth:2, excludeChunkNodes:true, entitySchema:""} | REQ-C04（设置面） | DB |

### 切片 1（HITL）：合并治理闭环

人工步骤：① 对生产库调 `GET /merge-candidates?threshold=0.92`，抽 20 对候选人工判定真同义率；② 对确认对执行 `POST /merge`，在 Neo4j 浏览器核验 REQ-D02 终态与自环；③ 核验 kb_graph_merge_audit 是否产生审计行（**预期失败——欠账**，作为 REQ-D05 补线的验收入口）；④ 合并后刷新 stats 观察计数一致性。产出：判定记录 + 缺陷单（审计缺线）。

### 切片 2（HITL）：抽取语义质量抽验

人工步骤：① 选 2 个真实知识库各 50 单元，重放/增量构建后抽样三元组，按「实体合法性/关系方向/垃圾漏网」三栏打标；② 与闸门规则失败案例比对，产出 EntityNameGuard 白名单/黑名单修订建议；③ UNKNOWN 占比 >30% 时修订 entitySchema 配置并复测。产出：质量抽验报告（GAP 阈值无代码证据，由本切片首次建立基线）。

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| 合并执行→source 消失无自环 | GraphStore.mergeEntity | GraphStoreModule | REQ-D02 |
| 合并重复执行→端点缺失 | GraphStore.mergeEntity | GraphStoreModule | REQ-D02（幂等终态） |
| 缝合写边→反向先建者胜 | GraphStore.createDirectedRelation | StitchFramework（休眠） | REQ-E01 |
| replay 两次→零 LLM 且写入序列一致 | GraphExtractionPipeline.handleGraphBuild | GraphExtractionModule | REQ-B04 |
| 增量构建→缓存命中零 LLM | GraphExtractionPipeline.handleGraphBuild | GraphExtractionModule + ExtractionUnitModule | REQ-B03、A01 |
| 垃圾实体拦截 | GraphExtractionPipeline.writeUnitGraph | GraphExtractionModule | REQ-A05 |
| PPR 种子生效 | PprRanker.rankChunksByPpr | GraphQueryServiceModule | REQ-C06 |
| 统计自动修复 | GraphManagementModule.getGraphStats | GraphManagementModule | REQ-C02 |

## 4. 待实施说明

红绿循环**未执行**（本链为测试设计；现有测试仅为开发期沉淀：GraphBuildConsumerUnitTest 8 用例、GraphBuildConsumerPromptTest 8 用例、GraphServiceImplOrphanFilterTest 3 用例、util 5 测试类约 27 用例——覆盖切片 5 全部与切片 7 的孤立点过滤）。执行时按本文件从**切片 3（GraphStore 图语义，Tracer Bullet=合并幂等用例）**起步：先以 testcontainers-Neo4j 打通绿路，再并行切片 4/6/7；切片 1/2（HITL）在切片 3/4 绿后安排人审；切片 5 仅补缺口用例。mock 纪律：只 mock Neo4j/LLM/Embedding/RabbitMQ/时间五个真缝，禁 mock 内部模块；Neo4j 不可用时切片 3 降级为 mock Driver 并在报告标注「未覆盖真图语义」。
