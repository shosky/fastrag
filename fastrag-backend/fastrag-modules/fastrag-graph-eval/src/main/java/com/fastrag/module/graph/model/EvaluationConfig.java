package com.fastrag.module.graph.model;

import lombok.Data;

/**
 * 评估运行配置参数
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
