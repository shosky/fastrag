package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.*; import java.util.*;
public interface KnowledgeManageService {
    List<KbKnowledge> list(String kbId,String keyword,String category);
    KbKnowledge get(String id);
    KbKnowledge create(KbKnowledge knowledge);
    KbKnowledge update(String id,KbKnowledge knowledge);
    void delete(String id);
    // 知识回收站：回收列表/恢复/彻底删除/清空（delete 为软删）
    List<KbKnowledge> listDeleted(String kbId);
    void restore(String id);
    void permanentDelete(String id);
    void emptyRecycleBin(String kbId);
    // AI 能力：更新封面图、获取内容
    void updateCoverImage(String id, String objectKey);
    String getContent(String id);
    // 知识测试
    List<KbKnowledgeTest> listTests(String kbId,String knowledgeId);
    KbKnowledgeTest createTest(KbKnowledgeTest test);
    KbKnowledgeTest updateTest(String id,KbKnowledgeTest test);
    void deleteTest(String id);
    // 知识对话
    List<KbKnowledgeDialog> listDialogs(String kbId,String knowledgeId);
    KbKnowledgeDialog createDialog(KbKnowledgeDialog dialog);
    void deleteDialog(String id);
    Map<String,Object> judge(String id,String query,String model);
}
