package com.fastrag.module.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AgentSerializeVO {

    // --- Agent fields ---
    private String id;
    private String backendId;
    private String name;
    private String description;
    private String slug;
    private String icon;
    private Map<String, Object> configJson;
    private Map<String, Object> shareConfig;
    private String accessLevel;
    private boolean isDefault;
    private boolean isSubagent;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Extra fields ---
    private boolean canManage;
    private boolean isBuiltin;
    private boolean isSubagentFlag;
    private List<String> capabilities;
    private Map<String, Object> metadata;
    private Map<String, Object> configurableItems;
    private boolean permissionLocked;
}
