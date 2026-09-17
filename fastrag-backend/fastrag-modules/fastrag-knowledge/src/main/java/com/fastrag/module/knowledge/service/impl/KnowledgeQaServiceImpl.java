package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.*; import com.fastrag.module.knowledge.mapper.*;
import com.fastrag.module.knowledge.service.KnowledgeQaService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class KnowledgeQaServiceImpl implements KnowledgeQaService {
    private final KbMultiTurnQaMapper mtMapper; private final KbMultimodalQaMapper mmMapper; private final KbDocGuideMapper dgMapper;
    private final KbMediaStorageMapper mediaMapper;
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
    // ===== 多模态检索（真实：对多媒体库 名称/描述/OCR文本/转写 打分检索） =====
    @Override public Map<String,Object> multimodalSearch(String kbId,String modality,String query,int topK) {
        Map<String,Object> result=new LinkedHashMap<>();
        List<Map<String,Object>> results=new ArrayList<>();
        List<String> tokens=tokenize(query);
        List<KbMediaStorage> media=mediaMapper.selectList(new LambdaQueryWrapper<KbMediaStorage>().eq(KbMediaStorage::getKbId,kbId));
        for(var m:media){
            if(modality!=null&&!modality.isBlank()&&!modality.equalsIgnoreCase("all")&&!modality.equalsIgnoreCase(m.getMediaType())) continue;
            String hay=String.join("\n", nvl(m.getName()),nvl(m.getOriginalName()),nvl(m.getDescription()),nvl(m.getOcrText()),nvl(m.getTranscript()));
            if(tokens.isEmpty()) continue;
            long hit=tokens.stream().filter(hay::contains).count();
            if(hit<=0) continue;
            double score=Math.min(1.0,0.4+0.6*hit/Math.max(1,tokens.size()+1));
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("id",m.getId()); item.put("name",m.getName()); item.put("mediaType",m.getMediaType());
            item.put("thumbnailKey",m.getThumbnailKey()); item.put("description",m.getDescription());
            item.put("score",Math.round(score*100)/100.0);
            results.add(item);
        }
        results.sort((a,b)->Double.compare((Double)b.get("score"),(Double)a.get("score")));
        if(results.size()>topK) results=new ArrayList<>(results.subList(0,topK));
        result.put("results",results); result.put("total",results.size()); result.put("query",query); result.put("modality",modality);
        return result;
    }
    @Override public Map<String,Object> multimodalSort(String kbId,String modality,List<String> ids,String sortBy) {
        Map<String,Object> result=new LinkedHashMap<>();
        List<Map<String,Object>> sorted=new ArrayList<>();
        List<KbMediaStorage> media=(ids==null||ids.isEmpty())?List.of():mediaMapper.selectBatchIds(ids);
        // sortBy: relevance(按传入的检索相关度顺序) / name / createdAt / size
        if("name".equalsIgnoreCase(sortBy)) media.sort(Comparator.comparing(KbMediaStorage::getName,Comparator.nullsLast(String::compareTo)));
        else if("size".equalsIgnoreCase(sortBy)) media.sort(Comparator.comparing(KbMediaStorage::getSize,Comparator.nullsLast(Long::compareTo)).reversed());
        else if("createdAt".equalsIgnoreCase(sortBy)) media.sort(Comparator.comparing(KbMediaStorage::getCreatedAt,Comparator.nullsLast(LocalDateTime::compareTo)).reversed());
        else if(ids!=null&&!ids.isEmpty()){
            // relevance：保持调用方传入的 ids 顺序（即检索相关度顺序）
            Map<String,Integer> rank=new java.util.HashMap<>();
            for(int i=0;i<ids.size();i++) rank.put(ids.get(i),i);
            media.sort(Comparator.comparingInt(m->rank.getOrDefault(m.getId(),Integer.MAX_VALUE)));
        }
        for(int i=0;i<media.size();i++){ var m=media.get(i); sorted.add(Map.of("id",m.getId(),"rank",i+1,"name",nvl(m.getName()),"score",Math.round((1.0-i*0.05)*100)/100.0)); }
        result.put("sortBy",sortBy); result.put("sorted",sorted);
        return result;
    }
    private List<String> tokenize(String query) {
        if(query==null||query.isBlank()) return List.of();
        LinkedHashSet<String> out=new LinkedHashSet<>();
        for(String seg:query.trim().split("[\\s，。；！？,.;!?、]+")){
            if(seg.length()>=2) out.add(seg);
            for(int i=0;i+2<=seg.length();i++) out.add(seg.substring(i,i+2));
        }
        return new ArrayList<>(out);
    }
    private String nvl(String s){ return s==null?"":s; }
}
