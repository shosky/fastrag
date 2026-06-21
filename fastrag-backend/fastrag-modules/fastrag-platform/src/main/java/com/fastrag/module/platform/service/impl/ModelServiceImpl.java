package com.fastrag.module.platform.service.impl;
import cn.hutool.core.util.StrUtil; import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.platform.entity.ModelRecord; import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.platform.service.ModelService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class ModelServiceImpl implements ModelService {
    private final ModelRecordMapper mapper;
    @Override public List<ModelRecord> list(String kw,String purpose) { var w=new LambdaQueryWrapper<ModelRecord>(); if(StrUtil.isNotBlank(kw))w.like(ModelRecord::getName,kw); if(StrUtil.isNotBlank(purpose))w.eq(ModelRecord::getPurpose,purpose); return mapper.selectList(w); }
    @Override public ModelRecord get(String id) { return mapper.selectById(id); }
    @Override public ModelRecord create(Map<String,Object> f) { var m=new ModelRecord(); m.setName((String)f.get("name")); m.setCode((String)f.get("code")); m.setPurpose((String)f.get("purpose")); m.setBrand((String)f.get("brand")); m.setApiUrl((String)f.get("apiUrl")); m.setStatus("offline"); mapper.insert(m); return m; }
    @Override public ModelRecord update(String id,Map<String,Object> f) { var m=mapper.selectById(id); if(m!=null){if(f.containsKey("name"))m.setName((String)f.get("name")); mapper.updateById(m);} return m; }
    @Override public void delete(String id) { mapper.deleteById(id); }
}
