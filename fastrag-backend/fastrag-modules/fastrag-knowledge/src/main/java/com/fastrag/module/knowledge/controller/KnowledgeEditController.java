package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbKnowledgeEdit;
import com.fastrag.module.knowledge.service.KnowledgeEditService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}/knowledge-edits") @RequiredArgsConstructor
public class KnowledgeEditController {
    private final KnowledgeEditService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String status,@RequestParam(required=false) String editor) { return ApiResponse.success(svc.list(kbId,status,editor)); }
    @GetMapping("/export") public ApiResponse<?> export(@PathVariable String kbId,@RequestParam(required=false) String ids,@RequestParam(required=false) String status,@RequestParam(required=false) String editor) { return ApiResponse.success(svc.export(kbId,ids,status,editor)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbKnowledgeEdit edit) { edit.setKbId(kbId); return ApiResponse.success(svc.create(edit)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody KbKnowledgeEdit edit) { return ApiResponse.success(svc.update(id,edit)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/submit") public ApiResponse<?> submit(@PathVariable String id) { return ApiResponse.success(svc.submit(id)); }
    @PostMapping("/{id}/approve") public ApiResponse<?> approve(@PathVariable String id,@RequestBody Map<String,String> body) { return ApiResponse.success(svc.approve(id,body.getOrDefault("reviewer","admin"))); }
    @PostMapping("/{id}/reject") public ApiResponse<?> reject(@PathVariable String id,@RequestBody Map<String,String> body) { return ApiResponse.success(svc.reject(id,body.getOrDefault("reviewer","admin"),body.get("comment"))); }
}
