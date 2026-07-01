package com.fastrag.module.knowledge.controller;

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
        return ApiResponse.success(svc.upload(kbId, file));
    }

    @PostMapping("/{id}/process")
    public ApiResponse<?> process(@PathVariable String kbId, @PathVariable String id) {
        svc.process(kbId, id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable String kbId, @PathVariable String id, @RequestBody Map<String, Object> p) {
        FileDto result = svc.update(kbId, id, p);
        // 记录文件更新日志
        try {
            KbFile kf = fileMapper.selectById(id);
            String username = SecurityUtil.getCurrentUser() != null ? SecurityUtil.getCurrentUser().getUsername() : "system";
            logService.addUpdateLog(kbId, "file_updated",
                    kf != null ? kf.getName() : id, "更新文件信息", username);
        } catch (Exception e) {
            log.error("Failed to log file update", e);
        }
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId, @PathVariable String id) {
        // 先记录文件名，再删除
        try {
            KbFile kf = fileMapper.selectById(id);
            String username = SecurityUtil.getCurrentUser() != null ? SecurityUtil.getCurrentUser().getUsername() : "system";
            logService.addUpdateLog(kbId, "file_removed",
                    kf != null ? kf.getName() : id, "删除文件", username);
        } catch (Exception e) {
            log.error("Failed to log file removal", e);
        }
        svc.delete(kbId, id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<?> restore(@PathVariable String kbId, @PathVariable String id) {
        svc.restore(kbId, id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}/permanent")
    public ApiResponse<?> permDelete(@PathVariable String kbId, @PathVariable String id) {
        svc.permanentDelete(kbId, id);
        return ApiResponse.success();
    }

    @DeleteMapping("/recycle-bin")
    public ApiResponse<?> emptyBin(@PathVariable String kbId) {
        svc.emptyRecycleBin(kbId);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/copy")
    public ApiResponse<?> copy(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.copy(kbId, id));
    }

    @GetMapping("/{id}/processing-status")
    public ApiResponse<?> status(@PathVariable String kbId, @PathVariable String id) {
        return ApiResponse.success(svc.getProcessingStatus(kbId, id));
    }

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
}
