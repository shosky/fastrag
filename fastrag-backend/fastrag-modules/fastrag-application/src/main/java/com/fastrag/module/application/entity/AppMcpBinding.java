package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_mcp_binding") public class AppMcpBinding {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,mcpServiceId,mcpServiceName;
    private Integer enabled; private LocalDateTime createdAt;
}
