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
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {
    private final WebClient aiWebClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.gateway.url:http://localhost:11434}")
    private String defaultGatewayUrl;

    public String chat(String model, List<ChatMessage> messages, double temperature) {
        return chat(model, messages, temperature, null, null);
    }

    public String chat(String model, List<ChatMessage> messages, double temperature, String apiUrl, String apiKey) {
        ChatRequest req = new ChatRequest();
        req.setModel(model); req.setMessages(messages); req.setTemperature(temperature);

        WebClient webClient = aiWebClient;
        String uri = "/v1/chat/completions";

        // 如果提供了自定义 API URL，使用动态 WebClient
        if (apiUrl != null && !apiUrl.isEmpty()) {
            webClient = WebClient.builder().baseUrl(apiUrl).build();
            log.info("Using custom API URL: {}", apiUrl);
        }

        try {
            WebClient.RequestBodySpec requestSpec = webClient.post().uri(uri);

            // 如果提供了 API Key，添加 Authorization header
            if (apiKey != null && !apiKey.isEmpty()) {
                requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
            }

            String resp = requestSpec.bodyValue(req)
                    .retrieve().bodyToMono(String.class).block();
            JsonNode root = objectMapper.readTree(resp);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) { log.error("LLM chat failed", e); return "模型调用失败: " + e.getMessage(); }
    }

    public String chat(String model, String prompt) {
        return chat(model, List.of(new ChatMessage("user", prompt)), 0.7);
    }

    public String chat(String model, String prompt, String apiUrl, String apiKey) {
        return chat(model, List.of(new ChatMessage("user", prompt)), 0.7, apiUrl, apiKey);
    }
}
