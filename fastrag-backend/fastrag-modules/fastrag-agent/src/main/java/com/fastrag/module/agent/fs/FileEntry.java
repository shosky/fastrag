package com.fastrag.module.agent.fs;

import lombok.AllArgsConstructor;
import lombok.Data;

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
