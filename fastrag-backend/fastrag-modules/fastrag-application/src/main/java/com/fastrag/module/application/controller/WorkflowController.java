package com.fastrag.module.application.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.application.entity.*;
import com.fastrag.module.application.service.WorkflowService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/workflows") @RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService svc;
    @GetMapping public ApiResponse<?> list() { return ApiResponse.success(svc.list()); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.update(id,f)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/publish") public ApiResponse<?> publish(@PathVariable String id) { svc.publish(id); return ApiResponse.success(); }
    // ===== 画布节点 =====
    @PostMapping("/{id}/nodes") public ApiResponse<?> addNode(@PathVariable String id,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.addNode(id,(String)b.get("nodeKey"),(String)b.get("type"),(String)b.get("name"),b.get("x") instanceof Number?((Number)b.get("x")).intValue():0,b.get("y") instanceof Number?((Number)b.get("y")).intValue():0)); }
    @PutMapping("/{id}/nodes/{nodeKey}") public ApiResponse<?> updateNode(@PathVariable String id,@PathVariable String nodeKey,@RequestBody WfNode n) { return ApiResponse.success(svc.updateNode(id,nodeKey,n)); }
    @DeleteMapping("/{id}/nodes/{nodeKey}") public ApiResponse<?> deleteNode(@PathVariable String id,@PathVariable String nodeKey) { svc.deleteNode(id,nodeKey); return ApiResponse.success(); }
    @GetMapping("/{id}/nodes") public ApiResponse<?> listNodes(@PathVariable String id) { return ApiResponse.success(svc.listNodes(id)); }
    @PutMapping("/{id}/nodes/{nodeKey}/position") public ApiResponse<?> moveNode(@PathVariable String id,@PathVariable String nodeKey,@RequestBody Map<String,Integer> b) { return ApiResponse.success(svc.moveNode(id,nodeKey,b.getOrDefault("x",0),b.getOrDefault("y",0))); }
    // ===== 节点配置 =====
    @GetMapping("/{id}/nodes/{nodeKey}/properties") public ApiResponse<?> getNodeProps(@PathVariable String id,@PathVariable String nodeKey) { return ApiResponse.success(svc.getNodeConfig(id,nodeKey)); }
    @PutMapping("/{id}/nodes/{nodeKey}/properties") public ApiResponse<?> setNodeProps(@PathVariable String id,@PathVariable String nodeKey,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.saveNodeConfig(id,nodeKey,"properties",b)); }
    // 维度化节点扩展属性（conditions/loops/delays/resources/permissions/log-level/env-vars/data-policies/backups）
    @GetMapping("/{id}/nodes/{nodeKey}/{dimension}") public ApiResponse<?> getNodeDim(@PathVariable String id,@PathVariable String nodeKey,@PathVariable String dimension) { return ApiResponse.success(svc.getNodeConfig(id,nodeKey)); }
    @PutMapping("/{id}/nodes/{nodeKey}/{dimension}") public ApiResponse<?> setNodeDim(@PathVariable String id,@PathVariable String nodeKey,@PathVariable String dimension,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.saveNodeConfig(id,nodeKey,dimension,b)); }
    @PostMapping("/{id}/nodes/{nodeKey}/test") public ApiResponse<?> testNode(@PathVariable String id,@PathVariable String nodeKey,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.executeNode(id,nodeKey,b)); }
    // ===== 执行业务流 =====
    @PostMapping("/{id}/execute") public ApiResponse<?> execute(@PathVariable String id,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.execute(id,b)); }
    // ===== 测试 =====
    @GetMapping("/{id}/test-cases") public ApiResponse<?> testCases(@PathVariable String id) { return ApiResponse.success(svc.listTestCases(id)); }
    @PostMapping("/{id}/test-cases") public ApiResponse<?> createTC(@PathVariable String id,@RequestBody WfTestCase tc) { return ApiResponse.success(svc.createTestCase(id,tc)); }
    @DeleteMapping("/{id}/test-cases/{tcId}") public ApiResponse<?> deleteTC(@PathVariable String tcId) { svc.deleteTestCase(tcId); return ApiResponse.success(); }
    // ===== 模板 =====
    @GetMapping("/templates") public ApiResponse<?> templates() { return ApiResponse.success(svc.listTemplates()); }
    @PostMapping("/templates") public ApiResponse<?> createTemplate(@RequestBody WfTemplate t) { return ApiResponse.success(svc.createTemplate(t)); }
    @PutMapping("/templates/{id}") public ApiResponse<?> updateTemplate(@PathVariable String id,@RequestBody WfTemplate t) { return ApiResponse.success(svc.updateTemplate(id,t)); }
    @DeleteMapping("/templates/{id}") public ApiResponse<?> deleteTemplate(@PathVariable String id) { svc.deleteTemplate(id); return ApiResponse.success(); }
    // ===== 调试 =====
    @GetMapping("/{id}/debug") public ApiResponse<?> debugInfo(@PathVariable String id) { return ApiResponse.success(svc.getDebugInfo(id)); }
    @PostMapping("/{id}/debug") public ApiResponse<?> saveDebug(@PathVariable String id,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveDebugConfig(id,cfg)); }
    // ===== 优化 =====
    @GetMapping("/{id}/optimizations") public ApiResponse<?> wfOpts(@PathVariable String id) { return ApiResponse.success(svc.listOptimizations(id)); }
    @PostMapping("/{id}/optimizations") public ApiResponse<?> createWfOpt(@PathVariable String id,@RequestBody WfOptimization o) { return ApiResponse.success(svc.createOptimization(id,o)); }
    @PostMapping("/{id}/optimizations/{optId}/apply") public ApiResponse<?> applyWfOpt(@PathVariable String optId) { return ApiResponse.success(svc.applyOptimization(optId)); }
    // ===== 迁移 =====
    @GetMapping("/migrations") public ApiResponse<?> migrations() { return ApiResponse.success(svc.listMigrations()); }
    @PostMapping("/migrations") public ApiResponse<?> createMigration(@RequestBody WfMigration m) { return ApiResponse.success(svc.createMigration(m)); }
    // ===== 监控 =====
    @GetMapping("/{id}/monitor") public ApiResponse<?> monitor(@PathVariable String id) { return ApiResponse.success(svc.getMonitorData(id)); }
}
