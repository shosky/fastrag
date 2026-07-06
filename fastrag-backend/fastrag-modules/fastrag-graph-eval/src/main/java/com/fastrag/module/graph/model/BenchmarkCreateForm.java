package com.fastrag.module.graph.model;

import lombok.Data;

/**
 * 手动创建基准测试表单
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
