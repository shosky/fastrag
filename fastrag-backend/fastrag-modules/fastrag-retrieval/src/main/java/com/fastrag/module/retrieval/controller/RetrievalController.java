package com.fastrag.module.retrieval.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.retrieval.model.*;
import com.fastrag.module.retrieval.service.*; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class RetrievalController {
    private final RetrievalService retrievalService; private final QueryEnhanceService queryService;
    @PostMapping("/api/retrieval/search") public ApiResponse<?> search(@RequestBody RetrievalRequest req) { return ApiResponse.success(retrievalService.search(req)); }
    @GetMapping("/api/retrieval/kb/{kbId}/chunks/count") public ApiResponse<?> count(@PathVariable String kbId) { return ApiResponse.success(retrievalService.getChunkCount(kbId)); }
    @PostMapping("/api/query/suggest") public ApiResponse<?> suggest(@RequestBody Map<String,String> b) { return ApiResponse.success(queryService.suggest(b.get("query"))); }
    @PostMapping("/api/query/expand-synonyms") public ApiResponse<?> synonyms(@RequestBody Map<String,String> b) { return ApiResponse.success(queryService.expandSynonyms(b.get("query"))); }
    @PostMapping("/api/query-rules/apply") public ApiResponse<?> applyRules(@RequestBody Map<String,String> b) { return ApiResponse.success(queryService.applyQueryRules(b.get("query"))); }
    @PostMapping("/api/graph/expand") public ApiResponse<?> expand(@RequestBody Map<String,Object> b) { return ApiResponse.success(queryService.expandGraph((String)b.get("kbId"),(String)b.get("query"),b.get("depth")instanceof Integer?(int)b.get("depth"):2,b.get("maxEntities")instanceof Integer?(int)b.get("maxEntities"):20)); }
}
