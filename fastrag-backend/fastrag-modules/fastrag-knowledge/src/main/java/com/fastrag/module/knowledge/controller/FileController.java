package com.fastrag.module.knowledge.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.service.FileService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
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
public class FileController {
    private final FileService svc;
    private final MinioService minioService;
    private final KbFileMapper fileMapper;

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
        return ApiResponse.success(svc.update(kbId, id, p));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable String kbId, @PathVariable String id) {
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
}
