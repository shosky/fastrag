package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.dto.NodeRequest;
import com.fastrag.module.bpm.service.BpmFlowNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/bpm/flows/{flowDefId}/versions/{versionId}/nodes") @RequiredArgsConstructor
public class NodeController {
    private final BpmFlowNodeService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String flowDefId, @PathVariable String versionId) { return ApiResponse.success(svc.listByVersion(versionId)); }
    @GetMapping("/{nodeKey}") public ApiResponse<?> get(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String nodeKey) { return ApiResponse.success(svc.getByKey(versionId, nodeKey)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String flowDefId, @PathVariable String versionId, @RequestBody NodeRequest req) { return ApiResponse.success(svc.create(versionId, req)); }
    @PutMapping("/{nodeKey}") public ApiResponse<?> update(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String nodeKey, @RequestBody NodeRequest req) { return ApiResponse.success(svc.update(versionId, nodeKey, req)); }
    @PutMapping("/{nodeKey}/position") public ApiResponse<?> move(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String nodeKey, @RequestBody java.util.Map<String, Integer> body) { return ApiResponse.success(svc.move(versionId, nodeKey, body.getOrDefault("x", 0), body.getOrDefault("y", 0))); }
    @PutMapping("/{nodeKey}/config") public ApiResponse<?> config(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String nodeKey, @RequestBody java.util.Map<String, Object> body) {
        Object c = body.get("config");
        return ApiResponse.success(svc.updateConfig(versionId, nodeKey, c == null ? null : c.toString()));
    }
    @DeleteMapping("/{nodeKey}") public ApiResponse<?> delete(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String nodeKey) { svc.delete(versionId, nodeKey); return ApiResponse.success(); }
}