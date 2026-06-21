package com.fastrag.module.tools.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("mcp_service") public class McpService {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String name,mcpUrl,authType,authValue,status;
    private Integer enabled;
    private LocalDateTime lastUsed,createdAt;
}
