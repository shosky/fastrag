package com.fastrag.module.retrieval.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.infra.graph.GraphStore;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.entity.TermLibrary;
import com.fastrag.module.platform.entity.TermRecord;
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
 *   <li>{@code suggest} - 查询建议：并入 {@code expandQueries} 统一实现（LLM 变体改写，失败降级原查询）</li>
 *   <li>{@code expandSynonyms} - 同义词扩展，调用 {@link TermService} 获取同义词列表拼入查询</li>
 *   <li>{@code applyQueryRules} - 查询规则改写：正则级最小接线（全角→半角 / 空白归一 / 术语别名→规范名）</li>
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

    /**
     * 查询建议：R6（P2-6）并入 {@link #expandQueries} 统一实现——复用同一 LLM 变体改写机制
     * （取 1 个变体），首个与原查询不同的变体作为建议查询；LLM 失败/无可用变体时降级为
     * 原查询且 reason 为空（等价无建议）。
     */
    @Override
    public Map<String, Object> suggest(String query) {
        return suggest(query, null);
    }

    @Override
    public Map<String, Object> suggest(String query, String llmModel) {
        var r = new HashMap<String, Object>();
        if (query == null || query.isBlank()) {
            r.put("suggestedQuery", query);
            r.put("reason", "");
            return r;
        }
        List<String> variants = expandQueries(query, 2, llmModel, null);
        String suggested = variants.stream()
                .filter(v -> !v.equals(query))
                // 双保险：expandQueries 已在源头过滤，这里再拦一次 LLM 失败文案，
                // 避免其被当作建议查询顶替真实 query（2026-10-09 检索事故根因）
                .filter(v -> !LlmService.isFailureText(v))
                .findFirst()
                .orElse(query);
        r.put("suggestedQuery", suggested);
        r.put("reason", suggested.equals(query) ? "" : "LLM 变体改写（expandQueries 统一实现）");
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

    /**
     * 查询规则改写：R6（P2-6）最小接线——检索主路径正则级改写（原占位实现移除）。
     * <ol>
     *   <li>halfwidth：全角 ASCII（字母/数字/标点/空格）归一为半角；</li>
     *   <li>whitespace：连续空白归一为单空格并去首尾；</li>
     *   <li>term_alias：术语库别名 → 规范术语名（长别名优先替换，避免子串嵌套部分替换）。</li>
     * </ol>
     * 术语库加载失败仅 warn 跳过（规则改写不阻断检索主路径）。
     */
    @Override
    public Map<String, Object> applyQueryRules(String query) {
        var r = new HashMap<String, Object>();
        List<String> applied = new ArrayList<>();
        if (query == null || query.isBlank()) {
            r.put("rewritten", query);
            r.put("appliedRules", applied);
            return r;
        }
        String rewritten = query;

        String halfwidth = toHalfWidth(rewritten);
        if (!halfwidth.equals(rewritten)) {
            applied.add("halfwidth");
            rewritten = halfwidth;
        }
        String collapsed = rewritten.replaceAll("\\s{2,}", " ").trim();
        if (!collapsed.equals(rewritten)) {
            applied.add("whitespace");
            rewritten = collapsed;
        }
        try {
            String termRewritten = applyTermAliases(rewritten);
            if (!termRewritten.equals(rewritten)) {
                applied.add("term_alias");
                rewritten = termRewritten;
            }
        } catch (Exception e) {
            log.warn("[QueryRules] term alias rewrite failed, skip: {}", e.getMessage());
        }

        r.put("rewritten", rewritten);
        r.put("appliedRules", applied);
        return r;
    }

    /** 全角 ASCII 区（U+FF01~U+FF5E）与全角空格（U+3000）归一为半角 */
    private static String toHalfWidth(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u3000') {
                sb.append(' ');
            } else if (c >= '\uFF01' && c <= '\uFF5E') {
                sb.append((char) (c - 0xFEE0));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 术语别名 → 规范术语名（启用状态术语库；长别名优先替换） */
    private String applyTermAliases(String query) {
        List<TermLibrary> libraries = termService.listLibraries();
        if (libraries == null || libraries.isEmpty()) {
            return query;
        }
        List<TermRecord> terms = new ArrayList<>();
        for (TermLibrary lib : libraries) {
            if (lib == null || lib.getId() == null) continue;
            List<TermRecord> libTerms = termService.listTerms(lib.getId());
            if (libTerms != null) terms.addAll(libTerms);
        }
        record AliasRule(String alias, String term) {}
        List<AliasRule> rules = new ArrayList<>();
        for (TermRecord tr : terms) {
            if (tr == null || tr.getStatus() == null || tr.getStatus() != 1) continue;
            String term = tr.getTerm();
            if (term == null || term.isBlank() || term.equals(query)) continue;
            if (tr.getAlias() == null || tr.getAlias().isBlank()) continue;
            for (String a : tr.getAlias().split("[,，]")) {
                String alias = a.trim();
                if (!alias.isEmpty() && !alias.equals(term) && query.contains(alias)) {
                    rules.add(new AliasRule(alias, term));
                }
            }
        }
        if (rules.isEmpty()) {
            return query;
        }
        rules.sort((x, y) -> Integer.compare(y.alias().length(), x.alias().length()));
        String rewritten = query;
        for (AliasRule rule : rules) {
            if (rewritten.contains(rule.alias())) {
                rewritten = rewritten.replace(rule.alias(), rule.term());
            }
        }
        return rewritten;
    }

    // ==================== 多查询改写（Multi-Query）====================

    /**
     * 多查询改写：LLM 生成 N-1 个语义互补的变体查询，配原查询做多路召回。
     *
     * <p>背景：分片边界切断的内容可能只被某一表述的查询命中，改写变体（同义词、
     * 缩写、换视角、原文措辞倾向）能提升漏召的找回概率。变体仅用于召回，
     * 不改动用户原始查询的日志与展示。</p>
     *
     * <p>降级链：LLM 调用失败 / 解析空 → 返回单元素 [原查询]（等价不启用）。</p>
     */
    @Override
    public List<String> expandQueries(String query, int count, String model, String kbId) {
        if (query == null || query.isBlank()) {
            return List.of(query);
        }
        int target = Math.max(1, Math.min(5, count > 0 ? count : 3));
        if (target <= 1) {
            return List.of(query);
        }

        // 解析模型配置（与 NER 解析一致；default 或查不到记录时走默认网关）
        String llmModel = (model != null && !model.isBlank()) ? model : "default";
        String apiUrl = null;
        String apiKey = null;
        ModelRecord modelRecord = null;
        if (!"default".equals(llmModel)) {
            modelRecord = modelRecordMapper.selectOne(
                    new LambdaQueryWrapper<ModelRecord>()
                            .eq(ModelRecord::getCode, llmModel)
                            .eq(ModelRecord::getStatus, "online")
                            .last("LIMIT 1"));
        }
        if (modelRecord != null) {
            apiUrl = modelRecord.getApiUrl();
            apiKey = modelRecord.getApiKeyRef();
            log.info("[MultiQuery] Resolved model '{}' -> apiUrl={}", llmModel, apiUrl);
        } else if (!"default".equals(llmModel)) {
            log.warn("[MultiQuery] Model '{}' not found in model table or offline, use default gateway", llmModel);
        }

        String prompt = """
                你是一名知识库检索专家。请将用户查询改写成 %d 个不同角度但语义相关的变体查询，
                用于提高知识库召回率（分片边界可能切断内容，需要不同表述互补命中）。

                要求：
                1. 每个变体替换说法：同义表达、缩写/全称、换视角、补细节方向，不要与原查询逐字相同
                2. 偏向"知识库原文中可能出现的措辞"，兼顾词面与语义召回
                3. 每行一个变体，不要编号、不要引号、不要解释性文字
                4. 变体保持与原查询同样的意图与主题

                原查询：
                %s
                """.formatted(target - 1, query);

        try {
            String response = llmService.chat(llmModel, prompt, apiUrl, apiKey);
            // LLM 网关失败时 doChatInternal 不抛异常而是返回错误文案（兼容既有调用方），
            // 必须在此识别并降级为原查询——否则错误文案会被当作改写变体
            // 顶替真实检索 query（2026-10-09 检索事故根因）
            if (LlmService.isFailureText(response)) {
                log.warn("[MultiQuery] LLM gateway failed ({}), fallback to original query: {}",
                        response, query);
                return List.of(query);
            }
            Set<String> variants = new LinkedHashSet<>();
            variants.add(query);
            for (String line : response.split("\\r?\\n")) {
                String v = line.trim()
                        .replaceAll("^\\s*\\d+[.、)]\\s*", "")
                        .replaceAll("^[\"']|[\"']$", "")
                        .trim();
                if (v.isEmpty() || v.equals(query) || v.length() < 2 || v.length() > 100) {
                    continue;
                }
                variants.add(v);
                if (variants.size() >= target) {
                    break;
                }
            }
            if (variants.size() >= 2) {
                List<String> result = new ArrayList<>(variants);
                log.info("[MultiQuery] query='{}' -> {} variants: {}", query,
                        result.size(), String.join(" | ", result));
                return result.size() > target ? result.subList(0, target) : result;
            }
            log.warn("[MultiQuery] LLM returned no usable variants for query='{}', fallback to original", query);
        } catch (Exception e) {
            log.warn("[MultiQuery] LLM expansion failed, fallback to original query: {}", e.getMessage());
        }
        return List.of(query);
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
     *
     * <p>冗余片段过滤（2026-10-21）：跨词边界的切分片段若已包含更短的保留 token
     * （如 "今天长沙天气" 切出的 "今天长/天长沙/长沙天" 均包含 "长沙"），对图谱匹配
     * 只是重复探测（{@code Neo4jGraphStore} UNWIND 匹配按实体去重），徒增匹配噪音，剔除；
     * 原始块/短中文块/英文词不参与本规则（原始块包含所有片段属预期，供双向 CONTAINS 命中）。</p>
     */
    private static final Pattern FALLBACK_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9]{2,}|[\\u4e00-\\u9fa5]+");

    private List<String> fallbackSplit(String query) {
        Set<String> tokens = new LinkedHashSet<>();
        // n-gram 切分产物（冗余过滤对象；原始块/整词不在其中）
        Set<String> fragments = new LinkedHashSet<>();
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
                            String gram = tok.substring(i, i + win);
                            tokens.add(gram);
                            fragments.add(gram);
                            if (++added >= 8) break outer;
                        }
                    }
                }
            } else {
                tokens.add(tok);
            }
        }
        // 冗余片段过滤：片段包含更短 token 即剔除（对照过滤前的完整 token 集，含原始块/整词）
        List<String> result = new ArrayList<>();
        for (String t : tokens) {
            if (fragments.contains(t)) {
                boolean redundant = false;
                for (String g : tokens) {
                    if (g.length() < t.length() && t.contains(g)) {
                        redundant = true;
                        break;
                    }
                }
                if (redundant) continue;
            }
            result.add(t);
        }
        // 限制总 token 数，避免噪音过多
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
