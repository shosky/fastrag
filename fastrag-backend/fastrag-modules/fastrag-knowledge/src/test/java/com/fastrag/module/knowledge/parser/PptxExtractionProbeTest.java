package com.fastrag.module.knowledge.parser;

import org.apache.poi.xslf.usermodel.*;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PPT 结构化解析验证：parsePptx 遍历 slide shapes 生成 DocNode
 * （HEADING + TABLE + PARAGRAPH + IMAGE），表格复用 isHeaderRow，图片进入 ParseImage 链路。
 */
class PptxExtractionProbeTest {

    private final DocumentParserImpl parser = new DocumentParserImpl(
            null, null, null, null, null, null, null, new MarkdownSerializer(), null);

    private byte[] buildPng() throws Exception {
        BufferedImage img = new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, java.awt.Color.RED, 100, 0, java.awt.Color.BLUE));
        g.fillRect(0, 0, 100, 80);
        g.setColor(java.awt.Color.WHITE);
        g.fillOval(20, 10, 50, 40);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    /** 构造含表格 + 文本框 + 图片的 pptx */
    private byte[] buildPptx() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFSlide slide = ppt.createSlide();

            // 表格：2 行 3 列（含表头）
            XSLFTable table = slide.createTable(2, 3);
            table.getCell(0, 0).setText("季度");
            table.getCell(0, 1).setText("营收(万)");
            table.getCell(0, 2).setText("利润(万)");
            table.getCell(1, 0).setText("Q1");
            table.getCell(1, 1).setText("1520");
            table.getCell(1, 2).setText("540");

            // 普通文本框
            XSLFTextBox box = slide.createTextBox();
            box.setText("自定义文本框：产数收入完成情况");

            // 图片（显式设置 anchor，POI 单位为 points：300x225pt ≈ 400x300px @96dpi，
            // 通过 ImageFilter 解析期过滤；不设置时 anchor 缺失，尺寸未知将按字节 < 15KB 兜底过滤掉 1x1 PNG）
            XSLFPictureData picData = ppt.addPicture(buildPng(),
                    org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
            XSLFPictureShape picShape = slide.createPicture(picData);
            picShape.setAnchor(new java.awt.geom.Rectangle2D.Double(0, 0, 300, 225));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ppt.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    void parsePptx_structuredNodes() throws Exception {
        ParseResult result = parser.parse(new ByteArrayInputStream(buildPptx()), "pptx", null);

        // 结构化节点：HEADING(页边界) + TABLE + PARAGRAPH + IMAGE
        assertNotNull(result.getNodes());
        List<DocNode> nodes = result.getNodes();
        assertEquals(4, nodes.size(), () -> "nodes=" + nodes);

        // 页边界 HEADING
        assertEquals(DocNode.NodeType.HEADING, nodes.get(0).getType());
        assertEquals("第 1 页", nodes.get(0).getTitle());

        // 表格：表头识别（复用 isHeaderRow）
        DocNode table = nodes.get(1);
        assertEquals(DocNode.NodeType.TABLE, table.getType());
        assertEquals(List.of("季度", "营收(万)", "利润(万)"), table.getHeaders());
        assertEquals(List.of("Q1", "1520", "540"), table.getRows().get(0));

        // 文本框 → PARAGRAPH
        DocNode para = nodes.get(2);
        assertEquals(DocNode.NodeType.PARAGRAPH, para.getType());
        assertTrue(para.getContent().contains("自定义文本框"));

        // 图片 → IMAGE 节点 + ParseImage（上传 + OCR 由消费方完成）
        DocNode imgNode = nodes.get(3);
        assertEquals(DocNode.NodeType.IMAGE, imgNode.getType());
        assertEquals("pptx_img_0.png", imgNode.getImageKey());

        assertNotNull(result.getImages());
        assertEquals(1, result.getImages().size());
        ParseResult.ParseImage img = result.getImages().get(0);
        assertEquals("pptx_img_0.png", img.getImageKey());
        assertTrue(img.getData().length > 0);
        assertEquals("第 1 页", img.getContext());
        assertEquals(0, img.getPageNumber()); // slide 序号
        // anchor 尺寸已提取（供 ImageFilter 过滤装饰性小图）
        assertEquals(400, img.getWidth());
        assertEquals(300, img.getHeight());
    }

    /** 解析期过滤装饰性小图（anchor 未知 + 小字节 PNG < 15KB）：不进入 ParseImage */
    @Test
    void parsePptx_filtersDecorativeTinyImages() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFSlide slide = ppt.createSlide();
            XSLFPictureData picData = ppt.addPicture(buildPng(),
                    org.apache.poi.sl.usermodel.PictureData.PictureType.PNG);
            // 不设置 anchor（尺寸未知），渐变 PNG 字节 < 15KB → 按字节规则过滤
            slide.createPicture(picData);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ppt.write(bos);

            ParseResult result = parser.parse(new ByteArrayInputStream(bos.toByteArray()), "pptx", null);
            assertEquals(0, result.getImages().size(), "尺寸未知的极小图应在解析期被过滤");
            boolean hasImageNode = result.getNodes().stream()
                    .anyMatch(n -> n.getType() == DocNode.NodeType.IMAGE);
            assertFalse(hasImageNode, "被过滤的图片不应生成 IMAGE 节点");
        }
    }
}
