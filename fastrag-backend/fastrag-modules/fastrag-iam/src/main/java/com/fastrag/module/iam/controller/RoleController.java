package com.fastrag.module.iam.controller;

/**
 * 角色管理控制器，提供系统角色的 CRUD 操作及默认角色设置功能。
 *
 * <p>核心职责：
 * <ul>
 *   <li>查询角色列表及角色详情</li>
 *   <li>创建、更新、删除角色</li>
 *   <li>设置系统的默认角色（新用户注册时自动分配）</li>
 * </ul>
 *
 * <p>提供的 REST API 端点：
 * <ul>
 *   <li>{@code GET    /api/roles} —— 获取角色列表</li>
 *   <li>{@code GET    /api/roles/{id}} —— 获取角色详情</li>
 *   <li>{@code POST   /api/roles} —— 创建角色</li>
 *   <li>{@code PUT    /api/roles/{id}} —— 更新角色</li>
 *   <li>{@code DELETE /api/roles/{id}} —— 删除角色</li>
 *   <li>{@code POST   /api/roles/{id}/set-default} —— 设置为默认角色</li>
 * </ul>
 *
 * <p>所有接口均要求 {@code admin:role} 权限，仅系统管理员可操作。
 * 角色的创建、更新、删除及设为默认操作均通过 {@code @Loggable} 记录审计日志。
 * 业务逻辑委托给 {@code RoleService} 处理，角色与权限的关联通过
 * {@code SysRolePermission} 实体维护。
 *
 * @see RoleService
 * @see RoleDto
 * @see RoleCreateRequest
 */
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.RoleService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import java.util.List;

import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
@RestController @RequestMapping("/api/roles") @RequiredArgsConstructor
public class RoleController {
    private final RoleService svc;
    @GetMapping @PreAuthorize("@perm.has('admin:role')") public ApiResponse<List<RoleDto>> list() { return ApiResponse.success(svc.listRoles()); }
    @GetMapping("/{id}") @PreAuthorize("@perm.has('admin:role')") public ApiResponse<RoleDto> get(@PathVariable String id) { return ApiResponse.success(svc.getRole(id)); }
    @Loggable(category = LogCategory.operation, action = ActionType.role_created)
    @PostMapping @PreAuthorize("@perm.has('admin:role')") public ApiResponse<RoleDto> create(@Valid @RequestBody RoleCreateRequest r) { return ApiResponse.success(svc.createRole(r)); }
    @Loggable(category = LogCategory.operation, action = ActionType.role_updated, target = "#id")
    @PutMapping("/{id}") @PreAuthorize("@perm.has('admin:role')") public ApiResponse<RoleDto> update(@PathVariable String id,@Valid @RequestBody RoleCreateRequest r) { return ApiResponse.success(svc.updateRole(id,r)); }
    @Loggable(category = LogCategory.operation, action = ActionType.role_deleted, target = "#id")
    @DeleteMapping("/{id}") @PreAuthorize("@perm.has('admin:role')") public ApiResponse<Void> delete(@PathVariable String id) { svc.deleteRole(id); return ApiResponse.success(); }
    @Loggable(category = LogCategory.operation, action = ActionType.role_set_default, target = "#id")
    @PostMapping("/{id}/set-default") @PreAuthorize("@perm.has('admin:role')") public ApiResponse<Void> setDefault(@PathVariable String id) { svc.setDefault(id); return ApiResponse.success(); }
}
