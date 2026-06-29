package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbQaExtractTask;
import com.fastrag.module.knowledge.service.QaExtractService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/kb/{kbId}/qa-extract") @RequiredArgsConstructor
public class QaExtractController {
    private final QaExtractService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String status) { return ApiResponse.success(svc.list(kbId,status)); }
    @GetMapping("/{taskId}") public ApiResponse<?> get(@PathVariable String taskId) { return ApiResponse.success(svc.get(taskId)); }
    @PostMapping("/start") public ApiResponse<?> start(@PathVariable String kbId,@RequestBody KbQaExtractTask task) { task.setKbId(kbId); return ApiResponse.success(svc.start(task)); }
    @PostMapping("/{taskId}/stop") public ApiResponse<?> stop(@PathVariable String taskId) { return ApiResponse.success(svc.stop(taskId)); }
    @PutMapping("/{taskId}") public ApiResponse<?> update(@PathVariable String taskId,@RequestBody KbQaExtractTask task) { return ApiResponse.success(svc.update(taskId,task)); }
    @DeleteMapping("/{taskId}") public ApiResponse<?> delete(@PathVariable String taskId) { svc.delete(taskId); return ApiResponse.success(); }
}
