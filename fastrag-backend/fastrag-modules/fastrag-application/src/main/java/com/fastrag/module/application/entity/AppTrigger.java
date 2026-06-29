package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_trigger") public class AppTrigger {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,name,triggerType,matchContent,actionType,actionConfig,createdBy;
    private Integer enabled,priority,hitCount; private LocalDateTime createdAt,updatedAt;
}
