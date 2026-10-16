package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.*; import java.util.*;
public interface KnowledgeManageService {
    /** 知识条目列表；attrDefId/attrValue 用于按自定义属性筛选（attrValue=__none__ 表示筛"无该属性值"的条目） */
    List<KbKnowledge> list(String kbId,String keyword,String category,String attrDefId,String attrValue);
    /** 按知识属性分组统计：返回各属性值分组及其条目数（含未分组条目数），供"依据知识属性进行分组"展示 */
    Map<String,Object> attributeGroups(String kbId,String attrDefId);
    KbKnowledge get(String id);
    KbKnowledge create(KbKnowledge knowledge);
    KbKnowledge update(String id,KbKnowledge knowledge);
    void delete(String id);
    // 知识回收站：回收列表（聚合 用户删除/取消发布/失效 三类来源）/恢复/彻底删除/清空（delete 为软删）
    List<KbKnowledge> listDeleted(String kbId);
    void restore(String id);
    void permanentDelete(String id);
    void emptyRecycleBin(String kbId);
    // 失效扫描：valid_until 到期置为 expired，纳入回收站失效来源
    int expireScan(String kbId);
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
