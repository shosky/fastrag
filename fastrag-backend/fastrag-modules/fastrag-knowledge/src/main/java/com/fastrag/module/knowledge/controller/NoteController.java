package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbNote;
import com.fastrag.module.knowledge.service.NoteService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/kb/{kbId}/notes") @RequiredArgsConstructor
public class NoteController {
    private final NoteService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String keyword) { return ApiResponse.success(svc.list(kbId,keyword)); }
    @GetMapping("/export") public ApiResponse<?> export(@PathVariable String kbId,@RequestParam(required=false) String ids) { return ApiResponse.success(svc.export(kbId,ids)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbNote note) { note.setKbId(kbId); return ApiResponse.success(svc.create(note)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody KbNote note) { return ApiResponse.success(svc.update(id,note)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
}
