package com.fastrag.module.application.dto;

import lombok.Data;

/**
 * 应用对话请求DTO，用于接收前端发起的对话请求参数。
 *
 * <p>使用场景：用户在应用对话界面输入问题时，前端将参数封装为该对象提交到后端。
 * 主要包含两个字段：query 为用户输入的提问内容，sessionId 为可选的会话标识，
 * 不传时系统将自动创建新会话。
 *
 * @see com.fastrag.module.application.controller.AppChatController
 */
@Data
public class AppChatRequest {
    /** 用户消息 */
    private String query;

    /** 会话ID（可选，不传则创建新会话） */
    private String sessionId;
}
