package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data; import java.time.LocalDateTime; import java.util.List; import java.util.Map;

/**
 * MCP 服务实体 - 对应 Yuxi 的 McpServerInfo 模型.
 * <p>
 * transport 含义:
 * <ul>
 *   <li>stdio - 本地进程通信 (command + args)</li>
 *   <li>sse   - Server-Sent Events 远程连接 (url)</li>
 * </ul>
 */
@Data
@TableName(value = "mcp_service", autoResultMap = true)
public class McpService {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 唯一标识符，如 "filesystem-mcp" */
    private String slug;

    /** 显示名称 */
    private String name;

    /** 传输协议: stdio / sse */
    private String transport;

    /** MCP Server URL (sse 模式) */
    private String mcpUrl;

    /** stdio 模式的启动命令 */
    private String command;

    /** stdio 模式的启动参数 (JSON array) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> args;

    /** 环境变量 (JSON map) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> env;

    /** 认证类型: none / bearer / basic */
    private String authType;

    /** 认证值 */
    private String authValue;

    /** 连接状态: online / offline / error */
    private String status;

    /** 是否启用 */
    private Integer enabled;

    /** 是否为内置 MCP 服务 */
    private Integer isBuiltin;

    /** 内置服务的配置哈希，用于同步检测 */
    private String configHash;

    /** MCP 服务元数据 (JSON) */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /** 最后使用时间 */
    private LocalDateTime lastUsed;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
