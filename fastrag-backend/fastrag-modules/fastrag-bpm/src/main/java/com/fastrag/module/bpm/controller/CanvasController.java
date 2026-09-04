package com.fastrag.module.bpm.controller;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.bpm.dto.CanvasSaveRequest;
import com.fastrag.module.bpm.service.BpmCanvasService;
import com.fastrag.module.bpm.service.BpmFlowVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController @RequestMapping("/api/bpm/flows/{flowDefId}/versions/{versionId}") @RequiredArgsConstructor
public class CanvasController {
    private final BpmCanvasService canvas;
    private final BpmFlowVersionService versionService;

    /** 获取整图(版本详情 + nodes + edges) */
    @GetMapping("/canvas") public ApiResponse<?> getCanvas(@PathVariable String flowDefId, @PathVariable String versionId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("detail", versionService.detail(flowDefId, findVersionNoById(flowDefId, versionId)));
        data.put("validation", canvas.validate(versionId));
        return ApiResponse.success(data);
    }
    /** 整图原子保存(仅 draft 状态) */
    @PutMapping("/canvas") public ApiResponse<?> saveCanvas(@PathVariable String flowDefId, @PathVariable String versionId, @RequestBody CanvasSaveRequest req) {
        canvas.saveCanvas(flowDefId, versionId, req);
        return ApiResponse.success(canvas.validate(versionId));
    }
    /** 校验画布结构 */
    @GetMapping("/validate") public ApiResponse<?> validate(@PathVariable String flowDefId, @PathVariable String versionId) {
        return ApiResponse.success(canvas.validate(versionId));
    }
    /** 通过 versionId 反查 versionNo(versionService.detail 需要 versionNo) */
    private Integer findVersionNoById(String flowDefId, String versionId) {
        return versionService.listByFlow(flowDefId).stream()
                .filter(v -> versionId.equals(v.getId())).map(v -> v.getVersionNo()).findFirst().orElse(null);
    }
}