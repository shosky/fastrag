package com.fastrag.module.knowledge.service.impl;

import com.fastrag.module.knowledge.model.AiChunkLayoutBlock;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PDFBox 几何版面分析测试：确定性几何聚类 + 真实 PDFBox 生成 PDF 的端到端分类。
 */
class PdfLayoutAnalyzerTest {

    private static final PDType1Font HELVETICA = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font HELVETICA_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font COURIER = new PDType1Font(Standard14Fonts.FontName.COURIER);

    private static PdfLayoutAnalyzer.Line line(float x, float y, float w, float h, String t, float size, String font) {
        return new PdfLayoutAnalyzer.Line(new float[]{x, y, w, h}, t, size, font);
    }

    @Test
    void titleAndParagraphClassifiedDeterministically() {
        List<PdfLayoutAnalyzer.Line> lines = List.of(
                line(50f, 30f, 200f, 20f, "1.1 产品概述", 18f, "Helvetica-Bold"),
                line(50f, 60f, 300f, 14f, "正文第一行内容", 10f, "Helvetica"),
                line(50f, 76f, 300f, 14f, "正文第二行继续", 10f, "Helvetica"));
        List<AiChunkLayoutBlock> blocks = PdfLayoutAnalyzer.analyze(1, 612f, 792f, lines, List.of());
        // 标题（字号 18 ≥ 中位数 10×1.25 且短行）+ 正文段（两行同列近距离归一段）
        assertEquals(2, blocks.size());
        assertEquals("title", blocks.get(0).getType());
        assertEquals("text", blocks.get(1).getType());
        assertTrue(blocks.get(1).getY() - blocks.get(0).getY() > 0, "块应按 y 页首→页尾排序");
    }

    @Test
    void doubleColumnTextStaysSeparate() {
        // 双栏：两栏 y 交错但 x 完全不重叠，且每行是长正文（不满足"短单元格"表格特征）
        // → 不应并成一块、也不应被误判成表格
        String left = "This is the first longer line of left column body text for two-column testing purposes";
        String right = "This is the first longer line of right column body text also used by the layout test";
        List<PdfLayoutAnalyzer.Line> lines = List.of(
                line(40f, 40f, 250f, 12f, left, 10f, "Helvetica"),
                line(360f, 40f, 250f, 12f, right, 10f, "Helvetica"),
                line(40f, 56f, 250f, 12f, left, 10f, "Helvetica"),
                line(360f, 56f, 250f, 12f, right, 10f, "Helvetica"));
        List<AiChunkLayoutBlock> blocks = PdfLayoutAnalyzer.analyze(1, 612f, 792f, lines, List.of());
        long textBlocks = blocks.stream().filter(b -> "text".equals(b.getType())).count();
        long tableBlocks = blocks.stream().filter(b -> "table".equals(b.getType())).count();
        // 双栏同 y 两长行：既不能并成一块，也不能因列对齐被误判为表格
        assertEquals(2, textBlocks, "左右栏各成一段");
        assertEquals(0, tableBlocks, "长正文不应被误判为表格");

        // 同栏相邻行应并成一段（x 重叠 + 行距小）
        List<PdfLayoutAnalyzer.Line> oneCol = List.of(
                line(40f, 40f, 200f, 12f, "段落第一行内容", 10f, "Helvetica"),
                line(40f, 56f, 200f, 12f, "段落第二行内容", 10f, "Helvetica"));
        List<AiChunkLayoutBlock> blocks2 = PdfLayoutAnalyzer.analyze(1, 612f, 792f, oneCol, List.of());
        assertEquals(1, blocks2.size());
        assertEquals("text", blocks2.get(0).getType());
    }

    @Test
    void smallXDriftInSameParagraphMergedIntoOneTextBlock() {
        // 同一段相邻行 x 起点有微小偏差（半角/全角字符宽度差异），COL_BIN=5pt 应容忍
        List<PdfLayoutAnalyzer.Line> lines = List.of(
                line(50f, 40f, 300f, 12f, "这是正文段落的第一行内容", 10f, "Helvetica"),
                line(52f, 56f, 300f, 12f, "正文段落第二行继续内容", 10f, "Helvetica"),
                line(48f, 72f, 300f, 12f, "正文段落第三行还有内容", 10f, "Helvetica"));
        List<AiChunkLayoutBlock> blocks = PdfLayoutAnalyzer.analyze(1, 612f, 792f, lines, List.of());
        assertEquals(1, blocks.size(), "微 x 偏差的同一段应合并为一个 text 块");
        assertEquals("text", blocks.get(0).getType());
    }

    @Test
    void tocWithDotLeaderAndPageNumberMergedIntoOneBlock() {
        // 目录行（点线 + 行尾页码）整段合并为一个 text 块
        List<PdfLayoutAnalyzer.Line> lines = List.of(
                line(50f, 40f, 300f, 12f, "1 概述 ...... 1", 10f, "Helvetica"),
                line(50f, 56f, 300f, 12f, "1.1 产品介绍 ............ 3", 10f, "Helvetica"),
                line(50f, 72f, 300f, 12f, "1.2 功能特点 ............................................ 5", 10f, "Helvetica"),
                line(50f, 88f, 300f, 12f, "1.3 部署安装 ...... 7", 10f, "Helvetica"),
                line(50f, 104f, 300f, 12f, "2 快速上手 ....... 9", 10f, "Helvetica"));
        List<AiChunkLayoutBlock> blocks = PdfLayoutAnalyzer.analyze(1, 612f, 792f, lines, List.of());
        // 整段目录应归并为 1 个 text 块
        assertEquals(1, blocks.size(), "目录行整体应合并为一个 text 块");
        assertEquals("text", blocks.get(0).getType());
        // 框顶应接近第一行 baseline - ascent，框底接近最后一行 baseline + descent
        assertTrue(blocks.get(0).getHeight() > 0.06f, "目录块应覆盖多行高度");
    }

    @Test
    void tocWithSpacedDotLeaderAlsoDetected() {
        // 变体点线：".... ...... ......"（空格间隔）
        List<PdfLayoutAnalyzer.Line> lines = List.of(
                line(50f, 40f, 300f, 12f, "1.1 概述", 10f, "Helvetica"),
                line(50f, 56f, 300f, 12f, "1.2 接口说明 .... ...... 4", 10f, "Helvetica"),
                line(50f, 72f, 300f, 12f, "1.3 错误码 ....... 8", 10f, "Helvetica"));
        List<AiChunkLayoutBlock> blocks = PdfLayoutAnalyzer.analyze(1, 612f, 792f, lines, List.of());
        assertEquals(1, blocks.size(), "空格间隔点线也应识别为目录行合并");
        assertEquals("text", blocks.get(0).getType());
    }

    @Test
    void tableSpanningManyRowsMergedIntoOneTableBlock() {
        // 5 行 2 列的表格（小单元格短文本）→ 应合并为一个 table 块
        List<PdfLayoutAnalyzer.Line> lines = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            float y = 200f - i * 20f;
            lines.add(line(60f, y, 60f, 12f, "K" + i, 10f, "Helvetica"));
            lines.add(line(280f, y, 60f, 12f, "V" + i, 10f, "Helvetica"));
        }
        List<AiChunkLayoutBlock> blocks = PdfLayoutAnalyzer.analyze(1, 612f, 792f, lines, List.of());
        // 5 行同 y 各两列 → 5 行均标记 table，连续合并 → 1 个 table 块
        assertEquals(1, blocks.size(), "多行表格应合并为一个 table 块");
        assertEquals("table", blocks.get(0).getType());
    }

    @Test
    void realisticPdfClassifiesBlocks() throws Exception {
        byte[] pdf = buildRealisticPdf();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDPage page = doc.getPage(0);
            List<PdfLayoutAnalyzer.Line> lines = extractLines(doc);
            // 内容图片盒（pt、顶左）：测试里手动画的图位于 (300, 300) 尺寸 100×80（pdf 坐标系 y 向上）
            List<float[]> imgs = List.of(new float[]{300f, 792f - 300f - 80f, 100f, 80f});
            List<AiChunkLayoutBlock> blocks = PdfLayoutAnalyzer.analyze(1, 612f, 792f, lines, imgs);

            assertNotNull(blocks);
            assertFalse(blocks.isEmpty());
            assertTrue(blocks.stream().anyMatch(b -> "title".equals(b.getType())), "应识别出标题");
            assertTrue(blocks.stream().anyMatch(b -> "text".equals(b.getType())), "应识别出正文");
            assertTrue(blocks.stream().anyMatch(b -> "table".equals(b.getType())), "应识别出表格（同 y 两列连续行）");
            assertTrue(blocks.stream().anyMatch(b -> "code".equals(b.getType())), "应识别出代码（等宽字体）");
            assertTrue(blocks.stream().anyMatch(b -> "image".equals(b.getType())), "应识别出图片");

            // 所有块坐标归一化合法且在页内
            for (AiChunkLayoutBlock b : blocks) {
                assertTrue(b.getX() >= 0 && b.getY() >= 0, b.getType() + " 坐标非法");
                assertTrue(b.getWidth() > 0 && b.getHeight() > 0, b.getType() + " 尺寸非法");
                assertTrue(b.getX() + b.getWidth() <= 1.01f && b.getY() + b.getHeight() <= 1.01f, b.getType() + " 越界");
            }
        }
    }

    // ============================ 构造与提取 ============================

    private static byte[] buildRealisticPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(612f, 792f));
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                // 标题：大字号加粗（Type1 基础字体仅支持 ASCII）
                cs.beginText();
                cs.setFont(HELVETICA_BOLD, 20);
                cs.newLineAtOffset(50, 740);
                cs.showText("1.1 Product Overview");
                cs.endText();
                // 正文两行
                cs.beginText();
                cs.setFont(HELVETICA, 11);
                cs.newLineAtOffset(50, 700);
                cs.showText("This is the first body line for paragraph clustering test.");
                cs.newLineAtOffset(0, -16);
                cs.showText("This is the second body line continuing the same paragraph.");
                cs.endText();
                // 表格：三行两列（同 y 两个单元格 → 两列每行）
                for (int i = 0; i < 3; i++) {
                    float y = 650 - i * 20f;
                    cs.beginText();
                    cs.setFont(HELVETICA, 10);
                    cs.newLineAtOffset(60, y);
                    cs.showText("CellA" + i);
                    cs.newLineAtOffset(220, 0);
                    cs.showText("CellB" + i);
                    cs.endText();
                }
                // 代码：等宽字体三行
                cs.beginText();
                cs.setFont(COURIER, 9);
                cs.newLineAtOffset(80, 570);
                cs.showText("public void run() {");
                cs.newLineAtOffset(0, -12);
                cs.showText("    int a = 1;");
                cs.newLineAtOffset(0, -12);
                cs.showText("}");
                cs.endText();
                // 图片
                BufferedImage bi = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = bi.createGraphics();
                g.setColor(Color.LIGHT_GRAY);
                g.fillRect(0, 0, 60, 40);
                g.dispose();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                ImageIO.write(bi, "png", bo);
                PDImageXObject img = PDImageXObject.createFromByteArray(doc, bo.toByteArray(), "test");
                cs.drawImage(img, 300, 300, 100, 80);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** 镜像生产 extractPageLines 的提取逻辑（y 聚行 + x 大间隙拆行 + 字号/字体名捕获） */
    private static List<PdfLayoutAnalyzer.Line> extractLines(PDDocument doc) throws Exception {
        List<float[]> rects = new ArrayList<>();
        List<StringBuilder> texts = new ArrayList<>();
        List<Float> fonts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        texts.add(new StringBuilder());
        final float[] lastY = {Float.NaN};
        final float[] x1 = {Float.MAX_VALUE}, ymin = {Float.MAX_VALUE}, x2 = {-1f}, ymax = {-1f};
        final float[] curFont = {0f};
        final String[] curName = {""};
        Runnable flush = () -> {
            if (x2[0] > 0) {
                rects.add(new float[]{x1[0], ymin[0], x2[0] - x1[0], ymax[0] - ymin[0]});
                texts.add(new StringBuilder());
                fonts.add(curFont[0]);
                names.add(curName[0]);
                x1[0] = Float.MAX_VALUE;
                ymin[0] = Float.MAX_VALUE;
                x2[0] = -1f;
                ymax[0] = -1f;
                curFont[0] = 0f;
                curName[0] = "";
            }
        };
        PDFTextStripper st = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) {
                if (positions.isEmpty()) return;
                float y0 = positions.get(0).getYDirAdj();
                float x0 = positions.get(0).getXDirAdj();
                boolean newY = Float.isNaN(lastY[0]) || Math.abs(y0 - lastY[0]) > 2f;
                float gap = Math.max(6f, curFont[0] * 1.5f);
                boolean newX = !newY && x2[0] > 0 && (x0 - x2[0]) > gap;
                if (newY || newX) flush.run();
                texts.get(texts.size() - 1).append(text);
                curFont[0] = positions.get(0).getFontSize();
                try {
                    curName[0] = positions.get(0).getFont().getName();
                } catch (Exception ignore) {
                    // 字体信息获取失败不影响几何
                }
                for (TextPosition tp : positions) {
                    float y = tp.getYDirAdj();
                    float x = tp.getXDirAdj();
                    float w = tp.getWidthDirAdj();
                    float h = tp.getHeightDir();
                    lastY[0] = y;
                    x1[0] = Math.min(x1[0], x);
                    ymin[0] = Math.min(ymin[0], y);
                    x2[0] = Math.max(x2[0], x + w);
                    ymax[0] = Math.max(ymax[0], y + h);
                }
            }
        };
        st.setStartPage(1);
        st.setEndPage(1);
        st.getText(doc);
        flush.run();
        List<PdfLayoutAnalyzer.Line> out = new ArrayList<>();
        for (int i = 0; i < rects.size(); i++) {
            out.add(new PdfLayoutAnalyzer.Line(rects.get(i), texts.get(i).toString().trim(),
                    fonts.get(i), names.get(i)));
        }
        return out;
    }
}