package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_knowledge_dialog") public class KbKnowledgeDialog {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,knowledgeId,dialogType,messages,result,createdBy;
    private Double confidence;
    private LocalDateTime createdAt;
}
