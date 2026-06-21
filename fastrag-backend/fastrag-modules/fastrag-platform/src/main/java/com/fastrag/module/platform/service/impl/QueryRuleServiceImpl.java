package com.fastrag.module.platform.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.platform.entity.QueryRule; import com.fastrag.module.platform.mapper.QueryRuleMapper;
import com.fastrag.module.platform.service.QueryRuleService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class QueryRuleServiceImpl implements QueryRuleService {
    private final QueryRuleMapper mapper;
    @Override public List<QueryRule> list(String type) { var w=new LambdaQueryWrapper<QueryRule>(); if(type!=null)w.eq(QueryRule::getRuleType,type); return mapper.selectList(w); }
    @Override public QueryRule create(Map<String,Object> f) { var r=new QueryRule(); r.setName((String)f.get("name")); r.setRuleType((String)f.get("type")); r.setPattern((String)f.get("pattern")); r.setEnabled(1); mapper.insert(r); return r; }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public void toggle(String id) { var r=mapper.selectById(id); if(r!=null){r.setEnabled(r.getEnabled()==1?0:1);mapper.updateById(r);} }
}
