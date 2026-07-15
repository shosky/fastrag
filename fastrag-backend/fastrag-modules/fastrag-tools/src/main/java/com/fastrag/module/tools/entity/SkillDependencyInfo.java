package com.fastrag.module.tools.entity;

import lombok.Data;

/**
 * 技能依赖信息 DTO，用于前端传递和验证。
 */
@Data
public class SkillDependencyInfo {
    /** 依赖类型: tool / model / mcp / skill */
    private String type;
    /** 依赖名称 */
    private String name;
    /** 是否必选 */
    private boolean required;
}
