package com.fastrag.module.knowledge.controller;

/**
 * 知识库文件管理控制器，提供文件上传、处理、下载、回收站等完整生命周期管理的 REST API。
 *
 * <p>核心职责：管理知识库中的文档文件，包括上传、解析处理、预览、下载、删除、恢复、跨知识库移动等操作，
 * 同时管理音频切片和 PDF 图片的下载。
 *
 * <p>提供的 REST API 端点（基础路径 {@code /api/kb/{kbId}/files}）：
 * <ul>
 *   <li>{@code GET /} — 列出知识库下所有文件（viewer 权限）</li>
 *   <li>{@code GET /deleted} — 列出回收站中的文件（viewer 权限）</li>
 *   <li>{@code POST /} — 上传文件到知识库（可选指定 folderId），上传后自动发送摄入消息到 MQ（editor 权限）</li>
 *   <li>{@code POST /{id}/process} — 手动触发文件解析处理，支持 chunk 和 qa 两种模式（editor 权限）</li>
 *   <li>{@code POST /{id}/retry} — 重新处理失败的文件（editor 权限）</li>
 *   <li>{@code PUT /{id}} — 更新文件信息（重命名、移动文件夹等）（editor 权限）</li>
 *   <li>{@code DELETE /{id}} — 软删除文件（移入回收站）（editor 权限）</li>
 *   <li>{@code POST /{id}/restore} — 从回收站恢复文件（editor 权限）</li>
 *   <li>{@code DELETE /{id}/permanent} — 永久删除文件（editor 权限）</li>
 *   <li>{@code DELETE /recycle-bin} — 清空回收站（editor 权限）</li>
 *   <li>{@code POST /{id}/copy} — 复制文件（editor 权限）</li>
 *   <li>{@code POST /{id}/move} — 跨知识库移动文件（editor 权限）</li>
 *   <li>{@code GET /{id}/processing-status} — 获取文件处理状态和进度（viewer 权限）</li>
 *   <li>{@code GET /{id}/preview} — 预览文件解析后的分块结果（viewer 权限）</li>
 *   <li>{@code GET /{id}/download} — 下载原始文件，根据扩展名自动设置 Content-Type（viewer 权限）</li>
 *   <li>{@code GET /{id}/segments/{chunkIndex}} — 下载音频切片文件（viewer 权限）</li>
 *   <li>{@code GET /{id}/images/{imageKey}} — 下载 PDF 提取的页面图片（viewer 权限）</li>
 * </ul>
 *
 * <p>所有文件操作通过 {@link KbAuth} 注解进行知识库级别权限校验，
 * 并通过 {@link LogService} 记录操作日志。
 */
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.KBRole;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.common.response.ApiResponse;
import com.fastrag.security.annotation.KbAuth;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.model.FileBatchTagRequest;
import com.fastrag.module.knowledge.model.FileDto;
import com.fastrag.module.knowledge.model.FileMetadataUpdateRequest;
import com.fastrag.module.knowledge.model.FileProcessRequest;
import com.fastrag.module.knowledge.model.ParseStrategyRequest;
import com.fastrag.module.knowledge.service.FileMetadataService;
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
import java.util.List;
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
    private final FileMetadataService metadataService;

    @KbAuth(KBRole.viewer)
    @GetMapping
    public ApiResponse<?> list(@PathVariable String kbId) {
        return ApiResponse.success(svc.list(kbId));
    }

    @KbAuth(KBRole.viewer)
    @GetMapping("/deleted")
    public ApiResponse<?> deleted(@PathVariable String kbId) {
        return ApiResponse.success(svc.listDeleted(kbId));
    }

    @KbAuth(KBRole.editor)
    @PostMapping
    public ApiResponse<?> upload(@PathVariable String kbId, @RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "folderId", required = false) String folderId) {
        FileDto result = svc.upload(kbId, file, folderId);
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

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_processed, detail = "处理文件")
    @PostMapping("/{id}/process")
    public ApiResponse<?> process(@PathVariable String kbId, @PathVariable String id,
                                  @RequestBody(required = false) FileProcessRequest req) {
        if (req == null) {
            req = new FileProcessRequest();
        }
        svc.process(kbId, id, req);
        return ApiResponse.success();
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_retried, detail = "重新处理文件")
    @PostMapping("/{id}/retry")
    public ApiResponse<?> retry(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.retryFile(kbId, id));
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_retried, detail = "重新分片")
    @PostMapping("/{id}/re-chunk")
    public ApiResponse<?> reChunk(@PathVariable String kbId, @PathVariable String id,
                                  @RequestBody(required = false) Map<String, Object> body) {
        // strategyId 三态：字段缺省=沿用当前绑定；非空 id=换绑重切；空串=清除覆盖回自动匹配重切
        String strategyId = null;
        String presetStrategy = null;
        if (body != null) {
            if (body.containsKey("strategyId")) {
                Object v = body.get("strategyId");
                strategyId = v == null ? "" : String.valueOf(v);
            }
            // presetStrategy（如 "structure_aware"）：仅当未显式传 strategyId 时生效，
            // 由服务端自动确保文件绑定该策略（「按结构分片」一键入口）
            if (body.containsKey("presetStrategy")) {
                Object v = body.get("presetStrategy");
                presetStrategy = v == null ? null : String.valueOf(v);
            }
        }
        return ApiResponse.success(svc.reChunkFile(kbId, id, strategyId, presetStrategy));
    }

    /**
     * 获取已解析文件的 Markdown 全文。
     * 数据由 IngestionConsumer 在解析完成后按约定路径 {kbId}/{fileId}/parsed.md 落盘到 MinIO。
     * 文件不存在或尚未生成（音视频/图片/未处理文档）时返回 markdown_not_ready。
     */
    @KbAuth(KBRole.viewer)
    @GetMapping("/{fileId}/markdown")
    public ApiResponse<?> getMarkdown(@PathVariable String kbId, @PathVariable String fileId) {
        String key = kbId + "/" + fileId + "/parsed.md";
        try (InputStream is = minioService.download(key)) {
            String md = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return ApiResponse.success(md);
        } catch (Exception e) {
            return ApiResponse.error(404,
                    "Markdown 尚未生成。请重新上传该文档，或确认文件为文档类（doc/docx/pdf/pptx/xlsx/txt/md 等）。");
        }
    }

    /**
     * 保存已解析文件的 Markdown 全文（写入 MinIO 约定路径）。
     * 可选 {@code rechunk=true} 在保存后触发重新分片（沿用现有 re-chunk 底层能力）。
     */
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_updated, detail = "保存文件 Markdown 全文")
    @PutMapping("/{fileId}/markdown")
    public ApiResponse<?> saveMarkdown(@PathVariable String kbId, @PathVariable String fileId,
                                       @RequestParam(name = "rechunk", defaultValue = "false") boolean rechunk,
                                       @RequestBody String markdown) {
        if (markdown == null) markdown = "";
        String key = kbId + "/" + fileId + "/parsed.md";
        try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(
                markdown.getBytes(StandardCharsets.UTF_8))) {
            minioService.upload(key, in, "text/markdown");
        } catch (Exception e) {
            return ApiResponse.error(500, "Markdown 保存失败：" + e.getMessage());
        }
        if (rechunk) {
            try {
                svc.reChunkFile(kbId, fileId, null);
            } catch (Exception e) {
                return ApiResponse.error(500,
                        "Markdown 已保存，但触发重新分片失败：" + e.getMessage());
            }
        }
        return ApiResponse.success();
    }

    @KbAuth(KBRole.editor)
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

    // ==================== 文件元数据管理（分册四 rag-file-metadata-management.md） ====================

    /**
     * 查看文件元数据（固定字段 + 标签 + 自定义属性 + 状态）。验收项 A1。
     */
    @KbAuth(KBRole.viewer)
    @GetMapping("/{id}/metadata")
    public ApiResponse<?> metadata(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(metadataService.getMetadata(kbId, id));
    }

    /**
     * 全量更新文件元数据（固定字段 + 自定义属性取值）；写入即视为人工校订（revised/manual），
     * 事务内回填 kb_chunk 冗余列。标签走独立 PUT /{id}/tags。验收项 A2/A7。
     */
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_updated, detail = "更新文件元数据")
    @PutMapping("/{id}/metadata")
    public ApiResponse<?> updateMetadata(@PathVariable String kbId, @PathVariable String id,
                                         @RequestBody FileMetadataUpdateRequest req) {
        return ApiResponse.success(metadataService.updateMetadata(kbId, id, req));
    }

    /**
     * 替换式设置单文件标签（body: {tagIds:[...]}）。验收项 A3。
     */
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_updated, detail = "更新文件标签")
    @PutMapping("/{id}/tags")
    public ApiResponse<?> setTags(@PathVariable String kbId, @PathVariable String id,
                                  @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> tagIds = body != null && body.get("tagIds") instanceof List
                ? (List<String>) body.get("tagIds") : List.of();
        metadataService.setFileTags(kbId, id, tagIds);
        return ApiResponse.success();
    }

    /**
     * 批量打标（增量加/删一批文件的标签）。验收项 A4。
     */
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_updated, detail = "批量打标")
    @PostMapping("/batch-tags")
    public ApiResponse<?> batchTags(@PathVariable String kbId, @RequestBody FileBatchTagRequest req) {
        return ApiResponse.success(metadataService.batchSetTags(kbId, req));
    }

    /**
     * 存量文件元数据补抽（规则抽取；仅覆盖 none/partial，force=true 强制重抽，失败置 partial）。验收项 A8。
     */
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_updated, detail = "存量元数据补抽")
    @PostMapping("/metadata/extract")
    public ApiResponse<?> extractMetadata(@PathVariable String kbId,
                                          @RequestBody(required = false) Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> fileIds = body != null && body.get("fileIds") instanceof List
                ? (List<String>) body.get("fileIds") : List.of();
        boolean force = body != null && Boolean.TRUE.equals(body.get("force"));
        return ApiResponse.success(metadataService.extractMetadata(kbId, fileIds, force));
    }

    @KbAuth(KBRole.editor)
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

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_restored, detail = "恢复文件")
    @PostMapping("/{id}/restore")
    public ApiResponse<?> restore(@PathVariable String kbId, @PathVariable String id) {
        svc.restore(kbId, id);
        return ApiResponse.success();
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_permanent_deleted, detail = "永久删除文件")
    @DeleteMapping("/{id}/permanent")
    public ApiResponse<?> permDelete(@PathVariable String kbId, @PathVariable String id) {
        svc.permanentDelete(kbId, id);
        return ApiResponse.success();
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_permanent_deleted, detail = "清空回收站")
    @DeleteMapping("/recycle-bin")
    public ApiResponse<?> emptyBin(@PathVariable String kbId) {
        svc.emptyRecycleBin(kbId);
        return ApiResponse.success();
    }

    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_copied, detail = "复制文件")
    @PostMapping("/{id}/copy")
    public ApiResponse<?> copy(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.copy(kbId, id));
    }

    @KbAuth(KBRole.editor)
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

    @KbAuth(KBRole.viewer)
    @GetMapping("/{id}/processing-status")
    public ApiResponse<?> status(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.getProcessingStatus(kbId, id));
    }

    @KbAuth(KBRole.viewer)
    @GetMapping("/{id}/preview")
    public ApiResponse<?> preview(@PathVariable String kbId, @PathVariable String id,
                                  @RequestParam(required = false) String strategyId,
                                  @RequestParam(required = false) String customConfig) {
        // customConfig：自定义临时策略预览（JSON：{parseMethod, advanced}），非空时优先于 strategyId
        Map<String, Object> configMap = null;
        if (StrUtil.isNotBlank(customConfig)) {
            try {
                configMap = JSONUtil.toBean(customConfig, Map.class);
            } catch (Exception e) {
                return ApiResponse.badRequest("customConfig 参数不是合法 JSON: " + e.getMessage());
            }
        }
        return ApiResponse.success(svc.previewChunks(kbId, id, strategyId, configMap));
    }

    /**
     * 保存文件级专属自定义策略并重新分片（ADR-0001：绑定变更与重切原子完成）。
     * body 结构同解析策略表单：name/description/parseMethod/extensions/advanced/llmModel。
     */
    @KbAuth(KBRole.editor)
    @Loggable(category = LogCategory.operation, action = ActionType.file_retried, detail = "保存文件自定义解析策略并重新分片")
    @PostMapping("/{id}/strategy")
    public ApiResponse<?> saveFileStrategy(@PathVariable String kbId, @PathVariable String id,
                                           @RequestBody ParseStrategyRequest req) {
        return ApiResponse.success(svc.saveFileStrategy(kbId, id, req));
    }

    @KbAuth(KBRole.viewer)
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
    @KbAuth(KBRole.viewer)
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
    @KbAuth(KBRole.viewer)
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
