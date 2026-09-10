package com.fastrag.module.retrieval.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.rerank.RerankService;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.infra.graph.GraphStore;
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
import com.fastrag.module.platform.entity.SysConfig;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.platform.service.ConfigManageService;
import com.fastrag.module.publish.entity.KbLog;
import com.fastrag.module.publish.mapper.KbLogMapper;
import com.fastrag.module.retrieval.entity.KbRetrievalLog;
import com.fastrag.module.retrieval.model.RetrievalRequest;
import com.fastrag.module.retrieval.model.SearchResultItem;
import com.fastrag.module.retrieval.service.QueryEnhanceService;
import com.fastrag.module.retrieval.service.RetrievalLogService;
import com.fastrag.module.retrieval.service.RetrievalService;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 检索服务实现 —— RAG 系统核心检索引擎。
 *
 * <p>实现 {@link RetrievalService} 接口，是知识库检索的完整执行链路，涵盖从查询预处理、
 * 多路召回、融合排序、后处理到上下文组装的全流程。</p>
 *
 * <h3>支持的检索模式：</h3>
 * <ul>
 *   <li><b>vector</b>：纯向量检索（Milvus COSINE 近似搜索），失败时降级为 MySQL LIKE</li>
 *   <li><b>fulltext</b>：全文检索（MySQL FULLTEXT 索引），失败时降级为 MySQL LIKE</li>
 *   <li><b>hybrid</b>：混合检索，向量 + 全文双路召回后加权融合（默认模式）</li>
 * </ul>
 *
 * <h3>核心处理流程（Phase 1-7）：</h3>
 * <ol>
 *   <li>Phase 3 - 查询预处理：自动纠错、查询改写（规则）、同义词扩展（可选）</li>
 *   <li>Phase 2 - 核心检索分发：根据 mode 选择 vector/fulltext/hybrid，或进入多路召回模式</li>
 *   <li>Phase 5 - 多路召回 + 融合：支持 RRF（Reciprocal Rank Fusion）、加权融合、交叉融合三种策略，
 *       召回通道包括向量、全文、QA 问答对、图谱子图（GraphStore 实体匹配 + 邻居 LIKE）</li>
 *   <li>图谱通道（方案 A）：独立召回通道，通过 NER + 向量语义双路匹配实体，再 LIKE 召回关联 chunk，
 *       与主检索结果 RRF 融合（不将实体名拼入 query 避免稀释 embedding）</li>
 *   <li>Phase 4 - 后处理：Rerank 模型重排、LLM 重排、MMR 多样性控制（可选）</li>
 *   <li>Phase 6 - 上下文组装：支持 concat（直接拼接）、parent_document（父文档整篇）、
 *       window（前后 N 个 chunk 窗口）、parent_chunk（父分片放大）四种策略，
 *       以及 <b>auto</b>（默认）：自动降级链 parent_chunk → window → concat，
 *       命中小片时自动带回父块/窗口完整上下文，弥补分片切断导致的回答不完整；
 *       另含条款/标题线索召回（enableClauseRecall）补救政策条款交叉引用</li>
 *   <li>Phase 7 - 配置加载与合并：优先级为 请求参数 &gt; 知识库保存配置 &gt; 系统默认值</li>
 * </ol>
 *
 * <h3>关键依赖：</h3>
 * <ul>
 *   <li>{@link MilvusService}：向量相似度搜索</li>
 *   <li>{@link EmbeddingService}：查询文本向量化</li>
 *   <li>{@link RerankService}：Rerank 模型重排序</li>
 *   <li>{@link QueryEnhanceService}：NER 实体识别、图谱扩展、同义词扩展、查询改写</li>
 *   <li>{@link GraphStore}：知识图谱实体存储与向量搜索</li>
 * </ul>
 *
 * <p>额外能力：权限校验（Redis kb:acl 缓存）、内容去重、检索日志双写（KbRetrievalLog + KbLog）。</p>
 *
 * @see RetrievalService
 * @see RetrievalRequest
 * @see SearchResultItem
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
    private final ConfigManageService configService;
    private final GraphStore graphStore;
    private final StringRedisTemplate redisTemplate;

    /**
     * 校验当前用户对知识库的访问权限（body 传 kbId 的接口，KbAuthAspect 的 URI 提取不适用）
     * 与 KbAuthAspect 同构：Redis kb:acl:{kbId}:{userId}，无记录即无权限
     */
    private void checkKbAccess(String kbId) {
        if (kbId == null || kbId.isBlank()) return;
        LoginUser user = SecurityUtil.getCurrentUser();
        // 超管或平台级 API Token 直接放行
        if (user.hasPermission("*") || user.getUserId().startsWith("api-token:")) return;
        String cacheKey = "kb:acl:" + kbId + ":" + user.getUserId();
        String roleStr = redisTemplate.opsForValue().get(cacheKey);
        if (roleStr == null) {
            log.warn("[Retrieval] Access denied: user={} has no permission on kb={}", user.getUserId(), kbId);
            throw BusinessException.forbidden("无知识库访问权限");
        }
    }

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

        // 知识库访问权限校验（body 传 kbId，KbAuthAspect 无法拦截）
        checkKbAccess(kbId);

        log.info("[Retrieval] ====== Search start kb={}, query='{}'", kbId, originalQuery);

        if (originalQuery == null || originalQuery.isBlank()) {
            log.info("[Retrieval] Empty query, return empty");
            return Collections.emptyList();
        }

        // ---- Phase 7: 加载知识库配置并合并 ----
        RetrievalRequest.RetrievalConfig config = loadAndMergeConfig(kbId, req.getConfig());
        log.info("[Retrieval] Effective config: mode={}, topK={}, threshold={}, keywordMatch={}, "
                        + "multiRetrieval={}, rerank={}, fusionStrategy={}, contextStrategy={}",
                config.getMode(), config.getTopK(), config.getSimilarityThreshold(),
                config.getEnableKeywordMatch(), config.getEnableMultiRetrieval(),
                config.getEnableRerank(), config.getFusionStrategy(), config.getContextAssemblyStrategy());

        // ---- Phase 3: 查询预处理 ----
        String query = preprocessQuery(kbId, originalQuery, config);
        if (!query.equals(originalQuery)) {
            log.info("[Retrieval] Query after preprocess: '{}'", query);
        }

        // ---- Phase 2 + Phase 5: 核心检索 ----
        // Multi-Query：LLM 改写 N 个变体查询分别召回后 RRF 融合（弥补分片切断导致的单一查询漏召）
        List<SearchResultItem> results = Boolean.TRUE.equals(config.getEnableMultiQuery())
                ? multiQuerySearch(kbId, query, config)
                : executeSearch(kbId, query, config);

        // ---- 图谱通道（方案 A）：固定参与一路图谱召回，与主检索结果 RRF 融合 ----
        // 不再把实体名/关系标签拼入 query 再向量化（避免稀释 embedding），
        // 而是作为独立召回通道（对标 LightRAG kg_query）
        // 降级显式化（ADR-0002）：整路失败（Neo4j 不可达等）由 graphChannelRecall 返回 null 标记，
        // 与「正常返回但零命中」区分，状态写入检索日志可查询
        boolean graphChannelDegraded = false;
        if (Boolean.TRUE.equals(config.getEnableGraphExpand())) {
            int graphCount = config.getGraphRecallCount() != null ? config.getGraphRecallCount() : 5;
            List<SearchResultItem> graphHits = safeSearch(() -> graphChannelRecall(kbId, query, config, graphCount));
            if (graphHits == null) {
                graphChannelDegraded = true;
                graphHits = Collections.emptyList();
                log.warn("[Retrieval] Graph channel DEGRADED for kb={}, query='{}' — 详情见 [GraphChannel] 告警日志",
                        kbId, query);
            }
            if (!graphHits.isEmpty()) {
                log.info("[Retrieval] Graph channel: {} graph results fused via RRF for kb={}, query='{}'",
                        graphHits.size(), kbId, query);
                results = rrfFusionMulti(List.of(results, graphHits), config.getTopK());
            } else {
                log.info("[Retrieval] Graph channel: no graph hits for kb={}, query='{}'", kbId, query);
            }
        }

        // ---- 条款/标题线索召回：政策文件条款交叉引用（"详见第三条"）定向召回 ----
        // 仅在 query 含条款号或命中标题时触发；命中结果与主检索 RRF 融合，补救条款关联的召回
        if (Boolean.TRUE.equals(config.getEnableClauseRecall())) {
            int clauseCount = config.getClauseRecallCount() != null ? config.getClauseRecallCount() : 3;
            List<SearchResultItem> clauseHits = safeSearch(() -> clauseRecall(kbId, query, config, clauseCount));
            if (!clauseHits.isEmpty()) {
                log.info("[Retrieval] Clause recall: {} clause/title results fused via RRF for kb={}, query='{}'",
                        clauseHits.size(), kbId, query);
                results = rrfFusionMulti(List.of(results, clauseHits), config.getTopK() * 2);
            }
        }

        // ---- Phase 4: 后处理（重排序 / MMR）----
        // 组装后重排模式（parent_chunk / window / auto）下，Rerank 延后到上下文组装后执行，
        // 避免被切断的块因单块向量得分低而进不了 rerank 视野
        results = postProcess(results, query, config, kbId);

        // 按内容去重：同一 content 只保留相似度最高的一条
        results = deduplicateResults(results);

        // ---- Phase 6: 上下文组装 ----
        results = assembleContext(results, config);

        // ---- P1: 扩展上下文后再重排（rerank 输入 = 组装后的完整父块/窗口上下文）----
        if (rerankAfterAssembly(config)) {
            int before = results.size();
            results = rerankResults(results, query, config, kbId);
            log.info("[Retrieval] Rerank after assembly: {} -> {} items (strategy={})",
                    before, results.size(), config.getContextAssemblyStrategy());
        }

        // ---- 关键词匹配：命中问答对时 QA 结果优先返回 ----
        if (Boolean.TRUE.equals(config.getEnableKeywordMatch())) {
            int qaCount = config.getQaRecallCount() != null ? config.getQaRecallCount() : 5;
            List<SearchResultItem> qaHits = safeSearch(() -> qaChannelRecall(kbId, query, qaCount));
            if (!qaHits.isEmpty()) {
                // 去掉与普通结果重复的 QA 项（按 fileId+chunkIndex 去重，QA 项 chunkIndex 恒为 0）
                Set<String> existingKeys = results.stream()
                        .map(r -> r.getFileId() + "_" + r.getChunkIndex())
                        .collect(Collectors.toSet());
                List<SearchResultItem> combined = new ArrayList<>();
                for (SearchResultItem qa : qaHits) {
                    if (existingKeys.add(qa.getFileId() + "_" + qa.getChunkIndex())) {
                        combined.add(qa);
                    }
                }
                combined.addAll(results);
                results = combined;
                log.info("[Retrieval] QA keyword match: {} QA results prepended for kb={}, query='{}'",
                        qaHits.size(), kbId, query);
            } else {
                log.info("[Retrieval] QA keyword match: no QA matched for kb={}, query='{}'", kbId, query);
            }
        }

        // ---- 统一设置结果序号 + 填充来源文件名 ----
        for (int i = 0; i < results.size(); i++) {
            results.get(i).setIndex(i);
        }
        resolveFileNames(results);

        log.info("[Retrieval] ====== Search done kb={}, query='{}', results={}, elapsed={}ms ======",
                kbId, originalQuery, results.size(), System.currentTimeMillis() - startMs);

        // ---- 日志记录 ----
        recordLog(kbId, originalQuery, config, results, startMs, graphChannelDegraded);

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
                        .in(KbChunk::getFileId, activeFileIds)
                        .ne(KbChunk::getChunkType, "parent"));
    }

    // ========================================================================
    //  Phase 7: 知识库检索配置加载与合并
    // ========================================================================

    private RetrievalRequest.RetrievalConfig loadAndMergeConfig(String kbId,
                                                                  RetrievalRequest.RetrievalConfig requestConfig) {
        RetrievalRequest.RetrievalConfig merged = new RetrievalRequest.RetrievalConfig();

        // 默认值（先设置系统级默认值，KB 配置和请求参数可覆盖）
        applySystemDefaults(merged);

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

    /**
     * 从系统配置读取默认检索参数，作为基础默认值。
     * 优先级最低（会被 KB 配置和请求参数覆盖）。
     */
    private void applySystemDefaults(RetrievalRequest.RetrievalConfig config) {
        try {
            SysConfig c = configService.getConfig("general_search_top_k");
            if (c != null && c.getConfigValue() != null) {
                config.setTopK(Integer.parseInt(c.getConfigValue().trim()));
            } else {
                config.setTopK(10);
            }
        } catch (Exception e) {
            config.setTopK(10);
        }

        try {
            SysConfig c = configService.getConfig("general_retrieval_mode");
            if (c != null && c.getConfigValue() != null) {
                config.setMode(c.getConfigValue().trim());
            } else {
                config.setMode("hybrid");
            }
        } catch (Exception e) {
            config.setMode("hybrid");
        }

        try {
            SysConfig c = configService.getConfig("general_enable_rerank");
            if (c != null && c.getConfigValue() != null) {
                config.setEnableRerank("true".equals(c.getConfigValue().trim()));
            } else {
                config.setEnableRerank(false);
            }
        } catch (Exception e) {
            config.setEnableRerank(false);
        }

        config.setSimilarityThreshold(0.2);
        // 图谱通道默认开启（方案 A，对标 LightRAG 一等检索通道）：与 vector/fulltext 路 RRF 融合
        config.setEnableGraphExpand(true);
        config.setGraphExpandDepth(1);
        config.setGraphMaxEntities(10);
        config.setGraphRecallCount(5);
        // 关键词匹配默认开启：命中问答对时优先返回（请求/KB 配置可显式关闭）
        config.setEnableKeywordMatch(true);
        // 多查询改写默认关闭（每查询多 N 次 LLM 改写 + N-1 路召回，延迟更高，需显式开启）
        config.setEnableMultiQuery(false);
        config.setMultiQueryCount(3);
        // 上下文组装默认 auto：自动降级链（parent_chunk → window → concat），
        // 命中小片时优先带回父块/窗口完整上下文，弥补分片切断导致的回答不完整
        config.setContextAssemblyStrategy("auto");
        // 条款/标题线索召回默认开启：仅当 query 含条款号（政策文件交叉引用）或命中标题时触发
        config.setEnableClauseRecall(true);
        config.setClauseWindowSize(3);
        config.setClauseRecallCount(3);
    }

    /** 将 source 中的非 null 字段复制到 target */
    private void mergeConfig(RetrievalRequest.RetrievalConfig target,
                              RetrievalRequest.RetrievalConfig source) {
        if (source.getMode() != null) target.setMode(source.getMode());
        if (source.getTopK() != null && source.getTopK() > 0) target.setTopK(source.getTopK());
        if (source.getSimilarityThreshold() != null) target.setSimilarityThreshold(source.getSimilarityThreshold());
        if (source.getEnableGraphExpand() != null) target.setEnableGraphExpand(source.getEnableGraphExpand());
        if (source.getGraphExpandDepth() > 0) target.setGraphExpandDepth(source.getGraphExpandDepth());
        if (source.getGraphMaxEntities() > 0) target.setGraphMaxEntities(source.getGraphMaxEntities());
        if (source.getNerModel() != null) target.setNerModel(source.getNerModel());
        if (source.getEnableRerank() != null) target.setEnableRerank(source.getEnableRerank());
        if (source.getRerankModel() != null) target.setRerankModel(source.getRerankModel());

        // 预处理
        if (source.getEnableAutoCorrection() != null) target.setEnableAutoCorrection(source.getEnableAutoCorrection());
        if (source.getEnableQueryRewrite() != null) target.setEnableQueryRewrite(source.getEnableQueryRewrite());
        if (source.getEnableSynonymExpansion() != null) target.setEnableSynonymExpansion(source.getEnableSynonymExpansion());
        if (source.getEnableKeywordMatch() != null) target.setEnableKeywordMatch(source.getEnableKeywordMatch());

        // 多查询改写
        if (source.getEnableMultiQuery() != null) target.setEnableMultiQuery(source.getEnableMultiQuery());
        if (source.getMultiQueryCount() != null) target.setMultiQueryCount(source.getMultiQueryCount());
        if (source.getMultiQueryModel() != null) target.setMultiQueryModel(source.getMultiQueryModel());

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

        // 条款/标题线索召回
        if (source.getEnableClauseRecall() != null) target.setEnableClauseRecall(source.getEnableClauseRecall());
        if (source.getClauseWindowSize() != null) target.setClauseWindowSize(source.getClauseWindowSize());
        if (source.getClauseRecallCount() != null) target.setClauseRecallCount(source.getClauseRecallCount());
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

        // 注意：图谱扩展不在预处理中拼词（方案 A）——实体名/关系标签拼入 query 会稀释 embedding，
        // 图谱召回由 search() 主流程的 graphChannelRecall 作为独立通道参与 RRF 融合

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

    /**
     * Multi-Query 检索：LLM 改写 N 个语义互补的变体查询（含原查询），
     * 每个变体独立走完整召回通道，最后按 chunk RRF 融合。
     *
     * <p>作用：分片边界切断会让被切掉的那一半语义"换一种表述"后才可能命中，
     * 多变体召回 + RRF 融合是最直接的兜底手段（对标 LangChain MultiQueryRetriever）。</p>
     *
     * <p>降级：改写失败 / 只有一个变体时退化为单查询 executeSearch，不改变现有行为。</p>
     */
    private List<SearchResultItem> multiQuerySearch(String kbId, String query,
                                                     RetrievalRequest.RetrievalConfig config) {
        int target = config.getMultiQueryCount() != null ? config.getMultiQueryCount() : 3;
        List<String> queries;
        try {
            queries = queryEnhanceService.expandQueries(query, target, config.getMultiQueryModel(), kbId);
        } catch (Exception e) {
            log.warn("[MultiQuery] Expand failed for kb={}, fallback to single query: {}", kbId, e.getMessage());
            queries = List.of(query);
        }
        if (queries.size() < 2) {
            return executeSearch(kbId, query, config);
        }

        // 每个变体独立召回（safeSearch：单路失败不拖垮整体）
        List<List<SearchResultItem>> channels = new ArrayList<>();
        for (String q : queries) {
            channels.add(safeSearch(() -> executeSearch(kbId, q, config)));
        }
        // 融合数上浮（topK*3 且至少 50），供后续 graph 融合 / rerank 裁剪
        int fusedTopK = Math.max(config.getTopK() * 3, 50);
        List<SearchResultItem> fused = rrfFusionMulti(channels, fusedTopK);

        log.info("[MultiQuery] kb={}, query='{}', variants={}, fused={}",
                kbId, query, queries.size(), fused.size());
        return fused;
    }

    /**
     * 是否采用"组装后重排"：仅在启用 Rerank 且上下文策略为 parent_chunk / window / auto 时生效。
     *
     * <p>这几类策略会把命中的子分片放大为父块/窗口上下文——被切分切断的部分此时已回到
     * content 中，rerank 输入是组装后的完整上下文，才能把"整体语义"排到前面。
     * auto 策略可能内部放大（L1/L2），因此同样纳入。</p>
     */
    private boolean rerankAfterAssembly(RetrievalRequest.RetrievalConfig config) {
        if (!Boolean.TRUE.equals(config.getEnableRerank())
                || config.getRerankModel() == null || config.getRerankModel().isBlank()) {
            return false;
        }
        String strategy = config.getContextAssemblyStrategy();
        return "parent_chunk".equals(strategy)
                || "window".equals(strategy)
                || "auto".equals(strategy);
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
        // BM25 候选数量：优先取 bm25RecallCount（前端「BM25 召回数量」），至少 topK 条
        int limit = Math.max(topK, config.getBm25RecallCount() != null ? config.getBm25RecallCount() : 0);
        try {
            // 尝试用 FULLTEXT 索引搜索
            List<KbChunk> chunks = chunkMapper.fulltextSearch(kbId, query, limit);
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

        // 3. 加权融合（使用前端可调的 vectorWeight / bm25Weight）
        List<SearchResultItem> fused = weightedFusion(List.of(vectorResults, fulltextResults), config, topK);

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
        int qCount = config.getQaRecallCount() != null ? config.getQaRecallCount() : 0;
        String strategy = config.getFusionStrategy() != null ? config.getFusionStrategy() : "rrf";

        // 各路并行召回（图谱通道统一在 search() 主流程参与融合，不在此处重复）
        List<List<SearchResultItem>> channels = new ArrayList<>();

        // 向量通道
        RetrievalRequest.RetrievalConfig vCfg = copyWithMode(config, "vector", vCount);
        channels.add(safeSearch(() -> vectorSearch(kbId, query, vCfg)));

        // 全文通道
        RetrievalRequest.RetrievalConfig fCfg = copyWithMode(config, "fulltext", fCount);
        channels.add(safeSearch(() -> fulltextSearch(kbId, query, fCfg)));

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
     * 图谱子图召回：实体匹配（文本 CONTAINS + 向量语义双路）→ 邻居实体 LIKE 召回关联 chunk
     */
    private List<SearchResultItem> graphChannelRecall(String kbId, String query,
                                                       RetrievalRequest.RetrievalConfig config, int count) {
        try {
            // 1. 文本实体匹配（NER/降级分词 + CONTAINS）
            Map<String, Object> graphResult = queryEnhanceService.expandGraph(
                    kbId, query, config.getGraphExpandDepth(),
                    config.getGraphMaxEntities(), config.getNerModel());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entities = (List<Map<String, Object>>)
                    graphResult.getOrDefault("entities", List.of());

            // 实体名集合（文本 + 向量去重合并）
            Set<String> entityNames = new LinkedHashSet<>();
            for (Map<String, Object> e : entities) {
                String name = (String) e.get("name");
                // 过滤过短实体名（如 n-gram 切出的 "流程/包含/什么" 等 2 字泛词，LIKE 命中全库噪音大）
                if (name != null && name.length() >= 3) entityNames.add(name);
            }

            // 2. 向量实体匹配（语义增强，对标 LightRAG entities_vdb；失败降级文本）
            EmbeddingModelConfig embCfg = getEmbeddingModelConfig(kbId);
            if (embCfg != null && embCfg.model() != null) {
                try {
                    List<Float> queryVector = embeddingService.embed(
                            embCfg.model(), List.of(query), embCfg.apiUrl(), embCfg.apiKey()).get(0);
                    List<Map<String, Object>> vecEntities = graphStore.searchEntitiesByVector(kbId, queryVector, 5);
                    for (Map<String, Object> e : vecEntities) {
                        String name = (String) e.get("name");
                        if (name != null && !name.isBlank()) entityNames.add(name);
                    }
                    if (!vecEntities.isEmpty()) {
                        log.info("[GraphChannel] Vector entity match: {} entities for kb={}, query='{}'",
                                vecEntities.size(), kbId, query);
                    }
                } catch (Exception e) {
                    log.warn("[GraphChannel] Vector entity match failed for kb={}, fallback to text: {}",
                            kbId, e.getMessage());
                }
            }

            if (entityNames.isEmpty()) return Collections.emptyList();

            // 3. 用实体名去搜索 chunk
            List<String> nameList = new ArrayList<>(entityNames);
            List<SearchResultItem> results = new ArrayList<>();
            for (String name : nameList) {
                if (results.size() >= count) break;
                List<KbChunk> chunks = chunkMapper.selectList(
                        new LambdaQueryWrapper<KbChunk>()
                                .eq(KbChunk::getKbId, kbId)
                                .ne(KbChunk::getChunkType, "parent")
                                .like(KbChunk::getContent, name)
                                .last("LIMIT " + Math.max(count / Math.max(nameList.size(), 1), 1)));
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
            // 整路失败（Neo4j 不可达等）：返回 null 向调用方标记图谱通道降级（区别于零命中），
            // 状态会写入检索日志 graphChannel=degraded
            log.warn("[GraphChannel] DEGRADED (channel failure) for kbId={}", kbId, e);
            return null;
        }
    }

    /**
     * QA 对召回：从 kb_qa_pair 表匹配问题
     *
     * <p>分词模糊匹配：query 切成 token（英文/数字按词、中文按连续汉字块），
     * 任一 token 命中即为候选，再按命中 token 数降序取前 count。
     * 例如 "DeepSeek V4参数" 可命中 "DeepSeek V4 Pro 参数"（字面 LIKE 做不到）。</p>
     */
    private List<SearchResultItem> qaChannelRecall(String kbId, String query, int count) {
        try {
            List<String> tokens = tokenizeQuery(query);
            if (tokens.isEmpty()) return Collections.emptyList();

            List<KbQaPair> qaPairs = qaPairMapper.selectList(
                    new LambdaQueryWrapper<KbQaPair>()
                            .eq(KbQaPair::getKbId, kbId)
                            .and(w -> {
                                for (int i = 0; i < tokens.size(); i++) {
                                    if (i > 0) w.or();
                                    w.like(KbQaPair::getQuestion, tokens.get(i));
                                }
                            })
                            .last("LIMIT " + Math.max(count * 3, 30)));
            if (qaPairs.isEmpty()) return Collections.emptyList();

            // 按命中 token 数降序（命中越多越相关）；先拷贝为可变列表（兼容不可变 List 入参）
            qaPairs = new ArrayList<>(qaPairs);
            qaPairs.sort(Comparator.comparingInt(
                    (KbQaPair q) -> tokenHitCount(q.getQuestion(), tokens)).reversed());

            List<SearchResultItem> results = new ArrayList<>();
            int size = Math.min(count, qaPairs.size());
            for (int i = 0; i < size; i++) {
                KbQaPair qa = qaPairs.get(i);
                SearchResultItem item = new SearchResultItem();
                item.setIndex(i);
                item.setContent("Q: " + qa.getQuestion() + "\nA: " + qa.getAnswer());
                item.setSource("qa");
                item.setChannel("qa");
                item.setFileId(qa.getFileId());
                item.setSimilarity(0.8 - (double) i / size);
                item.setDistance(0.2 + (double) i / size);
                results.add(item);
            }
            return results;
        } catch (Exception e) {
            log.warn("[QAChannel] Failed for kbId={}", kbId, e);
            return Collections.emptyList();
        }
    }

    /** 问答对关键词切分：英文/数字按词（≥2 字符）、中文按连续汉字块 */
    private static final Pattern QA_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9]{2,}|[\\u4e00-\\u9fa5]+");

    private List<String> tokenizeQuery(String query) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        List<String> tokens = new ArrayList<>();
        Matcher m = QA_TOKEN_PATTERN.matcher(query);
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    private int tokenHitCount(String question, List<String> tokens) {
        if (question == null) return 0;
        int hits = 0;
        for (String t : tokens) {
            if (question.contains(t)) hits++;
        }
        return hits;
    }

    // ========================================================================
    //  条款/标题线索召回（政策文件条款交叉引用场景）
    // ========================================================================

    /**
     * 条款提及正则：匹配"第三条/第3条/第二十一条/第二章/第5款"等
     * 支持中文数字与阿拉伯数字（条款常用二者混排）
     */
    private static final Pattern CLAUSE_PATTERN =
            Pattern.compile("第\\s*([0-9]+|[一二三四五六七八九十百千万]+)\\s*([条款章节款项])");

    /**
     * 条款/标题线索召回：补救"政策条款交叉引用"在纯向量召回下的漏召。
     *
     * <p>两条线索：</p>
     * <ol>
     *   <li><b>条款号联动</b>：query 含"第X条"等提及 → 以条款号为锚，
     *       用条款号 LIKE 召回所在 chunk，并向其前后取 clauseWindowSize 个相邻 chunk（条款上下文在同一章节内邻近）</li>
     *   <li><b>标题线索</b>：query 的 token 命中 chunk.title / heading_path（如"电价""补贴"章节标题），
     *       政策文件查询常以章节标题措辞提问，向量反而容易漏</li>
     * </ol>
     *
     * <p>返回结果参与 search() 主流程 RRF 融合（不改变现有主召回）。</p>
     */
    private List<SearchResultItem> clauseRecall(String kbId, String query,
                                                 RetrievalRequest.RetrievalConfig config, int count) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        int window = config.getClauseWindowSize() != null ? config.getClauseWindowSize() : 3;

        // 1. 提取条款提及（"第X条"等）
        List<String> clauseMentions = new ArrayList<>();
        Matcher m = CLAUSE_PATTERN.matcher(query);
        while (m.find()) {
            clauseMentions.add(m.group().replace(" ", "")); // 去内部空格，"第 3 条"→"第3条"
        }

        Set<String> seenKeys = new HashSet<>();
        List<SearchResultItem> clauseHits = new ArrayList<>();

        // 2. 条款号联动召回
        for (String mention : clauseMentions) {
            try {
                // 条款号 LIKE 匹配：content 首段出现"第X条"的 chunk（条款锚点）
                List<KbChunk> anchors = chunkMapper.selectList(
                        new LambdaQueryWrapper<KbChunk>()
                                .eq(KbChunk::getKbId, kbId)
                                .ne(KbChunk::getChunkType, "parent")
                                .like(KbChunk::getContent, mention)
                                .last("LIMIT " + Math.max(count * 3, 30)));
                for (KbChunk anchor : anchors) {
                    if (anchor.getFileId() != null && isFileDeleted(kbId, anchor.getFileId())) continue;
                    // 锚点 chunk 本身
                    addClauseHit(clauseHits, seenKeys, anchor, 0.9, "clause");
                    // 向锚点前后取 clauseWindowSize 个相邻 chunk（条款上下文在同一章节内邻近）
                    List<KbChunk> fileChunks = chunkMapper.selectByFileId(anchor.getFileId());
                    int pos = -1;
                    for (int i = 0; i < fileChunks.size(); i++) {
                        if (fileChunks.get(i).getChunkIndex() != null
                                && anchor.getChunkIndex() != null
                                && fileChunks.get(i).getChunkIndex().intValue() == anchor.getChunkIndex()) {
                            pos = i;
                            break;
                        }
                    }
                    if (pos >= 0) {
                        int start = Math.max(0, pos - window);
                        int end = Math.min(fileChunks.size(), pos + window + 1);
                        for (int i = start; i < end; i++) {
                            if (i == pos) continue; // 锚点已加
                            KbChunk neighbor = fileChunks.get(i);
                            if (neighbor.getFileId() != null && isFileDeleted(kbId, neighbor.getFileId())) continue;
                            double decay = 0.9 - 0.2 * Math.abs(i - pos);
                            addClauseHit(clauseHits, seenKeys, neighbor, Math.max(decay, 0.3), "clause_neighbor");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[ClauseRecall] Anchor recall failed for mention='{}': {}", mention, e.getMessage());
            }
        }

        // 3. 标题线索召回：无条款号且未命中条款锚点时，退化为按标题/headingPath 召回
        //    （条款号已产生足够结果的情形则跳过，避免重复堆叠）
        if (clauseHits.isEmpty()) {
            List<String> tokens = tokenizeQuery(query);
            // 无可用 token（长度 < 2）时跳过，避免空条件包装器
            boolean hasToken = false;
            for (String t : tokens) {
                if (t.length() >= 2) {
                    hasToken = true;
                    break;
                }
            }
            List<String> usableTokens = hasToken ? tokens : Collections.emptyList();
            if (!usableTokens.isEmpty()) {
                try {
                    List<KbChunk> titleHits = chunkMapper.selectList(
                            new LambdaQueryWrapper<KbChunk>()
                                    .eq(KbChunk::getKbId, kbId)
                                    .ne(KbChunk::getChunkType, "parent")
                                    .and(w -> {
                                        boolean first = true;
                                        for (String t : usableTokens) {
                                            if (t.length() < 2) continue;
                                            if (!first) w.or();
                                            w.like(KbChunk::getTitle, t).or().like(KbChunk::getHeadingPath, t);
                                            first = false;
                                        }
                                    })
                                    .last("LIMIT " + Math.max(count * 3, 30)));
                    for (int i = 0; i < titleHits.size(); i++) {
                        KbChunk c = titleHits.get(i);
                        // 仅保留标题/路径命中、但 content 未命中 query token 的 chunk（与主召回互补）
                        if (c.getFileId() != null && isFileDeleted(kbId, c.getFileId())) continue;
                        if (containsAny(c.getContent(), usableTokens)) continue;
                        double score = 0.85 - 0.1 * (i / 5); // 降序粗排
                        addClauseHit(clauseHits, seenKeys, c, score, "title");
                        if (clauseHits.size() >= count) break;
                    }
                } catch (Exception e) {
                    log.warn("[ClauseRecall] Title recall failed for kbId={}: {}", kbId, e.getMessage());
                }
            }
        }

        return clauseHits;
    }

    /** 检查文本是否包含任一 token（内容与标题的互补判断） */
    private boolean containsAny(String text, List<String> tokens) {
        if (text == null) return false;
        for (String t : tokens) {
            if (t.length() >= 2 && text.contains(t)) return true;
        }
        return false;
    }

    /** 追加条款线索命中项（按 fileId+chunkIndex 去重，避免与主召回重复） */
    private void addClauseHit(List<SearchResultItem> hits, Set<String> seenKeys,
                              KbChunk chunk, double similarity, String channel) {
        String key = (chunk.getFileId() == null ? "" : chunk.getFileId())
                + "_" + (chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex());
        if (!seenKeys.add(key)) return;
        hits.add(buildResultItem(chunk, similarity, 1.0 - similarity, "mysql", channel));
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
                // 只按 RRF 分排序，保留各路的原始 similarity/distance（RRF 排名分不是相似度语义）
                .map(e -> itemMap.get(e.getKey()))
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
                // 只按加权分排序，保留各路原始 similarity（加权排名分不是相似度语义）
                .map(e -> itemMap.get(e.getKey()))
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

        // Rerank 模型重排：组装后重排模式（parent_chunk / window）下跳过前置重排，
        // 由 search() 在上下文组装完成后再对扩展后的 content 执行
        if (!rerankAfterAssembly(config)
                && Boolean.TRUE.equals(config.getEnableRerank()) && config.getRerankModel() != null) {
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
            case "parent_chunk" -> assembleParentChunk(results);
            case "parent_document" -> assembleParentDocument(results);
            case "window" -> assembleWindow(results, config);
            case "auto" -> assembleAuto(results, config);
            default -> results;
        };
    }

    /**
     * 上下文自动降级链（auto 策略，P0 默认）：
     * 对每个命中的 chunk 依次尝试"召回小片 → 返回大片"，逐级降级：
     * <ol>
     *   <li><b>L1 parent_chunk</b>：命中子分片（parentId 非空）且父块存在且在 token 预算内 → 返回父块全文；</li>
     *   <li><b>L2 window</b>：无父块或父块超预算 → 取命中 chunk 前后 N 个邻居；</li>
     *   <li><b>L3 concat</b>：无法定位文件/邻居 → 降级返回自身。</li>
     * </ol>
     *
     * <p>目的：政策文件切片切断了条款上下文，默认路径（App 检索）也要能自动带回
     * 被切断的"另一半"，而不需要每 KB 显式配置 parent_chunk/window。</p>
     */
    private List<SearchResultItem> assembleAuto(List<SearchResultItem> results,
                                                 RetrievalRequest.RetrievalConfig config) {
        int tokenBudget = config.getMaxContextTokens() != null ? config.getMaxContextTokens() : 0;

        List<SearchResultItem> assembled = new ArrayList<>();
        // 无法走 L1 的命中块统一进入 L2（窗口）一次性组装，复用 assembleWindow 的全局去重与 token 预算
        List<SearchResultItem> windowCandidates = new ArrayList<>();
        // 按 (fileId, chunkIndex) 去重：同一命中块只处理一次（多路召回可能重复）
        Set<String> seen = new HashSet<>();
        for (SearchResultItem hit : results) {
            String dedupKey = (hit.getFileId() == null ? "" : hit.getFileId())
                    + "_" + hit.getChunkIndex();
            if (!seen.add(dedupKey)) {
                continue;
            }

            // L1：优先父块放大（parent_chunk）
            if (hit.getParentId() != null && !hit.getParentId().isBlank()) {
                KbChunk parent = chunkMapper.selectById(hit.getParentId());
                if (parent != null && parent.getContent() != null && !parent.getContent().isBlank()
                        && (tokenBudget <= 0 || estimateTokens(parent.getContent()) <= tokenBudget)) {
                    assembled.add(buildParentItem(hit, parent));
                    log.debug("[ContextAuto] L1 parent_chunk: fileId={} chunkIndex={} -> parent={}",
                            hit.getFileId(), hit.getChunkIndex(), hit.getParentId());
                    continue;
                }
                if (parent == null) {
                    log.warn("[ContextAuto] Parent not found for parentId={}, fallback L2 window", hit.getParentId());
                } else {
                    log.warn("[ContextAuto] Parent too large for token budget (budget={} tokens), fallback L2 window",
                            tokenBudget);
                }
            }

            // L2/L3：无父块放大时进入窗口候选；无法定位文件的最后降级 concat
            if (hit.getFileId() == null || hit.getFileId().isBlank()) {
                log.debug("[ContextAuto] L3 concat fallback: no file location for chunkIndex={}", hit.getChunkIndex());
                assembled.add(hit);
            } else {
                windowCandidates.add(hit);
            }
        }

        // L2：窗口候选一次性组装（assembleWindow 内部按距离由近及远、全局去重、token 预算裁剪）
        if (!windowCandidates.isEmpty()) {
            List<SearchResultItem> windowed = assembleWindow(windowCandidates, config);
            log.debug("[ContextAuto] L2 window: {} hits -> {} windowed items", windowCandidates.size(), windowed.size());
            assembled.addAll(windowed);
        }

        return assembled;
    }

    /** 构建父分片上下文条目（与 assembleParentChunk 的父块替换逻辑一致） */
    private SearchResultItem buildParentItem(SearchResultItem hit, KbChunk parent) {
        SearchResultItem item = new SearchResultItem();
        item.setIndex(hit.getIndex());
        item.setContent(parent.getContent());
        item.setFileId(hit.getFileId());
        item.setChunkIndex(hit.getChunkIndex());   // 保留命中的子分片位置
        item.setParentId(hit.getParentId());
        item.setSimilarity(hit.getSimilarity());
        item.setDistance(hit.getDistance());
        item.setSource(hit.getSource());
        item.setChannel(hit.getChannel());
        item.setChunkType("parent");
        String content = parent.getContent();
        if (content.length() > 200) {
            item.setPreviewSnippet(content.substring(0, 200) + "...");
        } else {
            item.setPreviewSnippet(content);
        }
        return item;
    }

    /**
     * 父分片模式（parent_chunk）：命中子分片（parentId 非空）时返回其所属父分片作为上下文；
     * 命中单层分片（无父分片）时退化为返回自身。父分片不参与召回（未向量化、全文检索排除），
     * 仅作为命中后的上下文放大。
     */
    private List<SearchResultItem> assembleParentChunk(List<SearchResultItem> results) {
        List<SearchResultItem> assembled = new ArrayList<>();
        for (SearchResultItem hit : results) {
            if (hit.getParentId() == null || hit.getParentId().isBlank()) {
                assembled.add(hit);
                continue;
            }
            KbChunk parent = chunkMapper.selectById(hit.getParentId());
            if (parent == null || parent.getContent() == null || parent.getContent().isBlank()) {
                assembled.add(hit);
                continue;
            }
            SearchResultItem item = new SearchResultItem();
            item.setIndex(hit.getIndex());
            item.setContent(parent.getContent());
            item.setFileId(hit.getFileId());
            item.setChunkIndex(hit.getChunkIndex());   // 保留命中的子分片位置
            item.setParentId(hit.getParentId());
            item.setSimilarity(hit.getSimilarity());
            item.setDistance(hit.getDistance());
            item.setSource(hit.getSource());
            item.setChannel(hit.getChannel());
            // 父分片内容预览
            String content = parent.getContent();
            if (content.length() > 200) {
                item.setPreviewSnippet(content.substring(0, 200) + "...");
            } else {
                item.setPreviewSnippet(content);
            }
            assembled.add(item);
        }
        return assembled;
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
     * 窗口模式：对命中的 chunk，获取其前后 N 个 chunk。
     *
     * <p>token 预算（maxContextTokens）：配置时生效。命中块始终保留（保主召回），
     * 邻居块按离命中块的距离由近及远累积，超出预算的邻居丢弃——被切分切断的"另一半"
     * 在最近邻范围内优先进入上下文，避免无限扩展把上下文撑爆。</p>
     */
    private List<SearchResultItem> assembleWindow(List<SearchResultItem> results,
                                                    RetrievalRequest.RetrievalConfig config) {
        int windowSize = config.getContextWindowSize() != null ? config.getContextWindowSize() : 2;
        String order = config.getContextOrder() != null ? config.getContextOrder() : "relevance";
        int tokenBudget = config.getMaxContextTokens() != null ? config.getMaxContextTokens() : 0;

        // 对命中的 chunk 按 (fileId, chunkIndex) 找到其邻居
        Set<String> seenChunks = new HashSet<>();
        List<SearchResultItem> windowed = new ArrayList<>();
        int consumedTokens = 0;

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

            // 前后 windowSize 个 chunk，按离命中块的距离由近及远加入：
            // dist=0 命中块（始终保留，不受预算约束）；dist=1..windowSize 邻居（受预算约束，优先左侧）
            int start = Math.max(0, pos - windowSize);
            int end = Math.min(fileChunks.size(), pos + windowSize + 1);
            for (int dist = 0; dist <= windowSize; dist++) {
                if (dist == 0) {
                    KbChunk hitChunk = fileChunks.get(pos);
                    if (seenChunks.add(hitChunk.getId())) {
                        consumedTokens += estimateTokens(hitChunk.getContent());
                        windowed.add(buildResultItem(hitChunk, hit.getSimilarity(),
                                0.0, hit.getSource(), hit.getChannel()));
                    }
                    continue;
                }
                // 邻居超预算：跳过该邻居并停止本层扩展（预算全局累积，后续命中块仍保留）
                boolean budgetHit = false;
                for (int offset : new int[]{-dist, dist}) {
                    int i = pos + offset;
                    if (i < start || i >= end) continue;
                    KbChunk chunk = fileChunks.get(i);
                    if (!seenChunks.add(chunk.getId())) continue;
                    int estTokens = estimateTokens(chunk.getContent());
                    if (tokenBudget > 0 && consumedTokens + estTokens > tokenBudget) {
                        budgetHit = true;
                        break;
                    }
                    consumedTokens += estTokens;
                    windowed.add(buildResultItem(chunk,
                            hit.getSimilarity() * (1 - 0.1 * dist),
                            0.0, hit.getSource(), hit.getChannel()));
                }
                if (budgetHit) break;
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
        item.setParentId(chunk.getParentId());
        item.setSimilarity(similarity);
        item.setDistance(distance);
        item.setSource(source);
        item.setChannel(channel);
        item.setChunkType(chunk.getChunkType());
        item.setImageKeys(parseImageKeys(chunk.getImageKeys()));
        // 截取前 200 字作为预览
        String content = chunk.getContent();
        if (content != null && content.length() > 200) {
            item.setPreviewSnippet(content.substring(0, 200) + "...");
        } else {
            item.setPreviewSnippet(content);
        }
        return item;
    }

    /** 解析 chunk.imageKeys JSON 数组字符串为 List */
    private List<String> parseImageKeys(String imageKeysJson) {
        if (StrUtil.isBlank(imageKeysJson)) return null;
        try {
            return JSONUtil.toList(imageKeysJson, String.class);
        } catch (Exception e) {
            log.warn("[parseImageKeys] Failed to parse imageKeys: {}", imageKeysJson);
            return null;
        }
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

    /** 批量填充结果条目的来源文件名（按 fileId 批量查 kb_file） */
    private void resolveFileNames(List<SearchResultItem> results) {
        Set<String> fileIds = results.stream()
                .map(SearchResultItem::getFileId)
                .filter(Objects::nonNull)
                .filter(fid -> !fid.isBlank())
                .collect(Collectors.toSet());
        if (fileIds.isEmpty()) return;
        try {
            Map<String, String> idToName = new HashMap<>();
            for (KbFile file : fileMapper.selectBatchIds(fileIds)) {
                idToName.put(file.getId(), file.getName());
            }
            for (SearchResultItem item : results) {
                if (item.getFileId() != null) {
                    item.setFileName(idToName.get(item.getFileId()));
                }
            }
        } catch (Exception e) {
            log.warn("[resolveFileNames] Failed to resolve file names, count={}", fileIds.size(), e);
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

    /**
     * 文本 token 粗估（用于上下文组装预算裁剪，不追求精确）：
     * 中文/全角字符按 1 token 计，其余字符按 4 字符 ≈ 1 token。
     */
    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjk = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u4e00' && c <= '\u9fff')
                    || (c >= '\u3000' && c <= '\u303f')
                    || (c >= '\uff00' && c <= '\uffef')) {
                cjk++;
            }
        }
        int other = Math.max(0, text.length() - cjk);
        return cjk + (int) Math.ceil(other / 4.0);
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
                        .ne(KbChunk::getChunkType, "parent")
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
                            List<SearchResultItem> results, long startMs, boolean graphChannelDegraded) {
        long elapsed = System.currentTimeMillis() - startMs;
        try {
            // 图谱通道命中数（可观测性：日志面板可看图谱贡献）
            long graphHits = results.stream()
                    .filter(r -> "graph".equals(r.getSource()))
                    .count();

            KbRetrievalLog logEntry = new KbRetrievalLog();
            logEntry.setKbId(kbId);
            logEntry.setQuery(originalQuery);
            logEntry.setHitCount(results.size());
            logEntry.setHasResult(!results.isEmpty());
            logEntry.setLatencyMs((int) elapsed);
            logEntry.setGraphEntityCount((int) graphHits);
            logEntry.setUserId(SecurityUtil.getCurrentUserId());
            logEntry.setCreatedAt(LocalDateTime.now());
            logService.log(logEntry);

            KbLog kbLog = new KbLog();
            kbLog.setKbId(kbId);
            kbLog.setCategory("retrieval");
            kbLog.setAction("search");
            kbLog.setTarget(originalQuery);
            kbLog.setDetail("检索完成，命中 " + results.size() + " 条，模式=" + config.getMode()
                    + (graphChannelDegraded ? "，图谱通道已降级" : ""));
            kbLog.setOperator(SecurityUtil.getCurrentUser() != null
                    ? SecurityUtil.getCurrentUser().getUsername() : "system");
            kbLog.setStatus(graphChannelDegraded ? "degraded" : "success");
            Map<String, Object> extra = new LinkedHashMap<>();
            extra.put("mode", config.getMode());
            extra.put("topK", config.getTopK());
            extra.put("hits", results.size());
            extra.put("duration", elapsed);
            extra.put("graphExpanded", config.getEnableGraphExpand());
            extra.put("graphHits", graphHits);
            // 图谱通道健康状态：ok / degraded（整路失败，通常为 Neo4j 不可达，见服务端告警日志）
            extra.put("graphChannel", graphChannelDegraded ? "degraded" : "ok");
            kbLog.setExtra(JSONUtil.toJsonStr(extra));
            kbLog.setTimestamp(LocalDateTime.now());
            kbLogMapper.insert(kbLog);
        } catch (Exception e) {
            log.warn("记录检索日志失败", e);
        }
    }
}
