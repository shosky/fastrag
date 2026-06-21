package com.fastrag.module.tools.service.impl;
import cn.hutool.core.util.StrUtil; import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.tools.entity.Skill; import com.fastrag.module.tools.mapper.SkillMapper;
import com.fastrag.module.tools.service.SkillService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {
    private final SkillMapper mapper;
    @Override public List<Skill> list(String kw,String cat) { var w=new LambdaQueryWrapper<Skill>(); if(StrUtil.isNotBlank(kw))w.like(Skill::getName,kw); return mapper.selectList(w); }
    @Override public Skill get(String id) { return mapper.selectById(id); }
    @Override public Skill create(Map<String,Object> f) { var s=new Skill(); s.setName((String)f.get("name")); s.setIdentifier((String)f.get("identifier")); s.setEnabled(1); mapper.insert(s); return s; }
    @Override public Skill update(String id,Map<String,Object> f) { var s=mapper.selectById(id); if(s!=null){if(f.containsKey("name"))s.setName((String)f.get("name")); mapper.updateById(s);} return s; }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public void toggleEnabled(String id) { var s=mapper.selectById(id); if(s!=null){s.setEnabled(s.getEnabled()==1?0:1);mapper.updateById(s);} }
}
