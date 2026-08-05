package com.fastrag.ai.rerank;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.*;

/**
 * Rerank 服务
 *
 * <p>调用兼容 Cohere / Jina 格式的 rerank API（POST /v1/rerank），
 * 对候选文档列表按 query 相关性重新排序。</p>
 *
 * <p>请求格式：{"model":"...","query":"...","documents":[...],"top_n":N}
 * <br>响应格式：{"results":[{"index":0,"relevance_score":0.95},...]}</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RerankService {
    private final WebClient aiWebClient;
    private final HttpClient aiHttpClient;
    private final ObjectMapper objectMapper;

    /**
     * 调用 Rerank API 对候选文档重排序（使用默认网关）
     */
    public List<Map<String, Object>> rerank(String model, String query, List<String> documents, int topK) {
        return rerank(model, query, documents, topK, null, null);
    }

    /**
     * 调用 Rerank API 对候选文档重排序（支持自定义 API 路由）
     *
     * @param model     模型名称
     * @param query     查询文本
     * @param documents 候选文档列表
     * @param topK      返回前 N 条
     * @param apiUrl    自定义 API 地址（为空时使用默认网关）
     * @param apiKey    API 密钥（为空时不添加认证头）
     * @return 按 relevance_score 降序排列的列表
     */
    public List<Map<String, Object>> rerank(String model, String query, List<String> documents, int topK,
                                            String apiUrl, String apiKey) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("model", model);
            req.put("query", query);
            req.put("documents", documents);
            req.put("top_n", Math.min(topK, documents.size()));

            WebClient.RequestBodySpec requestSpec;
            if (apiUrl != null && !apiUrl.isBlank()) {
                String fullUrl = apiUrl.endsWith("/") ? apiUrl + "v1/rerank" : apiUrl + "/v1/rerank";
                WebClient dynamicClient = WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(aiHttpClient))
                        .exchangeStrategies(ExchangeStrategies.builder()
                                .codecs(c -> c.defaultCodecs().maxInMemorySize(20 * 1024 * 1024))
                                .build())
                        .build();
                requestSpec = dynamicClient.post().uri(fullUrl);
            } else {
                requestSpec = aiWebClient.post().uri("/v1/rerank");
            }
            if (apiKey != null && !apiKey.isBlank()) {
                requestSpec = requestSpec.header("Authorization", "Bearer " + apiKey);
            }

            // 与 EmbeddingService 同理：SiliconFlow 对原始 UTF-8 中文返回 20015，
            // 请求体需将非 ASCII 字符转义为 unicode 转义序列（\\uXXXX）形式发送
            String body = JsonMapper.builder()
                    .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
                    .build()
                    .writeValueAsString(req);

            String resp = requestSpec.contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(60));

            if (resp == null || resp.isBlank()) {
                log.warn("[Rerank] Empty response");
                return List.of();
            }

            JsonNode root = objectMapper.readTree(resp);
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                log.warn("[Rerank] No 'results' array in response: {}", resp.substring(0, Math.min(200, resp.length())));
                return List.of();
            }

            List<Map<String, Object>> reranked = new ArrayList<>();
            for (JsonNode node : results) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("index", node.path("index").asInt(0));
                item.put("relevance_score", node.path("relevance_score").asDouble(0.0));
                reranked.add(item);
            }

            // 按 relevance_score 降序排列
            reranked.sort((a, b) -> Double.compare(
                    (Double) b.get("relevance_score"),
                    (Double) a.get("relevance_score")));

            log.info("[Rerank] model={}, documents={}, returned={}",
                    model, documents.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            log.error("[Rerank] Failed: model={}, query={}", model, query, e);
            return List.of();
        }
    }
}
