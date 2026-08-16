package com.fastrag.module.tools.entity;

/**
 * MCP 工具调用日志实体，对应数据库表 {@code mcp_call_log}。
 *
 * <p>记录每次 MCP 工具的调用情况，包括调用者、工具名称、执行状态和耗时，
 * 用于审计追踪和问题排查。</p>
 *
 * <h3>核心字段：</h3>
 * <ul>
 *   <li>{@code id} - 主键（自增）</li>
 *   <li>{@code serviceId} - 所属 MCP 服务 ID</li>
 *   <li>{@code caller} - 调用者标识</li>
 *   <li>{@code tool} - 工具名称</li>
 *   <li>{@code status} - 调用状态（success/error）</li>
 *   <li>{@code duration} - 调用耗时（毫秒）</li>
 *   <li>{@code timestamp} - 调用时间</li>
 * </ul>
 *
 * @see McpService
 * @see McpTool
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("mcp_call_log") public class McpCallLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String serviceId,caller,tool,status;
    private Integer duration;
    private LocalDateTime timestamp;
}
