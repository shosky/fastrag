package com.fastrag.module.knowledge.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ParseStrategyRequest {
    private String name;
    private String description;
    private String parseMethod;
    private String llmModel;
    private String vlmModel;
    private List<String> extensions;
    private Map<String, Object> advanced;
    private Boolean enableGraphBuild;
}
