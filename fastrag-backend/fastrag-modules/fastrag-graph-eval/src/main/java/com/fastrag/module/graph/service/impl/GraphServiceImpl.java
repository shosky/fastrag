package com.fastrag.module.graph.service.impl;

import cn.hutool.json.JSONUtil;
import com.fastrag.infra.graph.GraphStore;
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

    private final GraphStore graphStore;
    private final KbGraphIndexMapper indexMapper;
    private final MessagePublisher messagePublisher;

    @Override
    public Map<String, Object> getGraphData(String kbId, Integer maxNodes, Boolean excludeChunks) {
        return graphStore.getGraphData(kbId,
                maxNodes != null ? maxNodes : 100,
                excludeChunks != null ? excludeChunks : true);
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
            r.put("totalChunks", idx.getTotalChunks() != null ? idx.getTotalChunks() : 0);
            r.put("builtChunks", idx.getBuiltChunks() != null ? idx.getBuiltChunks() : 0);
        } else {
            r.put("status", "idle");
            r.put("progress", 0);
            r.put("entityCount", 0);
            r.put("relationCount", 0);
            r.put("totalChunks", 0);
            r.put("builtChunks", 0);
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

        // 发送图谱构建消息
        Map<String, Object> msg = new HashMap<>();
        msg.put("kbId", kbId);
        msg.put("mode", mode != null ? mode : "full");
        if (fileIds != null && !fileIds.isEmpty()) {
            msg.put("fileIds", fileIds);
        }
        messagePublisher.publishGraphBuild(msg);
        log.info("Published graph build message for kb: {}, fileIds: {}, mode: {}", kbId, fileIds, mode);
    }

    @Override
    public void retryBuild(String kbId) {
        log.info("Retrying graph build for kb: {}", kbId);

        // 1. 清空旧图谱数据（去重会保留残留，必须显式清空）
        try {
            graphStore.clearGraph(kbId);
            log.info("[Graph Retry] Cleared old graph data for kb: {}", kbId);
        } catch (Exception e) {
            log.warn("[Graph Retry] clearGraph failed for kb {}: {}", kbId, e.getMessage());
        }

        // 2. 重置索引状态
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
            idx.setEntityCount(null);
            idx.setRelationCount(null);
            idx.setTotalChunks(null);
            idx.setBuiltChunks(null);
            idx.setBuildError(null);
            indexMapper.updateById(idx);
        }

        // 3. 以 full 模式重新触发构建
        Map<String, Object> msg = new HashMap<>();
        msg.put("kbId", kbId);
        msg.put("mode", "full");
        messagePublisher.publishGraphBuild(msg);
        log.info("[Graph Retry] Published graph build message for kb: {}", kbId);
    }

    @Override
    public Map<String, Object> getBuildStatus(String kbId) {
        return getIndexStatus(kbId);
    }

    @Override
    public Map<String, Object> getSettings(String kbId) {
        var idx = indexMapper.selectById(kbId);
        if (idx != null && idx.getSettings() != null) {
            try {
                return JSONUtil.parseObj(idx.getSettings());
            } catch (Exception e) {
                log.debug("Failed to parse graph settings, using defaults");
            }
        }
        var r = new HashMap<String, Object>();
        r.put("maxNodes", 100);
        r.put("searchDepth", 2);
        r.put("excludeChunkNodes", true);
        return r;
    }

    @Override
    public void saveSettings(String kbId, Map<String, Object> settings) {
        var idx = indexMapper.selectById(kbId);
        if (idx == null) {
            idx = new KbGraphIndex();
            idx.setKbId(kbId);
            idx.setSettings(JSONUtil.toJsonStr(settings));
            indexMapper.insert(idx);
        } else {
            idx.setSettings(JSONUtil.toJsonStr(settings));
            indexMapper.updateById(idx);
        }
        log.info("Graph settings saved for kbId: {}", kbId);
    }
}
