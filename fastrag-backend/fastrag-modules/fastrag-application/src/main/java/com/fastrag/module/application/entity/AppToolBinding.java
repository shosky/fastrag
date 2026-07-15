package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_tool_binding") public class AppToolBinding {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,toolId,toolName,config;
    private Integer enabled; private LocalDateTime createdAt;
}
