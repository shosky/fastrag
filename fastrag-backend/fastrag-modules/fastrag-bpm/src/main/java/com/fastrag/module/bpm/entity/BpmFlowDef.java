package com.fastrag.module.bpm.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("bpm_flow_def") public class BpmFlowDef {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,description,category,ownerId,visibility,currentVersionId;
    private Integer timeoutMs;
    private String triggerType;
    private Boolean logSnapshotEnabled;
    private LocalDateTime createdAt,updatedAt;
}