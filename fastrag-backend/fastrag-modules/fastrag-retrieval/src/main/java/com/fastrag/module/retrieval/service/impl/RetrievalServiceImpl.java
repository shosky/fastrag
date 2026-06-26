package com.fastrag.module.retrieval.service.impl;

import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.infra.elasticsearch.ESIndexService;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.knowledge.entity.KnowledgeBase;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.mapper.KnowledgeBaseMapper;
import com.fastrag.module.retrieval.model.RetrievalRequest;
import com.fastrag.module.retrieval.model.SearchResultItem;
import com.fastrag.module.retrieval.service.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private final ESIndexService esService;
    private final MilvusService milvusService;
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseMapper kbMapper;
    private final KbChunkMapper chunkMapper;

    @Override
    public List<SearchResultItem> search(RetrievalRequest req) {
        String kbId = req.getKnowledgeId();
        String query = req.getQuery();
        String mode = req.getConfig() != null ? req.getConfig().getMode() : "hybrid";
        int topK = req.getConfig() != null ? req.getConfig().getTopK() : 10;

        switch (mode) {
            case "fulltext":
                return fulltextSearch(kbId, query, topK);
            case "vector":
                return vectorSearch(kbId, query, topK);
            case "hybrid":
            default:
                return hybridSearch(kbId, query, topK);
        }
    }

    @Override
    public long getChunkCount(String kbId) {
        return chunkMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getKbId, kbId));
    }

    private List<SearchResultItem> fulltextSearch(String kbId, String query, int topK) {
        List<SearchResultItem> results = new ArrayList<>();
        try {
            String esIndex = "kb_" + kbId.replace("-", "_");
            var esResults = esService.search(esIndex, query, topK);
            for (int i = 0; i < esResults.size(); i++) {
                var r = esResults.get(i);
                var item = new SearchResultItem();
                item.setIndex(i);
                item.setContent((String) r.get("content"));
                item.setFileId((String) r.get("fileId"));
                item.setChunkIndex(r.get("chunkIndex") instanceof Integer ? (int) r.get("chunkIndex") : 0);
                item.setSimilarity(r.get("score") instanceof Double ? (double) r.get("score") : 0);
                item.setDistance(1.0 - item.getSimilarity());
                item.setSource("fulltext");
                if (r.containsKey("highlights")) item.setHighlights((List<String>) r.get("highlights"));
                results.add(item);
            }
        } catch (Exception e) {
            log.error("Fulltext search error", e);
        }
        return results;
    }

    private List<SearchResultItem> vectorSearch(String kbId, String query, int topK) {
        List<SearchResultItem> results = new ArrayList<>();
        try {
            KnowledgeBase kb = kbMapper.selectById(kbId);
            if (kb == null || kb.getEmbeddingModel() == null) {
                log.warn("KB not found or no embedding model: {}", kbId);
                return results;
            }

            // 生成查询向量
            List<Float> queryVector = embeddingService.embed(kb.getEmbeddingModel(), query);

            // Milvus 向量搜索
            String collection = "kb_" + kbId.replace("-", "_");
            var milvusResults = milvusService.search(collection, queryVector, topK);

            // 获取切片内容
            for (int i = 0; i < milvusResults.size(); i++) {
                var r = milvusResults.get(i);
                String chunkId = (String) r.get("id");
                KbChunk chunk = chunkMapper.selectById(chunkId);

                var item = new SearchResultItem();
                item.setIndex(i);
                item.setContent(chunk != null ? chunk.getContent() : "");
                item.setFileId((String) r.get("fileId"));
                item.setChunkIndex(r.get("chunkIndex") instanceof Long ? ((Long) r.get("chunkIndex")).intValue() : 0);
                double distance = r.get("distance") instanceof Double ? (double) r.get("distance") : 0;
                item.setDistance(distance);
                item.setSimilarity(1.0 - distance);
                item.setSource("vector");
                results.add(item);
            }
        } catch (Exception e) {
            log.error("Vector search error", e);
        }
        return results;
    }

    private List<SearchResultItem> hybridSearch(String kbId, String query, int topK) {
        // 并行执行全文和向量搜索
        List<SearchResultItem> fulltextResults = fulltextSearch(kbId, query, topK);
        List<SearchResultItem> vectorResults = vectorSearch(kbId, query, topK);

        // RRF (Reciprocal Rank Fusion) 融合
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, SearchResultItem> itemMap = new LinkedHashMap<>();

        int k = 60; // RRF 常数
        for (int i = 0; i < fulltextResults.size(); i++) {
            String key = fulltextResults.get(i).getFileId() + "_" + fulltextResults.get(i).getChunkIndex();
            scoreMap.merge(key, 1.0 / (k + i + 1), Double::sum);
            itemMap.put(key, fulltextResults.get(i));
        }
        for (int i = 0; i < vectorResults.size(); i++) {
            String key = vectorResults.get(i).getFileId() + "_" + vectorResults.get(i).getChunkIndex();
            scoreMap.merge(key, 1.0 / (k + i + 1), Double::sum);
            if (!itemMap.containsKey(key)) {
                itemMap.put(key, vectorResults.get(i));
            }
        }

        // 按融合分数排序
        List<SearchResultItem> results = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    SearchResultItem item = itemMap.get(e.getKey());
                    item.setSimilarity(e.getValue());
                    item.setSource("hybrid");
                    return item;
                })
                .collect(Collectors.toList());

        return results;
    }
}
