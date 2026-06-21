package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.model.QaCreateRequest;
import com.fastrag.module.knowledge.service.QaPairService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}/qa-pairs") @RequiredArgsConstructor
public class QaPairController {
    private final QaPairService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId) { return ApiResponse.success(svc.list(kbId)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@Valid @RequestBody QaCreateRequest req) { return ApiResponse.success(svc.create(kbId,req)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String kbId,@PathVariable String id,@RequestBody Map<String,Object> p) { return ApiResponse.success(svc.update(kbId,id,p)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String kbId,@PathVariable String id) { svc.delete(kbId,id); return ApiResponse.success(); }
    @PostMapping("/{id}/confirm") public ApiResponse<?> confirm(@PathVariable String kbId,@PathVariable String id) { svc.confirm(kbId,id); return ApiResponse.success(); }
    @PostMapping("/qa-extract") public ApiResponse<?> extract(@PathVariable String kbId,@RequestBody Map<String,List<String>> b) { return ApiResponse.success(svc.extractQa(kbId,b.get("fileIds"))); }
}
