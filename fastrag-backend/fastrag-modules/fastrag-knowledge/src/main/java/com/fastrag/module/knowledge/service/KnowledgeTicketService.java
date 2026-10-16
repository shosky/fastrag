package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbKnowledgeTicket; import com.fastrag.module.knowledge.entity.KbKnowledgeTicketLog; import java.util.*;
public interface KnowledgeTicketService {
    List<KbKnowledgeTicket> list(String kbId,String status,String ticketType,String keyword);
    KbKnowledgeTicket get(String id);
    KbKnowledgeTicket create(String kbId,KbKnowledgeTicket ticket);
    KbKnowledgeTicket update(String id,KbKnowledgeTicket ticket);
    void delete(String id);
    /** 发起新增/修改/取消发布流程时自动生成工单（status=pending_approval，含首个流程环节与提交日志） */
    KbKnowledgeTicket autoCreate(String kbId,String knowledgeId,String ticketType,String title,String description,String reporter);
    /** 工单流程环节日志（提交/审批/驳回步骤时间轴） */
    List<KbKnowledgeTicketLog> logs(String ticketId);
    /** 审批通过：推进流程环节；末环节通过则回写业务（新增/修改上线、取消发布留档） */
    KbKnowledgeTicket approve(String id,String approver,String comment);
    /** 审批驳回：终止流程并回滚业务（取消发布驳回则恢复取消前状态） */
    KbKnowledgeTicket reject(String id,String approver,String comment);
}
