package com.fastrag.module.bpm.executor.impl;

import cn.hutool.core.util.StrUtil;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.NodeExecutor;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import com.fastrag.module.retrieval.model.RetrievalRequest;
import com.fastrag.module.retrieval.model.SearchResultItem;
import com.fastrag.module.retrieval.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 知识库检索节点,委托 fastrag-retrieval */
@Component @RequiredArgsConstructor
public class KbRetrievalNodeExecutor implements NodeExecutor {
    private final SpelEvaluator spel;
    private final RetrievalService retrievalService;

    @Override public String type() { return "kb_retrieval"; }
    @Override public String category() { return "execute"; }
    @Override public void validateConfig(Map<String, Object> config) {
        if (config == null || StrUtil.isBlank((String) config.get("kbId")))
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("kb_retrieval 节点 kbId 不能为空");
    }
    @Override public NodeExecutionResult execute(ExecutionContext ctx) {
        Map<String, Object> cfg = spel.parseConfig(ctx.getCurrentNode().getConfig());
        String kbId = (String) cfg.get("kbId");
        int topK = cfg.get("topK") instanceof Number n ? n.intValue() : 10;
        double threshold = cfg.get("similarityThreshold") instanceof Number t ? t.doubleValue() : 0.0;
        Map<String, Object> vars = new HashMap<>(ctx.getVariables() == null ? Map.of() : ctx.getVariables());
        if (ctx.getNodeInputs() != null) vars.putAll(ctx.getNodeInputs());
        String query = cfg.get("query") == null
                ? String.valueOf(vars.getOrDefault("query", vars.getOrDefault("input", "")))
                : String.valueOf(spel.eval((String) cfg.get("query"), vars));
        if (StrUtil.isBlank(query)) query = String.valueOf(vars.getOrDefault("input", ""));
        RetrievalRequest req = new RetrievalRequest();
        req.setKnowledgeId(kbId);
        req.setQuery(query);
        RetrievalRequest.RetrievalConfig rc = new RetrievalRequest.RetrievalConfig();
        rc.setTopK(topK);
        rc.setSimilarityThreshold(threshold);
        rc.setMode(cfg.get("mode") == null ? "hybrid" : (String) cfg.get("mode"));
        req.setConfig(rc);
        List<SearchResultItem> hits;
        try { hits = retrievalService.search(req); }
        catch (Exception e) { throw BpmErrorCode.EXECUTION_FAILED.of("kb_retrieval: " + e.getMessage()); }
        NodeExecutionResult r = new NodeExecutionResult();
        List<Map<String, Object>> docs = hits == null ? List.of() : hits.stream().map(h -> {
            Map<String, Object> m = new HashMap<>();
            m.put("chunkIndex", h.getChunkIndex());
            m.put("content", h.getContent());
            m.put("similarity", h.getSimilarity());
            m.put("source", h.getSource());
            m.put("fileId", h.getFileId());
            return m;
        }).collect(Collectors.toList());
        r.getOutputs().put("docs", docs);
        r.getOutputs().put("query", query);
        r.getOutputs().put("kbId", kbId);
        return r;
    }
}