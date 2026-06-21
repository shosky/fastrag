package com.fastrag.module.graph.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.graph.service.EvaluationService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}/evaluations") @RequiredArgsConstructor
public class EvaluationController {
    private final EvaluationService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId) { return ApiResponse.success(svc.list(kbId)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String kbId,@PathVariable String id) { return ApiResponse.success(svc.getDetail(kbId,id)); }
    @PostMapping("/run") public ApiResponse<?> run(@PathVariable String kbId,@RequestBody Map<String,Object> config) { svc.run(kbId,config); return ApiResponse.success(); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String kbId,@PathVariable String id) { svc.delete(kbId,id); return ApiResponse.success(); }
}
