package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbMatterKnowledgeRel;
import com.fastrag.module.knowledge.service.MatterKnowledgeRelService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
// 事项知识关联管理：新增/查看/编辑/删除
@RestController @RequestMapping("/api/kb/{kbId}/matter-knowledge-rels") @RequiredArgsConstructor
public class MatterKnowledgeRelController {
    private final MatterKnowledgeRelService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String matterName,@RequestParam(required=false) String keyword) { return ApiResponse.success(svc.list(kbId,matterName,keyword)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbMatterKnowledgeRel rel) { return ApiResponse.success(svc.create(kbId,rel)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody KbMatterKnowledgeRel rel) { return ApiResponse.success(svc.update(id,rel)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
}
