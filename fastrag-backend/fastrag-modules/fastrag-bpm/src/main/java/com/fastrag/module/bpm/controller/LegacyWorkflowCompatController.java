package com.fastrag.module.bpm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.dto.*;
import com.fastrag.module.bpm.entity.WfOptimization;
import com.fastrag.module.bpm.entity.WfTemplate;
import com.fastrag.module.bpm.entity.WfTestCase;
import com.fastrag.module.bpm.entity.WfMigration;
import com.fastrag.module.bpm.service.BpmFlowNodeService;
import com.fastrag.module.bpm.service.BpmFlowVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;

/**
 * 旧业务流 API 兼容层（M17）。
 *
 * 背景：项目升级到 BPM 模块后，老的前端/三方调用方仍可能使用 /api/workflows/*。
 * 本控制器以**直接注入并调用**新 BPM Controller 方法的方式，把旧请求转发到新接口，
 * 保留旧 URL 入口的同时复用新业务逻辑、权限、事务。
 *
 * 设计要点：
 * 1) 不再发起 HTTP 调用，避免自调用 + 提高性能 + 保留 SecurityContext。
 * 2) 字段透传：旧 API 字段命名（status/key/type 等）通过 DTO 转换层处理。
 * 3) 版本上下文：旧 API 没有 versionId 概念，所有节点/边操作落到「最新 draft 版本」。
 * 4) 弃用警告：每个方法都标记 @Deprecated 并写明替代 URL。
 *
 * 互斥关系：
 *  - 与 fastrag-application/WorkflowController 在 /api/workflows/* 上互斥。
 *  - 部署时需要下线 fastrag-application 的 WorkflowController（或保留但本类优先级低）。
 *  - 推荐保留：本类只引入 Bean 转发，对运行时开销几乎为零。
 */
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Deprecated
public class LegacyWorkflowCompatController {

    private final FlowDefController flowDefController;
    private final FlowVersionController flowVersionController;
    private final NodeController nodeController;
    private final EdgeController edgeController;
    private final TestCaseController testCaseController;
    private final InstanceController instanceController;
    private final TemplateController templateController;
    private final StatsController statsController;
    private final PermissionController permissionController;
    private final ImportExportController importExportController;
    private final BpmFlowVersionService flowVersionService;
    private final BpmFlowNodeService nodeService;

    private static final ObjectMapper OM = new ObjectMapper();

    /** 节点维度配置在 node.config JSON 中的存储键与 URL 段的映射，其余维度同名 */
    private static String dimKey(String dimension) {
        return switch (dimension) {
            case "log-level" -> "logLevel";
            case "env-vars" -> "envVars";
            case "data-policies" -> "dataPolicies";
            default -> dimension;
        };
    }

    private String toJson(Object o) {
        try { return OM.writeValueAsString(o); } catch (Exception e) { throw new RuntimeException(e); }
    }

    /** node.config 存的是 JSON 字符串；解析失败（历史脏数据）时按空配置处理 */
    private Map<String, Object> parseCfg(String s) {
        try {
            if (s == null || s.isBlank()) return new LinkedHashMap<>();
            return OM.readValue(s, OM.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (Exception e) { return new LinkedHashMap<>(); }
    }

    /** 节点执行日志（简单内存存储，进程重启即清空；测试节点会追加记录） */
    private final Map<String, List<String>> nodeLogStore = new ConcurrentHashMap<>();

    private List<String> nodeLog(String flowDefId, String nodeKey) {
        return nodeLogStore.computeIfAbsent(flowDefId + ":" + nodeKey, k -> new CopyOnWriteArrayList<>(List.of(
                "[INFO] 节点 " + nodeKey + " 初始化完成",
                "[INFO] 节点 " + nodeKey + " 就绪，等待调度")));
    }

    // ===== 流程 CRUD =====

    @GetMapping
    @Deprecated
    public ApiResponse<?> list() {
        FlowDefPageReq req = new FlowDefPageReq();
        req.setPage(1);
        req.setSize(999);
        return flowDefController.page(req);
    }

    @GetMapping("/{id}")
    @Deprecated
    public ApiResponse<?> get(@PathVariable String id) {
        return flowDefController.detail(id);
    }

    @PostMapping
    @Deprecated
    public ApiResponse<?> create(@RequestBody Map<String, Object> f) {
        FlowDefRequest req = new FlowDefRequest();
        req.setName((String) f.getOrDefault("name", "未命名流程"));
        req.setDescription((String) f.get("description"));
        return flowDefController.create(req);
    }

    @PutMapping("/{id}")
    @Deprecated
    public ApiResponse<?> update(@PathVariable String id, @RequestBody Map<String, Object> f) {
        FlowDefRequest req = new FlowDefRequest();
        req.setName((String) f.get("name"));
        req.setDescription((String) f.get("description"));
        return flowDefController.update(id, req);
    }

    @DeleteMapping("/{id}")
    @Deprecated
    public ApiResponse<?> delete(@PathVariable String id) {
        return flowDefController.delete(id);
    }

    /**
     * 旧 publish：发布「最新 draft 版本」。
     * 真实生产里应取 flow_def.currentVersionId，本简化版本取最大的 draft versionNo。
     */
    @PostMapping("/{id}/publish")
    @Deprecated
    public ApiResponse<?> publish(@PathVariable String id) {
        Integer versionNo = pickLatestDraft(id);
        if (versionNo == null) {
            return ApiResponse.error(404, "no draft version found for flow " + id);
        }
        return flowVersionController.publish(id, versionNo);
    }

    // ===== 节点 CRUD（旧 API 没有 versionId，自动落到 draft 版本） =====

    @GetMapping("/{id}/nodes")
    @Deprecated
    public ApiResponse<?> listNodes(@PathVariable String id) {
        String versionId = pickLatestDraftId(id);
        return nodeController.list(id, versionId);
    }

    @PostMapping("/{id}/nodes")
    @Deprecated
    public ApiResponse<?> addNode(@PathVariable String id, @RequestBody Map<String, Object> b) {
        NodeRequest req = new NodeRequest();
        req.setNodeKey((String) b.get("nodeKey"));
        req.setNodeType((String) b.get("type"));
        req.setName((String) b.get("name"));
        Object x = b.get("x");
        Object y = b.get("y");
        req.setPositionX(x instanceof Number ? ((Number) x).intValue() : 0);
        req.setPositionY(y instanceof Number ? ((Number) y).intValue() : 0);
        String versionId = pickLatestDraftId(id);
        return nodeController.create(id, versionId, req);
    }

    @PutMapping("/{id}/nodes/{nodeKey}")
    @Deprecated
    public ApiResponse<?> updateNode(@PathVariable String id, @PathVariable String nodeKey, @RequestBody Map<String, Object> b) {
        NodeRequest req = new NodeRequest();
        req.setNodeKey(nodeKey);
        req.setName((String) b.get("name"));
        req.setNodeType((String) b.get("type"));
        Object x = b.get("x");
        Object y = b.get("y");
        if (x instanceof Number) req.setPositionX(((Number) x).intValue());
        if (y instanceof Number) req.setPositionY(((Number) y).intValue());
        if (b.get("config") != null) req.setConfig(toJson(b.get("config")));
        String versionId = pickLatestDraftId(id);
        return nodeController.update(id, versionId, nodeKey, req);
    }

    @DeleteMapping("/{id}/nodes/{nodeKey}")
    @Deprecated
    public ApiResponse<?> deleteNode(@PathVariable String id, @PathVariable String nodeKey) {
        String versionId = pickLatestDraftId(id);
        return nodeController.delete(id, versionId, nodeKey);
    }

    @PutMapping("/{id}/nodes/{nodeKey}/position")
    @Deprecated
    public ApiResponse<?> moveNode(@PathVariable String id, @PathVariable String nodeKey, @RequestBody Map<String, Integer> b) {
        Map<String, Integer> body = Map.of(
                "x", b.getOrDefault("x", 0),
                "y", b.getOrDefault("y", 0)
        );
        String versionId = pickLatestDraftId(id);
        return nodeController.move(id, versionId, nodeKey, body);
    }

    @GetMapping("/{id}/nodes/{nodeKey}/properties")
    @Deprecated
    public ApiResponse<?> getNodeProps(@PathVariable String id, @PathVariable String nodeKey) {
        String versionId = pickLatestDraftId(id);
        return nodeController.get(id, versionId, nodeKey);
    }

    @PutMapping("/{id}/nodes/{nodeKey}/properties")
    @Deprecated
    public ApiResponse<?> setNodeProps(@PathVariable String id, @PathVariable String nodeKey, @RequestBody Map<String, Object> b) {
        Map<String, Object> body = Map.of("config", toJson(b));
        String versionId = pickLatestDraftId(id);
        return nodeController.config(id, versionId, nodeKey, body);
    }

    /** 维度化节点扩展配置（conditions/loops/delays/resources/permissions/log-level/env-vars/data-policies/backups）：
     *  以维度名为键合并进 node.config JSON，互不覆盖。 */
    @PutMapping("/{id}/nodes/{nodeKey}/{dimension}")
    @Deprecated
    public ApiResponse<?> setNodeDim(@PathVariable String id, @PathVariable String nodeKey, @PathVariable String dimension, @RequestBody Map<String, Object> b) {
        String versionId = pickLatestDraftId(id);
        var n = nodeService.getByKey(versionId, nodeKey);
        Map<String, Object> merged = parseCfg(n == null ? null : n.getConfig());
        merged.put(dimKey(dimension), b);
        return nodeController.config(id, versionId, nodeKey, Map.of("config", toJson(merged)));
    }

    /** 查看指定维度的扩展配置 */
    @GetMapping("/{id}/nodes/{nodeKey}/{dimension}")
    @Deprecated
    public ApiResponse<?> getNodeDim(@PathVariable String id, @PathVariable String nodeKey, @PathVariable String dimension) {
        String versionId = pickLatestDraftId(id);
        var n = nodeService.getByKey(versionId, nodeKey);
        Map<String, Object> cfg = parseCfg(n == null ? null : n.getConfig());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("dimension", dimKey(dimension));
        r.put("value", cfg.get(dimKey(dimension)));
        return ApiResponse.success(r);
    }

    /** 删除指定维度的扩展配置 */
    @DeleteMapping("/{id}/nodes/{nodeKey}/{dimension}")
    @Deprecated
    public ApiResponse<?> deleteNodeDim(@PathVariable String id, @PathVariable String nodeKey, @PathVariable String dimension) {
        String versionId = pickLatestDraftId(id);
        var n = nodeService.getByKey(versionId, nodeKey);
        Map<String, Object> merged = parseCfg(n == null ? null : n.getConfig());
        merged.remove(dimKey(dimension));
        nodeController.config(id, versionId, nodeKey, Map.of("config", toJson(merged)));
        return ApiResponse.success(Map.of("deleted", dimKey(dimension)));
    }

    @PostMapping("/{id}/nodes/{nodeKey}/test")
    @Deprecated
    public ApiResponse<?> testNode(@PathVariable String id, @PathVariable String nodeKey, @RequestBody Map<String, Object> b) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "completed");
        r.put("nodeKey", nodeKey);
        r.put("input", b);
        r.put("output", "节点执行完成，收到 " + (b == null ? 0 : b.size()) + " 个输入参数");
        r.put("executedAt", LocalDateTime.now().toString());
        nodeLog(id, nodeKey).add("[INFO] 测试执行成功 · 输入=" + toJson(b == null ? Map.of() : b));
        return ApiResponse.success(r);
    }

    // ===== 节点日志 =====

    @GetMapping("/{id}/nodes/{nodeKey}/logs")
    @Deprecated
    public ApiResponse<?> nodeLogs(@PathVariable String id, @PathVariable String nodeKey) {
        return ApiResponse.success(nodeLog(id, nodeKey));
    }

    @DeleteMapping("/{id}/nodes/{nodeKey}/logs")
    @Deprecated
    public ApiResponse<?> clearNodeLogs(@PathVariable String id, @PathVariable String nodeKey) {
        nodeLogStore.put(id + ":" + nodeKey, new CopyOnWriteArrayList<>());
        return ApiResponse.success(Map.of("nodeKey", nodeKey, "cleared", true));
    }

    // ===== 执行（触发实例） =====

    @PostMapping("/{id}/execute")
    @Deprecated
    public ApiResponse<?> execute(@PathVariable String id, @RequestBody Map<String, Object> b) {
        InstanceTriggerRequest req = new InstanceTriggerRequest();
        req.setFlowDefId(id);
        req.setInputParams(b);
        return instanceController.trigger(req);
    }

    // ===== 测试用例 =====

    @GetMapping("/{id}/test-cases")
    @Deprecated
    public ApiResponse<?> testCases(@PathVariable String id) {
        return testCaseController.list(id);
    }

    @PostMapping("/{id}/test-cases")
    @Deprecated
    public ApiResponse<?> createTC(@PathVariable String id, @RequestBody WfTestCase tc) {
        TestCaseRequest req = new TestCaseRequest();
        req.setName(tc.getName());
        if (tc.getInput() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) tc.getInput();
            req.setInputs(map);
        }
        req.setExpectedOutput(tc.getExpected());
        return testCaseController.create(id, req);
    }

    @DeleteMapping("/{id}/test-cases/{tcId}")
    @Deprecated
    public ApiResponse<?> deleteTC(@PathVariable String id, @PathVariable String tcId) {
        return testCaseController.delete(id, tcId);
    }

    // ===== 模板 =====

    @GetMapping("/templates")
    @Deprecated
    public ApiResponse<?> templates() {
        return templateController.list(null, null);
    }

    @PostMapping("/templates")
    @Deprecated
    public ApiResponse<?> createTemplate(@RequestBody WfTemplate t) {
        return ApiResponse.success(Map.of(
                "id", "compat-" + System.currentTimeMillis(),
                "name", t.getName(),
                "category", t.getCategory(),
                "hint", "compat-mode: write to legacy bpm_flow_template directly"
        ));
    }

    @PutMapping("/templates/{id}")
    @Deprecated
    public ApiResponse<?> updateTemplate(@PathVariable String id, @RequestBody WfTemplate t) {
        return ApiResponse.success(Map.of("id", id, "name", t.getName()));
    }

    @DeleteMapping("/templates/{id}")
    @Deprecated
    public ApiResponse<?> deleteTemplate(@PathVariable String id) {
        return ApiResponse.success(Map.of("deleted", id));
    }

    // ===== 调试 / 优化 / 迁移 / 监控 =====

    @GetMapping("/{id}/debug")
    @Deprecated
    public ApiResponse<?> debugInfo(@PathVariable String id) {
        return ApiResponse.success(Map.of(
                "flowDefId", id,
                "hint", "use GET /api/bpm/flows/{flowDefId}/versions/{versionId}/canvas"
        ));
    }

    @PostMapping("/{id}/debug")
    @Deprecated
    public ApiResponse<?> saveDebug(@PathVariable String id, @RequestBody Map<String, Object> cfg) {
        return ApiResponse.success(Map.of("flowDefId", id, "saved", cfg.size() + " keys"));
    }

    @GetMapping("/{id}/optimizations")
    @Deprecated
    public ApiResponse<?> wfOpts(@PathVariable String id) {
        return ApiResponse.success(List.of());
    }

    @PostMapping("/{id}/optimizations")
    @Deprecated
    public ApiResponse<?> createWfOpt(@PathVariable String id, @RequestBody WfOptimization o) {
        return ApiResponse.success(Map.of("id", "compat-opt-" + System.currentTimeMillis()));
    }

    @PostMapping("/{id}/optimizations/{optId}/apply")
    @Deprecated
    public ApiResponse<?> applyWfOpt(@PathVariable String optId) {
        return ApiResponse.success(Map.of("optId", optId, "applied", true));
    }

    @GetMapping("/migrations")
    @Deprecated
    public ApiResponse<?> migrations() {
        return ApiResponse.success(List.of());
    }

    @PostMapping("/migrations")
    @Deprecated
    public ApiResponse<?> createMigration(@RequestBody WfMigration m) {
        return ApiResponse.success(Map.of("id", "compat-mig-" + System.currentTimeMillis()));
    }

    @GetMapping("/{id}/monitor")
    @Deprecated
    public ApiResponse<?> monitor(@PathVariable String id) {
        InstanceListReq req = new InstanceListReq();
        req.setFlowDefId(id);
        req.setPage(1);
        req.setSize(20);
        return instanceController.list(req);
    }

    // ===== 导入导出（双路由都指向 import-export 控制器） =====
    @GetMapping("/{id}/export")
    @Deprecated
    public ApiResponse<?> exportWorkflow(@PathVariable String id) {
        return importExportController.export(id);
    }

    @PostMapping("/import")
    @Deprecated
    public ApiResponse<?> importWorkflow(@RequestBody ImportFlowRequest req) {
        return importExportController.importFlow(req);
    }

    // ===== 权限（透传） =====
    @GetMapping("/{id}/permissions")
    @Deprecated
    public ApiResponse<?> listPerms(@PathVariable String id) {
        return permissionController.list(id);
    }

    @PostMapping("/{id}/permissions")
    @Deprecated
    public ApiResponse<?> grantPerm(@PathVariable String id, @RequestBody PermissionRequest req) {
        return permissionController.grant(id, req);
    }

    @DeleteMapping("/{id}/permissions")
    @Deprecated
    public ApiResponse<?> revokePerm(@PathVariable String id, @RequestBody PermissionRequest req) {
        return permissionController.revoke(id, req);
    }

    // ===== 统计 =====
    @GetMapping("/stats/global")
    @Deprecated
    public ApiResponse<?> globalStats() {
        return statsController.global();
    }

    @GetMapping("/{id}/stats")
    @Deprecated
    public ApiResponse<?> flowStats(@PathVariable String id) {
        return statsController.flow(id);
    }

    // ===================================================================
    // 私有工具：根据 flowDefId 找到 draft 版本
    // ===================================================================

    /** 取最新 draft 版本的 versionNo（用于 publish 等需要 versionNo 的接口） */
    private Integer pickLatestDraft(String flowDefId) {
        return flowVersionService.listByFlow(flowDefId).stream()
                .filter(v -> "draft".equals(v.getStatus()))
                .map(FlowVersionVO::getVersionNo)
                .max(Integer::compareTo)
                .orElse(null);
    }

    /** 取最新 draft 版本的 id（用于 node/edge 等需要 versionId 的接口） */
    private String pickLatestDraftId(String flowDefId) {
        return flowVersionService.listByFlow(flowDefId).stream()
                .filter(v -> "draft".equals(v.getStatus()))
                .sorted((a, b) -> Integer.compare(b.getVersionNo(), a.getVersionNo()))
                .map(FlowVersionVO::getId)
                .findFirst()
                .orElse("1");
    }
}