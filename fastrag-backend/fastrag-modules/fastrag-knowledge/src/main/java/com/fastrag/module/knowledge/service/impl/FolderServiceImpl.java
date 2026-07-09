package com.fastrag.module.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.knowledge.entity.KbFile;
import com.fastrag.module.knowledge.entity.KbFolder;
import com.fastrag.module.knowledge.mapper.KbFileMapper;
import com.fastrag.module.knowledge.mapper.KbFolderMapper;
import com.fastrag.module.knowledge.model.FolderNodeDto;
import com.fastrag.module.knowledge.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {
    private final KbFolderMapper mapper;
    private final KbFileMapper fileMapper;

    @Override
    public List<FolderNodeDto> list(String kbId) {
        return buildTree(mapper.selectList(new LambdaQueryWrapper<KbFolder>()
                .eq(KbFolder::getKbId, kbId).orderByAsc(KbFolder::getSort)), null);
    }

    @Override
    public FolderNodeDto create(String kbId, String name, String parentId) {
        var f = new KbFolder();
        f.setKbId(kbId);
        f.setName(name);
        f.setParentId(parentId);
        f.setSort(0);
        mapper.insert(f);
        var d = new FolderNodeDto();
        d.setId(f.getId());
        d.setLabel(f.getName());
        return d;
    }

    @Override
    public String getName(String kbId, String folderId) {
        var f = mapper.selectOne(new LambdaQueryWrapper<KbFolder>()
                .eq(KbFolder::getKbId, kbId).eq(KbFolder::getId, folderId));
        return f != null ? f.getName() : "";
    }

    @Override
    public void rename(String kbId, String folderId, String newName) {
        var f = mapper.selectOne(new LambdaQueryWrapper<KbFolder>()
                .eq(KbFolder::getKbId, kbId).eq(KbFolder::getId, folderId));
        if (f == null) {
            throw new RuntimeException("文件夹不存在");
        }
        f.setName(newName);
        mapper.updateById(f);
    }

    @Override
    @Transactional
    public void delete(String kbId, String folderId) {
        // 检查文件夹是否存在
        var f = mapper.selectOne(new LambdaQueryWrapper<KbFolder>()
                .eq(KbFolder::getKbId, kbId).eq(KbFolder::getId, folderId));
        if (f == null) {
            throw new RuntimeException("文件夹不存在");
        }

        // 检查是否有子文件夹
        long subFolderCount = mapper.selectCount(new LambdaQueryWrapper<KbFolder>()
                .eq(KbFolder::getKbId, kbId).eq(KbFolder::getParentId, folderId));
        if (subFolderCount > 0) {
            throw new RuntimeException("文件夹下存在子文件夹，请先删除子文件夹");
        }

        // 检查文件夹内是否有文件
        long fileCount = fileMapper.selectCount(new LambdaQueryWrapper<KbFile>()
                .eq(KbFile::getKbId, kbId).eq(KbFile::getFolderId, folderId));
        if (fileCount > 0) {
            throw new RuntimeException("文件夹下存在文件，请先移出或删除文件");
        }

        mapper.deleteById(folderId);
    }

    private List<FolderNodeDto> buildTree(List<KbFolder> all, String pid) {
        return all.stream()
                .filter(f -> pid == null ? f.getParentId() == null : pid.equals(f.getParentId()))
                .map(f -> {
                    var n = new FolderNodeDto();
                    n.setId(f.getId());
                    n.setLabel(f.getName());
                    n.setChildren(buildTree(all, f.getId()));
                    return n;
                }).collect(Collectors.toList());
    }
}
