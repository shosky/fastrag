package com.fastrag.ai.imagegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文生图服务（Text-to-Image），调用硅基流动 /v1/images/generations
 *
 * 接口格式（来自官方 curl 示例）：
 * {
 *   "model": "Kwai-Kolors/Kolors",
 *   "prompt": "...",
 *   "image_size": "1024x1024",
 *   "batch_size": 1,
 *   "num_inference_steps": 20,
 *   "guidance_scale": 7.5
 * }
 * 响应默认返回 { data: [ { url: "https://..." } ] }，需要二次拉取获取字节流。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGenService {

    private final ObjectMapper objectMapper;

    @Value("${ai.imagegen.url:https://api.siliconflow.cn}")
    private String imageGenUrl;

    @Value("${ai.imagegen.key:}")
    private String imageGenKey;

    @Value("${ai.imagegen.model:Kwai-Kolors/Kolors}")
    private String imageGenModel;

    @Value("${ai.imagegen.image-size:1024x1024}")
    private String defaultImageSize;

    @Value("${ai.imagegen.num-inference-steps:20}")
    private Integer defaultNumInferenceSteps;

    @Value("${ai.imagegen.guidance-scale:7.5}")
    private Double defaultGuidanceScale;

    @Value("${ai.imagegen.batch-size:1}")
    private Integer defaultBatchSize;

    /** url / b64_json。不填则默认 url 模式 */
    @Value("${ai.imagegen.response-format:url}")
    private String defaultResponseFormat;

    /**
     * 调用文生图服务生成图片
     *
     * @param prompt     提示词
     * @param imageSize  例如 "1024x1024"，传 null 使用默认
     * @param seed       随机种子（可选）
     * @return 图片字节流 + 元信息
     */
    public ImageGenResult generate(String prompt, String imageSize, Long seed) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("文生图 prompt 不能为空");
        }
        String useSize = (imageSize == null || imageSize.isBlank()) ? defaultImageSize : imageSize;

        log.info("Calling ImageGen service, model: {}, prompt: {}, size: {}, responseFormat: {}",
                imageGenModel, prompt, useSize, defaultResponseFormat);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", imageGenModel);
        body.put("prompt", prompt);
        body.put("image_size", useSize);
        body.put("batch_size", defaultBatchSize);
        body.put("num_inference_steps", defaultNumInferenceSteps);
        body.put("guidance_scale", defaultGuidanceScale);
        // 仅在用户明确选 b64_json 时才传，否则走默认 url 模式
        if ("b64_json".equalsIgnoreCase(defaultResponseFormat)) {
            body.put("response_format", "b64_json");
        }
        if (seed != null) {
            body.put("seed", seed);
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageGenUrl + "/v1/images/generations"))
                    .header("Authorization", "Bearer " + imageGenKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            log.info("ImageGen response status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                throw new RuntimeException("ImageGen API returned " + response.statusCode() + ": " + response.body());
            }

            return parseResponse(response.body());
        } catch (Exception e) {
            log.error("ImageGen generation failed", e);
            throw new RuntimeException("图片生成失败: " + e.getMessage(), e);
        }
    }

    private ImageGenResult parseResponse(String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);
            JsonNode first = root.path("data").path(0);
            if (first.isMissingNode() || first.isNull()) {
                throw new RuntimeException("ImageGen 响应缺少 data[0]: " + resp);
            }

            byte[] imageBytes;
            String contentType = "image/png";
            String ext = "png";

            // 优先尝试 b64_json
            String b64 = first.path("b64_json").asText("");
            if (!b64.isBlank()) {
                imageBytes = Base64.getDecoder().decode(b64);
            } else {
                // 否则取 url 二次拉取
                String url = first.path("url").asText("");
                if (url.isBlank()) {
                    throw new RuntimeException("ImageGen 响应既无 b64_json 也无 url: " + resp);
                }
                log.info("Fetching generated image from url: {}", url);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<byte[]> r = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200) {
                    throw new RuntimeException("拉取生成图片失败，HTTP " + r.statusCode());
                }
                imageBytes = r.body();
                contentType = r.headers().firstValue("Content-Type").orElse(contentType);
                // 从 contentType 或 url 后缀推断扩展名
                if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                    ext = "jpg";
                } else if (contentType.contains("webp")) {
                    ext = "webp";
                } else if (url.contains(".jpg") || url.contains(".jpeg")) {
                    ext = "jpg";
                } else if (url.contains(".webp")) {
                    ext = "webp";
                }
            }

            String filename = "img_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
            return ImageGenResult.builder()
                    .imageBytes(imageBytes)
                    .contentType(contentType)
                    .mimeType(contentType)
                    .filename(filename)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse ImageGen response: {}", resp, e);
            throw new RuntimeException("解析 ImageGen 响应失败: " + e.getMessage(), e);
        }
    }
}
