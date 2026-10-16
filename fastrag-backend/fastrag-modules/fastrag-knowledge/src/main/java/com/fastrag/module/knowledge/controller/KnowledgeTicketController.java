package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbKnowledgeTicket;
import com.fastrag.module.knowledge.service.KnowledgeTicketService;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
// 知识工单：新增/查看/编辑/删除 + 审批（通过/驳回）+ 流程环节日志
@RestController @RequestMapping("/api/kb/{kbId}/knowledge-tickets") @RequiredArgsConstructor
public class KnowledgeTicketController {
    private final KnowledgeTicketService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String status,
        @RequestParam(required=false) String ticketType,@RequestParam(required=false) String keyword) { return ApiResponse.success(svc.list(kbId,status,ticketType,keyword)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbKnowledgeTicket ticket) { return ApiResponse.success(svc.create(kbId,ticket)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody KbKnowledgeTicket ticket) { return ApiResponse.success(svc.update(id,ticket)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
    // 审批通过/驳回：末环节通过回写业务（新增/修改上线、取消发布留档），驳回回滚（取消发布恢复取消前状态）
    @PostMapping("/{id}/approve") public ApiResponse<?> approve(@PathVariable String id,@RequestBody(required=false) Map<String,String> body) {
        return ApiResponse.success(svc.approve(id,currentApprover(),body==null?null:body.get("comment"))); }
    @PostMapping("/{id}/reject") public ApiResponse<?> reject(@PathVariable String id,@RequestBody(required=false) Map<String,String> body) {
        return ApiResponse.success(svc.reject(id,currentApprover(),body==null?null:body.get("comment"))); }
    // 流程环节：工单在流程中走过的步骤（提交/审批/驳回/改状态）
    @GetMapping("/{id}/logs") public ApiResponse<?> logs(@PathVariable String id) { return ApiResponse.success(svc.logs(id)); }
    private static String currentApprover() {
        var u=SecurityUtil.getCurrentUser();
        return u!=null&&u.getUsername()!=null&&!u.getUsername().isBlank()?u.getUsername():"admin";
    }
}
