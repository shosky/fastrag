package com.fastrag.module.bpm.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("bpm_flow_node") public class BpmFlowNode {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String versionId,nodeKey,nodeType,name;
    private Integer positionX,positionY;
    private String config;
    private Integer timeoutMs,retryCount,retryIntervalMs;
    private String onFailure,failureBranchNodeKey;
    private Boolean enabled;
    private LocalDateTime createdAt;
}