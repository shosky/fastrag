package com.fastrag.module.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class AgentCreateDTO {

    @NotBlank(message = "后端ID不能为空")
    private String backendId;

    @NotBlank(message = "名称不能为空")
    private String name;

    private String description;

    private String slug;

    private String icon;

    private Map<String, Object> configJson;

    private Map<String, Object> shareConfig;
}
