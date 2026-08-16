package com.fastrag.module.application.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.application.entity.*;
import com.fastrag.module.application.service.AppConfigService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 应用配置控制器，提供应用各项配置的读写与管理REST API。
 *
 * <p>路由前缀：/api/apps/{appId}。按配置类型划分为以下功能区域：
 * <ul>
 *   <li>基础配置（basic）— 记忆轮数、输出格式等</li>
 *   <li>对话配置（dialog）— 对话背景、对话参数</li>
 *   <li>智能体配置（config）— prompt、summary、maxSteps/maxTurns/retryTimes/maxTokens等</li>
 *   <li>触发器管理（triggers）— 触发器的CRUD、测试、运行</li>
 *   <li>全局策略（global-policy）— 安全策略、兜底策略、变量管理、敏感词、未匹配策略</li>
 *   <li>资源绑定 — 知识库（knowledge-bases）、数据库（databases）、技能（skills）、工具（tools）、MCP服务（mcp-services）</li>
 *   <li>发布管理（publish）— 发布记录查询、上线发布</li>
 *   <li>对话测试（dialog-tests）— 测试用例CRUD、CSV导出</li>
 *   <li>对话优化（optimizations）— 优化建议CRUD、应用、分析、CSV导出</li>
 *   <li>高级选项（advanced）— 导入导出、工作流配置、监控、调试、知识更新</li>
 *   <li>对话记录（conversations）— 对话历史查询、详情、删除、CSV导出</li>
 * </ul>
 *
 * <p>所有接口按操作类型要求不同权限：查看类接口需要 app:use 权限，
 * 编辑类接口需要 app:edit 权限，发布接口需要 app:publish 权限。
 * 依赖 {@link AppConfigService} 实现全部配置管理逻辑。
 */
@RestController @RequestMapping("/api/apps") @RequiredArgsConstructor
public class AppConfigController {
    private final AppConfigService svc;
    // ===== 基础配置 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/basic") public ApiResponse<?> basic(@PathVariable String appId) { return ApiResponse.success(svc.getBasic(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/basic") public ApiResponse<?> saveBasic(@PathVariable String appId,@RequestBody AppBasicConfig c) { return ApiResponse.success(svc.saveBasic(appId,c)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/basic/memory") public ApiResponse<?> memory(@PathVariable String appId,@RequestBody Map<String,Integer> b) { var c=svc.getBasic(appId);if(c==null)c=new AppBasicConfig();c.setMemoryRounds(b.getOrDefault("memoryRounds",5)); return ApiResponse.success(svc.saveBasic(appId,c)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/basic/output-format") public ApiResponse<?> output(@PathVariable String appId,@RequestBody Map<String,String> b) { var c=svc.getBasic(appId);if(c==null)c=new AppBasicConfig();c.setOutputFormat(b.getOrDefault("outputFormat","markdown")); return ApiResponse.success(svc.saveBasic(appId,c)); }
    // ===== 对话配置 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/dialog") public ApiResponse<?> dialog(@PathVariable String appId) { return ApiResponse.success(svc.getDialog(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/dialog/background") public ApiResponse<?> dialogBkg(@PathVariable String appId,@RequestBody AppDialogConfig c) { return ApiResponse.success(svc.saveDialog(appId,c)); }
    // ===== 智能体配置（对齐Yuxi） =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/config") public ApiResponse<?> getConfig(@PathVariable String appId) { return ApiResponse.success(svc.getConfig(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/config/prompt") public ApiResponse<?> savePrompt(@PathVariable String appId,@RequestBody Map<String,String> b) { return ApiResponse.success(svc.savePrompt(appId,b.getOrDefault("prompt",""))); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/config/summary") public ApiResponse<?> saveSummary(@PathVariable String appId,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.saveSummary(appId, b)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/config/max-steps") public ApiResponse<?> saveMaxSteps(@PathVariable String appId,@RequestBody Map<String,Integer> b) { return ApiResponse.success(svc.saveMaxSteps(appId, b.getOrDefault("maxSteps",15))); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/config/max-turns") public ApiResponse<?> saveMaxTurns(@PathVariable String appId,@RequestBody Map<String,Integer> b) { return ApiResponse.success(svc.saveMaxTurns(appId, b.getOrDefault("maxTurns",10))); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/config/retry-times") public ApiResponse<?> saveRetryTimes(@PathVariable String appId,@RequestBody Map<String,Integer> b) { return ApiResponse.success(svc.saveRetryTimes(appId, b.getOrDefault("retryTimes",2))); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/config/max-tokens") public ApiResponse<?> saveMaxTokens(@PathVariable String appId,@RequestBody Map<String,Integer> b) { return ApiResponse.success(svc.saveMaxTokens(appId, b.getOrDefault("maxTokens",2048))); }
    // 触发器
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/triggers") public ApiResponse<?> triggers(@PathVariable String appId) { return ApiResponse.success(svc.listTriggers(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/triggers") public ApiResponse<?> createTrigger(@PathVariable String appId,@RequestBody AppTrigger t) { return ApiResponse.success(svc.createTrigger(appId,t)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/triggers/{id}") public ApiResponse<?> updateTrigger(@PathVariable String id,@RequestBody AppTrigger t) { return ApiResponse.success(svc.updateTrigger(id,t)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/triggers/{id}/test") public ApiResponse<?> testTrigger(@PathVariable String id,@RequestBody Map<String,String> b) { return ApiResponse.success(svc.testTrigger(id,b.get("input"))); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/triggers/{id}/run") public ApiResponse<?> runTrigger(@PathVariable String id,@RequestBody Map<String,String> b) { return ApiResponse.success(svc.runTrigger(id,b.get("input"))); }
    @PreAuthorize("@perm.has('app:edit')")
    @DeleteMapping("/{appId}/triggers/{id}") public ApiResponse<?> deleteTrigger(@PathVariable String id) { svc.deleteTrigger(id); return ApiResponse.success(); }
    // ===== 全局策略 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/global-policy") public ApiResponse<?> policy(@PathVariable String appId) { return ApiResponse.success(svc.getGlobalPolicy(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/global-policy/safety") public ApiResponse<?> safety(@PathVariable String appId,@RequestBody AppGlobalPolicy p) { return ApiResponse.success(svc.saveGlobalPolicy(appId,p)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/global-policy/fallback") public ApiResponse<?> fallback(@PathVariable String appId,@RequestBody AppGlobalPolicy p) { return ApiResponse.success(svc.saveGlobalPolicy(appId,p)); }
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/global-policy/variables") public ApiResponse<?> vars(@PathVariable String appId) { return ApiResponse.success(svc.listVariables(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/global-policy/variables") public ApiResponse<?> createVar(@PathVariable String appId,@RequestBody AppVariable v) { return ApiResponse.success(svc.createVariable(appId,v)); }
    @PreAuthorize("@perm.has('app:edit')")
    @DeleteMapping("/{appId}/global-policy/variables/{id}") public ApiResponse<?> deleteVar(@PathVariable String id) { svc.deleteVariable(id); return ApiResponse.success(); }
    // ===== 知识库绑定 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/knowledge-bases") public ApiResponse<?> kbs(@PathVariable String appId) { return ApiResponse.success(svc.listKbBindings(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/knowledge-bases") public ApiResponse<?> bindKb(@PathVariable String appId,@RequestBody AppKbBinding b) { return ApiResponse.success(svc.bindKb(appId,b)); }
    @PreAuthorize("@perm.has('app:edit')")
    @DeleteMapping("/{appId}/knowledge-bases/{id}") public ApiResponse<?> unbindKb(@PathVariable String id) { svc.unbindKb(id); return ApiResponse.success(); }
    // ===== 数据库绑定 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/databases") public ApiResponse<?> dbs(@PathVariable String appId) { return ApiResponse.success(svc.listDbBindings(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/databases") public ApiResponse<?> bindDb(@PathVariable String appId, @RequestBody AppDbBinding b) { return ApiResponse.success(svc.bindDb(appId,b)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/databases/{id}") public ApiResponse<?> updateDb(@PathVariable String id, @RequestBody AppDbBinding b) { return ApiResponse.success(svc.updateDbBinding(id,b)); }
    @PreAuthorize("@perm.has('app:edit')")
    @DeleteMapping("/{appId}/databases/{id}") public ApiResponse<?> unbindDb(@PathVariable String id) { svc.unbindDb(id); return ApiResponse.success(); }
    // ===== 技能绑定 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/skills") public ApiResponse<?> skillBindings(@PathVariable String appId) { return ApiResponse.success(svc.listSkillBindings(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/skills") public ApiResponse<?> bindSkill(@PathVariable String appId,@RequestBody AppSkillBinding b) { return ApiResponse.success(svc.bindSkill(appId,b)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/skills/{id}") public ApiResponse<?> updateSkill(@PathVariable String id,@RequestBody AppSkillBinding b) { return ApiResponse.success(svc.updateSkillBinding(id,b)); }
    @PreAuthorize("@perm.has('app:edit')")
    @DeleteMapping("/{appId}/skills/{id}") public ApiResponse<?> unbindSkill(@PathVariable String id) { svc.unbindSkill(id); return ApiResponse.success(); }
    // ===== 工具绑定 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/tools") public ApiResponse<?> toolBindings(@PathVariable String appId) { return ApiResponse.success(svc.listToolBindings(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/tools") public ApiResponse<?> bindTool(@PathVariable String appId,@RequestBody AppToolBinding b) { return ApiResponse.success(svc.bindTool(appId,b)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/tools/{id}") public ApiResponse<?> updateTool(@PathVariable String id,@RequestBody AppToolBinding b) { return ApiResponse.success(svc.updateToolBinding(id,b)); }
    @PreAuthorize("@perm.has('app:edit')")
    @DeleteMapping("/{appId}/tools/{id}") public ApiResponse<?> unbindTool(@PathVariable String id) { svc.unbindTool(id); return ApiResponse.success(); }
    // ===== MCP 绑定 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/mcp-services") public ApiResponse<?> mcpBindings(@PathVariable String appId) { return ApiResponse.success(svc.listMcpBindings(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/mcp-services") public ApiResponse<?> bindMcp(@PathVariable String appId,@RequestBody AppMcpBinding b) { return ApiResponse.success(svc.bindMcp(appId,b)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/mcp-services/{id}") public ApiResponse<?> updateMcp(@PathVariable String id,@RequestBody AppMcpBinding b) { return ApiResponse.success(svc.updateMcpBinding(id,b)); }
    @PreAuthorize("@perm.has('app:edit')")
    @DeleteMapping("/{appId}/mcp-services/{id}") public ApiResponse<?> unbindMcp(@PathVariable String id) { svc.unbindMcp(id); return ApiResponse.success(); }
    // ===== 发布管理 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/publish/records") public ApiResponse<?> pubRecords(@PathVariable String appId) { return ApiResponse.success(svc.listPublishRecords(appId)); }
    @PreAuthorize("@perm.has('app:publish')")
    @PostMapping("/{appId}/publish/online") public ApiResponse<?> pubOnline(@PathVariable String appId,@RequestBody AppPublishRecord r) { return ApiResponse.success(svc.publish(appId,r)); }
    // ===== 对话测试 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/dialog-tests") public ApiResponse<?> tests(@PathVariable String appId) { return ApiResponse.success(svc.listDialogTests(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/dialog-tests") public ApiResponse<?> createTest(@PathVariable String appId,@RequestBody AppDialogTest t) { return ApiResponse.success(svc.createDialogTest(appId,t)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/dialog-tests/{id}") public ApiResponse<?> updateTest(@PathVariable String id,@RequestBody AppDialogTest t) { return ApiResponse.success(svc.updateDialogTest(id,t)); }
    @PreAuthorize("@perm.has('app:edit')")
    @DeleteMapping("/{appId}/dialog-tests/{id}") public ApiResponse<?> deleteTest(@PathVariable String id) { svc.deleteDialogTest(id); return ApiResponse.success(); }
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/dialog-tests/export") public void exportTests(@PathVariable String appId, HttpServletResponse resp) throws Exception {
        var list=svc.listDialogTests(appId);
        resp.setContentType("text/csv;charset=UTF-8"); resp.setHeader("Content-Disposition","attachment;filename=test_report.csv");
        var w=new java.io.PrintWriter(resp.getWriter()); w.println("name,query,expected,actual,matched,similarity,createdAt");
        for(var t:list) w.printf("\"%s\",\"%s\",\"%s\",\"%s\",%s,%s,%s%n",t.getName()!=null?t.getName():"",t.getQuery()!=null?t.getQuery():"",t.getExpectedAnswer()!=null?t.getExpectedAnswer():"",t.getActualAnswer()!=null?t.getActualAnswer():"",t.getMatched(),t.getSimilarity(),t.getCreatedAt());
        w.flush();
    }
    // ===== 对话优化 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/optimizations") public ApiResponse<?> opts(@PathVariable String appId) { return ApiResponse.success(svc.listOptimizations(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/optimizations") public ApiResponse<?> createOpt(@PathVariable String appId,@RequestBody AppOptimization o) { return ApiResponse.success(svc.createOptimization(appId,o)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/optimizations/{id}") public ApiResponse<?> updateOpt(@PathVariable String id,@RequestBody AppOptimization o) { return ApiResponse.success(svc.updateOptimization(id,o)); }
    @PreAuthorize("@perm.has('app:edit')")
    @DeleteMapping("/{appId}/optimizations/{id}") public ApiResponse<?> deleteOpt(@PathVariable String id) { svc.deleteOptimization(id); return ApiResponse.success(); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/optimizations/{id}/apply") public ApiResponse<?> applyOpt(@PathVariable String id) { return ApiResponse.success(svc.applyOptimization(id)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/optimization/analyze") public ApiResponse<?> analyze(@PathVariable String appId) { return ApiResponse.success(svc.analyze(appId)); }
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/optimizations/export") public void exportOpts(@PathVariable String appId, HttpServletResponse resp) throws Exception {
        var list=svc.listOptimizations(appId);
        resp.setContentType("text/csv;charset=UTF-8"); resp.setHeader("Content-Disposition","attachment;filename=optimization_report.csv");
        var w=new java.io.PrintWriter(resp.getWriter()); w.println("title,suggestionType,description,status,impactScore,createdAt");
        for(var o:list) w.printf("\"%s\",\"%s\",\"%s\",\"%s\",%s,%s%n",
            o.getTitle()!=null?o.getTitle():"",o.getSuggestionType()!=null?o.getSuggestionType():"",
            o.getDescription()!=null?o.getDescription():"",o.getStatus()!=null?o.getStatus():"",
            o.getImpactScore(),o.getCreatedAt());
        w.flush();
    }

    // ===== M16 扩展：高级选项 =====
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/basic/advanced") public ApiResponse<?> advanced(@PathVariable String appId,@RequestBody Map<String,Object> opts) { return ApiResponse.success(svc.saveAdvanced(appId,opts)); }
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/basic/export") public ApiResponse<?> exportBasic(@PathVariable String appId) { return ApiResponse.success(svc.exportConfig(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/basic/import") public ApiResponse<?> importBasic(@PathVariable String appId,@RequestBody Map<String,Object> data) { return ApiResponse.success(svc.importConfig(appId,data)); }
    // 对话配置导出/导入
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/dialog/export") public ApiResponse<?> exportDialog(@PathVariable String appId) { return ApiResponse.success(svc.exportDialogConfig(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/dialog/import") public ApiResponse<?> importDialog(@PathVariable String appId,@RequestBody Map<String,Object> data) { return ApiResponse.success(svc.importDialogConfig(appId,data)); }
    // 变量更新
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/global-policy/variables/{id}") public ApiResponse<?> updateVar(@PathVariable String id,@RequestBody AppVariable v) { return ApiResponse.success(svc.updateVariable(id,v)); }
    // 敏感词
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/global-policy/sensitive-words") public ApiResponse<?> sensitiveWords(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveSensitiveWords(appId,cfg)); }
    // 启用禁用
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/global-policy/toggle") public ApiResponse<?> togglePolicy(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.togglePolicy(appId,cfg)); }
    // 未匹配策略
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/global-policy/unmatched") public ApiResponse<?> unmatched(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveUnmatchedConfig(appId,cfg)); }
    // 工作流配置
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/workflow-config") public ApiResponse<?> wfConfig(@PathVariable String appId) { return ApiResponse.success(svc.getWorkflowConfig(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/workflow-config") public ApiResponse<?> saveWfConfig(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveWorkflowConfig(appId,cfg)); }
    // 监控管理
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/monitor") public ApiResponse<?> monitor(@PathVariable String appId) { return ApiResponse.success(svc.getMonitorData(appId)); }
    // 对话调试
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/debug") public ApiResponse<?> debugInfo(@PathVariable String appId) { return ApiResponse.success(svc.getDebugInfo(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/debug") public ApiResponse<?> saveDebug(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveDebugConfig(appId,cfg)); }
    // 知识更新
    @PreAuthorize("@perm.has('app:edit')")
    @PostMapping("/{appId}/knowledge-update") public ApiResponse<?> knowledgeUpdate(@PathVariable String appId,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.triggerKnowledgeUpdate(appId,cfg)); }
    // 知识库自动更新配置
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/knowledge-update/config") public ApiResponse<?> getKbAutoUpdate(@PathVariable String appId) { return ApiResponse.success(svc.getAutoKnowledgeUpdate(appId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @PutMapping("/{appId}/knowledge-update/config") public ApiResponse<?> saveKbAutoUpdate(@PathVariable String appId,@RequestBody AppKbAutoUpdateConfig cfg) { return ApiResponse.success(svc.saveAutoKnowledgeUpdate(appId,cfg)); }
    // ===== 对话记录 =====
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/conversations") public ApiResponse<?> listConvs(@PathVariable String appId,
        @RequestParam(required=false) String keyword,@RequestParam(required=false) Integer rating,
        @RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(svc.listConversations(appId,keyword,rating,startDate,endDate,page,size));
    }
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/conversations/{convId}") public ApiResponse<?> convDetail(@PathVariable String convId) { return ApiResponse.success(svc.getConversationDetail(convId)); }
    @PreAuthorize("@perm.has('app:edit')")
    @DeleteMapping("/{appId}/conversations/{convId}") public ApiResponse<?> delConv(@PathVariable String convId) { svc.deleteConversation(convId); return ApiResponse.success(); }
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/{appId}/conversations/export") public void exportConvs(@PathVariable String appId,HttpServletResponse resp) throws Exception { svc.exportConversationsCsv(appId,resp); }
}
