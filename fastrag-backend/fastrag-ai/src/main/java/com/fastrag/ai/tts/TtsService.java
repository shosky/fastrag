package com.fastrag.ai.tts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文本转语音服务（TTS），调用硅基流动 /v1/audio/speech
 *
 * 接口格式（来自官方 curl 示例）：
 * {
 *   "model": "fnlp/MOSS-TTSD-v0.5",
 *   "input": "文本内容（字符串）",
 *   "voice": "fnlp/MOSS-TTSD-v0.5:alex",   ← 注意是 model:voice_name 拼接
 *   "response_format": "mp3"
 * }
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${ai.tts.url:https://api.siliconflow.cn}")
    private String ttsUrl;

    @Value("${ai.tts.key:}")
    private String ttsKey;

    @Value("${ai.tts.model:fnlp/MOSS-TTSD-v0.5}")
    private String ttsModel;

    /** 纯音色名（如 alex / claire），运行时会自动拼成 {model}:{voice} */
    @Value("${ai.tts.voice:alex}")
    private String defaultVoiceName;

    @Value("${ai.tts.response-format:mp3}")
    private String defaultResponseFormat;

    /**
     * 调用 TTS 服务合成语音
     *
     * @param text  要合成的文本
     * @param voice 纯音色名（如 alex / claire）；传 null/空使用默认
     * @return 音频字节流 + 元信息
     */
    public TtsResult synthesize(String text, String voice) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("TTS 输入文本不能为空");
        }
        String useVoiceName = (voice == null || voice.isBlank()) ? defaultVoiceName : voice;
        // 硅基流动要求 voice 字段是 "model:voice_name" 拼接形式
        String fullVoice = ttsModel + ":" + useVoiceName;

        log.info("Calling TTS service, model: {}, voice: {}, text length: {}",
                ttsModel, fullVoice, text.length());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ttsModel);
        body.put("input", text);                  // 注意：input 是字符串
        body.put("voice", fullVoice);             // 顶级字段
        body.put("response_format", defaultResponseFormat);

        try {
            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ttsUrl + "/v1/audio/speech"))
                    .header("Authorization", "Bearer " + ttsKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            log.info("TTS response status: {}, audio size: {} bytes",
                    response.statusCode(),
                    response.body() == null ? 0 : response.body().length);

            if (response.statusCode() != 200) {
                String errBody = new String(
                        response.body() == null ? new byte[0] : response.body(),
                        StandardCharsets.UTF_8);
                throw new RuntimeException("TTS API returned " + response.statusCode() + ": " + errBody);
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("audio/mpeg");
            String ext = mapFormatToExtension(defaultResponseFormat);
            String filename = "tts_" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

            return TtsResult.builder()
                    .audioBytes(response.body())
                    .contentType(contentType)
                    .filename(filename)
                    .responseFormat(defaultResponseFormat)
                    .build();
        } catch (Exception e) {
            log.error("TTS synthesis failed", e);
            throw new RuntimeException("语音合成失败: " + e.getMessage(), e);
        }
    }

    private String mapFormatToExtension(String format) {
        if (format == null) return "mp3";
        return switch (format.toLowerCase()) {
            case "wav" -> "wav";
            case "pcm" -> "pcm";
            case "opus" -> "opus";
            case "flac" -> "flac";
            case "m4a" -> "m4a";
            default -> "mp3";
        };
    }
}
