package com.fastrag.ai.llm;

/**
 * LLM（大语言模型）服务，封装与各类大模型交互的统一调用层。
 *
 * <p>本服务是 fastrag-ai 模块最核心的服务类，通过 OpenAI 兼容的 Chat Completions API
 * 与各类大语言模型通信，支持对话、流式输出、工具调用（Function Calling）、
 * 思考模式（Thinking）等多种调用模式。</p>
 *
 * <p>核心能力：
 * <ul>
 *   <li><b>非流式对话</b> - 同步调用 LLM 获取完整回复（{@link #chat} 系列方法）</li>
 *   <li><b>流式对话</b> - 返回 Flux 逐 chunk 推送文本增量（{@link #streamChat}）</li>
 *   <li><b>流式 + 工具调用</b> - 返回 Flux 逐事件推送，区分 thinking/content/tool_call_delta/finish
 *       （{@link #streamChatWithTools}）</li>
 *   <li><b>带 Tool Calling 的调用</b> - 支持 OpenAI Function Calling 格式的工具定义和调用
 *       （{@link #chatWithTools}、{@link #chatWithToolsStream}）</li>
 *   <li><b>图谱抽取专用</b> - 支持自定义超时和 max_tokens 的流式调用
 *       （{@link #chatWithTimeout}），避免长输出截断</li>
 * </ul>
 *
 * <p>实现细节：
 * <ul>
 *   <li>注入 {@link com.fastrag.ai.config.AiGatewayConfig} 创建的 aiWebClient 和 aiHttpClient</li>
 *   <li>支持动态 API 路由：通过 apiUrl/apiUrl 参数指定自定义网关地址，否则使用默认网关</li>
 *   <li>流式 tool_calls 采用增量合并策略：按 index 累积 id/name/arguments 片段</li>
 *   <li>Thinking 提取双策略：优先从 delta.reasoning_content 提取（DeepSeek 格式），
 *       流结束后回退正则提取 &lt;think&gt; 标签内容（Qwen3 格式）</li>
 *   <li>超时配置：默认请求超时由 {@code ai.gateway.timeout} 控制（默认 30 秒），
 *       流式总超时为 2 倍请求超时</li>
 * </ul>
 *
 * <p>配置项（application.yml）：
 * <ul>
 *   <li>{@code ai.gateway.url} - 默认网关地址</li>
 *   <li>{@code ai.gateway.timeout} - 网关超时秒数，默认 30</li>
 * </ul>
 *
 * <p>依赖：aiWebClient（默认网关 WebClient）、aiHttpClient（代理 HTTP 客户端）、
 * ObjectMapper（JSON 解析）</p>
 */
import com.fastrag.ai.model.ChatChunk;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatRequest;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.ai.model.StreamEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {
    private final WebClient aiWebClient;
    private final HttpClient aiHttpClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.gateway.url:http://localhost:11434}")
    private String defaultGatewayUrl;

    @Value("${ai.gateway.timeout:30}")
    private int gatewayTimeoutSeconds;

    // ==================== 原有方法（保持兼容） ====================

    public String chat(String model, List<ChatMessage> messages, double temperature) {
        return chat(model, messages, temperature, null, null);
    }

    public String chat(String model, List<ChatMessage> messages, double temperature, String apiUrl, String apiKey) {
        return doChat(model, messages, temperature, apiUrl, apiKey, false, null, null);
    }

    public String chat(String model, List<ChatMessage> messages, double temperature, String apiUrl, String apiKey, Boolean enableThinking) {
        return doChat(model, messages, temperature, apiUrl, apiKey, true, enableThinking, null);
    }

    public String chat(String model, String prompt) {
        return chat(model, List.of(new ChatMessage("user", prompt)), 0.7);
    }

    public String chat(String model, String prompt, String apiUrl, String apiKey) {
        return chat(model, List.of(new ChatMessage("user", prompt)), 0.7, apiUrl, apiKey);
    }

    public String chat(String model, String prompt, String apiUrl, String apiKey, Boolean enableThinking) {
        return chat(model, List.of(new ChatMessage("user", prompt)), 0.7, apiUrl, apiKey, enableThinking);
    }

    /**
     * 图谱抽取专用：支持自定义超时和 thinking 控制。
     * 使用流式 SSE 收集（per-chunk 超时 + 总超时），避免 LLM 响应慢时直接断连。
     *
     * @param model           模型标识
     * @param prompt          用户提示
     * @param apiUrl          API 基础 URL
     * @param apiKey          API 密钥
     * @param enableThinking  是否启用思考模式（图谱抽取建议 false）
     * @param timeoutSeconds  总超时秒数（per-chunk 超时 = timeoutSeconds / 2）
     */
    public String chatWithTimeout(String model, String prompt, String apiUrl, String apiKey,
                                   Boolean enableThinking, int timeoutSeconds) {
        return chatWithTimeout(model, prompt, apiUrl, apiKey, enableThinking, timeoutSeconds, 2048);
    }

    /**
     * 流式 + 自定义超时 + 自定义 max_tokens（基准生成等长输出场景使用，
     * 默认 2048 不足以容纳 10 个中文问答对，输出截断会导致 JSON 解析失败）。
     */
    public String chatWithTimeout(String model, String prompt, String apiUrl, String apiKey,
                                   Boolean enableThinking, int timeoutSeconds, int maxTokens) {
        return doChatWithTimeout(model, List.of(new ChatMessage("user", prompt)), 0.7,
                apiUrl, apiKey, enableThinking, timeoutSeconds, maxTokens);
    }

    // ==================== 带 Tool Calling 的调用 ====================

    /**
     * 带 tools 的非流式调用，返回结构化响应（可能包含 tool_calls）
     */
    public ChatResponse chatWithTools(String model, List<ChatMessage> messages,
                                       List<ChatRequest.ToolDefinition> tools,
                                       double temperature, String apiUrl, String apiKey) {
        return doChatWithTools(model, messages, tools, temperature, apiUrl, apiKey, false);
    }

    /**
     * 带 tools 的流式调用（收集完整响应后返回结构化结果）
     */
    public ChatResponse chatWithToolsStream(String model, List<ChatMessage> messages,
                                             List<ChatRequest.ToolDefinition> tools,
                                             double temperature, String apiUrl, String apiKey) {
        return doChatWithTools(model, messages, tools, temperature, apiUrl, apiKey, true);
    }

    // ==================== 真正的流式输出（返回 Flux，逐 chunk 推送） ====================

    /**
     * 纯文本流式对话，返回 Flux&lt;ChatChunk&gt;，每个元素是一个文本增量。
     * 不带 tools，适用于应用中心普通对话场景。
     */
    public Flux<ChatChunk> streamChat(String model, List<ChatMessage> messages,
                                       double temperature) {
        return streamChat(model, messages, temperature, null, null);
    }

    /**
     * 纯文本流式对话，支持自定义 API 地址和密钥。
     */
    public Flux<ChatChunk> streamChat(String model, List<ChatMessage> messages,
                                       double temperature, String apiUrl, String apiKey) {
        ChatRequest req = new ChatRequest();
        req.setModel(model);
        req.setMessages(messages);
        req.setTemperature(temperature);
        req.setStream(true);
        req.setEnableThinking(false);

        WebClient webClient = aiWebClient;
        String uri = "/v1/chat/completions";
        if (apiUrl != null && !apiUrl.isEmpty()) {
            webClient = WebClient.builder()
                    .baseUrl(apiUrl)
                    .clientConnector(new ReactorClientHttpConnector(aiHttpClient))
                    .build();
        }

        log.info("[LLM] streamChat start: model={}, messages={}", model, messages != null ? messages.size() : 0);

        WebClient.RequestBodySpec requestSpec = webClient.post().uri(uri);
        if (apiKey != null && !apiKey.isEmpty()) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
        }

        // 累积 tool_calls 的状态
        List<ChatMessage.ToolCall> toolCallAccum = new ArrayList<>();

        return requestSpec.bodyValue(req)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(gatewayTimeoutSeconds * 2))
                .filter(chunk -> {
                    String trimmed = chunk.trim();
                    return !trimmed.isEmpty() && !"[DONE]".equals(trimmed);
                })
                .map(chunk -> {
                    try {
                        JsonNode root = objectMapper.readTree(chunk.trim());
                        JsonNode delta = root.path("choices").path(0).path("delta");

                        String content = delta.path("content").asText("");
                        String finishReason = root.path("choices").path(0).path("finish_reason").asText("");
                        boolean hasToolCalls = delta.path("tool_calls").isArray() && delta.path("tool_calls").size() > 0;

                        // 累积 tool_calls
                        if (hasToolCalls) {
                            JsonNode tcNodes = delta.path("tool_calls");
                            for (JsonNode tcNode : tcNodes) {
                                int index = tcNode.path("index").asInt(0);
                                while (toolCallAccum.size() <= index) {
                                    toolCallAccum.add(new ChatMessage.ToolCall());
                                }
                                ChatMessage.ToolCall tc = toolCallAccum.get(index);
                                if (tc.getId() == null || tc.getId().isEmpty()) {
                                    tc.setId(tcNode.path("id").asText(""));
                                }
                                if (tc.getFunction() == null) {
                                    tc.setFunction(new ChatMessage.FunctionCall());
                                }
                                ChatMessage.FunctionCall fn = tc.getFunction();
                                if (fn.getName() == null || fn.getName().isEmpty()) {
                                    fn.setName(tcNode.path("function").path("name").asText(""));
                                }
                                String argsFragment = tcNode.path("function").path("arguments").asText("");
                                if (argsFragment != null && !argsFragment.isEmpty()) {
                                    String existing = fn.getArguments() != null ? fn.getArguments() : "";
                                    fn.setArguments(existing + argsFragment);
                                }
                            }
                        }

                        ChatChunk chatChunk = new ChatChunk();
                        if (!content.isEmpty()) {
                            chatChunk.setContent(content);
                        }
                        if (!finishReason.isEmpty()) {
                            chatChunk.setFinishReason(finishReason);
                        }
                        if (!toolCallAccum.isEmpty()) {
                            chatChunk.setToolCalls(new ArrayList<>(toolCallAccum));
                        }
                        return chatChunk;

                    } catch (Exception e) {
                        log.warn("[LLM] Failed to parse SSE chunk: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .doOnComplete(() -> log.info("[LLM] streamChat complete, toolCalls={}", toolCallAccum.size()))
                .doOnError(e -> log.error("[LLM] streamChat error: {}", e.getMessage()));
    }

    // ==================== 流式 + 工具 + Thinking (新增) ====================

    /**
     * 流式 + 工具调用 + 思考过程提取。
     * 返回 Flux&lt;StreamEvent&gt;，区分 thinking / content / tool_call_delta / finish。
     *
     * <p>Thinking 提取策略：
     * <ol>
     *   <li>优先: delta.reasoning_content 字段（DeepSeek 格式）</li>
     *   <li>流结束后回退: 对累积 content 做 &lt;think.../&gt; 正则提取（Qwen3 格式）</li>
     * </ol>
     *
     * @param model          模型标识
     * @param messages       消息列表
     * @param tools          OpenAI 工具定义列表（可为 null）
     * @param temperature    温度
     * @param apiUrl         API 基础 URL（可为 null，使用默认）
     * @param apiKey         API 密钥（可为 null）
     * @param enableThinking 是否启用思考模式
     */
    public Flux<StreamEvent> streamChatWithTools(
            String model, List<ChatMessage> messages,
            List<ChatRequest.ToolDefinition> tools,
            double temperature, String apiUrl, String apiKey,
            boolean enableThinking) {

        ChatRequest req = new ChatRequest();
        req.setModel(model);
        req.setMessages(messages);
        req.setTemperature(temperature);
        req.setStream(true);
        req.setEnableThinking(enableThinking);
        if (tools != null && !tools.isEmpty()) {
            req.setTools(tools);
        }
        req.setToolChoice("auto");

        WebClient webClient = aiWebClient;
        String uri = "/v1/chat/completions";
        if (apiUrl != null && !apiUrl.isEmpty()) {
            webClient = WebClient.builder()
                    .baseUrl(apiUrl)
                    .clientConnector(new ReactorClientHttpConnector(aiHttpClient))
                    .build();
        }

        WebClient.RequestBodySpec requestSpec = webClient.post().uri(uri);
        if (apiKey != null && !apiKey.isEmpty()) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
        }

        // 累积 tool_calls 的状态（线程安全）
        List<ChatMessage.ToolCall> toolCallAccum = new ArrayList<>();

        // 累积 content（用于流结束后的 <think/> 正则回退）
        AtomicReference<StringBuilder> contentAccumRef = new AtomicReference<>(new StringBuilder());

        log.info("[LLM] streamChatWithTools start: model={}, enableThinking={}, tools={}",
                model, enableThinking, tools != null ? tools.size() : 0);

        // 打印请求详情
        try {
            String reqJson = objectMapper.writeValueAsString(req);
            String msgRoles = req.getMessages() != null
                    ? req.getMessages().stream().map(ChatMessage::getRole).reduce((a, b) -> a + "," + b).orElse("")
                    : "";
            log.info("[LLM] streamChatWithTools request: model={}, msgRoles=[{}], msgCount={}, tools={}, bodyLen={}",
                    req.getModel(), msgRoles,
                    req.getMessages() != null ? req.getMessages().size() : 0,
                    tools != null ? tools.size() : 0,
                    reqJson.length());
            log.debug("[LLM] streamChatWithTools request body: {}", reqJson);
            // 打印每条消息的摘要（role + content前200字符 + toolCalls信息）
            if (req.getMessages() != null) {
                for (int i = 0; i < req.getMessages().size(); i++) {
                    ChatMessage msg = req.getMessages().get(i);
                    String contentPreview = msg.getContent() != null
                            ? (msg.getContent().length() > 200 ? msg.getContent().substring(0, 200) + "...(" + msg.getContent().length() + "chars)" : msg.getContent())
                            : "null";
                    String toolCallsInfo = msg.getToolCalls() != null
                            ? "toolCalls=" + msg.getToolCalls().stream()
                                .map(tc -> tc.getId() + ":" + (tc.getFunction() != null ? tc.getFunction().getName() : "null"))
                                .reduce((a, b) -> a + "," + b).orElse("")
                            : "noToolCalls";
                    String toolCallIdInfo = msg.getToolCallId() != null ? "toolCallId=" + msg.getToolCallId() : "";
                    log.debug("[LLM]   msg[{}]: role={}, content={}, {}, {}", i, msg.getRole(), contentPreview, toolCallsInfo, toolCallIdInfo);
                }
            }
        } catch (Exception e) {
            log.warn("[LLM] Failed to log request details: {}", e.getMessage());
        }

        return requestSpec.bodyValue(req)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(gatewayTimeoutSeconds * 2))
                .filter(chunk -> {
                    String trimmed = chunk.trim();
                    return !trimmed.isEmpty() && !"[DONE]".equals(trimmed);
                })
                .flatMap(chunk -> parseChunkToStreamEvents(chunk, toolCallAccum, contentAccumRef, enableThinking))
                .filter(Objects::nonNull)
                .doOnComplete(() -> {
                    // 流结束后的 <think/> 正则回退（Qwen3 兼容）
                    if (enableThinking) {
                        extractThinkTagsFallback(contentAccumRef.get());
                    }
                    log.info("[LLM] streamChatWithTools complete, toolCalls={}", toolCallAccum.size());
                })
                .doOnError(e -> {
                    // 打印 API 返回的详细错误信息
                    if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
                        log.error("[LLM] streamChatWithTools error: {}, body={}",
                                e.getMessage(), wcre.getResponseBodyAsString());
                    } else {
                        log.error("[LLM] streamChatWithTools error: {}", e.getMessage());
                    }
                });
    }

    /**
     * 解析单个 SSE chunk 为 0..N 个 StreamEvent。
     */
    private Flux<StreamEvent> parseChunkToStreamEvents(
            String chunk, List<ChatMessage.ToolCall> toolCallAccum,
            AtomicReference<StringBuilder> contentAccumRef, boolean enableThinking) {
        List<StreamEvent> events = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(chunk.trim());
            JsonNode delta = root.path("choices").path(0).path("delta");

            // 1. 提取 reasoning_content（DeepSeek 格式）
            if (enableThinking) {
                String reasoningContent = delta.path("reasoning_content").asText("");
                if (!reasoningContent.isEmpty()) {
                    events.add(StreamEvent.thinking(reasoningContent));
                }
            }

            // 2. 提取 content（文本增量）
            String content = delta.path("content").asText("");
            if (!content.isEmpty()) {
                contentAccumRef.get().append(content);
                events.add(StreamEvent.content(content));
            }

            // 3. 提取 tool_calls（增量合并）
            if (delta.path("tool_calls").isArray() && delta.path("tool_calls").size() > 0) {
                JsonNode tcNodes = delta.path("tool_calls");
                for (JsonNode tcNode : tcNodes) {
                    int index = tcNode.path("index").asInt(0);
                    while (toolCallAccum.size() <= index) {
                        toolCallAccum.add(new ChatMessage.ToolCall());
                    }
                    ChatMessage.ToolCall tc = toolCallAccum.get(index);

                    if (tc.getId() == null || tc.getId().isEmpty()) {
                        tc.setId(tcNode.path("id").asText(""));
                    }
                    if (tc.getFunction() == null) {
                        tc.setFunction(new ChatMessage.FunctionCall());
                    }
                    ChatMessage.FunctionCall fn = tc.getFunction();
                    if (fn.getName() == null || fn.getName().isEmpty()) {
                        fn.setName(tcNode.path("function").path("name").asText(""));
                    }
                    String argsFragment = tcNode.path("function").path("arguments").asText("");
                    if (argsFragment != null && !argsFragment.isEmpty()) {
                        String existing = fn.getArguments() != null ? fn.getArguments() : "";
                        fn.setArguments(existing + argsFragment);
                    }

                    events.add(StreamEvent.toolCallDelta(tc));
                }
            }

            // 4. 提取 finish_reason
            String finishReason = root.path("choices").path(0).path("finish_reason").asText("");
            if (!finishReason.isEmpty()) {
                events.add(StreamEvent.finish(finishReason));
            }

        } catch (Exception e) {
            log.warn("[LLM] Failed to parse SSE chunk in streamChatWithTools: {}", e.getMessage());
        }
        return Flux.fromIterable(events);
    }

    /**
     * 对累积的 content 做 &lt;think.../&gt; 正则提取（Qwen3 兼容）。
     * 仅在流结束后调用一次。标记是否有 thinking 标签。
     */
    private void extractThinkTagsFallback(StringBuilder contentAccum) {
        if (contentAccum == null || contentAccum.length() == 0) return;

        String content = contentAccum.toString();
        Pattern thinkPattern = Pattern.compile("<think[^>]*>([\\s\\S]*?)(?:</think|$)", Pattern.DOTALL);
        Matcher matcher = thinkPattern.matcher(content);

        if (matcher.find()) {
            String thinkingPart = matcher.group(1).trim();
            if (!thinkingPart.isEmpty()) {
                log.info("[LLM] Detected <think/> tags in content: {} chars thinking extracted", thinkingPart.length());
            }
        }
    }

    // ==================== 内部实现 ====================

    /** 核心调用：兼容原有逻辑（无 tools） */
    private String doChat(String model, List<ChatMessage> messages, double temperature,
                          String apiUrl, String apiKey, boolean stream, Boolean enableThinking,
                          List<ChatRequest.ToolDefinition> tools) {
        ChatResponse resp = doChatInternal(model, messages, temperature, apiUrl, apiKey,
                stream, enableThinking, tools, "auto");
        return resp.getContent() != null ? resp.getContent() : "模型返回空响应";
    }

    /** 带 tools 的调用 */
    private ChatResponse doChatWithTools(String model, List<ChatMessage> messages,
                                          List<ChatRequest.ToolDefinition> tools,
                                          double temperature, String apiUrl, String apiKey, boolean stream) {
        return doChatInternal(model, messages, temperature, apiUrl, apiKey,
                stream, null, tools, "auto");
    }

    /** 统一的核心调用方法 */
    private ChatResponse doChatInternal(String model, List<ChatMessage> messages, double temperature,
                                         String apiUrl, String apiKey, boolean stream,
                                         Boolean enableThinking,
                                         List<ChatRequest.ToolDefinition> tools,
                                         String toolChoice) {
        ChatRequest req = new ChatRequest();
        req.setModel(model);
        req.setMessages(messages);
        req.setTemperature(temperature);
        req.setStream(stream);
        req.setEnableThinking(enableThinking != null ? enableThinking : false);
        if (tools != null && !tools.isEmpty()) {
            req.setTools(tools);
        }
        if (toolChoice != null) {
            req.setToolChoice(toolChoice);
        }

        WebClient webClient = aiWebClient;
        String uri = "/v1/chat/completions";

        if (apiUrl != null && !apiUrl.isEmpty()) {
            webClient = WebClient.builder()
                    .baseUrl(apiUrl)
                    .clientConnector(new ReactorClientHttpConnector(aiHttpClient))
                    .build();
            log.info("[LLM] Using custom API URL: {}", apiUrl);
        }

        long t0 = System.currentTimeMillis();
        log.info("[LLM] doChatInternal start: model={}, messages={}, stream={}, tools={}",
                model, messages != null ? messages.size() : 0, stream, tools != null ? tools.size() : 0);

        // 打印完整请求体
        try {
            String reqJson = objectMapper.writeValueAsString(req);
            log.debug("[LLM] doChatInternal request body: {}", reqJson);
            if (req.getMessages() != null) {
                for (int i = 0; i < req.getMessages().size(); i++) {
                    ChatMessage msg = req.getMessages().get(i);
                    String contentPreview = msg.getContent() != null
                            ? (msg.getContent().length() > 200 ? msg.getContent().substring(0, 200) + "...(" + msg.getContent().length() + "chars)" : msg.getContent())
                            : "null";
                    String tcInfo = msg.getToolCalls() != null
                            ? "toolCalls=" + msg.getToolCalls().size()
                            : "noToolCalls";
                    String tcIdInfo = msg.getToolCallId() != null ? "toolCallId=" + msg.getToolCallId() : "";
                    log.debug("[LLM]   msg[{}]: role={}, content={}, {}, {}", i, msg.getRole(), contentPreview, tcInfo, tcIdInfo);
                }
            }
        } catch (Exception e) {
            log.warn("[LLM] Failed to log request details: {}", e.getMessage());
        }

        try {
            WebClient.RequestBodySpec requestSpec = webClient.post().uri(uri);
            if (apiKey != null && !apiKey.isEmpty()) {
                requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
            }

            if (stream) {
                return handleStreamResponse(requestSpec, req, t0);
            } else {
                return handleNonStreamResponse(requestSpec, req, t0);
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t0;
            log.error("[LLM] doChatInternal failed after {}ms: model={}, error={}", elapsed, model, e.getMessage(), e);
            return ChatResponse.text("模型调用失败: " + e.getMessage());
        }
    }

    /** 非流式响应处理 */
    private ChatResponse handleNonStreamResponse(WebClient.RequestBodySpec requestSpec,
                                                  ChatRequest req, long t0) throws Exception {
        String resp = requestSpec.bodyValue(req)
                .retrieve().bodyToMono(String.class)
                .block(Duration.ofSeconds(gatewayTimeoutSeconds));

        if (resp == null) return ChatResponse.text("模型返回空响应");

        JsonNode root = objectMapper.readTree(resp);
        long elapsed = System.currentTimeMillis() - t0;

        // 打印完整响应
        log.debug("[LLM] non-stream raw response ({}ms): {}", elapsed, resp);

        // 判断是否为旧格式响应（仅有 content）
        JsonNode message = root.path("choices").path(0).path("message");
        if (message.path("tool_calls").isMissingNode() || message.path("tool_calls").isNull()) {
            String content = message.path("content").asText("");
            // 提取 token usage
            JsonNode usage = root.path("usage");
            String usageInfo = usage.isMissingNode() ? "N/A"
                    : String.format("prompt=%d, completion=%d, total=%d",
                    usage.path("prompt_tokens").asInt(0),
                    usage.path("completion_tokens").asInt(0),
                    usage.path("total_tokens").asInt(0));
            log.info("[LLM] non-stream response done: elapsed={}ms, length={}, usage={}", elapsed, content.length(), usageInfo);
            log.debug("[LLM] non-stream response content: {}", content.length() > 500 ? content.substring(0, 500) + "...(" + content.length() + "chars)" : content);
            return ChatResponse.text(content);
        }

        // 解析结构化响应（含 tool_calls）
        ChatResponse chatResponse = ChatResponse.parse(root);
        log.info("[LLM] non-stream response done: elapsed={}ms, finishReason={}, toolCalls={}",
                elapsed, chatResponse.getFinishReason(), chatResponse.getToolCalls().size());
        return chatResponse;
    }

    /** 流式响应处理：收集所有 chunks，合并后解析 */
    private ChatResponse handleStreamResponse(WebClient.RequestBodySpec requestSpec,
                                              ChatRequest req, long t0) throws Exception {
        int perItemTimeout = gatewayTimeoutSeconds;
        int totalTimeout = gatewayTimeoutSeconds * 2;

        java.util.List<String> chunks = requestSpec.bodyValue(req)
                .retrieve().bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(perItemTimeout))
                .collectList()
                .block(Duration.ofSeconds(totalTimeout));

        if (chunks == null || chunks.isEmpty()) return ChatResponse.text("模型返回空响应");

        log.info("[LLM] stream chunks received: {}", chunks.size());
        // 打印第一个 chunk 用于调试
        log.debug("[LLM] stream first chunk: {}", chunks.get(0).trim());

        StringBuilder contentSb = new StringBuilder();
        String finishReason = "stop";
        java.util.List<ChatMessage.ToolCall> toolCalls = new java.util.ArrayList<>();

        for (String chunk : chunks) {
            String trimmed = chunk.trim();
            if (trimmed.isEmpty() || "[DONE]".equals(trimmed)) continue;
            try {
                JsonNode root = objectMapper.readTree(trimmed);
                JsonNode delta = root.path("choices").path(0).path("delta");

                // 文本内容
                String content = delta.path("content").asText("");
                contentSb.append(content);

                // 流式 tool_calls：增量合并
                JsonNode tcNodes = delta.path("tool_calls");
                if (tcNodes.isArray()) {
                    for (JsonNode tcNode : tcNodes) {
                        int index = tcNode.path("index").asInt(0);
                        // 确保 list 足够大
                        while (toolCalls.size() <= index) {
                            toolCalls.add(new ChatMessage.ToolCall());
                        }
                        ChatMessage.ToolCall tc = toolCalls.get(index);

                        if (tc.getId() == null || tc.getId().isEmpty()) {
                            tc.setId(tcNode.path("id").asText(""));
                        }

                        JsonNode fnNode = tcNode.path("function");
                        if (tc.getFunction() == null) {
                            tc.setFunction(new ChatMessage.FunctionCall());
                        }
                        ChatMessage.FunctionCall fn = tc.getFunction();
                        if (fn.getName() == null || fn.getName().isEmpty()) {
                            fn.setName(fnNode.path("name").asText(""));
                        }
                        // arguments 是增量 JSON fragment，拼接
                        String argsFragment = fnNode.path("arguments").asText("");
                        if (argsFragment != null && !argsFragment.isEmpty()) {
                            String existing = fn.getArguments() != null ? fn.getArguments() : "";
                            fn.setArguments(existing + argsFragment);
                        }
                    }
                }

                String fr = root.path("choices").path(0).path("finish_reason").asText("");
                if (!fr.isEmpty()) finishReason = fr;
            } catch (Exception e) {
                log.warn("[LLM] Failed to parse SSE chunk: {}", e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setContent(contentSb.toString());
        chatResponse.setFinishReason(finishReason);
        chatResponse.setToolCalls(toolCalls);

        log.info("[LLM] stream response done: elapsed={}ms, finishReason={}, toolCalls={}, contentLen={}",
                elapsed, finishReason, toolCalls.size(), contentSb.length());
        log.debug("[LLM] stream response content: {}",
                contentSb.length() > 500 ? contentSb.substring(0, 500) + "...(" + contentSb.length() + "chars)" : contentSb.toString());
        // 打印 tool_calls 详情
        for (ChatMessage.ToolCall tc : toolCalls) {
            log.debug("[LLM] stream response toolCall: id={}, name={}, args={}",
                    tc.getId(),
                    tc.getFunction() != null ? tc.getFunction().getName() : "null",
                    tc.getFunction() != null ? tc.getFunction().getArguments() : "null");
        }
        return chatResponse;
    }

    /**
     * 带自定义超时的流式调用（图谱抽取专用）。
     * 与 handleStreamResponse 逻辑相同，但超时由调用方传入，而非固定使用 gatewayTimeoutSeconds。
     */
    private String doChatWithTimeout(String model, List<ChatMessage> messages, double temperature,
                                     String apiUrl, String apiKey, Boolean enableThinking,
                                     int timeoutSeconds, int maxTokens) {
        ChatRequest req = new ChatRequest();
        req.setModel(model);
        req.setMessages(messages);
        req.setTemperature(temperature);
        req.setStream(true);
        req.setEnableThinking(enableThinking != null ? enableThinking : false);
        req.setMaxTokens(maxTokens);

        WebClient webClient = aiWebClient;
        String uri = "/v1/chat/completions";
        if (apiUrl != null && !apiUrl.isEmpty()) {
            webClient = WebClient.builder()
                    .baseUrl(apiUrl)
                    .clientConnector(new ReactorClientHttpConnector(aiHttpClient))
                    .build();
        }

        WebClient.RequestBodySpec requestSpec = webClient.post().uri(uri);
        if (apiKey != null && !apiKey.isEmpty()) {
            requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
        }

        long t0 = System.currentTimeMillis();
        int perItemTimeout = Math.max(10, timeoutSeconds / 2);

        log.info("[LLM] chatWithTimeout start: model={}, enableThinking={}, perItem={}s, total={}s",
                model, enableThinking, perItemTimeout, timeoutSeconds);

        try {
            java.util.List<String> chunks = requestSpec.bodyValue(req)
                    .retrieve().bodyToFlux(String.class)
                    .timeout(Duration.ofSeconds(perItemTimeout))
                    .collectList()
                    .block(Duration.ofSeconds(timeoutSeconds));

            if (chunks == null || chunks.isEmpty()) {
                log.warn("[LLM] chatWithTimeout: empty response after {}ms", System.currentTimeMillis() - t0);
                return null;
            }

            StringBuilder contentSb = new StringBuilder();
            for (String chunk : chunks) {
                String trimmed = chunk.trim();
                if (trimmed.isEmpty() || "[DONE]".equals(trimmed)) continue;
                try {
                    JsonNode root = objectMapper.readTree(trimmed);
                    JsonNode delta = root.path("choices").path(0).path("delta");
                    contentSb.append(delta.path("content").asText(""));
                } catch (Exception e) {
                    log.warn("[LLM] chatWithTimeout: failed to parse SSE chunk: {}", e.getMessage());
                }
            }

            long elapsed = System.currentTimeMillis() - t0;
            log.info("[LLM] chatWithTimeout done: elapsed={}ms, contentLen={}", elapsed, contentSb.length());
            return contentSb.toString();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t0;
            log.error("[LLM] chatWithTimeout failed after {}ms: model={}, error={}", elapsed, model, e.getMessage());
            throw new RuntimeException("LLM call timeout (" + timeoutSeconds + "s) or error: " + e.getMessage(), e);
        }
    }
}
