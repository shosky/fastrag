package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_mcp_binding") public class AppMcpBinding {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,mcpServiceId,mcpServiceName;
    private Integer enabled; private LocalDateTime createdAt;
    /** 工具白名单（JSON 字符串数组文本）；NULL=注册该服务全部启用工具（存量兼容） */
    private String selectedTools;
}
