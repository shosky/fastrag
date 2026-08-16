package com.fastrag.module.operation.entity;

/**
 * 聊天会话实体类。
 *
 * <p>对应数据库表 chat_session，记录用户与AI系统的问答交互会话信息，
 * 包括用户提问内容、AI回答、检索到的知识块、使用的模型、响应时长和Token消耗等。
 *
 * <p>主要字段说明：
 * <ul>
 *     <li>id - 会话唯一标识（ASSIGN_ID 雪花算法）</li>
 *     <li>userId - 用户ID</li>
 *     <li>kbId - 关联的知识库ID</li>
 *     <li>appId - 关联的应用ID</li>
 *     <li>query - 用户提问内容</li>
 *     <li>answer - AI回答内容</li>
 *     <li>retrievedChunks - RAG检索命中的知识块</li>
 *     <li>model - 使用的AI模型</li>
 *     <li>duration - 响应时长（毫秒）</li>
 *     <li>tokens - Token消耗数</li>
 *     <li>createdAt - 会话创建时间</li>
 * </ul>
 */
import com.baomidou.mybatisplus.annotation.*; import lombok.Data; import java.time.LocalDateTime;
@Data @TableName("chat_session") public class ChatSession {
    @TableId(type=IdType.ASSIGN_ID) private String id;
    private String userId,kbId,appId,query,answer,retrievedChunks,model;
    private Long duration;
    private Integer tokens;
    private LocalDateTime createdAt;
}
