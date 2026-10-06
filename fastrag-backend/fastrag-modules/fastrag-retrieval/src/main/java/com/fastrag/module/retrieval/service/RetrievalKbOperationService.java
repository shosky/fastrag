package com.fastrag.module.retrieval.service;

import cn.hutool.json.JSONUtil;
import com.fastrag.common.service.KbOperationService;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.knowledge.service.KbService;
import com.fastrag.module.retrieval.model.RetrievalRequest;
import com.fastrag.module.retrieval.model.SearchResultItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link KbOperationService} 真实检索实现（@Primary，2026-09-30 全 agentic 化）。
 *
 * <p>fastrag-knowledge 侧原实现是 TODO 桩（返回「检索服务集成中」占位数据）；真实实现落在
 * 本模块的原因：knowledge → retrieval 会成依赖环，而 RetrievalService 与 KB 检索配置
 * （contextAssemblyStrategy 等）本就在此。query_kb 工具（KnowledgeToolExecutor）经
 * common 契约 {@code KbOperationService} 按类型注入到本 @Primary 实现。</p>
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RetrievalKbOperationService implements KbOperationService {

    private final RetrievalService retrievalService;
    private final KnowledgeBaseMapper kbMapper;
    private final KbService kbService;

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listKnowledgeBases(String userId) {
        // 与 knowledge 侧原实现同构（委托 KbService 列表投影；知识库权限过滤沿用 KbService 语义）
        try {
            Map<String, Object> result = kbService.list(null, null, 1, 100);
            Object listObj = result.get("list");
            if (listObj instanceof List) {
                List<Object> kbList = (List<Object>) listObj;
                return kbList.stream().map(kb -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    if (kb instanceof com.fastrag.module.knowledge.model.KbDto dto) {
                        map.put("id", dto.getId());
                        map.put("name", dto.getName());
                        map.put("description", dto.getDescription());
                        map.put("category", dto.getCategory());
                    }
                    return map;
                }).collect(Collectors.toList());
            }
            return List.of();
        } catch (Exception e) {
            log.error("[RetrievalKbOperation] 列出知识库失败", e);
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> queryKnowledgeBase(String kbId, String query, int topK) {
        if (kbId == null || kbId.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            RetrievalRequest req = new RetrievalRequest();
            req.setKnowledgeId(kbId);
            req.setQuery(query);
            req.setConfig(buildRetrievalConfig(kbId, topK));

            List<SearchResultItem> results = retrievalService.search(req);
            List<Map<String, Object>> chunks = new ArrayList<>();
            for (SearchResultItem item : results) {
                if (item.getContent() == null || item.getContent().isBlank()) {
                    continue;
                }
                Map<String, Object> chunk = new LinkedHashMap<>();
                chunk.put("content", item.getContent());
                chunk.put("score", item.getSimilarity());
                chunk.put("source", item.getFileName());
                chunk.put("file_id", item.getFileId());
                chunk.put("kb_id", kbId);
                chunk.put("chunk_index", item.getChunkIndex());
                chunk.put("chunk_type", item.getChunkType());
                chunks.add(chunk);
            }
            log.info("[RetrievalKbOperation] query_kb kbId={} query='{}' hits={}", kbId, query, chunks.size());
            return chunks;
        } catch (Exception e) {
            log.error("[RetrievalKbOperation] query_kb failed kbId={}", kbId, e);
            return List.of();
        }
    }

    /**
     * 以 KB 保存的检索配置为基底、仅覆盖 topK（App 会话原 buildRetrievalConfig 语义平移）：
     * 优先级与 {@code RetrievalServiceImpl.loadAndMergeConfig} 一致——
     * 请求参数 &gt; KB 保存配置 &gt; 系统默认；上下文组装策略（auto/parent_chunk/window）等
     * 语义增强字段仅在 KB 配置显式设置时透传。
     */
    private RetrievalRequest.RetrievalConfig buildRetrievalConfig(String kbId, int topK) {
        RetrievalRequest.RetrievalConfig config = new RetrievalRequest.RetrievalConfig();
        config.setTopK(topK);
        try {
            KnowledgeBase kb = kbMapper.selectById(kbId);
            if (kb != null && kb.getRetrievalConfig() != null && !kb.getRetrievalConfig().isBlank()) {
                RetrievalRequest.RetrievalConfig saved =
                        JSONUtil.toBean(kb.getRetrievalConfig(), RetrievalRequest.RetrievalConfig.class);
                if (saved != null) {
                    config.setContextAssemblyStrategy(saved.getContextAssemblyStrategy());
                    config.setEnableClauseRecall(saved.getEnableClauseRecall());
                    config.setContextWindowSize(saved.getContextWindowSize());
                    config.setMaxContextTokens(saved.getMaxContextTokens());
                    config.setEnableRerank(saved.getEnableRerank());
                    config.setRerankModel(saved.getRerankModel());
                    config.setEnableMultiQuery(saved.getEnableMultiQuery());
                    config.setEnableSynonymExpansion(saved.getEnableSynonymExpansion());
                }
            }
        } catch (Exception e) {
            log.warn("[RetrievalKbOperation] load KB retrieval config failed kbId={}: {}", kbId, e.getMessage());
        }
        return config;
    }
}
