package com.fastrag.module.publish.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LogController {

    private final LogService svc;

    @GetMapping("/api/kb/{kbId}/logs")
    public ApiResponse<?> logs(@PathVariable String kbId, @RequestParam(required = false) String category) {
        return ApiResponse.success(svc.listLogs(kbId, category));
    }

    @GetMapping("/api/kb/{kbId}/update-logs")
    public ApiResponse<?> updateLogs(@PathVariable String kbId, @RequestParam(required = false) String type) {
        return ApiResponse.success(svc.listUpdateLogs(kbId, type));
    }

    @PostMapping("/api/kb/{kbId}/logs")
    public ApiResponse<?> addLog(@PathVariable String kbId, @RequestBody Map<String, String> body) {
        String username = SecurityUtil.getCurrentUser() != null ? SecurityUtil.getCurrentUser().getUsername() : "system";
        svc.addLog(
                kbId,
                body.get("category"),
                body.get("action"),
                body.get("target"),
                body.get("detail"),
                body.getOrDefault("operator", username)
        );
        return ApiResponse.success();
    }

    @PostMapping("/api/kb/{kbId}/update-logs")
    public ApiResponse<?> addUpdateLog(@PathVariable String kbId, @RequestBody Map<String, String> body) {
        String username = SecurityUtil.getCurrentUser() != null ? SecurityUtil.getCurrentUser().getUsername() : "system";
        svc.addUpdateLog(
                kbId,
                body.get("updateType"),
                body.get("target"),
                body.get("detail"),
                body.getOrDefault("operator", username)
        );
        return ApiResponse.success();
    }
}
