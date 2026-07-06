package com.fastrag.module.agent.state;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SubAgentRunState {

    private String id;

    private String subagentType;

    private String subagentName;

    private String childThreadId;

    private String description;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private String resultPreview;

    private String error;

    private List<String> artifacts;
}
