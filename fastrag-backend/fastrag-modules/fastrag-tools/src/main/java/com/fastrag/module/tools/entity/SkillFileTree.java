package com.fastrag.module.tools.entity;

import lombok.Data;

import java.util.List;

@Data
public class SkillFileTree {
    private String name;
    private boolean isDir;
    private long size;
    private long lastModified;
    private List<SkillFileTree> children;
}
