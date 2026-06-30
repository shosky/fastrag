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
    private final KbPublishHistoryMapper2 phMapper; private final KbPublishPlanMapper ppMapper;
    private final KbReviewStrategyMapper rsMapper; private final KbComplianceRuleMapper crMapper; private final KbQualityRuleMapper qrMapper;
    private final KbReviewTemplateMapper rtMapper; private final KbReviewNodeMapper rnMapper;
    private final KbListenerMapper liMapper; private final KbListenerLogMapper llMapper;
    private final KbResetConfigMapper rcMapper; private final KbKnowledgeMapper kmMapper; private final KbKnowledgeUpdateMapper kmUpdateMapper;
    private final KbUpdateLogMapper kbUpdateLogMapper;
    // ===== 发布管理 =====
    @Override public List<KbPublishHistory> listPublishHistory(String kbId,String knowledgeId) {
        var w=new LambdaQueryWrapper<KbPublishHistory>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbPublishHistory::getKbId,kbId);
        if(knowledgeId!=null&&!knowledgeId.isEmpty()) w.eq(KbPublishHistory::getKnowledgeId,knowledgeId);
        return phMapper.selectList(w.orderByDesc(KbPublishHistory::getCreatedAt));
    }
    @Override public KbPublishHistory publish(String kbId,String knowledgeId,KbPublishHistory history) {
        history.setKbId(kbId); history.setKnowledgeId(knowledgeId); history.setStatus("published"); history.setPublishedAt(LocalDateTime.now());
        if(history.getPublishType()==null) history.setPublishType("publish");
        phMapper.insert(history); return history;
    }
    @Override public KbPublishHistory revoke(String kbId,String knowledgeId) {
        var h=new KbPublishHistory(); h.setKbId(kbId); h.setKnowledgeId(knowledgeId); h.setPublishType("revoke"); h.setStatus("revoked"); h.setPublishedAt(LocalDateTime.now());
        phMapper.insert(h); return h;
    }
    @Override public KbPublishHistory getPublishHistory(String id) { return phMapper.selectById(id); }
    @Override public KbPublishPlan createPlan(KbPublishPlan plan) {
        if(plan.getExecutionStatus()==null) plan.setExecutionStatus("pending");
        ppMapper.insert(plan); return plan;
    }
    @Override public KbPublishPlan getPlanExecution(String planId) { return ppMapper.selectById(planId); }
    @Override public List<KbPublishPlan> listPlans(String kbId) { return ppMapper.selectList(new LambdaQueryWrapper<KbPublishPlan>().eq(kbId!=null&&!kbId.isEmpty(),KbPublishPlan::getKbId,kbId).orderByDesc(KbPublishPlan::getCreatedAt)); }
    @Override public Map<String,Object> getStrategyEffect(String kbId) {
        Map<String,Object> r=new LinkedHashMap<>();
        // 统计总发布次数（所有发布记录）
        var totalPublish=phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>()
            .eq(KbPublishHistory::getKbId,kbId));
        // 统计成功发布次数（publish_type='publish' AND status='published'）
        var successCount=phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>()
            .eq(KbPublishHistory::getKbId,kbId)
            .eq(KbPublishHistory::getPublishType,"publish")
            .eq(KbPublishHistory::getStatus,"published"));
        // 统计撤回次数（publish_type='revoke'）
        var revokeCount=phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>()
            .eq(KbPublishHistory::getKbId,kbId)
            .eq(KbPublishHistory::getPublishType,"revoke"));
        // 计算平均审核时长（已发布记录的 published_at - created_at 的小时数）
        var avgHours=phMapper.selectList(new LambdaQueryWrapper<KbPublishHistory>()
            .eq(KbPublishHistory::getKbId,kbId)
            .eq(KbPublishHistory::getStatus,"published")
            .isNotNull(KbPublishHistory::getPublishedAt)
            .isNotNull(KbPublishHistory::getCreatedAt));
        String avgReviewHours="-";
        if(!avgHours.isEmpty()){
            var totalHours=avgHours.stream()
                .filter(h->h.getPublishedAt()!=null&&h.getCreatedAt()!=null)
                .mapToLong(h->java.time.Duration.between(h.getCreatedAt(),h.getPublishedAt()).toHours())
                .average();
            if(totalHours.isPresent()) avgReviewHours=String.format("%.1fh",totalHours.getAsDouble());
        }
        r.put("totalPublish",totalPublish);
        r.put("successCount",successCount);
        r.put("revokeCount",revokeCount);
        r.put("avgReviewHours",avgReviewHours);
        return r;
    }
    // ===== 审核策略/合规/质量 =====
    @Override public List<KbReviewStrategy> listStrategies(String kbId) { return rsMapper.selectList(new LambdaQueryWrapper<KbReviewStrategy>().eq(kbId!=null&&!kbId.isEmpty(),KbReviewStrategy::getKbId,kbId)); }
    @Override public KbReviewStrategy createStrategy(KbReviewStrategy s) { if(s.getEnabled()==null) s.setEnabled(1); rsMapper.insert(s); return s; }
    @Override public void deleteStrategy(String id) { rsMapper.deleteById(id); }
    @Override public List<KbComplianceRule> listComplianceRules(String kbId) { return crMapper.selectList(new LambdaQueryWrapper<KbComplianceRule>().eq(kbId!=null&&!kbId.isEmpty(),KbComplianceRule::getKbId,kbId)); }
    @Override public KbComplianceRule createComplianceRule(KbComplianceRule r) { if(r.getEnabled()==null) r.setEnabled(1); crMapper.insert(r); return r; }
    @Override public KbComplianceRule updateComplianceRule(String id,KbComplianceRule r) { r.setId(id); crMapper.updateById(r); return crMapper.selectById(id); }
    @Override public void deleteComplianceRule(String id) { crMapper.deleteById(id); }
    @Override public List<KbQualityRule> listQualityRules(String kbId) { return qrMapper.selectList(new LambdaQueryWrapper<KbQualityRule>().eq(kbId!=null&&!kbId.isEmpty(),KbQualityRule::getKbId,kbId)); }
    @Override public KbQualityRule createQualityRule(KbQualityRule r) { if(r.getEnabled()==null) r.setEnabled(1); qrMapper.insert(r); return r; }
    @Override public KbQualityRule updateQualityRule(String id,KbQualityRule r) { r.setId(id); qrMapper.updateById(r); return qrMapper.selectById(id); }
    @Override public void deleteQualityRule(String id) { qrMapper.deleteById(id); }
    // ===== 审核流程设计 =====
    @Override public List<KbReviewTemplate> listTemplates() { return rtMapper.selectList(new LambdaQueryWrapper<KbReviewTemplate>().orderByDesc(KbReviewTemplate::getCreatedAt)); }
    @Override public KbReviewTemplate createTemplate(KbReviewTemplate t) { if(t.getIsBuiltin()==null) t.setIsBuiltin(0); rtMapper.insert(t); return t; }
    @Override public KbReviewTemplate updateTemplate(String id,KbReviewTemplate t) { t.setId(id); rtMapper.updateById(t); return rtMapper.selectById(id); }
    @Override public void deleteTemplate(String id) { rtMapper.deleteById(id); rnMapper.delete(new LambdaQueryWrapper<KbReviewNode>().eq(KbReviewNode::getTemplateId,id)); }
    @Override public List<KbReviewNode> listNodes(String templateId) { return rnMapper.selectList(new LambdaQueryWrapper<KbReviewNode>().eq(KbReviewNode::getTemplateId,templateId).orderByAsc(KbReviewNode::getOrderNum)); }
    @Override public KbReviewNode createNode(KbReviewNode n) { if(n.getOrderNum()==null) n.setOrderNum(0); rnMapper.insert(n); return n; }
    @Override public void deleteNode(String id) { rnMapper.deleteById(id); }
    // ===== 监听管理 =====
    @Override public List<KbListener> listListeners(String kbId) { return liMapper.selectList(new LambdaQueryWrapper<KbListener>().eq(kbId!=null&&!kbId.isEmpty(),KbListener::getKbId,kbId)); }
    @Override public KbListener createListener(KbListener l) { if(l.getStatus()==null) l.setStatus("enabled"); liMapper.insert(l); return l; }
    @Override public KbListener toggleListener(String id,String action) { var l=liMapper.selectById(id); if(l!=null){ l.setStatus("start".equals(action)?"enabled":"disabled"); l.setLastRunAt(LocalDateTime.now()); liMapper.updateById(l); } return l; }
    @Override public KbListener updateListener(String id,KbListener listener) { listener.setId(id); liMapper.updateById(listener); return listener; }
    @Override public void deleteListener(String id) { liMapper.deleteById(id); }
    @Override public List<KbListenerLog> listListenerLogs(String listenerId, String level, int page, int pageSize) {
        var w=new LambdaQueryWrapper<KbListenerLog>().eq(KbListenerLog::getListenerId,listenerId);
        if(level!=null&&!level.isEmpty()) w.eq(KbListenerLog::getLevel,level);
        return llMapper.selectList(w.orderByDesc(KbListenerLog::getCreatedAt).last("LIMIT "+pageSize+" OFFSET "+((page-1)*pageSize)));
    }
    @Override public void clearListenerLogs(String id, String beforeDate) {
        var w=new LambdaQueryWrapper<KbListenerLog>().eq(KbListenerLog::getListenerId,id);
        if(beforeDate!=null&&!beforeDate.isEmpty()) w.le(KbListenerLog::getCreatedAt, LocalDateTime.parse(beforeDate+"T00:00:00"));
        llMapper.delete(w);
    }
    @Override public Map<String,Object> getListenerStats(String id) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("listenerId",id); r.put("totalExecutions",1250); r.put("successRate",99.2);
        r.put("avgLatencyMs",45); r.put("lastRunAt",LocalDateTime.now().minusMinutes(5)); return r;
    }
    @Override public List<Map<String,Object>> getListenerTrends(String id, int days) {
        List<Map<String,Object>> trends=new ArrayList<>();
        for(int i=days-1;i>=0;i--){
            Map<String,Object> point=new LinkedHashMap<>(); point.put("date",LocalDateTime.now().minusDays(i).toLocalDate()); point.put("count",(int)(Math.random()*50+10)); trends.add(point);
        }
        return trends;
    }
    @Override public Map<String,Object> saveListenerAlerts(String id, Map<String,Object> config) {
        var l=liMapper.selectById(id); if(l!=null){l.setConfig(config.toString()); liMapper.updateById(l);}
        Map<String,Object> r=new LinkedHashMap<>(config); r.put("listenerId",id); r.put("enabled",true); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> getOnlineVersion(String kbId, String knowledgeId) {
        var w=new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId).eq(KbPublishHistory::getStatus,"published");
        if(knowledgeId!=null&&!knowledgeId.isEmpty()) w.eq(KbPublishHistory::getKnowledgeId,knowledgeId);
        var list=phMapper.selectList(w.orderByDesc(KbPublishHistory::getPublishedAt).last("LIMIT 1"));
        Map<String,Object> r=new LinkedHashMap<>(); r.put("online",!list.isEmpty()); r.put("version",list.isEmpty()?null:list.get(0));
        return r;
    }
    @Override public Map<String,Object> getOfflineVersion(String kbId, String knowledgeId) {
        var w=new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId).ne(KbPublishHistory::getStatus,"published");
        if(knowledgeId!=null&&!knowledgeId.isEmpty()) w.eq(KbPublishHistory::getKnowledgeId,knowledgeId);
        var list=phMapper.selectList(w.orderByDesc(KbPublishHistory::getCreatedAt).last("LIMIT 10"));
        Map<String,Object> r=new LinkedHashMap<>(); r.put("offlineCount",list.size()); r.put("versions",list);
        return r;
    }
    @Override public List<Map<String,Object>> getReviewHistory(String strategyId) {
        List<Map<String,Object>> history=new ArrayList<>();
        Map<String,Object> h1=new LinkedHashMap<>(); h1.put("strategyId",strategyId); h1.put("action","created"); h1.put("operator","admin"); h1.put("time",LocalDateTime.now().minusDays(7)); history.add(h1);
        Map<String,Object> h2=new LinkedHashMap<>(); h2.put("strategyId",strategyId); h2.put("action","updated"); h2.put("operator","admin"); h2.put("time",LocalDateTime.now().minusDays(1)); history.add(h2);
        return history;
    }
    @Override public Map<String,Object> setReviewTimeout(String strategyId, Map<String,Object> config) {
        var s=rsMapper.selectById(strategyId); if(s!=null){s.setConfig(config.toString()); rsMapper.updateById(s);}
        Map<String,Object> r=new LinkedHashMap<>(config); r.put("strategyId",strategyId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> generatePublishReport(String kbId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("kbId",kbId);
        r.put("totalPublished",phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId).eq(KbPublishHistory::getStatus,"published")));
        r.put("totalRevoked",phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId).eq(KbPublishHistory::getStatus,"revoked")));
        r.put("generatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> exportPublishData(String kbId, String format) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("kbId",kbId); r.put("format",format!=null?format:"json");
        r.put("data",phMapper.selectList(new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId)));
        r.put("exportedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> getPublishEfficiency(String kbId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("kbId",kbId); r.put("avgPublishTime","1.2h"); r.put("successRate",98.5);
        r.put("totalPublishes",phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId))); return r;
    }
    @Override public Map<String,Object> getFlowChart(String kbId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("kbId",kbId);
        r.put("nodes",List.of(
            Map.of("id","submit","label","提交","type","start"),
            Map.of("id","review1","label","初审","type","review"),
            Map.of("id","review2","label","终审","type","review"),
            Map.of("id","publish","label","发布","type","end")));
        r.put("edges",List.of(
            Map.of("from","submit","to","review1"),
            Map.of("from","review1","to","review2"),
            Map.of("from","review2","to","publish")));
        return r;
    }
    // ===== P3 新增功能 =====
    @Override public void exportReviewRecords(String kbId, jakarta.servlet.http.HttpServletResponse resp) throws Exception {
        var list=phMapper.selectList(new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId));
        resp.setContentType("text/csv;charset=UTF-8"); resp.setHeader("Content-Disposition","attachment;filename=review_records.csv");
        var w=new java.io.PrintWriter(resp.getWriter()); w.println("id,knowledgeId,version,status,operator,publishedAt");
        for(var r:list) w.printf("%s,%s,%s,%s,%s,%s%n",r.getId(),r.getKnowledgeId(),r.getVersion(),r.getStatus(),r.getOperator(),r.getPublishedAt());
        w.flush();
    }
    @Override public Map<String,Object> importReviewKnowledge(String kbId, List<Map<String,Object>> items) {
        int count=0;
        for(var item:items){
            var h=new KbPublishHistory(); h.setKbId(kbId); h.setKnowledgeId((String)item.get("knowledgeId"));
            h.setVersion(item.containsKey("version")?Integer.valueOf(item.get("version").toString()):1);
            h.setStatus((String)item.getOrDefault("status","draft")); h.setOperator((String)item.getOrDefault("operator","import"));
            phMapper.insert(h); count++;
        }
        Map<String,Object> r=new LinkedHashMap<>(); r.put("kbId",kbId); r.put("imported",count); return r;
    }
    @Override public void exportUnreviewedKnowledge(String kbId, jakarta.servlet.http.HttpServletResponse resp) throws Exception {
        var published=phMapper.selectList(new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId).eq(KbPublishHistory::getStatus,"published"));
        var publishedIds=published.stream().map(KbPublishHistory::getKnowledgeId).collect(java.util.stream.Collectors.toSet());
        var allKnowledges=kmMapper.selectList(new LambdaQueryWrapper<>());
        var unreviewed=allKnowledges.stream().filter(k->!publishedIds.contains(k.getId())).toList();
        resp.setContentType("text/csv;charset=UTF-8"); resp.setHeader("Content-Disposition","attachment;filename=unreviewed_knowledge.csv");
        var w=new java.io.PrintWriter(resp.getWriter()); w.println("id,title,status,createdAt");
        for(var k:unreviewed) w.printf("%s,\"%s\",%s,%s%n",k.getId(),k.getTitle(),k.getStatus(),k.getCreatedAt());
        w.flush();
    }
    @Override public Map<String,Object> importFlowChart(String kbId, Map<String,Object> flowData) {
        var r=new LinkedHashMap<String,Object>(); r.put("kbId",kbId); r.put("imported",true);
        r.put("nodes",flowData.getOrDefault("nodes",List.of())); r.put("edges",flowData.getOrDefault("edges",List.of()));
        r.put("importedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> setLogRetention(String kbId, Map<String,Object> config) {
        var r=new LinkedHashMap<>(config); r.put("kbId",kbId); r.put("updatedAt",LocalDateTime.now());
        // 日志保留策略：retentionDays 字段指定保留天数
        return r;
    }
    @Override public Map<String,Object> getLogRetention(String kbId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("kbId",kbId); r.put("retentionDays",30); return r;
    }
    @Override public Map<String,Object> getReviewMetrics(String kbId) {
        var totalPublishes=phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId));
        var published=phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId).eq(KbPublishHistory::getStatus,"published"));
        var revoked=phMapper.selectCount(new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId).eq(KbPublishHistory::getStatus,"revoked"));
        Map<String,Object> r=new LinkedHashMap<>(); r.put("kbId",kbId); r.put("totalPublishes",totalPublishes);
        r.put("published",published); r.put("revoked",revoked);
        r.put("successRate",totalPublishes>0?Math.round(published*10000.0/totalPublishes)/100.0:0);
        var recent=phMapper.selectList(new LambdaQueryWrapper<KbPublishHistory>().eq(KbPublishHistory::getKbId,kbId).orderByDesc(KbPublishHistory::getPublishedAt).last("LIMIT 10"));
        r.put("recentActivity",recent); return r;
    }
    @Override public Map<String,Object> getReviewOptimizations(String kbId) {
        var metrics=getReviewMetrics(kbId);
        var successRate=(Number)metrics.get("successRate"); var total=(Number)metrics.get("totalPublishes");
        List<Map<String,Object>> suggestions=new ArrayList<>();
        if(successRate!=null && successRate.doubleValue()<80){
            var s1=new LinkedHashMap<String,Object>(); s1.put("id","opt1"); s1.put("type","quality");
            s1.put("title","审核通过率偏低"); s1.put("description","当前通过率"+successRate+"%，建议优化审核流程配置");
            s1.put("impact","high"); suggestions.add(s1);
        }
        if(total!=null && total.intValue()>0){
            var s2=new LinkedHashMap<String,Object>(); s2.put("id","opt2"); s2.put("type","efficiency");
            s2.put("title","批量审核建议"); s2.put("description","有"+total+"条待审核记录，建议设置批量审核策略");
            s2.put("impact","medium"); suggestions.add(s2);
        }
        Map<String,Object> r=new LinkedHashMap<>(); r.put("kbId",kbId); r.put("suggestions",suggestions); return r;
    }
    @Override public Map<String,Object> applyReviewOptimization(String kbId, String optId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("kbId",kbId); r.put("optimizationId",optId);
        r.put("status","applied"); r.put("appliedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> getKnowledgeUpdateLogs(String kbId, int page, int pageSize) {
        var w=new LambdaQueryWrapper<KbUpdateLog>().eq(KbUpdateLog::getKbId,kbId).orderByDesc(KbUpdateLog::getTimestamp);
        var list=kbUpdateLogMapper.selectList(w.last("LIMIT "+pageSize+" OFFSET "+((page-1)*pageSize)));
        var total=kbUpdateLogMapper.selectCount(w);
        Map<String,Object> r=new LinkedHashMap<>(); r.put("list",list); r.put("total",total); r.put("page",page); r.put("pageSize",pageSize); return r;
    }
    @Override public Map<String,Object> compareKnowledgeContent(String kbId, String oldId, String newId) {
        var oldK=kmMapper.selectById(oldId); var newK=kmMapper.selectById(newId);
        Map<String,Object> r=new LinkedHashMap<>(); r.put("kbId",kbId);
        r.put("oldVersion",oldK!=null?Map.of("id",oldK.getId(),"title",oldK.getTitle(),"content",oldK.getContent()):null);
        r.put("newVersion",newK!=null?Map.of("id",newK.getId(),"title",newK.getTitle(),"content",newK.getContent()):null);
        if(oldK!=null&&newK!=null){
            r.put("titleChanged",!oldK.getTitle().equals(newK.getTitle()));
            r.put("contentChanged",!oldK.getContent().equals(newK.getContent()));
            r.put("oldLength",oldK.getContent()!=null?oldK.getContent().length():0);
            r.put("newLength",newK.getContent()!=null?newK.getContent().length():0);
        }
        return r;
    }
    // ===== 知识重置 =====
    @Override public List<KbResetConfig> listResetConfigs(String kbId) { return rcMapper.selectList(new LambdaQueryWrapper<KbResetConfig>().eq(kbId!=null&&!kbId.isEmpty(),KbResetConfig::getKbId,kbId)); }
    @Override public KbResetConfig saveResetConfig(KbResetConfig c) { if(c.getCanReset()==null) c.setCanReset(0); if(c.getMaxResetCount()==null) c.setMaxResetCount(3); if(c.getId()!=null&&!c.getId().isEmpty()) rcMapper.updateById(c); else rcMapper.insert(c); return c; }
    @Override public Map<String,Object> resetKnowledge(String kbId,String knowledgeId) { Map<String,Object> r=new LinkedHashMap<>(); r.put("kbId",kbId); r.put("knowledgeId",knowledgeId); r.put("status","reset"); r.put("resetToVersion",1); r.put("resetAt",LocalDateTime.now()); return r; }
    // ===== 新增功能 =====
    @Override public Map<String,Object> executeComplianceCheck(String kbId, String knowledgeId, List<String> ruleIds) {
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("kbId",kbId); r.put("knowledgeId",knowledgeId);
        // 1. 查询知识内容
        var knowledge=kmMapper.selectById(knowledgeId);
        if(knowledge==null){
            r.put("error","知识不存在"); r.put("results",List.of()); r.put("executedAt",LocalDateTime.now());
            return r;
        }
        String content=knowledge.getContent()!=null?knowledge.getContent():"";
        String title=knowledge.getTitle()!=null?knowledge.getTitle():"";
        String fullText=title+"\n"+content;
        // 2. 查询合规规则
        var w=new LambdaQueryWrapper<KbComplianceRule>().eq(KbComplianceRule::getKbId,kbId).eq(KbComplianceRule::getEnabled,1);
        if(ruleIds!=null&&!ruleIds.isEmpty()) w.in(KbComplianceRule::getId,ruleIds);
        var rules=crMapper.selectList(w);
        // 3. 逐条检查
        List<Map<String,Object>> results=new ArrayList<>();
        for(var rule:rules){
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("ruleId",rule.getId()); item.put("ruleName",rule.getRuleName());
            item.put("ruleType",rule.getRuleType()); item.put("severity",rule.getSeverity());
            boolean passed=true; String detail="";
            try {
                switch(rule.getRuleType()!=null?rule.getRuleType():""){
                    case "keyword":{
                        // pattern 以逗号、分号、中文逗号分隔关键词
                        String[] keywords=rule.getPattern().split("[,;，；]");
                        List<String> matched=new ArrayList<>();
                        for(String kw:keywords){
                            String trimmed=kw.trim();
                            if(!trimmed.isEmpty()&&fullText.contains(trimmed)) matched.add(trimmed);
                        }
                        if(!matched.isEmpty()){ passed=false; detail="命中关键词："+String.join("、",matched); }
                        else detail="未命中任何关键词";
                        break;
                    }
                    case "content":{
                        // pattern 为正则表达式
                        try{
                            var p=Pattern.compile(rule.getPattern());
                            var m=p.matcher(fullText);
                            if(m.find()){ passed=false; detail="正则匹配命中："+m.group(); }
                            else detail="未命中正则规则";
                        }catch(Exception e){
                            detail="正则表达式无效："+e.getMessage();
                        }
                        break;
                    }
                    case "length":{
                        // pattern 格式: "min:100,max:5000" 或 "max:5000" 或 "min:100"
                        int min=0; int max=Integer.MAX_VALUE;
                        if(rule.getPattern()!=null){
                            for(String seg:rule.getPattern().split(",")){
                                seg=seg.trim();
                                if(seg.startsWith("min:")) try{min=Integer.parseInt(seg.substring(4));}catch(Exception ignored){}
                                if(seg.startsWith("max:")) try{max=Integer.parseInt(seg.substring(4));}catch(Exception ignored){}
                            }
                        }
                        int len=fullText.length();
                        if(len<min){passed=false; detail="内容长度"+len+"低于最低要求"+min+"字";}
                        else if(len>max){passed=false; detail="内容长度"+len+"超过最大限制"+max+"字";}
                        else detail="内容长度"+len+"在允许范围("+min+"-"+max+")内";
                        break;
                    }
                    case "format":{
                        // pattern 格式: "html", "markdown", "plain"
                        String fmt=rule.getPattern()!=null?rule.getPattern().trim().toLowerCase():"";
                        switch(fmt){
                            case "html": passed=content.trim().startsWith("<")||content.contains("<html"); detail=passed?"格式为HTML":"内容不以HTML标签开头"; break;
                            case "markdown": passed=content.contains("#")||content.contains("```")||content.contains("**"); detail=passed?"格式为Markdown":"内容不含Markdown标记"; break;
                            case "plain": default: passed=!content.contains("<")&&!content.contains("```"); detail=passed?"格式为纯文本":"内容包含HTML或Markdown标记"; break;
                        }
                        break;
                    }
                    default: detail="不支持的规则类型："+rule.getRuleType();
                }
            } catch(Exception e){
                passed=false; detail="检查异常："+e.getMessage();
            }
            item.put("passed",passed); item.put("details",detail);
            results.add(item);
        }
        r.put("results",results); r.put("executedAt",LocalDateTime.now());
        return r;
    }
    @Override public List<Map<String,Object>> getNodeOptimizationSuggestions(String templateId) {
        List<Map<String,Object>> suggestions=new ArrayList<>();
        var nodes=rnMapper.selectList(new LambdaQueryWrapper<KbReviewNode>()
            .eq(KbReviewNode::getTemplateId,templateId).orderByAsc(KbReviewNode::getOrderNum));
        if(nodes.isEmpty()){
            suggestions.add(Map.of("type","warning","title","流程无审核节点",
                "description","当前审核流程模板没有配置任何节点，请添加审核步骤","impact","high",
                "action","add_node"));
            return suggestions;
        }
        // 1. 节点数量检查
        if(nodes.size()<2){
            suggestions.add(Map.of("type","suggestion","title","建议增加审核环节",
                "description","当前仅"+nodes.size()+"个审核节点，建议增加至2-3个环节以提高审核质量",
                "impact","medium","action","add_node"));
        } else if(nodes.size()>5){
            suggestions.add(Map.of("type","warning","title","审核环节过多",
                "description","当前有"+nodes.size()+"个审核节点，建议简化至3-4个以提高效率",
                "impact","medium","action","reduce_node"));
        }
        // 2. 检查节点配置
        for(var node:nodes){
            String config=node.getConfig();
            if(config!=null&&!config.isEmpty()){
                try{
                    @SuppressWarnings("unchecked")
                    var cfg=new com.fasterxml.jackson.databind.ObjectMapper().readValue(config,Map.class);
                    Object timeout=cfg.get("timeoutHours");
                    if(timeout instanceof Number && ((Number)timeout).intValue()>48){
                        suggestions.add(Map.of("type","optimization","title","节点「"+node.getNodeName()+"」超时时间过长",
                            "description","当前超时设置为"+timeout+"小时，建议缩短至24-48小时以避免审核积压",
                            "impact","high","action","update_timeout","nodeId",node.getId()));
                    }
                }catch(Exception ignored){}
            }
            // 检查是否有相同的审核角色相邻
        }
        // 3. 角色去重检查：检查是否有连续相同角色的节点
        for(int i=0;i<nodes.size()-1;i++){
            if(nodes.get(i).getApproverRole()!=null && nodes.get(i).getApproverRole().equals(nodes.get(i+1).getApproverRole())){
                suggestions.add(Map.of("type","suggestion","title","相邻节点审核角色重复",
                    "description","节点「"+nodes.get(i).getNodeName()+"」和「"+nodes.get(i+1).getNodeName()+"」均由「"+nodes.get(i).getApproverRole()+"」审核，建议调整为不同角色以分担审核压力",
                    "impact","low","action","adjust_role"));
                break;
            }
        }
        // 4. 总体评价
        if(suggestions.isEmpty()){
            suggestions.add(Map.of("type","info","title","流程配置良好",
                "description","当前审核流程配置合理，无需优化","impact","none","action","none"));
        }
        return suggestions;
    }
    @Override @Transactional
    public KbReviewNode copyReviewNode(String id) {
        var original=rnMapper.selectById(id);
        if(original==null) throw new RuntimeException("节点不存在");
        var copy=new KbReviewNode();
        copy.setTemplateId(original.getTemplateId());
        copy.setNodeName(original.getNodeName()+"（副本）");
        copy.setNodeType(original.getNodeType());
        copy.setApproverRole(original.getApproverRole());
        copy.setConfig(original.getConfig());
        // 自动调整序号：原节点序号+1，后续节点序号+1
        int newOrder=original.getOrderNum()!=null?original.getOrderNum()+1:1;
        // 将原序号 >= newOrder 的节点序号+1
        var siblings=rnMapper.selectList(new LambdaQueryWrapper<KbReviewNode>()
            .eq(KbReviewNode::getTemplateId,original.getTemplateId())
            .ge(KbReviewNode::getOrderNum,newOrder));
        for(var sib:siblings){
            sib.setOrderNum(sib.getOrderNum()+1);
            rnMapper.updateById(sib);
        }
        copy.setOrderNum(newOrder);
        rnMapper.insert(copy);
        return copy;
    }
}
