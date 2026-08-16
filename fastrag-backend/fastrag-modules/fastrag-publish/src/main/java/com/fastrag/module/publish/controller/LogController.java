package com.fastrag.module.publish.controller;

/**
 * 知识库操作日志与更新日志 REST 控制器。
 *
 * <p>提供知识库（KB）级别的业务操作日志和更新日志的查询与写入接口。
 * 操作人信息优先从当前登录用户获取，未登录时默认为 "system"。</p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>{@code GET  /api/kb/{kbId}/logs} - 分页查询指定知识库的操作日志列表，支持 category 过滤、keyword 模糊搜索（对象/详情/操作人）</li>
 *   <li>{@code GET  /api/kb/{kbId}/logs/stats} - 查询指定知识库的日志总数及各分类数量</li>
 *   <li>{@code GET  /api/kb/{kbId}/update-logs} - 查询指定知识库的更新日志列表，可按 type 过滤</li>
 *   <li>{@code POST /api/kb/{kbId}/logs} - 手动添加操作日志</li>
 *   <li>{@code POST /api/kb/{kbId}/update-logs} - 手动添加更新日志</li>
 *   <li>{@code PUT  /api/kb/{kbId}/update-logs/{id}/read} - 标记更新日志为已读</li>
 * </ul>
 *
 * @see LogService
 */
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class LogController {

    private final LogService svc;

    @GetMapping("/api/kb/{kbId}/logs")
    public ApiResponse<?> logs(@PathVariable String kbId,
                               @RequestParam(required = false) String category,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(svc.listLogs(kbId, category, keyword, page, pageSize));
    }

    @GetMapping("/api/kb/{kbId}/logs/stats")
    public ApiResponse<?> logStats(@PathVariable String kbId) {
        return ApiResponse.success(svc.countByCategory(kbId));
    }

    @GetMapping("/api/kb/{kbId}/update-logs")
    public ApiResponse<?> updateLogs(@PathVariable String kbId, @RequestParam(required = false) String type) {
        return ApiResponse.success(svc.listUpdateLogs(kbId, type));
    }

    @PostMapping("/api/kb/{kbId}/logs")
    public ApiResponse<?> addLog(@PathVariable String kbId, @RequestBody Map<String, String> body) {
        String username = SecurityUtil.getCurrentUser() != null ? SecurityUtil.getCurrentUser().getUsername() : "system";
        svc.addLog(
                kbId,
                body.get("category"),
                body.get("action"),
                body.get("target"),
                body.get("detail"),
                body.getOrDefault("operator", username)
        );
        return ApiResponse.success();
    }

    @PostMapping("/api/kb/{kbId}/update-logs")
    public ApiResponse<?> addUpdateLog(@PathVariable String kbId, @RequestBody Map<String, String> body) {
        String username = SecurityUtil.getCurrentUser() != null ? SecurityUtil.getCurrentUser().getUsername() : "system";
        svc.addUpdateLog(
                kbId,
                body.get("updateType"),
                body.get("target"),
                body.get("detail"),
                body.getOrDefault("operator", username)
        );
        return ApiResponse.success();
    }

    @PutMapping("/api/kb/{kbId}/update-logs/{id}/read")
    public ApiResponse<?> markUpdateLogRead(@PathVariable String id) {
        svc.markUpdateLogRead(id);
        return ApiResponse.success();
    }
}
