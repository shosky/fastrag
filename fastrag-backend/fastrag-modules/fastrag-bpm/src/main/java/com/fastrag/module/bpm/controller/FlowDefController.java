package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.dto.*;
import com.fastrag.module.bpm.service.BpmFlowDefService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/** 流程定义 CRUD 与基础查询(对应 §11.1.1 flows) */
@RestController @RequestMapping("/api/bpm/flows") @RequiredArgsConstructor
public class FlowDefController {
    private final BpmFlowDefService svc;

    @GetMapping public ApiResponse<?> page(FlowDefPageReq req) { return ApiResponse.success(svc.page(req)); }
    @GetMapping("/list") public ApiResponse<?> listSimple(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String visibility) {
        return ApiResponse.success(svc.listSimple(keyword, visibility));
    }
    @GetMapping("/{id}") public ApiResponse<?> detail(@PathVariable String id) { return ApiResponse.success(svc.detail(id)); }
    @PostMapping public ApiResponse<?> create(@Valid @RequestBody FlowDefRequest req) {
        return ApiResponse.success(svc.create(req, currentUserId()));
    }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id, @Valid @RequestBody FlowDefRequest req) {
        return ApiResponse.success(svc.update(id, req));
    }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/copy") public ApiResponse<?> copy(@PathVariable String id, @RequestBody(required = false) java.util.Map<String, Object> body) {
        String newName = body == null ? null : (String) body.get("name");
        return ApiResponse.success(svc.copy(id, newName, currentUserId()));
    }

    private String currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonymous" : auth.getName();
    }
}