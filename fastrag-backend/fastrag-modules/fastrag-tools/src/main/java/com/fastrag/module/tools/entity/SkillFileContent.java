package com.fastrag.module.tools.entity;

import lombok.Data;

@Data
public class SkillFileContent {
    private String path;
    private String content;
    private long size;
    private long lastModified;
    private boolean exists;
    private String error;
}
