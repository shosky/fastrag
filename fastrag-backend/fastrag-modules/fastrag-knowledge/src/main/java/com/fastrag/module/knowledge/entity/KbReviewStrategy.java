package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_review_strategy") public class KbReviewStrategy {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,name,strategyType,config,createdBy;
    private Integer enabled;
    private LocalDateTime createdAt;
}
