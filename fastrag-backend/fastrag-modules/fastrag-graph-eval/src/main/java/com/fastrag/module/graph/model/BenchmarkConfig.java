package com.fastrag.module.graph.model;

import lombok.Data;

/**
 * 基准测试自动生成配置参数
 */
@Data
public class BenchmarkConfig {
    /** 基准测试名称 */
    private String name;
    /** 基准测试描述 */
    private String description;
    /** 生成问题数量（默认 10） */
    private Integer questionCount = 10;
    /** 构建方法：vector / graph（默认 vector） */
    private String buildMethod = "vector";
    /** LLM 模型名（为空时使用默认模型） */
    private String llmModel;
    /** 候选文档片段数量（默认 5，vector 模式使用） */
    private Integer candidateChunkCount = 5;
}
