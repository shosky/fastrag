package com.fastrag.module.knowledge.consumer;

import com.fastrag.infra.config.RabbitMQConfig;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.infra.rabbitmq.MessagePublisher;
import com.fastrag.module.knowledge.chunking.ChunkData;
import com.fastrag.module.knowledge.chunking.ChunkingService;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.parser.DocumentParser;
import com.fastrag.module.knowledge.parser.ParseResult;
import com.fastrag.module.knowledge.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionConsumer {

    private final MinioService minioService;
    private final DocumentParser documentParser;
    private final ChunkingService chunkingService;
    private final StorageService storageService;
    private final MessagePublisher messagePublisher;
    private final KbFileMapper fileMapper;

    @RabbitListener(queues = RabbitMQConfig.INGESTION_QUEUE)
    public void handleIngestion(Map<String, Object> message) {
        String fileId = (String) message.get("fileId");
        String kbId = (String) message.get("kbId");
        String objectKey = (String) message.get("objectKey");
        String strategyId = (String) message.get("strategyId");

        log.info("Start processing file: {}, kbId: {}", fileId, kbId);

        try {
            // 1. 更新状态为 processing
            updateStatus(fileId, "processing", 10, "downloading");

            // 2. 从 MinIO 下载文件
            InputStream fileStream = minioService.download(objectKey);
            String extension = getFileExtension(fileId);

            // 3. 解析文档
            updateStatus(fileId, "processing", 30, "parsing");
            ParseResult parseResult = documentParser.parse(fileStream, extension, strategyId);
            fileStream.close();

            // 4. 文本切片
            updateStatus(fileId, "processing", 60, "chunking");
            List<ChunkData> chunks = chunkingService.chunk(parseResult.getText(), strategyId);
            log.info("File {} chunked into {} pieces", fileId, chunks.size());

            // 5. 存储切片
            updateStatus(fileId, "processing", 80, "storing");
            storageService.storeChunks(kbId, fileId, chunks);

            // 6. 更新状态为 completed
            updateStatus(fileId, "completed", 100, "done");

            // 7. 发送图谱构建消息
            messagePublisher.publishGraphBuild(Map.of("kbId", kbId, "fileId", fileId));

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
                f.setStage(stage);
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
