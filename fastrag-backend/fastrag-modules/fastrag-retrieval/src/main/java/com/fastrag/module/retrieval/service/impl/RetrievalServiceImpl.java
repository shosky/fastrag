package com.fastrag.module.retrieval.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.rerank.RerankService;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.publish.entity.KbLog;
import com.fastrag.module.publish.mapper.KbLogMapper;
import com.fastrag.module.retrieval.entity.KbRetrievalLog;
import com.fastrag.module.retrieval.model.RetrievalRequest;
import com.fastrag.module.retrieval.model.SearchResultItem;
import com.fastrag.module.retrieval.service.QueryEnhanceService;
import com.fastrag.module.retrieval.service.RetrievalLogService;
import com.fastrag.module.retrieval.service.RetrievalService;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索服务实现
 *
 * <p>支持三种检索模式：vector（向量）、fulltext（全文）、hybrid（混合 RRF 融合）。</p>
 * <p>同时支持多路召回、查询预处理、重排序、MMR 多样性、上下文组装等能力。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private final KbChunkMapper chunkMapper;
    private final KbFileMapper fileMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final KbQaPairMapper qaPairMapper;
    private final RetrievalLogService logService;
    private final KbLogMapper kbLogMapper;
    private final MilvusService milvusService;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;
    private final QueryEnhanceService queryEnhanceService;
    private final ModelRecordMapper modelRecordMapper;

    /** RRF 融合常数 k */
    private static final int RRF_K = 60;

    // ========================================================================
    //  主入口
    // ========================================================================

    @Override
    public List<SearchResultItem> search(RetrievalRequest req) {
        long startMs = System.currentTimeMillis();
        String kbId = req.getKnowledgeId();
        String originalQuery = req.getQuery();

        if (originalQuery == null || originalQuery.isBlank()) {
            return Collections.emptyList();
        }

        // ---- Phase 7: 加载知识库配置并合并 ----
        RetrievalRequest.RetrievalConfig config = loadAndMergeConfig(kbId, req.getConfig());

        // ---- Phase 3: 查询预处理 ----
        String query = preprocessQuery(kbId, originalQuery, config);

        // ---- Phase 2 + Phase 5: 核心检索 ----
        List<SearchResultItem> results = executeSearch(kbId, query, config);

        // ---- Phase 4: 后处理（重排序 / MMR）----
        results = postProcess(results, query, config, kbId);

        // 按内容去重：同一 content 只保留相似度最高的一条
        results = deduplicateResults(results);

        // ---- Phase 6: 上下文组装 ----
        results = assembleContext(results, config);

        // ---- 统一设置结果序号 ----
        for (int i = 0; i < results.size(); i++) {
            results.get(i).setIndex(i);
        }

        // ---- 日志记录 ----
        recordLog(kbId, originalQuery, config, results, startMs);

        return results;
    }

    @Override
    public long getChunkCount(String kbId) {
        // 只统计未删除文件的 chunk（排除软删除文件）
        List<KbFile> activeFiles = fileMapper.selectList(
                new LambdaQueryWrapper<KbFile>()
                        .eq(KbFile::getKbId, kbId)
                        .isNull(KbFile::getDeletedAt));
        if (activeFiles.isEmpty()) return 0;
        List<String> activeFileIds = activeFiles.stream()
                .map(KbFile::getId).collect(Collectors.toList());
        return chunkMapper.selectCount(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getKbId, kbId)
                        .in(KbChunk::getFileId, activeFileIds));
    }

    // ========================================================================
    //  Phase 7: 知识库检索配置加载与合并
    // ========================================================================

    private RetrievalRequest.RetrievalConfig loadAndMergeConfig(String kbId,
                                                                  RetrievalRequest.RetrievalConfig requestConfig) {
        RetrievalRequest.RetrievalConfig merged = new RetrievalRequest.RetrievalConfig();

        // 默认值
        merged.setMode("hybrid");
        merged.setTopK(10);
        merged.setSimilarityThreshold(0.2);
        merged.setEnableGraphExpand(false);
        merged.setGraphExpandDepth(1);
        merged.setGraphMaxEntities(10);
        merged.setEnableRerank(false);

        // 1. 从知识库加载已保存的配置
        try {
            KnowledgeBase kb = kbMapper.selectById(kbId);
            if (kb != null && kb.getRetrievalConfig() != null) {
                String configJson = kb.getRetrievalConfig();
                RetrievalRequest.RetrievalConfig savedConfig =
                        JSONUtil.toBean(configJson, RetrievalRequest.RetrievalConfig.class);
                if (savedConfig != null) {
                    mergeConfig(merged, savedConfig);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load KB retrieval config for kbId={}, use defaults", kbId, e);
        }

        // 2. 请求参数覆盖（请求参数优先）
        if (requestConfig != null) {
            mergeConfig(merged, requestConfig);
        }

        return merged;
    }

    /** 将 source 中的非 null 字段复制到 target */
    private void mergeConfig(RetrievalRequest.RetrievalConfig target,
                              RetrievalRequest.RetrievalConfig source) {
        if (source.getMode() != null) target.setMode(source.getMode());
        if (source.getTopK() > 0) target.setTopK(source.getTopK());
        if (source.getSimilarityThreshold() > 0) target.setSimilarityThreshold(source.getSimilarityThreshold());
        if (source.isEnableGraphExpand()) target.setEnableGraphExpand(true);
        if (source.getGraphExpandDepth() > 0) target.setGraphExpandDepth(source.getGraphExpandDepth());
        if (source.getGraphMaxEntities() > 0) target.setGraphMaxEntities(source.getGraphMaxEntities());
        if (source.getNerModel() != null) target.setNerModel(source.getNerModel());
        if (source.isEnableRerank()) target.setEnableRerank(true);
        if (source.getRerankModel() != null) target.setRerankModel(source.getRerankModel());

        // 预处理
        if (source.getEnableAutoCorrection() != null) target.setEnableAutoCorrection(source.getEnableAutoCorrection());
        if (source.getEnableQueryRewrite() != null) target.setEnableQueryRewrite(source.getEnableQueryRewrite());
        if (source.getEnableSynonymExpansion() != null) target.setEnableSynonymExpansion(source.getEnableSynonymExpansion());

        // 多路召回
        if (source.getEnableMultiRetrieval() != null) target.setEnableMultiRetrieval(source.getEnableMultiRetrieval());
        if (source.getVectorRecallCount() != null) target.setVectorRecallCount(source.getVectorRecallCount());
        if (source.getFulltextRecallCount() != null) target.setFulltextRecallCount(source.getFulltextRecallCount());
        if (source.getGraphRecallCount() != null) target.setGraphRecallCount(source.getGraphRecallCount());
        if (source.getQaRecallCount() != null) target.setQaRecallCount(source.getQaRecallCount());
        if (source.getFusionStrategy() != null) target.setFusionStrategy(source.getFusionStrategy());

        // BM25
        if (source.getBm25RecallCount() != null) target.setBm25RecallCount(source.getBm25RecallCount());
        if (source.getVectorWeight() != null) target.setVectorWeight(source.getVectorWeight());
        if (source.getBm25Weight() != null) target.setBm25Weight(source.getBm25Weight());
        if (source.getBm25SparseDropRate() != null) target.setBm25SparseDropRate(source.getBm25SparseDropRate());

        // 重排序细化
        if (source.getEnableLLMRerank() != null) target.setEnableLLMRerank(source.getEnableLLMRerank());
        if (source.getEnableMMR() != null) target.setEnableMMR(source.getEnableMMR());
        if (source.getMmrLambda() != null) target.setMmrLambda(source.getMmrLambda());

        // 上下文组装
        if (source.getContextAssemblyStrategy() != null) target.setContextAssemblyStrategy(source.getContextAssemblyStrategy());
        if (source.getContextWindowSize() != null) target.setContextWindowSize(source.getContextWindowSize());
        if (source.getMaxContextTokens() != null) target.setMaxContextTokens(source.getMaxContextTokens());
        if (source.getContextOrder() != null) target.setContextOrder(source.getContextOrder());
    }

    // ========================================================================
    //  Phase 3: 查询预处理
    // ========================================================================

    private String preprocessQuery(String kbId, String query, RetrievalRequest.RetrievalConfig config) {
        String processed = query;

        // 自动纠错
        if (Boolean.TRUE.equals(config.getEnableAutoCorrection())) {
            try {
                Map<String, Object> suggest = queryEnhanceService.suggest(processed);
                String corrected = (String) suggest.get("suggestedQuery");
                if (corrected != null && !corrected.isBlank() && !corrected.equals(processed)) {
                    log.info("[Preprocess] Auto-correct: '{}' -> '{}'", processed, corrected);
                    processed = corrected;
                }
            } catch (Exception e) {
                log.warn("[Preprocess] Auto-correct failed", e);
            }
        }

        // 查询改写
        if (Boolean.TRUE.equals(config.getEnableQueryRewrite())) {
            try {
                Map<String, Object> rewritten = queryEnhanceService.applyQueryRules(processed);
                String rw = (String) rewritten.get("rewritten");
                if (rw != null && !rw.isBlank() && !rw.equals(processed)) {
                    log.info("[Preprocess] Query rewrite: '{}' -> '{}'", processed, rw);
                    processed = rw;
                }
            } catch (Exception e) {
                log.warn("[Preprocess] Query rewrite failed", e);
            }
        }

        // 图谱扩展
        if (config.isEnableGraphExpand()) {
            try {
                Map<String, Object> graphResult = queryEnhanceService.expandGraph(
                        kbId, processed, config.getGraphExpandDepth(),
                        config.getGraphMaxEntities(), config.getNerModel());
                String expanded = (String) graphResult.get("expandedQuery");
                if (expanded != null && !expanded.isBlank() && !expanded.equals(processed)) {
                    log.info("[Preprocess] Graph expansion: '{}' -> '{}'", processed, expanded);
                    processed = expanded;
                }
            } catch (Exception e) {
                log.warn("[Preprocess] Graph expansion failed", e);
            }
        }

        // 同义词扩展
        if (Boolean.TRUE.equals(config.getEnableSynonymExpansion())) {
            try {
                Map<String, Object> synonymResult = queryEnhanceService.expandSynonyms(processed);
                String expanded = (String) synonymResult.get("expandedQuery");
                if (expanded != null && !expanded.isBlank() && !expanded.equals(processed)) {
                    log.info("[Preprocess] Synonym expansion: '{}' -> '{}'", processed, expanded);
                    processed = expanded;
                }
            } catch (Exception e) {
                log.warn("[Preprocess] Synonym expansion failed", e);
            }
        }

        return processed;
    }

    // ========================================================================
    //  Phase 2 + Phase 5: 核心检索分发
    // ========================================================================

    private List<SearchResultItem> executeSearch(String kbId, String query,
                                                  RetrievalRequest.RetrievalConfig config) {
        // 多路召回模式
        if (Boolean.TRUE.equals(config.getEnableMultiRetrieval())) {
            return multiChannelRecall(kbId, query, config);
        }

        // 单一模式
        String mode = config.getMode() != null ? config.getMode() : "hybrid";
        return switch (mode) {
            case "vector" -> vectorSearch(kbId, query, config);
            case "fulltext" -> fulltextSearch(kbId, query, config);
            default -> hybridSearch(kbId, query, config);
        };
    }

    // ========================================================================
    //  向量检索
    // ========================================================================

    private List<SearchResultItem> vectorSearch(String kbId, String query,
                                                  RetrievalRequest.RetrievalConfig config) {
        int topK = config.getTopK();
        double threshold = config.getSimilarityThreshold();

        try {
            // 1. 生成查询向量（通过模型管理解析知识库配置的嵌入模型 API）
            EmbeddingModelConfig embedConfig = getEmbeddingModelConfig(kbId);
            List<List<Float>> queryEmbeddings = embeddingService.embed(
                    embedConfig != null ? embedConfig.model() : null,
                    List.of(query),
                    embedConfig != null ? embedConfig.apiUrl() : null,
                    embedConfig != null ? embedConfig.apiKey() : null);
            List<Float> queryVector = queryEmbeddings.get(0);

            // 2. Milvus 搜索（多取一些以便后续过滤）
            String collection = "kb_" + kbId.replace("-", "_");
            List<Map<String, Object>> milvusResults = milvusService.search(collection, queryVector, topK * 3);

            if (milvusResults.isEmpty()) {
                log.warn("[VectorSearch] No results from Milvus for kbId={}", kbId);
                return Collections.emptyList();
            }

            // 3. 从 MySQL 查询完整内容
            List<String> chunkIds = milvusResults.stream()
                    .map(r -> (String) r.get("id"))
                    .collect(Collectors.toList());
            Map<String, KbChunk> chunkMap = chunkMapper.selectByIds(chunkIds).stream()
                    .filter(c -> c.getFileId() != null && !isFileDeleted(kbId, c.getFileId()))
                    .collect(Collectors.toMap(KbChunk::getId, c -> c, (a, b) -> a));

            // 4. 组装结果，按相似度降序排列
            List<SearchResultItem> results = new ArrayList<>();
            for (Map<String, Object> mv : milvusResults) {
                String chunkId = (String) mv.get("id");
                double score = ((Number) mv.get("score")).doubleValue();

                // COSINE 相似度 >= threshold
                if (score < threshold) continue;

                KbChunk chunk = chunkMap.get(chunkId);
                if (chunk == null) continue;

                SearchResultItem item = buildResultItem(chunk, score, 1.0 - score, "milvus", "vector");
                results.add(item);
                if (results.size() >= topK) break;
            }

            log.info("[VectorSearch] kbId={}, query={}, hits={}", kbId, query, results.size());
            return results;

        } catch (Exception e) {
            log.error("[VectorSearch] Failed for kbId={}, fallback to LIKE", kbId, e);
            return fallbackLikeSearch(kbId, query, topK);
        }
    }

    // ========================================================================
    //  全文检索（MySQL FULLTEXT）
    // ========================================================================

    private List<SearchResultItem> fulltextSearch(String kbId, String query,
                                                    RetrievalRequest.RetrievalConfig config) {
        int topK = config.getTopK();
        try {
            // 尝试用 FULLTEXT 索引搜索
            List<KbChunk> chunks = chunkMapper.fulltextSearch(kbId, query, topK);
            if (!chunks.isEmpty()) {
                List<SearchResultItem> results = new ArrayList<>();
                for (int i = 0; i < chunks.size(); i++) {
                    KbChunk chunk = chunks.get(i);
                    // 跳过已删除文件的 chunk
                    if (chunk.getFileId() != null && isFileDeleted(kbId, chunk.getFileId())) {
                        continue;
                    }
                    SearchResultItem item = buildResultItem(chunk, 1.0 - (double) i / chunks.size(),
                            0.0, "mysql_fulltext", "fulltext");
                    results.add(item);
                    if (results.size() >= topK) break;
                }
                log.info("[FulltextSearch] kbId={}, query={}, hits={} (filtered {})",
                        kbId, query, results.size(), chunks.size() - results.size());
                return results;
            }
        } catch (Exception e) {
            log.warn("[FulltextSearch] FULLTEXT failed for kbId={}, fallback to LIKE", kbId, e);
        }

        // 降级：LIKE 查询
        return fallbackLikeSearch(kbId, query, topK);
    }

    // ========================================================================
    //  混合检索（Vector + Fulltext RRF 融合）
    // ========================================================================

    private List<SearchResultItem> hybridSearch(String kbId, String query,
                                                  RetrievalRequest.RetrievalConfig config) {
        int topK = config.getTopK();
        int recallCount = Math.max(topK * 3, 50); // 各路多召回一些用于融合

        // 1. 向量检索候选
        RetrievalRequest.RetrievalConfig vectorCfg = copyWithMode(config, "vector", recallCount);
        List<SearchResultItem> vectorResults = safeSearch(() -> vectorSearch(kbId, query, vectorCfg));

        // 2. 全文检索候选
        RetrievalRequest.RetrievalConfig fulltextCfg = copyWithMode(config, "fulltext", recallCount);
        List<SearchResultItem> fulltextResults = safeSearch(() -> fulltextSearch(kbId, query, fulltextCfg));

        // 3. RRF 融合
        List<SearchResultItem> fused = rrfFusion(vectorResults, fulltextResults, topK);

        log.info("[HybridSearch] kbId={}, query={}, vector={}, fulltext={}, fused={}",
                kbId, query, vectorResults.size(), fulltextResults.size(), fused.size());
        return fused;
    }

    // ========================================================================
    //  Phase 5: 多路召回 + 融合
    // ========================================================================

    private List<SearchResultItem> multiChannelRecall(String kbId, String query,
                                                       RetrievalRequest.RetrievalConfig config) {
        int vCount = config.getVectorRecallCount() != null ? config.getVectorRecallCount() : config.getTopK();
        int fCount = config.getFulltextRecallCount() != null ? config.getFulltextRecallCount() : config.getTopK();
        int gCount = config.getGraphRecallCount() != null ? config.getGraphRecallCount() : 0;
        int qCount = config.getQaRecallCount() != null ? config.getQaRecallCount() : 0;
        String strategy = config.getFusionStrategy() != null ? config.getFusionStrategy() : "rrf";

        // 各路并行召回
        List<List<SearchResultItem>> channels = new ArrayList<>();

        // 向量通道
        RetrievalRequest.RetrievalConfig vCfg = copyWithMode(config, "vector", vCount);
        channels.add(safeSearch(() -> vectorSearch(kbId, query, vCfg)));

        // 全文通道
        RetrievalRequest.RetrievalConfig fCfg = copyWithMode(config, "fulltext", fCount);
        channels.add(safeSearch(() -> fulltextSearch(kbId, query, fCfg)));

        // 图谱子图通道
        if (gCount > 0) {
            channels.add(safeSearch(() -> graphChannelRecall(kbId, query, config, gCount)));
        }

        // QA 对通道
        if (qCount > 0) {
            channels.add(safeSearch(() -> qaChannelRecall(kbId, query, qCount)));
        }

        // 融合
        return switch (strategy) {
            case "weighted" -> weightedFusion(channels, config, config.getTopK());
            case "interleave" -> interleaveFusion(channels, config.getTopK());
            default -> rrfFusionMulti(channels, config.getTopK());
        };
    }

    /**
     * 图谱子图召回：在知识图谱中展开实体，找到关联的 chunk
     */
    private List<SearchResultItem> graphChannelRecall(String kbId, String query,
                                                       RetrievalRequest.RetrievalConfig config, int count) {
        try {
            Map<String, Object> graphResult = queryEnhanceService.expandGraph(
                    kbId, query, config.getGraphExpandDepth(),
                    config.getGraphMaxEntities(), config.getNerModel());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>)
                    graphResult.getOrDefault("entities", List.of());

            if (entities.isEmpty()) return Collections.emptyList();

            // 用实体名去搜索 chunk
            List<String> entityNames = entities.stream()
                    .map(e -> (String) e.get("name"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<SearchResultItem> results = new ArrayList<>();
            for (String name : entityNames) {
                if (results.size() >= count) break;
                List<KbChunk> chunks = chunkMapper.selectList(
                        new LambdaQueryWrapper<KbChunk>()
                                .eq(KbChunk::getKbId, kbId)
                                .like(KbChunk::getContent, name)
                                .last("LIMIT " + (count / Math.max(entityNames.size(), 1))));
                for (KbChunk chunk : chunks) {
                    if (results.size() >= count) break;
                    // 跳过已删除文件的 chunk
                    if (chunk.getFileId() != null && isFileDeleted(kbId, chunk.getFileId())) {
                        continue;
                    }
                    results.add(buildResultItem(chunk, 0.5, 0.5, "graph", "graph"));
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("[GraphChannel] Failed for kbId={}", kbId, e);
            return Collections.emptyList();
        }
    }

    /**
     * QA 对召回：从 kb_qa_pair 表匹配问题
     */
    private List<SearchResultItem> qaChannelRecall(String kbId, String query, int count) {
        try {
            List<KbQaPair> qaPairs = qaPairMapper.selectList(
                    new LambdaQueryWrapper<KbQaPair>()
                            .eq(KbQaPair::getKbId, kbId)
                            .like(KbQaPair::getQuestion, query)
                            .last("LIMIT " + count));

            List<SearchResultItem> results = new ArrayList<>();
            for (int i = 0; i < qaPairs.size(); i++) {
                KbQaPair qa = qaPairs.get(i);
                SearchResultItem item = new SearchResultItem();
                item.setIndex(i);
                item.setContent("Q: " + qa.getQuestion() + "\nA: " + qa.getAnswer());
                item.setSource("qa");
                item.setChannel("qa");
                item.setFileId(qa.getFileId());
                item.setSimilarity(0.8 - (double) i / qaPairs.size());
                item.setDistance(0.2 + (double) i / qaPairs.size());
                results.add(item);
            }
            return results;
        } catch (Exception e) {
            log.warn("[QAChannel] Failed for kbId={}", kbId, e);
            return Collections.emptyList();
        }
    }

    // ========================================================================
    //  融合策略
    // ========================================================================

    /**
     * RRF 融合（两路）
     */
    private List<SearchResultItem> rrfFusion(List<SearchResultItem> list1,
                                              List<SearchResultItem> list2, int topK) {
        return rrfFusionMulti(List.of(list1, list2), topK);
    }

    /**
     * RRF 融合（多路）
     */
    private List<SearchResultItem> rrfFusionMulti(List<List<SearchResultItem>> channels, int topK) {
        // 计算每个结果的 RRF score
        Map<String, SearchResultItem> itemMap = new LinkedHashMap<>();
        Map<String, Double> scoreMap = new HashMap<>();

        for (List<SearchResultItem> channel : channels) {
            for (int rank = 0; rank < channel.size(); rank++) {
                SearchResultItem item = channel.get(rank);
                String key = item.getFileId() + "_" + item.getChunkIndex();
                // 以第一个出现的为准保留元信息
                itemMap.putIfAbsent(key, item);
                // RRF score = 1 / (k + rank)
                scoreMap.merge(key, 1.0 / (RRF_K + rank), Double::sum);
            }
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    SearchResultItem item = itemMap.get(e.getKey());
                    item.setSimilarity(e.getValue());
                    item.setDistance(1.0 / (1.0 + e.getValue()));
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 加权融合
     */
    private List<SearchResultItem> weightedFusion(List<List<SearchResultItem>> channels,
                                                    RetrievalRequest.RetrievalConfig config, int topK) {
        double vWeight = config.getVectorWeight() != null ? config.getVectorWeight() : 0.5;
        double fWeight = config.getBm25Weight() != null ? config.getBm25Weight() : 0.5;
        double[] weights = {vWeight, fWeight, 0.3, 0.3}; // vector, fulltext, graph, qa

        Map<String, SearchResultItem> itemMap = new LinkedHashMap<>();
        Map<String, Double> scoreMap = new HashMap<>();

        for (int ch = 0; ch < channels.size() && ch < weights.length; ch++) {
            double w = weights[ch];
            List<SearchResultItem> channel = channels.get(ch);
            int size = channel.size();
            for (int rank = 0; rank < size; rank++) {
                SearchResultItem item = channel.get(rank);
                String key = item.getFileId() + "_" + item.getChunkIndex();
                itemMap.putIfAbsent(key, item);
                scoreMap.merge(key, w * (1.0 - (double) rank / size), Double::sum);
            }
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    SearchResultItem item = itemMap.get(e.getKey());
                    item.setSimilarity(e.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }

    /**
     * 交叉融合（轮流从各路取结果）
     */
    private List<SearchResultItem> interleaveFusion(List<List<SearchResultItem>> channels, int topK) {
        List<SearchResultItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int maxLen = channels.stream().mapToInt(List::size).max().orElse(0);

        for (int i = 0; i < maxLen && result.size() < topK; i++) {
            for (List<SearchResultItem> channel : channels) {
                if (i < channel.size()) {
                    SearchResultItem item = channel.get(i);
                    String key = item.getFileId() + "_" + item.getChunkIndex();
                    if (seen.add(key)) {
                        result.add(item);
                        if (result.size() >= topK) break;
                    }
                }
            }
        }
        return result;
    }

    // ========================================================================
    //  Phase 4: 后处理（重排序 / MMR）
    // ========================================================================

    private List<SearchResultItem> postProcess(List<SearchResultItem> results, String query,
                                                 RetrievalRequest.RetrievalConfig config, String kbId) {
        if (results.isEmpty()) return results;

        // Rerank 模型重排
        if (config.isEnableRerank() && config.getRerankModel() != null) {
            results = rerankResults(results, query, config, kbId);
        }

        // LLM 重排
        if (Boolean.TRUE.equals(config.getEnableLLMRerank())) {
            results = llmRerankResults(results, config);
        }

        // MMR 多样性控制
        if (Boolean.TRUE.equals(config.getEnableMMR())) {
            double lambda = config.getMmrLambda() != null ? config.getMmrLambda() : 0.7;
            results = mmrDiversity(results, query, lambda, config.getTopK(), kbId);
        }

        return results;
    }

    /**
     * Rerank 模型重排（通过模型管理解析 API 配置）
     */
    private List<SearchResultItem> rerankResults(List<SearchResultItem> candidates, String query,
                                                   RetrievalRequest.RetrievalConfig config, String kbId) {
        try {
            List<String> documents = candidates.stream()
                    .map(SearchResultItem::getContent)
                    .collect(Collectors.toList());

            String rerankModel = config.getRerankModel();
            if (rerankModel == null || rerankModel.isBlank()) {
                log.warn("[Rerank] No rerank model configured, skip rerank");
                return candidates.subList(0, Math.min(config.getTopK(), candidates.size()));
            }
            // 解析 rerank 模型的 API 配置
            String apiUrl = null;
            String apiKey = null;
            ModelRecord modelRecord = modelRecordMapper.selectOne(
                    new LambdaQueryWrapper<ModelRecord>()
                            .eq(ModelRecord::getCode, rerankModel)
                            .eq(ModelRecord::getStatus, "online")
                            .last("LIMIT 1"));
            if (modelRecord != null) {
                apiUrl = modelRecord.getApiUrl();
                apiKey = modelRecord.getApiKeyRef();
                log.info("[Rerank] Resolved model '{}' -> apiUrl={}", rerankModel, apiUrl);
            } else {
                log.warn("[Rerank] Model '{}' not found in model table or offline, skip rerank", rerankModel);
                return candidates.subList(0, Math.min(config.getTopK(), candidates.size()));
            }

            List<Map<String, Object>> rerankResults = rerankService.rerank(
                    rerankModel, query, documents, config.getTopK(), apiUrl, apiKey);

            if (rerankResults.isEmpty()) {
                return candidates.subList(0, Math.min(config.getTopK(), candidates.size()));
            }

            // rerank 返回的 index 指向原始 candidates 的位置
            List<SearchResultItem> reranked = new ArrayList<>();
            for (Map<String, Object> rr : rerankResults) {
                int index = ((Number) rr.get("index")).intValue();
                double score = ((Number) rr.get("relevance_score")).doubleValue();
                if (index >= 0 && index < candidates.size()) {
                    SearchResultItem item = candidates.get(index);
                    item.setSimilarity(score);
                    item.setDistance(1.0 - score);
                    reranked.add(item);
                }
            }
            return reranked;
        } catch (Exception e) {
            log.warn("[Rerank] Failed, return topK candidates", e);
            return candidates.subList(0, Math.min(config.getTopK(), candidates.size()));
        }
    }

    /**
     * LLM 重排（用大模型对候选结果打分）
     */
    private List<SearchResultItem> llmRerankResults(List<SearchResultItem> candidates,
                                                      RetrievalRequest.RetrievalConfig config) {
        // LLM rerank 为高阶功能，当前简化实现：直接返回 topK
        log.info("[LLMRerank] Using default topK truncation");
        int limit = Math.min(config.getTopK(), candidates.size());
        return candidates.subList(0, limit);
    }

    /**
     * MMR 多样性控制
     * score = λ * similarity(q, d) - (1-λ) * max_{j in selected} similarity(d, d_j)
     */
    private List<SearchResultItem> mmrDiversity(List<SearchResultItem> candidates, String query,
                                                  double lambda, int topK, String kbId) {
        if (candidates.size() <= 1) return candidates;

        try {
            // 获取所有候选的向量用于计算相似度
            List<String> texts = candidates.stream()
                    .map(SearchResultItem::getContent)
                    .collect(Collectors.toList());
            EmbeddingModelConfig config = getEmbeddingModelConfig(kbId);
            List<List<Float>> vectors = (config != null && config.model() != null)
                    ? embeddingService.embed(config.model(), texts, config.apiUrl(), config.apiKey())
                    : Collections.emptyList();

            if (vectors.size() != candidates.size()) {
                return candidates.subList(0, Math.min(topK, candidates.size()));
            }

            int n = candidates.size();
            double[][] simMatrix = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    simMatrix[i][j] = cosineSimilarity(vectors.get(i), vectors.get(j));
                }
            }

            // 贪婪选择
            List<SearchResultItem> selected = new ArrayList<>();
            boolean[] used = new boolean[n];
            double[] querySim = new double[n];
            for (int i = 0; i < n; i++) {
                querySim[i] = simMatrix[0][i]; // 以第一个候选的相似度为基准
                // 更精确应该用 query 向量，但这里复用候选向量近似
            }

            for (int iter = 0; iter < Math.min(topK, n); iter++) {
                int bestIdx = -1;
                double bestScore = -Double.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (used[i]) continue;
                    double maxSimToSelected = 0;
                    for (int j = 0; j < selected.size(); j++) {
                        int selIdx = candidates.indexOf(selected.get(j));
                        maxSimToSelected = Math.max(maxSimToSelected, simMatrix[i][selIdx]);
                    }
                    double mmrScore = lambda * candidates.get(i).getSimilarity()
                            - (1 - lambda) * maxSimToSelected;
                    if (mmrScore > bestScore) {
                        bestScore = mmrScore;
                        bestIdx = i;
                    }
                }
                if (bestIdx >= 0) {
                    used[bestIdx] = true;
                    selected.add(candidates.get(bestIdx));
                }
            }
            return selected;
        } catch (Exception e) {
            log.warn("[MMR] Failed, return topK candidates", e);
            return candidates.subList(0, Math.min(topK, candidates.size()));
        }
    }

    // ========================================================================
    //  Phase 6: 上下文组装
    // ========================================================================

    private List<SearchResultItem> assembleContext(List<SearchResultItem> results,
                                                     RetrievalRequest.RetrievalConfig config) {
        String strategy = config.getContextAssemblyStrategy();
        if (strategy == null || "concat".equals(strategy)) {
            return results; // 直接返回命中的 chunk
        }

        return switch (strategy) {
            case "parent_document" -> assembleParentDocument(results);
            case "window" -> assembleWindow(results, config);
            default -> results;
        };
    }

    /**
     * 父文档模式：按 fileId 分组，返回每个文件的完整内容
     */
    private List<SearchResultItem> assembleParentDocument(List<SearchResultItem> results) {
        Set<String> fileIds = results.stream()
                .map(SearchResultItem::getFileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<SearchResultItem> docResults = new ArrayList<>();
        for (String fileId : fileIds) {
            List<KbChunk> chunks = chunkMapper.selectByFileId(fileId);
            String fullContent = chunks.stream()
                    .map(KbChunk::getContent)
                    .collect(Collectors.joining("\n\n"));
            SearchResultItem item = new SearchResultItem();
            item.setIndex(docResults.size());
            item.setContent(fullContent);
            item.setFileId(fileId);
            item.setSource(results.stream()
                    .filter(r -> fileId.equals(r.getFileId()))
                    .findFirst().map(SearchResultItem::getSource).orElse("mysql"));
            item.setSimilarity(results.stream()
                    .filter(r -> fileId.equals(r.getFileId()))
                    .mapToDouble(SearchResultItem::getSimilarity)
                    .max().orElse(1.0));
            docResults.add(item);
        }
        return docResults;
    }

    /**
     * 窗口模式：对命中的 chunk，获取其前后 N 个 chunk
     */
    private List<SearchResultItem> assembleWindow(List<SearchResultItem> results,
                                                    RetrievalRequest.RetrievalConfig config) {
        int windowSize = config.getContextWindowSize() != null ? config.getContextWindowSize() : 2;
        String order = config.getContextOrder() != null ? config.getContextOrder() : "relevance";

        // 对命中的 chunk 按 (fileId, chunkIndex) 找到其邻居
        Set<String> seenChunks = new HashSet<>();
        List<SearchResultItem> windowed = new ArrayList<>();

        for (SearchResultItem hit : results) {
            String fileId = hit.getFileId();
            int chunkIdx = hit.getChunkIndex();
            if (fileId == null) {
                windowed.add(hit);
                continue;
            }

            // 查询该文件的所有 chunk
            List<KbChunk> fileChunks = chunkMapper.selectByFileId(fileId);
            // 找到命中 chunk 在列表中的位置
            int pos = -1;
            for (int i = 0; i < fileChunks.size(); i++) {
                if (fileChunks.get(i).getChunkIndex() != null
                        && fileChunks.get(i).getChunkIndex() == chunkIdx) {
                    pos = i;
                    break;
                }
            }
            if (pos < 0) {
                windowed.add(hit);
                continue;
            }

            // 前后 windowSize 个 chunk
            int start = Math.max(0, pos - windowSize);
            int end = Math.min(fileChunks.size(), pos + windowSize + 1);
            for (int i = start; i < end; i++) {
                KbChunk chunk = fileChunks.get(i);
                if (seenChunks.add(chunk.getId())) {
                    SearchResultItem item = buildResultItem(chunk,
                            hit.getSimilarity() * (1 - 0.1 * Math.abs(i - pos)),
                            0.0, hit.getSource(), hit.getChannel());
                    windowed.add(item);
                }
            }
        }

        // 排序
        if ("document_order".equals(order)) {
            windowed.sort(Comparator.comparing(SearchResultItem::getFileId)
                    .thenComparingInt(SearchResultItem::getChunkIndex));
        }
        // relevance 排序已经是按添加顺序（命中 chunk 优先）

        return windowed;
    }

    // ========================================================================
    //  工具方法
    // ========================================================================

    /** 构建 SearchResultItem */
    private SearchResultItem buildResultItem(KbChunk chunk, double similarity,
                                              double distance, String source, String channel) {
        SearchResultItem item = new SearchResultItem();
        item.setContent(chunk.getContent());
        item.setFileId(chunk.getFileId());
        item.setChunkIndex(chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0);
        item.setSimilarity(similarity);
        item.setDistance(distance);
        item.setSource(source);
        item.setChannel(channel);
        // 截取前 200 字作为预览
        String content = chunk.getContent();
        if (content != null && content.length() > 200) {
            item.setPreviewSnippet(content.substring(0, 200) + "...");
        } else {
            item.setPreviewSnippet(content);
        }
        return item;
    }

    /** 获取知识库的嵌入模型完整配置（模型名 + API地址 + 密钥） */
    private EmbeddingModelConfig getEmbeddingModelConfig(String kbId) {
        if (kbId == null || kbId.isBlank()) return null;
        try {
            KnowledgeBase kb = kbMapper.selectById(kbId);
            if (kb == null || kb.getEmbeddingModel() == null || kb.getEmbeddingModel().isBlank()) {
                return null;
            }
            String modelCode = kb.getEmbeddingModel();

            ModelRecord modelRecord = modelRecordMapper.selectOne(
                    new LambdaQueryWrapper<ModelRecord>()
                            .eq(ModelRecord::getCode, modelCode)
                            .eq(ModelRecord::getStatus, "online")
                            .last("LIMIT 1"));
            if (modelRecord != null) {
                return new EmbeddingModelConfig(modelCode, modelRecord.getApiUrl(), modelRecord.getApiKeyRef());
            }
            log.warn("Embedding model '{}' not found in model table or offline, using default gateway", modelCode);
            return new EmbeddingModelConfig(modelCode, null, null);
        } catch (Exception e) {
            log.warn("[getEmbeddingModel] Failed for kbId={}", kbId, e);
            return null;
        }
    }

    /** 嵌入模型配置 */
    private record EmbeddingModelConfig(String model, String apiUrl, String apiKey) {}

    /** 检查文件是否已软删除 */
    private boolean isFileDeleted(String kbId, String fileId) {
        try {
            KbFile file = fileMapper.selectById(fileId);
            return file != null && file.getDeletedAt() != null;
        } catch (Exception e) {
            log.warn("[isFileDeleted] Check failed for fileId={}", fileId, e);
            return false;
        }
    }

    /** 计算余弦相似度 */
    private double cosineSimilarity(List<Float> v1, List<Float> v2) {
        if (v1 == null || v2 == null || v1.size() != v2.size()) return 0;
        double dot = 0, n1 = 0, n2 = 0;
        for (int i = 0; i < v1.size(); i++) {
            double a = v1.get(i), b = v2.get(i);
            dot += a * b;
            n1 += a * a;
            n2 += b * b;
        }
        double denom = Math.sqrt(n1) * Math.sqrt(n2);
        return denom == 0 ? 0 : dot / denom;
    }

    /** 按内容去重：同一 content 只保留相似度最高的一条 */
    private List<SearchResultItem> deduplicateResults(List<SearchResultItem> results) {
        if (results.size() <= 1) return results;

        Map<String, SearchResultItem> bestByContent = new LinkedHashMap<>();
        for (SearchResultItem item : results) {
            String content = item.getContent();
            if (content == null) continue;
            // 用内容前 500 字符 + 长度做去重 key，兼顾性能和准确度
            String key = content.length() > 500 ? content.substring(0, 500) + "_" + content.length() : content;
            bestByContent.merge(key, item, (existing, candidate) ->
                    candidate.getSimilarity() > existing.getSimilarity() ? candidate : existing
            );
        }

        List<SearchResultItem> deduped = new ArrayList<>(bestByContent.values());
        deduped.sort(Comparator.comparingDouble(SearchResultItem::getSimilarity).reversed());
        return deduped;
    }

    /** 安全执行搜索，失败返回空列表 */
    private List<SearchResultItem> safeSearch(java.util.function.Supplier<List<SearchResultItem>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("[SafeSearch] Search failed", e);
            return Collections.emptyList();
        }
    }

    /** 复制配置并修改 mode 和 topK */
    private RetrievalRequest.RetrievalConfig copyWithMode(RetrievalRequest.RetrievalConfig src,
                                                           String mode, int topK) {
        RetrievalRequest.RetrievalConfig cfg = new RetrievalRequest.RetrievalConfig();
        mergeConfig(cfg, src);
        cfg.setMode(mode);
        cfg.setTopK(topK);
        return cfg;
    }

    // ========================================================================
    //  降级方案：MySQL LIKE 搜索
    // ========================================================================

    private List<SearchResultItem> fallbackLikeSearch(String kbId, String query, int topK) {
        log.info("[Fallback] MySQL LIKE search: kbId={}, query={}, topK={}", kbId, query, topK);
        List<KbChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getKbId, kbId)
                        .like(KbChunk::getContent, query)
                        .orderByDesc(KbChunk::getChunkIndex)
                        .last("LIMIT " + (topK * 3)));
        List<SearchResultItem> results = new ArrayList<>();
        for (int i = 0; i < chunks.size() && results.size() < topK; i++) {
            KbChunk chunk = chunks.get(i);
            // 跳过已删除文件的 chunk
            if (chunk.getFileId() != null && isFileDeleted(kbId, chunk.getFileId())) {
                continue;
            }
            results.add(buildResultItem(chunk, 1.0, 0.0, "mysql", null));
        }
        log.info("[Fallback] kbId={}, query={}, hits={} (filtered from {})", kbId, query, results.size(), chunks.size());
        return results;
    }

    // ========================================================================
    //  日志记录
    // ========================================================================

    private void recordLog(String kbId, String originalQuery, RetrievalRequest.RetrievalConfig config,
                            List<SearchResultItem> results, long startMs) {
        long elapsed = System.currentTimeMillis() - startMs;
        try {
            KbRetrievalLog logEntry = new KbRetrievalLog();
            logEntry.setKbId(kbId);
            logEntry.setQuery(originalQuery);
            logEntry.setHitCount(results.size());
            logEntry.setHasResult(!results.isEmpty());
            logEntry.setLatencyMs((int) elapsed);
            logEntry.setUserId(SecurityUtil.getCurrentUserId());
            logEntry.setCreatedAt(LocalDateTime.now());
            logService.log(logEntry);

            KbLog kbLog = new KbLog();
            kbLog.setKbId(kbId);
            kbLog.setCategory("retrieval");
            kbLog.setAction("search");
            kbLog.setTarget(originalQuery);
            kbLog.setDetail("检索完成，命中 " + results.size() + " 条，模式=" + config.getMode());
            kbLog.setOperator(SecurityUtil.getCurrentUser() != null
                    ? SecurityUtil.getCurrentUser().getUsername() : "system");
            kbLog.setStatus("success");
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("mode", config.getMode());
            extra.put("topK", config.getTopK());
            extra.put("hits", results.size());
            extra.put("duration", elapsed);
            kbLog.setExtra(JSONUtil.toJsonStr(extra));
            kbLog.setTimestamp(LocalDateTime.now());
            kbLogMapper.insert(kbLog);
        } catch (Exception e) {
            log.warn("记录检索日志失败", e);
        }
    }
}
