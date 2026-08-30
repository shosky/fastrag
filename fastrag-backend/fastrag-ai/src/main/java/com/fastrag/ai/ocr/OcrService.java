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

        // DeepSeek-OCR 是任务式专用模型：只认训练时的固定 prompt（Free OCR. / Convert the document
        // to markdown.），对自由指令式 prompt 会返回空 content（completion_tokens=0，HF #89 / vLLM
        // recipes 证实）。其余通用 VL 模型继续用指令式 prompt 保证页眉脚/印章等定制要求。
        String ocrPrompt = model != null && model.toLowerCase().contains("deepseek-ocr")
                ? "Convert the document to markdown."
                : """
                        你是文档 OCR 引擎。请逐字转录图片中的全部文字内容：
                        1. 只输出图片中实际存在的文字，按阅读顺序（从上到下、从左到右）；
                        2. 禁止任何描述、解释、总结或评论——例如"这是一张文件的图片"之类的说明一律不要输出；
                        3. 表格用 Markdown 表格输出（| 分隔单元格，第二行为 |---| 分隔行）；
                        4. 忽略页眉、页脚、页码和水印；印章（公章/收文章/签名）是有效内容，
                        必须识别并转录其中的文字（红色印章文字也要逐字输出）；
                        5. 连续的编号/数字列直接输出数字本身，不要用 LaTeX 公式（\\[...\\]、\\begin{split} 等）包装；
                        6. 空白或无法辨认的区域不要编造内容；
                        7. 若图片为文档/扫描件，请尽力辨认并逐字输出全部可见文字（印刷体、印章字、轻微模糊的字都要输出）——
                        仅当整张图片确实不含任何文字时才允许输出空。""";

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                                Map.of("type", "text", "text", ocrPrompt)
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

            String result = parseResponse(response.body());
            // [ocr-dbg] 空结果时打印响应体（截断）：确认 finish_reason / 模型为何返回空
            if (result == null || result.isBlank()) {
                log.warn("[ocr-dbg] OCR returned EMPTY content, body: {}",
                        response.body() == null ? "null" : response.body().substring(0, Math.min(600, response.body().length())));
            }
            return result;
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
            // [ocr-dbg] 打印原始返回（截断），确认是否被 cleanOcrText 剥空
            log.info("[ocr-dbg] OCR raw content: {}", content.length() > 300 ? content.substring(0, 300) + "…" : content);
            return cleanOcrText(content);
        } catch (Exception e) {
            log.error("Failed to parse OCR response: {}", resp, e);
            throw new RuntimeException("解析 OCR 响应失败", e);
        }
    }

    /** LaTeX 环境块：\begin{env}...\end{env}（split/align/array 等公式环境） */
    private static final java.util.regex.Pattern LATEX_ENV_BLOCK = java.util.regex.Pattern.compile(
            "\\\\begin\\{([a-zA-Z*]+)}([\\s\\S]*?)\\\\end\\{\\1}");

    /**
     * OCR 结果清洗：
     * <ul>
     *   <li>丢弃"纯数字/符号"LaTeX 环境块（\begin{split} &28 \\ &29 ...\end{split} 之类——
     *       多为装饰线条/页码列被误识别为公式；剥掉 LaTeX 命令与标记符后不含任何文字即丢弃），
     *       含字母变量的真实公式保留；</li>
     *   <li>剥掉残留的 \[ \] \( \) 定界符与多余空行。</li>
     * </ul>
     */
    private String cleanOcrText(String content) {
        if (content == null || content.isBlank()) return content;
        String text = LATEX_ENV_BLOCK.matcher(content)
                .replaceAll(m -> hasProseLetters(m.group(2)) ? m.group(0) : "");
        // 剥掉残留的 \( \) \[ \] 定界符（regex：反斜杠 + 四种括号之一）
        text = text.replaceAll("\\\\[()\\[\\]]", "");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }

    /** 块内容剥掉 LaTeX 命令与标记符后是否还含有效文字（字母/中文）——无则视为线条/页码类伪公式 */
    private boolean hasProseLetters(String latexInner) {
        if (latexInner == null) return false;
        String s = latexInner.replaceAll("\\\\[a-zA-Z]+", "")
                .replaceAll("[$&{}\\[\\]\\s\\\\|]", "");
        return s.matches(".*[a-zA-Z\\u4e00-\\u9fa5].*");
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
