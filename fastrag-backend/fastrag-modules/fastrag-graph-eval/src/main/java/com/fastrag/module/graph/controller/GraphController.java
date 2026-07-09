package com.fastrag.module.graph.controller;

import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.graph.service.GraphService;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kb/{kbId}/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService svc;
    private final LogService logService;

    // ==================== 图谱可视化 ====================

    @GetMapping
    public ApiResponse<?> data(@PathVariable String kbId,
                               @RequestParam(required = false) Integer maxNodes,
                               @RequestParam(required = false) Boolean excludeChunks) {
        return ApiResponse.success(svc.getGraphData(kbId, maxNodes, excludeChunks));
    }

    @GetMapping("/stats")
    public ApiResponse<?> stats(@PathVariable String kbId) {
        return ApiResponse.success(svc.getGraphStats(kbId));
    }

    // ==================== 子图查询 ====================

    @GetMapping("/search")
    public ApiResponse<?> search(@PathVariable String kbId,
                                  @RequestParam String keyword,
                                  @RequestParam(required = false) Integer maxNodes) {
        return ApiResponse.success(svc.searchNodes(kbId, keyword, maxNodes));
    }

    @GetMapping("/labels")
    public ApiResponse<?> labels(@PathVariable String kbId) {
        return ApiResponse.success(svc.getLabels(kbId));
    }

    // ==================== 图谱构建 ====================

    @GetMapping("/index")
    public ApiResponse<?> index(@PathVariable String kbId) {
        return ApiResponse.success(svc.getIndexStatus(kbId));
    }

    @Loggable(category = LogCategory.operation, action = ActionType.graph_build_started, detail = "手动触发图谱构建")
    @PostMapping("/index/build")
    public ApiResponse<?> build(@PathVariable String kbId,
                                @RequestBody(required = false) Map<String, Object> body) {
        String mode = body != null ? (String) body.get("mode") : "full";
        List<String> fileIds = body != null ? (List<String>) body.get("fileIds") : null;
        svc.buildIndex(kbId, mode, fileIds);
        return ApiResponse.success();
    }

    @Loggable(category = LogCategory.operation, action = ActionType.graph_build_retried, detail = "清空后全量重建")
    @PostMapping("/index/retry")
    public ApiResponse<?> retry(@PathVariable String kbId) {
        svc.retryBuild(kbId);
        return ApiResponse.success();
    }

    @GetMapping("/index/build-status")
    public ApiResponse<?> buildStatus(@PathVariable String kbId) {
        return ApiResponse.success(svc.getBuildStatus(kbId));
    }

    // ==================== 文件图谱管理 ====================

    @Loggable(category = LogCategory.operation, action = ActionType.graph_deleted, target = "#fileId", detail = "删除文件图谱数据")
    @DeleteMapping("/file/{fileId}")
    public ApiResponse<?> deleteFileGraph(@PathVariable String kbId, @PathVariable String fileId) {
        svc.deleteFileGraph(kbId, fileId);
        return ApiResponse.success();
    }

    // ==================== PPR 排序（检索增强） ====================

    @PostMapping("/rank")
    public ApiResponse<?> rankChunks(@PathVariable String kbId,
                                     @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> entities = body != null ? (List<String>) body.get("entities") : List.of();
        int topK = body != null && body.containsKey("topK") ? ((Number) body.get("topK")).intValue() : 10;
        return ApiResponse.success(svc.rankChunksByPpr(kbId, entities, topK));
    }

    // ==================== 设置 ====================

    @GetMapping("/settings")
    public ApiResponse<?> getSettings(@PathVariable String kbId) {
        return ApiResponse.success(svc.getSettings(kbId));
    }

    @Loggable(category = LogCategory.operation, action = ActionType.graph_settings_updated, detail = "更新图谱设置")
    @PutMapping("/settings")
    public ApiResponse<?> saveSettings(@PathVariable String kbId,
                                       @RequestBody Map<String, Object> settings) {
        svc.saveSettings(kbId, settings);
        return ApiResponse.success();
    }
}
