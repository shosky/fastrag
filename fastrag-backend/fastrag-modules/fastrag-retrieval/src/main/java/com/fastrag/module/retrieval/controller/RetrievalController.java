package com.fastrag.module.retrieval.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.retrieval.model.*;
import com.fastrag.module.retrieval.service.*; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class RetrievalController {
    private final RetrievalService retrievalService; private final QueryEnhanceService queryService;
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
}
