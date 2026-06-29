package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.*; import com.fastrag.module.knowledge.mapper.*;
import com.fastrag.module.knowledge.service.KnowledgeQaService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class KnowledgeQaServiceImpl implements KnowledgeQaService {
    private final KbMultiTurnQaMapper mtMapper; private final KbMultimodalQaMapper mmMapper; private final KbDocGuideMapper dgMapper;
    // ===== 多轮问答 =====
    @Override public List<KbMultiTurnQa> listMultiTurnQa(String kbId) { return mtMapper.selectList(new LambdaQueryWrapper<KbMultiTurnQa>().eq(kbId!=null&&!kbId.isEmpty(),KbMultiTurnQa::getKbId,kbId).orderByDesc(KbMultiTurnQa::getCreatedAt)); }
    @Override public KbMultiTurnQa createMultiTurnQa(KbMultiTurnQa qa) { if(qa.getStatus()==null) qa.setStatus("active"); mtMapper.insert(qa); return qa; }
    @Override public KbMultiTurnQa updateMultiTurnQa(String id,KbMultiTurnQa qa) { qa.setId(id); mtMapper.updateById(qa); return mtMapper.selectById(id); }
    @Override public void deleteMultiTurnQa(String id) { mtMapper.deleteById(id); }
    @Override public KbMultiTurnQa getMultiTurnQa(String id) { return mtMapper.selectById(id); }
    // ===== 多模态问答 =====
    @Override public List<KbMultimodalQa> listMultimodalQa(String kbId) { return mmMapper.selectList(new LambdaQueryWrapper<KbMultimodalQa>().eq(kbId!=null&&!kbId.isEmpty(),KbMultimodalQa::getKbId,kbId).orderByDesc(KbMultimodalQa::getCreatedAt)); }
    @Override public KbMultimodalQa createMultimodalQa(KbMultimodalQa qa) { if(qa.getStatus()==null) qa.setStatus("active"); mmMapper.insert(qa); return qa; }
    @Override public KbMultimodalQa updateMultimodalQa(String id,KbMultimodalQa qa) { qa.setId(id); mmMapper.updateById(qa); return mmMapper.selectById(id); }
    @Override public void deleteMultimodalQa(String id) { mmMapper.deleteById(id); }
    @Override public KbMultimodalQa getMultimodalQa(String id) { return mmMapper.selectById(id); }
    // ===== 文档导读 =====
    @Override public List<KbDocGuide> listDocGuides(String kbId) { return dgMapper.selectList(new LambdaQueryWrapper<KbDocGuide>().eq(kbId!=null&&!kbId.isEmpty(),KbDocGuide::getKbId,kbId).orderByDesc(KbDocGuide::getCreatedAt)); }
    @Override public KbDocGuide createDocGuide(KbDocGuide guide) {
        if(guide.getIndexStatus()==null) guide.setIndexStatus("pending");
        if(guide.getIndexProgress()==null) guide.setIndexProgress(0);
        // 模拟LLM生成摘要/大纲/要点
        if(guide.getSummary()==null) guide.setSummary("文档摘要（自动生成）：本文档描述了相关业务的流程与规范。");
        if(guide.getOutline()==null) guide.setOutline("[{\"level\":1,\"title\":\"概述\"},{\"level\":2,\"title\":\"核心功能\"},{\"level\":2,\"title\":\"使用说明\"}]");
        if(guide.getKeyPoints()==null) guide.setKeyPoints("[\"核心概念说明\",\"操作流程要点\",\"注意事项\"]");
        dgMapper.insert(guide); return guide;
    }
    @Override public KbDocGuide updateDocGuide(String id,KbDocGuide guide) { guide.setId(id); dgMapper.updateById(guide); return dgMapper.selectById(id); }
    @Override public void deleteDocGuide(String id) { dgMapper.deleteById(id); }
    @Override public KbDocGuide getDocGuide(String id) { return dgMapper.selectById(id); }
    @Override public KbDocGuide indexDocGuide(String id) {
        var g=dgMapper.selectById(id);
        if(g!=null){ g.setIndexStatus("completed"); g.setIndexProgress(100); dgMapper.updateById(g); }
        return g;
    }
    // ===== 多模态检索（模拟） =====
    @Override public Map<String,Object> multimodalSearch(String kbId,String modality,String query,int topK) {
        Map<String,Object> result=new LinkedHashMap<>();
        List<Map<String,Object>> results=new ArrayList<>();
        results.add(Map.of("id","res_001","name",query+"相关结果","score",0.92));
        results.add(Map.of("id","res_002","name","相关内容","score",0.85));
        result.put("results",results); result.put("total",results.size()); result.put("query",query);
        return result;
    }
    @Override public Map<String,Object> multimodalSort(String kbId,String modality,List<String> ids,String sortBy) {
        Map<String,Object> result=new LinkedHashMap<>();
        List<Map<String,Object>> sorted=new ArrayList<>();
        for(int i=0;i<ids.size();i++){ sorted.add(Map.of("id",ids.get(i),"rank",i+1,"score",0.9-i*0.1)); }
        result.put("sortBy",sortBy); result.put("sorted",sorted);
        return result;
    }
}
