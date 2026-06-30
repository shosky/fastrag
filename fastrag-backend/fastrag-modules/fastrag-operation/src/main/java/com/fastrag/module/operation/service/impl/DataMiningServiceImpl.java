package com.fastrag.module.operation.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.operation.entity.DataMiningTask; import com.fastrag.module.operation.mapper.DataMiningTaskMapper;
import com.fastrag.module.operation.service.DataMiningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class DataMiningServiceImpl implements DataMiningService {
    private final DataMiningTaskMapper mapper;
    private final ObjectMapper objectMapper;
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
        // 模拟挖掘结果摘要
        var summary=new LinkedHashMap<String,Object>();
        summary.put("matchedCount",new Random().nextInt(100)+10);
        summary.put("topKeywords",List.of("退款","物流","质量"));
        summary.put("runStatus","success");
        try {
            t.setResultSummary(objectMapper.writeValueAsString(summary));
        } catch (Exception e) {
            t.setResultSummary("{\"error\":\"serialization failed\"}");
        }
        mapper.updateById(t);
        return t;
    }
}
