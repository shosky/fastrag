package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbEntity; import com.fastrag.module.knowledge.mapper.KbEntityMapper;
import com.fastrag.module.knowledge.service.EntityService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class EntityServiceImpl implements EntityService {
    private final KbEntityMapper mapper;
    @Override public List<KbEntity> list(String kbId,String keyword) {
        var w=new LambdaQueryWrapper<KbEntity>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbEntity::getKbId,kbId);
        if(keyword!=null&&!keyword.isEmpty()) w.like(KbEntity::getName,keyword);
        return mapper.selectList(w.orderByDesc(KbEntity::getCreatedAt));
    }
    @Override public KbEntity get(String id) { return mapper.selectById(id); }
    @Override public KbEntity create(KbEntity entity) {
        if(entity.getEntityType()==null) entity.setEntityType("enum");
        mapper.insert(entity); return entity;
    }
    @Override public KbEntity update(String id,KbEntity entity) {
        entity.setId(id); mapper.updateById(entity); return mapper.selectById(id);
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
}
