package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data; import java.util.Map;

/**
 * MCP 工具实体 - 对应 Yuxi 的 McpToolItem.
 * <p>
 * 每个工具从属于一个 MCP 服务，工具ID格式: mcp__{ServerCamel}__{ToolCamel}
 */
@Data
@TableName(value = "mcp_tool", autoResultMap = true)
public class McpTool {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 MCP 服务 ID */
    private String serviceId;

    /** 工具名称 (原样来自 MCP Server) */
    private String name;

    /** 格式化后的工具ID: mcp__{ServerCamel}__{ToolCamel} */
    private String toolId;

    /** 工具描述 */
    private String description;

    /** 工具参数 JSON Schema */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> params;

    /** 是否启用 */
    private Integer enabled;
}
