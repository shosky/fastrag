package com.fastrag.infra.graph;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MySQL 降级图存储实现（参考 Yuxi 三存储架构）
 *
 * <p>当 Neo4j 未启用或不可用时，实体和关系数据存储在 MySQL 的
 * kb_graph_entity / kb_graph_relation 表中。查询走 SQL，适合中小规模图谱。
 * 支持 mention 追踪和确定性 ID 哈希。
 *
 * <p>仅在不存在其他 GraphStore bean 时创建（如 Neo4jGraphStore 未启用时）。
 */
@Repository
@ConditionalOnMissingBean(GraphStore.class)
@RequiredArgsConstructor
public class MysqlGraphStore implements GraphStore {

    private static final Logger log = LoggerFactory.getLogger(MysqlGraphStore.class);

    private final JdbcTemplate jdbcTemplate;

    // ==================== Entity ====================

    @Override
    public void createEntity(String kbId, String entityId, String name, String normalizedName, String type) {
        if (name == null || name.isBlank()) {
            log.debug("[GraphStore] Skipping entity creation: name is null/blank");
            return;
        }
        String normalized = normalizedName != null ? normalizedName : name.trim().toLowerCase();
        String entityType = type != null ? type : "UNKNOWN";
        // INSERT IGNORE — 同一 kbId + normalized_name + entity_type 只保留一条
        int affected = jdbcTemplate.update(
                "INSERT IGNORE INTO kb_graph_entity (entity_id, kb_id, name, normalized_name, entity_type) VALUES (?, ?, ?, ?, ?)",
                entityId, kbId, name.trim(), normalized, entityType
        );
        if (affected == 0) {
            log.debug("[GraphStore] Entity already exists (INSERT IGNORE): name={}, type={}, kbId={}", name, entityType, kbId);
        }
    }

    @Override
    public void createRelation(String kbId, String tripleId, String source, String target, String label, String content) {
        if (source == null || target == null || source.isBlank() || target.isBlank()) {
            log.debug("[GraphStore] Skipping relation creation: source or target is null/blank, source={}, target={}", source, target);
            return;
        }
        String relLabel = label != null ? label : "RELATED";
        String relContent = content != null ? content : (source.trim() + " -> " + relLabel + " -> " + target.trim());
        // INSERT IGNORE — 同一 kbId + source + target + label 只保留一条
        int affected = jdbcTemplate.update(
                "INSERT IGNORE INTO kb_graph_relation (triple_id, kb_id, source, target, label, content) VALUES (?, ?, ?, ?, ?, ?)",
                tripleId, kbId, source.trim(), target.trim(), relLabel, relContent
        );
        if (affected == 0) {
            log.debug("[GraphStore] Relation already exists (INSERT IGNORE): source={}, target={}, label={}, kbId={}",
                    source, target, relLabel, kbId);
        }
    }

    // ==================== 图谱查询 ====================

    @Override
    public Map<String, Object> getGraphData(String kbId, int maxNodes, boolean excludeChunks) {
        // 查询实体节点（限制数量）
        List<Map<String, Object>> entities = jdbcTemplate.queryForList(
                "SELECT entity_id, name, normalized_name, entity_type, description FROM kb_graph_entity WHERE kb_id = ? LIMIT ?",
                kbId, maxNodes
        );

        // 收集实体 ID 集合
        Set<String> entityIds = entities.stream()
                .map(e -> String.valueOf(e.get("entity_id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (entityIds.isEmpty()) {
            return result("nodes", List.of(), "edges", List.of());
        }

        // 查询关系（只包含已知实体的）
        String placeholders = entityIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> relations = jdbcTemplate.queryForList(
                "SELECT triple_id, source, target, label, content FROM kb_graph_relation " +
                "WHERE kb_id = ? AND source IN (" + placeholders + ") AND target IN (" + placeholders + ") " +
                "LIMIT ?",
                buildEntityRelationParams(kbId, entityIds, maxNodes)
        );

        // 格式化输出
        List<Map<String, Object>> nodes = entities.stream().map(e -> {
            Map<String, Object> node = new HashMap<>();
            node.put("id", e.get("entity_id"));
            node.put("name", e.get("name"));
            node.put("entity_type", e.get("entity_type"));
            node.put("description", e.get("description"));
            return node;
        }).collect(Collectors.toList());

        List<Map<String, Object>> edges = relations.stream().map(r -> {
            Map<String, Object> edge = new HashMap<>();
            edge.put("id", r.get("triple_id"));
            edge.put("source", r.get("source"));
            edge.put("target", r.get("target"));
            edge.put("label", r.get("label"));
            return edge;
        }).collect(Collectors.toList());

        // 如果排除 chunk 节点，过滤掉 type=chunk 的实体
        if (excludeChunks) {
            nodes = nodes.stream()
                    .filter(n -> !"chunk".equalsIgnoreCase(String.valueOf(n.get("entity_type"))))
                    .collect(Collectors.toList());
        }

        return result("nodes", nodes, "edges", edges);
    }

    @Override
    public Map<String, Object> expandGraph(String kbId, List<String> entities, int depth, int maxEntities) {
        if (entities == null || entities.isEmpty()) {
            return result("entities", List.of(), "relations", List.of(), "expandedQuery", "");
        }

        // 1. 匹配输入实体到图谱中的实体（按 name 或 normalized_name）
        String entityPlaceholders = entities.stream().map(e -> "?").collect(Collectors.joining(","));
        List<Object> matchParams = new ArrayList<>();
        matchParams.add(kbId);
        entities.forEach(e -> matchParams.add(e.trim()));
        matchParams.addAll(entities.stream().map(String::trim).collect(Collectors.toList()));
        matchParams.add(maxEntities);

        List<Map<String, Object>> matchedEntities = jdbcTemplate.queryForList(
                "SELECT entity_id, name, normalized_name, entity_type FROM kb_graph_entity " +
                "WHERE kb_id = ? AND (name IN (" + entityPlaceholders + ") OR normalized_name IN (" + entityPlaceholders + ")) " +
                "LIMIT ?",
                matchParams.toArray()
        );

        Set<String> matchedIds = matchedEntities.stream()
                .map(e -> String.valueOf(e.get("entity_id")))
                .collect(Collectors.toSet());

        List<Map<String, Object>> allEntities = new ArrayList<>(matchedEntities);
        List<Map<String, Object>> allRelations = new ArrayList<>();

        // 2. 展开邻居（depth 跳）
        if (depth >= 1 && !matchedIds.isEmpty()) {
            String idPlaceholders = matchedIds.stream().map(id -> "?").collect(Collectors.joining(","));
            List<Map<String, Object>> relations = jdbcTemplate.queryForList(
                    "SELECT triple_id, source, target, label FROM kb_graph_relation " +
                    "WHERE kb_id = ? AND (source IN (SELECT name FROM kb_graph_entity WHERE entity_id IN (" + idPlaceholders + ")) " +
                    "OR target IN (SELECT name FROM kb_graph_entity WHERE entity_id IN (" + idPlaceholders + "))) " +
                    "LIMIT ?",
                    buildNeighborParams(kbId, matchedIds, maxEntities)
            );

            // 收集邻居实体名称
            Set<String> neighborNames = new HashSet<>();
            for (Map<String, Object> rel : relations) {
                String source = String.valueOf(rel.get("source"));
                String target = String.valueOf(rel.get("target"));
                if (!matchedEntities.stream().anyMatch(e -> source.equals(e.get("name")) || source.equals(e.get("normalized_name")))) {
                    neighborNames.add(source);
                }
                if (!matchedEntities.stream().anyMatch(e -> target.equals(e.get("name")) || target.equals(e.get("normalized_name")))) {
                    neighborNames.add(target);
                }
            }

            allRelations.addAll(relations);

            // 查邻居实体详情
            if (!neighborNames.isEmpty()) {
                String namePlaceholders = neighborNames.stream().map(n -> "?").collect(Collectors.joining(","));
                List<Object> neighborParams = new ArrayList<>();
                neighborParams.add(kbId);
                neighborParams.addAll(neighborNames);
                neighborParams.addAll(neighborNames);
                neighborParams.add(maxEntities);

                List<Map<String, Object>> neighborEntities = jdbcTemplate.queryForList(
                        "SELECT entity_id, name, normalized_name, entity_type FROM kb_graph_entity " +
                        "WHERE kb_id = ? AND (name IN (" + namePlaceholders + ") OR normalized_name IN (" + namePlaceholders + ")) " +
                        "LIMIT ?",
                        neighborParams.toArray()
                );
                allEntities.addAll(neighborEntities);
            }
        }

        // 3. 构建扩写 query
        String expandedQuery = allEntities.stream()
                .map(e -> String.valueOf(e.get("name")))
                .distinct()
                .collect(Collectors.joining(" "));

        return result("entities", allEntities, "relations", allRelations, "expandedQuery", expandedQuery);
    }

    @Override
    public Map<String, Object> searchNodes(String kbId, String keyword, int maxNodes) {
        if (keyword == null || keyword.isBlank()) {
            return result("nodes", List.of(), "edges", List.of());
        }

        String pattern = "%" + keyword.trim().toLowerCase() + "%";

        // 多字段模糊匹配：name, normalized_name, entity_type, description
        List<Map<String, Object>> entities = jdbcTemplate.queryForList(
                "SELECT entity_id, name, normalized_name, entity_type, description FROM kb_graph_entity " +
                "WHERE kb_id = ? AND (LOWER(name) LIKE ? OR LOWER(normalized_name) LIKE ? " +
                "OR LOWER(entity_type) LIKE ? OR LOWER(description) LIKE ?) LIMIT ?",
                kbId, pattern, pattern, pattern, pattern, maxNodes
        );

        // 收集实体名称集合用于查关系
        Set<String> entityNames = entities.stream()
                .map(e -> String.valueOf(e.get("name")))
                .collect(Collectors.toSet());

        List<Map<String, Object>> nodes = entities.stream().map(e -> {
            Map<String, Object> node = new HashMap<>();
            node.put("id", e.get("entity_id"));
            node.put("name", e.get("name"));
            node.put("entity_type", e.get("entity_type"));
            return node;
        }).collect(Collectors.toList());

        List<Map<String, Object>> edges = List.of();
        if (!entityNames.isEmpty()) {
            String placeholders = entityNames.stream().map(n -> "?").collect(Collectors.joining(","));
            List<Object> edgeParams = new ArrayList<>();
            edgeParams.add(kbId);
            edgeParams.addAll(entityNames);
            edgeParams.addAll(entityNames);
            edgeParams.add(maxNodes * 2);

            edges = jdbcTemplate.queryForList(
                    "SELECT triple_id, source, target, label FROM kb_graph_relation " +
                    "WHERE kb_id = ? AND source IN (" + placeholders + ") AND target IN (" + placeholders + ") LIMIT ?",
                    edgeParams.toArray()
            );
            edges = edges.stream().map(r -> {
                Map<String, Object> edge = new HashMap<>();
                edge.put("id", r.get("triple_id"));
                edge.put("source", r.get("source"));
                edge.put("target", r.get("target"));
                edge.put("label", r.get("label"));
                return edge;
            }).collect(Collectors.toList());
        }

        return result("nodes", nodes, "edges", edges);
    }

    @Override
    public List<String> getLabels(String kbId) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT entity_type FROM kb_graph_entity WHERE kb_id = ? AND entity_type IS NOT NULL ORDER BY entity_type",
                String.class, kbId
        );
    }

    // ==================== Mention 追踪 ====================

    @Override
    public void createChunk(String kbId, String chunkId, String content) {
        // MySQL fallback 中不创建独立的 Chunk 节点
        // mention 记录在 kb_graph_entity_mention / kb_graph_triple_mention 中
        log.debug("MySQL store: skip createChunk (mention tracked in tables)");
    }

    @Override
    public void createEntityMention(String kbId, String entityId, String chunkId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO kb_graph_entity_mention (entity_id, kb_id, chunk_id) VALUES (?, ?, ?)",
                entityId, kbId, chunkId
        );
    }

    @Override
    public void createTripleMention(String kbId, String tripleId, String chunkId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO kb_graph_triple_mention (triple_id, kb_id, chunk_id) VALUES (?, ?, ?)",
                tripleId, kbId, chunkId
        );
    }

    // ==================== 图谱管理 ====================

    @Override
    public void clearGraph(String kbId) {
        jdbcTemplate.update("DELETE FROM kb_graph_entity_mention WHERE kb_id = ?", kbId);
        jdbcTemplate.update("DELETE FROM kb_graph_triple_mention WHERE kb_id = ?", kbId);
        jdbcTemplate.update("DELETE FROM kb_graph_entity WHERE kb_id = ?", kbId);
        jdbcTemplate.update("DELETE FROM kb_graph_relation WHERE kb_id = ?", kbId);
    }

    @Override
    public void deleteFileGraph(String kbId, String fileId) {
        // 1. 找到该文件的所有 chunk
        List<String> chunkIds = jdbcTemplate.queryForList(
                "SELECT id FROM kb_chunk WHERE kb_id = ? AND file_id = ?",
                String.class, kbId, fileId
        );

        if (chunkIds.isEmpty()) {
            log.debug("No chunks found for file deletion: kb={}, file={}", kbId, fileId);
            return;
        }

        // 2. 删除 mention 记录
        String chunkPlaceholders = chunkIds.stream().map(c -> "?").collect(Collectors.joining(","));
        Object[] mentionParams = buildFileMentionParams(kbId, chunkIds);

        jdbcTemplate.update("DELETE FROM kb_graph_entity_mention WHERE kb_id = ? AND chunk_id IN (" + chunkPlaceholders + ")",
                mentionParams);
        jdbcTemplate.update("DELETE FROM kb_graph_triple_mention WHERE kb_id = ? AND chunk_id IN (" + chunkPlaceholders + ")",
                mentionParams);

        // 3. 回收没有 mention 的孤立实体
        jdbcTemplate.update(
                "DELETE FROM kb_graph_entity WHERE kb_id = ? AND entity_id NOT IN " +
                "(SELECT DISTINCT entity_id FROM kb_graph_entity_mention WHERE kb_id = ?)",
                kbId, kbId
        );

        // 4. 回收没有 mention 的孤立关系
        jdbcTemplate.update(
                "DELETE FROM kb_graph_relation WHERE kb_id = ? AND triple_id NOT IN " +
                "(SELECT DISTINCT triple_id FROM kb_graph_triple_mention WHERE kb_id = ?)",
                kbId, kbId
        );

        log.info("Deleted file graph data: kb={}, file={}, chunks={}", kbId, fileId, chunkIds.size());
    }

    // ==================== SQL 参数构建辅助方法 ====================

    private Map<String, Object> result(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /**
     * getGraphData 的关系查询参数：kbId + entityIds + entityIds + maxNodes
     */
    private Object[] buildEntityRelationParams(String kbId, Set<String> entityIds, int maxNodes) {
        List<Object> params = new ArrayList<>();
        params.add(kbId);
        params.addAll(entityIds);
        params.addAll(entityIds);
        params.add(maxNodes);
        return params.toArray();
    }

    /**
     * expandGraph 邻居关系参数：kbId + matchedIds + matchedIds + maxEntities
     */
    private Object[] buildNeighborParams(String kbId, Set<String> matchedIds, int maxEntities) {
        List<Object> params = new ArrayList<>();
        params.add(kbId);
        params.addAll(matchedIds);
        params.addAll(matchedIds);
        params.add(maxEntities);
        return params.toArray();
    }

    /**
     * deleteFileGraph mention 参数：kbId + chunkIds
     */
    private Object[] buildFileMentionParams(String kbId, List<String> chunkIds) {
        List<Object> params = new ArrayList<>();
        params.add(kbId);
        params.addAll(chunkIds);
        return params.toArray();
    }

    // ==================== 统计查询 ====================

    @Override
    public long countEntities(String kbId) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM kb_graph_entity WHERE kb_id = ?", Long.class, kbId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("[GraphStore] countEntities failed: kb={}, error={}", kbId, e.getMessage());
            return 0;
        }
    }

    @Override
    public long countRelations(String kbId) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM kb_graph_relation WHERE kb_id = ?", Long.class, kbId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("[GraphStore] countRelations failed: kb={}, error={}", kbId, e.getMessage());
            return 0;
        }
    }
}
