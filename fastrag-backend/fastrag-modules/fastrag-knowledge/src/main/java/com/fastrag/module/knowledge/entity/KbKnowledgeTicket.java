package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
// 知识工单：围绕知识条目的加工/纠错/下线等任务工单；新增/修改/取消发布发起流程后自动生成，需审批
@Data @TableName("kb_knowledge_ticket") public class KbKnowledgeTicket {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String kbId,title,description,ticketType,priority,knowledgeId,assignee,reporter,status,remark;
    private LocalDateTime createdAt,updatedAt,resolvedAt;
    // 审批与流程环节：approver 审批人、currentStep 当前环节、flowName 流程名、submittedAt/approvedAt 时间、approvalComment 审批意见
    private String approver,approvalComment,flowName,currentStep;
    private LocalDateTime submittedAt,approvedAt;
    // 关联知识点标题（非表字段，列表/详情展示"流程中的知识点"用）
    @TableField(exist = false) private String knowledgeTitle;
}
