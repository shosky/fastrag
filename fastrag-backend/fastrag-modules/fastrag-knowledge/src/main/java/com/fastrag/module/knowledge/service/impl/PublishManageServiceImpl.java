package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.*; import com.fastrag.module.knowledge.mapper.*;
import com.fastrag.module.knowledge.service.PublishManageService;
import com.fastrag.module.publish.entity.KbUpdateLog;
import com.fastrag.module.publish.mapper.KbUpdateLogMapper;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
import java.util.regex.Pattern;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class PublishManageServiceImpl implements PublishManageService {
    private final KbPublishHistoryMapper2 phMapper;
    private final KbPublishPlanMapper ppMapper;
    private final KbResetConfigMapper rcMapper;
    private final KbUpdateLogMapper kbUpdateLogMapper;

    // ===== 发布管理 =====
    @Override public List<KbPublishHistory> listPublishHistory(String kbId, String knowledgeId) {
        var w = new LambdaQueryWrapper<KbPublishHistory>();
        if (kbId != null && !kbId.isEmpty()) w.eq(KbPublishHistory::getKbId, kbId);
        if (knowledgeId != null && !knowledgeId.isEmpty()) w.eq(KbPublishHistory::getKnowledgeId, knowledgeId);
        return phMapper.selectList(w.orderByDesc(KbPublishHistory::getCreatedAt));
    }
    @Override public KbPublishHistory publish(String kbId, String knowledgeId, KbPublishHistory history) {
        history.setKbId(kbId);
        history.setKnowledgeId(knowledgeId);
        history.setPublishType("publish");
        history.setStatus("published");
        history.setPublishedAt(LocalDateTime.now());
        if (history.getVersion() == null) history.setVersion(1);
        phMapper.insert(history);
        return history;
    }
    @Override public KbPublishHistory revoke(String kbId, String knowledgeId) {
        var h = new KbPublishHistory();
        h.setKbId(kbId);
        h.setKnowledgeId(knowledgeId);
        h.setPublishType("revoke");
        h.setStatus("revoked");
        h.setPublishedAt(LocalDateTime.now());
        phMapper.insert(h);
        return h;
    }
    @Override public KbPublishHistory getPublishHistory(String id) {
        return phMapper.selectById(id);
    }
    @Override public KbPublishPlan createPlan(KbPublishPlan plan) {
        if (plan.getExecutionStatus() == null) plan.setExecutionStatus("pending");
        ppMapper.insert(plan);
        return plan;
    }
    @Override public KbPublishPlan getPlanExecution(String planId) {
        return ppMapper.selectById(planId);
    }
    @Override public List<KbPublishPlan> listPlans(String kbId) {
        return ppMapper.selectList(
            new LambdaQueryWrapper<KbPublishPlan>()
                .eq(kbId != null && !kbId.isEmpty(), KbPublishPlan::getKbId, kbId)
                .orderByDesc(KbPublishPlan::getCreatedAt));
    }
    @Override public Map<String, Object> getStrategyEffect(String kbId) {
        Map<String, Object> r = new LinkedHashMap<>();
        var totalPublish = phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId, kbId));
        var successCount = phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>()
            .eq(KbPublishHistory::getKbId, kbId).eq(KbPublishHistory::getPublishType, "publish").eq(KbPublishHistory::getStatus, "published"));
        var revokeCount = phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>()
            .eq(KbPublishHistory::getKbId, kbId).eq(KbPublishHistory::getPublishType, "revoke"));
        r.put("totalPublish", totalPublish);
        r.put("successCount", successCount);
        r.put("revokeCount", revokeCount);
        return r;
    }

    // ===== 查看线上/线下版本 =====
    @Override public Map<String, Object> getOnlineVersion(String kbId, String knowledgeId) {
        var w = new LambdaQueryWrapper<KbPublishHistory>()
            .eq(KbPublishHistory::getKbId, kbId).eq(KbPublishHistory::getStatus, "published");
        if (knowledgeId != null && !knowledgeId.isEmpty()) w.eq(KbPublishHistory::getKnowledgeId, knowledgeId);
        var list = phMapper.selectList(w.orderByDesc(KbPublishHistory::getPublishedAt).last("LIMIT 1"));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("online", !list.isEmpty());
        r.put("version", list.isEmpty() ? null : list.get(0));
        return r;
    }
    @Override public Map<String, Object> getOfflineVersion(String kbId, String knowledgeId) {
        var w = new LambdaQueryWrapper<KbPublishHistory>()
            .eq(KbPublishHistory::getKbId, kbId).ne(KbPublishHistory::getStatus, "published");
        if (knowledgeId != null && !knowledgeId.isEmpty()) w.eq(KbPublishHistory::getKnowledgeId, knowledgeId);
        var list = phMapper.selectList(w.orderByDesc(KbPublishHistory::getCreatedAt).last("LIMIT 10"));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("offlineCount", list.size());
        r.put("versions", list);
        return r;
    }

    // ===== 知识重置 =====
    @Override public List<KbResetConfig> listResetConfigs(String kbId) {
        return rcMapper.selectList(new LambdaQueryWrapper<KbResetConfig>().eq(kbId != null && !kbId.isEmpty(), KbResetConfig::getKbId, kbId));
    }
    @Override public KbResetConfig saveResetConfig(KbResetConfig c) {
        if (c.getCanReset() == null) c.setCanReset(0);
        if (c.getMaxResetCount() == null) c.setMaxResetCount(3);
        if (c.getId() != null && !c.getId().isEmpty()) rcMapper.updateById(c);
        else rcMapper.insert(c);
        return c;
    }
    @Override public void resetKnowledge(String kbId, String knowledgeId) {
        // 将指定 knowledgeId 回退到上一个已发布版本：找到最新一条 published 记录，
        // 将当前记录标记为 revoked，再复制该 published 记录作为新的 online 版本
        var published = phMapper.selectList(
            new LambdaQueryWrapper<KbPublishHistory>()
                .eq(KbPublishHistory::getKbId, kbId)
                .eq(KbPublishHistory::getKnowledgeId, knowledgeId)
                .eq(KbPublishHistory::getStatus, "published")
                .orderByDesc(KbPublishHistory::getPublishedAt).last("LIMIT 1"));
        if (published.isEmpty()) throw new RuntimeException("没有可回退的已发布版本");
        var latestPublished = published.get(0);
        // 创建一条新的 published 记录作为回退后的在线版本
        var resetRecord = new KbPublishHistory();
        resetRecord.setKbId(kbId);
        resetRecord.setKnowledgeId(knowledgeId);
        resetRecord.setPublishType("publish");
        resetRecord.setStatus("published");
        resetRecord.setVersion(latestPublished.getVersion());
        resetRecord.setPublishedAt(LocalDateTime.now());
        resetRecord.setOperator("system");
        phMapper.insert(resetRecord);
    }

    // ===== 更新日志 =====
    @Override public Map<String, Object> getKnowledgeUpdateLogs(String kbId, int page, int pageSize) {
        var w = new LambdaQueryWrapper<KbUpdateLog>().eq(KbUpdateLog::getKbId, kbId).orderByDesc(KbUpdateLog::getTimestamp);
        var list = kbUpdateLogMapper.selectList(w.last("LIMIT " + pageSize + " OFFSET " + ((page - 1) * pageSize)));
        var total = kbUpdateLogMapper.selectCount(w);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("list", list);
        r.put("total", total);
        r.put("page", page);
        r.put("pageSize", pageSize);
        return r;
    }
}
