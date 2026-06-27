package com.fastrag.infra.neo4j;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Neo4j 图数据库服务 (Stub 实现)
 * 图谱功能已禁用，仅保留接口兼容性。
 */
@Slf4j
@Service
public class Neo4jService {

    public Map<String, Object> getGraphData(String kbId, int maxNodes, boolean excludeChunks) {
        log.debug("Neo4j disabled - returning empty graph for: {}", kbId);
        return Map.of("nodes", List.of(), "edges", List.of());
    }

    public Map<String, Object> expandGraph(String kbId, List<String> entities, int depth, int maxEntities) {
        log.debug("Neo4j disabled - returning empty expandGraph for: {}", kbId);
        String expandedQuery = String.join(" ", entities);
        return Map.of("entities", List.of(), "relations", List.of(), "expandedQuery", expandedQuery);
    }

    public void createEntity(String kbId, String name, String type) {
        log.debug("Neo4j disabled - skip createEntity: {}", name);
    }

    public void createRelation(String kbId, String source, String target, String label) {
        log.debug("Neo4j disabled - skip createRelation: {} -> {}", source, target);
    }
}
