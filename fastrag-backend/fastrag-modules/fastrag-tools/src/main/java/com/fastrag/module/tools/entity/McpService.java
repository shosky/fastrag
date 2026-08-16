package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data; import java.time.LocalDateTime; import java.util.List; import java.util.Map;

/**
 * MCP（Model Context Protocol）服务实体，对应数据库表 {@code mcp_service}。
 *
 * <p>管理 MCP 服务的注册信息与连接配置，支持 stdio（本地进程通信）和 SSE（远程连接）两种传输协议。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code slug} - 唯一标识符（如 "filesystem-mcp"）</li>
 *   <li>{@code name} - 显示名称</li>
 *   <li>{@code transport} - 传输协议：{@code stdio}（command + args 启动子进程）/ {@code sse}（mcpUrl 远程连接）</li>
 *   <li>{@code command / args} - stdio 模式下的启动命令和参数</li>
 *   <li>{@code mcpUrl} - SSE 模式下的服务 URL</li>
 *   <li>{@code env} - 环境变量（JSON map）</li>
 *   <li>{@code authType / authValue} - 认证方式（none/bearer/basic）</li>
 *   <li>{@code status} - 连接状态（online/offline/error）</li>
 *   <li>{@code isBuiltin} - 是否为内置 MCP 服务（由 {@link McpBuiltinSeeder} 种子初始化）</li>
 *   <li>{@code orgId} - 归属组织</li>
 * </ul>
 *
 * @see McpTool
 * @see McpCallLog
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

    /** 描述 */
    @TableField(exist = false)
    private String description;

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
    private String creator; // 创建者 userId（system=系统预置）
    private String orgId; // 归属组织

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
