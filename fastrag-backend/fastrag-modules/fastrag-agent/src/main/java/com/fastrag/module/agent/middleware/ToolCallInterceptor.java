package com.fastrag.module.agent.middleware;

import com.fastrag.ai.model.ChatMessage;
import lombok.Data;

/**
 * 工具调用拦截结果。
 * 由 AgentMiddleware.interceptToolCall() 返回，
 * 可指示跳过默认工具执行并直接返回替代结果。
 */
@Data
public class ToolCallInterceptor {
    /** 是否跳过默认工具执行 */
    private boolean skip;
    /** 跳过时的替代结果消息（role=tool） */
    private ChatMessage resultMessage;

    /**
     * 创建一个跳过拦截：让引擎使用 resultMessage 作为工具返回值，不执行默认工具。
     */
    public static ToolCallInterceptor skip(ChatMessage resultMessage) {
        ToolCallInterceptor i = new ToolCallInterceptor();
        i.setSkip(true);
        i.setResultMessage(resultMessage);
        return i;
    }
}
