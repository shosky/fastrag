package com.fastrag.module.application.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.application.service.AppService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/apps") @RequiredArgsConstructor
public class AppController {
    private final AppService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String tag) { return ApiResponse.success(svc.list(keyword,tag)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.update(id,f)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @GetMapping("/templates") public ApiResponse<?> templates() { return ApiResponse.success(svc.getTemplates()); }
    @PostMapping("/{id}/run") public ApiResponse<?> run(@PathVariable String id,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.run(id,(String)b.get("query"))); }
}
