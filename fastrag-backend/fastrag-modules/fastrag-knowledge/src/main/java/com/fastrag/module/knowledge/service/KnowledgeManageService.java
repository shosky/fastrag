package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.*; import java.util.*;
public interface KnowledgeManageService {
    List<KbKnowledge> list(String kbId,String keyword,String category);
    KbKnowledge get(String id);
    KbKnowledge create(KbKnowledge knowledge);
    KbKnowledge update(String id,KbKnowledge knowledge);
    void delete(String id);
    // 知识测试
    List<KbKnowledgeTest> listTests(String kbId,String knowledgeId);
    KbKnowledgeTest createTest(KbKnowledgeTest test);
    KbKnowledgeTest updateTest(String id,KbKnowledgeTest test);
    // 知识对话
    List<KbKnowledgeDialog> listDialogs(String kbId,String knowledgeId);
    KbKnowledgeDialog createDialog(KbKnowledgeDialog dialog);
    Map<String,Object> judge(String id,String query,String model);
}
