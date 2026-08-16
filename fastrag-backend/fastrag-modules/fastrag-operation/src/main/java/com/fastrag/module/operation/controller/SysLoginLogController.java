package com.fastrag.module.operation.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.operation.service.SysLoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统登录日志控制器。
 *
 * <p>提供用户登录日志的查询接口，支持按用户ID和登录状态（success/failed/logout）进行过滤，
 * 可限制返回条数，用于安全审计和登录行为分析。
 *
 * <p>REST API 端点：
 * <ul>
 *     <li>GET /api/audit/login-log - 查询登录日志列表，支持 userId、status、limit 参数</li>
 * </ul>
 *
 * <p>委托 {@link SysLoginLogService} 完成具体的数据查询逻辑。
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
