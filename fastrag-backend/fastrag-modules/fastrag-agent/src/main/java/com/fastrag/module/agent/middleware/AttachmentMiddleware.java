package com.fastrag.module.agent.middleware;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.context.ChatBotContext;
import com.fastrag.module.agent.sandbox.SandboxExecutionService;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 附件中间件。将上传的附件信息注入到 system prompt。
 *
 * <p>执行顺序: Order=2。</p>
 *
 * <p>从 context.runtimeState 中读取 "uploads" 字段。沙箱可用时先经
 * {@link SandboxExecutionService#stageAttachments} 把附件实体拷入沙箱
 * {@code /workspace/input}（REQ-H13），注入 prompt 的附件路径为<b>沙箱内路径</b>；
 * 沙箱不可用/暂存失败时保持现状纯 prompt 注入（服务端路径），降级不阻断。</p>
 */
@Slf4j
@Component
public class AttachmentMiddleware implements AgentMiddleware {

    @Autowired(required = false)
    private SandboxExecutionService sandboxExecutionService;

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
                Map<String, String> stagedPaths = stageToSandbox(context, fileList);
                String attachmentPrompt = buildAttachmentPrompt(fileList, stagedPaths);
                injectSystemPrompt(messages, attachmentPrompt);
                log.info("[AttachmentMiddleware] Injected {} attachments into system prompt, stagedInSandbox={}",
                        fileList.size(), stagedPaths.size());
            }
        } catch (Exception e) {
            log.error("[AttachmentMiddleware] Failed to process attachments: {}", e.getMessage());
        }
        return context;
    }

    /**
     * 附件暂存（REQ-H13）：uploads 非空且沙箱可用时拷入 /workspace/input；
     * 门面不可用/异常返回空 Map（降级为纯 prompt 注入，不阻断）。
     */
    private Map<String, String> stageToSandbox(BaseContext context, List<?> fileList) {
        if (sandboxExecutionService == null) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> uploads = fileList.stream()
                    .filter(Map.class::isInstance)
                    .map(f -> (Map<String, Object>) f)
                    .toList();
            if (uploads.isEmpty()) {
                return Map.of();
            }
            return sandboxExecutionService.stageAttachments(
                    context.getUid(), sessionIdOf(context), sandboxConfigIdOf(context), uploads);
        } catch (Exception e) {
            log.warn("[AttachmentMiddleware] Sandbox staging failed, fall back to prompt-only injection: {}",
                    e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private String buildAttachmentPrompt(List<?> fileList, Map<String, String> stagedPaths) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- 用户上传的附件 ---\n");
        for (Object f : fileList) {
            if (f instanceof Map) {
                Map<String, String> fileMap = (Map<String, String>) f;
                String fileName = fileMap.getOrDefault("file_name", "未知文件");
                // 沙箱暂存成功 → 注入沙箱内路径（REQ-H13）；否则保持服务端路径（现状降级）
                String staged = stagedPaths.get(fileName);
                String path = staged != null ? staged : fileMap.getOrDefault("path", "");
                sb.append("- ").append(fileName);
                sb.append(" (路径: ").append(path).append(")\n");
            } else {
                sb.append("- ").append(f).append("\n");
            }
        }
        if (!stagedPaths.isEmpty()) {
            sb.append("附件已拷入沙箱 ").append(com.fastrag.module.agent.sandbox.SandboxWorkspaceLayout.INPUT_DIR)
                    .append("（只读）。\n");
        }
        sb.append("---\n");
        return sb.toString();
    }

    /** 会话键：fileThreadId 扩展优先，回落 threadId → runId → uid。 */
    private String sessionIdOf(BaseContext context) {
        Object fileThreadId = context.getRuntimeState().get("fileThreadId");
        if (fileThreadId instanceof String s && !s.isBlank()) {
            return s;
        }
        if (context.getThreadId() != null && !context.getThreadId().isBlank()) {
            return context.getThreadId();
        }
        if (context.getRunId() != null && !context.getRunId().isBlank()) {
            return context.getRunId();
        }
        return context.getUid();
    }

    /** 沙箱配置 ID：runtimeState 优先（FilesystemMiddleware order=1 已写入）；回落 ChatBotContext 字段。 */
    private String sandboxConfigIdOf(BaseContext context) {
        Object existing = context.getRuntimeState().get("sandboxConfigId");
        if (existing instanceof String s && !s.isBlank()) {
            return s;
        }
        if (context instanceof ChatBotContext chatBot) {
            return chatBot.getSandboxConfigId();
        }
        return null;
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
