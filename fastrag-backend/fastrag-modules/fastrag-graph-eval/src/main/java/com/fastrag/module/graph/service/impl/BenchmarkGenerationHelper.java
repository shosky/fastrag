package com.fastrag.module.graph.service.impl;

import cn.hutool.json.JSONUtil;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.module.graph.entity.KbBenchmark;
import com.fastrag.module.graph.entity.KbBenchmarkQuestion;
import com.fastrag.module.graph.mapper.KbBenchmarkMapper;
import com.fastrag.module.graph.mapper.KbBenchmarkQuestionMapper;
import com.fastrag.module.graph.model.BenchmarkConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json?", "").replaceAll("```", "").trim();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questions = (List<Map<String, Object>>) (List<?>) JSONUtil.toList(json, Map.class);

            List<KbBenchmarkQuestion> entities = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                Map<String, Object> q = questions.get(i);
                KbBenchmarkQuestion question = new KbBenchmarkQuestion();
                question.setBenchmarkId(benchmarkId);
                question.setQuestionIndex(i);
                question.setQuestion((String) q.get("question"));
                question.setGoldAnswer((String) q.get("goldAnswer"));
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
        }
    }

    /**
     * 调用 LLM：从 model 表解析模型的 API URL，支持自定义路由
     */
    private String callLlm(String model, String prompt) {
        if (model == null || model.isBlank() || "default".equals(model)) {
            return llmService.chat(model != null ? model : "default", prompt);
        }

        // 从 model 表查询模型的 API URL 和 Key
        try {
            List<Map<String, Object>> records = jdbcTemplate.queryForList(
                    "SELECT api_url, api_key_ref FROM model WHERE code = ? LIMIT 1", model);
            if (!records.isEmpty()) {
                Map<String, Object> record = records.get(0);
                String apiUrl = (String) record.get("api_url");
                String apiKeyRef = (String) record.get("api_key_ref");
                return llmService.chat(model, prompt,
                        apiUrl != null && !apiUrl.isBlank() ? apiUrl : null,
                        apiKeyRef != null && !apiKeyRef.isBlank() ? apiKeyRef : null);
            }
        } catch (Exception e) {
            log.warn("[Benchmark] Failed to resolve model '{}' from DB, fallback to default gateway: {}", model, e.getMessage());
        }

        // 降级：使用默认 AI gateway
        return llmService.chat(model, prompt);
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

        return String.format("""
                基于以下知识库文档片段，生成 %d 个有意义的问答对。
                要求：
                1. 问题要具体、有明确答案
                2. 答案要从提供的文档片段中能找到依据
                3. goldChunks 填入对应文档片段括号中的完整 id（如 ["abc123_chunk_0", "def456_chunk_1"]）

                文档片段：
                %s

                返回JSON数组：[{"question":"问题","goldAnswer":"标准答案","goldChunks":["文档id"]}]
                """, questionCount, context);
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
