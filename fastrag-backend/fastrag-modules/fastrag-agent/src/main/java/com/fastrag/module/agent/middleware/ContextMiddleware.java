package com.fastrag.module.agent.middleware;

import cn.hutool.core.util.StrUtil;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.executor.ModelConfig;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 模型路由中间件。从 ModelRecord 表加载模型配置(apiUrl/apiKey/enableThinking)。
 *
 * <p>执行顺序: Order=0，最先执行。</p>
 *
 * <p>NOTE: ModelRecordMapper is in fastrag-platform module which may not be available here.
 * For now, this middleware logs the model and defers to context.modelConfig being set externally.
 * When cross-module access is available, inject ModelRecordMapper.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextMiddleware implements AgentMiddleware {

    @Override
    public int getOrder() { return 0; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        // If modelConfig already set (by AppServiceImpl), skip
        if (context.getModelConfig() != null) {
            log.debug("[ContextMiddleware] ModelConfig already set, skipping");
            return context;
        }

        String model = context.getModel();
        if (StrUtil.isBlank(model)) {
            log.warn("[ContextMiddleware] No model configured in context");
            return context;
        }

        try {
            // Build default config (apiUrl/apiKey to be filled by caller if needed)
            ModelConfig config = ModelConfig.default_(model);
            context.setModelConfig(config);
            log.info("[ContextMiddleware] Model config created: model={}", model);
        } catch (Exception e) {
            log.error("[ContextMiddleware] Failed to create model config: model={}, error={}",
                    model, e.getMessage());
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
}
