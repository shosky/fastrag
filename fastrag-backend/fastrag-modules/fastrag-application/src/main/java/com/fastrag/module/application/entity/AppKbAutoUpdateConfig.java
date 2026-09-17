package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
/** 应用知识自动更新配置：仅映射 app_kb_auto_update_config 实际存在的列（此前实体含大量非本表字段导致 SELECT 报 Unknown column） */
@Data @TableName("app_kb_auto_update_config") public class AppKbAutoUpdateConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String appId;
    private Integer enabled;
    /** 明细配置 JSON（cron/通知渠道/模式等） */
    private String config;
    private LocalDateTime createdAt,updatedAt;
}
