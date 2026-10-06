# 文件上传与摄取流水线（file-ingestion）— 实现文档

## 1. Code File Map

```
fastrag-modules/fastrag-knowledge/src/main/java/com/fastrag/module/knowledge/
├── controller/
│   └── FileController.java            /api/kb/{kbId}/files 26 端点（上传/处理/重试/重切/markdown 读写/元数据/回收站/复制/移动/下载族/状态/预览/strategy）
├── service/
│   ├── FileService.java               文件生命周期服务接口（17 方法）
│   ├── FileMetadataService.java       元数据管理接口（查看/更新/标签/批量/补抽）
│   ├── MarkdownService.java           Markdown 落盘（资格集 + 永不抛出）
│   └── impl/
│       ├── FileServiceImpl.java       触发面（process/retry/reChunk）+ 生命周期操作 + 五路清理 + 自动匹配（parse-strategy 宿主）
│       └── FileMetadataServiceImpl.java  元数据状态机 + chunk 冗余列回填 + 规则补抽
├── consumer/
│   └── IngestionConsumer.java         @RabbitListener(fastrag.ingestion.queue)：十步流水线编排（793 行）
├── storage/
│   ├── StorageService.java            分片存储接口（4 方法）
│   └── StorageServiceImpl.java        Embedding 分批重试 + MySQL 批写 + Milvus 批插 + 父子聚合透传
├── parser/
│   ├── DocumentParser.java            解析入口接口（3 重载）
│   ├── DocumentParserImpl.java        八格式路由与实现（2837 行）+ LLM 增强 + 字符集链
│   ├── ParseMethodRegistry.java       扩展名→方法推断（parse-strategy 链权威映射，本特性消费）
│   ├── MediaExtractor.java            FFmpeg 进程封装（提音轨/关键帧/pHash 去重/切分/裁剪/PDF 图片）
│   ├── ImageFilter.java               装饰性图片五规则（纯函数，解析期+消费端共用）
│   ├── ImageDedup.java                图片频次去重
│   ├── MarkdownSerializer.java        DocNode → Markdown 序列化
│   └── ParseResult.java / DocNode.java / ParseOptions.java   解析产物与一次性处理选项模型
├── util/
│   └── OcrNoiseFilter.java            嵌入图片 OCR 行级噪声清洗（纯函数）
├── entity/KbFile.java                 kb_file 实体（transient markdownObjectKey）
├── mapper/KbFileMapper.java 等        file/chunk/qaPair/strategy/kb/tag 六 mapper
├── model/FileProcessRequest.java      上传向导处理配置（快照载体）
fastrag-modules/fastrag-platform/.../controller/UploadController.java   /api/upload 通用图片口（jpg/png≤1MB）
fastrag-infra/src/main/java/com/fastrag/infra/
├── minio/MinioService.java            对象存储边界（upload/download/delete/deleteByPrefix/copy）
└── rabbitmq/                          RabbitMQConfig（两队列+绑定+JSON 转换器）、MessagePublisher（投递+同步降级）
init-scripts/schema.sql                kb_file(:105-142)、kb_folder(:144-153)、kb_chunk(:155-186)、增量列(:1400-1412)
src/test/java/com/fastrag/module/knowledge/
├── parser/ 12 个测试                  PDF 标题/段落/图片、DOCX 图片、PPT 探测、Legacy、Excel 冒烟、LLM 表头、ImageFilter/ImageDedup、Registry
└── util/OcrNoiseFilterTest.java       噪声清洗 5 例
```

镜像说明：IngestionPipelineModule 与 FileLifecycleModule 共居 FileServiceImpl/IngestionConsumer（触发面 vs 编排面）；UploadModule (S) 两处落点（FileServiceImpl.upload + UploadController）；FileReadApiModule (S) 全在 FileController。

## 2. Interface Implementations

### IngestionPipeline（from contract: file-ingestion.IngestionPipeline）

**Logic Flow（process 触发面，FileServiceImpl.process :151-200）:**
1. 按 fileId+kbId 查文件；无 → RuntimeException「File not found」。
2. processingMode = req 值或缺省 "chunk"；strategyId = resolveRequestedStrategy（请求指定且属本库/本文件则用之，否则四级自动匹配——归 parse-strategy 守卫）。
3. 持久化三份状态：processingMode、parseStrategyId、processingConfig=JSON(req)。
4. 图谱开关：IF updatedAt 在 createdAt 之后（用户显式设过）THEN 保持原值 ELSE 按 KB.graphAutoBuild==1 解析（null/0 → 0）。
5. updateById 后组装 8 字段消息 Map，operator 取会话用户名否则 "system"；messagePublisher.publishIngestion(msg)。

**Logic Flow（publishIngestion，MessagePublisher :81-103）:**
1. IF 探活（createConnection 失败/关闭）THEN 同步直调 ingestionHandler.handleIngestion(msg)（自身异常仅 error 记账）并 RETURN。
2. rabbitTemplate.convertAndSend(fastrag.direct, "ingestion", msg)；异常 → 再次同步直调（双重保障）。

**Logic Flow（handleIngestion 编排，IngestionConsumer :113-546）:**
1. 解包消息 5 基础字段；幂等检查：文件不存在 → return；deletedAt 非空 → return；status=completed → return（全部 ack 不回队）。
2. 记开始日志（失败仅 warn）；buildParseOptions 从 processingConfig 翻译 ParseOptions（engineConfig→ocr/asr/video/keyframe，mediaConfig.timeRanges→Map<fileName,[start,end]>；解析失败返回 null 用默认）；parseRetryCount 取重试次数（缺省 0）。
3. FOR attempt IN 0..retryCount：
   - updateStatus(processing,10,downloading) → MinIO 下载全部字节 → 取扩展名（查库，兜底 "txt"）。
   - updateStatus(30,parsing) → documentParser.parse(bytes, ext, strategyId, options)；异常上抛进重试。
   - updateStatus(60,chunking) → config=configResolver.resolve(strategyId)，取 chunk.strategy 分派：hasSegments → chunkBySegments（音视频固定时间分段）；parent_child+hasNodes → structuralChunk（带父子三参）；rule_recursive → recursiveChunk；semantic → semanticChunk（KB embeddingModel 兜底，查库失败 warn）；structure_aware+hasNodes → structuralChunk；缺省 rule_fixed → hasNodes ? structuralChunk : chunk。
   - 音频（扩展名 ∈ 7 种 AUDIO_EXTENSIONS）且 hasSegments：updateStatus(70,slicing) → mediaExtractor.splitAudio；数量==分片数逐段上传 segments/{i}.{ext}，否则 warn 跳过；异常仅告警。
   - PDF：updateStatus(75,extracting images) → PDFBox 重新加载 → extractPdfImages 上传 → 装饰图兜底复查 → 5 线程+Semaphore(5) 并行 OCR（单图失败跳过）→ OcrNoiseFilter.sanitize 非空才入 map → mergePdfImageChunksByPage 按页归并（TreeSet 页序、页内文本先图片后、重排 index/id）。
   - DOCX/PPT 内嵌图片（parseResult.images 非空）：同模式上传+并行 OCR+按页归并（无页码追加末尾）；上下文标题拼「【context】\n」前缀。
   - updateStatus(80,storing) → childChunkLength>0 ? storeChunks(三参) : storeChunks(普通)。
   - reconcileManualChunks：manual 分片 chunkIndex 重排到非 parent 的 auto 数之后，file.chunkCount=auto+manual（异常 warn）。
   - 记更新日志 + 插入 SysNotification（knowledge_update/unread；失败 warn）。
   - enableGraphBuild 三类型解析（Boolean/Number/其他）为真 → publishGraphBuild({kbId,fileId,mode:incremental})（ObjectProvider 取 publisher，缺失/异常 warn）。
   - markdownService.persistParsedMarkdown（失败 warn 不阻断）→ updateStatus(completed,100,done) → RETURN。
   - CATCH：attempt<retryCount → updateStatus(pending,0,retrying) 并 continue；否则失败日志 + updateStatus(failed,0,"error:"+message)（stage 截断 60 字符）。

**Logic Flow（retryFile，FileServiceImpl :317-348）:** 查文件 → cleanupFileData 五路清理 → 重置 pending/0/空 stage/chunkCount=0 → processingMode 空白时补 "chunk" 并顺手解析图谱默认 → updateById → process(kbId, fileId, rebuildProcessRequest(f))（快照重建，parseStrategyId 以文件当前绑定覆盖快照历史值）。

**Logic Flow（reChunkFile，:369-482）:** 重切编排（守卫三分支→预设解析→换绑→定向清理→重置→重发）细节全文见 implement/chunk-management.md#RechunkOrchestrator，此处不复述。

**Logic Flow（persistParsedMarkdown，MarkdownService :52-70）:** 扩展名 ∉ 15 种文档类 → return；md 空 → return；上传 {kbId}/{fileId}/parsed.md（text/markdown）；CATCH 全部异常仅 warn——任何路径不抛出。

**Module Interactions:**
- Calls：MinioService（下载/上传/删除/前缀删/copy）、DocumentParser、ChunkingService（5 分片器）、StorageService、StrategyConfigResolver、MediaExtractor、OcrService、MarkdownService、LogService（publish 模块）、SysNotificationMapper（platform 表直插）、GraphService（图谱清理）、MilvusService（向量清理）、MessagePublisher（自身触发面）、ParseStrategyService（文件级策略级联删）。
- Called by：FileController 5 端点（process/retry/re-chunk/markdown PUT 间接）、moveToKb 内部、MessagePublisher 同步降级路径、OnlyOffice 回调链（replaceOriginalFile+reChunkFile）。
- Shared state：kb_file（status/progress/stage/processing_*）、kb_chunk、MinIO 对象（原件/segments/images/parsed.md）、RabbitMQ fastrag.ingestion.queue 与 fastrag.graph-build.queue、sys_notification、kb 更新日志表（经 LogService）。

**Decisions & Deviations:**
- 契约内「辅助步骤失败不改终态」在实现中通过每步独立 try/catch + warn 落地，与 contract Error modes 一一对应，无偏离。
- 消息 enableGraphBuild 缺省语义：javadoc 写「默认 true」（:63），实现 `getOrDefault("false")`（:486）——javadoc 过期，以实现为准（登记）。
- priority 字段全程落库不消费（REQ-B03 登记为现状而非缺陷）。
- 关键取舍：幂等以 DB 状态为准而非 MQ 去重表（零额外设施）；失败不回队（避免毒丸消息死循环，:129 注释明示）。

**Parent Trace:** [process/publishIngestion/handleIngestion/retryFile/reChunkFile/persistParsedMarkdown] → [file-ingestion.IngestionPipeline] → [Design.IngestionPipelineModule] → [REQ-B01~B05、C01~C10、E01]。

### DocumentParser（from contract: file-ingestion.DocumentParser）

**Logic Flow（路由，DocumentParserImpl :97-123）:**
1. method = strategy.parseMethod；IF 空或 "default" THEN method = ParseMethodRegistry.resolveByExtension(extension)。
2. SWITCH method → parsePdf/parseDoc/parseDocx/parsePptx/parseExcel/parseVideo/parseAudio/parseImage/default（parseDefault 纯文本）。
3. CATCH 全部 → 包装 RuntimeException「文档解析失败: …」上抛（唯一上抛点）。

**Logic Flow（parsePdf 双链路，:133-242）:**
1. 逐页 extractGeometryLines（基线差>2pt 换行、x 间隙>max(6pt,1.5×字号) 插 \t 列标记）；页文字 <50 字符 → 渲染 200dpi PNG → OcrService.recognize（ocrEngine 透传），OCR 文本转伪几何行；OCR 也失败 → 保留几何行。
2. 两链路分别清页眉脚/页码（几何行走 stripRowHeaderFooters 频次+边缘区，OCR 文本走 stripHeadersFooters 行频次）。
3. 页标题：每页首个标题样行，无则继承上页（跨页章节延续）。
4. assemblePageNodes 逐页装配 DocNode（段落 flush、tab 表格、Markdown 表格块）。
5. markdownSerializer.serialize → IF strategy.llmModel 非空 THEN enhanceWithLlm；返回 ParseResult{markdown, nodes, pages, pageTitles}。

**Logic Flow（enhanceWithLlm，:2744-2774）:** apiUrl 空 → 原文；text 空 → 原文；>5000 字符 → 原文（注释：超时风险/收益有限）；chatWithTimeout(model, prompt, false, 15s)；空响应/异常 → 原文。resolveLlmConfig 按 model code + status=online 查 ModelRecord（查无 warn 返回空配置）。

**Logic Flow（parseVideo 双路，:2443-2533）:**
1. 关键帧间隔 = options.keyframeInterval > parse.keyframeIntervalSeconds > 10；hashThreshold = parse.keyframeHashThreshold > 10；videoStrategy = options > "keyframe_asr"。
2. timeRanges 命中 fileName → cropMedia 裁剪（失败全长）。
3. IF ≠uniform_sample：extractAudio（FFmpeg 不可用直传原始字节）→ AsrService.transcribe；失败 error 后继续关键帧路。
4. IF ≠asr_only：extractKeyframes（FFmpeg 不可用/失败 → 空表）→ deduplicateByHash(pHash) → 逐帧 OCR（单帧失败跳过）。
5. mergeSegmentsWithKeyframes：ASR 空且有关键帧 → 关键帧成片（时长按采样间隔估算）；关键帧空 → 纯 ASR 段；双全 → 每 ASR 段找时间最近关键帧（NavigableMap floor/ceiling 取近）合并「【画面内容】+【语音内容】」；llmModel 非空对 fullText 增强（失败原文）；返回 segments 供 chunkBySegments。

**Logic Flow（parseDefault 编码链，:2344-2419）:** options.encoding 显式 → 指定 Charset；否则 UTF-8 严格解码（REPORT + 替换字符复查）→ 失败 → detectCharset 字节统计探测（BOM/中文域启发）→ 兜底。Excel：detectAndCreateWorkbook 兼容 xls/xlsx；加密错误模式识别（isEncryptedWorkbookError）为专用文案；表头区域 HeaderRegion 检测（多行合并/隐藏行列/LLM 表头合并 LlmHeaderFullFlow）。

**Module Interactions:**
- Calls：OcrService、AsrService、LlmService（fastrag-ai）、ModelRecordMapper（platform 模型表）、MinioService（PDF 图片经 MediaExtractor 落对象）、StrategyConfigResolver（video 参数）、MediaExtractor、MarkdownSerializer。
- Called by：IngestionConsumer（主链）、FileServiceImpl.previewChunks（策略 null 或临时策略对象两态）、AiChunkService（OCR 解析，跨特性）。
- Shared state：无自有表——纯函数式转换层；仅经 mapper 只读 kb_parse_strategy 与 model。

**Decisions & Deviations:**
- 与契约一致：路由双键、统一异常、五类降级均有落点，无偏离。
- 关键取舍：PDF 图片在 IngestionConsumer 重新 loadPDF 提取（解析器已产 nodes/文本）——一次冗余解析换解析器与图片提取的职责解耦（图片对象上传归消费侧）；「嵌入图片 OCR 噪声过滤 vs 扫描页豁免」的双轨在 OcrNoiseFilter javadoc 固化（宁缺勿滥原则）。
- enhanceWithLlm 的 15 秒流式超时（区别于非流式 30s）为管线阻塞专门设置（:2761-2763 注释）。

**Parent Trace:** [parse×3/parsePdf/parseDocx/parseDoc/parsePptx/parseExcel/parseDefault/parseVideo/parseAudio/parseImage/enhanceWithLlm/MediaExtractor×6] → [file-ingestion.DocumentParser] → [Design.FormatParserModule（缝 1/2/3）] → [REQ-D01~D09]。

### FileLifecycleModule / FileMetadataModule / UploadModule (S)（design 模块，无契约，实现要点）

- **五路清理（cleanupFileData :565-620）**：chunk 删库 → Milvus deleteByFileId（collection 名连字符转下划线，注释明示防删错 collection）→ GraphService.deleteFileGraph → MinIO 原件 + `{kbId}/{fileId}` 前缀双删 → QA 对删除 → 文件级策略级联删；五路各自 try/catch warn 不阻断。软删除仅清图谱 + 原生 SQL 置 deleted_at（:662-672）；moveToKb @Transactional 内完成「清理→MinIO copy+delete→改归属→重置→发消息」（发消息失败 warn 不回滚，:787-792）。
- **元数据（FileMetadataServiceImpl）**：写即 revised/manual，事务内回填 chunk 冗余列（契约 G01）；补抽仅 none/partial、force 强制、失败置 partial。
- **上传（upload :119-148）**：MinIO 失败 → RuntimeException「文件上传到存储失败」（唯一阻断点）；其余直线。UploadController：contentType 白名单 + 1MB 两校验后直传。
- **登记债务（2026-09-24 裁决补登）**：① 回收站保留期裁决为 30 天自动清理（spec §5），现状无 @Scheduled 清理任务，需新增回收站过期扫描；② 上传入口无扩展名/大小业务校验（multipart 200MB 兜底，application.yml:60-62），前端白名单前置，直调 API 可绕过；③ kb_file.duration/pages 摄取完成回填（解析已产出 totalPages 未写回，duration 需媒体探测）——三项均待排期。

## 3. Testing

引用 tdd/file-ingestion.md 八切片。**现有覆盖**：S5 部分覆盖——parser/ 12 测试（PdfPageTitleTest/PdfParagraphAssemblyTest 覆盖 PDF 标题继承与行装配；PdfImageExtractionTest/PdfImagePositionTest 覆盖图片提取与坐标；DocxImageParseTest/ExcelParserSmokeTest/LegacyFormatParseTest/LlmHeaderFullFlowTest/PptxExtractionProbeTest 覆盖各格式冒烟；ImageFilterTest/ImageDedupTest/ParseMethodRegistryTest 覆盖纯函数层）+ util/OcrNoiseFilterTest 5 例覆盖清洗规则。**缺口**：IngestionConsumer 编排全貌（幂等/状态机/重试/对账/降级）、FileServiceImpl 触发面与生命周期操作、MessagePublisher 同步降级、MarkdownService、FileMetadataService、视频双路合并与 FFmpeg 降级、enhanceWithLlm 三降级分支——全部未实施，按 tdd §1 优先级自 S1 补起。
