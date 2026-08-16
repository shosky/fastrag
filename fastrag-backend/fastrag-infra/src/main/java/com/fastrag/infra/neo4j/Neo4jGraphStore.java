package com.fastrag.infra.neo4j;

/**
 * 基于 Neo4j 图数据库的知识图谱存储实现（参考 Yuxi CypherTemplates + 三存储架构）。
 *
 * <p>核心职责：使用 Neo4j 原生 Cypher 查询语言实现知识图谱的全部操作，包括实体/关系创建、
 * 多跳展开检索、关键词搜索、Mention 追踪和孤立节点回收等。
 *
 * <p>依赖的外部系统：Neo4j 图数据库，通过 Neo4j Java Driver 直连（不依赖 Spring Data Neo4j，保持轻量）。
 *
 * <p>图模型设计：
 * <ul>
 *   <li>节点标签：{@code Entity}（实体节点，属性包括 kbId/name/normalizedName/entityType/embedding）、
 *       {@code Chunk}（文档块节点，属性包括 kbId/chunkId/fileId/content）</li>
 *   <li>关系类型：{@code RELATION}（实体间关系，属性包括 kbId/label/tripleId/content）、
 *       {@code MENTIONS}（实体→Chunk 的关联，属性包括 kbId/createdAt）</li>
 *   <li>独立索引节点：{@code TripleMention}（三元组与 Chunk 的溯源关联，因为 Neo4j 中
 *       RELATION 是 relationship 类型不能有出边）</li>
 *   <li>命名空间隔离：所有节点和关系均携带 kbId 属性，实现多知识库的数据隔离</li>
 * </ul>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>实体和关系使用 MERGE 语句实现幂等创建，MERGE 键为 (kbId, name)，避免重复节点</li>
 *   <li>expandGraph 支持模糊匹配（CONTAINS）和 mentionCount 加权排序，支持 1-2 跳邻居展开</li>
 *   <li>实体向量检索：利用 Neo4j 5.11+ 的原生向量索引（entity_embedding），支持 COSINE 相似度；
 *       索引创建失败时静默降级为文本匹配</li>
 *   <li>文件/Chunk 删除时通过 DETACH DELETE 和 NOT EXISTS 子查询回收孤立节点</li>
 *   <li>renameChunkId 通过创建新节点→迁移关系→删除旧节点的方式处理 Neo4j 不支持修改 MATCH 键属性的限制</li>
 * </ul>
 *
 * <p>条件装配：通过 {@code @ConditionalOnProperty(prefix = "neo4j", name = "enabled", havingValue = "true")}
 * 注解控制，仅当 {@code neo4j.enabled=true} 时创建此 Bean。
 *
 * <p>与其他模块的交互：通过 {@link GraphStore} 接口被上层知识库处理服务调用；
 * 被 {@link Neo4jService} 委托调用以保持向后兼容。
 */

import com.fastrag.infra.graph.GraphStore;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@ConditionalOnProperty(prefix = "neo4j", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class Neo4jGraphStore implements GraphStore {

    private static final Logger log = LoggerFactory.getLogger(Neo4jGraphStore.class);

    private final Driver neo4jDriver;

    @Value("${neo4j.enabled:false}")
    private boolean enabled;

    /** 构建查询参数 Map */
    private Map<String, Object> p(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** 将 key-value 对放入结果 Map */
    private Map<String, Object> result(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ==================== Entity ====================

    @Override
    public void createEntity(String kbId, String entityId, String name, String normalizedName, String type,
                             String description, String attributes) {
        if (name == null || name.isBlank()) return;
        String normalized = normalizedName != null ? normalizedName : name.trim().toLowerCase();
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                // MERGE on (kbId, name) — 统一标识键，与 createRelation 端点一致
                // entityType 作为属性回填（而非 MERGE 键），避免同名实体因不同 type 产生分裂节点
                tx.run("MERGE (e:Entity {kbId: $kbId, name: $name}) " +
                        "ON CREATE SET e.entityId = $entityId, e.normalizedName = $normalizedName, " +
                        "e.entityType = $type, e.description = $description, e.attributes = $attributes, " +
                        "e.createdAt = datetime() " +
                        "ON MATCH SET e.normalizedName = coalesce(e.normalizedName, $normalizedName), " +
                        "e.entityId = coalesce(e.entityId, $entityId), " +
                        "e.entityType = coalesce(e.entityType, $type), " +
                        "e.description = coalesce(e.description, $description), " +
                        "e.attributes = coalesce(e.attributes, $attributes)",
                        p("kbId", kbId, "entityId", entityId, "name", name.trim(),
                                "normalizedName", normalized, "type", type != null ? type : "UNKNOWN",
                                "description", description, "attributes", attributes));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j createEntity failed: kb={}, name={}, error={}", kbId, name, e.getMessage());
        }
    }

    @Override
    public void createRelation(String kbId, String tripleId, String sourceId, String source,
                               String targetId, String target, String label, String content) {
        if (source == null || target == null || source.isBlank() || target.isBlank()) return;
        String relLabel = label != null ? label : "RELATED";
        String relContent = content != null ? content : (source.trim() + " -> " + relLabel + " -> " + target.trim());
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                // 端点实体 ID：调用方未提供时（跨 chunk 引用）从已存在节点回填（coalesce），
                // 确保边始终携带 sourceId/targetId（KG-01）
                tx.run("MERGE (s:Entity {kbId: $kbId, name: $source}) " +
                        "MERGE (t:Entity {kbId: $kbId, name: $target}) " +
                        "MERGE (s)-[r:RELATION {kbId: $kbId, label: $label}]->(t) " +
                        "ON CREATE SET r.tripleId = $tripleId, " +
                        "r.sourceId = coalesce($sourceId, s.entityId), r.targetId = coalesce($targetId, t.entityId), " +
                        "r.content = $content, r.createdAt = datetime() " +
                        "ON MATCH SET r.tripleId = coalesce(r.tripleId, $tripleId), " +
                        "r.sourceId = coalesce(r.sourceId, $sourceId, s.entityId), " +
                        "r.targetId = coalesce(r.targetId, $targetId, t.entityId), " +
                        "r.content = coalesce(r.content, $content)",
                        p("kbId", kbId, "tripleId", tripleId, "sourceId", sourceId, "targetId", targetId,
                                "source", source.trim(), "target", target.trim(), "label", relLabel, "content", relContent));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j createRelation failed: kb={}, {}->{}, error={}", kbId, source, target, e.getMessage());
        }
    }

    @Override
    public String findEntityId(String kbId, String normalizedName) {
        if (!enabled || normalizedName == null || normalizedName.isBlank()) return null;
        try (Session session = neo4jDriver.session()) {
            return session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH (e:Entity {kbId: $kbId}) WHERE e.normalizedName = $name " +
                        "RETURN e.entityId AS id LIMIT 1",
                        p("kbId", kbId, "name", normalizedName));
                return r.hasNext() ? r.next().get("id", (String) null) : null;
            });
        } catch (Exception e) {
            log.warn("Neo4j findEntityId failed: kb={}, name={}, error={}", kbId, normalizedName, e.getMessage());
            return null;
        }
    }

    @Override
    public List<Map<String, String>> listEntityMentions(String kbId) {
        if (!enabled) return Collections.emptyList();
        try (Session session = neo4jDriver.session()) {
            return session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH (e:Entity {kbId: $kbId})-[:MENTIONS]->(c:Chunk {kbId: $kbId}) " +
                        "RETURN e.entityId AS entityId, c.chunkId AS chunkId",
                        p("kbId", kbId));
                List<Map<String, String>> list = new ArrayList<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    Map<String, String> m = new HashMap<>(2);
                    m.put("entityId", rec.get("entityId", (String) null));
                    m.put("chunkId", rec.get("chunkId", (String) null));
                    list.add(m);
                }
                return list;
            });
        } catch (Exception e) {
            log.warn("Neo4j listEntityMentions failed: kb={}, error={}", kbId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 图谱查询 ====================

    @Override
    public Map<String, Object> getGraphData(String kbId, int maxNodes, boolean excludeChunks) {
        log.info("[Neo4jQuery] getGraphData called: kbId={}, maxNodes={}, excludeChunks={}", kbId, maxNodes, excludeChunks);
        try (Session session = neo4jDriver.session()) {
            long t1 = System.currentTimeMillis();
            List<Map<String, Object>> nodes = session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH (e:Entity {kbId: $kbId}) " +
                        "RETURN e.entityId AS id, e.name AS name, e.normalizedName AS normalizedName, " +
                        "e.entityType AS entityType, e.description AS description, e.attributes AS attributes " +
                        "LIMIT $limit",
                        p("kbId", kbId, "limit", maxNodes));
                List<Map<String, Object>> list = new ArrayList<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rec.get("id", (String) null));
                    row.put("name", ((org.neo4j.driver.Value) rec.get("name")).asString(""));
                    row.put("entity_type", ((org.neo4j.driver.Value) rec.get("entityType")).asString(""));
                    row.put("description", rec.get("description", (String) null));
                    row.put("attributes", rec.get("attributes", (String) null));
                    list.add(row);
                }
                return list;
            });
            log.info("[Neo4jQuery] Found {} entity nodes in Neo4j for kb={} (took {} ms)",
                    nodes.size(), kbId, System.currentTimeMillis() - t1);

            List<String> nodeNames = nodes.stream()
                    .map(n -> (String) n.get("name"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (nodeNames.isEmpty()) {
                log.warn("[Neo4jQuery] No entity nodes found for kb={} in Neo4j. " +
                        "Either graph build didn't run or entities are stored with different kbId", kbId);
                return result("nodes", Collections.emptyList(), "edges", Collections.emptyList());
            }

            List<Map<String, Object>> edges = session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}]->(t:Entity {kbId: $kbId}) " +
                        "WHERE s.name IN $names AND t.name IN $names " +
                        "RETURN r.tripleId AS id, r.sourceId AS sourceId, r.targetId AS targetId, " +
                        "s.name AS source, t.name AS target, r.label AS label " +
                        "LIMIT $limit",
                        p("kbId", kbId, "names", nodeNames, "limit", maxNodes * 3));
                List<Map<String, Object>> list = new ArrayList<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rec.get("id", (String) null));
                    row.put("source_id", rec.get("sourceId", (String) null));
                    row.put("target_id", rec.get("targetId", (String) null));
                    row.put("source", ((org.neo4j.driver.Value) rec.get("source")).asString(""));
                    row.put("target", ((org.neo4j.driver.Value) rec.get("target")).asString(""));
                    row.put("label", ((org.neo4j.driver.Value) rec.get("label")).asString(""));
                    list.add(row);
                }
                return list;
            });

            log.info("[Neo4jQuery] Found {} edges for kb={} ({} nodes)",
                    edges.size(), kbId, nodes.size());

            if (excludeChunks) {
                int beforeFilter = nodes.size();
                nodes = nodes.stream()
                        .filter(n -> !"chunk".equalsIgnoreCase(String.valueOf(n.get("entity_type"))))
                        .collect(Collectors.toList());
                log.info("[Neo4jQuery] After excludeChunks: {} -> {} nodes", beforeFilter, nodes.size());
            } else {
                // 展示 Chunk 节点 + MENTIONS 边（对齐 Yuxi exclude_chunk 参数；KG-08）
                List<Map<String, Object>> chunkNodes = session.readTransaction(tx -> {
                    Result r = tx.run(
                            "MATCH (c:Chunk {kbId: $kbId}) " +
                            "RETURN c.chunkId AS id, c.content AS content LIMIT $limit",
                            p("kbId", kbId, "limit", maxNodes));
                    List<Map<String, Object>> list = new ArrayList<>();
                    while (r.hasNext()) {
                        Record rec = r.next();
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rec.get("id", (String) null));
                        row.put("name", rec.get("id", (String) null));
                        row.put("entity_type", "chunk");
                        row.put("description", rec.get("content", (String) null));
                        list.add(row);
                    }
                    return list;
                });
                nodes.addAll(chunkNodes);
                log.info("[Neo4jQuery] excludeChunks=false: added {} chunk nodes", chunkNodes.size());

                // MENTIONS 边：实体 -> Chunk（展示模式下附加）
                if (!chunkNodes.isEmpty()) {
                    List<Map<String, Object>> mentionEdges = session.readTransaction(tx -> {
                        Result r = tx.run(
                                "MATCH (e:Entity {kbId: $kbId})-[m:MENTIONS]->(c:Chunk {kbId: $kbId}) " +
                                "RETURN e.entityId AS sourceId, e.name AS source, c.chunkId AS targetId, c.chunkId AS target " +
                                "LIMIT $limit",
                                p("kbId", kbId, "limit", maxNodes * 2));
                        List<Map<String, Object>> list = new ArrayList<>();
                        while (r.hasNext()) {
                            Record rec = r.next();
                            Map<String, Object> row = new HashMap<>();
                            row.put("id", "m_" + rec.get("sourceId", (String) null) + "_" + rec.get("targetId", (String) null));
                            row.put("source_id", rec.get("sourceId", (String) null));
                            row.put("target_id", rec.get("targetId", (String) null));
                            row.put("source", ((org.neo4j.driver.Value) rec.get("source")).asString(""));
                            row.put("target", ((org.neo4j.driver.Value) rec.get("target")).asString(""));
                            row.put("label", "MENTIONS");
                            list.add(row);
                        }
                        return list;
                    });
                    edges.addAll(mentionEdges);
                }
            }

            return result("nodes", nodes, "edges", edges);
        } catch (Exception e) {
            log.error("Neo4j getGraphData failed: kb={}, error={}", kbId, e.getMessage(), e);
            return result("nodes", Collections.emptyList(), "edges", Collections.emptyList());
        }
    }

    @Override
    public Map<String, Object> expandGraph(String kbId, List<String> entities, int depth, int maxEntities) {
        if (entities == null || entities.isEmpty() || !enabled) {
            return result("entities", Collections.emptyList(), "relations", Collections.emptyList(), "expandedQuery", "");
        }

        try (Session session = neo4jDriver.session()) {
            List<Map<String, Object>> matched = session.readTransaction(tx -> {
                List<String> nameList = entities.stream().map(String::trim)
                        .filter(s -> !s.isBlank()).toList();
                if (nameList.isEmpty()) return new ArrayList<>();
                // 模糊匹配：实体名 = 关键词，或互为子串（兼容 "故障根因分析" 命中 "故障根因分析（RCA）"）
                // 按 mentionCount（被 chunk 引用数）降序；注意 Neo4j 5.x 禁止 size(pattern)，须用 COUNT {} 子查询
                Result r = tx.run(
                        "UNWIND $names AS t " +
                        "MATCH (e:Entity {kbId: $kbId}) " +
                        "WHERE e.name = t OR e.name CONTAINS t OR t CONTAINS e.name " +
                        "RETURN e.entityId AS id, e.name AS name, e.entityType AS entityType, " +
                        "       COUNT { (e)-[:MENTIONS]->(:Chunk) } AS mentionCount " +
                        "ORDER BY mentionCount DESC LIMIT $limit",
                        p("kbId", kbId, "names", nameList, "limit", maxEntities * 6));
                List<Map<String, Object>> list = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    String name = ((org.neo4j.driver.Value) rec.get("name")).asString("");
                    // UNWIND 下同一实体可能被多个关键词重复命中，去重
                    if (!seen.add(name)) continue;
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rec.get("id", (String) null));
                    row.put("name", name);
                    row.put("entity_type", ((org.neo4j.driver.Value) rec.get("entityType")).asString(""));
                    row.put("mentionCount", rec.get("mentionCount").asLong(0));
                    list.add(row);
                }
                // 按 mentionCount 降序截断（ORDER BY 受 UNWIND 重复行影响，这里兜底再排一次）
                list.sort(Comparator.comparingLong(
                        (Map<String, Object> m) -> ((Number) m.get("mentionCount")).longValue()).reversed());
                return list.size() <= maxEntities ? list : new ArrayList<>(list.subList(0, maxEntities));
            });

            Set<String> matchedNames = matched.stream()
                    .map(m -> (String) m.get("name"))
                    .collect(Collectors.toSet());

            List<Map<String, Object>> allEntities = new ArrayList<>(matched);
            List<Map<String, Object>> allRelations = new ArrayList<>();

            int hops = Math.min(Math.max(depth, 1), 2);
            if (hops >= 1 && !matchedNames.isEmpty()) {
                List<Map<String, Object>> relations = session.readTransaction(tx -> {
                    String cypher;
                    if (hops == 1) {
                        cypher = "MATCH (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}]-(n:Entity {kbId: $kbId}) " +
                                "WHERE s.name IN $names " +
                                "RETURN r.tripleId AS id, s.name AS source, n.name AS target, r.label AS label " +
                                "LIMIT $limit";
                    } else {
                        // 注意：Neo4j 不支持属性映射与可变长度（*1..2）组合，需 UNWIND 后过滤关系属性
                        cypher = "MATCH path = (s:Entity {kbId: $kbId})-[r:RELATION*1..2]-(n:Entity {kbId: $kbId}) " +
                                "WHERE s.name IN $names " +
                                "UNWIND relationships(path) AS rel " +
                                "WITH rel WHERE rel.kbId = $kbId " +
                                "RETURN rel.tripleId AS id, startNode(rel).name AS source, endNode(rel).name AS target, rel.label AS label " +
                                "LIMIT $limit";
                    }
                    Result r = tx.run(cypher,
                            p("kbId", kbId, "names", matchedNames, "limit", maxEntities * 2));
                    List<Map<String, Object>> list = new ArrayList<>();
                    while (r.hasNext()) {
                        Record rec = r.next();
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rec.get("id", (String) null));
                        row.put("source", ((org.neo4j.driver.Value) rec.get("source")).asString(""));
                        row.put("target", ((org.neo4j.driver.Value) rec.get("target")).asString(""));
                        row.put("label", ((org.neo4j.driver.Value) rec.get("label")).asString(""));
                        list.add(row);
                    }
                    return list;
                });

                Set<String> neighborNames = new HashSet<>();
                for (Map<String, Object> rel : relations) {
                    String s = (String) rel.get("source");
                    String t = (String) rel.get("target");
                    if (!matchedNames.contains(s)) neighborNames.add(s);
                    if (!matchedNames.contains(t)) neighborNames.add(t);
                }
                allRelations.addAll(relations);

                if (!neighborNames.isEmpty()) {
                    List<Map<String, Object>> neighbors = session.readTransaction(tx -> {
                        Result r = tx.run(
                                "MATCH (e:Entity {kbId: $kbId}) " +
                                "WHERE e.name IN $names " +
                                "RETURN e.entityId AS id, e.name AS name, e.entityType AS entityType, " +
                                "       COUNT { (e)-[:MENTIONS]->(:Chunk) } AS mentionCount " +
                                "ORDER BY mentionCount DESC LIMIT $limit",
                                p("kbId", kbId, "names", neighborNames, "limit", maxEntities));
                        List<Map<String, Object>> list = new ArrayList<>();
                        while (r.hasNext()) {
                            Record rec = r.next();
                            Map<String, Object> row = new HashMap<>();
                            row.put("id", rec.get("id", (String) null));
                            row.put("name", ((org.neo4j.driver.Value) rec.get("name")).asString(""));
                            row.put("entity_type", ((org.neo4j.driver.Value) rec.get("entityType")).asString(""));
                            list.add(row);
                        }
                        return list;
                    });
                    allEntities.addAll(neighbors);
                }
            }

            String expandedQuery = allEntities.stream()
                    .map(e -> (String) e.get("name"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(" "));

            return result("entities", allEntities, "relations", allRelations, "expandedQuery", expandedQuery);
        } catch (Exception e) {
            log.error("Neo4j expandGraph failed: kb={}, error={}", kbId, e.getMessage(), e);
            return result("entities", Collections.emptyList(), "relations", Collections.emptyList(), "expandedQuery", "");
        }
    }

    @Override
    public Map<String, Object> searchNodes(String kbId, String keyword, int maxNodes) {
        if (keyword == null || keyword.isBlank() || !enabled) {
            return result("nodes", Collections.emptyList(), "edges", Collections.emptyList());
        }
        String pattern = "(?i).*" + keyword.trim() + ".*";

        try (Session session = neo4jDriver.session()) {
            // 多字段 CONTAINS 匹配
            List<Map<String, Object>> nodes = session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH (e:Entity {kbId: $kbId}) " +
                        "WHERE e.name =~ $pattern OR e.normalizedName =~ $pattern " +
                        "OR e.entityType =~ $pattern OR e.description =~ $pattern " +
                        "RETURN e.entityId AS id, e.name AS name, e.entityType AS entityType, " +
                        "e.description AS description, e.attributes AS attributes " +
                        "LIMIT $limit",
                        p("kbId", kbId, "pattern", pattern, "limit", maxNodes));
                List<Map<String, Object>> list = new ArrayList<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rec.get("id", (String) null));
                    row.put("name", ((org.neo4j.driver.Value) rec.get("name")).asString(""));
                    row.put("entity_type", ((org.neo4j.driver.Value) rec.get("entityType")).asString(""));
                    row.put("description", rec.get("description", (String) null));
                    row.put("attributes", rec.get("attributes", (String) null));
                    list.add(row);
                }
                return list;
            });

            List<String> nodeNames = nodes.stream()
                    .map(n -> (String) n.get("name"))
                    .collect(Collectors.toList());

            List<Map<String, Object>> edges = Collections.emptyList();
            if (!nodeNames.isEmpty()) {
                edges = session.readTransaction(tx -> {
                    Result r = tx.run(
                            "MATCH (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}]->(t:Entity {kbId: $kbId}) " +
                            "WHERE s.name IN $names AND t.name IN $names " +
                            "RETURN r.tripleId AS id, r.sourceId AS sourceId, r.targetId AS targetId, " +
                            "s.name AS source, t.name AS target, r.label AS label " +
                            "LIMIT $limit",
                            p("kbId", kbId, "names", nodeNames, "limit", maxNodes * 2));
                    List<Map<String, Object>> list = new ArrayList<>();
                    while (r.hasNext()) {
                        Record rec = r.next();
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rec.get("id", (String) null));
                        row.put("source_id", rec.get("sourceId", (String) null));
                        row.put("target_id", rec.get("targetId", (String) null));
                        row.put("source", ((org.neo4j.driver.Value) rec.get("source")).asString(""));
                        row.put("target", ((org.neo4j.driver.Value) rec.get("target")).asString(""));
                        row.put("label", ((org.neo4j.driver.Value) rec.get("label")).asString(""));
                        list.add(row);
                    }
                    return list;
                });
            }

            return result("nodes", nodes, "edges", edges);
        } catch (Exception e) {
            log.error("Neo4j searchNodes failed: kb={}, keyword={}, error={}", kbId, keyword, e.getMessage(), e);
            return result("nodes", Collections.emptyList(), "edges", Collections.emptyList());
        }
    }

    @Override
    public List<String> getLabels(String kbId) {
        if (!enabled) return Collections.emptyList();
        try (Session session = neo4jDriver.session()) {
            return session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH (e:Entity {kbId: $kbId}) " +
                        "RETURN DISTINCT e.entityType AS label ORDER BY label",
                        p("kbId", kbId));
                List<String> labels = new ArrayList<>();
                while (r.hasNext()) {
                    labels.add(((org.neo4j.driver.Value) r.next().get("label")).asString(""));
                }
                return labels;
            });
        } catch (Exception e) {
            log.error("Neo4j getLabels failed: kb={}, error={}", kbId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Long> countEntitiesByType(String kbId) {
        if (!enabled) return Collections.emptyMap();
        try (Session session = neo4jDriver.session()) {
            return session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH (e:Entity {kbId: $kbId}) " +
                        "RETURN coalesce(e.entityType, 'UNKNOWN') AS type, count(e) AS cnt " +
                        "ORDER BY cnt DESC",
                        p("kbId", kbId));
                Map<String, Long> counts = new LinkedHashMap<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    counts.put(rec.get("type").asString("UNKNOWN"), rec.get("cnt").asLong(0));
                }
                return counts;
            });
        } catch (Exception e) {
            log.error("Neo4j countEntitiesByType failed: kb={}, error={}", kbId, e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    // ==================== Mention 追踪 ====================

    @Override
    public void createChunk(String kbId, String chunkId, String fileId, String content) {
        if (!enabled) return;
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                String summary = content != null && content.length() > 200
                        ? content.substring(0, 200) : content;
                tx.run("MERGE (c:Chunk {kbId: $kbId, chunkId: $chunkId}) " +
                        "ON CREATE SET c.fileId = $fileId, c.content = $content, c.createdAt = datetime() " +
                        "ON MATCH SET c.fileId = coalesce(c.fileId, $fileId)",
                        p("kbId", kbId, "chunkId", chunkId, "fileId", fileId, "content", summary));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j createChunk failed: kb={}, chunk={}, error={}", kbId, chunkId, e.getMessage());
        }
    }

    @Override
    public void createEntityMention(String kbId, String entityName, String entityId, String chunkId, String fileId) {
        if (!enabled) return;
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                // 使用 name 匹配（与 createEntity/createRelation 的 MERGE 键一致）
                tx.run("MATCH (e:Entity {kbId: $kbId, name: $name}) " +
                        "MERGE (c:Chunk {kbId: $kbId, chunkId: $chunkId}) " +
                        "MERGE (e)-[m:MENTIONS {kbId: $kbId}]->(c) " +
                        "ON CREATE SET m.createdAt = datetime()",
                        p("kbId", kbId, "name", entityName, "chunkId", chunkId));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j createEntityMention failed: kb={}, entity={}, chunk={}, error={}",
                    kbId, entityName, chunkId, e.getMessage());
        }
    }

    @Override
    public void createTripleMention(String kbId, String tripleId, String chunkId, String fileId) {
        if (!enabled) return;
        // 关系（RELATION 是 relationship 类型）不能有出边，因此用独立的 :TripleMention 索引节点
        // 记录 关系↔分块 溯源（对标 LightRAG relation_chunks），供删除/权重统计/孤儿回收使用
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                tx.run("MERGE (tm:TripleMention {kbId: $kbId, tripleId: $tripleId, chunkId: $chunkId}) " +
                        "ON CREATE SET tm.fileId = $fileId, tm.createdAt = datetime()",
                        p("kbId", kbId, "tripleId", tripleId, "chunkId", chunkId, "fileId", fileId));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j createTripleMention failed: kb={}, triple={}, chunk={}, error={}",
                    kbId, tripleId, chunkId, e.getMessage());
        }
    }

    // ==================== 图谱管理 ====================

    @Override
    public void clearGraph(String kbId) {
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                // 先删除 Chunk 节点和 MENTIONS 关系
                tx.run("MATCH (c:Chunk {kbId: $kbId}) DETACH DELETE c", p("kbId", kbId));
                // 再删除 Entity 节点
                tx.run("MATCH (e:Entity {kbId: $kbId}) DETACH DELETE e", p("kbId", kbId));
                return null;
            });
        } catch (Exception e) {
            log.error("Neo4j clearGraph failed: kb={}, error={}", kbId, e.getMessage(), e);
        }
    }

    @Override
    public void deleteFileGraph(String kbId, String fileId) {
        if (!enabled) return;
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                // 1. 查该文件的所有 chunkId（用于 TripleMention 溯源删除）
                List<String> chunkIds = new ArrayList<>();
                Result ids = tx.run("MATCH (c:Chunk {kbId: $kbId, fileId: $fileId}) RETURN c.chunkId AS id",
                        p("kbId", kbId, "fileId", fileId));
                while (ids.hasNext()) {
                    chunkIds.add(ids.next().get("id").asString());
                }

                // 2. 删除该文件的所有 Chunk 节点及其 MENTIONS 关系
                tx.run("MATCH (c:Chunk {kbId: $kbId, fileId: $fileId}) DETACH DELETE c",
                        p("kbId", kbId, "fileId", fileId));

                // 3. 删除该文件 chunk 对应的关系溯源节点（TripleMention）
                if (!chunkIds.isEmpty()) {
                    tx.run("MATCH (tm:TripleMention {kbId: $kbId}) WHERE tm.chunkId IN $ids DELETE tm",
                            p("kbId", kbId, "ids", chunkIds));
                }

                // 4. 回收孤立的 RELATION（没有任何 TripleMention 引用 = 不再被任何 chunk 提及）
                tx.run("MATCH (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}]->(t:Entity {kbId: $kbId}) " +
                        "WHERE NOT EXISTS { MATCH (:TripleMention {kbId: r.kbId, tripleId: r.tripleId}) } " +
                        "DELETE r",
                        p("kbId", kbId));

                // 5. 回收孤立的 Entity（不再被任何 Chunk MENTIONS）
                tx.run("MATCH (e:Entity {kbId: $kbId}) " +
                        "WHERE NOT (e)-[:MENTIONS]-(:Chunk) " +
                        "DETACH DELETE e",
                        p("kbId", kbId));

                return null;
            });
            log.info("Deleted file graph data in Neo4j: kb={}, file={}", kbId, fileId);
        } catch (Exception e) {
            log.error("Neo4j deleteFileGraph failed: kb={}, file={}, error={}", kbId, fileId, e.getMessage(), e);
        }
    }

    @Override
    public void deleteChunkGraph(String kbId, String chunkId) {
        if (!enabled) return;
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                // 1. 删除该 Chunk 节点及其所有 MENTIONS 关系
                tx.run("MATCH (c:Chunk {kbId: $kbId, chunkId: $chunkId}) DETACH DELETE c",
                        p("kbId", kbId, "chunkId", chunkId));

                // 2. 回收孤立的 Entity（不再被任何 Chunk MENTIONS）
                tx.run("MATCH (e:Entity {kbId: $kbId}) " +
                        "WHERE NOT (e)-[:MENTIONS]-(:Chunk) " +
                        "DETACH DELETE e",
                        p("kbId", kbId));

                // 3. 回收孤立的 RELATION（没有 MENTIONS 到任何 Chunk）
                tx.run("MATCH (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}]->(t:Entity {kbId: $kbId}) " +
                        "WHERE NOT EXISTS { MATCH (r)-[:MENTIONS]->(:Chunk) } " +
                        "DELETE r",
                        p("kbId", kbId));

                return null;
            });
            log.info("Deleted chunk graph data in Neo4j: kb={}, chunk={}", kbId, chunkId);
        } catch (Exception e) {
            log.error("Neo4j deleteChunkGraph failed: kb={}, chunk={}, error={}", kbId, chunkId, e.getMessage(), e);
        }
    }

    @Override
    public void renameChunkId(String kbId, String oldChunkId, String newChunkId) {
        if (!enabled) return;
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                // Neo4j 不支持修改 MATCH 键属性，先创建新节点再迁移关系后删旧节点
                // 1. 收集旧 Chunk 的属性和 MENTIONS 关系信息
                tx.run(
                        "MATCH (old:Chunk {kbId: $kbId, chunkId: $oldChunkId}) " +
                        "OPTIONAL MATCH (e)-[m:MENTIONS {kbId: $kbId}]->(old) " +
                        "WITH old, collect(m) AS entityMentions, collect(e) AS entityNodes " +
                        "CREATE (new:Chunk {kbId: $kbId, chunkId: $newChunkId}) " +
                        "SET new.fileId = old.fileId, new.content = old.content, new.createdAt = old.createdAt " +
                        "FOREACH (idx IN range(0, size(entityMentions)-1) | " +
                        "  MERGE (entityNodes[idx])-[:MENTIONS {kbId: $kbId, createdAt: entityMentions[idx].createdAt}]->(new)) " +
                        "DETACH DELETE old",
                        p("kbId", kbId, "oldChunkId", oldChunkId, "newChunkId", newChunkId));
                return null;
            });
            log.info("Renamed chunk in Neo4j: kb={}, {} -> {}", kbId, oldChunkId, newChunkId);
        } catch (Exception e) {
            log.error("Neo4j renameChunkId failed: kb={}, {} -> {}, error={}",
                    kbId, oldChunkId, newChunkId, e.getMessage(), e);
        }
    }

    // ==================== 统计查询 ====================

    @Override
    public long countEntities(String kbId) {
        if (!enabled) return 0;
        try (Session session = neo4jDriver.session()) {
            return session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH (e:Entity {kbId: $kbId}) RETURN count(e) AS cnt",
                        p("kbId", kbId));
                if (r.hasNext()) {
                    return r.next().get("cnt").asLong(0);
                }
                return 0L;
            });
        } catch (Exception e) {
            log.warn("Neo4j countEntities failed: kb={}, error={}", kbId, e.getMessage());
            return 0;
        }
    }

    @Override
    public long countRelations(String kbId) {
        if (!enabled) return 0;
        try (Session session = neo4jDriver.session()) {
            return session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH ()-[r:RELATION {kbId: $kbId}]->() RETURN count(r) AS cnt",
                        p("kbId", kbId));
                if (r.hasNext()) {
                    return r.next().get("cnt").asLong(0);
                }
                return 0L;
            });
        } catch (Exception e) {
            log.warn("Neo4j countRelations failed: kb={}, error={}", kbId, e.getMessage());
            return 0;
        }
    }

    // ==================== 实体向量检索（对标 LightRAG entities_vdb）====================

    /** 实体 embedding 维度（须与 KB 使用的 embedding 模型维度一致，默认 bge-m3 1024） */
    @Value("${graph.entity-embedding-dimension:1024}")
    private int vectorDimension;

    /** 向量索引是否已创建（懒初始化，创建失败时降级为文本匹配） */
    private volatile boolean vectorIndexReady = false;

    /** 幂等创建 Entity embedding 向量索引（Neo4j 5.11+），失败时降级不阻塞 */
    private synchronized void ensureVectorIndex() {
        if (vectorIndexReady || !enabled) return;
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                tx.run("CREATE VECTOR INDEX entity_embedding IF NOT EXISTS " +
                        "FOR (e:Entity) ON (e.embedding) " +
                        "OPTIONS {indexConfig: {`vector.dimensions`: $dim, `vector.similarity_function`: 'cosine'}}",
                        p("dim", vectorDimension));
                return null;
            });
            vectorIndexReady = true;
            log.info("Neo4j vector index 'entity_embedding' ready (dim={})", vectorDimension);
        } catch (Exception e) {
            // 服务器版本低于 5.11 或不支持 vector index → 降级为文本匹配
            log.warn("Neo4j vector index creation failed, entity vector search will fall back to text matching: {}",
                    e.getMessage());
        }
    }

    @Override
    public void updateEntityEmbedding(String kbId, String entityName, List<Float> embedding) {
        if (!enabled || embedding == null || embedding.isEmpty()) return;
        if (embedding.size() != vectorDimension) {
            log.warn("Entity embedding dimension mismatch: expected {}, got {} (entity='{}'), skip",
                    vectorDimension, embedding.size(), entityName);
            return;
        }
        ensureVectorIndex();
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                tx.run("MATCH (e:Entity {kbId: $kbId, name: $name}) SET e.embedding = $emb",
                        p("kbId", kbId, "name", entityName, "emb", embedding));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j updateEntityEmbedding failed: kb={}, entity={}, error={}",
                    kbId, entityName, e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> searchEntitiesByVector(String kbId, List<Float> embedding, int topK) {
        if (!enabled || embedding == null || embedding.isEmpty()) return Collections.emptyList();
        if (embedding.size() != vectorDimension) {
            log.warn("Query embedding dimension mismatch: expected {}, got {}", vectorDimension, embedding.size());
            return Collections.emptyList();
        }
        ensureVectorIndex();
        if (!vectorIndexReady) return Collections.emptyList();
        try (Session session = neo4jDriver.session()) {
            return session.readTransaction(tx -> {
                // 向量索引按 Entity label 全局建（跨 kb），放大取数后按 kbId 过滤再截断
                Result r = tx.run(
                        "CALL db.index.vector.queryNodes('entity_embedding', $k, $emb) YIELD node, score " +
                        "WHERE node.kbId = $kbId " +
                        "RETURN node.name AS name, node.entityType AS entityType, score " +
                        "ORDER BY score DESC LIMIT $limit",
                        p("kbId", kbId, "emb", embedding, "k", topK * 5, "limit", topK));
                List<Map<String, Object>> list = new ArrayList<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    Map<String, Object> row = new HashMap<>();
                    row.put("name", rec.get("name").asString(""));
                    row.put("entity_type", rec.get("entityType").asString(""));
                    row.put("score", rec.get("score").asDouble(0.0));
                    list.add(row);
                }
                return list;
            });
        } catch (Exception e) {
            log.warn("Neo4j searchEntitiesByVector failed: kb={}, error={}", kbId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> listEntitiesWithoutEmbedding(String kbId, int limit) {
        if (!enabled) return Collections.emptyList();
        try (Session session = neo4jDriver.session()) {
            return session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH (e:Entity {kbId: $kbId}) WHERE e.embedding IS NULL " +
                        "RETURN e.name AS name LIMIT $limit",
                        p("kbId", kbId, "limit", limit));
                List<String> names = new ArrayList<>();
                while (r.hasNext()) {
                    names.add(r.next().get("name").asString(""));
                }
                return names;
            });
        } catch (Exception e) {
            log.warn("Neo4j listEntitiesWithoutEmbedding failed: kb={}, error={}", kbId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void cleanupOrphanNodes(String kbId) {
        if (!enabled) return;
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                // 0. 边端点实体 ID 回填（KG-01）：跨 chunk 引用且端点实体从未被单独提取时，
                //    关系边的 sourceId/targetId 可能为空，从端点节点属性回填
                tx.run("MATCH (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}]->(t:Entity {kbId: $kbId}) " +
                        "WHERE r.sourceId IS NULL OR r.targetId IS NULL " +
                        "SET r.sourceId = coalesce(r.sourceId, s.entityId), " +
                        "r.targetId = coalesce(r.targetId, t.entityId)",
                        p("kbId", kbId));
                // 1. 回收孤立的 RELATION（无任何 TripleMention 引用 = 不再被任何 chunk 提及）
                tx.run("MATCH (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}]->(t:Entity {kbId: $kbId}) " +
                        "WHERE NOT EXISTS { MATCH (:TripleMention {kbId: r.kbId, tripleId: r.tripleId}) } " +
                        "DELETE r",
                        p("kbId", kbId));
                // 2. 回收孤立的 Entity（不再被任何 Chunk MENTIONS）
                tx.run("MATCH (e:Entity {kbId: $kbId}) " +
                        "WHERE NOT (e)-[:MENTIONS]-(:Chunk) " +
                        "DETACH DELETE e",
                        p("kbId", kbId));
                return null;
            });
            log.info("Neo4j orphan cleanup done for kb={}", kbId);
        } catch (Exception e) {
            log.warn("Neo4j cleanupOrphanNodes failed: kb={}, error={}", kbId, e.getMessage());
        }
    }
}
