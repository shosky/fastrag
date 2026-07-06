package com.fastrag.module.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AgentRunCreateDTO {

    @NotBlank(message = "线程ID不能为空")
    private String threadId;

    @NotBlank(message = "Agent ID不能为空")
    private String agentId;

    private String query;

    private String runType;

    private String modelOverride;

    private String requestId;
}
