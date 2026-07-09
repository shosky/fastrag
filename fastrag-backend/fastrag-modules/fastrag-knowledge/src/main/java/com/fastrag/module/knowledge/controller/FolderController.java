package com.fastrag.module.knowledge.controller;

import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
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

    @GetMapping
    public ApiResponse<?> list(@PathVariable String kbId) {
        return ApiResponse.success(svc.list(kbId));
    }

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

    @GetMapping("/{id}/name")
    public ApiResponse<?> name(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.getName(kbId, id));
    }

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

    @Loggable(category = LogCategory.operation, action = ActionType.folder_deleted, detail = "删除文件夹")
    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId, @PathVariable String id) {
        svc.delete(kbId, id);
        return ApiResponse.success();
    }
}
