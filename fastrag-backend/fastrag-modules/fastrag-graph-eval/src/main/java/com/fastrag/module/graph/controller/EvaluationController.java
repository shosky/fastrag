package com.fastrag.module.graph.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.graph.entity.KbEvaluation;
import com.fastrag.module.graph.model.EvaluationConfig;
import com.fastrag.module.graph.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/kb/{kbId}/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService svc;

    @GetMapping
    public ApiResponse<?> list(@PathVariable String kbId) {
        return ApiResponse.success(svc.list(kbId));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> get(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.getDetail(kbId, id));
    }

    @GetMapping("/{id}/status")
    public ApiResponse<?> status(@PathVariable String kbId, @PathVariable String id) {
        KbEvaluation e = svc.getDetail(kbId, id);
        if (e == null) {
            return ApiResponse.success(Map.of(
                    "id", id,
                    "status", "not_found",
                    "completedCount", 0,
                    "dataCount", 0,
                    "progress", 0));
        }
        int completed = e.getCompletedCount() != null ? e.getCompletedCount() : 0;
        int total = e.getDataCount() != null ? e.getDataCount() : 0;
        int progress = total > 0 ? (int) ((double) completed / total * 100) : 0;
        return ApiResponse.success(Map.of(
                "id", e.getId(),
                "status", e.getStatus(),
                "completedCount", completed,
                "dataCount", total,
                "progress", progress));
    }

    @PostMapping("/run")
    public ApiResponse<?> run(@PathVariable String kbId, @RequestBody(required = false) EvaluationConfig config) {
        return ApiResponse.success(svc.run(kbId, config != null ? config : new EvaluationConfig()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId, @PathVariable String id) {
        svc.delete(kbId, id);
        return ApiResponse.success();
    }
}
