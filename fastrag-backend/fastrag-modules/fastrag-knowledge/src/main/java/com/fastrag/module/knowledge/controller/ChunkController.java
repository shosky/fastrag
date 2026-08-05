package com.fastrag.module.knowledge.controller;

import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.knowledge.model.ChunkCreateRequest;
import com.fastrag.module.knowledge.service.ChunkService;
import com.fastrag.security.annotation.KbAuth;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kb/{kbId}/chunks")
@RequiredArgsConstructor
public class ChunkController {
    private final ChunkService svc;

    @KbAuth(KBRole.viewer)
    @GetMapping
    public ApiResponse<?> list(@PathVariable String kbId,
                               @RequestParam(required = false) String fileId,
                               @RequestParam(defaultValue = "1") int page,
                               @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(svc.list(kbId, fileId, page, pageSize));
    }

    @KbAuth(KBRole.viewer)
    @GetMapping("/count")
    public ApiResponse<?> count(@PathVariable String kbId) {
        return ApiResponse.success(svc.getCount(kbId));
    }

    @KbAuth(KBRole.viewer)
    @GetMapping("/{id}")
    public ApiResponse<?> getById(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.getById(kbId, id));
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.chunk_created, detail = "新增分片")
    @PostMapping
    public ApiResponse<?> create(@PathVariable String kbId, @Valid @RequestBody ChunkCreateRequest req) {
        return ApiResponse.success(svc.create(kbId, req));
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.chunk_updated, detail = "更新分片")
    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String kbId, @PathVariable String id, @RequestBody Map<String, Object> fields) {
        return ApiResponse.success(svc.update(kbId, id, fields));
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.chunk_deleted, detail = "删除分片")
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId, @PathVariable String id) {
        svc.delete(kbId, id);
        return ApiResponse.success();
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.chunk_deleted, detail = "批量删除分片")
    @PostMapping("/batch-delete")
    public ApiResponse<?> batchDelete(@PathVariable String kbId, @RequestBody List<String> ids) {
        svc.batchDelete(kbId, ids);
        return ApiResponse.success();
    }
}
