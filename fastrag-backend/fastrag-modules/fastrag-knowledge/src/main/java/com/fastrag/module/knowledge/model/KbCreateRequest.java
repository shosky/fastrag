package com.fastrag.module.knowledge.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class KbCreateRequest {
    @NotBlank
    private String name;
    private String category;
    private String description;
    private String permission;
    private String embeddingModel;
    private String parseMode;
    private String splitMode;
    private Boolean graphAutoBuild;
    private List<String> tags;
    private Object fileTypeConfig;
    private Object retrievalConfig;
}
