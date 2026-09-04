package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NodeVO {
    private String id, versionId, nodeKey, nodeType, name;
    private Integer positionX, positionY;
    private String config;
    private Integer timeoutMs, retryCount, retryIntervalMs;
    private String onFailure, failureBranchNodeKey;
    private Boolean enabled;
    private LocalDateTime createdAt;
}