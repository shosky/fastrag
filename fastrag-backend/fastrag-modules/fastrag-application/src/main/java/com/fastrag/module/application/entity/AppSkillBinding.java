package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_skill_binding") public class AppSkillBinding {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,skillId,skillName,params;
    private Integer enabled; private LocalDateTime createdAt;
}
