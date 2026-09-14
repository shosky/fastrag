package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
// 事项知识关联：业务事项（办事/服务事项）与知识条目的关联关系
@Data @TableName("kb_matter_knowledge_rel") public class KbMatterKnowledgeRel {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,matterName,matterCode,knowledgeId,knowledgeTitle,relationType,remark,createdBy;
    private LocalDateTime createdAt,updatedAt;
}
