package com.fastrag.module.tools.entity;

/**
 * 技能作用域实体，对应数据库表 {@code skill_scope}。
 *
 * <p>记录技能在不同作用域（Agent 配置、知识库绑定等）中的启用状态，
 * 控制技能在哪些场景下可被调用。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code id} - 主键（自增）</li>
 *   <li>{@code skillId} - 所属技能 ID</li>
 *   <li>{@code scopeId} - 作用域 ID（如 Agent 配置 ID）</li>
 *   <li>{@code scopeName} - 作用域名称</li>
 *   <li>{@code enabled} - 在该作用域下是否启用</li>
 * </ul>
 *
 * @see Skill
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("skill_scope") public class SkillScope {
    @TableId(type=IdType.AUTO) private Long id;
    private String skillId,scopeId,scopeName;
    private Integer enabled;
}
