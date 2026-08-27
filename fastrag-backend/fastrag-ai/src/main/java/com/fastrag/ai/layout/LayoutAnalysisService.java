package com.fastrag.ai.layout;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 版面分析服务：调用多模态大模型识别文档页面图片中的内容块（标题/正文/表格/图片/代码/公式/图注）
 * 及其归一化位置，供「AI分片 原件渲染」按类型分色画框。
 *
 * <p>与 {@link com.fastrag.ai.ocr.OcrService} 的区别：OCR 只输出纯文本，本服务要求模型输出
 * <b>结构化 JSON</b>（块类型 + bbox 归一化坐标 + 块文本摘要），是空间对齐信息而非文字内容。</p>
 *
 * <p>配置项（application.yml）：
 * <ul>
 *   <li>{@code ai.layout.url/key/model} - 版面分析引擎；<b>未配置时自动回退 {@code ai.ocr.*} 默认引擎</b>（零配置可用）</li>
 * </ul>
 *
 * <p>实现细节：
 * <ul>
 *   <li>OpenAI 兼容 /v1/chat/completions 多模态调用，图片 Base64 data URL，提示词约束输出严格 JSON 数组</li>
 *   <li>bbox 要求 0~1000 归一化整数（对模型更友好）；解析时自适应兼容 0~1 浮点输出</li>
 *   <li>{@link #parseBlocks} 做稳健清洗：截取首个 [ 到末个 ]、类型归一、坐标裁剪 0~1、退化块剔除；
 *       解析失败返回空列表（单页无框可接受，不抛异常中断整批）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LayoutAnalysisService {

    private final ObjectMapper objectMapper;
    private final Environment environment;

    /** 未单独配置时复用 OCR 默认引擎通道（零配置可用） */
    @Value("${ai.layout.url:${ai.ocr.url:https://api.siliconflow.cn}}")
    private String layoutUrl;

    @Value("${ai.layout.key:${ai.ocr.key:}}")
    private String layoutKey;

    @Value("${ai.layout.model:${ai.ocr.model:deepseek-ai/DeepSeek-OCR}}")
    private String layoutModel;

    /** 版面块类型白名单 */
    private static final Set<String> BLOCK_TYPES =
            Set.of("title", "text", "table", "image", "code", "formula", "caption");

    /**
     * 发送图片最长边上限：VLM 上下文总长 8192 token，图片输入本身占用大量 token，
     * 超长会导致 max_tokens 越界返回 400；且降采样可加快推理。归一化 bbox 与缩放无关。
     */
    private static final int MAX_IMAGE_DIM = 1280;

    /** 页面版面块：坐标为相对页面宽高的归一化 0~1，顶左原点 */
    public record LayoutBlock(String type, double x, double y, double width, double height, String text) {
    }

    static final String PROMPT = """
            你是文档版面分析引擎。分析这张文档页面图片，找出页面上所有内容块，只用一个 JSON 数组输出，\
            不要输出 JSON 以外的任何内容。每个元素格式：
            {"type":"title|text|table|image|code|formula|caption","bbox":[x1,y1,x2,y2],"text":"块内文字摘要"}
            要求：
            - bbox 是内容块的外接矩形，坐标为相对页面宽高的 0~1000 归一化整数，顶左为原点，x2>x1、y2>y1；
            - type 只能取七类之一：title=标题，text=正文段落，table=表格，image=图片或图表，\
            code=代码块，formula=数学公式，caption=图注或表注；
            - 逐块输出，不要合并相邻块，不要遗漏页眉页脚以外的任何可见内容块；
            - text 填块内文字的前 60 个字符；图片/公式块用一句话描述内容。
            """;

    /**
     * 分析单页文档图片的版面结构。
     *
     * @param pngBytes 页面渲染 PNG 字节
     * @param engine   引擎代码（对应 ai.layout.engines.{code}.*，null 用默认）
     * @return 版面块列表；识别或解析失败返回空列表（不抛出，调用方跳过该页即可）
     */
    public List<LayoutBlock> analyzePage(byte[] pngBytes, String engine) {
        String url = resolveEngineProp(engine, "url", layoutUrl);
        String key = resolveEngineProp(engine, "key", layoutKey);
        String model = resolveEngineProp(engine, "model", layoutModel);

        byte[] sendBytes = downscale(pngBytes);
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(sendBytes);
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                                Map.of("type", "text", "text", PROMPT)
                        ))
                ),
                // 上下文总长 8192：图片输入占大头，输出留 2048（版面块 JSON 足够），
                // 若过大模型会因 max_tokens 越界返回 400
                "max_tokens", 2048
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/v1/chat/completions"))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(90))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("Layout API returned {}: {}", response.statusCode(),
                        response.body().length() > 300 ? response.body().substring(0, 300) : response.body());
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            List<LayoutBlock> blocks = parseBlocks(content);
            log.info("Layout analysis done, model={}, blocks={}", model, blocks.size());
            return blocks;
        } catch (Exception e) {
            log.warn("Layout analysis failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 从模型输出中稳健提取版面块：截取首个 [ 到末个 ] 后 Jackson 解析，
     * 逐块清洗（类型白名单归一、bbox 自适应 0~1000/0~1 两种刻度、坐标裁剪、退化剔除）。
     * 任何失败返回空列表。
     */
    static List<LayoutBlock> parseBlocks(String content) {
        if (content == null || content.isBlank()) return List.of();
        int s = content.indexOf('[');
        int e = content.lastIndexOf(']');
        if (s < 0 || e <= s) return List.of();
        try {
            JsonNode arr = new ObjectMapper().readTree(content.substring(s, e + 1));
            if (!arr.isArray()) return List.of();
            // 自适应刻度：全为 ≤1.5 的小数视为模型已输出 0~1 浮点，否则按 0~1000 换算
            double maxCoord = 0;
            for (JsonNode n : arr) {
                JsonNode b = n.path("bbox");
                for (int i = 0; b.isArray() && i < Math.min(4, b.size()); i++) {
                    maxCoord = Math.max(maxCoord, Math.abs(b.get(i).asDouble()));
                }
            }
            double k = maxCoord <= 1.5 ? 1.0 : 1000.0;
            List<LayoutBlock> out = new ArrayList<>();
            for (JsonNode n : arr) {
                String type = n.path("type").asText("text").trim().toLowerCase();
                if (!BLOCK_TYPES.contains(type)) type = "text";
                JsonNode b = n.path("bbox");
                if (!b.isArray() || b.size() < 4) continue;
                double x1 = clamp(b.get(0).asDouble() / k);
                double y1 = clamp(b.get(1).asDouble() / k);
                double x2 = clamp(b.get(2).asDouble() / k);
                double y2 = clamp(b.get(3).asDouble() / k);
                // 退化块（宽/高 < 0.2% 页面）剔除
                if (x2 - x1 <= 0.002 || y2 - y1 <= 0.002) continue;
                String text = n.path("text").asText("");
                if (text.length() > 120) text = text.substring(0, 120);
                out.add(new LayoutBlock(type, x1, y1, x2 - x1, y2 - y1, text));
            }
            return out;
        } catch (Exception ex) {
            log.warn("Failed to parse layout blocks: {}", ex.getMessage());
            return List.of();
        }
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** 图片最长边降采样到 {@link #MAX_IMAGE_DIM}，减少 VLM 输入 token 并提速；失败或本就不大时原样返回 */
    private byte[] downscale(byte[] pngBytes) {
        try {
            java.awt.image.BufferedImage img =
                    javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(pngBytes));
            if (img == null) return pngBytes;
            int w = img.getWidth(), h = img.getHeight();
            double s = Math.min(1.0, (double) MAX_IMAGE_DIM / Math.max(w, h));
            if (s >= 1.0) return pngBytes;
            int nw = Math.max(1, (int) (w * s)), nh = Math.max(1, (int) (h * s));
            java.awt.image.BufferedImage out =
                    new java.awt.image.BufferedImage(nw, nh, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("Layout downscale failed, sending original: {}", e.getMessage());
            return pngBytes;
        }
    }

    /** 解析引擎配置：ai.layout.engines.{engine}.{key} 存在则使用，否则回退默认 */
    private String resolveEngineProp(String engine, String key, String defaultVal) {
        if (engine == null || engine.isBlank()) return defaultVal;
        String v = environment.getProperty("ai.layout.engines." + engine + "." + key, String.class);
        if (v != null && !v.isBlank()) return v;
        return defaultVal;
    }
}
