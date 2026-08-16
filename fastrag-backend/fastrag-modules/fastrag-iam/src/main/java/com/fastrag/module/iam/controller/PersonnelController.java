package com.fastrag.module.iam.controller;

/**
 * 人员管理控制器，提供系统用户的分页查询、创建、更新、角色分配及状态管理功能。
 *
 * <p>核心职责：
 * <ul>
 *   <li>分页查询人员列表（支持关键字搜索）</li>
 *   <li>创建和更新人员信息</li>
 *   <li>为人员分配角色</li>
 *   <li>启用/禁用人员账号</li>
 *   <li>根据用户名查询人员详情</li>
 *   <li>轻量人员列表查询（用于共享设置/成员选择场景，不含敏感信息）</li>
 * </ul>
 *
 * <p>提供的 REST API 端点：
 * <ul>
 *   <li>{@code GET    /api/personnel} —— 分页查询人员列表</li>
 *   <li>{@code POST   /api/personnel} —— 创建人员</li>
 *   <li>{@code PUT    /api/personnel/{id}} —— 更新人员信息</li>
 *   <li>{@code POST   /api/personnel/{id}/assign-roles} —— 分配角色</li>
 *   <li>{@code PUT    /api/personnel/{id}/status} —— 更新人员状态</li>
 *   <li>{@code GET    /api/personnel/by-username/{username}} —— 根据用户名查询</li>
 *   <li>{@code GET    /api/personnel/simple} —— 轻量人员选项列表</li>
 * </ul>
 *
 * <p>大部分接口要求 {@code admin:user} 权限，仅 {@code /simple} 和
 * {@code /by-username} 接口降低权限要求以便普通用户使用。
 * 写操作（创建、更新、角色分配、状态变更）均通过 {@code @Loggable} 记录审计日志。
 * 业务逻辑委托给 {@code PersonnelService} 处理。
 *
 * @see PersonnelService
 * @see PersonnelDto
 * @see PersonnelCreateRequest
 */
import com.fastrag.common.response.ApiResponse; import com.fastrag.common.response.PageResult;
import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.PersonnelService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;


@RestController @RequestMapping("/api/personnel") @RequiredArgsConstructor
public class PersonnelController {
    private final PersonnelService svc;

    @GetMapping @PreAuthorize("@perm.has('admin:user')")
    public ApiResponse<PageResult<PersonnelDto>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String keyword) {
        return ApiResponse.success(svc.listPersonnel(page, pageSize, keyword));
    }
@Loggable(category = LogCategory.operation, action = ActionType.user_created)
    @PostMapping @PreAuthorize("@perm.has('admin:user')")
    public ApiResponse<PersonnelDto> create(@Valid @RequestBody PersonnelCreateRequest r) {
        return ApiResponse.success(svc.createPersonnel(r));
    }
@Loggable(category = LogCategory.operation, action = ActionType.user_updated, target = "#id")
    @PutMapping("/{id}") @PreAuthorize("@perm.has('admin:user')")
    public ApiResponse<PersonnelDto> update(@PathVariable String id, @Valid @RequestBody PersonnelCreateRequest r) {
        return ApiResponse.success(svc.updatePersonnel(id, r));
    }
@Loggable(category = LogCategory.operation, action = ActionType.user_role_assigned, target = "#id")
    @PostMapping("/{id}/assign-roles") @PreAuthorize("@perm.has('admin:user')")
    public ApiResponse<Void> assignRoles(@PathVariable String id, @RequestBody Map<String, List<String>> body) {
        svc.assignRoles(id, body.get("roleIds"));
        return ApiResponse.success();
    }
@Loggable(category = LogCategory.operation, action = ActionType.user_status_changed, target = "#id")
    @PutMapping("/{id}/status") @PreAuthorize("@perm.has('admin:user')")
    public ApiResponse<Void> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        svc.updateStatus(id, body.get("status"));
        return ApiResponse.success();
    }

    @GetMapping("/by-username/{username}")
    public ApiResponse<PersonnelDto> byUsername(@PathVariable String username) {
        return ApiResponse.success(svc.findByUsername(username));
    }

    /**
     * 轻量人员选项（共享设置/成员选择用）：登录即可访问，不要求 admin:user。
     * 仅返回启用人员的精简字段（不含手机/邮箱等敏感信息）。
     */
    @GetMapping("/simple")
    public ApiResponse<?> simple() {
        return ApiResponse.success(svc.listPersonnel(1, 1000, null).getList().stream()
            .filter(p -> !"disabled".equals(p.getStatus()))
            .map(p -> { var m = new java.util.HashMap<String, Object>();
                m.put("id", p.getId());
                m.put("username", p.getUsername());
                m.put("realName", p.getRealName());
                m.put("orgId", p.getOrgId());
                m.put("orgName", p.getOrgName());
                m.put("status", p.getStatus());
                return m; }).toList());
    }
}
