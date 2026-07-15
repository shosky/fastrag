package com.fastrag.module.knowledge.controller;

import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.model.FileDto;
import com.fastrag.module.knowledge.service.FileService;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.security.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/kb/{kbId}/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {
    private final FileService svc;
    private final MinioService minioService;
    private final KbFileMapper fileMapper;
    private final LogService logService;

    @GetMapping
    public ApiResponse<?> list(@PathVariable String kbId) {
        return ApiResponse.success(svc.list(kbId));
    }

    @GetMapping("/deleted")
    public ApiResponse<?> deleted(@PathVariable String kbId) {
        return ApiResponse.success(svc.listDeleted(kbId));
    }

    @PostMapping
    public ApiResponse<?> upload(@PathVariable String kbId, @RequestParam("file") MultipartFile file) {
        FileDto result = svc.upload(kbId, file);
        // 记录文件上传日志（文件名在 Service 层生成，此处补充记录）
        try {
            String username = SecurityUtil.getCurrentUser() != null ? SecurityUtil.getCurrentUser().getUsername() : "system";
            String fileName = result != null ? result.getName() : file.getOriginalFilename();
            logService.addUpdateLog(kbId, "file_added", fileName, "上传文件: " + fileName, username);
            logService.addLog(kbId, LogCategory.operation, ActionType.file_uploaded,
                    fileName, "上传文件: " + fileName, username, "success", null);
        } catch (Exception e) {
            log.error("Failed to log file upload", e);
        }
        return ApiResponse.success(result);
    }

    @Loggable(category = LogCategory.operation, action = ActionType.file_processed, detail = "处理文件")
    @PostMapping("/{id}/process")
    public ApiResponse<?> process(@PathVariable String kbId, @PathVariable String id,
                                  @RequestBody(required = false) java.util.Map<String, Object> body) {
        String mode = body != null ? (String) body.get("processingMode") : "chunk";
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> qaConfig = body != null ? (java.util.Map<String, Object>) body.get("qaConfig") : null;
        svc.process(kbId, id, mode, qaConfig);
        return ApiResponse.success();
    }

    @Loggable(category = LogCategory.operation, action = ActionType.file_retried, detail = "重新处理文件")
    @PostMapping("/{id}/retry")
    public ApiResponse<?> retry(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.retryFile(kbId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String kbId, @PathVariable String id, @RequestBody Map<String, Object> p) {
        // update 前先取原始文件信息，用于对比日志
        KbFile before = fileMapper.selectById(id);
        FileDto result = svc.update(kbId, id, p);
        try {
            KbFile after = fileMapper.selectById(id);
            String fileName = after != null ? after.getName() : (before != null ? before.getName() : id);
            String username = SecurityUtil.getCurrentUser() != null ? SecurityUtil.getCurrentUser().getUsername() : "system";
            // 根据 patch 内容区分具体操作
            String detail = buildUpdateDetail(p, before, after);
            logService.addUpdateLog(kbId, "file_updated", fileName, detail, username);
            logService.addLog(kbId, LogCategory.operation, ActionType.file_updated,
                    fileName, detail, username, "success", null);
        } catch (Exception e) {
            log.error("Failed to log file update", e);
        }
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId, @PathVariable String id) {
        try {
            KbFile kf = fileMapper.selectById(id);
            String username = SecurityUtil.getCurrentUser() != null ? SecurityUtil.getCurrentUser().getUsername() : "system";
            String fileName = kf != null ? kf.getName() : id;
            logService.addUpdateLog(kbId, "file_removed", fileName, "删除文件", username);
            logService.addLog(kbId, LogCategory.operation, ActionType.file_removed,
                    fileName, "删除文件: " + fileName, username, "success", null);
        } catch (Exception e) {
            log.error("Failed to log file removal", e);
        }
        svc.delete(kbId, id);
        return ApiResponse.success();
    }

    @Loggable(category = LogCategory.operation, action = ActionType.file_restored, detail = "恢复文件")
    @PostMapping("/{id}/restore")
    public ApiResponse<?> restore(@PathVariable String kbId, @PathVariable String id) {
        svc.restore(kbId, id);
        return ApiResponse.success();
    }

    @Loggable(category = LogCategory.operation, action = ActionType.file_permanent_deleted, detail = "永久删除文件")
    @DeleteMapping("/{id}/permanent")
    public ApiResponse<?> permDelete(@PathVariable String kbId, @PathVariable String id) {
        svc.permanentDelete(kbId, id);
        return ApiResponse.success();
    }

    @Loggable(category = LogCategory.operation, action = ActionType.file_permanent_deleted, detail = "清空回收站")
    @DeleteMapping("/recycle-bin")
    public ApiResponse<?> emptyBin(@PathVariable String kbId) {
        svc.emptyRecycleBin(kbId);
        return ApiResponse.success();
    }

    @Loggable(category = LogCategory.operation, action = ActionType.file_copied, detail = "复制文件")
    @PostMapping("/{id}/copy")
    public ApiResponse<?> copy(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.copy(kbId, id));
    }

    @Loggable(category = LogCategory.operation, action = ActionType.file_moved, detail = "跨知识库移动文件")
    @PostMapping("/{id}/move")
    public ApiResponse<?> moveToKb(@PathVariable String kbId, @PathVariable String id,
                                   @RequestBody Map<String, String> body) {
        String targetKbId = body.get("targetKbId");
        String targetFolderId = body.get("targetFolderId");
        if (targetKbId == null || targetKbId.isBlank()) {
            return ApiResponse.badRequest("目标知识库 ID 不能为空");
        }
        return ApiResponse.success(svc.moveToKb(kbId, id, targetKbId, targetFolderId));
    }

    @GetMapping("/{id}/processing-status")
    public ApiResponse<?> status(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.getProcessingStatus(kbId, id));
    }

    @GetMapping("/{id}/preview")
    public ApiResponse<?> preview(@PathVariable String kbId, @PathVariable String id,
                                  @RequestParam(required = false) String strategyId) {
        return ApiResponse.success(svc.previewChunks(kbId, id, strategyId));
    }

    @Loggable(category = LogCategory.operation, action = ActionType.file_downloaded, detail = "下载文件")
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String kbId, @PathVariable String id) {
        KbFile file = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, id).eq(KbFile::getKbId, kbId));
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            InputStream stream = minioService.download(file.getObjectKey());
            String encodedName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20");

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            String ext = file.getExtension() != null ? file.getExtension().toLowerCase() : "";
            if (ext.matches("jpg|jpeg|png|gif|bmp|webp")) {
                mediaType = MediaType.parseMediaType("image/" + (ext.equals("jpg") ? "jpeg" : ext));
            } else if (ext.matches("mp3|wav|m4a|aac|ogg")) {
                mediaType = MediaType.parseMediaType("audio/" + ext);
            } else if (ext.matches("mp4|webm|avi|mov")) {
                mediaType = MediaType.parseMediaType("video/" + ext);
            } else if (ext.equals("pdf")) {
                mediaType = MediaType.APPLICATION_PDF;
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                    .contentType(mediaType)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 下载音频切片文件
     * 切片文件存储在: {kbId}/{fileId}/segments/{chunkIndex}.{ext}
     */
    @GetMapping("/{id}/segments/{chunkIndex}")
    public ResponseEntity<InputStreamResource> downloadSegment(
            @PathVariable String kbId, @PathVariable String id, @PathVariable int chunkIndex) {
        KbFile file = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, id).eq(KbFile::getKbId, kbId));
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        String ext = file.getExtension() != null ? file.getExtension().toLowerCase() : "mp3";
        String segKey = kbId + "/" + id + "/segments/" + chunkIndex + "." + ext;

        try {
            InputStream stream = minioService.download(segKey);
            MediaType mediaType = MediaType.parseMediaType("audio/" + ext);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .contentType(mediaType)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            log.warn("Segment not found: {} (chunkIndex={})", segKey, chunkIndex);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 下载 PDF 提取的页面图片
     * 图片存储在: {kbId}/{fileId}/images/{imageKey}
     */
    @GetMapping("/{id}/images/{imageKey}")
    public ResponseEntity<InputStreamResource> downloadPageImage(
            @PathVariable String kbId, @PathVariable String id, @PathVariable String imageKey) {
        try {
            String objectKey = kbId + "/" + id + "/images/" + imageKey;
            InputStream stream = minioService.download(objectKey);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .contentType(MediaType.IMAGE_PNG)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            log.warn("Page image not found: {} / {}", id, imageKey);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 根据 patch 内容和前后文件状态，生成差异化的日志 detail
     */
    private String buildUpdateDetail(Map<String, Object> patch, KbFile before, KbFile after) {
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        if (patch.containsKey("name") && before != null && after != null) {
            parts.add("重命名: " + before.getName() + " → " + after.getName());
        }
        if (patch.containsKey("folderId")) {
            String newFolderId = after != null && after.getFolderId() != null ? after.getFolderId() : "根目录";
            parts.add("移动到文件夹: " + newFolderId);
        }
        if (patch.containsKey("enableGraphBuild")) {
            parts.add("图谱构建开关已变更");
        }
        return parts.isEmpty() ? "更新文件信息" : String.join("，", parts);
    }
}
