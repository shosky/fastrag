package com.fastrag.infra.neo4j;

import com.fastrag.infra.graph.GraphStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Neo4j 图数据库服务（向后兼容委托）
 *
 * <p>委托给 {@link Neo4jGraphStore} 实际执行，保留原有接口不变。
 * 当 neo4j.enabled=false 时，Neo4jGraphStore 不会被创建，此类使用空实现降级。
 * 建议新代码直接注入 {@link GraphStore}。
 */
@Service
@RequiredArgsConstructor
public class Neo4jService {

    private static final Logger log = LoggerFactory.getLogger(Neo4jService.class);

    private final ObjectProvider<Neo4jGraphStore> neo4jGraphStoreProvider;

    @Value("${neo4j.enabled:false}")
    private boolean enabled;

    public Map<String, Object> getGraphData(String kbId, int maxNodes, boolean excludeChunks) {
        Neo4jGraphStore store = neo4jGraphStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("Neo4j disabled - returning empty graph for: {}", kbId);
            return Map.of("nodes", List.of(), "edges", List.of());
        }
        return store.getGraphData(kbId, maxNodes, excludeChunks);
    }

    public Map<String, Object> expandGraph(String kbId, List<String> entities, int depth, int maxEntities) {
        Neo4jGraphStore store = neo4jGraphStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("Neo4j disabled - returning empty expandGraph for: {}", kbId);
            return Map.of("entities", List.of(), "relations", List.of(), "expandedQuery",
                    entities != null ? String.join(" ", entities) : "");
        }
        return store.expandGraph(kbId, entities, depth, maxEntities);
    }

    public void createEntity(String kbId, String entityId, String name, String normalizedName, String type) {
        Neo4jGraphStore store = neo4jGraphStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("Neo4j disabled - skip createEntity: {}", name);
            return;
        }
        store.createEntity(kbId, entityId, name, normalizedName, type);
    }

    public void createRelation(String kbId, String tripleId, String source, String target, String label, String content) {
        Neo4jGraphStore store = neo4jGraphStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("Neo4j disabled - skip createRelation: {} -> {}", source, target);
            return;
        }
        store.createRelation(kbId, tripleId, source, target, label, content);
    }
}
