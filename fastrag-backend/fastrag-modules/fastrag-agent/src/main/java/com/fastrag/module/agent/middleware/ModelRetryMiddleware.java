package com.fastrag.module.agent.middleware;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型重试中间件。提供 LLM 调用的重试配置。
 *
 * <p>执行顺序: Order=9。</p>
 *
 * <p>支持通过 context.modelRetryTimes 自定义最大重试次数。
 * 默认配置: maxRetries=2, baseIntervalMs=1000, multiplier=2.0。</p>
 *
 * <p>该中间件不直接执行重试逻辑，而是将 RetryConfig 写入
 * context.runtimeState("retryConfig")，由 AgentEngine 读取并执行重试。</p>
 */
@Slf4j
@Component
public class ModelRetryMiddleware implements AgentMiddleware {

    private static final int DEFAULT_MAX_RETRIES = 2;
    private static final long DEFAULT_BASE_INTERVAL_MS = 1000;
    private static final double DEFAULT_MULTIPLIER = 2.0;

    @Override
    public int getOrder() { return 9; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        try {
            RetryConfig config = getRetryConfig(context);
            context.getRuntimeState().put("retryConfig", config);
            log.debug("[ModelRetryMiddleware] Retry config set: maxRetries={}, baseIntervalMs={}, multiplier={}",
                    config.getMaxRetries(), config.getBaseIntervalMs(), config.getMultiplier());
        } catch (Exception e) {
            log.error("[ModelRetryMiddleware] Failed to set retry config: {}", e.getMessage());
        }

        return context;
    }

    @Override
    public BaseContext afterModelCall(BaseContext context, ChatResponse response) {
        return context;
    }

    @Override
    public ToolCallInterceptor interceptToolCall(BaseContext context,
                                                 ChatMessage.ToolCall toolCall) {
        return null;
    }

    /**
     * 根据 context 中的配置构建 RetryConfig。
     *
     * @param context 运行时上下文
     * @return 重试配置
     */
    public RetryConfig getRetryConfig(BaseContext context) {
        int maxRetries = DEFAULT_MAX_RETRIES;
        long baseIntervalMs = DEFAULT_BASE_INTERVAL_MS;
        double multiplier = DEFAULT_MULTIPLIER;

        if (context.getModelRetryTimes() != null) {
            maxRetries = Math.max(0, context.getModelRetryTimes());
            log.debug("[ModelRetryMiddleware] Using custom maxRetries={} from context", maxRetries);
        }

        RetryConfig config = new RetryConfig();
        config.setMaxRetries(maxRetries);
        config.setBaseIntervalMs(baseIntervalMs);
        config.setMultiplier(multiplier);
        return config;
    }

    /**
     * 重试配置。
     */
    @Data
    public static class RetryConfig {
        /** 最大重试次数 */
        private int maxRetries;
        /** 基础重试间隔（毫秒） */
        private long baseIntervalMs;
        /** 退避乘数（指数退避） */
        private double multiplier;

        /**
         * 计算第 n 次重试的等待时间。
         *
         * @param retryIndex 重试索引（从 0 开始）
         * @return 等待时间（毫秒）
         */
        public long getWaitTime(int retryIndex) {
            return (long) (baseIntervalMs * Math.pow(multiplier, retryIndex));
        }

        /**
         * 创建默认配置。
         */
        public static RetryConfig defaultConfig() {
            RetryConfig config = new RetryConfig();
            config.setMaxRetries(DEFAULT_MAX_RETRIES);
            config.setBaseIntervalMs(DEFAULT_BASE_INTERVAL_MS);
            config.setMultiplier(DEFAULT_MULTIPLIER);
            return config;
        }
    }
}
