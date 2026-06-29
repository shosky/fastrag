package com.fastrag.module.knowledge.service;
import com.fastrag.common.response.PageResult; import com.fastrag.module.knowledge.entity.KbKnowledgeUpdate; import java.util.*;
public interface KnowledgeUpdateService {
    PageResult<KbKnowledgeUpdate> page(String kbId,String knowledgeId,String updateType,String status,int page,int pageSize);
    List<KbKnowledgeUpdate> list(String kbId,String knowledgeId);
    KbKnowledgeUpdate get(String id);
    KbKnowledgeUpdate create(KbKnowledgeUpdate update);
    KbKnowledgeUpdate update(String id,KbKnowledgeUpdate update);
    void delete(String id);
    KbKnowledgeUpdate apply(String id);
    KbKnowledgeUpdate rollback(String id);
}
