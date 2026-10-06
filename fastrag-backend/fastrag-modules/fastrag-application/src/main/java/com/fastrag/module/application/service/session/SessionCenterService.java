package com.fastrag.module.application.service.session;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.common.response.PageResult;
import com.fastrag.module.application.dto.ConversationDTO;
import com.fastrag.module.application.dto.ConversationRequestState;
import com.fastrag.module.application.entity.App;
import com.fastrag.module.application.entity.AppConversation;
import com.fastrag.module.application.entity.AppConversationMessage;
import com.fastrag.module.application.mapper.AppConversationMapper;
import com.fastrag.module.application.mapper.AppConversationMessageMapper;
import com.fastrag.module.application.mapper.AppMapper;
import com.fastrag.module.application.service.AppService;
import com.fastrag.module.application.service.preset.DialogDefaults;
import com.fastrag.security.filter.LoginUser;
import com.fastrag.security.util.DataScope;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话中心（new-conversation，contract new-conversation.SessionCenter）：
 * 用户聚合轴的会话生命周期——创建/统一列表（置顶优先+来源标记）/详情（请求态还原）/
 * 置顶/清空/批量删除/LLM 起标题/停止 + 直聊消息级操作（登记债务 #4 的补口）。
 *
 * <p>归属不变量：会话 ID 主键定位 + API Token 旁路（与账本同语义）；历史消息含 references
 * 供前端回看（REQ-F02）。消息级三操作委托 {@link AppService} 同名方法——其 appId 参数为
 * 遗留 no-op（方法体仅走 requireOwnMessage 消息→会话→属主链），直聊会话传 null 安全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionCenterService {

    private final AppConversationMapper convMapper;
    private final AppConversationMessageMapper convMsgMapper;
    private final AppMapper appMapper;
    private final LlmService llmService;
    private final SessionRequestStateStore requestStateStore;
    private final StopRegistry stopRegistry;
    private final AppService appService;

    /** 创建会话（app/kb 均可选，REQ-A01）；创建即固化请求态初值（data.md 生命周期） */
    public ConversationDTO createConversation(Map<String, Object> form) {
        LoginUser user = SecurityUtil.getCurrentUser();
        String appId = form == null ? null : (String) form.get("appId");
        if (StrUtil.isNotBlank(appId)) {
            App app = appMapper.selectById(appId);
            if (app == null) throw BusinessException.notFound("应用不存在");
            if (!DataScope.visible(user, app.getOwner(), app.getOrgId(), null)) {
                throw BusinessException.forbidden("无权访问该应用");
            }
        }

        ConversationRequestState state = new ConversationRequestState();
        state.setAppId(StrUtil.blankToDefault(appId, null));
        Object kbIds = form == null ? null : form.get("kbIds");
        if (kbIds instanceof List<?> list) {
            state.setKbIds(list.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toList()));
        }
        Object modelId = form == null ? null : form.get("modelId");
        if (modelId != null) state.setModelId(modelId.toString());
        state.validate();

        AppConversation conv = new AppConversation();
        conv.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
        conv.setAppId(StrUtil.blankToDefault(appId, null));
        conv.setUserId(user.getUserId());
        conv.setUserName(user.getUsername());
        conv.setTitle("新对话");
        conv.setMessageCount(0);
        conv.setTokenCount(0);
        conv.setIsPinned(0);
        conv.setRequestState(JSONUtil.toJsonStr(state));
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        convMapper.insert(conv);

        ConversationDTO dto = ConversationDTO.from(conv, null);
        dto.setRequestState(state);
        return dto;
    }

    /** 统一列表：置顶优先 → updatedAt 倒序；非 API Token 仅本人；来源标记批量查询（防 N+1，REQ-A03） */
    public PageResult<ConversationDTO> listConversations(int page, int pageSize) {
        LoginUser user = SecurityUtil.getCurrentUser();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);

        LambdaQueryWrapper<AppConversation> countW = new LambdaQueryWrapper<>();
        if (!DataScope.isApiToken(user)) countW.eq(AppConversation::getUserId, user.getUserId());
        long total = convMapper.selectCount(countW);

        LambdaQueryWrapper<AppConversation> listW = new LambdaQueryWrapper<>();
        if (!DataScope.isApiToken(user)) listW.eq(AppConversation::getUserId, user.getUserId());
        listW.orderByDesc(AppConversation::getIsPinned)
                .orderByDesc(AppConversation::getUpdatedAt)
                .last("LIMIT " + ((long) (safePage - 1) * safeSize) + "," + safeSize);
        List<AppConversation> rows = convMapper.selectList(listW);

        // 来源标记：每页一次批量应用名查询
        List<String> appIds = rows.stream().map(AppConversation::getAppId)
                .filter(Objects::nonNull).distinct().toList();
        Map<String, String> appNames = appIds.isEmpty() ? Map.of()
                : appMapper.selectBatchIds(appIds).stream()
                        .collect(Collectors.toMap(App::getId, App::getName, (a, b) -> a));

        List<ConversationDTO> list = rows.stream().map(conv ->
                ConversationDTO.from(conv, conv.getAppId() == null ? null : appNames.get(conv.getAppId()))).toList();
        return PageResult.of(list, total, safePage, safeSize);
    }

    /** 详情：请求态（NULL→空形状）+ 消息历史（含 references），供输入框还原与历史回看（REQ-A04/F02） */
    public ConversationDTO getConversation(String conversationId) {
        AppConversation conv = requireOwn(conversationId);
        ConversationDTO dto = ConversationDTO.from(conv, resolveAppName(conv.getAppId()));
        dto.setRequestState(requestStateStore.loadOf(conv));
        List<Map<String, Object>> messages = new ArrayList<>();
        for (AppConversationMessage msg : convMsgMapper.selectList(
                new LambdaQueryWrapper<AppConversationMessage>()
                        .eq(AppConversationMessage::getConversationId, conv.getId())
                        .isNull(AppConversationMessage::getDeletedAt)
                        .orderByAsc(AppConversationMessage::getCreatedAt))) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", msg.getId());
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            m.put("references", msg.getReferences());
            m.put("thinkingContent", msg.getThinkingContent());
            m.put("toolCalls", msg.getToolCalls());
            m.put("iterations", msg.getIterations());
            m.put("feedback", msg.getFeedback());
            m.put("createdAt", msg.getCreatedAt());
            messages.add(m);
        }
        dto.setMessages(messages);
        return dto;
    }

    /** 置顶/取消置顶（REQ-E05） */
    public void pin(String conversationId, boolean pinned) {
        AppConversation conv = requireOwn(conversationId);
        conv.setIsPinned(pinned ? 1 : 0);
        convMapper.updateById(conv);
    }

    /** 清空消息：全删（含软删）+ 计数归零 + 摘要置空（REQ-E03，设计决议） */
    public void clearMessages(String conversationId) {
        AppConversation conv = requireOwn(conversationId);
        convMsgMapper.delete(new LambdaQueryWrapper<AppConversationMessage>()
                .eq(AppConversationMessage::getConversationId, conv.getId()));
        conv.setMessageCount(0);
        conv.setFirstQuestion(null);
        conv.setAnswerSummary(null);
        conv.setUpdatedAt(LocalDateTime.now());
        convMapper.updateById(conv);
    }

    /** 批量删除：逐个归属校验 + 级联物理删（与单删语义一致，REQ-E04） */
    public void deleteConversations(List<String> ids) {
        if (ids == null) return;
        for (String id : ids) {
            AppConversation conv = requireOwn(id);
            convMsgMapper.delete(new LambdaQueryWrapper<AppConversationMessage>()
                    .eq(AppConversationMessage::getConversationId, conv.getId()));
            convMapper.deleteById(conv.getId());
        }
    }

    /**
     * LLM 起标题（REQ-E01）：模型取会话请求态 modelId（缺省 default 网关，与 C05 一致）；
     * 失败/空返回回退现行截断首问逻辑，不抛错。
     */
    public String generateTitle(String conversationId) {
        AppConversation conv = requireOwn(conversationId);
        String fallback = StrUtil.isNotBlank(conv.getTitle()) ? conv.getTitle() : "新对话";
        try {
            String source = StrUtil.isNotBlank(conv.getFirstQuestion()) ? conv.getFirstQuestion() : conv.getTitle();
            if (StrUtil.isBlank(source)) return fallback;
            ConversationRequestState state = requestStateStore.loadOf(conv);
            String model = StrUtil.isNotBlank(state.getModelId()) ? state.getModelId() : DialogDefaults.DEFAULT_MODEL;
            String prompt = "请为以下用户提问生成一个简洁的中文标题：直接输出标题文本本身，"
                    + "不要引号、句号或任何解释，最多 20 个字。\n用户提问：" + source;
            String title = llmService.chat(model, prompt);
            if (StrUtil.isBlank(title)) return fallback;
            title = title.trim().replaceAll("^[\"'「『]+|[\"'」』。．.]+$", "");
            if (title.isEmpty()) return fallback;
            if (title.length() > 30) title = title.substring(0, 30);
            conv.setTitle(title);
            conv.setUpdatedAt(LocalDateTime.now());
            convMapper.updateById(conv);
            return title;
        } catch (Exception e) {
            log.warn("[SessionCenter] generateTitle failed, keep current title: {}", e.getMessage());
            return fallback;
        }
    }

    /** 停止生成（REQ-E02）：无活跃流幂等 no-op；部分落库与关流在句柄 partialSave 内 */
    public boolean stop(String conversationId) {
        requireOwn(conversationId);
        return stopRegistry.cancel(conversationId);
    }

    // ===== 直聊消息级操作（债务登记 #4 补口）：委托 AppService（appId 参数为遗留 no-op） =====

    public void deleteMessage(String conversationId, String messageId) {
        requireOwn(conversationId);
        appService.deleteMessage(null, messageId);
    }

    public void feedbackMessage(String conversationId, String messageId, String feedback) {
        requireOwn(conversationId);
        appService.feedbackMessage(null, messageId, feedback);
    }

    public void updateMessage(String conversationId, String messageId, String content) {
        requireOwn(conversationId);
        appService.updateMessage(null, messageId, content);
    }

    // ===== 内部 =====

    /** 归属守卫：会话 ID 主键定位 + API Token 旁路（与账本同语义的会话中心侧复用点） */
    private AppConversation requireOwn(String conversationId) {
        AppConversation conv = convMapper.selectById(conversationId);
        if (conv == null) throw BusinessException.notFound("会话不存在");
        LoginUser user = SecurityUtil.getCurrentUser();
        if (!DataScope.isApiToken(user) && !user.getUserId().equals(conv.getUserId())) {
            throw BusinessException.forbidden("无权访问该会话");
        }
        return conv;
    }

    private String resolveAppName(String appId) {
        if (appId == null) return null;
        App app = appMapper.selectById(appId);
        return app != null ? app.getName() : null;
    }
}
