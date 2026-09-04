package com.fastrag.ai.tts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsResult {
    private byte[] audioBytes;
    private String contentType;   // e.g. audio/mpeg
    private String filename;      // e.g. tts_xxx.mp3
    private String responseFormat; // mp3 / wav / pcm ...
    private Integer durationMs;   // 服务端返回时长（若有）
}
