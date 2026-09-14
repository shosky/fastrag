package com.fastrag.module.retrieval.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.infra.neo4j.Neo4jService;
import com.fastrag.module.retrieval.entity.QueryRuleRow; import com.fastrag.module.retrieval.entity.TermRecordRow;
import com.fastrag.module.retrieval.mapper.QueryRuleRowMapper; import com.fastrag.module.retrieval.mapper.TermRecordRowMapper;
import com.fastrag.module.retrieval.service.QueryEnhanceService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class QueryEnhanceServiceImpl implements QueryEnhanceService {
    private final Neo4jService neo4jService;
    private final QueryRuleRowMapper ruleMapper;
    private final TermRecordRowMapper termMapper;
    @Override public Map<String,Object> suggest(String query) { var r=new HashMap<String,Object>(); r.put("suggestedQuery",query); r.put("reason",""); return r; }
    // 同义词扩写：命中扩写规则 + 术语库别名，追加 query 中没有的词
    @Override public Map<String,Object> expandSynonyms(String query) {
        List<String> added=new ArrayList<>(); List<String> matched=new ArrayList<>();
        Set<String> addedSet=new HashSet<>(); Set<String> matchedSet=new HashSet<>();
        for(var rule:enabledRules("expand")) {
            if(query.contains(rule.getPattern())) {
                matchedAdd(matched,matchedSet,rule.getName());
                for(var w:words(rule.getAction())) if(!query.contains(w)&&addedSet.add(w)) added.add(w);
            }
        }
        for(var t:termMapper.selectList(null)) {
            List<String> aliases=aliasList(t);
            boolean hit=query.contains(t.getTerm()==null?"":t.getTerm())||aliases.stream().anyMatch(query::contains);
            if(!hit) continue;
            matchedAdd(matched,matchedSet,t.getTerm());
            for(var a:aliases) if(!query.contains(a)&&addedSet.add(a)) added.add(a);
        }
        var r=new LinkedHashMap<String,Object>();
        String expanded=added.isEmpty()?query:query+" "+String.join(" ",added);
        r.put("expandedQuery",expanded); r.put("matchedTerms",matched); r.put("addedTerms",added);
        return r;
    }
    // 查询重写/扩写：按优先级依次应用 rewrite 替换、expand 追词（复刻前端 mock 语义）
    @Override public Map<String,Object> applyQueryRules(String query) {
        List<String> applied=new ArrayList<>();
        String result=query;
        for(var rule:enabledRules("rewrite")) {
            if(result.contains(rule.getPattern())) {
                result=result.replace(rule.getPattern(),rule.getAction()==null?"":rule.getAction());
                applied.add("重写："+rule.getName());
            }
        }
        Set<String> added=new LinkedHashSet<>();
        for(var rule:enabledRules("expand")) {
            if(result.contains(rule.getPattern())) {
                for(var w:words(rule.getAction())) if(!result.contains(w)) added.add(w);
                applied.add("扩写："+rule.getName());
            }
        }
        if(!added.isEmpty()) result=result+" "+String.join(" ",added);
        var r=new LinkedHashMap<String,Object>();
        r.put("rewritten",result); r.put("appliedRules",applied); r.put("original",query);
        return r;
    }
    @Override public Map<String,Object> expandGraph(String kbId,String query,int depth,int maxEntities) {
        List<String> entities=Arrays.stream(query.split("\\s+")).limit(3).toList();
        return neo4jService.expandGraph(kbId,entities,depth,maxEntities);
    }
    private List<QueryRuleRow> enabledRules(String type) {
        return ruleMapper.selectList(new LambdaQueryWrapper<QueryRuleRow>()
            .eq(QueryRuleRow::getEnabled,1).eq(QueryRuleRow::getRuleType,type)
            .orderByAsc(QueryRuleRow::getPriority));
    }
    // 术语别名：alias 字段按 逗号/顿号/分号/空白 拆分
    private List<String> aliasList(TermRecordRow t) {
        if(t.getAlias()==null||t.getAlias().isBlank()) return List.of();
        return Arrays.stream(t.getAlias().split("[,，、;；\\s]+")).map(String::trim).filter(s->!s.isEmpty()).toList();
    }
    private List<String> words(String action) {
        if(action==null||action.isBlank()) return List.of();
        return Arrays.stream(action.split("[\\s]+")).map(String::trim).filter(s->!s.isEmpty()).toList();
    }
    private void matchedAdd(List<String> list,Set<String> set,String v) { if(v!=null&&!v.isEmpty()&&set.add(v)) list.add(v); }
}
