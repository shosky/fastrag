package com.fastrag.module.iam.controller;
import com.fastrag.common.enums.KBRole; import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.iam.model.KbAclDto; import com.fastrag.module.iam.service.KbAclService;
import com.fastrag.security.util.SecurityUtil; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.*; import java.util.Map;
@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class KbAclController {
    private final KbAclService svc;
    @GetMapping("/kb/{kbId}/acl") public ApiResponse<List<KbAclDto>> get(@PathVariable String kbId) { return ApiResponse.success(svc.getKbAcl(kbId)); }
    @PutMapping("/kb/{kbId}/acl") public ApiResponse<Void> set(@PathVariable String kbId,@RequestBody Map<String,List<KbAclDto>> b) { svc.setKbAcl(kbId,b.get("entries")); return ApiResponse.success(); }
    @PostMapping("/kb/{kbId}/acl") public ApiResponse<Void> add(@PathVariable String kbId,@RequestBody KbAclDto e) { svc.addAclEntry(kbId,e.getUserId(),e.getKbRole(),SecurityUtil.getCurrentUserId()); return ApiResponse.success(); }
    @DeleteMapping("/kb/{kbId}/acl/{userId}") public ApiResponse<Void> remove(@PathVariable String kbId,@PathVariable String userId) { svc.removeAclEntry(kbId,userId); return ApiResponse.success(); }
    @GetMapping("/acl/users/{userId}/kbs") public ApiResponse<List<String>> kbs(@PathVariable String userId) { return ApiResponse.success(svc.getAccessibleKbIds(userId)); }
    @GetMapping("/acl/users/{userId}/kbs/{kbId}/role") public ApiResponse<String> role(@PathVariable String userId,@PathVariable String kbId) { var r=svc.getKbRole(userId,kbId); return ApiResponse.success(r!=null?r.name():null); }
}
