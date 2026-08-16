package com.fastrag.module.operation.controller;

/**
 * 系统审计日志控制器。
 *
 * <p>提供系统操作审计日志的查询接口，支持按模块名称过滤和返回条数限制。
 *
 * <p>REST API 端点：
 * <ul>
 *     <li>GET /api/audit/system-log - 查询系统审计日志列表，支持 module 和 limit 参数</li>
 * </ul>
 *
 * <p>委托 {@link AuditLogService} 完成具体的数据查询逻辑。
 */
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.operation.service.AuditLogService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/audit/system-log") @RequiredArgsConstructor
public class AuditLogController {
    private final AuditLogService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String module,@RequestParam(required=false) Integer limit) { return ApiResponse.success(svc.list(module,limit)); }
}
