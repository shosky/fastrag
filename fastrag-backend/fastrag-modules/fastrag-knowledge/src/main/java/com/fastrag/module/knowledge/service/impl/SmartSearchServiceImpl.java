package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.knowledge.entity.*; import com.fastrag.module.knowledge.mapper.*;
import com.fastrag.module.knowledge.service.SmartSearchService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class SmartSearchServiceImpl implements SmartSearchService {
    private final KbSearchAssociationMapper assocMapper; private final KbAutoCorrectionMapper correctionMapper;
    @Override public PageResult<KbSearchAssociation> pageAssociations(String kbId,String dimension,String keyword,int page,int pageSize) {
        var pg=assocMapper.selectPage(new Page<>(page,pageSize),buildAssocWrapper(kbId,dimension,keyword));
        return PageResult.of(pg.getRecords(),pg.getTotal(),page,pageSize);
    }
    @Override public List<KbSearchAssociation> listAssociations(String kbId,String dimension) { return assocMapper.selectList(buildAssocWrapper(kbId,dimension,null)); }
    private LambdaQueryWrapper<KbSearchAssociation> buildAssocWrapper(String kbId,String dimension,String keyword) {
        var w=new LambdaQueryWrapper<KbSearchAssociation>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbSearchAssociation::getKbId,kbId);
        if(dimension!=null&&!dimension.isEmpty()) w.eq(KbSearchAssociation::getDimension,dimension);
        if(keyword!=null&&!keyword.isEmpty()) w.like(KbSearchAssociation::getName,keyword);
        return w.orderByDesc(KbSearchAssociation::getPriority);
    }
    @Override public KbSearchAssociation createAssociation(KbSearchAssociation a) { if(a.getEnabled()==null) a.setEnabled(1); if(a.getPriority()==null) a.setPriority(0); assocMapper.insert(a); return a; }
    @Override public KbSearchAssociation updateAssociation(String id,KbSearchAssociation a) { a.setId(id); assocMapper.updateById(a); return assocMapper.selectById(id); }
    @Override public void deleteAssociation(String id) { assocMapper.deleteById(id); }
    @Override public Map<String,Object> search(String kbId,String q,String dimension,int limit) {
        var w=buildAssocWrapper(kbId,dimension,null).eq(KbSearchAssociation::getEnabled,1);
        var rules=assocMapper.selectList(w);
        List<Map<String,Object>> associations=new ArrayList<>();
        for(var r:rules){
            if(r.getPattern()!=null&&java.util.regex.Pattern.compile(r.getPattern()).matcher(q).find()){
                associations.add(Map.of("dimension",r.getDimension(),"ruleName",r.getName(),"relevance",0.9));
            }
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("associations",associations);
        result.put("corrections",autoCorrectInternal(kbId,q));
        return result;
    }
    @Override public Map<String,Object> judge(String kbId,String dimension,String query,String targetText) {
        var w=buildAssocWrapper(kbId,dimension,null).eq(KbSearchAssociation::getEnabled,1);
        var rules=assocMapper.selectList(w);
        Map<String,Object> result=new LinkedHashMap<>();
        boolean matched=false; double confidence=0.0; KbSearchAssociation matchedRule=null;
        for(var r:rules){
            if(r.getPattern()!=null&&java.util.regex.Pattern.compile(r.getPattern()).matcher(query).find()){ matched=true; confidence=0.9; matchedRule=r; break; }
        }
        result.put("matched",matched); result.put("confidence",confidence);
        if(matchedRule!=null) result.put("matchedRule",Map.of("id",matchedRule.getId(),"name",matchedRule.getName(),"dimension",matchedRule.getDimension()));
        return result;
    }
    @Override public Map<String,Object> autoCorrect(String kbId,String q) { return autoCorrectInternal(kbId,q); }
    private Map<String,Object> autoCorrectInternal(String kbId,String q) {
        var corrections=correctionMapper.selectList(new LambdaQueryWrapper<KbAutoCorrection>().eq(KbAutoCorrection::getKbId,kbId).eq(KbAutoCorrection::getEnabled,1).orderByDesc(KbAutoCorrection::getPriority));
        String corrected=q; List<Map<String,Object>> correctionList=new ArrayList<>(); boolean changed=false;
        for(var c:corrections){
            if("exact".equals(c.getMatchType())&&corrected.contains(c.getWrongText())){
                corrected=corrected.replace(c.getWrongText(),c.getCorrectText()); changed=true;
                Map<String,Object> m=new LinkedHashMap<>(); m.put("original",c.getWrongText()); m.put("corrected",c.getCorrectText()); m.put("confidence",0.95); correctionList.add(m);
                c.setHitCount((c.getHitCount()!=null?c.getHitCount():0)+1); correctionMapper.updateById(c);
            }
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("original",q); result.put("corrected",corrected); result.put("corrections",correctionList); result.put("hasCorrection",changed);
        return result;
    }
    @Override public List<KbAutoCorrection> listCorrections(String kbId) { return correctionMapper.selectList(new LambdaQueryWrapper<KbAutoCorrection>().eq(KbAutoCorrection::getKbId,kbId).orderByDesc(KbAutoCorrection::getPriority)); }
    @Override public KbAutoCorrection createCorrection(KbAutoCorrection c) { if(c.getEnabled()==null) c.setEnabled(1); if(c.getHitCount()==null) c.setHitCount(0); if(c.getMatchType()==null) c.setMatchType("exact"); correctionMapper.insert(c); return c; }
    @Override public KbAutoCorrection updateCorrection(String id,KbAutoCorrection c) { c.setId(id); correctionMapper.updateById(c); return correctionMapper.selectById(id); }
    @Override public void deleteCorrection(String id) { correctionMapper.deleteById(id); }
    @Override public List<KbAutoCorrection> batchImportCorrections(String kbId,List<KbAutoCorrection> items) { for(var c:items){ c.setKbId(kbId); createCorrection(c); } return items; }
}
