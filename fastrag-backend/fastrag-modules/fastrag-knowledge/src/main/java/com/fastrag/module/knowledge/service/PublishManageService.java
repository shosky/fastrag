package com.fastrag.module.knowledge.service;
import com.fastrag.module.knowledge.entity.*; import java.util.*;
public interface PublishManageService {
    // ===== 发布管理 =====
    List<KbPublishHistory> listPublishHistory(String kbId, String knowledgeId);
    KbPublishHistory publish(String kbId, String knowledgeId, KbPublishHistory history);
    KbPublishHistory revoke(String kbId, String knowledgeId);
    KbPublishHistory getPublishHistory(String id);
    KbPublishPlan createPlan(KbPublishPlan plan);
    KbPublishPlan getPlanExecution(String planId);
    List<KbPublishPlan> listPlans(String kbId);
    Map<String,Object> getStrategyEffect(String kbId);
    // 查看线上/线下版本
    Map<String,Object> getOnlineVersion(String kbId, String knowledgeId);
    Map<String,Object> getOfflineVersion(String kbId, String knowledgeId);
    // 知识重置
    List<KbResetConfig> listResetConfigs(String kbId);
    KbResetConfig saveResetConfig(KbResetConfig config);
    void resetKnowledge(String kbId, String knowledgeId);
    // 更新日志
    Map<String,Object> getKnowledgeUpdateLogs(String kbId, int page, int pageSize);
}
