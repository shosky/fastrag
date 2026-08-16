package com.fastrag.module.knowledge.parser;

import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DOCX 图片链路测试：parseDocx 必须提取图片字节（ParseResult.images），
 * imageKey 与 DocNode(IMAGE) 节点一致（供 IngestionConsumer 上传 MinIO，前端可访问）。
 */
class DocxImageParseTest {

    private final DocumentParserImpl parser = new DocumentParserImpl(
            null, null, null, null, null, null, null, new MarkdownSerializer(), null);

    /** 生成内容丰富的渐变 PNG 字节（通过 ImageFilter 尺寸/颜色规则，不被过滤） */
    private byte[] buildPng() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                100, 80, java.awt.image.BufferedImage.TYPE_INT_RGB);
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

    /** 构造含 2 张图片的 docx（图片位于标题段落之后，400x300px 通过 ImageFilter 过滤） */
    private byte[] buildDocxWithImages() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            // 标题段落（图片的上下文来源）
            XWPFParagraph heading = doc.createParagraph();
            heading.setStyle("Heading 1");
            heading.createRun().setText("3.2 各市产数收入完成情况");

            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun r1 = p1.createRun();
            r1.setText("第一张图：");
            r1.addPicture(new ByteArrayInputStream(buildPng()),
                    Document.PICTURE_TYPE_PNG, "img1.png", 10, 10);
            // POI addPicture(int,int) 的参数直接写入 EMU（10 EMU ≈ 0px），
            // 手工修正为真实像素对应的 EMU（400px = 3,810,000 EMU），模拟真实 Word 文件
            fixExtentToPixels(r1, 400, 300);

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun r2 = p2.createRun();
            r2.setText("第二张图：");
            r2.addPicture(new ByteArrayInputStream(buildPng()),
                    Document.PICTURE_TYPE_PNG, "img2.png", 10, 10);
            fixExtentToPixels(r2, 400, 300);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);
            return bos.toByteArray();
        }
    }

    /** 把 run 内图片的 wp:extent 修正为指定像素尺寸对应的 EMU 值 */
    private void fixExtentToPixels(XWPFRun run, int pxW, int pxH) throws Exception {
        for (XWPFPicture pic : run.getEmbeddedPictures()) {
            org.w3c.dom.Node node = (org.w3c.dom.Node) pic.getCTPicture().getDomNode();
            while (node != null) {
                if (node instanceof org.w3c.dom.Element el && "inline".equals(el.getLocalName())) {
                    org.w3c.dom.NodeList children = el.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        org.w3c.dom.Node child = children.item(i);
                        if (child instanceof org.w3c.dom.Element ce && "extent".equals(ce.getLocalName())) {
                            ce.setAttribute("cx", String.valueOf(pxW * 9525L));
                            ce.setAttribute("cy", String.valueOf(pxH * 9525L));
                            return;
                        }
                    }
                }
                node = node.getParentNode();
            }
        }
    }

    @Test
    void parseDocx_extractsImagesWithMatchingKeys() throws Exception {
        ParseResult result = parser.parse(new ByteArrayInputStream(buildDocxWithImages()), "docx", null);

        // 图片字节已提取（不再是占位符）
        assertNotNull(result.getImages());
        assertEquals(2, result.getImages().size(), "应提取 2 张图片");
        assertTrue(result.getImages().get(0).getData().length > 0, "图片字节不应为空");
        assertEquals("image/png", result.getImages().get(0).getContentType());

        // 尺寸已提取（400x300px），供消费方过滤装饰性小图
        assertEquals(400, result.getImages().get(0).getWidth());
        assertEquals(300, result.getImages().get(0).getHeight());

        // 上下文：最近标题 + 所在段落文本摘要（供图片分片的语义上下文/embedding）
        assertEquals("3.2 各市产数收入完成情况 | 第一张图：", result.getImages().get(0).getContext());
        assertEquals("3.2 各市产数收入完成情况 | 第二张图：", result.getImages().get(1).getContext());

        // imageKey 稳定命名且与 IMAGE 节点一致
        assertEquals("docx_img_0.png", result.getImages().get(0).getImageKey());
        assertEquals("docx_img_1.png", result.getImages().get(1).getImageKey());

        List<DocNode> imageNodes = result.getNodes().stream()
                .filter(n -> n.getType() == DocNode.NodeType.IMAGE)
                .toList();
        assertEquals(2, imageNodes.size());
        assertEquals("docx_img_0.png", imageNodes.get(0).getImageKey());
        assertEquals("docx_img_1.png", imageNodes.get(1).getImageKey());

        // Markdown 序列化中包含可访问的图片标记（前端据此拼接下载 URL；
        // 未设置 description 时 caption 为空串，序列化为 ![](key)）
        assertTrue(result.getText().contains("docx_img_0.png"));
        assertTrue(result.getText().contains("docx_img_1.png"));
        assertTrue(result.getText().contains("!["));
    }

    /** 解析期过滤装饰性小图（10x10px 尺寸规则）：不进入 ParseImage / IMAGE 节点 */
    @Test
    void parseDocx_filtersDecorativeSmallImages() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun r1 = p1.createRun();
            r1.setText("小图：");
            r1.addPicture(new ByteArrayInputStream(buildPng()),
                    Document.PICTURE_TYPE_PNG, "icon.png", 10, 10);
            fixExtentToPixels(r1, 10, 10);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);

            ParseResult result = parser.parse(new ByteArrayInputStream(bos.toByteArray()), "docx", null);
            assertEquals(0, result.getImages().size(), "10x10 装饰性小图应在解析期被过滤");
            boolean hasImageNode = result.getNodes().stream()
                    .anyMatch(n -> n.getType() == DocNode.NodeType.IMAGE);
            assertFalse(hasImageNode, "被过滤的图片不应生成 IMAGE 节点");
        }
    }

    /** 频次去重：同文档 3 张相同图片（模板 logo/水印）→ 解析期整组剔除 */
    @Test
    void parseDocx_deduplicatesRepeatedImages() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            byte[] logo = buildPng(); // 3 张字节完全相同
            for (int i = 0; i < 3; i++) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText("logo " + i + "：");
                r.addPicture(new ByteArrayInputStream(logo),
                        Document.PICTURE_TYPE_PNG, "logo" + i + ".png", 10, 10);
                fixExtentToPixels(r, 400, 300);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.write(bos);

            ParseResult result = parser.parse(new ByteArrayInputStream(bos.toByteArray()), "docx", null);
            assertEquals(0, result.getImages().size(), "重复 3 次的模板图应整组剔除");
            boolean hasImageNode = result.getNodes().stream()
                    .anyMatch(n -> n.getType() == DocNode.NodeType.IMAGE);
            assertFalse(hasImageNode, "被去重的图片不应残留 IMAGE 节点（避免失效引用）");
        }
    }
}
