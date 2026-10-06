# 分片管理（chunk-management）— 架构设计

> 对应 Spec：`spec/chunk-management.md`（REQ-A01~G05）；契约：`contract/interfaces.md` / `contract/data.md` 的 `## chunk-management` 节。
> 上游 ADR 引用：**ADR-0001**（策略绑定变更必须触发重新分片，不允许只改绑定不重切的漂移态）——本设计的 RechunkOrchestrator 是其物理执行面，绑定裁决归 parse-strategy.ParseStrategyBindingGuard（contract 已登记），两模块以「裁决/执行」分工协作。**ADR-0002**（图存储收敛 Neo4j 唯一实现，启动 fail-fast）——本设计的图谱清理/改名调用直接依赖 GraphStore 接口，无第二实现、无降级开关。原 `docs/legacy/adr/` 归档文件已丢失，仅引要点编号。

## 1. Module Map

| 模块 | Responsibility（一行） | Files | Depth Rationale |
|---|---|---|---|
| **ChunkAlgorithmModule** | 五族分片算法（规则/递归/语义/时间分段/结构感知）的唯一实现地，吞掉全部「怎么切」的复杂度：标题栈、延迟合并、不可切分单元、overlap、降级回退 | `chunking/ChunkingService.java`、`chunking/ChunkingServiceImpl.java` | **深**：5 个公开方法背后是 ~1300 行切分细节（标题 pending 合并、围栏状态、纯表格豁免、余弦断点），调用方（IngestionConsumer）只见「文本进、ChunkData 列表出」 |
| **ParentChunkAssembler** | 把子分片按标题层级聚合为父分片并回填 parentId，父子语义的唯一裁决点 | `chunking/ParentChunkAssembler.java` | **深（候选 1）**：输入 chunks+maxParentLength+aggLevel，输出 parents+children 的纯函数；分组键、层级截断、二次切分、ID/index 规则全部隐藏，测试无需任何 mock |
| **ChunkStoreModule** | 摄取管线的分片落库定音锤：批量 embedding（重试）、父子装配触发、BATCH 批插 MySQL、批量写 Milvus、chunkCount 记账 | `storage/StorageService.java`、`storage/StorageServiceImpl.java` | **深**：storeChunks 一方法吞掉「分片→三处存储」的全部编排（分批/错位防护/父分片免向量/元数据冗余回填），上游契约节已声明 kb_chunk 数据契约归本链 |
| **ChunkLifecycleModule** | 用户手工操作分片（查/建/改/删/批删）的唯一入口，吞掉三库（MySQL/Milvus/图谱）同步与索引位移的连锁复杂度 | `service/ChunkService.java`、`service/impl/ChunkServiceImpl.java`、`controller/ChunkController.java`、`model/ChunkDto.java`、`model/ChunkCreateRequest.java`、`mapper/KbChunkMapper.java` | **深**：一次 delete 背后是级联子分片→向量删除→图谱 mention 回收→索引前移→auto 分片 ID 重建→向量重嵌入→图谱改名的完整连锁；HTTP 层（ChunkController 7 端点）是其薄包装 |
| **RechunkOrchestrator** | 重新分片的物理编排：状态守卫→删 auto 留 manual→清向量/图谱→重置文件→重发摄取→完成后 manual 对账 | `service/impl/FileServiceImpl.java`（reChunkFile/rebuildProcessRequest 段）、`consumer/IngestionConsumer.java`（reconcileManualChunks 段） | **深（候选 2）**：ADR-0001 的执行面；「保留什么、清理什么、何时对账」的重切语义集中于此；但编排体分散在 FileServiceImpl 与 IngestionConsumer 两处（见 §4 深化机会） |
| ChunkQueryModeDetector **(S)** | list 的父子/单层模式探测（parentCount>0 判别 + 孤立子分片混排 + tokenTotal 聚合） | ChunkServiceImpl.java:46-100 | 浅：一次 count + 两条查询的读取面，无隐藏状态；建议维持现状不独立成类，若父子展示需求增长（如折叠/展开分页）再深化 |
| （共享边界，非本特性模块）MilvusService / EmbeddingService / GraphStore / MessagePublisher | 向量库、向量化、图存储（Neo4j 唯一实现）、MQ 发布的 infra/AI 共享层 | `fastrag-infra/.../MilvusService.java` 等 | 按宪法归共享层；本链是消费方，接口面见 infra，不在此重复定义 |

## 2. Interface Contracts

### ChunkAlgorithmModule

**Public interface（ChunkingService，ChunkingService.java:27-90）：**
- `chunk(text, strategyId)` — 策略驱动的规则分块（分隔符+长度+overlap，PDF 页感知、图片占位符提取）
- `chunk(text, ParseStrategyConfig)` — 同上但接受未落库的临时配置（预览场景）
- `recursiveChunk(text, strategyId)` — 递归字符切分（分隔符优先级降级→句子边界→硬切）
- `semanticChunk(text, strategyId, kbEmbeddingModel)` — 语义切片（相邻句相似度断点，三重回退规则分片）
- `chunkBySegments(segments, strategyId)` — 音视频 ASR 时间分段（按文本长度比例分配时间）
- `structuralChunk(nodes, strategyId)` — 结构感知分片（标题栈/不可切分单元/tableMode）

**Hidden implementation：** 标题 pending 延迟合并、代码围栏状态机、headingPath 前缀注入、forceSplit 的 overlap 死循环防护、纯表格文档判定、语义模型路由（purpose=EMBEDDING 校验+KB 兜底）、余弦相似度。

**Depth rationale：** 调用方（IngestionConsumer 分发处）每个算法一行调用；换任何切分细节（如改句子边界集）零调用方感知。

### ParentChunkAssembler

**Public interface（ParentChunkAssembler.java:46-56）：**
- `assemble(chunks, fileId, maxParentLength) → AssemblyResult{parents, children}` — 按完整 headingPath 聚合
- `assemble(chunks, fileId, maxParentLength, aggLevel) → AssemblyResult` — 支持按 H1/H2/H3 截断层级分组

**Hidden implementation：** 连续同键分组算法、parseAggLevel 解析、组超长按子分片边界二次切分、父分片 ID/序号/索引规则、子分片 parentId 回填（原地变异输入列表）。

**Depth rationale：** 纯函数、零依赖；「父子是什么」的全部规则（含「聚合必须在存储层做」的图片分片归组约束）藏在 46-166 行内。

### ChunkStoreModule

**Public interface（StorageService，StorageServiceImpl 实现签名 :50-65,178）：**
- `storeChunks(kbId, fileId, chunks)` — 单层模式落库（embedding→MySQL 批插→Milvus 批写→chunkCount）
- `storeChunks(kbId, fileId, chunks, childChunkLength)` / `storeChunks(kbId, fileId, chunks, childChunkLength, maxParentLength, parentAggLevel)` — childChunkLength>0 时启用父子装配后落库
- `storeChunkBatch(kbId, fileId, chunks, batchStart)` — 流式分批落库变体

**Hidden implementation：** Milvus collection 幂等创建、模型 apiUrl/apiKey 解析、32 片分批 embedding+3 次指数退避、no-vector 错位防护、BATCH Executor 事务自管（禁 @Transactional 混用）、origin=auto/parent 的双路径批插、region/publish_date/doc_level 冗余回填、embedding 输入拼接（content+headingPath+fileName）。

**Depth rationale：** IngestionConsumer 只在 :450-454 两行调用；「分片落库要同时正确多少件事」完全在此内聚。

### ChunkLifecycleModule

**Public interface（ChunkService，ChunkService.java:10-31）：**
- `list(kbId, fileId, page, pageSize)` — 父子/单层双模式分页（含 parentCount/tokenTotal 聚合）
- `getCount(kbId)` — 非父分片总数
- `getById(kbId, id)` — 详情（归属校验）
- `create(kbId, req)` — 手工创建（manual ID 段+向量+图谱联动）
- `update(kbId, id, fields)` — 部分编辑（内容变更→重嵌入+父分片刷新+图谱重建）
- `delete(kbId, id)` / `batchDelete(kbId, ids)` — 删除（级联+索引位移+三库清理）

**Hidden implementation：** 父子级联收集、shiftChunkIndices（manual 保 ID 仅移位 / auto 删行重插+向量重建+图谱 renameChunkId）、embedding 模型路由（KB 级+模型表 online 记录）、refreshParentContent 重拼、增量图谱构建触发（enableGraphBuild 门控）、文件 chunkCount 对账。

**Depth rationale：** ChunkController 7 端点每行一句委托；「删一个分片牵动三处存储与文件计数」的连锁是典型应隐藏的复杂度。

### RechunkOrchestrator

**Public interface（语义面，实现分布见 Files）：**
- `reChunkFile(kbId, fileId, strategyId三态, presetStrategy) → FileDto` — 重切入口（FileServiceImpl.java:364-482；绑定裁决委托 parse-strategy 守卫语义）
- `reconcileManualChunks(kbId, fileId)` — 管线完成后的 manual 对账（IngestionConsumer.java:753-782，包内私有，经 storeChunks→reconcile 调用序隐式契约）

**Hidden implementation：** 三态换绑校验、预设策略解析、auto/manual 分类删除、embeddingId 选择性向量清理、图谱文件级清理+manual graphIndexed 重置、处理配置快照重建、manual 索引重排与 chunkCount 修正。

**Depth rationale：** 上游只需表达「这个文件按新绑定重切」；「保留 manual、其余三库清理、对账重排」的 ADR-0001 执行细节不外泄。

## 3. Seam Diagram

**缝 1：策略配置缝（StrategyConfigResolver）**
- What varies：分片参数的来源与格式（策略 advanced 新分组 JSON / 旧平铺字段 / SysConfig 系统默认 / 代码内置默认，四层合并）
- Adapters（4，真缝）：新分组结构解析、旧平铺回退映射、SysConfig 读取、代码兜底值
- Stable side：ChunkAlgorithmModule（只认类型化 ParseStrategyConfig）
- Placement rationale：格式兼容是 parse-strategy 历史包袱，分片算法不应感知（契约见 `## parse-strategy` 节 StrategyConfigResolver）

**缝 2：分片算法分发缝（chunk.strategy → 算法族）**
- What varies：切分算法（五族，未来可增）
- Adapters（5，真缝）：chunkBySegments / parent_child→structuralChunk / recursiveChunk / semanticChunk / structure_aware→structuralChunk（默认 rule_fixed 双分支兜底）
- Stable side：ChunkStoreModule 与 IngestionConsumer 主流程（拿到 List&lt;ChunkData&gt; 后一律同构处理）
- Placement rationale：分发 switch 当前在 IngestionConsumer.java:184-236——落点偏浅（见 §4），但缝本身稳定：新增算法只增一个分支

**缝 3：embedding 模型路由缝**
- What varies：用哪个模型、经哪个网关端点（策略级 embeddingModel / KB 级兜底 / model 表 apiUrl+apiKey / 默认网关）
- Adapters（3+，真缝）：模型表在线记录路由、KB 级回退、无记录默认网关
- Stable side：算法（语义切片）与存储（向量化）只调 `embeddingService.embed(model, texts, apiUrl, apiKey)`
- Placement rationale：模型动态路由是平台级机制（model 表），fastrag-ai 不查业务库（宪法），caller 侧解析后传参

**假设缝（不立缝）：** Milvus——MilvusService 为 infra 具体类、当前唯一向量实现（CONTEXT.md Vector Store 词条），无第二实现故非真缝；Neo4j GraphStore——ADR-0002 明令唯一实现、启动 fail-fast，直接依赖不设缝。

## 4. Deepening Opportunities

- **ChunkLifecycleModule**：① 错误语义收敛——RuntimeException→BusinessException（404/400），消除 NFR-5 债务；② update 的裸 `Map<String,Object>` 收敛为类型化请求对象（现状 String/Double/Integer 强转 :216-218 靠调用方自觉）；③ shiftChunkIndices 的「删行重插+向量重建+图谱改名」整体源于「ID 内嵌 chunkIndex」——若 ID 稳定（不再内嵌索引）可删除该连锁（见 §6 ADR-CM1 的长期方向）；④ 三处外部依赖的 try-warn 样板可收敛为 `SyncStep` 辅助（记账+继续）。
- **ChunkStoreModule**：把 manual 对账（reconcileManualChunks 现散在 IngestionConsumer :753-782）下推进 storeChunks 尾部，摄取完成即对账，消除调用序隐式契约。
- **RechunkOrchestrator**：AiChunkServiceImpl.java:804-835 与 FileServiceImpl.java:421-436 是同一段「删 auto 留 manual+清向量」的复制——抽提 `ChunkPurgeService.purgeAuto(kbId, fileId)` 供重切与 AI 分片应用共用；同时把编排体从 FileServiceImpl/IngestionConsumer 两处收拢为一个类。
- **ChunkAlgorithmModule**：把缝 2 的分发 switch 从 IngestionConsumer 下推为 `ChunkingService.dispatch(parseResult, strategyId, kbEmbeddingModel)`，主流程不再认识算法名。
- **ParentChunkAssembler**：None（纯函数已深）。
- ChunkQueryModeDetector (S)：维持现状。

## 5. Parent Trace

| Design 模块/缝 | Spec REQ |
|---|---|
| ChunkLifecycleModule（list/getById/getCount） | REQ-A01~A05 |
| ChunkLifecycleModule（create/delete/batchDelete） | REQ-B01~B06 |
| ChunkLifecycleModule（update + 父分片刷新） | REQ-C01~C04 |
| ChunkAlgorithmModule（分发+五算法） | REQ-D01~D10 |
| ParentChunkAssembler | REQ-E01~E05 |
| RechunkOrchestrator | REQ-F01~F06 |
| ChunkStoreModule + 缝 3（embedding 路由） | REQ-G01、G05 |
| ChunkLifecycleModule（三库同步+增量图谱触发） | REQ-G02~G04 |
| 缝 1（策略配置） | REQ-D01（参数来源前提，上游 parse-strategy REQ-G01~G06 承接） |
| 缝 2（算法分发） | REQ-D01 |
| NFR-1/NFR-2 | ChunkController 注解面（鉴权/审计复用 iam 契约，不立新模块） |
| ChunkLifecycleModule（list/getById/getCount） | REQ-A02（2026-09-24 review 补锚：父子模式下孤立子分片与父分片按 chunkIndex 升序混排保持文档顺序） |
| ChunkLifecycleModule（list/getById/getCount） | REQ-A03（2026-09-24 review 补锚：列表响应携带 parentCount 与 tokenTotal（排除父分片防重复统计）） |
| ChunkLifecycleModule（list/getById/getCount） | REQ-A04（2026-09-24 review 补锚：计数接口返回分片总数且排除父分片（父分片是上下文聚合非独立分片）） |
| ChunkLifecycleModule（create/delete/batchDelete） | REQ-B04（2026-09-24 review 补锚：删除后大于被删索引的分片索引一律 -1 保持连续；父分片索引取首个子分片位置不参与移位） |
| ChunkLifecycleModule（update + 父分片刷新） | REQ-C03（2026-09-24 review 补锚：子分片内容变更后所属父分片内容立即按「\n\n」拼接重拼） |
| ChunkLifecycleModule（update + 父分片刷新） | REQ-C04（2026-09-24 review 补锚：content 变更清理该分片图谱数据、重置 graphIndexed=0 与 extractionResult，图谱开关开启时触发增量构建） |
| ChunkAlgorithmModule（分发+五算法） | REQ-D03（2026-09-24 review 补锚：结构感知分片维护 H1-H6 标题栈，携带 title/headingPath，titlePrefix 开启时注入祖先标题前缀） |
| ChunkAlgorithmModule（分发+五算法） | REQ-D07（2026-09-24 review 补锚：递归字符切分按分隔符优先级逐级降级，用尽回退句界再按 chunkLength 硬切，短片段贪心合并） |
| ParentChunkAssembler | REQ-E04（2026-09-24 review 补锚：单分片文档（<2 片）不聚合） |
| ParentChunkAssembler | REQ-E05（2026-09-24 review 补锚：图片 OCR 分片与所属章节子分片聚合进同一父分片） |
| RechunkOrchestrator | REQ-F03（2026-09-24 review 补锚：重切保留 manual 分片（行/ID/向量原地保留），图谱清空后保留分片 graphIndexed 重置为 0 随增量构建重建） |
| RechunkOrchestrator | REQ-F06（2026-09-24 review 补锚：AI 分片应用落库对既有 auto 分片采用同款「删 auto 留 manual」替换口径（用户确认边界为唯一事实源）） |
| ChunkLifecycleModule（三库同步+增量图谱触发） | REQ-G03（2026-09-24 review 补锚：索引位移致 auto 分片 id 变化时图谱 chunk 节点 id 同步改名（防 MENTIONS 悬空），向量按新 id 重嵌入重插） |
| ChunkLifecycleModule（三库同步+增量图谱触发） | REQ-G04（2026-09-24 review 补锚：新增分片（graphIndexed 未提取）在文件图谱开关开启时自动触发增量图谱构建补齐） |

## 6. ADR

**ADR-CM1（本链登记）：分片 ID 内嵌 chunkIndex 与 manual 独立 ID 段。**
- **背景**：kb_chunk 主键为应用生成字符串：auto=`{fileId}_chunk_{index}`、manual=`{fileId}_manual_{n}`、parent=`{fileId}_parent_{n}`，且 Milvus 向量 id 与图谱 chunk 节点 id 与之同源（KbChunk.java:34-35、ChunkServiceImpl.java:144-147、StorageServiceImpl.java:155,239）。
- **取舍**：顺序编号 ID 让「看 ID 知位置」并省一次 ID 生成分配，但索引位移（插入/删除）必须**删行重插+向量删除重嵌+图谱 renameChunkId** 三连锁（ChunkServiceImpl.java:395-448）；manual 用独立 ID 段 + origin 标记才使重分片保留手工分片可行（FileServiceImpl.java:421-436）。
- **理由**：难逆转（ID 已落库且被 Milvus/Neo4j 引用）+ 费解（新人难以理解为何改索引要重建向量）+ 真权衡（稳定 ID 的额外分配逻辑 vs 位移连锁成本）三者齐备，故记录。**长期方向**：ID 去索引化（随机/雪花 ID + chunkIndex 纯排序列）可整体删除 shiftChunkIndices 连锁——属破坏性迁移，本链不动，登记备查。

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
