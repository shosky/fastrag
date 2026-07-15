package com.fastrag.module.agent.middleware;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 附件中间件。将上传的附件信息注入到 system prompt。
 *
 * <p>执行顺序: Order=2。</p>
 *
 * <p>从 context.runtimeState 中读取 "uploads" 字段，
 * 如果存在上传文件列表，则构建附件描述并注入到 system message 末尾。</p>
 */
@Slf4j
@Component
public class AttachmentMiddleware implements AgentMiddleware {

    @Override
    public int getOrder() { return 2; }

    @Override
    @SuppressWarnings("unchecked")
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        try {
            Object uploads = context.getRuntimeState().get("uploads");
            if (uploads instanceof List<?> fileList && !fileList.isEmpty()) {
                String attachmentPrompt = buildAttachmentPrompt(fileList);
                injectSystemPrompt(messages, attachmentPrompt);
                log.info("[AttachmentMiddleware] Injected {} attachments into system prompt", fileList.size());
            }
        } catch (Exception e) {
            log.error("[AttachmentMiddleware] Failed to process attachments: {}", e.getMessage());
        }
        return context;
    }

    @SuppressWarnings("unchecked")
    private String buildAttachmentPrompt(List<?> fileList) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- 用户上传的附件 ---\n");
        for (Object f : fileList) {
            if (f instanceof Map) {
                Map<String, String> fileMap = (Map<String, String>) f;
                sb.append("- ").append(fileMap.getOrDefault("file_name", "未知文件"));
                sb.append(" (路径: ").append(fileMap.getOrDefault("path", "")).append(")\n");
            } else {
                sb.append("- ").append(f).append("\n");
            }
        }
        sb.append("---\n");
        return sb.toString();
    }

    private void injectSystemPrompt(List<ChatMessage> messages, String prompt) {
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.getRole())) {
                String existing = msg.getContent();
                msg.setContent(existing != null ? existing + prompt : prompt);
                return;
            }
        }
        // 如果没有 system message，在开头添加一个
        messages.add(0, new ChatMessage("system", prompt));
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
