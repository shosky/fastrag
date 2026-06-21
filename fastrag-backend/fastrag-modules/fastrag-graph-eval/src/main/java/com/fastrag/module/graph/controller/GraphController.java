package com.fastrag.module.graph.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.graph.service.GraphService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/kb/{kbId}/graph") @RequiredArgsConstructor
public class GraphController {
    private final GraphService svc;
    @GetMapping public ApiResponse<?> data(@PathVariable String kbId,@RequestParam(required=false) Integer maxNodes,@RequestParam(required=false) Boolean excludeChunks) { return ApiResponse.success(svc.getGraphData(kbId,maxNodes,excludeChunks)); }
    @GetMapping("/stats") public ApiResponse<?> stats(@PathVariable String kbId) { return ApiResponse.success(svc.getGraphStats(kbId)); }
    @GetMapping("/index") public ApiResponse<?> indexStatus(@PathVariable String kbId) { return ApiResponse.success(svc.getIndexStatus(kbId)); }
    @PostMapping("/index/build") public ApiResponse<?> build(@PathVariable String kbId,@RequestBody(required=false) Map<String,Object> body) { svc.buildIndex(kbId,body!=null?(String)body.get("mode"):"full",body!=null?(List<String>)body.get("fileIds"):null); return ApiResponse.success(); }
    @GetMapping("/index/build-status") public ApiResponse<?> buildStatus(@PathVariable String kbId) { return ApiResponse.success(svc.getBuildStatus(kbId)); }
    @GetMapping("/settings") public ApiResponse<?> settings(@PathVariable String kbId) { return ApiResponse.success(svc.getSettings(kbId)); }
    @PutMapping("/settings") public ApiResponse<?> saveSettings(@PathVariable String kbId,@RequestBody Map<String,Object> s) { svc.saveSettings(kbId,s); return ApiResponse.success(); }
}
