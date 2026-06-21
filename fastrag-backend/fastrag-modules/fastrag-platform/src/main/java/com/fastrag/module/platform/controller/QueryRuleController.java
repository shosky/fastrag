package com.fastrag.module.platform.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.platform.service.QueryRuleService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/query-rules") @RequiredArgsConstructor
public class QueryRuleController {
    private final QueryRuleService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String type) { return ApiResponse.success(svc.list(type)); }
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/toggle") public ApiResponse<?> toggle(@PathVariable String id) { svc.toggle(id); return ApiResponse.success(); }
}
