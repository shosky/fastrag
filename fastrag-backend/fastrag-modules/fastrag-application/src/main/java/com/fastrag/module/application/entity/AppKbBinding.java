package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_kb_binding") public class AppKbBinding {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,kbId,filterTags;
    private Integer priority,enabled; private LocalDateTime createdAt;
}
