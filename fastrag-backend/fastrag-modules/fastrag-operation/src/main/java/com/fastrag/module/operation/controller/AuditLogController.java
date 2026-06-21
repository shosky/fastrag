package com.fastrag.module.operation.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.operation.service.AuditLogService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/audit/system-log") @RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String module,@RequestParam(required=false) Integer limit) { return ApiResponse.success(svc.list(module,limit)); }
}
