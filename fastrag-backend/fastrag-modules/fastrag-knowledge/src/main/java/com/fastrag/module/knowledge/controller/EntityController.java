package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbEntity;
import com.fastrag.module.knowledge.service.EntityService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/kb/{kbId}/entities") @RequiredArgsConstructor
public class EntityController {
    private final EntityService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String keyword) { return ApiResponse.success(svc.list(kbId,keyword)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbEntity entity) { entity.setKbId(kbId); return ApiResponse.success(svc.create(entity)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody KbEntity entity) { return ApiResponse.success(svc.update(id,entity)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
}
