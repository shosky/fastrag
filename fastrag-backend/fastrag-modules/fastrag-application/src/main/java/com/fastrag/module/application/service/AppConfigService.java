package com.fastrag.module.application.service;
import com.fastrag.module.application.entity.*; import java.util.*;
public interface AppConfigService {
    // 基础配置
    AppBasicConfig getBasic(String appId);
    AppBasicConfig saveBasic(String appId,AppBasicConfig config);
    // 对话配置
    AppDialogConfig getDialog(String appId);
    AppDialogConfig saveDialog(String appId,AppDialogConfig config);
    // 智能体配置（对齐Yuxi）
    AppConfig getConfig(String appId);
    AppConfig savePrompt(String appId, String prompt);
    AppConfig saveSummary(String appId, Map<String,Object> cfg);
    AppConfig saveMaxSteps(String appId, int maxSteps);
    AppConfig saveMaxTurns(String appId, int maxTurns);
    AppConfig saveRetryTimes(String appId, int retryTimes);
    AppConfig saveMaxTokens(String appId, int maxTokens);
    List<AppTrigger> listTriggers(String appId);
    AppTrigger createTrigger(String appId,AppTrigger t);
    AppTrigger updateTrigger(String id,AppTrigger t);
    void deleteTrigger(String id);
    AppTrigger testTrigger(String id,String input);
    AppTrigger runTrigger(String id,String input);
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
    // 数据库绑定
    List<AppDbBinding> listDbBindings(String appId);
    AppDbBinding bindDb(String appId,AppDbBinding b);
    AppDbBinding updateDbBinding(String id,AppDbBinding b);
    void unbindDb(String id);
    // 技能绑定
    List<AppSkillBinding> listSkillBindings(String appId);
    AppSkillBinding bindSkill(String appId,AppSkillBinding b);
    AppSkillBinding updateSkillBinding(String id,AppSkillBinding b);
    void unbindSkill(String id);
    // 工具绑定
    List<AppToolBinding> listToolBindings(String appId);
    AppToolBinding bindTool(String appId,AppToolBinding b);
    AppToolBinding updateToolBinding(String id,AppToolBinding b);
    void unbindTool(String id);
    // MCP 绑定
    List<AppMcpBinding> listMcpBindings(String appId);
    AppMcpBinding bindMcp(String appId,AppMcpBinding b);
    AppMcpBinding updateMcpBinding(String id,AppMcpBinding b);
    void unbindMcp(String id);
    // 发布
    List<AppPublishRecord> listPublishRecords(String appId);
    AppPublishRecord publish(String appId,AppPublishRecord r);
    // 对话测试
    List<AppDialogTest> listDialogTests(String appId);
    AppDialogTest createDialogTest(String appId,AppDialogTest t);
    void deleteDialogTest(String id);
    AppDialogTest updateDialogTest(String id,AppDialogTest t);
    // 对话优化
    List<AppOptimization> listOptimizations(String appId);
    AppOptimization createOptimization(String appId,AppOptimization o);
    AppOptimization updateOptimization(String id,AppOptimization o);
    void deleteOptimization(String id);
    AppOptimization applyOptimization(String id);
    Map<String,Object> analyze(String appId);
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
    Map<String,Object> triggerKnowledgeUpdate(String appId, Map<String,Object> cfg);
    // 知识库自动更新配置
    AppKbAutoUpdateConfig getAutoKnowledgeUpdate(String appId);
    AppKbAutoUpdateConfig saveAutoKnowledgeUpdate(String appId, AppKbAutoUpdateConfig cfg);
    // 对话记录
    Map<String,Object> listConversations(String appId, String keyword, Integer rating, String startDate, String endDate, int page, int size);
    Map<String,Object> getConversationDetail(String convId);
    void deleteConversation(String convId);
    void exportConversationsCsv(String appId, jakarta.servlet.http.HttpServletResponse resp) throws Exception;
}
