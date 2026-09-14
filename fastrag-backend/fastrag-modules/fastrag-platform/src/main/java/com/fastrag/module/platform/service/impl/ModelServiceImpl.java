package com.fastrag.module.platform.service.impl;
import cn.hutool.core.util.StrUtil; import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.module.platform.entity.ModelCallLog; import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelCallLogMapper; import com.fastrag.module.platform.mapper.ModelRecordMapper;
import com.fastrag.module.platform.service.ModelService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class ModelServiceImpl implements ModelService {
    private final ModelRecordMapper mapper;
    private final ModelCallLogMapper callLogMapper;
    private final LlmService llmService;
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
    // ===== 模型调用（真实 LLM 推理，写调用日志） =====
    @Override public Map<String,Object> invoke(String id,Map<String,Object> params) {
        var m=mapper.selectById(id);
        if(m==null) throw new RuntimeException("模型不存在");
        String prompt=params.get("prompt")==null?"你好，请做自我介绍":params.get("prompt").toString();
        double temperature=params.get("temperature") instanceof Number n?n.doubleValue():0.7;
        long start=System.currentTimeMillis();
        String resp=llmService.chat(m.getCode(),prompt,m.getApiUrl(),m.getApiKeyRef());
        long dur=System.currentTimeMillis()-start;
        boolean ok=resp!=null&&!resp.startsWith("模型调用失败");
        insertCallLog(id,"manual",ok,dur,resp);
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("response",resp); r.put("durationMs",dur); r.put("success",ok);
        return r;
    }
    @Override public List<ModelCallLog> listCallLogs(String id) {
        return callLogMapper.selectList(new LambdaQueryWrapper<ModelCallLog>()
            .eq(ModelCallLog::getModelId,id).orderByDesc(ModelCallLog::getTimestamp).last("LIMIT 200"));
    }
    // 统一调用日志写入（供本类与模型测试使用）
    public void insertCallLog(String modelId,String caller,boolean success,long durationMs,String response) {
        try {
            var log=new ModelCallLog();
            log.setModelId(modelId); log.setCaller(caller);
            log.setStatus(success?"success":"failed");
            log.setDuration((int)Math.min(durationMs,Integer.MAX_VALUE));
            log.setTokens(response==null?0:response.length());
            log.setTimestamp(LocalDateTime.now());
            callLogMapper.insert(log);
        } catch (Exception ignore) { }
    }
}
