package com.fastrag.module.bpm.entity;

import lombok.Data;

/**
 * 旧 /api/workflows/* 接口的模板入参 DTO。
 */
@Data
public class WfTemplate {
    private String name;
    private String category;
    private String description;
    /** JSON 字符串：完整流程定义 */
    private String definitionJson;
}