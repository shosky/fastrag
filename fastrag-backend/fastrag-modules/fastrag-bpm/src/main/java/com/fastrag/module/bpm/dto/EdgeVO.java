package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EdgeVO {
    private String id, versionId, sourceNodeKey, targetNodeKey, edgeKind;
    private String conditionExpr, conditionParams, label;
    private Integer priority;
    private LocalDateTime createdAt;
}