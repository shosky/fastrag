package com.fastrag.module.agent.fs;

import lombok.Data;

/**
 * 虚拟文件内容对象。
 *
 * <p>封装从虚拟文件系统读取的文件内容和元信息，包含文件路径、内容、是否存在、
 * 错误信息和文件大小。提供静态工厂方法：ok()表示文件存在且读取成功，
 * notFound()表示文件不存在或读取失败。被 FilesystemMiddleware 使用。</p>
 */
@Data
public class FileContent {
    private String path;
    private String content;
    private boolean exists;
    private String error;
    private long size;

    public static FileContent ok(String path, String content) {
        FileContent fc = new FileContent();
        fc.path = path;
        fc.content = content;
        fc.exists = true;
        fc.size = content != null ? content.length() : 0;
        return fc;
    }

    public static FileContent notFound(String path, String error) {
        FileContent fc = new FileContent();
        fc.path = path;
        fc.exists = false;
        fc.error = error;
        return fc;
    }
}
