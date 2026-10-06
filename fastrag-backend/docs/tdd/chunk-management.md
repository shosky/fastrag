# 分片管理（chunk-management）— 测试设计

> 契约来源：`contract/interfaces.md` / `contract/data.md` 的 `## chunk-management` 节。mock 纪律：只允许在真缝/系统边界 mock（外部 API、Milvus、MinIO、Neo4j/GraphStore、RabbitMQ、Embedding 模型网关、时间）；禁 mock 内部模块（StrategyConfigResolver 的真实实现可用 mock mapper 构造——现有测试即此模式，ChunkingServiceImplTest.java:51-56）。

## 1. 切片规划

| # | 切片 | 契约接口 | AFK/HITL | 说明 |
|---|---|---|---|---|
| S0 | 父子装配产品语义人工确认 | ParentChunkAssembler | **HITL** | aggLevel=H1/H2/H3 的章节观感、纯表格文档字符阈值（100/1000）需人对真实文档样本确认后再固化断言 |
| S1 | 分片算法族 | ChunkingService | AFK | 纯算法+边界 mock（EmbeddingService、ConfigManageService），现有 26 例为底 |
| S2 | 父子装配 | ParentChunkAssembler | AFK | 纯函数零 mock，现有 7 例为底 |
| S3 | 摄取落库 | ChunkStoreService | AFK | mock MilvusService/EmbeddingService/SqlSessionFactory 边界 |
| S4 | 手工生命周期 | ChunkLifecycleService | AFK | mock Milvus/Embedding/GraphStore/MessagePublisher/KbFileMapper 边界（**现状零覆盖，最大缺口**） |
| S5 | 重切编排 | RechunkOrchestrator | AFK | mock 守卫依赖与 MQ；对账逻辑可与 S4 共用夹具 |

执行顺序建议：S0（HITL 在前）→ S1/S2（红绿最快）→ S4（缺口最大）→ S3 → S5。

## 2. 测试用例（逐接口）

### ParentChunkAssembler（contract: chunk-management.ParentChunkAssembler）— HITL S0

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [聚合] H2 层级截断时 [产出] 跨 H3 子章节同组 | 同一 H2 下两个不同 H3 headingPath 的子分片序列 | assemble(chunks, fileId, 4000, "H2") | 两 H3 分片归入同一父分片，父 headingPath 为 H2 路径 | REQ-E01 | 无（纯函数） |
| [聚合] 真实 Excel 解析样本 tableMode=ignore [产出] 数据零丢失（人工确认阈值） | 真实 Excel 解析出的 HEADING+短标题 PARAGRAPH+TABLE 节点 | structuralChunk（tableMode=ignore） | 表格数据完整保留；标题段落保留；无空结果 | REQ-D05 | 无 |
| [装配] 100+ 子分片大文档 [产出] 父分片数量与长度在人工可读范围 | 真实长文档的结构分片结果 | assemble(auto) | 抽样父分片内容为完整章节语境（无半句截断观感） | REQ-E02 | 无 |

### ChunkingService（contract: chunk-management.ChunkingService）— AFK S1

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [结构分片] 表格超长时 [产出] 按行拆分且子片复制表头 | 12 行 TABLE 节点 + chunkLength=80 | structuralChunk | 多个 chunkType=table 子片，每片含表头行 "| h1 | h2 |"，行不重复 | REQ-D02 | ConfigManageService（chunkLength） |
| [结构分片] 代码块超长且无函数边界时 [产出] 按行均分并恢复围栏 | 无 def/class 的长代码块 | structuralChunk | 多个 chunkType=code 子片，每片以 ```lang 开头 ``` 结尾 | REQ-D02 | 同上 |
| [结构分片] 文档以标题结尾时 [产出] 挂起标题独立成片 | H1+P+H2（无后续正文） | structuralChunk | 末片含 "## H2" 行且 title=H2 | REQ-D04 | 无 |
| [结构分片] PDF 页标记跨页分片 [产出] pageRange 正确 | 含 [PAGE_BREAK:2]/[PAGE_BREAK:3] 的文本 | chunk(text, config) | 跨页累积片 pageRange="2-3" | REQ-D10 | ConfigManageService |
| [递归切分] overlap 大于 chunkLength 时 [产出] 正常终止不死循环 | chunkLength=5, overlap=10 | recursiveChunk | 返回有限片数且每片 ≤ 有效长度 | REQ-D06 | ConfigManageService |
| [语义切片] 策略模型误配对话模型时 [产出] 自动回退 KB 级模型 | 模型表无 embed-x 的 EMBEDDING 在线记录 | semanticChunk(threshold, kbEmbeddingModel=kb-embed) | embed 以 "kb-embed" 调用并正常断片 | REQ-D08 | EmbeddingService、ModelRecordMapper |
| [时间分段] 单段超长 [产出] 时间按文本比例线性分配 | segment 0-100s、文本 400 字、chunkLength=100 | chunkBySegments | 各片 startTime/endTime 区间连续、比例≈文本占比、区间并集=原区间 | REQ-D09 | ConfigManageService |

（现有 ChunkingServiceImplTest 21 例 + TableModeIgnoreTest 5 例继续作为回归基线，不重写。）

### ChunkStoreService（contract: chunk-management.ChunkStoreService）— AFK S3

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [落库] parent_child 参数时 [产出] 先聚合并父分片免向量 | chunks 6 片 + childChunkLength=500,maxParentLength=1000 | storeChunks(...) | Milvus insert 的 ids 不含 `_parent_`；父行 vector_stored=0；子行 parentId 已回填 | REQ-E03、G01 | MilvusService、EmbeddingService |
| [落库] 某批 embedding 三次重试均失败时 [产出] 该批纯文本落库且 Milvus 无此批 | EmbeddingService 对第 2 批恒抛异常 | storeChunks | 第 2 批行 vector_stored=0；Milvus insert ids 不含第 2 批 id；第 1/3 批正常 | REQ-G05 | EmbeddingService、MilvusService |
| [落库] embedding 重试序列 [产出] 指数退避后成功不再重试 | EmbeddingService 第 1 次抛、第 2 次成功 | storeChunks | embed 恰调用 2 次；全部行 vector_stored=1 | REQ-G05 | EmbeddingService |
| [落库] 完成后 [产出] chunk_count=auto 片数且文件元数据冗余回填 | kb_file.region="华东" | storeChunks | file.chunkCount=chunks.size()；每行 region/publish_date/doc_level 与文件一致 | REQ-B06（对账前口径） | 无（mapper mock） |
| [落库] MySQL 批插失败时 [产出] 异常上抛流水线置 failed | batchMapper.insert 抛异常 | storeChunks | RuntimeException 上抛（E_BATCH_INSERT_FAILED 路径） | 契约 Error modes | SqlSessionFactory/SqlSession |

### ChunkLifecycleService（contract: chunk-management.ChunkLifecycleService）— AFK S4

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [列表] 存在父分片与孤立子分片时 [产出] parent 模式混排且父片内嵌子片 | 父分片 idx=0、孤立子片 idx=1、父+子 idx=2/3 | list(kbId, null, 1, 20) | mode=parent；items 按索引 [父0, 孤1, 父2]；父0.children 按序；parentCount/tokenTotal 正确且不含父片 | REQ-A01~A03 | 无（mapper 真实 H2 或 mock） |
| [创建] 指定 insertAfterIndex 时 [产出] 后续 auto 片连锁重建而 manual 片仅移位 | 文件含 auto idx0-2（vectorStored=1）+ manual idx3 | create(insertAfterIndex=0) | 新片 id=`{f}_manual_1` origin=manual；auto1/2 行 id 改为 chunk_2/3 且 Milvus 先删旧后插新；manual3 仅 chunkIndex=4、id 不变；图谱 renameChunkId(旧,新) 两次 | REQ-B01~B03、G03 | Milvus、Embedding、GraphStore |
| [删除] 删除父分片时 [产出] 子片三处级联且索引前移 | 父分片 + 2 子片 + 后继片 idx5 | delete(parentId) | deleteBatchIds 含父+2子；Milvus deleteByIds 同集；deleteChunkGraph ×3；idx5 行 chunkIndex=4 | REQ-B03、B04、G02 | Milvus、GraphStore |
| [批删] 混入他库 id 时 [产出] 静默忽略且不误删 | ids=[本库×2, 他库×1] | batchDelete | 仅本库 2 片被删；涉及文件 chunkCount 重算；**无索引重排调用** | REQ-B05 | Milvus、GraphStore |
| [编辑] content 变更时 [产出] 向量替换+父片重拼+图谱重置+增量消息 | 子片（parentId=P）vectorStored=1、文件 enableGraphBuild=1 | update(fields={content:new}) | Milvus deleteById(旧)+insert(新)；父 P content=子片新拼接；graphIndexed=0/extractionResult=null；publishGraphBuild(mode=incremental) 恰一次 | REQ-C02~C04、G04 | Milvus、Embedding、GraphStore、MessagePublisher |
| [编辑] content 未变仅改 startTime 时 [产出] 不触碰向量与图谱 | fields={startTime:1.0} | update | Milvus/GraphStore/MessagePublisher 零交互 | REQ-C01 | 同上 |
| [编辑] embedding 失败时 [产出] 编辑成功且 vector_stored=0 | EmbeddingService 恒抛异常 | update(content) | 返回成功 DTO；行 vector_stored=0；无 Milvus insert | REQ-C02、G02 | Embedding |
| [创建] 文件不存在或跨库时 [产出] 拒绝 | fileId 无行 / chunk.kbId≠kbId | create / getById | RuntimeException（500 语义，E_FILE_NOT_FOUND / E_CHUNK_KB_MISMATCH 现状债务路径） | REQ-A05、契约 Error modes | 无 |

### RechunkOrchestrator（contract: chunk-management.RechunkOrchestrator）— AFK S5

| 测试名 | Given | When | Then | 追溯 REQ | 边界mock |
|---|---|---|---|---|---|
| [重切] 混合 auto/manual 分片时 [产出] 删 auto 留 manual 且向量按 embeddingId 定向删 | auto×3（含 1 片 vectorStored=0）+ manual×2 | reChunkFile(kbId, f, null) | MySQL delete 仅 auto 3 行；Milvus deleteByIds 仅 2 个非空 embeddingId（不用 deleteByFileId）；manual 行/向量不动；manual graphIndexed=0；文件 pending/chunkCount=0 且 process 被调 | REQ-F02~F04 | Milvus、GraphStore、MessagePublisher |
| [重切] 回收站/处理中/QA 模式时 [产出] 拒绝且零清理 | file.deletedAt≠null / status=processing / mode=qa | reChunkFile | BusinessException(400) 对应文案；chunkMapper/milvus 零交互 | REQ-F01 | 无 |
| [重切] 换绑目标非本库策略时 [产出] 拒绝（上游守卫语义回归） | strategyId 属他库 | reChunkFile | 400「解析策略不存在或不属于该知识库」；绑定字段不变 | REQ-F01 前置 | 无 |
| [对账] 管线完成后 [产出] manual 重排至 auto 后且 chunkCount=auto+manual | 新 auto 5 片 + 保留 manual 原索引 0/3 | reconcileManualChunks | manual chunkIndex=5/6（按原相对序）；file.chunkCount=7 | REQ-F04、F05 | 无（mapper mock） |
| [对账] 无 manual 分片时 [产出] 空操作 | 仅 auto | reconcileManualChunks | 零更新 | REQ-F05 | 无 |

## 3. 追溯（抽样）

| 测试 | 契约接口 | Design 模块 | Spec REQ |
|---|---|---|---|
| [编辑] content 变更时… | chunk-management.ChunkLifecycleService | ChunkLifecycleModule | REQ-C02/C03/C04/G04 |
| [落库] parent_child 参数时… | chunk-management.ChunkStoreService | ChunkStoreModule + ParentChunkAssembler | REQ-E03、G01 |
| [重切] 混合 auto/manual… | chunk-management.RechunkOrchestrator | RechunkOrchestrator | REQ-F02/F03/F04 |
| [结构分片] 表格超长… | chunk-management.ChunkingService | ChunkAlgorithmModule | REQ-D02 |
| [聚合] H2 层级截断… | chunk-management.ParentChunkAssembler | ParentChunkAssembler | REQ-E01 |
| [创建] 指定 insertAfterIndex… | chunk-management.ChunkLifecycleService | ChunkLifecycleModule | REQ-B01/B02/B03/G03 |

## 4. 待实施说明

- 本文件为测试设计，红绿循环**未执行**（任务铁律：不改源码/测试代码）。执行时按本文件从 Tracer Bullet 起步：先走通「S2 聚合 → S4 [编辑] content 变更」一条纵切（算法纯函数最易红绿、生命周期用例牵动三库最有回归价值），再横向铺开 S1→S3→S5。
- 现有测试基线：ChunkingServiceImplTest（21 例，ChunkingServiceImplTest.java:102-416）、ParentChunkAssemblerTest（7 例，ParentChunkAssemblerTest.java:40-152）、TableModeIgnoreTest（5 例，TableModeIgnoreTest.java:41-130）——S1/S2 以回归保全为主，新增用例只补边界（aggLevel 截断、代码围栏均分、死循环防护、挂起标题结尾片、时间比例分配）。
- **缺口清单（现状无测试）**：ChunkServiceImpl 全部 7 方法（三库同步/索引位移/级联/对账）、StorageServiceImpl 全部 3 方法、FileServiceImpl.reChunkFile、IngestionConsumer.reconcileManualChunks——即 S3/S4/S5 全部为待新增。
- mock 边界白名单（本链）：MilvusService、EmbeddingService、GraphStore、MessagePublisher、ModelRecordMapper/ConfigManageService（模型/配置数据边界）、SqlSessionFactory（BATCH 事务边界）；KbChunkMapper 等内部 mapper 优先用内存 stub 或 H2，避免过度 mock 内部模块。
- HITL S0 结论回填方式：人工确认后把阈值/层级语义固化为 AFK 断言（新增用例），并在本文件标记确认人与日期。
