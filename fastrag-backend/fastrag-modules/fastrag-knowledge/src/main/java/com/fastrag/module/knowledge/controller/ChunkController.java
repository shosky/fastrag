package com.fastrag.module.knowledge.controller;
import com.fastrag.common.response.ApiResponse; import com.fastrag.module.knowledge.service.ChunkService;
import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/kb/{kbId}/chunks") @RequiredArgsConstructor
public class ChunkController {
    private final ChunkService svc;
    @GetMapping public ApiResponse<?> list(@PathVariable String kbId,@RequestParam(required=false) String fileId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) { return ApiResponse.success(svc.list(kbId,fileId,page,pageSize)); }
    @GetMapping("/count") public ApiResponse<?> count(@PathVariable String kbId) { return ApiResponse.success(svc.getCount(kbId)); }
}
