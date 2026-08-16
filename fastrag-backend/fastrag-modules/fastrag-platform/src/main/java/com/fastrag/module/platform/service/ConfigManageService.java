package com.fastrag.module.platform.service;

/**
 * 平台配置管理服务接口
 * <p>
 * 提供平台管理的核心业务能力，涵盖四个主要功能域：
 * </p>
 * <ol>
 *   <li><b>模型训练与测试</b>：触发模型训练、执行模型测试，查询训练记录和测试报告，以及导出模型数据</li>
 *   <li><b>系统配置管理</b>：配置项的增删改查、变更历史记录、默认值管理、配置导入导出与重置</li>
 *   <li><b>安全策略管理</b>：安全策略的 CRUD 操作，用于内容安全防护</li>
 *   <li><b>发布策略管理</b>：发布策略的 CRUD 操作，用于控制内容发布行为</li>
 * </ol>
 *
 * <p>该服务是 {@link com.fastrag.module.platform.controller.ConfigManageController} 的唯一业务依赖，
 * 配置变更操作会自动记录到 {@link com.fastrag.module.platform.entity.SysConfigHistory} 中以支持审计追踪。
 * 配置值以 JSON 字符串形式存储。</p>
 *
 * @see com.fastrag.module.platform.controller.ConfigManageController
 * @see com.fastrag.module.platform.service.impl.ConfigManageServiceImpl
 */
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
    // 安全策略 CRUD
    List<SysSecurityPolicy> listSecurityPolicies(String policyType);
    SysSecurityPolicy createSecurityPolicy(SysSecurityPolicy p);
    SysSecurityPolicy updateSecurityPolicy(String id,SysSecurityPolicy p);
    void deleteSecurityPolicy(String id);
    // 发布策略 CRUD
    List<SysPublishStrategy> listPublishStrategies(String strategyType);
    SysPublishStrategy createPublishStrategy(SysPublishStrategy s);
    SysPublishStrategy updatePublishStrategy(String id,SysPublishStrategy s);
    void deletePublishStrategy(String id);
}
