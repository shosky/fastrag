package com.fastrag.module.knowledge.parser;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图片频次去重单元测试（ImageDedup）：
 * 同文档内 MD5/pHash 重复 >= 3 次的图片整组过滤（模板 logo/全局水印）。
 */
class ImageDedupTest {

    /** 生成内容丰富（渐变）的 PNG，避免被尺寸/颜色规则干扰 */
    private byte[] buildRichPng(int width, int height, boolean horizontal) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, Color.RED,
                horizontal ? width : 0, horizontal ? 0 : height, Color.BLUE));
        g.fillRect(0, 0, width, height);
        // 叠加形状增加结构
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(width / 4.0, height / 4.0, width / 2.0, height / 2.0));
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    /** 生成随机噪点图（每张内容不同） */
    private byte[] buildNoisePng(int seed) throws Exception {
        BufferedImage img = new BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(seed);
        for (int y = 0; y < 150; y++) {
            for (int x = 0; x < 200; x++) {
                img.setRGB(x, y, rand.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    @Test
    void md5ExactDuplicates_threshold3_filtered() throws Exception {
        byte[] png = buildRichPng(300, 200, true);
        Map<String, byte[]> map = new HashMap<>();
        map.put("logo1", png);
        map.put("logo2", png);
        map.put("logo3", png);
        assertEquals(Set.of("logo1", "logo2", "logo3"), ImageDedup.findRepeatedKeys(map),
                "MD5 完全相同的 3 张图应整组过滤");
    }

    @Test
    void md5Duplicates_belowThreshold_kept() throws Exception {
        byte[] png = buildRichPng(300, 200, true);
        Map<String, byte[]> map = new HashMap<>();
        map.put("a", png);
        map.put("b", png);
        map.put("c", buildRichPng(300, 200, false)); // 不同图
        assertTrue(ImageDedup.findRepeatedKeys(map).isEmpty(), "重复 2 次低于阈值应保留");
    }

    @Test
    void phashSimilarDuplicates_filtered() throws Exception {
        // 同一渐变图：PNG / JPEG 编码 / 80% 缩放 PNG → 字节不同但感知哈希相近
        BufferedImage base = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = base.createGraphics();
        g.setPaint(new GradientPaint(0, 0, Color.RED, 300, 0, Color.BLUE));
        g.fillRect(0, 0, 300, 200);
        g.setColor(Color.WHITE);
        g.fillOval(80, 40, 140, 120);
        g.dispose();

        ByteArrayOutputStream pngBos = new ByteArrayOutputStream();
        ImageIO.write(base, "png", pngBos);
        ByteArrayOutputStream jpgBos = new ByteArrayOutputStream();
        ImageIO.write(base, "jpg", jpgBos);

        BufferedImage small = new BufferedImage(240, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = small.createGraphics();
        g2.drawImage(base, 0, 0, 240, 160, null);
        g2.dispose();
        ByteArrayOutputStream smallBos = new ByteArrayOutputStream();
        ImageIO.write(small, "png", smallBos);

        Map<String, byte[]> map = new HashMap<>();
        map.put("a", pngBos.toByteArray());
        map.put("b", jpgBos.toByteArray());
        map.put("c", smallBos.toByteArray());
        assertEquals(Set.of("a", "b", "c"), ImageDedup.findRepeatedKeys(map),
                "同一图的不同编码/缩放应被 pHash 聚类过滤");
    }

    @Test
    void differentImages_kept() throws Exception {
        Map<String, byte[]> map = new HashMap<>();
        map.put("a", buildNoisePng(1));
        map.put("b", buildNoisePng(2));
        map.put("c", buildNoisePng(3));
        assertTrue(ImageDedup.findRepeatedKeys(map).isEmpty(), "3 张不同图片不应过滤");
    }

    @Test
    void undecodableBytes_notClustered() {
        // 无法解码的字节（模拟 EMF/WMF 矢量格式）：pHash 返回全 0 不参与聚类，不误判
        Map<String, byte[]> map = new HashMap<>();
        map.put("a", new byte[]{1});
        map.put("b", new byte[]{2});
        map.put("c", new byte[]{3});
        assertTrue(ImageDedup.findRepeatedKeys(map).isEmpty(), "不可解码图片不应被 pHash 聚类误判");
    }

    @Test
    void belowThresholdSize_returnsEmpty() {
        Map<String, byte[]> map = Map.of("a", new byte[]{1}, "b", new byte[]{2});
        assertTrue(ImageDedup.findRepeatedKeys(map).isEmpty());
        assertTrue(ImageDedup.findRepeatedKeys(null).isEmpty());
    }
}
