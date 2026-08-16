package com.fastrag.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.infra.rabbitmq.MessagePublisher;
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
import org.springframework.beans.factory.ObjectProvider;
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
    private final GraphStore graphStore;
    /** 图谱构建消息发布器（ObjectProvider 避免与 infra 模块产生循环依赖，同 IngestionConsumer 模式） */
    private final ObjectProvider<MessagePublisher> messagePublisherProvider;

    @Override
    public Map<String, Object> list(String kbId, String fileId, int page, int pageSize) {
        // 父子分片模式：存在父分片时按父分片分页（父分片行内嵌子分片），
        // 无父分片的孤立子分片（单分片章节）按 chunk_index 保持文档顺序混排
        LambdaQueryWrapper<KbChunk> parentW = new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, kbId)
                .eq(KbChunk::getChunkType, "parent");
        if (fileId != null && !fileId.isBlank()) parentW.eq(KbChunk::getFileId, fileId);
        long parentCount = mapper.selectCount(parentW);

        var result = new HashMap<String, Object>();
        if (parentCount > 0) {
            var w = new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getKbId, kbId)
                    .and(q -> q.eq(KbChunk::getChunkType, "parent")
                            .or(o -> o.isNull(KbChunk::getParentId).ne(KbChunk::getChunkType, "parent")));
            if (fileId != null && !fileId.isBlank()) w.eq(KbChunk::getFileId, fileId);
            w.orderByAsc(KbChunk::getChunkIndex);
            var r = mapper.selectPage(new Page<>(page, pageSize), w);

            List<ChunkDto> items = new ArrayList<>();
            for (KbChunk row : r.getRecords()) {
                ChunkDto dto = ChunkDto.toDto(row);
                if ("parent".equals(row.getChunkType())) {
                    // 父分片行内嵌子分片
                    List<KbChunk> children = mapper.selectList(new LambdaQueryWrapper<KbChunk>()
                            .eq(KbChunk::getParentId, row.getId())
                            .orderByAsc(KbChunk::getChunkIndex));
                    dto.setChildren(children.stream().map(ChunkDto::toDto).collect(Collectors.toList()));
                }
                items.add(dto);
            }
            result.put("mode", "parent");
            result.put("list", items);
            result.put("total", r.getTotal());
            result.put("page", page);
            result.put("pageSize", pageSize);
            return result;
        }

        // 单层模式（原逻辑）
        var w = new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId, kbId);
        if (fileId != null && !fileId.isBlank()) w.eq(KbChunk::getFileId, fileId);
        w.orderByAsc(KbChunk::getChunkIndex);
        var r = mapper.selectPage(new Page<>(page, pageSize), w);
        result.put("mode", "single");
        result.put("list", r.getRecords());
        result.put("total", r.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    @Override
    public long getCount(String kbId) {
        return mapper.selectCount(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getKbId, kbId)
                .ne(KbChunk::getChunkType, "parent"));
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

        // 图谱联动：新分片未做图谱提取（graph_indexed=null），自动触发增量构建补齐
        triggerIncrementalGraphBuild(kbId, fileId);

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

        // 父子分片联动：编辑子分片后重新拼接其所属父分片内容，保持一致性
        if (contentChanged) {
            refreshParentContent(chunk);
        }

        // 图谱联动：内容变化时清理旧图谱数据并标记待重新提取，随后自动触发增量构建（KG-02）
        if (contentChanged) {
            try {
                graphStore.deleteChunkGraph(kbId, id);
                log.info("[Chunk Update] Graph data cleaned for chunk: {}", id);
            } catch (Exception e) {
                log.warn("[Chunk Update] Graph cleanup failed for chunk {}: {}", id, e.getMessage());
            }
            try {
                // 重置 graphIndexed 使增量构建能重新处理此 chunk
                mapper.update(null, new LambdaUpdateWrapper<KbChunk>()
                        .eq(KbChunk::getId, id)
                        .set(KbChunk::getGraphIndexed, 0)
                        .set(KbChunk::getExtractionResult, null));
                log.info("[Chunk Update] graphIndexed reset for chunk: {}", id);
            } catch (Exception e) {
                log.warn("[Chunk Update] Failed to reset graphIndexed for chunk {}: {}", id, e.getMessage());
            }
            // 触发增量图谱构建（受文件 enableGraphBuild 开关约束；增量模式只处理该 chunk，成本低）
            triggerIncrementalGraphBuild(kbId, chunk.getFileId());
        }

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

        // 删除 MySQL 记录（父分片级联删除其子分片）
        List<String> cascadeIds = new ArrayList<>();
        if ("parent".equals(chunk.getChunkType())) {
            cascadeIds = mapper.selectList(new LambdaQueryWrapper<KbChunk>()
                            .eq(KbChunk::getParentId, id))
                    .stream().map(KbChunk::getId).collect(Collectors.toList());
        }
        cascadeIds.add(id);
        mapper.deleteBatchIds(cascadeIds);

        // 删除 Milvus 向量
        String collection = "kb_" + kbId.replace("-", "_");
        try {
            milvusService.deleteByIds(collection, cascadeIds);
            log.info("[Chunk Delete] Milvus vectors deleted for chunks: {}", cascadeIds);
        } catch (Exception e) {
            log.warn("[Chunk Delete] Milvus delete failed for chunks {}: {}", cascadeIds, e.getMessage());
        }

        // 清理知识图谱数据（MENTIONS + 回收孤立实体/关系）
        for (String cascadeId : cascadeIds) {
            try {
                graphStore.deleteChunkGraph(kbId, cascadeId);
            } catch (Exception e) {
                log.warn("[Chunk Delete] Graph cleanup failed for chunk {}: {}", cascadeId, e.getMessage());
            }
        }

        // 将大于 deletedIndex 的分片索引 -1（保持连续性；父分片索引取首个子分片位置，不参与移位）
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

        // 父分片级联：删除父分片时连同其子分片一起删除
        Set<String> parentIds = chunks.stream()
                .filter(c -> "parent".equals(c.getChunkType()))
                .map(KbChunk::getId)
                .collect(Collectors.toSet());
        if (!parentIds.isEmpty()) {
            List<KbChunk> children = mapper.selectList(new LambdaQueryWrapper<KbChunk>()
                    .in(KbChunk::getParentId, parentIds));
            for (KbChunk child : children) {
                validIds.add(child.getId());
                fileIds.add(child.getFileId());
            }
        }

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

        // 批量清理知识图谱数据
        for (String chunkId : validIds) {
            try {
                graphStore.deleteChunkGraph(kbId, chunkId);
            } catch (Exception e) {
                log.warn("[Chunk BatchDelete] Graph cleanup failed for chunk {}: {}", chunkId, e.getMessage());
            }
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
                        .ne(KbChunk::getChunkType, "parent") // 父分片索引取首个子分片位置，不参与移位
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
            String oldId = c.getId();
            c.setId(newId);
            c.setChunkIndex(newIndex);
            c.setEmbeddingId(newId);
            mapper.insert(c);

            // 同步更新 Neo4j 中 Chunk 节点的 chunkId（避免 MENTIONS 引用悬空）
            try {
                graphStore.renameChunkId(c.getKbId(), oldId, newId);
            } catch (Exception e) {
                log.warn("[ShiftIndex] Failed to rename chunk in graph: {} -> {}: {}", oldId, newId, e.getMessage());
            }

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
     * 更新文件的 chunkCount（只统计子分片，父分片不计数）
     */
    private void updateFileChunkCount(String fileId) {
        KbFile file = fileMapper.selectById(fileId);
        if (file != null) {
            long count = mapper.selectCount(
                    new LambdaQueryWrapper<KbChunk>()
                            .eq(KbChunk::getFileId, fileId)
                            .ne(KbChunk::getChunkType, "parent"));
            file.setChunkCount((int) count);
            fileMapper.updateById(file);
        }
    }

    /**
     * 子分片内容变更后，重新拼接其所属父分片内容（父子分片一致性）。
     * 父分片内容 = 子分片内容按 "\n\n" 拼接，与分片阶段 ParentChunkAssembler 规则一致。
     */
    private void refreshParentContent(KbChunk child) {
        if (child.getParentId() == null || child.getParentId().isBlank()) return;
        try {
            List<KbChunk> children = mapper.selectList(new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getParentId, child.getParentId())
                    .orderByAsc(KbChunk::getChunkIndex));
            if (children.isEmpty()) return;
            String content = children.stream()
                    .map(KbChunk::getContent)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n\n"));
            mapper.update(null, new LambdaUpdateWrapper<KbChunk>()
                    .eq(KbChunk::getId, child.getParentId())
                    .set(KbChunk::getContent, content));
            log.info("[Chunk Update] Parent chunk refreshed: {} ({} children)", child.getParentId(), children.size());
        } catch (Exception e) {
            log.warn("[Chunk Update] Failed to refresh parent chunk {}: {}", child.getParentId(), e.getMessage());
        }
    }

    /**
     * 触发增量图谱构建（KG-02）。
     * <p>开关与文件上传链路一致：文件 enableGraphBuild=1（上传时由 KB graphAutoBuild 解析并落库）
     * 才自动触发；增量模式只处理 graph_indexed=0 的 chunk，单个分片构建成本低。</p>
     */
    private void triggerIncrementalGraphBuild(String kbId, String fileId) {
        try {
            KbFile file = fileMapper.selectById(fileId);
            if (file == null || !Integer.valueOf(1).equals(file.getEnableGraphBuild())) {
                log.debug("[Chunk Graph] Graph build skipped: file enableGraphBuild != 1, fileId={}", fileId);
                return;
            }
            MessagePublisher pub = messagePublisherProvider.getIfAvailable();
            if (pub == null) {
                log.warn("[Chunk Graph] MessagePublisher bean not available, cannot trigger graph build for kb={}, fileId={}", kbId, fileId);
                return;
            }
            Map<String, Object> graphMsg = new HashMap<>();
            graphMsg.put("kbId", kbId);
            graphMsg.put("fileId", fileId);
            graphMsg.put("mode", "incremental");
            pub.publishGraphBuild(graphMsg);
            log.info("[Chunk Graph] Incremental graph build message published: kb={}, fileId={}", kbId, fileId);
        } catch (Exception e) {
            log.warn("[Chunk Graph] Failed to trigger incremental graph build: kb={}, fileId={}, error={}",
                    kbId, fileId, e.getMessage());
        }
    }
}
