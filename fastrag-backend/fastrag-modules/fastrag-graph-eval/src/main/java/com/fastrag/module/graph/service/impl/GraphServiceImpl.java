package com.fastrag.module.graph.service.impl;

import com.fastrag.infra.neo4j.Neo4jService;
import com.fastrag.infra.rabbitmq.MessagePublisher;
import com.fastrag.module.graph.entity.KbGraphIndex;
import com.fastrag.module.graph.mapper.KbGraphIndexMapper;
import com.fastrag.module.graph.service.GraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphServiceImpl implements GraphService {

    private final Neo4jService neo4jService;
    private final KbGraphIndexMapper indexMapper;
    private final MessagePublisher messagePublisher;

    @Override
    public Map<String, Object> getGraphData(String kbId, Integer maxNodes, Boolean excludeChunks) {
        return neo4jService.getGraphData(kbId, maxNodes != null ? maxNodes : 100, excludeChunks != null ? excludeChunks : true);
    }

    @Override
    public Map<String, Object> getGraphStats(String kbId) {
        var idx = indexMapper.selectById(kbId);
        var r = new HashMap<String, Object>();
        if (idx == null) {
            r.put("entityCount", 0);
            r.put("relationCount", 0);
            r.put("entityTypes", List.of());
        } else {
            r.put("entityCount", idx.getEntityCount() != null ? idx.getEntityCount() : 0);
            r.put("relationCount", idx.getRelationCount() != null ? idx.getRelationCount() : 0);
            r.put("entityTypes", List.of());
        }
        return r;
    }

    @Override
    public Map<String, Object> getIndexStatus(String kbId) {
        var idx = indexMapper.selectById(kbId);
        var r = new HashMap<String, Object>();
        if (idx != null) {
            r.put("status", idx.getStatus());
            r.put("progress", idx.getBuildProgress() != null ? idx.getBuildProgress() : 0);
            r.put("entityCount", idx.getEntityCount() != null ? idx.getEntityCount() : 0);
            r.put("relationCount", idx.getRelationCount() != null ? idx.getRelationCount() : 0);
        } else {
            r.put("status", "idle");
            r.put("progress", 0);
            r.put("entityCount", 0);
            r.put("relationCount", 0);
        }
        return r;
    }

    @Override
    public void buildIndex(String kbId, String mode, List<String> fileIds) {
        // 更新索引状态
        var idx = indexMapper.selectById(kbId);
        if (idx == null) {
            idx = new KbGraphIndex();
            idx.setKbId(kbId);
            idx.setStatus("building");
            idx.setBuildProgress(0);
            indexMapper.insert(idx);
        } else {
            idx.setStatus("building");
            idx.setBuildProgress(0);
            indexMapper.updateById(idx);
        }

        // 发送图谱构建消息（kbId 级别，由消费者处理所有文件）
        Map<String, Object> msg = new HashMap<>();
        msg.put("kbId", kbId);
        msg.put("mode", mode != null ? mode : "full");
        if (fileIds != null && !fileIds.isEmpty()) {
            msg.put("fileIds", fileIds);
        }
        messagePublisher.publishGraphBuild(msg);
        log.info("Published graph build message for kb: {}", kbId);
    }

    @Override
    public Map<String, Object> getBuildStatus(String kbId) {
        return getIndexStatus(kbId);
    }

    @Override
    public Map<String, Object> getSettings(String kbId) {
        var r = new HashMap<String, Object>();
        r.put("maxNodes", 100);
        r.put("searchDepth", 2);
        r.put("excludeChunkNodes", true);
        return r;
    }

    @Override
    public void saveSettings(String kbId, Map<String, Object> settings) {
        log.info("Save graph settings for kbId: {}", kbId);
    }
}
