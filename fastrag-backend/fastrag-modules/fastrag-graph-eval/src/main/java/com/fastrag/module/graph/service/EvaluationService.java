package com.fastrag.module.graph.service;

/**
 * 知识图谱评测任务业务服务接口。
 *
 * <p>定义评测任务的核心业务操作，包括评测任务列表查询、详情获取、启动执行和删除。
 * 实现类为 {@link com.fastrag.module.graph.service.impl.EvaluationServiceImpl}。</p>
 *
 * <p>核心业务流程：</p>
 * <ul>
 *   <li>{@code list} - 查询指定知识库下的所有评测任务</li>
 *   <li>{@code getDetail} - 获取评测任务详情（包含汇总的评测结果）</li>
 *   <li>{@code run} - 启动评测执行，逐题查询LLM生成答案并评判正确性，
 *       计算检索召回率和答案准确率，最终汇总生成评测报告。支持异步执行</li>
 *   <li>{@code delete} - 删除评测任务及其关联的所有评测结果记录</li>
 * </ul>
 *
 * @see com.fastrag.module.graph.service.impl.EvaluationServiceImpl
 * @see EvaluationConfig
 */
import com.fastrag.module.graph.entity.KbEvaluation;
import com.fastrag.module.graph.model.EvaluationConfig;

import java.util.List;

public interface EvaluationService {
    List<KbEvaluation> list(String kbId);

    KbEvaluation getDetail(String kbId, String id);

    KbEvaluation run(String kbId, EvaluationConfig config);

    void delete(String kbId, String id);
}
