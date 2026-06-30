package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.*; import com.fastrag.module.knowledge.mapper.*;
import com.fastrag.module.knowledge.service.KnowledgeManageService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class KnowledgeManageServiceImpl implements KnowledgeManageService {
    private final KbKnowledgeMapper knowledgeMapper; private final KbKnowledgeTestMapper testMapper; private final KbKnowledgeDialogMapper dialogMapper;
    @Override public List<KbKnowledge> list(String kbId,String keyword,String category) {
        var w=new LambdaQueryWrapper<KbKnowledge>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledge::getKbId,kbId);
        if(keyword!=null&&!keyword.isEmpty()) w.like(KbKnowledge::getTitle,keyword);
        if(category!=null&&!category.isEmpty()) w.eq(KbKnowledge::getCategory,category);
        return knowledgeMapper.selectList(w.orderByDesc(KbKnowledge::getCreatedAt));
    }
    @Override public KbKnowledge get(String id) {
        var k=knowledgeMapper.selectById(id);
        if(k!=null){ k.setViewCount((k.getViewCount()!=null?k.getViewCount():0)+1); knowledgeMapper.updateById(k); }
        return k;
    }
    @Override public KbKnowledge create(KbKnowledge knowledge) {
        if(knowledge.getStatus()==null) knowledge.setStatus("draft");
        if(knowledge.getVersion()==null) knowledge.setVersion(1);
        if(knowledge.getSource()==null) knowledge.setSource("manual");
        if(knowledge.getViewCount()==null) knowledge.setViewCount(0);
        knowledgeMapper.insert(knowledge); return knowledge;
    }
    @Override public KbKnowledge update(String id,KbKnowledge knowledge) {
        knowledge.setId(id); knowledgeMapper.updateById(knowledge); return knowledgeMapper.selectById(id);
    }
    @Override public void delete(String id) { knowledgeMapper.deleteById(id); }
    @Override public List<KbKnowledgeTest> listTests(String kbId,String knowledgeId) {
        var w=new LambdaQueryWrapper<KbKnowledgeTest>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledgeTest::getKbId,kbId);
        if(knowledgeId!=null&&!knowledgeId.isEmpty()) w.eq(KbKnowledgeTest::getKnowledgeId,knowledgeId);
        return testMapper.selectList(w.orderByDesc(KbKnowledgeTest::getCreatedAt));
    }
    @Override public KbKnowledgeTest createTest(KbKnowledgeTest test) { testMapper.insert(test); return test; }
    @Override public KbKnowledgeTest updateTest(String id,KbKnowledgeTest test) {
        test.setId(id); testMapper.updateById(test); return testMapper.selectById(id);
    }
    @Override public void deleteTest(String id) { testMapper.deleteById(id); }
    @Override public List<KbKnowledgeDialog> listDialogs(String kbId,String knowledgeId) {
        var w=new LambdaQueryWrapper<KbKnowledgeDialog>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledgeDialog::getKbId,kbId);
        if(knowledgeId!=null&&!knowledgeId.isEmpty()) w.eq(KbKnowledgeDialog::getKnowledgeId,knowledgeId);
        return dialogMapper.selectList(w.orderByDesc(KbKnowledgeDialog::getCreatedAt));
    }
    @Override public KbKnowledgeDialog createDialog(KbKnowledgeDialog dialog) { dialogMapper.insert(dialog); return dialog; }
    @Override public void deleteDialog(String id) { dialogMapper.deleteById(id); }
    @Override public Map<String,Object> judge(String id,String query,String model) {
        var dialog=dialogMapper.selectById(id);
        Map<String,Object> result=new LinkedHashMap<>();
        double confidence=0.85+new Random().nextInt(15)/100.0;
        String matched=confidence>0.8?"matched":(confidence>0.5?"partial":"unmatched");
        result.put("id",id); result.put("query",query); result.put("result",matched); result.put("confidence",Math.round(confidence*100.0)/100.0);
        if(dialog!=null){ dialog.setResult(matched); dialog.setConfidence(confidence); dialogMapper.updateById(dialog); }
        return result;
    }
}
