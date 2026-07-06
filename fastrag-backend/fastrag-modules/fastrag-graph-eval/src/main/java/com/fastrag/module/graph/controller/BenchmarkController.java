package com.fastrag.module.graph.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.graph.model.BenchmarkConfig;
import com.fastrag.module.graph.model.BenchmarkCreateForm;
import com.fastrag.module.graph.service.BenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kb/{kbId}/benchmarks")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService svc;

    @GetMapping
    public ApiResponse<?> list(@PathVariable String kbId) {
        return ApiResponse.success(svc.list(kbId));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.getDetail(kbId, id));
    }

    @GetMapping("/{id}/questions")
    public ApiResponse<?> getQuestions(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.listQuestions(kbId, id));
    }

    @PostMapping
    public ApiResponse<?> create(@PathVariable String kbId, @RequestBody BenchmarkCreateForm form) {
        return ApiResponse.success(svc.create(kbId, form));
    }

    @PostMapping("/generate")
    public ApiResponse<?> generate(@PathVariable String kbId, @RequestBody BenchmarkConfig config) {
        return ApiResponse.success(svc.generate(kbId, config));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId, @PathVariable String id) {
        svc.delete(kbId, id);
        return ApiResponse.success();
    }
}
