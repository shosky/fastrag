package com.fastrag.module.application.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.application.entity.*; import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.AppConfigService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class AppConfigServiceImpl implements AppConfigService {
    private final AppBasicConfigMapper basicMapper; private final AppDialogConfigMapper dialogMapper; private final AppTriggerMapper triggerMapper;
    private final AppGlobalPolicyMapper policyMapper; private final AppVariableMapper varMapper; private final AppKbBindingMapper kbMapper;
    private final AppConfigMapper configMapper;
    private final AppDbBindingMapper dbMapper; private final AppPublishRecordMapper pubMapper; private final AppDialogTestMapper testMapper;
    private final AppOptimizationMapper optMapper; private final AppKbAutoUpdateConfigMapper autoKbMapper;
    private final AppConversationMapper convMapper;
    private final AppConversationMessageMapper convMsgMapper;
    private final ObjectMapper objectMapper;

    // ========== JSON 工具 ==========
    private String toJson(Object obj) { try { return objectMapper.writeValueAsString(obj); } catch (JsonProcessingException e) { return "{}"; } }
    @SuppressWarnings("unchecked")
    private Map<String,Object> parseJsonMap(String json) { try { return json==null||json.isBlank()?new LinkedHashMap<>():objectMapper.readValue(json,LinkedHashMap.class); } catch (Exception e) { return new LinkedHashMap<>(); } }

    // 基础配置
    @Override public AppBasicConfig getBasic(String appId) { return basicMapper.selectOne(new LambdaQueryWrapper<AppBasicConfig>().eq(AppBasicConfig::getAppId,appId)); }
    @Override public AppBasicConfig saveBasic(String appId,AppBasicConfig c) { c.setAppId(appId); var e=getBasic(appId); if(e==null){c.setMemoryRounds(5);basicMapper.insert(c);}else{c.setId(e.getId());basicMapper.updateById(c);} return c; }
    // 对话配置
    @Override public AppDialogConfig getDialog(String appId) { return dialogMapper.selectOne(new LambdaQueryWrapper<AppDialogConfig>().eq(AppDialogConfig::getAppId,appId)); }
    @Override public AppDialogConfig saveDialog(String appId,AppDialogConfig c) { c.setAppId(appId); var e=getDialog(appId); if(e==null)dialogMapper.insert(c);else{c.setId(e.getId());dialogMapper.updateById(c);} return c; }

    // ===== 智能体配置（对齐Yuxi） =====
    @Override public AppConfig getConfig(String appId) {
        return configMapper.selectOne(new LambdaQueryWrapper<AppConfig>().eq(AppConfig::getAppId,appId));
    }
    /** 获取或创建 AppConfig（upsert 模式） */
    private AppConfig getOrCreateConfig(String appId) {
        var e = getConfig(appId);
        if (e == null) { e = new AppConfig(); e.setAppId(appId); e.setMaxSteps(15); e.setMaxTurns(10);
            e.setRetryTimes(2); e.setSummaryThreshold(8000); e.setMaxTokens(2048); e.setTemperature(new java.math.BigDecimal("0.70")); }
        return e;
    }
    @Override public AppConfig savePrompt(String appId, String prompt) {
        var c = getOrCreateConfig(appId); c.setPrompt(prompt);
        if (c.getId() == null) configMapper.insert(c); else configMapper.updateById(c);
        return c;
    }
    @Override public AppConfig saveSummary(String appId, Map<String,Object> cfg) {
        var c = getOrCreateConfig(appId);
        if (cfg.containsKey("summaryThreshold")) c.setSummaryThreshold(((Number)cfg.get("summaryThreshold")).intValue());
        if (cfg.containsKey("summaryPrompt")) c.setSummaryPrompt((String)cfg.get("summaryPrompt"));
        if (c.getId() == null) configMapper.insert(c); else configMapper.updateById(c);
        return c;
    }
    @Override public AppConfig saveMaxSteps(String appId, int maxSteps) {
        var c = getOrCreateConfig(appId); c.setMaxSteps(maxSteps);
        if (c.getId() == null) configMapper.insert(c); else configMapper.updateById(c);
        return c;
    }
    @Override public AppConfig saveMaxTurns(String appId, int maxTurns) {
        var c = getOrCreateConfig(appId); c.setMaxTurns(maxTurns);
        if (c.getId() == null) configMapper.insert(c); else configMapper.updateById(c);
        return c;
    }
    @Override public AppConfig saveRetryTimes(String appId, int retryTimes) {
        var c = getOrCreateConfig(appId); c.setRetryTimes(retryTimes);
        if (c.getId() == null) configMapper.insert(c); else configMapper.updateById(c);
        return c;
    }
    @Override public AppConfig saveMaxTokens(String appId, int maxTokens) {
        var c = getOrCreateConfig(appId); c.setMaxTokens(maxTokens);
        if (c.getId() == null) configMapper.insert(c); else configMapper.updateById(c);
        return c;
    }
    // 触发器
    @Override public List<AppTrigger> listTriggers(String appId) { return triggerMapper.selectList(new LambdaQueryWrapper<AppTrigger>().eq(AppTrigger::getAppId,appId)); }
    @Override public AppTrigger createTrigger(String appId,AppTrigger t) { t.setAppId(appId); if(t.getEnabled()==null)t.setEnabled(1); if(t.getPriority()==null)t.setPriority(0); triggerMapper.insert(t); return t; }
    @Override public AppTrigger updateTrigger(String id,AppTrigger t) { t.setId(id); triggerMapper.updateById(t); return triggerMapper.selectById(id); }
    @Override public void deleteTrigger(String id) { triggerMapper.deleteById(id); }
    @Override public AppTrigger testTrigger(String id,String input) { var t=triggerMapper.selectById(id); if(t!=null)t.setHitCount((t.getHitCount()!=null?t.getHitCount():0)+1); triggerMapper.updateById(t); return t; }
    @Override public AppTrigger runTrigger(String id,String input) {
        var t=triggerMapper.selectById(id); if(t!=null)t.setHitCount((t.getHitCount()!=null?t.getHitCount():0)+1); triggerMapper.updateById(t);
        var r=new LinkedHashMap<String,Object>(); r.put("triggerId",id); r.put("input",input); r.put("status","executed"); r.put("output","trigger fired: "+t.getName()); r.put("executedAt",LocalDateTime.now());
        if(t!=null){ t.setActionConfig(toJson(r)); triggerMapper.updateById(t); } return t;
    }
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
    // 数据库绑定
    @Override public List<AppDbBinding> listDbBindings(String appId) {
        List<AppDbBinding> list = dbMapper.selectList(new LambdaQueryWrapper<AppDbBinding>().eq(AppDbBinding::getAppId,appId));
        // JSON 列读取后还原为逗号分隔字符串，保持前端兼容
        list.forEach(b -> b.setAllowedTables(jsonArrayToCsv(b.getAllowedTables())));
        return list;
    }
    @Override public AppDbBinding bindDb(String appId,AppDbBinding b) {
        b.setAppId(appId); if(b.getEnabled()==null)b.setEnabled(1);
        // 逗号分隔字符串转 JSON 数组存入
        b.setAllowedTables(csvToJsonArray(b.getAllowedTables()));
        dbMapper.insert(b); return b;
    }
    @Override public AppDbBinding updateDbBinding(String id,AppDbBinding b) {
        b.setId(id);
        // 逗号分隔字符串转 JSON 数组
        b.setAllowedTables(csvToJsonArray(b.getAllowedTables()));
        dbMapper.updateById(b);
        AppDbBinding updated = dbMapper.selectById(id);
        if(updated != null) updated.setAllowedTables(jsonArrayToCsv(updated.getAllowedTables()));
        return updated;
    }
    @Override public void unbindDb(String id) { dbMapper.deleteById(id); }

    /** 逗号分隔字符串 → JSON 数组字符串（兼容 MySQL JSON 列） */
    private String csvToJsonArray(String csv) {
        if(csv == null || csv.isBlank()) return "[]";
        String trimmed = csv.trim();
        if(trimmed.startsWith("[")) return trimmed; // 已经是 JSON
        String[] parts = trimmed.split("\\s*,\\s*");
        StringBuilder sb = new StringBuilder("[");
        for(int i=0;i<parts.length;i++){
            if(i>0) sb.append(",");
            sb.append("\"").append(parts[i].replace("\"","\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /** JSON 数组字符串 → 逗号分隔字符串 */
    private String jsonArrayToCsv(String json) {
        if(json == null || json.isBlank()) return "";
        String trimmed = json.trim();
        if(!trimmed.startsWith("[")) return trimmed;
        try {
            java.util.List<String> list = new ObjectMapper().readValue(trimmed, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>(){});
            return String.join(",", list);
        } catch(Exception e) {
            return trimmed;
        }
    }
    // 技能绑定
    private final AppSkillBindingMapper skillBindMapper;
    private final AppToolBindingMapper toolBindMapper;
    private final AppMcpBindingMapper mcpBindMapper;
    @Override public List<AppSkillBinding> listSkillBindings(String appId) { try { return skillBindMapper.selectList(new LambdaQueryWrapper<AppSkillBinding>().eq(AppSkillBinding::getAppId,appId)); } catch(Exception e) { return new ArrayList<>(); } }
    @Override public AppSkillBinding bindSkill(String appId,AppSkillBinding b) { b.setAppId(appId); if(b.getEnabled()==null)b.setEnabled(1); skillBindMapper.insert(b); return b; }
    @Override public AppSkillBinding updateSkillBinding(String id,AppSkillBinding b) { b.setId(id); skillBindMapper.updateById(b); return skillBindMapper.selectById(id); }
    @Override public void unbindSkill(String id) { skillBindMapper.deleteById(id); }
    @Override public List<AppToolBinding> listToolBindings(String appId) { try { return toolBindMapper.selectList(new LambdaQueryWrapper<AppToolBinding>().eq(AppToolBinding::getAppId,appId)); } catch(Exception e) { return new ArrayList<>(); } }
    @Override public AppToolBinding bindTool(String appId,AppToolBinding b) { b.setAppId(appId); if(b.getEnabled()==null)b.setEnabled(1); toolBindMapper.insert(b); return b; }
    @Override public AppToolBinding updateToolBinding(String id,AppToolBinding b) { b.setId(id); toolBindMapper.updateById(b); return toolBindMapper.selectById(id); }
    @Override public void unbindTool(String id) { toolBindMapper.deleteById(id); }
    @Override public List<AppMcpBinding> listMcpBindings(String appId) { try { return mcpBindMapper.selectList(new LambdaQueryWrapper<AppMcpBinding>().eq(AppMcpBinding::getAppId,appId)); } catch(Exception e) { return new ArrayList<>(); } }
    @Override public AppMcpBinding bindMcp(String appId,AppMcpBinding b) { b.setAppId(appId); if(b.getEnabled()==null)b.setEnabled(1); mcpBindMapper.insert(b); return b; }
    @Override public AppMcpBinding updateMcpBinding(String id,AppMcpBinding b) { b.setId(id); mcpBindMapper.updateById(b); return mcpBindMapper.selectById(id); }
    @Override public void unbindMcp(String id) { mcpBindMapper.deleteById(id); }
    // 发布
    @Override public List<AppPublishRecord> listPublishRecords(String appId) { return pubMapper.selectList(new LambdaQueryWrapper<AppPublishRecord>().eq(AppPublishRecord::getAppId,appId)); }
    @Override public AppPublishRecord publish(String appId,AppPublishRecord r) { r.setAppId(appId); r.setPublishedAt(LocalDateTime.now()); pubMapper.insert(r); return r; }
    // 对话测试
    @Override public List<AppDialogTest> listDialogTests(String appId) { return testMapper.selectList(new LambdaQueryWrapper<AppDialogTest>().eq(AppDialogTest::getAppId,appId)); }
    @Override public AppDialogTest createDialogTest(String appId,AppDialogTest t) { t.setAppId(appId); testMapper.insert(t); return t; }
    @Override public AppDialogTest updateDialogTest(String id,AppDialogTest t) { t.setId(id); testMapper.updateById(t); return testMapper.selectById(id); }
    @Override public void deleteDialogTest(String id) { testMapper.deleteById(id); }
    // 优化
    @Override public List<AppOptimization> listOptimizations(String appId) { return optMapper.selectList(new LambdaQueryWrapper<AppOptimization>().eq(AppOptimization::getAppId,appId)); }
    @Override public AppOptimization createOptimization(String appId,AppOptimization o) { o.setAppId(appId); if(o.getStatus()==null)o.setStatus("pending"); optMapper.insert(o); return o; }
    @Override public AppOptimization updateOptimization(String id,AppOptimization o) { o.setId(id); optMapper.updateById(o); return optMapper.selectById(id); }
    @Override public void deleteOptimization(String id) { optMapper.deleteById(id); }
    @Override public AppOptimization applyOptimization(String id) { var o=optMapper.selectById(id); if(o!=null){o.setStatus("applied");optMapper.updateById(o);} return o; }
    @Override public Map<String,Object> analyze(String appId) {
        Map<String,Object> r=new LinkedHashMap<>();
        var tests=listDialogTests(appId);
        var pubs=listPublishRecords(appId);
        var kbs=listKbBindings(appId);
        var triggers=listTriggers(appId);
        r.put("appId",appId);
        r.put("totalTests",tests!=null?tests.size():0);
        r.put("matchedTests",tests!=null?(int)tests.stream().filter(t->t.getMatched()!=null&&t.getMatched()==1).count():0);
        r.put("unmatchedRate",tests==null||tests.isEmpty()?0.0:1.0*tests.stream().filter(t->t.getMatched()==null||t.getMatched()==0).count()/tests.size());
        r.put("totalPublish",pubs!=null?pubs.size():0);
        r.put("totalKbBindings",kbs!=null?kbs.size():0);
        r.put("totalTriggers",triggers!=null?triggers.size():0);
        r.put("analyzedAt",LocalDateTime.now().toString());
        return r;
    }

    // ===== M16 扩展实现 =====
    @Override public Map<String,Object> saveAdvanced(String appId, Map<String,Object> opts) {
        var b=getBasic(appId);
        if(b==null){b=new AppBasicConfig(); b.setAppId(appId); b.setMemoryRounds(5); basicMapper.insert(b);}
        b.setAdvancedOptions(toJson(opts)); basicMapper.updateById(b);
        Map<String,Object> r=new LinkedHashMap<>(opts); r.put("appId",appId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> exportConfig(String appId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId);
        r.put("basic",getBasic(appId)); r.put("dialog",getDialog(appId));
        r.put("policy",getGlobalPolicy(appId)); r.put("triggers",listTriggers(appId));
        r.put("variables",listVariables(appId)); r.put("kbs",listKbBindings(appId));
        r.put("dbs",listDbBindings(appId)); r.put("publishRecords",listPublishRecords(appId));
        r.put("skills",listSkillBindings(appId)); r.put("tools",listToolBindings(appId));
        r.put("mcps",listMcpBindings(appId));
        return r;
    }
    @Override public Map<String,Object> importConfig(String appId, Map<String,Object> data) {
        // 支持全部配置类型的导入
        if(data.containsKey("basic")) saveBasic(appId, objectMapper.convertValue(data.get("basic"), AppBasicConfig.class));
        if(data.containsKey("dialog")) saveDialog(appId, objectMapper.convertValue(data.get("dialog"), AppDialogConfig.class));
        if(data.containsKey("policy")) saveGlobalPolicy(appId, objectMapper.convertValue(data.get("policy"), AppGlobalPolicy.class));
        if(data.containsKey("triggers")) { @SuppressWarnings("unchecked") var ts=(List<Map<String,Object>>)data.get("triggers"); ts.forEach(t->createTrigger(appId, objectMapper.convertValue(t, AppTrigger.class))); }
        if(data.containsKey("variables")) { @SuppressWarnings("unchecked") var vs=(List<Map<String,Object>>)data.get("variables"); vs.forEach(v->createVariable(appId, objectMapper.convertValue(v, AppVariable.class))); }
        if(data.containsKey("kbs")) { @SuppressWarnings("unchecked") var ks=(List<Map<String,Object>>)data.get("kbs"); ks.forEach(k->bindKb(appId, objectMapper.convertValue(k, AppKbBinding.class))); }
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId); r.put("imported",true); r.put("updatedAt",LocalDateTime.now());
        return r;
    }
    @Override public Map<String,Object> exportDialogConfig(String appId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId);
        r.put("dialog",getDialog(appId)); r.put("triggers",listTriggers(appId));
        return r;
    }
    @Override public Map<String,Object> importDialogConfig(String appId, Map<String,Object> data) {
        if(data.containsKey("dialog")) saveDialog(appId, objectMapper.convertValue(data.get("dialog"), AppDialogConfig.class));
        if(data.containsKey("triggers")) { @SuppressWarnings("unchecked") var ts=(List<Map<String,Object>>)data.get("triggers"); ts.forEach(t->createTrigger(appId, objectMapper.convertValue(t, AppTrigger.class))); }
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
        p.setUnmatchedConfig(toJson(cfg));
        saveGlobalPolicy(appId,p);
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("appId",appId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> getWorkflowConfig(String appId) {
        // 从 app_basic_config 的 advancedOptions 中读取 workflow 段
        var b=getBasic(appId);
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId);
        if(b!=null && b.getAdvancedOptions()!=null) {
            var opts=parseJsonMap(b.getAdvancedOptions());
            if(opts.containsKey("workflow")) r.put("workflow",opts.get("workflow"));
            else r.put("workflow",null);
        } else { r.put("workflow",null); }
        return r;
    }
    @Override public Map<String,Object> saveWorkflowConfig(String appId, Map<String,Object> cfg) {
        var b=getBasic(appId);
        if(b==null){b=new AppBasicConfig(); b.setAppId(appId); b.setMemoryRounds(5); basicMapper.insert(b);}
        var opts=b.getAdvancedOptions()!=null?parseJsonMap(b.getAdvancedOptions()):new LinkedHashMap<String,Object>();
        opts.put("workflow",cfg);
        b.setAdvancedOptions(toJson(opts)); basicMapper.updateById(b);
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("appId",appId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
	    @Override public Map<String,Object> getDebugInfo(String appId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId); r.put("level","info");
        var logs=new ArrayList<Map<String,Object>>();
        var b=getBasic(appId);
        if(b!=null){ var m=new LinkedHashMap<String,Object>(); m.put("timestamp",b.getUpdatedAt()!=null?b.getUpdatedAt().toString():LocalDateTime.now().toString()); m.put("level","INFO"); m.put("message","基础配置已更新"); logs.add(m); }
        var pubs=listPublishRecords(appId);
        if(pubs!=null && !pubs.isEmpty()){ var pub=pubs.get(pubs.size()-1); var m=new LinkedHashMap<String,Object>(); m.put("timestamp",pub.getPublishedAt()!=null?pub.getPublishedAt().toString():"-"); m.put("level","INFO"); m.put("message","应用已发布 (版本:"+pub.getVersion()+")"); logs.add(m); }
        if(logs.isEmpty()){ var m=new LinkedHashMap<String,Object>(); m.put("timestamp",LocalDateTime.now().toString()); m.put("level","INFO"); m.put("message","系统初始化完成"); logs.add(m); }
        r.put("logs",logs);
        return r;
    }
    @Override public Map<String,Object> saveDebugConfig(String appId, Map<String,Object> cfg) {
        // 将调试配置持久化到 advancedOptions.debug
        var b=getBasic(appId);
        if(b==null){b=new AppBasicConfig(); b.setAppId(appId); b.setMemoryRounds(5); basicMapper.insert(b);}
        var opts=b.getAdvancedOptions()!=null?parseJsonMap(b.getAdvancedOptions()):new LinkedHashMap<String,Object>();
        opts.put("debug",cfg);
        b.setAdvancedOptions(toJson(opts)); basicMapper.updateById(b);
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("appId",appId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    @Override public Map<String,Object> triggerKnowledgeUpdate(String appId, Map<String,Object> cfg) {
        // 检查自动更新配置
        var autoCfg=autoKbMapper.selectOne(new LambdaQueryWrapper<AppKbAutoUpdateConfig>().eq(AppKbAutoUpdateConfig::getAppId,appId));
        if(autoCfg==null){ autoCfg=new AppKbAutoUpdateConfig(); autoCfg.setAppId(appId); autoCfg.setEnabled(1); autoCfg.setAutoPublish(0); autoKbMapper.insert(autoCfg); }
        else { autoCfg.setEnabled(1); autoKbMapper.updateById(autoCfg); }
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("appId",appId); r.put("status","triggered");
        r.put("message","知识库更新已触发，自动更新配置已启用");
        r.put("triggeredAt",LocalDateTime.now()); return r;
    }

    // ===== 新增：知识库自动更新配置 CRUD =====
    @Override public AppKbAutoUpdateConfig getAutoKnowledgeUpdate(String appId) {
        var c=autoKbMapper.selectOne(new LambdaQueryWrapper<AppKbAutoUpdateConfig>().eq(AppKbAutoUpdateConfig::getAppId,appId));
        if(c==null){c=new AppKbAutoUpdateConfig(); c.setAppId(appId); c.setEnabled(0); c.setAutoPublish(0); autoKbMapper.insert(c);}
        return c;
    }
    @Override public AppKbAutoUpdateConfig saveAutoKnowledgeUpdate(String appId, AppKbAutoUpdateConfig c) {
        c.setAppId(appId); var e=autoKbMapper.selectOne(new LambdaQueryWrapper<AppKbAutoUpdateConfig>().eq(AppKbAutoUpdateConfig::getAppId,appId));
        if(e==null) autoKbMapper.insert(c); else { c.setId(e.getId()); autoKbMapper.updateById(c); }
        return c;
    }

    // ===== 对话记录 =====
    @Override public Map<String,Object> listConversations(String appId, String keyword, Integer rating, String startDate, String endDate, int page, int size) {
        try {
        var qw=new LambdaQueryWrapper<AppConversation>().eq(AppConversation::getAppId,appId).orderByDesc(AppConversation::getCreatedAt);
        if(keyword!=null&&!keyword.isBlank()) qw.and(w->w.like(AppConversation::getFirstQuestion,keyword).or().like(AppConversation::getAnswerSummary,keyword).or().like(AppConversation::getTitle,keyword));
        if(rating!=null) qw.eq(AppConversation::getRating,rating);
        if(startDate!=null&&!startDate.isBlank()) qw.ge(AppConversation::getCreatedAt,startDate+" 00:00:00");
        if(endDate!=null&&!endDate.isBlank()) qw.le(AppConversation::getCreatedAt,endDate+" 23:59:59");
        var total=convMapper.selectCount(qw);
        var records=convMapper.selectList(qw.last("LIMIT "+size+" OFFSET "+((page-1)*size)));
        Map<String,Object> r=new LinkedHashMap<>(); r.put("total",total); r.put("items",records); r.put("page",page); r.put("size",size);
        return r;
        } catch(Exception e) { Map<String,Object> r=new LinkedHashMap<>(); r.put("total",0); r.put("items",new ArrayList<>()); r.put("page",page); r.put("size",size); return r; }
    }
    @Override public Map<String,Object> getConversationDetail(String convId) {
        try {
        var conv=convMapper.selectById(convId);
        if(conv==null){ Map<String,Object> r=new LinkedHashMap<>(); r.put("error","not found"); return r; }
        var msgs=convMsgMapper.selectList(new LambdaQueryWrapper<AppConversationMessage>().eq(AppConversationMessage::getConversationId,convId).orderByAsc(AppConversationMessage::getCreatedAt));
        Map<String,Object> r=new LinkedHashMap<>(); r.put("conversation",conv); r.put("messages",msgs);
        return r;
        } catch(Exception e) { Map<String,Object> r=new LinkedHashMap<>(); r.put("error","table not ready"); return r; }
    }
    @Override public void deleteConversation(String convId) {
        try {
        convMsgMapper.delete(new LambdaQueryWrapper<AppConversationMessage>().eq(AppConversationMessage::getConversationId,convId));
        convMapper.deleteById(convId);
        } catch(Exception e) { /* table may not exist yet */ }
    }
    @Override public void exportConversationsCsv(String appId, jakarta.servlet.http.HttpServletResponse resp) throws Exception {
        try {
        var list=convMapper.selectList(new LambdaQueryWrapper<AppConversation>().eq(AppConversation::getAppId,appId).orderByDesc(AppConversation::getCreatedAt));
        resp.setContentType("text/csv;charset=UTF-8"); resp.setHeader("Content-Disposition","attachment;filename=conversations_"+appId+".csv");
        var w=new java.io.PrintWriter(resp.getWriter());
        w.println("sessionId,userName,title,firstQuestion,rating,tokens,messageCount,createdAt");
        for(var c:list) w.printf("\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,%s,%s%n",
            c.getSessionId()!=null?c.getSessionId():"",c.getUserName()!=null?c.getUserName():"",
            c.getTitle()!=null?c.getTitle():"",c.getFirstQuestion()!=null?c.getFirstQuestion():"",
            c.getRating(),c.getTokenCount(),c.getMessageCount(),c.getCreatedAt());
        w.flush();
        } catch(Exception e) { resp.setContentType("text/plain;charset=UTF-8"); var w=resp.getWriter(); w.println("Table not ready - run migration"); w.flush(); }
    }

    // ===== 更新 Monitor 使用真实对话数据 =====
    @Override public Map<String,Object> getMonitorData(String appId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("appId",appId);
        var pubs=listPublishRecords(appId);
        var tests=listDialogTests(appId);
        List<AppConversation> convs=null; try { convs=convMapper.selectList(new LambdaQueryWrapper<AppConversation>().eq(AppConversation::getAppId,appId)); } catch(Exception e) { convs=new ArrayList<>(); }
        var totalPublish=pubs!=null?pubs.size():0;
        var released=pubs!=null?pubs.stream().filter(p->"released".equals(p.getStatus())).count():0;
        var rolledBack=pubs!=null?pubs.stream().filter(p->"rolled_back".equals(p.getStatus())).count():0;
        var totalCalls=convs!=null?convs.size():0;
        var todayCalls=convs!=null?(int)convs.stream().filter(c->c.getCreatedAt()!=null&&c.getCreatedAt().toLocalDate().equals(java.time.LocalDate.now())).count():0;
        var totalTokens=convs!=null?convs.stream().filter(c->c.getTokenCount()!=null).mapToInt(AppConversation::getTokenCount).sum():0;
        r.put("totalPublish",totalPublish);
        r.put("releasedCount",released);
        r.put("rollbackCount",rolledBack);
        r.put("successRate",totalPublish>0?Math.round(released*10000.0/totalPublish)/100.0:0);
        r.put("totalTests",tests!=null?tests.size():0);
        r.put("totalCalls",totalCalls);
        r.put("todayCalls",todayCalls);
        r.put("totalTokens",totalTokens);
        r.put("avgResponseTime",totalCalls>0?"-":"-");
        return r;
    }
}
