package com.fastrag.module.platform.controller;

/**
 * 查询规则管理控制器
 * <p>
 * 提供查询规则的查询、创建、删除和启用/禁用切换功能。
 * 查询规则用于管理和优化系统的查询行为，支持按类型（type）过滤规则列表。
 * 业务逻辑委托给 {@link com.fastrag.module.platform.service.QueryRuleService} 处理。
 * </p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>GET /api/query-rules — 查询规则列表，支持按 type 过滤</li>
 *   <li>POST /api/query-rules — 创建查询规则</li>
 *   <li>DELETE /api/query-rules/{id} — 删除查询规则</li>
 *   <li>POST /api/query-rules/{id}/toggle — 切换规则的启用/禁用状态</li>
 * </ul>
 *
 * @see com.fastrag.module.platform.service.QueryRuleService
 * @see com.fastrag.module.platform.entity.QueryRule
 */
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.platform.service.QueryRuleService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/query-rules") @RequiredArgsConstructor
public class QueryRuleController {
    private final QueryRuleService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String type) { return ApiResponse.success(svc.list(type)); }
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PostMapping("/{id}/toggle") public ApiResponse<?> toggle(@PathVariable String id) { svc.toggle(id); return ApiResponse.success(); }
}
