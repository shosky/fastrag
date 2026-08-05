package com.fastrag.module.operation.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.operation.model.UnifiedLogQuery;
import com.fastrag.module.operation.service.UnifiedLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一日志查询控制器
 * <p>提供跨日志类型的统一查询入口。
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
