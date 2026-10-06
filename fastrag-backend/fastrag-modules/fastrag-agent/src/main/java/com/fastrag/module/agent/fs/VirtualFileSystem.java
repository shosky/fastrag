package com.fastrag.module.agent.fs;

import java.util.List;

/**
 * 虚拟文件系统接口。
 * M3-6 已删除 FileSystemManager 路由器与 SkillReadonlyBackend 实现；
 * 技能正文统一走 DB content + 沙箱物化（SandboxExecutionService.syncSkills）。
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
