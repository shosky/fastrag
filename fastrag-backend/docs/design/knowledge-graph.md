# 知识图谱（knowledge-graph）— 架构设计

## 1. Module Map

| 模块 | Responsibility（一行） | Files | Depth Rationale |
|---|---|---|---|
| **ExtractionUnitModule**（抽取单元装配） | 把文件分片按序重组为语义完整的抽取单元，携带内容哈希与证据分片清单——单元化抽取（v1.2）的数据地基 | `fastrag-modules/fastrag-knowledge/.../chunking/ExtractionUnitAssembler.java` | 接口一个静态方法、吞掉「预算装箱 + 超长独占 + 空内容容忍 + 确定性哈希」规则；纯函数零依赖，是 v1.2 取代 carry/stitch/backfill 三层补丁的单点 |
| **GraphExtractionModule**（图谱构建消费者） | MQ 消息 → KB 互斥 → 单元级并行 LLM 抽取 → 质量防线（垃圾闸门/类型归一/退化重试/gleaning）→ 确定性写入与证据链 → 状态机/回填/清理 | `fastrag-modules/fastrag-knowledge/.../consumer/GraphBuildConsumer.java`（内嵌 prompt/gleaning/解析兜底）；质量工具 `fastrag-modules/fastrag-graph-eval/.../util/{EntityNameGuard,NameNormalizer,EntityTypeNormalizer,ExtractionNormalizer,GraphIdHashing}.java` | 消费者一个 `handleGraphBuild` 入口吞掉全部编排复杂度（三模式分流、构建内重试、进度、失败标记）；五个 util 各自吞一类规范化知识且纯函数可单测——LLM 输出的全部不可信性都终止在这一簇内 |
| **GraphManagementModule**（图谱领域服务） | 图谱查询/统计/构建触发/设置/文件清理/合并调用的领域门面：缓存计数优先+自动修复、孤立点过滤、删除后状态同步 | `fastrag-modules/fastrag-graph-eval/.../controller/GraphController.java`、`service/GraphService.java`、`service/impl/GraphServiceImpl.java` | 13 个接口方法聚在一个门面后，「缓存优先/实时回退/自动修复」「删除后 idle 归零」「合并后计数刷新」等一致性规则不再分散到调用方；HTTP 层是它的外部面（见 §2） |
| **GraphQueryServiceModule**（PPR 排序器） | 基于 Entity-Chunk 提及图的个性化 PageRank：提及数据→二分图→种子映射→迭代排序→Top-K 分片 | `fastrag-modules/fastrag-graph-eval/.../service/GraphQueryService.java` | 输入（实体名+topK）到输出（排序分片）之间隐藏 JGraphT 建图、双向边无向语义、收敛迭代（阻尼/容差/迭代上限）全部数值细节；KG-04 修复后种子语义正确 |
| **GraphStoreModule**（图存储，infra） | 图数据的唯一读写口：实体/关系幂等合并、查询/扩展/搜索、mention 证据链、删除回收、向量索引、合并执行、缝合原语 | `fastrag-infra/.../graph/GraphStore.java`（接口）、`fastrag-infra/.../neo4j/Neo4jGraphStore.java`（唯一实现，1220 行）、`fastrag-infra/.../graph/EntitySimilarity.java`（余弦工具） | 30+ 方法把 Neo4j Cypher（MERGE 语义、COUNT{} 度数、向量索引、改名三步法、pruneOrphans 三处共用）的全部方言细节折叠在接口之后；失败语义两分法（构建写入上抛/查询维护空结果）也是接口级约定 |
| **（休眠）StitchFramework**（缝合原语） | 跨分片补边的框架能力：冲突消解写边、按归一化名查实体、查分片成员、判已有边、缝合产出持久化 | GraphStore 4 个缝合方法 + `entity/KbGraphStitch.java` + mapper；表 kb_graph_stitch | v1.2 移除缝合 pass 后无现役调用方——原语保留为未来变体的接口面（见缝 3 说明与 ADR-1）；不单列模块图节点，寄生在 GraphStoreModule 内 |

辅助装配：`fastrag-common/handler/GraphBuildHandler.java`（common 侧接口，解耦 knowledge 消费者与 infra 发布者的编译依赖）；`fastrag-infra/.../neo4j/Neo4jConfig.java`（Driver Bean + 启动 fail-fast）；`fastrag-infra/.../rabbitmq/{RabbitMQConfig,MessagePublisher}.java`（graph-build 队列/绑定/发布含同步降级）。

## 2. Interface Contracts

**GraphManagementModule（公共接口 = GraphService + GraphController 暴露面）**
- `getGraphData(kbId, maxNodes, excludeChunks)` — 可视化子图（默认 500 节点、钳制 [1,5000]、孤立点过滤）〔GraphServiceImpl.java:65-93〕
- `getGraphStats(kbId)` — 缓存优先统计 + 实时回退自动修复 + 类型分布〔:147-196〕
- `getIndexStatus(kbId)` / `getBuildStatus(kbId)` — 构建状态面（无记录返回 idle 零值）〔:199-229,323-325〕
- `buildIndex(kbId, mode, fileIds)` — 置 building 后发构建消息（mode: full/incremental/replay）〔:232-272〕
- `retryBuild(kbId)` — 清图→重置分片标记→重置索引→full 重触发〔:275-320〕
- `getSettings/saveSettings(kbId, settings)` — 构建配置（缺省 maxNodes=500/searchDepth=2/excludeChunkNodes=true/entitySchema=""）〔:328-366〕
- `searchNodes(kbId, keyword, maxNodes)` / `getLabels(kbId)` — 子图搜索（缺省 50）/ 类型标签〔:371-378〕
- `deleteFileGraph(kbId, fileId)` — 删图 + 重置分片标记 + 索引缓存同步（空则 idle）〔:381-437〕
- `rankChunksByPpr(kbId, entities, topK)` — 委托 PPR 排序器〔:440-442〕
- `findMergeCandidates(kbId, threshold, limit)` / `mergeEntities(kbId, sourceId, targetId)` — 合并候选/执行 + 计数刷新〔:447-472〕
- HTTP 暴露面：GraphController 15 端点（data/stats/search/labels/index、index/build+retry+replay+build-status、file/{fileId} 删除、rank、settings 读写、merge-candidates、merge），基础路径 `/api/kb/{kbId}/graph`〔GraphController.java:46-176〕

**GraphExtractionModule（公共接口 = GraphBuildHandler.handleGraphBuild(Map)）**
- `handleGraphBuild(message)` — 唯一入口；消息键 `kbId`（必填）/`fileId`/`fileIds`/`mode`（full|incremental|replay，缺省 full）〔GraphBuildConsumer.java:183-216,219-241〕
- 包级静态测试面：`isJunkEntityName` / `shouldGlean` / `isDegenerateExtraction` / `extractionScore` / `buildExtractionPromptText` / `buildGleaningPrompt` / `mergeExtractionResults` / `groupChunksByFile`〔:138-140,827-906,976-1051,688-700〕

**ExtractionUnitModule（公共接口 = ExtractionUnitAssembler.assemble）**
- `assemble(orderedChunks, maxChars, hashFn) → List<ExtractionUnit{index, content, contentHash, chunks}>`〔ExtractionUnitAssembler.java:34-56,23〕

**GraphQueryServiceModule（公共接口 = GraphQueryService.rankChunksByPpr）**
- `rankChunksByPpr(kbId, entityNames, topK) → [{chunkId, score, entityCount}]`〔GraphQueryService.java:73-151〕

**GraphStoreModule（公共接口 = GraphStore，六族 30 方法）**
- 基础 CRUD：`createEntity`（双签名）/`createRelation`（双签名，端点按归一化名 MERGE + 占位节点）〔GraphStore.java:38-89〕
- 查询：`getGraphData`/`expandGraph`/`searchNodes`/`getLabels`/`countEntitiesByType`/`findEntityId`/`listEntityMentions`〔:101-155〕
- Mention 追踪：`createChunk`/`createEntityMention`/`createTripleMention`〔:167-189〕
- 管理：`clearGraph`/`deleteFileGraph`/`deleteChunkGraph`/`renameChunkId`/`cleanupOrphanNodes`〔:196-277〕
- 统计：`countEntities`/`countRelations`〔:231-239〕
- 向量：`updateEntityEmbedding`/`searchEntitiesByVector`/`listEntitiesWithoutEmbedding`〔:250-269〕
- 合并：`findMergeCandidates`/`mergeEntity`〔:292-302〕
- 缝合（休眠）：`findEntityByNormalizedName`/`findChunkMembers`/`hasRelationBetween`/`createDirectedRelation`〔:311-339〕
- Hidden implementation：全部 Cypher、类型计票合并、pruneOrphans 共用实现、向量索引懒创建、失败语义两分法〔Neo4jGraphStore.java 全文〕

**契约的共享工件落地**：数据实体（MySQL 4 表 + Neo4j 图谱本体 + 运行时结构）见 contract/data.md `## knowledge-graph` 节；深模块接口契约（GraphStore 六族 + GraphExtraction 编排）见 contract/interfaces.md `## knowledge-graph` 节。端点排除：GraphController 15 端点为 GraphManagementModule 外部面，不立契约。

## 3. Seam Diagram

**缝 1：图存储引擎（GraphStore）— 假设缝（单适配器）**
- What varies：图数据库引擎（图模型方言、幂等合并语义、向量索引能力）。
- Adapters：仅 `Neo4jGraphStore` 一个（ADR-0002 收敛后 MySQL 适配器已删除，`neo4j.enabled` 开关移除、启动 fail-fast）。**不立缝理由**：第二适配器消失后接口的抽象税仍在，但接口承担了第二职责——把「构建写入失败上抛 / 查询维护失败空结果」的失败语义与 mention/溯源/合并的语义契约固定下来，且缝合原语（REQ-E01~E03）与「更大规模用向量库近邻检索替换 O(n²) 两两比对」（EntitySimilarity.java:218-221 注释明示的演进方向）都是真实的未来变体落点，故保留接口不降为具体类。
- Stable side：GraphExtractionModule / GraphManagementModule / GraphQueryServiceModule（三者只 import 接口）。
- Placement：infra 层（宪法：存储抽象归 infra；AI/infra 不查业务库）。

**缝 2：抽取 LLM 网关**
- What varies：模型 code/apiUrl/apiKey 按文件策略 → KB 级 → 无 的解析结果；超时/输出上限/思考模式参数。
- Adapters：`LlmService.chatWithTimeout`（Ollama 兼容网关，多模型动态路由）；replay 模式是「零适配器」分支（不触网关）。
- Stable side：GraphExtractionModule 的抽取/退化重试/gleaning 编排。
- Placement：模型表查询在 caller 侧（GraphBuildConsumer 持 ModelRecordMapper，符合宪法），网关调用在 fastrag-ai。

**缝 3：Embedding 网关**
- What varies：KB 级 embedding 模型（bge-m3/text-embedding-v4 等，维度 1024）。
- Adapters：`EmbeddingService.embed`（含瞬态 3 次重试，infra-ai 共享层）；未配置模型 = 降级分支（REQ-F04）。
- Stable side：构建写入与回填流程；`graph.entity-embedding-dimension` 配置面。
- Placement：模型解析在 consumer、调用在 fastrag-ai、维度守卫在 Neo4jGraphStore。

**缝 4：异步执行边界（MQ ↔ 进程内）**
- What varies：RabbitMQ 可用性；消费者线程 vs 应用内 graph-build-worker 线程的分工。
- Adapters：① `MessagePublisher.publishGraphBuild` 的同步降级（MQ 不可用直调 handleGraphBuild，MessagePublisher.java:106-125）；② 消费侧「即时 ack + buildExecutor 提交 + KB 互斥」（GraphBuildConsumer.java:183-216）。
- Stable side：runGraphBuild 编排（对消息来自队列还是同步降级无感知）。
- Placement：发布在 infra、消费在 knowledge、接口解耦在 common（GraphBuildHandler）——防止 modules→modules 与 modules→infra 的编译耦合。

**缝 5（假设缝）：抽取单元预算**
- What varies：单元字符预算（`graph.build.unit-max-chars`，默认 2000）与内容哈希函数（注入 GraphIdHashing::hashstr32）。
- Adapters：仅一组现值（2000 字符 + SHA-256 截断 32）。不立缝理由：预算是配置不是行为变体；哈希函数参数化仅为可测试性（测试注入确定性哈希），无第二生产适配器预期。
- Stable side：GraphBuildConsumer（组装结果只依赖 ExtractionUnit record 形状）。

**ADR 符合性**：ADR-0002（图存储收敛 Neo4j、唯一实现、启动 fail-fast）——缝 1 现状即其落地形态，符合；无冲突。ADR-0001（绑定变更必须重切）——本特性是下游受益者（分片重建后 graph_indexed=0/extraction_result=null → 增量构建自然重抽，chunk-management 链已登记），无需本链裁决。

## 4. Deepening Opportunities

- **GraphExtractionModule**：`resolveLlmConfig` 的「全库重建回退任意文件」对多文件多策略库会抽取到与内容无关的模型配置（文件级 LLM 只取 fileIds[0]，GraphBuildConsumer.java:1076）——按文件分组解析 LLM 配置可吞掉这个近似；`updateGraphStatus/updateGraphProgress` 双 UPSERT 可合并为一个状态机方法。
- **GraphStoreModule**：`mergeEntity` 第 5 步删除 `EntityMention` 节点（Neo4jGraphStore.java:1058-1059），但该 label 在 Neo4j 实现中从未创建（mention 走 MENTIONS 边）——删除死语句；`getGraphData` 的 excludeChunks=false 分支返回的 chunk 节点不带 MENTIONS 过滤，可与孤立点过滤统一。
- **GraphManagementModule**：`buildIndex` 的 mode 参数从 controller body 直传（"full"/"replay" 字符串约定）——可收敛为枚举 + 显式 replayBuilder 方法，消除魔法串。
- **StitchFramework**：若合并候选规模超过 MAX_ENTITIES=2000，按 EntitySimilarity 注释演进为 `searchEntitiesByVector` 近邻检索路径（接口已备）。
- 其余模块：None（ExtractionUnitModule/GraphQueryServiceModule 已收敛）。

## 5. Parent Trace

| Design 模块/缝 | Spec REQ |
|---|---|
| ExtractionUnitModule | REQ-A01、A02；缝 5 |
| GraphExtractionModule（prompt/质量防线） | REQ-A03~A07 |
| GraphExtractionModule（编排/状态/回填/清理） | REQ-B01~B05、B08；缝 2/3/4 |
| GraphIdHashing + createEntity/createRelation 合并语义 | REQ-B06、B07 |
| GraphManagementModule（查询/统计/状态/重试/删除） | REQ-C01~C05、D03、D04（索引缓存同步半边） |
| GraphQueryServiceModule | REQ-C06 |
| GraphStoreModule（合并/孤儿回收） | REQ-D01、D02、D04、D05（审计缺口登记） |
| StitchFramework（休眠原语） | REQ-E01~E03 |
| GraphStoreModule + GraphExtractionModule（向量族） | REQ-F01~F04；缝 3 |
| Neo4jConfig fail-fast | §5 非功能（Neo4j 必需依赖） |
| ExtractionUnitModule | REQ-A02（2026-09-24 review 补锚：内容过短（<50 字符）的单元不调用 LLM，直接记为已完成的空抽取） |
| GraphManagementModule（查询/统计/状态/重试/删除） | REQ-C03（2026-09-24 review 补锚：关键词子图搜索多字段模糊匹配（名称/归一化名/类型/描述）返回命中节点及内部边；实体类型标签去重排序返回） |
| StitchFramework（休眠原语） | REQ-E02（2026-09-24 review 补锚：缝合决策查询原语可用——按归一化名查实体上下文、查分片提及实体、判两实体间已有边） |

## 6. ADR

**ADR-KG1（本链登记）：v1.2 单元化抽取取代跨分片补丁层（carry/stitch/backfill）**——背景：500 字检索分片逐片抽 LLM 导致跨分片关系（如"营销六步法"标题+六个步骤）必然漏抽，v1.1 以链式携带、文件级缝合、headingPath 框架补边三层补丁缓解，复杂度高且互相踩踏；决策：把抽取单元重组为 ~2000 字符（LightRAG 1200 token 同款），跨分片关系在单元内直接抽出，三层补丁整体移除（GraphBuildConsumer.java:49-64 决议记录；其引用的上位设计文档 cross-chunk-graph-extraction.md 已随根 docs/ 删除，仅存代码内注释与 kb_graph_stitch 遗表）；代价：接受「跨单元（>2000 字符）关系仍靠归一化名合并兜底、缝合能力降级为休眠框架（REQ-E03 无写入方）」；回滚：缝合原语与 kb_graph_stitch 表已保留，重建缝合 pass 不需要新存储。符合「难逆转+费解+真权衡」三要素，故记此 ADR；ADR-0002（Neo4j 收敛）为既有决议，本链全部设计在其约束内，无新决议。

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
