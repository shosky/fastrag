package com.fastrag.module.platform.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.platform.entity.*; import com.fastrag.module.platform.mapper.*;
import com.fastrag.module.platform.service.ConfigManageService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class ConfigManageServiceImpl implements ConfigManageService {
    private final SysConfigMapper configMapper; private final SysConfigHistoryMapper historyMapper;
    private final ModelTrainingMapper trainingMapper; private final ModelTestReportMapper testMapper;
    private final ModelRecordMapper modelMapper;
    private final SysSecurityPolicyMapper securityPolicyMapper;
    private final SysPublishStrategyMapper publishStrategyMapper;
    // ===== 模型训练/测试 =====
    @Override public ModelTraining train(String modelId,Map<String,Object> params) {
        var model=modelMapper.selectById(modelId);
        var t=new ModelTraining(); t.setModelId(modelId); t.setModelName(model!=null?model.getName():modelId);
        t.setDataset((String)params.get("dataset")); t.setStatus("running");
        t.setEpochs(params.get("epochs") instanceof Number?((Number)params.get("epochs")).intValue():10);
        t.setMetrics("{\"loss\":0.32,\"accuracy\":0.91}");
        trainingMapper.insert(t); return t;
    }
    @Override public ModelTestReport test(String modelId,Map<String,Object> params) {
        var model=modelMapper.selectById(modelId);
        var r=new ModelTestReport(); r.setModelId(modelId); r.setModelName(model!=null?model.getName():modelId);
        r.setTestDataset((String)params.get("testDataset")); r.setStatus("completed");
        r.setTestCount(params.get("testCount") instanceof Number?((Number)params.get("testCount")).intValue():100);
        r.setScore("0.92"); r.setMetrics("{\"accuracy\":0.92,\"f1\":0.89,\"latency\":280}");
        testMapper.insert(r); return r;
    }
    @Override public List<ModelTraining> listTrainings(String modelId) { return trainingMapper.selectList(new LambdaQueryWrapper<ModelTraining>().eq(modelId!=null&&!modelId.isEmpty(),ModelTraining::getModelId,modelId).orderByDesc(ModelTraining::getCreatedAt)); }
    @Override public List<ModelTestReport> listTestReports(String modelId) { return testMapper.selectList(new LambdaQueryWrapper<ModelTestReport>().eq(modelId!=null&&!modelId.isEmpty(),ModelTestReport::getModelId,modelId).orderByDesc(ModelTestReport::getCreatedAt)); }
    @Override public Map<String,Object> exportModels(String ids,String purpose) {
        var w=new LambdaQueryWrapper<ModelRecord>();
        if(purpose!=null&&!purpose.isEmpty()) w.eq(ModelRecord::getPurpose,purpose);
        var models=modelMapper.selectList(w);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("format","json"); result.put("count",models.size()); result.put("items",models); return result;
    }
    // ===== 配置管理 =====
    @Override public List<SysConfig> listConfigs(String configType) {
        var w=new LambdaQueryWrapper<SysConfig>();
        if(configType!=null&&!configType.isEmpty()) w.eq(SysConfig::getConfigType,configType);
        return configMapper.selectList(w.orderByAsc(SysConfig::getConfigKey));
    }
    @Override public SysConfig getConfig(String configKey) { return configMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey,configKey)); }
    @Override public SysConfig saveConfig(String configKey,String configValue,String configType,String description,String operator) {
        var existing=getConfig(configKey);
        if(existing==null){
            var c=new SysConfig(); c.setConfigKey(configKey); c.setConfigValue(configValue); c.setConfigType(configType); c.setDescription(description); c.setUpdatedBy(operator);
            configMapper.insert(c);
            recordHistory(c.getId(),configKey,null,configValue,"create",operator);
            return c;
        } else {
            var old=existing.getConfigValue(); existing.setConfigValue(configValue);
            if(configType!=null) existing.setConfigType(configType);
            if(description!=null) existing.setDescription(description);
            existing.setUpdatedBy(operator);
            configMapper.updateById(existing);
            recordHistory(existing.getId(),configKey,old,configValue,"update",operator);
            return existing;
        }
    }
    private void recordHistory(String configId,String key,String oldVal,String newVal,String type,String operator) {
        var h=new SysConfigHistory(); h.setConfigId(configId); h.setConfigKey(key); h.setOldValue(oldVal); h.setNewValue(newVal); h.setChangeType(type); h.setOperator(operator);
        historyMapper.insert(h);
    }
    @Override public List<SysConfigHistory> listHistory(String configKey,String configType) {
        var w=new LambdaQueryWrapper<SysConfigHistory>();
        if(configKey!=null&&!configKey.isEmpty()) w.eq(SysConfigHistory::getConfigKey,configKey);
        return historyMapper.selectList(w.orderByDesc(SysConfigHistory::getTimestamp).last("LIMIT 100"));
    }
    @Override public Map<String,Object> exportConfig(String configType) {
        var all=listConfigs(configType);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("format","json"); result.put("count",all.size()); result.put("items",all); return result;
    }
    @Override public List<SysConfig> importConfig(List<Map<String,Object>> items,String operator) {
        List<SysConfig> result=new ArrayList<>();
        for(var item:items){ var c=saveConfig((String)item.get("configKey"),String.valueOf(item.get("configValue")),(String)item.get("configType"),(String)item.get("description"),operator); result.add(c); }
        return result;
    }
    @Override public List<SysConfig> getDefaultConfigs() { return configMapper.selectList(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getIsDefault,1)); }
    @Override public List<SysConfig> setDefault(List<String> configKeys) {
        for(var key:configKeys){ var c=getConfig(key); if(c!=null){ c.setIsDefault(1); configMapper.updateById(c); } }
        return getDefaultConfigs();
    }
    @Override public List<SysConfig> resetToDefault(List<String> configKeys,String operator) {
        List<SysConfig> result=new ArrayList<>();
        for(var key:configKeys){
            var c=getConfig(key);
            if(c!=null&&c.getIsDefault()!=null&&c.getIsDefault()==1){
                // 当前值就是默认值，无需重置
                result.add(c);
            } else if(c!=null){
                // 先查询该 key 的默认配置记录（is_default=1），若不存在则无法重置
                var defaults=configMapper.selectList(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey,key).eq(SysConfig::getIsDefault,1));
                if(!defaults.isEmpty()){
                    String defaultValue=defaults.get(0).getConfigValue();
                    String oldValue=c.getConfigValue();
                    c.setConfigValue(defaultValue);
                    c.setIsDefault(1);
                    c.setUpdatedBy(operator);
                    configMapper.updateById(c);
                    recordHistory(c.getId(),key,oldValue,defaultValue,"reset",operator);
                    result.add(c);
                }
            }
        }
        return result;
    }
    // ===== 安全策略 CRUD =====
    @Override public List<SysSecurityPolicy> listSecurityPolicies(String policyType) {
        var w=new LambdaQueryWrapper<SysSecurityPolicy>();
        if(policyType!=null&&!policyType.isEmpty()) w.eq(SysSecurityPolicy::getPolicyType,policyType);
        return securityPolicyMapper.selectList(w.orderByAsc(SysSecurityPolicy::getPriority));
    }
    @Override public SysSecurityPolicy createSecurityPolicy(SysSecurityPolicy p) { if(p.getEnabled()==null) p.setEnabled(1); if(p.getPriority()==null) p.setPriority(0); securityPolicyMapper.insert(p); return p; }
    @Override public SysSecurityPolicy updateSecurityPolicy(String id,SysSecurityPolicy p) { p.setId(id); securityPolicyMapper.updateById(p); return securityPolicyMapper.selectById(id); }
    @Override public void deleteSecurityPolicy(String id) { securityPolicyMapper.deleteById(id); }
    // ===== 发布策略 CRUD =====
    @Override public List<SysPublishStrategy> listPublishStrategies(String strategyType) {
        var w=new LambdaQueryWrapper<SysPublishStrategy>();
        if(strategyType!=null&&!strategyType.isEmpty()) w.eq(SysPublishStrategy::getStrategyType,strategyType);
        return publishStrategyMapper.selectList(w.orderByAsc(SysPublishStrategy::getPriority));
    }
    @Override public SysPublishStrategy createPublishStrategy(SysPublishStrategy s) { if(s.getEnabled()==null) s.setEnabled(1); if(s.getPriority()==null) s.setPriority(0); publishStrategyMapper.insert(s); return s; }
    @Override public SysPublishStrategy updatePublishStrategy(String id,SysPublishStrategy s) { s.setId(id); publishStrategyMapper.updateById(s); return publishStrategyMapper.selectById(id); }
    @Override public void deletePublishStrategy(String id) { publishStrategyMapper.deleteById(id); }
}
