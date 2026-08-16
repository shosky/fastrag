package com.fastrag.module.knowledge.parser;

import com.fastrag.infra.minio.MinioService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PDF 图片提取链路测试（extractPdfImages）：
 * 位置规则（页眉/页脚）、频次去重（重复 >= 3 次）、上传计数。
 */
class PdfImageExtractionTest {

    /** 记录上传 key 的 Fake MinioService（覆写 upload 计数，不落盘） */
    static class CountingMinioService extends MinioService {
        final List<String> uploadedKeys = new ArrayList<>();

        @Override
        public String upload(String objectKey, InputStream stream, String contentType) {
            uploadedKeys.add(objectKey);
            return objectKey;
        }
    }

    /** 生成内容丰富（渐变 + 图形）的 PNG，避免被尺寸/颜色规则过滤 */
    private byte[] buildRichPng(int width, int height, boolean horizontal) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, Color.RED,
                horizontal ? width : 0, horizontal ? 0 : height, Color.BLUE));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(width / 4.0, height / 4.0, width / 2.0, height / 2.0));
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    /** 生成随机噪点图（每张内容不同） */
    private byte[] buildNoisePng(int seed) throws Exception {
        BufferedImage img = new BufferedImage(250, 150, BufferedImage.TYPE_INT_RGB);
        Random rand = new Random(seed);
        for (int y = 0; y < 150; y++) {
            for (int x = 0; x < 250; x++) {
                img.setRGB(x, y, rand.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    /** 创建单页 PDF（content 中绘制一张图） */
    private PDDocument buildPdf(byte[] png, float x, float y, float w, float h) throws Exception {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4); // 595 x 842 pt
        doc.addPage(page);
        PDImageXObject xobj = PDImageXObject.createFromByteArray(doc, png, "img");
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(xobj, x, y, w, h);
        }
        return doc;
    }

    /** 创建 N 页 PDF（每页相同位置绘制同一张图，模拟模板 logo 重复） */
    private PDDocument buildMultiPagePdf(byte[] png, int pages, float x, float y, float w, float h) throws Exception {
        PDDocument doc = new PDDocument();
        for (int i = 0; i < pages; i++) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDImageXObject xobj = PDImageXObject.createFromByteArray(doc, png, "img");
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(xobj, x, y, w, h);
            }
        }
        return doc;
    }

    @Test
    void headerLogo_filteredByPositionRule() throws Exception {
        // 250x150 图绘制在页眉区域（y 690..840，中心 765 > 0.9*842=757.8），面积 37,500 < 页面 25%
        byte[] png = buildRichPng(250, 150, true);
        try (PDDocument doc = buildPdf(png, 50, 690, 250, 150)) {
            CountingMinioService minio = new CountingMinioService();
            List<MediaExtractor.PdfImage> images =
                    new MediaExtractor().extractPdfImages(doc, "kb1", "f1", minio);
            assertTrue(images.isEmpty(), "页眉区域图片应被位置规则过滤");
            assertTrue(minio.uploadedKeys.isEmpty(), "页眉 logo 不应上传到 MinIO");
        }
    }

    @Test
    void bodyImage_keptAndUploaded() throws Exception {
        // 250x150 噪点图绘制在正文（y 300..450，中心 375）
        byte[] png = buildNoisePng(7);
        try (PDDocument doc = buildPdf(png, 50, 300, 250, 150)) {
            CountingMinioService minio = new CountingMinioService();
            List<MediaExtractor.PdfImage> images =
                    new MediaExtractor().extractPdfImages(doc, "kb1", "f1", minio);
            assertEquals(1, images.size(), "正文内容图应保留");
            assertEquals(1, minio.uploadedKeys.size(), "正文内容图应上传");
            assertEquals("kb1/f1/images/page_1_img_0.png", minio.uploadedKeys.get(0));
        }
    }

    @Test
    void repeatedImages_deduplicatedByFrequency() throws Exception {
        // 3 页正文相同图片（MD5 相同，重复 >= 3 次）→ 整组过滤，不上传
        byte[] png = buildRichPng(250, 150, true);
        try (PDDocument doc = buildMultiPagePdf(png, 3, 50, 300, 250, 150)) {
            CountingMinioService minio = new CountingMinioService();
            List<MediaExtractor.PdfImage> images =
                    new MediaExtractor().extractPdfImages(doc, "kb1", "f1", minio);
            assertTrue(images.isEmpty(), "重复 3 次的图片应整组过滤");
            assertTrue(minio.uploadedKeys.isEmpty(), "重复图片不应上传");
        }
    }

    @Test
    void repeatedPlusUnique_keepsUniqueOnly() throws Exception {
        // 3 页相同图 + 1 页不同图 → 仅上传不同的 1 张
        byte[] same = buildRichPng(250, 150, true);
        byte[] unique = buildNoisePng(11);
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < 3; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                PDImageXObject xobj = PDImageXObject.createFromByteArray(doc, same, "img");
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(xobj, 50, 300, 250, 150);
                }
            }
            // 第 4 页：不同的图
            PDPage page4 = new PDPage(PDRectangle.A4);
            doc.addPage(page4);
            PDImageXObject xobj4 = PDImageXObject.createFromByteArray(doc, unique, "img");
            try (PDPageContentStream cs = new PDPageContentStream(doc, page4)) {
                cs.drawImage(xobj4, 50, 300, 250, 150);
            }

            CountingMinioService minio = new CountingMinioService();
            List<MediaExtractor.PdfImage> images =
                    new MediaExtractor().extractPdfImages(doc, "kb1", "f1", minio);
            assertEquals(1, images.size(), "重复 3 次 + 1 张不同 → 仅保留不同图");
            assertEquals(1, minio.uploadedKeys.size());
        }
    }

    @Test
    void twoRepeatedImages_belowThreshold_kept() throws Exception {
        // 2 页相同图（< 3 次）→ 保留并上传
        byte[] png = buildRichPng(250, 150, true);
        try (PDDocument doc = buildMultiPagePdf(png, 2, 50, 300, 250, 150)) {
            CountingMinioService minio = new CountingMinioService();
            List<MediaExtractor.PdfImage> images =
                    new MediaExtractor().extractPdfImages(doc, "kb1", "f1", minio);
            assertEquals(2, images.size(), "重复 2 次低于阈值应保留");
            assertEquals(2, minio.uploadedKeys.size());
        }
    }

    @Test
    void solidColorImage_filteredByColorRule() throws Exception {
        // 纯色图（1 色）→ 视觉丰富度规则过滤
        BufferedImage img = new BufferedImage(250, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 250, 150);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);

        try (PDDocument doc = buildPdf(bos.toByteArray(), 50, 300, 250, 150)) {
            CountingMinioService minio = new CountingMinioService();
            List<MediaExtractor.PdfImage> images =
                    new MediaExtractor().extractPdfImages(doc, "kb1", "f1", minio);
            assertTrue(images.isEmpty(), "纯色块应被视觉丰富度规则过滤");
            assertTrue(minio.uploadedKeys.isEmpty());
        }
    }
}
