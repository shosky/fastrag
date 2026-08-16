package com.fastrag.module.retrieval.controller;

/**
 * 知识检索服务 REST 控制器。
 *
 * <p>提供知识检索、查询增强（建议/同义词/规则改写/图谱扩展）、
 * 检索日志管理和更新提醒等功能的 REST API。</p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>{@code POST /api/retrieval/search} - 知识检索（核心接口，支持向量/全文/混合模式）</li>
 *   <li>{@code GET  /api/retrieval/kb/{kbId}/chunks/count} - 获取知识库 chunk 总数</li>
 *   <li>{@code POST /api/query/suggest} - 查询建议</li>
 *   <li>{@code POST /api/query/expand-synonyms} - 同义词扩展</li>
 *   <li>{@code POST /api/query-rules/apply} - 查询规则改写</li>
 *   <li>{@code POST /api/graph/expand} - 知识图谱扩展</li>
 *   <li>{@code GET  /api/retrieval/logs} - 检索日志分页查询</li>
 *   <li>{@code GET  /api/retrieval/logs/analysis} - 检索日志统计分析</li>
 *   <li>{@code POST /api/retrieval/logs} - 添加检索日志</li>
 *   <li>{@code PUT  /api/retrieval/logs/{id}} - 更新检索日志</li>
 *   <li>{@code GET  /api/kb/{kbId}/update-remind} - 获取指定知识库的更新提醒</li>
 *   <li>{@code GET  /api/update-remind} - 查询更新提醒列表</li>
 *   <li>{@code POST /api/update-remind} - 保存更新提醒配置</li>
 *   <li>{@code PUT  /api/update-remind/{id}} - 更新提醒配置</li>
 *   <li>{@code DELETE /api/update-remind/{id}} - 删除提醒配置</li>
 * </ul>
 *
 * @see RetrievalService
 * @see QueryEnhanceService
 * @see RetrievalLogService
 * @see UpdateRemindService
 */
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.retrieval.entity.*;
import com.fastrag.module.retrieval.model.*;
import com.fastrag.module.retrieval.service.*; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class RetrievalController {
    private final RetrievalService retrievalService; private final QueryEnhanceService queryService;
    private final RetrievalLogService logService; private final UpdateRemindService remindService;
    @PostMapping("/api/retrieval/search") public ApiResponse<?> search(@RequestBody RetrievalRequest req) { return ApiResponse.success(retrievalService.search(req)); }
    @GetMapping("/api/retrieval/kb/{kbId}/chunks/count") public ApiResponse<?> count(@PathVariable String kbId) { return ApiResponse.success(retrievalService.getChunkCount(kbId)); }
    @PostMapping("/api/query/suggest") public ApiResponse<?> suggest(@RequestBody Map<String,Object> b) {
        var result = queryService.suggest(String.valueOf(b.get("query")));
        return ApiResponse.success(result.get("suggestedQuery"));
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
    @GetMapping("/api/kb/{kbId}/update-remind") public ApiResponse<?> remind(@PathVariable String kbId) { return ApiResponse.success(remindService.remind(kbId)); }
    @GetMapping("/api/update-remind") public ApiResponse<?> remindList(@RequestParam(required=false) String kbId) { return ApiResponse.success(remindService.list(kbId)); }
    @PostMapping("/api/update-remind") public ApiResponse<?> saveRemind(@RequestBody KbUpdateRemind remind) { return ApiResponse.success(remindService.save(remind)); }
    @PutMapping("/api/update-remind/{id}") public ApiResponse<?> updateRemind(@PathVariable String id,@RequestBody KbUpdateRemind remind) { remind.setId(id); return ApiResponse.success(remindService.save(remind)); }
    @DeleteMapping("/api/update-remind/{id}") public ApiResponse<?> deleteRemind(@PathVariable String id) { remindService.delete(id); return ApiResponse.success(); }
}
