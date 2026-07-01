package com.fastrag.module.knowledge.parser;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 音视频文件处理工具
 * <p>
 * 使用 FFmpeg 从视频中提取音频，或直接传递音频文件给 ASR。
 * 支持从视频中均匀采样关键帧，并通过感知哈希(pHash)进行去重。
 */
@Slf4j
@Component
public class MediaExtractor {

    private static final String[] VIDEO_EXTENSIONS = {"mp4", "avi", "mov", "mkv", "flv", "wmv", "webm"};
    private static final String[] AUDIO_EXTENSIONS = {"mp3", "wav", "m4a", "aac", "ogg", "flac", "wma"};

    @Value("${ai.ffmpeg.path:ffmpeg}")
    private String ffmpegPath;

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
     * 从视频中均匀采样关键帧
     * <p>
     * 使用 FFmpeg 按 fps=1/{intervalSeconds} 采样，输出 JPEG 格式图片。
     *
     * @param videoStream     视频文件流
     * @param extension       文件扩展名
     * @param intervalSeconds 采样间隔（秒），默认 10
     * @return 关键帧列表，每个关键帧包含图片字节和对应时间戳
     */
    public List<Keyframe> extractKeyframes(InputStream videoStream, String extension, int intervalSeconds) throws IOException {
        Path tempInput = Files.createTempFile("fastrag_kf_input_", "." + (extension != null ? extension : "mp4"));
        Path tempOutputDir = Files.createTempDirectory("fastrag_kf_output_");

        try {
            // 写入临时视频文件
            Files.write(tempInput, videoStream.readAllBytes());

            if (!isFfmpegAvailable()) {
                log.warn("FFmpeg not available, cannot extract keyframes from video");
                return List.of();
            }

            // 调用 FFmpeg 均匀采样关键帧
            // fps=1/N 表示每 N 秒截取一帧
            String outputPattern = tempOutputDir.resolve("keyframe_%03d.jpg").toString();
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath, "-i", tempInput.toString(),
                    "-vf", "fps=1/" + intervalSeconds,
                    "-q:v", "2",        // 高质量 JPEG
                    "-y",               // 覆盖输出
                    outputPattern
            );
            pb.redirectErrorStream(true);

            log.info("Running FFmpeg to extract keyframes (interval={}s)", intervalSeconds);
            Process process = pb.start();

            // 消费 stdout/stderr 防止阻塞
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("FFmpeg keyframe extraction failed with exit code: {}", exitCode);
                return List.of();
            }

            // 读取输出的关键帧文件
            return readKeyframeFiles(tempOutputDir, intervalSeconds);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg keyframe extraction interrupted", e);
        } finally {
            // 清理临时文件和目录
            try { Files.deleteIfExists(tempInput); } catch (IOException ignored) {}
            try { deleteDirectory(tempOutputDir); } catch (IOException ignored) {}
        }
    }

    /**
     * 使用感知哈希(pHash)对关键帧进行去重
     * <p>
     * 计算每帧的感知哈希，与前一帧比较汉明距离，
     * 如果距离小于阈值则认为是重复帧，予以过滤。
     *
     * @param keyframes      待去重的关键帧列表
     * @param hashThreshold 汉明距离阈值，小于该值视为重复（默认 10）
     * @return 去重后的关键帧列表
     */
    public List<Keyframe> deduplicateByHash(List<Keyframe> keyframes, int hashThreshold) {
        if (keyframes == null || keyframes.isEmpty()) {
            return keyframes;
        }

        List<Keyframe> result = new ArrayList<>();
        String prevHash = null;

        for (Keyframe kf : keyframes) {
            // 计算当前帧的感知哈希
            String hash = computePerceptualHash(kf.getImageBytes());
            kf.setHash(hash);

            if (prevHash == null) {
                // 第一帧，直接保留
                result.add(kf);
            } else {
                int distance = hammingDistance(prevHash, hash);
                if (distance >= hashThreshold) {
                    // 与前一帧差异足够大，保留
                    result.add(kf);
                } else {
                    log.debug("Deduplicating keyframe at {}s (hamming distance={} < threshold={})",
                            kf.getTimestampSeconds(), distance, hashThreshold);
                }
            }
            prevHash = hash;
        }

        log.info("Keyframe deduplication: {} -> {} frames (threshold={})",
                keyframes.size(), result.size(), hashThreshold);
        return result;
    }

    /**
     * 按时间戳分片切割音频
     * <p>
     * 使用 FFmpeg 将原始音频按 ASR 返回的时间戳切割为多个独立音频片段。
     *
     * @param audioBytes  原始音频字节
     * @param extension   文件扩展名
     * @param segments    时间戳分段列表（需包含 startTime / endTime）
     * @return 切割后的音频片段列表，顺序与 segments 对应；失败时返回空列表
     */
    public List<byte[]> splitAudio(byte[] audioBytes, String extension, List<ParseResult.ChunkTimeSegment> segments) {
        if (audioBytes == null || audioBytes.length == 0 || segments == null || segments.isEmpty()) {
            return List.of();
        }
        if (!isFfmpegAvailable()) {
            log.warn("FFmpeg not available, cannot split audio");
            return List.of();
        }

        Path tempInput = null;
        List<Path> tempOutputs = new ArrayList<>();
        try {
            tempInput = Files.createTempFile("fastrag_split_input_", "." + extension);
            Files.write(tempInput, audioBytes);

            List<byte[]> result = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                ParseResult.ChunkTimeSegment seg = segments.get(i);
                double start = seg.getStartTime() != null ? seg.getStartTime() : 0.0;
                double end = seg.getEndTime() != null ? seg.getEndTime() : start;
                double duration = end - start;
                if (duration <= 0) duration = 10.0; // 兜底 10 秒

                Path tempOutput = Files.createTempFile("fastrag_segment_" + i + "_", "." + extension);
                tempOutputs.add(tempOutput);

                ProcessBuilder pb = new ProcessBuilder(
                        ffmpegPath, "-i", tempInput.toString(),
                        "-ss", String.valueOf(start),
                        "-t", String.valueOf(duration),
                        "-c", "copy",          // 直接复制编码，不重新编码（超快）
                        "-y",
                        tempOutput.toString()
                );
                pb.redirectErrorStream(true);

                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.trace("FFmpeg split: {}", line);
                    }
                }
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    result.add(Files.readAllBytes(tempOutput));
                    log.debug("Audio segment {} split: {} bytes ({}s-{}s)", i, result.get(i).length, start, end);
                } else {
                    log.warn("FFmpeg split failed for segment {} (exit={}), using full audio", i, exitCode);
                    result.add(audioBytes);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Audio split failed", e);
            return List.of();
        } finally {
            try { if (tempInput != null) Files.deleteIfExists(tempInput); } catch (IOException ignored) {}
            for (Path p : tempOutputs) {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 从 PDF 中提取所有内嵌图片，重编码为统一 PNG 格式
     *
     * @param pdfDocument PDFBox PDDocument 对象
     * @param kbId        知识库 ID
     * @param fileId      文件 ID
     * @param minioService MinioService 用于上传图片
     * @return 图片信息列表 {pageNum, imageKey}
     */
    public List<PdfImage> extractPdfImages(
            org.apache.pdfbox.pdmodel.PDDocument pdfDocument,
            String kbId, String fileId,
            com.fastrag.infra.minio.MinioService minioService) {
        List<PdfImage> result = new ArrayList<>();
        try {
            int pageCount = pdfDocument.getNumberOfPages();
            for (int pageNum = 0; pageNum < pageCount; pageNum++) {
                org.apache.pdfbox.pdmodel.PDPage page = pdfDocument.getPage(pageNum);
                org.apache.pdfbox.pdmodel.PDResources resources = page.getResources();
                int imgIndex = 0;
                for (var name : resources.getXObjectNames()) {
                    var xobject = resources.getXObject(name);
                    if (xobject instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                        org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image =
                                (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) xobject;
                        // 重编码为统一 PNG
                        BufferedImage bi = image.getImage();
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(bi, "png", baos);
                        byte[] pngBytes = baos.toByteArray();

                        // 上传到 MinIO
                        String imageKey = "page_" + (pageNum + 1) + "_img_" + imgIndex + ".png";
                        String objectKey = kbId + "/" + fileId + "/images/" + imageKey;
                        try (ByteArrayInputStream is = new ByteArrayInputStream(pngBytes)) {
                            minioService.upload(objectKey, is, "image/png");
                        }

                        result.add(PdfImage.builder()
                                .pageNum(pageNum + 1)
                                .imageKey(imageKey)
                                .width(image.getWidth())   // PDF 页面显示尺寸(CSS像素)
                                .height(image.getHeight()) // 非图片存储像素
                                .build());
                        imgIndex++;
                    }
                }
            }
            log.info("Extracted {} images from PDF ({} pages)", result.size(), pageCount);
        } catch (Exception e) {
            log.error("Failed to extract images from PDF", e);
        }
        return result;
    }

    /**
     * PDF 图片信息
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PdfImage {
        private int pageNum;       // 页码 (1-based)
        private String imageKey;   // MinIO 图片 key (如 "page_1_img_0.png")
        private int width;
        private int height;
    }

    /**
     * 关键帧数据模型
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Keyframe {
        /** 关键帧图片字节（JPEG 格式） */
        private byte[] imageBytes;
        /** 关键帧对应的时间戳（秒） */
        private double timestampSeconds;
        /** 感知哈希值（64 位十六进制字符串） */
        private String hash;
    }

    // ==================== 内部方法 ====================

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
                    ffmpegPath, "-i", tempInput.toString(),
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
     * 读取 FFmpeg 输出的关键帧文件，按序号排序后构建 Keyframe 列表
     */
    private List<Keyframe> readKeyframeFiles(Path outputDir, int intervalSeconds) throws IOException {
        List<Keyframe> keyframes = new ArrayList<>();

        // FFmpeg 输出文件名格式: keyframe_001.jpg, keyframe_002.jpg, ...
        Pattern pattern = Pattern.compile("keyframe_(\\d+)\\.jpg");

        try (Stream<Path> files = Files.list(outputDir)) {
            List<Path> jpgFiles = files
                    .filter(p -> p.getFileName().toString().endsWith(".jpg"))
                    .sorted()
                    .toList();

            for (Path jpgFile : jpgFiles) {
                Matcher matcher = pattern.matcher(jpgFile.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }

                int seqNumber = Integer.parseInt(matcher.group(1));
                double timestamp = (double) seqNumber * intervalSeconds;
                byte[] imageBytes = Files.readAllBytes(jpgFile);

                keyframes.add(Keyframe.builder()
                        .imageBytes(imageBytes)
                        .timestampSeconds(timestamp)
                        .build());
            }
        }

        log.info("Read {} keyframes from output directory", keyframes.size());
        return keyframes;
    }

    /**
     * 计算感知哈希(pHash)
     * <p>
     * 算法步骤:
     * 1. 缩放图片到 32x32
     * 2. 转为灰度图
     * 3. 执行 DCT 变换
     * 4. 取左上角 8x8 的 DCT 系数
     * 5. 计算中位数，高于中位数为 1，否则为 0
     * 6. 生成 64 位哈希字符串
     *
     * @param imageBytes JPEG 图片字节
     * @return 64 位十六进制哈希字符串
     */
    private String computePerceptualHash(byte[] imageBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img == null) {
                log.warn("Failed to read image for perceptual hash calculation");
                return "0".repeat(16);
            }

            // 缩放到 32x32
            BufferedImage scaled = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, 32, 32, null);
            g.dispose();

            // 转灰度
            double[][] gray = toGrayscale(scaled, 32, 32);

            // 执行 DCT
            double[][] dct = applyDCT(gray);

            // 取左上角 8x8
            double[] dctLow = new double[64];
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    dctLow[i * 8 + j] = dct[i][j];
                }
            }

            // 计算中位数
            double[] sorted = dctLow.clone();
            Arrays.sort(sorted);
            double median = sorted[32]; // 64 个元素的中位数

            // 生成哈希位
            StringBuilder hash = new StringBuilder();
            long hashLong = 0;
            for (int i = 0; i < 64; i++) {
                if (dctLow[i] > median) {
                    hashLong |= (1L << (63 - i));
                }
            }

            return String.format("%016x", hashLong);
        } catch (Exception e) {
            log.warn("Failed to compute perceptual hash: {}", e.getMessage());
            return "0".repeat(16);
        }
    }

    /**
     * 将 BufferedImage 转为灰度二维数组
     */
    private double[][] toGrayscale(BufferedImage img, int width, int height) {
        double[][] gray = new double[height][width];
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        BufferedImage grayImg = op.filter(img, null);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                gray[y][x] = grayImg.getRGB(x, y) & 0xFF;
            }
        }
        return gray;
    }

    /**
     * 对灰度矩阵应用 DCT（离散余弦变换）
     */
    private double[][] applyDCT(double[][] matrix) {
        int N = matrix.length;
        double[][] result = new double[N][N];

        for (int u = 0; u < N; u++) {
            for (int v = 0; v < N; v++) {
                double sum = 0.0;
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        sum += matrix[i][j]
                                * Math.cos((2.0 * i + 1.0) * u * Math.PI / (2.0 * N))
                                * Math.cos((2.0 * j + 1.0) * v * Math.PI / (2.0 * N));
                    }
                }
                double cu = (u == 0) ? 1.0 / Math.sqrt(2) : 1.0;
                double cv = (v == 0) ? 1.0 / Math.sqrt(2) : 1.0;
                result[u][v] = 0.25 * cu * cv * sum;
            }
        }
        return result;
    }

    /**
     * 计算两个十六进制哈希字符串之间的汉明距离
     *
     * @param hash1 哈希字符串
     * @param hash2 哈希字符串
     * @return 汉明距离（不同位的数量）
     */
    private int hammingDistance(String hash1, String hash2) {
        if (hash1 == null || hash2 == null || hash1.length() != hash2.length()) {
            return 64; // 最大距离
        }
        try {
            long v1 = Long.parseUnsignedLong(hash1, 16);
            long v2 = Long.parseUnsignedLong(hash2, 16);
            return Long.bitCount(v1 ^ v2);
        } catch (NumberFormatException e) {
            return 64;
        }
    }

    /**
     * 递归删除目录及其内容
     */
    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        }
    }

    /**
     * 检查 FFmpeg 是否可用
     */
    private boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder(ffmpegPath, "-version")
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
