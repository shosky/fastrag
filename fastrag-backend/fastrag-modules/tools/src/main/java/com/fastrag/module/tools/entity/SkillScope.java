package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("skill_scope") public class SkillScope {
    @TableId(type=IdType.AUTO) private Long id;
    private String skillId,scopeId,scopeName;
    private Integer enabled;
}
