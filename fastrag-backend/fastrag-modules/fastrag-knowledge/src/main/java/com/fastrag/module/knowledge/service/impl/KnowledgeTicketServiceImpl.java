package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbKnowledgeTicket; import com.fastrag.module.knowledge.mapper.KbKnowledgeTicketMapper;
import com.fastrag.module.knowledge.service.KnowledgeTicketService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class KnowledgeTicketServiceImpl implements KnowledgeTicketService {
    private final KbKnowledgeTicketMapper mapper;
    @Override public List<KbKnowledgeTicket> list(String kbId,String status,String ticketType,String keyword) {
        var w=new LambdaQueryWrapper<KbKnowledgeTicket>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledgeTicket::getKbId,kbId);
        if(status!=null&&!status.isEmpty()) w.eq(KbKnowledgeTicket::getStatus,status);
        if(ticketType!=null&&!ticketType.isEmpty()) w.eq(KbKnowledgeTicket::getTicketType,ticketType);
        if(keyword!=null&&!keyword.isEmpty()) w.like(KbKnowledgeTicket::getTitle,keyword);
        return mapper.selectList(w.orderByDesc(KbKnowledgeTicket::getCreatedAt));
    }
    @Override public KbKnowledgeTicket get(String id) { return mapper.selectById(id); }
    @Override public KbKnowledgeTicket create(String kbId,KbKnowledgeTicket t) {
        t.setId(null); t.setKbId(kbId);
        if(t.getStatus()==null) t.setStatus("open");
        if(t.getPriority()==null) t.setPriority("medium");
        if(t.getTicketType()==null) t.setTicketType("other");
        mapper.insert(t); return t;
    }
    @Override public KbKnowledgeTicket update(String id,KbKnowledgeTicket t) {
        t.setId(id);
        // 状态推进到 resolved/closed 时记录解决时间
        if(("resolved".equals(t.getStatus())||"closed".equals(t.getStatus()))) t.setResolvedAt(LocalDateTime.now());
        mapper.updateById(t); return mapper.selectById(id);
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
}
