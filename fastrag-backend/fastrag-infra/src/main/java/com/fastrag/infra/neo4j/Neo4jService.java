package com.fastrag.infra.neo4j;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jService {
    private final Driver driver;

    public Map<String, Object> getGraphData(String kbId, int maxNodes, boolean excludeChunks) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        try (Session session = driver.session()) {
            String nq = excludeChunks
                ? "MATCH (e:Entity {kbId: $kbId}) RETURN e LIMIT $limit"
                : "MATCH (n) WHERE n.kbId = $kbId RETURN n LIMIT $limit";
            session.run(nq, Map.of("kbId", kbId, "limit", maxNodes)).forEachRemaining(r -> {
                Node n = r.get(excludeChunks ? "e" : "n").asNode();
                nodes.add(Map.of("id", n.elementId(), "name", n.get("name").asString(""),
                    "label", n.get("label").asString(""), "type", n.hasLabel("Entity") ? "entity" : "chunk"));
            });
            String eq = excludeChunks
                ? "MATCH (e1:Entity)-[r]->(e2:Entity) WHERE e1.kbId=$kbId AND e2.kbId=$kbId RETURN e1.name as source, e2.name as target, type(r) as label LIMIT $limit"
                : "MATCH (a)-[r]->(b) WHERE a.kbId=$kbId AND b.kbId=$kbId RETURN a.name as source, b.name as target, type(r) as label LIMIT $limit";
            session.run(eq, Map.of("kbId", kbId, "limit", maxNodes)).forEachRemaining(r ->
                edges.add(Map.of("source", r.get("source").asString(), "target", r.get("target").asString(), "label", r.get("label").asString())));
        }
        return Map.of("nodes", nodes, "edges", edges);
    }

    public Map<String, Object> expandGraph(String kbId, List<String> entities, int depth, int maxEntities) {
        List<Map<String, Object>> expEntities = new ArrayList<>();
        List<Map<String, Object>> relations = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        try (Session session = driver.session()) {
            for (String entity : entities) {
                String q = "MATCH path = (e:Entity {kbId: $kbId, name: $name})-[*1.." + depth + "]-(connected) RETURN path LIMIT $limit";
                session.run(q, Map.of("kbId", kbId, "name", entity, "limit", maxEntities)).forEachRemaining(r -> {
                    Path path = r.get("path").asPath();
                    for (Node n : path.nodes()) {
                        String name = n.get("name").asString();
                        if (visited.add(name))
                            expEntities.add(Map.of("id", n.elementId(), "name", name, "type", n.get("label").asString("")));
                    }
                    for (Relationship rel : path.relationships())
                        relations.add(Map.of("source", rel.startNodeElementId(), "target", rel.endNodeElementId(), "label", rel.type()));
                });
            }
        }
        String expandedQuery = String.join(" ", entities) + " " + expEntities.stream().map(e -> (String) e.get("name")).reduce("", (a, b) -> a + " " + b);
        return Map.of("entities", expEntities, "relations", relations, "expandedQuery", expandedQuery.trim());
    }

    public void createEntity(String kbId, String name, String type) {
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                tx.run("MERGE (e:Entity {name: $name, kbId: $kbId}) SET e.type = $type, e.label = $type",
                        Map.of("name", name, "kbId", kbId, "type", type != null ? type : "unknown"));
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to create entity: {} - {}", name, e.getMessage());
        }
    }

    public void createRelation(String kbId, String source, String target, String label) {
        try (Session session = driver.session()) {
            String safeLabel = label.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_");
            session.writeTransaction(tx -> {
                tx.run("MATCH (a:Entity {name: $source, kbId: $kbId}) MATCH (b:Entity {name: $target, kbId: $kbId}) MERGE (a)-[r:" + safeLabel + "]->(b)",
                        Map.of("source", source, "target", target, "kbId", kbId));
                return null;
            });
        } catch (Exception e) {
            log.warn("Failed to create relation: {} -> {} - {}", source, target, e.getMessage());
        }
    }
}
