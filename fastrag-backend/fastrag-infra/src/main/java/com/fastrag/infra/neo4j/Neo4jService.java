package com.fastrag.infra.neo4j;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Neo4j 图数据库服务（向后兼容委托层）。
 *
 * <p>核心职责：作为 {@link Neo4jGraphStore} 的委托包装器，保留原有服务接口不变，
 * 同时支持 Neo4j 未启用时的优雅降级。
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>通过 Spring {@link ObjectProvider} 延迟获取 {@link Neo4jGraphStore} Bean，
 *       当 Neo4j 未启用（{@code neo4j.enabled=false}）时，{@code getIfAvailable()} 返回 null，
 *       此时所有方法返回空结果或静默跳过</li>
 *   <li>封装了 getGraphData、expandGraph、createEntity、createRelation 四个核心方法，
 *       仅暴露图谱构建和查询中最常用的操作</li>
 * </ul>
 *
 * <p>使用建议：新代码应直接注入 {@link GraphStore} 接口（支持 Neo4j 和 MySQL 两种实现的自动切换），
 * 本类仅用于兼容已有的调用方代码。
 *
 * <p>与其他模块的交互：被知识库模块的 Controller 和 Service 层调用，提供图谱数据查询和写入入口。
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

    public void createEntity(String kbId, String entityId, String name, String normalizedName, String type,
                             String description, String attributes) {
        Neo4jGraphStore store = neo4jGraphStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("Neo4j disabled - skip createEntity: {}", name);
            return;
        }
        store.createEntity(kbId, entityId, name, normalizedName, type, description, attributes);
    }

    public void createRelation(String kbId, String tripleId, String sourceId, String source,
                               String targetId, String target, String label, String content) {
        Neo4jGraphStore store = neo4jGraphStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("Neo4j disabled - skip createRelation: {} -> {}", source, target);
            return;
        }
        store.createRelation(kbId, tripleId, sourceId, source, targetId, target, label, content);
    }
}
