package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data; import java.util.Map;

/**
 * MCP 工具实体，对应数据库表 {@code mcp_tool}。
 *
 * <p>记录从 MCP 服务发现或手动注册的工具信息，每个工具从属于一个 {@link McpService}，
 * 工具 ID 格式为 {@code mcp__{ServerCamel}__{ToolCamel}}。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code id} - 主键（自增）</li>
 *   <li>{@code serviceId} - 所属 MCP 服务 ID</li>
 *   <li>{@code name} - 工具名称（原样来自 MCP Server）</li>
 *   <li>{@code toolId} - 格式化后的工具 ID（mcp__ 前缀格式）</li>
 *   <li>{@code description} - 工具描述</li>
 *   <li>{@code params} - 工具参数 JSON Schema</li>
 *   <li>{@code enabled} - 是否启用</li>
 * </ul>
 *
 * @see McpService
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
