package com.fastrag.module.graph.service.impl;

import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.module.graph.entity.KbEvaluation;
import com.fastrag.module.graph.entity.KbEvaluationResult;
import com.fastrag.module.graph.mapper.KbEvaluationMapper;
import com.fastrag.module.graph.mapper.KbEvaluationResultMapper;
import com.fastrag.module.graph.model.EvaluationConfig;
import com.fastrag.module.graph.service.EvaluationService;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评估服务实现
 */
@Service
@RequiredArgsConstructor
public class EvaluationServiceImpl implements EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationServiceImpl.class);

    private final KbEvaluationMapper mapper;
    private final KbEvaluationResultMapper resultMapper;
    private final EvaluationExecutionHelper executionHelper;
    private final LogService logService;

    @Override
    public List<KbEvaluation> list(String kbId) {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbEvaluation>()
                .eq(KbEvaluation::getKbId, kbId)
                .orderByDesc(KbEvaluation::getCreatedAt));
    }

    @Override
    public KbEvaluation getDetail(String kbId, String id) {
        KbEvaluation evaluation = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbEvaluation>()
                .eq(KbEvaluation::getKbId, kbId)
                .eq(KbEvaluation::getId, id));
        if (evaluation == null) return null;

        // 查询每道题的评估结果
        List<KbEvaluationResult> results = resultMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbEvaluationResult>()
                        .eq(KbEvaluationResult::getEvaluationId, id)
                        .orderByAsc(KbEvaluationResult::getId));
        evaluation.setResults(results);

        return evaluation;
    }

    @Override
    public KbEvaluation run(String kbId, EvaluationConfig config) {
        String benchmark = config.getBenchmark();
        log.info("[Evaluation] Run requested: kbId={}, benchmark={}, name={}, answerModel={}, judgeModel={}, retrievalMode={}",
                kbId, benchmark, config.getName(), config.getAnswerModel(), config.getJudgeModel(), config.getRetrievalMode());

        // 校验 benchmark 参数
        if (benchmark == null || benchmark.isBlank()) {
            log.warn("[Evaluation] Rejected: benchmark is null/blank");
            KbEvaluation evaluation = new KbEvaluation();
            evaluation.setKbId(kbId);
            evaluation.setName(config.getName());
            evaluation.setBenchmark(benchmark);
            evaluation.setAnswerModel(config.getAnswerModel());
            evaluation.setJudgeModel(config.getJudgeModel());
            evaluation.setStatus("failed");
            evaluation.setCompletedCount(0);
            evaluation.setDataCount(0);
            evaluation.setBenchmarkCount(0);
            mapper.insert(evaluation);
            return evaluation;
        }

        KbEvaluation evaluation = new KbEvaluation();
        evaluation.setKbId(kbId);
        evaluation.setName(config.getName());
        evaluation.setBenchmark(benchmark);
        evaluation.setAnswerModel(config.getAnswerModel());
        evaluation.setJudgeModel(config.getJudgeModel());
        evaluation.setStatus("running");
        evaluation.setCompletedCount(0);
        mapper.insert(evaluation);
        log.info("[Evaluation] Created evaluation record: id={}, benchmark={}", evaluation.getId(), benchmark);

        // 记录评测启动日志
        try {
            logService.addLog(kbId, LogCategory.operation, ActionType.evaluation_run,
                    evaluation.getId(), "启动评测，benchmark: " + benchmark + ", answerModel: " + config.getAnswerModel(),
                    "system", "success", null);
        } catch (Exception e) {
            log.warn("[Log] Failed to record evaluation run log for kb={}", kbId);
        }

        // 通过独立 Bean 调用，确保 @Async 代理生效
        executionHelper.execute(evaluation.getId(), kbId, config);

        return evaluation;
    }

    @Override
    public void delete(String kbId, String id) {
        KbEvaluation existing = mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbEvaluation>()
                .eq(KbEvaluation::getKbId, kbId)
                .eq(KbEvaluation::getId, id));
        if (existing == null) return;
        resultMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbEvaluationResult>()
                .eq(KbEvaluationResult::getEvaluationId, id));
        mapper.deleteById(id);
        // 记录评测删除日志
        try {
            logService.addLog(kbId, LogCategory.operation, ActionType.evaluation_deleted,
                    id, "删除评测记录", "system", "success", null);
        } catch (Exception e) {
            log.warn("[Log] Failed to record evaluation delete log for kb={}, id={}", kbId, id);
        }
    }
}
