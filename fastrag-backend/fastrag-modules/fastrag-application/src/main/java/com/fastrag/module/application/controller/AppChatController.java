package com.fastrag.module.application.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.application.dto.AppChatRequest;
import com.fastrag.module.application.dto.AppSessionDTO;
import com.fastrag.module.application.service.AppService;
import com.fastrag.security.util.SecurityUtil;
import com.fastrag.security.filter.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 应用对话控制器 - 提供流式对话和会话管理 API
 */
@RestController
@RequestMapping("/api/apps/{appId}/chat")
@RequiredArgsConstructor
public class AppChatController {

    private final AppService appService;

    /**
     * SSE 流式对话
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@PathVariable String appId, @RequestBody AppChatRequest request) {
        LoginUser user = SecurityUtil.getCurrentUser();
        return appService.runStream(appId, request.getQuery(),
                request.getSessionId(), user.getUserId(), user.getUsername());
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public ApiResponse<?> createSession(@PathVariable String appId) {
        LoginUser user = SecurityUtil.getCurrentUser();
        Map<String, Object> session = appService.createSession(appId, user.getUserId(), user.getUsername());
        return ApiResponse.success(session);
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/sessions")
    public ApiResponse<?> listSessions(@PathVariable String appId) {
        LoginUser user = SecurityUtil.getCurrentUser();
        List<AppSessionDTO> sessions = appService.listSessions(appId, user.getUserId());
        return ApiResponse.success(sessions);
    }

    /**
     * 获取会话消息历史
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<?> getMessages(@PathVariable String appId, @PathVariable String sessionId) {
        List<Map<String, Object>> messages = appService.getSessionMessages(appId, sessionId);
        return ApiResponse.success(messages);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<?> deleteSession(@PathVariable String appId, @PathVariable String sessionId) {
        appService.deleteSession(appId, sessionId);
        return ApiResponse.success();
    }

    /**
     * 软删除单条消息
     */
    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<?> deleteMessage(@PathVariable String appId, @PathVariable String messageId) {
        appService.deleteMessage(appId, messageId);
        return ApiResponse.success();
    }

    /**
     * 消息反馈（like/dislike/null）
     */
    @PostMapping("/messages/{messageId}/feedback")
    public ApiResponse<?> feedbackMessage(@PathVariable String appId, @PathVariable String messageId, @RequestBody Map<String, String> body) {
        appService.feedbackMessage(appId, messageId, body.getOrDefault("feedback", ""));
        return ApiResponse.success();
    }

    /**
     * 编辑消息内容
     */
    @PutMapping("/messages/{messageId}")
    public ApiResponse<?> updateMessage(@PathVariable String appId, @PathVariable String messageId, @RequestBody Map<String, String> body) {
        appService.updateMessage(appId, messageId, body.get("content"));
        return ApiResponse.success();
    }
}
