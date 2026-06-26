package com.fastrag.module.knowledge.service;

import com.fastrag.module.knowledge.model.FileDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface FileService {
    List<FileDto> list(String kbId);
    List<FileDto> listDeleted(String kbId);
    FileDto upload(String kbId, MultipartFile file);
    void process(String kbId, String fileId);
    FileDto update(String kbId, String fileId, Map<String, Object> patch);
    void delete(String kbId, String fileId);
    void restore(String kbId, String fileId);
    void permanentDelete(String kbId, String fileId);
    void emptyRecycleBin(String kbId);
    FileDto copy(String kbId, String fileId);
    Map<String, Object> getProcessingStatus(String kbId, String fileId);
}
