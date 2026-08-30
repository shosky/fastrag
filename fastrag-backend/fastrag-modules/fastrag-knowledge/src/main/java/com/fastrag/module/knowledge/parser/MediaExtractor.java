package com.fastrag.module.knowledge.parser;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.Point2D;
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
     * 按给定时间范围裁剪音视频（上传向导 timeRanges）。
     * <p>
     * 使用 FFmpeg {@code -ss start -t duration} 截取指定区间，输出为原格式（重新编码，
     * 兼容 mp4/mp3 等容器的 seek 精确性）；FFmpeg 不可用或失败时返回原始字节。
     *
     * @param mediaBytes  原始音视频字节
     * @param extension   文件扩展名
     * @param start       起始时间（秒）
     * @param duration    裁剪时长（秒）
     * @return 裁剪后的字节；失败时返回原始字节
     */
    public byte[] cropMedia(byte[] mediaBytes, String extension, double start, double duration) {
        if (mediaBytes == null || mediaBytes.length == 0 || duration <= 0) {
            return mediaBytes;
        }
        if (!isFfmpegAvailable()) {
            log.warn("FFmpeg not available, cannot crop media");
            return mediaBytes;
        }

        Path tempInput = null;
        Path tempOutput = null;
        try {
            tempInput = Files.createTempFile("fastrag_crop_input_", "." + extension);
            Files.write(tempInput, mediaBytes);
            tempOutput = Files.createTempFile("fastrag_crop_output_", "." + extension);

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath, "-i", tempInput.toString(),
                    "-ss", String.valueOf(start),
                    "-t", String.valueOf(duration),
                    "-y",
                    tempOutput.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.trace("FFmpeg crop: {}", line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                byte[] result = Files.readAllBytes(tempOutput);
                log.info("Media cropped [{}, +{}s]: {} bytes (was {} bytes)", start, duration, result.length, mediaBytes.length);
                return result;
            }
            log.warn("FFmpeg crop failed (exit={}), using full media", exitCode);
            return mediaBytes;
        } catch (Exception e) {
            log.error("Media crop failed, using full media", e);
            return mediaBytes;
        } finally {
            try { if (tempInput != null) Files.deleteIfExists(tempInput); } catch (IOException ignored) {}
            try { if (tempOutput != null) Files.deleteIfExists(tempOutput); } catch (IOException ignored) {}
        }
    }

    /**
     * 从 PDF 中提取内嵌图片（过滤小图标/装饰图/页眉页脚图/重复模板图），
     * 上传到 MinIO 并返回图片信息列表。
     *
     * 过滤规则：
     * - 短边 < 100px 或 面积 < 30,000px² → 跳过（图标、装饰线，ImageFilter 统一尺寸规则）
     * - 面积 < 页面面积 1% → 跳过（页眉页脚装饰、背景纹理）
     * - 图片中心位于页面上/下部 10% 区域 → 跳过（页眉页脚 logo，位置规则）
     * - 纯色/纯透明占比 > 85% 或 颜色种类 < 16 → 跳过（纯色色块/占位符，视觉丰富度规则）
     * - PNG 编码后 < 1KB → 跳过（空白/极小图）
     * - 同文档内 MD5/pHash 重复 ≥ 3 次 → 跳过（模板 logo/全局水印，频次规则）
     * - 超大图片（全页扫描图）不过滤：保留提取 + OCR + 分片，
     *   扫描页中的表格/文字内容可通过图片 OCR 进入检索（与页文本 OCR 兜底互补）
     */
    public List<PdfImage> extractPdfImages(
            org.apache.pdfbox.pdmodel.PDDocument pdfDocument,
            String kbId, String fileId,
            com.fastrag.infra.minio.MinioService minioService) {
        List<PdfImage> result = new ArrayList<>();
        // ① 收集阶段：逐页过滤 + 重编码，先不上传（供频次去重整体判断）
        List<PdfCandidate> candidates = new ArrayList<>();
        int skippedSmall = 0;
        int skippedAreaPct = 0;
        int skippedHeaderFooter = 0;
        int skippedColor = 0;
        int skippedTinyBytes = 0;
        try {
            int pageCount = pdfDocument.getNumberOfPages();
            for (int pageNum = 0; pageNum < pageCount; pageNum++) {
                org.apache.pdfbox.pdmodel.PDPage page = pdfDocument.getPage(pageNum);
                // 获取页面尺寸（CSS像素 @96dpi）
                org.apache.pdfbox.pdmodel.common.PDRectangle pageBox = page.getMediaBox();
                float pageW = pageBox.getWidth();
                float pageH = pageBox.getHeight();
                double pageArea = pageW * pageH;

                // 采集本页内嵌图片的渲染位置（供页眉/页脚位置规则使用）
                Map<String, List<float[]>> boxesByName = collectImageBoxes(page);

                org.apache.pdfbox.pdmodel.PDResources resources = page.getResources();
                int imgIndex = 0;
                for (var name : resources.getXObjectNames()) {
                    var xobject = resources.getXObject(name);
                    if (xobject instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                        org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image =
                                (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) xobject;

                        int w = image.getWidth();
                        int h = image.getHeight();

                        // 过滤小图片（短边 < 100px 或 面积 < 30,000px²，图标/装饰线）
                        if (ImageFilter.isDecorativeBySize(w, h)) {
                            skippedSmall++;
                            continue;
                        }

                        // 过滤面积 < 页面面积 1% 的图片（大概率是装饰元素）
                        double imgArea = (double) w * h;
                        if (imgArea / pageArea < 0.01) {
                            skippedAreaPct++;
                            continue;
                        }

                        // 过滤页眉/页脚位置图片（中心位于页面上/下部 10%，且面积不超过页面 25%）
                        if (isInHeaderFooterZone(boxesByName.get(name.getName()), pageH, imgArea, pageArea)) {
                            skippedHeaderFooter++;
                            continue;
                        }

                        // 重编码为统一 PNG
                        BufferedImage bi = image.getImage();

                        // 过滤纯色/纯透明占比高或颜色种类少的图片（纯色色块/占位符，视觉丰富度规则）
                        if (ImageFilter.isDecorativeByColor(bi)) {
                            skippedColor++;
                            continue;
                        }

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(bi, "png", baos);
                        byte[] pngBytes = baos.toByteArray();

                        // 过滤编码后极小的图片（空白或噪点图）
                        if (pngBytes.length < 1024) {
                            skippedTinyBytes++;
                            continue;
                        }

                        String imageKey = "page_" + (pageNum + 1) + "_img_" + imgIndex + ".png";
                        candidates.add(new PdfCandidate(pageNum + 1, imageKey, w, h, pngBytes));
                        imgIndex++;
                    }
                }
            }

            // ② 频次去重（模板与频次规则）：同文档内重复 >= 3 次的图片（模板 logo/水印）整组过滤
            int skippedRepeated = 0;
            if (candidates.size() >= ImageDedup.REPEAT_THRESHOLD) {
                Map<String, byte[]> keyToData = new HashMap<>();
                for (PdfCandidate c : candidates) {
                    keyToData.put(c.imageKey, c.pngBytes);
                }
                Set<String> repeated = ImageDedup.findRepeatedKeys(keyToData);
                if (!repeated.isEmpty()) {
                    skippedRepeated = repeated.size();
                    candidates.removeIf(c -> repeated.contains(c.imageKey));
                }
            }

            // ③ 上传阶段：仅上传去重后保留的图片
            for (PdfCandidate c : candidates) {
                String objectKey = kbId + "/" + fileId + "/images/" + c.imageKey;
                try (ByteArrayInputStream is = new ByteArrayInputStream(c.pngBytes)) {
                    minioService.upload(objectKey, is, "image/png");
                }
                result.add(PdfImage.builder()
                        .pageNum(c.pageNum)
                        .imageKey(c.imageKey)
                        .width(c.width)
                        .height(c.height)
                        .build());
            }

            int skippedTotal = skippedSmall + skippedAreaPct + skippedHeaderFooter
                    + skippedColor + skippedTinyBytes + skippedRepeated;
            log.info("Extracted {} images from PDF ({} pages), skipped {} (small={}, areaPct={}, headerFooter={}, color={}, tinyBytes={}, repeated={})",
                    result.size(), pageCount, skippedTotal, skippedSmall, skippedAreaPct,
                    skippedHeaderFooter, skippedColor, skippedTinyBytes, skippedRepeated);
        } catch (Exception e) {
            log.error("Failed to extract images from PDF", e);
        }
        return result;
    }

    /**
     * PDF 图片提取候选（收集阶段暂存，去重后统一上传）
     */
    private record PdfCandidate(int pageNum, String imageKey, int width, int height, byte[] pngBytes) {
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
     * 提取 PDF 内容图片的渲染位置盒（供 AI 分片「原件渲染」可视化画 image 框）。
     * <p>复用 {@link #collectImageBoxes(PDPage)} 的 CTM 位置采集与页眉/页脚/装饰尺寸过滤规则；
     * 不做颜色过滤与频次去重（需逐图解码/哈希，可视化场景容忍个别装饰图多画一个框）。
     * 同一图片多处绘制时每个出现位置各产出一个盒。</p>
     * <p>坐标已从用户空间（y 向上）转换为<b>顶左原点（y 向下、单位 pt）</b>，与前端 pdf.js overlay 一致。</p>
     */
    public List<PdfImageBox> extractContentImageBoxes(org.apache.pdfbox.pdmodel.PDDocument doc) {
        List<PdfImageBox> result = new ArrayList<>();
        try {
            for (int pageNum = 0; pageNum < doc.getNumberOfPages(); pageNum++) {
                org.apache.pdfbox.pdmodel.PDPage page = doc.getPage(pageNum);
                org.apache.pdfbox.pdmodel.common.PDRectangle pageBox = page.getMediaBox();
                float pageH = pageBox.getHeight();
                double pageArea = (double) pageBox.getWidth() * pageH;
                // 顶左转换基准与文本坐标一致：用 cropBox（页面可视区），mediaBox 含装订/出血区时二者不同
                org.apache.pdfbox.pdmodel.common.PDRectangle cropBox = page.getCropBox();
                float cropX = cropBox.getLowerLeftX();
                float cropTopY = cropBox.getUpperRightY();

                Map<String, List<float[]>> boxesByName = collectImageBoxes(page);
                org.apache.pdfbox.pdmodel.PDResources resources = page.getResources();
                for (var name : resources.getXObjectNames()) {
                    var xobject = resources.getXObject(name);
                    if (!(xobject instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject image)) {
                        continue;
                    }
                    double imgArea = (double) image.getWidth() * image.getHeight();
                    // 只排除分隔线（极端宽高比）与极小图标（单边 <40px）。
                    // 不再用 isDecorativeBySize（单边<100px/面积<3万px²）与"像素面积<页面1%"过滤——
                    // 那会误杀大量小尺寸内容图（命令截图/小图表），导致原件预览无 image 框、
                    // 区域分片时图片内容缺失（用户要求小图同样可选、可随区域提取）
                    if (ImageFilter.isDecorativeByAspectRatio(image.getWidth(), image.getHeight())) continue;
                    if (Math.min(image.getWidth(), image.getHeight()) < 40) continue;
                    List<float[]> boxes = boxesByName.get(name.getName());
                    if (boxes == null || isInHeaderFooterZone(boxes, pageH, imgArea, pageArea)) continue;
                    for (float[] b : boxes) {
                        // 渲染尺寸过小（占页面 <0.05%，如 <16pt 的图标）仍视为装饰，不入框
                        double boxArea = (double) (b[2] - b[0]) * (b[3] - b[1]);
                        if (boxArea / pageArea < 0.0005) continue;
                        // [minX,minY,maxX,maxY]（用户空间，y 向上、原点为 mediaBox 左下）
                        // → cropBox 顶左原点 [x,y,w,h]（y 向下），与文本坐标同基准
                        result.add(new PdfImageBox(pageNum + 1, b[0] - cropX, cropTopY - b[3],
                                b[2] - b[0], b[3] - b[1]));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to collect content image boxes: {}", e.getMessage());
        }
        return result;
    }

    /** PDF 内容图片渲染位置盒（顶左原点、pt，page 为 1-based 页码） */
    public record PdfImageBox(int page, float x, float y, float width, float height) {
    }

    /**
     * 采集页面内嵌图片的渲染位置（页面用户空间坐标，y 轴向上，单位 pt），
     * 按图片 XObject 名称分组（同一图片多处绘制会有多个位置）。
     * <p>通过遍历页面 content stream 的 Do 操作符与 CTM 矩阵计算边界框，
     * 供页眉/页脚位置过滤使用。失败时返回空 Map（位置未知的图片不过滤）。</p>
     */
    private Map<String, List<float[]>> collectImageBoxes(org.apache.pdfbox.pdmodel.PDPage page) {
        try {
            ImagePositionEngine engine = new ImagePositionEngine(page);
            engine.processPage(page);
            return engine.getBoxesByName();
        } catch (Exception e) {
            log.warn("Failed to collect image positions: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 页眉/页脚位置过滤：图片<b>所有</b>出现位置的中心均位于页面上/下部 10% 区域，
     * 且图片面积不超过页面 25%（排除大面积图）时判定为页眉/页脚装饰。
     * 任一位置在正文区域则保留（同一图片在页眉与正文同时使用时视为内容图）。
     */
    private boolean isInHeaderFooterZone(List<float[]> boxes, float pageH,
                                         double imgArea, double pageArea) {
        if (boxes == null || boxes.isEmpty()) return false; // 位置未知 → 不过滤
        if (imgArea > pageArea * 0.25) return false;         // 大面积图不按位置过滤
        for (float[] box : boxes) {
            if (!ImageFilter.isInHeaderFooterZone(box[1], box[3], pageH)) {
                return false; // 任一位置在正文区域 → 保留
            }
        }
        return true;
    }

    /**
     * 遍历页面 content stream，采集每张内嵌图片的渲染位置（Do 操作符 + CTM）。
     * <p>图片绘制在单位方块 [0,1]x[0,1] 经 CTM 变换后的区域，取 4 角点的包围盒；
     * 同一 XObject 多处绘制会产生多个位置条目。仅用于位置采集，不渲染任何内容。</p>
     */
    static class ImagePositionEngine extends PDFGraphicsStreamEngine {

        private final Map<String, List<float[]>> boxesByName = new HashMap<>();

        ImagePositionEngine(org.apache.pdfbox.pdmodel.PDPage page) {
            super(page);
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> arguments) throws IOException {
            if ("Do".equals(operator.getName()) && !arguments.isEmpty()
                    && arguments.get(0) instanceof COSName xobjectName) {
                PDXObject xobject = getResources().getXObject(xobjectName);
                if (xobject instanceof PDImageXObject) {
                    Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
                    // 图片绘制在单位方块经 CTM 变换后的区域，取 4 角点包围盒
                    Point2D.Float p00 = ctm.transformPoint(0, 0);
                    Point2D.Float p10 = ctm.transformPoint(1, 0);
                    Point2D.Float p01 = ctm.transformPoint(0, 1);
                    Point2D.Float p11 = ctm.transformPoint(1, 1);
                    float minX = Math.min(Math.min(p00.x, p10.x), Math.min(p01.x, p11.x));
                    float maxX = Math.max(Math.max(p00.x, p10.x), Math.max(p01.x, p11.x));
                    float minY = Math.min(Math.min(p00.y, p10.y), Math.min(p01.y, p11.y));
                    float maxY = Math.max(Math.max(p00.y, p10.y), Math.max(p01.y, p11.y));
                    boxesByName.computeIfAbsent(xobjectName.getName(), k -> new ArrayList<>())
                            .add(new float[]{minX, minY, maxX, maxY});
                }
            }
            super.processOperator(operator, arguments);
        }

        Map<String, List<float[]>> getBoxesByName() {
            return boxesByName;
        }

        // ===== PDFGraphicsStreamEngine 抽象方法：本工具仅关心图片位置，其余空实现 =====

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        }

        @Override
        public void drawImage(PDImage pdImage) {
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void moveTo(float x, float y) {
        }

        @Override
        public void lineTo(float x, float y) {
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Float(0, 0);
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
        }

        @Override
        public void strokePath() {
        }

        @Override
        public void fillPath(int windingRule) {
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
        }

        @Override
        public void shadingFill(COSName shadingName) {
        }
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
     * @param imageBytes 图片字节
     * @return 64 位十六进制哈希字符串；图片无法解码时返回全 0（调用方应跳过全 0）
     */
    public static String computePerceptualHash(byte[] imageBytes) {
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
    private static double[][] toGrayscale(BufferedImage img, int width, int height) {
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
    private static double[][] applyDCT(double[][] matrix) {
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
    public static int hammingDistance(String hash1, String hash2) {
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
