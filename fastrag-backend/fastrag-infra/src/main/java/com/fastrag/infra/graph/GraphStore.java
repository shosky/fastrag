package com.fastrag.infra.graph;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱数据存储抽象接口（参考 Yuxi GraphStore 三存储架构）
 *
 * <p>提供两种实现：
 * <ul>
 *   <li>{@link com.fastrag.infra.neo4j.Neo4jGraphStore} — Neo4j 驱动实现（需配置 neo4j.enabled=true）</li>
 *   <li>{@link com.fastrag.infra.graph.MysqlGraphStore} — MySQL 降级实现（默认，零额外依赖）</li>
 * </ul>
 */
public interface GraphStore {

    // ==================== 基础 CRUD ====================

    /**
     * 创建或更新实体（去重：同一 kbId + normalizedName + entityType 只保留一条）
     *
     * @param kbId          知识库 ID
     * @param entityId      确定性实体 ID（SHA-256 截断）
     * @param name          原始显示名称
     * @param normalizedName 标准化名称
     * @param type          实体类型/标签
     */
    void createEntity(String kbId, String entityId, String name, String normalizedName, String type);

    /**
     * 创建关系（去重：同一 kbId + source + target + label 只保留一条）
     *
     * @param kbId          知识库 ID
     * @param tripleId      确定性三元组 ID（SHA-256 截断）
     * @param source        源实体名称
     * @param target        目标实体名称
     * @param label         关系类型
     * @param content       关系显示文本
     */
    void createRelation(String kbId, String tripleId, String source, String target, String label, String content);

    // ==================== 图谱查询 ====================

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
     * 关键词子图查询：多字段模糊匹配（参考 Yuxi keyword subgraph query）
     *
     * @param kbId      知识库 ID
     * @param keyword   搜索关键词
     * @param maxNodes  最大返回节点数
     * @return Map 包含 "nodes" 和 "edges"
     */
    Map<String, Object> searchNodes(String kbId, String keyword, int maxNodes);

    /**
     * 获取图谱中所有实体类型标签列表
     *
     * @param kbId 知识库 ID
     * @return 类型标签列表，如 ["人物", "组织", "地点"]
     */
    List<String> getLabels(String kbId);

    // ==================== Mention 追踪 ====================

    /**
     * 创建 Chunk 节点（用于 Neo4j 可视化）
     *
     * @param kbId    知识库 ID
     * @param chunkId Chunk ID
     * @param content Chunk 内容摘要
     */
    void createChunk(String kbId, String chunkId, String content);

    /**
     * 创建实体-Chunk 关联（MENTIONS 边）
     *
     * @param kbId     知识库 ID
     * @param entityId 实体 ID
     * @param chunkId  Chunk ID
     */
    void createEntityMention(String kbId, String entityId, String chunkId);

    /**
     * 创建三元组-Chunk 关联
     *
     * @param kbId     知识库 ID
     * @param tripleId 三元组 ID
     * @param chunkId  Chunk ID
     */
    void createTripleMention(String kbId, String tripleId, String chunkId);

    // ==================== 图谱管理 ====================

    /**
     * 清空指定知识库的图谱数据（用于重建）
     */
    void clearGraph(String kbId);

    /**
     * 删除指定文件关联的图谱数据，回收孤立实体/关系（参考 Yuxi GraphDeleter）
     *
     * @param kbId   知识库 ID
     * @param fileId 文件 ID
     */
    void deleteFileGraph(String kbId, String fileId);

    // ==================== 统计查询 ====================

    /**
     * 统计图谱中的实体数量
     *
     * @param kbId 知识库 ID
     * @return 实体总数
     */
    default long countEntities(String kbId) { return 0; }

    /**
     * 统计图谱中的关系数量
     *
     * @param kbId 知识库 ID
     * @return 关系总数
     */
    default long countRelations(String kbId) { return 0; }
}
