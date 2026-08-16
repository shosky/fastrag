package com.fastrag.module.graph.model;

import lombok.Data;

/**
 * 手动创建基准测试表单。
 *
 * <p>用于手动创建空的基准测试记录（不含自动生成的题目）。用户创建后可后续通过
 * generate 接口触发LLM自动生成题目。该表单通过 {@link com.fastrag.module.graph.controller.BenchmarkController}
 * 的 create 接口以JSON请求体传入。</p>
 *
 * <p>核心字段：</p>
 * <ul>
 *   <li>{@code name} - 基准测试名称（必填）</li>
 *   <li>{@code description} - 基准测试描述说明</li>
 *   <li>{@code questionCount} - 初始问题数量（默认0，手动创建时通常为0）</li>
 * </ul>
 *
 * @see BenchmarkConfig
 */
@Data
public class BenchmarkCreateForm {
    /** 基准测试名称 */
    private String name;
    /** 基准测试描述 */
    private String description;
    /** 问题数量 */
    private Integer questionCount = 0;
}
