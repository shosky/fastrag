package com.fastrag.module.graph.controller;

/**
 * 知识图谱数据管理与可视化控制器。
 *
 * <p>提供知识图谱的构建、查询、可视化、检索增强等全功能REST API。
 * 该控制器是知识图谱模块的核心入口，涵盖了从图谱构建到数据消费的完整链路。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>图谱可视化：获取图谱全量/子图数据及统计信息</li>
 *   <li>子图查询：关键词搜索节点、获取标签列表</li>
 *   <li>图谱构建：触发索引构建、全量/增量重建、构建状态查询</li>
 *   <li>文件图谱管理：删除指定文件的图谱数据</li>
 *   <li>PPR排序：基于Personalized PageRank算法，利用实体对文档chunk进行相关性排序，用于检索增强</li>
 *   <li>图谱设置：获取和更新图谱构建参数配置</li>
 * </ul>
 *
 * <p>REST API端点：</p>
 * <ul>
 *   <li>图谱可视化：GET /graph, GET /graph/stats</li>
 *   <li>子图查询：GET /graph/search, GET /graph/labels</li>
 *   <li>图谱构建：GET /graph/index, POST /graph/index/build, POST /graph/index/retry, GET /graph/index/build-status</li>
 *   <li>文件管理：DELETE /graph/file/{fileId}</li>
 *   <li>PPR排序：POST /graph/rank</li>
 *   <li>设置：GET /graph/settings, PUT /graph/settings</li>
 * </ul>
 *
 * <p>所有API均以 /api/kb/{kbId}/graph 为基础路径，通过kbId关联到具体知识库。</p>
 *
 * @see GraphService
 */
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

    /**
     * 重放式重建（零 LLM 成本，ADR-0002 存量迁移）：
     * 重放 kb_chunk.extraction_result 持久化的抽取结果写入图谱；
     * 历史上失败被误标已构建（无抽取结果）的 chunk 自动重置为待提取，由后续增量构建重新抽取。
     */
    @Loggable(category = LogCategory.operation, action = ActionType.graph_build_started, detail = "重放抽取结果重建图谱")
    @PostMapping("/index/replay")
    public ApiResponse<?> replay(@PathVariable String kbId) {
        svc.buildIndex(kbId, "replay", null);
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

    // ==================== 同义实体合并 ====================

    /** 合并候选发现：基于实体 embedding 余弦相似度，仅建议不自动合并 */
    @GetMapping("/merge-candidates")
    public ApiResponse<?> mergeCandidates(@PathVariable String kbId,
                                          @RequestParam(defaultValue = "0.92") double threshold,
                                          @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(svc.findMergeCandidates(kbId, threshold, limit));
    }

    /** 手动合并：source 的边与提及迁移到 target 后删除 source */
    @Loggable(category = LogCategory.operation, action = ActionType.graph_settings_updated, detail = "合并同义实体")
    @PostMapping("/merge")
    public ApiResponse<?> mergeEntities(@PathVariable String kbId,
                                        @RequestBody Map<String, Object> body) {
        String sourceId = body != null ? (String) body.get("sourceId") : null;
        String targetId = body != null ? (String) body.get("targetId") : null;
        svc.mergeEntities(kbId, sourceId, targetId);
        return ApiResponse.success();
    }
}
