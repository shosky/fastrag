package com.fastrag.module.tools.service.impl;
import cn.hutool.core.util.StrUtil; import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.Tool; import com.fastrag.module.tools.mapper.ToolMapper;
import com.fastrag.module.tools.service.ToolService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class ToolServiceImpl implements ToolService {
    private final ToolMapper mapper;
    @Override public List<Tool> list(String kw,String type) { var w=new LambdaQueryWrapper<Tool>(); if(StrUtil.isNotBlank(kw))w.like(Tool::getName,kw); if(StrUtil.isNotBlank(type))w.eq(Tool::getType,type); return mapper.selectList(w); }
    @Override public Tool get(String id) { return mapper.selectById(id); }
    @Override public Tool create(Map<String,Object> f) { var t=new Tool(); t.setName((String)f.get("name")); t.setIdentifier((String)f.get("identifier")); t.setDescription((String)f.get("description")); t.setType((String)f.getOrDefault("type","http")); t.setEnabled(1); mapper.insert(t); return t; }
    @Override public Tool update(String id,Map<String,Object> f) { var t=mapper.selectById(id); if(t!=null){if(f.containsKey("name"))t.setName((String)f.get("name")); mapper.updateById(t);} return t; }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public void toggleEnabled(String id) { var t=mapper.selectById(id); if(t!=null){t.setEnabled(t.getEnabled()==1?0:1);mapper.updateById(t);} }
}
