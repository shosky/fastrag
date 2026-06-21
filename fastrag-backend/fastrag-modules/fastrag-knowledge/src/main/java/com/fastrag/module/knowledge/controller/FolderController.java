package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.service.FolderService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/kb/{kbId}/folders") @RequiredArgsConstructor
public class FolderController {
    private final FolderService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId) { return ApiResponse.success(svc.list(kbId)); }
    @PostMapping public ApiResponse<?> create(@PathVariable String kbId,@RequestBody java.util.Map<String,String> b) { return ApiResponse.success(svc.create(kbId,b.get("name"),b.get("parentId"))); }
    @GetMapping("/{id}/name") public ApiResponse<?> name(@PathVariable String kbId,@PathVariable String id) { return ApiResponse.success(svc.getName(kbId,id)); }
}
