package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ParseStrategyDto {
    private String id;
    private String name;
    private String description;
    private String parseMethod;
    private String llmModel;
    private String vlmModel;
    private List<String> extensions;
    private boolean isDefault;
    private Map<String, Object> advanced;
    private Integer enableGraphBuild;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
