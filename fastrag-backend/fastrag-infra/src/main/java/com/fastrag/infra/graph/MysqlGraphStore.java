package com.fastrag.infra.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MySQL 降级图存储实现
 *
 * <p>当 Neo4j 未启用或不可用时，实体和关系数据存储在 MySQL 的
 * kb_graph_entity / kb_graph_relation 表中。查询走 SQL，适合中小规模图谱。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MysqlGraphStore implements GraphStore {

    private final JdbcTemplate jdbcTemplate;

    // ==================== Entity ====================

    @Override
    public void createEntity(String kbId, String name, String type) {
        if (name == null || name.isBlank()) return;
        // INSERT IGNORE — 同一 kbId + name 只保留一条
        jdbcTemplate.update(
                "INSERT IGNORE INTO kb_graph_entity (kb_id, name, entity_type) VALUES (?, ?, ?)",
                kbId, name.trim(), type != null ? type : "UNKNOWN"
        );
    }

    @Override
    public void createRelation(String kbId, String source, String target, String label) {
        if (source == null || target == null || source.isBlank() || target.isBlank()) return;
        // INSERT IGNORE — 同一 kbId + source + target + label 只保留一条
        jdbcTemplate.update(
                "INSERT IGNORE INTO kb_graph_relation (kb_id, source, target, label) VALUES (?, ?, ?, ?)",
                kbId, source.trim(), target.trim(), label != null ? label : "RELATED"
        );
    }

    @Override
    public Map<String, Object> getGraphData(String kbId, int maxNodes, boolean excludeChunks) {
        // 查询实体节点（限制数量）
        List<Map<String, Object>> entities = jdbcTemplate.queryForList(
                "SELECT id, name, entity_type FROM kb_graph_entity WHERE kb_id = ? LIMIT ?",
                kbId, maxNodes
        );

        // 收集实体 ID 集合
        Set<String> entityIds = entities.stream()
                .map(e -> String.valueOf(e.get("id")))
                .collect(Collectors.toSet());

        if (entityIds.isEmpty()) {
            return Map.of("nodes", List.of(), "edges", List.of());
        }

        // 查询关系（只包含已知实体的）
        String placeholders = entityIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> relations = jdbcTemplate.queryForList(
                "SELECT id, source, target, label FROM kb_graph_relation " +
                "WHERE kb_id = ? AND source IN (" + placeholders + ") AND target IN (" + placeholders + ") " +
                "LIMIT ?",
                buildEntityRelationParams(kbId, entityIds, maxNodes)
        );

        // 如果排除 chunk 节点，过滤掉 type=chunk 的实体
        if (excludeChunks) {
            entities = entities.stream()
                    .filter(e -> !"chunk".equalsIgnoreCase(String.valueOf(e.get("entity_type"))))
                    .collect(Collectors.toList());
        }

        return Map.of("nodes", entities, "edges", relations);
    }

    @Override
    public Map<String, Object> expandGraph(String kbId, List<String> entities, int depth, int maxEntities) {
        if (entities == null || entities.isEmpty()) {
            return Map.of("entities", List.of(), "relations", List.of(), "expandedQuery", "");
        }

        // 1. 匹配输入实体到图谱中的实体
        String entityPlaceholders = entities.stream().map(e -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> matchedEntities = jdbcTemplate.queryForList(
                "SELECT id, name, entity_type FROM kb_graph_entity " +
                "WHERE kb_id = ? AND name IN (" + entityPlaceholders + ") " +
                "LIMIT ?",
                buildExpandParams(kbId, entities, maxEntities)
        );

        Set<String> matchedIds = matchedEntities.stream()
                .map(e -> String.valueOf(e.get("id")))
                .collect(Collectors.toSet());

        List<Map<String, Object>> allEntities = new ArrayList<>(matchedEntities);
        List<Map<String, Object>> allRelations = new ArrayList<>();

        // 2. 展开邻居（depth 跳）
        if (depth >= 1 && !matchedIds.isEmpty()) {
            String idPlaceholders = matchedIds.stream().map(id -> "?").collect(Collectors.joining(","));
            List<Map<String, Object>> relations = jdbcTemplate.queryForList(
                    "SELECT id, source, target, label FROM kb_graph_relation " +
                    "WHERE kb_id = ? AND (source IN (" + idPlaceholders + ") OR target IN (" + idPlaceholders + ")) " +
                    "LIMIT ?",
                    buildNeighborParams(kbId, matchedIds, maxEntities)
            );

            // 收集邻居实体 ID
            Set<String> neighborIds = new HashSet<>();
            for (Map<String, Object> rel : relations) {
                String source = String.valueOf(rel.get("source"));
                String target = String.valueOf(rel.get("target"));
                if (!matchedIds.contains(source)) neighborIds.add(source);
                if (!matchedIds.contains(target)) neighborIds.add(target);
            }

            allRelations.addAll(relations);

            // 查邻居实体详情
            if (!neighborIds.isEmpty()) {
                String neighborPlaceholders = neighborIds.stream().map(id -> "?").collect(Collectors.joining(","));
                List<Map<String, Object>> neighborEntities = jdbcTemplate.queryForList(
                        "SELECT id, name, entity_type FROM kb_graph_entity " +
                        "WHERE kb_id = ? AND id IN (" + neighborPlaceholders + ") " +
                        "LIMIT ?",
                        buildNeighborEntityParams(kbId, neighborIds, maxEntities)
                );
                allEntities.addAll(neighborEntities);
            }
        }

        // 3. 构建扩写 query
        String expandedQuery = allEntities.stream()
                .map(e -> String.valueOf(e.get("name")))
                .distinct()
                .collect(Collectors.joining(" "));

        return Map.of("entities", allEntities, "relations", allRelations, "expandedQuery", expandedQuery);
    }

    @Override
    public void clearGraph(String kbId) {
        jdbcTemplate.update("DELETE FROM kb_graph_entity WHERE kb_id = ?", kbId);
        jdbcTemplate.update("DELETE FROM kb_graph_relation WHERE kb_id = ?", kbId);
    }

    // ==================== SQL 参数构建辅助方法 ====================

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
     * expandGraph 实体匹配参数：kbId + entityNames + maxEntities
     */
    private Object[] buildExpandParams(String kbId, List<String> entities, int maxEntities) {
        List<Object> params = new ArrayList<>();
        params.add(kbId);
        entities.forEach(e -> params.add(e.trim()));
        params.add(maxEntities);
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
     * expandGraph 邻居实体详情参数：kbId + neighborIds + maxEntities
     */
    private Object[] buildNeighborEntityParams(String kbId, Set<String> neighborIds, int maxEntities) {
        List<Object> params = new ArrayList<>();
        params.add(kbId);
        params.addAll(neighborIds);
        params.add(maxEntities);
        return params.toArray();
    }
}
