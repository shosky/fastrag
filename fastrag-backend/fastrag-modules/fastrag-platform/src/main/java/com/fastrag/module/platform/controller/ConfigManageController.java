package com.fastrag.module.platform.controller;

/**
 * 平台配置管理控制器
 * <p>
 * 提供系统级配置的统一管理入口，涵盖模型训练/测试、系统配置CRUD、安全策略管理、发布策略管理等多个功能域。
 * 所有写操作均通过 {@link com.fastrag.common.annotation.Loggable} 注解记录操作审计日志。
 * </p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>模型训练与测试：POST /api/models/{id}/train、POST /api/models/{id}/test、
 *       GET /api/models/{id}/trainings、GET /api/models/{id}/test-reports、GET /api/models/export</li>
 *   <li>系统配置管理：GET /api/config（按类型查询）、PUT /api/config（保存配置）、
 *       GET /api/config/history（变更历史）、GET/POST /api/config/export|import（导入导出）、
 *       GET/PUT /api/config/default（默认值管理）、POST /api/config/reset-to-default（重置为默认）</li>
 *   <li>业务开关配置：PUT /api/config/publish-switch（发布开关）、PUT /api/config/review-switch（审核开关）、
 *       PUT /api/config/review-flow（审核流程）、PUT /api/config/doc-guide（文档导读）、
 *       PUT /api/config/review-flow-binding（审核流程绑定）、PUT /api/config/publish-settings、PUT /api/config/review-settings</li>
 *   <li>状态查询：GET /api/config/publish-status、GET /api/config/review-status</li>
 *   <li>安全策略CRUD：GET/POST /api/security-policies、PUT/DELETE /api/security-policies/{id}</li>
 *   <li>发布策略CRUD：GET/POST /api/publish-strategies、PUT/DELETE /api/publish-strategies/{id}</li>
 * </ul>
 *
 * <p>该控制器直接依赖 {@link com.fastrag.module.platform.service.ConfigManageService} 处理所有业务逻辑，
 * 本身不包含业务实现，仅做参数转发与响应封装。配置值以 JSON 字符串形式存储，通过内部 ObjectMapper 进行序列化。</p>
 *
 * @see com.fastrag.module.platform.service.ConfigManageService
 * @see com.fastrag.module.platform.entity.SysConfig
 * @see com.fastrag.module.platform.entity.SysSecurityPolicy
 * @see com.fastrag.module.platform.entity.SysPublishStrategy
 */
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.platform.entity.SysPublishStrategy;
import com.fastrag.module.platform.entity.SysSecurityPolicy;
import com.fastrag.module.platform.service.ConfigManageService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;

import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class ConfigManageController {
    private final ConfigManageService svc;
    // ===== 模型训练/测试/导出 =====
    @Loggable(category = LogCategory.operation, action = ActionType.model_trained, target = "#modelId")
    @PostMapping("/models/{id}/train") public ApiResponse<?> train(@PathVariable("id") String modelId,@RequestBody Map<String,Object> params) { return ApiResponse.success(svc.train(modelId,params)); }
    @Loggable(category = LogCategory.operation, action = ActionType.model_tested, target = "#modelId")
    @PostMapping("/models/{id}/test") public ApiResponse<?> test(@PathVariable("id") String modelId,@RequestBody Map<String,Object> params) { return ApiResponse.success(svc.test(modelId,params)); }
    @GetMapping("/models/{id}/trainings") public ApiResponse<?> trainings(@PathVariable("id") String modelId) { return ApiResponse.success(svc.listTrainings(modelId)); }
    @GetMapping("/models/{id}/test-reports") public ApiResponse<?> testReports(@PathVariable("id") String modelId) { return ApiResponse.success(svc.listTestReports(modelId)); }
    @GetMapping("/models/export") public ApiResponse<?> exportModels(@RequestParam(required=false) String ids,@RequestParam(required=false) String purpose) { return ApiResponse.success(svc.exportModels(ids,purpose)); }
    // ===== 配置管理 =====
    @GetMapping("/config") public ApiResponse<?> list(@RequestParam(required=false) String configType) { return ApiResponse.success(svc.listConfigs(configType)); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_updated)
    @PutMapping("/config") public ApiResponse<?> save(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig((String)body.get("configKey"),String.valueOf(body.get("configValue")),(String)body.get("configType"),(String)body.get("description"),"admin")); }
    @GetMapping("/config/history") public ApiResponse<?> history(@RequestParam(required=false) String configKey,@RequestParam(required=false) String configType) { return ApiResponse.success(svc.listHistory(configKey,configType)); }
    @GetMapping("/config/export") public ApiResponse<?> exportConfig(@RequestParam(required=false) String configType) { return ApiResponse.success(svc.exportConfig(configType)); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_imported)
    @PostMapping("/config/import") public ApiResponse<?> importConfig(@RequestBody List<Map<String,Object>> items) { return ApiResponse.success(svc.importConfig(items,"admin")); }
    @GetMapping("/config/default") public ApiResponse<?> defaults() { return ApiResponse.success(svc.getDefaultConfigs()); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_updated)
    @PutMapping("/config/default") public ApiResponse<?> setDefault(@RequestBody Map<String,List<String>> body) { return ApiResponse.success(svc.setDefault(body.getOrDefault("configKeys",List.of()))); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_updated, target = "#configKey")
    @PostMapping("/config/{configKey}/set-default") public ApiResponse<?> setDefaultSingle(@PathVariable String configKey) { return ApiResponse.success(svc.setDefault(List.of(configKey))); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_reset_default)
    @PostMapping("/config/reset-to-default") public ApiResponse<?> resetToDefault(@RequestBody Map<String,List<String>> body) { return ApiResponse.success(svc.resetToDefault(body.getOrDefault("configKeys",List.of()),"admin")); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_reset_default, target = "#configKey")
    @PostMapping("/config/{configKey}/reset-default") public ApiResponse<?> resetToDefaultSingle(@PathVariable String configKey) { return ApiResponse.success(svc.resetToDefault(List.of(configKey),"admin")); }
    // 发布/审核开关等具体配置项
    @Loggable(category = LogCategory.operation, action = ActionType.config_updated)
    @PutMapping("/config/publish-switch") public ApiResponse<?> publishSwitch(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("publish_switch",toJson(body),"publish","发布开关","admin")); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_updated)
    @PutMapping("/config/review-switch") public ApiResponse<?> reviewSwitch(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("review_switch",toJson(body),"review","审核开关","admin")); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_updated)
    @PutMapping("/config/review-flow") public ApiResponse<?> reviewFlow(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("review_flow",toJson(body),"review","审核流程","admin")); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_updated)
    @PutMapping("/config/doc-guide") public ApiResponse<?> docGuide(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("doc_guide",toJson(body),"doc_guide","文档导读","admin")); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_updated)
    @PutMapping("/config/review-flow-binding") public ApiResponse<?> reviewFlowBinding(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("review_flow_binding",toJson(body),"review","审核流程绑定","admin")); }
    @GetMapping("/config/publish-status") public ApiResponse<?> publishStatus() { var c=svc.getConfig("publish_switch"); return ApiResponse.success(c!=null?c.getConfigValue():"{}"); }
    @GetMapping("/config/review-status") public ApiResponse<?> reviewStatus() { var c=svc.getConfig("review_switch"); return ApiResponse.success(c!=null?c.getConfigValue():"{}"); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_updated)
    @PutMapping("/config/publish-settings") public ApiResponse<?> publishSettings(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("publish_settings",toJson(body),"publish","发布设置","admin")); }
    @Loggable(category = LogCategory.operation, action = ActionType.config_updated)
    @PutMapping("/config/review-settings") public ApiResponse<?> reviewSettings(@RequestBody Map<String,Object> body) { return ApiResponse.success(svc.saveConfig("review_settings",toJson(body),"review","审核设置","admin")); }
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private static String toJson(Object o) {
        try { return MAPPER.writeValueAsString(o); } catch(Exception e) { return "{}"; }
    }
    // ===== 安全策略 CRUD =====
    @GetMapping("/security-policies") public ApiResponse<?> listSecurityPolicies(@RequestParam(required=false) String policyType) { return ApiResponse.success(svc.listSecurityPolicies(policyType)); }
    @Loggable(category = LogCategory.operation, action = ActionType.security_policy_created)
    @PostMapping("/security-policies") public ApiResponse<?> createSecurityPolicy(@RequestBody SysSecurityPolicy p) { return ApiResponse.success(svc.createSecurityPolicy(p)); }
    @Loggable(category = LogCategory.operation, action = ActionType.security_policy_updated, target = "#id")
    @PutMapping("/security-policies/{id}") public ApiResponse<?> updateSecurityPolicy(@PathVariable String id,@RequestBody SysSecurityPolicy p) { return ApiResponse.success(svc.updateSecurityPolicy(id,p)); }
    @Loggable(category = LogCategory.operation, action = ActionType.security_policy_deleted, target = "#id")
    @DeleteMapping("/security-policies/{id}") public ApiResponse<?> deleteSecurityPolicy(@PathVariable String id) { svc.deleteSecurityPolicy(id); return ApiResponse.success(); }
    // ===== 发布策略 CRUD =====
    @GetMapping("/publish-strategies") public ApiResponse<?> listPublishStrategies(@RequestParam(required=false) String strategyType) { return ApiResponse.success(svc.listPublishStrategies(strategyType)); }
    @Loggable(category = LogCategory.operation, action = ActionType.publish_strategy_created)
    @PostMapping("/publish-strategies") public ApiResponse<?> createPublishStrategy(@RequestBody SysPublishStrategy s) { return ApiResponse.success(svc.createPublishStrategy(s)); }
    @Loggable(category = LogCategory.operation, action = ActionType.publish_strategy_updated, target = "#id")
    @PutMapping("/publish-strategies/{id}") public ApiResponse<?> updatePublishStrategy(@PathVariable String id,@RequestBody SysPublishStrategy s) { return ApiResponse.success(svc.updatePublishStrategy(id,s)); }
    @Loggable(category = LogCategory.operation, action = ActionType.publish_strategy_deleted, target = "#id")
    @DeleteMapping("/publish-strategies/{id}") public ApiResponse<?> deletePublishStrategy(@PathVariable String id) { svc.deletePublishStrategy(id); return ApiResponse.success(); }
}
