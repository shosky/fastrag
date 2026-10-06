package com.fastrag.module.application.dto;

import com.fastrag.module.application.entity.AppConversation;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话中心列表/详情 DTO（new-conversation）。
 * 来源标记拆为 appId+appName+direct 三字段，由前端拼「直聊 / 来自应用 X」文案。
 */
@Data
public class ConversationDTO {
    private String id;
    private String title;
    private String appId;
    private String appName;
    private boolean direct;
    private String firstQuestion;
    private String answerSummary;
    private Integer messageCount;
    private Boolean pinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 详情态填充：会话请求态（NULL → 空形状，供输入框还原 REQ-A04）；列表态为 null */
    private ConversationRequestState requestState;
    /** 详情态填充：消息历史（含 references）；列表态为 null */
    private List<java.util.Map<String, Object>> messages;

    public static ConversationDTO from(AppConversation conv, String appName) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conv.getId());
        dto.setTitle(conv.getTitle());
        dto.setAppId(conv.getAppId());
        dto.setAppName(appName);
        dto.setDirect(conv.getAppId() == null);
        dto.setFirstQuestion(conv.getFirstQuestion());
        dto.setAnswerSummary(conv.getAnswerSummary());
        dto.setMessageCount(conv.getMessageCount());
        dto.setPinned(conv.getIsPinned() != null && conv.getIsPinned() == 1);
        dto.setCreatedAt(conv.getCreatedAt());
        dto.setUpdatedAt(conv.getUpdatedAt());
        return dto;
    }
}
