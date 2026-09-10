package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.model.KbCreateRequest;
import com.fastrag.module.knowledge.service.KbService; import com.fastrag.security.util.SecurityUtil;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/kb") @RequiredArgsConstructor
public class KbController {
    private final KbService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String category,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) { return ApiResponse.success(svc.list(keyword,category,page,pageSize)); }
    @GetMapping("/categories") public ApiResponse<?> categories() { return ApiResponse.success(svc.getCategories()); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@Valid @RequestBody KbCreateRequest req) { return ApiResponse.success(svc.create(req,SecurityUtil.getCurrentUserId())); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@Valid @RequestBody KbCreateRequest req) { return ApiResponse.success(svc.update(id,req)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    // 知识库导出 / 导入（导出包含元数据与文档全文，导入后自动重新解析嵌入）
    @GetMapping("/{id}/export") public ApiResponse<?> exportKb(@PathVariable String id) { return ApiResponse.success(svc.exportKb(id)); }
    @PostMapping("/import") public ApiResponse<?> importKb(@RequestBody java.util.Map<String,Object> data) { return ApiResponse.success(svc.importKb(data, SecurityUtil.getCurrentUserId())); }
}
