package com.fastrag.ai.embedding;

/**
 * 文本向量化（Embedding）服务，负责将文本转换为高维向量表示。
 *
 * <p>本服务通过 OpenAI 兼容的 Embeddings API 将文本转为向量，是 RAG（检索增强生成）
 * 系统中文档索引和语义检索的核心基础服务。</p>
 *
 * <p>核心能力：
 * <ul>
 *   <li>单文本向量化（{@link #embed(String, String)}）</li>
 *   <li>批量文本向量化（{@link #embed(String, List)}）</li>
 *   <li>动态 API 路由（{@link #embed(String, List, String, String)}），支持指定自定义 API 地址和密钥</li>
 * </ul>
 *
 * <p>实现细节：
 * <ul>
 *   <li>注入 {@link com.fastrag.ai.config.AiGatewayConfig} 创建的 aiWebClient 和 aiHttpClient</li>
 *   <li>默认走 AI 网关的相对路径（/v1/embeddings），支持动态路由到外部 API</li>
 *   <li>请求体使用 JsonMapper 启用 ESCAPE_NON_ASCII，将中文等非 ASCII 字符转义为
 *       Unicode 转义序列（\\uXXXX），以兼容 SiliconFlow 等网关对 UTF-8 中文处理的 bug（返回 20015）</li>
 *   <li>动态路由时独立构建 WebClient 实例，避免 baseUrl 路径拼接问题</li>
 *   <li>请求超时 60 秒</li>
 * </ul>
 *
 * <p>依赖：aiWebClient（默认网关客户端）、aiHttpClient（支持代理的底层 HTTP 客户端）、
 * ObjectMapper（JSON 序列化）</p>
 */
import com.fastrag.ai.model.EmbeddingRequest;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {
    private final WebClient aiWebClient;
    private final HttpClient aiHttpClient;
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
                        .clientConnector(new ReactorClientHttpConnector(aiHttpClient))
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

            // SiliconFlow 等网关对原始 UTF-8 中文 input 返回 20015（服务端回归 bug），
            // 必须将非 ASCII 字符转义为 unicode 转义序列（\\uXXXX）形式发送；英文/数字不受影响
            String body = JsonMapper.builder()
                    .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
                    .build()
                    .writeValueAsString(req);

            String resp = requestSpec.contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
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
