package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_knowledge_update") public class KbKnowledgeUpdate {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,knowledgeId,updateType,title,oldValue,newValue,changeSummary,status,operator;
    private LocalDateTime appliedAt,createdAt;
}
