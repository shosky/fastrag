package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
/** 知识库同步配置：源库 → 目标库（知识条目 + 问答对） */
@Data @TableName("kb_sync_config") public class KbSyncConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,sourceKbId,targetKbId;
    private String syncMode; // full / incremental
    private Integer intervalMinutes;
    private Integer enabled;
    private LocalDateTime lastSyncAt,createdAt;
    private String createdBy;
}
