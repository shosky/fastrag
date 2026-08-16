package com.fastrag.module.application.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.application.entity.*;
import com.fastrag.module.application.service.WorkflowService;
import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;

/**
 * 工作流管理控制器，提供工作流全生命周期管理的REST API。
 *
 * <p>路由前缀：/api/workflows。核心功能区域包括：
 * <ul>
 *   <li>工作流CRUD — 列表、详情、创建、更新、删除</li>
 *   <li>发布管理 — 发布工作流</li>
 *   <li>画布节点管理 — 节点的增删改查、移动位置、节点属性/扩展维度（conditions/loops/delays等）读写、单节点测试</li>
 *   <li>执行引擎 — 触发工作流执行</li>
 *   <li>测试用例管理 — 测试用例的增删查</li>
 *   <li>工作流模板 — 模板的增删改查</li>
 *   <li>调试 — 调试信息查看与配置保存</li>
 *   <li>优化建议 — 优化建议的列表、创建、应用</li>
 *   <li>迁移 — 工作流迁移记录管理</li>
 *   <li>监控 — 监控数据获取</li>
 * </ul>
 *
 * <p>权限控制：查看类需 workflow:edit 权限，创建需 workflow:create 权限，
 * 删除需 workflow:delete 权限，发布需 workflow:publish 权限，执行需 workflow:run 权限。
 * 关键操作（创建、更新、删除、发布）通过 {@link Loggable} 注解记录操作日志。
 * 依赖 {@link WorkflowService} 实现全部业务逻辑。
 */
@RestController @RequestMapping("/api/workflows") @RequiredArgsConstructor
public class WorkflowController {
    private final WorkflowService svc;
    @PreAuthorize("@perm.has('workflow:edit')")
    @GetMapping public ApiResponse<?> list() { return ApiResponse.success(svc.list()); }
    @PreAuthorize("@perm.has('workflow:edit')")
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_created)
    @PreAuthorize("@perm.has('workflow:create')")
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_updated,target="#id")
    @PreAuthorize("@perm.has('workflow:edit')")
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.update(id,f)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_deleted,target="#id")
    @PreAuthorize("@perm.has('workflow:delete')")
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_published,target="#id")
    @PreAuthorize("@perm.has('workflow:publish')")
    @PostMapping("/{id}/publish") public ApiResponse<?> publish(@PathVariable String id) { svc.publish(id); return ApiResponse.success(); }
    // ===== 画布节点 =====
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_node_added,target="#id")
    @PreAuthorize("@perm.has('workflow:edit')")
    @PostMapping("/{id}/nodes") public ApiResponse<?> addNode(@PathVariable String id,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.addNode(id,(String)b.get("nodeKey"),(String)b.get("type"),(String)b.get("name"),b.get("x") instanceof Number?((Number)b.get("x")).intValue():0,b.get("y") instanceof Number?((Number)b.get("y")).intValue():0)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_node_updated,target="#id")
    @PreAuthorize("@perm.has('workflow:edit')")
    @PutMapping("/{id}/nodes/{nodeKey}") public ApiResponse<?> updateNode(@PathVariable String id,@PathVariable String nodeKey,@RequestBody WfNode n) { return ApiResponse.success(svc.updateNode(id,nodeKey,n)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_node_deleted,target="#id")
    @PreAuthorize("@perm.has('workflow:delete')")
    @DeleteMapping("/{id}/nodes/{nodeKey}") public ApiResponse<?> deleteNode(@PathVariable String id,@PathVariable String nodeKey) { svc.deleteNode(id,nodeKey); return ApiResponse.success(); }
    @PreAuthorize("@perm.has('workflow:edit')")
    @GetMapping("/{id}/nodes") public ApiResponse<?> listNodes(@PathVariable String id) { return ApiResponse.success(svc.listNodes(id)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_node_moved,target="#id")
    @PreAuthorize("@perm.has('workflow:edit')")
    @PutMapping("/{id}/nodes/{nodeKey}/position") public ApiResponse<?> moveNode(@PathVariable String id,@PathVariable String nodeKey,@RequestBody Map<String,Integer> b) { return ApiResponse.success(svc.moveNode(id,nodeKey,b.getOrDefault("x",0),b.getOrDefault("y",0))); }
    // ===== 节点配置 =====
    @PreAuthorize("@perm.has('workflow:edit')")
    @GetMapping("/{id}/nodes/{nodeKey}/properties") public ApiResponse<?> getNodeProps(@PathVariable String id,@PathVariable String nodeKey) { return ApiResponse.success(svc.getNodeConfig(id,nodeKey)); }
    @PreAuthorize("@perm.has('workflow:edit')")
    @PutMapping("/{id}/nodes/{nodeKey}/properties") public ApiResponse<?> setNodeProps(@PathVariable String id,@PathVariable String nodeKey,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.saveNodeConfig(id,nodeKey,"properties",b)); }
    // 维度化节点扩展属性（conditions/loops/delays/resources/permissions/log-level/env-vars/data-policies/backups）
    @PreAuthorize("@perm.has('workflow:edit')")
    @GetMapping("/{id}/nodes/{nodeKey}/{dimension}") public ApiResponse<?> getNodeDim(@PathVariable String id,@PathVariable String nodeKey,@PathVariable String dimension) { return ApiResponse.success(svc.getNodeConfig(id,nodeKey)); }
    @PreAuthorize("@perm.has('workflow:edit')")
    @PutMapping("/{id}/nodes/{nodeKey}/{dimension}") public ApiResponse<?> setNodeDim(@PathVariable String id,@PathVariable String nodeKey,@PathVariable String dimension,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.saveNodeConfig(id,nodeKey,dimension,b)); }
    @PreAuthorize("@perm.has('workflow:edit')")
    @PostMapping("/{id}/nodes/{nodeKey}/test") public ApiResponse<?> testNode(@PathVariable String id,@PathVariable String nodeKey,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.executeNode(id,nodeKey,b)); }
    // ===== 执行业务流 =====
    @PreAuthorize("@perm.has('workflow:run')")
    @PostMapping("/{id}/execute") public ApiResponse<?> execute(@PathVariable String id,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.execute(id,b)); }
    // ===== 测试 =====
    @PreAuthorize("@perm.has('workflow:edit')")
    @GetMapping("/{id}/test-cases") public ApiResponse<?> testCases(@PathVariable String id) { return ApiResponse.success(svc.listTestCases(id)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_test_case_created,target="#id")
    @PreAuthorize("@perm.has('workflow:edit')")
    @PostMapping("/{id}/test-cases") public ApiResponse<?> createTC(@PathVariable String id,@RequestBody WfTestCase tc) { return ApiResponse.success(svc.createTestCase(id,tc)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_test_case_deleted,target="#tcId")
    @PreAuthorize("@perm.has('workflow:delete')")
    @DeleteMapping("/{id}/test-cases/{tcId}") public ApiResponse<?> deleteTC(@PathVariable String tcId) { svc.deleteTestCase(tcId); return ApiResponse.success(); }
    // ===== 模板 =====
    @PreAuthorize("@perm.has('workflow:edit')")
    @GetMapping("/templates") public ApiResponse<?> templates() { return ApiResponse.success(svc.listTemplates()); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_template_created)
    @PreAuthorize("@perm.has('workflow:create')")
    @PostMapping("/templates") public ApiResponse<?> createTemplate(@RequestBody WfTemplate t) { return ApiResponse.success(svc.createTemplate(t)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_template_updated,target="#id")
    @PreAuthorize("@perm.has('workflow:edit')")
    @PutMapping("/templates/{id}") public ApiResponse<?> updateTemplate(@PathVariable String id,@RequestBody WfTemplate t) { return ApiResponse.success(svc.updateTemplate(id,t)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_template_deleted,target="#id")
    @PreAuthorize("@perm.has('workflow:delete')")
    @DeleteMapping("/templates/{id}") public ApiResponse<?> deleteTemplate(@PathVariable String id) { svc.deleteTemplate(id); return ApiResponse.success(); }
    // ===== 调试 =====
    @PreAuthorize("@perm.has('workflow:edit')")
    @GetMapping("/{id}/debug") public ApiResponse<?> debugInfo(@PathVariable String id) { return ApiResponse.success(svc.getDebugInfo(id)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_updated,target="#id")
    @PreAuthorize("@perm.has('workflow:edit')")
    @PostMapping("/{id}/debug") public ApiResponse<?> saveDebug(@PathVariable String id,@RequestBody Map<String,Object> cfg) { return ApiResponse.success(svc.saveDebugConfig(id,cfg)); }
    // ===== 优化 =====
    @PreAuthorize("@perm.has('workflow:edit')")
    @GetMapping("/{id}/optimizations") public ApiResponse<?> wfOpts(@PathVariable String id) { return ApiResponse.success(svc.listOptimizations(id)); }
    @PreAuthorize("@perm.has('workflow:edit')")
    @PostMapping("/{id}/optimizations") public ApiResponse<?> createWfOpt(@PathVariable String id,@RequestBody WfOptimization o) { return ApiResponse.success(svc.createOptimization(id,o)); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_updated,target="#optId")
    @PreAuthorize("@perm.has('workflow:edit')")
    @PostMapping("/{id}/optimizations/{optId}/apply") public ApiResponse<?> applyWfOpt(@PathVariable String optId) { return ApiResponse.success(svc.applyOptimization(optId)); }
    // ===== 迁移 =====
    @PreAuthorize("@perm.has('workflow:edit')")
    @GetMapping("/migrations") public ApiResponse<?> migrations() { return ApiResponse.success(svc.listMigrations()); }
    @Loggable(category=LogCategory.operation,action=ActionType.workflow_updated)
    @PreAuthorize("@perm.has('workflow:edit')")
    @PostMapping("/migrations") public ApiResponse<?> createMigration(@RequestBody WfMigration m) { return ApiResponse.success(svc.createMigration(m)); }
    // ===== 监控 =====
    @PreAuthorize("@perm.has('workflow:edit')")
    @GetMapping("/{id}/monitor") public ApiResponse<?> monitor(@PathVariable String id) { return ApiResponse.success(svc.getMonitorData(id)); }
}
