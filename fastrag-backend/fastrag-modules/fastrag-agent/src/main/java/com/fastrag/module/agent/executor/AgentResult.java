package com.fastrag.module.agent.executor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class AgentResult {
    private boolean success;
    private String answer;
    private String error;
    private List<ToolCallRecord> toolCallRecords = new ArrayList<>();
    private int totalDurationMs;

    public static AgentResult success(String answer) {
        AgentResult r = new AgentResult();
        r.setSuccess(true);
        r.setAnswer(answer);
        return r;
    }

    public static AgentResult error(String error) {
        AgentResult r = new AgentResult();
        r.setSuccess(false);
        r.setError(error);
        return r;
    }

    @Data
    public static class ToolCallRecord {
        private String toolName;
        private String toolId;
        private Map<String, Object> arguments;
        private boolean success;
        private String output;
        private String error;
        private int durationMs;
    }
}
