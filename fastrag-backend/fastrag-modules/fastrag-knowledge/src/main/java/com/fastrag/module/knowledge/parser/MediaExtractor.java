package com.fastrag.module.knowledge.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 音视频文件处理工具
 * 使用 FFmpeg 从视频中提取音频，或直接传递音频文件给 ASR
 */
@Slf4j
@Component
public class MediaExtractor {

    private static final String[] VIDEO_EXTENSIONS = {"mp4", "avi", "mov", "mkv", "flv", "wmv", "webm"};
    private static final String[] AUDIO_EXTENSIONS = {"mp3", "wav", "m4a", "aac", "ogg", "flac", "wma"};

    /**
     * 判断是否为视频文件
     */
    public boolean isVideo(String extension) {
        if (extension == null) return false;
        String ext = extension.toLowerCase().trim();
        for (String ve : VIDEO_EXTENSIONS) {
            if (ve.equals(ext)) return true;
        }
        return false;
    }

    /**
     * 判断是否为音频文件
     */
    public boolean isAudio(String extension) {
        if (extension == null) return false;
        String ext = extension.toLowerCase().trim();
        for (String ae : AUDIO_EXTENSIONS) {
            if (ae.equals(ext)) return true;
        }
        return false;
    }

    /**
     * 从视频文件中提取音频
     * 如果 FFmpeg 不可用，直接返回原始字节（SiliconFlow 支持 mp4 等格式）
     *
     * @param videoStream 视频文件流
     * @param extension   文件扩展名
     * @return 音频字节数组（WAV 格式）或原始字节
     */
    public byte[] extractAudio(InputStream videoStream, String extension) throws IOException {
        // 如果已经是音频格式，直接返回
        if (isAudio(extension)) {
            log.info("File is already audio format, passing directly to ASR");
            return videoStream.readAllBytes();
        }

        // 尝试使用 FFmpeg 提取音频
        if (isFfmpegAvailable()) {
            return extractAudioWithFfmpeg(videoStream, extension);
        }

        // FFmpeg 不可用，直接返回原始字节（SiliconFlow 支持多种格式）
        log.warn("FFmpeg not available, sending original file to ASR service");
        return videoStream.readAllBytes();
    }

    /**
     * 使用 FFmpeg 从视频提取音频
     */
    private byte[] extractAudioWithFfmpeg(InputStream videoStream, String extension) throws IOException {
        Path tempInput = Files.createTempFile("fastrag_input_", "." + (extension != null ? extension : "mp4"));
        Path tempOutput = Files.createTempFile("fastrag_audio_", ".wav");

        try {
            // 写入临时文件
            Files.write(tempInput, videoStream.readAllBytes());

            // 调用 FFmpeg
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-i", tempInput.toString(),
                    "-vn",                    // 不包含视频
                    "-acodec", "pcm_s16le",   // PCM 16-bit
                    "-ar", "16000",           // 16kHz 采样率
                    "-ac", "1",               // 单声道
                    "-y",                     // 覆盖输出
                    tempOutput.toString()
            );
            pb.redirectErrorStream(true);

            log.info("Running FFmpeg to extract audio from video");
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("FFmpeg exited with code: {}", exitCode);
                // 回退：返回原始字节
                return Files.readAllBytes(tempInput);
            }

            byte[] audioBytes = Files.readAllBytes(tempOutput);
            log.info("FFmpeg extracted audio, size: {} bytes", audioBytes.length);
            return audioBytes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg process interrupted", e);
        } finally {
            // 清理临时文件
            try { Files.deleteIfExists(tempInput); } catch (IOException ignored) {}
            try { Files.deleteIfExists(tempOutput); } catch (IOException ignored) {}
        }
    }

    /**
     * 检查 FFmpeg 是否可用
     */
    private boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            log.debug("FFmpeg not available: {}", e.getMessage());
            return false;
        }
    }
}
