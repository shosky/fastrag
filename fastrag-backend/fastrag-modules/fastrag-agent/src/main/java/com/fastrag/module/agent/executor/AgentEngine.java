package com.fastrag.module.agent.executor;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.ai.model.*;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.util.JsonSchemaValidator;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.event.RunEventPublisher;
import com.fastrag.module.agent.middleware.AgentMiddlewareChain;
import com.fastrag.module.agent.middleware.ToolCallInterceptor;
import com.fastrag.module.tools.executor.*;
import com.fastrag.module.tools.registry.ToolDefinition;
import com.fastrag.module.tools.registry.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 智能体核心引擎（替代 AgentExecutor）。
 * 统一的流式执行引擎，支持工具调用循环 + 中间件链 + thinking 提取 + SSE 事件推送。
 *
 * <p>两种执行模式：
 * <ul>
 *   <li>{@link #executeStream} — 流式执行（应用对话场景），通过 SseEmitter 推送事件</li>
 *   <li>{@link #executeSync} — 同步执行（Agent 场景），返回 AgentResult</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEngine {

    private final LlmService llmService;
    private final ToolRegistry toolRegistry;
    private final ToolExecutorFactory executorFactory;
    private final RunEventPublisher eventPublisher;
    private final AgentMiddlewareChain middlewareChain;
    private final ObjectMapper objectMapper;

    /** @Value("${llm.gateway.timeout:30}") */
    private int gatewayTimeoutSeconds = 30;

    /** 最大工具调用轮次 */
    private static final int MAX_ITERATIONS = 15;

    /** 工具输出截断长度 */
    private static final int TOOL_OUTPUT_MAX = 5000;

    // ==================== 流式执行（应用对话场景） ====================

    /**
     * 流式执行智能体：支持工具调用循环 + 中间件链 + thinking 提取 + SSE 事件推送。
     *
     * @param context      运行时上下文（需已设置 modelConfig）
     * @param emitter      SSE 发射器（已设置 timeout 和 callback）
     * @param modelConfig  模型配置（apiUrl, apiKey, enableThinking, temperature）
     * @param run          AgentRun 记录（可为 null，用于事件记录）
     * @param onComplete   流完成回调（可选，用于保存消息到 DB 等）
     */
    public void executeStream(BaseContext context,
                              SseEmitter emitter,
                              ModelConfig modelConfig,
                              AgentRun run,
                              java.util.function.Consumer<String> onComplete) {
        long totalStart = System.currentTimeMillis();
        StringBuilder fullAnswer = new StringBuilder();
        StringBuilder thinkingContent = new StringBuilder();
        int totalIterations = 0;

        try {
            // 1. 解析工具定义
            List<ToolDefinition> tools = toolRegistry.resolveTools(context.getTools());
            List<ChatRequest.ToolDefinition> openaiTools = toolRegistry.toOpenAITools(tools);
            log.info("[AgentEngine] executeStream start: model={}, tools={}", modelConfig.getModel(), tools.size());

            // 2. 构建初始消息列表
            List<ChatMessage> messages = new ArrayList<>();
            if (StrUtil.isNotBlank(context.getSystemPrompt())) {
                messages.add(new ChatMessage("system", context.getSystemPrompt()));
            }
            String userQuery = extractQuery(run);
            messages.add(new ChatMessage("user", userQuery));

            // 3. 发送 init 事件
            sendSseEvent(emitter, "init", Map.of("status", "loading"));

            // 4. 前置中间件链（中间件会往 tools 列表中添加新工具）
            middlewareChain.executeBefore(context, messages, tools);

            // 中间件可能已添加新工具到 tools 列表，重新生成 openaiTools
            openaiTools = toolRegistry.toOpenAITools(tools);
            log.info("[AgentEngine] After middleware: total tools={}", tools.size());

            // 5. 工具调用循环（maxSteps 可通过 context 配置）
            int maxIterations = (context.getMaxSteps() != null && context.getMaxSteps() > 0)
                    ? context.getMaxSteps() : MAX_ITERATIONS;
            for (int round = 0; round < maxIterations; round++) {
                totalIterations++;
                log.info("[AgentEngine] Iteration {}/{}", round + 1, maxIterations);

                // 打印当前消息列表摘要
                logMessagesSummary(messages, round);

                // 5a. 调用 LLM（流式 + 工具 + thinking）
                Flux<StreamEvent> flux = llmService.streamChatWithTools(
                        modelConfig.getModel(), messages, openaiTools,
                        modelConfig.getTemperature(), modelConfig.getApiUrl(),
                        modelConfig.getApiKey(), modelConfig.isEnableThinking());

                // 5b. 流式消费 + SSE 推送
                LlmStreamResult result = consumeStream(
                        flux, emitter, fullAnswer, thinkingContent);

                // 5c. 后置中间件链
                middlewareChain.executeAfter(context, result.getResponse());

                // 5d. 无工具调用 → 结束循环
                if (!result.getResponse().hasToolCalls()) {
                    log.info("[AgentEngine] LLM returned final answer (iterations={})", totalIterations);
                    break;
                }

                // 5e. 将 assistant 消息（含 tool_calls）加入历史
                // content 设置为 null（NON_NULL 会排除它），因为对于 tool_calls 消息
                // 某些 API（如 SiliconFlow）不允许 content 为空字符串
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(null);
                assistantMsg.setToolCalls(result.getResponse().getToolCalls());
                messages.add(assistantMsg);
                log.debug("[AgentEngine] Added assistant message with {} toolCalls, content=null(NON_NULL excluded)",
                        result.getResponse().getToolCalls().size());
                for (ChatMessage.ToolCall tc : result.getResponse().getToolCalls()) {
                    log.debug("[AgentEngine]   toolCall: id={}, name={}, argsLen={}",
                            tc.getId(),
                            tc.getFunction() != null ? tc.getFunction().getName() : "null",
                            tc.getFunction() != null && tc.getFunction().getArguments() != null ? tc.getFunction().getArguments().length() : 0);
                }

                // 5f. 执行每个工具调用
                for (ChatMessage.ToolCall tc : result.getResponse().getToolCalls()) {
                    // 中间件拦截（如 SkillsMiddleware 激活技能）
                    ToolCallInterceptor interceptor =
                            middlewareChain.getToolCallInterceptor(context, tc);
                    if (interceptor != null && interceptor.isSkip()) {
                        // 中间件处理了该工具调用
                        ChatMessage interceptorMsg = interceptor.getResultMessage();
                        if (interceptorMsg.getName() == null && tc.getFunction() != null) {
                            interceptorMsg.setName(tc.getFunction().getName());
                        }
                        messages.add(interceptorMsg);
                        // 推送 tool_result 事件
                        sendSseEvent(emitter, "tool_result", Map.of(
                                "id", tc.getId(),
                                "name", tc.getFunction().getName(),
                                "success", true,
                                "output", truncate(interceptor.getResultMessage().getContent(), TOOL_OUTPUT_MAX),
                                "durationMs", 0,
                                "intercepted", true
                        ));
                        continue;
                    }

                    // 发送 tool_call 事件
                    sendSseEvent(emitter, "tool_call", Map.of(
                            "id", tc.getId(),
                            "name", tc.getFunction().getName(),
                            "arguments", tc.getFunction().getArguments(),
                            "index", result.getResponse().getToolCalls().indexOf(tc),
                            "status", "calling"
                    ));

                    // 查找工具定义
                    ToolDefinition toolDef = findTool(tools, tc.getFunction().getName());
                    if (toolDef == null) {
                        String errOutput = "Error: Tool '" + tc.getFunction().getName() + "' not found";
                        sendSseEvent(emitter, "tool_result", Map.of(
                                "id", tc.getId(), "name", tc.getFunction().getName(),
                                "success", false, "output", errOutput, "durationMs", 0));
                        ChatMessage toolNotFoundMsg = new ChatMessage("tool", errOutput, tc.getId());
                        toolNotFoundMsg.setName(tc.getFunction().getName());
                        messages.add(toolNotFoundMsg);
                        continue;
                    }

                    // 解析参数 + 执行
                    long toolStart = System.currentTimeMillis();
                    ToolResult toolResult = executeTool(toolDef, tc, context, run);
                    long toolDuration = System.currentTimeMillis() - toolStart;

                    // 发送 tool_result 事件
                    Map<String, Object> resultData = new LinkedHashMap<>();
                    resultData.put("id", tc.getId());
                    resultData.put("name", tc.getFunction().getName());
                    resultData.put("success", toolResult.isSuccess());
                    resultData.put("output", truncate(toolResult.getOutput(), TOOL_OUTPUT_MAX));
                    resultData.put("durationMs", toolDuration);
                    if (toolResult.getError() != null) {
                        resultData.put("error", toolResult.getError());
                    }
                    sendSseEvent(emitter, "tool_result", resultData);

                    // 加入消息历史（截断到大模型友好的长度，前端仍然可查看完整结果）
                    String resultContent = toolResult.isSuccess()
                            ? toolResult.getOutput() : "Error: " + toolResult.getError();
                    // 工具返回数据给 LLM 时截断到 2000 字符
                    // 注意: tool 消息需要包含 name 字段（SiliconFlow/Qwen 等要求）
                    ChatMessage toolResultMsg = new ChatMessage("tool",
                            truncate(resultContent, 2000), tc.getId());
                    toolResultMsg.setName(tc.getFunction().getName());
                    messages.add(toolResultMsg);
                    log.debug("[AgentEngine] Added tool result message: role=tool, toolCallId={}, name={}, contentLen={}",
                            tc.getId(), tc.getFunction().getName(),
                            truncate(resultContent, 2000).length());

                    // 事件记录
                    if (run != null && run.getId() != null) {
                        publishToolCallEvent(run.getId(), tc, toolResult, toolDuration);
                    }
                }

                // 5g. 推送 agent_state（todos 等）
                pushAgentState(context, emitter);
            }

            // 6. 发送 end 事件
            long totalDuration = System.currentTimeMillis() - totalStart;

            // 调用完成回调（AppServiceImpl 保存消息等）
            if (onComplete != null) {
                try {
                    onComplete.accept(fullAnswer.toString());
                } catch (Exception e) {
                    log.warn("[AgentEngine] onComplete callback error: {}", e.getMessage());
                }
            }

            Map<String, Object> endData = new LinkedHashMap<>();
            endData.put("content", fullAnswer.toString());
            endData.put("durationMs", totalDuration);
            endData.put("iterations", totalIterations);
            endData.put("hasThinking", thinkingContent.length() > 0);
            sendSseEvent(emitter, "end", endData);
            emitter.complete();

            log.info("[AgentEngine] executeStream complete: duration={}ms, iterations={}, contentLen={}",
                    totalDuration, totalIterations, fullAnswer.length());

        } catch (Exception e) {
            log.error("[AgentEngine] executeStream fatal error", e);
            try {
                Map<String, Object> errData = new LinkedHashMap<>();
                errData.put("message", "Agent execution error: " + e.getMessage());
                sendSseEvent(emitter, "error", errData);
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    // ==================== 同步执行（Agent 场景） ====================

    /**
     * 同步执行智能体：执行工具调用循环但不推 SSE。
     * 结果写入 RunEventPublisher，返回 AgentResult。
     *
     * @param run     AgentRun 记录
     * @param context 运行时上下文（需已设置 modelConfig）
     * @return AgentResult
     */
    public AgentResult executeSync(AgentRun run, BaseContext context) {
        long totalStart = System.currentTimeMillis();
        int totalIterations = 0;
        List<AgentResult.ToolCallRecord> toolCallRecords = new ArrayList<>();

        try {
            // 1. 解析工具
            List<ToolDefinition> tools = toolRegistry.resolveTools(context.getTools());
            List<ChatRequest.ToolDefinition> openaiTools = toolRegistry.toOpenAITools(tools);
            ModelConfig mc = context.getModelConfig() != null
                    ? context.getModelConfig() : ModelConfig.default_(context.getModel());

            log.info("[AgentEngine] executeSync start: runId={}, model={}, tools={}",
                    run.getId(), mc.getModel(), tools.size());

            // 2. 构建消息
            List<ChatMessage> messages = new ArrayList<>();
            if (StrUtil.isNotBlank(context.getSystemPrompt())) {
                messages.add(new ChatMessage("system", context.getSystemPrompt()));
            }
            messages.add(new ChatMessage("user", extractQuery(run)));

            // 3. 前置中间件（中间件会往 tools 列表中添加新工具）
            middlewareChain.executeBefore(context, messages, tools);

            // 重新生成 openaiTools（包含中间件注册的新工具）
            openaiTools = toolRegistry.toOpenAITools(tools);
            log.info("[AgentEngine] After middleware: total tools={}", tools.size());

            // 4. 工具调用循环（maxSteps 可通过 context 配置）
            String finalAnswer = "";
            int maxIterationsSync = (context.getMaxSteps() != null && context.getMaxSteps() > 0)
                    ? context.getMaxSteps() : MAX_ITERATIONS;
            for (int round = 0; round < maxIterationsSync; round++) {
                totalIterations++;

                ChatResponse resp = llmService.chatWithTools(
                        mc.getModel(), messages, openaiTools,
                        mc.getTemperature(), mc.getApiUrl(), mc.getApiKey());

                middlewareChain.executeAfter(context, resp);

                if (!resp.hasToolCalls()) {
                    finalAnswer = resp.getContent();
                    break;
                }

                // Add assistant message
                ChatMessage assistantMsg = new ChatMessage();
                assistantMsg.setRole("assistant");
                assistantMsg.setContent(resp.getContent());
                assistantMsg.setToolCalls(resp.getToolCalls());
                messages.add(assistantMsg);

                // Execute tools
                for (ChatMessage.ToolCall tc : resp.getToolCalls()) {
                    // Middleware intercept
                    ToolCallInterceptor interceptor =
                            middlewareChain.getToolCallInterceptor(context, tc);
                    if (interceptor != null && interceptor.isSkip()) {
                        ChatMessage interceptorMsg = interceptor.getResultMessage();
                        if (interceptorMsg.getName() == null && tc.getFunction() != null) {
                            interceptorMsg.setName(tc.getFunction().getName());
                        }
                        messages.add(interceptorMsg);
                        continue;
                    }

                    ToolDefinition toolDef = findTool(tools, tc.getFunction().getName());
                    if (toolDef == null) {
                        ChatMessage toolNotFoundMsg = new ChatMessage("tool",
                                "Error: Tool not found: " + tc.getFunction().getName(), tc.getId());
                        toolNotFoundMsg.setName(tc.getFunction().getName());
                        messages.add(toolNotFoundMsg);
                        continue;
                    }

                    long toolStart = System.currentTimeMillis();
                    ToolResult toolResult = executeTool(toolDef, tc, context, run);
                    long toolDuration = System.currentTimeMillis() - toolStart;

                    String resultContent = toolResult.isSuccess()
                            ? toolResult.getOutput() : "Error: " + toolResult.getError();
                    ChatMessage toolResultMsg = new ChatMessage("tool",
                            truncate(resultContent, TOOL_OUTPUT_MAX), tc.getId());
                    toolResultMsg.setName(tc.getFunction().getName());
                    messages.add(toolResultMsg);

                    publishToolCallEvent(run.getId(), tc, toolResult, toolDuration);

                    AgentResult.ToolCallRecord record = new AgentResult.ToolCallRecord();
                    record.setToolName(tc.getFunction().getName());
                    record.setSuccess(toolResult.isSuccess());
                    record.setOutput(toolResult.getOutput());
                    record.setError(toolResult.getError());
                    record.setDurationMs((int) toolDuration);
                    toolCallRecords.add(record);
                }
            }

            AgentResult result = AgentResult.success(finalAnswer);
            result.setToolCallRecords(toolCallRecords);
            result.setTotalDurationMs((int)(System.currentTimeMillis() - totalStart));
            log.info("[AgentEngine] executeSync complete: iterations={}", totalIterations);
            return result;

        } catch (Exception e) {
            log.error("[AgentEngine] executeSync fatal error", e);
            AgentResult r = AgentResult.error("Agent execution error: " + e.getMessage());
            r.setTotalDurationMs((int)(System.currentTimeMillis() - totalStart));
            return r;
        }
    }

    // ==================== 流式消费方法 ====================

    /**
     * 消费 Flux&lt;StreamEvent&gt;，分类推送 SSE 事件，累积完整响应。
     * 使用 CountDownLatch 阻塞直到流完成（在 Executor 线程中调用）。
     */
    private LlmStreamResult consumeStream(
            Flux<StreamEvent> flux, SseEmitter emitter,
            StringBuilder fullAnswer, StringBuilder thinkingContent) throws InterruptedException {

        AtomicReference<String> finishReason = new AtomicReference<>("stop");
        List<ChatMessage.ToolCall> accumulatedToolCalls = new ArrayList<>();
        StringBuilder contentAccum = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        flux.subscribe(
                event -> {
                    try {
                        switch (event.getType()) {
                            case THINKING -> {
                                thinkingContent.append(event.getContent());
                                sendSseEvent(emitter, "thinking",
                                        Map.of("content", event.getContent()));
                            }
                            case CONTENT -> {
                                contentAccum.append(event.getContent());
                                fullAnswer.append(event.getContent());
                                sendSseEvent(emitter, "message",
                                        Map.of("content", event.getContent()));
                            }
                            case TOOL_CALL_DELTA -> {
                                mergeToolCallDelta(accumulatedToolCalls, event.getToolCall());
                            }
                            case FINISH -> {
                                finishReason.set(event.getFinishReason());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[AgentEngine] SSE send error during consumeStream: {}", e.getMessage());
                    }
                },
                error -> {
                    log.error("[AgentEngine] LLM stream error: {}", error.getMessage());
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
                        log.error("[AgentEngine] LLM stream error body: {}", wcre.getResponseBodyAsString());
                    }
                    // 打印当前累积的 toolCalls 用于诊断
                    if (!accumulatedToolCalls.isEmpty()) {
                        for (ChatMessage.ToolCall tc : accumulatedToolCalls) {
                            log.debug("[AgentEngine] On error, accumulated toolCall: id={}, name={}, args={}",
                                    tc.getId(),
                                    tc.getFunction() != null ? tc.getFunction().getName() : "null",
                                    tc.getFunction() != null ? tc.getFunction().getArguments() : "null");
                        }
                    }
                    errorRef.set(error);
                    latch.countDown();
                },
                () -> latch.countDown()
        );

        // 阻塞等待流完成
        long timeout = Math.max(60L, (long) gatewayTimeoutSeconds * 3);
        boolean completed = latch.await(timeout, TimeUnit.SECONDS);

        if (!completed) {
            log.warn("[AgentEngine] Stream timeout after {}s", timeout);
            throw new RuntimeException("LLM stream timeout after " + timeout + "s");
        }

        // 处理错误
        if (errorRef.get() != null) {
            throw new RuntimeException("LLM stream error: " + errorRef.get().getMessage());
        }

        // 构建 ChatResponse
        ChatResponse response = new ChatResponse();
        response.setContent(contentAccum.toString());
        response.setFinishReason(finishReason.get());
        response.setToolCalls(accumulatedToolCalls);

        return new LlmStreamResult(response, false);
    }

    // ==================== 工具执行 ====================

    /**
     * 执行单个工具调用（参数解析 + Schema 验证 + 执行器调用）。
     */
    private ToolResult executeTool(ToolDefinition toolDef, ChatMessage.ToolCall tc,
                                   BaseContext context, AgentRun run) {
        String argsJson = tc.getFunction().getArguments();

        // 解析参数
        Map<String, Object> args;
        try {
            args = objectMapper.readValue(argsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("[AgentEngine] Failed to parse arguments for {}: {}", tc.getFunction().getName(), e.getMessage());
            return ToolResult.error("Invalid arguments JSON: " + e.getMessage(), 0);
        }

        // JSON Schema 验证
        if (toolDef.getInputSchema() != null) {
            JsonSchemaValidator.ValidationResult vr =
                    JsonSchemaValidator.validate(args, toolDef.getInputSchema());
            if (!vr.valid()) {
                log.warn("[AgentEngine] Validation failed for {}: {}", tc.getFunction().getName(), vr.errors());
                return ToolResult.error("Validation failed: " + vr.errors(), 0);
            }
        }

        // 构建 ToolContext
        ToolContext toolCtx = new ToolContext();
        toolCtx.setUserId(context.getUid());
        toolCtx.setRunId(context.getRunId());
        if (run != null) {
            toolCtx.setRunId(run.getId());
        }
        toolCtx.setQuery(run != null ? extractQuery(run) : "");
        // 传递 extendions（如 fileThreadId 等）
        if (context.getRuntimeState() != null) {
            context.getRuntimeState().forEach((k, v) -> toolCtx.setExtension(k, v));
        }

        // 查找执行器并执行
        ToolExecutor executor = executorFactory.getExecutor(toolDef.getType());
        if (executor == null) {
            return ToolResult.error("No executor for type: " + toolDef.getType(), 0);
        }

        return executor.execute(toolDef, args, toolCtx);
    }

    // ==================== 辅助方法 ====================

    /**
     * 推送智能体状态（todos 等）到前端。
     */
    private void pushAgentState(BaseContext context, SseEmitter emitter) {
        Object todos = context.getRuntimeState().get("todos");
        Object artifacts = context.getRuntimeState().get("artifacts");
        if (todos != null || artifacts != null) {
            Map<String, Object> stateData = new LinkedHashMap<>();
            if (todos != null) stateData.put("todos", todos);
            if (artifacts != null) stateData.put("artifacts", artifacts);
            sendSseEvent(emitter, "agent_state", stateData);
        }
    }

    /**
     * 发布工具调用事件到 RunEventPublisher。
     */
    private void publishToolCallEvent(String runId, ChatMessage.ToolCall tc,
                                      ToolResult result, long duration) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("toolName", tc.getFunction().getName());
            payload.put("toolCallId", tc.getId());
            payload.put("success", result.isSuccess());
            payload.put("output", result.getOutput());
            payload.put("error", result.getError());
            payload.put("durationMs", duration);
            eventPublisher.pushEvent(runId, "tool_call", payload);
        } catch (Exception e) {
            log.debug("[AgentEngine] Event publish failed (non-critical): {}", e.getMessage());
        }
    }

    /**
     * 增量合并 tool_call 片段（index-based）。
     * StreamEvent 中的 toolCall 已经是 LlmService 累积后的完整对象，
     * 按 index 保持最新的版本。
     */
    private void mergeToolCallDelta(List<ChatMessage.ToolCall> accumulated,
                                     ChatMessage.ToolCall delta) {
        if (delta == null) return;
        // 按 index 查找并更新
        int index = accumulated.size();
        // 检查是否已存在相同 id 的 tool_call
        for (int i = 0; i < accumulated.size(); i++) {
            ChatMessage.ToolCall existing = accumulated.get(i);
            if (existing.getId() != null && existing.getId().equals(delta.getId())) {
                // 更新已存在的条目
                accumulated.set(i, delta);
                return;
            }
            if (existing.getFunction() != null && delta.getFunction() != null
                    && existing.getFunction().getName() != null
                    && existing.getFunction().getName().equals(delta.getFunction().getName())) {
                accumulated.set(i, delta);
                return;
            }
        }
        // 新条目
        accumulated.add(delta);
    }

    /**
     * 查找工具定义（按 name 或 toolId）。
     */
    private ToolDefinition findTool(List<ToolDefinition> tools, String name) {
        return tools.stream()
                .filter(t -> name.equals(t.getName()) || name.equals(t.getToolId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 从 AgentRun 的 inputPayload 中提取查询文本。
     */
    @SuppressWarnings("unchecked")
    private String extractQuery(AgentRun run) {
        if (run == null || run.getInputPayload() == null) return "";
        Object input = run.getInputPayload();
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

    /**
     * 截断文本到指定长度。
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "...(truncated)" : text;
    }

    /**
     * 打印当前消息列表摘要（用于调试 LLM 调用前的上下文）。
     */
    private void logMessagesSummary(List<ChatMessage> messages, int round) {
        log.info("[AgentEngine] Round {}: messageList size={}", round + 1, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            String contentPreview = msg.getContent() != null
                    ? (msg.getContent().length() > 100 ? msg.getContent().substring(0, 100) + "...(" + msg.getContent().length() + "c)" : msg.getContent())
                    : "null";
            String tcInfo = msg.getToolCalls() != null
                    ? "toolCalls=" + msg.getToolCalls().stream()
                        .map(tc -> tc.getId() + ":" + (tc.getFunction() != null ? tc.getFunction().getName() : "?"))
                        .reduce((a, b) -> a + "," + b).orElse("")
                    : "";
            String tcIdInfo = msg.getToolCallId() != null ? "toolCallId=" + msg.getToolCallId() : "";
            String nameInfo = msg.getName() != null ? "name=" + msg.getName() : "";
            log.debug("[AgentEngine]   msg[{}]: role={}, content=[{}], {}, {}, {}", i, msg.getRole(), contentPreview, tcInfo, tcIdInfo, nameInfo);
        }
    }

    /**
     * 安全发送 SSE 事件。
     */
    private void sendSseEvent(SseEmitter emitter, String eventName, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            log.warn("[AgentEngine] Failed to send SSE event '{}': {}", eventName, e.getMessage());
        }
    }
}
