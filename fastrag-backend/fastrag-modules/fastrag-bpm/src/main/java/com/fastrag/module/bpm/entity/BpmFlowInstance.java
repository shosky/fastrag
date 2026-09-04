package com.fastrag.module.bpm.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("bpm_flow_instance") public class BpmFlowInstance {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String traceId,flowDefId,flowVersionId;
    private Integer flowVersionNo;
    private String status,inputParams,outputParams,variables,currentNodeKeys;
    private String pendingInputToken,pendingInputForm;
    private Integer inputTimeoutMs;
    private LocalDateTime inputDeadline,timeoutAt;
    private String startUserId,triggerType;
    private LocalDateTime startedAt,finishedAt;
    private Long durationMs;
    private String failureReason;
    private LocalDateTime createdAt;
}