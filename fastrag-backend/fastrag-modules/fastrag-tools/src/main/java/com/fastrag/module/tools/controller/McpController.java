package com.fastrag.module.tools.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.tools.service.McpServiceService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/mcp-services") @RequiredArgsConstructor
public class McpController {
    private final McpServiceService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword) { return ApiResponse.success(svc.list(keyword)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.update(id,f)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/toggle") public ApiResponse<?> toggle(@PathVariable String id) { svc.toggleEnabled(id); return ApiResponse.success(); }
    @GetMapping("/{id}/tools") public ApiResponse<?> tools(@PathVariable String id) { return ApiResponse.success(svc.listTools(id)); }
}
