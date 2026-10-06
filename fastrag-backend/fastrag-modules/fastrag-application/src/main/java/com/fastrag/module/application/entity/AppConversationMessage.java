package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_conversation_message") public class AppConversationMessage {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String conversationId,role,content;
    private String thinkingContent;
    private String toolCalls;
    /** 消息引用 JSON 数组（MessageReference 投影；列名 references 为 MySQL 保留字，new-conversation） */
    @TableField("`references`")
    private String references;
    /** 引擎迭代轮数（new-conversation，仅 assistant 完成消息） */
    private Integer iterations;
    private Integer tokens,latencyMs;
    private String feedback;
    private LocalDateTime deletedAt, createdAt;
}
