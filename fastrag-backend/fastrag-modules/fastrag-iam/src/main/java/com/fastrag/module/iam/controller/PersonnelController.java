package com.fastrag.module.iam.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.common.response.PageResult;
import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.PersonnelService;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
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

    @PostMapping @PreAuthorize("@perm.has('admin:user')")
    public ApiResponse<PersonnelDto> create(@Valid @RequestBody PersonnelCreateRequest r) {
        return ApiResponse.success(svc.createPersonnel(r));
    }

    @PutMapping("/{id}") @PreAuthorize("@perm.has('admin:user')")
    public ApiResponse<PersonnelDto> update(@PathVariable String id, @Valid @RequestBody PersonnelCreateRequest r) {
        return ApiResponse.success(svc.updatePersonnel(id, r));
    }

    @PostMapping("/{id}/assign-roles") @PreAuthorize("@perm.has('admin:user')")
    public ApiResponse<Void> assignRoles(@PathVariable String id, @RequestBody Map<String, List<String>> body) {
        svc.assignRoles(id, body.get("roleIds"));
        return ApiResponse.success();
    }

    @PutMapping("/{id}/status") @PreAuthorize("@perm.has('admin:user')")
    public ApiResponse<Void> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        svc.updateStatus(id, body.get("status"));
        return ApiResponse.success();
    }

    @GetMapping("/by-username/{username}")
    public ApiResponse<PersonnelDto> byUsername(@PathVariable String username) {
        return ApiResponse.success(svc.findByUsername(username));
    }

    @GetMapping("/simple")
    public ApiResponse<?> simple() {
        return ApiResponse.success(svc.listPersonnel(1, 1000, null).getList().stream()
            .map(p -> { var m = new java.util.HashMap<String, Object>();
                m.put("id", p.getId()); m.put("name", p.getRealName()); return m; }).toList());
    }
}
