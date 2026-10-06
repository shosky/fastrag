package com.fastrag.module.application.service;
import com.fastrag.module.application.dto.AppSessionDTO;
import com.fastrag.module.application.dto.ConversationRequestState;
import com.fastrag.module.application.entity.*; import java.util.*;
import com.fastrag.security.filter.LoginUser;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AppService {
    List<App> list(String keyword, String tag);
    App get(String id);
    App create(Map<String, Object> form);
    App update(String id, Map<String, Object> form);
    void delete(String id);
    List<AppTemplate> getTemplates();
    AppConfig getConfig(String id);
    AppConfig saveConfig(String id, AppConfig config);

    /** @deprecated 使用流式对话 runStream 替代 */
    @Deprecated
    Map<String, Object> run(String id, String query);

    // ===== 会话管理 =====
    /** 创建新会话，返回会话信息 */
    Map<String, Object> createSession(String appId, String userId, String userName);

    /** 获取应用的会话列表 */
    List<AppSessionDTO> listSessions(String appId, String userId);

    /** 获取会话消息历史 */
    List<Map<String, Object>> getSessionMessages(String appId, String sessionId);

    /** 删除会话及其消息 */
    void deleteSession(String appId, String sessionId);

    /** 软删除单条消息 */
    void deleteMessage(String appId, String messageId);

    /** 消息反馈（like/dislike/null） */
    void feedbackMessage(String appId, String messageId, String feedback);

    /** 编辑消息内容 */
    void updateMessage(String appId, String messageId, String content);

    // ===== 流式对话 =====
    /** SSE 流式对话 */
    SseEmitter runStream(String appId, String query, String sessionId, String userId, String userName);

    /**
     * 新通道 SSE 流式对话（new-conversation）：会话一等公民入口。
     *
     * @param conversationId 会话 ID（主键定位，app_id 可空）
     * @param query          用户输入
     * @param turnState      本轮请求态覆盖（空时回落持久化形状）
     * @param user           当前用户
     */
    SseEmitter runStreamByConversation(String conversationId, String query,
                                       ConversationRequestState turnState, LoginUser user);
}
