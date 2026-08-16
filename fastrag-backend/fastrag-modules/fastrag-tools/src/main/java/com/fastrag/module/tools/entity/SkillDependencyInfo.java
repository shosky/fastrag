package com.fastrag.module.tools.entity;

import lombok.Data;

/**
 * 技能依赖信息 DTO，用于前端传递和依赖校验。
 *
 * <p>非数据库实体，仅作为请求参数或响应对象使用。
 * 包含依赖类型（tool/model/mcp/skill）、名称和是否必选标记。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code type} - 依赖类型：tool（工具）/ model（模型）/ mcp（MCP 服务）/ skill（其他技能）</li>
 *   <li>{@code name} - 依赖名称</li>
 *   <li>{@code required} - 是否必选</li>
 * </ul>
 *
 * @see SkillDependency
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
