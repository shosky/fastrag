package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbMatterKnowledgeRel; import java.util.*;
public interface MatterKnowledgeRelService {
    List<KbMatterKnowledgeRel> list(String kbId,String matterName,String keyword);
    KbMatterKnowledgeRel get(String id);
    KbMatterKnowledgeRel create(String kbId,KbMatterKnowledgeRel rel);
    KbMatterKnowledgeRel update(String id,KbMatterKnowledgeRel rel);
    void delete(String id);
}
