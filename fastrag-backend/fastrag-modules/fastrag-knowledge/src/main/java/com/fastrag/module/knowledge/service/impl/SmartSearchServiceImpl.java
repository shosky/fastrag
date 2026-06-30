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
            if(matchesDimension(r,q)){
                var item=new LinkedHashMap<String,Object>();
                item.put("dimension",r.getDimension());
                item.put("ruleName",r.getName());
                item.put("relevance",0.9);
                // 解析 JSON suggestions 数组返回给前端展示
                if(r.getSuggestions()!=null&&!r.getSuggestions().isEmpty()){
                    try{ item.put("suggestions",new com.fasterxml.jackson.databind.ObjectMapper().readValue(r.getSuggestions(),List.class)); }
                    catch(Exception e){ item.put("suggestions",List.of(r.getSuggestions())); }
                } else { item.put("suggestions",List.of()); }
                associations.add(item);
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
            if(matchesDimension(r,query)){ matched=true; confidence=0.9; matchedRule=r; break; }
        }
        result.put("matched",matched); result.put("confidence",confidence);
        if(matchedRule!=null) result.put("matchedRule",Map.of("id",matchedRule.getId(),"name",matchedRule.getName(),"dimension",matchedRule.getDimension()));
        return result;
    }
    // 按维度分派匹配策略：content/rule 用正则，attachment 用包含匹配，publishTime 用模糊日期匹配，knowledgeType 用精确/包含匹配
    private boolean matchesDimension(KbSearchAssociation r,String query){
        if(r.getPattern()==null) return false;
        String dim=r.getDimension();
        String pat=r.getPattern();
        String cond=r.getConditions(); // JSON 格式的附加条件
        return switch(dim!=null?dim:"content"){
            case "content","rule" -> { try{ yield java.util.regex.Pattern.compile(pat).matcher(query).find(); }catch(Exception e){ yield false; } }
            case "attachment" -> query.contains(pat) || pat.toLowerCase().contains(query.toLowerCase());
            case "publishTime" -> { // pattern 存储日期范围表达式，如 "2026-01~2026-06" 或 "2026"
                yield fuzzyDateMatch(query,pat);
            }
            case "knowledgeType" -> query.equalsIgnoreCase(pat) || query.toLowerCase().contains(pat.toLowerCase()) || pat.toLowerCase().contains(query.toLowerCase());
            default -> { try{ yield java.util.regex.Pattern.compile(pat).matcher(query).find(); }catch(Exception e){ yield false; } }
        };
    }
    // 模糊日期匹配：查询文本中含年份/月份/日期，与规则 pattern 中的日期范围比较
    private boolean fuzzyDateMatch(String query,String pattern){
        // 从 pattern 解析日期范围（支持 "YYYY" / "YYYY-MM" / "YYYY-MM~YYYY-MM" 格式）
        try{
            String[] parts=pattern.split("~");
            String start=parts[0].trim();
            String end=parts.length>1?parts[1].trim():start;
            // 从查询文本提取年月
            String qNorm=query.replaceAll("[年月]", "-").replaceAll("[日号]", "").replaceAll("\\s","");
            return !qNorm.isEmpty() && qNorm.compareTo(start)>=0 && qNorm.compareTo(end)<=0;
        }catch(Exception e){ return false; }
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
