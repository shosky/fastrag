package com.fastrag.module.application.service;
import com.fastrag.module.application.entity.*; import java.util.*;
public interface WorkflowService {
    // 基础
    List<Workflow> list(); Workflow get(String id); Workflow create(Map<String,Object> form); Workflow update(String id,Map<String,Object> form); void delete(String id); void publish(String id);
    // 画布节点管理
    WfNode addNode(String wfId,String nodeKey,String nodeType,String name,Integer x,Integer y);
    WfNode updateNode(String wfId,String nodeKey,WfNode node); void deleteNode(String wfId,String nodeKey);
    WfNode moveNode(String wfId,String nodeKey,Integer x,Integer y);
    List<WfNode> listNodes(String wfId);
    // 业务流执行
    Map<String,Object> execute(String wfId,Map<String,Object> inputs);
    Map<String,Object> executeNode(String wfId,String nodeKey,Map<String,Object> inputs);
    // 测试/调试
    List<WfTestCase> listTestCases(String wfId);
    WfTestCase createTestCase(String wfId,WfTestCase tc);
    void deleteTestCase(String id);
    // 配置模板
    List<WfTemplate> listTemplates(); WfTemplate createTemplate(WfTemplate t);
    WfTemplate updateTemplate(String id, WfTemplate t);
    void deleteTemplate(String id);
    // 调试
    Map<String,Object> getDebugInfo(String wfId);
    Map<String,Object> saveDebugConfig(String wfId, Map<String,Object> cfg);
    // 优化
    List<WfOptimization> listOptimizations(String wfId);
    WfOptimization createOptimization(String wfId, WfOptimization o);
    WfOptimization applyOptimization(String optId);
    // 配置迁移
    WfMigration createMigration(WfMigration m);
    List<WfMigration> listMigrations();
    // 节点扩展属性(统一通过JSON config管理)
    Map<String,Object> getNodeConfig(String wfId,String nodeKey);
    Map<String,Object> saveNodeConfig(String wfId,String nodeKey,String dimension,Map<String,Object> config);
    // 监控
    Map<String,Object> getMonitorData(String wfId);
}
