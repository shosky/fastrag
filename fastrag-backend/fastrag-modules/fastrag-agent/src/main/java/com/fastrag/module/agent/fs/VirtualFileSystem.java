package com.fastrag.module.agent.fs;

import java.util.List;

/**
 * 虚拟文件系统接口。
 * 不同文件系统通过 mount point 注册，FileSystemManager 按路径前缀路由。
 */
public interface VirtualFileSystem {

    /** 挂载点，如 "/home/gem/skills/" */
    String getMountPoint();

    /** 列出目录内容 */
    List<FileEntry> ls(String path);

    /** 读取文件 */
    FileContent read(String filePath);

    /** 是否允许写入 */
    boolean isAllowedWrite(String filePath);

    /** 搜索文件 */
    List<FileEntry> grep(String pattern, String path);
}
