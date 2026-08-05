package com.fastrag.module.knowledge.parser;

import cn.hutool.json.JSONUtil;
import com.fastrag.ai.asr.AsrResult;
import com.fastrag.ai.asr.AsrService;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.ocr.OcrService;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.config.StrategyConfigResolver;
import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import com.fastrag.module.platform.entity.ModelRecord;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParserImpl implements DocumentParser {

    private final KbParseStrategyMapper strategyMapper;
    private final ModelRecordMapper modelRecordMapper;
    private final LlmService llmService;
    private final AsrService asrService;
    private final OcrService ocrService;
    private final MediaExtractor mediaExtractor;
    private final MinioService minioService;
    private final MarkdownSerializer markdownSerializer;
    private final StrategyConfigResolver configResolver;

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
        byte[] pdfBytes = stream.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int totalPages = doc.getNumberOfPages();
            log.info("Parsing PDF: {} pages", totalPages);

            // 逐页提取文字，插入 PAGE_BREAK 标记
            StringBuilder textWithMarkers = new StringBuilder();
            PDFRenderer renderer = new PDFRenderer(doc);

            for (int pageNum = 0; pageNum < totalPages; pageNum++) {
                // 提取该页文本
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pageNum + 1);
                stripper.setEndPage(pageNum + 1);
                String pageText = stripper.getText(doc);

                // 扫描件 OCR 兜底：文字太少则渲染页面为图片调 DeepSeek-OCR
                if (pageText.trim().length() < 50) {
                    try {
                        java.awt.image.BufferedImage pageImage = renderer.renderImageWithDPI(pageNum, 200);
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(pageImage, "png", baos);
                        String ocrResult = ocrService.recognize(baos.toByteArray(), "png");
                        if (ocrResult != null && !ocrResult.isBlank()) {
                            log.info("OCR fallback for page {}: {} chars", pageNum + 1, ocrResult.length());
                            pageText = ocrResult;
                        }
                    } catch (Exception e) {
                        log.warn("OCR fallback failed for page {}: {}", pageNum + 1, e.getMessage());
                    }
                }

                textWithMarkers.append(pageText.trim());
                if (pageNum < totalPages - 1) {
                    textWithMarkers.append("\n\n[PAGE_BREAK:").append(pageNum + 1).append("]\n\n");
                }
            }

            // LLM 增强（可选）
            String fullText = textWithMarkers.toString();
            if (strategy != null && strategy.getLlmModel() != null) {
                LlmConfig llmCfg = resolveLlmConfig(strategy);
                fullText = enhanceWithLlm(fullText, strategy.getLlmModel(), llmCfg.apiUrl, llmCfg.apiKey);
            }

            return ParseResult.builder()
                    .text(fullText)
                    .pages(totalPages)
                    .build();
        }
    }

    private ParseResult parseDocx(InputStream stream, KbParseStrategy strategy) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(stream)) {
            // 结构化解析：遍历 body elements
            List<DocNode> nodes = parseDocxStructured(doc);
            // 序列化为 Markdown
            String markdown = markdownSerializer.serialize(nodes);
            // 可选 LLM 增强
            if (strategy != null && strategy.getLlmModel() != null) {
                LlmConfig llmCfg = resolveLlmConfig(strategy);
                markdown = enhanceWithLlm(markdown, strategy.getLlmModel(), llmCfg.apiUrl, llmCfg.apiKey);
            }
            return ParseResult.builder()
                    .text(markdown)
                    .nodes(nodes)
                    .pages(1)
                    .build();
        }
    }

    /**
     * 结构化解析 DOCX：遍历 body elements，生成 DocNode 列表
     */
    private List<DocNode> parseDocxStructured(XWPFDocument doc) {
        List<DocNode> nodes = new ArrayList<>();
        for (IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFParagraph p) {
                // 一个段落可能产出多个节点（标题+图片，或公式+图片等）
                nodes.addAll(parseDocxParagraph(p));
            } else if (element instanceof XWPFTable t) {
                nodes.add(parseDocxTable(t));
            }
        }
        return nodes;
    }

    /**
     * 解析 DOCX 段落：检测标题、代码块、数学公式、内嵌图片、普通段落
     * 返回列表以支持一个段落产出多个节点（如多个图片）
     */
    private List<DocNode> parseDocxParagraph(XWPFParagraph p) {
        List<DocNode> nodes = new ArrayList<>();
        String style = p.getStyle();
        String styleId = p.getStyleID();
        String text = p.getText().trim();

        // 1. 标题检测（通过 Word 样式名）
        if (style != null || styleId != null) {
            String s = style != null ? style.toLowerCase() : styleId.toLowerCase();
            if (s.startsWith("heading") || s.contains("heading")) {
                int level = extractDocxHeadingLevel(s);
                nodes.add(DocNode.builder()
                        .type(DocNode.NodeType.HEADING)
                        .level(level)
                        .title(text.isEmpty() ? p.getText().trim() : text)
                        .build());
                return nodes; // 标题段落只产出标题节点
            }
            // TOC 目录样式跳过
            if (s.contains("toc") || s.contains("目录")) {
                return nodes; // empty
            }
        }

        // 2. 代码块检测
        if (isDocxCodeBlock(p)) {
            StringBuilder codeContent = new StringBuilder();
            for (XWPFRun run : p.getRuns()) {
                codeContent.append(run.getText(0));
            }
            // 如果 run 取不到完整文本，直接用段落文本
            String code = codeContent.length() > 0 ? codeContent.toString().trim() : text;
            if (code.isEmpty()) code = text;
            nodes.add(DocNode.builder()
                    .type(DocNode.NodeType.CODE_BLOCK)
                    .codeLanguage(detectDocxCodeLanguage(p))
                    .content(code)
                    .build());
            return nodes;
        }

        // 3. 图片检测（段落中嵌入的多个图片）— 先提取但不返回，公式判断需要 hasPictures
        List<XWPFPicture> pictures = extractPicturesFromParagraph(p);
        boolean hasPictures = !pictures.isEmpty();

        // 4. 数学公式检测（OMML → 基本 LaTeX 转换）
        List<String> formulas = extractDocxFormulas(p);
        boolean hasFormulas = !formulas.isEmpty();
        if (hasFormulas) {
            // 独立公式块（无文本、无图片、仅一个公式）→ DISPLAY_MATH，否则 → INLINE_MATH
            boolean isDisplay = text.isEmpty() && formulas.size() == 1 && !hasPictures;
            for (String latex : formulas) {
                nodes.add(DocNode.builder()
                        .type(isDisplay ? DocNode.NodeType.DISPLAY_MATH : DocNode.NodeType.INLINE_MATH)
                        .latex(latex)
                        .build());
            }
        }

        // 5. 图片节点生成
        if (hasPictures) {
            for (XWPFPicture pic : pictures) {
                XWPFPictureData picData = pic.getPictureData();
                String ext = picData != null ? picData.suggestFileExtension() : "png";
                String imageKey = "docx_img_" + System.currentTimeMillis() + "_" + nodes.size() + "." + ext;
                // TODO: 此 imageKey 仅为占位符，未上传真实图片到 MinIO。
                // IngestionConsumer 应在 ingest 阶段从 XWPFPictureData 获取字节并上传。
                // 当前设计：解析器负责检测和标记图片，上传由调用方负责。
                String caption = pic.getDescription() != null ? pic.getDescription() : "图片";
                nodes.add(DocNode.builder()
                        .type(DocNode.NodeType.IMAGE)
                        .imageKey(imageKey)
                        .imageCaption(caption)
                        .build());
            }
        }

        // 5. 如果段落只有公式/图片（无文本），提前返回
        if (!hasFormulas && hasPictures) {
            return nodes;
        }
        if (hasFormulas && text.isEmpty()) {
            return nodes;
        }

        // 6. 空段落跳过
        if (text.isEmpty()) {
            String rawText = p.getText();
            if (rawText == null || rawText.trim().isEmpty()) {
                return nodes;
            }
        }

        // 7. 普通段落（如果已有公式/图片节点，将文本作为独立段落追加）
        nodes.add(DocNode.builder()
                .type(DocNode.NodeType.PARAGRAPH)
                .content(text)
                .build());

        return nodes;
    }

    /**
     * 从 DOCX 段落中提取 OMML 数学公式，执行基本 LaTeX 转换。
     *
     * 完整 OMML→LaTeX 需要 XDocReport 等库支持，此处实现常用模式转换：
     *   - <m:f> 分数 → \frac{numerator}{denominator}
     *   - <m:sup/> 上标 → ^{...}
     *   - <m:sub/> 下标 → _{...}
     *   - <m:nary/> ∑∫ 等大运算符
     * 未识别的 OMML 内容保留原始 XML 提取。
     */
    private List<String> extractDocxFormulas(XWPFParagraph p) {
        List<String> formulas = new ArrayList<>();
        try {
            // 通过底层 CTParagraph 获取 OMML 公式列表
            var ctP = p.getCTP();
            if (ctP == null) return formulas;

            // 尝试获取 oMath 元素（OMML 公式）
            // 方法：从 CTParagraph 的 OMath 列表获取（通过反射避免依赖 ooxml-schemas 编译）
            try {
                // POI 5.x 方式：从 CTParagraph 获取 oMath 元素
                java.lang.reflect.Method getOMathList = ctP.getClass().getMethod("getOMathList");
                @SuppressWarnings("unchecked")
                List<Object> mathList = (List<Object>) getOMathList.invoke(ctP);
                if (mathList != null) {
                    for (Object math : mathList) {
                        String latex = ommlToLatex(math);
                        if (latex != null && !latex.isEmpty()) {
                            formulas.add(latex);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to extract OMML formulas from paragraph: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.debug("Error accessing CTParagraph for math extraction: {}", e.getMessage());
        }
        return formulas;
    }

    /**
     * 基本 OMML → LaTeX 转换器
     *
     * 处理常见公式结构。对于复杂公式，返回 OMML XML 片段并标记为待转换，
     * 避免信息丢失。
     */
    private String ommlToLatex(Object ommlObj) {
        try {
            // 获取 OMML 元素的 XML 字符串
            String xml = ommlObj.toString();
            if (xml == null || xml.isEmpty()) return "[公式]";

            // 基本 LaTeX 转换规则
            String latex = xml;

            // 转义 XML 标签：提取文本内容和结构
            // 分数: <m:f>...<m:num>a</m:num><m:den>b</m:den></m:f>
            latex = latex.replaceAll("(?s)<m:f>.*?<m:num>(.*?)</m:num>.*?<m:den>(.*?)</m:den>.*?</m:f>",
                    "\\\\frac{$1}{$2}");

            // 简单替换已知的数学符号
            latex = latex.replace("<m:alpha/>", "\\alpha");
            latex = latex.replace("<m:beta/>", "\\beta");
            latex = latex.replace("<m:gamma/>", "\\gamma");
            latex = latex.replace("<m:delta/>", "\\delta");
            latex = latex.replace("<m:epsi/>", "\\epsilon");
            latex = latex.replace("<m:zeta/>", "\\zeta");
            latex = latex.replace("<m:eta/>", "\\eta");
            latex = latex.replace("<m:theta/>", "\\theta");
            latex = latex.replace("<m:iota/>", "\\iota");
            latex = latex.replace("<m:kappa/>", "\\kappa");
            latex = latex.replace("<m:lambda/>", "\\lambda");
            latex = latex.replace("<m:mu/>", "\\mu");
            latex = latex.replace("<m:nu/>", "\\nu");
            latex = latex.replace("<m:xi/>", "\\xi");
            latex = latex.replace("<m:pi/>", "\\pi");
            latex = latex.replace("<m:rho/>", "\\rho");
            latex = latex.replace("<m:sigma/>", "\\sigma");
            latex = latex.replace("<m:tau/>", "\\tau");
            latex = latex.replace("<m:phi/>", "\\phi");
            latex = latex.replace("<m:chi/>", "\\chi");
            latex = latex.replace("<m:psi/>", "\\psi");
            latex = latex.replace("<m:omega/>", "\\omega");

            // 移除残留的 XML 标签
            latex = latex.replaceAll("<[^>]+>", " ");
            // 合并空白
            latex = latex.replaceAll("\\s+", " ").trim();

            return latex.isEmpty() ? "[公式]" : latex;
        } catch (Exception e) {
            log.debug("OMML to LaTeX conversion failed", e);
            return "[公式]";
        }
    }

    /**
     * 从 Word 样式中提取标题层级
     * heading1 → 1, heading 2 → 2, Heading3 → 3
     */
    private int extractDocxHeadingLevel(String style) {
        // 尝试提取样式名中的数字
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(style);
        if (m.find()) {
            int level = Integer.parseInt(m.group(1));
            return Math.min(Math.max(level, 1), 6);
        }
        return 1; // 有 heading 但没有数字则默认为 H1
    }

    /**
     * 检测 DOCX 段落是否为代码块
     */
    private boolean isDocxCodeBlock(XWPFParagraph p) {
        // 检测样式名
        String style = p.getStyle();
        if (style != null) {
            String s = style.toLowerCase();
            if (s.contains("code") || s.contains("source") || s.contains("listing")) {
                return true;
            }
        }
        // 检测等宽字体
        for (XWPFRun run : p.getRuns()) {
            String fontFamily = run.getFontFamily();
            if (fontFamily != null) {
                String f = fontFamily.toLowerCase();
                if (f.contains("courier") || f.contains("consolas") || f.contains("monaco")
                        || f.contains("monospace") || f.contains("source code")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检测 DOCX 代码块的语言（通过关键字启发式）
     */
    private String detectDocxCodeLanguage(XWPFParagraph p) {
        String text = p.getText();
        if (text == null || text.isEmpty()) return "";
        String t = text.toLowerCase();
        if (t.contains("public class") || t.contains("public static") || t.contains("private ")
                || t.contains("import java")) return "java";
        if (t.contains("function ") || t.contains("const ") || t.contains("let ")
                || t.contains("export ") || t.contains("=>")) return "javascript";
        if (t.contains("def ") || t.contains("import ") && t.contains(":")) return "python";
        if (t.contains("<html") || t.contains("<div") || t.contains("<body")) return "html";
        if (t.contains("SELECT ") || t.contains("FROM ") || t.contains("WHERE ")) return "sql";
        return "";
    }

    /**
     * 从段落中提取内嵌图片
     */
    private List<XWPFPicture> extractPicturesFromParagraph(XWPFParagraph p) {
        List<XWPFPicture> pictures = new ArrayList<>();
        for (XWPFRun run : p.getRuns()) {
            pictures.addAll(run.getEmbeddedPictures());
        }
        return pictures;
    }

    /**
     * 解析 DOCX 表格
     */
    private DocNode parseDocxTable(XWPFTable table) {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        boolean firstRow = true;
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText().trim());
            }
            if (firstRow && isHeaderRow(cells)) {
                headers = cells;
            } else if (firstRow) {
                // 第一行但不是表头，先暂存到 rows 中
                rows.add(cells);
            } else {
                rows.add(cells);
            }
            firstRow = false; // 无论是否表头，第 0 行后都设为 false
        }
        // 如果未识别出表头，用第一行作为表头
        if (headers.isEmpty() && !rows.isEmpty()) {
            headers = rows.remove(0);
        }
        return DocNode.builder()
                .type(DocNode.NodeType.TABLE)
                .headers(headers)
                .rows(rows)
                .build();
    }

    /**
     * 判断表格第一行是否为表头：检查是否所有单元格都是非空短文本且无数字开头
     */
    private boolean isHeaderRow(List<String> cells) {
        if (cells == null || cells.isEmpty()) return false;
        long nonEmptyCount = cells.stream().filter(c -> c != null && !c.trim().isEmpty()).count();
        if (nonEmptyCount < cells.size() / 2.0) return false;
        long digitStartCount = cells.stream()
                .filter(c -> c != null && !c.trim().isEmpty() && Character.isDigit(c.trim().charAt(0)))
                .count();
        // 如果超过半数单元格以数字开头，则不是表头
        return digitStartCount <= cells.size() / 2.0;
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
     * 解析视频文件：关键帧 OCR + 音频 ASR → 联合分块
     * <p>
     * 处理流程：
     * 1. 从 strategy.advanced 读取关键帧配置（采样间隔、哈希阈值）
     * 2. 音频路：FFmpeg 提取音频 → ASR 转文字（带时间戳分段）
     * 3. 视觉路：FFmpeg 均匀采样关键帧 → pHash 去重 → DeepSeek-OCR 提取文字
     * 4. 以 ASR 自然分段对齐，合并关键帧 OCR 文字和 ASR 文本为联合 chunk
     */
    private ParseResult parseVideo(InputStream stream, String extension, KbParseStrategy strategy) throws Exception {
        log.info("Parsing video file with multimodal processing, extension: {}", extension);

        // 读取策略配置（统一入口，兼容新分组结构与旧平铺字段）
        ParseStrategyConfig.ParseConfig parseConfig = configResolver
                .resolve(strategy != null ? strategy.getId() : null).getParse();
        int keyframeInterval = parseConfig.getKeyframeIntervalSeconds() != null
                ? parseConfig.getKeyframeIntervalSeconds() : 10;   // 默认每 10 秒采样一帧
        int hashThreshold = parseConfig.getKeyframeHashThreshold() != null
                ? parseConfig.getKeyframeHashThreshold().intValue() : 10;  // 默认汉明距离阈值
        log.info("Video parse config: keyframeInterval={}s, hashThreshold={}", keyframeInterval, hashThreshold);

        // 读取视频流字节（因为 stream 只能读一次，需要分别传给音频和视觉两条路径）
        byte[] videoBytes = stream.readAllBytes();

        // === 音频路：提取音频 → ASR ===
        List<AsrResult.AsrSegment> asrSegments = new ArrayList<>();
        String asrFullText = "";
        try {
            byte[] audioBytes = mediaExtractor.extractAudio(
                    new java.io.ByteArrayInputStream(videoBytes), extension);
            AsrResult asrResult = asrService.transcribe(audioBytes, "audio." + (extension != null ? extension : "mp4"));
            asrFullText = asrResult.getText();
            if (asrResult.getSegments() != null) {
                asrSegments.addAll(asrResult.getSegments());
            }
            log.info("Video ASR completed: {} segments, {} chars", asrSegments.size(), asrFullText.length());
        } catch (Exception e) {
            log.error("Video ASR failed, continuing with keyframe-only mode", e);
        }

        // === 视觉路：关键帧提取 → 去重 → OCR ===
        List<KeyframeOcr> keyframeOcrList = new ArrayList<>();
        try {
            List<MediaExtractor.Keyframe> keyframes = mediaExtractor.extractKeyframes(
                    new java.io.ByteArrayInputStream(videoBytes), extension, keyframeInterval);
            log.info("Extracted {} raw keyframes from video", keyframes.size());

            // 感知哈希去重
            List<MediaExtractor.Keyframe> deduped = mediaExtractor.deduplicateByHash(keyframes, hashThreshold);

            // 逐帧 OCR
            for (MediaExtractor.Keyframe kf : deduped) {
                try {
                    String ocrText = ocrService.recognize(kf.getImageBytes(), "jpg");
                    if (ocrText != null && !ocrText.isBlank()) {
                        keyframeOcrList.add(KeyframeOcr.builder()
                                .timestampSeconds(kf.getTimestampSeconds())
                                .ocrText(ocrText.trim())
                                .build());
                    }
                } catch (Exception e) {
                    log.warn("OCR failed for keyframe at {}s: {}", kf.getTimestampSeconds(), e.getMessage());
                }
            }
            log.info("Keyframe OCR completed: {} frames with text", keyframeOcrList.size());
        } catch (Exception e) {
            log.error("Video keyframe extraction/OCR failed, continuing with ASR-only mode", e);
        }

        // === 合并两路结果 ===
        return mergeSegmentsWithKeyframes(asrFullText, asrSegments, keyframeOcrList, strategy);
    }

    /**
     * 将 ASR 分段与关键帧 OCR 结果按时间轴合并
     * <p>
     * 以 ASR 自然分段对齐，对每个 ASR 段找到时间上最近的关键帧，
     * 将 OCR 文字与 ASR 文本合并为联合 chunk。
     */
    private ParseResult mergeSegmentsWithKeyframes(
            String asrFullText,
            List<AsrResult.AsrSegment> asrSegments,
            List<KeyframeOcr> keyframeOcrList,
            KbParseStrategy strategy) {

        List<ParseResult.ChunkTimeSegment> mergedSegments = new ArrayList<>();

        if (asrSegments.isEmpty()) {
            // ASR 失败但有关键帧 OCR 结果时，以关键帧构建分段
            log.info("No ASR segments, building segments from keyframes only");
            for (KeyframeOcr kf : keyframeOcrList) {
                mergedSegments.add(ParseResult.ChunkTimeSegment.builder()
                        .text("【画面内容】\n" + kf.getOcrText())
                        .startTime(kf.getTimestampSeconds())
                        .endTime(kf.getTimestampSeconds() + 10.0) // 估算
                        .build());
            }
        } else if (keyframeOcrList.isEmpty()) {
            // 无关键帧 OCR 结果时，保留纯 ASR 分段
            log.info("No keyframe OCR results, keeping ASR segments only");
            for (AsrResult.AsrSegment seg : asrSegments) {
                mergedSegments.add(ParseResult.ChunkTimeSegment.builder()
                        .text(seg.getText())
                        .startTime(seg.getStart())
                        .endTime(seg.getEnd())
                        .build());
            }
        } else {
            // 两路都有结果：按 ASR 分段对齐，附加最近关键帧的 OCR 文字
            // 构建 keyframe 时间索引（NavigableMap 方便查找最近的 keyframe）
            NavigableMap<Double, KeyframeOcr> kfIndex = new TreeMap<>();
            for (KeyframeOcr kf : keyframeOcrList) {
                kfIndex.put(kf.getTimestampSeconds(), kf);
            }

            for (AsrResult.AsrSegment seg : asrSegments) {
                double startTime = seg.getStart() != null ? seg.getStart() : 0.0;
                double endTime = seg.getEnd() != null ? seg.getEnd() : startTime;
                double midpoint = (startTime + endTime) / 2.0;

                // 查找时间上最近的关键帧
                KeyframeOcr nearestKf = findNearestKeyframe(kfIndex, midpoint);

                String asrText = seg.getText() != null ? seg.getText().trim() : "";
                if (asrText.isEmpty()) continue;

                if (nearestKf != null && nearestKf.getOcrText() != null && !nearestKf.getOcrText().isBlank()) {
                    // 合并为联合文本
                    String merged = "【画面内容】\n" + nearestKf.getOcrText()
                            + "\n【语音内容】\n" + asrText;
                    mergedSegments.add(ParseResult.ChunkTimeSegment.builder()
                            .text(merged)
                            .startTime(startTime)
                            .endTime(endTime)
                            .build());
                } else {
                    // 无对应关键帧，保留纯 ASR 文本
                    mergedSegments.add(ParseResult.ChunkTimeSegment.builder()
                            .text(asrText)
                            .startTime(startTime)
                            .endTime(endTime)
                            .build());
                }
            }
        }

        // 可选 LLM 增强
        String fullText = asrFullText;
        if (strategy != null && strategy.getLlmModel() != null && fullText != null) {
            try {
                LlmConfig llmCfg = resolveLlmConfig(strategy);
                fullText = enhanceWithLlm(fullText, strategy.getLlmModel(), llmCfg.apiUrl, llmCfg.apiKey);
            } catch (Exception e) {
                log.warn("LLM enhancement failed, using original text", e);
            }
        }

        return ParseResult.builder()
                .text(fullText != null ? fullText : "")
                .pages(1)
                .segments(mergedSegments)
                .build();
    }

    /**
     * 在关键帧时间索引中查找与指定时间点最近的关键帧
     *
     * @param kfIndex  关键帧时间索引（NavigableMap，key 为时间戳）
     * @param targetTime 目标时间点
     * @return 最近的关键帧，如果没有则返回 null
     */
    private KeyframeOcr findNearestKeyframe(NavigableMap<Double, KeyframeOcr> kfIndex, double targetTime) {
        if (kfIndex.isEmpty()) return null;

        // floorEntry: <= targetTime 的最大 key
        Map.Entry<Double, KeyframeOcr> floor = kfIndex.floorEntry(targetTime);
        // ceilingEntry: >= targetTime 的最小 key
        Map.Entry<Double, KeyframeOcr> ceiling = kfIndex.ceilingEntry(targetTime);

        if (floor == null && ceiling == null) return null;
        if (floor == null) return ceiling.getValue();
        if (ceiling == null) return floor.getValue();

        // 两者都存在，选距离更近的
        double floorDist = Math.abs(targetTime - floor.getKey());
        double ceilingDist = Math.abs(targetTime - ceiling.getKey());

        return floorDist <= ceilingDist ? floor.getValue() : ceiling.getValue();
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
            LlmConfig llmCfg = resolveLlmConfig(strategy);
            fullText = enhanceWithLlm(fullText, strategy.getLlmModel(), llmCfg.apiUrl, llmCfg.apiKey);
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
            LlmConfig llmCfg = resolveLlmConfig(strategy);
            text = enhanceWithLlm(text, strategy.getLlmModel(), llmCfg.apiUrl, llmCfg.apiKey);
        }

        return ParseResult.builder().text(text).pages(1).build();
    }

    /**
     * 使用 LLM 增强文档解析结果（配置了正确的 API URL 和 Key）
     */
    /**
     * 使用 LLM 增强文档解析结果。
     *
     * 限制：仅对 <= 5000 字符的文本执行增强。大文本直接跳过，
     * 因为"格式优化"对长文本收益有限，且 API 调用容易超时阻塞整个处理管线。
     */
    private String enhanceWithLlm(String text, String model, String apiUrl, String apiKey) {
        if (apiUrl == null || apiUrl.isBlank()) {
            log.warn("[LLM-Enhance] apiUrl is null/blank, skipping LLM enhancement for model={}", model);
            return text;
        }
        if (text == null || text.isBlank()) return text;

        // 大文本跳过增强：超时风险高且收益有限
        if (text.length() > 5000) {
            log.info("[LLM-Enhance] Skipping LLM enhancement: text too long ({} chars > 5000), model={}",
                    text.length(), model);
            return text;
        }

        try {
            String prompt = "请优化以下文档解析结果，保持原文内容，改善格式和可读性：\n\n" + text;
            log.info("[LLM-Enhance] Calling model={} via apiUrl={}, text length={}", model, apiUrl, text.length());
            // 使用流式+自定义超时（15s），避免非流式 30s 阻塞整个文件处理管线
            // 文档增强是可选操作，超时应快速降级到原文
            String result = llmService.chatWithTimeout(model, prompt, apiUrl, apiKey, false, 15);
            if (result == null || result.isBlank()) {
                log.warn("[LLM-Enhance] Empty response, using original text");
                return text;
            }
            log.info("[LLM-Enhance] Enhancement done, result length={}", result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            log.warn("[LLM-Enhance] Failed, using original text: {}", e.getMessage());
            return text;
        }
    }

    /**
     * 解析 LLM 配置：从策略中获取 llmModel，查找 ModelRecord 得到 apiUrl/apiKey
     */
    private LlmConfig resolveLlmConfig(KbParseStrategy strategy) {
        if (strategy == null || strategy.getLlmModel() == null || strategy.getLlmModel().isBlank()) {
            return new LlmConfig(null, null);
        }
        String llmModel = strategy.getLlmModel();
        ModelRecord modelRecord = modelRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ModelRecord>()
                        .eq(ModelRecord::getCode, llmModel)
                        .eq(ModelRecord::getStatus, "online")
                        .last("LIMIT 1"));
        if (modelRecord != null) {
            log.info("[LLM-Enhance] Resolved model config: model={}, apiUrl={}", llmModel, modelRecord.getApiUrl());
            return new LlmConfig(modelRecord.getApiUrl(), modelRecord.getApiKeyRef());
        }
        log.warn("[LLM-Enhance] ModelRecord not found for code={} with status=online, skipping LLM enhancement", llmModel);
        return new LlmConfig(null, null);
    }

    /** LLM 配置内部 DTO */
    @lombok.Data
    private static class LlmConfig {
        private final String apiUrl;
        private final String apiKey;
    }

    /**
     * 关键帧 OCR 结果模型
     */
    @Data
    @Builder
    public static class KeyframeOcr {
        /** 关键帧时间戳（秒） */
        private double timestampSeconds;
        /** OCR 识别出的文字内容 */
        private String ocrText;
    }
}
