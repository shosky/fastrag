package com.fastrag.module.retrieval.entity;
import com.baomidou.mybatisplus.annotation.*; import com.fasterxml.jackson.annotation.JsonIgnore; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_knowledge_push") public class KbKnowledgePush {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,title,content,knowledgeId,pushType,status,createdBy;
    @JsonIgnore private String targetUsers;
    private LocalDateTime pushedAt,createdAt,updatedAt;
}
