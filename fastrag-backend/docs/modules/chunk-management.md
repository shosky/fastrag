---
name: Chunk Management
summary: 分片全生命周期唯一权威——五族算法与分发（IF-009）、落库定音锤（IF-010）、手工分片三库连锁（IF-011）、重切编排（ADR-0001 执行面）。
type: module
id: MOD-CHUNK-MANAGEMENT
profile: backend
status: draft
updated: 2026-11-17
satisfies: [REQ-A01, REQ-A02, REQ-A03, REQ-A04, REQ-A05, REQ-B01, REQ-B02, REQ-B03, REQ-B04, REQ-B05, REQ-B06, REQ-C01, REQ-C02, REQ-C03, REQ-C04, REQ-D01, REQ-D02, REQ-D03, REQ-D04, REQ-D05, REQ-D06, REQ-D07, REQ-D08, REQ-D09, REQ-D10, REQ-E01, REQ-E02, REQ-E03, REQ-E04, REQ-E05, REQ-F01, REQ-F02, REQ-F03, REQ-F04, REQ-F05, REQ-F06, REQ-G01, REQ-G02, REQ-G03, REQ-G04, REQ-G05]  # 世代 spec 未受管，REQ id 由 spec 层声明；逐条映射见追溯节
decisions: []  # 核心裁决 ADR-0001（绑定-重切一致）与图谱存储唯一实现的原重构版 adr 文件缺失（design 文档 §6 载要点）；重建 adr 后回填
declares: []  # ChunkController 7 端点等 HTTP 形态归 docs/interfaces.md；本模块暂无新增外部错误码（contract 阶段补充）
hash: "7a3a9637ab90"
summaryHash: "7a3a9637ab90"
---

# 分片管理（chunk-management）— 模块设计

> 世代记录文档 [../design/chunk-management.md](../design/chunk-management.md)（A~G 架构与分片方式参考）承载架构叙事；本文档是 IDD 受管契约面。架构裁决（Module Map、缝、深化方向）以其为事实源，本文按受管格式收敛。

## 职责 {#responsibility}

文档变成可检索分片之后的全生命周期唯一权威：把解析产物切分为知识分片、落库并向量化、供用户手工操作、并在绑定变更时重切——「怎么切」「怎么存」「怎么删干净」的复杂度全部吞在本模块内。

**Files:**（5 个子单元，深度裁决见世代文档 Module Map）
- ChunkAlgorithmModule — `chunking/ChunkingService(Impl)`：五族分片算法（规则/递归/语义/时间分段/结构感知）唯一实现地，深（5 个公开方法背后 ~1700 行切分细节）
- ChunkStoreModule — `storage/StorageService(Impl)`：摄取管线落库定音锤（embedding→MySQL→Milvus→chunkCount），深
- ChunkLifecycleModule — `service/ChunkService(Impl)` + `controller/ChunkController`：手工分片 CRUD 唯一入口与三库连锁，深；Controller 为薄包装
- RechunkOrchestrator — `FileServiceImpl` reChunk 段 + `IngestionConsumer` reconcile 段：ADR-0001 物理执行面，深（编排体分散两文件，边界记录见边界设计）
- ParentChunkAssembler — `chunking/ParentChunkAssembler`：父子装配纯函数，深；仅存储层调用，对本模块外部隐藏
- ChunkQueryModeDetector (S) — list 的父子/单层模式探测，浅：维持现状，建议不独立成类

## 契约 {#contract}

### IF-009 — ChunkingService {#if-chunkingservice}

**Purpose:** 五族分片算法唯一入口——调用方只见「解析产物进、`ChunkData` 列表出」，标题栈、围栏状态机、动态阈值、降级回退全部隐藏；分发到哪个算法由 `chunk.strategy` 经 IngestionConsumer 分发（缝 2）。

**Signature:**
```java
// com.fastrag.module.knowledge.chunking
public interface ChunkingService {
    List<ChunkData> chunk(String text, String strategyId);                    // rule_fixed 规则切分（纯文本兜底路径）
    List<ChunkData> chunk(String text, ParseStrategyConfig config);           // 同上，接受未落库临时配置（预览场景）
    List<ChunkData> recursiveChunk(String text, String strategyId);           // rule_recursive 分隔符逐级降级
    List<ChunkData> semanticChunk(String text, String strategyId, String kbEmbeddingModel); // semantic；KB 级 embedding 兜底
    List<ChunkData> chunkBySegments(List<ParseResult.ChunkTimeSegment> segments, String strategyId); // 音视频 ASR 时间分段
    List<ChunkData> structuralChunk(List<DocNode> nodes, String strategyId);  // structure_aware / parent_child / excel_row 共用入口
}
```

**Types:**
```java
// com.fastrag.module.knowledge.chunking —— 分片统一产出形状；默认值与可选性以行内注释为准
public class ChunkData {
    private String id;             // "chunk_{index}"（重建时与列表位置一致）；存储层改写为 "{fileId}_chunk_{n}"
    private Integer index;         // 0 起连续编号
    private String content;
    private String title;          // 所属最近标题（structure_aware 产）
    private String headingPath;    // 层级路径 "第一章 > 1.1 背景"
    private Integer pageNumber; private String pageRange;
    private String chunkType;      // text | image | table | code（image 分片由消费方归并，非本接口产出）
    private List<String> imageKeys;
    private Double startTime, endTime;          // 仅时间分段
    private String parentId;                    // 仅 parent_child（存储层装配回填）
    private Integer llmReviewed;                // 0|1 —— LLM 边界审查标记（仅 structure_aware 接入）
    private String reviewModel; private LocalDateTime reviewTime; private String reviewNote; private String reviewPromptVersion;
}
// com.fastrag.module.knowledge.parser —— 解析产物类型，归属 file-ingestion（DocumentParser）；
// 本模块只消费其最小形状：DocNode{type: PARAGRAPH|HEADING|TABLE|CODE_BLOCK|DISPLAY_MATH|INLINE_MATH|IMAGE, text, level, children}
// ChunkTimeSegment{text, startTime, endTime}
```

**Error modes:**
- 空输入统一：text 为 null/空白、nodes 为空列表 → 返回空列表不抛出；structuralChunk 空节点由分发方落 rule_fixed 纯文本兜底（分发约定）
- 参数钳制：recursiveChunk/semanticChunk 入口 `chunkLength=max(1,·)`、`overlap=max(0,·)`、semanticThreshold 钳制 0~100 后映射 0.0~1.0——非法配置不炸管线
- semantic 模型三级自愈：策略级与 KB 级均无模型 → 回退 `chunk(text, strategyId)` 规则分片（不阻断）；策略级模型在模型表无 EMBEDDING purpose 记录（如误配对话模型）→ 自动回退 KB 级 embedding 模型（历史错误配置自愈）
- semantic 退化短路：预处理后 0 个原子单元 → 空列表；仅 1 个单元 → 整单元单分片（不走向量断点）
- strategy=excel_row 而节点非 ExcelRow 产物：不发生——解析侧 REQ-H09 守卫已在 parse() 降级 default（归 parse-strategy）

**Invariants:**
- 输出 index 从 0 连续、id 与列表位置一致；内容零丢失（切分只改边界不改文字）
- 实现无共享可变状态（无状态单例）——摄取与预览可并发调用同一实例
- 不可切分单元（TABLE/CODE_BLOCK/公式/IMAGE）整体保留，超长按行（表格）/函数边界（代码）二次拆分
- 标题延迟合并：标题等待下一正文节点合并不产孤标题片；excel_row 模式切片独占分片、overlap 强制 0（adr/0012）
- parent_child 模式本接口只产子片；父子装配发生在存储层（IF-010），`chunkLength` 语义为子片目标长度

**Public vs hidden:** 调用方传解析产物与 strategyId / 标题 pending 合并、代码围栏状态机、纯表格豁免、语义滑窗向量化与动态阈值分位、余弦相似度全部隐藏。

### IF-010 — StorageService {#if-storageservice}

**Purpose:** 摄取管线落库定音锤——一方法吞掉「分片→三处存储」的全部编排：批量 embedding（重试）、父子装配触发、MySQL 批插、Milvus 批写、chunkCount 记账。

**Signature:**
```java
// com.fastrag.module.knowledge.storage
public interface StorageService {
    void storeChunks(String kbId, String fileId, List<ChunkData> chunks);                 // 单层模式（childChunkLength=-1）
    void storeChunks(String kbId, String fileId, List<ChunkData> chunks, int childChunkLength); // >0 启用父子装配；缺省 maxParentLength=childChunkLength×2
    void storeChunks(String kbId, String fileId, List<ChunkData> chunks,
                     int childChunkLength, int maxParentLength, String parentAggLevel);   // 父子装配全参；parentAggLevel=null → 装配器默认 auto
    void storeChunkBatch(String kbId, String fileId, List<ChunkData> chunks, int batchStart); // 流式分批变体
}
```

**Types:** 复用 `ChunkData`（IF-009 已定义）；装配产物父分片同表落库（`chunkType=parent`，见实体）。

**Error modes:**
- chunks 为空：warn 后 no-op 返回（非错误；空文档不落任何行）
- embedding 批次失败（`embedBatchWithRetry` 3 次指数退避后返回 null）：该批记入无向量名单，纯文本落库、跳过 Milvus 插入（错位防护）——不抛出
- embedding 模型表无 online 记录：降级默认网关（warn 不抛）；KB 未配 embedding 模型 → 全部 `vectorStored=0`
- Milvus 批写整体失败：error 记账不抛出；此时 MySQL 已按 `vectorStored=1` 落库，DB 标记与实际向量存在失真窗口——最终一致缺口，需向量化重建对账（已知现状，深化候选）
- Milvus collection 不存在：幂等创建（存在即跳过）

**Invariants:**
- **非幂等**：本接口不做任何旧数据清理，重复调用产生重复行——「删旧再存」是调用方编排职责（重切链 FileServiceImpl 先删后调，ADR-0001 执行序）
- **非事务整体**：禁用 @Transactional（BATCH Executor 事务自管，两者不可混用）；MySQL 提交后 Milvus 失败不回滚 MySQL，靠 `vectorStored=0` 标记对账
- `childChunkLength > 0` 才触发 ParentChunkAssembler；父分片先落库（免向量、不进 Milvus），子分片回填 parentId 后走常规存储
- embedding 输入拼接固定为 `content + headingPath + fileName`（IndexConfig.embedFields 一期冻结）
- 自动分片 `origin=auto`、父分片 `chunkType=parent`；文件 `chunkCount` 记本次 auto 数（manual 对账由消费方编排，见接口节 RechunkOrchestrator）
- region/publish_date/doc_level 冗余自 kb_file 回填

**Public vs hidden:** 调用方（IngestionConsumer 两行调用）拿到「已可检索」的承诺 / Milvus collection 幂等创建、模型 apiUrl+apiKey 解析（无记录降级默认网关）、分批与重试、事务边界、错位防护全部隐藏。

### IF-011 — ChunkService {#if-chunkservice}

**Purpose:** 用户手工操作分片（查/建/改/删/批删）的唯一入口——「删一个分片牵动三处存储与文件计数」的连锁完全内聚；ChunkController 7 端点是它的薄包装（HTTP 形态归 docs/interfaces.md）。

**Signature:**
```java
// com.fastrag.module.knowledge.service
public interface ChunkService {
    PageResult list(String kbId, String fileId, int page, int pageSize);  // 父子/单层双模式分页（parentCount>0 判别 + tokenTotal 聚合）
    long getCount(String kbId);                                            // 非父分片总数
    ChunkDto getById(String kbId, String id);                              // 归属校验后详情
    ChunkDto create(String kbId, ChunkCreateRequest req);                  // 手工创建（manual ID 段 + 向量 + 图谱联动）
    ChunkDto update(String kbId, String id, Map<String, Object> fields);   // 部分编辑（内容变更→重嵌入+父分片刷新+图谱重建）
    void delete(String kbId, String id);
    void batchDelete(String kbId, List<String> ids);
}
```

**Types:** 无新增跨模块类型——出参 `ChunkDto` 为 HTTP DTO 归 docs/interfaces.md；patch 的裸 `Map<String,Object>` 是已知深化候选（见边界设计）。`list` 返回 Map 形状（行内注释为准）：
```java
// list(kbId, fileId, page, pageSize) →
// { mode: "parent"|"single",        // parentCount>0 判别
//   list: List<ChunkDto>,           // parent 模式下父分片行内嵌 children；孤立子分片按 chunk_index 混排
//   total, page, pageSize,
//   parentCount: long,              // 父分片数
//   tokenTotal: long }              // 整库叶分片字符总量（排除父分片防重复统计）
```

**Error modes:**
- 不存在/跨库访问（getById/update/delete）：抛 `RuntimeException`（"分片不存在"/"分片不属于该知识库"/create 的"文件不存在"）——尚未收敛 BusinessException（深化候选），调用方经 Controller 包装为业务错误
- batchDelete 语义差（现状记录）：不存在/跨库 id **静默过滤不报错**，与 delete 单删的抛错语义不对称
- create 并发窗口：manual 序号 = `max+1` 非原子，同文件并发 create 可能撞 `{fileId}_manual_{n}` 主键（后者失败，无锁）
- update 内容变更重嵌入：Milvus「删旧成功、插新失败」时 `vectorStored` 保持原值——DB 标记与实际向量可能失真（warn 记账，与 IF-010 的 Milvus 缺口同类）
- Milvus/Graph 外部依赖失败：一律 try-warn 记账继续，不阻断 MySQL 主数据事务（@Transactional 内执行非事务资源）

**Invariants:**
- create 的 manual ID 段独立（`{fileId}_manual_{n}`，按 manual 序号递增），与 auto 的 index 重编号天然隔离——重切保留 manual 时新 auto 从 0 重编号不撞车（ADR-0001 前提）
- delete 单删后索引前移保持连续（auto 删行重插 + 向量重建 + 图谱 renameChunkId；manual 仅位移 ID/向量/图谱不动）；**batchDelete 不做索引重排**（允许空洞，现状记录）
- 编辑子分片内容 → 所属父分片内容按 `\n\n` 重拼（与 ParentChunkAssembler 聚合规则一致）
- 写操作后文件 `chunkCount` 对账修正（只统计子分片）；删父分片级联子分片、逐片回收图谱 mention
- 内容变更触发的增量图谱构建经文件 `enableGraphBuild=1` 门控（KG-02）
- AI 分片应用落库、重分片共享「删 auto 留 manual」清理口径（与 RechunkOrchestrator 同款语义）

**Public vs hidden:** 调用方见 CRUD 语义 / 级联收集、shiftChunkIndices 双路径、embedding 模型路由（KB 级 + model 表 online 记录）、refreshParentContent 重拼全部隐藏。

## 接口 {#interface}

### RechunkOrchestrator（语义面）

**Purpose:** 重切入口——绑定裁决委托 parse-strategy 守卫后，本模块执行「保留什么、清理什么、何时对账」的物理编排。
**Signature:**
```java
// 语义级契约；实现分散两文件（见 Implemented by）
reChunkFile(kbId, fileId, strategyId 三态, presetStrategy) -> FileDto   // 状态守卫→删 auto 留 manual→清向量/图谱→重置文件→重发摄取
reconcileManualChunks(kbId, fileId)                                      // 管线完成后 manual 索引重排 + chunkCount 对账（包内私有）
```
**Implemented by:** `service/impl/FileServiceImpl.java`（reChunk 段）+ `consumer/IngestionConsumer.java`（reconcile 段）——两文件主归属 file-ingestion 链；编排体成类收拢为深化候选（世代文档 §4），收敛前本模块以语义契约持有该面。

### ChunkReviewService

**Purpose:** LLM 分片边界审查（仅 structure_aware，切片后入库前）——分批送审 merge/split/keep 调整，文字逐字守恒，失败整批回退标记未审。
**Signature:**
```java
// com.fastrag.module.knowledge.chunking
List<ChunkData> reviewAndAdjust(String kbId, String fileId, String strategyId, List<ChunkData> chunks);
```
双开关（yml `fastrag.chunk.llm-review.enabled` 强制 / 策略级 `chunk.llmReview.enabled`）；模型回退链 `strategy.llmModel → kb.llmModel → kb.graphLlmModel` 全空跳过留 WARN。明细落 `kb_chunk_llm_review`（见实体）。

## 实体 {#entities}

```sql
-- 分片主表：单表多态（chunkType 区分 text/image/parent；origin 区分 auto/manual）
CREATE TABLE kb_chunk (
    id VARCHAR(64) PRIMARY KEY,          -- {fileId}_chunk_{n} | {fileId}_parent_{n}（ID 内嵌 chunkIndex，删除连锁根源）
    kb_id VARCHAR(32) NOT NULL,
    file_id VARCHAR(32) NOT NULL,
    parent_id VARCHAR(64),               -- 子分片指向父分片；父分片自身为 NULL
    chunk_index INT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    embedding_id VARCHAR(64),            -- = id；Milvus 同键向量（向量不落 MySQL）
    vector_stored TINYINT DEFAULT 0,     -- 父分片恒 0（免向量）
    start_time DOUBLE, end_time DOUBLE,  -- 仅音视频时间分段
    page_number INT, page_range VARCHAR(16),
    image_keys TEXT,                     -- JSON 数组 ["page_1_img_0.png"]
    chunk_type VARCHAR(16) DEFAULT 'text',  -- text | image | parent | table | code
    origin VARCHAR(16) DEFAULT 'auto',   -- auto（管线）| manual（手工，重切保留）
    title VARCHAR(512), heading_path VARCHAR(1024),
    region VARCHAR(64), publish_date DATE, doc_level VARCHAR(16),  -- 冗余自 kb_file，检索过滤免 JOIN
    graph_indexed TINYINT DEFAULT 0,
    extraction_result TEXT,
    llm_reviewed TINYINT DEFAULT 0,      -- 0=未经审查 1=审查通过（含 keep）
    review_model VARCHAR(128), review_time DATETIME, review_note TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

```sql
-- LLM 边界审查明细（运营排查/事后补审定位；检索主链不消费）
CREATE TABLE kb_chunk_llm_review (
    id VARCHAR(32) PRIMARY KEY,
    chunk_id VARCHAR(64) NOT NULL,       -- {fileId}_chunk_{n}
    kb_id VARCHAR(32), file_id VARCHAR(32),
    model VARCHAR(128),
    adjusted TINYINT DEFAULT 0,          -- 1=merge/split 调整 0=keep
    note TEXT, prompt_version VARCHAR(16),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

- Relationships: 文件 1—N 分片；父分片 1—N 子分片（`parent_id` 同表自引用）；审查明细 N—1 分片（`chunk_id`）。
- Notes: 向量在 Milvus collection `kb_{kbId}`（键=分片 id），DDL 双处同步见 init-scripts/schema.sql + SchemaInitializer。

## 逻辑 {#logic}

### Logic Flow

各分片方式（`chunk.strategy`）的伪代码级实现细节，按策略分节；分发规则与参数默认值见契约节与世代记录「分片方式参考」。

五族算法与 excel_row 的伪代码级实现细节（semantic 滑窗断点、rule_fixed 标题挂起与围栏状态机、
rule_recursive 降级、structuralChunk 延迟合并与二次拆、parent_child 装配算法、时间分段线性内插）
统一收录于世代记录 [../design/chunk-management.md](../design/chunk-management.md)「分片方式参考 → 各策略实现细节」——
分片算法的叙事文档按特性归 design 层，本节保留契约接口的实现流。

IF-010 storeChunks 编排：

```text
storeChunks(kbId, fileId, chunks[, childChunkLength, maxParentLength, aggLevel]):
  IF chunks 空 THEN warn; RETURN
  createCollection(collection, dimension) 幂等
  解析 embedding 模型路由（KB 级 → model 表 online → 默认网关）
  IF childChunkLength > 0:
      assembly = ParentChunkAssembler.assemble(chunks, fileId, maxParentLength, aggLevel)
      先落库 parents（免向量）→ chunks = children（回填 parentId）
  FOR 每批 32 片:
      vectors = embedBatchWithRetry(3 次指数退避)
      IF null THEN 该批记入 noVectorIndexes（纯文本落库）
      batchInsertChunks（BATCH Executor 事务自管）
  Milvus 批写（跳过 noVectorIndexes）；整体失败 error 记账不回滚 MySQL
  file.chunkCount = chunks.size()
```

IF-011 手工操作连锁：

```text
create: insertAfterIndex? → shiftChunkIndices(start, +1) : 追加 max+1
        id = {fileId}_manual_{maxManualSeq+1}；origin=manual
        embed 失败/Milvus 失败 → vectorStored=0 纯文本落库（不阻断）
        chunkCount 对账 → 增量图谱触发（enableGraphBuild=1 门控）
update: patch 六键（content/startTime/endTime/pageNumber/chunkType/imageKeys）
        content 变更 → 重嵌入（删旧插新）→ refreshParentContent（子片按 \n\n 重拼父片）
        → 图谱 deleteChunkGraph + graphIndexed 置 0 → 增量图谱触发
delete: 父分片级联收集子分片 → MySQL 批删 → Milvus 删向量（try-warn）
        → 逐片图谱回收（try-warn）→ shiftChunkIndices(deletedIndex+1, -1) → chunkCount
shiftChunkIndices(fileId, start, shift):
  FOR index>=start 且非 parent:
    IF manual: 仅改 chunkIndex（ID/向量/图谱稳定）
    ELSE: 删行重插（新 ID）+ Milvus 删旧嵌新 + graphStore.renameChunkId（均 try-warn）
batchDelete: 跨库/不存在 id 静默过滤；父级联；不做索引重排；逐文件 chunkCount 对账
```

### Module Interactions

- Calls：StrategyConfigResolver（IF-006，参数合并）、EmbeddingService + model 表（缝 3 模型路由）、MilvusService（infra，假设缝）。
  另调 GraphStore（infra Neo4j，图谱存储唯一实现裁决）、MessagePublisher（增量图谱消息）、MarkdownSerializer（DocNode 序列化，属解析链）。
- Called by：IngestionConsumer（分发 + 落库调用方）、ChunkController（HTTP 薄包装）。
  另被 AiChunkServiceImpl（应用落库走同款清理口径）与 FileServiceImpl（重切编排）调用。
- Shared state：无共享可变状态；跨模块一致性经 kb_chunk 行 + vectorStored/graphIndexed 标记对账

### Decisions & Deviations

- **业务错误未收敛**：ChunkServiceImpl 抛 RuntimeException 落全局兜底（HTTP 200 + code 500「服务异常」），"分片不存在/跨库"语义对前端不可见。
  BusinessException 收敛为深化候选（世代文档 §4 ①）。
- **batchDelete 与 delete 语义不对称（现状记录）**：批删静默过滤跨库 id、不做索引重排（允许空洞）；单删抛错且索引前移。
- **Milvus 失真窗口**：storeChunks 的 Milvus 批写整体失败与 update 重嵌入「删旧插新失败」两处，
  DB `vectorStored=1` 与实际向量可能不一致。现状为 error/warn 记账、无自动补偿，依赖向量化重建对账。
- **分发 switch 落在 IngestionConsumer**（缝 2 落点偏浅）：主流程认识算法名；下推 `ChunkingService.dispatch(...)` 为深化候选。
- **manual 对账在消费方**：reconcileManualChunks 散在 IngestionConsumer 而非 storeChunks 尾部——调用序隐式契约（世代文档 §4 深化项）。
- **测试回填修正（2026-11-17）**：ChunkingServiceImplTest 两例语义切片用例过期——e5f8b6a 四步优化引入三重新契约：
  滑窗向量按窗口位置对齐、计数失配回退规则分片、MIN_CHUNK_SIZE=150 碎片合并。旧玩具句子 mock（3 向量对 19 单元）触发计数失配回退得 1 片。
  已按新契约重写：窗口位置对齐的向量列表（19/12 个、主题边界对齐断点），并加长句至 ≥150 字符防碎片合并折回单片。
  另修复 IngestionQaHookTest 预存编译破损：IngestionConsumer 增 MarkdownService 依赖后构造调用未跟上，补第 15 位 null。
- **测试缺口（现状记录）**：ChunkServiceImpl（三库一致）、StorageServiceImpl、ChunkReviewServiceImpl 无测试类——世代文档 §7 自认「最大缺口」；
  chunking 域现有 42 例（含修正）全绿，缺口补齐走 /idd-tdd。

## 边界设计 {#seam}

**缝 1：策略配置缝（Stable side）**——本模块是稳定侧：ChunkAlgorithmModule 只认类型化 `ParseStrategyConfig`。
参数来源与新旧双格式兼容是真缝但归 parse-strategy（IF-006 StrategyConfigResolver），分片算法不感知 JSON 存在。

**缝 2：分片算法分发缝（真缝）**——What varies：切分算法（五族 + excel_row 内部分支，未来可增）。
Adapters（5+1）：chunkBySegments / parent_child→structuralChunk / recursiveChunk / semanticChunk / 
structure_aware→structuralChunk，兜底 rule_fixed 双分支。Stable side：ChunkStoreModule 与 IngestionConsumer 主流程（拿到 
List&lt;ChunkData&gt; 后同构处理）。Placement rationale：分发 switch 现落 IngestionConsumer——落点偏浅（主流程被迫认识算法名）
，深化方向为下推 `ChunkingService.dispatch(parseResult, strategyId, kbEmbeddingModel)`。

**缝 3：embedding 模型路由缝（真缝）**——What varies：用哪个模型、经哪个网关（策略级 embeddingModel / KB 级兜底 / model 表 apiUrl+apiKey / 
默认网关）。Adapters（3+）：在线记录路由、KB 级回退、无记录默认网关。Stable side：算法与存储只调 `embeddingService.embed(...)`；
模型动态路由是平台级机制，fastrag-ai 不查业务库（宪法），caller 侧解析后传参。

**假设缝（不立缝）：** Milvus——infra 具体类、唯一向量实现，无第二实现非真缝；Neo4j GraphStore——图谱存储唯一实现裁决（启动 fail-fast，世代记录 §6 载要点），直接依赖不设缝。

**边界记录：** RechunkOrchestrator 编排体散在 FileServiceImpl 与 IngestionConsumer（两文件主归属 file-ingestion 链）
——boundary smell；深化方向为抽提 `ChunkPurgeService.purgeAuto(kbId, fileId)`（与 AI 分片应用共用清理语义）并收拢成类。
ChunkLifecycleModule 深化：RuntimeException→BusinessException 收敛、裸 Map patch 类型化、ID 稳定性消除 shiftChunkIndices 连锁、
SyncStep 收敛 try-warn 样板。

## 追溯 {#trace}

- ChunkingService（五族算法与分发）→ REQ-D01~REQ-D10
- StorageService（落库/向量化）与缝 3（embedding 模型路由）→ REQ-G01、REQ-G05
- ChunkService（list/getById/getCount）→ REQ-A01~REQ-A05
- ChunkService（create/delete/batchDelete）→ REQ-B01~REQ-B06
- ChunkService（update + 父分片刷新）→ REQ-C01~REQ-C04
- ChunkService（三库同步 + 增量图谱触发）→ REQ-G02~REQ-G04
- ParentChunkAssembler（父子装配，存储层隐藏协作者）→ REQ-E01~REQ-E05
- RechunkOrchestrator（语义面，编排体归 file-ingestion 链）→ REQ-F01~REQ-F06
- ChunkReviewService（LLM 边界审查，仅 structure_aware）→ 契约面见接口节
- 测试证据：chunking 域 5 测试类 42 例全绿（2026-11-17 回填修正后）；三库一致/存储/审查无测试类（缺口见逻辑节 Deviations）
- MOD-CHUNK-MANAGEMENT → 世代记录 [../design/chunk-management.md](../design/chunk-management.md)（A~G 架构 + 分片方式参考）

## 代码 {#code}

```yaml
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/chunking/ChunkingService.java
  description: 五族分片算法对外接口（IF-009 定义）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/chunking/ChunkingServiceImpl.java
  description: 算法实现宿主——标题栈延迟合并/围栏状态机/语义滑窗动态阈值/时间分段/excel_row 分支（IF-009 实现）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/chunking/ParentChunkAssembler.java
  description: 父子装配纯函数——标题层级聚合/层级截断/超长二次切分（存储层内部协作者，对外隐藏）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/chunking/ChunkReviewServiceImpl.java
  description: LLM 边界审查——分批送审/全覆盖校验/内容守恒/整批回退（接口节 ChunkReviewService 实现）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/storage/StorageService.java
  description: 落库定音锤接口（IF-010 定义）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/storage/StorageServiceImpl.java
  description: storeChunks 编排——分批 embedding 3 次退避/BATCH 批插/Milvus 批写/父子装配触发/冗余回填（IF-010 实现）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/service/ChunkService.java
  description: 手工分片接口（IF-011 定义）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/service/impl/ChunkServiceImpl.java
  description: CRUD 三库连锁——级联收集/shiftChunkIndices 双路径/图谱 mention 回收/chunkCount 对账（IF-011 实现）
- path: fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/controller/ChunkController.java
  description: 7 端点薄包装（HTTP 形态归 docs/interfaces.md，API-016~022）
- path: fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/chunking/ChunkingServiceImplTest.java
  description: 算法族主测试（20 例）——语义切片四步管线（滑窗/动态阈值/软硬上限/碎片合并）、模型自愈与回退、规则切分 overlap 防护；2026-11-17 按四步优化契约修正两例
- path: fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/chunking/ParentChunkAssemblerTest.java
  description: 装配纯函数测试——同路径聚合/超长二次切分/aggLevel 截断/空路径归组
- path: fastrag-modules/fastrag-knowledge/src/test/java/com/fastrag/module/knowledge/chunking/TableModeIgnoreTest.java
  description: tableMode=ignore 行为测试——表格跳过与纯表格文档豁免边界
```

- RechunkOrchestrator 两处实现宿主 `FileServiceImpl` / `IngestionConsumer` 已由 modules/parse-strategy.md#code 与 file-ingestion 链登记，本模块以接口节 `Implemented by:` 指针持有、不重复认领（DUP004）。
