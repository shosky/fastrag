package com.fastrag.module.knowledge.service;
import com.fastrag.common.response.PageResult; import com.fastrag.module.knowledge.entity.KbMediaStorage; import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
public interface MediaStorageService {
    PageResult<KbMediaStorage> page(String kbId,String mediaType,String keyword,String status,int page,int pageSize);
    List<KbMediaStorage> list(String kbId,String mediaType,String keyword);
    KbMediaStorage get(String id);
    KbMediaStorage create(KbMediaStorage media);
    KbMediaStorage update(String id,KbMediaStorage media);
    void delete(String id);
    Map<String,Object> batchImport(String kbId,String mediaType,List<KbMediaStorage> items);
    // M9 文件上传/下载
    KbMediaStorage uploadFile(String kbId, String mediaType, MultipartFile file, String description, String tags);
    FileResource getDownloadResource(String id);
}
