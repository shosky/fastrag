package com.fastrag.module.application.controller;

import com.fastrag.common.response.ApiResponse;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.application.dto.ConversationDTO;
import com.fastrag.module.application.dto.ConversationRequestState;
import com.fastrag.module.application.service.AppService;
import com.fastrag.module.application.service.session.SessionCenterService;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 会话中心（new-conversation）：会话一等公民的统一 HTTP 面。
 *
 * <p>鉴权 = 登录即可（REQ-G04，无应用级权限键）；资源授权在知识库检索时逐 KB 执行。
 * 消息级三操作委托 AppService 既有实现（其 appId 参数为遗留 no-op，债务登记 #4）。
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class SessionCenterController {

    private final SessionCenterService sessionCenterService;
    private final AppService appService;

    /** 新建对话（app/kb 均可选） */
    @PostMapping
    public ApiResponse<ConversationDTO> create(@RequestBody(required = false) Map<String, Object> form) {
        return ApiResponse.success(sessionCenterService.createConversation(form));
    }

    /** 统一列表：置顶优先 + updatedAt 倒序 + 来源标记（REQ-A03） */
    @GetMapping
    public ApiResponse<PageResult<ConversationDTO>> list(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(sessionCenterService.listConversations(page, pageSize));
    }

    /** 详情：请求态 + 消息历史（含 references），重开还原（REQ-A04/F02） */
    @GetMapping("/{id}")
    public ApiResponse<ConversationDTO> detail(@PathVariable String id) {
        return ApiResponse.success(sessionCenterService.getConversation(id));
    }

    /** 置顶/取消置顶（REQ-E05） */
    @PutMapping("/{id}/pin")
    public ApiResponse<Void> pin(@PathVariable String id, @RequestBody Map<String, Object> body) {
        sessionCenterService.pin(id, Boolean.TRUE.equals(body.get("pinned"))
                || "true".equals(String.valueOf(body.get("pinned"))));
        return ApiResponse.success(null);
    }

    /** 清空消息（保会话，REQ-E03） */
    @DeleteMapping("/{id}/messages")
    public ApiResponse<Void> clearMessages(@PathVariable String id) {
        sessionCenterService.clearMessages(id);
        return ApiResponse.success(null);
    }

    /** 批量删除会话（REQ-E04） */
    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) body.get("ids");
        sessionCenterService.deleteConversations(ids);
        return ApiResponse.success(null);
    }

    /** LLM 起标题（REQ-E01，失败回退截断首问） */
    @PostMapping("/{id}/title")
    public ApiResponse<String> generateTitle(@PathVariable String id) {
        return ApiResponse.success(sessionCenterService.generateTitle(id));
    }

    /** 停止生成（REQ-E02；无活跃流幂等 no-op） */
    @PostMapping("/{id}/stop")
    public ApiResponse<Boolean> stop(@PathVariable String id) {
        return ApiResponse.success(sessionCenterService.stop(id));
    }

    /** 新通道流式对话（事件词汇与旧通道一致，REQ-C02） */
    @PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String query = body.get("query") == null ? "" : body.get("query").toString();
        ConversationRequestState turnState =
                JSONUtil.toBean(JSONUtil.toJsonStr(body), ConversationRequestState.class);
        return appService.runStreamByConversation(id, query, turnState,
                com.fastrag.security.util.SecurityUtil.getCurrentUser());
    }

    /** 删除单条消息（软删；直聊消息级补口，债务 #4） */
    @DeleteMapping("/{id}/messages/{messageId}")
    public ApiResponse<Void> deleteMessage(@PathVariable String id, @PathVariable String messageId) {
        sessionCenterService.deleteMessage(id, messageId);
        return ApiResponse.success(null);
    }

    /** 消息点赞/点踩/清除 */
    @PostMapping("/{id}/messages/{messageId}/feedback")
    public ApiResponse<Void> feedback(@PathVariable String id, @PathVariable String messageId,
                                      @RequestBody Map<String, Object> body) {
        sessionCenterService.feedbackMessage(id, messageId,
                body.get("feedback") == null ? "" : body.get("feedback").toString());
        return ApiResponse.success(null);
    }

    /** 编辑消息内容 */
    @PutMapping("/{id}/messages/{messageId}")
    public ApiResponse<Void> updateMessage(@PathVariable String id, @PathVariable String messageId,
                                           @RequestBody Map<String, Object> body) {
        sessionCenterService.updateMessage(id, messageId,
                body.get("content") == null ? "" : body.get("content").toString());
        return ApiResponse.success(null);
    }
}
