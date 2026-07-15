package com.fastrag.module.agent.middleware;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;

import java.util.List;

/**
 * Agent 中间件接口（增强版）。
 * 支持三个阶段: beforeModelCall / afterModelCall / interceptToolCall。
 * 实现类可选择性覆盖需要的方法。
 *
 * <p>执行顺序由 {@link #getOrder()} 决定，值越小越先执行。
 * 建议范围: 0-100。</p>
 */
public interface AgentMiddleware extends Comparable<AgentMiddleware> {

    /**
     * 执行顺序。值越小越先执行。
     * 建议范围: 0-100。
     */
    int getOrder();

    @Override
    default int compareTo(AgentMiddleware other) {
        return Integer.compare(this.getOrder(), other.getOrder());
    }

    /**
     * 前置阶段: LLM 调用前。
     * 可修改 context（如添加运行时字段）或 messages（如注入 system prompt）。
     * 也可动态添加 tools 到工具列表。
     *
     * @param context  运行时上下文
     * @param messages 消息列表（可修改）
     * @param tools    当前工具列表（可动态添加工具）
     * @return 修改后的 context
     */
    default BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        return context;
    }

    /**
     * 后置阶段: LLM 调用后、工具执行前。
     * 可检查/修改 LLM 响应（如检测技能激活）。
     *
     * @param context  运行时上下文
     * @param response LLM 响应
     * @return 修改后的 context
     */
    default BaseContext afterModelCall(BaseContext context, ChatResponse response) {
        return context;
    }

    /**
     * 工具调用拦截: 工具执行前。
     * 可修改工具参数、替换执行结果、或阻断执行。
     * 返回 null 表示不拦截，交由默认执行逻辑。
     *
     * @param context  运行时上下文
     * @param toolCall LLM 返回的工具调用
     * @return ToolCallInterceptor 或 null（不拦截）
     */
    default ToolCallInterceptor interceptToolCall(BaseContext context,
                                                 ChatMessage.ToolCall toolCall) {
        return null;
    }

    /**
     * 中间件名称（用于日志）。
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}
