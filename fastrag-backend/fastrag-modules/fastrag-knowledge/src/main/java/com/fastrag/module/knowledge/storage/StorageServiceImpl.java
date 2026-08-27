package com.fastrag.module.knowledge.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.knowledge.chunking.ChunkData;
import com.fastrag.module.knowledge.chunking.ParentChunkAssembler;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
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
    private final SqlSessionFactory sqlSessionFactory;
    private final ParentChunkAssembler parentChunkAssembler;

    /** Embedding 分批大小 */
    private static final int EMBED_BATCH_SIZE = 32;

    /** Embedding 重试：最多 3 次，指数退避 */
    private static final int EMBED_MAX_RETRIES = 3;

    @Override
    // 注意：不使用 @Transactional，因为内部 batchInsertChunks 通过 SqlSession(BATCH) 自行管理事务
    // Spring @Transactional 与 MyBatis BATCH Executor 的事务不能混用
    public void storeChunks(String kbId, String fileId, List<ChunkData> chunks) {
        storeChunks(kbId, fileId, chunks, -1);
    }

    @Override
    // 注意：不使用 @Transactional，因为内部 batchInsertChunks 通过 SqlSession(BATCH) 自行管理事务
    // Spring @Transactional 与 MyBatis BATCH Executor 的事务不能混用
    public void storeChunks(String kbId, String fileId, List<ChunkData> chunks, int childChunkLength) {
        storeChunks(kbId, fileId, chunks, childChunkLength, childChunkLength * 2, null);
    }

    @Override
    // 注意：不使用 @Transactional，因为内部 batchInsertChunks 通过 SqlSession(BATCH) 自行管理事务
    // Spring @Transactional 与 MyBatis BATCH Executor 的事务不能混用
    public void storeChunks(String kbId, String fileId, List<ChunkData> chunks, int childChunkLength,
                            int maxParentLength, String parentAggLevel) {
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

        // 2. 解析模型 API 配置
        String apiUrl = null;
        String apiKey = null;
        if (embeddingModel != null && !embeddingModel.isBlank()) {
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
        }

        KbFile file = fileMapper.selectById(fileId);

        // 3. 父子分片聚合（仅父分片模式传入 childChunkLength > 0 时启用）
        //    父分片先落库（无向量、不进 Milvus），子分片回填 parentId 后走常规存储
        if (childChunkLength > 0) {
            int effectiveMaxParent = maxParentLength > 0 ? maxParentLength : childChunkLength * 2;
            ParentChunkAssembler.AssemblyResult assembly =
                    parentChunkAssembler.assemble(chunks, fileId, effectiveMaxParent, parentAggLevel);
            if (assembly.hasParents()) {
                batchInsertParents(kbId, fileId, file, assembly.parents());
            }
            chunks = assembly.children();
        }

        // 4. 分批处理：Embedding + MySQL 批量写入
        List<List<Float>> allVectors = new ArrayList<>();
        // 记录无向量的 chunk 索引（embedding 批次失败时），Milvus 插入时跳过，避免 ids/vectors 错位
        Set<Integer> noVectorIndexes = new HashSet<>();

        for (int batchStart = 0; batchStart < chunks.size(); batchStart += EMBED_BATCH_SIZE) {
            int end = Math.min(batchStart + EMBED_BATCH_SIZE, chunks.size());
            List<ChunkData> batch = chunks.subList(batchStart, end);

            // 4a. Embedding（每批，带指数退避重试）
            List<List<Float>> batchVectors = embedBatchWithRetry(embeddingModel, batch, apiUrl, apiKey,
                    file != null ? file.getName() : null);
            if (batchVectors != null) {
                allVectors.addAll(batchVectors);
            } else {
                // 该批 embedding 失败：chunk 以纯文本存储，Milvus 不写入这批向量
                for (int i = batchStart; i < end; i++) {
                    noVectorIndexes.add(i);
                }
            }

            // 4b. MySQL 批量写入（使用 MyBatis BATCH Executor）
            long tMysql = System.currentTimeMillis();
            batchInsertChunks(kbId, fileId, file, batch, batchStart,
                    batchVectors != null && !batchVectors.isEmpty());
            log.info("[TIMING] MySQL batch insert {} chunks: {} ms", batch.size(),
                    System.currentTimeMillis() - tMysql);
        }

        // 5. 批量写入 Milvus（只写入有向量的 chunk，ids/indices/vectors 严格对应 chunk 索引）
        if (!allVectors.isEmpty()) {
            long tMilvus = System.currentTimeMillis();
            try {
                List<String> ids = new ArrayList<>();
                List<Long> indices = new ArrayList<>();
                List<List<Float>> vectors = new ArrayList<>();
                int vectorCursor = 0;
                for (int i = 0; i < chunks.size(); i++) {
                    if (noVectorIndexes.contains(i)) continue;
                    if (vectorCursor >= allVectors.size()) break; // 防御：不应发生
                    ids.add(fileId + "_chunk_" + i);
                    indices.add((long) i);
                    vectors.add(allVectors.get(vectorCursor++));
                }
                milvusService.insert(collection, ids, vectors, kbId, fileId, indices);
                log.info("[TIMING] Milvus insert {} vectors (skipped {} no-vector chunks): {} ms",
                        ids.size(), noVectorIndexes.size(), System.currentTimeMillis() - tMilvus);
            } catch (Exception e) {
                log.error("Milvus insert failed", e);
            }
        }

        // 6. 更新文件的 chunkCount（只统计子分片，父分片不计数）
        if (file != null) {
            file.setChunkCount(chunks.size());
            fileMapper.updateById(file);
        }

        log.info("Stored {} chunks for file: {}", chunks.size(), fileId);
    }

    @Override
    // 不使用 @Transactional，batchInsertChunks 通过 SqlSession(BATCH) 自行管理事务
    public void storeChunkBatch(String kbId, String fileId, List<ChunkData> chunks, int batchStart) {
        if (chunks == null || chunks.isEmpty()) return;

        KnowledgeBase kb = kbMapper.selectById(kbId);
        String embeddingModel = (kb != null) ? kb.getEmbeddingModel() : null;

        String apiUrl = null;
        String apiKey = null;
        if (embeddingModel != null && !embeddingModel.isBlank()) {
            ModelRecord modelRecord = modelRecordMapper.selectOne(
                    new LambdaQueryWrapper<ModelRecord>()
                            .eq(ModelRecord::getCode, embeddingModel)
                            .eq(ModelRecord::getStatus, "online")
                            .last("LIMIT 1"));
            if (modelRecord != null) {
                apiUrl = modelRecord.getApiUrl();
                apiKey = modelRecord.getApiKeyRef();
            }
        }

        // Embedding（带重试）
        KbFile file = fileMapper.selectById(fileId);
        List<List<Float>> vectors = embedBatchWithRetry(embeddingModel, chunks, apiUrl, apiKey,
                file != null ? file.getName() : null);

        // MySQL 批量写入
        boolean hasVectors = vectors != null && !vectors.isEmpty();
        batchInsertChunks(kbId, fileId, file, chunks, batchStart, hasVectors);

        // Milvus
        if (hasVectors) {
            String collection = "kb_" + kbId.replace("-", "_");
            try {
                List<String> ids = new ArrayList<>();
                List<Long> indices = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i++) {
                    ids.add(fileId + "_chunk_" + (batchStart + i));
                    indices.add((long) (batchStart + i));
                }
                milvusService.insert(collection, ids, vectors, kbId, fileId, indices);
            } catch (Exception e) {
                log.warn("Milvus batch insert failed for batch starting at {}: {}", batchStart, e.getMessage());
            }
        }

        log.debug("Stored chunk batch [{}, {}) for file {}", batchStart, batchStart + chunks.size(), fileId);
    }

    // ========== 内部辅助方法 ==========

    /**
     * MyBatis BATCH 模式批量插入 chunk 记录。
     * 使用 BATCH ExecutorType 替代逐条 INSERT，减少 SQL 编译开销。
     */
    private void batchInsertChunks(String kbId, String fileId, KbFile file,
                                   List<ChunkData> chunks, int batchStart, boolean hasVectors) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            KbChunkMapper batchMapper = sqlSession.getMapper(KbChunkMapper.class);
            for (int i = 0; i < chunks.size(); i++) {
                ChunkData chunk = chunks.get(i);
                int globalIdx = batchStart + i;
                String chunkId = fileId + "_chunk_" + globalIdx;
                KbChunk kc = new KbChunk();
                kc.setId(chunkId);
                kc.setKbId(kbId);
                kc.setFileId(fileId);
                kc.setFileName(file != null ? file.getName() : "");
                kc.setChunkIndex(globalIdx);
                kc.setContent(chunk.getContent());
                kc.setEmbeddingId(chunkId);
                kc.setVectorStored(hasVectors ? 1 : 0);
                kc.setStartTime(chunk.getStartTime());
                kc.setEndTime(chunk.getEndTime());
                kc.setPageNumber(chunk.getPageNumber());
                kc.setPageRange(chunk.getPageRange());
                kc.setChunkType(chunk.getChunkType() != null ? chunk.getChunkType() : "text");
                // 管线生成的分片均为自动分片（origin=auto）；manual 由 ChunkServiceImpl.create 显式标记
                kc.setOrigin("auto");
                kc.setTitle(chunk.getTitle());
                kc.setHeadingPath(chunk.getHeadingPath());
                kc.setParentId(chunk.getParentId());
                // 元数据冗余回填（分册四：入库时把文件级元数据冗余到 chunk，检索过滤/排序直接消费）
                if (file != null) {
                    kc.setRegion(file.getRegion());
                    kc.setPublishDate(file.getPublishDate());
                    kc.setDocLevel(file.getDocLevel());
                }
                if (chunk.getImageKeys() != null && !chunk.getImageKeys().isEmpty()) {
                    kc.setImageKeys(JSONUtil.toJsonStr(chunk.getImageKeys()));
                }
                batchMapper.insert(kc);
            }
            sqlSession.commit();
        } catch (Exception e) {
            log.error("Batch insert failed for chunks batch starting at {}", batchStart, e);
            throw new RuntimeException("批量插入 chunk 失败", e);
        }
    }

    /**
     * 批量插入父分片（MyBatis BATCH 模式）。
     * 父分片不向量化（vector_stored=0、无 embeddingId、不进 Milvus），
     * id = {fileId}_parent_{n}，chunk_index 取首个子分片位置保证列表文档顺序。
     */
    private void batchInsertParents(String kbId, String fileId, KbFile file, List<ChunkData> parents) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            KbChunkMapper batchMapper = sqlSession.getMapper(KbChunkMapper.class);
            for (ChunkData parent : parents) {
                KbChunk kc = new KbChunk();
                kc.setId(parent.getId());
                kc.setKbId(kbId);
                kc.setFileId(fileId);
                kc.setFileName(file != null ? file.getName() : "");
                kc.setChunkIndex(parent.getIndex());
                kc.setContent(parent.getContent());
                kc.setVectorStored(0);
                kc.setChunkType("parent");
                kc.setTitle(parent.getTitle());
                kc.setHeadingPath(parent.getHeadingPath());
                kc.setPageNumber(parent.getPageNumber());
                kc.setPageRange(parent.getPageRange());
                // 元数据冗余回填（同 batchInsertChunks）
                if (file != null) {
                    kc.setRegion(file.getRegion());
                    kc.setPublishDate(file.getPublishDate());
                    kc.setDocLevel(file.getDocLevel());
                }
                batchMapper.insert(kc);
            }
            sqlSession.commit();
        } catch (Exception e) {
            log.error("Batch insert parents failed for file {}", fileId, e);
            throw new RuntimeException("批量插入父分片失败", e);
        }
    }

    /**
     * Embedding 分批调用，带指数退避重试。
     *
     * 策略：最多重试 EMBED_MAX_RETRIES 次，等待时间 1s, 2s, 4s
     * 所有重试均失败后返回 null（不阻断流程，chunk 以纯文本存储）
     *
     * 注意：当前在主线程同步阻塞，大文件多批次重试时总等待时间可能较长
     * （最多 7s/批 × N 批）。后续可改为异步重试 + 回调模式。
     */
    private List<List<Float>> embedBatchWithRetry(String model, List<ChunkData> chunks,
                                                   String apiUrl, String apiKey, String fileName) {
        if (model == null || model.isBlank()) return null;
        if (chunks == null || chunks.isEmpty()) return null;

        // embedding 输入拼接：content + headingPath + fileName（indexConfig.embedFields 一期固定）
        List<String> texts = chunks.stream()
                .map(c -> buildEmbedText(c, fileName))
                .collect(Collectors.toList());

        for (int attempt = 0; attempt < EMBED_MAX_RETRIES; attempt++) {
            long tEmbed = System.currentTimeMillis();
            try {
                List<List<Float>> vectors = embeddingService.embed(model, texts, apiUrl, apiKey);
                log.info("[TIMING] embed batch of {} chunks: {} ms, model={}, attempt={}",
                        chunks.size(), System.currentTimeMillis() - tEmbed, model, attempt + 1);
                return vectors;
            } catch (Exception e) {
                log.warn("Embedding batch failed for {} chunks (attempt {}/{}): {}",
                        chunks.size(), attempt + 1, EMBED_MAX_RETRIES, e.getMessage());

                if (attempt < EMBED_MAX_RETRIES - 1) {
                    // 指数退避：1s, 2s, 4s
                    long delay = (long) Math.pow(2, attempt) * 1000;
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.warn("Embedding batch failed after {} retries for {} chunks, storing without vectors",
                EMBED_MAX_RETRIES, chunks.size());
        return null;
    }

    /**
     * 构建 embedding 输入文本：content + headingPath + fileName。
     * 让层级信息进入向量检索（与 indexConfig.embedFields 一期固定策略一致）。
     */
    private String buildEmbedText(ChunkData chunk, String fileName) {
        StringBuilder sb = new StringBuilder();
        if (chunk.getContent() != null) {
            sb.append(chunk.getContent());
        }
        if (chunk.getHeadingPath() != null && !chunk.getHeadingPath().isBlank()) {
            sb.append("\n").append(chunk.getHeadingPath());
        }
        if (fileName != null && !fileName.isBlank()) {
            sb.append("\n").append(fileName);
        }
        return sb.toString();
    }
}
