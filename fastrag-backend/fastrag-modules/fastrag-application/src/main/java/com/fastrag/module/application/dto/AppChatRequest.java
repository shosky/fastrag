package com.fastrag.module.application.dto;

import lombok.Data;

/**
 * 应用对话请求
 */
@Data
public class AppChatRequest {
    /** 用户消息 */
    private String query;

    /** 会话ID（可选，不传则创建新会话） */
    private String sessionId;
}
