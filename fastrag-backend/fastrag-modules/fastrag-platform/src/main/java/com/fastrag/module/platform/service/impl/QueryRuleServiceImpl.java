package com.fastrag.module.platform.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.platform.entity.QueryRule; import com.fastrag.module.platform.mapper.QueryRuleMapper;
import com.fastrag.module.platform.service.QueryRuleService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class QueryRuleServiceImpl implements QueryRuleService {
    private final QueryRuleMapper mapper;
    @Override public List<QueryRule> list(String type) {
        var w=new LambdaQueryWrapper<QueryRule>();
        if(type!=null&&!type.isEmpty()) w.eq(QueryRule::getRuleType,type);
        return mapper.selectList(w.orderByAsc(QueryRule::getPriority).orderByDesc(QueryRule::getCreatedAt));
    }
    @Override public QueryRule create(Map<String,Object> f) { var r=apply(new QueryRule(),f); mapper.insert(r); return r; }
    @Override public QueryRule update(String id,Map<String,Object> f) {
        var r=mapper.selectById(id);
        if(r==null) throw new RuntimeException("查询规则不存在");
        apply(r,f); mapper.updateById(r); return r;
    }
    // 前端表单字段兼容映射：type/replacement/status ↔ rule_type/action/enabled
    private QueryRule apply(QueryRule r,Map<String,Object> f) {
        if(f.containsKey("name")) r.setName((String)f.get("name"));
        if(f.containsKey("description")) r.setDescription((String)f.get("description"));
        if(f.containsKey("type")) r.setRuleType((String)f.get("type"));
        else if(f.containsKey("ruleType")) r.setRuleType((String)f.get("ruleType"));
        if(f.containsKey("pattern")) r.setPattern((String)f.get("pattern"));
        if(f.containsKey("replacement")) r.setAction((String)f.get("replacement"));
        else if(f.containsKey("action")) r.setAction((String)f.get("action"));
        if(f.get("priority") instanceof Number n) r.setPriority(n.intValue());
        Object st=f.get("status");
        if(st!=null) r.setEnabled("enabled".equals(st)||"1".equals(st.toString())||Boolean.TRUE.equals(st)?1:0);
        else if(f.get("enabled") instanceof Number n2) r.setEnabled(n2.intValue());
        return r;
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public void toggle(String id) { var r=mapper.selectById(id); if(r!=null){r.setEnabled(r.getEnabled()!=null&&r.getEnabled()==1?0:1);mapper.updateById(r);} }
}
