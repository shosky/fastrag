package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.dto.VersionCreateRequest;
import com.fastrag.module.bpm.service.BpmFlowVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController @RequestMapping("/api/bpm/flows/{flowDefId}/versions") @RequiredArgsConstructor
public class FlowVersionController {
    private final BpmFlowVersionService svc;

    @GetMapping public ApiResponse<?> list(@PathVariable String flowDefId) { return ApiResponse.success(svc.listByFlow(flowDefId)); }
    @GetMapping("/{versionNo}") public ApiResponse<?> detail(@PathVariable String flowDefId, @PathVariable Integer versionNo) {
        return ApiResponse.success(svc.detail(flowDefId, versionNo));
    }
    @PostMapping public ApiResponse<?> create(@PathVariable String flowDefId, @RequestBody(required = false) VersionCreateRequest req) {
        Integer nextNo = svc.createDraft(flowDefId, req == null ? new VersionCreateRequest() : req);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("flowDefId", flowDefId);
        r.put("versionNo", nextNo);
        return ApiResponse.success(r);
    }
    @PostMapping("/{versionNo}/publish") public ApiResponse<?> publish(@PathVariable String flowDefId, @PathVariable Integer versionNo) {
        return ApiResponse.success(svc.publish(flowDefId, versionNo, currentUserId()));
    }
    @PostMapping("/{versionNo}/rollback") public ApiResponse<?> rollback(@PathVariable String flowDefId, @PathVariable Integer versionNo) {
        return ApiResponse.success(svc.rollback(flowDefId, versionNo));
    }
    @PostMapping("/{versionNo}/archive") public ApiResponse<?> archive(@PathVariable String flowDefId, @PathVariable Integer versionNo) {
        svc.archive(flowDefId, versionNo);
        return ApiResponse.success();
    }
    private String currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }
}