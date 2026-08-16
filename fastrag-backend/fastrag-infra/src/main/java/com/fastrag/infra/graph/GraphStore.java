package com.fastrag.infra.graph;

/**
 * 知识图谱数据存储抽象接口，定义知识图谱的统一操作契约（参考 Yuxi 三存储架构）。
 *
 * <p>本接口是知识图谱存储层的核心抽象，上层的知识库处理流水线（知识提取、三元组构建、图谱构建等）
 * 均依赖此接口操作图谱数据，不直接耦合具体存储引擎。
 *
 * <p>提供两种实现，运行时通过 Spring 条件装配自动选择：
 * <ul>
 *   <li>{@link com.fastrag.infra.neo4j.Neo4jGraphStore} — Neo4j 图数据库实现，支持原生图查询、
 *       多跳展开、向量索引检索，适合大规模图谱场景（需配置 {@code neo4j.enabled=true}）</li>
 *   <li>{@link com.fastrag.infra.graph.MysqlGraphStore} — MySQL 关系型降级实现，利用 MySQL 表模拟图结构，
 *       零额外依赖，适合中小规模图谱场景（默认激活，通过 {@code @ConditionalOnMissingBean} 生效）</li>
 * </ul>
 *
 * <p>接口能力覆盖图谱全生命周期：
 * <ul>
 *   <li>基础 CRUD：创建实体、创建关系（均支持去重）</li>
 *   <li>图谱查询：获取可视化数据、关键词搜索、多跳邻居展开</li>
 *   <li>Mention 追踪：记录实体/三元组与文档 Chunk 的关联关系，支持文件级和 Chunk 级的精准回收</li>
 *   <li>图谱管理：清空、按文件删除、按 Chunk 删除、孤立节点回收</li>
 *   <li>实体向量检索：更新实体 embedding 并支持向量相似度查询（对标 LightRAG entities_vdb）</li>
 * </ul>
 */
import java.util.List;
import java.util.Map;
import java.util.Collections;
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
    default void createEntity(String kbId, String entityId, String name, String normalizedName, String type) {
        createEntity(kbId, entityId, name, normalizedName, type, null, null);
    }

    /**
     * 创建或更新实体（完整字段，含描述与属性）
     *
     * @param kbId          知识库 ID
     * @param entityId      确定性实体 ID（SHA-256 截断）
     * @param name          原始显示名称
     * @param normalizedName 标准化名称
     * @param type          实体类型/标签
     * @param description   实体描述（可空；已存在的非空描述不被覆盖）
     * @param attributes    实体属性 JSON 字符串（键值对数组，可空）
     */
    void createEntity(String kbId, String entityId, String name, String normalizedName, String type,
                      String description, String attributes);

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
    default void createRelation(String kbId, String tripleId, String source, String target, String label, String content) {
        createRelation(kbId, tripleId, null, source, null, target, label, content);
    }

    /**
     * 创建关系（完整字段，含端点实体 ID，消除"边按名称引用"的悬空/错连问题）
     *
     * @param kbId          知识库 ID
     * @param tripleId      确定性三元组 ID（SHA-256 截断）
     * @param sourceId      源实体确定性 ID（可空，为空时按规范化名反查）
     * @param source        源实体名称
     * @param targetId      目标实体确定性 ID（可空，为空时按规范化名反查）
     * @param target        目标实体名称
     * @param label         关系类型
     * @param content       关系显示文本
     */
    void createRelation(String kbId, String tripleId, String sourceId, String source,
                        String targetId, String target, String label, String content);

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

    /**
     * 获取实体类型分布统计（类型名 -> 数量），用于前端类型分布展示
     *
     * @param kbId 知识库 ID
     * @return Map，key 为实体类型名，value 为该类型的实体数量
     */
    default Map<String, Long> countEntitiesByType(String kbId) { return Collections.emptyMap(); }

    /**
     * 按规范化名称反查实体 ID（跨 chunk 引用解析用；同名不同型实体取任意一条）
     *
     * @param kbId          知识库 ID
     * @param normalizedName 规范化名称
     * @return 实体 ID；不存在返回 null
     */
    default String findEntityId(String kbId, String normalizedName) { return null; }

    /**
     * 列出全部 Entity-Chunk 提及关系（PPR 检索用；存储无关：MySQL 查 mention 表，Neo4j 查 MENTIONS 边）
     *
     * @param kbId 知识库 ID
     * @return [{entityId, chunkId}] 列表
     */
    default List<Map<String, String>> listEntityMentions(String kbId) { return Collections.emptyList(); }

    // ==================== Mention 追踪 ====================

    /**
     * 创建 Chunk 节点（用于 Neo4j 可视化）
     *
     * @param kbId    知识库 ID
     * @param chunkId Chunk ID
     * @param fileId  所属文件 ID（用于按文件删除时的匹配）
     * @param content Chunk 内容摘要
     */
    void createChunk(String kbId, String chunkId, String fileId, String content);

    /**
     * 创建实体-Chunk 关联（MENTIONS 边）
     * 通过实体名称匹配（与 Entity 节点 MERGE 键一致）
     *
     * @param kbId       知识库 ID
     * @param entityName 实体原始名称（用于 MATCH Entity 节点）
     * @param entityId   实体确定性哈希 ID（MySQL 降级存储的 entity_id，须与 kb_graph_entity.entity_id 一致）
     * @param chunkId    Chunk ID
     * @param fileId     所属文件 ID（MySQL 降级存储按文件删除 mention 的依据）
     */
    void createEntityMention(String kbId, String entityName, String entityId, String chunkId, String fileId);

    /**
     * 创建三元组-Chunk 关联
     *
     * @param kbId     知识库 ID
     * @param tripleId 三元组 ID
     * @param chunkId  Chunk ID
     * @param fileId   所属文件 ID（MySQL 降级存储按文件删除 mention 的依据）
     */
    void createTripleMention(String kbId, String tripleId, String chunkId, String fileId);

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

    /**
     * 删除指定 chunk 关联的图谱数据（MENTIONS 关系），并回收因删除而变为孤立的实体/关系
     *
     * @param kbId    知识库 ID
     * @param chunkId Chunk ID
     */
    void deleteChunkGraph(String kbId, String chunkId);

    /**
     * 重命名 Chunk 节点的 chunkId（用于索引平移后同步图谱引用）
     *
     * @param kbId       知识库 ID
     * @param oldChunkId 旧 Chunk ID
     * @param newChunkId 新 Chunk ID
     */
    void renameChunkId(String kbId, String oldChunkId, String newChunkId);

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

    // ==================== 实体向量检索（对标 LightRAG entities_vdb）====================

    /**
     * 更新实体 embedding（用于图谱向量检索；维度不符/存储不支持时静默跳过）
     *
     * @param kbId       知识库 ID
     * @param entityName 实体名（MERGE 键）
     * @param embedding  向量
     */
    default void updateEntityEmbedding(String kbId, String entityName, List<Float> embedding) {}

    /**
     * 按向量检索实体（语义匹配，对标 LightRAG entities_vdb.query）
     *
     * @param kbId      知识库 ID
     * @param embedding 查询向量
     * @param topK      返回数量
     * @return 实体列表，每项含 name / entityType / score
     */
    default List<Map<String, Object>> searchEntitiesByVector(String kbId, List<Float> embedding, int topK) {
        return Collections.emptyList();
    }

    /**
     * 查询尚未生成 embedding 的实体名（用于存量回填）
     *
     * @param kbId  知识库 ID
     * @param limit 最大返回数量
     * @return 实体名列表
     */
    default List<String> listEntitiesWithoutEmbedding(String kbId, int limit) {
        return Collections.emptyList();
    }

    /**
     * 清理孤立节点：无任何 chunk 引用的 Entity、无任何 TripleMention 引用的 RELATION
     * （构建完成后调用，避免删除文件/编辑 chunk 后残留孤立数据）
     *
     * @param kbId 知识库 ID
     */
    default void cleanupOrphanNodes(String kbId) {}
}
