package com.fastrag.module.operation.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.operation.entity.DataMiningTask; import com.fastrag.module.operation.mapper.DataMiningTaskMapper;
import com.fastrag.module.operation.service.DataMiningService;
import com.fastrag.module.retrieval.entity.KbRetrievalLog;
import com.fastrag.module.retrieval.mapper.KbRetrievalLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
import java.util.stream.Collectors;

/** 数据挖掘：基于真实检索日志的关键词统计与命中率分析（智能判断=自动分词聚合，一键判断=手动触发同链路） */
@Service @RequiredArgsConstructor
public class DataMiningServiceImpl implements DataMiningService {
    private final DataMiningTaskMapper mapper;
    private final KbRetrievalLogMapper retrievalLogMapper;
    private final ObjectMapper objectMapper;

    private static final Set<String> STOPWORDS = Set.of("的","了","是","在","我","有","和","就","不","人","都","一个","什么","怎么","如何","吗","呢","the","a","an","of","to","is","and");

    @Override public List<DataMiningTask> list(String kbId,String keyword) {
        var w=new LambdaQueryWrapper<DataMiningTask>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(DataMiningTask::getKbId,kbId);
        if(keyword!=null&&!keyword.isEmpty()) w.like(DataMiningTask::getName,keyword);
        return mapper.selectList(w.orderByDesc(DataMiningTask::getCreatedAt));
    }
    @Override public DataMiningTask get(String id) { return mapper.selectById(id); }
    @Override public DataMiningTask create(DataMiningTask task) {
        if(task.getStatus()==null) task.setStatus("enabled");
        if(task.getRuleType()==null) task.setRuleType("keyword");
        mapper.insert(task); return task;
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public DataMiningTask run(String id) {
        var t=mapper.selectById(id);
        if(t==null) return null;
        t.setLastRunAt(LocalDateTime.now());
        // 真实挖掘：取该知识库（或全库）近 30 天检索日志，分词统计 top 关键词与命中率
        var w=new LambdaQueryWrapper<KbRetrievalLog>().ge(KbRetrievalLog::getCreatedAt,LocalDateTime.now().minusDays(30));
        if(t.getKbId()!=null&&!t.getKbId().isEmpty()) w.eq(KbRetrievalLog::getKbId,t.getKbId());
        List<KbRetrievalLog> logs=retrievalLogMapper.selectList(w);
        long matchedCount=logs.stream().filter(l->Boolean.TRUE.equals(l.getHasResult())).count();
        Map<String,Long> freq=new HashMap<>();
        for(var log:logs) tokenize(log.getQuery()).forEach(tok->freq.merge(tok,1L,Long::sum));
        List<String> topKeywords=freq.entrySet().stream()
                .sorted(Map.Entry.<String,Long>comparingByValue().reversed())
                .limit(10).map(Map.Entry::getKey).collect(Collectors.toList());
        var summary=new LinkedHashMap<String,Object>();
        summary.put("totalQueries",logs.size());
        summary.put("matchedCount",matchedCount);
        summary.put("hitRate",logs.isEmpty()?0.0:Math.round((double)matchedCount/logs.size()*100)/100.0);
        summary.put("topKeywords",topKeywords);
        summary.put("runStatus","success");
        try {
            t.setResultSummary(objectMapper.writeValueAsString(summary));
        } catch (Exception e) {
            t.setResultSummary("{\"error\":\"serialization failed\"}");
        }
        mapper.updateById(t);
        return t;
    }

    /** 分词：拉丁词 + CJK 二元/一元组，过滤停用词与单字高频虚词 */
    private List<String> tokenize(String query) {
        if(query==null||query.isBlank()) return List.of();
        LinkedHashSet<String> out=new LinkedHashSet<>();
        for(String seg:query.trim().split("[\\s，。；！？,.;!?、]+")){
            if(seg.matches("[a-zA-Z0-9]+")){ if(seg.length()>=2&&!STOPWORDS.contains(seg.toLowerCase())) out.add(seg.toLowerCase()); }
            else {
                if(seg.length()>=2&&seg.length()<=4&&!STOPWORDS.contains(seg)) out.add(seg);
                for(int i=0;i+2<=seg.length();i++){
                    String bi=seg.substring(i,i+2);
                    if(!STOPWORDS.contains(bi)) out.add(bi);
                }
            }
        }
        return new ArrayList<>(out);
    }
}
