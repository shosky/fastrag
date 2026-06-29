package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_global_policy") public class AppGlobalPolicy {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,sensitiveWordMode,fallbackText,unmatchedAction,unmatchedConfig,updatedBy;
    private Integer safetyEnabled,unmatchedEnabled; private LocalDateTime createdAt,updatedAt;
}
