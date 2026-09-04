package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.service.BpmNodeTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/bpm/node-types") @RequiredArgsConstructor
public class NodeTypeController {
    private final BpmNodeTypeService svc;
    @GetMapping public ApiResponse<?> list() { return ApiResponse.success(svc.listEnabled()); }
    @GetMapping("/{type}") public ApiResponse<?> get(@PathVariable String type) { return ApiResponse.success(svc.getByType(type)); }
}