package com.fastrag.module.retrieval.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.retrieval.entity.KbRetrievalLog; import com.fastrag.module.retrieval.mapper.KbRetrievalLogMapper;
import com.fastrag.module.retrieval.service.RetrievalLogService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class RetrievalLogServiceImpl implements RetrievalLogService {
    private final KbRetrievalLogMapper mapper;
    @Override public void log(KbRetrievalLog log) { mapper.insert(log); }
    @Override public PageResult<KbRetrievalLog> page(String kbId,Boolean hasResult,int page,int pageSize) {
        var w=new LambdaQueryWrapper<KbRetrievalLog>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbRetrievalLog::getKbId,kbId);
        if(hasResult!=null) w.eq(KbRetrievalLog::getHasResult,hasResult);
        w.orderByDesc(KbRetrievalLog::getCreatedAt);
        var pg=mapper.selectPage(new Page<>(page,pageSize),w);
        return PageResult.of(pg.getRecords(),pg.getTotal(),page,pageSize);
    }
    @Override public Map<String,Object> analysis(String kbId) {
        var w=new LambdaQueryWrapper<KbRetrievalLog>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbRetrievalLog::getKbId,kbId);
        List<KbRetrievalLog> all=mapper.selectList(w);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("totalQueries",all.size());
        long noResult=all.stream().filter(l->Boolean.FALSE.equals(l.getHasResult())).count();
        result.put("noResultCount",noResult);
        result.put("noResultRate",all.isEmpty()?0.0:Math.round(noResult*10000.0/all.size())/100.0);
        double avgLatency=all.stream().filter(l->l.getLatencyMs()!=null).mapToInt(KbRetrievalLog::getLatencyMs).average().orElse(0);
        result.put("avgLatencyMs",Math.round(avgLatency*100.0)/100.0);
        double avgHit=all.stream().filter(l->l.getHitCount()!=null).mapToInt(KbRetrievalLog::getHitCount).average().orElse(0);
        result.put("avgHitCount",Math.round(avgHit*100.0)/100.0);
        // 热门查询 Top10
        Map<String,Long> freq=new HashMap<>();
        for(var l:all){ if(l.getQuery()!=null) freq.merge(l.getQuery(),1L,Long::sum); }
        List<Map<String,Object>> top=new ArrayList<>();
        freq.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(10).forEach(e->{
            Map<String,Object> m=new LinkedHashMap<>(); m.put("query",e.getKey()); m.put("count",e.getValue()); top.add(m);
        });
        result.put("topQueries",top);
        // 无结果查询 Top10
        List<Map<String,Object>> noResultQueries=new ArrayList<>();
        Map<String,Long> noFreq=new HashMap<>();
        for(var l:all){ if(Boolean.FALSE.equals(l.getHasResult())&&l.getQuery()!=null) noFreq.merge(l.getQuery(),1L,Long::sum); }
        noFreq.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(10).forEach(e->{
            Map<String,Object> m=new LinkedHashMap<>(); m.put("query",e.getKey()); m.put("count",e.getValue()); noResultQueries.add(m);
        });
        result.put("noResultQueries",noResultQueries);
        return result;
    }
}
