package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.service.BpmFlowTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController @RequestMapping("/api/bpm/templates") @RequiredArgsConstructor
public class TemplateController {
    private final BpmFlowTemplateService svc;
    @GetMapping public ApiResponse<?> list(@RequestParam(required = false) Boolean builtinOnly,
                                            @RequestParam(required = false) String category) {
        return ApiResponse.success(svc.listAll(builtinOnly, category));
    }
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable String id) { return ApiResponse.success(svc.get(id)); }
    @PostMapping("/{id}/apply") public ApiResponse<?> apply(@PathVariable String id, @RequestBody Map<String, String> body) {
        return ApiResponse.success(svc.applyTemplate(id, body.get("flowDefId")));
    }
}