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
    @Override public ModelRecord create(Map<String,Object> f) { var m=new ModelRecord(); m.setName((String)f.get("name")); m.setCode((String)f.get("code")); m.setPurpose((String)f.get("purpose")); m.setBrand((String)f.get("brand")); m.setApiUrl((String)f.get("apiUrl")); m.setApiKeyRef((String)f.get("apiKeyRef")); m.setStatus("offline"); mapper.insert(m); return m; }
    @Override public ModelRecord update(String id,Map<String,Object> f) {
        var m=mapper.selectById(id);
        if(m!=null){
            if(f.containsKey("name")) m.setName((String)f.get("name"));
            if(f.containsKey("code")) m.setCode((String)f.get("code"));
            if(f.containsKey("purpose")) m.setPurpose((String)f.get("purpose"));
            if(f.containsKey("brand")) m.setBrand((String)f.get("brand"));
            if(f.containsKey("apiUrl")) m.setApiUrl((String)f.get("apiUrl"));
            if(f.containsKey("apiKeyRef")) m.setApiKeyRef((String)f.get("apiKeyRef"));
            if(f.containsKey("status")) m.setStatus((String)f.get("status"));
            mapper.updateById(m);
        }
        return m;
    }
    @Override public void delete(String id) { mapper.deleteById(id); }
    @Override public void toggle(String id) { var m=mapper.selectById(id); if(m!=null){ m.setStatus("online".equals(m.getStatus())?"offline":"online"); mapper.updateById(m); } }
    @Override public List<ModelRecord> importModels(List<Map<String,Object>> models) {
        List<ModelRecord> result=new ArrayList<>();
        for(var f:models){ var m=new ModelRecord(); m.setName((String)f.get("name")); m.setCode((String)f.get("code")); m.setPurpose((String)f.getOrDefault("purpose","chat")); m.setBrand((String)f.get("brand")); m.setApiUrl((String)f.get("apiUrl")); m.setApiKeyRef((String)f.get("apiKeyRef")); m.setStatus((String)f.getOrDefault("status","offline")); mapper.insert(m); result.add(m); }
        return result;
    }
    // ===== 模型预置 =====
    @Override public List<Map<String,Object>> listPresets() {
        return List.of(
            Map.of("id","preset_llm","name","通义千问大模型","type","llm","models",List.of("qwen3-72b","qwen-max","qwen-plus")),
            Map.of("id","preset_embed","name","BGE嵌入模型","type","embedding","models",List.of("bge-m3","bge-large-zh")),
            Map.of("id","preset_rerank","name","BGE重排序模型","type","rerank","models",List.of("bge-reranker-v2-m3"))
        );
    }
    @Override public Map<String,Object> createPreset(Map<String,Object> preset) {
        Map<String,Object> r=new LinkedHashMap<>(preset); r.put("id","preset_"+UUID.randomUUID().toString().substring(0,8)); r.put("created",true); return r;
    }
    @Override public Map<String,Object> updatePreset(String id, Map<String,Object> preset) {
        Map<String,Object> r=new LinkedHashMap<>(preset); r.put("id",id); r.put("updated",true); return r;
    }
    @Override public void deletePreset(String id) { /* 预置删除逻辑由调用方处理 */ }
}
