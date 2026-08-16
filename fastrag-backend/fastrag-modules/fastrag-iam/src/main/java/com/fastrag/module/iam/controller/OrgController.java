package com.fastrag.module.iam.controller;

/**
 * 组织架构管理控制器，提供部门/组织的树形结构管理与成员查询功能。
 *
 * <p>核心职责：
 * <ul>
 *   <li>获取组织树形结构（用于前端树形组件展示）</li>
 *   <li>获取扁平化的组织列表（含 id、name、parentId 等字段）</li>
 *   <li>获取所有部门名称列表（用于下拉选择等场景）</li>
 *   <li>获取指定部门下的成员列表</li>
 *   <li>创建、更新、删除组织节点</li>
 * </ul>
 *
 * <p>提供的 REST API 端点：
 * <ul>
 *   <li>{@code GET    /api/org/tree} —— 获取组织树</li>
 *   <li>{@code GET    /api/org/flat} —— 获取扁平组织列表</li>
 *   <li>{@code GET    /api/org/departments} —— 获取所有部门名称</li>
 *   <li>{@code GET    /api/org/{id}/members} —— 获取部门成员</li>
 *   <li>{@code POST   /api/org} —— 创建组织节点</li>
 *   <li>{@code PUT    /api/org/{id}} —— 更新组织节点</li>
 *   <li>{@code DELETE /api/org/{id}} —— 删除组织节点</li>
 * </ul>
 *
 * <p>读接口仅需登录即可访问（供 KB 表单中的部门共享、组织树展示等场景使用），
 * 写接口要求 {@code admin:org} 权限。写操作均通过 {@code @Loggable} 记录审计日志。
 * 业务逻辑委托给 {@code OrgService} 处理。
 *
 * @see OrgService
 * @see OrgNodeDto
 */
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
