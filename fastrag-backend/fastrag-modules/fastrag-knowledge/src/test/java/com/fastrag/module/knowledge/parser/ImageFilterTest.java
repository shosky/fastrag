package com.fastrag.module.knowledge.parser;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档内嵌图片统一过滤规则单元测试（ImageFilter）。
 */
class ImageFilterTest {

    // ===== 尺寸规则：任意单边 < 100px 或 面积 < 30,000px² =====

    @Test
    void sizeRule_filtersIconsAndTinyImages() {
        assertTrue(ImageFilter.isDecorativeBySize(10, 10), "10x10 图标应过滤");
        assertTrue(ImageFilter.isDecorativeBySize(99, 500), "单边 99 < 100 应过滤");
        assertTrue(ImageFilter.isDecorativeBySize(200, 140), "面积 28,000 < 30,000 应过滤");
        assertTrue(ImageFilter.isDecorativeBySize(173, 173), "面积 29,929 < 30,000 应过滤");
    }

    @Test
    void sizeRule_keepsRealContentImages() {
        assertFalse(ImageFilter.isDecorativeBySize(400, 300), "400x300 正文图应保留");
        assertFalse(ImageFilter.isDecorativeBySize(200, 200), "200x200 面积 40,000 应保留");
        assertFalse(ImageFilter.isDecorativeBySize(120, 400), "窄高正文图（面积 48,000）应保留");
    }

    @Test
    void sizeRule_unknownDimensionsNotFiltered() {
        assertFalse(ImageFilter.isDecorativeBySize(0, 100), "尺寸未知不按尺寸规则过滤");
        assertFalse(ImageFilter.isDecorativeBySize(-1, -1), "尺寸非法不按尺寸规则过滤");
    }

    // ===== 宽高比规则：> 8:1 或 < 1:8 =====

    @Test
    void aspectRatioRule_filtersSeparatorLines() {
        assertTrue(ImageFilter.isDecorativeByAspectRatio(1000, 50), "横向分隔线 20:1 应过滤");
        assertTrue(ImageFilter.isDecorativeByAspectRatio(50, 1000), "纵向分隔线 1:20 应过滤");
    }

    @Test
    void aspectRatioRule_keepsNormalImages() {
        assertFalse(ImageFilter.isDecorativeByAspectRatio(600, 400), "1.5:1 常规图应保留");
        assertFalse(ImageFilter.isDecorativeByAspectRatio(800, 100), "恰好 8:1 边界不过滤（严格大于）");
    }

    // ===== 文件大小规则：原始字节 < 15KB（尺寸未知时的兜底） =====

    @Test
    void fileSizeRule_filtersTinyBytes() {
        assertTrue(ImageFilter.isDecorativeByFileSize(new byte[70]), "70 字节极小图应过滤");
        assertTrue(ImageFilter.isDecorativeByFileSize(new byte[15 * 1024 - 1]), "15KB-1 应过滤");
    }

    @Test
    void fileSizeRule_keepsNormalBytes() {
        assertFalse(ImageFilter.isDecorativeByFileSize(new byte[15 * 1024]), "恰好 15KB 不过滤");
        assertFalse(ImageFilter.isDecorativeByFileSize(new byte[100 * 1024]), "100KB 正常图应保留");
    }

    // ===== 组合规则（解析期使用）：尺寸已知走尺寸/宽高比，未知走字节 =====

    @Test
    void combinedRule_prefersDimensionRulesWhenKnown() {
        // 尺寸已知（400x300 合法）但字节极小 → 不按字节规则过滤（避免误杀小体积真实内容图）
        assertFalse(ImageFilter.isDecorative(400, 300, new byte[70]));
        // 尺寸已知且命中尺寸规则 → 过滤
        assertTrue(ImageFilter.isDecorative(10, 10, new byte[50 * 1024]));
        // 尺寸已知且命中宽高比规则 → 过滤
        assertTrue(ImageFilter.isDecorative(1000, 50, new byte[50 * 1024]));
    }

    @Test
    void combinedRule_usesBytesAsFallbackWhenDimensionsUnknown() {
        assertTrue(ImageFilter.isDecorative(null, null, new byte[70]), "尺寸未知 + 极小字节应过滤");
        assertTrue(ImageFilter.isDecorative(null, 300, new byte[70]), "单边未知 + 极小字节应过滤");
        assertFalse(ImageFilter.isDecorative(null, null, new byte[100 * 1024]), "尺寸未知但字节正常应保留");
    }

    // ===== 位置规则（PDF）：中心位于页面上/下部 10% 区域 =====

    @Test
    void positionRule_detectsHeaderAndFooter() {
        float pageH = 842f; // A4 高度（pt）
        // 页眉：中心 y > 0.9 * 842 = 757.8
        assertTrue(ImageFilter.isInHeaderFooterZone(760, 830, pageH), "页眉 logo 应识别");
        // 页脚：中心 y < 84.2
        assertTrue(ImageFilter.isInHeaderFooterZone(10, 60, pageH), "页脚装饰应识别");
    }

    @Test
    void positionRule_keepsBodyImages() {
        float pageH = 842f;
        assertFalse(ImageFilter.isInHeaderFooterZone(200, 600, pageH), "正文中部图片应保留");
        assertFalse(ImageFilter.isInHeaderFooterZone(100, 760, pageH), "跨正文大图应保留");
    }

    // ===== 视觉丰富度规则：颜色种类 < 16 或 单色/纯透明占比 > 85% =====

    /** 生成 PNG 字节 */
    private byte[] encodePng(BufferedImage img) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    @Test
    void colorRule_filtersSolidColorAndPlaceholder() throws Exception {
        // 纯色块（1 色）
        BufferedImage solid = new BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g1 = solid.createGraphics();
        g1.setColor(Color.RED);
        g1.fillRect(0, 0, 200, 150);
        g1.dispose();
        assertTrue(ImageFilter.isDecorativeByColor(encodePng(solid)), "纯色块应过滤");

        // 白底黑字（2 色，主色占比 > 85%）
        BufferedImage text = new BufferedImage(400, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = text.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, 400, 100);
        g2.setColor(Color.BLACK);
        g2.fillRect(50, 40, 300, 10); // 模拟一行文字
        g2.dispose();
        assertTrue(ImageFilter.isDecorativeByColor(encodePng(text)), "白底黑字（<16 色且单色主导）应过滤");

        // 纯透明图
        BufferedImage transparent = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        assertTrue(ImageFilter.isDecorativeByColor(encodePng(transparent)), "纯透明图应过滤");
    }

    @Test
    void colorRule_keepsRichImages() throws Exception {
        // 横向渐变（量化后多色，主色占比低）
        BufferedImage gradient = new BufferedImage(256, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g1 = gradient.createGraphics();
        g1.setPaint(new GradientPaint(0, 0, Color.RED, 256, 0, Color.BLUE));
        g1.fillRect(0, 0, 256, 128);
        g1.dispose();
        assertFalse(ImageFilter.isDecorativeByColor(encodePng(gradient)), "渐变图（多色）应保留");

        // 随机噪点图（颜色种类极多）
        BufferedImage noise = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(42);
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                noise.setRGB(x, y, rand.nextInt(0xFFFFFF));
            }
        }
        assertFalse(ImageFilter.isDecorativeByColor(encodePng(noise)), "噪点图（多色）应保留");
    }

    @Test
    void colorRule_ignoresUndecodableAndOversizeBytes() {
        assertFalse(ImageFilter.isDecorativeByColor("not-an-image".getBytes()), "解码失败不按颜色规则过滤");
        assertFalse(ImageFilter.isDecorativeByColor((byte[]) null), "null 不按颜色规则过滤");
        assertFalse(ImageFilter.isDecorativeByColor(new byte[600 * 1024]), "超过检查上限的大图跳过颜色规则");
    }
}
