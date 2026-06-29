package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbQaExtractTask; import com.fastrag.module.knowledge.mapper.KbQaExtractTaskMapper;
import com.fastrag.module.knowledge.service.QaExtractService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class QaExtractServiceImpl implements QaExtractService {
    private final KbQaExtractTaskMapper mapper;
    @Override public List<KbQaExtractTask> list(String kbId,String status) {
        var w=new LambdaQueryWrapper<KbQaExtractTask>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbQaExtractTask::getKbId,kbId);
        if(status!=null&&!status.isEmpty()) w.eq(KbQaExtractTask::getStatus,status);
        return mapper.selectList(w.orderByDesc(KbQaExtractTask::getCreatedAt));
    }
    @Override public KbQaExtractTask get(String id) { return mapper.selectById(id); }
    @Override public KbQaExtractTask start(KbQaExtractTask task) {
        if(task.getStatus()==null) task.setStatus("running");
        if(task.getTotalCount()==null) task.setTotalCount(0);
        if(task.getCompletedCount()==null) task.setCompletedCount(0);
        mapper.insert(task); return task;
    }
    @Override public KbQaExtractTask stop(String id) {
        var t=mapper.selectById(id);
        if(t!=null){ t.setStatus("stopped"); t.setCompletedAt(LocalDateTime.now()); mapper.updateById(t); }
        return t;
    }
    @Override public KbQaExtractTask update(String id,KbQaExtractTask task) {
        task.setId(id); mapper.updateById(task); return mapper.selectById(id);
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
}
