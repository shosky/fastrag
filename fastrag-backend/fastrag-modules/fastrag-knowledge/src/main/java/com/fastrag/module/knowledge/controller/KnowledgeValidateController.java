package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbKnowledgeValidate;
import com.fastrag.module.knowledge.service.KnowledgeValidateService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/kb/{kbId}/knowledge-validate") @RequiredArgsConstructor
public class KnowledgeValidateController {
    private final KnowledgeValidateService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId) { return ApiResponse.success(svc.list(kbId)); }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping("/check") public ApiResponse<?> check(@PathVariable String kbId,@RequestBody KbKnowledgeValidate validate) { validate.setKbId(kbId); return ApiResponse.success(svc.check(validate)); }
}
