package com.fastrag.module.retrieval.controller;
import com.fastrag.common.exception.BusinessException; import com.fastrag.common.response.ApiResponse; import com.fastrag.module.retrieval.entity.*;
import com.fastrag.module.retrieval.model.*;
import com.fastrag.module.retrieval.service.*; import com.fastrag.security.util.SecurityUtil; import lombok.RequiredArgsConstructor; import org.springframework.data.redis.core.StringRedisTemplate; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class RetrievalController {
    private final RetrievalService retrievalService; private final QueryEnhanceService queryService;
    private final RetrievalLogService logService; private final UpdateRemindService remindService;
    private final SearchPreferenceService preferenceService; private final KnowledgePushService pushService;
    private final StringRedisTemplate redisTemplate;
    /** 检索权限控制：与 @KbAuth 同源的 ACL 校验（kb:acl:{kbId}:{userId}，super_admin 直通） */
    private void checkKbAcl(String kbId) {
        var user = SecurityUtil.getCurrentUser();
        if (user == null || user.hasPermission("*") || kbId == null || kbId.isBlank()) return;
        String role = redisTemplate.opsForValue().get("kb:acl:" + kbId + ":" + user.getUserId());
        if (role == null) throw BusinessException.forbidden("无知识库访问权限");
    }
    @PostMapping("/api/retrieval/search") public ApiResponse<?> search(@RequestBody RetrievalRequest req) { checkKbAcl(req.getKnowledgeId()); return ApiResponse.success(retrievalService.search(req)); }
    @GetMapping("/api/retrieval/kb/{kbId}/chunks/count") public ApiResponse<?> count(@PathVariable String kbId) { return ApiResponse.success(retrievalService.getChunkCount(kbId)); }
    @PostMapping("/api/query/suggest") public ApiResponse<?> suggest(@RequestBody Map<String,Object> b) {
        return ApiResponse.success(queryService.suggest(String.valueOf(b.get("query"))));
    }
    @PostMapping("/api/query/expand-synonyms") public ApiResponse<?> synonyms(@RequestBody Map<String,Object> b) { return ApiResponse.success(queryService.expandSynonyms(String.valueOf(b.get("query")))); }
    @PostMapping("/api/query-rules/apply") public ApiResponse<?> applyRules(@RequestBody Map<String,Object> b) {
        var result = queryService.applyQueryRules(String.valueOf(b.get("query")));
        return ApiResponse.success(result.get("rewritten"));
    }
    @PostMapping("/api/graph/expand") public ApiResponse<?> expand(@RequestBody Map<String,Object> b) {
        String kbId = String.valueOf(b.get("kbId"));
        String query = String.valueOf(b.get("query"));
        int depth = b.get("depth") instanceof Number ? ((Number)b.get("depth")).intValue() : 2;
        int maxEntities = b.get("maxEntities") instanceof Number ? ((Number)b.get("maxEntities")).intValue() : 20;
        return ApiResponse.success(queryService.expandGraph(kbId, query, depth, maxEntities));
    }
    // ===== M2 知识检索增强 =====
    @GetMapping("/api/retrieval/logs") public ApiResponse<?> logs(@RequestParam(required=false) String kbId,@RequestParam(required=false) Boolean hasResult,
                                                                  @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.success(logService.page(kbId,hasResult,page,pageSize));
    }
    @GetMapping("/api/retrieval/logs/analysis") public ApiResponse<?> logAnalysis(@RequestParam(required=false) String kbId) {
        return ApiResponse.success(logService.analysis(kbId));
    }
    @PostMapping("/api/retrieval/logs") public ApiResponse<?> addLog(@RequestBody KbRetrievalLog log) { logService.log(log); return ApiResponse.success(); }
    @PutMapping("/api/retrieval/logs/{id}") public ApiResponse<?> updateLog(@PathVariable Long id,@RequestBody KbRetrievalLog log) { log.setId(id); logService.update(log); return ApiResponse.success(); }
    @DeleteMapping("/api/retrieval/logs/{id}") public ApiResponse<?> deleteLog(@PathVariable Long id) { logService.delete(id); return ApiResponse.success(); }
    @GetMapping("/api/kb/{kbId}/update-remind") public ApiResponse<?> remind(@PathVariable String kbId) { return ApiResponse.success(remindService.remind(kbId)); }
    @GetMapping("/api/update-remind") public ApiResponse<?> remindList(@RequestParam(required=false) String kbId) { return ApiResponse.success(remindService.list(kbId)); }
    @PostMapping("/api/update-remind") public ApiResponse<?> saveRemind(@RequestBody KbUpdateRemind remind) { return ApiResponse.success(remindService.save(remind)); }
    @PutMapping("/api/update-remind/{id}") public ApiResponse<?> updateRemind(@PathVariable String id,@RequestBody KbUpdateRemind remind) { remind.setId(id); return ApiResponse.success(remindService.save(remind)); }
    @DeleteMapping("/api/update-remind/{id}") public ApiResponse<?> deleteRemind(@PathVariable String id) { remindService.delete(id); return ApiResponse.success(); }
    // ===== 检索偏好设置（新增/修改/删除/查询） =====
    @GetMapping("/api/kb/{kbId}/search-preferences") public ApiResponse<?> prefList(@PathVariable String kbId,@RequestParam(required=false) String userId,@RequestParam(required=false,defaultValue="false") boolean mine) { return ApiResponse.success(preferenceService.list(kbId,userId,mine)); }
    @GetMapping("/api/search-preferences/{id}") public ApiResponse<?> prefGet(@PathVariable String id) { return ApiResponse.success(preferenceService.get(id)); }
    @PostMapping("/api/kb/{kbId}/search-preferences") public ApiResponse<?> prefCreate(@PathVariable String kbId,@RequestBody KbSearchPreference p) { return ApiResponse.success(preferenceService.create(kbId,p)); }
    @PutMapping("/api/search-preferences/{id}") public ApiResponse<?> prefUpdate(@PathVariable String id,@RequestBody KbSearchPreference p) { return ApiResponse.success(preferenceService.update(id,p)); }
    @DeleteMapping("/api/search-preferences/{id}") public ApiResponse<?> prefDelete(@PathVariable String id) { preferenceService.delete(id); return ApiResponse.success(); }
    // ===== 知识推送（新增/修改/删除/查询/发送） =====
    @GetMapping("/api/kb/{kbId}/knowledge-pushes") public ApiResponse<?> pushList(@PathVariable String kbId,@RequestParam(required=false) String status) { return ApiResponse.success(pushService.list(kbId,status)); }
    @GetMapping("/api/knowledge-pushes/{id}") public ApiResponse<?> pushGet(@PathVariable String id) { return ApiResponse.success(pushService.get(id)); }
    @PostMapping("/api/kb/{kbId}/knowledge-pushes") public ApiResponse<?> pushCreate(@PathVariable String kbId,@RequestBody KbKnowledgePush push) { return ApiResponse.success(pushService.create(kbId,push)); }
    @PutMapping("/api/knowledge-pushes/{id}") public ApiResponse<?> pushUpdate(@PathVariable String id,@RequestBody KbKnowledgePush push) { return ApiResponse.success(pushService.update(id,push)); }
    @DeleteMapping("/api/knowledge-pushes/{id}") public ApiResponse<?> pushDelete(@PathVariable String id) { pushService.delete(id); return ApiResponse.success(); }
    // 发送知识推送（写入系统通知）
    @PostMapping("/api/knowledge-pushes/{id}/send") public ApiResponse<?> pushSend(@PathVariable String id) { return ApiResponse.success(pushService.send(id,null)); }
}
