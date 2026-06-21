package com.fastrag.module.iam.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.RoleService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/roles") @RequiredArgsConstructor
public class RoleController {
    private final RoleService svc;
    @GetMapping @PreAuthorize("@perm.has('admin:role')") public ApiResponse<List<RoleDto>> list() { return ApiResponse.success(svc.listRoles()); }
    @GetMapping("/{id}") @PreAuthorize("@perm.has('admin:role')") public ApiResponse<RoleDto> get(@PathVariable String id) { return ApiResponse.success(svc.getRole(id)); }
    @PostMapping @PreAuthorize("@perm.has('admin:role')") public ApiResponse<RoleDto> create(@Valid @RequestBody RoleCreateRequest r) { return ApiResponse.success(svc.createRole(r)); }
    @PutMapping("/{id}") @PreAuthorize("@perm.has('admin:role')") public ApiResponse<RoleDto> update(@PathVariable String id,@Valid @RequestBody RoleCreateRequest r) { return ApiResponse.success(svc.updateRole(id,r)); }
    @DeleteMapping("/{id}") @PreAuthorize("@perm.has('admin:role')") public ApiResponse<Void> delete(@PathVariable String id) { svc.deleteRole(id); return ApiResponse.success(); }
    @PostMapping("/{id}/set-default") @PreAuthorize("@perm.has('admin:role')") public ApiResponse<Void> setDefault(@PathVariable String id) { svc.setDefault(id); return ApiResponse.success(); }
}
