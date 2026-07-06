package com.fastrag.module.agent.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AgentConfigDTO {

    private Map<String, Object> context;

    private Map<String, Object> shareConfig;
}
