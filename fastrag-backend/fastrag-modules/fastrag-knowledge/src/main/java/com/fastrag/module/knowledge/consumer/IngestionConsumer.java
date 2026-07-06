package com.fastrag.module.knowledge.consumer;

import com.fastrag.common.handler.IngestionHandler;
import com.fastrag.ai.ocr.OcrService;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.infra.rabbitmq.MessagePublisher;
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

            // 文本切片
            long t2 = System.currentTimeMillis();
            updateStatus(fileId, "processing", 60, "chunking");
            boolean hasSegments = parseResult.getSegments() != null && !parseResult.getSegments().isEmpty();
            if (hasSegments) {
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

                // 4.6 PDF 图片提取 → 独立分片
                long tPdf = System.currentTimeMillis();
                if ("pdf".equals(extension) && fileBytes != null) {
                    try {
                        updateStatus(fileId, "processing", 75, "extracting images");
                        var doc = org.apache.pdfbox.Loader.loadPDF(fileBytes);
                        try {
                            List<MediaExtractor.PdfImage> pdfImages = mediaExtractor.extractPdfImages(doc, kbId, fileId, minioService);
                            if (!pdfImages.isEmpty()) {
                                log.info("Extracted {} images from PDF", pdfImages.size());
                                for (MediaExtractor.PdfImage img : pdfImages) {
                                    if (img.getWidth() < 48 || img.getHeight() < 48) {
                                        int idx = chunks.size();
                                        chunks.add(com.fastrag.module.knowledge.chunking.ChunkData.builder()
                                                .id("chunk_" + idx).index(idx)
                                                .content("[图片: 第" + img.getPageNum() + "页, " + img.getWidth() + "x" + img.getHeight() + "px]")
                                                .pageNumber(img.getPageNum()).pageRange(String.valueOf(img.getPageNum()))
                                                .imageKeys(java.util.List.of(img.getImageKey())).chunkType("image").build());
                                        continue;
                                    }
                                    String imageKey = img.getImageKey();
                                    String imgObjectKey = kbId + "/" + fileId + "/images/" + imageKey;
                                    try (InputStream imgStream = minioService.download(imgObjectKey)) {
                                        byte[] imgBytes = imgStream.readAllBytes();
                                        String ocrText = null;
                                        try { ocrText = ocrService.recognize(imgBytes, "png"); }
                                        catch (Exception e) { log.warn("OCR failed for image {}, skipping: {}", imageKey, e.getMessage()); }
                                        String content = (ocrText != null && !ocrText.isBlank()) ? ocrText.trim() : "[图片: 第" + img.getPageNum() + "页]";
                                        int idx = chunks.size();
                                        chunks.add(com.fastrag.module.knowledge.chunking.ChunkData.builder()
                                                .id("chunk_" + idx).index(idx)
                                                .content(content).chunkType("image")
                                                .pageNumber(img.getPageNum()).pageRange(String.valueOf(img.getPageNum()))
                                                .imageKeys(java.util.List.of(imageKey)).build());
                                    }
                                }
                                log.info("[TIMING] PDF image extraction & OCR: {} ms, {} image chunks",
                                        System.currentTimeMillis() - tPdf, pdfImages.size());
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
            if (Boolean.TRUE.equals(enableGraphBuild)) {
                try {
                    Map<String, Object> graphMsg = new HashMap<>();
                    graphMsg.put("kbId", kbId);
                    graphMsg.put("fileId", fileId);
                    graphMsg.put("mode", "incremental");
                    MessagePublisher pub = messagePublisherProvider.getIfAvailable();
                    if (pub != null) {
                        pub.publishGraphBuild(graphMsg);
                    }
                    log.info("[Graph] Auto-triggered graph build for file: {}, kb: {}", fileId, kbId);
                } catch (Exception e) {
                    log.warn("[Graph] Failed to auto-trigger graph build for file {}: {}", fileId, e.getMessage());
                }
            }

            // 8. 更新状态为 completed
            updateStatus(fileId, "completed", 100, "done");

            long tTotal = System.currentTimeMillis() - tStart;
            log.info("File processing completed: {}, total time: {} ms", fileId, tTotal);

        } catch (Exception e) {
            log.error("File processing failed: {}", fileId, e);
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
