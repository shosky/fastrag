package com.fastrag.module.knowledge.consumer;

import com.fastrag.common.handler.IngestionHandler;
import com.fastrag.ai.ocr.OcrService;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.infra.rabbitmq.MessagePublisher;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.module.knowledge.chunking.ChunkData;
import com.fastrag.module.knowledge.chunking.ChunkingService;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.parser.DocumentParser;
import com.fastrag.module.knowledge.parser.MediaExtractor;
import com.fastrag.module.knowledge.parser.ParseResult;
import com.fastrag.module.knowledge.storage.StorageService;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.module.platform.entity.SysNotification;
import com.fastrag.module.platform.mapper.SysNotificationMapper;
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
 * 文档摄入处理服务 (RabbitMQ Consumer)
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
    private final LogService logService;
    private final SysNotificationMapper notificationMapper;
    private final MediaExtractor mediaExtractor;
    private final OcrService ocrService;
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
                    new ByteArrayInputStream(fileBytes), extension, strategyId);
            log.info("[TIMING] parse complete: {} ms, segments={}, chars={}",
                    System.currentTimeMillis() - t1,
                    parseResult.getSegments() != null ? parseResult.getSegments().size() : 0,
                    parseResult.getText() != null ? parseResult.getText().length() : 0);

            // 判断是否为音频文件
            boolean isAudio = extension != null && AUDIO_EXTENSIONS.contains(extension.toLowerCase());

            List<ChunkData> chunks = new ArrayList<>();

            // 文本切片 — 优先使用结构感知分片
            long t2 = System.currentTimeMillis();
            updateStatus(fileId, "processing", 60, "chunking");
            boolean hasNodes = parseResult.getNodes() != null && !parseResult.getNodes().isEmpty();
            boolean hasSegments = parseResult.getSegments() != null && !parseResult.getSegments().isEmpty();
            if (hasNodes) {
                chunks = chunkingService.structuralChunk(parseResult.getNodes(), strategyId);
                log.info("File {} structural chunked into {} pieces with heading context", fileId, chunks.size());
            } else if (hasSegments) {
                chunks = chunkingService.chunkBySegments(parseResult.getSegments(), strategyId);
                log.info("File {} chunked into {} time-based pieces", fileId, chunks.size());
            } else {
                chunks = chunkingService.chunk(parseResult.getText(), strategyId);
                log.info("File {} chunked into {} pieces", fileId, chunks.size());
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
                long tPdf = System.currentTimeMillis();
                if ("pdf".equals(extension) && fileBytes != null) {
                    try {
                        updateStatus(fileId, "processing", 75, "extracting images");
                        var doc = org.apache.pdfbox.Loader.loadPDF(fileBytes);
                        try {
                            List<MediaExtractor.PdfImage> pdfImages = mediaExtractor.extractPdfImages(doc, kbId, fileId, minioService);
                            if (!pdfImages.isEmpty()) {
                                log.info("Extracted {} images from PDF", pdfImages.size());

                                // 并行 OCR，最多 5 并发
                                // 用 final 副本供 lambda 捕获（chunks 变量本身不是 effectively final）
                                final List<ChunkData> chunksRef = chunks;
                                java.util.concurrent.Semaphore ocpSemaphore = new java.util.concurrent.Semaphore(5);
                                java.util.concurrent.ExecutorService ocrExecutor = java.util.concurrent.Executors.newFixedThreadPool(5);
                                List<java.util.concurrent.Future<Void>> ocrFutures = new ArrayList<>();

                                for (MediaExtractor.PdfImage img : pdfImages) {
                                    if (img.getWidth() < 100 || img.getHeight() < 100) {
                                        continue;
                                    }

                                    final String fImageKey = img.getImageKey();
                                    final String fImgObjectKey = kbId + "/" + fileId + "/images/" + fImageKey;
                                    final int fPageNum = img.getPageNum();

                                    ocrFutures.add(ocrExecutor.submit(() -> {
                                        ocpSemaphore.acquire();
                                        try {
                                            byte[] imgBytes;
                                            try (InputStream imgStream = minioService.download(fImgObjectKey)) {
                                                imgBytes = imgStream.readAllBytes();
                                            }
                                            String ocrText = null;
                                            try {
                                                ocrText = ocrService.recognize(imgBytes, "png");
                                            } catch (Exception e) {
                                                log.warn("OCR failed for image {}, skipping: {}", fImageKey, e.getMessage());
                                            }

                                            if (ocrText != null && !ocrText.isBlank()) {
                                                synchronized (chunksRef) {
                                                    int idx = chunksRef.size();
                                                    chunksRef.add(ChunkData.builder()
                                                            .id("chunk_" + idx).index(idx)
                                                            .content(ocrText.trim()).chunkType("image")
                                                            .pageNumber(fPageNum).pageRange(String.valueOf(fPageNum))
                                                            .imageKeys(java.util.List.of(fImageKey)).build());
                                                }
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

                                log.info("[TIMING] PDF image extraction & OCR: {} ms, {} image chunks ({} total images)",
                                        System.currentTimeMillis() - tPdf, chunksRef.size(), pdfImages.size());
                            }
                        } finally { doc.close(); }
                    } catch (Exception e) {
                        log.warn("PDF image extraction failed, continuing: {}", e.getMessage());
                    }
                }

                // 5. 存储切片（含 Embedding + MySQL + Milvus）
                long t3 = System.currentTimeMillis();
                updateStatus(fileId, "processing", 80, "storing");
                storageService.storeChunks(kbId, fileId, chunks);
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

        } catch (Exception e) {
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
