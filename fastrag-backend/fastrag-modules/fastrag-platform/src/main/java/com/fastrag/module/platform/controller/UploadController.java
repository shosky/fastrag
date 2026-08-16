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
 * 提供通用的文件上传接口，主要用于 Logo、品牌图片等非特定业务域的文件上传。
 * 文件存储基于 MinIO 对象存储服务（{@link com.fastrag.infra.minio.MinioService}），
 * 上传后返回可通过 HTTP 访问的文件 URL。
 * </p>
 *
 * <h3>REST API 端点：</h3>
 * <ul>
 *   <li>POST /api/upload — 上传文件（仅支持 image/jpeg、image/png 格式，大小不超过 1MB），
 *       文件存储路径为 uploads/brand/{uuid}.{ext}，返回可访问的 URL</li>
 * </ul>
 *
 * <p>上传流程：校验文件类型和大小 -> 生成 UUID 文件名 -> 通过 MinioService 存储到 MinIO ->
 * 返回 /api/files/{objectKey} 格式的访问 URL。</p>
 *
 * @see com.fastrag.infra.minio.MinioService
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
