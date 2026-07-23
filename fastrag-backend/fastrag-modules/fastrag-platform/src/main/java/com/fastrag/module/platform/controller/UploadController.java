package com.fastrag.module.platform.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.infra.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 通用文件上传控制器
 * <p>
 * 用于 Logo 等通用文件上传，不限定于特定业务域。
 * 文件存储使用 MinioService 基础设施。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UploadController {

    private final MinioService minioService;

    /**
     * 通用文件上传
     *
     * @param file 上传的文件（仅支持 image/jpeg, image/png，不超过 1MB）
     * @return 可访问的文件 URL
     */
    @PostMapping("/upload")
    public ApiResponse<?> upload(@RequestParam("file") MultipartFile file) {
        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType))) {
            return ApiResponse.badRequest("仅支持 jpg/jpeg/png 格式的图片");
        }

        // 校验文件大小（不超过 1MB）
        if (file.getSize() > 1024 * 1024) {
            return ApiResponse.badRequest("图片大小不能超过 1MB");
        }

        try {
            // 生成存储路径：uploads/brand/{uuid}.{ext}
            String ext = "image/jpeg".equals(contentType) ? "jpg" : "png";
            String objectKey = "uploads/brand/" + UUID.randomUUID() + "." + ext;

            minioService.upload(objectKey, file.getInputStream(), contentType);

            // 返回可访问的 URL
            String url = "/api/files/" + objectKey;
            log.info("File uploaded successfully: key={}, url={}", objectKey, url);
            return ApiResponse.success(url);
        } catch (Exception e) {
            log.error("File upload failed", e);
            return ApiResponse.serverError("文件上传失败：" + e.getMessage());
        }
    }
}
