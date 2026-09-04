package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.dto.TestCaseRequest;
import com.fastrag.module.bpm.service.BpmTestCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/bpm/flows/{flowDefId}/test-cases") @RequiredArgsConstructor
public class TestCaseController {
    private final BpmTestCaseService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String flowDefId) { return ApiResponse.success(svc.listByFlow(flowDefId)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String flowDefId, @RequestBody TestCaseRequest req) {
        return ApiResponse.success(svc.create(flowDefId, req, currentUserId()));
    }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String flowDefId, @PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/run") public ApiResponse<?> run(@PathVariable String flowDefId, @PathVariable String id) {
        return ApiResponse.success(svc.run(id));
    }
    private String currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }
}