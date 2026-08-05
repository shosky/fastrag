package com.fastrag.module.application.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.application.service.AppService;
import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
@RestController @RequestMapping("/api/apps") @RequiredArgsConstructor
public class AppController {
    private final AppService svc;
    @PreAuthorize("@perm.has('app:use')") @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String tag) { return ApiResponse.success(svc.list(keyword,tag)); }
    @PreAuthorize("@perm.has('app:use')") @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PreAuthorize("@perm.has('app:create')")
    @Loggable(category=LogCategory.operation,action=ActionType.app_created)
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @PreAuthorize("@perm.has('app:edit')")
    @Loggable(category=LogCategory.operation,action=ActionType.app_updated,target="#id")
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.update(id,f)); }
    @PreAuthorize("@perm.has('app:delete')")
    @Loggable(category=LogCategory.operation,action=ActionType.app_deleted,target="#id")
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PreAuthorize("@perm.has('app:use')") @GetMapping("/templates") public ApiResponse<?> templates() { return ApiResponse.success(svc.getTemplates()); }
    @PreAuthorize("@perm.has('app:use')") @PostMapping("/{id}/run") public ApiResponse<?> run(@PathVariable String id,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.run(id,(String)b.get("query"))); }
}
