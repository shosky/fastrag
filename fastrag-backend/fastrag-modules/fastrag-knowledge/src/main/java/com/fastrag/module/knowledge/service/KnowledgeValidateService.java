package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.KbKnowledgeValidate; import java.util.*;
public interface KnowledgeValidateService {
    List<KbKnowledgeValidate> list(String kbId);
    KbKnowledgeValidate get(String id);
    KbKnowledgeValidate check(KbKnowledgeValidate validate);
}
