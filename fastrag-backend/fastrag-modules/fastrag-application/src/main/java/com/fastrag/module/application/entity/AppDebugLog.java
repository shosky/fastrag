package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_debug_log") public class AppDebugLog {
    @TableId(type=IdType.AUTO) private Long id; private String appId,sessionId,level,module,message,context; private LocalDateTime createdAt;
}
