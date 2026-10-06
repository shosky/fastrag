# 知识图谱（knowledge-graph）— 实现文档

## 1. Code File Map

```
fastrag-backend/
├── fastrag-infra/src/main/java/com/fastrag/infra/
│   ├── graph/GraphStore.java                  图存储接口（六族 30 方法，失败语义两分法约定）
│   ├── graph/EntitySimilarity.java            实体名向量两两余弦工具（MAX_ENTITIES=2000 防御）
│   ├── neo4j/Neo4jConfig.java                 Driver Bean + 启动 verifyConnectivity fail-fast（ADR-0002）
│   ├── neo4j/Neo4jGraphStore.java             唯一实现：全部 Cypher/MERGE 语义/pruneOrphans/向量索引/合并
│   └── rabbitmq/{RabbitMQConfig,MessagePublisher}.java   graph-build 队列/绑定/发布（MQ 不可用同步降级）
├── fastrag-common/src/main/java/com/fastrag/common/handler/GraphBuildHandler.java
│                                              消费接口（common 侧，解耦 infra 发布者与 knowledge 消费者）
├── fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/
│   ├── consumer/GraphBuildConsumer.java       构建编排（MQ 入口/互斥/三模式/单元并行/质量防线/收尾）
│   ├── chunking/ExtractionUnitAssembler.java  抽取单元组装（纯函数，v1.2 核心）
│   └── consumer/IngestionConsumer.java:495-510  摄取完成触发（fileId+mode=incremental，归 file-ingestion 链）
├── fastrag-modules/fastrag-graph-eval/src/main/java/com/fastrag/module/graph/
│   ├── controller/GraphController.java        15 端点外部面（无 @KbAuth，见 §2 偏离）
│   ├── service/GraphService.java + impl/GraphServiceImpl.java   领域门面（统计缓存修复/孤立点过滤/删除同步）
│   ├── service/GraphQueryService.java         PPR 排序器（JGraphT + 手动迭代）
│   ├── entity/{KbGraphIndex,KbGraphUnit,KbGraphStitch,KbGraphMergeAudit}.java   MySQL 4 表实体
│   ├── entity/{KbGraphEntity,KbGraphRelation,KbGraphEntityMention,KbGraphTripleMention}.java
│   │                                          MySQL 图表遗留实体（表已删，死代码）
│   ├── mapper/                                8 个 mapper 全部零消费方（除 Index 外）
│   └── util/{EntityNameGuard,NameNormalizer,EntityTypeNormalizer,ExtractionNormalizer,GraphIdHashing}.java
│                                              质量防线纯函数五件套
└── fastrag-bootstrap/.../SchemaInitializer.java:302-370   kb_graph_* 增量 DDL（settings 列漂移，见偏离）
```

## 2. Interface Implementations

### GraphStore（from contract: knowledge-graph.GraphStore）

**Logic Flow:**（Neo4jGraphStore）
1. `createEntity`：① 空名直接返回；② 归一化名回退（入参空则 name 小写）；③ 读旧值（entityType/description/typeCounts）；④ 类型合并——存量类型非占位且无计票历史时记 1 票，新类型非占位 +1 票，占位类型（UNKNOWN/Entity/null）无投票权，oldType 为占位则取最高票否则挑战者票数**严格大于**才换（平票保现状，确定性）；⑤ 描述非空择长（等长保现状）；⑥ `MERGE (kbId, normalizedName)` ON CREATE 写 name/createdAt、恒写 entityId/mergedType/mergedDescription/typeCounts、attributes 用 coalesce 首写保留。
2. `createRelation`：① 空/空白端点返回；② 归一化名回退小写；③ 端点按 (kbId, normalizedName) MERGE（不存在则创建占位节点：只写 name/entityId，不设 entityType=无投票权）；④ `MERGE (s)-[r:RELATION {kbId,label}]->(t)`，ON CREATE 写 tripleId/端点 ID/content，ON MATCH 只 coalesce 补空不清既有值。
3. 查询族：getGraphData=实体按 `COUNT{}` 度数降序+名称稳定排序取 maxNodes → 按 nodeNames 拉边（LIMIT maxNodes×3）→ excludeChunks=false 时附加 Chunk 节点与 MENTIONS 边；expandGraph=实体名互为子串模糊命中（LIMIT maxEntities×6、去重、兜底重排）→ 1 跳直查 / 2 跳 `*1..2` UNWIND 后按 kbId 过滤（Neo4j 禁止属性映射与变长组合）→ 邻居按 mentionCount 加权 → expandedQuery 空格拼接；searchNodes=四字段 `=~ "(?i).*kw.*"` 正则。全部 catch → 空结果 + error 记账。
4. Mention 追踪：createChunk 按 (kbId, chunkId) MERGE（content ≤200 截断，fileId coalesce）；createEntityMention **按 entityId MATCH 实体**（2026-09-07 修复注释：按 name 会因展示名首写化而丢边→实体被误删）；createTripleMention 独立 `(:TripleMention)` 节点 MERGE（RELATION 是 relationship 不能有出边）。
5. 删除族：deleteFileGraph=查文件 chunkIds → DETACH DELETE Chunk → 删 TripleMention(chunkId IN ids) → pruneOrphans；deleteChunkGraph 同口径单分片；pruneOrphans 三步：回填边空端点 ID → 删无 TripleMention 的 RELATION（计数入日志）→ DETACH DELETE 无 MENTIONS 的 Entity；renameChunkId=CREATE 新节点 + FOREACH 迁 MENTIONS + 改 TripleMention.chunkId + DETACH DELETE 旧节点（Neo4j 不能改 MATCH 键属性）。
6. 向量族：ensureVectorIndex 懒初始化（CREATE VECTOR INDEX IF NOT EXISTS，失败置 vectorIndexReady=false 降级）；updateEntityEmbedding 维度≠配置跳过；searchEntitiesByVector 索引未就绪返回空、否则 topK×5 放大取数按 kbId 过滤截断。
7. 合并：mergeEntity=双方存在性校验（缺→IllegalArgumentException）→ 出边/入边 MERGE 迁移（o=target 排除=自环丢弃）→ 残余自环删除 → MENTIONS MERGE 迁移 → 删 source 的 EntityMention 节点（死语句，见偏离）→ 描述择长 + typeCounts 累加 → DETACH DELETE source；其他异常包 IllegalStateException 上抛。
8. 缝合：createDirectedRelation=先查同 label 反向边（存在→证据并入既有边 TripleMention、返回既有 tripleId；否则建边 + addTripleMentions 逐条 MERGE、空 chunkId 过滤）。

**Module Interactions:**
- Calls：Neo4j Java Driver（唯一外部依赖）；hutool JSONUtil（typeCounts/attributes 序列化）。
- Called by：GraphBuildConsumer（写入+回填+清理）、GraphServiceImpl（查询/统计/删除/合并）、GraphQueryService（listEntityMentions/findEntityId）、retrieval 链 QueryEnhanceServiceImpl（expandGraph）、chunk-management 链（deleteChunkGraph/renameChunkId，经上游契约）。
- Shared state：Neo4j 库（Entity/Chunk/TripleMention 节点、RELATION/MENTIONS 边、entity_embedding 向量索引）。

**Decisions & Deviations:**
- 合并语义与契约一致，无偏离；**登记债务 3 项**：① mergeEntity 第 5 步删除 `(:EntityMention)` 节点，但该 label 从未被创建（mention 走 MENTIONS 边），空跑 Cypher〔Neo4jGraphStore.java:1057-1059〕；② GraphQueryService 类内 javadoc 自相矛盾——:16-17 旧注释称「Neo4j 模式返回空列表」，:38-40 与实现（经 listEntityMentions 读 MENTIONS 边）为存储无关，前者过期；③ mergeEntity 不写 kb_graph_merge_audit（见下）。
- EntitySimilarity 放 infra 层供 Neo4jGraphStore 直用（注释明示 O(n²) 边界与向量库近邻检索演进方向），但其测试落在 graph-eval 模块（镜像包结构按「被测类属主模块」惯例偏离）。

**Parent Trace:** [Neo4jGraphStore/EntitySimilarity] → [GraphStore] → [GraphStoreModule（缝 1）+ StitchFramework（缝 3 休眠）] → [REQ-B06、B07、D01、D02、D04、E01~E03、F02、F03]

### GraphExtractionPipeline（from contract: knowledge-graph.GraphExtractionPipeline）

**Logic Flow:**（GraphBuildConsumer）
1. `handleGraphBuild`：① kbId 缺失→warn 丢弃；② `runningKbs.add(kbId)` 失败→warn 跳过（E_DUPLICATE_BUILD）；③ 提交 buildExecutor（拒绝→释放互斥位跳过）；任务体 runGraphBuild，finally 移除互斥位；异常不向 MQ 抛（消息已即时 ack），error 记账 + 失败日志。
2. `runGraphBuild`：① 解析 fileId/fileIds/mode（replayMode="replay"、ignoreCache="full"）；② 开始日志；③ replay 跳过 LLM 解析，否则 `resolveLlmConfig`；④ 非 replay 且 apiUrl 空→置 failed+buildError+失败日志后 return（E_LLM_NOT_CONFIGURED）；⑤ queryChunks 拉范围内全部分片（空→completed 空态返回）；⑥ 按文件分组、组内 (chunkIndex, id) 排序 → ExtractionUnitAssembler.assemble 每文件产单元（预算 unit-max-chars、哈希 GraphIdHashing::hashstr32）；⑦ 线程池 min(concurrency, max(1, units/2)) 并行 processUnit（首尝试=true）；⑧ 失败清单构建内重试一轮（firstAttempt=false，不计重复失败）；⑨ live 计数 + graph_indexed=1 计数 → updateGraphStatus(completed)；⑩ backfillEntityEmbeddings → cleanupOrphanNodes → 完成日志 → UNKNOWN 占比观测；任何异常→failed+日志。
3. `processUnit`：① 内容 <50 字符→空结果落行+分片置 1 返回；② replay：行有持久化结果→反序列化；无→分片置 2+行失败+计失败（E_REPLAY_RESULT_MISSING）；③ 非 replay 且缓存四条件命中（行在+status=1+hash 同+结果非空可反序列化）→cachedUnits+1；④ 未命中→extractWithNormalization（null=失败→分片置 2+行失败+首尝试计失败入清单）；⑤ 成功→upsertUnitRow(结果, 1)；⑥ writeUnitGraph → markUnitChunks(1)；任何异常→分片置 2+行失败+首尝试计失败；finally 每 5 单元 updateGraphProgress（不覆盖计数）。
4. `writeUnitGraph`：实体循环（类型归一→垃圾闸门跳过计 valueSkipped→确定性 ID→createEntity→attributeEntityMentions 名字包含匹配+首分片兜底→登记名称映射与 embed 候选）；关系循环（端点空/垃圾跳过→归一化名自环跳过（KG-05）→tripleId 哈希→resolveRelationEndpointId（单元映射→图库反查→UNKNOWN 占位登记+证据）→createRelation→attributeTripleMention（两端都被包含））；Chunk 节点逐个 createChunk；updateEntityEmbeddings。
5. `extractWithNormalization`：调 LLM（关闭 thinking、超时 180s、maxTokens 4096）→ 空响应 null → parseLlmExtractionJson（剥 markdown 围栏→截取首 { →JSONUtil 解析失败走正则兜底）→ ExtractionNormalizer.normalize → 退化判定（有关系全缺端点）→ 重试一次按 extractionScore 择优 → gleaning（有实体零关系且 text+首答 ≤16000 字符）→ mergeExtractionResults 再 normalize。
6. `resolveLlmConfig`：fileIds 空→取该库任意未删文件（无→空配置）；文件不存在→空配置；file.parseStrategyId 有策略且 llm_model 有值→查 model 表（code+status=online LIMIT 1）命中即返回；否则 KB 级 graphLlmModel 同法；都不中→空配置（触发 E_LLM_NOT_CONFIGURED）。

**Module Interactions:**
- Calls：KbChunkMapper/KbFileMapper/KbParseStrategyMapper/KnowledgeBaseMapper（业务库读）、ModelRecordMapper（模型路由）、GraphStore（写入/清理/统计）、LlmService、EmbeddingService、KbGraphIndexMapper/KbGraphUnitMapper、LogService、MessagePublisher 间接（发布侧）。
- Called by：RabbitMQ 监听（fastrag.graph-build.queue）、MessagePublisher 同步降级、GraphServiceImpl 间接触发（发消息）。
- Shared state：MySQL（kb_graph_unit/kb_graph_index/kb_chunk.graph_indexed）、Neo4j（经 GraphStore）、进程内 runningKbs 互斥集与 buildExecutor 线程池。

**Decisions & Deviations:**
- 消息即时 ack + 应用内执行器 + KB 互斥是 2026-09-11 生产事故（consumer_timeout 重投→并发构建互相踩踏→计数塌陷）的修复，与契约 Invariants 一致，无偏离。
- **偏离/欠账 4 项**：① mergeEntities 链路不写 kb_graph_merge_audit（REQ-D05 契约先行、实现欠账，tdd 切片 1 为验收入口）；② `resolveLlmConfig` 全库重建（无 fileIds）只取任一文件解析策略，多策略库会以单一模型抽取全部文件（近似，注释自认 fallback）；③ 抽取 LLM 路由只看 fileIds[0] 的文件策略——fileIds 多文件且策略不同时后文件沿用首文件模型（近似）；④ controller 的 buildIndex body.mode 直传字符串（"full"/"replay"），魔法串无枚举校验（错误值按缺省 incremental 语义落入缓存路径——GraphServiceImpl.java:265 缺省 "full" 兜底了手动入口，但 MQ 侧缺省为 "incremental"，两入口缺省不一致属设计内但易混淆）。
- 垃圾闸门 `isTableDominant`（表格主导判定）与 `VALUE_TYPE_EXCLUDE`（值型类型提示排除）为 prompt 层与代码层双防线，规则与 EntityNameGuardTest 用例一一对应。

**Parent Trace:** [GraphBuildConsumer + ExtractionUnitAssembler + util 五件套] → [GraphExtractionPipeline] → [GraphExtractionModule + ExtractionUnitModule（缝 2/3/4/5）] → [REQ-A01~A07、B01~B08、F01、F02、F04]

### PprRanker（from contract: knowledge-graph.PprRanker）

**Logic Flow:**（GraphQueryService）
1. entityNames 空→空列表；2. listEntityMentions 全量拉取（异常→空列表），构建 JGraphT 有向图（节点前缀 E:/C:，每条提及加正反双边表达无向）；3. 种子：每名 normalize→findEntityId→图中存在才入种子集；空→空列表；4. personalizedPageRank：个性化向量=种子均匀 1/|seeds|、初始分=个性化向量、迭代 `s'=(1-d)p+d·Σs(u)/outdeg(u)`（d=0.85）至 L1<1e-8 或 100 次；5. Chunk 节点按分数降序取 topK，entityCount=degreeOf/2。

**Module Interactions:** Calls：GraphStore（listEntityMentions/findEntityId）、NameNormalizer；Called by：GraphServiceImpl.rankChunksByPpr（HTTP /graph/rank）、retrieval 链 graph 通道消费口径经 GraphStore.expandGraph 而非本方法‹CERTAIN（2026-09-24 深证：GraphController.java:133-139 为 rankChunksByPpr 后端唯一调用方；retrieval 链经 QueryEnhanceServiceImpl.java:342、RetrievalServiceImpl.java:723 消费 expandGraph；前端仅 fastrag-frontend/src/api/index.ts:623-628 有封装、全前端零调用）›——PPR 端点当前为独立消费面，前端/检索链均未接线（是否接线归 retrieval 链决策，见检索链契约）。Shared state：无（每次调用重建图）。

**Decisions & Deviations:** 实现与契约一致（KG-04 修复后种子真实生效）；无偏离。JGraphT 双向边使度数翻倍，entityCount=/2 修正——契约已声明。

**Parent Trace:** [GraphQueryService] → [PprRanker] → [GraphQueryServiceModule] → [REQ-C06]

### GraphManagementModule（门面，无独立契约——偏离登记附此）

GraphServiceImpl 各方法与契约对齐（统计缓存优先/自动修复、删除后 idle 归零、合并后计数刷新失败不回滚）。**登记债务 2 项**：① GraphController 全部 15 端点**无 @KbAuth/@PreAuthorize**（类与方法级均无，ChunkController 对比实证）——图谱数据与构建触发仅需登录（SecurityGate），未做 KB 级 ACL 判定，跨库操作面依赖 kbId 不可猜测性，属安全债务〔GraphController.java:45-176 ‹CERTAIN›〕；② kb_graph_index.settings 列仅存于 schema.sql ALTER（:1423），SchemaInitializer 不补（仅补 failed_chunks，:303）——纯增量升级库会缺列致 saveSettings 失败（DDL 双处漂移，conventions.md §4 禁止项）。另：last_built_at 全链无写入方（仅删除时置 null），字段预留。

**Parent Trace:** [GraphController + GraphServiceImpl] → [无独立契约（端点排除），门面对齐 GraphStore/PprRanker 契约] → [GraphManagementModule] → [REQ-C01~C05、D03、D04]

## 3. Testing

引用 tdd/knowledge-graph.md 七切片。**现有覆盖**（开发期沉淀，红绿循环未按本设计执行）：切片 5 全部——GraphBuildConsumerUnitTest 8 用例（单元组装 5 + 退化判定 2 + 评分 1）、GraphBuildConsumerPromptTest 8 用例（prompt 契约 6 + gleaning 2）、util 五测试类（EntityNameGuard 7 / NameNormalizer 6 / EntityTypeNormalizer 5 / ExtractionNormalizer 5 / EntitySimilarity 5，BenchmarkResponseParserTest 6 归 benchmark 链）；切片 7 部分——GraphServiceImplOrphanFilterTest 3 用例（孤立点剔除/存量名称兼容/空边清空）。**缺口**：切片 3（GraphStore 真图语义——合并幂等、孤儿回收、缝合幂等、MENTIONS 实体 ID 匹配全部无测试）、切片 4（编排——互斥/三模式/缓存命中/replay 确定性/LLM 路由无测试）、切片 6（PPR 无测试）、切片 7 其余（统计修复/重试顺序/删除归 idle/设置回退）、切片 1/2（HITL 未组织）。执行起步点与 mock 纪律见 tdd §4。
