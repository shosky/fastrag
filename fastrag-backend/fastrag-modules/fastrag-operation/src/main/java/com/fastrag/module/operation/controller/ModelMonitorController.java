package com.fastrag.module.operation.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.operation.service.ModelMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 模型监控分析控制器。
 *
 * <p>提供AI模型调用维度的监控分析功能，数据来源于 model_call_log 表。
 * 支持按时间范围和模型关键字进行筛选，返回模型调用的统计总览数据。
 *
 * <p>REST API 端点：
 * <ul>
 *     <li>GET /api/monitor/model/overview - 获取模型监控总览数据，支持 timeRange（天）、keyword、page、pageSize 参数</li>
 * </ul>
 *
 * <p>委托 {@link ModelMonitorService} 完成具体的数据聚合和统计逻辑。
 */
@RestController
@RequestMapping("/api/monitor/model")
@RequiredArgsConstructor
public class ModelMonitorController {

    private final ModelMonitorService svc;

    /**
     * 获取模型监控总览数据
     *
     * @param timeRange 时间范围（天），默认 7
     * @param keyword   模型 Code 搜索关键字（可选）
     * @param page      模型统计分页页码，默认 1
     * @param pageSize  模型统计分页每页条数，默认 10
     */
    @GetMapping("/overview")
    public ApiResponse<?> overview(
            @RequestParam(defaultValue = "7") int timeRange,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(svc.getOverview(timeRange, keyword, page, pageSize));
    }
}
