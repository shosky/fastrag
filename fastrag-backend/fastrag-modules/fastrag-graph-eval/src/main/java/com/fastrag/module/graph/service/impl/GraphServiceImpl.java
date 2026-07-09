package com.fastrag.module.graph.service.impl;

import cn.hutool.json.JSONUtil;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.infra.rabbitmq.MessagePublisher;
import com.fastrag.module.graph.entity.KbGraphIndex;
import com.fastrag.module.graph.mapper.KbGraphIndexMapper;
import com.fastrag.module.graph.service.GraphQueryService;
import com.fastrag.module.graph.service.GraphService;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GraphServiceImpl implements GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphServiceImpl.class);

    private final GraphStore graphStore;
    private final KbGraphIndexMapper indexMapper;
    private final MessagePublisher messagePublisher;
    private final GraphQueryService graphQueryService;
    private final LogService logService;

    @Override
    public Map<String, Object> getGraphData(String kbId, Integer maxNodes, Boolean excludeChunks) {
        log.info("[GraphQuery] ====== /graph called: kbId={}, maxNodes={}, excludeChunks={}",
                kbId, maxNodes, excludeChunks);
        long t1 = System.currentTimeMillis();
        Map<String, Object> data = graphStore.getGraphData(kbId,
                maxNodes != null ? maxNodes : 100,
                excludeChunks != null ? excludeChunks : true);
        List<?> nodes = (List<?>) data.getOrDefault("nodes", List.of());
        List<?> edges = (List<?>) data.getOrDefault("edges", List.of());
        log.info("[GraphQuery] /graph result: {} nodes, {} edges, took {} ms",
                nodes.size(), edges.size(), System.currentTimeMillis() - t1);
        if (nodes.isEmpty()) {
            // 诊断：查 KbGraphIndex 看构建状态
            var idx = indexMapper.selectById(kbId);
            if (idx == null) {
                log.warn("[GraphQuery] KbGraphIndex NOT FOUND for kb={} -> graph build has NEVER been triggered", kbId);
            } else {
                log.warn("[GraphQuery] KbGraphIndex status={}, entityCount={}, relationCount={}, totalChunks={}, builtChunks={}",
                        idx.getStatus(), idx.getEntityCount(), idx.getRelationCount(),
                        idx.getTotalChunks(), idx.getBuiltChunks());
            }
        }
        return data;
    }

    @Override
    public Map<String, Object> getGraphStats(String kbId) {
        log.info("[GraphQuery] ====== /graph/stats called: kbId={}", kbId);
        var idx = indexMapper.selectById(kbId);
        var r = new HashMap<String, Object>();

        // 优先从 KbGraphIndex 读取计数（缓存）
        long entityCount = 0;
        long relationCount = 0;
        boolean fromIndex = false;

        if (idx != null && idx.getEntityCount() != null && idx.getEntityCount() > 0) {
            entityCount = idx.getEntityCount();
            relationCount = idx.getRelationCount() != null ? idx.getRelationCount() : 0;
            fromIndex = true;
        } else {
            // KbGraphIndex 计数为 0 或不存在时，从存储层实时查询（兼容早期未记录的数据）
            entityCount = graphStore.countEntities(kbId);
            relationCount = graphStore.countRelations(kbId);
            log.info("[GraphQuery] Fallback to live count: entityCount={}, relationCount={}", entityCount, relationCount);

            // 如果实时查询有数据但 index 为 0，自动修复 index 记录
            if (entityCount > 0 && idx != null) {
                idx.setEntityCount((int) entityCount);
                idx.setRelationCount((int) relationCount);
                if (idx.getStatus() == null || "idle".equals(idx.getStatus()) || "failed".equals(idx.getStatus())) {
                    idx.setStatus("completed");
                }
                indexMapper.updateById(idx);
                log.info("[GraphQuery] Auto-repaired KbGraphIndex counts for kb={}: entity={}, relation={}",
                        kbId, entityCount, relationCount);
            }
        }

        r.put("entityCount", entityCount);
        r.put("relationCount", relationCount);
        r.put("entityTypes", graphStore.getLabels(kbId));

        log.info("[GraphQuery] /graph/stats result: entityCount={}, relationCount={}, entityTypes={} (fromIndex={})",
                entityCount, relationCount, r.get("entityTypes"), fromIndex);
        return r;
    }

    @Override
    public Map<String, Object> getIndexStatus(String kbId) {
        log.info("[GraphQuery] ====== /graph/index called: kbId={}", kbId);
        var idx = indexMapper.selectById(kbId);
        var r = new HashMap<String, Object>();
        if (idx != null) {
            r.put("status", idx.getStatus());
            r.put("progress", idx.getBuildProgress() != null ? idx.getBuildProgress() : 0);
            r.put("entityCount", idx.getEntityCount() != null ? idx.getEntityCount() : 0);
            r.put("relationCount", idx.getRelationCount() != null ? idx.getRelationCount() : 0);
            r.put("totalChunks", idx.getTotalChunks() != null ? idx.getTotalChunks() : 0);
            r.put("builtChunks", idx.getBuiltChunks() != null ? idx.getBuiltChunks() : 0);
            r.put("failedChunks", idx.getFailedChunks() != null ? idx.getFailedChunks() : 0);
            r.put("buildError", idx.getBuildError());
            r.put("lastBuiltAt", idx.getLastBuiltAt());
            log.info("[GraphQuery] /graph/index result: status={}, entityCount={}, relationCount={}, totalChunks={}, builtChunks={}, failedChunks={}",
                    idx.getStatus(), idx.getEntityCount(), idx.getRelationCount(),
                    idx.getTotalChunks(), idx.getBuiltChunks(), idx.getFailedChunks());
        } else {
            r.put("status", "idle");
            r.put("progress", 0);
            r.put("entityCount", 0);
            r.put("relationCount", 0);
            r.put("totalChunks", 0);
            r.put("builtChunks", 0);
            r.put("failedChunks", 0);
            r.put("buildError", null);
            r.put("lastBuiltAt", null);
            log.warn("[GraphQuery] /graph/index: KbGraphIndex NOT FOUND for kb={} -> graph build never triggered", kbId);
        }
        return r;
    }

    @Override
    public void buildIndex(String kbId, String mode, List<String> fileIds) {
        log.info("========== [GraphBuild-Manual] buildIndex called ==========");
        log.info("kbId={}, mode={}, fileIds={}", kbId, mode, fileIds);

        // 记录图谱构建开始日志（覆盖内部调用场景，如 retryBuild 内部调用）
        try {
            logService.addLog(kbId, LogCategory.operation, ActionType.graph_build_started,
                    "", "开始构建图谱，模式: " + (mode != null ? mode : "full") +
                            ", 文件数: " + (fileIds != null && !fileIds.isEmpty() ? fileIds.size() : "all"),
                    "system", "success", null);
        } catch (Exception e) {
            log.warn("[Log] Failed to record graph build start log for kb={}", kbId);
        }

        // 更新索引状态
        var idx = indexMapper.selectById(kbId);
        if (idx == null) {
            idx = new KbGraphIndex();
            idx.setKbId(kbId);
            idx.setStatus("building");
            idx.setBuildProgress(0);
            indexMapper.insert(idx);
            log.info("[GraphBuild-Manual] Created new KbGraphIndex record for kb={}", kbId);
        } else {
            idx.setStatus("building");
            idx.setBuildProgress(0);
            indexMapper.updateById(idx);
            log.info("[GraphBuild-Manual] Updated existing KbGraphIndex record for kb={}, prevStatus={}", kbId, idx.getStatus());
        }

        // 发送图谱构建消息
        Map<String, Object> msg = new HashMap<>();
        msg.put("kbId", kbId);
        msg.put("mode", mode != null ? mode : "full");
        if (fileIds != null && !fileIds.isEmpty()) {
            msg.put("fileIds", fileIds);
        }
        log.info("[GraphBuild-Manual] About to publish graph build message: {}", msg);
        messagePublisher.publishGraphBuild(msg);
        log.info("[GraphBuild-Manual] Published graph build message for kb: {}, fileIds: {}, mode: {}", kbId, fileIds, mode);
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
        // 记录图谱设置更新日志
        try {
            logService.addLog(kbId, LogCategory.operation, ActionType.graph_settings_updated,
                    "", "更新图谱设置", "system", "success", null);
        } catch (Exception e) {
            log.warn("[Log] Failed to record graph settings update log for kb={}", kbId);
        }
    }

    // ==================== P1 新增方法 ====================

    @Override
    public Map<String, Object> searchNodes(String kbId, String keyword, Integer maxNodes) {
        return graphStore.searchNodes(kbId, keyword, maxNodes != null ? maxNodes : 50);
    }

    @Override
    public List<String> getLabels(String kbId) {
        return graphStore.getLabels(kbId);
    }

    @Override
    public void deleteFileGraph(String kbId, String fileId) {
        graphStore.deleteFileGraph(kbId, fileId);
        log.info("Deleted file graph data: kb={}, file={}", kbId, fileId);
        // 记录删除图谱日志（覆盖 FileServiceImpl 内部调用场景）
        try {
            logService.addLog(kbId, LogCategory.operation, ActionType.graph_deleted,
                    fileId, "删除文件关联图谱数据", "system", "success", null);
        } catch (Exception e) {
            log.warn("[Log] Failed to record graph delete log for kb={}, file={}", kbId, fileId);
        }
    }

    @Override
    public List<Map<String, Object>> rankChunksByPpr(String kbId, List<String> entityNames, int topK) {
        return graphQueryService.rankChunksByPpr(kbId, entityNames, topK);
    }
}
