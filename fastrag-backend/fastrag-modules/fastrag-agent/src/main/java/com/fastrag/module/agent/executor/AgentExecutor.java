package com.fastrag.module.agent.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.ai.model.*;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.util.JsonSchemaValidator;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.event.RunEventPublisher;
import com.fastrag.module.agent.middleware.AgentMiddleware;
import com.fastrag.module.agent.middleware.AgentMiddlewareChain;
import com.fastrag.module.tools.executor.*;
import com.fastrag.module.tools.registry.ToolDefinition;
import com.fastrag.module.tools.registry.ToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

/**
 * @deprecated 使用 {@link AgentEngine} 替代。
 * 保留此类仅为向后兼容，后续版本将移除。
 */
@Slf4j
@Component
@Deprecated
public class AgentExecutor {
    private final LlmService llmService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutorFactory executorFactory;
    private final RunEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    private AgentMiddlewareChain middlewareChain;

    public AgentExecutor(LlmService llmService, ToolRegistry toolRegistry,
                          ToolExecutorFactory executorFactory,
                          RunEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.executorFactory = executorFactory;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    private static final int MAX_TOOL_ITERATIONS = 10;

    @PostConstruct
    public void init() {
        // Deprecated: use empty middleware chain to avoid circular references
        this.middlewareChain = new AgentMiddlewareChain(List.of());
        log.info("[AgentExecutor] Initialized with empty middleware chain (deprecated)");
    }

    public AgentResult execute(AgentRun run, BaseContext context) {
        long totalStart = System.currentTimeMillis();
        log.info("[AgentExecutor] Start: runId={}, model={}", run.getId(), context.getModel());

        try {
            // 1. Resolve tool definitions
            List<ToolDefinition> tools = toolRegistry.resolveTools(context.getTools());
            List<ChatRequest.ToolDefinition> openaiTools = toolRegistry.toOpenAITools(tools);
            log.info("[AgentExecutor] Resolved {} tools", tools.size());

            // 2. Build initial messages
            List<ChatMessage> messages = new ArrayList<>();
            if (context.getSystemPrompt() != null && !context.getSystemPrompt().isEmpty()) {
                messages.add(new ChatMessage("system", context.getSystemPrompt()));
            }
            // Add user query from run input
            String userQuery = extractQuery(run);
            messages.add(new ChatMessage("user", userQuery));

            // 3. Execute middleware before first model call
            middlewareChain.executeBefore(context, messages, List.of());

            // 4. Tool calling loop
            for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
                log.info("[AgentExecutor] Iteration {}/{}", i + 1, MAX_TOOL_ITERATIONS);

                // 3a. Call LLM
                ChatResponse resp = llmService.chatWithTools(
                    context.getModel(), messages, openaiTools, 0.7, null, null);

                // 3b. Execute middleware after model call
                middlewareChain.executeAfter(context, resp);

                // 3c. Check if LLM wants to call tools
                if (!resp.hasToolCalls()) {
                    // LLM produced final answer
                    log.info("[AgentExecutor] LLM returned final answer");
                    AgentResult result = AgentResult.success(resp.getContent());
                    result.setTotalDurationMs((int)(System.currentTimeMillis() - totalStart));
                    return result;
                }

                // 3c. Add assistant message with tool_calls to history
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(resp.getContent());
                assistantMsg.setToolCalls(resp.getToolCalls());
                messages.add(assistantMsg);

                // 3d. Execute each tool call
                for (ChatMessage.ToolCall tc : resp.getToolCalls()) {
                    String toolName = tc.getFunction().getName();
                    String argsJson = tc.getFunction().getArguments();
                    log.info("[AgentExecutor] Executing tool: {} args={}", toolName, argsJson);

                    // Find tool definition
                    ToolDefinition toolDef = findTool(tools, toolName);
                    if (toolDef == null) {
                        log.warn("[AgentExecutor] Tool not found: {}", toolName);
                        messages.add(new ChatMessage("tool", "Error: Tool '" + toolName + "' not found", tc.getId()));
                        continue;
                    }

                    // Parse arguments
                    Map<String, Object> args;
                    try {
                        args = objectMapper.readValue(argsJson, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        log.error("[AgentExecutor] Failed to parse arguments: {}", argsJson, e);
                        messages.add(new ChatMessage("tool", "Error: Invalid arguments JSON", tc.getId()));
                        continue;
                    }

                    // Validate arguments against schema
                    if (toolDef.getInputSchema() != null) {
                        JsonSchemaValidator.ValidationResult vr =
                            JsonSchemaValidator.validate(args, toolDef.getInputSchema());
                        if (!vr.valid()) {
                            log.warn("[AgentExecutor] Validation failed: {}", vr.errors());
                            messages.add(new ChatMessage("tool", "Error: " + vr.errors(), tc.getId()));
                            continue;
                        }
                    }

                    // Execute tool
                    ToolContext toolCtx = new ToolContext();
                    toolCtx.setUserId(context.getUid());
                    toolCtx.setAppId(run.getId());
                    toolCtx.setRunId(run.getId());
                    toolCtx.setQuery(userQuery);

                    ToolExecutor executor = executorFactory.getExecutor(toolDef.getType());
                    if (executor == null) {
                        messages.add(new ChatMessage("tool", "Error: No executor for tool type '" + toolDef.getType() + "'", tc.getId()));
                        continue;
                    }

                    ToolResult toolResult = executor.execute(toolDef, args, toolCtx);

                    // Record for result
                    if (run != null && run.getId() != null) {
                        try {
                            Map<String, Object> payload = new LinkedHashMap<>();
                            payload.put("toolName", toolName);
                            payload.put("toolCallId", tc.getId());
                            payload.put("success", toolResult.isSuccess());
                            payload.put("output", toolResult.getOutput());
                            payload.put("error", toolResult.getError());
                            payload.put("durationMs", toolResult.getDurationMs());
                            eventPublisher.pushEvent(run.getId(), "tool_call", payload);
                        } catch (Exception e) {
                            log.debug("Event publish failed (non-critical): {}", e.getMessage());
                        }
                    }

                    // Add tool result to messages
                    String resultContent = toolResult.isSuccess() ? toolResult.getOutput() : "Error: " + toolResult.getError();
                    // Truncate if too long for LLM context
                    if (resultContent.length() > 5000) {
                        resultContent = resultContent.substring(0, 5000) + "...(truncated)";
                    }
                    messages.add(new ChatMessage("tool", resultContent, tc.getId()));
                }
            }

            return AgentResult.error("达到最大工具调用轮次限制 (" + MAX_TOOL_ITERATIONS + ")");
        } catch (Exception e) {
            log.error("[AgentExecutor] Fatal error", e);
            AgentResult r = AgentResult.error("Agent execution error: " + e.getMessage());
            r.setTotalDurationMs((int)(System.currentTimeMillis() - totalStart));
            return r;
        }
    }

    /**
     * 流式执行：纯文本对话（无 tools），通过 SseEmitter 逐 chunk 推送。
     * 适用于应用中心普通对话场景。
     *
     * @param messages 包含 system prompt + 历史消息 + 用户消息的完整列表
     * @param model    模型名称
     * @param temperature 温度
     * @param emitter  SSE 发射器
     */
    public void executeStream(List<ChatMessage> messages, String model,
                               double temperature, SseEmitter emitter) {
        long totalStart = System.currentTimeMillis();
        StringBuilder fullAnswer = new StringBuilder();

        try {
            // 使用 Flux 订阅逐 chunk 推送
            llmService.streamChat(model, messages, temperature)
                    .doOnSubscribe(s -> {
                        try {
                            emitter.send(SseEmitter.event().name("init").data("{\"status\":\"loading\"}"));
                            emitter.send(SseEmitter.event().name("message").data("{\"content\":\"\"}"));
                        } catch (Exception e) {
                            log.warn("[AgentExecutor] Failed to send init event: {}", e.getMessage());
                        }
                    })
                    .subscribe(
                        chunk -> {
                            try {
                                if (chunk.getContent() != null && !chunk.getContent().isEmpty()) {
                                    fullAnswer.append(chunk.getContent());
                                    // 发送增量内容
                                    Map<String, Object> data = new LinkedHashMap<>();
                                    data.put("content", chunk.getContent());
                                    emitter.send(SseEmitter.event().name("message").data(data));
                                }
                            } catch (Exception e) {
                                log.warn("[AgentExecutor] Failed to send chunk: {}", e.getMessage());
                            }
                        },
                        error -> {
                            log.error("[AgentExecutor] Stream error: {}", error.getMessage());
                            try {
                                Map<String, Object> errData = new LinkedHashMap<>();
                                errData.put("message", "模型调用失败: " + error.getMessage());
                                emitter.send(SseEmitter.event().name("error").data(errData));
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        () -> {
                            long elapsed = System.currentTimeMillis() - totalStart;
                            log.info("[AgentExecutor] Stream complete: elapsed={}ms, contentLen={}",
                                    elapsed, fullAnswer.length());
                            try {
                                Map<String, Object> endData = new LinkedHashMap<>();
                                endData.put("content", fullAnswer.toString());
                                endData.put("durationMs", elapsed);
                                emitter.send(SseEmitter.event().name("end").data(endData));
                                emitter.complete();
                            } catch (Exception e) {
                                log.warn("[AgentExecutor] Failed to send end event: {}", e.getMessage());
                                emitter.complete();
                            }
                        }
                    );
        } catch (Exception e) {
            log.error("[AgentExecutor] executeStream fatal error", e);
            try {
                Map<String, Object> errData = new LinkedHashMap<>();
                errData.put("message", "执行失败: " + e.getMessage());
                emitter.send(SseEmitter.event().name("error").data(errData));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    private ToolDefinition findTool(List<ToolDefinition> tools, String name) {
        return tools.stream()
            .filter(t -> name.equals(t.getName()) || name.equals(t.getToolId()))
            .findFirst()
            .orElse(null);
    }

    private String extractQuery(AgentRun run) {
        // Try to get query from input payload
        Object input = run.getInputPayload();
        if (input == null) return "";
        if (input instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) input;
            Object q = m.get("query");
            if (q != null) return q.toString();
            Object m2 = m.get("message");
            if (m2 != null) return m2.toString();
            Object m3 = m.get("input");
            if (m3 != null) return m3.toString();
        }
        return input.toString();
    }
}
