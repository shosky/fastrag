package com.fastrag.module.knowledge.parser;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 文档内嵌图片统一过滤规则（解析期与消费端共用，防止装饰性图片进入 MinIO / OCR / 分片）。
 *
 * <p>规则（与 FastRAG 图片过滤规范对齐）：</p>
 * <ol>
 *   <li><b>像素尺寸</b>：任意单边 &lt; 100px 或 面积 &lt; 30,000px² → 图标/装饰线（尺寸未知时跳过本条）</li>
 *   <li><b>极端宽高比</b>：宽/高 &gt; 8:1 或 &lt; 1:8 → 横向/纵向分隔线（尺寸未知时跳过本条）</li>
 *   <li><b>文件大小</b>：原始字节 &lt; 15KB → 极小图（空白/纯色噪点）。
 *       仅作为<b>尺寸未知</b>（EMU/anchor 提取失败）时的兜底规则，避免误杀小体积的真实内容图</li>
 *   <li><b>位置规则（PDF）</b>：图片中心位于页面上部/下部 10% 区域 → 页眉/页脚 logo</li>
 *   <li><b>视觉丰富度</b>：颜色种类 &lt; 16 或 单色/纯透明占比 &gt; 85% → 纯色色块/占位符
 *       （64x64 采样 + 每通道量化到 5bit 后统计）</li>
 * </ol>
 *
 * <p>注：尺寸均为 CSS 像素（@96dpi），DOCX/PPT 由 EMU 换算（1px = 9525 EMU），PDF 为原始像素。</p>
 */
public final class ImageFilter {

    /** 任意单边 < 该值（px）视为装饰性小图（图标/按钮/装饰线） */
    public static final int MIN_SIDE_PX = 100;
    /** 面积 < 该值（px²）视为装饰性小图 */
    public static final long MIN_AREA_PX2 = 30_000L;
    /** 尺寸未知时，原始字节 < 该值视为极小图（空白/纯色噪点） */
    public static final int MIN_FILE_SIZE_BYTES = 15 * 1024;
    /** 极端宽高比阈值（宽/高 &gt; 该值 或 &lt; 1/该值 视为分隔线） */
    public static final double MAX_ASPECT_RATIO = 8.0;
    /** PDF 页眉/页脚区域比例（页面上部/下部各占页高比例，中心落入即视为页眉页脚） */
    public static final double HEADER_FOOTER_ZONE_RATIO = 0.1;
    /** 视觉丰富度：颜色种类 < 该值视为纯色色块/占位符 */
    public static final int MIN_COLOR_COUNT = 16;
    /** 视觉丰富度：单色/纯透明像素占比 > 该值视为纯色色块/占位符 */
    public static final double MAX_DOMINANT_RATIO = 0.85;
    /** 视觉丰富度颜色统计的采样尺寸（缩放至 NxN 后统计，控制成本） */
    private static final int COLOR_SAMPLE_SIZE = 64;
    /** 视觉丰富度颜色量化位数（每通道 8bit 右移该位数 → 5bit，共 32³ 空间） */
    private static final int COLOR_QUANTIZE_SHIFT = 3;
    /** 超过该字节数的图片跳过颜色检查（大图内容丰富的概率极高，避免大图解码开销） */
    private static final int MAX_COLOR_CHECK_BYTES = 512 * 1024;
    /** 透明像素 alpha 阈值（低于该值视为纯透明） */
    private static final int TRANSPARENT_ALPHA = 16;

    private ImageFilter() {
    }

    /**
     * 尺寸规则：任意单边 < 100px 或 面积 < 30,000px² → 装饰性小图。
     *
     * @return 尺寸未知（<= 0）时返回 false，交由字节大小规则兜底
     */
    public static boolean isDecorativeBySize(int width, int height) {
        if (width <= 0 || height <= 0) return false;
        int minSide = Math.min(width, height);
        return minSide < MIN_SIDE_PX || (long) width * height < MIN_AREA_PX2;
    }

    /**
     * 宽高比规则：宽/高 > 8:1 或 < 1:8 → 横向/纵向分隔线。
     *
     * @return 尺寸未知（<= 0）时返回 false
     */
    public static boolean isDecorativeByAspectRatio(int width, int height) {
        if (width <= 0 || height <= 0) return false;
        double ratio = (double) Math.max(width, height) / Math.min(width, height);
        return ratio > MAX_ASPECT_RATIO;
    }

    /**
     * 文件大小规则：原始字节 < 15KB → 极小图（空白/纯色噪点）。
     */
    public static boolean isDecorativeByFileSize(byte[] data) {
        return data != null && data.length < MIN_FILE_SIZE_BYTES;
    }

    /**
     * 组合规则（DOCX/DOC/PPTX 解析期使用）：
     * <ul>
     *   <li>尺寸已知：按 尺寸 + 宽高比 规则判断；</li>
     *   <li>尺寸未知（EMU/anchor 提取失败）：按 原始字节 &lt; 15KB 兜底。</li>
     * </ul>
     */
    public static boolean isDecorative(Integer width, Integer height, byte[] data) {
        if (width != null && height != null && width > 0 && height > 0) {
            return isDecorativeBySize(width, height) || isDecorativeByAspectRatio(width, height);
        }
        return isDecorativeByFileSize(data);
    }

    /**
     * 位置规则（PDF）：图片中心是否位于页面上部/下部 10% 区域（y 轴向上，单位 pt）。
     *
     * @param minY       图片边界框最小 y（页面用户空间坐标）
     * @param maxY       图片边界框最大 y
     * @param pageHeight 页面高度
     */
    public static boolean isInHeaderFooterZone(float minY, float maxY, float pageHeight) {
        if (pageHeight <= 0) return false;
        float centerY = (minY + maxY) / 2f;
        float zone = (float) (HEADER_FOOTER_ZONE_RATIO * pageHeight);
        return centerY > pageHeight - zone || centerY < zone;
    }

    /**
     * 视觉丰富度规则（字节重载）：解码图片后判定颜色种类 &lt; 16 或 单色/纯透明占比 &gt; 85%。
     * <p>超过 {@link #MAX_COLOR_CHECK_BYTES} 的大图跳过检查（内容丰富的概率极高，节省解码开销）；
     * 解码失败（EMF/WMF/SVG 等矢量格式）不按颜色规则过滤。</p>
     */
    public static boolean isDecorativeByColor(byte[] data) {
        if (data == null || data.length > MAX_COLOR_CHECK_BYTES) return false;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
            return img != null && isDecorativeByColor(img);
        } catch (Exception e) {
            return false; // 解码失败不按颜色规则过滤
        }
    }

    /**
     * 视觉丰富度规则（BufferedImage 重载，PDF 已解码时直接复用）：
     * 缩放至 64x64 采样，每通道量化到 5bit 后统计：
     * <ul>
     *   <li>纯透明像素占比 &gt; 85% → 过滤；</li>
     *   <li>量化后颜色种类 &lt; 16 → 过滤（纯色色块/占位符）；</li>
     *   <li>主色（最频繁量化色）占比 &gt; 85% → 过滤（白底黑字等单色为主图）。</li>
     * </ul>
     */
    public static boolean isDecorativeByColor(BufferedImage img) {
        if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) return false;

        // 缩放采样（双线性），降低统计成本且对压缩噪声鲁棒
        BufferedImage scaled = new BufferedImage(COLOR_SAMPLE_SIZE, COLOR_SAMPLE_SIZE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, COLOR_SAMPLE_SIZE, COLOR_SAMPLE_SIZE, null);
        g.dispose();

        int total = COLOR_SAMPLE_SIZE * COLOR_SAMPLE_SIZE;
        int transparent = 0;
        Map<Integer, Integer> colorCount = new HashMap<>();
        for (int y = 0; y < COLOR_SAMPLE_SIZE; y++) {
            for (int x = 0; x < COLOR_SAMPLE_SIZE; x++) {
                int argb = scaled.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) < TRANSPARENT_ALPHA) {
                    transparent++;
                    continue;
                }
                // 每通道右移 3bit 量化（8bit → 5bit），对 JPEG 压缩噪声鲁棒
                int q = (((argb >> 19) & 0x1F) << 10)
                        | (((argb >> 11) & 0x1F) << 5)
                        | ((argb >> 3) & 0x1F);
                colorCount.merge(q, 1, Integer::sum);
            }
        }

        // 纯透明占比 > 85%（或全透明）
        if ((double) transparent / total > MAX_DOMINANT_RATIO) return true;
        if (colorCount.isEmpty()) return true;
        // 颜色种类 < 16 → 纯色色块/占位符
        if (colorCount.size() < MIN_COLOR_COUNT) return true;
        // 单色占比 > 85% → 纯色背景为主的图
        int dominant = 0;
        for (int count : colorCount.values()) {
            dominant = Math.max(dominant, count);
        }
        return (double) dominant / total > MAX_DOMINANT_RATIO;
    }
}
