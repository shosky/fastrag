package com.fastrag.module.knowledge.consumer;

import com.fastrag.common.handler.IngestionHandler;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.module.knowledge.chunking.ChunkData;
import com.fastrag.module.knowledge.chunking.ChunkingService;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.parser.DocumentParser;
import com.fastrag.module.knowledge.parser.ParseResult;
import com.fastrag.module.knowledge.storage.StorageService;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 文档摄入处理服务 (原 RabbitMQ Consumer，现改为直接调用)
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

    @Override
    public void handleIngestion(Map<String, Object> message) {
        String fileId = (String) message.get("fileId");
        String kbId = (String) message.get("kbId");
        String objectKey = (String) message.get("objectKey");
        String strategyId = (String) message.get("strategyId");
        String operator = (String) message.getOrDefault("operator", "system");

        log.info("Start processing file: {}, kbId: {}", fileId, kbId);

        try {
            // 1. 更新状态为 processing
            updateStatus(fileId, "processing", 10, "downloading");

            // 2. 从本地存储下载文件
            InputStream fileStream = minioService.download(objectKey);
            String extension = getFileExtension(fileId);

            // 3. 解析文档
            updateStatus(fileId, "processing", 30, "parsing");
            ParseResult parseResult = documentParser.parse(fileStream, extension, strategyId);
            fileStream.close();

            // 4. 文本切片
            updateStatus(fileId, "processing", 60, "chunking");
            List<ChunkData> chunks;
            if (parseResult.getSegments() != null && !parseResult.getSegments().isEmpty()) {
                // 音视频文件：使用时间轴分片
                chunks = chunkingService.chunkBySegments(parseResult.getSegments());
                log.info("File {} chunked into {} time-based pieces", fileId, chunks.size());
            } else {
                // 文档文件：使用文本分片
                chunks = chunkingService.chunk(parseResult.getText(), strategyId);
                log.info("File {} chunked into {} pieces", fileId, chunks.size());
            }

            // 5. 存储切片
            updateStatus(fileId, "processing", 80, "storing");
            storageService.storeChunks(kbId, fileId, chunks);

            // 6. 记录更新日志
            try {
                KbFile kf = fileMapper.selectById(fileId);
                String fileName = kf != null ? kf.getName() : fileId;
                logService.addUpdateLog(kbId, "ADD", fileName,
                        "上传并处理了文档 " + fileName, operator);
            } catch (Exception e) {
                log.warn("Failed to record update log for file: {}", fileId, e);
            }

            // 7. 更新状态为 completed
            updateStatus(fileId, "completed", 100, "done");

            log.info("File processing completed: {}, chunks: {}", fileId, chunks.size());

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
}
