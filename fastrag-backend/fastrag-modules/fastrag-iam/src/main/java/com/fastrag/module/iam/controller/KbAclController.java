package com.fastrag.module.iam.controller;

/**
 * 知识库访问控制（ACL）控制器，管理知识库级别的用户权限分配。
 *
 * <p>核心职责：
 * <ul>
 *   <li>查询某个知识库的 ACL 列表（owner 权限）</li>
 *   <li>批量设置知识库的 ACL 规则（owner 权限）</li>
 *   <li>为知识库添加单条 ACL 记录（owner 权限）</li>
 *   <li>移除知识库中指定用户的 ACL 记录（owner 权限）</li>
 *   <li>查询用户可访问的知识库 ID 列表</li>
 *   <li>查询用户在指定知识库中的角色</li>
 * </ul>
 *
 * <p>提供的 REST API 端点：
 * <ul>
 *   <li>{@code GET    /api/kb/{kbId}/acl} —— 获取知识库 ACL 列表</li>
 *   <li>{@code PUT    /api/kb/{kbId}/acl} —— 批量设置知识库 ACL</li>
 *   <li>{@code POST   /api/kb/{kbId}/acl} —— 添加 ACL 条目</li>
 *   <li>{@code DELETE /api/kb/{kbId}/acl/{userId}} —— 移除 ACL 条目</li>
 *   <li>{@code GET    /api/acl/users/{userId}/kbs} —— 查询用户可访问的知识库</li>
 *   <li>{@code GET    /api/acl/users/{userId}/kbs/{kbId}/role} —— 查询用户在知识库中的角色</li>
 * </ul>
 *
 * <p>知识库写操作（GET/PUT/POST/DELETE acl）仅限知识库 owner 访问，
 * 通过 {@code @KbAuth(KBRole.owner)} 注解实现。
 * 查询用户可访问知识库的接口仅允许查询自身或管理员可查询任意用户。
 * ACL 数据由 {@code KbAclService}（security 模块）管理，本控制器作为 IAM 模块的入口。
 * 所有写操作均通过 {@code @Loggable} 记录审计日志。
 *
 * @see KbAclService
 * @see KBRole
 */
import com.fastrag.common.enums.KBRole; import com.fastrag.common.exception.BusinessException; import com.fastrag.common.response.ApiResponse;
import com.fastrag.security.model.KbAclDto; import com.fastrag.security.service.KbAclService;
import com.fastrag.security.annotation.KbAuth;
import com.fastrag.security.util.SecurityUtil; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class KbAclController {
    private final KbAclService svc;
    @KbAuth(KBRole.owner)
    @GetMapping("/kb/{kbId}/acl") public ApiResponse<List<KbAclDto>> get(@PathVariable String kbId) { return ApiResponse.success(svc.getKbAcl(kbId)); }
    @KbAuth(KBRole.owner)
    @Loggable(category=LogCategory.operation,action=ActionType.config_changed,target="#kbId")
    @PutMapping("/kb/{kbId}/acl") public ApiResponse<Void> set(@PathVariable String kbId,@RequestBody List<KbAclDto> entries) { svc.setKbAcl(kbId,entries); return ApiResponse.success(); }
    @KbAuth(KBRole.owner)
    @Loggable(category=LogCategory.operation,action=ActionType.config_changed,target="#kbId")
    @PostMapping("/kb/{kbId}/acl") public ApiResponse<Void> add(@PathVariable String kbId,@RequestBody KbAclDto e) { svc.addAclEntry(kbId,e.getUserId(),e.getKbRole(),SecurityUtil.getCurrentUserId()); return ApiResponse.success(); }
    @KbAuth(KBRole.owner)
    @Loggable(category=LogCategory.operation,action=ActionType.config_changed,target="#kbId")
    @DeleteMapping("/kb/{kbId}/acl/{userId}") public ApiResponse<Void> remove(@PathVariable String kbId,@PathVariable String userId) { svc.removeAclEntry(kbId,userId); return ApiResponse.success(); }
    @GetMapping("/acl/users/{userId}/kbs") public ApiResponse<List<String>> kbs(@PathVariable String userId) { checkSelfOrAdmin(userId); return ApiResponse.success(svc.getAccessibleKbIds(userId)); }
    @GetMapping("/acl/users/{userId}/kbs/{kbId}/role") public ApiResponse<String> role(@PathVariable String userId,@PathVariable String kbId) { checkSelfOrAdmin(userId); var r=svc.getKbRole(userId,kbId); return ApiResponse.success(r!=null?r.name():null); }

    /**
     * 仅允许查询自己的权限，超管可查询任意用户
     */
    private void checkSelfOrAdmin(String userId) {
        var user = SecurityUtil.getCurrentUser();
        if (user != null && (user.hasPermission("*") || userId.equals(user.getUserId()))) return;
        throw BusinessException.forbidden("无权查看该用户的权限");
    }
}
