package com.fastrag.module.retrieval.service.impl;
import com.fastrag.infra.neo4j.Neo4jService; import com.fastrag.module.retrieval.service.QueryEnhanceService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class QueryEnhanceServiceImpl implements QueryEnhanceService {
    private final Neo4jService neo4jService;
    @Override public Map<String,Object> suggest(String query) { var r=new HashMap<String,Object>(); r.put("suggestedQuery",query); r.put("reason",""); return r; }
    @Override public Map<String,Object> expandSynonyms(String query) { var r=new HashMap<String,Object>(); r.put("expandedQuery",query); r.put("matchedTerms",List.of()); r.put("addedTerms",List.of()); return r; }
    @Override public Map<String,Object> applyQueryRules(String query) { var r=new HashMap<String,Object>(); r.put("rewritten",query); r.put("appliedRules",List.of()); return r; }
    @Override public Map<String,Object> expandGraph(String kbId,String query,int depth,int maxEntities) {
        List<String> entities=Arrays.stream(query.split("\\s+")).limit(3).toList();
        return neo4jService.expandGraph(kbId,entities,depth,maxEntities);
    }
}
