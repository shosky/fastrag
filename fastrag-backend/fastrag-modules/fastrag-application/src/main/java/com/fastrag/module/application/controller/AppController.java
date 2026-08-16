package com.fastrag.module.application.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.application.service.AppService;
import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;

/**
 * 应用管理控制器，提供应用全生命周期管理的REST API。
 *
 * <p>路由前缀：/api/apps。核心端点包括：
 * <ul>
 *   <li>GET / — 按关键字和标签筛选应用列表</li>
 *   <li>GET /{id} — 获取应用详情</li>
 *   <li>POST / — 创建新应用（需 app:create 权限，带操作日志记录）</li>
 *   <li>PUT /{id} — 更新应用信息（需 app:edit 权限，带操作日志记录）</li>
 *   <li>DELETE /{id} — 删除应用（需 app:delete 权限，带操作日志记录）</li>
 *   <li>GET /templates — 获取应用模板列表</li>
 *   <li>POST /{id}/run — 同步运行应用（传入query执行对话）</li>
 * </ul>
 *
 * <p>创建、更新、删除操作通过 {@link Loggable} 注解记录操作日志。
 * 依赖 {@link AppService} 实现所有业务逻辑。
 */
@RestController @RequestMapping("/api/apps") @RequiredArgsConstructor
public class AppController {
    private final AppService svc;
    @PreAuthorize("@perm.has('app:use')") @GetMapping public ApiResponse<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String tag) { return ApiResponse.success(svc.list(keyword,tag)); }
    @PreAuthorize("@perm.has('app:use')") @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PreAuthorize("@perm.has('app:create')")
    @Loggable(category=LogCategory.operation,action=ActionType.app_created)
    @PostMapping public ApiResponse<?> create(@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.create(f)); }
    @PreAuthorize("@perm.has('app:edit')")
    @Loggable(category=LogCategory.operation,action=ActionType.app_updated,target="#id")
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody Map<String,Object> f) { return ApiResponse.success(svc.update(id,f)); }
    @PreAuthorize("@perm.has('app:delete')")
    @Loggable(category=LogCategory.operation,action=ActionType.app_deleted,target="#id")
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    @PreAuthorize("@perm.has('app:use')") @GetMapping("/templates") public ApiResponse<?> templates() { return ApiResponse.success(svc.getTemplates()); }
    @PreAuthorize("@perm.has('app:use')") @PostMapping("/{id}/run") public ApiResponse<?> run(@PathVariable String id,@RequestBody Map<String,Object> b) { return ApiResponse.success(svc.run(id,(String)b.get("query"))); }
}
