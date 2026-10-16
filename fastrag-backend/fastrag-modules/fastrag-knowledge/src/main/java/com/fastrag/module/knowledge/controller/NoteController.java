package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbNote;
import com.fastrag.module.knowledge.service.NoteService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}/notes") @RequiredArgsConstructor
public class NoteController {
    private final NoteService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String keyword) { return ApiResponse.success(svc.list(kbId,keyword)); }
    // 导出为 CSV 文件流（真实文件下载）：支持按 ids 勾选导出，无 ids 时全量导出
    @GetMapping("/export") public void export(@PathVariable String kbId,@RequestParam(required=false) String ids,HttpServletResponse resp) throws Exception { svc.exportCsv(kbId,ids,resp); }
    // 批量导入笔记（JSON 数组），与 /knowledge-edits/import 同构
    @PostMapping("/import") public ApiResponse<?> importNotes(@PathVariable String kbId,@RequestBody List<KbNote> items) {
        return ApiResponse.success(Map.of("imported",svc.importNotes(kbId,items)));
    }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbNote note) { note.setKbId(kbId); return ApiResponse.success(svc.create(note)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody KbNote note) { return ApiResponse.success(svc.update(id,note)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
}
