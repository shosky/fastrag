package com.fastrag.module.agent.middleware;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Agent 中间件链执行器。
 * 按 {@link AgentMiddleware#getOrder()} 排序执行所有中间件。
 * before 阶段正序执行，after 阶段逆序执行，interceptor 取第一个非 null 结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentMiddlewareChain {

    private final List<AgentMiddleware> middlewares;

    @PostConstruct
    public void init() {
        // 按 Order 排序
        middlewares.sort(Comparator.comparingInt(AgentMiddleware::getOrder));
        log.info("[MiddlewareChain] Initialized with {} middlewares (sorted by order): {}",
                middlewares.size(),
                middlewares.stream().map(m -> m.getName() + "(" + m.getOrder() + ")").toList());
    }

    /**
     * 前置阶段: 按顺序执行所有中间件的 beforeModelCall。
     */
    public void executeBefore(BaseContext context,
                              List<ChatMessage> messages,
                              List<ToolDefinition> tools) {
        for (AgentMiddleware m : middlewares) {
            try {
                context = m.beforeModelCall(context, messages, tools);
            } catch (Exception e) {
                log.warn("[Middleware] beforeModelCall error in {}: {}",
                        m.getName(), e.getMessage());
            }
        }
    }

    /**
     * 后置阶段: 按逆序执行所有中间件的 afterModelCall。
     */
    public void executeAfter(BaseContext context, ChatResponse response) {
        for (int i = middlewares.size() - 1; i >= 0; i--) {
            try {
                context = middlewares.get(i).afterModelCall(context, response);
            } catch (Exception e) {
                log.warn("[Middleware] afterModelCall error in {}: {}",
                        middlewares.get(i).getName(), e.getMessage());
            }
        }
    }

    /**
     * 获取第一个匹配的 ToolCallInterceptor。
     * 按中间件顺序遍历，返回第一个非 null 结果。
     */
    public ToolCallInterceptor getToolCallInterceptor(BaseContext context,
                                                       ChatMessage.ToolCall toolCall) {
        for (AgentMiddleware m : middlewares) {
            try {
                ToolCallInterceptor interceptor = m.interceptToolCall(context, toolCall);
                if (interceptor != null) return interceptor;
            } catch (Exception e) {
                log.warn("[Middleware] interceptToolCall error in {}: {}",
                        m.getName(), e.getMessage());
            }
        }
        return null;
    }
}
