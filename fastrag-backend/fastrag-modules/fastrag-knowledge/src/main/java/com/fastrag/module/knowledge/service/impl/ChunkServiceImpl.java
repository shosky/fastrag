package com.fastrag.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.model.ChunkCreateRequest;
import com.fastrag.module.knowledge.model.ChunkDto;
import com.fastrag.module.knowledge.service.ChunkService;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkServiceImpl implements ChunkService {

    private final KbChunkMapper mapper;
    private final KbFileMapper fileMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final ModelRecordMapper modelRecordMapper;
    private final MilvusService milvusService;
    private final EmbeddingService embeddingService;

    @Override
    public Map<String, Object> list(String kbId, String fileId, int page, int pageSize) {
        var w = new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId, kbId);
        if (fileId != null && !fileId.isBlank()) w.eq(KbChunk::getFileId, fileId);
        w.orderByAsc(KbChunk::getChunkIndex);
        var r = mapper.selectPage(new Page<>(page, pageSize), w);
        var result = new HashMap<String, Object>();
        result.put("list", r.getRecords());
        result.put("total", r.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    @Override
    public long getCount(String kbId) {
        return mapper.selectCount(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId, kbId));
    }

    @Override
    public ChunkDto getById(String kbId, String id) {
        KbChunk chunk = mapper.selectById(id);
        if (chunk == null) {
            throw new RuntimeException("分片不存在: " + id);
        }
        if (!chunk.getKbId().equals(kbId)) {
            throw new RuntimeException("分片不属于该知识库");
        }
        return ChunkDto.toDto(chunk);
    }

    @Override
    @Transactional
    public ChunkDto create(String kbId, ChunkCreateRequest req) {
        String fileId = req.getFileId();

        // 查询文件信息
        KbFile file = fileMapper.selectById(fileId);
        if (file == null) {
            throw new RuntimeException("文件不存在: " + fileId);
        }

        // 确定 chunkIndex
        int newChunkIndex;
        if (req.getInsertAfterIndex() != null) {
            // 在指定索引之后插入，后续分片索引 +1
            newChunkIndex = req.getInsertAfterIndex() + 1;
            // 将 >= newChunkIndex 的分片索引全部 +1
            shiftChunkIndices(fileId, newChunkIndex, 1);
        } else {
            // 追加到末尾
            newChunkIndex = mapper.selectMaxChunkIndex(fileId) + 1;
        }

        // 生成 ID
        String chunkId = fileId + "_chunk_" + newChunkIndex;

        // 创建实体
        KbChunk chunk = new KbChunk();
        chunk.setId(chunkId);
        chunk.setKbId(kbId);
        chunk.setFileId(fileId);
        chunk.setFileName(file.getName());
        chunk.setChunkIndex(newChunkIndex);
        chunk.setContent(req.getContent());
        chunk.setEmbeddingId(chunkId);
        chunk.setStartTime(req.getStartTime());
        chunk.setEndTime(req.getEndTime());
        chunk.setPageNumber(req.getPageNumber());
        chunk.setChunkType(req.getChunkType() != null ? req.getChunkType() : "text");

        // 生成向量
        List<List<Float>> vectors = generateEmbeddings(kbId, List.of(req.getContent()));
        if (!vectors.isEmpty()) {
            chunk.setVectorStored(1);
            mapper.insert(chunk);

            // 写入 Milvus
            String collection = "kb_" + kbId.replace("-", "_");
            try {
                milvusService.createCollection(collection, getDimension(kbId));
                milvusService.insert(collection, List.of(chunkId), vectors, kbId, fileId, List.of((long) newChunkIndex));
                log.info("[Chunk Create] Milvus vector inserted for chunk: {}", chunkId);
            } catch (Exception e) {
                log.warn("[Chunk Create] Milvus insert failed for chunk {}: {}", chunkId, e.getMessage());
                chunk.setVectorStored(0);
                mapper.updateById(chunk);
            }
        } else {
            chunk.setVectorStored(0);
            mapper.insert(chunk);
        }

        // 更新文件 chunkCount
        updateFileChunkCount(fileId);

        log.info("[Chunk Create] Created chunk: id={}, index={}, fileId={}", chunkId, newChunkIndex, fileId);
        return ChunkDto.toDto(chunk);
    }

    @Override
    @Transactional
    public ChunkDto update(String kbId, String id, Map<String, Object> fields) {
        KbChunk chunk = mapper.selectById(id);
        if (chunk == null) {
            throw new RuntimeException("分片不存在: " + id);
        }
        if (!chunk.getKbId().equals(kbId)) {
            throw new RuntimeException("分片不属于该知识库");
        }

        boolean contentChanged = false;
        if (fields.containsKey("content")) {
            String newContent = (String) fields.get("content");
            if (!Objects.equals(newContent, chunk.getContent())) {
                contentChanged = true;
                chunk.setContent(newContent);
            }
        }
        if (fields.containsKey("startTime")) chunk.setStartTime((Double) fields.get("startTime"));
        if (fields.containsKey("endTime")) chunk.setEndTime((Double) fields.get("endTime"));
        if (fields.containsKey("pageNumber")) chunk.setPageNumber((Integer) fields.get("pageNumber"));
        if (fields.containsKey("chunkType")) chunk.setChunkType((String) fields.get("chunkType"));
        if (fields.containsKey("imageKeys")) chunk.setImageKeys((String) fields.get("imageKeys"));

        // 如果内容发生变化，重新生成向量（删旧插新）
        if (contentChanged) {
            String collection = "kb_" + kbId.replace("-", "_");
            List<List<Float>> newVectors = generateEmbeddings(kbId, List.of(chunk.getContent()));
            if (!newVectors.isEmpty()) {
                try {
                    // 先删旧向量
                    milvusService.deleteById(collection, id);
                    // 再插新向量
                    milvusService.insert(collection, List.of(id), newVectors, kbId, chunk.getFileId(), List.of((long) chunk.getChunkIndex()));
                    chunk.setVectorStored(1);
                    log.info("[Chunk Update] Vector re-embedded for chunk: {}", id);
                } catch (Exception e) {
                    log.warn("[Chunk Update] Vector update failed for chunk {}: {}", id, e.getMessage());
                }
            } else {
                chunk.setVectorStored(0);
            }
        }

        mapper.updateById(chunk);

        log.info("[Chunk Update] Updated chunk: {}", id);
        return ChunkDto.toDto(chunk);
    }

    @Override
    @Transactional
    public void delete(String kbId, String id) {
        KbChunk chunk = mapper.selectById(id);
        if (chunk == null) {
            throw new RuntimeException("分片不存在: " + id);
        }
        if (!chunk.getKbId().equals(kbId)) {
            throw new RuntimeException("分片不属于该知识库");
        }

        String fileId = chunk.getFileId();
        int deletedIndex = chunk.getChunkIndex();

        // 删除 MySQL 记录
        mapper.deleteById(id);

        // 删除 Milvus 向量
        String collection = "kb_" + kbId.replace("-", "_");
        try {
            milvusService.deleteById(collection, id);
            log.info("[Chunk Delete] Milvus vector deleted for chunk: {}", id);
        } catch (Exception e) {
            log.warn("[Chunk Delete] Milvus delete failed for chunk {}: {}", id, e.getMessage());
        }

        // 将大于 deletedIndex 的分片索引 -1（保持连续性）
        shiftChunkIndices(fileId, deletedIndex + 1, -1);

        // 更新文件 chunkCount
        updateFileChunkCount(fileId);

        log.info("[Chunk Delete] Deleted chunk: id={}, index={}, fileId={}", id, deletedIndex, fileId);
    }

    @Override
    @Transactional
    public void batchDelete(String kbId, List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        // 批量查询验证
        List<KbChunk> chunks = mapper.selectByIds(ids);
        if (chunks.isEmpty()) return;

        // 收集涉及的 fileId
        Set<String> fileIds = new HashSet<>();
        List<String> validIds = new ArrayList<>();
        for (KbChunk c : chunks) {
            if (c.getKbId().equals(kbId)) {
                validIds.add(c.getId());
                fileIds.add(c.getFileId());
            }
        }

        if (validIds.isEmpty()) return;

        // 批量删除 MySQL
        mapper.deleteBatchIds(validIds);

        // 批量删除 Milvus 向量
        String collection = "kb_" + kbId.replace("-", "_");
        try {
            milvusService.deleteByIds(collection, validIds);
            log.info("[Chunk BatchDelete] Milvus vectors deleted for {} chunks", validIds.size());
        } catch (Exception e) {
            log.warn("[Chunk BatchDelete] Milvus batch delete failed: {}", e.getMessage());
        }

        // 更新涉及文件的 chunkCount
        for (String fileId : fileIds) {
            updateFileChunkCount(fileId);
        }

        log.info("[Chunk BatchDelete] Deleted {} chunks for kbId={}", validIds.size(), kbId);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 将指定文件中 chunkIndex >= startIndex 的分片索引偏移 shift 值
     */
    private void shiftChunkIndices(String fileId, int startIndex, int shift) {
        List<KbChunk> affectedChunks = mapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getFileId, fileId)
                        .ge(KbChunk::getChunkIndex, startIndex)
                        .orderByAsc(KbChunk::getChunkIndex));
        for (KbChunk c : affectedChunks) {
            int newIndex = c.getChunkIndex() + shift;
            // 更新 ID（因为 ID 包含 chunkIndex）
            String newId = c.getFileId() + "_chunk_" + newIndex;
            // 先删除旧记录再插入（因为 ID 是主键且为 INPUT 类型）
            // 同时需要更新 Milvus 中的数据
            String collection = "kb_" + c.getKbId().replace("-", "_");
            if (c.getVectorStored() != null && c.getVectorStored() == 1) {
                try {
                    milvusService.deleteById(collection, c.getId());
                } catch (Exception e) {
                    log.warn("[ShiftIndex] Failed to delete old vector for chunk {}: {}", c.getId(), e.getMessage());
                }
            }
            mapper.deleteById(c.getId());
            c.setId(newId);
            c.setChunkIndex(newIndex);
            c.setEmbeddingId(newId);
            mapper.insert(c);
            if (c.getVectorStored() != null && c.getVectorStored() == 1) {
                try {
                    // 重新生成向量（因为 ID 变了，Milvus 中需要用新 ID 存储）
                    List<List<Float>> vectors = generateEmbeddings(c.getKbId(), List.of(c.getContent()));
                    if (!vectors.isEmpty()) {
                        milvusService.insert(collection, List.of(newId), vectors, c.getKbId(), c.getFileId(), List.of((long) newIndex));
                    }
                } catch (Exception e) {
                    log.warn("[ShiftIndex] Failed to re-insert vector for chunk {}: {}", newId, e.getMessage());
                }
            }
        }
    }

    /**
     * 生成 Embedding 向量
     */
    private List<List<Float>> generateEmbeddings(String kbId, List<String> texts) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        String embeddingModel = (kb != null) ? kb.getEmbeddingModel() : null;
        if (embeddingModel == null || embeddingModel.isBlank()) {
            log.warn("No embedding model configured for kb: {}", kbId);
            return Collections.emptyList();
        }
        try {
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
            }
            return embeddingService.embed(embeddingModel, texts, apiUrl, apiKey);
        } catch (Exception e) {
            log.warn("Embedding generation failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取知识库的向量维度
     */
    private int getDimension(String kbId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        return (kb != null && kb.getDimension() != null) ? kb.getDimension() : 1024;
    }

    /**
     * 更新文件的 chunkCount
     */
    private void updateFileChunkCount(String fileId) {
        KbFile file = fileMapper.selectById(fileId);
        if (file != null) {
            long count = mapper.selectCount(
                    new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getFileId, fileId));
            file.setChunkCount((int) count);
            fileMapper.updateById(file);
        }
    }
}
