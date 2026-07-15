package com.fastrag.module.agent.fs;

import lombok.Data;

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
