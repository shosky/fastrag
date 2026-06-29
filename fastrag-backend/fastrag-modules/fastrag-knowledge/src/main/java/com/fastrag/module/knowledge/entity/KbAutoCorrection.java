package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_auto_correction") public class KbAutoCorrection {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,wrongText,correctText,matchType,createdBy;
    private Integer priority,enabled,hitCount;
    private LocalDateTime createdAt;
}
