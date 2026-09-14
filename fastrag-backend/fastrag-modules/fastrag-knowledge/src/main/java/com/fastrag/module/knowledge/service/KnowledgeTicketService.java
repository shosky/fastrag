package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbKnowledgeTicket; import java.util.*;
public interface KnowledgeTicketService {
    List<KbKnowledgeTicket> list(String kbId,String status,String ticketType,String keyword);
    KbKnowledgeTicket get(String id);
    KbKnowledgeTicket create(String kbId,KbKnowledgeTicket ticket);
    KbKnowledgeTicket update(String id,KbKnowledgeTicket ticket);
    void delete(String id);
}
