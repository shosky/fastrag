package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.dto.PermissionRequest;
import com.fastrag.module.bpm.service.BpmPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController @RequestMapping("/api/bpm/flows/{flowDefId}/permissions") @RequiredArgsConstructor
public class PermissionController {
    private final BpmPermissionService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String flowDefId) { return ApiResponse.success(svc.listByFlow(flowDefId)); }
    @PostMapping public ApiResponse<?> grant(@PathVariable String flowDefId, @RequestBody PermissionRequest req) {
        svc.grant(flowDefId, req, currentUserId());
        return ApiResponse.success();
    }
    @DeleteMapping public ApiResponse<?> revoke(@PathVariable String flowDefId, @RequestBody PermissionRequest req) {
        svc.revoke(flowDefId, req.getSubjectType(), req.getSubjectId(), req.getPermission());
        return ApiResponse.success();
    }
    @GetMapping("/check") public ApiResponse<?> check(@PathVariable String flowDefId, @RequestParam(required = false, defaultValue = "view") String permission) {
        boolean ok = svc.hasPermission(flowDefId, currentUserId(), permission);
        return ApiResponse.success(Map.of("hasPermission", ok));
    }
    private String currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }
}