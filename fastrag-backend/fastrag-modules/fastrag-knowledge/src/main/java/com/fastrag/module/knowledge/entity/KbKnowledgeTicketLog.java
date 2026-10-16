package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
// 知识工单流程环节日志：记录 提交/审批/驳回 等步骤，供「流程环节」时间轴展示工单在流程中走到哪一步
@Data @TableName("kb_knowledge_ticket_log") public class KbKnowledgeTicketLog {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String ticketId,stepName,action,operator,comment;
    private LocalDateTime createdAt;
}
