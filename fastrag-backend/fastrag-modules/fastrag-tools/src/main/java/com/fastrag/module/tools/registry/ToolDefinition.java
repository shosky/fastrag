package com.fastrag.module.tools.registry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 运行时工具定义，从 DB 实体转换而来。
 * 统一了 HTTP Tool、MCP Tool、Knowledge Tool、DB Tool 的表示。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    private String toolId;                // 工具唯一标识
    private String name;                  // 工具显示名（LLM 看到的名称）
    private String description;           // 工具描述
    private String type;                  // "http" | "mcp" | "knowledge" | "database" | "builtin"
    private Map<String, Object> inputSchema;    // 入参 JSON Schema（直接传给 LLM）
    private Map<String, Object> outputSchema;   // 出参 JSON Schema（描述工具返回的数据结构）
    private String outputMapping;         // 输出映射规则（JSONPath，如 $.data.temperature）
    private Map<String, Object> config;          // 工具配置（HTTP config 等）
}
