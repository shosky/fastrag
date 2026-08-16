package com.fastrag.module.retrieval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.service.TermService;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.retrieval.service.QueryEnhanceService;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询增强服务实现。
 *
 * <p>实现 {@link QueryEnhanceService} 接口，提供多种查询增强策略以提升检索效果。</p>
 *
 * <h3>核心实现逻辑：</h3>
 * <ul>
 *   <li>{@code suggest} - 查询建议（当前为占位实现，直接返回原始查询）</li>
 *   <li>{@code expandSynonyms} - 同义词扩展，调用 {@link TermService} 获取同义词列表拼入查询</li>
 *   <li>{@code applyQueryRules} - 查询规则改写（当前为占位实现，直接返回原始查询）</li>
 *   <li>{@code extractEntities} - NER 实体提取：通过 LLM 从查询中提取命名实体，
 *       无 NER 模型配置时降级为基于正则的简单分词（英文按词、中文按连续块 + n-gram 切分）</li>
 *   <li>{@code expandGraph} - 图谱扩展：先用 NER 提取实体，再通过 {@link GraphStore} 在知识图谱中
 *       展开邻居实体和关系，将实体名称和关系标签拼入原始查询生成增强查询文本</li>
 * </ul>
 *
 * <p>图谱扩展流程中的关键交互：依赖 platform 模块的 {@link ModelRecordMapper} 解析 NER 模型配置，
 * 依赖 infra 模块的 {@link GraphStore} 执行图谱查询，依赖 platform 模块的 {@link TermService} 进行同义词扩展。
 * 同时通过 Redis 进行知识库访问权限校验。</p>
 *
 * @see QueryEnhanceService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryEnhanceServiceImpl implements QueryEnhanceService {

    private final GraphStore graphStore;
    private final LlmService llmService;
    private final ModelRecordMapper modelRecordMapper;
    private final TermService termService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 校验当前用户对知识库的访问权限（body 传 kbId 的接口，KbAuthAspect 的 URI 提取不适用）
     */
    private void checkKbAccess(String kbId) {
        if (kbId == null || kbId.isBlank()) return;
        LoginUser user = SecurityUtil.getCurrentUser();
        // 超管或平台级 API Token 直接放行
        if (user.hasPermission("*") || user.getUserId().startsWith("api-token:")) return;
        String roleStr = redisTemplate.opsForValue().get("kb:acl:" + kbId + ":" + user.getUserId());
        if (roleStr == null) {
            throw BusinessException.forbidden("无知识库访问权限");
        }
    }

    @Override
    public Map<String, Object> suggest(String query) {
        var r = new HashMap<String, Object>();
        r.put("suggestedQuery", query);
        r.put("reason", "");
        return r;
    }

    @Override
    public Map<String, Object> expandSynonyms(String query) {
        List<String> addedTerms = termService.expandSynonyms(query);
        String expandedQuery = query;
        if (!addedTerms.isEmpty()) {
            expandedQuery = query + " " + String.join(" ", addedTerms);
        }
        var r = new HashMap<String, Object>();
        r.put("expandedQuery", expandedQuery);
        r.put("matchedTerms", List.of());
        r.put("addedTerms", addedTerms);
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

    /**
     * 简单分词降级：英文/数字按词（≥2 字符）；中文连续块保留原始块 + 2~4 字 n-gram 切分，
     * 配合图谱模糊匹配（CONTAINS）可命中 "故障根因分析" -> "故障根因分析（RCA）" 这类实体。
     */
    private static final Pattern FALLBACK_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9]{2,}|[\\u4e00-\\u9fa5]+");

    private List<String> fallbackSplit(String query) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = FALLBACK_TOKEN_PATTERN.matcher(query == null ? "" : query);
        while (m.find()) {
            String tok = m.group();
            if (tok.matches("[\\u4e00-\\u9fa5]+")) {
                int len = tok.length();
                if (len <= 4) {
                    tokens.add(tok);
                } else {
                    // 保留原始块，再补充 n-gram（每块最多 8 个，优先 2-gram 短词召回）
                    tokens.add(tok);
                    int added = 0;
                    outer:
                    for (int win = 2; win <= 4; win++) {
                        for (int i = 0; i + win <= len; i++) {
                            tokens.add(tok.substring(i, i + win));
                            if (++added >= 8) break outer;
                        }
                    }
                }
            } else {
                tokens.add(tok);
            }
        }
        // 限制总 token 数，避免噪音过多
        List<String> result = new ArrayList<>(tokens);
        return result.size() <= 20 ? result : result.subList(0, 20);
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

        // 知识库访问权限校验（/api/graph/expand 直达接口）
        checkKbAccess(kbId);

        // 1. NER 提取实体
        List<String> entities = extractEntities(query, nerModel, kbId);
        log.info("[GraphExpand] kb={}, query='{}', extracted entities: {}", kbId, query, entities);

        if (entities.isEmpty()) {
            log.info("[GraphExpand] kb={}, query='{}', no entities extracted (NER model={}, skipped)", kbId, query, nerModel);
            return Map.of("originalQuery", query, "expandedQuery", query,
                    "entities", List.of(), "relations", List.of());
        }

        // 2. 图谱展开
        Map<String, Object> graphResult = graphStore.expandGraph(kbId, entities, depth, maxEntities);
        int matchedEntities = ((List<?>) graphResult.getOrDefault("entities", List.of())).size();
        int matchedRelations = ((List<?>) graphResult.getOrDefault("relations", List.of())).size();
        log.info("[GraphExpand] kb={}, graph matched: {} entities, {} relations (depth={}, maxEntities={})",
                kbId, matchedEntities, matchedRelations, depth, maxEntities);

        // 3. 拼入 expandedQuery
        String expandedQuery = buildExpandedQuery(query, graphResult);
        if (expandedQuery.equals(query.trim())) {
            log.info("[GraphExpand] kb={}, query='{}', graph expansion empty (no matched entity names/labels appended)", kbId, query);
        } else {
            log.info("[GraphExpand] kb={}, expandedQuery='{}'", kbId, expandedQuery);
        }

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
