package com.fastrag.module.graph.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.graph.service.BenchmarkService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}/benchmarks") @RequiredArgsConstructor
public class BenchmarkController {
    private final BenchmarkService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId) { return ApiResponse.success(svc.list(kbId)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String kbId,@PathVariable String id) { return ApiResponse.success(svc.getDetail(kbId,id)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody Map<String,Object> form) { return ApiResponse.success(svc.create(kbId,form)); }
    @PostMapping("/generate") public ApiResponse<?> generate(@PathVariable String kbId,@RequestBody Map<String,Object> config) { svc.generate(kbId,config); return ApiResponse.success(); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String kbId,@PathVariable String id) { svc.delete(kbId,id); return ApiResponse.success(); }
}
