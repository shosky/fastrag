package com.fastrag.module.tools.executor;

import com.fastrag.module.tools.registry.ToolDefinition;

import java.util.Map;

public interface ToolExecutor {
    String getType();
    ToolResult execute(ToolDefinition tool, Map<String, Object> arguments, ToolContext ctx);
}
