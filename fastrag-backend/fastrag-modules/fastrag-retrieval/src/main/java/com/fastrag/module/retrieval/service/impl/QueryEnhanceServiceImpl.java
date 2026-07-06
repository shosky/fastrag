package com.fastrag.module.retrieval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.retrieval.service.QueryEnhanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 查询增强服务实现
 *
 * <p>提供：
 * <ul>
 *   <li>suggest — 查询建议（占位）</li>
 *   <li>expandSynonyms — 同义词扩展（占位）</li>
 *   <li>applyQueryRules — 规则改写（占位）</li>
 *   <li>extractEntities — 用 LLM 从 query 中提取实体（NER）</li>
 *   <li>expandGraph — 用提取的实体在图谱中展开邻居，拼入检索 query</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryEnhanceServiceImpl implements QueryEnhanceService {

    private final GraphStore graphStore;
    private final LlmService llmService;
    private final ModelRecordMapper modelRecordMapper;

    @Override
    public Map<String, Object> suggest(String query) {
        var r = new HashMap<String, Object>();
        r.put("suggestedQuery", query);
        r.put("reason", "");
        return r;
    }

    @Override
    public Map<String, Object> expandSynonyms(String query) {
        var r = new HashMap<String, Object>();
        r.put("expandedQuery", query);
        r.put("matchedTerms", List.of());
        r.put("addedTerms", List.of());
        return r;
    }

    @Override
    public Map<String, Object> applyQueryRules(String query) {
        var r = new HashMap<String, Object>();
        r.put("rewritten", query);
        r.put("appliedRules", List.of());
        return r;
    }

    // ==================== NER + 图谱扩展 ====================

    /**
     * 用 LLM 从 query 中提取命名实体
     *
     * @param query     用户查询
     * @param nerModel  模型名，为空时不调用 LLM 直接走降级
     * @param kbId      知识库 ID（用于解析模型配置）
     * @return 提取到的实体名称列表
     */
    public List<String> extractEntities(String query, String nerModel, String kbId) {
        if (query == null || query.isBlank()) return List.of();

        // 没有配置 NER 模型，跳过 LLM 调用的降级到简单分词
        if (nerModel == null || nerModel.isBlank()) {
            log.info("[NER] No NER model configured, skip LLM extraction");
            return fallbackSplit(query);
        }

        String model = nerModel;
        String prompt = """
                请从以下查询文本中提取命名实体，返回JSON数组格式。
                只返回实体名称，不要其他内容。
                如果文本中没有明确实体，返回空数组 []。

                示例：
                输入："苹果公司发布了iPhone 15"
                输出：["苹果公司", "iPhone 15"]

                输入："北京在哪里？"
                输出：["北京"]

                查询文本：
                """ + query;

        // 解析模型配置
        String apiUrl = null;
        String apiKey = null;
        ModelRecord modelRecord = modelRecordMapper.selectOne(
                new LambdaQueryWrapper<ModelRecord>()
                        .eq(ModelRecord::getCode, model)
                        .eq(ModelRecord::getStatus, "online")
                        .last("LIMIT 1"));
        if (modelRecord != null) {
            apiUrl = modelRecord.getApiUrl();
            apiKey = modelRecord.getApiKeyRef();
            log.info("[NER] Resolved model '{}' -> apiUrl={}", model, apiUrl);
        } else {
            log.warn("[NER] Model '{}' not found in model table or offline, skip NER extraction", model);
            return fallbackSplit(query);
        }

        try {
            String response = llmService.chat(model, prompt, apiUrl, apiKey);
            String json = response.trim();
            // 清理 markdown code block 包裹
            if (json.startsWith("```")) {
                json = json.replaceAll("```json?", "").replaceAll("```", "").trim();
            }
            // 提取 JSON 数组
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start >= 0 && end > start) {
                json = json.substring(start, end + 1);
            }
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            if (root.isArray()) {
                List<String> entities = new ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    String name = node.asText(null);
                    if (name != null && !name.isBlank() && name.length() >= 2) {
                        entities.add(name.trim());
                    }
                }
                return entities;
            }
        } catch (Exception e) {
            log.warn("NER extraction failed, fallback to simple split: {}", e.getMessage());
        }
        // 降级：简单分词
        return fallbackSplit(query);
    }

    /** 简单分词降级 */
    private List<String> fallbackSplit(String query) {
        return Arrays.stream(query.split("\\s+"))
                .filter(s -> s.length() >= 2)
                .limit(3)
                .toList();
    }

    /**
     * 图谱扩展：用 NER 提取的实体在图谱中展开邻居，拼入检索 query
     *
     * @param kbId         知识库 ID
     * @param query        原始查询
     * @param depth        展开深度
     * @param maxEntities  最大实体数
     * @param nerModel     NER 模型
     * @return 增强后的查询结果
     */
    @Override
    public Map<String, Object> expandGraph(String kbId, String query, int depth, int maxEntities) {
        return expandGraph(kbId, query, depth, maxEntities, null);
    }

    /**
     * 图谱扩展（带 NER 模型参数）
     */
    public Map<String, Object> expandGraph(String kbId, String query, int depth, int maxEntities, String nerModel) {
        if (query == null || query.isBlank()) {
            return Map.of("originalQuery", query, "expandedQuery", query,
                    "entities", List.of(), "relations", List.of());
        }

        // 1. NER 提取实体
        List<String> entities = extractEntities(query, nerModel, kbId);
        log.info("[GraphExpand] kb={}, query={}, extracted entities: {}", kbId, query, entities);

        if (entities.isEmpty()) {
            return Map.of("originalQuery", query, "expandedQuery", query,
                    "entities", List.of(), "relations", List.of());
        }

        // 2. 图谱展开
        Map<String, Object> graphResult = graphStore.expandGraph(kbId, entities, depth, maxEntities);

        // 3. 拼入 expandedQuery
        String expandedQuery = buildExpandedQuery(query, graphResult);

        return Map.of(
                "originalQuery", query,
                "expandedQuery", expandedQuery,
                "entities", graphResult.getOrDefault("entities", List.of()),
                "relations", graphResult.getOrDefault("relations", List.of())
        );
    }

    /**
     * 将图谱展开结果拼入原始 query
     * 策略：将图谱中匹配到的实体名称和关系标签拼到原始 query 后面
     */
    private String buildExpandedQuery(String originalQuery, Map<String, Object> graphResult) {
        StringBuilder sb = new StringBuilder(originalQuery);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities = (List<Map<String, Object>>) graphResult.getOrDefault("entities", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations = (List<Map<String, Object>>) graphResult.getOrDefault("relations", List.of());

        // 拼入实体名（去重）
        Set<String> entityNames = new LinkedHashSet<>();
        for (Map<String, Object> e : entities) {
            String name = (String) e.get("name");
            if (name != null && !name.isBlank() && !originalQuery.contains(name)) {
                entityNames.add(name);
            }
        }

        if (!entityNames.isEmpty()) {
            sb.append(" ").append(String.join(" ", entityNames));
        }

        // 拼入关系标签（去重）
        Set<String> labels = new LinkedHashSet<>();
        for (Map<String, Object> r : relations) {
            String label = (String) r.get("label");
            if (label != null && !label.isBlank()) {
                labels.add(label);
            }
        }

        if (!labels.isEmpty()) {
            sb.append(" ").append(String.join(" ", labels));
        }

        return sb.toString().trim();
    }
}
