package com.fastrag.module.iam.controller;
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
