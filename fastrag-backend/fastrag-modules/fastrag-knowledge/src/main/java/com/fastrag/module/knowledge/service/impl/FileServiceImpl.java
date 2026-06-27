package com.fastrag.module.knowledge.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.infra.rabbitmq.MessagePublisher;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.model.FileDto;
import com.fastrag.module.knowledge.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {
    private final KbFileMapper fileMapper;
    private final MinioService minioService;
    private final MessagePublisher messagePublisher;
    private final KbParseStrategyMapper strategyMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Override
    public List<FileDto> list(String kbId) {
        // 过滤已删除的文件
        return fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId)
                .isNull(KbFile::getDeletedAt)
                .orderByDesc(KbFile::getCreatedAt))
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public List<FileDto> listDeleted(String kbId) {
        // 查询已删除的文件需要绕过逻辑删除
        // 使用自定义 SQL 或直接查询
        return fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId)
                .isNotNull(KbFile::getDeletedAt)
                .orderByDesc(KbFile::getDeletedAt))
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public FileDto upload(String kbId, MultipartFile file) {
        String objectKey = kbId + "/" + IdUtil.fastSimpleUUID();

        // 上传文件到 MinIO
        try {
            minioService.upload(objectKey, file.getInputStream(), file.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("文件上传到存储失败", e);
        }

        // 保存元数据
        String extension = FileUtil.extName(file.getOriginalFilename());
        KbFile f = new KbFile();
        f.setKbId(kbId);
        f.setName(file.getOriginalFilename());
        f.setExtension(extension);
        f.setSize(file.getSize());
        f.setCategory(detectCategory(extension));
        f.setObjectKey(objectKey);
        f.setStatus("pending");
        f.setProgress(0);
        f.setChunkCount(0);
        fileMapper.insert(f);

        log.info("File uploaded: {}", f.getId());
        return toDto(f);
    }

    @Override
    public void process(String kbId, String fileId) {
        KbFile f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, kbId));
        if (f == null) throw new RuntimeException("File not found");

        // 获取解析策略
        String strategyId = resolveStrategy(kbId, f.getExtension());

        // 发送消息到 RabbitMQ 触发处理
        Map<String, Object> msg = new HashMap<>();
        msg.put("fileId", f.getId());
        msg.put("kbId", kbId);
        msg.put("objectKey", f.getObjectKey());
        msg.put("strategyId", strategyId);
        messagePublisher.publishIngestion(msg);

        log.info("File processing triggered: {}", f.getId());
    }

    @Override
    public FileDto update(String kbId, String fileId, Map<String, Object> patch) {
        var f = fileMapper.selectOne(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId).eq(KbFile::getId, fileId).isNull(KbFile::getDeletedAt));
        if (f == null) throw new RuntimeException("File not found");
        if (patch.containsKey("name")) f.setName((String) patch.get("name"));
        if (patch.containsKey("folderId")) f.setFolderId((String) patch.get("folderId"));
        fileMapper.updateById(f);
        return toDto(f);
    }

    @Override
    public void delete(String kbId, String fileId) {
        // 软删除：使用原生 SQL 确保 deleted_at 被更新
        jdbcTemplate.update("UPDATE kb_file SET deleted_at = NOW() WHERE id = ? AND kb_id = ?", fileId, kbId);
    }

    @Override
    public void restore(String kbId, String fileId) {
        // 恢复：清除 deletedAt
        fileMapper.update(null, new LambdaUpdateWrapper<KbFile>()
                .eq(KbFile::getId, fileId)
                .eq(KbFile::getKbId, kbId)
                .set(KbFile::getDeletedAt, null));
    }

    @Override
    public void permanentDelete(String kbId, String fileId) {
        fileMapper.deleteById(fileId);
    }

    @Override
    public void emptyRecycleBin(String kbId) {
        fileMapper.selectList(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId).isNotNull(KbFile::getDeletedAt))
                .forEach(f -> fileMapper.deleteById(f.getId()));
    }

    @Override
    public FileDto copy(String kbId, String fileId) {
        var s = fileMapper.selectById(fileId);
        if (s == null) throw new RuntimeException("File not found");
        var c = new KbFile();
        c.setKbId(kbId);
        c.setName(s.getName() + " (copy)");
        c.setCategory(s.getCategory());
        c.setExtension(s.getExtension());
        c.setSize(s.getSize());
        c.setObjectKey(s.getObjectKey());
        c.setStatus("pending");
        c.setProgress(0);
        c.setChunkCount(0);
        fileMapper.insert(c);
        return toDto(c);
    }

    @Override
    public Map<String, Object> getProcessingStatus(String kbId, String fileId) {
        var f = fileMapper.selectById(fileId);
        if (f == null) throw new RuntimeException("File not found");
        var r = new HashMap<String, Object>();
        r.put("fileId", f.getId());
        r.put("status", f.getStatus());
        r.put("progress", f.getProgress() != null ? f.getProgress() : 0);
        r.put("stage", f.getStage() != null ? f.getStage() : "");
        return r;
    }

    private String detectCategory(String ext) {
        if (ext == null) return "document";
        ext = ext.toLowerCase();
        if (Set.of("pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "txt", "md", "csv").contains(ext)) return "document";
        if (Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(ext)) return "image";
        if (Set.of("mp3", "wav", "m4a", "aac", "ogg").contains(ext)) return "audio";
        if (Set.of("mp4", "avi", "mov", "mkv", "flv").contains(ext)) return "video";
        return "document";
    }

    private String resolveStrategy(String kbId, String extension) {
        try {
            List<KbParseStrategy> strategies = strategyMapper.selectList(
                    new LambdaQueryWrapper<KbParseStrategy>()
                            .eq(KbParseStrategy::getKbId, kbId)
                            .eq(KbParseStrategy::getIsDefault, 1));
            if (!strategies.isEmpty()) return strategies.get(0).getId();

            strategies = strategyMapper.selectList(
                    new LambdaQueryWrapper<KbParseStrategy>().eq(KbParseStrategy::getKbId, kbId));
            if (!strategies.isEmpty()) return strategies.get(0).getId();
        } catch (Exception e) {
            log.warn("Failed to resolve strategy", e);
        }
        return null;
    }

    private FileDto toDto(KbFile f) {
        var d = new FileDto();
        d.setId(f.getId());
        d.setName(f.getName());
        d.setCategory(f.getCategory());
        d.setExtension(f.getExtension());
        d.setSize(f.getSize());
        d.setUrl("/api/kb/" + f.getKbId() + "/files/" + f.getId() + "/download");
        d.setStatus(f.getStatus());
        d.setProgress(f.getProgress());
        d.setStage(f.getStage());
        d.setChunkCount(f.getChunkCount());
        d.setFolderId(f.getFolderId());
        d.setDeletedAt(f.getDeletedAt());
        d.setCreatedAt(f.getCreatedAt());
        d.setUpdatedAt(f.getUpdatedAt());
        return d;
    }
}
