package com.fastrag.module.knowledge.consumer;

import com.fastrag.common.handler.IngestionHandler;
import com.fastrag.ai.ocr.OcrService;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.infra.rabbitmq.MessagePublisher;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.module.knowledge.chunking.ChunkData;
import com.fastrag.module.knowledge.chunking.ChunkingService;
import com.fastrag.module.knowledge.config.StrategyConfigResolver;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.model.FileProcessRequest;
import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import com.fastrag.module.knowledge.parser.DocumentParser;
import com.fastrag.module.knowledge.parser.ImageFilter;
import com.fastrag.module.knowledge.parser.MediaExtractor;
import com.fastrag.module.knowledge.parser.ParseOptions;
import com.fastrag.module.knowledge.parser.ParseResult;
import com.fastrag.module.knowledge.storage.StorageService;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.module.platform.entity.SysNotification;
import com.fastrag.module.platform.mapper.SysNotificationMapper;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档摄入消费者（RabbitMQ Consumer），负责文件上传后的完整处理流水线。
 *
 * <p>核心职责：
 * <ul>
 *   <li>监听 RabbitMQ 队列 {@code fastrag.ingestion.queue}，消费文档摄入消息</li>
 *   <li>实现文档处理完整流水线：下载 → 解析 → 分块 → 图片OCR → 存储 → 通知 → 触发图谱构建</li>
 *   <li>支持幂等性检查：文件已删除或已完成时跳过重复消息</li>
 * </ul>
 *
 * <p>消息格式（Map）：
 * <ul>
 *   <li>{@code fileId} — 文件 ID（必填）</li>
 *   <li>{@code kbId} — 知识库 ID（必填）</li>
 *   <li>{@code objectKey} — MinIO 对象存储路径（必填）</li>
 *   <li>{@code strategyId} — 解析策略 ID（可选）</li>
 *   <li>{@code operator} — 操作人（可选，默认 "system"）</li>
 *   <li>{@code enableGraphBuild} — 是否自动触发图谱构建（可选，默认 true）</li>
 * </ul>
 *
 * <p>关键处理流程：
 * <ul>
 *   <li>1. 幂等性检查（文件是否存在、是否已完成）</li>
 *   <li>2. 从 MinIO 下载文件字节</li>
 *   <li>3. 通过 {@link DocumentParser} 解析文档（PDF/DOCX/PPT/Excel/音频等）</li>
 *   <li>4. 通过 {@link ChunkingService} 进行文本分块（优先结构感知分片 structuralChunk，
 *       其次时间戳分段 chunkBySegments，最后规则分块 chunk）</li>
 *   <li>5. 音频文件：按 ASR 时间戳切割音频段并上传到 MinIO</li>
 *   <li>6. PDF 图片提取：PDFBox 提取图片 → 上传 MinIO → 并行 OCR（Semaphore 限流 5 并发）
 *       → 按页归并到文本 chunk 流（保持文档顺序）</li>
 *   <li>7. DOCX/PPT 图片：上传 MinIO → 并行 OCR → 按页/文档顺序归并</li>
 *   <li>8. 通过 {@link StorageService} 存储切片（含 Embedding + MySQL + Milvus）</li>
 *   <li>9. 发送系统通知和记录操作日志</li>
 *   <li>10. 可选触发图谱构建消息</li>
 * </ul>
 *
 * <p>与其他模块的交互：
 * <ul>
 *   <li>infra 模块（{@link MinioService}）— 文件下载/上传</li>
 *   <li>knowledge 模块（{@link DocumentParser}、{@link ChunkingService}、{@link StorageService}）— 解析、分块、存储</li>
 *   <li>ai 模块（{@link OcrService}）— 图片文字识别</li>
 *   <li>publish 模块（{@link LogService}）— 操作日志记录</li>
 *   <li>platform 模块（{@link SysNotificationMapper}）— 系统通知</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionConsumer implements IngestionHandler {

    private final MinioService minioService;
    private final DocumentParser documentParser;
    private final ChunkingService chunkingService;
    private final StorageService storageService;
    private final KbFileMapper fileMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final LogService logService;
    private final SysNotificationMapper notificationMapper;
    private final MediaExtractor mediaExtractor;
    private final OcrService ocrService;
    private final StrategyConfigResolver configResolver;
    private final ObjectProvider<MessagePublisher> messagePublisherProvider;

    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "wav", "m4a", "aac", "ogg", "flac", "wma");

    @Override
    @RabbitListener(queues = "fastrag.ingestion.queue")
    public void handleIngestion(Map<String, Object> message) {
        String fileId = (String) message.get("fileId");
        String kbId = (String) message.get("kbId");
        String objectKey = (String) message.get("objectKey");
        String strategyId = (String) message.get("strategyId");
        String operator = (String) message.getOrDefault("operator", "system");

        long tStart = System.currentTimeMillis();
        log.info("Start processing file: {}, kbId: {}", fileId, kbId);

        // === 幂等性检查：文件是否已被删除或已完成 ===
        KbFile existingFile = fileMapper.selectById(fileId);
        if (existingFile == null) {
            log.warn("File {} not found (deleted before processing), skipping message", fileId);
            return;  // 消息已 ack，不回队，避免死循环
        }
        if (existingFile.getDeletedAt() != null) {
            log.warn("File {} is soft-deleted (deleted_at={}), skipping message", fileId, existingFile.getDeletedAt());
            return;
        }
        if ("completed".equals(existingFile.getStatus())) {
            log.info("File {} already processed (status=completed), skipping duplicate message", fileId);
            return;
        }
        // ================================

        // 记录处理开始日志
        try {
            logService.addLog(kbId, LogCategory.operation, ActionType.file_processing_started,
                    getFileName(fileId), "开始处理文件", operator, "success", null);
        } catch (Exception e) {
            log.warn("Failed to record processing start log for file: {}", fileId);
        }

        // 解析上传向导的处理配置（引擎/语言/编码/视频策略/时间裁剪）与失败重试次数
        ParseOptions options = buildParseOptions(message, existingFile != null ? existingFile.getName() : null);
        int retryCount = parseRetryCount(message);
        if (retryCount > 0) {
            log.info("File {} will be retried up to {} times on failure", fileId, retryCount);
        }

        for (int attempt = 0; attempt <= retryCount; attempt++) {
        try {
            // 1. 更新状态为 processing
            updateStatus(fileId, "processing", 10, "downloading");

            // 2. 从本地存储下载文件并缓存字节
            InputStream fileStream = minioService.download(objectKey);
            byte[] fileBytes = fileStream.readAllBytes();
            fileStream.close();
            String extension = getFileExtension(fileId);
            log.info("[TIMING] download complete: {} ms, file={}, size={}",
                    System.currentTimeMillis() - tStart, fileId, fileBytes.length);

            // 3. 解析文档（QA 和 chunk 模式都需要）
            long t1 = System.currentTimeMillis();
            updateStatus(fileId, "processing", 30, "parsing");
            ParseResult parseResult = documentParser.parse(
                    new ByteArrayInputStream(fileBytes), extension, strategyId, options);
            log.info("[TIMING] parse complete: {} ms, segments={}, chars={}",
                    System.currentTimeMillis() - t1,
                    parseResult.getSegments() != null ? parseResult.getSegments().size() : 0,
                    parseResult.getText() != null ? parseResult.getText().length() : 0);

            // 判断是否为音频文件
            boolean isAudio = extension != null && AUDIO_EXTENSIONS.contains(extension.toLowerCase());

            List<ChunkData> chunks = new ArrayList<>();

            // 文本切片 — 按解析策略的 advanced.chunk.strategy 分发分片器
            long t2 = System.currentTimeMillis();
            updateStatus(fileId, "processing", 60, "chunking");
            boolean hasNodes = parseResult.getNodes() != null && !parseResult.getNodes().isEmpty();
            boolean hasSegments = parseResult.getSegments() != null && !parseResult.getSegments().isEmpty();
            ParseStrategyConfig config = configResolver.resolve(strategyId);
            String strategy = config.getChunk().getStrategy(); // 默认 rule_fixed（向后兼容）
            // 父分片聚合参数（仅 parent_child 策略 + 有 DocNode 结构时启用）
            int childChunkLength = -1;
            int maxParentLength = -1;
            String parentAggLevel = null;

            if (hasSegments) {
                // 音视频 ASR：固定按时间戳分段（分片策略不适用于时间分段）
                chunks = chunkingService.chunkBySegments(parseResult.getSegments(), strategyId);
                log.info("File {} chunked into {} time-based pieces", fileId, chunks.size());
            } else if ("parent_child".equals(strategy) && hasNodes) {
                // 父子切片：结构感知分片生成子分片，存储层按标题聚合父分片
                chunks = chunkingService.structuralChunk(parseResult.getNodes(), strategyId);
                childChunkLength = config.getChunk().getChunkLength();
                maxParentLength = config.getChunk().getParentMaxChunkLength();
                parentAggLevel = config.getChunk().getParentAggLevel();
                log.info("File {} chunked into {} child chunks (parent: max={}, aggLevel={})",
                        fileId, chunks.size(), maxParentLength, parentAggLevel);
            } else if ("rule_recursive".equals(strategy)) {
                // 递归字符切分：按分隔符优先级逐级降级
                chunks = chunkingService.recursiveChunk(parseResult.getText(), strategyId);
                log.info("File {} recursively chunked into {} pieces", fileId, chunks.size());
            } else if ("semantic".equals(strategy)) {
                // 语义切片：相邻句子向量相似度断点（模型 = 策略级配置，空则用 KB 级）
                String kbEmbeddingModel = null;
                try {
                    KnowledgeBase kb = kbMapper.selectById(kbId);
                    kbEmbeddingModel = kb != null ? kb.getEmbeddingModel() : null;
                } catch (Exception e) {
                    log.warn("Failed to load KB embedding model, semantic chunking will use strategy config: {}", e.getMessage());
                }
                chunks = chunkingService.semanticChunk(parseResult.getText(), strategyId, kbEmbeddingModel);
                log.info("File {} semantically chunked into {} pieces", fileId, chunks.size());
            } else if ("structure_aware".equals(strategy) && hasNodes) {
                // 结构感知切片：DocNode 结构 + 标题上下文
                chunks = chunkingService.structuralChunk(parseResult.getNodes(), strategyId);
                log.info("File {} structural chunked into {} pieces with heading context", fileId, chunks.size());
            } else {
                // 默认（rule_fixed）：保持向后兼容 — 有节点走结构分片，否则规则分片
                if (hasNodes) {
                    chunks = chunkingService.structuralChunk(parseResult.getNodes(), strategyId);
                    log.info("File {} structural chunked into {} pieces with heading context", fileId, chunks.size());
                } else {
                    chunks = chunkingService.chunk(parseResult.getText(), strategyId);
                    log.info("File {} chunked into {} pieces", fileId, chunks.size());
                }
            }
            log.info("[TIMING] chunking complete: {} ms, {} chunks",
                    System.currentTimeMillis() - t2, chunks.size());

                // 4.5 音频切片：按 ASR 时间戳切割音频并上传到 MinIO
                long tSplitStart = System.currentTimeMillis();
                if (isAudio && hasSegments && !chunks.isEmpty()) {
                    try {
                        updateStatus(fileId, "processing", 70, "slicing");
                        List<byte[]> segments = mediaExtractor.splitAudio(
                                fileBytes, extension, parseResult.getSegments());
                        if (!segments.isEmpty() && segments.size() == chunks.size()) {
                            for (int i = 0; i < segments.size(); i++) {
                                String segKey = kbId + "/" + fileId + "/segments/" + i + "." + extension;
                                try (ByteArrayInputStream segStream = new ByteArrayInputStream(segments.get(i))) {
                                    minioService.upload(segKey, segStream, "audio/" + extension);
                                }
                            }
                            log.info("[TIMING] audio split & upload complete: {} segments, {} ms",
                                    segments.size(), System.currentTimeMillis() - tSplitStart);
                        } else {
                            log.warn("Audio split returned {} segments, expected {}, skipping",
                                    segments.size(), chunks.size());
                        }
                    } catch (Exception e) {
                        log.warn("Audio slicing failed, continuing without segment files: {}", e.getMessage());
                    }
                }

                // 4.6 PDF 图片提取 → 独立分片（并行 OCR，Semaphore 限流 5 并发）
                // 顺序保证：OCR 结果先收集到 map（并发安全），全部完成后按页归并插入文本 chunk 流，
                // 避免"图片 chunk 追加末尾"和"并发完成顺序不定"导致的文档顺序错乱
                long tPdf = System.currentTimeMillis();
                if ("pdf".equals(extension) && fileBytes != null) {
                    try {
                        updateStatus(fileId, "processing", 75, "extracting images");
                        var doc = org.apache.pdfbox.Loader.loadPDF(fileBytes);
                        try {
                            List<MediaExtractor.PdfImage> pdfImages = mediaExtractor.extractPdfImages(doc, kbId, fileId, minioService);
                            if (!pdfImages.isEmpty()) {
                                log.info("Extracted {} images from PDF", pdfImages.size());

                                // ① 并行 OCR：结果写入 map（imageKey → OCR 文本），不直接操作 chunks 列表
                                final java.util.concurrent.ConcurrentHashMap<String, String> ocrResults =
                                        new java.util.concurrent.ConcurrentHashMap<>();
                                java.util.concurrent.Semaphore ocpSemaphore = new java.util.concurrent.Semaphore(5);
                                java.util.concurrent.ExecutorService ocrExecutor = java.util.concurrent.Executors.newFixedThreadPool(5);
                                List<java.util.concurrent.Future<Void>> ocrFutures = new ArrayList<>();

                                for (MediaExtractor.PdfImage img : pdfImages) {
                                    // 兜底过滤（提取端已按 ImageFilter 规则过滤，此处防御性再查一次）
                                    if (ImageFilter.isDecorativeBySize(img.getWidth(), img.getHeight())) {
                                        continue;
                                    }

                                    final String fImageKey = img.getImageKey();
                                    final String fImgObjectKey = kbId + "/" + fileId + "/images/" + fImageKey;

                                    ocrFutures.add(ocrExecutor.submit(() -> {
                                        ocpSemaphore.acquire();
                                        try {
                                            byte[] imgBytes;
                                            try (InputStream imgStream = minioService.download(fImgObjectKey)) {
                                                imgBytes = imgStream.readAllBytes();
                                            }
                                            String ocrText = null;
                                            try {
                                                ocrText = ocrService.recognize(imgBytes, "png",
                                                        options != null ? options.getOcrEngine() : null);
                                            } catch (Exception e) {
                                                log.warn("OCR failed for image {}, skipping: {}", fImageKey, e.getMessage());
                                            }
                                            if (ocrText != null && !ocrText.isBlank()) {
                                                ocrResults.put(fImageKey, ocrText.trim());
                                            }
                                        } catch (Exception e) {
                                            log.warn("Failed to process image {}: {}", fImageKey, e.getMessage());
                                        } finally {
                                            ocpSemaphore.release();
                                        }
                                        return null;
                                    }));
                                }

                                // 等待所有 OCR 完成
                                for (java.util.concurrent.Future<Void> f : ocrFutures) {
                                    try { f.get(); } catch (Exception e) {
                                        log.warn("OCR future failed: {}", e.getMessage());
                                    }
                                }
                                ocrExecutor.shutdown();

                                // ② 按页归并：图片 chunk 插入对应页文本 chunk 之后（保持文档顺序）
                                chunks = mergePdfImageChunksByPage(chunks, pdfImages, ocrResults, parseResult);
                            }
                        } finally { doc.close(); }
                    } catch (Exception e) {
                        log.warn("PDF image extraction failed, continuing: {}", e.getMessage());
                    }
                }

                // 4.7 DOCX 图片：上传 MinIO + 并行 OCR → 独立分片（与 PDF 4.6 同模式）
                // 解析器只负责提取图片字节（ParseResult.images），上传与 OCR 由本消费方完成
                long tDocxImg = System.currentTimeMillis();
                if (parseResult.getImages() != null && !parseResult.getImages().isEmpty()) {
                    // ① 上传全部图片到 {kbId}/{fileId}/images/{imageKey}（前端下载接口通用）
                    // 解析期已按 ImageFilter 过滤 + 频次去重，此处尺寸检查为防御性兜底，
                    // 杜绝装饰性小图进入 MinIO
                    int uploaded = 0;
                    for (com.fastrag.module.knowledge.parser.ParseResult.ParseImage img : parseResult.getImages()) {
                        if (img.getWidth() != null && img.getHeight() != null
                                && ImageFilter.isDecorativeBySize(img.getWidth(), img.getHeight())) {
                            continue;
                        }
                        try {
                            String imgObjectKey = kbId + "/" + fileId + "/images/" + img.getImageKey();
                            try (ByteArrayInputStream imgStream = new ByteArrayInputStream(img.getData())) {
                                minioService.upload(imgObjectKey, imgStream, img.getContentType());
                            }
                            uploaded++;
                        } catch (Exception e) {
                            log.warn("Failed to upload docx image {}: {}", img.getImageKey(), e.getMessage());
                        }
                    }

                    // ② 并行 OCR（Semaphore 限流 5 并发）：结果收集到 map，不直接操作 chunks 列表
                    // （保证图片 chunk 按文档顺序生成，不受并发完成顺序影响）
                    final java.util.concurrent.ConcurrentHashMap<String, String> ocrResults =
                            new java.util.concurrent.ConcurrentHashMap<>();
                    java.util.concurrent.Semaphore ocrSemaphore = new java.util.concurrent.Semaphore(5);
                    java.util.concurrent.ExecutorService ocrExecutor = java.util.concurrent.Executors.newFixedThreadPool(5);
                    List<java.util.concurrent.Future<Void>> ocrFutures = new ArrayList<>();

                    for (com.fastrag.module.knowledge.parser.ParseResult.ParseImage img : parseResult.getImages()) {
                        // 兜底过滤装饰性小图（解析期已按 ImageFilter 规则过滤，此处防御性再查一次）
                        if (img.getWidth() != null && img.getHeight() != null
                                && ImageFilter.isDecorativeBySize(img.getWidth(), img.getHeight())) {
                            continue;
                        }
                        final String fImgKey = img.getImageKey();
                        final String fImgObjectKey = kbId + "/" + fileId + "/images/" + fImgKey;
                        final String fImgExt = fImgKey.contains(".")
                                ? fImgKey.substring(fImgKey.lastIndexOf('.') + 1) : "png";

                        ocrFutures.add(ocrExecutor.submit(() -> {
                            ocrSemaphore.acquire();
                            try {
                                String ocrText = null;
                                try (InputStream imgStream = minioService.download(fImgObjectKey)) {
                                    byte[] imgBytes = imgStream.readAllBytes();
                                    ocrText = ocrService.recognize(imgBytes, fImgExt,
                                            options != null ? options.getOcrEngine() : null);
                                } catch (Exception e) {
                                    log.warn("OCR failed for image {}, skipping: {}", fImgKey, e.getMessage());
                                }
                                if (ocrText != null && !ocrText.isBlank()) {
                                    ocrResults.put(fImgKey, ocrText.trim());
                                }
                            } catch (Exception e) {
                                log.warn("Failed to process image {}: {}", fImgKey, e.getMessage());
                            } finally {
                                ocrSemaphore.release();
                            }
                            return null;
                        }));
                    }

                    for (java.util.concurrent.Future<Void> f : ocrFutures) {
                        try { f.get(); } catch (Exception e) {
                            log.warn("OCR future failed: {}", e.getMessage());
                        }
                    }
                    ocrExecutor.shutdown();

                    // ③ 按文档顺序（ParseImage 列表顺序）生成图片 chunk
                    List<ChunkData> imageChunks = new ArrayList<>();
                    for (com.fastrag.module.knowledge.parser.ParseResult.ParseImage img : parseResult.getImages()) {
                        String ocrText = ocrResults.get(img.getImageKey());
                        if (ocrText == null || ocrText.isBlank()) continue;
                        String context = img.getContext();
                        String chunkContent = (context != null && !context.isBlank())
                                ? "【" + context + "】\n" + ocrText
                                : ocrText;
                        int imgPage = img.getPageNumber() != null ? img.getPageNumber() : 1;
                        ChunkData.ChunkDataBuilder chunkBuilder = ChunkData.builder()
                                .content(chunkContent).chunkType("image")
                                .pageNumber(imgPage).pageRange(String.valueOf(imgPage))
                                .imageKeys(java.util.List.of(img.getImageKey()));
                        if (context != null && !context.isBlank()) {
                            chunkBuilder.title(context).headingPath(context);
                        }
                        imageChunks.add(chunkBuilder.build());
                    }

                    // ④ 归并：PPT（有 pageNumber）按页插入文本 chunk 流；DOCX（无页码）退化按序追加末尾
                    if (!imageChunks.isEmpty()) {
                        chunks = mergeImageChunksByPage(chunks, imageChunks);
                    }

                    log.info("[TIMING] DOCX/PPT image upload & OCR: {} ms, {} images uploaded, {} ocr chunks",
                            System.currentTimeMillis() - tDocxImg, uploaded, imageChunks.size());
                }

                // 5. 存储切片（含 Embedding + MySQL + Milvus；父子切片策略启用父分片聚合）
                long t3 = System.currentTimeMillis();
                updateStatus(fileId, "processing", 80, "storing");
                if (childChunkLength > 0) {
                    storageService.storeChunks(kbId, fileId, chunks, childChunkLength, maxParentLength, parentAggLevel);
                } else {
                    storageService.storeChunks(kbId, fileId, chunks);
                }
                log.info("[TIMING] storeChunks complete: {} ms, {} chunks",
                        System.currentTimeMillis() - t3, chunks.size());

            // 6. 记录更新日志
            long t4 = System.currentTimeMillis();
            try {
                KbFile kf = fileMapper.selectById(fileId);
                String logFileName = kf != null ? kf.getName() : fileId;
                logService.addUpdateLog(kbId, "file_added", logFileName, "上传并处理了文档 " + logFileName, operator);

                SysNotification notice = new SysNotification();
                notice.setTitle("知识更新提醒");
                notice.setContent("知识库新增文件: " + logFileName);
                notice.setNotifyType("knowledge_update");
                notice.setSourceType("kb");
                notice.setSourceId(kbId);
                notice.setStatus("unread");
                notice.setCreatedAt(LocalDateTime.now());
                notificationMapper.insert(notice);
            } catch (Exception e) {
                log.warn("Failed to record update log for file: {}", fileId, e);
            }

            log.info("[TIMING] notification complete: {} ms", System.currentTimeMillis() - t4);

            // 7. 可选：自动触发知识图谱构建
            Object rawEnableGraphBuild = message.getOrDefault("enableGraphBuild", false);
            boolean enableGraphBuild = rawEnableGraphBuild instanceof Boolean
                    ? (Boolean) rawEnableGraphBuild
                    : (rawEnableGraphBuild instanceof Number
                        ? ((Number) rawEnableGraphBuild).intValue() == 1
                        : Boolean.TRUE.equals(rawEnableGraphBuild));
            log.info("[Graph] File={}, enableGraphBuild raw={} (type={}), resolved={}",
                    fileId, rawEnableGraphBuild, rawEnableGraphBuild.getClass().getName(), enableGraphBuild);
            if (Boolean.TRUE.equals(enableGraphBuild)) {
                log.info("[Graph] Condition met, about to trigger graph build for file={}", fileId);
                try {
                    Map<String, Object> graphMsg = new HashMap<>();
                    graphMsg.put("kbId", kbId);
                    graphMsg.put("fileId", fileId);
                    graphMsg.put("mode", "incremental");
                    MessagePublisher pub = messagePublisherProvider.getIfAvailable();
                    if (pub != null) {
                        log.info("[Graph] Publishing graph build message for file={}", fileId);
                        pub.publishGraphBuild(graphMsg);
                        log.info("[Graph] Graph build message published successfully for file={}", fileId);
                    } else {
                        log.warn("[Graph] MessagePublisher bean not available via ObjectProvider, cannot trigger graph build for file={}", fileId);
                    }
                } catch (Exception e) {
                    log.warn("[Graph] Failed to auto-trigger graph build for file {}: {}", fileId, e.getMessage(), e);
                }
            } else {
                log.info("[Graph] Graph build NOT triggered for file={}. Reason: enableGraphBuild={} (raw={})",
                        fileId, enableGraphBuild, rawEnableGraphBuild);
            }

            // 8. 更新状态为 completed
            updateStatus(fileId, "completed", 100, "done");

            long tTotal = System.currentTimeMillis() - tStart;
            log.info("File processing completed: {}, total time: {} ms", fileId, tTotal);
            return;

        } catch (Exception e) {
            if (attempt < retryCount) {
                log.warn("File processing failed (attempt {}/{}), retrying: {}",
                        attempt + 1, retryCount + 1, e.getMessage());
                try {
                    updateStatus(fileId, "pending", 0, "retrying");
                } catch (Exception ignored) {
                }
                continue;
            }
            log.error("File processing failed: {}", fileId, e);
            try {
                logService.addLog(kbId, LogCategory.operation, ActionType.file_processing_failed,
                        getFileName(fileId), "处理失败: " + e.getMessage(), operator, "failed", null);
            } catch (Exception le) {
                log.warn("Failed to record processing failure log for file: {}", fileId);
            }
            updateStatus(fileId, "failed", 0, "error: " + e.getMessage());
        }
        }
    }

    /**
     * 从 MQ 消息的 processingConfig（上传向导配置 JSON）构造解析选项；
     * 无配置或解析失败返回 null（解析器使用策略/系统默认值）。
     */
    private ParseOptions buildParseOptions(Map<String, Object> message, String fileName) {
        String processingConfigJson = (String) message.get("processingConfig");
        if (processingConfigJson == null || processingConfigJson.isBlank()) {
            return null;
        }
        try {
            FileProcessRequest req = JSONUtil.toBean(processingConfigJson, FileProcessRequest.class);
            if (req == null) {
                return null;
            }
            ParseOptions.ParseOptionsBuilder builder = ParseOptions.builder()
                    .language(req.getLanguage())
                    .encoding(req.getEncoding())
                    .fileName(fileName);
            if (req.getEngineConfig() != null) {
                builder.ocrEngine(str(req.getEngineConfig().get("ocrEngine")))
                        .asrEngine(str(req.getEngineConfig().get("asrEngine")))
                        .videoStrategy(str(req.getEngineConfig().get("videoStrategy")));
                Object keyframeInterval = req.getEngineConfig().get("keyframeInterval");
                if (keyframeInterval instanceof Number n) {
                    builder.keyframeInterval(n.intValue());
                }
            }
            if (req.getMediaConfig() != null) {
                Object timeRanges = req.getMediaConfig().get("timeRanges");
                if (timeRanges instanceof Map<?, ?> map && !map.isEmpty()) {
                    Map<String, double[]> ranges = new HashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        Object v = entry.getValue();
                        if (v instanceof List<?> list && list.size() >= 2
                                && list.get(0) instanceof Number s && list.get(1) instanceof Number e) {
                            ranges.put(String.valueOf(entry.getKey()),
                                    new double[]{s.doubleValue(), e.doubleValue()});
                        }
                    }
                    if (!ranges.isEmpty()) {
                        builder.timeRanges(ranges);
                    }
                }
            }
            return builder.build();
        } catch (Exception e) {
            log.warn("Failed to parse processingConfig for file {}, using defaults: {}",
                    message.get("fileId"), e.getMessage());
            return null;
        }
    }

    private String str(Object v) {
        return v != null ? String.valueOf(v) : null;
    }

    /** 失败自动重试次数（上传向导 retryCount，缺省 0；MQ 消息缺省为 0） */
    private int parseRetryCount(Map<String, Object> message) {
        String json = (String) message.get("processingConfig");
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            FileProcessRequest req = JSONUtil.toBean(json, FileProcessRequest.class);
            return req != null && req.getRetryCount() != null ? Math.max(0, req.getRetryCount()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 按页归并 PDF 图片 chunk 到文本 chunk 流（保持文档顺序）。
     * <p>修复"图片 chunk 追加末尾 + 并发 OCR 完成顺序不定"导致的顺序错乱：
     * 以页为单位合并（TreeSet 页序），每页先文本 chunk 后图片 chunk（页内保持各自顺序）；
     * 纯图片页（无文本）按页号排入正确位置。归并后重新分配 index/id 与列表位置一致。</p>
     *
     * @param textChunks  文本 chunk（ruleBasedChunk 输出，按文档顺序）
     * @param pdfImages   PDF 图片列表（按页/提取顺序）
     * @param ocrResults  OCR 结果（imageKey → 文本，并发收集）
     */
    private List<ChunkData> mergePdfImageChunksByPage(List<ChunkData> textChunks,
                                                      List<MediaExtractor.PdfImage> pdfImages,
                                                      Map<String, String> ocrResults,
                                                      ParseResult parseResult) {
        // ① 构建 页 → 图片 chunk 列表（页内按提取顺序）
        Map<Integer, List<ChunkData>> imagesByPage = new java.util.TreeMap<>();
        for (MediaExtractor.PdfImage img : pdfImages) {
            String ocrText = ocrResults.get(img.getImageKey());
            if (ocrText == null || ocrText.isBlank()) continue;
            int pageNum = img.getPageNum();
            String pageTitle = parseResult.getPageTitles() != null
                    ? parseResult.getPageTitles().get(pageNum) : null;
            String content = (pageTitle != null && !pageTitle.isBlank())
                    ? "【" + pageTitle + "】\n" + ocrText
                    : ocrText;
            ChunkData.ChunkDataBuilder builder = ChunkData.builder()
                    .content(content).chunkType("image")
                    .pageNumber(pageNum).pageRange(String.valueOf(pageNum))
                    .imageKeys(java.util.List.of(img.getImageKey()));
            if (pageTitle != null && !pageTitle.isBlank()) {
                builder.title(pageTitle).headingPath(pageTitle);
            }
            imagesByPage.computeIfAbsent(pageNum, k -> new ArrayList<>()).add(builder.build());
        }
        if (imagesByPage.isEmpty()) return textChunks;

        // ② 文本 chunk 按页分组（保持页顺序）
        Map<Integer, List<ChunkData>> textByPage = new java.util.TreeMap<>();
        for (ChunkData chunk : textChunks) {
            int page = chunk.getPageNumber() != null ? chunk.getPageNumber() : 1;
            textByPage.computeIfAbsent(page, k -> new ArrayList<>()).add(chunk);
        }

        // ③ 按页合并：每页先文本后图片（TreeSet 保证页序，纯图片页排入正确位置）
        java.util.TreeSet<Integer> allPages = new java.util.TreeSet<>();
        allPages.addAll(textByPage.keySet());
        allPages.addAll(imagesByPage.keySet());

        List<ChunkData> merged = new ArrayList<>(textChunks.size() + pdfImages.size());
        for (int page : allPages) {
            merged.addAll(textByPage.getOrDefault(page, java.util.Collections.emptyList()));
            merged.addAll(imagesByPage.getOrDefault(page, java.util.Collections.emptyList()));
        }

        // ④ 重新分配 index/id（与最终列表位置一致）
        for (int i = 0; i < merged.size(); i++) {
            merged.get(i).setIndex(i);
            merged.get(i).setId("chunk_" + i);
        }
        log.info("PDF image merge: {} image chunks merged into {} text chunks, total {}",
                merged.size() - textChunks.size(), textChunks.size(), merged.size());
        return merged;
    }

    /**
     * 按页码归并图片 chunk 到文本 chunk 流（保持文档顺序，DOCX/PPT 通用版）。
     * <p>以页为单位合并（TreeSet 页序）：每页先文本 chunk 后图片 chunk（页内保持各自顺序）；
     * 纯图片页按页号排入正确位置；无页码的图片 chunk（DOCX）按文档顺序追加末尾。
     * 归并后重新分配 index/id 与列表位置一致。</p>
     */
    private List<ChunkData> mergeImageChunksByPage(List<ChunkData> textChunks, List<ChunkData> imageChunks) {
        // ① 图片 chunk 按页分组；无页码的（DOCX）单独收集
        Map<Integer, List<ChunkData>> imagesByPage = new java.util.TreeMap<>();
        List<ChunkData> noPageImages = new ArrayList<>();
        for (ChunkData ic : imageChunks) {
            if (ic.getPageNumber() != null) {
                imagesByPage.computeIfAbsent(ic.getPageNumber(), k -> new ArrayList<>()).add(ic);
            } else {
                noPageImages.add(ic);
            }
        }

        // ② 文本 chunk 按页分组（保持页顺序）
        Map<Integer, List<ChunkData>> textByPage = new java.util.TreeMap<>();
        for (ChunkData chunk : textChunks) {
            int page = chunk.getPageNumber() != null ? chunk.getPageNumber() : 1;
            textByPage.computeIfAbsent(page, k -> new ArrayList<>()).add(chunk);
        }

        // ③ 按页合并：每页先文本后图片（TreeSet 保证页序，纯图片页排入正确位置）
        java.util.TreeSet<Integer> allPages = new java.util.TreeSet<>();
        allPages.addAll(textByPage.keySet());
        allPages.addAll(imagesByPage.keySet());

        List<ChunkData> merged = new ArrayList<>(textChunks.size() + imageChunks.size());
        for (int page : allPages) {
            merged.addAll(textByPage.getOrDefault(page, java.util.Collections.emptyList()));
            merged.addAll(imagesByPage.getOrDefault(page, java.util.Collections.emptyList()));
        }
        // 无页码图片（DOCX）：按文档顺序追加末尾
        merged.addAll(noPageImages);

        // ④ 重新分配 index/id（与最终列表位置一致）
        for (int i = 0; i < merged.size(); i++) {
            merged.get(i).setIndex(i);
            merged.get(i).setId("chunk_" + i);
        }
        log.info("Image merge: {} image chunks merged into {} text chunks, total {}",
                imageChunks.size(), textChunks.size(), merged.size());
        return merged;
    }

    private void updateStatus(String fileId, String status, int progress, String stage) {
        try {
            KbFile f = fileMapper.selectById(fileId);
            if (f != null) {
                f.setStatus(status);
                f.setProgress(progress);
                f.setStage(stage != null && stage.length() > 60 ? stage.substring(0, 60) : stage);
                fileMapper.updateById(f);
            }
        } catch (Exception e) {
            log.error("Failed to update file status: {}", fileId, e);
        }
    }

    private String getFileExtension(String fileId) {
        KbFile f = fileMapper.selectById(fileId);
        return f != null ? f.getExtension() : "txt";
    }

    private String getFileName(String fileId) {
        KbFile f = fileMapper.selectById(fileId);
        return f != null ? f.getName() : fileId;
    }
}
