package com.fastrag.module.knowledge.service;

import com.fastrag.module.knowledge.model.FolderNodeDto;

import java.util.List;

public interface FolderService {
    List<FolderNodeDto> list(String kbId);
    FolderNodeDto create(String kbId, String name, String parentId);
    String getName(String kbId, String folderId);
    void rename(String kbId, String folderId, String newName);
    void delete(String kbId, String folderId);
}
