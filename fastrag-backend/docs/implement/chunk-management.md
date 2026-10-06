# 分片管理（chunk-management）— 实现文档

> 记录既有代码如何实现 `contract/interfaces.md` `## chunk-management` 节的四个契约。镜像 design/chunk-management.md §1 的 Module Map。

## 1. Code File Map

```text
fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/
├── chunking/
│   ├── ChunkingService.java          算法族接口（6 方法含预览重载）
│   ├── ChunkingServiceImpl.java      五族算法实现（~1415 行，算法复杂度全部在此）
│   ├── ParentChunkAssembler.java     父子装配纯函数组件
│   ├── ChunkData.java                分片中间模型（builder）
│   └── ExtractionUnitAssembler.java  【交叉引用】图谱抽取单元重组，归 knowledge-graph 链；
│                                     仅共享 chunking/ 目录与 KbChunk 读模型，不属本链契约
├── storage/
│   ├── StorageService.java           摄取落库接口（3 重载）
│   └── StorageServiceImpl.java       落库编排（embedding 重试/父子装配/BATCH 批插/Milvus）
├── service/
│   ├── ChunkService.java             手工生命周期接口（7 方法）
│   └── impl/ChunkServiceImpl.java    手工生命周期实现（三库同步/索引位移）
├── controller/ChunkController.java   HTTP 包装（7 端点，薄）
├── consumer/IngestionConsumer.java   【部分】算法分发 + storeChunks 调用 + manual 对账（重切收尾）
├── service/impl/FileServiceImpl.java 【部分】reChunkFile/rebuildProcessRequest（重切入口段）
├── entity/KbChunk.java               kb_chunk 实体
├── mapper/KbChunkMapper.java         mapper（fulltextSearch/批量/聚合/序号查询）
└── model/ChunkDto.java、ChunkCreateRequest.java  DTO
```

测试：`src/test/java/.../chunking/{ChunkingServiceImplTest,ParentChunkAssemblerTest,TableModeIgnoreTest}.java`。

## 2. Interface Implementations

### ChunkingService（from contract: chunk-management.ChunkingService）

**Logic Flow:**
1. `chunk(text, strategyId)` → `configResolver.resolve(strategyId)` 取类型化配置 → `ruleBasedChunk`：CRLF 归一 → 按 `[PAGE_BREAK:n]` 分页块并收集页码 → 块内按分隔符正则（长度降序 quote 组合）切 part → `splitPartByHeadings` 逐行扫描拆「标题+正文」片段（围栏行翻转 inFence 状态、围栏内不识别标题）→ 标题开启新章节：先 flush 当前累积 → `updateHeadingPathByText` 维护栈 → 纯标题挂起 pendingHeadings → 组装单元（挂起标题+本段标题+正文，标题正文永不分离）→ 提取 `[IMAGE:key]` 占位符入 imageKeys → 超长单元 `forceSplit`（effectiveLength=max(chunkLength, overlap+1)）硬切、首子片含标题其余用完整路径前缀 → 累积超 chunkLength 落盘并保留 overlap 尾（pureOverlapTail 防垃圾片）→ 页尾 flush；文档以标题结尾时挂起标题独立成片。
2. `recursiveChunk` → `recursiveSplit` 按分隔符优先级递归（indexOf 保留分隔符；文本不含当前分隔符则降级）→ 分隔符用尽 `hardSplit` 句子边界、单句超长按字符硬切 → 贪心合并短片段、落盘携 overlap 尾。
3. `semanticChunk` → 拆句（。！？.!?\n 后视）→ 句数 ≤1 直接规则分片 → 模型缺省（策略级空→KB 级）→ `resolveEmbeddingModelRecord` 查 purpose=EMBEDDING+online 记录（查无且 KB 级不同则回退 KB 级）→ 32 句/批 embed，异常或数量不符 → 回退规则分片 → 逐句比较余弦相似度 < threshold/100 或累积超长即断片。
4. `chunkBySegments` → 每 segment 超 chunkLength 时按句拆并以 `当前累计长度/总长度` 线性分配 start/end 时间，否则整段成片。
5. `structuralChunk` → resolve 配置 → tableMode=ignore 且 `isPureTableDocument`（仅 HEADING/TABLE + 表格标题型短段落：≤100 字无句读或 ≤1000 字「说明/注」开头）时豁免忽略 → 遍历 DocNode：HEADING → 先 flush 有正文累积、连续标题先入上一个 pending、更新栈、存 pending；TABLE 且 ignore → 跳过；有 pending 先 appendHeading（不算正文）→ 原子单元（TABLE/CODE_BLOCK/DISPLAY_MATH/INLINE_MATH/IMAGE）整体 append（记录 typeHint/imageKeys，仅正文已存在且超长才 flush）；段落 `splitText` 按句累积 appendInline → 收尾合并 pending 再 flush → `handleAtomicOverflow`：超长 table 按行拆（复制表头、前缀只附首子片）、code 按 `FUNC_SPLIT_PATTERN` 函数边界拆（无边界退化按行均分）、其余保持完整。
6. 错误恢复：算法内部无上抛路径——embedding 失败/模型缺失/数量不符全部回退规则分片；空输入返回空列表。

**Module Interactions:**
- Calls：StrategyConfigResolver（缝 1，契约归 parse-strategy）、MarkdownSerializer（DocNode 序列化与纯文本长度）、EmbeddingService + ModelRecordMapper（语义切片批量向量化，模型边界 mock 白名单内）。
- Called by：IngestionConsumer（分发 :196-233，含 chunkBySegments/structuralChunk/recursiveChunk/semanticChunk/chunk(text, strategyId) 五形态）、FileServiceImpl.previewChunks（分块预览 :852 临时 config 形态、:854 strategyId 形态）。‹CERTAIN（2026-09-24 深证）›原「ParseStrategyServiceImpl（预览）」有误——ParseStrategyServiceImpl 全文无 ChunkingService 依赖，预览调用方为 FileServiceImpl.previewChunks。
- Shared state：无（无状态 Service，仅方法内局部状态）。

**Decisions & Deviations:** ① 聚合与「标题永不独占成片」为分片质量核心决策（F1 修复），以挂起-合并实现并在连续标题场景保留前一标题；② 语义切片三重回退（无模型/失败/数量不符）是「摄入不中断」的落实；③ 偏离：`chunk.strategy` 分发不在本类而在 IngestionConsumer（design §4 已登记下推机会）；④ forceSplit 的 overlap≥chunkLength 防护（:761）是隐式约定，契约已显式化。

**Parent Trace:** ChunkingServiceImpl → 契约 chunk-management.ChunkingService → Design.ChunkAlgorithmModule → REQ-D01~D10。

### ParentChunkAssembler（from contract: chunk-management.ParentChunkAssembler）

**Logic Flow:**
1. chunks 为空 → 空结果；size<2 → 不聚合原样返回。
2. parseAggLevel：H1/H2/H3 → 1/2/3；auto/非法/null → 0（完整路径）。
3. 按文档顺序扫描：`keyOf` = headingPath 为空 → ""；否则按 aggLevel 截断（`" > "` split 后取前 n 段重组）→ 键变化即把当前组交给 buildParents。
4. buildParents：组内按 content 长度累积，超 maxParentLength 时先落一个父分片再开新组（每组至少 1 子片）。
5. createParent：id=`{fileId}_parent_{seq}`、index=首子片 index、content=子片 content 过滤 null 后 `"\n\n"` join、chunkType=parent、title/headingPath/pageNumber/pageRange 取首子片；**原地**给每个子片 setParentId。
6. RETURN AssemblyResult{parents, children=原列表（已回填）}。

**Module Interactions:** 无外部模块交互（纯函数、零注入依赖——log 除外）。
Called by：StorageServiceImpl.storeChunks（:105-113，仅 childChunkLength>0 时）。
Shared state：无（输入列表原地回填 parentId 为唯一状态通道，契约已声明）。

**Decisions & Deviations:** ① 聚合放存储层而非算法层（javadoc :15-17 明示）：图片 OCR 分片在 IngestionConsumer 中后置归并，存储层聚合才能把它们归进所属章节父分片；② 输入列表被原地变异（parentId 回填）——契约已声明调用方以返回 children 为准；③ 无偏离。

**Parent Trace:** ParentChunkAssembler → 契约 chunk-management.ParentChunkAssembler → Design.ParentChunkAssembler → REQ-E01~E05。

### ChunkStoreService（from contract: chunk-management.ChunkStoreService，实现类 StorageServiceImpl）

**Logic Flow:**
1. chunks 空 → warn 返回。
2. 读 KB：dimension 缺省 1024、embeddingModel；`createCollection("kb_"+kbId.replace("-","_"), dimension)`（幂等，已存在则 warn 跳过）。
3. 模型解析：embeddingModel 非空 → model 表查 code+online LIMIT 1 → apiUrl/apiKey；查无 warn 走默认网关。
4. IF childChunkLength>0 THEN：effectiveMaxParent = maxParentLength>0 ?: childChunkLength×2 → `parentChunkAssembler.assemble(...)` → 有父分片则 `batchInsertParents`（vector_stored=0、无 embeddingId、不进 Milvus）→ chunks=assembly.children()。
5. 32 片/批循环：`embedBatchWithRetry`（输入=content+"\n"+headingPath+"\n"+fileName；最多 3 次、1s/2s/4s 退避；失败返回 null）→ null 则该批索引记入 noVectorIndexes → `batchInsertChunks`（SqlSession BATCH、auto-commit=false、手工 commit；id=`{fileId}_chunk_{globalIdx}`、embeddingId=id、vector_stored=批向量有效?1:0、origin=auto、title/headingPath/parentId/页码/时间戳/imageKeys JSON 化、region/publish_date/doc_level 冗余回填）。
6. Milvus 批写：按 noVectorIndexes 跳过错位、vectorCursor 对齐 → `milvusService.insert(collection, ids, vectors, kbId, fileId, indices)`；异常 error 记账（唯一 catch 不上抛的路径）。
7. file.chunkCount=chunks.size()（仅 auto 数）→ updateById。
8. 错误恢复：MySQL 批插失败 → RuntimeException 上抛（唯一上抛路径，流水线置 failed）；Milvus 失败 → 记账继续；embedding 失败 → 纯文本落库。

**Module Interactions:**
- Calls：MilvusService（collection 创建/insert）、EmbeddingService（批量 embed）、ParentChunkAssembler（父子装配）、KbFileMapper/KnowledgeBaseMapper/ModelRecordMapper/SqlSessionFactory。
- Called by：IngestionConsumer（:450-454 两分支调用）。
- Shared state：kb_chunk 表（写）、kb_file.chunk_count（写）、Milvus collection（写）。

**Decisions & Deviations:** ① 显式不用 @Transactional（:48-49 注释：与 BATCH Executor 事务互斥）——事务边界由 SqlSession 自管；② embedding 输入拼接 headingPath+fileName（:366-378，「与 indexConfig.embedFields 一期固定策略一致」）；③ 批失败错位防护（noVectorIndexes + vectorCursor 防御 :154）保证 ids/vectors 严格对应；④ 偏离：契约名 ChunkStoreService，实现类名 StorageService——上游 file-ingestion 节预留的 StorageModule 由本链正式收编时保留原类名（重命名影响面大，登记于 design §4）。

**Parent Trace:** StorageServiceImpl → 契约 chunk-management.ChunkStoreService → Design.ChunkStoreModule → REQ-E01~E05（装配触发）、G01、G05。

### ChunkLifecycleService（from contract: chunk-management.ChunkLifecycleService，实现类 ChunkServiceImpl）

**Logic Flow:**
- **list**：count(chunkType=parent) → tokenTotal=sumLeafCharLength（排除父片）→ parentCount>0：查「parent OR (parentId IS NULL AND ≠parent)」按 chunkIndex 升序分页 → 逐行 toDto，parent 行内嵌 selectList(parentId=..., orderByAsc chunkIndex) 子片 → mode=parent；否则单层分页 mode=single。
- **getCount**：count(kbId=, chunkType≠parent)。
- **getById**：selectById → null 抛 RuntimeException「分片不存在」→ kbId 不等抛「分片不属于该知识库」→ toDto。
- **create**：@Transactional → file 查无抛「文件不存在」→ insertAfterIndex≠null 则 newChunkIndex=插入位+1 且 shiftChunkIndices(fileId,该位, +1)；否则 maxChunkIndex+1 → manualSeq=maxManualSeq+1 → id=`{fileId}_manual_{seq}` → 组装实体（chunkType 缺省 text、origin=manual、embeddingId=id）→ generateEmbeddings（KB 级模型 → model 表 online 记录 → embed；无模型/失败 → 空列表）→ 有向量：vectorStored=1 先插行再 Milvus insert，Milvus 异常 → warn + vectorStored=0 回写；无向量：vectorStored=0 插行 → updateFileChunkCount → triggerIncrementalGraphBuild → 返回 DTO。
- **update**：归属校验同 getById → 按字段存在性更新 content/startTime/endTime/pageNumber/chunkType/imageKeys（强转目标类型）→ contentChanged：generateEmbeddings → 非空则 Milvus deleteById(旧)+insert(新)（失败 warn 不置 0）、空则 vectorStored=0 → updateById → contentChanged 时 refreshParentContent（子片按 parentId 查全、"\n\n" join、update 父行 content；异常 warn）→ contentChanged 时 deleteChunkGraph（warn 容错）+ graphIndexed=0/extractionResult=null + triggerIncrementalGraphBuild。
- **delete**：归属校验 → parent 片收集子片 id → cascadeIds=[子片...,自身] → deleteBatchIds → Milvus deleteByIds（warn 容错）→ 逐片 deleteChunkGraph（warn 容错）→ shiftChunkIndices(fileId, deletedIndex+1, -1) → updateFileChunkCount。
- **batchDelete**：空/查无即返回 → 按 kbId 过滤 validIds+fileIds（跨库忽略）→ parent 片补子片 → deleteBatchIds → Milvus deleteByIds（warn）→ 逐片 deleteChunkGraph（warn）→ 逐文件 updateFileChunkCount（**无索引重排**）。
- **shiftChunkIndices(fileId, startIndex, shift)**：查该文件非 parent、index≥start 的片升序 → manual 片：仅 setChunkIndex+update（ID/向量/图谱不动）→ auto 片：vectorStored=1 先 Milvus deleteById(旧)（warn）→ deleteById 行 → 换 id/embeddingId=`{fileId}_chunk_{newIndex}` → insert → renameChunkId(oldId,newId)（warn）→ vectorStored=1 再 embed+insert 新向量（warn）。
- **triggerIncrementalGraphBuild**：file.enableGraphBuild==1 否则 debug 跳过 → messagePublisherProvider.getIfAvailable()（null warn）→ publishGraphBuild({kbId,fileId,mode:incremental})；全程 try-warn。

**Module Interactions:**
- Calls：MilvusService、EmbeddingService、GraphStore（deleteChunkGraph/renameChunkId）、MessagePublisher（ObjectProvider 防循环依赖，同 IngestionConsumer 模式）、KbChunkMapper/KbFileMapper/KnowledgeBaseMapper/ModelRecordMapper。
- Called by：ChunkController（7 端点一一委托）。
- Shared state：kb_chunk（读写删）、kb_file.chunk_count（写）、Milvus collection（写删）、Neo4j 图（删/改名）、RabbitMQ graph-build（发布）。

**Decisions & Deviations:** ① 错误语义偏离契约理想值：三处 RuntimeException 走兜底 500 语义而非 404/400（NFR-5 登记债务，与 kb-core E_KB_NOT_FOUND 同款）；② list 返回 Map 扩展 mode/parentCount/tokenTotal 三键（NFR-6 形态偏离）；③ update 强转 (Double)/(Integer)/(String)（:216-218）依赖调用方传型正确——类型化请求对象为已登记深化机会；④ batchDelete 不做索引重排与 delete 不对称〔决议 2026-09-24：按现状为准——无注释/测试佐证「有意保留」，不对称已登记偏离（spec REQ-B05，代裁可推翻）〕；⑤ manual 分片 ID 段隔离（:144-147 注释）是重分片保留可行性的关键决策（ADR-CM1）；⑥ 登记债务（2026-09-24 裁决补登）：create/update 的 content 无业务长度校验（:156、213 直写入），裁决上限 50,000 字符待补入口校验（spec §5）。

**Parent Trace:** ChunkServiceImpl → 契约 chunk-management.ChunkLifecycleService → Design.ChunkLifecycleModule → REQ-A01~A05、B01~B06、C01~C04、G02~G04。

### RechunkOrchestrator（from contract: chunk-management.RechunkOrchestrator；实现分布 FileServiceImpl + IngestionConsumer）

**Logic Flow（reChunkFile，FileServiceImpl.java:369-482）:**
1. 查文件（kbId+fileId）→ 无则 notFound「文件不存在」。
2. 守卫：deletedAt≠null → 400「已在回收站」；status=processing → 400「处理中」；processingMode=qa → 400「QA 模式不支持」（E_RECHUNK_STATE_CONFLICT，归 parse-strategy 契约）。
3. presetStrategy 非空且 strategyId 为空 → resolvePresetStrategy（当前绑定已生效→复用；KB 内有→复用 kb 级优先；均无→创建文件级专属策略）→ 得 strategyId；解析失败 warn 保持当前绑定。
4. strategyId 三态：null 沿用；空串 setParseStrategyId(null) 清除覆盖走自动匹配；非空 → 查 kb_id 匹配且（file_id NULL 或=本文件）→ 查无 400「解析策略不存在或不属于该知识库」（E_REBIND_NOT_OWNED，归 parse-strategy 契约），命中 setParseStrategyId。
5. 清理：查 origin IS NULL OR ≠'manual' 的 auto 片 → 收集非空 embeddingId → chunkMapper.delete(auto 条件) → Milvus deleteByIds(embeddingIds)（warn 容错；**不用** deleteByFileId）→ graphService.deleteFileGraph（warn 容错）→ manual 片 graphIndexed=0/extractionResult=null（warn 容错）。
6. 重置：status=pending、progress=0、stage=""、chunkCount=0 → updateById → `process(kbId, fileId, rebuildProcessRequest(f))`（快照重建时以文件当前绑定覆盖内嵌历史策略值，:226-247）。
7. RETURN toDto(f)——重切异步完成。

**Logic Flow（reconcileManualChunks，IngestionConsumer.java:753-782）:**
1. storeChunks 完成后调用 → 查 origin=manual 按原 chunkIndex 升序 → 空则返回。
2. 统计 auto 非父片数 base → manual 第 i 片 chunkIndex=base+i → updateById。
3. file.chunkCount=base+manual 数 → updateById；异常 warn（对账失败不回滚主管线）。

**Module Interactions:**
- Calls：KbParseStrategyMapper/ChunkMapper/FileMapper、MilvusService、GraphService（文件图谱清理）、自身 process（标准摄取触发）、MessagePublisher（间接经 IngestionConsumer 链）。
- Called by：FileController（POST /{id}/re-chunk，:131-149；saveFileStrategy 链 :555-560；OnlyOffice forcesave 回调路径）、IngestionConsumer（storeChunks 后对账）。
- Shared state：kb_chunk（删/改）、kb_file（状态机重置/绑定写回/chunkCount）、Milvus collection（删）、Neo4j（文件图谱删）、RabbitMQ ingestion 队列（经 process 重发）。

**Decisions & Deviations:** ① 向量清理按 embeddingId 定向删除而非 deleteByFileId（:421-431 注释：防全量删清掉 manual 分片向量后无向量可检索）——本契约最关键实现决策；② 清理与重发同流程（无「已清理未重跑」终态）即 ADR-0001 无漂移态的落实；③ 对账放在消费端管线尾部而非清理侧——重跑才知道 auto 数；④ 偏离：编排体分散于 FileServiceImpl 与 IngestionConsumer 两处（design §4 已登记收拢机会）；⑤ AiChunkServiceImpl.java:804-835 与步骤 5 前半段为复制代码（design §4 已登记抽提机会）。

**Parent Trace:** FileServiceImpl.reChunkFile + IngestionConsumer.reconcileManualChunks → 契约 chunk-management.RechunkOrchestrator → Design.RechunkOrchestrator → REQ-F01~F06；上游衔接 parse-strategy.ParseStrategyBindingGuard / file-ingestion.IngestionPipeline。

## 3. Testing

引用 `tdd/chunk-management.md` 的切片（S0 HITL / S1 算法 / S2 装配 / S3 落库 / S4 生命周期 / S5 重切）。

**现有测试覆盖（63 测试类中的 3 个，均 AFK 且已绿）：**
- S1 算法族：ChunkingServiceImplTest 21 例（结构 8 / 规则文本 4 / 递归 4 / 语义 4 + 旧平铺兼容 1）+ TableModeIgnoreTest 5 例（纯表格豁免/混合跳过/表格标题段落/structured 默认/连续标题）——覆盖 REQ-D02~D08 主干与 D10 部分。
- S2 装配：ParentChunkAssemblerTest 7 例（同路径聚合/单片不聚合/超长二次切分/分章节/空路径归组/空与单片输入/图片分片归组）——覆盖 REQ-E01~E03、E05。

**缺口（现状零测试，tdd S3/S4/S5 待新增）：**
- ChunkServiceImpl 全部 7 方法：三库同步、索引位移连锁（manual/auto 分支）、父子级联、跨库过滤、图谱联动触发——REQ-A/B/C/G 主体无回归防线。
- StorageServiceImpl：父子装配触发、embedding 重试与错位防护、批插事务——REQ-G01/G05 与 E 域落库侧无覆盖。
- FileServiceImpl.reChunkFile / IngestionConsumer.reconcileManualChunks：manual 保留、定向删向量、对账重排、状态守卫——REQ-F 域无覆盖。
- tableMode 纯表格判定的字符阈值（100/1000）无真实样本断言（HITL S0 待回填）。
