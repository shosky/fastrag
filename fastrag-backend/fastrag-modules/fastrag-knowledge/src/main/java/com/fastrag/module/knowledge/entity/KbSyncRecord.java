package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
/** 知识库同步执行记录 */
@Data @TableName("kb_sync_record") public class KbSyncRecord {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String configId;
    private Integer syncedEntries,syncedQaPairs;
    private String status,message;
    private LocalDateTime createdAt;
}
