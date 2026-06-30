package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.*; import java.util.*;
public interface PublishManageService {
    // 发布管理
    List<KbPublishHistory> listPublishHistory(String kbId,String knowledgeId);
    KbPublishHistory publish(String kbId,String knowledgeId,KbPublishHistory history);
    KbPublishHistory revoke(String kbId,String knowledgeId);
    KbPublishHistory getPublishHistory(String id);
    KbPublishPlan createPlan(KbPublishPlan plan);
    KbPublishPlan getPlanExecution(String planId);
    List<KbPublishPlan> listPlans(String kbId);
    Map<String,Object> getStrategyEffect(String kbId);
    // 审核流程
    List<KbReviewStrategy> listStrategies(String kbId);
    KbReviewStrategy createStrategy(KbReviewStrategy strategy);
    void deleteStrategy(String id);
    List<KbComplianceRule> listComplianceRules(String kbId);
    KbComplianceRule createComplianceRule(KbComplianceRule rule);
    KbComplianceRule updateComplianceRule(String id,KbComplianceRule rule);
    void deleteComplianceRule(String id);
    List<KbQualityRule> listQualityRules(String kbId);
    KbQualityRule createQualityRule(KbQualityRule rule);
    KbQualityRule updateQualityRule(String id,KbQualityRule rule);
    void deleteQualityRule(String id);
    // 审核流程设计
    List<KbReviewTemplate> listTemplates();
    KbReviewTemplate createTemplate(KbReviewTemplate template);
    KbReviewTemplate updateTemplate(String id,KbReviewTemplate template);
    void deleteTemplate(String id);
    List<KbReviewNode> listNodes(String templateId);
    KbReviewNode createNode(KbReviewNode node);
    void deleteNode(String id);
    // 监听管理
    List<KbListener> listListeners(String kbId);
    KbListener createListener(KbListener listener);
    KbListener toggleListener(String id,String action);
    KbListener updateListener(String id,KbListener listener);
    void deleteListener(String id);
    List<KbListenerLog> listListenerLogs(String listenerId, String level, int page, int pageSize);
    void clearListenerLogs(String id, String beforeDate);
    Map<String,Object> getListenerStats(String id);
    List<Map<String,Object>> getListenerTrends(String id, int days);
    Map<String,Object> saveListenerAlerts(String id, Map<String,Object> config);
    // 知识重置
    List<KbResetConfig> listResetConfigs(String kbId);
    KbResetConfig saveResetConfig(KbResetConfig config);
    Map<String,Object> resetKnowledge(String kbId,String knowledgeId);
    // 查看线上/线下版本
    Map<String,Object> getOnlineVersion(String kbId, String knowledgeId);
    Map<String,Object> getOfflineVersion(String kbId, String knowledgeId);
    // 审核流程细化
    List<Map<String,Object>> getReviewHistory(String strategyId);
    Map<String,Object> setReviewTimeout(String strategyId, Map<String,Object> config);
    // 审核报告/导出
    Map<String,Object> generatePublishReport(String kbId);
    Map<String,Object> exportPublishData(String kbId, String format);
    // 审核记录导出
    void exportReviewRecords(String kbId, jakarta.servlet.http.HttpServletResponse resp) throws Exception;
    // 导入审核知识
    Map<String,Object> importReviewKnowledge(String kbId, List<Map<String,Object>> items);
    // 导出未审核知识
    void exportUnreviewedKnowledge(String kbId, jakarta.servlet.http.HttpServletResponse resp) throws Exception;
    // 导入流程图
    Map<String,Object> importFlowChart(String kbId, Map<String,Object> flowData);
    // 监听日志保留策略
    Map<String,Object> setLogRetention(String kbId, Map<String,Object> config);
    Map<String,Object> getLogRetention(String kbId);
    // 审核性能指标
    Map<String,Object> getReviewMetrics(String kbId);
    // 审核优化引擎
    Map<String,Object> getReviewOptimizations(String kbId);
    Map<String,Object> applyReviewOptimization(String kbId, String optId);
    // 更新日志
    Map<String,Object> getKnowledgeUpdateLogs(String kbId, int page, int pageSize);
    // 内容比较
    Map<String,Object> compareKnowledgeContent(String kbId, String oldId, String newId);
    // 效率分析
    Map<String,Object> getPublishEfficiency(String kbId);
    Map<String,Object> getFlowChart(String kbId);
}
