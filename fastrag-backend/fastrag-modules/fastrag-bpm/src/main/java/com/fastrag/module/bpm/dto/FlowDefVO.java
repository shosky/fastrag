package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** 流程定义读视图 */
@Data
public class FlowDefVO {
    private String id, name, description, category, ownerId, visibility;
    private String currentVersionId;
    private Integer currentVersionNo;
    private String currentVersionStatus;
    private Integer timeoutMs, nodeCount, edgeCount;
    private String triggerType;
    private Boolean logSnapshotEnabled;
    private LocalDateTime createdAt, updatedAt;
    /** 该流程最近的版本列表(轻量) */
    private List<FlowVersionVO> recentVersions;
}