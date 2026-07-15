package com.fastrag.module.application.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用会话 DTO
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
