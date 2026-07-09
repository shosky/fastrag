package com.fastrag.ai.llm;

import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {
    private final WebClient aiWebClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.gateway.url:http://localhost:11434}")
    private String defaultGatewayUrl;

    @Value("${ai.gateway.timeout:30}")
    private int gatewayTimeoutSeconds;

    public String chat(String model, List<ChatMessage> messages, double temperature) {
        return chat(model, messages, temperature, null, null);
    }

    public String chat(String model, List<ChatMessage> messages, double temperature, String apiUrl, String apiKey) {
        return doChat(model, messages, temperature, apiUrl, apiKey, false, null);
    }

    public String chat(String model, List<ChatMessage> messages, double temperature, String apiUrl, String apiKey, Boolean enableThinking) {
        return doChat(model, messages, temperature, apiUrl, apiKey, true, enableThinking);
    }

    /** 核心调用：支持流式/非流式，支持思考模式 */
    private String doChat(String model, List<ChatMessage> messages, double temperature,
                          String apiUrl, String apiKey, boolean stream, Boolean enableThinking) {
        ChatRequest req = new ChatRequest();
        req.setModel(model); req.setMessages(messages); req.setTemperature(temperature);
        req.setStream(stream);
        if (enableThinking != null && enableThinking) {
            req.setEnableThinking(true);
        }else{
            req.setEnableThinking(false);
        }

        WebClient webClient = aiWebClient;
        String uri = "/v1/chat/completions";

        // 如果提供了自定义 API URL，使用动态 WebClient
        if (apiUrl != null && !apiUrl.isEmpty()) {
            webClient = WebClient.builder().baseUrl(apiUrl).build();
            log.info("Using custom API URL: {}", apiUrl);
        }

        long t0 = System.currentTimeMillis();
        log.info("[LLM] doChat start: model={}, messages={}, stream={}, enableThinking={}",
                model, messages != null ? messages.size() : 0, stream, enableThinking);

        try {
            WebClient.RequestBodySpec requestSpec = webClient.post().uri(uri);
            if (apiKey != null && !apiKey.isEmpty()) {
                requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
            }

            String fullResponse;
            if (stream) {
                // 流式：bodyToFlux 已自动解码 SSE，每个 chunk 是已去前缀的 JSON 字符串
                // 总超时 = 首包超时 60s + 流式总时长上限 120s
                int perItemTimeout = gatewayTimeoutSeconds;
                int totalTimeout = gatewayTimeoutSeconds * 2;
                java.util.List<String> chunks = requestSpec.bodyValue(req)
                        .retrieve().bodyToFlux(String.class)
                        .timeout(Duration.ofSeconds(perItemTimeout))
                        .collectList()
                        .block(Duration.ofSeconds(totalTimeout));
                if (chunks == null) return "模型返回空响应";
                log.info("[LLM] stream chunks received: {}, first chunk: {}",
                        chunks.size(), chunks.stream().findFirst().orElse("").substring(0, Math.min(100, chunks.get(0).length())));
                StringBuilder sb = new StringBuilder();
                for (String chunk : chunks) {
                    String trimmed = chunk.trim();
                    if (trimmed.isEmpty() || "[DONE]".equals(trimmed)) continue;
                    try {
                        JsonNode root = objectMapper.readTree(trimmed);
                        JsonNode delta = root.path("choices").path(0).path("delta");
                        String content = delta.path("content").asText("");
                        sb.append(content);
                    } catch (Exception e) {
                        log.warn("[LLM] Failed to parse SSE chunk: {} | chunk={}", e.getMessage(), trimmed.substring(0, Math.min(200, trimmed.length())));
                    }
                }
                fullResponse = sb.toString();
            } else {
                // 非流式：直接获取完整 JSON 响应
                String resp = requestSpec.bodyValue(req)
                        .retrieve().bodyToMono(String.class)
                        .block(Duration.ofSeconds(gatewayTimeoutSeconds));
                if (resp == null) return "模型返回空响应";
                JsonNode root = objectMapper.readTree(resp);
                fullResponse = root.path("choices").path(0).path("message").path("content").asText();
                log.info("[LLM] non-stream response received: length={}", fullResponse != null ? fullResponse.length() : 0);
            }

            long elapsed = System.currentTimeMillis() - t0;
            boolean isBlank = fullResponse == null || fullResponse.isBlank();
            log.info("[LLM] doChat done: elapsed={}ms, responseLength={}, blank={}",
                    elapsed, fullResponse != null ? fullResponse.length() : 0, isBlank);
            return isBlank ? "模型返回空响应" : fullResponse;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t0;
            log.error("[LLM] doChat failed after {}ms: model={}, error={}", elapsed, model, e.getMessage(), e);
            return "模型调用失败: " + e.getMessage();
        }
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
}
