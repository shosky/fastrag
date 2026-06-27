package com.fastrag.ai.asr;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsrService {

    private final ObjectMapper objectMapper;

    @Value("${ai.asr.url:https://api.siliconflow.cn}")
    private String asrUrl;

    @Value("${ai.asr.key:}")
    private String asrKey;

    @Value("${ai.asr.model:FunAudioLLM/SenseVoiceSmall}")
    private String asrModel;

    /**
     * 调用 ASR 服务进行语音转文字
     */
    public AsrResult transcribe(byte[] audioBytes, String filename) {
        log.info("Calling ASR service, model: {}, file: {}, size: {} bytes", asrModel, filename, audioBytes.length);

        try {
            // 写入临时文件
            Path tempFile = Files.createTempFile("asr_", "_" + filename);
            Files.write(tempFile, audioBytes);

            try {
                String boundary = UUID.randomUUID().toString();
                byte[] body = buildMultipartBody(boundary, tempFile, filename);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(asrUrl + "/v1/audio/transcriptions"))
                        .header("Authorization", "Bearer " + asrKey)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                HttpClient client = HttpClient.newHttpClient();
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

    private byte[] buildMultipartBody(String boundary, Path file, String filename) throws Exception {
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
        out.write(asrModel.getBytes());
        out.write(CRLF.getBytes());

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
