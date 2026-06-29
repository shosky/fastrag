package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_knowledge_test") public class KbKnowledgeTest {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,knowledgeId,testQuery,expectedAnswer,actualAnswer,testModel,createdBy;
    private Integer isPassed;
    private Double relevanceScore;
    private LocalDateTime createdAt,updatedAt;
}
