package com.fastrag.module.application.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.application.entity.*;
import com.fastrag.module.application.service.AppConfigService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/apps") @RequiredArgsConstructor
public class AppConfigController {
    private final AppConfigService svc;
    // ===== 基础配置 =====
    @GetMapping("/{appId}/basic") public ApiResponse<?> basic(@PathVariable String appId) { return ApiResponse.success(svc.getBasic(appId)); }
    @PutMapping("/{appId}/basic") public ApiResponse<?> saveBasic(@PathVariable String appId,@RequestBody AppBasicConfig c) { return ApiResponse.success(svc.saveBasic(appId,c)); }
    @PutMapping("/{appId}/basic/memory") public ApiResponse<?> memory(@PathVariable String appId,@RequestBody Map<String,Integer> b) { var c=svc.getBasic(appId);if(c==null)c=new AppBasicConfig();c.setMemoryRounds(b.getOrDefault("memoryRounds",5)); return ApiResponse.success(svc.saveBasic(appId,c)); }
    @PutMapping("/{appId}/basic/output-format") public ApiResponse<?> output(@PathVariable String appId,@RequestBody Map<String,String> b) { var c=svc.getBasic(appId);if(c==null)c=new AppBasicConfig();c.setOutputFormat(b.getOrDefault("outputFormat","markdown")); return ApiResponse.success(svc.saveBasic(appId,c)); }
    // ===== 对话配置 =====
    @GetMapping("/{appId}/dialog") public ApiResponse<?> dialog(@PathVariable String appId) { return ApiResponse.success(svc.getDialog(appId)); }
    @PutMapping("/{appId}/dialog/background") public ApiResponse<?> dialogBkg(@PathVariable String appId,@RequestBody AppDialogConfig c) { return ApiResponse.success(svc.saveDialog(appId,c)); }
    // 触发器
    @GetMapping("/{appId}/triggers") public ApiResponse<?> triggers(@PathVariable String appId) { return ApiResponse.success(svc.listTriggers(appId)); }
    @PostMapping("/{appId}/triggers") public ApiResponse<?> createTrigger(@PathVariable String appId,@RequestBody AppTrigger t) { return ApiResponse.success(svc.createTrigger(appId,t)); }
    @PutMapping("/{appId}/triggers/{id}") public ApiResponse<?> updateTrigger(@PathVariable String id,@RequestBody AppTrigger t) { return ApiResponse.success(svc.updateTrigger(id,t)); }
    @PostMapping("/{appId}/triggers/{id}/test") public ApiResponse<?> testTrigger(@PathVariable String id,@RequestBody Map<String,String> b) { return ApiResponse.success(svc.testTrigger(id,b.get("input"))); }
    @PostMapping("/{appId}/triggers/{id}/run") public ApiResponse<?> runTrigger(@PathVariable String id,@RequestBody Map<String,String> b) { return ApiResponse.success(svc.testTrigger(id,b.get("input"))); }
    @DeleteMapping("/{appId}/triggers/{id}") public ApiResponse<?> deleteTrigger(@PathVariable String id) { svc.deleteTrigger(id); return ApiResponse.success(); }
    // ===== 全局策略 =====
    @GetMapping("/{appId}/global-policy") public ApiResponse<?> policy(@PathVariable String appId) { return ApiResponse.success(svc.getGlobalPolicy(appId)); }
    @PutMapping("/{appId}/global-policy/safety") public ApiResponse<?> safety(@PathVariable String appId,@RequestBody AppGlobalPolicy p) { return ApiResponse.success(svc.saveGlobalPolicy(appId,p)); }
    @PutMapping("/{appId}/global-policy/fallback") public ApiResponse<?> fallback(@PathVariable String appId,@RequestBody AppGlobalPolicy p) { return ApiResponse.success(svc.saveGlobalPolicy(appId,p)); }
    @GetMapping("/{appId}/global-policy/variables") public ApiResponse<?> vars(@PathVariable String appId) { return ApiResponse.success(svc.listVariables(appId)); }
    @PostMapping("/{appId}/global-policy/variables") public ApiResponse<?> createVar(@PathVariable String appId,@RequestBody AppVariable v) { return ApiResponse.success(svc.createVariable(appId,v)); }
    @DeleteMapping("/{appId}/global-policy/variables/{id}") public ApiResponse<?> deleteVar(@PathVariable String id) { svc.deleteVariable(id); return ApiResponse.success(); }
    // ===== 知识库绑定 =====
    @GetMapping("/{appId}/knowledge-bases") public ApiResponse<?> kbs(@PathVariable String appId) { return ApiResponse.success(svc.listKbBindings(appId)); }
    @PostMapping("/{appId}/knowledge-bases") public ApiResponse<?> bindKb(@PathVariable String appId,@RequestBody AppKbBinding b) { return ApiResponse.success(svc.bindKb(appId,b)); }
    @DeleteMapping("/{appId}/knowledge-bases/{id}") public ApiResponse<?> unbindKb(@PathVariable String id) { svc.unbindKb(id); return ApiResponse.success(); }
    // ===== 知识库配置（应用管理）：导出/导入/检索设置 =====
    @GetMapping("/{appId}/knowledge-bases/export") public ApiResponse<?> exportKbs(@PathVariable String appId) { return ApiResponse.success(svc.exportKbBindings(appId)); }
    @PostMapping("/{appId}/knowledge-bases/import") public ApiResponse<?> importKbs(@PathVariable String appId,@RequestBody Map<String,Object> data) { return ApiResponse.success(svc.importKbBindings(appId,data)); }
    @GetMapping("/{appId}/knowledge-bases/settings") public ApiResponse<?> getKbSettings(@PathVariable String appId) { return ApiResponse.success(svc.getKbSettings(appId)); }
    @PutMapping("/{appId}/knowledge-bases/settings") public ApiResponse<?> saveKbSettings(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveKbSettings(appId,cfg)); }
    // ===== 数据库绑定 =====
    @GetMapping("/{appId}/databases") public ApiResponse<?> dbs(@PathVariable String appId) { return ApiResponse.success(svc.listDbBindings(appId)); }
    @PostMapping("/{appId}/databases") public ApiResponse<?> bindDb(@PathVariable String appId, @RequestBody AppDbBinding b) { return ApiResponse.success(svc.bindDb(appId,b)); }
    @PutMapping("/{appId}/databases/{id}") public ApiResponse<?> updateDb(@PathVariable String id, @RequestBody AppDbBinding b) { return ApiResponse.success(svc.updateDbBinding(id,b)); }
    @DeleteMapping("/{appId}/databases/{id}") public ApiResponse<?> unbindDb(@PathVariable String id) { svc.unbindDb(id); return ApiResponse.success(); }
    // ===== 发布管理：保存配置 / 上线机器人 / 发布范围 / 查看状态 / 更新发布 =====
    @GetMapping("/{appId}/publish/records") public ApiResponse<?> pubRecords(@PathVariable String appId) { return ApiResponse.success(svc.listPublishRecords(appId)); }
    @PostMapping("/{appId}/publish/online") public ApiResponse<?> pubOnline(@PathVariable String appId,@RequestBody AppPublishRecord r) { return ApiResponse.success(svc.publish(appId,r)); }
    @PostMapping("/{appId}/publish/config") public ApiResponse<?> savePubCfg(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.savePublishConfig(appId,cfg)); }
    @GetMapping("/{appId}/publish/status") public ApiResponse<?> pubStatus(@PathVariable String appId) { return ApiResponse.success(svc.getPublishStatus(appId)); }
    @PostMapping("/{appId}/publish/{recordId}/revoke") public ApiResponse<?> pubRevoke(@PathVariable String appId,@PathVariable String recordId) { return ApiResponse.success(svc.revokePublish(appId,recordId)); }
    @PostMapping("/{appId}/publish/republish") public ApiResponse<?> pubRepublish(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.republish(appId,cfg)); }
    // ===== 对话测试 =====
    @GetMapping("/{appId}/dialog-tests") public ApiResponse<?> tests(@PathVariable String appId) { return ApiResponse.success(svc.listDialogTests(appId)); }
    @PostMapping("/{appId}/dialog-tests") public ApiResponse<?> createTest(@PathVariable String appId,@RequestBody AppDialogTest t) { return ApiResponse.success(svc.createDialogTest(appId,t)); }
    @PutMapping("/{appId}/dialog-tests/{id}") public ApiResponse<?> updateTest(@PathVariable String id,@RequestBody AppDialogTest t) { return ApiResponse.success(svc.updateDialogTest(id,t)); }
    @DeleteMapping("/{appId}/dialog-tests/{id}") public ApiResponse<?> deleteTest(@PathVariable String id) { svc.deleteDialogTest(id); return ApiResponse.success(); }
    // 进行对话测试：执行单条 / 批量执行全部
    @PostMapping("/{appId}/dialog-tests/{id}/run") public ApiResponse<?> runTest(@PathVariable String appId,@PathVariable String id) { return ApiResponse.success(svc.runDialogTest(appId,id)); }
    @PostMapping("/{appId}/dialog-tests/run-all") public ApiResponse<?> runAllTests(@PathVariable String appId) { return ApiResponse.success(svc.runAllDialogTests(appId)); }
    @GetMapping("/{appId}/dialog-tests/export") public void exportTests(@PathVariable String appId, HttpServletResponse resp) throws Exception {
        var list=svc.listDialogTests(appId);
        resp.setContentType("text/csv;charset=UTF-8"); resp.setHeader("Content-Disposition","attachment;filename=test_report.csv");
        var w=new java.io.PrintWriter(resp.getWriter()); w.println("name,query,expected,actual,matched,similarity,createdAt");
        for(var t:list) w.printf("\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,%s%n",t.getName()!=null?t.getName():"",t.getQuery()!=null?t.getQuery():"",t.getExpectedAnswer()!=null?t.getExpectedAnswer():"",t.getActualAnswer()!=null?t.getActualAnswer():"",t.getMatched(),t.getSimilarity(),t.getCreatedAt());
        w.flush();
    }
    // ===== 对话优化 =====
    @GetMapping("/{appId}/optimizations") public ApiResponse<?> opts(@PathVariable String appId) { return ApiResponse.success(svc.listOptimizations(appId)); }
    @PostMapping("/{appId}/optimizations") public ApiResponse<?> createOpt(@PathVariable String appId,@RequestBody AppOptimization o) { return ApiResponse.success(svc.createOptimization(appId,o)); }
    @PutMapping("/{appId}/optimizations/{id}") public ApiResponse<?> updateOpt(@PathVariable String id,@RequestBody AppOptimization o) { return ApiResponse.success(svc.updateOptimization(id,o)); }
    @DeleteMapping("/{appId}/optimizations/{id}") public ApiResponse<?> deleteOpt(@PathVariable String id) { svc.deleteOptimization(id); return ApiResponse.success(); }
    @PostMapping("/{appId}/optimizations/{id}/apply") public ApiResponse<?> applyOpt(@PathVariable String id) { return ApiResponse.success(svc.applyOptimization(id)); }
    @PostMapping("/{appId}/optimizations/{id}/test") public ApiResponse<?> testOpt(@PathVariable String id) { return ApiResponse.success(svc.testOptimization(id)); }
    @PostMapping("/{appId}/optimization/analyze") public ApiResponse<?> analyze(@PathVariable String appId) { return ApiResponse.success(svc.analyze(appId)); }
    @GetMapping("/{appId}/optimizations/export") public void exportOpts(@PathVariable String appId, HttpServletResponse resp) throws Exception {
        var list=svc.listOptimizations(appId);
        resp.setContentType("text/csv;charset=UTF-8"); resp.setHeader("Content-Disposition","attachment;filename=optimization_report.csv");
        var w=new java.io.PrintWriter(resp.getWriter()); w.println("title,suggestionType,description,status,impactScore,beforeMetric,afterMetric,createdAt");
        for(var o:list) w.printf("\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\",\"%s\",%s%n",
            o.getTitle()!=null?o.getTitle():"",o.getSuggestionType()!=null?o.getSuggestionType():"",
            o.getDescription()!=null?o.getDescription():"",o.getStatus()!=null?o.getStatus():"",
            o.getImpactScore(),o.getBeforeMetric()!=null?o.getBeforeMetric():"",o.getAfterMetric()!=null?o.getAfterMetric():"",o.getCreatedAt());
        w.flush();
    }

    // ===== M16 扩展：高级选项 =====
    @PutMapping("/{appId}/basic/advanced") public ApiResponse<?> advanced(@PathVariable String appId,@RequestBody Map<String,Object> opts) { return ApiResponse.success(svc.saveAdvanced(appId,opts)); }
    @GetMapping("/{appId}/basic/export") public ApiResponse<?> exportBasic(@PathVariable String appId) { return ApiResponse.success(svc.exportConfig(appId)); }
    @PostMapping("/{appId}/basic/import") public ApiResponse<?> importBasic(@PathVariable String appId,@RequestBody Map<String,Object> data) { return ApiResponse.success(svc.importConfig(appId,data)); }
    // 对话配置导出/导入
    @GetMapping("/{appId}/dialog/export") public ApiResponse<?> exportDialog(@PathVariable String appId) { return ApiResponse.success(svc.exportDialogConfig(appId)); }
    @PostMapping("/{appId}/dialog/import") public ApiResponse<?> importDialog(@PathVariable String appId,@RequestBody Map<String,Object> data) { return ApiResponse.success(svc.importDialogConfig(appId,data)); }
    // 变量更新
    @PutMapping("/{appId}/global-policy/variables/{id}") public ApiResponse<?> updateVar(@PathVariable String id,@RequestBody AppVariable v) { return ApiResponse.success(svc.updateVariable(id,v)); }
    // 敏感词
    @PutMapping("/{appId}/global-policy/sensitive-words") public ApiResponse<?> sensitiveWords(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveSensitiveWords(appId,cfg)); }
    // 启用禁用
    @PostMapping("/{appId}/global-policy/toggle") public ApiResponse<?> togglePolicy(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.togglePolicy(appId,cfg)); }
    // 未匹配策略
    @PutMapping("/{appId}/global-policy/unmatched") public ApiResponse<?> unmatched(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveUnmatchedConfig(appId,cfg)); }
    // 工作流配置
    @GetMapping("/{appId}/workflow-config") public ApiResponse<?> wfConfig(@PathVariable String appId) { return ApiResponse.success(svc.getWorkflowConfig(appId)); }
    @PostMapping("/{appId}/workflow-config") public ApiResponse<?> saveWfConfig(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveWorkflowConfig(appId,cfg)); }
    // 监控管理
    @GetMapping("/{appId}/monitor") public ApiResponse<?> monitor(@PathVariable String appId) { return ApiResponse.success(svc.getMonitorData(appId)); }
    @GetMapping("/{appId}/monitor/chat-records") public ApiResponse<?> monitorChatRecords(@PathVariable String appId,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status) { return ApiResponse.success(svc.getMonitorChatRecords(appId,keyword,status)); }
    @GetMapping("/{appId}/monitor/data-analysis") public ApiResponse<?> monitorDataAnalysis(@PathVariable String appId) { return ApiResponse.success(svc.getMonitorDataAnalysis(appId)); }
    @GetMapping("/{appId}/monitor/alert-config") public ApiResponse<?> monitorAlertConfig(@PathVariable String appId) { return ApiResponse.success(svc.getMonitorAlertConfig(appId)); }
    @PutMapping("/{appId}/monitor/alert-config") public ApiResponse<?> saveMonitorAlertConfig(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveMonitorAlertConfig(appId,cfg)); }
    @GetMapping("/{appId}/monitor/perf-metrics") public ApiResponse<?> monitorPerfMetrics(@PathVariable String appId) { return ApiResponse.success(svc.getMonitorPerfMetrics(appId)); }
    @GetMapping("/{appId}/monitor/optimize-config") public ApiResponse<?> monitorOptimizeConfig(@PathVariable String appId) { return ApiResponse.success(svc.getMonitorOptimizeConfig(appId)); }
    @PutMapping("/{appId}/monitor/optimize-config") public ApiResponse<?> saveMonitorOptimizeConfig(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveMonitorOptimizeConfig(appId,cfg)); }
    // 对话调试
    @GetMapping("/{appId}/debug") public ApiResponse<?> debugInfo(@PathVariable String appId) { return ApiResponse.success(svc.getDebugInfo(appId)); }
    @PostMapping("/{appId}/debug") public ApiResponse<?> saveDebug(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveDebugConfig(appId,cfg)); }
    // 导出调试日志
    @GetMapping("/{appId}/debug/export") public void exportDebugLogs(@PathVariable String appId, HttpServletResponse resp) throws Exception {
        var info=svc.getDebugInfo(appId);
        @SuppressWarnings("unchecked") var logs=(java.util.List<Map<String,Object>>)info.get("logs");
        resp.setContentType("text/csv;charset=UTF-8"); resp.setHeader("Content-Disposition","attachment;filename=debug_logs_"+appId+".csv");
        resp.setCharacterEncoding("UTF-8");
        var w=new java.io.PrintWriter(new java.io.OutputStreamWriter(resp.getOutputStream(),java.nio.charset.StandardCharsets.UTF_8));
        w.println('\ufeff'+"time,level,module,message");
        for(var l:logs) w.printf("\"%s\",\"%s\",\"%s\",\"%s\"%n",l.get("createdAt"),l.get("level"),l.get("module"),String.valueOf(l.get("message")).replace("\"","\"\""));
        w.flush();
    }
    // 清理调试日志
    @DeleteMapping("/{appId}/debug/logs") public ApiResponse<?> clearDebugLogs(@PathVariable String appId) { svc.clearDebugLogs(appId); return ApiResponse.success(); }
    // 知识更新
    @PostMapping("/{appId}/knowledge-update") public ApiResponse<?> knowledgeUpdate(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.triggerKnowledgeUpdate(appId,cfg)); }
}
