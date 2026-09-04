package com.fastrag.module.bpm.entity;

import lombok.Data;

/**
 * 旧 /api/workflows/* 接口的优化建议入参 DTO。
 */
@Data
public class WfOptimization {
    private String title;
    private String description;
    /** suggestion / refactor / performance / cost */
    private String category;
    private Object payload;
}