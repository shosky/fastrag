package com.fastrag.module.tools.entity;

/**
 * 技能依赖实体，对应数据库表 {@code skill_dependency}。
 *
 * <p>记录技能对其他能力的依赖关系，用于安装前的依赖校验和运行时检查。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code id} - 主键（自增）</li>
 *   <li>{@code skillId} - 所属技能 ID</li>
 *   <li>{@code type} - 依赖类型（tool / model / mcp / skill）</li>
 *   <li>{@code name} - 依赖名称</li>
 *   <li>{@code required} - 是否为必选依赖</li>
 * </ul>
 *
 * @see Skill
 * @see SkillDependencyInfo
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("skill_dependency") public class SkillDependency {
    @TableId(type=IdType.AUTO) private Long id;
    private String skillId,type,name;
    private Integer required;
}
