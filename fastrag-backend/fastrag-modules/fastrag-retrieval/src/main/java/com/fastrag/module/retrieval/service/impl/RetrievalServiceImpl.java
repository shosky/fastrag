package com.fastrag.module.retrieval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbChunk;
import com.fastrag.module.knowledge.mapper.KbChunkMapper;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalServiceImpl implements RetrievalService {

    private final KbChunkMapper chunkMapper;
    private final RetrievalLogService logService;

    @Override
    public List<SearchResultItem> search(RetrievalRequest req) {
        String kbId = req.getKnowledgeId();
        String query = req.getQuery();
        int topK = req.getConfig() != null ? req.getConfig().getTopK() : 10;

        log.info("MySQL search: kbId={}, query={}, topK={}", kbId, query, topK);

        long startMs = System.currentTimeMillis();

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

        // 自动记录检索日志
        try {
            KbRetrievalLog logEntry = new KbRetrievalLog();
            logEntry.setKbId(kbId);
            logEntry.setQuery(query);
            logEntry.setHitCount(results.size());
            logEntry.setHasResult(!results.isEmpty());
            logEntry.setLatencyMs((int) (System.currentTimeMillis() - startMs));
            logEntry.setUserId(SecurityUtil.getCurrentUserId());
            logEntry.setCreatedAt(LocalDateTime.now());
            logService.log(logEntry);
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
}
