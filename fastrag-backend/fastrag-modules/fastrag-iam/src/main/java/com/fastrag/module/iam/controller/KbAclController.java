package com.fastrag.module.iam.controller;
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
