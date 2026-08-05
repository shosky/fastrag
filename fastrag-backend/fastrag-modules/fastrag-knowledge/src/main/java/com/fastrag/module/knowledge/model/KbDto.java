package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KbDto {
    private String id;
    private String name;
    private String description;
    private String category;
    private String embeddingModel;
    private String creator;
    private String orgId;
    private String type;
    private String parseMode;
    private String splitMode;
    private String permission;
    private Integer graphAutoBuild;
    private List<String> tags;
    private Integer dimension;
    private LocalDateTime createdAt;
    private Long usedSize;
    private Long totalSize;
    private Object fileTypeConfig;
    private Object retrievalConfig;
}
