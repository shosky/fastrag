package com.fastrag.module.knowledge.controller;

/**
 * 文档分片（Chunk）管理控制器，提供分片的 CRUD 和批量操作 REST API。
 *
 * <p>核心职责：管理知识库中文档经分块处理后的 Chunk 数据，支持手动创建、编辑、删除和批量删除。
 *
 * <p>提供的 REST API 端点（基础路径 {@code /api/kb/{kbId}/chunks}）：
 * <ul>
 *   <li>{@code GET /} — 分页列出指定知识库（可选按 fileId 过滤）的分片列表（viewer 权限）</li>
 *   <li>{@code GET /count} — 获取知识库的分片总数（viewer 权限）</li>
 *   <li>{@code GET /{id}} — 获取单个分片详情（viewer 权限）</li>
 *   <li>{@code POST /} — 新建分片（editor 权限）</li>
 *   <li>{@code PUT /{id}} — 更新分片内容（editor 权限）</li>
 *   <li>{@code DELETE /{id}} — 删除单个分片（editor 权限）</li>
 *   <li>{@code POST /batch-delete} — 批量删除分片（editor 权限）</li>
 * </ul>
 *
 * <p>所有端点均通过 {@link KbAuth} 注解进行知识库级别的权限校验。
 */
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
