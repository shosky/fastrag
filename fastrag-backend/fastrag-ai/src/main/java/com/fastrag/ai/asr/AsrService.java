package com.fastrag.ai.asr;

/**
 * ASR（自动语音识别）服务，负责将音频文件转换为文字。
 *
 * <p>本服务通过 HTTP 调用外部 ASR API（默认 SiliconFlow 平台，兼容 OpenAI Whisper API 格式），
 * 支持上传音频文件并获取完整的语音识别结果，包括带时间戳的分段信息。</p>
 *
 * <p>核心能力：
 * <ul>
 *   <li>音频文件上传与语音转文字（{@link #transcribe}）</li>
 *   <li>multipart/form-data 格式的请求构建</li>
 *   <li>ASR 响应解析，提取完整文本和时间分段</li>
 * </ul>
 *
 * <p>实现细节：
 * <ul>
 *   <li>使用 Java 标准 HttpClient 发送 HTTP 请求，连接超时 30 秒，请求超时 120 秒</li>
 *   <li>先将音频字节写入临时文件，再构建 multipart 请求体上传</li>
 *   <li>默认使用 SenseVoiceSmall 模型，可通过配置项切换</li>
 *   <li>响应解析时优先提取 segments 分段信息，若为空则用完整文本作为单段兜底</li>
 * </ul>
 *
 * <p>配置项（application.yml）：
 * <ul>
 *   <li>{@code ai.asr.url} - ASR 服务地址，默认 SiliconFlow</li>
 *   <li>{@code ai.asr.key} - API 密钥</li>
 *   <li>{@code ai.asr.model} - ASR 模型名称</li>
 *   <li>{@code ai.asr.engines.{code}.url/key/model} - 可选多引擎配置（如 whisper），
 *       {@link #transcribe(byte[], String, String, String)} 传入引擎代码时优先使用，未配置回退默认</li>
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsrService {

    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Value("${ai.asr.url:https://api.siliconflow.cn}")
    private String asrUrl;

    @Value("${ai.asr.key:}")
    private String asrKey;

    @Value("${ai.asr.model:FunAudioLLM/SenseVoiceSmall}")
    private String asrModel;

    /**
     * 调用 ASR 服务进行语音转文字（使用系统默认引擎与自动语言检测）
     */
    public AsrResult transcribe(byte[] audioBytes, String filename) {
        return transcribe(audioBytes, filename, null, null);
    }

    /**
     * 调用 ASR 服务进行语音转文字。
     *
     * @param engine   ASR 引擎代码（funasr / whisper 等，对应 ai.asr.engines.{code}.* 配置），
     *                 null 或未配置时回退系统默认引擎
     * @param language 内容语言（auto/zh/en/mixed）；auto/mixed 时不传（服务端自动检测）
     */
    public AsrResult transcribe(byte[] audioBytes, String filename, String engine, String language) {
        String url = resolveEngineProp(engine, "url", asrUrl);
        String key = resolveEngineProp(engine, "key", asrKey);
        String model = resolveEngineProp(engine, "model", asrModel);
        log.info("Calling ASR service, engine={}, model: {}, language: {}, file: {}, size: {} bytes",
                engine != null ? engine : "default", model, language, filename, audioBytes.length);

        try {
            // 写入临时文件
            Path tempFile = Files.createTempFile("asr_", "_" + filename);
            Files.write(tempFile, audioBytes);

            try {
                String boundary = UUID.randomUUID().toString();
                byte[] body = buildMultipartBody(boundary, tempFile, filename, model, language);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/v1/audio/transcriptions"))
                        .header("Authorization", "Bearer " + key)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .timeout(Duration.ofSeconds(120))   // 请求超时 120 秒
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(30))  // 连接超时 30 秒
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                log.info("ASR response status: {}, body: {}", response.statusCode(), response.body());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("ASR API returned " + response.statusCode() + ": " + response.body());
                }

                return parseResponse(response.body());
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            log.error("ASR transcription failed", e);
            throw new RuntimeException("语音转文字失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析引擎配置：ai.asr.engines.{engine}.{key} 存在则使用，否则回退系统默认并告警
     */
    private String resolveEngineProp(String engine, String key, String defaultVal) {
        if (engine == null || engine.isBlank()) return defaultVal;
        String v = environment.getProperty("ai.asr.engines." + engine + "." + key, String.class);
        if (v != null && !v.isBlank()) return v;
        log.warn("ASR engine '{}' has no ai.asr.engines.{}.{} config, falling back to default", engine, engine, key);
        return defaultVal;
    }

    private byte[] buildMultipartBody(String boundary, Path file, String filename, String model, String language) throws Exception {
        String CRLF = "\r\n";
        var out = new java.io.ByteArrayOutputStream();

        // file part
        out.write(("--" + boundary + CRLF).getBytes());
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"" + CRLF).getBytes());
        out.write(("Content-Type: audio/mpeg" + CRLF).getBytes());
        out.write(CRLF.getBytes());
        out.write(Files.readAllBytes(file));
        out.write(CRLF.getBytes());

        // model part
        out.write(("--" + boundary + CRLF).getBytes());
        out.write(("Content-Disposition: form-data; name=\"model\"" + CRLF).getBytes());
        out.write(CRLF.getBytes());
        out.write(model.getBytes());
        out.write(CRLF.getBytes());

        // language part（auto/mixed 不传，服务端自动检测；SenseVoice/Whisper 均支持显式指定）
        if (language != null && !language.isBlank()
                && !"auto".equalsIgnoreCase(language) && !"mixed".equalsIgnoreCase(language)) {
            out.write(("--" + boundary + CRLF).getBytes());
            out.write(("Content-Disposition: form-data; name=\"language\"" + CRLF).getBytes());
            out.write(CRLF.getBytes());
            out.write(language.getBytes());
            out.write(CRLF.getBytes());
        }

        // end boundary
        out.write(("--" + boundary + "--" + CRLF).getBytes());

        return out.toByteArray();
    }

    private AsrResult parseResponse(String resp) {
        try {
            JsonNode root = objectMapper.readTree(resp);
            String fullText = root.path("text").asText("");

            List<AsrResult.AsrSegment> segments = new ArrayList<>();
            JsonNode segmentsNode = root.path("segments");
            if (segmentsNode.isArray()) {
                for (JsonNode seg : segmentsNode) {
                    segments.add(AsrResult.AsrSegment.builder()
                            .text(seg.path("text").asText(""))
                            .start(seg.path("start").asDouble(0))
                            .end(seg.path("end").asDouble(0))
                            .build());
                }
            }

            if (segments.isEmpty() && !fullText.isBlank()) {
                segments.add(AsrResult.AsrSegment.builder()
                        .text(fullText)
                        .start(0.0)
                        .end(0.0)
                        .build());
            }

            return AsrResult.builder()
                    .text(fullText)
                    .segments(segments)
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse ASR response: {}", resp, e);
            throw new RuntimeException("解析 ASR 响应失败", e);
        }
    }
}
