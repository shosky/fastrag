package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.dto.EdgeRequest;
import com.fastrag.module.bpm.service.BpmFlowEdgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/bpm/flows/{flowDefId}/versions/{versionId}/edges") @RequiredArgsConstructor
public class EdgeController {
    private final BpmFlowEdgeService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String flowDefId, @PathVariable String versionId) { return ApiResponse.success(svc.listByVersion(versionId)); }
    @GetMapping("/{edgeId}") public ApiResponse<?> get(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String edgeId) { return ApiResponse.success(svc.get(edgeId)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String flowDefId, @PathVariable String versionId, @RequestBody EdgeRequest req) { return ApiResponse.success(svc.create(versionId, req)); }
    @PutMapping("/{edgeId}") public ApiResponse<?> update(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String edgeId, @RequestBody EdgeRequest req) { return ApiResponse.success(svc.update(edgeId, req)); }
    @DeleteMapping("/{edgeId}") public ApiResponse<?> delete(@PathVariable String flowDefId, @PathVariable String versionId, @PathVariable String edgeId) { svc.delete(edgeId); return ApiResponse.success(); }
}