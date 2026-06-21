package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data;
@Data @TableName("skill_dependency") public class SkillDependency {
    @TableId(type=IdType.AUTO) private Long id;
    private String skillId,type,name;
    private Integer required;
}
