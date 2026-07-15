package com.fastrag.module.agent.fs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件系统管理器。
 * 持有所有 VirtualFileSystem 实现，按路径前缀路由到对应的文件系统。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileSystemManager {

    private final List<VirtualFileSystem> fileSystems;

    /**
     * 根据路径查找对应的文件系统
     */
    public VirtualFileSystem findFs(String path) {
        if (path == null) return null;
        for (VirtualFileSystem fs : fileSystems) {
            if (path.startsWith(fs.getMountPoint())) {
                return fs;
            }
        }
        return null;
    }

    /**
     * 列出目录内容
     */
    public List<FileEntry> ls(String path) {
        VirtualFileSystem fs = findFs(path);
        if (fs == null) return List.of();
        return fs.ls(path);
    }

    /**
     * 读取文件
     */
    public FileContent read(String filePath) {
        VirtualFileSystem fs = findFs(filePath);
        if (fs == null) {
            return FileContent.notFound(filePath, "No filesystem mounted for: " + filePath);
        }
        return fs.read(filePath);
    }
}
