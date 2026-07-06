package com.fastrag.infra.neo4j;

import com.fastrag.infra.graph.GraphStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j 图存储实现
 *
 * <p>使用 Neo4j Java Driver 直连，不依赖 Spring Data Neo4j，保持轻量。
 * 节点标签：Entity，关系类型：RELATION
 *
 * <p>仅当 neo4j.enabled=true 时创建此 Bean。
 */
@Slf4j
@Repository
@ConditionalOnProperty(prefix = "neo4j", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class Neo4jGraphStore implements GraphStore {

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

    /** 将 key-value 对放入结果 Map（避免 Map.of() 类型推断问题） */
    private Map<String, Object> result(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    // ==================== Entity ====================

    @Override
    public void createEntity(String kbId, String name, String type) {
        if (name == null || name.isBlank()) return;
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                tx.run("MERGE (e:Entity {kbId: $kbId, name: $name}) " +
                        "ON CREATE SET e.entityType = $type, e.createdAt = datetime() " +
                        "ON MATCH SET e.entityType = coalesce(e.entityType, $type)",
                        p("kbId", kbId, "name", name.trim(), "type", type != null ? type : "UNKNOWN"));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j createEntity failed: kb={}, name={}, error={}", kbId, name, e.getMessage());
        }
    }

    @Override
    public void createRelation(String kbId, String source, String target, String label) {
        if (source == null || target == null || source.isBlank() || target.isBlank()) return;
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                tx.run("MERGE (s:Entity {kbId: $kbId, name: $source}) " +
                        "MERGE (t:Entity {kbId: $kbId, name: $target}) " +
                        "MERGE (s)-[r:RELATION {kbId: $kbId, label: $label}]->(t) " +
                        "ON CREATE SET r.createdAt = datetime()",
                        p("kbId", kbId, "source", source.trim(), "target", target.trim(),
                                "label", label != null ? label : "RELATED"));
                return null;
            });
        } catch (Exception e) {
            log.warn("Neo4j createRelation failed: kb={}, {}->{}, error={}", kbId, source, target, e.getMessage());
        }
    }

    // ==================== Graph Query ====================

    @Override
    public Map<String, Object> getGraphData(String kbId, int maxNodes, boolean excludeChunks) {
        try (Session session = neo4jDriver.session()) {
            List<Map<String, Object>> nodes = session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH (e:Entity {kbId: $kbId}) " +
                        "RETURN e.id AS id, e.name AS name, e.entityType AS entityType " +
                        "LIMIT $limit",
                        p("kbId", kbId, "limit", maxNodes));
                List<Map<String, Object>> list = new ArrayList<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rec.get("id").asString(null));
                    row.put("name", rec.get("name").asString(""));
                    row.put("entity_type", rec.get("entityType").asString(""));
                    list.add(row);
                }
                return list;
            });

            List<String> nodeIds = nodes.stream()
                    .map(n -> (String) n.get("id"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (nodeIds.isEmpty()) {
                return result("nodes", Collections.emptyList(), "edges", Collections.emptyList());
            }

            List<Map<String, Object>> edges = session.readTransaction(tx -> {
                Result r = tx.run(
                        "MATCH (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}]->(t:Entity {kbId: $kbId}) " +
                        "WHERE elementId(s) IN $ids AND elementId(t) IN $ids " +
                        "RETURN elementId(r) AS id, elementId(s) AS source, elementId(t) AS target, r.label AS label " +
                        "LIMIT $limit",
                        p("kbId", kbId, "ids", nodeIds, "limit", maxNodes * 3));
                List<Map<String, Object>> list = new ArrayList<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rec.get("id").asString(""));
                    row.put("source", rec.get("source").asString(""));
                    row.put("target", rec.get("target").asString(""));
                    row.put("label", rec.get("label").asString(""));
                    list.add(row);
                }
                return list;
            });

            if (excludeChunks) {
                nodes = nodes.stream()
                        .filter(n -> !"chunk".equalsIgnoreCase(String.valueOf(n.get("entity_type"))))
                        .collect(Collectors.toList());
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
                        "RETURN elementId(e) AS id, e.name AS name, e.entityType AS entityType LIMIT $limit",
                        p("kbId", kbId, "names", nameList, "limit", maxEntities));
                List<Map<String, Object>> list = new ArrayList<>();
                while (r.hasNext()) {
                    Record rec = r.next();
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rec.get("id").asString(""));
                    row.put("name", rec.get("name").asString(""));
                    row.put("entity_type", rec.get("entityType").asString(""));
                    list.add(row);
                }
                return list;
            });

            Set<String> matchedIds = matched.stream()
                    .map(m -> (String) m.get("id"))
                    .collect(Collectors.toSet());

            List<Map<String, Object>> allEntities = new ArrayList<>(matched);
            List<Map<String, Object>> allRelations = new ArrayList<>();

            int hops = Math.min(Math.max(depth, 1), 2);
            if (hops >= 1 && !matchedIds.isEmpty()) {
                List<Map<String, Object>> relations = session.readTransaction(tx -> {
                    String cypher;
                    if (hops == 1) {
                        cypher = "MATCH (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}]-(n:Entity {kbId: $kbId}) " +
                                "WHERE elementId(s) IN $ids " +
                                "RETURN elementId(r) AS id, elementId(s) AS source, elementId(n) AS target, r.label AS label " +
                                "LIMIT $limit";
                    } else {
                        cypher = "MATCH path = (s:Entity {kbId: $kbId})-[r:RELATION {kbId: $kbId}*1..2]-(n:Entity {kbId: $kbId}) " +
                                "WHERE elementId(s) IN $ids " +
                                "UNWIND relationships(path) AS rel " +
                                "RETURN elementId(rel) AS id, elementId(startNode(rel)) AS source, elementId(endNode(rel)) AS target, rel.label AS label " +
                                "LIMIT $limit";
                    }
                    Result r = tx.run(cypher,
                            p("kbId", kbId, "ids", matchedIds, "limit", maxEntities * 2));
                    List<Map<String, Object>> list = new ArrayList<>();
                    while (r.hasNext()) {
                        Record rec = r.next();
                        Map<String, Object> row = new HashMap<>();
                        row.put("id", rec.get("id").asString(""));
                        row.put("source", rec.get("source").asString(""));
                        row.put("target", rec.get("target").asString(""));
                        row.put("label", rec.get("label").asString(""));
                        list.add(row);
                    }
                    return list;
                });

                Set<String> neighborIds = new HashSet<>();
                for (Map<String, Object> rel : relations) {
                    String s = (String) rel.get("source");
                    String t = (String) rel.get("target");
                    if (!matchedIds.contains(s)) neighborIds.add(s);
                    if (!matchedIds.contains(t)) neighborIds.add(t);
                }
                allRelations.addAll(relations);

                if (!neighborIds.isEmpty()) {
                    List<Map<String, Object>> neighbors = session.readTransaction(tx -> {
                        Result r = tx.run(
                                "MATCH (e:Entity {kbId: $kbId}) " +
                                "WHERE elementId(e) IN $ids " +
                                "RETURN elementId(e) AS id, e.name AS name, e.entityType AS entityType " +
                                "LIMIT $limit",
                                p("kbId", kbId, "ids", neighborIds, "limit", maxEntities));
                        List<Map<String, Object>> list = new ArrayList<>();
                        while (r.hasNext()) {
                            Record rec = r.next();
                            Map<String, Object> row = new HashMap<>();
                            row.put("id", rec.get("id").asString(""));
                            row.put("name", rec.get("name").asString(""));
                            row.put("entity_type", rec.get("entityType").asString(""));
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
    public void clearGraph(String kbId) {
        try (Session session = neo4jDriver.session()) {
            session.writeTransaction(tx -> {
                tx.run("MATCH (e:Entity {kbId: $kbId}) DETACH DELETE e",
                        p("kbId", kbId));
                return null;
            });
        } catch (Exception e) {
            log.error("Neo4j clearGraph failed: kb={}, error={}", kbId, e.getMessage(), e);
        }
    }
}
