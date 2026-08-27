package com.fastrag.module.retrieval.service;

/**
 * 查询增强服务接口。
 *
 * <p>定义知识检索的查询增强能力，通过多种策略提升检索效果：</p>
 * <ul>
 *   <li>{@code suggest} - 查询建议/纠错</li>
 *   <li>{@code expandSynonyms} - 同义词扩展</li>
 *   <li>{@code applyQueryRules} - 查询规则改写（术语归一化）</li>
 *   <li>{@code expandGraph} - 知识图谱扩展，用 NER 提取实体后在图谱中展开邻居，增强检索 query</li>
 *   <li>{@code expandQueries} - 多查询改写（Multi-Query）：LLM 生成 N-1 个语义互补的变体查询，
 *       配合原查询多路召回后融合，弥补单一查询在分片边界处漏召（切断内容靠变体表述命中）</li>
 * </ul>
 *
 * <p>图谱扩展支持指定 NER 模型，不指定时走降级分词策略。</p>
 */
import java.util.List;
import java.util.Map;

public interface QueryEnhanceService {
    Map<String, Object> suggest(String query);
    Map<String, Object> expandSynonyms(String query);
    Map<String, Object> applyQueryRules(String query);
    Map<String, Object> expandGraph(String kbId, String query, int depth, int maxEntities);
    Map<String, Object> expandGraph(String kbId, String query, int depth, int maxEntities, String nerModel);

    /**
     * 多查询改写：用 LLM 将原查询改写为 {@code count} 个语义等价的变体查询（含原查询）。
     *
     * @param query 原始查询
     * @param count 目标查询总数（含原查询，2~5）
     * @param model LLM 模型 code，空/空白时使用系统默认网关
     * @param kbId  知识库 ID（解析模型配置用，可为空）
     * @return 去重后的查询列表，至少包含原查询；任一环节失败返回单元素列表（降级为单查询召回）
     */
    List<String> expandQueries(String query, int count, String model, String kbId);
}
