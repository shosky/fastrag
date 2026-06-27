package com.fastrag.module.retrieval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
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

    private final KbChunkMapper chunkMapper;

    @Override
    public List<SearchResultItem> search(RetrievalRequest req) {
        String kbId = req.getKnowledgeId();
        String query = req.getQuery();
        int topK = req.getConfig() != null ? req.getConfig().getTopK() : 10;

        log.info("MySQL search: kbId={}, query={}, topK={}", kbId, query, topK);

        List<KbChunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<KbChunk>()
                        .eq(KbChunk::getKbId, kbId)
                        .like(KbChunk::getContent, query)
                        .orderByDesc(KbChunk::getChunkIndex)
                        .last("LIMIT " + topK)
        );

        List<SearchResultItem> results = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KbChunk chunk = chunks.get(i);
            SearchResultItem item = new SearchResultItem();
            item.setIndex(i);
            item.setContent(chunk.getContent());
            item.setFileId(chunk.getFileId());
            item.setChunkIndex(chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0);
            item.setSimilarity(1.0);
            item.setDistance(0.0);
            item.setSource("mysql");
            results.add(item);
        }

        return results;
    }

    @Override
    public long getChunkCount(String kbId) {
        return chunkMapper.selectCount(
                new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getKbId, kbId));
    }
}
