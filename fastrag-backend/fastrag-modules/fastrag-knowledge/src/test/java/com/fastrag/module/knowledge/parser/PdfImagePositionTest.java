package com.fastrag.module.knowledge.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PDF 图片位置提取链路测试：ImagePositionEngine 通过 content stream CTM
 * 计算图片渲染位置，供页眉/页脚（上/下部 10% 区域）过滤规则使用。
 */
class PdfImagePositionTest {

    private byte[] buildPng() throws Exception {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    @Test
    void positionEngine_distinguishesHeaderAndBodyImages() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4); // 595 x 842 pt
            doc.addPage(page);
            PDImageXObject xobj = PDImageXObject.createFromByteArray(doc, buildPng(), "img");

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // 页眉：y 800..830，中心 815 > 0.9*842=757.8（顶部 10% 区域）
                cs.drawImage(xobj, 50, 800, 100, 30);
                // 正文：y 300..500，中心 400（正文区域）
                cs.drawImage(xobj, 50, 300, 200, 200);
            }

            MediaExtractor.ImagePositionEngine engine = new MediaExtractor.ImagePositionEngine(page);
            engine.processPage(page);
            Map<String, List<float[]>> boxesByName = engine.getBoxesByName();

            assertEquals(1, boxesByName.size(), "同一 XObject 多处绘制合并为一个条目");
            List<float[]> boxes = boxesByName.values().iterator().next();
            assertEquals(2, boxes.size(), "同一图片两处绘制产生两个位置");

            // 页眉位置：图片边界框与绘制区域一致（y 800..830）
            float[] header = boxes.get(0);
            assertEquals(50, header[0], 0.5);
            assertEquals(800, header[1], 0.5);
            assertEquals(150, header[2], 0.5);
            assertEquals(830, header[3], 0.5);
            assertTrue(ImageFilter.isInHeaderFooterZone(header[1], header[3], 842f),
                    "页眉图片应命中位置规则");

            // 正文位置：不命中位置规则
            float[] body = boxes.get(1);
            assertFalse(ImageFilter.isInHeaderFooterZone(body[1], body[3], 842f),
                    "正文图片不应命中位置规则");
        }
    }
}
