package com.fastrag.module.application.service;
import com.fastrag.module.application.entity.*; import java.util.*;
public interface AppConfigService {
    // 基础配置
    AppBasicConfig getBasic(String appId);
    AppBasicConfig saveBasic(String appId,AppBasicConfig config);
    // 重置基础配置：删除自定义配置，恢复默认
    AppBasicConfig resetBasic(String appId);
    // 对话配置
    AppDialogConfig getDialog(String appId);
    AppDialogConfig saveDialog(String appId,AppDialogConfig config);
    // 重置对话配置：删除自定义配置，恢复默认
    AppDialogConfig resetDialog(String appId);
    // 知识更新：自动更新配置读取/保存 + 手动触发（写真实更新日志）
    Map<String,Object> getAutoKnowledgeUpdate(String appId);
    Map<String,Object> saveAutoKnowledgeUpdate(String appId, Map<String,Object> cfg);
    List<AppTrigger> listTriggers(String appId);
    AppTrigger createTrigger(String appId,AppTrigger t);
    AppTrigger updateTrigger(String id,AppTrigger t);
    void deleteTrigger(String id);
    AppTrigger testTrigger(String id,String input);
    // 全局策略
    AppGlobalPolicy getGlobalPolicy(String appId);
    AppGlobalPolicy saveGlobalPolicy(String appId,AppGlobalPolicy p);
    List<AppVariable> listVariables(String appId);
    AppVariable createVariable(String appId,AppVariable v);
    void deleteVariable(String id);
    // 知识库绑定
    List<AppKbBinding> listKbBindings(String appId);
    AppKbBinding bindKb(String appId,AppKbBinding b);
    void unbindKb(String id);
    // 知识库配置（应用管理）：导出/导入/检索设置
    Map<String,Object> exportKbBindings(String appId);
    Map<String,Object> importKbBindings(String appId, Map<String,Object> data);
    Map<String,Object> getKbSettings(String appId);
    Map<String,Object> saveKbSettings(String appId, Map<String,Object> cfg);
    // 数据库绑定
    List<AppDbBinding> listDbBindings(String appId);
    AppDbBinding bindDb(String appId,AppDbBinding b);
    AppDbBinding updateDbBinding(String id,AppDbBinding b);
    void unbindDb(String id);
    // 发布管理：保存配置 / 上线机器人 / 发布范围 / 查看状态 / 更新发布 / 撤回
    List<AppPublishRecord> listPublishRecords(String appId);
    AppPublishRecord publish(String appId,AppPublishRecord r);
    Map<String,Object> savePublishConfig(String appId, Map<String,Object> cfg);
    Map<String,Object> getPublishStatus(String appId);
    AppPublishRecord revokePublish(String appId,String recordId);
    AppPublishRecord republish(String appId, Map<String,Object> cfg);
    // 对话测试
    List<AppDialogTest> listDialogTests(String appId);
    AppDialogTest createDialogTest(String appId,AppDialogTest t);
    void deleteDialogTest(String id);
    AppDialogTest updateDialogTest(String id,AppDialogTest t);
    AppDialogTest runDialogTest(String appId,String testId);
    Map<String,Object> runAllDialogTests(String appId);
    // 对话优化
    List<AppOptimization> listOptimizations(String appId);
    AppOptimization createOptimization(String appId,AppOptimization o);
    AppOptimization updateOptimization(String id,AppOptimization o);
    void deleteOptimization(String id);
    AppOptimization applyOptimization(String id);
    Map<String,Object> analyze(String appId);
    Map<String,Object> testOptimization(String id);
    // M16 扩展
    Map<String,Object> saveAdvanced(String appId, Map<String,Object> opts);
    Map<String,Object> exportConfig(String appId);
    Map<String,Object> importConfig(String appId, Map<String,Object> data);
    Map<String,Object> exportDialogConfig(String appId);
    Map<String,Object> importDialogConfig(String appId, Map<String,Object> data);
    AppVariable updateVariable(String id, AppVariable v);
    Map<String,Object> saveSensitiveWords(String appId, Map<String,Object> cfg);
    Map<String,Object> togglePolicy(String appId, Map<String,Object> cfg);
    Map<String,Object> saveUnmatchedConfig(String appId, Map<String,Object> cfg);
    Map<String,Object> getWorkflowConfig(String appId);
    Map<String,Object> saveWorkflowConfig(String appId, Map<String,Object> cfg);
    Map<String,Object> getMonitorData(String appId);
    Map<String,Object> getDebugInfo(String appId);
    Map<String,Object> saveDebugConfig(String appId, Map<String,Object> cfg);
    List<AppDebugLog> listDebugLogs(String appId);
    void clearDebugLogs(String appId);
    Map<String,Object> triggerKnowledgeUpdate(String appId, Map<String,Object> cfg);
    // 监控管理
    Map<String,Object> getMonitorChatRecords(String appId, String keyword, String status);
    Map<String,Object> getMonitorDataAnalysis(String appId);
    Map<String,Object> getMonitorAlertConfig(String appId);
    Map<String,Object> saveMonitorAlertConfig(String appId, Map<String,Object> cfg);
    Map<String,Object> getMonitorPerfMetrics(String appId);
    Map<String,Object> getMonitorOptimizeConfig(String appId);
    Map<String,Object> saveMonitorOptimizeConfig(String appId, Map<String,Object> cfg);
}
