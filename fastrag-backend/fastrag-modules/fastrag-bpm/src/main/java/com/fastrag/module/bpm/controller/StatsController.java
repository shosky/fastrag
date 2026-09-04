package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.service.BpmStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/bpm/stats") @RequiredArgsConstructor
public class StatsController {
    private final BpmStatsService svc;
    @GetMapping public ApiResponse<?> global() { return ApiResponse.success(svc.globalStats()); }
    @GetMapping("/flows/{flowDefId}") public ApiResponse<?> flow(@PathVariable String flowDefId) { return ApiResponse.success(svc.flowStats(flowDefId)); }
    @GetMapping("/instances/{instanceId}/timeline") public ApiResponse<?> timeline(@PathVariable String instanceId) { return ApiResponse.success(svc.instanceTimeline(instanceId)); }
}