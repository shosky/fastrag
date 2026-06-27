package com.fastrag.module.knowledge.storage;

import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.infra.elasticsearch.ESIndexService;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.knowledge.chunking.ChunkData;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
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
    private final ESIndexService esIndexService;
    private final MilvusService milvusService;
    private final EmbeddingService embeddingService;
    private final KbFileMapper fileMapper;
    private final KnowledgeBaseMapper kbMapper;

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

        // 2. 确保 ES index 存在（ES 不可用时跳过）
        String esIndex = "kb_" + kbId.replace("-", "_");
        try {
            esIndexService.createIndex(esIndex);
        } catch (Exception e) {
            log.warn("Elasticsearch not available, skipping index creation: {}", e.getMessage());
        }

        // 3. 生成 Embedding（Embedding 服务不可用时跳过）
        List<String> texts = chunks.stream().map(ChunkData::getContent).collect(Collectors.toList());
        List<List<Float>> vectors = new ArrayList<>();
        // 暂时跳过 embedding，直接存储文本
        log.info("Skipping embedding, storing {} chunks as text only", chunks.size());

        // 4. 存储到 MySQL
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
            chunkMapper.insert(kc);
        }

        // 5. 存储到 Milvus
        if (!vectors.isEmpty()) {
            try {
                List<String> ids = new ArrayList<>();
                List<Long> indices = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i++) {
                    ids.add(fileId + "_chunk_" + i);
                    indices.add((long) i);
                }
                milvusService.insert(collection, ids, vectors, kbId, fileId, indices);
            } catch (Exception e) {
                log.error("Milvus insert failed", e);
            }
        }

        // 6. 存储到 Elasticsearch（ES 不可用时不阻断流程）
        try {
            esIndexService.createIndex(esIndex);
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("content", chunks.get(i).getContent());
                doc.put("kbId", kbId);
                doc.put("fileId", fileId);
                doc.put("chunkIndex", i);
                esIndexService.indexDocument(esIndex, fileId + "_chunk_" + i, doc);
            }
        } catch (Exception e) {
            log.warn("Elasticsearch not available, skipping ES indexing: {}", e.getMessage());
        }

        // 7. 更新文件的 chunkCount
        if (file != null) {
            file.setChunkCount(chunks.size());
            fileMapper.updateById(file);
        }

        log.info("Stored {} chunks for file: {}", chunks.size(), fileId);
    }
}
