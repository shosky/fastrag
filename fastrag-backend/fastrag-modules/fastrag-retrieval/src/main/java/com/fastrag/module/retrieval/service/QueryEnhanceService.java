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
 * </ul>
 *
 * <p>图谱扩展支持指定 NER 模型，不指定时走降级分词策略。</p>
 */
import java.util.Map;

public interface QueryEnhanceService {
    Map<String, Object> suggest(String query);
    Map<String, Object> expandSynonyms(String query);
    Map<String, Object> applyQueryRules(String query);
    Map<String, Object> expandGraph(String kbId, String query, int depth, int maxEntities);
    Map<String, Object> expandGraph(String kbId, String query, int depth, int maxEntities, String nerModel);
}
