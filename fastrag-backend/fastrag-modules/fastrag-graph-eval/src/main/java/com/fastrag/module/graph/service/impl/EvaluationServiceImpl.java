package com.fastrag.module.graph.service.impl;

/**
 * 评测服务实现类，实现 {@link com.fastrag.module.graph.service.EvaluationService} 接口。
 *
 * <p>提供知识图谱评测任务的管理入口，负责评测任务的创建、查询和启动执行。
 * 实际的评测执行逻辑委托给 {@link EvaluationExecutionHelper} 异步处理。</p>
 *
 * <p>核心业务逻辑：</p>
 * <ul>
 *   <li>列表查询：按创建时间倒序返回指定知识库下的所有评测任务</li>
 *   <li>详情查询：返回评测任务信息及每道题的评测结果明细</li>
 *   <li>启动评测：校验基准测试参数（必须指定benchmark且基准测试必须有题目），
 *       创建评测记录后通过 {@link EvaluationExecutionHelper} 异步执行评测</li>
 *   <li>参数校验失败时创建status为failed的评测记录，避免前端误以为评测已启动</li>
 *   <li>删除：级联删除评测任务关联的所有评测结果记录</li>
 * </ul>
 *
 * <p>与其他模块的交互：</p>
 * <ul>
 *   <li>通过 {@link EvaluationExecutionHelper} 异步执行评测</li>
 *   <li>通过 {@link com.fastrag.module.publish.service.LogService} 记录操作日志</li>
 * </ul>
 *
 * @see com.fastrag.module.graph.service.EvaluationService
 * @see EvaluationExecutionHelper
 */
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.module.graph.entity.KbBenchmarkQuestion;
import com.fastrag.module.graph.entity.KbEvaluation;
import com.fastrag.module.graph.entity.KbEvaluationResult;
import com.fastrag.module.graph.mapper.KbBenchmarkQuestionMapper;
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
    private final KbBenchmarkQuestionMapper questionMapper;
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
            return createFailedEvaluation(kbId, config, benchmark);
        }

        // 校验基准存在且有题目，避免空基准跑出"completed 0 分"的误导结果
        long questionCount = questionMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KbBenchmarkQuestion>()
                        .eq(KbBenchmarkQuestion::getBenchmarkId, benchmark));
        if (questionCount == 0) {
            log.warn("[Evaluation] Rejected: benchmark '{}' has no questions", benchmark);
            return createFailedEvaluation(kbId, config, benchmark);
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

    /** 参数/基准校验失败：创建一条 failed 评估记录，避免前端误以为评估已启动 */
    private KbEvaluation createFailedEvaluation(String kbId, EvaluationConfig config, String benchmark) {
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
