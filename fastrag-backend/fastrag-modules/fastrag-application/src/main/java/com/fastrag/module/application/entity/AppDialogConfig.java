package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_dialog_config") public class AppDialogConfig {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,backgroundColor,backgroundImage,bubbleStyle,updatedBy;
    private Integer showAvatar,showFeedback,showSuggestions; private LocalDateTime createdAt,updatedAt;
}
