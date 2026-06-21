package com.fastrag.module.operation.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("chat_session") public class ChatSession {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String userId,kbId,appId,query,answer,retrievedChunks,model;
    private Long duration;
    private Integer tokens;
    private LocalDateTime createdAt;
}
