package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbKnowledgeTicket;
import com.fastrag.module.knowledge.service.KnowledgeTicketService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
// 知识工单：新增/查看/编辑/删除
@RestController @RequestMapping("/api/kb/{kbId}/knowledge-tickets") @RequiredArgsConstructor
public class KnowledgeTicketController {
    private final KnowledgeTicketService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String status,
        @RequestParam(required=false) String ticketType,@RequestParam(required=false) String keyword) { return ApiResponse.success(svc.list(kbId,status,ticketType,keyword)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbKnowledgeTicket ticket) { return ApiResponse.success(svc.create(kbId,ticket)); }
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody KbKnowledgeTicket ticket) { return ApiResponse.success(svc.update(id,ticket)); }
    @DeleteMapping("/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.delete(id); return ApiResponse.success(); }
}
