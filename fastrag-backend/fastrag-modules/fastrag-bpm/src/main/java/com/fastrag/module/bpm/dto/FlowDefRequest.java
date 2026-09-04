package com.fastrag.module.bpm.dto;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 流程定义创建/更新请求 */
@Data
public class FlowDefRequest {
    @NotBlank(message = "流程名称不能为空")
    @Size(max = 128, message = "流程名称不能超过128字符")
    private String name;
    @Size(max = 4000, message = "描述不能超过4000字符")
    private String description;
    @Size(max = 64)
    private String category;
    /** private_flow/team/public_flow,默认 private_flow */
    private String visibility;
    /** 整体超时(毫秒),默认 24h,0 表示不限 */
    private Integer timeoutMs;
    /** manual/api/scheduled/event,默认 manual */
    private String triggerType;
    private Boolean logSnapshotEnabled;
}