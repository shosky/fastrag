package com.fastrag.ai.ocr;

/**
 * OCR（光学字符识别）服务，负责从图片中提取文字内容。
 *
 * <p>本服务通过调用多模态大模型的视觉能力（默认使用 SiliconFlow 平台的 DeepSeek-OCR 模型）
 * 实现图片文字识别，将图片以 Base64 编码发送至 Chat Completions API 进行识别。</p>
 *
 * <p>核心能力：
 * <ul>
 *   <li>图片文字识别（{@link #recognize}），支持 jpg/png/gif/bmp/webp 格式</li>
 *   <li>自动识别图片 MIME 类型并构建 data URL</li>
 *   <li>从 API 响应中提取纯文本识别结果</li>
 * </ul>
 *
 * <p>实现细节：
 * <ul>
 *   <li>使用 Java 标准 HttpClient 发送 HTTP 请求，连接超时 30 秒，请求超时 60 秒</li>
 *   <li>图片以 Base64 编码后通过 data URL 方式传入，构建多模态消息请求体</li>
 *   <li>请求体使用多模态消息格式（image_url + text），提示词要求直接输出文字不加解释</li>
 *   <li>响应从 choices[0].message.content 中提取识别文本</li>
 * </ul>
 *
 * <p>配置项（application.yml）：
 * <ul>
 *   <li>{@code ai.ocr.url} - OCR 服务地址，默认 SiliconFlow</li>
 *   <li>{@code ai.ocr.key} - API 密钥</li>
 *   <li>{@code ai.ocr.model} - OCR 模型名称，默认 DeepSeek-OCR</li>
 *   <li>{@code ai.ocr.engines.{code}.url/key/model} - 可选多引擎配置（如 paddle），
 *       {@link #recognize(byte[], String, String)} 传入引擎代码时优先使用，未配置回退默认</li>
 * </ul>
 *
 * <p>依赖：{@link ObjectMapper}（JSON 序列化/反序列化）</p>
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Value("${ai.ocr.url:https://api.siliconflow.cn}")
    private String ocrUrl;

    @Value("${ai.ocr.key:}")
    private String ocrKey;

    @Value("${ai.ocr.model:deepseek-ai/DeepSeek-OCR}")
    private String ocrModel;

    /**
     * 对图片进行 OCR 识别（使用系统默认引擎）
     */
    public String recognize(byte[] imageBytes, String extension) {
        return recognize(imageBytes, extension, null);
    }

    /**
     * 对图片进行 OCR 识别。
     *
     * @param engine OCR 引擎代码（deepseek / paddle 等，对应 ai.ocr.engines.{code}.* 配置），
     *               null 或未配置时回退系统默认引擎
     */
    public String recognize(byte[] imageBytes, String extension, String engine) {
        String url = resolveEngineProp(engine, "url", ocrUrl);
        String key = resolveEngineProp(engine, "key", ocrKey);
        String model = resolveEngineProp(engine, "model", ocrModel);
        log.info("Calling OCR service, engine={}, model: {}, size: {} bytes",
                engine != null ? engine : "default", model, imageBytes.length);

        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = getMimeType(extension);
        String dataUrl = "data:" + mimeType + ";base64," + base64;

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                                Map.of("type", "text", "text", "请识别图片中的所有文字内容，直接输出文字，不要添加任何解释。")
                        ))
                ),
                "max_tokens", 4096
        );

        try {
            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/v1/chat/completions"))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            log.info("OCR response status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                throw new RuntimeException("OCR API returned " + response.statusCode() + ": " + response.body());
            }

            return parseResponse(response.body());
        } catch (Exception e) {
            log.error("OCR recognition failed", e);
            throw new RuntimeException("图片识别失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析引擎配置：ai.ocr.engines.{engine}.{key} 存在则使用，否则回退系统默认并告警
     */
    private String resolveEngineProp(String engine, String key, String defaultVal) {
        if (engine == null || engine.isBlank()) return defaultVal;
        String v = environment.getProperty("ai.ocr.engines." + engine + "." + key, String.class);
        if (v != null && !v.isBlank()) return v;
        log.warn("OCR engine '{}' has no ai.ocr.engines.{}.{} config, falling back to default", engine, engine, key);
        return defaultVal;
    }

    private String parseResponse(String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            log.info("OCR result length: {} chars", content.length());
            return content;
        } catch (Exception e) {
            log.error("Failed to parse OCR response: {}", resp, e);
            throw new RuntimeException("解析 OCR 响应失败", e);
        }
    }

    private String getMimeType(String extension) {
        if (extension == null) return "image/jpeg";
        return switch (extension.toLowerCase()) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}
