package com.fastrag.module.bpm.dto;
import lombok.Data;
import java.util.Map;

/** 流程实例触发请求 */
@Data
public class InstanceTriggerRequest {
    private String flowDefId;
    /** 可选:指定某个已发布版本号,否则使用 currentVersionId */
    private Integer versionNo;
    private String triggerType;
    /** 启动输入参数 */
    private Map<String, Object> inputParams;
    /** 触发者(可选,默认取 SecurityContext) */
    private String startUserId;
}