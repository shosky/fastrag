package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_conversation") public class AppConversation {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    /** 挂载应用；NULL = 知识库直聊会话（new-conversation 放宽） */
    private String appId,sessionId,userId,userName,title,firstQuestion,answerSummary;
    /** 会话请求态 JSON（ConversationRequestState 形状，new-conversation） */
    private String requestState;
    /** 置顶标记（new-conversation） */
    private Integer isPinned;
    private Integer messageCount,tokenCount,rating; private LocalDateTime createdAt,updatedAt;
}
