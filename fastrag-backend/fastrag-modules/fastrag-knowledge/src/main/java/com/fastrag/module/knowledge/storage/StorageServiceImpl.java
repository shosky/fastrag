package com.fastrag.module.knowledge.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.knowledge.chunking.ChunkData;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final KbChunkMapper chunkMapper;
    private final MilvusService milvusService;
    private final EmbeddingService embeddingService;
    private final KbFileMapper fileMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final ModelRecordMapper modelRecordMapper;

    @Override
    public void storeChunks(String kbId, String fileId, List<ChunkData> chunks) {
        if (chunks.isEmpty()) {
            log.warn("No chunks to store for file: {}", fileId);
            return;
        }

        KnowledgeBase kb = kbMapper.selectById(kbId);
        int dimension = (kb != null && kb.getDimension() != null) ? kb.getDimension() : 1024;
        String embeddingModel = (kb != null) ? kb.getEmbeddingModel() : null;

        // 1. 确保 Milvus collection 存在
        String collection = "kb_" + kbId.replace("-", "_");
        try {
            milvusService.createCollection(collection, dimension);
        } catch (Exception e) {
            log.warn("Milvus collection may already exist: {}", e.getMessage());
        }

        // 2. 生成 Embedding（通过模型管理获取 API 地址和密钥）
        List<String> texts = chunks.stream().map(ChunkData::getContent).collect(Collectors.toList());
        List<List<Float>> vectors = new ArrayList<>();
        if (embeddingModel != null && !embeddingModel.isBlank()) {
            long tEmbed = System.currentTimeMillis();
            try {
                // 从模型管理表查询 API 地址和密钥
                String apiUrl = null;
                String apiKey = null;
                ModelRecord modelRecord = modelRecordMapper.selectOne(
                        new LambdaQueryWrapper<ModelRecord>()
                                .eq(ModelRecord::getCode, embeddingModel)
                                .eq(ModelRecord::getStatus, "online")
                                .last("LIMIT 1"));
                if (modelRecord != null) {
                    apiUrl = modelRecord.getApiUrl();
                    apiKey = modelRecord.getApiKeyRef();
                    log.info("Resolved embedding model '{}' -> apiUrl={}", embeddingModel, apiUrl);
                } else {
                    log.warn("Embedding model '{}' not found in model table or offline, using default gateway", embeddingModel);
                }

                vectors = embeddingService.embed(embeddingModel, texts, apiUrl, apiKey);
                log.info("[TIMING] embed {} chunks: {} ms, model={}", chunks.size(),
                        System.currentTimeMillis() - tEmbed, embeddingModel);
            } catch (Exception e) {
                log.warn("Embedding generation failed, storing chunks without vectors: {}", e.getMessage());
                log.warn("[TIMING] embed failed after {} ms", System.currentTimeMillis() - tEmbed);
            }
        } else {
            log.info("No embedding model configured, storing {} chunks as text only", chunks.size());
        }

        // 3. 存储到 MySQL
        long tMysql = System.currentTimeMillis();
        KbFile file = fileMapper.selectById(fileId);
        for (int i = 0; i < chunks.size(); i++) {
            ChunkData chunk = chunks.get(i);
            String chunkId = fileId + "_chunk_" + i;
            KbChunk kc = new KbChunk();
            kc.setId(chunkId);
            kc.setKbId(kbId);
            kc.setFileId(fileId);
            kc.setFileName(file != null ? file.getName() : "");
            kc.setChunkIndex(i);
            kc.setContent(chunk.getContent());
            kc.setEmbeddingId(chunkId);
            kc.setVectorStored(vectors.isEmpty() ? 0 : 1);
            kc.setStartTime(chunk.getStartTime());
            kc.setEndTime(chunk.getEndTime());
            kc.setPageNumber(chunk.getPageNumber());
            kc.setPageRange(chunk.getPageRange());
            kc.setChunkType(chunk.getChunkType() != null ? chunk.getChunkType() : "text");
            if (chunk.getImageKeys() != null && !chunk.getImageKeys().isEmpty()) {
                kc.setImageKeys(cn.hutool.json.JSONUtil.toJsonStr(chunk.getImageKeys()));
            }
            chunkMapper.insert(kc);
        }
        log.info("[TIMING] MySQL insert {} chunks: {} ms", chunks.size(), System.currentTimeMillis() - tMysql);

        // 4. 存储到 Milvus
        if (!vectors.isEmpty()) {
            long tMilvus = System.currentTimeMillis();
            try {
                List<String> ids = new ArrayList<>();
                List<Long> indices = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i++) {
                    ids.add(fileId + "_chunk_" + i);
                    indices.add((long) i);
                }
                milvusService.insert(collection, ids, vectors, kbId, fileId, indices);
                log.info("[TIMING] Milvus insert {} vectors: {} ms", ids.size(), System.currentTimeMillis() - tMilvus);
            } catch (Exception e) {
                log.error("Milvus insert failed", e);
            }
        }

        // 5. 更新文件的 chunkCount
        if (file != null) {
            file.setChunkCount(chunks.size());
            fileMapper.updateById(file);
        }

        log.info("Stored {} chunks for file: {}", chunks.size(), fileId);
    }
}
