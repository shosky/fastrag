package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.dto.ImportFlowRequest;
import com.fastrag.module.bpm.service.BpmImportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController @RequiredArgsConstructor
public class ImportExportController {
    private final BpmImportExportService svc;
    @GetMapping("/api/bpm/flows/{flowDefId}/export") public ApiResponse<?> export(@PathVariable String flowDefId) {
        return ApiResponse.success(svc.export(flowDefId, currentUserId()));
    }
    @PostMapping("/api/bpm/flows/import") public ApiResponse<?> importFlow(@RequestBody ImportFlowRequest req) {
        String id = svc.importFlow(req, currentUserId());
        return ApiResponse.success(Map.of("flowDefId", id));
    }
    private String currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }
}