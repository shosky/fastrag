package com.fastrag.module.graph.service.impl;

/**
 * 基准测试题目异步生成助手。
 *
 * <p>单独抽取为独立的Spring Bean，确保 {@link org.springframework.scheduling.annotation.Async} 注解
 * 通过Spring AOP代理生效，避免在 {@link BenchmarkServiceImpl} 同类内部调用时异步机制失效的问题。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>根据构建方法（vector/graph）构建不同的LLM提示词</li>
 *   <li>vector模式：基于知识库文档chunk生成问答对，LLM从文档片段中提取问题和答案</li>
 *   <li>graph模式：基于知识图谱实体和关系生成问答对，LLM利用图谱结构信息出题</li>
 *   <li>解析LLM返回的JSON格式题目数据，持久化到数据库</li>
 *   <li>调用失败时自动重置基准测试的题目计数，避免前端显示误导信息</li>
 * </ul>
 *
 * <p>关键实现细节：</p>
 * <ul>
 *   <li>使用流式收集+长超时（默认300s）避免外网API网络抖动导致的超时</li>
 *   <li>输出上限设为8192 tokens，避免JSON截断</li>
 *   <li>支持从model表解析自定义模型的API URL和Key，支持自定义路由</li>
 *   <li>使用 {@link org.springframework.transaction.support.TransactionTemplate} 确保题目插入和计数更新的原子性</li>
 * </ul>
 *
 * @see BenchmarkServiceImpl
 * @see BenchmarkResponseParser
 */
import cn.hutool.json.JSONUtil;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.module.graph.entity.KbBenchmark;
import com.fastrag.module.graph.entity.KbBenchmarkQuestion;
import com.fastrag.module.graph.mapper.KbBenchmarkMapper;
import com.fastrag.module.graph.mapper.KbBenchmarkQuestionMapper;
import com.fastrag.module.graph.model.BenchmarkConfig;
import com.fastrag.module.graph.util.BenchmarkResponseParser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基准问题异步生成助手
 *
 * <p>单独抽取为 Spring Bean，确保 {@link Async} 注解通过代理生效，
 * 避免同类自调用时异步失效的问题。
 */
@Component
@RequiredArgsConstructor
public class BenchmarkGenerationHelper {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkGenerationHelper.class);

    /**
     * 基准生成 LLM 总超时（秒），默认 300s。
     * 生成 10 个问答对输出长（3K+ tokens），且外网 API（SiliconFlow）存在网络抖动，
     * 必须走流式收集 + 长超时。
     */
    @Value("${benchmark.llm-timeout:300}")
    private int benchmarkLlmTimeoutSeconds;

    /** 基准生成输出上限：10 个中文问答对约需 3K+ tokens，默认 2048 会截断 JSON */
    private static final int BENCHMARK_MAX_TOKENS = 8192;

    private final KbBenchmarkMapper mapper;
    private final KbBenchmarkQuestionMapper questionMapper;
    private final LlmService llmService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Async
    public void generateQuestions(String benchmarkId, String kbId, int questionCount,
                                   String buildMethod, BenchmarkConfig config) {
        try {
            String llmModel = config.getLlmModel();
            String model = llmModel != null && !llmModel.isBlank() ? llmModel : "default";

            String prompt;
            if ("graph".equalsIgnoreCase(buildMethod)) {
                prompt = buildGraphPrompt(kbId, questionCount);
            } else {
                prompt = buildVectorPrompt(kbId, questionCount, config);
            }

            String response = callLlm(model, prompt);
            List<Map<String, Object>> questions = parseResponse(benchmarkId, response);

            if (questions.isEmpty()) {
                resetQuestionCount(benchmarkId);
                return;
            }

            List<KbBenchmarkQuestion> entities = new ArrayList<>();
            boolean crossBoundary = Boolean.TRUE.equals(config.getCrossBoundary());
            for (int i = 0; i < questions.size(); i++) {
                Map<String, Object> q = questions.get(i);
                KbBenchmarkQuestion question = new KbBenchmarkQuestion();
                question.setBenchmarkId(benchmarkId);
                question.setQuestionIndex(i);
                question.setQuestion((String) q.get("question"));
                question.setGoldAnswer((String) q.get("goldAnswer"));
                question.setCrossBoundary(crossBoundary ? 1 : 0);
                Object goldChunks = q.get("goldChunks");
                if (goldChunks instanceof List) {
                    question.setGoldChunks(JSONUtil.toJsonStr(goldChunks));
                } else if (goldChunks instanceof String) {
                    question.setGoldChunks((String) goldChunks);
                }
                entities.add(question);
            }

            transactionTemplate.executeWithoutResult(status -> {
                for (KbBenchmarkQuestion q : entities) {
                    questionMapper.insert(q);
                }
                KbBenchmark benchmark = mapper.selectById(benchmarkId);
                if (benchmark != null) {
                    benchmark.setQuestionCount(entities.size());
                    mapper.updateById(benchmark);
                }
            });

            log.info("[Benchmark] Generated {} questions for benchmark: {}, method: {}",
                    entities.size(), benchmarkId, buildMethod);

        } catch (Exception e) {
            log.error("[Benchmark] Failed to generate questions for benchmark: {}", benchmarkId, e);
            // 失败时清零计数：记录创建时写入了请求题数，若实际 0 道题则 UI 会误导
            resetQuestionCount(benchmarkId);
        }
    }

    /**
     * 解析 LLM 响应。空响应/错误串/无 JSON 数组 → 记警告并返回空列表；
     * JSON 损坏 → 记录响应预览，避免再次踩 "A JSONArray text must start with '['"。
     */
    private List<Map<String, Object>> parseResponse(String benchmarkId, String response) {
        if (response == null || response.isBlank()) {
            log.warn("[Benchmark] LLM returned empty response for benchmark: {}", benchmarkId);
            return List.of();
        }
        try {
            List<Map<String, Object>> questions = BenchmarkResponseParser.parseQuestions(response);
            if (questions.isEmpty()) {
                log.warn("[Benchmark] No JSON array found in LLM response for benchmark: {}, preview: {}",
                        benchmarkId, preview(response));
            }
            return questions;
        } catch (Exception e) {
            log.warn("[Benchmark] Failed to parse LLM response for benchmark: {}, preview: {}, error: {}",
                    benchmarkId, preview(response), e.getMessage());
            return List.of();
        }
    }

    /** 生成失败时把基准计数清零，避免 UI 显示"10 道题"但实际为 0 */
    private void resetQuestionCount(String benchmarkId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                KbBenchmark benchmark = mapper.selectById(benchmarkId);
                if (benchmark != null && benchmark.getQuestionCount() != 0) {
                    benchmark.setQuestionCount(0);
                    mapper.updateById(benchmark);
                }
            });
        } catch (Exception e) {
            log.warn("[Benchmark] Failed to reset question count for benchmark: {}", benchmarkId, e.getMessage());
        }
    }

    private static String preview(String response) {
        return response.length() > 300 ? response.substring(0, 300) + "..." : response;
    }

    /**
     * 调用 LLM：从 model 表解析模型的 API URL，支持自定义路由。
     * 使用流式收集 + 长超时（chatWithTimeout），避免非流式 30s 阻塞超时。
     */
    private String callLlm(String model, String prompt) {
        if (model == null || model.isBlank() || "default".equals(model)) {
            return llmService.chatWithTimeout(model != null ? model : "default", prompt,
                    null, null, false, benchmarkLlmTimeoutSeconds, BENCHMARK_MAX_TOKENS);
        }

        // 从 model 表查询模型的 API URL 和 Key
        try {
            List<Map<String, Object>> records = jdbcTemplate.queryForList(
                    "SELECT api_url, api_key_ref FROM model WHERE code = ? LIMIT 1", model);
            if (!records.isEmpty()) {
                Map<String, Object> record = records.get(0);
                String apiUrl = (String) record.get("api_url");
                String apiKeyRef = (String) record.get("api_key_ref");
                return llmService.chatWithTimeout(model, prompt,
                        apiUrl != null && !apiUrl.isBlank() ? apiUrl : null,
                        apiKeyRef != null && !apiKeyRef.isBlank() ? apiKeyRef : null,
                        false, benchmarkLlmTimeoutSeconds, BENCHMARK_MAX_TOKENS);
            }
        } catch (Exception e) {
            log.warn("[Benchmark] Failed to resolve model '{}' from DB, fallback to default gateway: {}", model, e.getMessage());
        }

        // 降级：使用默认 AI gateway
        return llmService.chatWithTimeout(model, prompt,
                null, null, false, benchmarkLlmTimeoutSeconds, BENCHMARK_MAX_TOKENS);
    }

    private String buildVectorPrompt(String kbId, int questionCount, BenchmarkConfig config) {
        int candidateCount = config.getCandidateChunkCount() != null ? config.getCandidateChunkCount() : 5;

        List<Map<String, Object>> chunks = jdbcTemplate.queryForList(
                "SELECT id, content FROM kb_chunk WHERE kb_id = ? ORDER BY id LIMIT ?",
                kbId, Math.min(candidateCount * 3, 30));

        if (chunks.isEmpty()) {
            return String.format("""
                    生成 %d 个知识问答对。请基于一般知识生成。
                    返回JSON数组：[{"question":"问题","goldAnswer":"标准答案","goldChunks":[]}]
                    """, questionCount);
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < Math.min(chunks.size(), candidateCount); i++) {
            Map<String, Object> chunk = chunks.get(i);
            String chunkId = (String) chunk.get("id");
            String content = chunk.get("content").toString();
            context.append(String.format("[文档片段%d](id:%s) %s\n\n", i + 1, chunkId,
                    content.substring(0, Math.min(content.length(), 500))));
        }

        // 跨界切断测试集：要求答案横跨两个相邻片段（模拟分片边界切断的语义）
        boolean crossBoundary = Boolean.TRUE.equals(config.getCrossBoundary());
        String requirement = crossBoundary ? """
                基于以下知识库文档片段，生成 %d 个"跨界完整度"测试问答对。
                要求：
                1. 每个问题的答案都必须横跨两个编号相邻的文档片段才能完整回答
                   （如 [文档片段3] 与 [文档片段4]：答案要点分布在相邻两个片段中，
                   单个片段无法回答完整——这正是分片边界切断的场景）
                2. 问题要具体、有明确答案
                3. 答案要从相邻的两个片段中能找到依据
                4. goldChunks 必须同时填入这两个相邻片段括号中的完整 id（必须 2 个 id）
                """ : """
                基于以下知识库文档片段，生成 %d 个有意义的问答对。
                要求：
                1. 问题要具体、有明确答案
                2. 答案要从提供的文档片段中能找到依据
                3. goldChunks 填入对应文档片段括号中的完整 id（如 ["abc123_chunk_0", "def456_chunk_1"]）
                """;

        return String.format(requirement + "文档片段：\n%s\n\n返回JSON数组：[{\"question\":\"问题\",\"goldAnswer\":\"标准答案\",\"goldChunks\":[\"文档id\"]}]",
                questionCount, context);
    }

    private String buildGraphPrompt(String kbId, int questionCount) {
        List<Map<String, Object>> entities = jdbcTemplate.queryForList(
                "SELECT name, entity_type FROM kb_graph_entity WHERE kb_id = ? LIMIT 20", kbId);

        List<Map<String, Object>> relations = jdbcTemplate.queryForList(
                "SELECT source, target, label FROM kb_graph_relation WHERE kb_id = ? LIMIT 20", kbId);

        StringBuilder entityCtx = new StringBuilder();
        for (Map<String, Object> e : entities) {
            entityCtx.append(String.format("- %s (%s)\n", e.get("name"), e.get("entity_type")));
        }

        StringBuilder relationCtx = new StringBuilder();
        for (Map<String, Object> r : relations) {
            relationCtx.append(String.format("- %s --[%s]--> %s\n",
                    r.get("source"), r.get("label"), r.get("target")));
        }

        // 查找包含实体名称的 chunks，作为 goldChunks 的候选
        List<String> entityNames = new ArrayList<>();
        for (Map<String, Object> e : entities) {
            String name = (String) e.get("name");
            if (name != null && !name.isBlank()) entityNames.add(name);
        }
        StringBuilder chunkContext = new StringBuilder();
        if (!entityNames.isEmpty()) {
            // 构建 LIKE 查询：查找包含任一实体名称的 chunks
            StringBuilder where = new StringBuilder("WHERE kb_id = ? AND (");
            List<Object> params = new ArrayList<>();
            params.add(kbId);
            for (int i = 0; i < entityNames.size() && i < 5; i++) {
                if (i > 0) where.append(" OR ");
                where.append("content LIKE ?");
                params.add("%" + entityNames.get(i) + "%");
            }
            where.append(")");
            params.add(10); // LIMIT
            List<Map<String, Object>> chunks = jdbcTemplate.queryForList(
                    "SELECT id, content FROM kb_chunk " + where + " LIMIT ?",
                    params.toArray());

            if (!chunks.isEmpty()) {
                chunkContext.append("\n\n相关文档片段（可用于标注 goldChunks）：\n");
                for (int i = 0; i < chunks.size(); i++) {
                    Map<String, Object> chunk = chunks.get(i);
                    String content = chunk.get("content").toString();
                    chunkContext.append(String.format("[文档片段%d](id:%s) %s\n\n", i + 1,
                            chunk.get("id"),
                            content.substring(0, Math.min(content.length(), 500))));
                }
            }
        }

        String graphPart = String.format("""
                基于以下知识图谱中的实体和关系，生成 %d 个问答对。
                要求问题能考察对这些实体和关系的理解。
                """, questionCount);

        if (chunkContext.length() > 0) {
            return graphPart + String.format("""
                    要求 goldChunks 填入对应文档片段括号中的完整 id。

                    实体列表：
                    %s

                    关系列表：
                    %s
                    %s

                    返回JSON数组：[{"question":"问题","goldAnswer":"标准答案","goldChunks":["文档id"]}]
                    """, entityCtx, relationCtx, chunkContext);
        } else {
            return graphPart + String.format("""

                    实体列表：
                    %s

                    关系列表：
                    %s

                    返回JSON数组：[{"question":"问题","goldAnswer":"标准答案","goldChunks":[]}]
                    """, entityCtx, relationCtx);
        }
    }
}
