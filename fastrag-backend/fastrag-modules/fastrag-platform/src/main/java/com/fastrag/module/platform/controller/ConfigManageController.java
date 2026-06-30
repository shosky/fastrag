package com.fastrag.module.platform.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.platform.entity.SysPublishStrategy;
import com.fastrag.module.platform.entity.SysSecurityPolicy;
import com.fastrag.module.platform.service.ConfigManageService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class ConfigManageController {
    private final ConfigManageService svc;
    // ===== 模型训练/测试/导出 =====
    @PostMapping("/models/{id}/train") public ApiResponse<?> train(@PathVariable("id") String modelId,@RequestBody Map<String,Object> params) { return ApiResponse.success(svc.train(modelId,params)); }
    @PostMapping("/models/{id}/test") public ApiResponse<?> test(@PathVariable("id") String modelId,@RequestBody Map<String,Object> params) { return ApiResponse.success(svc.test(modelId,params)); }
    @GetMapping("/models/{id}/trainings") public ApiResponse<?> trainings(@PathVariable("id") String modelId) { return ApiResponse.success(svc.listTrainings(modelId)); }
    @GetMapping("/models/{id}/test-reports") public ApiResponse<?> testReports(@PathVariable("id") String modelId) { return ApiResponse.success(svc.listTestReports(modelId)); }
    @GetMapping("/models/export") public ApiResponse<?> exportModels(@RequestParam(required=false) String ids,@RequestParam(required=false) String purpose) { return ApiResponse.success(svc.exportModels(ids,purpose)); }
    // ===== 配置管理 =====
    @GetMapping("/config") public ApiResponse<?> list(@RequestParam(required=false) String configType) { return ApiResponse.success(svc.listConfigs(configType)); }
    @PutMapping("/config") public ApiResponse<?> save(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig((String)body.get("configKey"),String.valueOf(body.get("configValue")),(String)body.get("configType"),(String)body.get("description"),"admin")); }
    @GetMapping("/config/history") public ApiResponse<?> history(@RequestParam(required=false) String configKey,@RequestParam(required=false) String configType) { return ApiResponse.success(svc.listHistory(configKey,configType)); }
    @GetMapping("/config/export") public ApiResponse<?> exportConfig(@RequestParam(required=false) String configType) { return ApiResponse.success(svc.exportConfig(configType)); }
    @PostMapping("/config/import") public ApiResponse<?> importConfig(@RequestBody List<Map<String,Object>> items) { return ApiResponse.success(svc.importConfig(items,"admin")); }
    @GetMapping("/config/default") public ApiResponse<?> defaults() { return ApiResponse.success(svc.getDefaultConfigs()); }
    @PutMapping("/config/default") public ApiResponse<?> setDefault(@RequestBody Map<String,List<String>> body) { return ApiResponse.success(svc.setDefault(body.getOrDefault("configKeys",List.of()))); }
    @PostMapping("/config/{configKey}/set-default") public ApiResponse<?> setDefaultSingle(@PathVariable String configKey) { return ApiResponse.success(svc.setDefault(List.of(configKey))); }
    @PostMapping("/config/reset-to-default") public ApiResponse<?> resetToDefault(@RequestBody Map<String,List<String>> body) { return ApiResponse.success(svc.resetToDefault(body.getOrDefault("configKeys",List.of()),"admin")); }
    @PostMapping("/config/{configKey}/reset-default") public ApiResponse<?> resetToDefaultSingle(@PathVariable String configKey) { return ApiResponse.success(svc.resetToDefault(List.of(configKey),"admin")); }
    // 发布/审核开关等具体配置项
    @PutMapping("/config/publish-switch") public ApiResponse<?> publishSwitch(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("publish_switch",toJson(body),"publish","发布开关","admin")); }
    @PutMapping("/config/review-switch") public ApiResponse<?> reviewSwitch(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("review_switch",toJson(body),"review","审核开关","admin")); }
    @PutMapping("/config/review-flow") public ApiResponse<?> reviewFlow(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("review_flow",toJson(body),"review","审核流程","admin")); }
    @PutMapping("/config/doc-guide") public ApiResponse<?> docGuide(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("doc_guide",toJson(body),"doc_guide","文档导读","admin")); }
    @PutMapping("/config/review-flow-binding") public ApiResponse<?> reviewFlowBinding(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("review_flow_binding",toJson(body),"review","审核流程绑定","admin")); }
    @GetMapping("/config/publish-status") public ApiResponse<?> publishStatus() { var c=svc.getConfig("publish_switch"); return ApiResponse.success(c!=null?c.getConfigValue():"{}"); }
    @GetMapping("/config/review-status") public ApiResponse<?> reviewStatus() { var c=svc.getConfig("review_switch"); return ApiResponse.success(c!=null?c.getConfigValue():"{}"); }
    @PutMapping("/config/publish-settings") public ApiResponse<?> publishSettings(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("publish_settings",toJson(body),"publish","发布设置","admin")); }
    @PutMapping("/config/review-settings") public ApiResponse<?> reviewSettings(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("review_settings",toJson(body),"review","审核设置","admin")); }
    private static String toJson(Object o) {
        // 简易JSON序列化（避免引入额外依赖），复用Jackson
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o); } catch(Exception e) { return "{}"; }
    }
    // ===== 安全策略 CRUD =====
    @GetMapping("/security-policies") public ApiResponse<?> listSecurityPolicies(@RequestParam(required=false) String policyType) { return ApiResponse.success(svc.listSecurityPolicies(policyType)); }
    @PostMapping("/security-policies") public ApiResponse<?> createSecurityPolicy(@RequestBody SysSecurityPolicy p) { return ApiResponse.success(svc.createSecurityPolicy(p)); }
    @PutMapping("/security-policies/{id}") public ApiResponse<?> updateSecurityPolicy(@PathVariable String id,@RequestBody SysSecurityPolicy p) { return ApiResponse.success(svc.updateSecurityPolicy(id,p)); }
    @DeleteMapping("/security-policies/{id}") public ApiResponse<?> deleteSecurityPolicy(@PathVariable String id) { svc.deleteSecurityPolicy(id); return ApiResponse.success(); }
    // ===== 发布策略 CRUD =====
    @GetMapping("/publish-strategies") public ApiResponse<?> listPublishStrategies(@RequestParam(required=false) String strategyType) { return ApiResponse.success(svc.listPublishStrategies(strategyType)); }
    @PostMapping("/publish-strategies") public ApiResponse<?> createPublishStrategy(@RequestBody SysPublishStrategy s) { return ApiResponse.success(svc.createPublishStrategy(s)); }
    @PutMapping("/publish-strategies/{id}") public ApiResponse<?> updatePublishStrategy(@PathVariable String id,@RequestBody SysPublishStrategy s) { return ApiResponse.success(svc.updatePublishStrategy(id,s)); }
    @DeleteMapping("/publish-strategies/{id}") public ApiResponse<?> deletePublishStrategy(@PathVariable String id) { svc.deletePublishStrategy(id); return ApiResponse.success(); }
}
