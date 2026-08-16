package com.fastrag.module.agent.fs;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 虚拟文件条目对象。
 *
 * <p>表示虚拟文件系统中的一个文件或目录条目，包含名称、是否为目录、
 * 大小和最后修改时间。用于文件列表展示和文件系统操作的返回结果。
 * 被 FilesystemMiddleware 和相关虚拟文件系统服务使用。</p>
 */
@Data
@AllArgsConstructor
public class FileEntry {
    private String name;
    private boolean isDir;
    private long size;
    private long lastModified;

    public FileEntry(String name, boolean isDir) {
        this.name = name;
        this.isDir = isDir;
        this.size = 0;
        this.lastModified = System.currentTimeMillis();
    }
}
