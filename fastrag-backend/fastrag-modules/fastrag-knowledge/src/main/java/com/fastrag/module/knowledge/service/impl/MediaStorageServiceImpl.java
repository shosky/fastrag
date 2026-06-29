package com.fastrag.module.knowledge.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.knowledge.entity.KbMediaStorage; import com.fastrag.module.knowledge.mapper.KbMediaStorageMapper;
import com.fastrag.module.knowledge.service.MediaStorageService;
import com.fastrag.module.knowledge.service.FileResource;
import lombok.RequiredArgsConstructor; import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@Service @RequiredArgsConstructor
public class MediaStorageServiceImpl implements MediaStorageService {
    private final KbMediaStorageMapper mapper;
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
    @Override public void delete(String id) { mapper.deleteById(id); }
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
        var media=new KbMediaStorage();
        media.setKbId(kbId); media.setMediaType(mediaType);
        media.setName(file.getOriginalFilename());
        media.setOriginalName(file.getOriginalFilename());
        media.setExtension(getExtension(file.getOriginalFilename()));
        media.setSize(file.getSize());
        media.setDescription(description);
        media.setTags(tags);
        media.setStatus("uploaded");
        mapper.insert(media);
        return media;
    }

    @Override public FileResource getDownloadResource(String id) {
        var media=mapper.selectById(id);
        if(media==null) return null;
        return new FileResource(media.getName(), new ByteArrayResource(new byte[0]));
    }

    private String getExtension(String filename) {
        if(filename==null||!filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.')+1);
    }
}
