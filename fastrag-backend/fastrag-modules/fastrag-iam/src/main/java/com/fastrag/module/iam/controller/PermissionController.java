package com.fastrag.module.iam.controller;
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
