package com.fastrag.ai.llm;

import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {
    private final WebClient aiWebClient;
    private final ObjectMapper objectMapper;

    public String chat(String model, List<ChatMessage> messages, double temperature) {
        ChatRequest req = new ChatRequest();
        req.setModel(model); req.setMessages(messages); req.setTemperature(temperature);
        try {
            String resp = aiWebClient.post().uri("/v1/chat/completions").bodyValue(req)
                    .retrieve().bodyToMono(String.class).block();
            JsonNode root = objectMapper.readTree(resp);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) { log.error("LLM chat failed", e); return "模型调用失败: " + e.getMessage(); }
    }

    public String chat(String model, String prompt) {
        return chat(model, List.of(new ChatMessage("user", prompt)), 0.7);
    }
}
