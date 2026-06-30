package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.*;
import com.fastrag.module.knowledge.service.PublishManageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}") @RequiredArgsConstructor
public class PublishManageController {
    private final PublishManageService svc;
    // 发布管理
    @GetMapping("/publish/history") public ApiResponse<?> history(@PathVariable String kbId,@RequestParam(required=false) String knowledgeId) { return ApiResponse.success(svc.listPublishHistory(kbId,knowledgeId)); }
    @PostMapping("/publish/{knowledgeId}") public ApiResponse<?> publish(@PathVariable String kbId,@PathVariable String knowledgeId,@RequestBody KbPublishHistory body) { return ApiResponse.success(svc.publish(kbId,knowledgeId,body)); }
    @PostMapping("/publish/{knowledgeId}/revoke") public ApiResponse<?> revoke(@PathVariable String kbId,@PathVariable String knowledgeId) { return ApiResponse.success(svc.revoke(kbId,knowledgeId)); }
    @PostMapping("/publish/plans") public ApiResponse<?> createPlan(@PathVariable String kbId,@RequestBody KbPublishPlan plan) { plan.setKbId(kbId); return ApiResponse.success(svc.createPlan(plan)); }
    @GetMapping("/publish/plans") public ApiResponse<?> plans(@PathVariable String kbId) { return ApiResponse.success(svc.listPlans(kbId)); }
    @GetMapping("/publish/plans/{planId}/execution") public ApiResponse<?> planExecution(@PathVariable String planId) { return ApiResponse.success(svc.getPlanExecution(planId)); }
    @GetMapping("/publish/strategy-effect") public ApiResponse<?> strategyEffect(@PathVariable String kbId) { return ApiResponse.success(svc.getStrategyEffect(kbId)); }
    // 审核策略
    @GetMapping("/review-strategies") public ApiResponse<?> strategies(@PathVariable String kbId) { return ApiResponse.success(svc.listStrategies(kbId)); }
    @PostMapping("/review-strategies") public ApiResponse<?> createStrategy(@PathVariable String kbId,@RequestBody KbReviewStrategy s) { s.setKbId(kbId); return ApiResponse.success(svc.createStrategy(s)); }
    @DeleteMapping("/review-strategies/{id}") public ApiResponse<?> deleteStrategy(@PathVariable String id) { svc.deleteStrategy(id); return ApiResponse.success(); }
    @GetMapping("/compliance-rules") public ApiResponse<?> complianceRules(@PathVariable String kbId) { return ApiResponse.success(svc.listComplianceRules(kbId)); }
    @PostMapping("/compliance-rules") public ApiResponse<?> createCompliance(@PathVariable String kbId,@RequestBody KbComplianceRule r) { r.setKbId(kbId); return ApiResponse.success(svc.createComplianceRule(r)); }
    @PutMapping("/compliance-rules/{id}") public ApiResponse<?> updateCompliance(@PathVariable String id,@RequestBody KbComplianceRule r) { return ApiResponse.success(svc.updateComplianceRule(id,r)); }
    @DeleteMapping("/compliance-rules/{id}") public ApiResponse<?> deleteCompliance(@PathVariable String id) { svc.deleteComplianceRule(id); return ApiResponse.success(); }
    @GetMapping("/quality-rules") public ApiResponse<?> qualityRules(@PathVariable String kbId) { return ApiResponse.success(svc.listQualityRules(kbId)); }
    @PostMapping("/quality-rules") public ApiResponse<?> createQuality(@PathVariable String kbId,@RequestBody KbQualityRule r) { r.setKbId(kbId); return ApiResponse.success(svc.createQualityRule(r)); }
    @PutMapping("/quality-rules/{id}") public ApiResponse<?> updateQuality(@PathVariable String id,@RequestBody KbQualityRule r) { return ApiResponse.success(svc.updateQualityRule(id,r)); }
    @DeleteMapping("/quality-rules/{id}") public ApiResponse<?> deleteQuality(@PathVariable String id) { svc.deleteQualityRule(id); return ApiResponse.success(); }
    // 审核流程设计
    @GetMapping("/review-templates") public ApiResponse<?> templates() { return ApiResponse.success(svc.listTemplates()); }
    @PostMapping("/review-templates") public ApiResponse<?> createTemplate(@RequestBody KbReviewTemplate t) { return ApiResponse.success(svc.createTemplate(t)); }
    @PutMapping("/review-templates/{id}") public ApiResponse<?> updateTemplate(@PathVariable String id,@RequestBody KbReviewTemplate t) { return ApiResponse.success(svc.updateTemplate(id,t)); }
    @DeleteMapping("/review-templates/{id}") public ApiResponse<?> deleteTemplate(@PathVariable String id) { svc.deleteTemplate(id); return ApiResponse.success(); }
    @GetMapping("/review-templates/{templateId}/nodes") public ApiResponse<?> nodes(@PathVariable String templateId) { return ApiResponse.success(svc.listNodes(templateId)); }
    @PostMapping("/review-templates/{templateId}/nodes") public ApiResponse<?> createNode(@PathVariable String templateId,@RequestBody KbReviewNode n) { n.setTemplateId(templateId); return ApiResponse.success(svc.createNode(n)); }
    @DeleteMapping("/review-nodes/{id}") public ApiResponse<?> deleteNode(@PathVariable String id) { svc.deleteNode(id); return ApiResponse.success(); }
    // 监听管理
    @GetMapping("/listeners") public ApiResponse<?> listeners(@PathVariable String kbId) { return ApiResponse.success(svc.listListeners(kbId)); }
    @PostMapping("/listeners") public ApiResponse<?> createListener(@PathVariable String kbId,@RequestBody KbListener l) { l.setKbId(kbId); return ApiResponse.success(svc.createListener(l)); }
    @PutMapping("/listeners/{id}") public ApiResponse<?> updateListener(@PathVariable String id,@RequestBody KbListener l) { return ApiResponse.success(svc.updateListener(id,l)); }
    @PostMapping("/listeners/{id}/{action}") public ApiResponse<?> toggleListener(@PathVariable String id,@PathVariable String action) { return ApiResponse.success(svc.toggleListener(id,action)); }
    @DeleteMapping("/listeners/{id}") public ApiResponse<?> deleteListener(@PathVariable String id) { svc.deleteListener(id); return ApiResponse.success(); }
    @GetMapping("/listeners/{id}/logs") public ApiResponse<?> listenerLogs(@PathVariable String id,
        @RequestParam(required=false) String level, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.success(svc.listListenerLogs(id, level, page, pageSize));
    }
    // 监听管理细化
    @DeleteMapping("/listeners/{id}/logs") public ApiResponse<?> clearListenerLogs(@PathVariable String id,
        @RequestParam(required=false) String beforeDate) { svc.clearListenerLogs(id, beforeDate); return ApiResponse.success(); }
    @GetMapping("/listeners/{id}/stats") public ApiResponse<?> listenerStats(@PathVariable String id) { return ApiResponse.success(svc.getListenerStats(id)); }
    @GetMapping("/listeners/{id}/trends") public ApiResponse<?> listenerTrends(@PathVariable String id,
        @RequestParam(defaultValue="7") int days) { return ApiResponse.success(svc.getListenerTrends(id, days)); }
    @PostMapping("/listeners/{id}/alerts") public ApiResponse<?> setListenerAlerts(@PathVariable String id, @RequestBody Map<String,Object> config) { return ApiResponse.success(svc.saveListenerAlerts(id, config)); }
    // 查看线上/线下版本
    @GetMapping("/publish/online-version") public ApiResponse<?> onlineVersion(@PathVariable String kbId,
        @RequestParam(required=false) String knowledgeId) { return ApiResponse.success(svc.getOnlineVersion(kbId, knowledgeId)); }
    @GetMapping("/publish/offline-version") public ApiResponse<?> offlineVersion(@PathVariable String kbId,
        @RequestParam(required=false) String knowledgeId) { return ApiResponse.success(svc.getOfflineVersion(kbId, knowledgeId)); }
    // 审核流程细化
    @GetMapping("/review-strategies/{id}/history") public ApiResponse<?> reviewHistory(@PathVariable String id) { return ApiResponse.success(svc.getReviewHistory(id)); }
    @PutMapping("/review-strategies/{id}/timeout") public ApiResponse<?> reviewTimeout(@PathVariable String id, @RequestBody Map<String,Object> config) { return ApiResponse.success(svc.setReviewTimeout(id, config)); }
    // 审核报告/导出
    @GetMapping("/publish/report") public ApiResponse<?> publishReport(@PathVariable String kbId) { return ApiResponse.success(svc.generatePublishReport(kbId)); }
    @GetMapping("/publish/export") public ApiResponse<?> publishExport(@PathVariable String kbId,
        @RequestParam(required=false) String format) { return ApiResponse.success(svc.exportPublishData(kbId, format)); }
    // 效率分析
    @GetMapping("/publish/efficiency") public ApiResponse<?> efficiency(@PathVariable String kbId) { return ApiResponse.success(svc.getPublishEfficiency(kbId)); }
    // 流程图数据
    @GetMapping("/publish/flow-chart") public ApiResponse<?> flowChart(@PathVariable String kbId) { return ApiResponse.success(svc.getFlowChart(kbId)); }
    // ===== P3 新增端点 =====
    @GetMapping("/publish/export-review-records") public void exportReviewRecords(@PathVariable String kbId, HttpServletResponse resp) throws Exception { svc.exportReviewRecords(kbId, resp); }
    @PostMapping("/publish/import-review-knowledge") public ApiResponse<?> importReviewKnowledge(@PathVariable String kbId, @RequestBody List<Map<String,Object>> items) { return ApiResponse.success(svc.importReviewKnowledge(kbId, items)); }
    @GetMapping("/publish/export-unreviewed") public void exportUnreviewed(@PathVariable String kbId, HttpServletResponse resp) throws Exception { svc.exportUnreviewedKnowledge(kbId, resp); }
    @PostMapping("/publish/import-flow-chart") public ApiResponse<?> importFlowChart(@PathVariable String kbId, @RequestBody Map<String,Object> data) { return ApiResponse.success(svc.importFlowChart(kbId, data)); }
    @GetMapping("/publish/log-retention") public ApiResponse<?> logRetention(@PathVariable String kbId) { return ApiResponse.success(svc.getLogRetention(kbId)); }
    @PutMapping("/publish/log-retention") public ApiResponse<?> setLogRetention(@PathVariable String kbId, @RequestBody Map<String,Object> config) { return ApiResponse.success(svc.setLogRetention(kbId, config)); }
    @GetMapping("/publish/review-metrics") public ApiResponse<?> reviewMetrics(@PathVariable String kbId) { return ApiResponse.success(svc.getReviewMetrics(kbId)); }
    @GetMapping("/publish/review-optimizations") public ApiResponse<?> reviewOptimizations(@PathVariable String kbId) { return ApiResponse.success(svc.getReviewOptimizations(kbId)); }
    @PostMapping("/publish/review-optimizations/{optId}/apply") public ApiResponse<?> applyOptimization(@PathVariable String kbId, @PathVariable String optId) { return ApiResponse.success(svc.applyReviewOptimization(kbId, optId)); }
    @GetMapping("/knowledge-update-logs") public ApiResponse<?> updateLogs(@PathVariable String kbId,
        @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) { return ApiResponse.success(svc.getKnowledgeUpdateLogs(kbId, page, pageSize)); }
    @GetMapping("/knowledge-compare") public ApiResponse<?> compare(@PathVariable String kbId,
        @RequestParam String oldId, @RequestParam String newId) { return ApiResponse.success(svc.compareKnowledgeContent(kbId, oldId, newId)); }
    // 知识重置
    @GetMapping("/reset-configs") public ApiResponse<?> resetConfigs(@PathVariable String kbId) { return ApiResponse.success(svc.listResetConfigs(kbId)); }
    @PostMapping("/reset-configs") public ApiResponse<?> saveResetConfig(@PathVariable String kbId,@RequestBody KbResetConfig c) { c.setKbId(kbId); return ApiResponse.success(svc.saveResetConfig(c)); }
    @PostMapping("/reset/{knowledgeId}") public ApiResponse<?> reset(@PathVariable String kbId,@PathVariable String knowledgeId) { return ApiResponse.success(svc.resetKnowledge(kbId,knowledgeId)); }
}
