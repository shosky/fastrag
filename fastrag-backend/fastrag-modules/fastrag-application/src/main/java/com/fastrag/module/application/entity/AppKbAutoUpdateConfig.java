package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_kb_auto_update_config") public class AppKbAutoUpdateConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String appId;
    private Integer enabled;
    private String cronExpr;
    private Integer autoPublish;
    private String notifyChannels;
    private LocalDateTime createdAt, updatedAt;
}
