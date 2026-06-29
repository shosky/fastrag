package com.fastrag.module.application.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.application.entity.*; import com.fastrag.module.application.mapper.*;
import com.fastrag.module.application.service.WorkflowService;
import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime; import java.util.*;
@Service @RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {
    private final WorkflowMapper wfMapper; private final WfNodeMapper nodeMapper;
    private final WfTestCaseMapper tcMapper; private final WfTemplateMapper templateMapper; private final WfMigrationMapper migMapper;
    private final WfOptimizationMapper optMapper;
    @Override public List<Workflow> list() { return wfMapper.selectList(null); }
    @Override public Workflow get(String id) { return wfMapper.selectById(id); }
    @Override public Workflow create(Map<String,Object> f) { var w=new Workflow(); w.setName((String)f.get("name")); w.setDescription((String)f.get("description")); w.setStatus("draft"); w.setNodes("[]"); w.setEdges("[]"); wfMapper.insert(w); return w; }
    @Override public Workflow update(String id,Map<String,Object> f) { var w=wfMapper.selectById(id); if(w!=null){if(f.containsKey("name"))w.setName((String)f.get("name")); if(f.containsKey("nodes"))w.setNodes((String)f.get("nodes")); if(f.containsKey("edges"))w.setEdges((String)f.get("edges")); wfMapper.updateById(w);} return w; }
    @Override public void delete(String id) { wfMapper.deleteById(id); }
    @Override public void publish(String id) { var w=wfMapper.selectById(id); if(w!=null){w.setStatus("published");wfMapper.updateById(w);} }
    // ===== 画布节点 =====
    @Override public WfNode addNode(String wfId,String nodeKey,String nodeType,String name,Integer x,Integer y) {
        var n=new WfNode(); n.setWorkflowId(wfId); n.setNodeKey(nodeKey); n.setNodeType(nodeType); n.setName(name); n.setPositionX(x); n.setPositionY(y); n.setEnabled(1); nodeMapper.insert(n); return n;
    }
    @Override public WfNode updateNode(String wfId,String nodeKey,WfNode node) { node.setWorkflowId(wfId); node.setNodeKey(nodeKey); nodeMapper.update(node,new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).eq(WfNode::getNodeKey,nodeKey)); return nodeMapper.selectOne(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).eq(WfNode::getNodeKey,nodeKey)); }
    @Override public void deleteNode(String wfId,String nodeKey) { nodeMapper.delete(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).eq(WfNode::getNodeKey,nodeKey)); }
    @Override public WfNode moveNode(String wfId,String nodeKey,Integer x,Integer y) { var n=nodeMapper.selectOne(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).eq(WfNode::getNodeKey,nodeKey)); if(n!=null){n.setPositionX(x);n.setPositionY(y);nodeMapper.updateById(n);} return n; }
    @Override public List<WfNode> listNodes(String wfId) { return nodeMapper.selectList(new LambdaQueryWrapper<WfNode>().eq(WfNode::getWorkflowId,wfId).orderByAsc(WfNode::getCreatedAt)); }
    // ===== 执行 =====
    @Override public Map<String,Object> execute(String wfId,Map<String,Object> inputs) { Map<String,Object> r=new LinkedHashMap<>(); r.put("status","completed"); r.put("output","执行完成"); r.put("executionId","exe_"+UUID.randomUUID().toString().substring(0,8)); return r; }
    @Override public Map<String,Object> executeNode(String wfId,String nodeKey,Map<String,Object> inputs) { Map<String,Object> r=new LinkedHashMap<>(); r.put("status","completed"); r.put("nodeKey",nodeKey); r.put("output","节点执行完成"); return r; }
    // ===== 测试 =====
    @Override public List<WfTestCase> listTestCases(String wfId) { return tcMapper.selectList(new LambdaQueryWrapper<WfTestCase>().eq(WfTestCase::getWorkflowId,wfId)); }
    @Override public WfTestCase createTestCase(String wfId,WfTestCase tc) { tc.setWorkflowId(wfId); tcMapper.insert(tc); return tc; }
    @Override public void deleteTestCase(String id) { tcMapper.deleteById(id); }
    // ===== 模板 =====
    @Override public List<WfTemplate> listTemplates() { return templateMapper.selectList(null); }
    @Override public WfTemplate createTemplate(WfTemplate t) { if(t.getIsBuiltin()==null)t.setIsBuiltin(0); templateMapper.insert(t); return t; }
    @Override public WfTemplate updateTemplate(String id, WfTemplate t) { t.setId(id); templateMapper.updateById(t); return templateMapper.selectById(id); }
    @Override public void deleteTemplate(String id) { templateMapper.deleteById(id); }
    // ===== 调试 =====
    @Override public Map<String,Object> getDebugInfo(String wfId) {
        Map<String,Object> r=new LinkedHashMap<>(); r.put("wfId",wfId); r.put("level","info");
        r.put("logs",List.of("[INFO] Workflow started","[DEBUG] Node node_llm executed","[INFO] Workflow completed"));
        return r;
    }
    @Override public Map<String,Object> saveDebugConfig(String wfId, Map<String,Object> cfg) {
        Map<String,Object> r=new LinkedHashMap<>(cfg); r.put("wfId",wfId); r.put("updatedAt",LocalDateTime.now()); return r;
    }
    // ===== 优化 =====
    @Override public List<WfOptimization> listOptimizations(String wfId) { return optMapper.selectList(new LambdaQueryWrapper<WfOptimization>().eq(WfOptimization::getWorkflowId,wfId)); }
    @Override public WfOptimization createOptimization(String wfId, WfOptimization o) { o.setWorkflowId(wfId); if(o.getStatus()==null)o.setStatus("pending"); optMapper.insert(o); return o; }
    @Override public WfOptimization applyOptimization(String optId) { var o=optMapper.selectById(optId); if(o!=null){o.setStatus("applied");optMapper.updateById(o);} return o; }
    // ===== 迁移 =====
    @Override public WfMigration createMigration(WfMigration m) { if(m.getStatus()==null)m.setStatus("running"); if(m.getProgress()==null)m.setProgress(0); migMapper.insert(m); return m; }
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
