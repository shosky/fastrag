package com.fastrag.module.knowledge.service;

import com.fastrag.module.knowledge.model.FolderNodeDto;

import java.util.List;

/**
 * 知识库文件夹管理服务接口。
 *
 * <p>定义知识库文件夹的树形结构管理操作，包括文件夹列表查询、创建、
 * 名称查询、重命名和删除。文件夹用于对知识库内文件进行分类组织。</p>
 */
public interface FolderService {
    List<FolderNodeDto> list(String kbId);
    FolderNodeDto create(String kbId, String name, String parentId);
    String getName(String kbId, String folderId);
    void rename(String kbId, String folderId, String newName);
    void delete(String kbId, String folderId);
}
