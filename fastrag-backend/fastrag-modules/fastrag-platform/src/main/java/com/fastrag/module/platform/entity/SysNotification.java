package com.fastrag.module.platform.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("sys_notification") public class SysNotification {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String title,content,notifyType,sourceType,sourceId,targetUser,status;
    private LocalDateTime createdAt;
}
