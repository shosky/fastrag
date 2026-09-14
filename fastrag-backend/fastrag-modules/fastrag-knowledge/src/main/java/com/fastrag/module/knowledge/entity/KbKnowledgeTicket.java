package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
// 知识工单：围绕知识条目的加工/纠错/下线等任务工单
@Data @TableName("kb_knowledge_ticket") public class KbKnowledgeTicket {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,title,description,ticketType,priority,knowledgeId,assignee,reporter,status,remark;
    private LocalDateTime createdAt,updatedAt,resolvedAt;
}
