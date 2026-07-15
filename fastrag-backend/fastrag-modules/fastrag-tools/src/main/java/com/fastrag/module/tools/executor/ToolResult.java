package com.fastrag.module.tools.executor;

import lombok.Data;

import java.util.Map;

@Data
public class ToolResult {
    private boolean success;
    private String output;
    private Map<String, Object> structured;
    private int durationMs;
    private String error;

    public static ToolResult success(String output, int durationMs) {
        ToolResult r = new ToolResult();
        r.setSuccess(true);
        r.setOutput(output);
        r.setDurationMs(durationMs);
        return r;
    }

    public static ToolResult error(String error, int durationMs) {
        ToolResult r = new ToolResult();
        r.setSuccess(false);
        r.setError(error);
        r.setDurationMs(durationMs);
        return r;
    }
}
