package com.fastrag.module.application.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("app_conversation") public class AppConversation {
    @TableId(type=IdType.ASSIGN_ID) private String id; private String appId,sessionId,userId,userName,title,firstQuestion,answerSummary;
    private Integer messageCount,tokenCount,rating; private LocalDateTime createdAt,updatedAt;
}
