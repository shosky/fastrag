package com.fastrag.module.graph.model;

import lombok.Data;

/**
 * 评测运行配置参数。
 *
 * <p>用于配置评测任务的执行参数，包括使用的基准测试、LLM模型选择和检索策略配置。
 * 该配置通过 {@link com.fastrag.module.graph.controller.EvaluationController}
 * 的 run 接口以JSON请求体传入，由 {@link com.fastrag.module.graph.service.EvaluationService} 使用。</p>
 *
 * <p>核心配置项分为三部分：</p>
 * <ul>
 *   <li>基础配置：评测名称、引用的基准测试ID</li>
 *   <li>模型配置：答案生成模型（answerModel）和评判模型（judgeModel）。
 *       judgeModel为空时使用规则评判（字符串匹配），非空时使用LLM评判</li>
 *   <li>检索配置：检索模式（vector/hybrid/fulltext）、向量化模型、
 *       是否启用Rerank重排序及重排序模型</li>
 * </ul>
 *
 * @see BenchmarkConfig
 */
@Data
public class EvaluationConfig {
    /** 评估名称 */
    private String name;
    /** 基准测试 ID */
    private String benchmark;
    /** 答案生成模型 */
    private String answerModel;
    /** 评判模型（为空时使用规则评判） */
    private String judgeModel;

    // ===== 检索配置 =====
    /** 检索模式：vector / hybrid / fulltext（默认 hybrid） */
    private String retrievalMode = "hybrid";
    /** 向量化模型名 */
    private String embeddingModel;
    /** 是否启用 Rerank 重排序 */
    private Boolean enableRerank = false;
    /** 重排序模型名 */
    private String rerankModel;
}
