package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.knowledge.entity.KbKnowledgeUpdate; import com.fastrag.module.knowledge.mapper.KbKnowledgeUpdateMapper;
import com.fastrag.module.knowledge.service.KnowledgeUpdateService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class KnowledgeUpdateServiceImpl implements KnowledgeUpdateService {
    private final KbKnowledgeUpdateMapper mapper;
    @Override public PageResult<KbKnowledgeUpdate> page(String kbId,String knowledgeId,String updateType,String status,int page,int pageSize) {
        var w=buildWrapper(kbId,knowledgeId,updateType,status);
        var pg=mapper.selectPage(new Page<>(page,pageSize),w);
        return PageResult.of(pg.getRecords(),pg.getTotal(),page,pageSize);
    }
    @Override public List<KbKnowledgeUpdate> list(String kbId,String knowledgeId) { return mapper.selectList(buildWrapper(kbId,knowledgeId,null,null)); }
    private LambdaQueryWrapper<KbKnowledgeUpdate> buildWrapper(String kbId,String knowledgeId,String updateType,String status) {
        var w=new LambdaQueryWrapper<KbKnowledgeUpdate>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbKnowledgeUpdate::getKbId,kbId);
        if(knowledgeId!=null&&!knowledgeId.isEmpty()) w.eq(KbKnowledgeUpdate::getKnowledgeId,knowledgeId);
        if(updateType!=null&&!updateType.isEmpty()) w.eq(KbKnowledgeUpdate::getUpdateType,updateType);
        if(status!=null&&!status.isEmpty()) w.eq(KbKnowledgeUpdate::getStatus,status);
        return w.orderByDesc(KbKnowledgeUpdate::getCreatedAt);
    }
    @Override public KbKnowledgeUpdate get(String id) { return mapper.selectById(id); }
    @Override public KbKnowledgeUpdate create(KbKnowledgeUpdate update) {
        if(update.getStatus()==null) update.setStatus("pending");
        mapper.insert(update); return update;
    }
    @Override public KbKnowledgeUpdate update(String id,KbKnowledgeUpdate update) {
        var existing=mapper.selectById(id);
        if(existing!=null&&!"pending".equals(existing.getStatus())) throw new RuntimeException("仅pending状态可编辑");
        update.setId(id); mapper.updateById(update); return mapper.selectById(id);
    }
    @Override public void delete(String id) {
        var existing=mapper.selectById(id);
        if(existing!=null&&!"pending".equals(existing.getStatus())) throw new RuntimeException("仅pending状态可删除");
        mapper.deleteById(id);
    }
    @Override public KbKnowledgeUpdate apply(String id) {
        var u=mapper.selectById(id);
        if(u!=null){ u.setStatus("applied"); u.setAppliedAt(LocalDateTime.now()); mapper.updateById(u); }
        return u;
    }
    @Override public KbKnowledgeUpdate rollback(String id) {
        var u=mapper.selectById(id);
        if(u!=null){ u.setStatus("rolled_back"); mapper.updateById(u); }
        return u;
    }
}
