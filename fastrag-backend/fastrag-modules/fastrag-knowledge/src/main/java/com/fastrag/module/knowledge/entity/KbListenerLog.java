package com.fastrag.module.knowledge.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("kb_listener_log") public class KbListenerLog {
    @TableId(type=IdType.AUTO) private Long id;
    private String listenerId,eventType,message,status;
    @TableField(exist = false) private String level;
    private LocalDateTime createdAt;
}
