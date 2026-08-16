package com.fastrag.infra.graph;

/**
 * 基于关系型数据库的知识图谱存储实现，作为 Neo4j 不可用时的降级方案（参考 Yuxi 三存储架构）。
 *
 * <p>核心职责：将知识图谱的实体和关系数据存储在 MySQL 的以下表中：
 * <ul>
 *   <li>{@code kb_graph_entity} — 存储实体节点（entity_id, kb_id, name, normalized_name, entity_type, description）</li>
 *   <li>{@code kb_graph_relation} — 存储实体间关系（triple_id, kb_id, source, target, label, content）</li>
 *   <li>{@code kb_graph_entity_mention} — 实体与 Chunk 的关联追踪（entity_id, kb_id, file_id, chunk_id）</li>
 *   <li>{@code kb_graph_triple_mention} — 三元组与 Chunk 的关联追踪（triple_id, kb_id, file_id, chunk_id）</li>
 * </ul>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>使用 {@code INSERT IGNORE} 语句实现实体和关系的去重，基于 (kb_id + normalized_name + entity_type)
 *       或 (kb_id + source + target + label) 唯一约束</li>
 *   <li>图谱查询通过 SQL JOIN 实现，包括多跳邻居展开（expandGraph）、多字段模糊搜索（searchNodes）</li>
 *   <li>文件/Chunk 删除时通过 mention 表追踪实现精准回收：先删 mention，再通过 LEFT JOIN 找出孤立节点并删除</li>
 *   <li>依赖 Spring {@link JdbcTemplate} 执行原生 SQL，不使用 ORM 映射</li>
 * </ul>
 *
 * <p>条件装配：通过 {@code @ConditionalOnMissingBean(GraphStore.class)} 注解确保仅在
 * 没有 Neo4jGraphStore Bean 时才激活（即 Neo4j 未启用时的降级方案）。
 *
 * <p>与其他模块的交互：由上层知识库处理服务通过 {@link GraphStore} 接口调用，间接依赖 fastrag-common 模块的
 * 知识提取和图谱构建组件。
 */

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@ConditionalOnMissingBean(GraphStore.class)
@RequiredArgsConstructor
public class MysqlGraphStore implements GraphStore {

    private static final Logger log = LoggerFactory.getLogger(MysqlGraphStore.class);

    private final JdbcTemplate jdbcTemplate;

    // ==================== Entity ====================

    @Override
    public void createEntity(String kbId, String entityId, String name, String normalizedName, String type,
                             String description, String attributes) {
        if (name == null || name.isBlank()) {
            log.debug("[GraphStore] Skipping entity creation: name is null/blank");
            return;
        }
        String normalized = normalizedName != null ? normalizedName : normalizeName(name);
        String entityType = type != null ? type : "UNKNOWN";
        // 同一 kbId + normalized_name + entity_type 只保留一条；
        // 已存在的实体仅回填 description/attributes（保留首次出现的非空值，不覆盖）
        int affected = jdbcTemplate.update(
                "INSERT INTO kb_graph_entity (entity_id, kb_id, name, normalized_name, entity_type, description, attributes) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "description = IF(LENGTH(description) > 0, description, VALUES(description)), " +
                "attributes = IF(LENGTH(attributes) > 0, attributes, VALUES(attributes))",
                entityId, kbId, name.trim(), normalized, entityType, description, attributes
        );
        if (affected == 0) {
            log.debug("[GraphStore] Entity already exists (INSERT IGNORE): name={}, type={}, kbId={}", name, entityType, kbId);
        }
    }

    @Override
    public void createRelation(String kbId, String tripleId, String sourceId, String source,
                               String targetId, String target, String label, String content) {
        if (source == null || target == null || source.isBlank() || target.isBlank()) {
            log.debug("[GraphStore] Skipping relation creation: source or target is null/blank, source={}, target={}", source, target);
            return;
        }
        // 自环防护（KG-05）：规范化后同名或同实体 ID 即视为自环，跳过
        if (normalizeName(source).equals(normalizeName(target))
                || (sourceId != null && sourceId.equals(targetId))) {
            log.debug("[GraphStore] Skipping self-loop relation: {} -> {}, label={}", source, target, label);
            return;
        }
        String relLabel = label != null ? label : "RELATED";
        String relContent = content != null ? content : (source.trim() + " -> " + relLabel + " -> " + target.trim());
        // 未提供实体 ID 时按规范化名反查（兼容历史调用方/存量数据）
        String srcId = sourceId != null ? sourceId : lookupEntityId(kbId, source);
        String tgtId = targetId != null ? targetId : lookupEntityId(kbId, target);
        // INSERT IGNORE — 同一 kbId + source + target + label 只保留一条
        int affected = jdbcTemplate.update(
                "INSERT IGNORE INTO kb_graph_relation (triple_id, kb_id, source_id, source, target_id, target, label, content) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                tripleId, kbId, srcId, source.trim(), tgtId, target.trim(), relLabel, relContent
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
                "SELECT entity_id, name, normalized_name, entity_type, description, attributes FROM kb_graph_entity WHERE kb_id = ? LIMIT ?",
                kbId, maxNodes
        );

        // 收集实体 ID 集合与名称集合（旧数据边无 source_id/target_id 时按名称兜底匹配）
        Set<String> entityIds = new HashSet<>();
        Set<String> entityNames = new HashSet<>();
        for (Map<String, Object> e : entities) {
            if (e.get("entity_id") != null) entityIds.add(String.valueOf(e.get("entity_id")));
            if (e.get("name") != null) entityNames.add(String.valueOf(e.get("name")));
        }

        if (entityIds.isEmpty()) {
            return result("nodes", List.of(), "edges", List.of());
        }

        // 查询关系：优先按实体 ID 关联；历史数据（source_id/target_id 为空）按名称兜底。
        // 避免 maxNodes 截断后"边随可见节点集过滤而整体丢失"的问题
        String idPlaceholders = entityIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String namePlaceholders = entityNames.stream().map(n -> "?").collect(Collectors.joining(","));
        List<Object> relParams = new ArrayList<>();
        relParams.add(kbId);
        relParams.addAll(entityIds);
        relParams.addAll(entityNames);
        relParams.addAll(entityIds);
        relParams.addAll(entityNames);
        relParams.add(maxNodes);

        List<Map<String, Object>> relations = jdbcTemplate.queryForList(
                "SELECT triple_id, source_id, target_id, source, target, label, content FROM kb_graph_relation r " +
                "WHERE r.kb_id = ? " +
                "  AND ((r.source_id IS NOT NULL AND r.source_id IN (" + idPlaceholders + ")) " +
                "       OR (r.source_id IS NULL AND r.source IN (" + namePlaceholders + "))) " +
                "  AND ((r.target_id IS NOT NULL AND r.target_id IN (" + idPlaceholders + ")) " +
                "       OR (r.target_id IS NULL AND r.target IN (" + namePlaceholders + "))) " +
                "LIMIT ?",
                relParams.toArray()
        );

        // 格式化输出
        List<Map<String, Object>> nodes = entities.stream().map(e -> {
            Map<String, Object> node = new HashMap<>();
            node.put("id", e.get("entity_id"));
            node.put("name", e.get("name"));
            node.put("entity_type", e.get("entity_type"));
            node.put("description", e.get("description"));
            node.put("attributes", e.get("attributes"));
            return node;
        }).collect(Collectors.toList());

        List<Map<String, Object>> edges = relations.stream().map(r -> {
            Map<String, Object> edge = new HashMap<>();
            edge.put("id", r.get("triple_id"));
            edge.put("source_id", r.get("source_id"));
            edge.put("target_id", r.get("target_id"));
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
                .filter(id -> !"null".equals(id))
                .collect(Collectors.toSet());

        // 匹配实体的名称集合（旧数据边按名称兜底匹配）
        Set<String> matchedNames = matchedEntities.stream()
                .map(e -> String.valueOf(e.get("name")))
                .collect(Collectors.toSet());

        List<Map<String, Object>> allEntities = new ArrayList<>(matchedEntities);
        List<Map<String, Object>> allRelations = new ArrayList<>();

        // 2. 展开邻居（depth 跳）：优先按实体 ID 关联，历史数据按名称兜底
        if (depth >= 1 && !matchedIds.isEmpty()) {
            String idPlaceholders = matchedIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String namePlaceholders = matchedNames.stream().map(n -> "?").collect(Collectors.joining(","));
            List<Object> relParams = new ArrayList<>();
            relParams.add(kbId);
            relParams.addAll(matchedIds);
            relParams.addAll(matchedNames);
            relParams.addAll(matchedIds);
            relParams.addAll(matchedNames);
            relParams.add(maxEntities);

            List<Map<String, Object>> relations = jdbcTemplate.queryForList(
                    "SELECT triple_id, source_id, target_id, source, target, label FROM kb_graph_relation r " +
                    "WHERE r.kb_id = ? " +
                    "  AND ( " +
                    "    ((r.source_id IS NOT NULL AND r.source_id IN (" + idPlaceholders + ")) " +
                    "     OR (r.source_id IS NULL AND r.source IN (" + namePlaceholders + "))) " +
                    "    OR " +
                    "    ((r.target_id IS NOT NULL AND r.target_id IN (" + idPlaceholders + ")) " +
                    "     OR (r.target_id IS NULL AND r.target IN (" + namePlaceholders + "))) " +
                    "  ) " +
                    "LIMIT ?",
                    relParams.toArray()
            );

            // 收集邻居（优先按实体 ID，缺失时按名称）
            Set<String> neighborIds = new HashSet<>();
            Set<String> neighborNames = new HashSet<>();
            for (Map<String, Object> rel : relations) {
                String sourceId = rel.get("source_id") != null ? String.valueOf(rel.get("source_id")) : null;
                String targetId = rel.get("target_id") != null ? String.valueOf(rel.get("target_id")) : null;
                if (sourceId != null && !sourceId.isEmpty() && !matchedIds.contains(sourceId)) {
                    neighborIds.add(sourceId);
                } else if (sourceId == null && !matchedNames.contains(rel.get("source"))) {
                    neighborNames.add(String.valueOf(rel.get("source")));
                }
                if (targetId != null && !targetId.isEmpty() && !matchedIds.contains(targetId)) {
                    neighborIds.add(targetId);
                } else if (targetId == null && !matchedNames.contains(rel.get("target"))) {
                    neighborNames.add(String.valueOf(rel.get("target")));
                }
            }

            allRelations.addAll(relations);

            // 查邻居实体详情（按 ID 或名称匹配，二者取并集）
            if (!neighborIds.isEmpty() || !neighborNames.isEmpty()) {
                String idPh = neighborIds.stream().map(id -> "?").collect(Collectors.joining(","));
                String namePh = neighborNames.stream().map(n -> "?").collect(Collectors.joining(","));
                StringBuilder sql = new StringBuilder(
                        "SELECT entity_id, name, normalized_name, entity_type FROM kb_graph_entity " +
                        "WHERE kb_id = ?");
                List<Object> neighborParams = new ArrayList<>();
                neighborParams.add(kbId);
                List<String> conditions = new ArrayList<>();
                if (!neighborIds.isEmpty()) {
                    conditions.add("entity_id IN (" + idPh + ")");
                    neighborParams.addAll(neighborIds);
                }
                if (!neighborNames.isEmpty()) {
                    conditions.add("name IN (" + namePh + ")");
                    neighborParams.addAll(neighborNames);
                }
                sql.append(" AND (").append(String.join(" OR ", conditions)).append(")");
                sql.append(" LIMIT ?");
                neighborParams.add(maxEntities);

                List<Map<String, Object>> neighborEntities = jdbcTemplate.queryForList(
                        sql.toString(), neighborParams.toArray());
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
                "SELECT entity_id, name, normalized_name, entity_type, description, attributes FROM kb_graph_entity " +
                "WHERE kb_id = ? AND (LOWER(name) LIKE ? OR LOWER(normalized_name) LIKE ? " +
                "OR LOWER(entity_type) LIKE ? OR LOWER(description) LIKE ?) LIMIT ?",
                kbId, pattern, pattern, pattern, pattern, maxNodes
        );

        // 收集实体 ID/名称集合用于查关系（旧数据边按名称兜底）
        Set<String> entityIds = new HashSet<>();
        Set<String> entityNames = new HashSet<>();
        for (Map<String, Object> e : entities) {
            if (e.get("entity_id") != null) entityIds.add(String.valueOf(e.get("entity_id")));
            if (e.get("name") != null) entityNames.add(String.valueOf(e.get("name")));
        }

        List<Map<String, Object>> nodes = entities.stream().map(e -> {
            Map<String, Object> node = new HashMap<>();
            node.put("id", e.get("entity_id"));
            node.put("name", e.get("name"));
            node.put("entity_type", e.get("entity_type"));
            node.put("description", e.get("description"));
            node.put("attributes", e.get("attributes"));
            return node;
        }).collect(Collectors.toList());

        List<Map<String, Object>> edges = List.of();
        if (!entityIds.isEmpty()) {
            String idPh = entityIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String namePh = entityNames.stream().map(n -> "?").collect(Collectors.joining(","));
            List<Object> edgeParams = new ArrayList<>();
            edgeParams.add(kbId);
            edgeParams.addAll(entityIds);
            edgeParams.addAll(entityNames);
            edgeParams.addAll(entityIds);
            edgeParams.addAll(entityNames);
            edgeParams.add(maxNodes * 2);

            edges = jdbcTemplate.queryForList(
                    "SELECT triple_id, source_id, target_id, source, target, label FROM kb_graph_relation r " +
                    "WHERE r.kb_id = ? " +
                    "  AND ((r.source_id IS NOT NULL AND r.source_id IN (" + idPh + ")) " +
                    "       OR (r.source_id IS NULL AND r.source IN (" + namePh + "))) " +
                    "  AND ((r.target_id IS NOT NULL AND r.target_id IN (" + idPh + ")) " +
                    "       OR (r.target_id IS NULL AND r.target IN (" + namePh + "))) " +
                    "LIMIT ?",
                    edgeParams.toArray()
            );
            edges = edges.stream().map(r -> {
                Map<String, Object> edge = new HashMap<>();
                edge.put("id", r.get("triple_id"));
                edge.put("source_id", r.get("source_id"));
                edge.put("target_id", r.get("target_id"));
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
    public void createChunk(String kbId, String chunkId, String fileId, String content) {
        // MySQL 模式同样落 chunk 节点（entity_type='chunk'），支持前端 excludeChunks 开关展示，
        // 对齐 Yuxi 的 Chunk 节点 + exclude_chunk 参数
        if (chunkId == null || chunkId.isBlank()) return;
        String preview = content != null && content.length() > 200 ? content.substring(0, 200) + "…" : content;
        try {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO kb_graph_entity (entity_id, kb_id, name, normalized_name, entity_type, description) " +
                    "VALUES (?, ?, ?, ?, 'chunk', ?)",
                    chunkId, kbId, chunkId, normalizeName(chunkId), preview
            );
        } catch (Exception e) {
            log.warn("[GraphStore] createChunk failed: kb={}, chunk={}, error={}", kbId, chunkId, e.getMessage());
        }
    }

    @Override
    public void createEntityMention(String kbId, String entityName, String entityId, String chunkId, String fileId) {
        // entity_id 写入确定性哈希 ID（与 kb_graph_entity.entity_id 一致），
        // file_id 一并写入，供 deleteFileGraph 按文件精确回收
        jdbcTemplate.update(
                "INSERT IGNORE INTO kb_graph_entity_mention (entity_id, kb_id, file_id, chunk_id) VALUES (?, ?, ?, ?)",
                entityId != null ? entityId : entityName, kbId, fileId, chunkId
        );
    }

    @Override
    public void createTripleMention(String kbId, String tripleId, String chunkId, String fileId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO kb_graph_triple_mention (triple_id, kb_id, file_id, chunk_id) VALUES (?, ?, ?, ?)",
                tripleId, kbId, fileId, chunkId
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
        // 1. 按 file_id 直接删除 mention 记录（不依赖 kb_chunk 表，解决 chunk 先被删导致查不到的问题）
        jdbcTemplate.update(
                "DELETE FROM kb_graph_entity_mention WHERE kb_id = ? AND file_id = ?",
                kbId, fileId
        );
        jdbcTemplate.update(
                "DELETE FROM kb_graph_triple_mention WHERE kb_id = ? AND file_id = ?",
                kbId, fileId
        );

        // 2. 安全网：回收 chunk 已不存在的 mention（历史遗留 file_id 为空的记录，
        //    其 chunk 已随文件删除，需一并清掉，否则对应实体/关系永远不会被回收）
        jdbcTemplate.update(
                "DELETE m FROM kb_graph_entity_mention m " +
                "LEFT JOIN kb_chunk c ON c.id = m.chunk_id AND c.kb_id = m.kb_id " +
                "WHERE m.kb_id = ? AND c.id IS NULL",
                kbId
        );
        jdbcTemplate.update(
                "DELETE m FROM kb_graph_triple_mention m " +
                "LEFT JOIN kb_chunk c ON c.id = m.chunk_id AND c.kb_id = m.kb_id " +
                "WHERE m.kb_id = ? AND c.id IS NULL",
                kbId
        );

        // 3. 回收没有 mention 的孤立实体（chunk 节点不受 mention 约束，另行按 kb_chunk 回收）
        jdbcTemplate.update(
                "DELETE FROM kb_graph_entity WHERE kb_id = ? AND entity_type <> 'chunk' AND entity_id NOT IN " +
                "(SELECT DISTINCT entity_id FROM kb_graph_entity_mention WHERE kb_id = ?)",
                kbId, kbId
        );

        // 4. 回收没有 mention 的孤立关系
        jdbcTemplate.update(
                "DELETE FROM kb_graph_relation WHERE kb_id = ? AND triple_id NOT IN " +
                "(SELECT DISTINCT triple_id FROM kb_graph_triple_mention WHERE kb_id = ?)",
                kbId, kbId
        );

        // 5. 回收已不存在于 kb_chunk 的 chunk 节点
        jdbcTemplate.update(
                "DELETE FROM kb_graph_entity WHERE kb_id = ? AND entity_type = 'chunk' AND entity_id NOT IN " +
                "(SELECT DISTINCT id FROM kb_chunk WHERE kb_id = ?)",
                kbId, kbId
        );

        log.info("Deleted file graph data: kb={}, file={}", kbId, fileId);
    }

    @Override
    public void deleteChunkGraph(String kbId, String chunkId) {
        // 1. 删除该 chunk 的 mention 记录
        jdbcTemplate.update(
                "DELETE FROM kb_graph_entity_mention WHERE kb_id = ? AND chunk_id = ?",
                kbId, chunkId);
        jdbcTemplate.update(
                "DELETE FROM kb_graph_triple_mention WHERE kb_id = ? AND chunk_id = ?",
                kbId, chunkId);

        // 1.5 删除该 chunk 节点本身（entity_type='chunk'）
        jdbcTemplate.update(
                "DELETE FROM kb_graph_entity WHERE kb_id = ? AND entity_id = ? AND entity_type = 'chunk'",
                kbId, chunkId);

        // 2. 回收孤立实体（chunk 节点不受 mention 约束）
        jdbcTemplate.update(
                "DELETE FROM kb_graph_entity WHERE kb_id = ? AND entity_type <> 'chunk' AND entity_id NOT IN " +
                "(SELECT DISTINCT entity_id FROM kb_graph_entity_mention WHERE kb_id = ?)",
                kbId, kbId);

        // 3. 回收孤立关系
        jdbcTemplate.update(
                "DELETE FROM kb_graph_relation WHERE kb_id = ? AND triple_id NOT IN " +
                "(SELECT DISTINCT triple_id FROM kb_graph_triple_mention WHERE kb_id = ?)",
                kbId, kbId);

        log.info("Deleted chunk graph data: kb={}, chunk={}", kbId, chunkId);
    }

    @Override
    public void renameChunkId(String kbId, String oldChunkId, String newChunkId) {
        // MySQL fallback: mention 表唯一键为 (entity_id, chunk_id)，rename 需先删后插
        // 当前实现不做实际重命名（增量重建时 chunkId 不变，此场景较少见）
        log.debug("MySQL store: renameChunkId skipped (kb={}, {} -> {})", kbId, oldChunkId, newChunkId);
    }

    // ==================== SQL 参数构建辅助方法 ====================

    private Map<String, Object> result(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ==================== 统计查询 ====================

    @Override
    public long countEntities(String kbId) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM kb_graph_entity WHERE kb_id = ? AND entity_type <> 'chunk'", Long.class, kbId);
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

    @Override
    public Map<String, Long> countEntitiesByType(String kbId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT entity_type, COUNT(*) AS cnt FROM kb_graph_entity " +
                    "WHERE kb_id = ? AND entity_type <> 'chunk' GROUP BY entity_type ORDER BY cnt DESC",
                    kbId);
            Map<String, Long> result = new LinkedHashMap<>();
            for (Map<String, Object> row : rows) {
                Object type = row.get("entity_type");
                Object cnt = row.get("cnt");
                result.put(type != null ? String.valueOf(type) : "UNKNOWN",
                        cnt instanceof Number ? ((Number) cnt).longValue() : 0L);
            }
            return result;
        } catch (Exception e) {
            log.warn("[GraphStore] countEntitiesByType failed: kb={}, error={}", kbId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    public String findEntityId(String kbId, String normalizedName) {
        return lookupEntityId(kbId, normalizedName);
    }

    @Override
    public List<Map<String, String>> listEntityMentions(String kbId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT entity_id, chunk_id FROM kb_graph_entity_mention WHERE kb_id = ?", kbId);
            List<Map<String, String>> result = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                Map<String, String> m = new HashMap<>(2);
                m.put("entityId", row.get("entity_id") != null ? String.valueOf(row.get("entity_id")) : null);
                m.put("chunkId", row.get("chunk_id") != null ? String.valueOf(row.get("chunk_id")) : null);
                result.add(m);
            }
            return result;
        } catch (Exception e) {
            log.warn("[GraphStore] listEntityMentions failed: kb={}, error={}", kbId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 名称标准化（与 NameNormalizer 对齐：trim + 小写 + 合并空白）。
     * infra 层不依赖 graph-eval 模块，此处内联实现。
     */
    private static String normalizeName(String text) {
        if (text == null || text.isEmpty()) return "";
        return String.join(" ", text.trim().toLowerCase().split("\\s+"));
    }

    /**
     * 按规范化名称反查实体 ID（createRelation 未提供 sourceId/targetId 时兜底，
     * 兼容历史调用方；同名不同型实体取任意一条）
     */
    private String lookupEntityId(String kbId, String name) {
        try {
            List<String> ids = jdbcTemplate.queryForList(
                    "SELECT entity_id FROM kb_graph_entity WHERE kb_id = ? AND normalized_name = ? LIMIT 1",
                    String.class, kbId, normalizeName(name));
            return ids.isEmpty() ? null : ids.get(0);
        } catch (Exception e) {
            log.warn("[GraphStore] lookupEntityId failed: kb={}, name={}, error={}", kbId, name, e.getMessage());
            return null;
        }
    }
}
