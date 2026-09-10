package com.fastrag.module.graph.service.impl;

/**
 * 知识图谱数据管理服务实现类，实现 {@link com.fastrag.module.graph.service.GraphService} 接口。
 *
 * <p>提供知识图谱的全功能管理，包括数据可视化查询、图谱构建、文件管理和PPR检索增强等功能。
 * 是知识图谱模块的核心业务实现，协调GraphStore存储层、消息队列和日志服务等组件。</p>
 *
 * <p>核心业务逻辑：</p>
 * <ul>
 *   <li>图谱数据查询：委托 {@link com.fastrag.infra.graph.GraphStore} 获取图谱节点/边数据、
 *       统计信息、子图搜索和标签列表。统计信息优先从KbGraphIndex缓存读取，
 *       缓存缺失时从存储层实时查询并自动修复缓存</li>
 *   <li>图谱构建：更新构建状态为building后，通过 {@link com.fastrag.infra.rabbitmq.MessagePublisher}
 *       发送图谱构建消息到消息队列，由消费者异步处理。支持full（全量）和incremental（增量）两种模式</li>
 *   <li>重试构建：清空旧图谱数据，重置所有chunk的graph_indexed标记，以full模式重新触发构建</li>
 *   <li>文件图谱删除：删除指定文件关联的图谱数据，并同步刷新KbGraphIndex缓存计数。
 *       图谱已清空时重置为idle状态</li>
 *   <li>设置管理：将图谱构建配置序列化为JSON存储到KbGraphIndex的settings字段</li>
 *   <li>PPR排序：委托 {@link GraphQueryService} 执行Personalized PageRank算法</li>
 * </ul>
 *
 * <p>与其他模块的交互：</p>
 * <ul>
 *   <li>{@link com.fastrag.infra.graph.GraphStore} - 图谱数据的底层存储（Neo4j，ADR-0002）</li>
 *   <li>{@link com.fastrag.infra.rabbitmq.MessagePublisher} - 图谱构建消息发布</li>
 *   <li>{@link GraphQueryService} - PPR排序计算</li>
 *   <li>{@link com.fastrag.module.publish.service.LogService} - 操作日志记录</li>
 * </ul>
 *
 * @see com.fastrag.module.graph.service.GraphService
 */
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> getGraphData(String kbId, Integer maxNodes, Boolean excludeChunks) {
        log.info("[GraphQuery] ====== /graph called: kbId={}, maxNodes={}, excludeChunks={}",
                kbId, maxNodes, excludeChunks);
        long t1 = System.currentTimeMillis();
        // 可视化默认 500 节点（此前 100 对 KB 级图谱过小）；参数钳制 [1, 5000] 防御性上限
        int effectiveMaxNodes = Math.min(Math.max(maxNodes != null ? maxNodes : 500, 1), 5000);
        Map<String, Object> data = graphStore.getGraphData(kbId, effectiveMaxNodes,
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
        // 可视化过滤孤立点：零边实体在画布上只是噪声（占比可达 25%+），不渲染。
        // 非破坏性——孤立实体保留在库内，实体搜索/embedding 检索不受影响；
        // 实体总数口径仍以 /graph/stats 为准
        filterOrphanNodes(data);
        return data;
    }

    /**
     * 过滤图数据中的孤立节点（包级私有静态，可单测）。
     *
     * <p>只保留至少出现在一条边中的节点。端点匹配优先用边的 source_id/target_id
     * （实体确定性 ID），缺失时回退 source/target 名称（兼容存量边）。</p>
     */
    static void filterOrphanNodes(Map<String, Object> data) {
        List<?> nodes = (List<?>) data.getOrDefault("nodes", List.of());
        List<?> edges = (List<?>) data.getOrDefault("edges", List.of());
        if (nodes.isEmpty() || edges.isEmpty()) {
            if (edges.isEmpty()) data.put("nodes", new ArrayList<>());
            return;
        }
        Set<String> connectedIds = new HashSet<>();
        Set<String> connectedNames = new HashSet<>();
        for (Object edgeObj : edges) {
            if (!(edgeObj instanceof Map<?, ?> e)) continue;
            addEndpoint(connectedIds, connectedNames, e.get("source_id"), e.get("source"));
            addEndpoint(connectedIds, connectedNames, e.get("target_id"), e.get("target"));
        }
        List<Object> kept = new ArrayList<>(nodes.size());
        int removed = 0;
        for (Object nodeObj : nodes) {
            if (!(nodeObj instanceof Map<?, ?> n)) continue;
            String id = str(n.get("id"));
            String name = str(n.get("name"));
            if ((id != null && connectedIds.contains(id)) || (name != null && connectedNames.contains(name))) {
                kept.add(nodeObj);
            } else {
                removed++;
            }
        }
        data.put("nodes", kept);
        if (removed > 0) {
            log.info("[GraphQuery] Filtered {} orphan nodes (no edges), kept {} of {}",
                    removed, kept.size(), nodes.size());
        }
    }

    /** 边端点同时登记 ID 与名称（任一维度匹配即视为连通，兼容存量无 source_id 的边） */
    private static void addEndpoint(Set<String> ids, Set<String> names, Object id, Object name) {
        String idStr = str(id);
        String nameStr = str(name);
        if (idStr != null) ids.add(idStr);
        if (nameStr != null) names.add(nameStr);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
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
        // 实体类型分布：返回 [{name, count}] 数组，前端直接展示计数
        Map<String, Long> typeCounts = graphStore.countEntitiesByType(kbId);
        List<Map<String, Object>> entityTypes = new ArrayList<>();
        typeCounts.forEach((name, cnt) -> {
            Map<String, Object> t = new HashMap<>();
            t.put("name", name);
            t.put("count", cnt);
            entityTypes.add(t);
        });
        r.put("entityTypes", entityTypes);

        log.info("[GraphQuery] /graph/stats result: entityCount={}, relationCount={}, entityTypes={} (fromIndex={})",
                entityCount, relationCount, entityTypes, fromIndex);
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

        // 2. 重置所有 chunk 的 graphIndexed 标记，使 full 模式构建能重新处理所有 chunk
        try {
            int resetCount = jdbcTemplate.update(
                    "UPDATE kb_chunk SET graph_indexed = 0 WHERE kb_id = ?", kbId);
            log.info("[Graph Retry] Reset graphIndexed for {} chunks in kb: {}", resetCount, kbId);
        } catch (Exception e) {
            log.warn("[Graph Retry] Failed to reset graphIndexed for kb {}: {}", kbId, e.getMessage());
        }

        // 3. 重置索引状态
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

        // 4. 以 full 模式重新触发构建
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
        r.put("maxNodes", 500);
        r.put("searchDepth", 2);
        r.put("excludeChunkNodes", true);
        // KG-07：KB 级实体类型 schema（逗号/换行分隔；空则回退全局白名单）
        r.put("entitySchema", "");
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
        // 重置该文件 chunks 的 graph_indexed，使后续增量构建不会跳过它们
        jdbcTemplate.update(
                "UPDATE kb_chunk SET graph_indexed = 0 WHERE kb_id = ? AND file_id = ?", kbId, fileId);
        log.info("Deleted file graph data and reset graph_indexed: kb={}, file={}", kbId, fileId);
        // 同步 KbGraphIndex 缓存：删除文件后计数/状态仍展示旧值（stats/build-status 直接读缓存）
        refreshIndexAfterDelete(kbId);
        // 记录删除图谱日志（覆盖 FileServiceImpl 内部调用场景）
        try {
            logService.addLog(kbId, LogCategory.operation, ActionType.graph_deleted,
                    fileId, "删除文件关联图谱数据", "system", "success", null);
        } catch (Exception e) {
            log.warn("[Log] Failed to record graph delete log for kb={}, file={}", kbId, fileId);
        }
    }

    /**
     * 文件删除后刷新 KbGraphIndex 缓存计数：
     * 图谱已清空时重置为 idle（否则 stats/build-status 仍返回删除前的旧值）；
     * 仍有残留实体时仅同步计数与 chunk 口径。
     */
    private void refreshIndexAfterDelete(String kbId) {
        try {
            var idx = indexMapper.selectById(kbId);
            if (idx == null) return;

            long entityCount = graphStore.countEntities(kbId);
            long relationCount = graphStore.countRelations(kbId);
            if (entityCount == 0 && relationCount == 0) {
                idx.setStatus("idle");
                idx.setBuildProgress(0);
                idx.setEntityCount(0);
                idx.setRelationCount(0);
                idx.setTotalChunks(0);
                idx.setBuiltChunks(0);
                idx.setFailedChunks(0);
                idx.setBuildError(null);
                idx.setLastBuiltAt(null);
            } else {
                idx.setEntityCount((int) entityCount);
                idx.setRelationCount((int) relationCount);
                // 同步剩余 chunk 口径（kb_chunk 中未删除的）
                Long total = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM kb_chunk WHERE kb_id = ?", Long.class, kbId);
                Long built = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM kb_chunk WHERE kb_id = ? AND graph_indexed = 1", Long.class, kbId);
                idx.setTotalChunks(total != null ? total.intValue() : 0);
                idx.setBuiltChunks(built != null ? built.intValue() : 0);
            }
            indexMapper.updateById(idx);
            log.info("[Graph Delete] Refreshed KbGraphIndex for kb={}: entityCount={}, relationCount={}, status={}",
                    kbId, entityCount, relationCount, idx.getStatus());
        } catch (Exception e) {
            log.warn("[Graph Delete] Failed to refresh KbGraphIndex for kb={}: {}", kbId, e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> rankChunksByPpr(String kbId, List<String> entityNames, int topK) {
        return graphQueryService.rankChunksByPpr(kbId, entityNames, topK);
    }

    // ==================== 同义实体合并 ====================

    @Override
    public List<Map<String, Object>> findMergeCandidates(String kbId, double threshold, int limit) {
        log.info("[GraphMerge] findMergeCandidates: kb={}, threshold={}, limit={}", kbId, threshold, limit);
        return graphStore.findMergeCandidates(kbId, threshold, limit);
    }

    @Override
    public void mergeEntities(String kbId, String sourceId, String targetId) {
        log.info("[GraphMerge] mergeEntities: kb={}, source={} -> target={}", kbId, sourceId, targetId);
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
            throw new IllegalArgumentException("sourceId/targetId 不能为空且不能相同");
        }
        graphStore.mergeEntity(kbId, sourceId, targetId);
        // 合并后实体/关系数变化，刷新索引计数（失败不影响合并结果）
        try {
            int entities = (int) graphStore.countEntities(kbId);
            int relations = (int) graphStore.countRelations(kbId);
            var idx = indexMapper.selectById(kbId);
            if (idx != null) {
                idx.setEntityCount(entities);
                idx.setRelationCount(relations);
                indexMapper.updateById(idx);
            }
        } catch (Exception e) {
            log.warn("[GraphMerge] Failed to refresh index counts: {}", e.getMessage());
        }
    }
}
