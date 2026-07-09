package com.fastrag.infra.neo4j;

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

/**
 * Neo4j 图存储实现（参考 Yuxi CypherTemplates + 三存储架构）
 *
 * <p>使用 Neo4j Java Driver 直连，不依赖 Spring Data Neo4j，保持轻量。
 * <ul>
 *   <li>节点标签：Entity（实体），Chunk（文档块）</li>
 *   <li>关系类型：RELATION（实体间关系），MENTIONS（实体→Chunk 关联）</li>
 *   <li>命名空间：通过 kbId 属性隔离不同知识库</li>
 * </ul>
 *
 * <p>仅当 neo4j.enabled=true 时创建此 Bean。
 */
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
    public void createEntity(String kbId, String entityId, String name, String normalizedName, String type) {
        if (name == null || name.isBlank()) return;
        String normalized = normalizedName != null ? normalizedName : name.trim().toLowerCase();
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                // MERGE on (kbId, normalized_name, entityType) — 参考 Yuxi CypherTemplates
                tx.run("MERGE (e:Entity {kbId: $kbId, normalizedName: $normalizedName, entityType: $type}) " +
                        "ON CREATE SET e.entityId = $entityId, e.name = $name, e.createdAt = datetime() " +
                        "ON MATCH SET e.name = coalesce(e.name, $name), e.entityId = coalesce(e.entityId, $entityId)",
                        p("kbId", kbId, "entityId", entityId, "name", name.trim(),
                                "normalizedName", normalized, "type", type != null ? type : "UNKNOWN"));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j createEntity failed: kb={}, name={}, error={}", kbId, name, e.getMessage());
        }
    }

    @Override
    public void createRelation(String kbId, String tripleId, String source, String target, String label, String content) {
        if (source == null || target == null || source.isBlank() || target.isBlank()) return;
        String relLabel = label != null ? label : "RELATED";
        String relContent = content != null ? content : (source.trim() + " -> " + relLabel + " -> " + target.trim());
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                tx.run("MERGE (s:Entity {kbId: $kbId, name: $source}) " +
                        "MERGE (t:Entity {kbId: $kbId, name: $target}) " +
                        "MERGE (s)-[r:RELATION {kbId: $kbId, label: $label}]->(t) " +
                        "ON CREATE SET r.tripleId = $tripleId, r.content = $content, r.createdAt = datetime() " +
                        "ON MATCH SET r.tripleId = coalesce(r.tripleId, $tripleId), r.content = coalesce(r.content, $content)",
                        p("kbId", kbId, "tripleId", tripleId, "source", source.trim(),
                                "target", target.trim(), "label", relLabel, "content", relContent));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j createRelation failed: kb={}, {}->{}, error={}", kbId, source, target, e.getMessage());
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
                        "e.entityType AS entityType, e.description AS description " +
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
                        "RETURN r.tripleId AS id, s.name AS source, t.name AS target, r.label AS label " +
                        "LIMIT $limit",
                        p("kbId", kbId, "names", nodeNames, "limit", maxNodes * 3));
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

            log.info("[Neo4jQuery] Found {} edges for kb={} ({} nodes)",
                    edges.size(), kbId, nodes.size());

            if (excludeChunks) {
                int beforeFilter = nodes.size();
                nodes = nodes.stream()
                        .filter(n -> !"chunk".equalsIgnoreCase(String.valueOf(n.get("entity_type"))))
                        .collect(Collectors.toList());
                log.info("[Neo4jQuery] After excludeChunks: {} -> {} nodes", beforeFilter, nodes.size());
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
                List<String> nameList = entities.stream().map(String::trim).toList();
                Result r = tx.run(
                        "MATCH (e:Entity {kbId: $kbId}) WHERE e.name IN $names " +
                        "RETURN e.entityId AS id, e.name AS name, e.entityType AS entityType LIMIT $limit",
                        p("kbId", kbId, "names", nameList, "limit", maxEntities));
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
                        cypher = "MATCH path = (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}*1..2]-(n:Entity {kbId: $kbId}) " +
                                "WHERE s.name IN $names " +
                                "UNWIND relationships(path) AS rel " +
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
                                "RETURN e.entityId AS id, e.name AS name, e.entityType AS entityType " +
                                "LIMIT $limit",
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
                        "RETURN e.entityId AS id, e.name AS name, e.entityType AS entityType " +
                        "LIMIT $limit",
                        p("kbId", kbId, "pattern", pattern, "limit", maxNodes));
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

            List<String> nodeNames = nodes.stream()
                    .map(n -> (String) n.get("name"))
                    .collect(Collectors.toList());

            List<Map<String, Object>> edges = Collections.emptyList();
            if (!nodeNames.isEmpty()) {
                edges = session.readTransaction(tx -> {
                    Result r = tx.run(
                            "MATCH (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}]->(t:Entity {kbId: $kbId}) " +
                            "WHERE s.name IN $names AND t.name IN $names " +
                            "RETURN r.tripleId AS id, s.name AS source, t.name AS target, r.label AS label " +
                            "LIMIT $limit",
                            p("kbId", kbId, "names", nodeNames, "limit", maxNodes * 2));
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

    // ==================== Mention 追踪 ====================

    @Override
    public void createChunk(String kbId, String chunkId, String content) {
        if (!enabled) return;
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                String summary = content != null && content.length() > 200
                        ? content.substring(0, 200) : content;
                tx.run("MERGE (c:Chunk {kbId: $kbId, chunkId: $chunkId}) " +
                        "ON CREATE SET c.content = $content, c.createdAt = datetime()",
                        p("kbId", kbId, "chunkId", chunkId, "content", summary));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j createChunk failed: kb={}, chunk={}, error={}", kbId, chunkId, e.getMessage());
        }
    }

    @Override
    public void createEntityMention(String kbId, String entityId, String chunkId) {
        if (!enabled) return;
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                tx.run("MATCH (e:Entity {kbId: $kbId, entityId: $entityId}) " +
                        "MERGE (c:Chunk {kbId: $kbId, chunkId: $chunkId}) " +
                        "MERGE (e)-[m:MENTIONS {kbId: $kbId}]->(c) " +
                        "ON CREATE SET m.createdAt = datetime()",
                        p("kbId", kbId, "entityId", entityId, "chunkId", chunkId));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j createEntityMention failed: kb={}, entity={}, chunk={}, error={}",
                    kbId, entityId, chunkId, e.getMessage());
        }
    }

    @Override
    public void createTripleMention(String kbId, String tripleId, String chunkId) {
        if (!enabled) return;
        // Neo4j 中三元组 mention 通过关系属性记录
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                tx.run("MATCH (r:RELATION {kbId: $kbId, tripleId: $tripleId}) " +
                        "MERGE (c:Chunk {kbId: $kbId, chunkId: $chunkId}) " +
                        "MERGE (r)-[m:MENTIONS {kbId: $kbId}]->(c) " +
                        "ON CREATE SET m.createdAt = datetime()",
                        p("kbId", kbId, "tripleId", tripleId, "chunkId", chunkId));
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
                // 1. 删除该文件的所有 Chunk 节点及其 MENTIONS 关系
                tx.run("MATCH (c:Chunk {kbId: $kbId, fileId: $fileId}) DETACH DELETE c",
                        p("kbId", kbId, "fileId", fileId));

                // 2. 回收孤立的 Entity 节点（没有被任何 Chunk MENTIONS）
                tx.run("MATCH (e:Entity {kbId: $kbId}) " +
                        "WHERE NOT (e)-[:MENTIONS]-(:Chunk) " +
                        "DETACH DELETE e",
                        p("kbId", kbId));

                // 3. 回收没有 MENTIONS 到 Chunk 的孤立 RELATION
                // 注意：Neo4j 5.x 不允许在 WHERE NOT 的模式中引入新变量，需用 OPTIONAL MATCH
                tx.run("MATCH ()-[r:RELATION {kbId: $kbId}]->() " +
                        "OPTIONAL MATCH ()-[m:MENTIONS]->(:Chunk {kbId: $kbId}) " +
                        "WHERE m IS NULL " +
                        "DELETE r",
                        p("kbId", kbId));

                return null;
            });
            log.info("Deleted file graph data in Neo4j: kb={}, file={}", kbId, fileId);
        } catch (Exception e) {
            log.error("Neo4j deleteFileGraph failed: kb={}, file={}, error={}", kbId, fileId, e.getMessage(), e);
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
}
