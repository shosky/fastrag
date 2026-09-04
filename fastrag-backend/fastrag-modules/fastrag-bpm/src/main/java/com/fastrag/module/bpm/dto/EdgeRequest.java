package com.fastrag.module.bpm.dto;
import lombok.Data;

/** 边创建/更新请求 */
@Data
public class EdgeRequest {
    private String sourceNodeKey, targetNodeKey;
    /** default_edge/condition/parallel/exception */
    private String edgeKind;
    /** SpEL 条件表达式 */
    private String conditionExpr;
    /** 条件参数 JSON 字符串 */
    private String conditionParams;
    private String label;
    private Integer priority;
}