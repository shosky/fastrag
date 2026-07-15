package com.fastrag.module.application.service;
import com.fastrag.module.application.dto.AppSessionDTO;
import com.fastrag.module.application.entity.*; import java.util.*;
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
}
