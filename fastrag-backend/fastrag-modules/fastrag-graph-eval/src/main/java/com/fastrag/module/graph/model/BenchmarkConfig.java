package com.fastrag.module.graph.model;

import lombok.Data;

/**
 * 基准测试自动生成配置参数。
 *
 * <p>用于配置LLM自动生成基准测试题目时的各项参数，包括生成的题目数量、构建方法、
 * 使用的LLM模型等。该配置通过 {@link com.fastrag.module.graph.controller.BenchmarkController}
 * 的 generate 接口传入，由 {@link com.fastrag.module.graph.service.BenchmarkService} 使用。</p>
 *
 * <p>核心配置项：</p>
 * <ul>
 *   <li>{@code name} - 生成的基准测试名称</li>
 *   <li>{@code description} - 基准测试描述</li>
 *   <li>{@code questionCount} - 生成问题数量（默认10）</li>
 *   <li>{@code buildMethod} - 构建方法，支持 vector（向量检索）和 graph（图谱检索）两种模式</li>
 *   <li>{@code llmModel} - 生成题目使用的LLM模型名，为空时使用系统默认模型</li>
 *   <li>{@code candidateChunkCount} - 候选文档片段数量（默认5），在vector模式下用于确定检索召回范围</li>
 * </ul>
 *
 * @see BenchmarkCreateForm
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
