package com.fastrag.module.agent.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AgentUpdateDTO {

    private String name;

    private String description;

    private String icon;

    private Map<String, Object> configJson;

    private Map<String, Object> shareConfig;
}
