package com.fastrag.module.knowledge.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PDF 页标题检测测试：parsePdf 应为每页构建"最近标题"映射（1-based），
 * 供 PDF 图片分片的语义上下文（跨页继承）。
 */
class PdfPageTitleTest {

    private final DocumentParserImpl parser = new DocumentParserImpl(
            null, null, null, null, null, null, null, new MarkdownSerializer(), null);

    private byte[] buildPdf(String... pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (String pageContent : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    // PDFBox 3.x：标准 14 字体通过 Standard14Fonts 构造
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    for (String line : pageContent.split("\n")) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -20);
                    }
                    cs.endText();
                }
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            return bos.toByteArray();
        }
    }

    @Test
    void parsePdf_detectsPageTitle() throws Exception {
        // 页 1：标题 + 正文（正文以句号结尾，不应被识别为标题）
        // 注意：标准 14 字体（Helvetica/WinAnsi）不支持中文，测试用英文文本
        String page1 = "3.2 Revenue by Region\n"
                + "This is the body paragraph with sufficient length to avoid the OCR fallback branch, "
                + "ending with a period.";
        byte[] pdf = buildPdf(page1);

        ParseResult result = parser.parse(new ByteArrayInputStream(pdf), "pdf", null);

        assertNotNull(result.getPageTitles());
        assertEquals("3.2 Revenue by Region", result.getPageTitles().get(1));
    }

    @Test
    void parsePdf_pageTitleInheritsAcrossPages() throws Exception {
        // 页 1 有标题，页 2 无标题（章节延续）→ 页 2 继承页 1 标题
        String page1 = "3.2 Revenue by Region\nBody paragraph of the first page, used for title detection testing.";
        String page2 = "Continued: detailed data explanation paragraph, this page has no new section heading, "
                + "so it should inherit the previous page title context.";
        byte[] pdf = buildPdf(page1, page2);

        ParseResult result = parser.parse(new ByteArrayInputStream(pdf), "pdf", null);

        assertEquals("3.2 Revenue by Region", result.getPageTitles().get(1));
        assertEquals("3.2 Revenue by Region", result.getPageTitles().get(2));
    }

    @Test
    void parsePdf_tableLineNotDetectedAsTitle() throws Exception {
        // 表格数据行（多数字 token）不应被识别为标题
        String page1 = "3.2 Revenue by Region\nChangsha 10737 9.3% 62.7% 85467 73.8%\n";
        byte[] pdf = buildPdf(page1);

        ParseResult result = parser.parse(new ByteArrayInputStream(pdf), "pdf", null);

        assertEquals("3.2 Revenue by Region", result.getPageTitles().get(1));
    }
}
