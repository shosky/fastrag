package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_review_node") public class KbReviewNode {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String templateId,nodeName,nodeType,approverRole,config;
    private Integer orderNum;
    private LocalDateTime createdAt;
}
