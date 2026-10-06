package com.fastrag.module.application.service.session;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fastrag.module.application.dto.ConversationRequestState;
import com.fastrag.module.application.entity.AppConversation;
import com.fastrag.module.application.mapper.AppConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 会话请求态存取（new-conversation）：request_state JSON 形状的唯一属主
 * （contract：SessionRequestStateStore；形状见 data.md ConversationRequestState）。
 *
 * <p>固化时机 = 与消息落库同点的三处（正常完成 onComplete / 超时 onTimeout / 停止部分落库），
 * 由编排执行核调用 {@link #persistOnSuccess}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionRequestStateStore {

    private final AppConversationMapper convMapper;

    /** 读取会话请求态；NULL/坏 JSON 一律还原为空形状（存量会话 G03 降级） */
    public ConversationRequestState load(String conversationId) {
        AppConversation conv = convMapper.selectById(conversationId);
        return parse(conv == null ? null : conv.getRequestState());
    }

    /** JSON → 形状；空白/坏 JSON 容错为空请求态，不抛（对齐 advancedOptions 手解容错口径） */
    public ConversationRequestState parse(String json) {
        if (StrUtil.isBlank(json)) return new ConversationRequestState();
        try {
            ConversationRequestState state = JSONUtil.toBean(json, ConversationRequestState.class);
            return state != null ? state : new ConversationRequestState();
        } catch (Exception e) {
            log.warn("[SessionRequestState] malformed request_state ignored: {}", e.getMessage());
            return new ConversationRequestState();
        }
    }

    /**
     * 对话成功（含超时/停止部分落库）后整包覆写请求态。
     * 校验失败（kbIds 超限等）抛业务错误；会话已删除则静默跳过。
     */
    public void persistOnSuccess(String conversationId, ConversationRequestState state) {
        if (state == null || conversationId == null) return;
        state.validate();
        AppConversation conv = convMapper.selectById(conversationId);
        if (conv == null) {
            log.warn("[SessionRequestState] conversation gone, skip persist: {}", conversationId);
            return;
        }
        conv.setRequestState(JSONUtil.toJsonStr(state));
        convMapper.updateById(conv);
    }

    /** 仅读形状（不落库）——供列表/详情组装来源标记等场景 */
    public ConversationRequestState loadOf(AppConversation conv) {
        return parse(conv == null ? null : conv.getRequestState());
    }
}
