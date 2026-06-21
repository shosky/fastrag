package com.fastrag.module.publish.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class LogController {
    private final LogService svc;
    @GetMapping("/api/kb/{kbId}/logs") public ApiResponse<?> logs(@PathVariable String kbId,@RequestParam(required=false) String category) { return ApiResponse.success(svc.listLogs(kbId,category)); }
    @GetMapping("/api/kb/{kbId}/update-logs") public ApiResponse<?> updateLogs(@PathVariable String kbId,@RequestParam(required=false) String type) { return ApiResponse.success(svc.listUpdateLogs(kbId,type)); }
}
