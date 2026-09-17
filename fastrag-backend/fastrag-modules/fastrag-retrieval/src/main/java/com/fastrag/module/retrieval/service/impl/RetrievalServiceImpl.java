package com.fastrag.module.retrieval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.json.JSONUtil;
import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.rerank.RerankService;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.entity.KbQaPair;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.mapper.KbQaPairMapper;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.publish.entity.KbLog;
import com.fastrag.module.publish.mapper.KbLogMapper;
import com.fastrag.module.retrieval.entity.KbRetrievalLog;
import com.fastrag.module.retrieval.model.RetrievalRequest;
import com.fastrag.module.retrieval.model.SearchResultItem;
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
 * 混合检索：词法打分（多词召回+覆盖率/频次归一）× 语义向量（AI 网关 Embedding，chunk 向量懒计算并缓存到 kb_chunk.embedding）加权融合。
 * 向量来源为库内缓存（Milvus 当前为禁用 stub），AI 网关不可达时优雅降级为纯词法检索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private static final int CANDIDATE_LIMIT = 300;
    private static final int EMBED_BUDGET_PER_SEARCH = 64;

    private final KbChunkMapper chunkMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final KbQaPairMapper qaPairMapper;
    private final RetrievalLogService logService;
    private final KbLogMapper kbLogMapper;
    private final EmbeddingService embeddingService;
    private final RerankService rerankService;

    @Override
    public List<SearchResultItem> search(RetrievalRequest req) {
        String kbId = req.getKnowledgeId();
        String query = req.getQuery();
        int topK = req.getConfig() != null && req.getConfig().getTopK() > 0 ? req.getConfig().getTopK() : 10;
        double threshold = req.getConfig() != null ? req.getConfig().getSimilarityThreshold() : 0.0;

        log.info("Hybrid search: kbId={}, query={}, topK={}, threshold={}", kbId, query, topK, threshold);

        long startMs = System.currentTimeMillis();

        List<String> tokens = tokenize(query);
        // 多词 OR 召回（整句 LIKE 召回率过低），再在内存中精排
        List<KbChunk> candidates = chunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getKbId, kbId)
                        .and(w -> {
                            boolean first = true;
                            List<String> terms = new ArrayList<>(tokens);
                            if (terms.isEmpty()) terms.add(query.trim());
                            for (String t : terms) {
                                if (first) { w.like(KbChunk::getContent, t); first = false; }
                                else w.or().like(KbChunk::getContent, t);
                            }
                        })
                        .last("LIMIT " + CANDIDATE_LIMIT)
        );

        // 兜底召回：词法零命中且无向量时，取库内分片作为推荐候选
        boolean fallbackMode = candidates.isEmpty();

        // 问答对（FAQ）召回：问题/答案/多关键词分词命中 + 生效期过滤（生效时间外的问答对不参与召回）
        List<SearchResultItem> qaResults = searchQaPairs(kbId, query, tokens);

        KnowledgeBase kb = kbMapper.selectById(kbId);
        double semanticWeight = resolveSemanticWeight(kb, req);
        Map<String, double[]> vectors = resolveVectors(kb, query, candidates, fallbackMode);
        boolean vectorReady = vectors.containsKey(QUERY_KEY);

        // 1) 词法得分 + 2) 向量得分 → 加权融合
        List<SearchResultItem> results = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            KbChunk chunk = candidates.get(i);
            double kwScore = keywordScore(chunk.getContent(), tokens);
            double score = kwScore;
            if (vectorReady) {
                double[] qv = vectors.get(QUERY_KEY);
                double[] cv = vectors.get(chunk.getId());
                double vecScore = (cv == null) ? kwScore : cosine(qv, cv);
                score = semanticWeight * vecScore + (1 - semanticWeight) * kwScore;
            }
            score = applyTagBoost(kbId, chunk, req, score);
            if (score < threshold) continue;

            SearchResultItem item = new SearchResultItem();
            item.setIndex(i);
            item.setContent(chunk.getContent());
            item.setFileId(chunk.getFileId());
            item.setChunkIndex(chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0);
            item.setSimilarity(round4(score));
            item.setDistance(round4(1 - score));
            item.setSource(vectorReady ? "hybrid" : "keyword");
            applyHighlight(item, query);
            results.add(item);
        }

        // FAQ 问答对命中与分片命中合并，进入统一的排序/TopK 截断
        results.addAll(qaResults);

        // 可选重排（配置了 rerank 模型时）
        if (req.getConfig() != null && req.getConfig().isEnableRerank()
                && req.getConfig().getRerankModel() != null && !results.isEmpty()) {
            results = rerankSafe(req.getConfig().getRerankModel(), query, results, topK);
        } else {
            results.sort(Comparator.comparingDouble(SearchResultItem::getSimilarity).reversed());
            results = results.stream().limit(topK).collect(Collectors.toList());
            for (int i = 0; i < results.size(); i++) results.get(i).setIndex(i);
        }

        // 无结果兜底：返回库内近期分片作为推荐（带 fallback 标记，前端展示"为您推荐"）
        if (results.isEmpty() && fallbackMode) {
            List<KbChunk> recs = chunkMapper.selectList(
                    new LambdaQueryWrapper<KbChunk>()
                            .eq(KbChunk::getKbId, kbId)
                            .orderByDesc(KbChunk::getChunkIndex)
                            .last("LIMIT " + topK));
            for (int i = 0; i < recs.size(); i++) {
                KbChunk chunk = recs.get(i);
                SearchResultItem item = new SearchResultItem();
                item.setIndex(i);
                item.setContent(chunk.getContent());
                item.setFileId(chunk.getFileId());
                item.setChunkIndex(chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0);
                item.setSimilarity(0.0);
                item.setDistance(1.0);
                item.setSource("fallback");
                item.setFallback(true);
                applyHighlight(item, query);
                results.add(item);
            }
        }

        // 自动记录检索日志
        long elapsed = System.currentTimeMillis() - startMs;
        try {
            KbRetrievalLog logEntry = new KbRetrievalLog();
            logEntry.setKbId(kbId);
            logEntry.setQuery(query);
            logEntry.setHitCount((int) results.stream().filter(r -> !r.isFallback()).count());
            logEntry.setHasResult(!results.isEmpty());
            logEntry.setLatencyMs((int) elapsed);
            logEntry.setUserId(SecurityUtil.getCurrentUserId());
            logEntry.setCreatedAt(LocalDateTime.now());
            logService.log(logEntry);

            // 同步写入 kb_log 表，供知识库详情-日志管理页面展示
            KbLog kbLog = new KbLog();
            kbLog.setKbId(kbId);
            kbLog.setCategory("retrieval");
            kbLog.setAction("search");
            kbLog.setTarget(query);
            kbLog.setDetail("检索完成，命中 " + results.size() + " 条");
            kbLog.setOperator(SecurityUtil.getCurrentUser() != null
                ? SecurityUtil.getCurrentUser().getUsername() : "system");
            kbLog.setStatus("success");
            Map<String,Object> extra = new LinkedHashMap<>();
            extra.put("mode", vectorReady ? "hybrid" : "keyword");
            extra.put("topK", topK);
            extra.put("hits", results.size());
            extra.put("duration", elapsed);
            kbLog.setExtra(JSONUtil.toJsonStr(extra));
            kbLog.setTimestamp(LocalDateTime.now());
            kbLogMapper.insert(kbLog);
        } catch (Exception e) {
            log.warn("记录检索日志失败", e);
        }

        return results;
    }

    @Override
    public long getChunkCount(String kbId) {
        return chunkMapper.selectCount(
                new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId, kbId));
    }

    private static final String QUERY_KEY = "__query__";

    /** 分词：拉丁/数字词 + CJK 二元组（覆盖单字无区分度、整句无召回的问题） */
    private List<String> tokenize(String query) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (query == null || query.isBlank()) return new ArrayList<>();
        String q = query.trim();
        for (String w : q.split("[\\s，。；！？,.;!?、]+")) {
            if (w.matches("[a-zA-Z0-9]+")) tokens.add(w.toLowerCase());
            else if (w.length() >= 2) tokens.add(w);
        }
        StringBuilder cjk = new StringBuilder();
        for (char c : q.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) cjk.append(c);
            else if (cjk.length() > 0) { flushBigrams(cjk, tokens); cjk.setLength(0); }
        }
        flushBigrams(cjk, tokens);
        return new ArrayList<>(tokens);
    }

    private void flushBigrams(StringBuilder cjk, Set<String> tokens) {
        String s = cjk.toString();
        if (s.length() == 1) tokens.add(s);
        for (int i = 0; i + 2 <= s.length(); i++) tokens.add(s.substring(i, i + 2));
    }

    /** 词法得分 = 覆盖率(0.7) + 归一化频次(0.3)，无命中为 0 */
    private double keywordScore(String content, List<String> tokens) {
        if (content == null || content.isEmpty() || tokens.isEmpty()) return 0.0;
        int matched = 0, totalFreq = 0, maxPossible = 0;
        for (String t : tokens) {
            int freq = countOccurrences(content, t);
            maxPossible += 3;
            if (freq > 0) { matched++; totalFreq += Math.min(freq, 3); }
        }
        if (matched == 0) return 0.0;
        double coverage = (double) matched / tokens.size();
        double freqNorm = (double) totalFreq / maxPossible;
        return round4(0.7 * coverage + 0.3 * freqNorm);
    }

    private int countOccurrences(String content, String token) {
        int count = 0, idx = 0;
        while ((idx = content.indexOf(token, idx)) >= 0) { count++; idx += token.length(); }
        return count;
    }

    /** 语义权重：请求 > 知识库 retrievalConfig.semanticWeight > 默认 0.5 */
    private double resolveSemanticWeight(KnowledgeBase kb, RetrievalRequest req) {
        if (kb != null && kb.getRetrievalConfig() != null) {
            try {
                var node = JSONUtil.parseObj(kb.getRetrievalConfig());
                if (node.containsKey("semanticWeight")) {
                    double w = node.getDouble("semanticWeight");
                    if (w >= 0 && w <= 1) return w;
                }
            } catch (Exception ignored) { }
        }
        return 0.5;
    }

    /**
     * 向量准备：query 向量实时计算；chunk 向量读 kb_chunk.embedding 缓存，
     * 缺失时批量补算（每次搜索限量），任一环节失败则放弃向量路径（纯词法降级）。
     */
    private Map<String, double[]> resolveVectors(KnowledgeBase kb, String query, List<KbChunk> chunks, boolean fallbackMode) {
        Map<String, double[]> result = new HashMap<>();
        if (fallbackMode || kb == null || kb.getEmbeddingModel() == null || kb.getEmbeddingModel().isBlank()) return result;
        String model = kb.getEmbeddingModel();
        try {
            result.put(QUERY_KEY, toArray(embeddingService.embed(model, query)));
        } catch (Exception e) {
            log.warn("查询向量化失败，降级词法检索: {}", e.getMessage());
            result.remove(QUERY_KEY);
            return result;
        }
        List<KbChunk> missing = new ArrayList<>();
        for (KbChunk c : chunks) {
            double[] v = parseCachedVector(c.getEmbedding());
            if (v != null) result.put(c.getId(), v); else missing.add(c);
        }
        if (!missing.isEmpty()) {
            int budget = Math.min(missing.size(), EMBED_BUDGET_PER_SEARCH);
            try {
                List<String> texts = missing.subList(0, budget).stream().map(c -> c.getContent() == null ? "" : c.getContent()).toList();
                List<List<Float>> vecs = embeddingService.embed(model, texts);
                for (int i = 0; i < vecs.size(); i++) {
                    KbChunk c = missing.get(i);
                    double[] v = toArray(vecs.get(i));
                    result.put(c.getId(), v);
                    try {
                        KbChunk upd = new KbChunk();
                        upd.setId(c.getId());
                        upd.setEmbedding(JSONUtil.toJsonStr(vecs.get(i)));
                        chunkMapper.updateById(upd);
                    } catch (Exception ignored) { }
                }
            } catch (Exception e) {
                log.warn("分片向量补算失败，已命中的缓存向量仍参与融合: {}", e.getMessage());
            }
        }
        return result;
    }

    private double[] parseCachedVector(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            List<Float> list = JSONUtil.toList(json, Float.class);
            if (list == null || list.isEmpty()) return null;
            double[] v = new double[list.size()];
            for (int i = 0; i < list.size(); i++) v[i] = list.get(i);
            return v;
        } catch (Exception e) { return null; }
    }

    private double[] toArray(List<Float> list) {
        double[] v = new double[list.size()];
        for (int i = 0; i < list.size(); i++) v[i] = list.get(i);
        return v;
    }

    private double cosine(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        if (na == 0 || nb == 0) return 0.0;
        return round4((dot / (Math.sqrt(na) * Math.sqrt(nb)) + 1) / 2);
    }

    /** 偏好标签加权：命中 preferTags 的结果最多上浮 15% */
    private double applyTagBoost(String kbId, KbChunk chunk, RetrievalRequest req, double score) {
        if (req.getPreferTags() == null || req.getPreferTags().isEmpty()) return score;
        String hay = (chunk.getFileName() == null ? "" : chunk.getFileName()) + "\n" + (chunk.getContent() == null ? "" : chunk.getContent());
        for (String tag : req.getPreferTags()) {
            if (tag != null && !tag.isBlank() && hay.contains(tag)) return Math.min(1.0, score * 1.15);
        }
        return score;
    }

    private List<SearchResultItem> rerankSafe(String model, String query, List<SearchResultItem> items, int topK) {
        try {
            List<String> docs = items.stream().map(SearchResultItem::getContent).toList();
            List<Map<String, Object>> ranked = rerankService.rerank(model, query, docs, topK);
            List<SearchResultItem> out = new ArrayList<>();
            int i = 0;
            for (Map<String, Object> r : ranked) {
                int idx = ((Number) r.get("index")).intValue();
                SearchResultItem src = items.get(idx);
                src.setIndex(i++);
                if (r.get("score") instanceof Number s) src.setSimilarity(round4(Math.min(1.0, Math.max(0.0, s.doubleValue()))));
                src.setDistance(round4(1 - src.getSimilarity()));
                src.setSource("rerank");
                out.add(src);
            }
            return out;
        } catch (Exception e) {
            log.warn("重排失败，使用融合排序: {}", e.getMessage());
            items.sort(Comparator.comparingDouble(SearchResultItem::getSimilarity).reversed());
            List<SearchResultItem> out = items.stream().limit(topK).collect(Collectors.toList());
            for (int i = 0; i < out.size(); i++) out.get(i).setIndex(i);
            return out;
        }
    }

    /** 问答对（FAQ）召回：多关键词命中取高分，问题/答案分词覆盖率融合打分；仅召回生效期内的问答对 */
    private List<SearchResultItem> searchQaPairs(String kbId, String query, List<String> tokens) {
        List<SearchResultItem> out = new ArrayList<>();
        if (tokens.isEmpty()) return out;
        LocalDateTime now = LocalDateTime.now();
        int i = 0;
        for (KbQaPair qa : qaPairMapper.selectList(new LambdaQueryWrapper<KbQaPair>().eq(KbQaPair::getKbId, kbId))) {
            // 生效时间设置：生效期外的问答对不参与召回
            if (qa.getEffectiveStart() != null && now.isBefore(qa.getEffectiveStart())) continue;
            if (qa.getEffectiveEnd() != null && now.isAfter(qa.getEffectiveEnd())) continue;
            String hay = nvl(qa.getKeywords()) + "\n" + nvl(qa.getQuestion());
            String full = hay + "\n" + nvl(qa.getAnswer());
            // 多关键词配置：关键词整词命中查询即直接高分命中
            double kwHit = 0;
            if (qa.getKeywords() != null) {
                for (String kw : qa.getKeywords().split("[,，]")) {
                    if (!kw.isBlank() && query.contains(kw.trim())) kwHit = Math.max(kwHit, 0.95);
                }
            }
            double score = Math.max(kwHit, 0.55 * coverageScore(tokens, hay) + 0.2 * coverageScore(tokens, full));
            if (score < 0.3) continue;
            SearchResultItem item = new SearchResultItem();
            item.setIndex(i++);
            item.setContent("Q: " + nvl(qa.getQuestion()) + "\nA: " + nvl(qa.getAnswer()));
            item.setFileId(qa.getFileId() == null ? "qa:" + qa.getId() : qa.getFileId());
            item.setChunkIndex(0);
            item.setSimilarity(round4(Math.min(1.0, score)));
            item.setDistance(round4(1 - item.getSimilarity()));
            item.setSource("qa");
            applyHighlight(item, query);
            out.add(item);
        }
        return out;
    }

    private double coverageScore(List<String> tokens, String text) {
        if (text == null || text.isEmpty() || tokens.isEmpty()) return 0.0;
        long hit = tokens.stream().filter(text::contains).count();
        return (double) hit / tokens.size();
    }

    private String nvl(String s) { return s == null ? "" : s; }

    private double round4(double v) { return Math.round(v * 10000) / 10000.0; }

    // 高亮定位：提取 query 命中的关键词（highlights）并生成带 <mark> 的预览片段（previewSnippet）
    private void applyHighlight(SearchResultItem item, String query) {
        String content = item.getContent() == null ? "" : item.getContent();
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (query != null && !query.isBlank()) {
            tokens.add(query.trim());
            for (String p : query.trim().split("[\\s]+")) {
                if (p.length() >= 2) tokens.add(p);
            }
        }
        List<String> hits = new ArrayList<>();
        for (String t : tokens) {
            if (t.length() >= 2 && content.contains(t) && hits.size() < 8) hits.add(t);
        }
        item.setHighlights(hits);
        if (!hits.isEmpty()) {
            String first = hits.get(0);
            int pos = content.indexOf(first);
            int start = Math.max(0, pos - 50);
            int end = Math.min(content.length(), pos + first.length() + 70);
            String snippet = content.substring(start, end);
            for (String t : hits) snippet = snippet.replace(t, "<mark>" + t + "</mark>");
            item.setPreviewSnippet((start > 0 ? "..." : "") + snippet + (end < content.length() ? "..." : ""));
        } else {
            item.setPreviewSnippet(content.length() > 120 ? content.substring(0, 120) + "..." : content);
        }
    }
}
