package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.knowledge.entity.KbMediaStorage;
import com.fastrag.module.knowledge.mapper.KbMediaStorageMapper;
import com.fastrag.module.knowledge.service.MediaStorageService;
import com.fastrag.module.knowledge.service.FileResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaStorageServiceImpl implements MediaStorageService {
    private final KbMediaStorageMapper mapper;

    /** 媒体文件本地存储根目录（application.yml: storage.local.path） */
    @Value("${storage.local.path:./uploads}")
    private String storagePath;

    @Override public PageResult<KbMediaStorage> page(String kbId,String mediaType,String keyword,String status,int page,int pageSize) {
        var w=buildWrapper(kbId,mediaType,keyword,status);
        var pg=mapper.selectPage(new Page<>(page,pageSize),w);
        return PageResult.of(pg.getRecords(),pg.getTotal(),page,pageSize);
    }
    @Override public List<KbMediaStorage> list(String kbId,String mediaType,String keyword) {
        return mapper.selectList(buildWrapper(kbId,mediaType,keyword,null));
    }
    private LambdaQueryWrapper<KbMediaStorage> buildWrapper(String kbId,String mediaType,String keyword,String status) {
        var w=new LambdaQueryWrapper<KbMediaStorage>();
        if(kbId!=null&&!kbId.isEmpty()) w.eq(KbMediaStorage::getKbId,kbId);
        if(mediaType!=null&&!mediaType.isEmpty()) w.eq(KbMediaStorage::getMediaType,mediaType);
        if(keyword!=null&&!keyword.isEmpty()) w.like(KbMediaStorage::getName,keyword);
        if(status!=null&&!status.isEmpty()) w.eq(KbMediaStorage::getStatus,status);
        return w.orderByDesc(KbMediaStorage::getCreatedAt);
    }
    @Override public KbMediaStorage get(String id) { return mapper.selectById(id); }
    @Override public KbMediaStorage create(KbMediaStorage media) {
        if(media.getStatus()==null) media.setStatus("uploaded");
        mapper.insert(media); return media;
    }
    @Override public KbMediaStorage update(String id,KbMediaStorage media) {
        media.setId(id); mapper.updateById(media); return mapper.selectById(id);
    }
    @Override public void delete(String id) {
        var media = mapper.selectById(id);
        if (media != null && media.getObjectKey() != null) {
            try {
                Files.deleteIfExists(Paths.get(storagePath, media.getObjectKey()));
            } catch (IOException e) {
                log.warn("删除磁盘文件失败: {}", e.getMessage());
            }
        }
        mapper.deleteById(id);
    }
    @Override public Map<String,Object> batchImport(String kbId,String mediaType,List<KbMediaStorage> items) {
        List<KbMediaStorage> imported=new ArrayList<>();
        for(var item:items){
            item.setKbId(kbId); item.setMediaType(mediaType);
            if(item.getStatus()==null) item.setStatus("uploaded");
            mapper.insert(item); imported.add(item);
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("imported",imported.size());
        result.put("resources",imported);
        return result;
    }

    @Override public KbMediaStorage uploadFile(String kbId, String mediaType, MultipartFile file, String description, String tags) {
        String ext = getExtension(file.getOriginalFilename());
        String objectKey;
        try {
            objectKey = writeBytes(kbId, mediaType, ext, file.getBytes());
        } catch (IOException e) {
            log.error("上传文件写入磁盘失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
        var media=new KbMediaStorage();
        media.setKbId(kbId); media.setMediaType(mediaType);
        media.setName(file.getOriginalFilename());
        media.setOriginalName(file.getOriginalFilename());
        media.setExtension(ext);
        media.setSize(file.getSize());
        media.setObjectKey(objectKey);
        media.setDescription(description);
        media.setTags(tags);
        media.setStatus("uploaded");
        mapper.insert(media);
        return media;
    }

    @Override public KbMediaStorage saveBytes(String kbId, String mediaType, byte[] bytes, String extension, String source, String description) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("saveBytes 字节流不能为空");
        }
        String ext = extension == null || extension.isBlank() ? "bin" : extension;
        String objectKey;
        try {
            objectKey = writeBytes(kbId, mediaType, ext, bytes);
        } catch (IOException e) {
            log.error("AI 媒体写入磁盘失败", e);
            throw new RuntimeException("媒体保存失败: " + e.getMessage(), e);
        }
        var media = new KbMediaStorage();
        media.setKbId(kbId);
        media.setMediaType(mediaType);
        media.setName(objectKey.substring(objectKey.lastIndexOf('/') + 1));
        media.setOriginalName(media.getName());
        media.setExtension(ext);
        media.setSize((long) bytes.length);
        media.setObjectKey(objectKey);
        media.setDescription(description);
        media.setStatus(source != null && source.startsWith("ai_") ? "ai_generated" : "uploaded");
        mapper.insert(media);
        return media;
    }

    /**
     * 把字节流写入本地存储。路径策略：
     * {storagePath}/{kbId}/{mediaType}/{yyyyMM}/{uuid}.{ext}
     */
    private String writeBytes(String kbId, String mediaType, String ext, byte[] bytes) throws IOException {
        String yyyyMM = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String relativeKey = String.format("%s/%s/%s/%s",
                safe(kbId), safe(mediaType), yyyyMM, filename);
        Path target = Paths.get(storagePath, relativeKey);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return relativeKey;
    }

    @Override public FileResource getDownloadResource(String id) {
        var media=mapper.selectById(id);
        if(media==null) return null;
        if (media.getObjectKey() == null || media.getObjectKey().isBlank()) {
            // 兼容历史数据：没有真实文件
            return new FileResource(media.getOriginalName(), new ByteArrayResource(new byte[0]));
        }
        try {
            Path file = Paths.get(storagePath, media.getObjectKey());
            byte[] bytes = Files.readAllBytes(file);
            return new FileResource(media.getOriginalName() != null ? media.getOriginalName() : file.getFileName().toString(),
                    new ByteArrayResource(bytes));
        } catch (IOException e) {
            log.error("下载文件读取失败: {}", e.getMessage());
            throw new RuntimeException("下载文件不存在或已损坏: " + e.getMessage(), e);
        }
    }

    private String getExtension(String filename) {
        if(filename==null||!filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.')+1);
    }

    private String safe(String s) {
        return s == null ? "default" : s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
