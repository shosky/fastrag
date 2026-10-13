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
            if (apiUrl.endsWith("/chat/completions")) {
                // apiUrl 已是完整对话端点，直接使用，避免二次拼接出 404 路径
                webClient = WebClient.builder().build();
                uri = apiUrl;
            } else if (apiUrl.contains("/images/generations") || apiUrl.contains("/embeddings")
                    || apiUrl.contains("/rerank") || apiUrl.contains("/audio")) {
                // 非对话补全端点（图片/向量/重排/语音），直接给出可读错误而不是拼出 404 路径
                log.warn("chat() called with non-chat endpoint: {}", apiUrl);
                return "模型调用失败: 该模型接口地址(" + apiUrl + ")不是对话补全端点，不支持对话调用；"
                        + "请将模型用途改为 LLM 并把接口地址配置为服务根地址(如 https://api.siliconflow.cn/v1)，或在模型列表选择 LLM 模型";
            } else {
                webClient = WebClient.builder().baseUrl(apiUrl).build();
            }
            log.info("Using custom API URL: {}", apiUrl);
        }

        try {
            WebClient.RequestBodySpec requestSpec = webClient.post().uri(uri);

            // 如果提供了 API Key，添加 Authorization header
            String effKey = apiKey;
            if (effKey != null && !effKey.isEmpty()) {
                // 防御清洗：剪掉首尾空白/换行、去掉误粘贴的 "Bearer " 前缀，避免拼出 "Bearer Bearer sk-xx" 导致 401
                effKey = effKey.trim();
                if (effKey.toLowerCase().startsWith("bearer ")) effKey = effKey.substring(7).trim();
                if (!effKey.isEmpty()) requestSpec = requestSpec.header("Authorization", "Bearer " + effKey);
            }

            // 调试日志：打印真实等价 curl + 密钥特征，用于排查上游 401/404（含真实密钥，生产环境请降级或移除）
            try {
                String fullUrl = uri.startsWith("http") ? uri
                        : (apiUrl != null && !apiUrl.isEmpty() ? (apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl) + uri
                        : (defaultGatewayUrl != null ? defaultGatewayUrl.replaceAll("/$", "") : "") + uri);
                log.info("LLM 等价curl: curl -X POST '{}' -H 'Content-Type: application/json' -H 'Authorization: Bearer {}' --data '{}'",
                        fullUrl, effKey == null ? "(未携带密钥)" : effKey, objectMapper.writeValueAsString(req));
                if (apiKey != null) {
                    log.info("LLM 密钥特征: 原始长度={}, 含首尾空白={}, 误带Bearer前缀={}, 前4位={}",
                            apiKey.length(), !apiKey.equals(apiKey.trim()),
                            apiKey.trim().toLowerCase().startsWith("bearer "), apiKey.substring(0, Math.min(4, apiKey.length())));
                } else {
                    log.warn("LLM 密钥特征: 未配置任何密钥（api_key 与 api_key_ref 均为空）");
                }
            } catch (Exception ignore) { }

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
