package com.fastrag.module.iam.controller;

/**
 * 权限管理控制器，提供系统权限项的 CRUD 操作及树形结构查询接口。
 *
 * <p>核心职责：
 * <ul>
 *   <li>查询全部权限项列表</li>
 *   <li>查询权限树形结构（含父子层级关系）</li>
 *   <li>创建、更新、删除权限项</li>
 * </ul>
 *
 * <p>提供的 REST API 端点：
 * <ul>
 *   <li>{@code GET    /api/permissions} —— 获取权限列表</li>
 *   <li>{@code GET    /api/permissions/tree} —— 获取权限树</li>
 *   <li>{@code POST   /api/permissions} —— 创建权限</li>
 *   <li>{@code PUT    /api/permissions/{id}} —— 更新权限</li>
 *   <li>{@code DELETE /api/permissions/{id}} —— 删除权限</li>
 * </ul>
 *
 * <p>所有接口均要求 {@code admin:role} 权限，仅系统管理员可操作。
 * 权限项通过 {@code PermissionService} 管理，权限数据最终关联到 {@code SysRolePermission}
 * 实体以实现角色-权限绑定。业务逻辑委托给 {@code PermissionService} 处理。
 *
 * @see PermissionService
 * @see PermissionDto
 * @see PermissionCreateRequest
 */
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.iam.model.*;
import com.fastrag.module.iam.service.PermissionService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;

@RestController @RequestMapping("/api/permissions") @RequiredArgsConstructor
public class PermissionController {
    private final PermissionService svc;

    @GetMapping @PreAuthorize("@perm.has('admin:role')")
    public ApiResponse<List<PermissionDto>> list() {
        return ApiResponse.success(svc.listPermissions());
    }

    @GetMapping("/tree") @PreAuthorize("@perm.has('admin:role')")
    public ApiResponse<List<PermissionDto>> tree() {
        return ApiResponse.success(svc.getPermissionTree());
    }

    @PostMapping @PreAuthorize("@perm.has('admin:role')")
    public ApiResponse<PermissionDto> create(@Valid @RequestBody PermissionCreateRequest req) {
        return ApiResponse.success(svc.createPermission(req));
    }

    @PutMapping("/{id}") @PreAuthorize("@perm.has('admin:role')")
    public ApiResponse<PermissionDto> update(@PathVariable Long id, @Valid @RequestBody PermissionCreateRequest req) {
        return ApiResponse.success(svc.updatePermission(id, req));
    }

    @DeleteMapping("/{id}") @PreAuthorize("@perm.has('admin:role')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        svc.deletePermission(id);
        return ApiResponse.success();
    }
}
