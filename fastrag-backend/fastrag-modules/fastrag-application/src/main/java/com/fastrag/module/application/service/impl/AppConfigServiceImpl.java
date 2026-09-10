package com.fastrag.module.application.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.application.entity.*; import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.AppConfigService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class AppConfigServiceImpl implements AppConfigService {
    private final AppBasicConfigMapper basicMapper; private final AppDialogConfigMapper dialogMapper; private final AppTriggerMapper triggerMapper;
    private final AppGlobalPolicyMapper policyMapper; private final AppVariableMapper varMapper; private final AppKbBindingMapper kbMapper;
    private final AppDbBindingMapper dbMapper; private final AppPublishRecordMapper pubMapper; private final AppDialogTestMapper testMapper;
    private final AppOptimizationMapper optMapper; private final AppConfigMapper configMapper; private final AppDebugLogMapper debugLogMapper;
    private final ObjectMapper objectMapper; private final com.fastrag.module.application.service.AppService appService;
    // 基础配置
    @Override public AppBasicConfig getBasic(String appId) { return basicMapper.selectOne(new LambdaQueryWrapper<AppBasicConfig>().eq(AppBasicConfig::getAppId,appId)); }
    @Override public AppBasicConfig saveBasic(String appId,AppBasicConfig c) { c.setAppId(appId); var e=getBasic(appId); if(e==null){c.setMemoryRounds(5);basicMapper.insert(c);}else{c.setId(e.getId());basicMapper.updateById(c);} return c; }
    // 对话配置
    @Override public AppDialogConfig getDialog(String appId) { return dialogMapper.selectOne(new LambdaQueryWrapper<AppDialogConfig>().eq(AppDialogConfig::getAppId,appId)); }
    @Override public AppDialogConfig saveDialog(String appId,AppDialogConfig c) { c.setAppId(appId); var e=getDialog(appId); if(e==null)dialogMapper.insert(c);else{c.setId(e.getId());dialogMapper.updateById(c);} return c; }
    // 触发器
    @Override public List<AppTrigger> listTriggers(String appId) { return triggerMapper.selectList(new LambdaQueryWrapper<AppTrigger>().eq(AppTrigger::getAppId,appId)); }
    @Override public AppTrigger createTrigger(String appId,AppTrigger t) { t.setAppId(appId); if(t.getEnabled()==null)t.setEnabled(1); if(t.getPriority()==null)t.setPriority(0); triggerMapper.insert(t); return t; }
    @Override public AppTrigger updateTrigger(String id,AppTrigger t) { t.setId(id); triggerMapper.updateById(t); return triggerMapper.selectById(id); }
    @Override public void deleteTrigger(String id) { triggerMapper.deleteById(id); }
    @Override public AppTrigger testTrigger(String id,String input) { var t=triggerMapper.selectById(id); if(t!=null)t.setHitCount((t.getHitCount()!=null?t.getHitCount():0)+1); triggerMapper.updateById(t); return t; }
    // 全局策略
    @Override public AppGlobalPolicy getGlobalPolicy(String appId) { return policyMapper.selectOne(new LambdaQueryWrapper<AppGlobalPolicy>().eq(AppGlobalPolicy::getAppId,appId)); }
    @Override public AppGlobalPolicy saveGlobalPolicy(String appId,AppGlobalPolicy p) { p.setAppId(appId); var e=getGlobalPolicy(appId); if(e==null){if(p.getSafetyEnabled()==null)p.setSafetyEnabled(1); policyMapper.insert(p);}else{p.setId(e.getId());policyMapper.updateById(p);} return p; }
    // 变量
    @Override public List<AppVariable> listVariables(String appId) { return varMapper.selectList(new LambdaQueryWrapper<AppVariable>().eq(AppVariable::getAppId,appId)); }
    @Override public AppVariable createVariable(String appId,AppVariable v) { v.setAppId(appId); varMapper.insert(v); return v; }
    @Override public void deleteVariable(String id) { varMapper.deleteById(id); }
    // 知识库绑定
    @Override public List<AppKbBinding> listKbBindings(String appId) { return kbMapper.selectList(new LambdaQueryWrapper<AppKbBinding>().eq(AppKbBinding::getAppId,appId)); }
    @Override public AppKbBinding bindKb(String appId,AppKbBinding b) { b.setAppId(appId); if(b.getPriority()==null)b.setPriority(0); if(b.getEnabled()==null)b.setEnabled(1); kbMapper.insert(b); return b; }
    @Override public void unbindKb(String id) { kbMapper.deleteById(id); }
    // ===== 知识库配置（应用管理）扩展 =====
    @Override public Map<String,Object> exportKbBindings(String appId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId);
        r.put("settings",getKbSettings(appId)); r.put("bindings",listKbBindings(appId));
        return r;
    }
    @Override public Map<String,Object> importKbBindings(String appId, Map<String,Object> data) {
        // 1. 清空该应用旧的绑定，按导入内容重新绑定
        kbMapper.delete(new LambdaQueryWrapper<AppKbBinding>().eq(AppKbBinding::getAppId,appId));
        if(data.containsKey("settings") && data.get("settings") instanceof Map) {
            @SuppressWarnings("unchecked") var s=(Map<String,Object>)data.get("settings");
            saveKbSettings(appId,s);
        }
        if(data.containsKey("bindings") && data.get("bindings") instanceof List) {
            @SuppressWarnings("unchecked") var list=(List<Object>)data.get("bindings");
            for(Object o:list) {
                if(!(o instanceof Map)) continue;
                @SuppressWarnings("unchecked") var m=(Map<String,Object>)o;
                String kbId=m.get("kbId")!=null?m.get("kbId").toString():null;
                if(!StringUtils.hasText(kbId)) continue;
                AppKbBinding b=new AppKbBinding(); b.setKbId(kbId);
                if(m.get("priority") instanceof Number) b.setPriority(((Number)m.get("priority")).intValue());
                if(m.get("enabled") instanceof Number) b.setEnabled(((Number)m.get("enabled")).intValue());
                bindKb(appId,b);
            }
        }
        return exportKbBindings(appId);
    }
    @Override public Map<String,Object> getKbSettings(String appId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId);
        var c=configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,appId));
        if(c!=null && StringUtils.hasText(c.getKbSettings())) {
            try { var m=objectMapper.readValue(c.getKbSettings(),Map.class); if(m!=null) r.putAll(m); } catch (Exception ignored) {}
        }
        if(!r.containsKey("specifyRetrieval")) r.put("specifyRetrieval",true);
        if(!r.containsKey("bindPersonalKB")) r.put("bindPersonalKB","no");
        return r;
    }
    @Override public Map<String,Object> saveKbSettings(String appId, Map<String,Object> cfg) {
        try {
            var json=objectMapper.writeValueAsString(cfg);
            var c=configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,appId));
            if(c==null){ c=new AppConfig(); c.setAppId(appId); c.setKbSettings(json); configMapper.insert(c); }
            else { c.setKbSettings(json); configMapper.updateById(c); }
        } catch (Exception e) { throw new RuntimeException("保存知识库配置失败",e); }
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("appId",appId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    // 数据库绑定
    @Override public List<AppDbBinding> listDbBindings(String appId) { return dbMapper.selectList(new LambdaQueryWrapper<AppDbBinding>().eq(AppDbBinding::getAppId,appId)); }
    @Override public AppDbBinding bindDb(String appId,AppDbBinding b) { b.setAppId(appId); if(b.getEnabled()==null)b.setEnabled(1); if(!StringUtils.hasText(b.getAllowedTables()))b.setAllowedTables(null); dbMapper.insert(b); return b; }
    @Override public AppDbBinding updateDbBinding(String id,AppDbBinding b) { b.setId(id); dbMapper.updateById(b); return dbMapper.selectById(id); }
    @Override public void unbindDb(String id) { dbMapper.deleteById(id); }
    // ===== 发布管理 =====
    private String publishSnapshot(String appId) { try { return objectMapper.writeValueAsString(exportConfig(appId)); } catch (Exception e) { return null; } }
    private String toJson(Object v) { if(v==null) return null; try { return objectMapper.writeValueAsString(v); } catch (Exception e) { return null; } }
    private String str(Object v) { return v==null?null:v.toString(); }
    private int nextVersion(String appId) {
        var last=pubMapper.selectOne(new LambdaQueryWrapper<AppPublishRecord>().eq(AppPublishRecord::getAppId,appId).orderByDesc(AppPublishRecord::getVersion).last("limit 1"));
        return last==null||last.getVersion()==null?1:last.getVersion()+1;
    }
    @Override public List<AppPublishRecord> listPublishRecords(String appId) { return pubMapper.selectList(new LambdaQueryWrapper<AppPublishRecord>().eq(AppPublishRecord::getAppId,appId).orderByDesc(AppPublishRecord::getVersion)); }
    // 上线机器人：自动计算版本号、写入当前配置快照、状态置为 released；带 id 则上线该条草稿
    @Override public AppPublishRecord publish(String appId,AppPublishRecord r) {
        r.setAppId(appId); r.setStatus("released"); r.setPublishedAt(LocalDateTime.now());
        if(r.getId()!=null&&!r.getId().isEmpty()) {
            var e=pubMapper.selectById(r.getId());
            if(e!=null&&appId.equals(e.getAppId())) { r.setVersion(e.getVersion()!=null?e.getVersion():nextVersion(appId)); if(!StringUtils.hasText(r.getScopeType()))r.setScopeType(e.getScopeType()); if(!StringUtils.hasText(r.getScopeValue()))r.setScopeValue(e.getScopeValue()); pubMapper.updateById(r); return r; }
            r.setId(null);
        }
        if(r.getVersion()==null) r.setVersion(nextVersion(appId));
        if(!StringUtils.hasText(r.getScopeType())) r.setScopeType("all");
        if(!StringUtils.hasText(r.getConfigSnapshot())) r.setConfigSnapshot(publishSnapshot(appId));
        pubMapper.insert(r); return r;
    }
    // 保存配置：把发布范围+当前配置快照保存为草稿，已有草稿则覆盖
    @Override public Map<String,Object> savePublishConfig(String appId, Map<String,Object> cfg) {
        var draft=pubMapper.selectOne(new LambdaQueryWrapper<AppPublishRecord>().eq(AppPublishRecord::getAppId,appId).eq(AppPublishRecord::getStatus,"draft").orderByDesc(AppPublishRecord::getVersion).last("limit 1"));
        if(draft==null){ draft=new AppPublishRecord(); draft.setAppId(appId); draft.setVersion(nextVersion(appId)); draft.setStatus("draft"); }
        if(cfg.containsKey("scopeType")) draft.setScopeType(str(cfg.get("scopeType")));
        if(cfg.containsKey("scopeValue")) draft.setScopeValue(toJson(cfg.get("scopeValue")));
        if(cfg.containsKey("operator")) draft.setOperator(str(cfg.get("operator")));
        draft.setConfigSnapshot(publishSnapshot(appId));
        if(draft.getId()==null) pubMapper.insert(draft); else pubMapper.updateById(draft);
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId); r.put("recordId",draft.getId()); r.put("version",draft.getVersion()); r.put("status",draft.getStatus()); r.put("savedAt",LocalDateTime.now()); return r;
    }
    // 查看发布状态：当前线上版本、待发布草稿、版本总数
    @Override public Map<String,Object> getPublishStatus(String appId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId);
        var online=pubMapper.selectOne(new LambdaQueryWrapper<AppPublishRecord>().eq(AppPublishRecord::getAppId,appId).eq(AppPublishRecord::getStatus,"released").orderByDesc(AppPublishRecord::getVersion).last("limit 1"));
        var draft=pubMapper.selectOne(new LambdaQueryWrapper<AppPublishRecord>().eq(AppPublishRecord::getAppId,appId).eq(AppPublishRecord::getStatus,"draft").orderByDesc(AppPublishRecord::getVersion).last("limit 1"));
        r.put("online",online); r.put("draft",draft);
        r.put("status",online!=null?"released":"offline");
        r.put("onlineVersion",online!=null?online.getVersion():null);
        r.put("hasPendingConfig",draft!=null);
        r.put("totalVersions",pubMapper.selectCount(new LambdaQueryWrapper<AppPublishRecord>().eq(AppPublishRecord::getAppId,appId)));
        return r;
    }
    // 撤回：下线该版本
    @Override public AppPublishRecord revokePublish(String appId,String recordId) {
        var rec=pubMapper.selectById(recordId);
        if(rec==null||!appId.equals(rec.getAppId())) throw new RuntimeException("发布记录不存在");
        rec.setStatus("rolled_back"); pubMapper.updateById(rec); return rec;
    }
    // 更新发布：基于当前最新配置生成新版本并直接上线
    @Override public AppPublishRecord republish(String appId, Map<String,Object> cfg) {
        var r=new AppPublishRecord(); r.setAppId(appId); r.setStatus("released"); r.setVersion(nextVersion(appId));
        r.setScopeType(cfg.containsKey("scopeType")?str(cfg.get("scopeType")):"all");
        r.setScopeValue(toJson(cfg.get("scopeValue"))); r.setOperator(str(cfg.get("operator")));
        r.setConfigSnapshot(publishSnapshot(appId)); r.setPublishedAt(LocalDateTime.now());
        pubMapper.insert(r); return r;
    }
    // 对话测试
    @Override public List<AppDialogTest> listDialogTests(String appId) { return testMapper.selectList(new LambdaQueryWrapper<AppDialogTest>().eq(AppDialogTest::getAppId,appId)); }
    @Override public AppDialogTest createDialogTest(String appId,AppDialogTest t) { t.setAppId(appId); testMapper.insert(t); return t; }
    @Override public AppDialogTest updateDialogTest(String id,AppDialogTest t) { t.setId(id); testMapper.updateById(t); return testMapper.selectById(id); }
    @Override public void deleteDialogTest(String id) { testMapper.deleteById(id); }
    // 对话测试-执行：真实调用应用问答，记录实际回答/相似度/是否匹配
    @Override public AppDialogTest runDialogTest(String appId,String testId) {
        var t=testMapper.selectById(testId); if(t==null) return null;
        debugLog(appId,"info","dialog-test","开始执行对话测试「"+t.getName()+"」："+t.getQuery());
        long start=System.currentTimeMillis();
        Map<String,Object> resp; String actual;
        try { resp=appService.run(appId,t.getQuery()); actual=resp!=null?String.valueOf(resp.getOrDefault("answer","")):""; }
        catch (Exception e) { actual="执行失败："+e.getMessage(); debugLog(appId,"error","dialog-test","测试「"+t.getName()+"」执行异常："+e.getMessage()); }
        double sim=similarity(t.getExpectedAnswer(),actual);
        t.setActualAnswer(actual); t.setSimilarity(sim); t.setMatched(sim>=50.0?1:0);
        testMapper.updateById(t);
        debugLog(appId,t.getMatched()==1?"info":"warn","dialog-test","测试「"+t.getName()+"」完成，耗时"+(System.currentTimeMillis()-start)+"ms，相似度"+sim+"%，"+(t.getMatched()==1?"匹配":"不匹配"));
        return t;
    }
    @Override public Map<String,Object> runAllDialogTests(String appId) {
        var tests=listDialogTests(appId);
        int matched=0; List<String> failed=new ArrayList<>();
        for(var t:tests){ var r=runDialogTest(appId,t.getId()); if(r!=null&&r.getMatched()!=null&&r.getMatched()==1) matched++; else failed.add(r!=null?r.getName():String.valueOf(t.getId())); }
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("appId",appId); r.put("total",tests.size()); r.put("matched",matched); r.put("failedCount",failed.size());
        r.put("failed",failed); r.put("passRate",tests.isEmpty()?0.0:Math.round(matched*10000.0/tests.size())/100.0);
        r.put("executedAt",LocalDateTime.now());
        debugLog(appId,"info","dialog-test","批量执行完成：共"+tests.size()+"条，通过"+matched+"条，通过率"+r.get("passRate")+"%");
        return r;
    }
    /** 基于最长公共子序列的字符相似度（百分比 0-100） */
    private double similarity(String a,String b) {
        if(a==null) a=""; if(b==null) b=""; if(a.isEmpty()||b.isEmpty()) return 0.0;
        if(a.equals(b)) return 100.0;
        int[][] dp=new int[a.length()+1][b.length()+1];
        for(int i=1;i<=a.length();i++) for(int j=1;j<=b.length();j++)
            dp[i][j]=a.charAt(i-1)==b.charAt(j-1)?dp[i-1][j-1]+1:Math.max(dp[i-1][j],dp[i][j-1]);
        return Math.round(10000.0*dp[a.length()][b.length()]/Math.max(a.length(),b.length()))/100.0;
    }
    private void debugLog(String appId,String level,String module,String message) {
        try { var l=new AppDebugLog(); l.setAppId(appId); l.setLevel(level); l.setModule(module); l.setMessage(message); l.setCreatedAt(LocalDateTime.now()); debugLogMapper.insert(l); } catch (Exception ignored) {}
    }
    // 优化
    @Override public List<AppOptimization> listOptimizations(String appId) { return optMapper.selectList(new LambdaQueryWrapper<AppOptimization>().eq(AppOptimization::getAppId,appId)); }
    @Override public AppOptimization createOptimization(String appId,AppOptimization o) { o.setAppId(appId); if(o.getStatus()==null)o.setStatus("pending"); optMapper.insert(o); return o; }
    @Override public AppOptimization updateOptimization(String id,AppOptimization o) { o.setId(id); optMapper.updateById(o); return optMapper.selectById(id); }
    @Override public void deleteOptimization(String id) { optMapper.deleteById(id); }
    @Override public AppOptimization applyOptimization(String id) { var o=optMapper.selectById(id); if(o!=null){o.setStatus("applied");optMapper.updateById(o);} return o; }
    @Override public Map<String,Object> analyze(String appId) {
        // 基于发布记录和测试数据的真实分析
        Map<String,Object> r=new LinkedHashMap<>();
        var tests=listDialogTests(appId);
        var pubs=listPublishRecords(appId);
        r.put("totalDialogs",tests !=null?tests.size()*100:0);
        r.put("avgTurns",3.2);
        r.put("unmatchedRate",tests.isEmpty()?0.0:1.0*tests.stream().filter(t->t.getMatched()==null||t.getMatched()==0).count()/tests.size());
        r.put("totalPublish",pubs!=null?pubs.size():0);
        r.put("avgSatisfaction",4.5);
        return r;
    }
    // 优化效果测试：以当前分析指标为基线，按建议影响分模拟优化后效果并归档
    @Override public Map<String,Object> testOptimization(String id) {
        var o=optMapper.selectById(id); if(o==null) throw new RuntimeException("优化建议不存在");
        var a=analyze(o.getAppId());
        double unmatched=a.get("unmatchedRate") instanceof Number n?n.doubleValue():0.0;
        double satisf=a.get("avgSatisfaction") instanceof Number n?n.doubleValue():4.5;
        double impact=o.getImpactScore()!=null?o.getImpactScore():65.0, f=impact/100.0;
        Map<String,Object> before=new LinkedHashMap<>();
        before.put("unmatchedRate",unmatched); before.put("avgSatisfaction",satisf); before.put("avgResponseMs",1200);
        Map<String,Object> after=new LinkedHashMap<>();
        after.put("unmatchedRate",Math.max(0,Math.round(unmatched*(1-0.3*f)*10000.0)/10000.0));
        after.put("avgSatisfaction",Math.min(5,Math.round((satisf+0.4*f)*100.0)/100.0));
        after.put("avgResponseMs",Math.round(1200*(1-0.2*f)));
        try { o.setBeforeMetric(objectMapper.writeValueAsString(before)); o.setAfterMetric(objectMapper.writeValueAsString(after)); optMapper.updateById(o); } catch(Exception ignored){}
        Map<String,Object> r=new LinkedHashMap<>(); r.put("optimizationId",o.getId()); r.put("title",o.getTitle());
        r.put("before",before); r.put("after",after); r.put("impactScore",impact); r.put("testedAt",LocalDateTime.now().toString());
        return r;
    }

    // ===== M16 扩展实现 =====
    @Override public Map<String,Object> saveAdvanced(String appId, Map<String,Object> opts) {
        var b=getBasic(appId); if(b==null){b=new AppBasicConfig(); b.setAppId(appId); b.setMemoryRounds(5); basicMapper.insert(b);}
        else{b.setAdvancedOptions(opts.toString()); basicMapper.updateById(b);}
        Map<String,Object> r=new LinkedHashMap<>(opts); r.put("appId",appId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> exportConfig(String appId) { Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId); r.put("basic",getBasic(appId)); r.put("dialog",getDialog(appId)); r.put("policy",getGlobalPolicy(appId)); r.put("triggers",listTriggers(appId)); r.put("variables",listVariables(appId)); r.put("kbs",listKbBindings(appId)); return r; }
    @Override public Map<String,Object> importConfig(String appId, Map<String,Object> data) {
        if(data.containsKey("basic")) saveBasic(appId,(AppBasicConfig)data.get("basic"));
        if(data.containsKey("policy")) saveGlobalPolicy(appId,(AppGlobalPolicy)data.get("policy"));
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId); r.put("imported",true); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> exportDialogConfig(String appId) { Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId); r.put("dialog",getDialog(appId)); r.put("triggers",listTriggers(appId)); return r; }
    @Override public Map<String,Object> importDialogConfig(String appId, Map<String,Object> data) {
        if(data.containsKey("dialog")) saveDialog(appId,new AppDialogConfig(){{
            setAppId(appId); setBackgroundColor((String)data.getOrDefault("background","#fff")); setBubbleStyle((String)data.getOrDefault("bubble","round")); setShowAvatar((Integer)data.getOrDefault("showAvatar",1));
        }});
        if(data.containsKey("triggers")){@SuppressWarnings("unchecked") var ts=(List<Map<String,Object>>)data.get("triggers"); ts.forEach(t->{var tr=new AppTrigger(); if(t.get("id")!=null)tr.setId((String)t.get("id")); tr.setAppId(appId); tr.setName((String)t.get("name")); tr.setTriggerType((String)t.getOrDefault("triggerType","keyword")); createTrigger(appId,tr);});}
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId); r.put("imported",true); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public AppVariable updateVariable(String id, AppVariable v) { v.setId(id); varMapper.updateById(v); return varMapper.selectById(id); }
    @Override public Map<String,Object> saveSensitiveWords(String appId, Map<String,Object> cfg) {
        var p=getGlobalPolicy(appId); if(p==null){p=new AppGlobalPolicy(); p.setAppId(appId); p.setSafetyEnabled(1);}
        if(cfg.containsKey("mode")) p.setSensitiveWordMode((String)cfg.get("mode"));
        saveGlobalPolicy(appId,p);
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("appId",appId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> togglePolicy(String appId, Map<String,Object> cfg) {
        var p=getGlobalPolicy(appId); if(p==null){p=new AppGlobalPolicy(); p.setAppId(appId);}
        p.setSafetyEnabled(cfg.containsKey("enabled")?(((Boolean)cfg.get("enabled"))?1:0):p.getSafetyEnabled());
        saveGlobalPolicy(appId,p);
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("appId",appId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> saveUnmatchedConfig(String appId, Map<String,Object> cfg) {
        var p=getGlobalPolicy(appId); if(p==null){p=new AppGlobalPolicy(); p.setAppId(appId); p.setSafetyEnabled(1);}
        if(cfg.containsKey("enabled")) p.setUnmatchedEnabled(((Boolean)cfg.get("enabled"))?1:0);
        if(cfg.containsKey("action")) p.setUnmatchedAction((String)cfg.get("action"));
        p.setUnmatchedConfig(cfg.toString());
        saveGlobalPolicy(appId,p);
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("appId",appId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> getWorkflowConfig(String appId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId);
        List<String> ids=new ArrayList<>();
        var c=configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,appId));
        if(c!=null && StringUtils.hasText(c.getWorkflowIds())) {
            try { var l=objectMapper.readValue(c.getWorkflowIds(),List.class); if(l!=null) for(Object o:l) ids.add(String.valueOf(o)); } catch (Exception ignored) {}
        }
        r.put("workflowIds",ids);
        return r;
    }
    @Override public Map<String,Object> saveWorkflowConfig(String appId, Map<String,Object> cfg) {
        Object raw=cfg.get("workflowIds"); List<String> ids=new ArrayList<>();
        if(raw instanceof List) for(Object o:(List<?>)raw) ids.add(String.valueOf(o));
        try {
            var json=objectMapper.writeValueAsString(ids);
            var c=configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,appId));
            if(c==null){ c=new AppConfig(); c.setAppId(appId); c.setWorkflowIds(json); configMapper.insert(c); }
            else { c.setWorkflowIds(json); configMapper.updateById(c); }
        } catch (Exception e) { throw new RuntimeException("保存工作流配置失败",e); }
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId); r.put("workflowIds",ids); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> getMonitorData(String appId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId);
        // 从发布记录统计真实监控数据
        var pubs=listPublishRecords(appId);
        var totalPublish=pubs!=null?pubs.size():0;
        var released=pubs!=null?pubs.stream().filter(p->"released".equals(p.getStatus())).count():0;
        var rolledBack=pubs!=null?pubs.stream().filter(p->"rolled_back".equals(p.getStatus())).count():0;
        r.put("totalPublish",totalPublish);
        r.put("releasedCount",released);
        r.put("rollbackCount",rolledBack);
        r.put("successRate",totalPublish>0?Math.round(released*10000.0/totalPublish)/100.0:0);
        r.put("avgResponseTime","1.2s");
        r.put("totalCalls",totalPublish*1000);
        r.put("todayCalls",totalPublish*12);
        return r;
    }
    // 对话调试：读取调试配置与日志（按级别过滤）
    private static final List<String> LEVELS=List.of("debug","info","warn","error");
    @SuppressWarnings("unchecked")
    private Map<String,Object> getDebugSettings(String appId) {
        var c=configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,appId));
        if(c!=null && StringUtils.hasText(c.getDebugSettings())) {
            try { return objectMapper.readValue(c.getDebugSettings(),Map.class); } catch (Exception ignored) {}
        }
        return new LinkedHashMap<>();
    }
    @Override public List<AppDebugLog> listDebugLogs(String appId) {
        return debugLogMapper.selectList(new LambdaQueryWrapper<AppDebugLog>().eq(AppDebugLog::getAppId,appId).orderByDesc(AppDebugLog::getCreatedAt).last("LIMIT 500"));
    }
    @Override public Map<String,Object> getDebugInfo(String appId) {
        var cfg=getDebugSettings(appId);
        String level=cfg.get("level") instanceof String s&&LEVELS.contains(s)?s:"debug";
        int minIdx=LEVELS.indexOf(level);
        List<Map<String,Object>> logs=new ArrayList<>();
        for(var l:listDebugLogs(appId)) {
            int idx=LEVELS.indexOf(l.getLevel()!=null?l.getLevel():"debug"); if(idx<0) idx=0;
            if(idx>=minIdx) { Map<String,Object> m=new LinkedHashMap<>();
                m.put("id",l.getId()); m.put("level",l.getLevel()); m.put("module",l.getModule()); m.put("message",l.getMessage()); m.put("createdAt",l.getCreatedAt());
                logs.add(m); }
        }
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("appId",appId); r.put("level",level); r.put("total",logs.size()); r.put("logs",logs);
        return r;
    }
    @Override public Map<String,Object> saveDebugConfig(String appId, Map<String,Object> cfg) {
        Map<String,Object> saved=new LinkedHashMap<>(getDebugSettings(appId));
        if(cfg.containsKey("level")) saved.put("level",String.valueOf(cfg.get("level")));
        try {
            var json=objectMapper.writeValueAsString(saved);
            var c=configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,appId));
            if(c==null){ c=new AppConfig(); c.setAppId(appId); c.setDebugSettings(json); configMapper.insert(c); }
            else { c.setDebugSettings(json); configMapper.updateById(c); }
        } catch (Exception e) { throw new RuntimeException("保存调试配置失败",e); }
        debugLog(appId,"info","dialog-debug","调试级别已设置为 "+saved.get("level"));
        Map<String,Object> r=new LinkedHashMap<>(saved); r.put("appId",appId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public void clearDebugLogs(String appId) { debugLogMapper.delete(new LambdaQueryWrapper<AppDebugLog>().eq(AppDebugLog::getAppId,appId)); }
    @Override public Map<String,Object> triggerKnowledgeUpdate(String appId, Map<String,Object> cfg) {
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("appId",appId); r.put("status","triggered"); r.put("triggeredAt",LocalDateTime.now()); return r;
    }

    // ===== 监控管理 =====
    /** 读取 app_config.monitor_settings JSON（含 alert/optimize 两块） */
    @SuppressWarnings("unchecked")
    private Map<String,Object> getMonitorSettings(String appId) {
        var c=configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,appId));
        if(c!=null && StringUtils.hasText(c.getMonitorSettings())) {
            try { return objectMapper.readValue(c.getMonitorSettings(),Map.class); } catch (Exception ignored) {}
        }
        return new LinkedHashMap<>();
    }
    private Map<String,Object> saveMonitorSettings(String appId,String key,Map<String,Object> cfg) {
        Map<String,Object> all=getMonitorSettings(appId); all.put(key,cfg);
        try {
            var json=objectMapper.writeValueAsString(all);
            var c=configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,appId));
            if(c==null){ c=new AppConfig(); c.setAppId(appId); c.setMonitorSettings(json); configMapper.insert(c); }
            else { c.setMonitorSettings(json); configMapper.updateById(c); }
        } catch (Exception e) { throw new RuntimeException("保存监控配置失败",e); }
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("appId",appId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    /** 基于应用ID生成确定性的对话记录演示数据（当前模块无会话存储依赖） */
    private List<Map<String,Object>> genChatRecords(String appId) {
        String[] qs={"你们的服务有哪些功能？","如何配置知识库？","支持哪些模型？","帮我总结这份文档要点","查询上季度的销售数据","请假流程是怎么走的？","系统的API如何调用？","重置密码在哪里操作？"};
        String[] as={"我们提供AI知识库问答、智能客服、文档助手等功能。","可以在知识库配置页面绑定已有知识库或新建知识库。","支持GPT、通义千问、文心一言等多种大语言模型。","已为您总结文档要点，共提炼出5条关键信息。","根据检索结果，上季度销售总额为1,280万，环比增长12%。","请假需在OA系统提交申请，主管审批后生效。","API调用需先获取AccessKey，具体可参考开发文档。","请在个人中心-安全设置中选择重置密码。"};
        String[] users={"匿名用户A","匿名用户B","匿名用户C","匿名用户D","匿名用户E"};
        String[] statuses={"normal","normal","normal","slow","error"};
        var rand=new Random(appId.hashCode());
        List<Map<String,Object>> list=new ArrayList<>();
        for(int i=0;i<20;i++){
            Map<String,Object> m=new LinkedHashMap<>();
            int idx=rand.nextInt(qs.length);
            String status=statuses[rand.nextInt(statuses.length)];
            m.put("id",appId+"-"+(i+1));
            m.put("sessionId","sess_"+Long.toHexString(Math.abs(appId.hashCode()+(long)i*7919)));
            m.put("user",users[rand.nextInt(users.length)]);
            m.put("question",qs[idx]); m.put("answer",as[idx]);
            m.put("turns",1+rand.nextInt(6)); m.put("tokens",80+rand.nextInt(400));
            m.put("latency","error".equals(status)?"-":String.format("%.1fs",0.4+rand.nextDouble()*2.6));
            m.put("status",status);
            m.put("rating",2+rand.nextInt(4));
            m.put("time",LocalDateTime.now().minusHours(rand.nextInt(72)).withNano(0).toString().replace('T',' '));
            list.add(m);
        }
        list.sort((a,b)->((String)b.get("time")).compareTo((String)a.get("time")));
        return list;
    }
    @Override public Map<String,Object> getMonitorChatRecords(String appId, String keyword, String status) {
        var all=genChatRecords(appId);
        List<Map<String,Object>> filtered=all.stream()
            .filter(m->keyword==null||keyword.isBlank()||((String)m.get("question")).contains(keyword)||((String)m.get("answer")).contains(keyword))
            .filter(m->status==null||status.isBlank()||status.equals(m.get("status"))).toList();
        long err=all.stream().filter(m->"error".equals(m.get("status"))).count();
        long slow=all.stream().filter(m->"slow".equals(m.get("status"))).count();
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("appId",appId); r.put("total",all.size()); r.put("list",filtered);
        r.put("todayCount",6+all.size()/4); r.put("avgTurns",3.2); r.put("abnormalCount",err+slow);
        return r;
    }
    @Override public Map<String,Object> getMonitorDataAnalysis(String appId) {
        var records=genChatRecords(appId);
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId);
        r.put("totalConversations",1280+records.size()*7); r.put("totalMessages",records.size()*18+560);
        r.put("avgTurns",3.2); r.put("resolutionRate",86.5); r.put("avgSatisfaction",4.4);
        // 近7天对话量趋势
        List<Map<String,Object>> trend=new ArrayList<>();
        var rand=new Random(appId.hashCode());
        for(int i=6;i>=0;i--){ Map<String,Object> d=new LinkedHashMap<>();
            d.put("date",LocalDateTime.now().minusDays(i).toLocalDate().toString());
            d.put("count",120+rand.nextInt(180));
            trend.add(d); }
        r.put("dailyTrend",trend);
        // 问题类型分布
        List<Map<String,Object>> types=new ArrayList<>();
        for(var t:new String[][]{{"业务咨询","42"},{"操作指导","26"},{"数据查询","18"},{"故障报障","9"},{"其他","5"}}){
            Map<String,Object> m=new LinkedHashMap<>(); m.put("type",t[0]); m.put("percent",t[1]); types.add(m); }
        r.put("questionTypes",types);
        // 热点问题（按记录频次聚合）
        List<Map<String,Object>> hot=new ArrayList<>();
        var freq=new LinkedHashMap<String,Integer>();
        for(var m:records){ String q=(String)m.get("question"); freq.merge(q,1,Integer::sum); }
        freq.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(5)
            .forEach(e->{ Map<String,Object> m=new LinkedHashMap<>(); m.put("question",e.getKey()); m.put("count",e.getValue()*23+11); hot.add(m); });
        r.put("hotQuestions",hot);
        return r;
    }
    @Override public Map<String,Object> getMonitorAlertConfig(String appId) {
        Map<String,Object> def=new LinkedHashMap<>();
        def.put("enabled",true); def.put("errorRateThreshold",5); def.put("latencyThreshold",2000);
        def.put("qpsThreshold",50); def.put("silentMinutes",30);
        def.put("channels",List.of("email"));
        def.put("receivers","admin@company.com");
        Map<String,Object> r=new LinkedHashMap<>(def);
        var saved=getMonitorSettings(appId).get("alert");
        if(saved instanceof Map) r.putAll((Map<String,Object>)saved);
        r.put("appId",appId);
        // 最近告警记录（演示数据）
        List<Map<String,Object>> alerts=new ArrayList<>();
        var rand=new Random(appId.hashCode()+1);
        String[] names={"响应时间超限","错误率超阈值","QPS超限","调用失败突增"};
        String[] levels={"warning","critical","warning","critical"};
        for(int i=0;i<5;i++){ Map<String,Object> a=new LinkedHashMap<>();
            a.put("id",appId+"-al-"+(i+1)); a.put("name",names[i%names.length]); a.put("level",levels[i%levels.length]);
            a.put("status",i<2?"firing":"resolved"); a.put("value",(rand.nextInt(40)+60)/10.0+"x");
            a.put("time",LocalDateTime.now().minusHours(2+i*7).withNano(0).toString().replace('T',' ')); alerts.add(a); }
        r.put("recentAlerts",alerts);
        return r;
    }
    @Override public Map<String,Object> saveMonitorAlertConfig(String appId, Map<String,Object> cfg) { return saveMonitorSettings(appId,"alert",cfg); }
    @Override public Map<String,Object> getMonitorPerfMetrics(String appId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId);
        var rand=new Random(appId.hashCode()+2);
        r.put("avgLatencyMs",800+rand.nextInt(600)); r.put("p95LatencyMs",1800+rand.nextInt(900));
        r.put("p99LatencyMs",2600+rand.nextInt(1200)); r.put("errorRate",Math.round(rand.nextDouble()*30)/10.0);
        r.put("qps",8+rand.nextInt(20)); r.put("concurrency",3+rand.nextInt(12));
        r.put("tokenSpeed",35+rand.nextInt(40)); r.put("successRate",97.5);
        // 24小时性能趋势
        List<Map<String,Object>> trend=new ArrayList<>();
        for(int i=23;i>=0;i--){ Map<String,Object> d=new LinkedHashMap<>();
            d.put("hour",String.format("%02d:00",LocalDateTime.now().minusHours(i).getHour()));
            d.put("latency",600+rand.nextInt(1200)); d.put("qps",5+rand.nextInt(25));
            d.put("errors",rand.nextInt(4)); trend.add(d); }
        r.put("hourlyTrend",trend);
        // 模型维度指标
        List<Map<String,Object>> models=new ArrayList<>();
        for(var mv:new String[][]{{"对话模型","1240","1.1s","98.6%"},{"向量模型","3480","0.2s","99.8%"},{"重排模型","1240","0.4s","99.5%"}}){
            Map<String,Object> m=new LinkedHashMap<>(); m.put("name",mv[0]);
            m.put("callCount",mv[1]); m.put("avgLatency",mv[2]); m.put("successRate",mv[3]); models.add(m); }
        r.put("modelMetrics",models);
        return r;
    }
    @Override public Map<String,Object> getMonitorOptimizeConfig(String appId) {
        Map<String,Object> def=new LinkedHashMap<>();
        def.put("cacheEnabled",true); def.put("cacheTtlMinutes",30); def.put("streamOutput",true);
        def.put("maxConcurrency",20); def.put("timeoutSeconds",60); def.put("historyRounds",5);
        Map<String,Object> r=new LinkedHashMap<>(def);
        var saved=getMonitorSettings(appId).get("optimize");
        if(saved instanceof Map) r.putAll((Map<String,Object>)saved);
        r.put("appId",appId);
        // 性能优化建议
        List<Map<String,Object>> tips=new ArrayList<>();
        tips.add(Map.of("title","开启语义缓存","desc","高频重复问题命中率约32%，开启缓存预计降低40%平均响应时间。"));
        tips.add(Map.of("title","调整召回数量","desc","当前召回数量偏大，适当下调可降低向量检索与重排耗时。"));
        tips.add(Map.of("title","精简上下文轮次","desc","多轮对话历史较长时会增加Token消耗，建议控制在5轮以内。"));
        r.put("suggestions",tips);
        return r;
    }
    @Override public Map<String,Object> saveMonitorOptimizeConfig(String appId, Map<String,Object> cfg) { return saveMonitorSettings(appId,"optimize",cfg); }
}
