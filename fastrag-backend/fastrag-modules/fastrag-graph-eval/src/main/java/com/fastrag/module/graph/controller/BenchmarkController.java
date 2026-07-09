package com.fastrag.module.graph.controller;

import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.graph.model.BenchmarkConfig;
import com.fastrag.module.graph.model.BenchmarkCreateForm;
import com.fastrag.module.graph.service.BenchmarkService;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kb/{kbId}/benchmarks")
@RequiredArgsConstructor
public class BenchmarkController {

    private final BenchmarkService svc;
    private final LogService logService;

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

    @Loggable(category = LogCategory.operation, action = ActionType.benchmark_created, detail = "创建基准测试")
    @PostMapping
    public ApiResponse<?> create(@PathVariable String kbId, @RequestBody BenchmarkCreateForm form) {
        return ApiResponse.success(svc.create(kbId, form));
    }

    @Loggable(category = LogCategory.operation, action = ActionType.benchmark_generated, detail = "生成基准测试题目")
    @PostMapping("/generate")
    public ApiResponse<?> generate(@PathVariable String kbId, @RequestBody BenchmarkConfig config) {
        return ApiResponse.success(svc.generate(kbId, config));
    }

    @Loggable(category = LogCategory.operation, action = ActionType.benchmark_deleted, detail = "删除基准测试")
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId, @PathVariable String id) {
        svc.delete(kbId, id);
        return ApiResponse.success();
    }
}
