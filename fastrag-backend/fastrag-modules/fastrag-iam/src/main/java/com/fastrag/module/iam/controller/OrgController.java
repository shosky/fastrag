package com.fastrag.module.iam.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.iam.model.*; import com.fastrag.module.iam.service.OrgService;
import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*; import java.util.Map;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
@RestController @RequestMapping("/api/org") @RequiredArgsConstructor
public class OrgController {
    private final OrgService svc;
    // 读接口保持登录即可（KB 表单的部门共享、组织树展示需要）
    @GetMapping("/tree") public ApiResponse<List<OrgNodeDto>> tree() { return ApiResponse.success(svc.getOrgTree()); }
    @GetMapping("/flat") public ApiResponse<List<Map<String,Object>>> flat() { return ApiResponse.success(svc.getFlatList()); }
    @GetMapping("/departments") public ApiResponse<List<String>> depts() { return ApiResponse.success(svc.getDepartmentNames()); }
    @GetMapping("/{id}/members") public ApiResponse<List<PersonnelDto>> members(@PathVariable String id) { return ApiResponse.success(svc.getDepartmentMembers(id)); }
    // 写接口：组织管理权限
    @PreAuthorize("@perm.has('admin:org')")
    @Loggable(category=LogCategory.operation, action = ActionType.user_created)
    @PostMapping public ApiResponse<OrgNodeDto> create(@RequestBody Map<String,String> b) { return ApiResponse.success(svc.createOrg(b.get("name"),b.get("alias"),b.get("parentId"))); }
    @PreAuthorize("@perm.has('admin:org')")
    @Loggable(category=LogCategory.operation,action=ActionType.config_updated,target="#id")
    @PutMapping("/{id}") public ApiResponse<Void> update(@PathVariable String id,@RequestBody Map<String,String> b) { svc.updateOrg(id,b.get("name"),b.get("alias")); return ApiResponse.success(); }
    @PreAuthorize("@perm.has('admin:org')")
    @Loggable(category=LogCategory.operation,action=ActionType.config_deleted,target="#id")
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable String id) { svc.deleteOrg(id); return ApiResponse.success(); }
}
