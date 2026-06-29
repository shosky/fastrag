package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_publish_plan") public class KbPublishPlan {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name,knowledgeIds,strategy,executionStatus,executionDetail,createdBy;
    private Integer successCount,failCount;
    private LocalDateTime scheduledTime,executedTime,createdAt;
}
