package com.fastrag.ai.ocr;

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
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final ObjectMapper objectMapper;

    @Value("${ai.ocr.url:https://api.siliconflow.cn}")
    private String ocrUrl;

    @Value("${ai.ocr.key:}")
    private String ocrKey;

    @Value("${ai.ocr.model:deepseek-ai/DeepSeek-OCR}")
    private String ocrModel;

    /**
     * 对图片进行 OCR 识别
     *
     * @param imageBytes 图片字节
     * @param extension  图片扩展名（jpg, png 等）
     * @return OCR 识别出的文本
     */
    public String recognize(byte[] imageBytes, String extension) {
        log.info("Calling OCR service, model: {}, size: {} bytes", ocrModel, imageBytes.length);

        String base64 = Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = getMimeType(extension);
        String dataUrl = "data:" + mimeType + ";base64," + base64;

        Map<String, Object> body = Map.of(
                "model", ocrModel,
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
                    .uri(URI.create(ocrUrl + "/v1/chat/completions"))
                    .header("Authorization", "Bearer " + ocrKey)
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
