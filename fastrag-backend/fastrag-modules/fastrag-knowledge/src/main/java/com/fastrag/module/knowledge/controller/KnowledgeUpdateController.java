package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbKnowledgeUpdate;
import com.fastrag.module.knowledge.service.KnowledgeUpdateService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/kb/{kbId}/knowledge-updates") @RequiredArgsConstructor
public class KnowledgeUpdateController {
    private final KnowledgeUpdateService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String knowledgeId,@RequestParam(required=false) String updateType,
        @RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.success(svc.page(kbId,knowledgeId,updateType,status,page,pageSize));
    }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbKnowledgeUpdate update) { update.setKbId(kbId); return ApiResponse.success(svc.create(update)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody KbKnowledgeUpdate update) { return ApiResponse.success(svc.update(id,update)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/apply") public ApiResponse<?> apply(@PathVariable String id) { return ApiResponse.success(svc.apply(id)); }
    @PostMapping("/{id}/rollback") public ApiResponse<?> rollback(@PathVariable String id) { return ApiResponse.success(svc.rollback(id)); }
}
