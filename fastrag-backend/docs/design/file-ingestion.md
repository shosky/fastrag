# 文件上传与摄取流水线（file-ingestion）— 架构设计

> 上游符合性：ADR-0001（绑定变更必须触发重新分片）由 parse-strategy 链的 ParseStrategyBindingGuard 承载，本特性的 re-chunk/retry/saveFileStrategy 触发面是其执行载体（FileServiceImpl.java:369-482、555-560 ‹CERTAIN›），符合无冲突；ADR-0002（图存储收敛 Neo4j）与本特性仅通过 GraphService.deleteFileGraph 清理面接触（FileServiceImpl.java:451,584,666 ‹CERTAIN›），无冲突。另沿用 parsed-markdown ADR-1 要点（Markdown 对象键路径约定、不落库不加列；原文档已随 legacy 归档丢失，仅引要点，证据见 KbFile.java:74-82 ‹CERTAIN›）。MinIO 与 RabbitMQ 是系统边界（详见缝 3、缝 4）。

## 1. Module Map

| 模块 | Responsibility（一行） | Files | Depth Rationale |
|---|---|---|---|
| **IngestionPipelineModule** | 摄取流水线编排：消息收发（含 MQ 不可用同步降级）、幂等、状态机推进、分块分派、图片 OCR 归并、音频切片、通知/图谱触发/Markdown 落盘、失败重试 | `consumer/IngestionConsumer.java`、`service/MarkdownService.java`、`infra/rabbitmq/MessagePublisher.java`、`infra/rabbitmq/RabbitMQConfig.java`、`service/FileService.java`（process/retryFile/reChunkFile 触发面）、`model/FileProcessRequest.java`、`parser/ParseOptions.java` | **深**：十步编排与三重降级（MQ→同步、步骤失败不阻断、重试计数）全部隐藏在 handleIngestion 之后；调用方只需投递一个 Map 消息。processingConfig 快照 ↔ ParseOptions 的映射是两处共担的翻译层（IngestionConsumer.buildParseOptions :552 与 FileServiceImpl.rebuildProcessRequest :230） |
| **FormatParserModule** | 八格式解析路由与逐级降级：字节流 → 统一 ParseResult（Markdown 全文/DocNode 结构/时间分段/内嵌图片/页标题） | `parser/DocumentParser.java`、`parser/DocumentParserImpl.java`、`parser/ParseMethodRegistry.java`（消费 parse-strategy 的权威映射）、`parser/MediaExtractor.java`、`parser/ImageFilter.java`、`parser/ImageDedup.java`、`parser/MarkdownSerializer.java`、`util/OcrNoiseFilter.java` | **深**：吞掉 8 类格式 × 各自库（PDFBox/POI/POI-math/FFmpeg/字符集探测）× 各自失败模式的组合爆炸；消费方只见 ParseResult。降级不抛异常是本模块的隐性契约（LLM 增强/FFmpeg/OCR/单路失败全部就地消化） |
| **StorageModule** | 分片持久化：Embedding 分批（32/批、重试 3 次）→ MySQL 批写 → Milvus 批插（ids/vectors 严格对齐），父子分片聚合参数透传 | `storage/StorageService.java`、`storage/StorageServiceImpl.java`（消费 chunk-management 的 ParentChunkAssembler 与 infra MilvusService） | **深**：吞掉「无跨库事务」下的最终一致细节——无向量批次跳过 Milvus 防错位、MyBatis BATCH 自管事务不用 Spring 事务（:47-49）、chunkCount 单侧统计由消费端对账补偿 |
| **FileLifecycleModule** | 文件生命周期操作面：列表/回收站、软删/恢复/永久删除/清空、复制、跨库移动、五类衍生数据清理、预览、状态查询 | `service/FileService.java`、`service/impl/FileServiceImpl.java`（upload/process 触发面归 Pipeline 视角，此处指其余方法） | **深（清理面）**：cleanupFileData 五路清理（分片/向量/图谱/MinIO 前缀/QA+文件级策略）+ 降级记账，是「删除测试通过」级能力；操作面本身偏 CRUD。清理逻辑与 KbServiceImpl 的 KB 级删除清理存在平行实现（§4 登记收敛候选） |
| **FileMetadataModule** | 文档业务元数据：查看/全量更新（事务内回填 kb_chunk 冗余列）/标签替换与批量/存量补抽 | `service/FileMetadataService.java`、`service/impl/FileMetadataServiceImpl.java`、`model/FileMetadata*.java`、`model/FileBatchTagRequest.java` | **中**：状态机（none/partial/full/revised 与 force 语义）是真实策略；标签维护复用 kb-core.TagConsistencyService 语义。检索列回填使元数据与检索过滤闭环 |
| **UploadModule (S)** | 两阶段上传的第一阶段：MinIO 落盘 + kb_file pending 登记 + 类别识别；另含平台通用图片上传口 | `service/impl/FileServiceImpl.java`（upload :119-148、detectCategory :906-914）、`fastrag-platform/.../UploadController.java` | **浅**：单方法直线逻辑，无隐藏不变式；无校验是现状而非策略（§4 建议深化）。UploadController 透传 MinIO，两个校验即全部逻辑 |
| **FileReadApiModule (S)** | HTTP 读面：下载族三口（原文件/音频切片/页图）、处理状态查询、Markdown 读写端点、预览端点 | `controller/FileController.java`（download :379-464、status :344-348、markdown :152-197、preview :350-365） | **浅**：MinIO 直读 + Content-Type 映射的薄包装；Markdown 端点的策略在 MarkdownService，预览的策略在 FileServiceImpl.previewChunks |

数据面：kb_file（schema.sql:105-142）、kb_folder（:144-153，folderId 引用目标）；MQ 消息与 MinIO 对象键约定为运行时结构（contract/data.md `## file-ingestion`）。kb_chunk 的写入发生在 StorageModule，但该表的数据契约归 chunk-management 链（届时另立）。

## 2. Interface Contracts

### IngestionPipelineModule

- Public interface：
  - `process(kbId, fileId, FileProcessRequest)` — 触发处理：持久化模式/绑定/配置快照后投递消息〔FileService.java:33、FileServiceImpl.java:151-200〕
  - `retryFile(kbId, fileId)` — 清理重置后复用快照重触发〔FileService.java:49〕
  - `reChunkFile(kbId, fileId, strategyId[, presetStrategy])` — 重切入口（绑定语义归 parse-strategy 守卫，物理重跑归本模块）〔FileService.java:55,62〕
  - `publishIngestion(message)` — MQ 投递 + 不可用/失败双路同步降级〔MessagePublisher.java:81-103〕
  - `handleIngestion(message)` — 队列消费入口：幂等 → 十步编排 → completed/failed〔IngestionConsumer.java:113-546〕
  - `persistParsedMarkdown(kbId, fileId, extension, ParseResult)` — Markdown 落盘（永不抛出）〔MarkdownService.java:52-70〕
- Hidden implementation：状态里程碑推进与 stage 截断（:730-742）、processingConfig→ParseOptions 翻译（:552-598）、OCR 并发限流与按页归并保序（:279-334、628-728）、manual 分片对账（:753-782）、通知/日志降级（:464-481）、重试循环与终态（:156,526-544）。
- Depth rationale：上游（控制器/重试/移动/OnlyOffice 回调）只表达「处理这个文件」；异步性、幂等、进度、降级、补偿全部在缝后。消息 Map 的字段集是缝上的稳定词汇（fileId/kbId/objectKey/strategyId/operator/processingMode/enableGraphBuild/processingConfig）。

### FormatParserModule

- Public interface：
  - `parse(stream, extension, strategyId[, options]) → ParseResult` — 管线入口：按 id 装载策略后路由〔DocumentParser.java:19-21、DocumentParserImpl.java:89-92〕
  - `parse(stream, extension, KbParseStrategy, options) → ParseResult` — 预览场景：未落库临时策略对象直接解析（自定义面板用）〔DocumentParser.java:26、DocumentParserImpl.java:97〕
  - `extractGeometryLines(doc, pageIndex) → PageLine[]` — 公开行几何（AI分片 rect 装配与解析共用同一行结构）〔DocumentParserImpl.java:255-316〕
  - MediaExtractor：`extractAudio` / `extractKeyframes` / `deduplicateByHash` / `splitAudio` / `cropMedia` / `extractPdfImages` — FFmpeg 进程与 PDF 图片提取的唯一封装〔MediaExtractor.java:78,105,170,214,290,353〕
  - `ImageFilter.isDecorativeBy*` / `OcrNoiseFilter.sanitize` — 纯函数过滤规则（解析期与消费端共用）〔ImageFilter.java:59-80、OcrNoiseFilter.java:47-80〕
- Hidden implementation：各格式私有解析（parsePdf 双链路/parseDocx 结构化/parseExcel 表头检测/parseVideo 双路合并等 20+ 私有方法）、扩展名→方法推断、字符集探测链、OMML→LaTeX、pHash 去重、LLM 增强与 ModelRecord 解析（:229-233、2744-2795）。
- Depth rationale：新增一种格式只加一个 parseXxx 与 Registry 一行映射；调用方对格式库、降级路径、阈值常量全盲。ParseResult 是缝上的稳定词汇。

### StorageModule

- Public interface：
  - `storeChunks(kbId, fileId, chunks[, childChunkLength[, maxParentLength, parentAggLevel]])` — 三参递进：普通/父子缺省聚合/父子全参〔StorageService.java:7-27〕
  - `storeChunkBatch(kbId, fileId, chunks, batchStart)` — 并行分批口（预留）〔StorageService.java:37〕
- Hidden implementation：collection 名归一（连字符转下划线）、ModelRecord 在线模型解析、Embedding 分批重试、无向量索引跳过、父分片先落库不向量化、chunkCount 单侧统计。
- Depth rationale：消费方不感知「MySQL 与 Milvus 无分布式事务」这一现实；批次错位防护与补偿对账都在模块内。

### FileLifecycleModule

- Public interface：
  - `list/listDeleted(kbId)`、`getProcessingStatus(kbId, fileId)`、`previewChunks(kbId, fileId, strategyId, customConfig)`〔FileService.java:30-31,40,48〕
  - `update(kbId, fileId, patch)`（含 ADR-0001 改绑拦截）、`copy`、`moveToKb`、`delete/restore/permanentDelete/emptyRecycleBin`〔FileService.java:34-39,63〕
  - `replaceOriginalFile(kbId, fileId, bytes, contentType)` — OnlyOffice 复用的原文件替换口〔FileService.java:78、FileServiceImpl.java:280-314〕
- Hidden implementation：五路清理（cleanupFileData :565-620）与降级记账、软删走原生 SQL（:671）、moveToKb 的事务边界与 MinIO 迁移（:728-796）、预览的前 20/300 字符裁剪（:857-874）、列表批量标签装配（:1013-1039）。
- Depth rationale：清理的完整性（五类衍生数据一个不漏）与「失败不阻断、部分失败可重入」是本模块真正的隐藏复杂度；操作面方法本身是浅的。

### FileMetadataModule

- Public interface：`getMetadata` / `updateMetadata`（写即 revised/manual，事务回填 chunk 冗余列）/ `setFileTags`（替换式）/ `batchSetTags`（增量加删带汇总）/ `extractMetadata`（仅覆盖 none/partial，force 强制，失败置 partial）〔FileMetadataService.java:21-34〕
- Hidden implementation：规则抽取器、chunk 冗余列回填的事务边界、标签差量经 TagRelationHelper。
- Depth rationale：状态机（none→partial/full→revised）与「人工校订不被自动覆盖」的不变式是唯一需要保护的东西。

### HTTP 暴露面（实现细节，不立契约）

FileController 26 端点（`/api/kb/{kbId}/files`：list :80、deleted :86、upload :92、process :110、retry :122、re-chunk :129、markdown GET :157 / PUT :174、update :199、metadata GET :225 / PUT :235、tags PUT :246、batch-tags :261、metadata/extract :271、delete :283、restore :300、permanent :308、recycle-bin :316、copy :324、move :331、processing-status :344、preview :350、strategy :371、download :379、segments :418、images :449；读 viewer 写 editor + @Loggable + @KbAuth）；UploadController `/api/upload`（UploadController.java:45）。按端点排除铁律全部不立契约；其中 re-chunk/preview/strategy 三端点的绑定面契约已在 parse-strategy 链登记，本特性不重复。

## 3. Seam Diagram

### 缝 1：格式解析路由缝（真缝）

- **What varies**：文档格式与解析实现（pdf/doc/docx/pptx/xlsx/文本/图片/音视频及其内部库选择）
- **Adapters**：parsePdf / parseDoc / parseDocx / parsePptx / parseExcel / parseDefault / parseVideo / parseAudio / parseImage 九个私有解析器（DocumentParserImpl.java:108-118 switch）——2+ 适配器，真缝
- **Stable side**：调用方（IngestionConsumer :172、previewChunks :842-844）与 ParseResult 数据形状；parseMethod（策略显式）与扩展名（Registry 推断）是缝上的两个路由键
- **Placement rationale**：路由放解析器内部而非消费侧，使「格式识别失败→default 兜底」成为模块内聚决策；消费方永远拿到合法 ParseResult 或统一异常

### 缝 2：AI 能力降级缝（真缝）

- **What varies**：OCR 引擎（deepseek/paddle）、ASR 引擎（funasr/whisper）、LLM 增强模型（策略 llmModel→ModelRecord 动态路由）及其可用性
- **Adapters**：OcrService.recognize / AsrService.transcribe / LlmService.chatWithTimeout（fastrag-ai 层），外加「不用该能力」的降级路径（OCR 失败跳过该图 :305-307、ASR 失败转纯关键帧 :2494-2496、关键帧失败转纯 ASR :2526-2528、LLM 失败回退原文 :2770-2773）——2+ 适配器，真缝
- **Stable side**：ParseResult 的产出义务——任一 AI 能力失效都必须仍返回可用结果
- **Placement rationale**：降级决策贴近调用点（每处失败语义不同：图级跳过 vs 路级切换 vs 文本级回退），上收为统一网关反而丢失粒度

### 缝 3：媒体工具进程缝（边界缝）

- **What varies**：FFmpeg 二进制可用性与音视频处理操作（提音轨/采样关键帧/切分/裁剪）
- **Adapters**：MediaExtractor 的四个 FFmpeg 操作 + 「不可用」直通降级（extractAudio 直传原始字节 :90-92、extractKeyframes 返回空 :113-116）——操作 2+ 个，真缝（外部进程即边界）
- **Stable side**：byte[] 进、byte[]/List 出的纯内存语义；调用方不知道 FFmpeg 存在与否
- **Placement rationale**：FFmpeg 是系统边界（外部进程，路径可配 `ai.ffmpeg.path` :43-44），mock 点天然成立；降级在封装内完成使上层零感知

### 缝 4：MQ→同步降级缝（真缝）

- **What varies**：投递通道（RabbitMQ 异步 / 进程内同步直调）
- **Adapters**：rabbitTemplate.convertAndSend 与 ingestionHandler.handleIngestion 直调（MessagePublisher.java:81-103，先探活 :66-78 再投递、失败再降级）——2 适配器，真缝
- **Stable side**：调用方（FileServiceImpl.process/moveToKb）与消息 Map 形状；消费逻辑同一份（IngestionHandler 双实现同签）
- **Placement rationale**：MQ 宕机不阻断业务是摄取可用性的底线；降级收在 publisher 内使所有触发方（上传/重试/重切/移动）免费获得同一保障

### 缝 5：分片策略分派缝（上游缝，本特性为消费侧）

- parse-strategy/design 缝 1 已立：ChunkingService 五分片器按 `ParseStrategyConfig.chunk.strategy` 分派（IngestionConsumer.java:184-236）。本特性不重复立缝，仅声明消费关系：分派发生在流水线内、参数来自 StrategyConfigResolver。

### 缝 6：三分存储清理缝（上游缝，本特性为主要消费者）

- parse-strategy/design 缝 3 已立（Milvus/GraphStore/chunkMapper 三载体清理）。FileLifecycleModule 的 cleanupFileData 是该缝的全量版（+MinIO 前缀/QA/文件级策略五路，:565-620），KB 删除场景的平行实现见 §4。

## 4. Deepening Opportunities

- **IngestionPipelineModule**：把散布的 8 处 updateStatus 调用收敛为流水线内的状态机对象（阶段序+合法迁移内聚，当前靠字符串散写）；把 processingConfig↔ParseOptions↔FileProcessRequest 三方翻译收敛为一个类型化「处理快照」值对象（当前 FileServiceImpl.rebuildProcessRequest 与 IngestionConsumer.buildParseOptions 两处各翻一次，字段清单漂移风险）。
- **FormatParserModule**：把 enhanceWithLlm/resolveLlmConfig 下沉为独立 LlmEnhancer 组件（PDF/DOCX/PPTX/Excel/音视频/图片六处重复同一降级模式）；扫描页 50 字符阈值、LLM 5000 字符上限、15 秒超时等常量配置化（当前硬编码）。
- **FileLifecycleModule**：cleanupFileData 与 KbServiceImpl.delete 的逐文件清理（kb-core 链 data.md 已登记其 MinIO 双删逻辑）是同一「文档衍生数据清理」语义的两份实现——收敛为共享 DocumentPurgeService，KB 删除与文件删除共用一套五路清理。
- **UploadModule (S)**：若要深化，最小路径是上传时经 ParseMethodRegistry.isSupported 做扩展名预警（非阻断），把「上传任意类型」从隐式行为变为显式策略；维持浅亦可接受。
- **FileMetadataModule**：规则抽取器与 KB custom_attr_schema 的耦合可外置为可注册抽取器列表（当前内聚在 Impl，规则增长会膨胀）。

## 5. Parent Trace

| Design 单元 | Spec REQ |
|---|---|
| UploadModule (S) | REQ-A01、A02、A03、A04 |
| IngestionPipelineModule（process/retry 触发面） | REQ-B01、B02、B03、B04、B05 |
| IngestionPipelineModule（handleIngestion 编排） | REQ-C01、C02、C03、C04、C05、C06、C07、C08、C09、C10、E01 |
| FormatParserModule | REQ-D01、D02、D03、D04、D05、D06、D07、D08、D09 |
| MarkdownService（Pipeline 内）+ FileReadApiModule markdown 端点 | REQ-E01、E02、E03、E04 |
| FileLifecycleModule | REQ-F01、F02、F03、F04、F05、F06、F07 |
| FileMetadataModule | REQ-G01、G02、G03 |
| StorageModule | REQ-C07（存储面）、§5 Embedding 韧性 |
| 缝 2 AI 能力降级 | REQ-D08、D06、C05 |
| 缝 3 媒体工具缝 | REQ-D07 |
| 缝 4 MQ 同步降级 | REQ-C10 |
| 缝 5 分派缝（消费） | REQ-C03 |
| UploadModule (S) | REQ-A02（2026-09-24 review 补锚：上传阶段不做扩展名/大小白名单校验、不自动触发处理——格式适配推迟到摄取期（未识别扩展名走纯文本兜底）） |
| UploadModule (S) | REQ-A03（2026-09-24 review 补锚：上传成功记录 file_added/file_uploaded 操作日志，日志失败不阻断上传） |
| UploadModule (S) | REQ-A04（2026-09-24 review 补锚：管理员上传品牌图（仅 jpg/png、≤1MB）即时生效并返回访问 URL） |
| IngestionPipelineModule（process/retry 触发面） | REQ-B02（2026-09-24 review 补锚：处理配置全量持久化到 kb_file.processing_config 快照，重试/重切时从快照重建请求（解析失败降级最小默认请求）） |
| IngestionPipelineModule（handleIngestion 编排） | REQ-C06（2026-09-24 review 补锚：嵌入图片 OCR 文本并入正文前经行级噪声清洗，全噪声图不入分片；仅用于嵌入图片不适用扫描件整页 OCR） |
| FormatParserModule | REQ-D03（2026-09-24 review 补锚：DOCX 结构化解析（标题层级/表格/代码块/公式 OMML→LaTeX/图片提取），DOC 走 legacy HWPF 链路，PPTX/PPT 逐形状提取并识别标题占位符） |
| MarkdownService（Pipeline 内）+ FileReadApiModule markdown 端点 | REQ-E02（2026-09-24 review 补锚：读 Markdown 按约定路径取对象，不存在/未生成返回 markdown_not_ready 语义错误并提示文档类范围） |
| MarkdownService（Pipeline 内）+ FileReadApiModule markdown 端点 | REQ-E04（2026-09-24 review 补锚：Markdown 对象键不落库——路径约定定位，实体仅设 transient 字段，避免 DDL 迁移） |
| FileLifecycleModule | REQ-F06（2026-09-24 review 补锚：下载族三口——原文件下载（双校验+Content-Type/UTF-8 文件名）、音频切片下载、PDF 页面图片下载；对象缺失返回 404 语义） |
| FileLifecycleModule | REQ-F07（2026-09-24 review 补锚：处理状态查询返回 fileId/status/progress/stage 轮询四字段；分块预览下载-解析-分块返回前 20 块、截断 300 字符、不落库） |
| FileMetadataModule | REQ-G02（2026-09-24 review 补锚：单文件标签为替换式设置；批量打标为增量加/删并返回处理汇总） |

## ADR 引用指针

本文所引 **ADR-0001**（解析策略绑定变更必须触发重新分片）与 **ADR-0002**（图存储收敛 Neo4j——GraphStore 唯一实现、启动 fail-fast）原文已随根 docs/ 树删除丢失，全文重构版见 [adr/0001-strategy-binding-requires-rechunk.md](adr/0001-strategy-binding-requires-rechunk.md) 与 [adr/0002-graph-store-consolidate-on-neo4j.md](adr/0002-graph-store-consolidate-on-neo4j.md)（2026-09-24 由链内要点源重构，不可考处 [GAP] 标注）；本文正文「仅引要点」处即指该两文。
