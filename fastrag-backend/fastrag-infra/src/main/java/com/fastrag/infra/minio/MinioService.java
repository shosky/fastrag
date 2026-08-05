package com.fastrag.infra.minio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;

/**
 * 文件存储服务 - 使用本地文件系统替代 MinIO
 */
@Service
public class MinioService {

    private static final Logger log = LoggerFactory.getLogger(MinioService.class);

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

    /**
     * 递归删除指定前缀下的所有文件（本地文件系统即删除目录树）
     *
     * @param prefix 路径前缀，如 "kbId/fileId"
     */
    public void deleteByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return;
        try {
            Path dir = getBasePath().resolve(prefix);
            if (Files.exists(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    log.warn("Failed to delete {}: {}", path, e.getMessage());
                                }
                            });
                }
                log.debug("Directory deleted from local storage: {}", prefix);
            }
        } catch (IOException e) {
            log.error("Failed to delete directory: {}", prefix, e);
        }
    }

    /**
     * 复制文件到新路径（用于跨知识库移动文件）
     */
    public void copy(String sourceKey, String destKey) {
        if (sourceKey == null || destKey == null || sourceKey.equals(destKey)) {
            return;
        }
        try {
            Path source = getBasePath().resolve(sourceKey);
            Path dest = getBasePath().resolve(destKey);
            Files.createDirectories(dest.getParent());
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            log.debug("File copied from {} to {}", sourceKey, destKey);
        } catch (IOException e) {
            throw new RuntimeException("文件复制失败: " + sourceKey + " → " + destKey, e);
        }
    }
}
