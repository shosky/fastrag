package com.fastrag.module.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用会话摘要DTO，用于在会话列表接口中返回会话的基本信息。
 *
 * <p>使用场景：{@code GET /api/apps/{appId}/chat/sessions} 接口返回值，
 * 将会话的关键摘要信息（标题、首条问题、回答摘要、消息数、Token消耗数等）
 * 组装后返回给前端展示，避免返回完整的消息历史数据。
 */
@Data
public class AppSessionDTO {
    private String conversationId;
    private String sessionId;
    private String title;
    private String firstQuestion;
    private String answerSummary;
    private Integer messageCount;
    private Integer tokenCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
