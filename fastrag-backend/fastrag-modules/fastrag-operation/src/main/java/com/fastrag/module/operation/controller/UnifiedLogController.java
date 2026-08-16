package com.fastrag.module.operation.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.operation.model.UnifiedLogQuery;
import com.fastrag.module.operation.service.UnifiedLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一日志查询控制器。
 *
 * <p>提供跨日志类型的统一查询入口，将系统审计日志、登录日志等不同来源的日志整合到同一个分页查询接口中，
 * 便于运维人员进行全面的日志检索和审计分析。
 *
 * <p>REST API 端点：
 * <ul>
 *     <li>GET /api/logs - 统一日志分页查询，通过 {@link UnifiedLogQuery} 对象接收查询参数</li>
 * </ul>
 *
 * <p>委托 {@link UnifiedLogService} 完成具体的多源日志聚合和分页查询逻辑。
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class UnifiedLogController {

    private final UnifiedLogService unifiedLogService;

    /**
     * 统一日志分页查询
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<?> list(UnifiedLogQuery query) {
        return ApiResponse.success(unifiedLogService.pageQuery(query));
    }
}
