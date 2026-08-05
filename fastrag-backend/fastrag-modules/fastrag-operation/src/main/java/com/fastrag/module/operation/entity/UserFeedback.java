package com.fastrag.module.operation.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("user_feedback") public class UserFeedback {
    @TableId(type=IdType.AUTO) private Long id;
    private String sessionId,userId,kbId,appId,query,answer,feedback,comment,category,status,reply,processedBy;
    private String orgId; // 归属组织（提交者组织，创建时写入）
    private Integer score;
    private LocalDateTime createdAt,processedAt;
}
