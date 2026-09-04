package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FlowTemplateVO {
    private String id, name, category, description, canvasData, thumbnailUrl, createdBy;
    private Boolean isBuiltin;
    private LocalDateTime createdAt;
}