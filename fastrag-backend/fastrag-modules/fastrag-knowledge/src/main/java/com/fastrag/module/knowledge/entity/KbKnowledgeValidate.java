package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_knowledge_validate") public class KbKnowledgeValidate {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,validateType,targetScope,targetValue,result,status,createdBy;
    private Integer totalCount,passedCount,warningCount,failedCount;
    private LocalDateTime createdAt,completedAt;
}
