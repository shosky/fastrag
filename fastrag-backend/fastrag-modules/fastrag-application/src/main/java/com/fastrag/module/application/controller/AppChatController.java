package com.fastrag.module.application.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.module.application.dto.AppChatRequest;
import com.fastrag.module.application.dto.AppSessionDTO;
import com.fastrag.module.application.service.AppService;
import com.fastrag.security.util.SecurityUtil;
import com.fastrag.security.filter.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 应用对话控制器，提供应用级别的流式对话与会话管理REST API。
 *
 * <p>路由前缀：/api/apps/{appId}/chat。所有接口要求 app:use 权限。
 * 核心端点包括：
 * <ul>
 *   <li>POST /stream — SSE流式对话，接收用户提问并以Server-Sent Events方式返回模型生成内容</li>
 *   <li>POST /sessions — 创建新的对话会话</li>
 *   <li>GET /sessions — 获取当前用户在该应用下的会话列表</li>
 *   <li>GET /sessions/{sessionId}/messages — 获取指定会话的消息历史</li>
 *   <li>DELETE /sessions/{sessionId} — 删除指定会话</li>
 *   <li>DELETE /messages/{messageId} — 软删除单条消息</li>
 *   <li>POST /messages/{messageId}/feedback — 对消息进行反馈（点赞/点踩）</li>
 *   <li>PUT /messages/{messageId} — 编辑消息内容</li>
 * </ul>
 *
 * <p>依赖 {@link AppService} 完成实际的对话调度和会话管理逻辑，
 * 通过 {@link SecurityUtil} 获取当前登录用户信息。
 */
@RestController
@RequestMapping("/api/apps/{appId}/chat")
@RequiredArgsConstructor
public class AppChatController {

    private final AppService appService;

    /**
     * SSE 流式对话
     */
    @PreAuthorize("@perm.has('app:use')")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@PathVariable String appId, @RequestBody AppChatRequest request) {
        LoginUser user = SecurityUtil.getCurrentUser();
        return appService.runStream(appId, request.getQuery(),
                request.getSessionId(), user.getUserId(), user.getUsername());
    }

    /**
     * 创建新会话
     */
    @PreAuthorize("@perm.has('app:use')")
    @PostMapping("/sessions")
    public ApiResponse<?> createSession(@PathVariable String appId) {
        LoginUser user = SecurityUtil.getCurrentUser();
        Map<String, Object> session = appService.createSession(appId, user.getUserId(), user.getUsername());
        return ApiResponse.success(session);
    }

    /**
     * 获取会话列表
     */
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/sessions")
    public ApiResponse<?> listSessions(@PathVariable String appId) {
        LoginUser user = SecurityUtil.getCurrentUser();
        List<AppSessionDTO> sessions = appService.listSessions(appId, user.getUserId());
        return ApiResponse.success(sessions);
    }

    /**
     * 获取会话消息历史
     */
    @PreAuthorize("@perm.has('app:use')")
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<?> getMessages(@PathVariable String appId, @PathVariable String sessionId) {
        List<Map<String, Object>> messages = appService.getSessionMessages(appId, sessionId);
        return ApiResponse.success(messages);
    }

    /**
     * 删除会话
     */
    @PreAuthorize("@perm.has('app:use')")
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<?> deleteSession(@PathVariable String appId, @PathVariable String sessionId) {
        appService.deleteSession(appId, sessionId);
        return ApiResponse.success();
    }

    /**
     * 软删除单条消息
     */
    @PreAuthorize("@perm.has('app:use')")
    @DeleteMapping("/messages/{messageId}")
    public ApiResponse<?> deleteMessage(@PathVariable String appId, @PathVariable String messageId) {
        appService.deleteMessage(appId, messageId);
        return ApiResponse.success();
    }

    /**
     * 消息反馈（like/dislike/null）
     */
    @PreAuthorize("@perm.has('app:use')")
    @PostMapping("/messages/{messageId}/feedback")
    public ApiResponse<?> feedbackMessage(@PathVariable String appId, @PathVariable String messageId, @RequestBody Map<String, String> body) {
        appService.feedbackMessage(appId, messageId, body.getOrDefault("feedback", ""));
        return ApiResponse.success();
    }

    /**
     * 编辑消息内容
     */
    @PreAuthorize("@perm.has('app:use')")
    @PutMapping("/messages/{messageId}")
    public ApiResponse<?> updateMessage(@PathVariable String appId, @PathVariable String messageId, @RequestBody Map<String, String> body) {
        appService.updateMessage(appId, messageId, body.get("content"));
        return ApiResponse.success();
    }
}
