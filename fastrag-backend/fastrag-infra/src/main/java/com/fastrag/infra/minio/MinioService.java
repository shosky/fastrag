package com.fastrag.infra.minio;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;

/**
 * 文件存储服务 - 使用本地文件系统替代 MinIO
 */
@Slf4j
@Service
public class MinioService {

    @Value("${storage.local.path:./uploads}")
    private String basePath;

    private Path getBasePath() {
        Path path = Path.of(basePath);
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            log.error("Failed to create storage directory: {}", path, e);
        }
        return path;
    }

    public String upload(String objectKey, InputStream stream, String contentType) {
        try {
            Path target = getBasePath().resolve(objectKey);
            Files.createDirectories(target.getParent());
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("File uploaded to local storage: {}", objectKey);
            return objectKey;
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    public InputStream download(String objectKey) {
        try {
            Path source = getBasePath().resolve(objectKey);
            return Files.newInputStream(source);
        } catch (IOException e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }

    public void delete(String objectKey) {
        try {
            Path source = getBasePath().resolve(objectKey);
            Files.deleteIfExists(source);
            log.debug("File deleted from local storage: {}", objectKey);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", objectKey, e);
        }
    }
}
