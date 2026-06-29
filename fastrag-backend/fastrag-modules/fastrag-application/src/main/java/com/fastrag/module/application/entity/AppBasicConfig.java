package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_basic_config") public class AppBasicConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,outputFormat,responseLanguage,greeting,goodbyeMessage,advancedOptions,updatedBy;
    private Integer memoryRounds,timeoutSeconds,maxInputLength; private LocalDateTime createdAt,updatedAt;
}
