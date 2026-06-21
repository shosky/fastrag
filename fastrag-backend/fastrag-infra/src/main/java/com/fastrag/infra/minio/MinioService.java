package com.fastrag.infra.minio;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {
    private final MinioClient minioClient;
    @Value("${minio.bucket:fastrag}") private String bucket;

    public void ensureBucket() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) { log.error("Failed to ensure bucket", e); }
    }

    public String upload(String objectKey, InputStream stream, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(stream, -1, 10485760).contentType(contentType).build());
            return objectKey;
        } catch (Exception e) { throw new RuntimeException("文件上传失败", e); }
    }

    public InputStream download(String objectKey) {
        try { return minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build()); }
        catch (Exception e) { throw new RuntimeException("文件下载失败", e); }
    }

    public void delete(String objectKey) {
        try { minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build()); }
        catch (Exception e) { log.error("Failed to delete: {}", objectKey, e); }
    }
}
