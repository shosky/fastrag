package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.entity.KbAutoCorrection;
import com.fastrag.module.knowledge.entity.KbSearchAssociation;
import com.fastrag.module.knowledge.service.SmartSearchService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/kb/{kbId}") @RequiredArgsConstructor
public class SmartSearchController {
    private final SmartSearchService svc;
    // 搜索联想
    @GetMapping("/search-associations") public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String dimension,@RequestParam(required=false) String keyword,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) { return ApiResponse.success(svc.pageAssociations(kbId,dimension,keyword,page,pageSize)); }
    @PostMapping("/search-associations") public ApiResponse<?> create(@PathVariable String kbId,@RequestBody KbSearchAssociation a) { a.setKbId(kbId); return ApiResponse.success(svc.createAssociation(a)); }
    @PutMapping("/search-associations/{id}") public ApiResponse<?> update(@PathVariable String id,@RequestBody KbSearchAssociation a) { return ApiResponse.success(svc.updateAssociation(id,a)); }
    @DeleteMapping("/search-associations/{id}") public ApiResponse<?> delete(@PathVariable String id) { svc.deleteAssociation(id); return ApiResponse.success(); }
    @GetMapping("/search-associations/search") public ApiResponse<?> search(@PathVariable String kbId,@RequestParam String q,@RequestParam(required=false) String dimension,@RequestParam(defaultValue="10") int limit) { return ApiResponse.success(svc.search(kbId,q,dimension,limit)); }
    @PostMapping("/search-associations/judge") public ApiResponse<?> judge(@PathVariable String kbId,@RequestBody Map<String,String> body) { return ApiResponse.success(svc.judge(kbId,body.get("dimension"),body.get("query"),body.get("targetText"))); }
    @GetMapping("/search-associations/auto-correct") public ApiResponse<?> autoCorrect(@PathVariable String kbId,@RequestParam String q) { return ApiResponse.success(svc.autoCorrect(kbId,q)); }
    // 纠错规则
    @GetMapping("/auto-corrections") public ApiResponse<?> corrections(@PathVariable String kbId) { return ApiResponse.success(svc.listCorrections(kbId)); }
    @PostMapping("/auto-corrections") public ApiResponse<?> createCorrection(@PathVariable String kbId,@RequestBody KbAutoCorrection c) { c.setKbId(kbId); return ApiResponse.success(svc.createCorrection(c)); }
    @PutMapping("/auto-corrections/{id}") public ApiResponse<?> updateCorrection(@PathVariable String id,@RequestBody KbAutoCorrection c) { return ApiResponse.success(svc.updateCorrection(id,c)); }
    @DeleteMapping("/auto-corrections/{id}") public ApiResponse<?> deleteCorrection(@PathVariable String id) { svc.deleteCorrection(id); return ApiResponse.success(); }
    @PostMapping("/auto-corrections/batch") public ApiResponse<?> batchCorrections(@PathVariable String kbId,@RequestBody List<KbAutoCorrection> items) { return ApiResponse.success(svc.batchImportCorrections(kbId,items)); }
}
