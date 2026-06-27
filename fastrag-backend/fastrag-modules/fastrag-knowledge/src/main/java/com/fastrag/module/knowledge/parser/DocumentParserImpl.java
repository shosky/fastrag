package com.fastrag.module.knowledge.parser;

import com.fastrag.ai.asr.AsrResult;
import com.fastrag.ai.asr.AsrService;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.ocr.OcrService;
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
import java.util.ArrayList;
import java.util.List;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserImpl implements DocumentParser {

    private final KbParseStrategyMapper strategyMapper;
    private final LlmService llmService;
    private final AsrService asrService;
    private final OcrService ocrService;
    private final MediaExtractor mediaExtractor;

    @Override
    public ParseResult parse(InputStream fileStream, String extension, String strategyId) {
        KbParseStrategy strategy = strategyId != null ? strategyMapper.selectById(strategyId) : null;
        String method = strategy != null ? strategy.getParseMethod() : null;

        // 当策略未指定解析方法或为 default 时，根据文件扩展名自动选择解析器
        if (method == null || method.isBlank() || "default".equals(method)) {
            method = resolveMethodByExtension(extension);
        }

        log.debug("Parsing file with extension={}, method={}", extension, method);

        try {
            return switch (method) {
                case "pdf" -> parsePdf(fileStream, strategy);
                case "pptx" -> parsePptx(fileStream, strategy);
                case "docx" -> parseDocx(fileStream, strategy);
                case "xlsx" -> parseExcel(fileStream, strategy);
                case "video" -> parseVideo(fileStream, extension, strategy);
                case "audio" -> parseAudio(fileStream, extension, strategy);
                case "image" -> parseImage(fileStream, extension, strategy);
                default -> parseDefault(fileStream, strategy);
            };
        } catch (Exception e) {
            log.error("Document parsing failed for extension: {}", extension, e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据文件扩展名推断解析方法
     */
    private String resolveMethodByExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "default";
        }
        return switch (extension.toLowerCase().trim()) {
            case "pdf" -> "pdf";
            case "docx" -> "docx";
            case "pptx" -> "pptx";
            case "xlsx", "xls" -> "xlsx";
            case "mp4", "avi", "mov", "mkv", "flv", "wmv", "webm" -> "video";
            case "mp3", "wav", "m4a", "aac", "ogg", "flac", "wma" -> "audio";
            case "jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff" -> "image";
            default -> "default"; // txt, md, csv 等纯文本文件
        };
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

    private ParseResult parseDefault(InputStream stream, KbParseStrategy strategy) throws Exception {
        byte[] bytes = stream.readAllBytes();
        try {
            Charset charset = detectCharset(bytes);
            log.debug("Detected charset: {} for text file", charset.name());
            String text = new String(bytes, charset);
            return ParseResult.builder().text(text).pages(1).build();
        } catch (Exception e) {
            log.warn("Failed to decode with detected charset, falling back to UTF-8", e);
            String text = new String(bytes, StandardCharsets.UTF_8);
            return ParseResult.builder().text(text).pages(1).build();
        }
    }

    /**
     * 检测文本文件的字符编码
     * 优先检查 BOM，然后尝试 UTF-8，最后回退到 GBK
     */
    private Charset detectCharset(byte[] bytes) {
        // 1. 检查 UTF-8 BOM (EF BB BF)
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }

        // 2. 检查 UTF-16 BOM
        if (bytes.length >= 2) {
            if ((bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
            if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
        }

        // 3. 尝试用 UTF-8 严格解码，如果完全合法则使用 UTF-8
        if (isValidUtf8(bytes)) {
            return StandardCharsets.UTF_8;
        }

        // 4. 回退到 GBK（中文 Windows 系统常用编码）
        try {
            Charset gbk = Charset.forName("GBK");
            CharsetDecoder decoder = gbk.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes));
            return gbk;
        } catch (Exception e) {
            // GBK 也解码失败，强制使用 UTF-8
            log.warn("GBK decode failed, falling back to UTF-8");
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * 验证字节数组是否为合法的 UTF-8 编码
     */
    private boolean isValidUtf8(byte[] bytes) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer charBuffer = decoder.decode(ByteBuffer.wrap(bytes));
            // 额外检查：解码后的文本不应包含大量替换字符
            String text = charBuffer.toString();
            long replacementCount = text.chars().filter(c -> c == '\uFFFD').count();
            return replacementCount == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析视频文件：提取音频 → ASR 转文字 → 带时间戳分段
     */
    private ParseResult parseVideo(InputStream stream, String extension, KbParseStrategy strategy) throws Exception {
        log.info("Parsing video file, extension: {}", extension);
        byte[] audioBytes = mediaExtractor.extractAudio(stream, extension);
        String filename = "audio." + (extension != null ? extension : "mp4");
        return doAsrParse(audioBytes, filename, strategy);
    }

    /**
     * 解析音频文件：直接 ASR 转文字 → 带时间戳分段
     */
    private ParseResult parseAudio(InputStream stream, String extension, KbParseStrategy strategy) throws Exception {
        log.info("Parsing audio file, extension: {}", extension);
        byte[] audioBytes = stream.readAllBytes();
        String filename = "audio." + (extension != null ? extension : "mp3");
        return doAsrParse(audioBytes, filename, strategy);
    }

    /**
     * 执行 ASR 解析并构建 ParseResult
     */
    private ParseResult doAsrParse(byte[] audioBytes, String filename, KbParseStrategy strategy) {
        AsrResult asrResult = asrService.transcribe(audioBytes, filename);

        String fullText = asrResult.getText();
        if (strategy != null && strategy.getLlmModel() != null) {
            fullText = enhanceWithLlm(fullText, strategy.getLlmModel());
        }

        List<ParseResult.ChunkTimeSegment> segments = new ArrayList<>();
        if (asrResult.getSegments() != null) {
            for (AsrResult.AsrSegment seg : asrResult.getSegments()) {
                segments.add(ParseResult.ChunkTimeSegment.builder()
                        .text(seg.getText())
                        .startTime(seg.getStart())
                        .endTime(seg.getEnd())
                        .build());
            }
        }

        return ParseResult.builder()
                .text(fullText)
                .pages(1)
                .segments(segments)
                .build();
    }

    /**
     * 解析图片文件：OCR 识别文字
     */
    private ParseResult parseImage(InputStream stream, String extension, KbParseStrategy strategy) throws Exception {
        log.info("Parsing image file, extension: {}", extension);
        byte[] imageBytes = stream.readAllBytes();
        String text = ocrService.recognize(imageBytes, extension);

        if (strategy != null && strategy.getLlmModel() != null) {
            text = enhanceWithLlm(text, strategy.getLlmModel());
        }

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
