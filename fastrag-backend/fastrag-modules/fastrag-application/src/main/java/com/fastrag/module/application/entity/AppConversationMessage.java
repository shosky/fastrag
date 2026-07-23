package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_conversation_message") public class AppConversationMessage {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String conversationId,role,content;
    private String thinkingContent;
    private String toolCalls;
    private Integer tokens,latencyMs;
    private String feedback;
    private LocalDateTime deletedAt, createdAt;
}
