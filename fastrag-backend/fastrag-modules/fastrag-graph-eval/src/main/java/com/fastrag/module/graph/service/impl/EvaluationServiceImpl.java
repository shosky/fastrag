package com.fastrag.module.graph.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.module.graph.entity.KbBenchmarkQuestion;
import com.fastrag.module.graph.entity.KbEvaluation;
import com.fastrag.module.graph.entity.KbEvaluationResult;
import com.fastrag.module.graph.mapper.KbBenchmarkQuestionMapper;
import com.fastrag.module.graph.mapper.KbEvaluationMapper;
import com.fastrag.module.graph.mapper.KbEvaluationResultMapper;
import com.fastrag.module.graph.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private final KbEvaluationMapper mapper;
    private final KbEvaluationResultMapper resultMapper;
    private final KbBenchmarkQuestionMapper questionMapper;
    private final LlmService llmService;

    @Override
    public List<KbEvaluation> list(String kbId) {
        return mapper.selectList(new LambdaQueryWrapper<KbEvaluation>()
                .eq(KbEvaluation::getKbId, kbId)
                .orderByDesc(KbEvaluation::getCreatedAt));
    }

    @Override
    public KbEvaluation getDetail(String kbId, String id) {
        return mapper.selectById(id);
    }

    @Override
    public void run(String kbId, Map<String, Object> config) {
        KbEvaluation evaluation = new KbEvaluation();
        evaluation.setKbId(kbId);
        evaluation.setName((String) config.get("name"));
        evaluation.setBenchmark((String) config.get("benchmark"));
        evaluation.setAnswerModel((String) config.get("answerModel"));
        evaluation.setJudgeModel((String) config.get("judgeModel"));
        evaluation.setStatus("running");
        evaluation.setCompletedCount(0);
        mapper.insert(evaluation);

        executeEvaluation(evaluation.getId(), kbId, config);
    }

    @Override
    public void delete(String kbId, String id) {
        resultMapper.delete(new LambdaQueryWrapper<KbEvaluationResult>()
                .eq(KbEvaluationResult::getEvaluationId, id));
        mapper.deleteById(id);
    }

    @Async
    protected void executeEvaluation(String evaluationId, String kbId, Map<String, Object> config) {
        String answerModel = (String) config.get("answerModel");
        String judgeModel = (String) config.get("judgeModel");
        String benchmarkId = (String) config.get("benchmark");

        try {
            List<KbBenchmarkQuestion> questions = questionMapper.selectList(
                    new LambdaQueryWrapper<KbBenchmarkQuestion>()
                            .eq(KbBenchmarkQuestion::getBenchmarkId, benchmarkId));

            KbEvaluation evaluation = mapper.selectById(evaluationId);
            if (evaluation == null) return;

            evaluation.setDataCount(questions.size());
            mapper.updateById(evaluation);

            int correctCount = 0;
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < questions.size(); i++) {
                KbBenchmarkQuestion question = questions.get(i);
                try {
                    // 生成答案
                    String answerPrompt = "请回答以下问题：\n" + question.getQuestion();
                    String generatedAnswer = llmService.chat(answerModel, answerPrompt);

                    // 评判答案
                    boolean isCorrect = false;
                    String judgeReason = "";
                    if (question.getGoldAnswer() != null && !question.getGoldAnswer().isEmpty()) {
                        String judgePrompt = String.format(
                                "评判答案是否正确。问题：%s\n标准答案：%s\n生成答案：%s\n返回JSON：{\"isCorrect\":true/false,\"reason\":\"理由\"}",
                                question.getQuestion(), question.getGoldAnswer(), generatedAnswer);
                        String judgeResponse = llmService.chat(judgeModel, judgePrompt);
                        try {
                            String json = judgeResponse.trim();
                            if (json.startsWith("```")) json = json.replaceAll("```json?", "").replaceAll("```", "").trim();
                            var judgeResult = JSONUtil.parseObj(json);
                            isCorrect = judgeResult.getBool("isCorrect", false);
                            judgeReason = judgeResult.getStr("reason", "");
                        } catch (Exception e) {
                            log.warn("Failed to parse judge response: {}", e.getMessage());
                        }
                    }

                    if (isCorrect) correctCount++;

                    KbEvaluationResult result = new KbEvaluationResult();
                    result.setEvaluationId(evaluationId);
                    result.setQuestion(question.getQuestion());
                    result.setGeneratedAnswer(generatedAnswer);
                    result.setIsCorrect(isCorrect ? 1 : 0);
                    result.setJudgeReason(judgeReason);
                    resultMapper.insert(result);

                    evaluation.setCompletedCount(i + 1);
                    mapper.updateById(evaluation);

                } catch (Exception e) {
                    log.warn("Failed to evaluate question {}: {}", i, e.getMessage());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            double accuracy = questions.size() > 0 ? (double) correctCount / questions.size() : 0;

            evaluation.setStatus("completed");
            evaluation.setDuration(duration);
            evaluation.setAnswerAccuracy(BigDecimal.valueOf(accuracy));
            evaluation.setOverallScore(BigDecimal.valueOf(accuracy));
            evaluation.setBenchmarkCount(questions.size());
            mapper.updateById(evaluation);

            log.info("Evaluation completed: {}, accuracy: {}", evaluationId, accuracy);

        } catch (Exception e) {
            log.error("Evaluation failed: {}", evaluationId, e);
            KbEvaluation evaluation = mapper.selectById(evaluationId);
            if (evaluation != null) {
                evaluation.setStatus("failed");
                mapper.updateById(evaluation);
            }
        }
    }
}
