package com.fastrag.infra.minio;

/**
 * 文件存储服务，提供文件上传、下载、删除和复制的统一抽象。
 *
 * <p>当前实现使用本地文件系统存储（替代 MinIO 对象存储），通过配置项
 * {@code storage.local.path}（默认 {@code ./uploads}）指定存储根目录。
 * 接口设计与 MinIO 兼容，后续可无缝切换到 MinIO 或 S3 等对象存储服务。
 *
 * <p>核心职责：为知识库模块提供文件持久化能力，包括原始文件上传、下载、按前缀批量删除等。
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>使用 Java NIO Files API 操作本地文件系统</li>
 *   <li>upload 时自动创建目标目录层级，支持同名文件覆盖</li>
 *   <li>deleteByPrefix 通过 Files.walk 递归遍历目录树，按逆序删除文件和空目录</li>
 *   <li>copy 方法支持跨路径文件复制，用于知识库间文件迁移场景</li>
 * </ul>
 *
 * <p>提供的核心能力：
 * <ul>
 *   <li>{@code upload} — 上传文件（输入流写入目标路径）</li>
 *   <li>{@code download} — 下载文件（返回输入流）</li>
 *   <li>{@code delete} — 删除单个文件</li>
 *   <li>{@code deleteByPrefix} — 递归删除指定前缀下的所有文件（用于文件级图谱清理）</li>
 *   <li>{@code copy} — 复制文件到新路径</li>
 * </ul>
 *
 * <p>与其他模块的交互：被知识库的文件处理服务调用，用于文档上传、文件预览和文件清理等场景。
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;

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
