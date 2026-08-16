package com.fastrag.module.knowledge.controller;

/**
 * 知识库文件夹管理控制器，提供文件夹的 CRUD REST API。
 *
 * <p>核心职责：管理知识库内的文件夹结构，支持创建、列表、重命名和删除操作，
 * 用于组织知识库中的文件层级。
 *
 * <p>提供的 REST API 端点（基础路径 {@code /api/kb/{kbId}/folders}）：
 * <ul>
 *   <li>{@code GET /} — 列出知识库下所有文件夹（viewer 权限）</li>
 *   <li>{@code POST /} — 创建文件夹（支持指定 parentId 构建层级）（editor 权限）</li>
 *   <li>{@code GET /{id}/name} — 获取文件夹名称（viewer 权限）</li>
 *   <li>{@code PUT /{id}} — 重命名文件夹（editor 权限）</li>
 *   <li>{@code DELETE /{id}} — 删除文件夹（editor 权限）</li>
 * </ul>
 *
 * <p>所有端点通过 {@link KbAuth} 进行知识库级别权限校验，
 * 文件夹操作通过 {@link LogService} 记录日志。
 */
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.security.annotation.KbAuth;
import com.fastrag.module.knowledge.service.FolderService;
import com.fastrag.module.publish.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/kb/{kbId}/folders")
@RequiredArgsConstructor
@Slf4j
public class FolderController {
    private final FolderService svc;
    private final LogService logService;

    @KbAuth(KBRole.viewer)
    @GetMapping
    public ApiResponse<?> list(@PathVariable String kbId) {
        return ApiResponse.success(svc.list(kbId));
    }

    @KbAuth(KBRole.editor)
    @PostMapping
    public ApiResponse<?> create(@PathVariable String kbId, @RequestBody Map<String, String> b) {
        var result = svc.create(kbId, b.get("name"), b.get("parentId"));
        try {
            String folderName = b.get("name");
            logService.addLog(kbId, LogCategory.operation, ActionType.folder_created,
                    folderName != null ? folderName : "", "创建文件夹: " + folderName,
                    "system", "success", null);
        } catch (Exception e) {
            log.warn("Failed to log folder creation", e);
        }
        return ApiResponse.success(result);
    }

    @KbAuth(KBRole.viewer)
    @GetMapping("/{id}/name")
    public ApiResponse<?> name(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.getName(kbId, id));
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.folder_updated, detail = "重命名文件夹")
    @PutMapping("/{id}")
    public ApiResponse<?> rename(@PathVariable String kbId, @PathVariable String id,
                                 @RequestBody Map<String, String> b) {
        String newName = b.get("name");
        if (newName == null || newName.isBlank()) {
            return ApiResponse.badRequest("文件夹名称不能为空");
        }
        svc.rename(kbId, id, newName);
        return ApiResponse.success();
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.folder_deleted, detail = "删除文件夹")
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId, @PathVariable String id) {
        svc.delete(kbId, id);
        return ApiResponse.success();
    }
}
