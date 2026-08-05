package com.fastrag.module.graph.service.impl;

import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.rerank.RerankService;
import com.fastrag.infra.milvus.MilvusService;
import com.fastrag.module.graph.entity.KbBenchmarkQuestion;
import com.fastrag.module.graph.entity.KbEvaluation;
import com.fastrag.module.graph.entity.KbEvaluationResult;
import com.fastrag.module.graph.mapper.KbBenchmarkQuestionMapper;
import com.fastrag.module.graph.mapper.KbEvaluationMapper;
import com.fastrag.module.graph.mapper.KbEvaluationResultMapper;
import com.fastrag.module.graph.model.EvaluationConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 评估执行异步助手。
 *
 * <p>单独抽取为 Spring Bean，确保 {@link Async} 注解通过代理生效，
 * 避免同类自调用时异步失效的问题。
 *
 * <p>性能优化：
 * <ul>
 *   <li>批量 Embedding — 所有题目一次性向量化，减少 HTTP 往返</li>
 *   <li>并行处理 — 多题并发检索+生成，限制并发数避免 API 限流</li>
 *   <li>合并 LLM 调用 — 答案生成与评判合并为一次 API 调用，减少 50% LLM 请求</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EvaluationExecutionHelper {

    private static final Logger log = LoggerFactory.getLogger(EvaluationExecutionHelper.class);

    /** 并行处理的最大并发数（避免 API 限流） */
    private static final int MAX_CONCURRENT = 4;

    private final KbEvaluationMapper evaluationMapper;
    private final KbEvaluationResultMapper resultMapper;
    private final KbBenchmarkQuestionMapper questionMapper;
    private final LlmService llmService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    // 检索基础设施
    private final EmbeddingService embeddingService;
    private final MilvusService milvusService;
    private final RerankService rerankService;

    // 并行执行器
    private final java.util.concurrent.Executor evalExecutor = Executors.newFixedThreadPool(MAX_CONCURRENT, r -> {
        Thread t = new Thread(r, "eval-worker");
        t.setDaemon(true);
        return t;
    });

    @Async
    public void execute(String evaluationId, String kbId, EvaluationConfig config) {
        String answerModel = config.getAnswerModel();
        String judgeModel = config.getJudgeModel();
        String benchmarkId = config.getBenchmark();

        // 检索配置
        String retrievalMode = config.getRetrievalMode() != null ? config.getRetrievalMode() : "hybrid";
        String embeddingModel = config.getEmbeddingModel();
        boolean enableRerank = Boolean.TRUE.equals(config.getEnableRerank());
        String rerankModel = config.getRerankModel();

        log.info("[Evaluation] Execute started: evalId={}, kbId={}, benchmarkId={}, answerModel={}, judgeModel={}, mode={}, rerank={}, embedModel={}",
                evaluationId, kbId, benchmarkId, answerModel, judgeModel, retrievalMode, enableRerank, embeddingModel);

        try {
            List<KbBenchmarkQuestion> questions = questionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbBenchmarkQuestion>()
                            .eq(KbBenchmarkQuestion::getBenchmarkId, benchmarkId));

            log.info("[Evaluation] Query benchmark questions: benchmarkId={}, found={} questions", benchmarkId, questions.size());
            for (int i = 0; i < questions.size(); i++) {
                log.info("[Evaluation]   Q{}: {} (goldChunks={}, goldAnswer={})",
                        i + 1,
                        questions.get(i).getQuestion().substring(0, Math.min(60, questions.get(i).getQuestion().length())),
                        questions.get(i).getGoldChunks() != null ? questions.get(i).getGoldChunks().substring(0, Math.min(40, questions.get(i).getGoldChunks().length())) : "null",
                        questions.get(i).getGoldAnswer() != null ? questions.get(i).getGoldAnswer().substring(0, Math.min(40, questions.get(i).getGoldAnswer().length())) : "null");
            }

            KbEvaluation evaluation = evaluationMapper.selectById(evaluationId);
            if (evaluation == null) {
                log.warn("[Evaluation] Evaluation record not found: {}", evaluationId);
                return;
            }

            evaluation.setDataCount(questions.size());
            evaluationMapper.updateById(evaluation);
            log.info("[Evaluation] dataCount set to {}", questions.size());

            if (questions.isEmpty()) {
                log.warn("[Evaluation] No questions found for benchmark: {}", benchmarkId);
                // 无题不算"完成"：标记 failed，避免 UI 显示"completed 0 分"误导
                transactionTemplate.executeWithoutResult(status -> {
                    KbEvaluation ev = evaluationMapper.selectById(evaluationId);
                    if (ev != null) {
                        ev.setStatus("failed");
                        ev.setCompletedCount(0);
                        ev.setDataCount(0);
                        evaluationMapper.updateById(ev);
                    }
                });
                return;
            }

            // ========== 优化1: 批量 Embedding ==========
            ModelApi embeddingApi = resolveModelApi(embeddingModel);
            List<String> allQueries = questions.stream().map(KbBenchmarkQuestion::getQuestion).toList();
            List<List<Float>> allVectors = batchEmbed(embeddingModel, allQueries, embeddingApi);
            log.info("[Evaluation] Batch embedded {} queries, got {} vectors", allQueries.size(), allVectors.size());

            // ========== 优化2: 并行处理题目 ==========
            int recall1Hits = 0, recall3Hits = 0, recall5Hits = 0, recall10Hits = 0;
            int answerCorrect = 0;
            long startTime = System.currentTimeMillis();
            Semaphore semaphore = new Semaphore(MAX_CONCURRENT);

            List<CompletableFuture<QuestionResult>> futures = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                final int idx = i;
                final KbBenchmarkQuestion q = questions.get(i);
                final List<Float> queryVector = allVectors.get(i);

                CompletableFuture<QuestionResult> future = CompletableFuture.supplyAsync(() -> {
                    semaphore.acquireUninterruptibly();
                    try {
                        return evaluateQuestion(evaluationId, idx, q, kbId, queryVector, retrievalMode,
                                embeddingModel, enableRerank, rerankModel, answerModel, judgeModel);
                    } finally {
                        semaphore.release();
                    }
                }, evalExecutor);

                futures.add(future);
            }

            // 等待所有题目完成
            List<KbEvaluationResult> allResults = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                try {
                    QuestionResult r = futures.get(i).join();
                    allResults.add(r.result());
                    if (r.recall1()) recall1Hits++;
                    if (r.recall3()) recall3Hits++;
                    if (r.recall5()) recall5Hits++;
                    if (r.recall10()) recall10Hits++;
                    if (r.correct()) answerCorrect++;

                    log.info("[Evaluation]   Q{} done: R@1={}, R@3={}, R@5={}, R@10={}, correct={}, answer={}",
                            i + 1, r.recall1(), r.recall3(), r.recall5(), r.recall10(), r.correct(),
                            r.answer().substring(0, Math.min(50, r.answer().length())));
                } catch (Exception e) {
                    log.warn("[Evaluation] Failed to evaluate question {}: {}", i + 1, e.getMessage(), e);
                }
            }

            // ========== 聚合指标 ==========
            long duration = System.currentTimeMillis() - startTime;
            int total = questions.size();
            double recallAt1 = total > 0 ? (double) recall1Hits / total : 0;
            double recallAt3 = total > 0 ? (double) recall3Hits / total : 0;
            double recallAt5 = total > 0 ? (double) recall5Hits / total : 0;
            double recallAt10 = total > 0 ? (double) recall10Hits / total : 0;
            double answerAccuracy = total > 0 ? (double) answerCorrect / total : 0;
            double overallScore = recallAt10 * 0.7 + answerAccuracy * 0.3;

            log.info("[Evaluation] Aggregation: total={}, recall1Hits={}, recall3Hits={}, recall5Hits={}, recall10Hits={}, answerCorrect={}, duration={}ms, score={:.3f}",
                    total, recall1Hits, recall3Hits, recall5Hits, recall10Hits, answerCorrect, duration, overallScore);

            // 批量写入结果
            for (KbEvaluationResult result : allResults) {
                resultMapper.insert(result);
            }

            finishEvaluation(evaluationId, total, recallAt1, recallAt3, recallAt5, recallAt10,
                    answerAccuracy, overallScore, duration);

        } catch (Exception e) {
            log.error("[Evaluation] Failed: {}", evaluationId, e);
            transactionTemplate.executeWithoutResult(status -> {
                KbEvaluation ev = evaluationMapper.selectById(evaluationId);
                if (ev != null) {
                    ev.setStatus("failed");
                    evaluationMapper.updateById(ev);
                }
            });
        }
    }

    // ==================== 单题评估 ====================

    private record QuestionResult(
            KbEvaluationResult result,
            boolean recall1, boolean recall3, boolean recall5, boolean recall10,
            boolean correct,
            String answer
    ) {}

    private QuestionResult evaluateQuestion(String evaluationId, int index, KbBenchmarkQuestion question, String kbId,
                                             List<Float> queryVector, String retrievalMode,
                                             String embeddingModel, boolean enableRerank,
                                             String rerankModel, String answerModel,
                                             String judgeModel) {
        String query = question.getQuestion();

        try {
            // ========== 1. 检索 ==========
            List<Map<String, Object>> retrievedChunks = retrieveChunksWithVector(
                    kbId, query, 10, queryVector, retrievalMode, embeddingModel, enableRerank, rerankModel);

            List<String> retrievedChunkIds = new ArrayList<>();
            for (Map<String, Object> chunk : retrievedChunks) {
                retrievedChunkIds.add((String) chunk.get("id"));
            }

            // ========== 2. Recall@K ==========
            List<String> goldChunks = parseGoldChunks(question.getGoldChunks());
            boolean r1 = checkRecall(goldChunks, retrievedChunkIds, 1);
            boolean r3 = checkRecall(goldChunks, retrievedChunkIds, 3);
            boolean r5 = checkRecall(goldChunks, retrievedChunkIds, 5);
            boolean r10 = checkRecall(goldChunks, retrievedChunkIds, 10);

            // ========== 3. 合并 LLM：答案生成 + 评判 ==========
            // 如果有独立的 judgeModel，用它做合并调用（通常配置更轻量的模型以提速）
            String llmModel = (judgeModel != null && !judgeModel.isBlank() && !judgeModel.equals(answerModel))
                    ? judgeModel : answerModel;
            // 只取前2个最相关chunk，减少LLM输入/输出token
            List<Map<String, Object>> topChunks = retrievedChunks.size() > 2
                    ? retrievedChunks.subList(0, Math.min(2, retrievedChunks.size()))
                    : retrievedChunks;
            String context = buildContext(topChunks);
            String llmResponse = generateAnswerWithJudgment(index, llmModel, query, context, question.getGoldAnswer());

            // 解析合并响应（兼容长短字段名）
            boolean isCorrect = false;
            String judgeReason = "";
            String generatedAnswer = llmResponse;
            try {
                String json = llmResponse.trim();
                if (json.startsWith("```")) {
                    json = json.replaceAll("```json?", "").replaceAll("```", "").trim();
                }
                var judgeResult = cn.hutool.json.JSONUtil.parseObj(json);
                isCorrect = judgeResult.getBool("isCorrect", judgeResult.getBool("c", false));
                judgeReason = judgeResult.getStr("reason", "");
                String parsedAnswer = judgeResult.getStr("answer", judgeResult.getStr("a", ""));
                if (parsedAnswer != null && !parsedAnswer.isBlank()) {
                    generatedAnswer = parsedAnswer;
                }
            } catch (Exception e) {
                log.warn("[Evaluation] Q{} Failed to parse merged LLM response: {}", index + 1, e.getMessage());
                var ruleResult = ruleBasedJudge(llmResponse, question.getGoldAnswer());
                isCorrect = (Boolean) ruleResult.get("isCorrect");
                judgeReason = (String) ruleResult.get("reason");
            }

            // ========== 4. 构建检索指标字符串 ==========
            String retrievalMetrics = String.format(
                    "R@1 %d.%03d  R@3 %d.%03d  R@5 %d.%03d  R@10 %d.%03d",
                    r1 ? 1 : 0, 0, r3 ? 1 : 0, 0, r5 ? 1 : 0, 0, r10 ? 1 : 0, 0);

            // ========== 5. 构建结果 ==========
            KbEvaluationResult result = new KbEvaluationResult();
            result.setEvaluationId(evaluationId);
            result.setQuestion(question.getQuestion());
            result.setGeneratedAnswer(generatedAnswer);
            result.setRetrievalMetrics(retrievalMetrics);
            result.setIsCorrect(isCorrect ? 1 : 0);
            result.setJudgeReason(judgeReason);
            result.setRecallAt1(BigDecimal.valueOf(r1 ? 1 : 0));
            result.setRecallAt3(BigDecimal.valueOf(r3 ? 1 : 0));
            result.setRecallAt5(BigDecimal.valueOf(r5 ? 1 : 0));
            result.setRecallAt10(BigDecimal.valueOf(r10 ? 1 : 0));

            return new QuestionResult(result, r1, r3, r5, r10, isCorrect, generatedAnswer);

        } catch (Exception e) {
            log.warn("[Evaluation] Q{} Failed: {}", index + 1, e.getMessage(), e);
            KbEvaluationResult fallback = new KbEvaluationResult();
            fallback.setEvaluationId(evaluationId);
            fallback.setQuestion(question.getQuestion());
            fallback.setGeneratedAnswer("评估失败: " + e.getMessage());
            fallback.setRetrievalMetrics("R@1 0.000  R@3 0.000  R@5 0.000  R@10 0.000");
            fallback.setIsCorrect(0);
            fallback.setJudgeReason("执行异常");
            fallback.setRecallAt1(BigDecimal.ZERO);
            fallback.setRecallAt3(BigDecimal.ZERO);
            fallback.setRecallAt5(BigDecimal.ZERO);
            fallback.setRecallAt10(BigDecimal.ZERO);
            return new QuestionResult(fallback, false, false, false, false, false, "评估失败");
        }
    }

    // ==================== 批量 Embedding ====================

    /**
     * 批量向量化所有查询文本，一次 HTTP 调用完成
     */
    private List<List<Float>> batchEmbed(String embeddingModel, List<String> queries, ModelApi embeddingApi) {
        try {
            String model = (embeddingModel != null && !embeddingModel.isBlank()) ? embeddingModel : "default";
            if (embeddingApi != null) {
                return embeddingService.embed(model, queries, embeddingApi.apiUrl, embeddingApi.apiKey);
            } else {
                // 逐个 embed（不支持无 apiUrl 的批量）
                List<List<Float>> results = new ArrayList<>();
                for (String q : queries) {
                    results.add(embeddingService.embed(model, q));
                }
                return results;
            }
        } catch (Exception e) {
            log.warn("[Evaluation] Batch embedding failed: {}", e.getMessage());
            List<List<Float>> results = new ArrayList<>();
            for (String q : queries) {
                try {
                    results.add(embeddingService.embed("default", q));
                } catch (Exception ex) {
                    results.add(List.of());
                }
            }
            return results;
        }
    }

    // ==================== 检索（使用预计算向量）====================

    private List<Map<String, Object>> retrieveChunksWithVector(String kbId, String query, int topK,
                                                               List<Float> queryVector,
                                                               String retrievalMode, String embeddingModel,
                                                               boolean enableRerank, String rerankModel) {
        List<Map<String, Object>> results;

        switch (retrievalMode.toLowerCase()) {
            case "vector":
                results = executeVectorSearchWithVector(kbId, query, topK, queryVector);
                break;
            case "hybrid":
                results = executeHybridSearchWithVector(kbId, query, topK, queryVector, embeddingModel);
                break;
            case "fulltext":
            default:
                results = executeFulltextSearch(kbId, query, topK);
                break;
        }

        if (enableRerank && !results.isEmpty() && rerankModel != null && !rerankModel.isBlank()) {
            results = rerankResults(results, query, rerankModel, topK);
        } else if (enableRerank && !results.isEmpty()) {
            results = rerankResults(results, query, "default", topK);
        }

        return results;
    }

    private List<Map<String, Object>> executeVectorSearchWithVector(String kbId, String query, int topK,
                                                                     List<Float> queryVector) {
        if (queryVector == null || queryVector.isEmpty()) {
            return executeFulltextSearch(kbId, query, topK);
        }

        try {
            String collection = "kb_" + kbId;
            List<Map<String, Object>> milvusResults = milvusService.search(collection, queryVector, topK * 3);
            if (milvusResults.isEmpty()) {
                log.warn("[VectorSearch] Milvus returned empty, fallback to fulltext for query: {}", query);
                return executeFulltextSearch(kbId, query, topK);
            }

            List<String> chunkIds = new ArrayList<>();
            Map<String, Double> scoreMap = new LinkedHashMap<>();
            for (Map<String, Object> item : milvusResults) {
                String id = (String) item.get("id");
                Object scoreObj = item.get("score");
                double score = 0.0;
                if (scoreObj instanceof Double d) {
                    score = d;
                } else if (scoreObj instanceof Float f) {
                    score = f.doubleValue();
                } else if (scoreObj instanceof Number n) {
                    score = n.doubleValue();
                }
                if (id != null) {
                    chunkIds.add(id);
                    scoreMap.put(id, score);
                }
            }

            List<Map<String, Object>> chunks = fetchChunksByIds(kbId, chunkIds);

            Map<String, Map<String, Object>> chunkMap = new HashMap<>();
            for (Map<String, Object> chunk : chunks) {
                chunkMap.put((String) chunk.get("id"), chunk);
            }

            List<Map<String, Object>> ordered = new ArrayList<>();
            for (String id : chunkIds) {
                Map<String, Object> chunk = chunkMap.get(id);
                if (chunk != null) {
                    chunk.put("similarity", scoreMap.getOrDefault(id, 0.0));
                    ordered.add(chunk);
                }
            }

            log.info("[VectorSearch] query=\"{}\", candidates={}, returned={}", query, milvusResults.size(), ordered.size());
            return ordered.size() > topK ? ordered.subList(0, topK) : ordered;

        } catch (Exception e) {
            log.warn("[VectorSearch] Failed, fallback to fulltext: {}", e.getMessage());
            return executeFulltextSearch(kbId, query, topK);
        }
    }

    private List<Map<String, Object>> executeHybridSearchWithVector(String kbId, String query, int topK,
                                                                     List<Float> queryVector,
                                                                     String embeddingModel) {
        try {
            List<Map<String, Object>> vectorResults = executeVectorSearchWithVector(kbId, query, topK * 2, queryVector);
            List<Map<String, Object>> fulltextResults = executeFulltextSearch(kbId, query, topK * 2);
            return fuseResults(vectorResults, fulltextResults, topK);
        } catch (Exception e) {
            log.warn("[HybridSearch] Failed, fallback to fulltext: {}", e.getMessage());
            return executeFulltextSearch(kbId, query, topK);
        }
    }

    // ==================== 全文检索 ====================

    private List<Map<String, Object>> executeFulltextSearch(String kbId, String query, int topK) {
        String[] keywords = query.split("\\s+");
        if (keywords.length == 0 || (keywords.length == 1 && keywords[0].isEmpty())) {
            return List.of();
        }

        try {
            StringBuilder fulltextQuery = new StringBuilder();
            for (int i = 0; i < keywords.length; i++) {
                if (i > 0) fulltextQuery.append(" ");
                fulltextQuery.append("+").append(keywords[i]).append("*");
            }
            return jdbcTemplate.queryForList(
                    "SELECT id, file_id, chunk_index, content FROM kb_chunk " +
                    "WHERE kb_id = ? AND MATCH(content) AGAINST(? IN BOOLEAN MODE) " +
                    "ORDER BY chunk_index ASC LIMIT ?",
                    kbId, fulltextQuery.toString(), topK);
        } catch (Exception e) {
            log.debug("[FulltextSearch] MATCH...AGAINST failed, fallback to LIKE: {}", e.getMessage());
        }

        StringBuilder where = new StringBuilder("WHERE kb_id = ? AND (");
        List<Object> params = new ArrayList<>();
        params.add(kbId);
        for (int i = 0; i < keywords.length; i++) {
            if (i > 0) where.append(" OR ");
            where.append("content LIKE ?");
            params.add("%" + keywords[i] + "%");
        }
        where.append(")");

        return jdbcTemplate.queryForList(
                "SELECT id, file_id, chunk_index, content FROM kb_chunk " + where +
                " ORDER BY chunk_index ASC LIMIT ?",
                params.toArray(new Object[0]));
    }

    // ==================== RRF 融合 ====================

    private List<Map<String, Object>> fuseResults(List<Map<String, Object>> vectorResults,
                                                   List<Map<String, Object>> fulltextResults,
                                                   int topK) {
        Map<String, Map<String, Object>> seen = new LinkedHashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();
        double k = 60.0;

        for (int i = 0; i < vectorResults.size(); i++) {
            String id = (String) vectorResults.get(i).get("id");
            if (id == null) continue;
            seen.put(id, vectorResults.get(i));
            rrfScores.merge(id, 1.0 / (k + i + 1), Double::sum);
        }

        for (int i = 0; i < fulltextResults.size(); i++) {
            String id = (String) fulltextResults.get(i).get("id");
            if (id == null) continue;
            seen.putIfAbsent(id, fulltextResults.get(i));
            rrfScores.merge(id, 1.0 / (k + i + 1), Double::sum);
        }

        return seen.keySet().stream()
                .sorted((a, b) -> Double.compare(rrfScores.get(b), rrfScores.get(a)))
                .limit(topK)
                .map(seen::get)
                .collect(Collectors.toList());
    }

    // ==================== Rerank ====================

    private List<Map<String, Object>> rerankResults(List<Map<String, Object>> chunks,
                                                     String query, String rerankModel, int topK) {
        try {
            List<String> documents = chunks.stream()
                    .map(c -> (String) c.get("content"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            if (documents.isEmpty()) return chunks;

            ModelApi modelApi = resolveModelApi(rerankModel);
            List<Map<String, Object>> reranked;
            if (modelApi != null) {
                reranked = rerankService.rerank(rerankModel, query, documents, topK, modelApi.apiUrl, modelApi.apiKey);
            } else {
                reranked = rerankService.rerank(rerankModel, query, documents, topK);
            }
            if (reranked.isEmpty()) {
                return chunks.size() > topK ? chunks.subList(0, topK) : chunks;
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> item : reranked) {
                int idx = (int) item.get("index");
                if (idx >= 0 && idx < chunks.size()) {
                    Map<String, Object> reordered = chunks.get(idx);
                    reordered.put("rerank_score", item.get("relevance_score"));
                    result.add(reordered);
                }
            }
            return result;

        } catch (Exception e) {
            log.warn("[Rerank] Failed, keeping original order: {}", e.getMessage());
            return chunks.size() > topK ? chunks.subList(0, topK) : chunks;
        }
    }

    // ==================== 合并 LLM 调用（答案生成 + 评判）====================

    /**
     * 一次 LLM 调用同时完成答案生成和评判。
     *
     * <p>最小化输出结构以提速：{"a":"简短答案","c":true/false}
     *
     * <p>只传入最相关的2个chunk，答案要求极短，LLM 输出 token 大幅减少。
     *
     * <p>直接同步调用：chat() 内部自带 30s 非流式阻塞超时保护（超时返回错误串，
     * 由解析逻辑降级为规则评判）。此前用 evalExecutor 二次包装导致线程池自占用
     * （4 个线程被外层题目占满，内部 LLM 任务饿死排队，30s 后全部超时判错）。
     */
    private String generateAnswerWithJudgment(int index, String model, String query, String context, String goldAnswer) {
        if (context == null || context.isBlank()) {
            return "{\"a\":\"\",\"c\":false,\"reason\":\"检索内容为空\"}";
        }

        String prompt = String.format("""
                根据检索内容回答问题，并对照标准答案判断回答是否正确。
                内容：%s
                问题：%s
                标准答案：%s
                严格JSON：{"a":"1-3字答案","c":true/false}
                """, context, query, goldAnswer != null ? goldAnswer : "");

        ModelApi modelApi = resolveModelApi(model);

        try {
            String llmResponse = (modelApi != null)
                    ? llmService.chat(model, prompt, modelApi.apiUrl, modelApi.apiKey)
                    : llmService.chat(model, prompt);
            return parseJudgmentResponse(llmResponse, goldAnswer);
        } catch (Exception e) {
            log.warn("[Evaluation] Q{} LLM call failed: {}", index + 1, "模型调用失败: " + e.getMessage());
            return buildRuleJudgmentResponse(goldAnswer, "LLM 调用失败，规则评判降级");
        }
    }

    /** 解析 LLM 的合并响应，兼容长短字段名；错误串/损坏响应降级为规则评判 */
    private String parseJudgmentResponse(String llmResponse, String goldAnswer) {
        try {
            String json = llmResponse.trim();
            // chat() 超时/异常时返回"模型调用失败: ..."错误串（不抛异常）
            if (json.startsWith("模型调用失败")) {
                String reason = json.contains("Timeout") ? "LLM 调用超时，规则评判降级" : "LLM 调用失败，规则评判降级";
                return buildRuleJudgmentResponse(goldAnswer, reason);
            }
            if (json.startsWith("```")) {
                json = json.replaceAll("```json?", "").replaceAll("```", "").trim();
            }
            var judgeResult = cn.hutool.json.JSONUtil.parseObj(json);
            boolean isCorrect = judgeResult.getBool("isCorrect", judgeResult.getBool("c", false));
            String answer = judgeResult.getStr("answer", judgeResult.getStr("a", ""));
            if (answer != null && !answer.isBlank()) {
                return String.format("{\"a\":\"%s\",\"c\":%s}",
                        answer.replace("\"", "\\\""), isCorrect);
            }
            return String.format("{\"a\":\"\",\"c\":%s}", isCorrect);
        } catch (Exception e) {
            // 降级：规则评判
            return buildRuleJudgmentResponse(goldAnswer, "响应解析失败，规则评判降级");
        }
    }

    /** 规则评判兜底：LLM 超时/失败时标记为不正确，并附 reason 便于追溯 */
    private String buildRuleJudgmentResponse(String goldAnswer, String reason) {
        return String.format("{\"a\":\"\",\"c\":false,\"reason\":\"%s\"}", reason);
    }

    private String buildContext(List<Map<String, Object>> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> chunk = chunks.get(i);
            String content = (String) chunk.get("content");
            if (content != null) {
                sb.append(String.format("[%d] %s\n\n", i + 1,
                        content.length() > 300 ? content.substring(0, 300) + "..." : content));
            }
        }
        return sb.toString().trim();
    }

    // ==================== 按 ID 批量获取 Chunk ====================

    private List<Map<String, Object>> fetchChunksByIds(String kbId, List<String> ids) {
        if (ids.isEmpty()) return List.of();

        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>();
        params.add(kbId);
        params.addAll(ids);

        return jdbcTemplate.queryForList(
                "SELECT id, file_id, chunk_index, content FROM kb_chunk " +
                "WHERE kb_id = ? AND id IN (" + placeholders + ")",
                params.toArray());
    }

    // ==================== 规则评判（降级方案）====================

    private Map<String, Object> ruleBasedJudge(String generatedAnswer, String goldAnswer) {
        Map<String, Object> result = new HashMap<>();

        if (generatedAnswer == null || generatedAnswer.isBlank()) {
            result.put("isCorrect", false);
            result.put("reason", "未生成答案");
            return result;
        }

        Pattern numPattern = Pattern.compile("\\d+(\\.\\d+)?");
        Matcher goldMatcher = numPattern.matcher(goldAnswer);
        Set<String> goldNumbers = new HashSet<>();
        while (goldMatcher.find()) {
            goldNumbers.add(goldMatcher.group());
        }

        if (!goldNumbers.isEmpty()) {
            int hit = 0;
            for (String num : goldNumbers) {
                if (generatedAnswer.contains(num)) hit++;
            }
            double rate = (double) hit / goldNumbers.size();
            result.put("isCorrect", rate >= 0.8);
            result.put("reason", String.format("关键数字命中 %d/%d（%.0f%%）", hit, goldNumbers.size(), rate * 100));
            return result;
        }

        Set<String> goldKeywords = new HashSet<>();
        Matcher kwMatcher = Pattern.compile("[\\u4e00-\\u9fa5]{2,4}").matcher(goldAnswer);
        while (kwMatcher.find()) {
            goldKeywords.add(kwMatcher.group());
        }

        if (goldKeywords.isEmpty()) {
            result.put("isCorrect", false);
            result.put("reason", "gold 答案无可校验内容");
            return result;
        }

        int hit = 0;
        for (String kw : goldKeywords) {
            if (generatedAnswer.contains(kw)) hit++;
        }
        double rate = (double) hit / goldKeywords.size();
        result.put("isCorrect", rate >= 0.8);
        result.put("reason", String.format("关键词命中 %d/%d（%.0f%%）", hit, goldKeywords.size(), rate * 100));

        return result;
    }

    // ==================== Recall@K ====================

    private List<String> parseGoldChunks(String goldChunksJson) {
        if (goldChunksJson == null || goldChunksJson.isBlank()) return List.of();
        try {
            return cn.hutool.json.JSONUtil.toList(goldChunksJson, String.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean checkRecall(List<String> goldChunks, List<String> retrievedChunkIds, int k) {
        if (goldChunks.isEmpty()) return false;
        int limit = Math.min(k, retrievedChunkIds.size());
        for (int i = 0; i < limit; i++) {
            if (goldChunks.contains(retrievedChunkIds.get(i))) return true;
        }
        return false;
    }

    // ==================== 完成评估 ====================

    private void finishEvaluation(String evaluationId, int total,
                                  double recallAt1, double recallAt3, double recallAt5, double recallAt10,
                                  double answerAccuracy, double overallScore, long duration) {
        transactionTemplate.executeWithoutResult(status -> {
            KbEvaluation ev = evaluationMapper.selectById(evaluationId);
            if (ev == null) return;
            ev.setStatus("completed");
            ev.setCompletedCount(total);
            ev.setDataCount(total);
            ev.setDuration(duration);
            ev.setBenchmarkCount(total);
            ev.setRecallAt1(BigDecimal.valueOf(recallAt1));
            ev.setRecallAt3(BigDecimal.valueOf(recallAt3));
            ev.setRecallAt5(BigDecimal.valueOf(recallAt5));
            ev.setRecallAt10(BigDecimal.valueOf(recallAt10));
            ev.setAnswerAccuracy(BigDecimal.valueOf(answerAccuracy));
            ev.setOverallScore(BigDecimal.valueOf(overallScore));
            evaluationMapper.updateById(ev);
        });

        log.info("[Evaluation] Completed: {}, score={}, R@10={}, accuracy={}, parallel={}",
                evaluationId, String.format("%.3f", overallScore), String.format("%.3f", recallAt10),
                String.format("%.3f", answerAccuracy), MAX_CONCURRENT);
    }

    // ==================== Model 表解析 ====================

    /** 从 model 表解析模型的 API 地址和密钥 */
    private record ModelApi(String apiUrl, String apiKey) {}

    private ModelApi resolveModelApi(String modelCode) {
        if (modelCode == null || modelCode.isBlank() || "default".equals(modelCode)) return null;
        try {
            List<Map<String, Object>> records = jdbcTemplate.queryForList(
                    "SELECT api_url, api_key_ref FROM model WHERE code = ? LIMIT 1", modelCode);
            if (!records.isEmpty()) {
                Map<String, Object> record = records.get(0);
                String apiUrl = (String) record.get("api_url");
                String apiKeyRef = (String) record.get("api_key_ref");
                if (apiUrl != null && !apiUrl.isBlank()) {
                    return new ModelApi(apiUrl, apiKeyRef);
                }
            }
            log.warn("[Evaluation] Model '{}' not found in model table or no api_url, using default gateway", modelCode);
        } catch (Exception e) {
            log.warn("[Evaluation] Failed to resolve model '{}' from DB, fallback to default gateway: {}", modelCode, e.getMessage());
        }
        return null;
    }
}
