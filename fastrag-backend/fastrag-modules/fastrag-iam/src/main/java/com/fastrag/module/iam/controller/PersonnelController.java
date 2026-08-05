package com.fastrag.module.iam.controller;
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
