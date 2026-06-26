package com.fastrag.module.knowledge.parser;

import cn.hutool.core.io.IoUtil;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserImpl implements DocumentParser {

    private final KbParseStrategyMapper strategyMapper;
    private final LlmService llmService;

    @Override
    public ParseResult parse(InputStream fileStream, String extension, String strategyId) {
        KbParseStrategy strategy = strategyId != null ? strategyMapper.selectById(strategyId) : null;
        String method = strategy != null ? strategy.getParseMethod() : "default";

        try {
            return switch (method != null ? method : "default") {
                case "pdf" -> parsePdf(fileStream, strategy);
                case "pptx" -> parsePptx(fileStream, strategy);
                case "docx" -> parseDocx(fileStream, strategy);
                case "xlsx" -> parseExcel(fileStream, strategy);
                default -> parseDefault(fileStream, strategy);
            };
        } catch (Exception e) {
            log.error("Document parsing failed for extension: {}", extension, e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }

    private ParseResult parsePdf(InputStream stream, KbParseStrategy strategy) throws Exception {
        try (PDDocument doc = Loader.loadPDF(stream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            int pages = doc.getNumberOfPages();

            if (strategy != null && strategy.getLlmModel() != null) {
                text = enhanceWithLlm(text, strategy.getLlmModel());
            }

            return ParseResult.builder().text(text).pages(pages).build();
        }
    }

    private ParseResult parseDocx(InputStream stream, KbParseStrategy strategy) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(stream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            return ParseResult.builder().text(text).pages(1).build();
        }
    }

    private ParseResult parsePptx(InputStream stream, KbParseStrategy strategy) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(stream);
             SlideShowExtractor extractor = new SlideShowExtractor(ppt)) {
            String text = extractor.getText();
            int slides = ppt.getSlides().size();
            return ParseResult.builder().text(text).pages(slides).build();
        }
    }

    private ParseResult parseExcel(InputStream stream, KbParseStrategy strategy) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(stream)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                var sheet = workbook.getSheetAt(i);
                sb.append("=== ").append(sheet.getSheetName()).append(" ===\n");
                sheet.forEach(row -> {
                    row.forEach(cell -> sb.append(cell.toString()).append("\t"));
                    sb.append("\n");
                });
            }
            return ParseResult.builder().text(sb.toString()).pages(workbook.getNumberOfSheets()).build();
        }
    }

    private ParseResult parseDefault(InputStream stream, KbParseStrategy strategy) {
        String text = IoUtil.read(stream, StandardCharsets.UTF_8);
        return ParseResult.builder().text(text).pages(1).build();
    }

    private String enhanceWithLlm(String text, String model) {
        try {
            String prompt = "请优化以下文档解析结果，保持原文内容，改善格式和可读性：\n\n" +
                    text.substring(0, Math.min(text.length(), 4000));
            return llmService.chat(model, prompt);
        } catch (Exception e) {
            log.warn("LLM enhancement failed, using original text", e);
            return text;
        }
    }
}
