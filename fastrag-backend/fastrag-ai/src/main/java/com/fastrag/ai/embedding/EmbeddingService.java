package com.fastrag.ai.embedding;

import com.fastrag.ai.model.EmbeddingRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final WebClient aiWebClient;
    private final ObjectMapper objectMapper;

    public List<Float> embed(String model, String text) {
        return embed(model, List.of(text)).get(0);
    }

    public List<List<Float>> embed(String model, List<String> texts) {
        return embed(model, texts, null, null);
    }

    /**
     * 支持动态 API 路由的 Embedding 调用
     *
     * @param model  模型名称
     * @param texts  待向量化的文本列表
     * @param apiUrl 自定义 API 地址（为空时使用默认网关）
     * @param apiKey API 密钥（为空时不添加认证头）
     * @return 嵌入向量列表
     */
    public List<List<Float>> embed(String model, List<String> texts, String apiUrl, String apiKey) {
        EmbeddingRequest req = new EmbeddingRequest();
        req.setModel(model);
        req.setInput(texts);

        try {
            WebClient.RequestBodySpec requestSpec;
            if (apiUrl != null && !apiUrl.isBlank()) {
                // 动态路由：直接构造完整 URL，避免 baseUrl 路径被拼接
                String fullUrl = apiUrl.endsWith("/") ? apiUrl + "v1/embeddings" : apiUrl + "/v1/embeddings";
                WebClient dynamicClient = WebClient.builder()
                        .exchangeStrategies(ExchangeStrategies.builder()
                                .codecs(c -> c.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                                .build())
                        .build();
                requestSpec = dynamicClient.post().uri(fullUrl);
            } else {
                // 默认网关：使用 aiWebClient，路径相对
                requestSpec = aiWebClient.post().uri("/v1/embeddings");
            }
            if (apiKey != null && !apiKey.isBlank()) {
                requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
            }

            String resp = requestSpec.bodyValue(req)
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(60));
            JsonNode root = objectMapper.readTree(resp);
            List<List<Float>> embeddings = new ArrayList<>();
            for (JsonNode item : root.path("data")) {
                List<Float> vec = new ArrayList<>();
                for (JsonNode v : item.path("embedding")) vec.add(v.floatValue());
                embeddings.add(vec);
            }
            log.info("Embedding success: {} texts -> {} vectors, model={}, apiUrl={}",
                    texts.size(), embeddings.size(), model, apiUrl != null ? apiUrl : "default");
            return embeddings;
        } catch (Exception e) {
            log.error("Embedding failed, model={}, apiUrl={}", model, apiUrl, e);
            throw new RuntimeException("向量化失败", e);
        }
    }
}
