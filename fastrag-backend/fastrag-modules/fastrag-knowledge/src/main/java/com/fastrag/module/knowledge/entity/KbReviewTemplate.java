package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_review_template") public class KbReviewTemplate {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,category,description,flowConfig,createdBy;
    private Integer isBuiltin;
    private LocalDateTime createdAt;
}
