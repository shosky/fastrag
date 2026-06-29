package com.fastrag.module.platform.service;
import com.fastrag.module.platform.entity.*; import java.util.*;
public interface ConfigManageService {
    // 模型训练/测试
    ModelTraining train(String modelId,Map<String,Object> params);
    ModelTestReport test(String modelId,Map<String,Object> params);
    List<ModelTraining> listTrainings(String modelId);
    List<ModelTestReport> listTestReports(String modelId);
    Map<String,Object> exportModels(String ids,String purpose);
    // 配置管理
    List<SysConfig> listConfigs(String configType);
    SysConfig getConfig(String configKey);
    SysConfig saveConfig(String configKey,String configValue,String configType,String description,String operator);
    List<SysConfigHistory> listHistory(String configKey,String configType);
    Map<String,Object> exportConfig(String configType);
    List<SysConfig> importConfig(List<Map<String,Object>> items,String operator);
    List<SysConfig> getDefaultConfigs();
    List<SysConfig> setDefault(List<String> configKeys);
    List<SysConfig> resetToDefault(List<String> configKeys,String operator);
}
