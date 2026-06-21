package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.model.ParseStrategyRequest;
import com.fastrag.module.knowledge.service.ParseStrategyService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/kb/{kbId}/parse-strategies") @RequiredArgsConstructor
public class ParseStrategyController {
    private final ParseStrategyService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId) { return ApiResponse.success(svc.list(kbId)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String kbId,@PathVariable String id) { return ApiResponse.success(svc.get(kbId,id)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody ParseStrategyRequest req) { return ApiResponse.success(svc.create(kbId,req)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String kbId,@PathVariable String id,@RequestBody ParseStrategyRequest req) { return ApiResponse.success(svc.update(kbId,id,req)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String kbId,@PathVariable String id) { svc.delete(kbId,id); return ApiResponse.success(); }
    @PostMapping("/{id}/set-default") public ApiResponse<?> setDefault(@PathVariable String kbId,@PathVariable String id) { svc.setDefault(kbId,id); return ApiResponse.success(); }
    @GetMapping("/resolve") public ApiResponse<?> resolve(@PathVariable String kbId,@RequestParam String extension) { return ApiResponse.success(svc.resolveByExtension(kbId,extension)); }
    @PostMapping("/conflicts") public ApiResponse<?> conflicts(@PathVariable String kbId,@RequestBody java.util.Map<String,Object> b) { return ApiResponse.success(svc.detectConflicts(kbId,(List<String>)b.get("extensions"),(String)b.get("excludeId"))); }
}
