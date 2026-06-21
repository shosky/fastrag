package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("mcp_call_log") public class McpCallLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String serviceId,caller,tool,status;
    private Integer duration;
    private LocalDateTime timestamp;
}
