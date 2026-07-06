package com.fastrag.infra.graph;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱数据存储抽象接口
 *
 * <p>提供两种实现：
 * <ul>
 *   <li>{@link com.fastrag.infra.neo4j.Neo4jGraphStore} — Neo4j 驱动实现（需配置 neo4j.enabled=true）</li>
 *   <li>{@link com.fastrag.infra.graph.MysqlGraphStore} — MySQL 降级实现（默认，零额外依赖）</li>
 * </ul>
 */
public interface GraphStore {

    /**
     * 创建或更新实体（去重：同一 kbId + name 只保留一条）
     */
    void createEntity(String kbId, String name, String type);

    /**
     * 创建关系（去重：同一 kbId + source + target + label 只保留一条）
     */
    void createRelation(String kbId, String source, String target, String label);

    /**
     * 获取图谱数据（节点 + 边），用于前端可视化
     *
     * @param kbId          知识库 ID
     * @param maxNodes      最大节点数
     * @param excludeChunks 是否排除 chunk 节点
     * @return Map 包含 "nodes" 和 "edges" 两个 key
     */
    Map<String, Object> getGraphData(String kbId, int maxNodes, boolean excludeChunks);

    /**
     * 图谱扩展：给定实体列表，展开 1-2 跳邻居，用于检索增强
     *
     * @param kbId      知识库 ID
     * @param entities  查询中提取的实体名列表
     * @param depth     展开深度（1 或 2）
     * @param maxEntities 最大返回实体数
     * @return Map 包含 "entities", "relations", "expandedQuery"
     */
    Map<String, Object> expandGraph(String kbId, List<String> entities, int depth, int maxEntities);

    /**
     * 清空指定知识库的图谱数据（用于重建）
     */
    void clearGraph(String kbId);
}
