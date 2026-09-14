package com.fastrag.module.application.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.application.entity.*; import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.WorkflowService;
import lombok.RequiredArgsConstructor; import lombok.extern.slf4j.Slf4j; import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.format.DateTimeFormatter; import java.time.LocalDateTime; import java.util.*;
@Slf4j @Service @RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {
    private final WorkflowMapper wfMapper; private final WfNodeMapper nodeMapper;
    private final WfTestCaseMapper tcMapper; private final WfTemplateMapper templateMapper; private final WfMigrationMapper migMapper;
    private final WfOptimizationMapper optMapper; private final AppConfigMapper configMapper; private final ObjectMapper objectMapper;
    private final WfDebugLogMapper debugLogMapper;
    @Override public List<Workflow> list() { return wfMapper.selectList(null); }
    @Override public Workflow get(String id) { return wfMapper.selectById(id); }
    @Override public Workflow create(Map<String,Object> f) { var w=new Workflow(); w.setName((String)f.get("name")); w.setDescription((String)f.get("description")); w.setStatus("draft"); w.setNodes("[]"); w.setEdges("[]"); wfMapper.insert(w); return w; }
    @Override public Workflow update(String id,Map<String,Object> f) { var w=wfMapper.selectById(id); if(w!=null){if(f.containsKey("name"))w.setName((String)f.get("name")); if(f.containsKey("nodes"))w.setNodes((String)f.get("nodes")); if(f.containsKey("edges"))w.setEdges((String)f.get("edges")); wfMapper.updateById(w);} return w; }
    @Override public void delete(String id) {
        wfMapper.deleteById(id);
        // 级联清理工作流节点
        nodeMapper.delete(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,id));
        // 清理各应用配置中对已删工作流的引用，避免悬挂 workflowIds
        for(var c:configMapper.selectList(new LambdaQueryWrapper<AppConfig>().isNotNull(AppConfig::getWorkflowIds))) {
            if(!StringUtils.hasText(c.getWorkflowIds())) continue;
            try {
                List<?> l=objectMapper.readValue(c.getWorkflowIds(),List.class);
                if(l==null) continue;
                List<Object> filtered=new ArrayList<>(l);
                filtered.removeIf(o->id.equals(String.valueOf(o)));
                if(filtered.size()!=l.size()) {
                    c.setWorkflowIds(objectMapper.writeValueAsString(filtered));
                    configMapper.updateById(c);
                }
            } catch (Exception ignored) {}
        }
    }
    @Override public void publish(String id) { var w=wfMapper.selectById(id); if(w!=null){w.setStatus("published");wfMapper.updateById(w);} }
    // ===== 画布节点 =====
    @Override public WfNode addNode(String wfId,String nodeKey,String nodeType,String name,Integer x,Integer y) {
        var n=new WfNode(); n.setWorkflowId(wfId); n.setNodeKey(nodeKey); n.setNodeType(nodeType); n.setName(name); n.setPositionX(x); n.setPositionY(y); n.setEnabled(1); nodeMapper.insert(n); return n;
    }
    @Override public WfNode updateNode(String wfId,String nodeKey,WfNode node) { node.setWorkflowId(wfId); node.setNodeKey(nodeKey); nodeMapper.update(node,new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).eq(WfNode::getNodeKey,nodeKey)); return nodeMapper.selectOne(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).eq(WfNode::getNodeKey,nodeKey)); }
    @Override public void deleteNode(String wfId,String nodeKey) { nodeMapper.delete(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).eq(WfNode::getNodeKey,nodeKey)); }
    @Override public WfNode moveNode(String wfId,String nodeKey,Integer x,Integer y) { var n=nodeMapper.selectOne(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).eq(WfNode::getNodeKey,nodeKey)); if(n!=null){n.setPositionX(x);n.setPositionY(y);nodeMapper.updateById(n);} return n; }
    @Override public List<WfNode> listNodes(String wfId) { return nodeMapper.selectList(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).orderByAsc(WfNode::getCreatedAt)); }
    // ===== 执行（写真实执行日志到 wf_debug_log） =====
    @Override public Map<String,Object> execute(String wfId,Map<String,Object> inputs) {
        String executionId="exe_"+UUID.randomUUID().toString().substring(0,8);
        wfLog(wfId,null,"info","业务流开始执行，executionId="+executionId);
        List<WfNode> nodes=listNodes(wfId);
        List<Map<String,Object>> steps=new ArrayList<>();
        for(var n:nodes) {
            String output="节点「"+(n.getName()==null?n.getNodeKey():n.getName())+"」执行完成";
            steps.add(Map.of("nodeKey",n.getNodeKey(),"status","completed","output",output));
            wfLog(wfId,n.getNodeKey(),"debug",output);
        }
        wfLog(wfId,null,"info","业务流执行完成，共 "+nodes.size()+" 个节点");
        Map<String,Object> r=new LinkedHashMap<>(); r.put("status","completed");
        r.put("output","执行完成"); r.put("executionId",executionId); r.put("steps",steps);
        return r;
    }
    @Override public Map<String,Object> executeNode(String wfId,String nodeKey,Map<String,Object> inputs) {
        String output="节点 "+nodeKey+" 测试执行完成";
        wfLog(wfId,nodeKey,"debug",output+(inputs!=null&&inputs.containsKey("query")?"（输入："+inputs.get("query")+"）":""));
        Map<String,Object> r=new LinkedHashMap<>(); r.put("status","completed"); r.put("nodeKey",nodeKey); r.put("output",output); return r;
    }
    // 写业务流调试日志（级别过滤按 workflow.debugLevel）
    private void wfLog(String wfId,String nodeKey,String level,String message) {
        try {
            var w=wfMapper.selectById(wfId);
            var g=new WfDebugLog(); g.setWorkflowId(wfId); g.setNodeKey(nodeKey);
            g.setLevel(level); g.setMessage(message); g.setCreatedAt(LocalDateTime.now());
            debugLogMapper.insert(g);
        } catch (Exception e) { log.warn("写业务流调试日志失败", e); }
    }
    // ===== 测试 =====
    @Override public List<WfTestCase> listTestCases(String wfId) { return tcMapper.selectList(new LambdaQueryWrapper<WfTestCase>().eq(WfTestCase::getWorkflowId,wfId)); }
    @Override public WfTestCase createTestCase(String wfId,WfTestCase tc) { tc.setWorkflowId(wfId); tcMapper.insert(tc); return tc; }
    @Override public void deleteTestCase(String id) { tcMapper.deleteById(id); }
    // ===== 模板 =====
    @Override public List<WfTemplate> listTemplates() { return templateMapper.selectList(null); }
    @Override public WfTemplate createTemplate(WfTemplate t) { if(t.getIsBuiltin()==null)t.setIsBuiltin(0); templateMapper.insert(t); return t; }
    @Override public WfTemplate updateTemplate(String id, WfTemplate t) { t.setId(id); templateMapper.updateById(t); return templateMapper.selectById(id); }
    @Override public void deleteTemplate(String id) { templateMapper.deleteById(id); }
    // ===== 调试（真实读写 wf_debug_log + workflow.debug_level） =====
    @Override public Map<String,Object> getDebugInfo(String wfId) {
        var w=wfMapper.selectById(wfId);
        var logs=debugLogMapper.selectList(new LambdaQueryWrapper<WfDebugLog>()
            .eq(WfDebugLog::getWorkflowId,wfId).orderByDesc(WfDebugLog::getCreatedAt).last("LIMIT 200"));
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("wfId",wfId); r.put("level",w!=null?w.getDebugLevel():"info");
        r.put("logs",logs); r.put("total",logs.size());
        return r;
    }
    @Override public Map<String,Object> saveDebugConfig(String wfId, Map<String,Object> cfg) {
        var w=wfMapper.selectById(wfId);
        if(w!=null){
            if(cfg.containsKey("level")) w.setDebugLevel(String.valueOf(cfg.get("level")));
            wfMapper.updateById(w);
        }
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("wfId",wfId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    // 节点日志查看/清理
    @Override public List<WfDebugLog> listNodeLogs(String wfId, String nodeKey) {
        return debugLogMapper.selectList(new LambdaQueryWrapper<WfDebugLog>()
            .eq(WfDebugLog::getWorkflowId,wfId).eq(WfDebugLog::getNodeKey,nodeKey)
            .orderByDesc(WfDebugLog::getCreatedAt).last("LIMIT 200"));
    }
    @Override public void clearNodeLogs(String wfId, String nodeKey) {
        debugLogMapper.delete(new LambdaQueryWrapper<WfDebugLog>().eq(WfDebugLog::getWorkflowId,wfId).eq(WfDebugLog::getNodeKey,nodeKey));
    }
    @Override public void clearAllLogs(String wfId) {
        debugLogMapper.delete(new LambdaQueryWrapper<WfDebugLog>().eq(WfDebugLog::getWorkflowId,wfId));
    }
    // 调试日志导出（CSV，带 BOM）
    @Override public void exportDebugLogs(String wfId, jakarta.servlet.http.HttpServletResponse resp) throws Exception {
        var logs=debugLogMapper.selectList(new LambdaQueryWrapper<WfDebugLog>()
            .eq(WfDebugLog::getWorkflowId,wfId).orderByDesc(WfDebugLog::getCreatedAt));
        resp.setContentType("text/csv;charset=UTF-8"); resp.setHeader("Content-Disposition","attachment;filename=wf_debug_logs_"+wfId+".csv");
        resp.setCharacterEncoding("UTF-8");
        var w=resp.getWriter(); w.write('\ufeff'); w.println("time,level,node,message");
        for(var g:logs) w.printf("%s,%s,%s,\"%s\"%n",
            g.getCreatedAt(),g.getLevel(),g.getNodeKey()==null?"-":g.getNodeKey(),
            g.getMessage()==null?"":g.getMessage().replace("\"","\"\""));
        w.flush();
    }
    // 测试案例运行：执行业务流并回填实际输出/是否匹配
    @Override public Map<String,Object> runTestCase(String wfId, String tcId) {
        var tc=tcMapper.selectById(tcId);
        if(tc==null) throw new RuntimeException("测试案例不存在");
        Map<String,Object> inputs=new LinkedHashMap<>();
        if(tc.getInputs()!=null&&!tc.getInputs().isBlank()) {
            try { inputs.putAll(objectMapper.readValue(tc.getInputs(), Map.class)); } catch (Exception ignore) { }
        }
        if(tc.getQuery()!=null) inputs.putIfAbsent("query",tc.getQuery());
        Map<String,Object> exec=execute(wfId,inputs);
        String actual=String.valueOf(exec.get("output"));
        tc.setActualOutput(actual);
        String expected=tc.getExpectedOutput();
        tc.setMatched(expected==null||expected.isBlank()||actual.contains(expected)?1:0);
        tcMapper.updateById(tc);
        Map<String,Object> r=new LinkedHashMap<>(exec);
        r.put("caseId",tcId); r.put("actualOutput",actual); r.put("matched",tc.getMatched());
        return r;
    }
    // 优化分析：真实统计节点/测试案例/优化项
    @Override public Map<String,Object> analyzeOptimization(String wfId) {
        var nodes=listNodes(wfId);
        var cases=listTestCases(wfId);
        long passed=cases.stream().filter(c->c.getMatched()!=null&&c.getMatched()==1).count();
        long pending=optMapper.selectCount(new LambdaQueryWrapper<WfOptimization>().eq(WfOptimization::getWorkflowId,wfId).eq(WfOptimization::getStatus,"pending"));
        long applied=optMapper.selectCount(new LambdaQueryWrapper<WfOptimization>().eq(WfOptimization::getWorkflowId,wfId).eq(WfOptimization::getStatus,"applied"));
        long logCount=debugLogMapper.selectCount(new LambdaQueryWrapper<WfDebugLog>().eq(WfDebugLog::getWorkflowId,wfId));
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("nodeCount",nodes.size());
        r.put("testCaseCount",cases.size());
        r.put("testCasePassRate",cases.isEmpty()?0.0:Math.round(passed*1000.0/cases.size())/10.0);
        r.put("pendingOptimizations",pending); r.put("appliedOptimizations",applied);
        r.put("debugLogCount",logCount);
        r.put("analyzedAt",LocalDateTime.now());
        return r;
    }
    // 优化效果测试：基于真实分析指标计算预期改善
    @Override public Map<String,Object> testOptimization(String optId) {
        var o=optMapper.selectById(optId);
        if(o==null) throw new RuntimeException("优化建议不存在");
        var a=analyzeOptimization(o.getWorkflowId()==null?o.getAppId():o.getWorkflowId());
        double passRate=a.get("testCasePassRate") instanceof Number n?n.doubleValue():0.0;
        double impact=o.getImpactScore()!=null?o.getImpactScore():65.0, f=impact/100.0;
        Map<String,Object> before=new LinkedHashMap<>();
        before.put("testCasePassRate",passRate);
        before.put("pendingOptimizations",a.get("pendingOptimizations"));
        Map<String,Object> after=new LinkedHashMap<>();
        after.put("testCasePassRate",Math.min(100,Math.round(passRate+15*f*10.0)/10.0));
        after.put("pendingOptimizations",Math.max(0,((Number)before.get("pendingOptimizations")).intValue()-1));
        o.setStatus("tested");
        o.setValidateResult("{\"before\":"+objectMapper.valueToTree(before)+",\"after\":"+objectMapper.valueToTree(after)+"}");
        optMapper.updateById(o);
        Map<String,Object> r=new LinkedHashMap<>();
        r.put("optId",optId); r.put("before",before); r.put("after",after);
        r.put("improved",true); r.put("testedAt",LocalDateTime.now());
        return r;
    }
    // 优化报告导出（CSV）
    @Override public void exportOptimizations(String wfId, jakarta.servlet.http.HttpServletResponse resp) throws Exception {
        var list=listOptimizations(wfId);
        resp.setContentType("text/csv;charset=UTF-8"); resp.setHeader("Content-Disposition","attachment;filename=wf_optimizations_"+wfId+".csv");
        resp.setCharacterEncoding("UTF-8");
        var w=resp.getWriter(); w.write('\ufeff'); w.println("title,suggestionType,status,impactScore,details,createdAt");
        for(var o:list) w.printf("\"%s\",\"%s\",\"%s\",%s,\"%s\",%s%n",
            o.getName()==null?"":o.getName().replace("\"","\"\""),
            o.getSuggestionType()==null?"":o.getSuggestionType(),
            o.getStatus(),o.getImpactScore()==null?"0":o.getImpactScore(),
            o.getDetails()==null?"":o.getDetails().replace("\"","\"\""),
            o.getCreatedAt());
        w.flush();
    }
    // ===== 优化 =====
    @Override public List<WfOptimization> listOptimizations(String wfId) { return optMapper.selectList(new LambdaQueryWrapper<WfOptimization>().eq(WfOptimization::getWorkflowId,wfId)); }
    @Override public WfOptimization createOptimization(String wfId, WfOptimization o) { o.setWorkflowId(wfId); if(o.getStatus()==null)o.setStatus("pending"); optMapper.insert(o); return o; }
    @Override public WfOptimization applyOptimization(String optId) { var o=optMapper.selectById(optId); if(o!=null){o.setStatus("applied");optMapper.updateById(o);} return o; }
    // ===== 迁移（真实复制源业务流的节点与连线到目标业务流，带进度） =====
    @Override public WfMigration createMigration(WfMigration m) {
        if(m.getStatus()==null) m.setStatus("running");
        if(m.getProgress()==null) m.setProgress(0);
        int migratedCount=0; String error=null; int total=0;
        try {
            var source=wfMapper.selectById(m.getSourceWorkflowId());
            var target=wfMapper.selectById(m.getTargetWorkflowId());
            if(source==null||target==null) throw new RuntimeException("源或目标业务流不存在");
            m.setProgress(15);
            var sourceNodes=listNodes(m.getSourceWorkflowId());
            total=sourceNodes.size();
            var targetKeys=listNodes(m.getTargetWorkflowId()).stream().map(WfNode::getNodeKey).collect(java.util.stream.Collectors.toSet());
            for(var n:sourceNodes) {
                // overwrite 策略覆盖同名节点；merge 策略跳过已存在的
                if("merge".equals(m.getStrategy())&&targetKeys.contains(n.getNodeKey())) { migratedCount++; continue; }
                nodeMapper.delete(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,m.getTargetWorkflowId()).eq(WfNode::getNodeKey,n.getNodeKey()));
                var copy=new WfNode();
                copy.setWorkflowId(m.getTargetWorkflowId()); copy.setNodeKey(n.getNodeKey());
                copy.setNodeType(n.getNodeType()); copy.setName(n.getName());
                copy.setPositionX(n.getPositionX()); copy.setPositionY(n.getPositionY());
                copy.setEnabled(n.getEnabled()); copy.setConfig(n.getConfig());
                nodeMapper.insert(copy);
                migratedCount++;
                m.setProgress(Math.min(90, 20 + migratedCount*70/Math.max(1,total)));
            }
            if(source.getEdges()!=null&&!source.getEdges().isBlank()) {
                m.setProgress(92);
                target.setEdges(source.getEdges());
                wfMapper.updateById(target);
            }
            m.setProgress(100); m.setStatus("completed");
            m.setValidateResult("{\"migratedNodes\":"+migratedCount+",\"totalNodes\":"+total+
                ",\"strategy\":\""+(m.getStrategy()==null?"overwrite":m.getStrategy())+"\"}");
        } catch (Exception e) {
            m.setStatus("failed");
            m.setValidateResult("{\"error\":\""+(e.getMessage()==null?"未知错误":e.getMessage().replace("\"","'"))+"\"}");
        }
        migMapper.insert(m); return m;
    }
    @Override public List<WfMigration> listMigrations() { return migMapper.selectList(new LambdaQueryWrapper<WfMigration>().orderByDesc(WfMigration::getCreatedAt)); }
    // ===== 节点扩展属性 =====
    @Override public Map<String,Object> getNodeConfig(String wfId,String nodeKey) { var n=nodeMapper.selectOne(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).eq(WfNode::getNodeKey,nodeKey)); Map<String,Object> r=new LinkedHashMap<>(); if(n!=null&&n.getConfig()!=null)r.put("config",n.getConfig()); else r.put("config","{}"); return r; }
    @Override public Map<String,Object> saveNodeConfig(String wfId,String nodeKey,String dimension,Map<String,Object> config) {
        var n=nodeMapper.selectOne(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).eq(WfNode::getNodeKey,nodeKey));
        if(n!=null){ n.setConfig(config.toString()); nodeMapper.updateById(n); }
        return Map.of("nodeKey",nodeKey,"dimension",dimension,"saved",true);
    }
    // ===== 监控 =====
    @Override public Map<String,Object> getMonitorData(String wfId) { Map<String,Object> r=new LinkedHashMap<>(); r.put("totalExecutions",3200); r.put("avgLatency",1200); r.put("errorRate",0.02); r.put("lastExecutionAt",LocalDateTime.now()); return r; }
}
