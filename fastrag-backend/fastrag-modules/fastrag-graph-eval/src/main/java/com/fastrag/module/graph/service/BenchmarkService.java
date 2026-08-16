package com.fastrag.module.graph.service;

/**
 * 知识图谱基准测试业务服务接口。
 *
 * <p>定义基准测试的核心业务操作，包括基准测试的列表查询、详情获取、手动创建、
 * 自动生成（基于LLM从图谱三元组数据生成测试题目）和删除。
 * 实现类为 {@link com.fastrag.module.graph.service.impl.BenchmarkServiceImpl}。</p>
 *
 * <p>核心业务流程：</p>
 * <ul>
 *   <li>{@code list} - 查询指定知识库下的所有基准测试</li>
 *   <li>{@code getDetail} - 获取单个基准测试的详细信息</li>
 *   <li>{@code listQuestions} - 获取基准测试关联的所有题目</li>
 *   <li>{@code create} - 手动创建空的基准测试记录（不含题目）</li>
 *   <li>{@code generate} - 调用LLM，基于图谱三元组自动生成测试题目，
 *       支持vector和graph两种构建方法，生成的题目包含标准答案和标准检索chunk</li>
 *   <li>{@code delete} - 删除基准测试及其关联的所有题目</li>
 * </ul>
 *
 * @see com.fastrag.module.graph.service.impl.BenchmarkServiceImpl
 * @see BenchmarkConfig
 * @see BenchmarkCreateForm
 */
import com.fastrag.module.graph.entity.KbBenchmark;
import com.fastrag.module.graph.entity.KbBenchmarkQuestion;
import com.fastrag.module.graph.model.BenchmarkConfig;
import com.fastrag.module.graph.model.BenchmarkCreateForm;

import java.util.List;

public interface BenchmarkService {
    List<KbBenchmark> list(String kbId);

    KbBenchmark getDetail(String kbId, String id);

    List<KbBenchmarkQuestion> listQuestions(String kbId, String benchmarkId);

    KbBenchmark create(String kbId, BenchmarkCreateForm form);

    KbBenchmark generate(String kbId, BenchmarkConfig config);

    void delete(String kbId, String id);
}
