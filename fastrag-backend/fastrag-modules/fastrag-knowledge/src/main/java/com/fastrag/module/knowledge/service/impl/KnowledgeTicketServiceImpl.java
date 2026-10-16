package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.*; import com.fastrag.module.knowledge.mapper.*;
import com.fastrag.module.knowledge.service.KnowledgeTicketService;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Slf4j @Service @RequiredArgsConstructor
public class KnowledgeTicketServiceImpl implements KnowledgeTicketService {
    private final KbKnowledgeTicketMapper mapper;
    private final KbKnowledgeTicketLogMapper logMapper;
    private final KbKnowledgeMapper kmMapper;
    private final KbKnowledgeUpdateMapper kuMapper;
    private final KbReviewTemplateMapper rtMapper;
    private final KbReviewNodeMapper rnMapper;
    @Override public List<KbKnowledgeTicket> list(String kbId,String status,String ticketType,String keyword) {
        var w=new LambdaQueryWrapper<KbKnowledgeTicket>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledgeTicket::getKbId,kbId);
        if(status!=null&&!status.isEmpty()) w.eq(KbKnowledgeTicket::getStatus,status);
        if(ticketType!=null&&!ticketType.isEmpty()) w.eq(KbKnowledgeTicket::getTicketType,ticketType);
        if(keyword!=null&&!keyword.isEmpty()) w.like(KbKnowledgeTicket::getTitle,keyword);
        var list=mapper.selectList(w.orderByDesc(KbKnowledgeTicket::getCreatedAt));
        for(var t:list) fillKnowledgeTitle(t);
        return list;
    }
    @Override public KbKnowledgeTicket get(String id) { var t=mapper.selectById(id); fillKnowledgeTitle(t); return t; }
    @Override public KbKnowledgeTicket create(String kbId,KbKnowledgeTicket t) {
        t.setId(null); t.setKbId(kbId);
        if(t.getStatus()==null) t.setStatus("open");
        if(t.getPriority()==null) t.setPriority("medium");
        if(t.getTicketType()==null) t.setTicketType("other");
        mapper.insert(t); return t;
    }
    @Override public KbKnowledgeTicket update(String id,KbKnowledgeTicket t) {
        var old=mapper.selectById(id);
        t.setId(id);
        // 状态推进到 resolved/closed 时记录解决时间
        if(("resolved".equals(t.getStatus())||"closed".equals(t.getStatus()))) t.setResolvedAt(LocalDateTime.now());
        mapper.updateById(t);
        // 状态变更留痕：工单列表内即可查看该工单的改状态历史
        if(old!=null&&t.getStatus()!=null&&!t.getStatus().equals(old.getStatus())) {
            addLog(id,t.getCurrentStep()!=null?t.getCurrentStep():old.getCurrentStep(),"status_change",old.getReporter(),
                "状态变更："+old.getStatus()+" → "+t.getStatus());
        }
        var cur=mapper.selectById(id); fillKnowledgeTitle(cur); return cur;
    }
    @Override public void delete(String id) {
        mapper.deleteById(id);
        // 工单删除后其流程环节日志一并清理，避免孤儿数据
        logMapper.delete(new LambdaQueryWrapper<KbKnowledgeTicketLog>().eq(KbKnowledgeTicketLog::getTicketId,id));
    }

    // ===== 自动生成（发起新增/修改/取消发布流程时由业务侧调用） =====
    @Override public KbKnowledgeTicket autoCreate(String kbId,String knowledgeId,String ticketType,String title,String description,String reporter) {
        var t=new KbKnowledgeTicket();
        t.setKbId(kbId); t.setKnowledgeId(knowledgeId); t.setTitle(title); t.setDescription(description);
        t.setTicketType(ticketType); t.setPriority("medium"); t.setReporter(reporter);
        t.setStatus("pending_approval"); t.setSubmittedAt(LocalDateTime.now());
        String[] flow=resolveFlow();
        t.setFlowName(flow[0]); t.setCurrentStep(flow[1]);
        mapper.insert(t);
        addLog(t.getId(),t.getCurrentStep(),"submit",reporter,"发起"+typeLabel(ticketType)+"流程，等待"+t.getCurrentStep()+"审批");
        log.info("自动生成知识工单: {} type={} kb={} knowledge={}",t.getId(),ticketType,kbId,knowledgeId);
        return t;
    }
    @Override public List<KbKnowledgeTicketLog> logs(String ticketId) {
        return logMapper.selectList(new LambdaQueryWrapper<KbKnowledgeTicketLog>()
            .eq(KbKnowledgeTicketLog::getTicketId,ticketId).orderByAsc(KbKnowledgeTicketLog::getCreatedAt));
    }

    // ===== 审批 =====
    @Override public KbKnowledgeTicket approve(String id,String approver,String comment) {
        var t=mapper.selectById(id);
        if(t==null) throw new RuntimeException("工单不存在");
        String step=t.getCurrentStep()==null?"初审":t.getCurrentStep();
        String next=nextStep(step);
        t.setApprover(approver); t.setApprovalComment(comment); t.setApprovedAt(LocalDateTime.now());
        if(next!=null){
            // 还有下一环节：仍是审批中，环节前进
            t.setCurrentStep(next); t.setStatus("pending_approval");
            mapper.updateById(t);
            addLog(id,step,"approve",approver,appendComment(step+"通过，流转至"+next,comment));
        } else {
            // 末环节通过：流程结束并回写业务
            t.setCurrentStep(step); t.setStatus("approved"); t.setResolvedAt(LocalDateTime.now());
            mapper.updateById(t);
            addLog(id,step,"approve",approver,appendComment(step+"通过，流程结束",comment));
            applyBusiness(t,true);
        }
        var cur=mapper.selectById(id); fillKnowledgeTitle(cur); return cur;
    }
    @Override public KbKnowledgeTicket reject(String id,String approver,String comment) {
        var t=mapper.selectById(id);
        if(t==null) throw new RuntimeException("工单不存在");
        String step=t.getCurrentStep()==null?"初审":t.getCurrentStep();
        t.setApprover(approver); t.setApprovalComment(comment); t.setApprovedAt(LocalDateTime.now());
        t.setStatus("rejected"); t.setResolvedAt(LocalDateTime.now());
        mapper.updateById(t);
        addLog(id,step,"reject",approver,appendComment(step+"驳回",comment));
        applyBusiness(t,false);
        var cur=mapper.selectById(id); fillKnowledgeTitle(cur); return cur;
    }

    /** 审批结果回写业务：新增/修改通过→发布；驳回→回草稿；取消发布被驳回→恢复取消前状态 */
    private void applyBusiness(KbKnowledgeTicket t,boolean approved) {
        String knowledgeId=t.getKnowledgeId();
        if(knowledgeId==null||knowledgeId.isBlank()) return;
        var k=kmMapper.selectById(knowledgeId);
        if(k==null) return;
        String type=t.getTicketType()==null?"other":t.getTicketType();
        if(approved) {
            if("create".equals(type)||"update".equals(type)) { k.setStatus("published"); kmMapper.updateById(k); }
            // offline：下架已即时生效，审批通过仅作流程留档
        } else if("offline".equals(type)) {
            String prev=previousStatusBeforeRevoke(knowledgeId);
            k.setStatus(prev==null||prev.isBlank()?"draft":prev);
            kmMapper.updateById(k);
        } else if("create".equals(type)||"update".equals(type)) {
            k.setStatus("draft"); kmMapper.updateById(k);
        }
    }
    /** 取消发布前的状态：取该知识点最近一条 revoke 留痕的 oldValue（由 PublishManageServiceImpl 写入） */
    private String previousStatusBeforeRevoke(String knowledgeId) {
        var list=kuMapper.selectList(new LambdaQueryWrapper<KbKnowledgeUpdate>()
            .eq(KbKnowledgeUpdate::getKnowledgeId,knowledgeId).eq(KbKnowledgeUpdate::getUpdateType,"revoke")
            .orderByDesc(KbKnowledgeUpdate::getCreatedAt).last("LIMIT 1"));
        return list.isEmpty()?null:list.get(0).getOldValue();
    }

    /** 流程环节推进：在审核模板里找到当前环节节点，取其下一节点；找不到或已是末节点返回 null（流程结束） */
    private String nextStep(String currentStep) {
        try {
            for(var tpl:rtMapper.selectList(null)) {
                var nodes=rnMapper.selectList(new LambdaQueryWrapper<KbReviewNode>()
                    .eq(KbReviewNode::getTemplateId,tpl.getId()).orderByAsc(KbReviewNode::getOrderNum));
                for(int i=0;i<nodes.size();i++) {
                    var name=nodes.get(i).getNodeName();
                    if(name!=null&&name.equals(currentStep)) {
                        return i+1<nodes.size()?nodes.get(i+1).getNodeName():null;
                    }
                }
            }
        } catch (Exception e) { log.warn("流程环节推进查询失败: {}",e.getMessage()); }
        return null;
    }
    /** 流程名与首个环节：取第一个审核流程模板的首节点，缺省「知识审核流程 / 初审」 */
    private String[] resolveFlow() {
        try {
            var tpl=rtMapper.selectList(new LambdaQueryWrapper<KbReviewTemplate>()
                .orderByAsc(KbReviewTemplate::getCreatedAt).last("LIMIT 1"));
            if(!tpl.isEmpty()) {
                var t=tpl.get(0);
                var nodes=rnMapper.selectList(new LambdaQueryWrapper<KbReviewNode>()
                    .eq(KbReviewNode::getTemplateId,t.getId()).orderByAsc(KbReviewNode::getOrderNum));
                String first=(!nodes.isEmpty()&&nodes.get(0).getNodeName()!=null&&!nodes.get(0).getNodeName().isBlank())
                    ?nodes.get(0).getNodeName():"初审";
                return new String[]{t.getName()==null||t.getName().isBlank()?"知识审核流程":t.getName(),first};
            }
        } catch (Exception e) { log.warn("审核流程模板读取失败: {}",e.getMessage()); }
        return new String[]{"知识审核流程","初审"};
    }
    private void fillKnowledgeTitle(KbKnowledgeTicket t) {
        if(t==null||t.getKnowledgeId()==null||t.getKnowledgeId().isBlank()) return;
        var k=kmMapper.selectById(t.getKnowledgeId());
        if(k!=null) t.setKnowledgeTitle(k.getTitle());
    }
    private void addLog(String ticketId,String step,String action,String operator,String comment) {
        try {
            var l=new KbKnowledgeTicketLog();
            l.setTicketId(ticketId); l.setStepName(step); l.setAction(action);
            l.setOperator(operator); l.setComment(comment); l.setCreatedAt(LocalDateTime.now());
            logMapper.insert(l);
        } catch (Exception e) { log.warn("写工单流程日志失败: {}",e.getMessage()); }
    }
    private static String appendComment(String base,String comment) {
        return comment==null||comment.isBlank()?base:base+"；意见："+comment;
    }
    private static String typeLabel(String type) {
        if(type==null) return "其他";
        return switch(type) { case "create" -> "新增"; case "update" -> "修改"; case "offline" -> "取消发布"; case "review" -> "审核"; default -> "其他"; };
    }
}
