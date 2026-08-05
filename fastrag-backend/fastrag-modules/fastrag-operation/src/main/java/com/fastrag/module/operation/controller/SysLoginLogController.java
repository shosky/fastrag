package com.fastrag.module.operation.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.operation.service.SysLoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统登录日志控制器
 */
@RestController
@RequestMapping("/api/audit/login-log")
@RequiredArgsConstructor
public class SysLoginLogController {

    private final SysLoginLogService loginLogService;

    /**
     * 查询登录日志
     *
     * @param userId 用户 ID（可选）
     * @param status 状态：success / failed / logout（可选）
     * @param limit  返回条数上限，默认 100
     */
    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String userId,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(loginLogService.list(userId, status, limit));
    }
}
