package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class InstanceVO {
    private String id, traceId, flowDefId, flowVersionId;
    private Integer flowVersionNo;
    private String status, startUserId, triggerType, failureReason;
    private String inputParams, outputParams, variables, currentNodeKeys;
    private String pendingInputToken, pendingInputForm;
    private Integer inputTimeoutMs;
    private LocalDateTime inputDeadline, timeoutAt, startedAt, finishedAt, createdAt;
    private Long durationMs;
    /** 状态子态(可选) */
    private String subStatus;
    /** 转好的输入/输出 Map,供前端直接展示 */
    private Map<String, Object> inputs;
    private Map<String, Object> outputs;
}