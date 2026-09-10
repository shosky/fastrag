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
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hwpf.model.PicturesTable;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.PictureShape;
import org.apache.poi.sl.usermodel.PlaceableShape;
import org.apache.poi.sl.usermodel.Shape;
import org.apache.poi.sl.usermodel.ShapeContainer;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import java.util.function.Predicate;
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
        return parse(fileStream, extension, strategyId, null);
    }

    @Override
    public ParseResult parse(InputStream fileStream, String extension, String strategyId, ParseOptions options) {
        KbParseStrategy strategy = strategyId != null ? strategyMapper.selectById(strategyId) : null;
        return parse(fileStream, extension, strategy, options);
    }

    /**
     * 按显式策略对象解析（预览场景：临时构造的策略对象，未落库，含自定义解析方式与 advanced 配置）。
     */
    public ParseResult parse(InputStream fileStream, String extension, KbParseStrategy strategy, ParseOptions options) {
        String method = strategy != null ? strategy.getParseMethod() : null;

        // 当策略未指定解析方法或为 default 时，根据文件扩展名自动选择解析器
        if (method == null || method.isBlank() || "default".equals(method)) {
            method = resolveMethodByExtension(extension);
        }

        log.debug("Parsing file with extension={}, method={}", extension, method);

        try {
            return switch (method) {
                case "pdf" -> parsePdf(fileStream, strategy, options);
                case "doc" -> parseDoc(fileStream, strategy);
                case "docx" -> parseDocx(fileStream, strategy);
                case "pptx" -> parsePptx(fileStream, extension, strategy);
                case "xlsx" -> parseExcel(fileStream, extension, strategy);
                case "video" -> parseVideo(fileStream, extension, strategy, options);
                case "audio" -> parseAudio(fileStream, extension, strategy, options);
                case "image" -> parseImage(fileStream, extension, strategy, options);
                default -> parseDefault(fileStream, strategy, options);
            };
        } catch (Exception e) {
            log.error("Document parsing failed for extension: {}", extension, e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据文件扩展名推断解析方法。
     * 委托 {@link ParseMethodRegistry}（唯一权威映射，含 .ppt → pptx 等），未识别时返回 default 纯文本兜底。
     */
    private String resolveMethodByExtension(String extension) {
        return ParseMethodRegistry.resolveByExtension(extension);
    }

    private ParseResult parsePdf(InputStream stream, KbParseStrategy strategy, ParseOptions options) throws Exception {
        byte[] pdfBytes = stream.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int totalPages = doc.getNumberOfPages();
            log.info("Parsing PDF: {} pages", totalPages);
            PDFRenderer renderer = new PDFRenderer(doc);

            // 页 → 最近标题映射（供 PDF 图片分片的语义上下文，1-based）
            Map<Integer, String> pageTitles = new HashMap<>();
            String currentTitle = null;

            // 第一遍：逐页行提取。文字层页 → 带坐标几何行（行距分段/表格/标题识别的输入）；
            // <50 字符判定扫描页 → 渲染 PNG 走 OCR 兜底（OCR 文本无坐标，仅按结构切分）
            Map<Integer, List<PageLine>> geoRows = new LinkedHashMap<>();
            Map<Integer, List<PageLine>> ocrRows = new LinkedHashMap<>();
            for (int pageNum = 0; pageNum < totalPages; pageNum++) {
                List<PageLine> rows = extractGeometryLines(doc, pageNum);
                int chars = rows.stream().mapToInt(r -> r.text().length()).sum();
                if (chars < 50) {
                    // 扫描件 OCR 兜底：文字太少则渲染页面为图片调 OCR
                    try {
                        java.awt.image.BufferedImage pageImage = renderer.renderImageWithDPI(pageNum, 200);
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        javax.imageio.ImageIO.write(pageImage, "png", baos);
                        String ocrResult = ocrService.recognize(baos.toByteArray(), "png",
                                options != null ? options.getOcrEngine() : null);
                        if (ocrResult != null && !ocrResult.isBlank()) {
                            log.info("OCR fallback for page {}: {} chars", pageNum + 1, ocrResult.length());
                            List<PageLine> o = new ArrayList<>();
                            int ln = 0;
                            for (String l : ocrResult.split("\n")) {
                                String tt = l.trim();
                                if (!tt.isEmpty()) o.add(new PageLine(++ln * 100f, 80f, 0f, 1f, tt));
                            }
                            if (!o.isEmpty()) {
                                ocrRows.put(pageNum, o);
                                continue;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("OCR fallback failed for page {}: {}", pageNum + 1, e.getMessage());
                    }
                }
                geoRows.put(pageNum, rows);
            }

            // 页眉/页脚/页码清理：几何行与 OCR 文本两条链路各自清理
            Map<Integer, Float> geoPageH = new HashMap<>();
            for (int p = 0; p < totalPages; p++) {
                geoPageH.put(p, doc.getPage(p).getCropBox().getHeight());
            }
            stripRowHeaderFooters(geoRows, geoPageH);
            if (!ocrRows.isEmpty()) {
                List<String> ocrTexts = new ArrayList<>();
                List<Integer> ocrPageNums = new ArrayList<>();
                for (Map.Entry<Integer, List<PageLine>> e : ocrRows.entrySet()) {
                    ocrPageNums.add(e.getKey());
                    ocrTexts.add(e.getValue().stream().map(PageLine::text).collect(Collectors.joining("\n")));
                }
                List<String> cleanedOcr = stripHeadersFooters(ocrTexts);
                for (int i = 0; i < ocrPageNums.size(); i++) {
                    List<PageLine> o = new ArrayList<>();
                    int ln = 0;
                    for (String l : cleanedOcr.get(i).split("\n")) {
                        String tt = l.trim();
                        if (!tt.isEmpty()) o.add(new PageLine(++ln * 100f, 80f, 0f, 1f, tt));
                    }
                    ocrRows.put(ocrPageNums.get(i), o);
                }
            }

            // 标题继承：每页第一个标题行，无则继承上一页（跨页章节延续）
            for (int pageNum = 0; pageNum < totalPages; pageNum++) {
                List<PageLine> rows = ocrRows.containsKey(pageNum)
                        ? ocrRows.get(pageNum) : geoRows.getOrDefault(pageNum, new ArrayList<>());
                for (PageLine r : rows) {
                    String t = r.text().trim();
                    if (!t.contains("\t") && isPdfTitleLikeLine(t)) {
                        currentTitle = t;
                        break;
                    }
                }
                pageTitles.put(pageNum + 1, currentTitle);
            }

            // 第二遍：逐页行 → DocNode 装配（行几何分段）
            List<DocNode> pdfNodes = new ArrayList<>();
            for (int pageNum = 0; pageNum < totalPages; pageNum++) {
                if (ocrRows.containsKey(pageNum)) {
                    pdfNodes.addAll(assemblePageNodes(ocrRows.get(pageNum), pageNum + 1, false));
                } else {
                    pdfNodes.addAll(assemblePageNodes(geoRows.getOrDefault(pageNum, new ArrayList<>()), pageNum + 1, true));
                }
            }

            // Markdown 序列化 + 可选 LLM 增强（与 docx/pptx/xlsx 对齐，见 docs/design/parsed-markdown.md ADR-2）
            String markdown = markdownSerializer.serialize(pdfNodes);
            if (strategy != null && strategy.getLlmModel() != null) {
                LlmConfig llmCfg = resolveLlmConfig(strategy);
                markdown = enhanceWithLlm(markdown, strategy.getLlmModel(), llmCfg.apiUrl, llmCfg.apiKey);
            }

            return ParseResult.builder()
                    .text(markdown)
                    .nodes(pdfNodes)
                    .pages(totalPages)
                    .pageTitles(pageTitles)
                    .build();
        }
    }

    /**
     * 页面行（顶左原点 pt，cropBox 相对）：y/height 为行盒位置与视觉高度，
     * x/width 为行横向范围（表格行覆盖全部单元格）；text 内 \t 为列分隔。
     * 公开供 rect 装配（AiChunkServiceImpl.fillPdfRects）与解析共用同一行结构。
     */
    public record PageLine(float y, float height, float x, float width, String text) {}

    /**
     * 逐页提取带坐标的行（静态、无状态，rect 装配与解析共用同一行结构）：
     * 基线差 >2pt 换行；同一视觉行内 x 间隙 > max(6pt, 1.5×字号) 以 \t 连接（列结构标记）。
     */
    public static List<PageLine> extractGeometryLines(PDDocument doc, int pageIndex) throws IOException {
        List<float[]> boxes = new ArrayList<>();   // [minY, maxY, minX, maxX]（顶左原点；y 用 getYDirAdj，排序方向已验证正确）
        List<Float> maxFonts = new ArrayList<>();
        List<StringBuilder> texts = new ArrayList<>();
        boxes.add(new float[]{Float.MAX_VALUE, -1f, Float.MAX_VALUE, -1f});
        maxFonts.add(0f);
        texts.add(new StringBuilder());
        final float[] lastY = {Float.NaN};
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<org.apache.pdfbox.text.TextPosition> positions) {
                if (positions == null || positions.isEmpty()) return;
                org.apache.pdfbox.text.TextPosition first = positions.get(0);
                float y0 = first.getYDirAdj();
                float x0 = first.getXDirAdj();
                // getFontSize() 返回文本状态字号（可能被矩阵放大，如 240pt），
                // 用 getFontSizeInPt() 取真实渲染字号（~12pt）——行高/行盒判定依赖它，
                // 虚高会让 midY 失真导致区域行命中错位
                float font = first.getFontSizeInPt() > 0 ? first.getFontSizeInPt() : 10f;
                boolean newRow = Float.isNaN(lastY[0]) || Math.abs(y0 - lastY[0]) > 2f;
                if (newRow) {
                    boxes.add(new float[]{Float.MAX_VALUE, -1f, Float.MAX_VALUE, -1f});
                    maxFonts.add(0f);
                    texts.add(new StringBuilder());
                } else {
                    float[] b = boxes.get(boxes.size() - 1);
                    if (b[3] > 0 && x0 - b[3] > Math.max(6f, font * 1.5f)) {
                        texts.get(texts.size() - 1).append('\t');
                    }
                }
                for (org.apache.pdfbox.text.TextPosition tp : positions) {
                    lastY[0] = tp.getYDirAdj();
                    float[] b = boxes.get(boxes.size() - 1);
                    b[0] = Math.min(b[0], tp.getYDirAdj());
                    b[1] = Math.max(b[1], tp.getYDirAdj());
                    b[2] = Math.min(b[2], tp.getXDirAdj());
                    b[3] = Math.max(b[3], tp.getXDirAdj() + tp.getWidthDirAdj());
                    int last = maxFonts.size() - 1;
                    maxFonts.set(last, Math.max(maxFonts.get(last), font));
                }
                texts.get(texts.size() - 1).append(text == null ? "" : text);
            }
        };
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(doc);
        List<PageLine> rows = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            String t = texts.get(i).toString();
            if (t.isBlank()) continue;
            float[] b = boxes.get(i);
            float font = Math.max(1f, maxFonts.get(i));
            // 行盒按基线张开（YDirAdj 是基线 y，顶左原点）：字形上沿约在基线上方 0.7~0.88 字号、
            // 降部约在基线下方 0.2~0.25 字号。旧实现上沿只提 0.25、下沿却留 0.75——行盒整体
            // 下坠约半个字号，段落框随之整体下坠数像素（AI分片 结构块"框往下坠几像素"的根因）。
            float top = b[0] - font * 0.8f;
            float bottom = b[1] + font * 0.25f;
            rows.add(new PageLine(top, bottom - top, b[2], b[3] - b[2], t));
        }
        rows.sort(java.util.Comparator.comparingDouble(PageLine::y));
        return rows;
    }

    /** 几何行页眉脚/页码清理（与解析文本清理同规则：跨页频次 + 数字掩码 + 边缘区域） */

    /** 几何行页眉脚/页码清理（与解析文本清理同规则：跨页频次 + 数字掩码 + 边缘区域） */
    public static void stripRowHeaderFooters(Map<Integer, List<PageLine>> pageRows, Map<Integer, Float> pageHeights) {
        if (pageRows == null || pageRows.size() < 2) return;
        int threshold = Math.max(2, (int) Math.ceil(pageRows.size() * 0.3));
        Map<String, Integer> headFreq = new HashMap<>();
        Map<String, Integer> footFreq = new HashMap<>();
        for (Map.Entry<Integer, List<PageLine>> e : pageRows.entrySet()) {
            float pageH = pageHeights.getOrDefault(e.getKey(), 0f);
            List<PageLine> rows = e.getValue();
            for (int i = 0; i < rows.size(); i++) {
                if (!inRowEdgeZone(rows, i, pageH)) continue;
                String t = rows.get(i).text().trim();
                if (t.isEmpty()) continue;
                String key = t.replaceAll("\\d+", "#").replaceAll("\\s+", "");
                headFreq.merge(key, 1, Integer::sum);
                footFreq.merge(key, 1, Integer::sum);
            }
        }
        for (Map.Entry<Integer, List<PageLine>> e : pageRows.entrySet()) {
            float pageH = pageHeights.getOrDefault(e.getKey(), 0f);
            List<PageLine> rows = e.getValue();
            List<PageLine> kept = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                PageLine r = rows.get(i);
                String t = r.text().trim();
                if (t.isEmpty()) continue;
                boolean headZone = i < 2 || (pageH > 0 && (r.y() + r.height()) <= pageH * 0.12f);
                boolean footZone = i >= rows.size() - 2 || (pageH > 0 && r.y() >= pageH * 0.87f);
                String key = t.replaceAll("\\d+", "#").replaceAll("\\s+", "");
                if (headZone && headFreq.getOrDefault(key, 0) >= threshold) continue;
                if (footZone && footFreq.getOrDefault(key, 0) >= threshold) continue;
                if ((headZone || footZone) && isPageNumberLine(t)) continue;
                kept.add(r);
            }
            rows.clear();
            rows.addAll(kept);
        }
    }

    /** 行序前 2 行/末 2 行，或行盒落在页高 12% 顶带（head）/87% 以下底带（foot） */
    private static boolean inRowEdgeZone(List<PageLine> rows, int i, float pageH) {
        if (i < 2 || i >= rows.size() - 2) return true;
        PageLine r = rows.get(i);
        return pageH > 0 && ((r.y() + r.height()) <= pageH * 0.12f || r.y() >= pageH * 0.87f);
    }

    /**
     * 行 → DocNode 装配（行几何分段，通用；阈值均为行高/行距的相对量）：
     * <ul>
     *   <li>段落边界：垂直间距 &gt; max(1.6×中位行距, 0.9×行高)（geometric=true 时）</li>
     *   <li>单行标题样（isPdfTitleLikeLine）→ HEADING，级别按编号深度——块内单行也能识别</li>
     *   <li>Markdown 表格行（| 开头）连续 → TABLE</li>
     *   <li>连续 ≥2 行、行内 ≥2 个 \t 列且单元格 ≤40 字符 → TABLE（文字层表格结构化）</li>
     *   <li>其余累积为 PARAGRAPH（段内行以 \n 连接）</li>
     * </ul>
     * geometric=false（扫描页 OCR 文本，无坐标）时不做间距分段，仅按标题/表格结构切分。
     */
    private List<DocNode> assemblePageNodes(List<PageLine> rows, int pageNo, boolean geometric) {
        List<DocNode> nodes = new ArrayList<>();
        if (rows == null || rows.isEmpty()) return nodes;
        List<PageLine> sorted = new ArrayList<>(rows);
        sorted.sort(java.util.Comparator.comparingDouble(PageLine::y));

        List<Float> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            float g = sorted.get(i).y() - (sorted.get(i - 1).y() + sorted.get(i - 1).height());
            if (g > 0) gaps.add(g);
        }
        Collections.sort(gaps);
        float medianGap = gaps.isEmpty() ? 0f : gaps.get(gaps.size() / 2);
        float rowH = sorted.get(0).height() > 0 ? sorted.get(0).height() : 12f;
        float paraGap = Math.max(medianGap * 1.6f, rowH * 0.9f);
        // 页内行高中位数（正文字号的几何证据）：标题行判定用它区分"真标题"与"无标点的正文换行行"
        List<Float> heights = new ArrayList<>();
        for (PageLine r : sorted) {
            if (r.height() > 0) heights.add(r.height());
        }
        Collections.sort(heights);
        float medianH = heights.isEmpty() ? 0f : heights.get(heights.size() / 2);

        List<String> paraLines = new ArrayList<>();
        float prevBottom = -1f;

        for (int i = 0; i < sorted.size(); i++) {
            PageLine row = sorted.get(i);
            String t = row.text() == null ? "" : row.text().trim();
            float rowBottom = row.y() + row.height();
            if (t.isEmpty()) {
                prevBottom = rowBottom;
                continue;
            }
            // 标题行判定须带几何/编号证据（isPdfTitleRow）：纯文本特征会把中文正文
            // 的无标点换行行几乎全部判成标题 → 每行一个 HEADING 节点，段落粒度退化为行
            boolean titleLike = !t.contains("\t") && isPdfTitleRow(row, medianH);
            boolean mdTable = t.startsWith("|");
            int tabCols = t.contains("\t") ? t.split("\t", -1).length : 0;
            boolean tabRow = tabCols >= 2;

            boolean paraBreak = geometric && !paraLines.isEmpty() && i > 0
                    && (row.y() - prevBottom) > paraGap;
            if (titleLike || mdTable || tabRow) paraBreak = true;
            if (paraBreak) {
                flushParagraph(paraLines, nodes, pageNo);
            }

            if (titleLike) {
                nodes.add(DocNode.builder()
                        .type(DocNode.NodeType.HEADING)
                        .level(titleLevel(t))
                        .title(t)
                        .pageNumber(pageNo)
                        .build());
                prevBottom = rowBottom;
                continue;
            }
            if (mdTable) {
                int j = i;
                List<String> block = new ArrayList<>();
                while (j < sorted.size() && sorted.get(j).text().trim().startsWith("|")) {
                    block.add(sorted.get(j).text().trim());
                    j++;
                }
                DocNode tableNode = buildMarkdownTableNode(String.join("\n", block), pageNo);
                if (tableNode != null) {
                    nodes.add(tableNode);
                    prevBottom = sorted.get(j - 1).y() + sorted.get(j - 1).height();
                    i = j - 1;
                    continue;
                }
                paraLines.add(t);
                prevBottom = rowBottom;
                continue;
            }
            if (tabRow) {
                int j = i;
                int cols = tabCols;
                List<String> block = new ArrayList<>();
                while (j < sorted.size()) {
                    String rt = sorted.get(j).text().trim();
                    String[] cells = rt.split("\t", -1);
                    if (cells.length != cols
                            || !Arrays.stream(cells).allMatch(c -> c.length() <= 40)) break;
                    block.add(rt);
                    j++;
                }
                if (block.size() >= 2) {
                    List<List<String>> tableRows = new ArrayList<>();
                    for (String rt : block) {
                        List<String> cells = new ArrayList<>();
                        for (String c : rt.split("\t", -1)) cells.add(c.trim());
                        tableRows.add(cells);
                    }
                    nodes.add(DocNode.builder()
                            .type(DocNode.NodeType.TABLE)
                            .headers(tableRows.get(0))
                            .rows(tableRows.subList(1, tableRows.size()))
                            .pageNumber(pageNo)
                            .build());
                    prevBottom = sorted.get(j - 1).y() + sorted.get(j - 1).height();
                    i = j - 1;
                    continue;
                }
            }
            paraLines.add(t);
            prevBottom = rowBottom;
        }
        flushParagraph(paraLines, nodes, pageNo);
        return nodes;
    }

    /** 段落行累积 → PARAGRAPH 节点（段内行 \n 连接，保留行结构供锚定/前端 normalizeLines 使用） */
    private void flushParagraph(List<String> paraLines, List<DocNode> nodes, int pageNo) {
        if (paraLines.isEmpty()) return;
        nodes.add(DocNode.builder()
                .type(DocNode.NodeType.PARAGRAPH)
                .content(String.join("\n", paraLines))
                .pageNumber(pageNo)
                .build());
        paraLines.clear();
    }

    /** tab 分隔行 → TABLE 节点（首行为表头）；少于「表头 + 1 数据行」返回 null */
    private DocNode buildTabTableNode(List<String> rowTexts, int pageNo) {
        List<List<String>> rows = new ArrayList<>();
        for (String rt : rowTexts) {
            List<String> cells = new ArrayList<>();
            for (String c : rt.split("\t", -1)) cells.add(c.trim());
            rows.add(cells);
        }
        if (rows.size() < 2) return null;
        return DocNode.builder()
                .type(DocNode.NodeType.TABLE)
                .headers(rows.get(0))
                .rows(rows.subList(1, rows.size()))
                .pageNumber(pageNo)
                .build();
    }

    /**
     * 从 PDF 页文本中检测"标题行"（供图片分片的语义上下文）。
     * 取该页第一个符合条件的行。特征：
     * - 长度 5~60 字符（过滤短页眉与长正文）
     * - 非纯数字/页码
     * - 不以句子结束标点结尾（。，；：！？等，标题通常无句号）
     * - 数字 token 少于 3 个（表格/数据行通常含多个数字）
     */
    private String detectPdfTitleLine(String pageText) {
        if (pageText == null || pageText.isBlank()) return null;
        for (String line : pageText.split("\n")) {
            String t = line.trim();
            if (isPdfTitleLikeLine(t)) return t;
        }
        return null;
    }

    /**
     * 页眉/页脚/页码清理：
     * <ol>
     *   <li>纯页码行（"12"、"- 3 -"、"第5页/共10页"）出现在页面前两行/末两行 → 删除；</li>
     *   <li>规范化后相同文本在 ≥30% 页（且 ≥2 页）的页首区重复 → 判定为页眉，删除；
     *       页尾区重复 → 页脚，删除。</li>
     * </ol>
     * 仅处理页面前两行/末两行区域，正文内容不受影响；单页文档不做清理。
     */
    private List<String> stripHeadersFooters(List<String> pageTexts) {
        int n = pageTexts.size();
        if (n < 2) return pageTexts;
        Map<String, Integer> headFreq = new HashMap<>();
        Map<String, Integer> footFreq = new HashMap<>();
        List<List<String>> pageLines = new ArrayList<>();
        for (String pt : pageTexts) {
            List<String> lines = new ArrayList<>();
            if (pt != null) {
                for (String l : pt.split("\n")) {
                    String t = l.trim();
                    if (!t.isEmpty()) lines.add(t);
                }
            }
            pageLines.add(lines);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                headFreq.merge(normLineFreq(lines.get(i)), 1, Integer::sum);
                footFreq.merge(normLineFreq(lines.get(lines.size() - 1 - i)), 1, Integer::sum);
            }
        }
        int threshold = Math.max(2, (int) Math.ceil(n * 0.3));
        Set<String> headerLines = frequentOf(headFreq, threshold);
        Set<String> footerLines = frequentOf(footFreq, threshold);

        List<String> out = new ArrayList<>();
        for (List<String> lines : pageLines) {
            List<String> kept = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i);
                String norm = normLineFreq(t);
                boolean inHeadZone = i < 2;
                boolean inFootZone = i >= lines.size() - 2;
                if ((inHeadZone && headerLines.contains(norm)) || (inFootZone && footerLines.contains(norm))) continue;
                if ((inHeadZone || inFootZone) && isPageNumberLine(t)) continue;
                kept.add(t);
            }
            out.add(String.join("\n", kept));
        }
        return out;
    }

    /** 频次归一化：数字掩码为 #（页码不同的页脚行也能对上频次）、去空白 */
    private String normLineFreq(String s) {
        return normLine(s).replaceAll("\\d+", "#");
    }

    private Set<String> frequentOf(Map<String, Integer> freq, int threshold) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, Integer> e : freq.entrySet()) {
            if (e.getValue() >= threshold) out.add(e.getKey());
        }
        return out;
    }

    private String normLine(String s) {
        return s.replaceAll("\\s+", "");
    }

    /** 纯页码行：12、- 3 -、第5页、第5页/共10页、3/28 等 */
    private static boolean isPageNumberLine(String s) {
        String t = s.trim();
        return t.matches("[-–—\\s]*\\d{1,4}[-–—\\s]*")
                || t.matches("第\\s*\\d+\\s*页(\\s*[,，/]\\s*共?\\s*\\d+\\s*页)?")
                || t.matches("\\d{1,4}\\s*/\\s*\\d{1,4}");
    }

    public static boolean isPdfTitleLikeLine(String t) {
        if (t.isEmpty()) return false;
        if (t.length() < 5 || t.length() > 60) return false;
        if (t.matches("^[\\d\\s\\-–.]+$")) return false; // 纯数字/页码
        char last = t.charAt(t.length() - 1);
        if (last == '。' || last == '.' || last == '，' || last == ',' || last == '；'
                || last == ';' || last == '：' || last == ':' || last == '！' || last == '？') {
            return false;
        }
        // 数字 token 过多（表格/数据行特征）
        long digitTokens = java.util.Arrays.stream(t.split("\\s+"))
                .filter(w -> w.matches(".*\\d.*")).count();
        return digitTokens < 3;
    }

    /** 显式标题编号：1.2 / 2.1.3（后随分隔或空白）/ 1、 / 一、 / 第二章（分隔符后须非数字，排除"1.5万元"这类小数开头的正文行） */
    private static final java.util.regex.Pattern TITLE_NUMBERED_PATTERN = java.util.regex.Pattern.compile(
            "^\\s*(?:\\d+(?:\\.\\d+)+(?=[\\s、.．（(])"
                    + "|\\d{1,3}[、.．](?=\\s*[^\\d\\s])"
                    + "|[一二三四五六七八九十百]+[、.．](?=\\s*\\S)"
                    + "|第[一二三四五六七八九十百\\d]+[章节条款篇部分])");

    /**
     * 标题行判定（几何证据版，仅用于行→节点装配）：在 isPdfTitleLikeLine 文本特征之上，
     * 还须满足其一——显式编号；行高 ≥ 页内行高中位数 × 1.12（真标题字号通常大于正文）。
     * 纯文本特征会把中文/英文正文的无标点换行行几乎全部误判为标题：每行一个 HEADING 节点，
     * 段落粒度退化为行（AI分片 结构块框选"一行一框"即源于此）。
     */
    public static boolean isPdfTitleRow(PageLine row, float medianH) {
        String t = row.text() == null ? "" : row.text().trim();
        if (!isPdfTitleLikeLine(t)) return false;
        if (TITLE_NUMBERED_PATTERN.matcher(t).find()) return true;
        return medianH > 0 && row.height() >= medianH * 1.12f;
    }

    /** 标题级别：按编号深度推断（"1"→1、"2.1"→2、"2.1.1"→3，封顶 4；无编号默认 2） */
    private int titleLevel(String t) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d+(?:\\.\\d+)*)").matcher(t);
        if (m.find()) {
            return Math.min(4, m.group(1).split("\\.").length);
        }
        return 2;
    }

    /**
     * Markdown 表格块（OCR 常见输出，| 分隔单元格）→ TABLE 节点。
     * 跳过 |---| 分隔行；单元格转 tab 语义（与 tableText 序列化格式一致，前端 parseTable 可直接渲染）。
     * 少于「表头 + 1 数据行」不成表，按普通段落处理。
     */
    private DocNode buildMarkdownTableNode(String block, int pageNo) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : block.split("\n")) {
            String t = line.trim();
            if (!t.startsWith("|")) continue;
            String inner = t.length() > 1 && t.endsWith("|") ? t.substring(1, t.length() - 1) : t.substring(1);
            String[] cells = inner.split("\\|", -1);
            boolean separator = Arrays.stream(cells).allMatch(c -> c.trim().matches(":?-{2,}:?"));
            if (separator) continue;
            List<String> row = new ArrayList<>();
            for (String c : cells) row.add(c.trim());
            rows.add(row);
        }
        if (rows.size() < 2) return null;
        return DocNode.builder()
                .type(DocNode.NodeType.TABLE)
                .headers(rows.get(0))
                .rows(rows.subList(1, rows.size()))
                .pageNumber(pageNo)
                .build();
    }

    private ParseResult parseDocx(InputStream stream, KbParseStrategy strategy) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(stream)) {
            // 结构化解析：遍历 body elements（同时收集内嵌图片字节，供消费方上传 MinIO）
            List<ParseResult.ParseImage> images = new ArrayList<>();
            int[] skippedDecorative = {0}; // 装饰性图片过滤计数（解析期内共享）
            List<DocNode> nodes = parseDocxStructured(doc, images, skippedDecorative);
            // 频次去重：同文档重复 >= 3 次的图片（模板 logo/水印）整组过滤（同步剔除 IMAGE 节点）
            deduplicateRepeatedImages(nodes, images);
            // 序列化为 Markdown
            String markdown = markdownSerializer.serialize(nodes);
            // 可选 LLM 增强
            if (strategy != null && strategy.getLlmModel() != null) {
                LlmConfig llmCfg = resolveLlmConfig(strategy);
                markdown = enhanceWithLlm(markdown, strategy.getLlmModel(), llmCfg.apiUrl, llmCfg.apiKey);
            }
            log.info("Extracted {} images from .docx, filtered {} decorative images",
                    images.size(), skippedDecorative[0]);
            return ParseResult.builder()
                    .text(markdown)
                    .nodes(nodes)
                    .images(images)
                    .pages(1)
                    .build();
        }
    }

    /**
     * 解析旧版 Word 文档（.doc，OLE2 二进制格式）。
     *
     * <p>使用 POI HWPF 组件提取文本和嵌入图片。HWPF 处于维护模式，无法像 docx 一样
     * 提取标题层级/表格结构，因此降级为纯文本段落提取：文本内容完整保留（含表格单元格文本），
     * 但结构化程度弱于 docx。图片通过 PicturesTable 提取，供图片分片使用。</p>
     *
     * <p>处理流程：HWPFDocument 打开 → WordExtractor 提取全文 → 按行生成 PARAGRAPH 节点
     * → 提取嵌入图片 → Markdown 序列化 → 可选 LLM 增强。</p>
     */
    private ParseResult parseDoc(InputStream stream, KbParseStrategy strategy) throws Exception {
        byte[] bytes = stream.readAllBytes();
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            // 1. 提取纯文本（WordExtractor 自动拼接段落/表格单元格文本）
            String rawText;
            try (WordExtractor extractor = new WordExtractor(doc)) {
                rawText = extractor.getText();
            }

            // 2. 按行生成 PARAGRAPH 节点（doc 无标题样式信息，统一按段落处理）
            List<DocNode> nodes = new ArrayList<>();
            for (String line : rawText.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                nodes.add(DocNode.builder()
                        .type(DocNode.NodeType.PARAGRAPH)
                        .content(trimmed)
                        .build());
            }

            // 3. 提取嵌入图片（上下文取文档文本开头片段，HWPF 无法定位图片所在段落）
            List<ParseResult.ParseImage> images = extractDocImages(doc, nodes);
            // 频次去重：同文档重复 >= 3 次的图片（模板 logo/水印）整组过滤（同步剔除 IMAGE 节点）
            deduplicateRepeatedImages(nodes, images);

            // 4. 序列化为 Markdown 文本
            String text = markdownSerializer.serialize(nodes);

            // 5. 可选 LLM 增强
            if (strategy != null && strategy.getLlmModel() != null) {
                LlmConfig llmCfg = resolveLlmConfig(strategy);
                text = enhanceWithLlm(text, strategy.getLlmModel(), llmCfg.apiUrl, llmCfg.apiKey);
            }

            return ParseResult.builder()
                    .text(text)
                    .nodes(nodes)
                    .images(images)
                    .pages(1)
                    .build();
        } catch (Exception e) {
            // 加密/损坏文件给出明确提示（复用异常链关键字检测）
            if (isEncryptedWorkbookError(e)) {
                throw new RuntimeException("Word 文件已加密或受密码保护，无法解析", e);
            }
            throw e;
        }
    }

    /**
     * 从 HWPFDocument 提取嵌入图片为 ParseImage 列表，同时在 DocNode 中插入 IMAGE 节点。
     * 图片上下文取文档起始文本片段（HWPF 无法定位图片所属段落）。
     */
    private List<ParseResult.ParseImage> extractDocImages(HWPFDocument doc, List<DocNode> nodes) {
        List<ParseResult.ParseImage> images = new ArrayList<>();
        int skipped = 0;
        try {
            PicturesTable pics = doc.getPicturesTable();
            if (pics == null) return images;
            List<Picture> allPics = pics.getAllPictures();
            if (allPics == null || allPics.isEmpty()) return images;

            // 上下文：取节点文本前 200 字符作为图片的语义上下文
            StringBuilder contextBuilder = new StringBuilder();
            for (DocNode n : nodes) {
                if (n.getContent() != null) {
                    contextBuilder.append(n.getContent()).append("\n");
                    if (contextBuilder.length() > 200) break;
                }
            }
            String context = contextBuilder.length() > 200
                    ? contextBuilder.substring(0, 200) : contextBuilder.toString();

            for (int i = 0; i < allPics.size(); i++) {
                Picture pic = allPics.get(i);
                // 解析期统一过滤（ImageFilter）：尺寸/宽高比规则，尺寸未知时按字节 < 15KB 兜底；
                // 视觉丰富度规则：纯色/透明占比高或颜色种类少的色块/占位符
                // （HWPF 无法定位图片所属段落/页眉页脚，PicturesTable 含页眉页脚图片，
                //   靠尺寸/字节/颜色规则过滤掉大部分装饰图）
                Integer w = pic.getWidth() > 0 ? (int) pic.getWidth() : null;
                Integer h = pic.getHeight() > 0 ? (int) pic.getHeight() : null;
                if (ImageFilter.isDecorative(w, h, pic.getContent())
                        || ImageFilter.isDecorativeByColor(pic.getContent())) {
                    skipped++;
                    continue;
                }
                String ext = pic.suggestFileExtension();
                if (ext == null || ext.isBlank()) ext = "png";
                // 稳定命名（按文档内图片序号，与 images 列表一一对应），
                // 由 IngestionConsumer 按 {kbId}/{fileId}/images/{imageKey} 上传到 MinIO
                String imageKey = "doc_img_" + i + "." + ext;
                nodes.add(DocNode.builder()
                        .type(DocNode.NodeType.IMAGE)
                        .imageKey(imageKey)
                        .imageCaption("图片")
                        .build());
                images.add(ParseResult.ParseImage.builder()
                        .imageKey(imageKey)
                        .data(pic.getContent())
                        .contentType(resolveImageContentType(ext))
                        .width(w)
                        .height(h)
                        .context(context)
                        .build());
            }
            log.info("Extracted {} images from .doc file, filtered {} decorative images",
                    images.size(), skipped);
        } catch (Exception e) {
            log.warn("Failed to extract images from .doc file: {}", e.getMessage());
        }
        return images;
    }

    /**
     * 频次去重（模板与频次规则）：同文档内 MD5/pHash 重复出现 >= 3 次的图片
     * （公司公文模板 Logo、全局水印、每页重复的装饰图）整组过滤，
     * 同步剔除 nodes 中对应的 IMAGE 节点，避免 Markdown 序列化残留失效图片引用。
     */
    private void deduplicateRepeatedImages(List<DocNode> nodes, List<ParseResult.ParseImage> images) {
        if (images == null || images.size() < ImageDedup.REPEAT_THRESHOLD) return;
        Map<String, byte[]> keyToData = new HashMap<>();
        for (ParseResult.ParseImage img : images) {
            keyToData.put(img.getImageKey(), img.getData());
        }
        Set<String> repeated = ImageDedup.findRepeatedKeys(keyToData);
        if (repeated.isEmpty()) return;
        images.removeIf(img -> repeated.contains(img.getImageKey()));
        nodes.removeIf(n -> n.getType() == DocNode.NodeType.IMAGE && repeated.contains(n.getImageKey()));
        log.info("Filtered {} repeated decorative images (template logo/watermark)", repeated.size());
    }

    /**
     * 结构化解析 DOCX：遍历 body elements，生成 DocNode 列表。
     * 同时维护"最近标题"作为图片等元素的上下文（用于图片分片的语义上下文）。
     */
    private List<DocNode> parseDocxStructured(XWPFDocument doc, List<ParseResult.ParseImage> images,
                                              int[] skippedDecorative) {
        List<DocNode> nodes = new ArrayList<>();
        String currentTitle = null;
        for (IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFParagraph p) {
                // 维护最近标题上下文（供图片分片使用）
                String heading = docxHeadingTitle(p);
                if (heading != null) {
                    currentTitle = heading;
                }
                // 一个段落可能产出多个节点（标题+图片，或公式+图片等）
                nodes.addAll(parseDocxParagraph(p, images, currentTitle, skippedDecorative));
            } else if (element instanceof XWPFTable t) {
                nodes.add(parseDocxTable(t));
            }
        }
        return nodes;
    }

    /**
     * 判断 DOCX 段落是否为标题（Word 样式名含 heading），是则返回标题文本，否则返回 null。
     * 供 parseDocxParagraph 产出 HEADING 节点、parseDocxStructured 维护上下文共用。
     */
    private String docxHeadingTitle(XWPFParagraph p) {
        String style = p.getStyle();
        String styleId = p.getStyleID();
        if (style == null && styleId == null) return null;
        String s = (style != null ? style : styleId).toLowerCase();
        if (s.startsWith("heading") || s.contains("heading")) {
            String text = p.getText().trim();
            return text.isEmpty() ? p.getText() : text;
        }
        return null;
    }

    /**
     * 解析 DOCX 段落：检测标题、代码块、数学公式、内嵌图片、普通段落
     * 返回列表以支持一个段落产出多个节点（如多个图片）
     *
     * @param images           图片收集容器（供消费方上传 MinIO + OCR）
     * @param contextTitle     图片所在位置的最近标题（文档上下文，可为 null）
     * @param skippedDecorative 装饰性图片过滤计数（与 parseDocx 共享，int[] 以便在循环中累加）
     */
    private List<DocNode> parseDocxParagraph(XWPFParagraph p, List<ParseResult.ParseImage> images,
                                             String contextTitle, int[] skippedDecorative) {
        List<DocNode> nodes = new ArrayList<>();
        String style = p.getStyle();
        String styleId = p.getStyleID();
        String text = p.getText().trim();

        // 1. 标题检测（通过 Word 样式名）
        String heading = docxHeadingTitle(p);
        if (heading != null) {
            String s = (p.getStyle() != null ? p.getStyle() : p.getStyleID()).toLowerCase();
            int level = extractDocxHeadingLevel(s);
            nodes.add(DocNode.builder()
                    .type(DocNode.NodeType.HEADING)
                    .level(level)
                    .title(heading)
                    .build());
            return nodes; // 标题段落只产出标题节点
        }
        // TOC 目录样式跳过（style/styleId 已在方法开头声明）
        if (style != null || styleId != null) {
            String s = (style != null ? style : styleId).toLowerCase();
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

        // 5. 图片节点生成（解析期过滤装饰性图片：小尺寸/分隔线/极小字节，
        //    避免垃圾图上传 MinIO 与 OCR；消费端不再需要过滤）
        if (hasPictures) {
            // 图片上下文：最近标题 + 所在段落文本摘要（标题 | 段落摘要）
            String picContext = null;
            if (text != null && !text.isBlank()) {
                String excerpt = text.length() > 80 ? text.substring(0, 80) + "..." : text;
                picContext = (contextTitle != null && !contextTitle.isBlank())
                        ? contextTitle + " | " + excerpt
                        : excerpt;
            } else {
                picContext = contextTitle;
            }
            for (XWPFPicture pic : pictures) {
                XWPFPictureData picData = pic.getPictureData();
                // 无数据的图片无法上传 MinIO，直接跳过
                if (picData == null) continue;
                // 解析期统一过滤（ImageFilter）：尺寸/宽高比规则，尺寸未知时按字节 < 15KB 兜底；
                // 视觉丰富度规则：纯色/透明占比高或颜色种类少的色块/占位符
                Integer w = extractPictureWidth(pic);
                Integer h = extractPictureHeight(pic);
                if (ImageFilter.isDecorative(w, h, picData.getData())
                        || ImageFilter.isDecorativeByColor(picData.getData())) {
                    skippedDecorative[0]++;
                    continue;
                }
                String ext = picData.suggestFileExtension();
                // 稳定命名（按文档内图片序号，与 images 列表一一对应），
                // 由 IngestionConsumer 按 {kbId}/{fileId}/images/{imageKey} 上传到 MinIO
                String imageKey = "docx_img_" + images.size() + "." + ext;
                String caption = pic.getDescription() != null ? pic.getDescription() : "图片";
                nodes.add(DocNode.builder()
                        .type(DocNode.NodeType.IMAGE)
                        .imageKey(imageKey)
                        .imageCaption(caption)
                        .build());
                images.add(ParseResult.ParseImage.builder()
                        .imageKey(imageKey)
                        .data(picData.getData())
                        .contentType(resolveImageContentType(ext))
                        .width(w)
                        .height(h)
                        .context(picContext)
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
     * 根据图片扩展名解析 MIME 类型（用于 MinIO 上传）
     */
    private String resolveImageContentType(String ext) {
        if (ext == null) return "application/octet-stream";
        return switch (ext.toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "tiff" -> "image/tiff";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    /**
     * 提取图片宽度（像素，EMU → px），失败返回 null（如无 extents 或 schema 方法不可用）
     */
    private Integer extractPictureWidth(XWPFPicture pic) {
        return extractPictureExtent(pic, true);
    }

    /**
     * 提取图片高度（像素，EMU → px），失败返回 null
     */
    private Integer extractPictureHeight(XWPFPicture pic) {
        return extractPictureExtent(pic, false);
    }

    /**
     * 从 CTPicture 读取图片尺寸（EMU 单位，1px = 9525 EMU）。
     * <p>尺寸位于 pic:pic 祖先的 wp:inline / wp:anchor 的 wp:extent 子元素（cx/cy 属性）。
     * 用 DOM 遍历实现，不依赖 poi-ooxml-lite 中 schema 方法的存在性。</p>
     */
    private Integer extractPictureExtent(XWPFPicture pic, boolean width) {
        try {
            Object ctPicture = pic.getCTPicture();
            if (ctPicture == null) return null;
            org.w3c.dom.Node node = (org.w3c.dom.Node)
                    ctPicture.getClass().getMethod("getDomNode").invoke(ctPicture);

            // 向上找 wp:inline 或 wp:anchor（其下含 wp:extent 尺寸元素）
            while (node != null) {
                if (node instanceof org.w3c.dom.Element el) {
                    String name = el.getLocalName();
                    if ("inline".equals(name) || "anchor".equals(name)) {
                        org.w3c.dom.NodeList children = el.getChildNodes();
                        for (int i = 0; i < children.getLength(); i++) {
                            org.w3c.dom.Node child = children.item(i);
                            if (child instanceof org.w3c.dom.Element ce
                                    && "extent".equals(ce.getLocalName())) {
                                String attr = width ? "cx" : "cy";
                                String val = ce.getAttribute(attr);
                                if (!val.isEmpty()) {
                                    return (int) (Long.parseLong(val) / 9525);
                                }
                            }
                        }
                    }
                }
                node = node.getParentNode();
            }
            return null;
        } catch (Exception e) {
            log.debug("Failed to extract picture extent: {}", e.getMessage());
            return null;
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

    /**
     * 解析 PPTX：结构化遍历 slide shapes 生成 DocNode（对齐 DOCX）。
     * - 每页生成 HEADING（页标题占位符或"第 N 页"），作为分片边界
     * - XSLFTextShape → PARAGRAPH / HEADING（标题占位符）
     * - XSLFTable → DocNode(TABLE)（复用 isHeaderRow 识别表头）
     * - XSLFPictureShape → IMAGE 节点 + ParseImage（上传 + OCR 由 IngestionConsumer 完成）
     * - XSLFGroupShape → 递归
     * - 图表（XSLFGraphicFrame）不提取（POI 对 chart part 支持受限）
     */
    private ParseResult parsePptx(InputStream stream, String extension, KbParseStrategy strategy) throws Exception {
        // 旧版二进制 .ppt（OLE2）：XMLSlideShow 仅支持 OOXML .pptx，降级走 HSLF（与 .doc→HWPF 同模式）
        if (extension != null && ".ppt".equalsIgnoreCase(extension.trim())) {
            return parsePptLegacy(stream, strategy);
        }
        try (XMLSlideShow ppt = new XMLSlideShow(stream)) {
            List<DocNode> nodes = new ArrayList<>();
            List<ParseResult.ParseImage> images = new ArrayList<>();
            int[] skippedDecorative = {0}; // 装饰性图片过滤计数（递归遍历内共享）
            int slideIndex = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                // Slide 边界：HEADING（页标题占位符文本或"第 N 页"）
                String slideTitle = slide.getTitle();
                nodes.add(DocNode.builder()
                        .type(DocNode.NodeType.HEADING)
                        .level(1)
                        .title(slideTitle != null && !slideTitle.isBlank()
                                ? slideTitle : "第 " + (slideIndex + 1) + " 页")
                        .pageNumber(slideIndex + 1)
                        .build());

                // 遍历 shapes（递归处理组形状）
                parsePptxShapes(slide, nodes, images, slideIndex, skippedDecorative);
                slideIndex++;
            }

            // 频次去重：同文档重复 >= 3 次的图片（模板 logo/水印）整组过滤（同步剔除 IMAGE 节点）
            deduplicateRepeatedImages(nodes, images);

            // 序列化为 Markdown（供 ruleBasedChunk 兜底与展示）
            String text = markdownSerializer.serialize(nodes);
            log.info("Parsed {} slides, extracted {} images, filtered {} decorative images",
                    slideIndex, images.size(), skippedDecorative[0]);
            return ParseResult.builder()
                    .text(text)
                    .pages(slideIndex)
                    .nodes(nodes)
                    .images(images)
                    .build();
        }
    }

    /**
     * 递归遍历 PPT shapes，生成 DocNode / 收集图片
     */
    private void parsePptxShapes(XSLFShapeContainer container, List<DocNode> nodes,
                                 List<ParseResult.ParseImage> images, int slideIndex,
                                 int[] skippedDecorative) {
        for (XSLFShape shape : container) {
            if (shape instanceof XSLFTextShape textShape) {
                String text = textShape.getText().trim();
                if (text.isEmpty()) continue;
                // 符号字符画过滤：SmartArt/结构图导出的 ◻◇ 占位字符树无语义价值，
                // 曾实测整段进入向量库污染检索（约 200 行符号树被向量化）
                if (isSymbolArtText(text)) {
                    skippedDecorative[0]++;
                    continue;
                }
                // 标题占位符 → HEADING；其余 → PARAGRAPH
                if (isPptxTitlePlaceholder(textShape)) {
                    nodes.add(DocNode.builder()
                            .type(DocNode.NodeType.HEADING)
                            .level(1)
                            .title(text)
                            .pageNumber(slideIndex + 1)
                            .build());
                } else {
                    nodes.add(DocNode.builder()
                            .type(DocNode.NodeType.PARAGRAPH)
                            .content(text)
                            .pageNumber(slideIndex + 1)
                            .build());
                }
            } else if (shape instanceof XSLFTable table) {
                // 表格 → 结构化 TABLE 节点（复用 isHeaderRow）
                nodes.add(parsePptxTable(table, slideIndex));
            } else if (shape instanceof XSLFPictureShape pic) {
                // 图片 → IMAGE 节点 + ParseImage（上传 + OCR 由消费方完成）
                XSLFPictureData picData = pic.getPictureData();
                if (picData == null) continue;
                // 解析期统一过滤（ImageFilter）：尺寸/宽高比规则，尺寸未知时按字节 < 15KB 兜底；
                // 视觉丰富度规则：纯色/透明占比高或颜色种类少的色块/占位符
                Integer w = extractPptxPictureWidth(pic);
                Integer h = extractPptxPictureHeight(pic);
                if (ImageFilter.isDecorative(w, h, picData.getData())
                        || ImageFilter.isDecorativeByColor(picData.getData())) {
                    skippedDecorative[0]++;
                    continue;
                }
                String ext = picData.suggestFileExtension();
                String imageKey = "pptx_img_" + images.size() + "." + ext;
                nodes.add(DocNode.builder()
                        .type(DocNode.NodeType.IMAGE)
                        .imageKey(imageKey)
                        .imageCaption("图片")
                        .pageNumber(slideIndex + 1)
                        .build());
                images.add(ParseResult.ParseImage.builder()
                        .imageKey(imageKey)
                        .data(picData.getData())
                        .contentType(resolveImageContentType(ext))
                        .width(w)
                        .height(h)
                        .context("第 " + (slideIndex + 1) + " 页")
                        .pageNumber(slideIndex + 1)
                        .build());
            } else if (shape instanceof XSLFGroupShape group) {
                // 组形状：递归
                parsePptxShapes(group, nodes, images, slideIndex, skippedDecorative);
            }
            // XSLFGraphicFrame（图表/SmartArt）、连接符等：不提取（POI 支持受限）
        }
    }

    /**
     * 判断 PPT 文本形状是否为标题占位符（TITLE / CENTERED_TITLE / SUBTITLE）
     */
    private boolean isPptxTitlePlaceholder(XSLFTextShape textShape) {
        try {
            org.apache.poi.sl.usermodel.Placeholder ph = textShape.getPlaceholder();
            return ph == org.apache.poi.sl.usermodel.Placeholder.TITLE
                    || ph == org.apache.poi.sl.usermodel.Placeholder.CENTERED_TITLE
                    || ph == org.apache.poi.sl.usermodel.Placeholder.SUBTITLE;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * HSLF（旧版 .ppt）标题占位符判断。
     * HSLF 文本形状的 getPlaceholder 为具体类方法（不在 common-sl 接口上），反射调用。
     */
    private boolean isHslfTitlePlaceholder(Object shape) {
        try {
            Object ph = shape.getClass().getMethod("getPlaceholder").invoke(shape);
            if (ph == null) return false;
            String name = String.valueOf(ph);
            return "TITLE".equals(name) || "CENTERED_TITLE".equals(name) || "SUBTITLE".equals(name);
        } catch (Exception e) {
            return false;
        }
    }

    /** MIME 类型 → 扩展名（HSLF 图片数据无 suggestFileExtension，从 contentType 反推） */
    private static String contentTypeToExt(String contentType) {
        if (contentType == null) return "png";
        String ct = contentType.toLowerCase();
        if (ct.contains("jpeg") || ct.contains("jpg")) return "jpg";
        if (ct.contains("png")) return "png";
        if (ct.contains("gif")) return "gif";
        if (ct.contains("bmp")) return "bmp";
        if (ct.contains("tiff")) return "tiff";
        if (ct.contains("emf")) return "emf";
        if (ct.contains("wmf")) return "wmf";
        return "png";
    }

    /**
     * 检测「符号字符画」文本：SmartArt/结构关系图导出的占位字符树
     * （如 "◻[3], [258.42]\n │\n◻[3], [267.37]..."，由几何符号+坐标数字构成）。
     *
     * <p>判定规则：非空白字符 ≥ 30、几何/制表/箭头符号出现 ≥ 5 次、
     * 且字母与 CJK 字符占比 < 10%（正常 PPT 文本以文字为主，代码块以字母为主，均不误杀）。
     * 这类文本无语义价值，曾实测整段进入向量库污染检索。
     */
    static boolean isSymbolArtText(String text) {
        if (text == null) return false;
        int geo = 0;      // 几何符号（U+25A0–25FF）、制表符（U+2500–257F）、箭头（U+2190–21FF）
        int letters = 0;  // 字母/CJK（有效语义字符，Character.isLetter 对中文为 true）
        int len = 0;      // 非空白字符总数
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            len++;
            if (Character.isLetter(c)) {
                letters++;
            } else if ((c >= 0x2500 && c <= 0x25FF) || (c >= 0x2190 && c <= 0x21FF)) {
                geo++;
            }
        }
        return len >= 30 && geo >= 5 && letters * 10 < len;
    }

    /**
     * 解析旧版 PPT（.ppt，OLE2 二进制格式）。
     *
     * <p>使用 POI HSLF 组件（与 .doc→HWPF 同为降级模式）。HSLF 无结构化表格 API
     * （PPT97 表格以形状组合呈现，单元格文本经文本形状提取），结构化程度弱于 pptx，
     * 但标题层级、正文、嵌入图片均完整保留，图片处理与 pptx 对齐
     * （ImageFilter 装饰图过滤 + 频次去重 + 消费方上传/OCR）。
     */
    private ParseResult parsePptLegacy(InputStream stream, KbParseStrategy strategy) throws Exception {
        try (HSLFSlideShow ppt = new HSLFSlideShow(stream)) {
            List<DocNode> nodes = new ArrayList<>();
            List<ParseResult.ParseImage> images = new ArrayList<>();
            int[] skippedDecorative = {0};
            int slideIndex = 0;
            for (HSLFSlide slide : ppt.getSlides()) {
                // Slide 边界：HEADING（页标题或"第 N 页"，与 pptx 一致）
                String slideTitle = null;
                try {
                    slideTitle = slide.getTitle();
                } catch (Exception ignore) {
                    // HSLF 标题提取失败时退化为页码占位
                }
                nodes.add(DocNode.builder()
                        .type(DocNode.NodeType.HEADING)
                        .level(1)
                        .title(slideTitle != null && !slideTitle.isBlank()
                                ? slideTitle : "第 " + (slideIndex + 1) + " 页")
                        .pageNumber(slideIndex + 1)
                        .build());

                parsePptLegacyShapes(slide, nodes, images, slideIndex, skippedDecorative);
                slideIndex++;
            }

            // 频次去重：同文档重复 >= 3 次的图片（模板 logo/水印）整组过滤（与 pptx/docx 一致）
            deduplicateRepeatedImages(nodes, images);

            String text = markdownSerializer.serialize(nodes);
            log.info("Parsed {} slides (legacy .ppt), extracted {} images, filtered {} decorative/symbol-art",
                    slideIndex, images.size(), skippedDecorative[0]);
            return ParseResult.builder()
                    .text(text)
                    .pages(slideIndex)
                    .nodes(nodes)
                    .images(images)
                    .build();
        } catch (Exception e) {
            if (e instanceof EncryptedDocumentException || isEncryptedWorkbookError(e)) {
                throw new RuntimeException("PPT 文件已加密或受密码保护，无法解析", e);
            }
            throw new RuntimeException("PPT（.ppt）解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 递归遍历旧版 PPT shapes（基于 common-sl 通用接口），生成 DocNode / 收集图片。
     * 行为对齐 parsePptxShapes：标题占位符→HEADING、文本→PARAGRAPH（含符号画过滤）、
     * 图片→IMAGE 节点 + ParseImage（ImageFilter 过滤）。
     */
    private void parsePptLegacyShapes(ShapeContainer<?, ?> container, List<DocNode> nodes,
                                      List<ParseResult.ParseImage> images, int slideIndex,
                                      int[] skippedDecorative) {
        for (Shape<?, ?> shape : container) {
            if (shape instanceof TextShape<?, ?> ts) {
                String text = ts.getText() == null ? "" : ts.getText().trim();
                if (text.isEmpty()) continue;
                if (isSymbolArtText(text)) {
                    skippedDecorative[0]++;
                    continue;
                }
                if (isHslfTitlePlaceholder(ts)) {
                    nodes.add(DocNode.builder()
                            .type(DocNode.NodeType.HEADING)
                            .level(1)
                            .title(text)
                            .pageNumber(slideIndex + 1)
                            .build());
                } else {
                    nodes.add(DocNode.builder()
                            .type(DocNode.NodeType.PARAGRAPH)
                            .content(text)
                            .pageNumber(slideIndex + 1)
                            .build());
                }
            } else if (shape instanceof org.apache.poi.hslf.usermodel.HSLFPictureShape pic) {
                // suggestFileExtension 为 HSLFPictureData 具体方法，不落在 common 接口上
                org.apache.poi.hslf.usermodel.HSLFPictureData picData = pic.getPictureData();
                if (picData == null) continue;
                // 解析期统一过滤（ImageFilter）：与 pptx/docx 同规则
                Integer w = null;
                Integer h = null;
                try {
                    var anchor = pic.getAnchor();
                    if (anchor != null && anchor.getWidth() > 0 && anchor.getHeight() > 0) {
                        w = (int) anchor.getWidth();
                        h = (int) anchor.getHeight();
                    }
                } catch (Exception ignore) {
                    // anchor 不可用时尺寸传 null，走字节兜底规则
                }
                if (ImageFilter.isDecorative(w, h, picData.getData())
                        || ImageFilter.isDecorativeByColor(picData.getData())) {
                    skippedDecorative[0]++;
                    continue;
                }
                String ext = contentTypeToExt(picData.getContentType());
                String imageKey = "ppt_img_" + images.size() + "." + ext;
                nodes.add(DocNode.builder()
                        .type(DocNode.NodeType.IMAGE)
                        .imageKey(imageKey)
                        .imageCaption("图片")
                        .pageNumber(slideIndex + 1)
                        .build());
                images.add(ParseResult.ParseImage.builder()
                        .imageKey(imageKey)
                        .data(picData.getData())
                        .contentType(resolveImageContentType(ext))
                        .width(w)
                        .height(h)
                        .context("第 " + (slideIndex + 1) + " 页")
                        .pageNumber(slideIndex + 1)
                        .build());
            } else if (shape instanceof ShapeContainer<?, ?> group) {
                // 组形状：递归
                parsePptLegacyShapes(group, nodes, images, slideIndex, skippedDecorative);
            }
        }
    }

    /**
     * 解析 PPT 表格为 DocNode(TABLE)：复用 isHeaderRow 识别表头，结构对齐 DOCX/Excel
     */
    private DocNode parsePptxTable(XSLFTable table, int slideIndex) {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        boolean firstRow = true;
        for (XSLFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XSLFTableCell cell : row.getCells()) {
                cells.add(cell.getText() != null ? cell.getText().trim() : "");
            }
            if (firstRow && isHeaderRow(cells)) {
                headers = cells;
            } else {
                rows.add(cells);
            }
            firstRow = false;
        }
        // 未识别出表头时用第一行作为表头（与 parseDocxTable 一致）
        if (headers.isEmpty() && !rows.isEmpty()) {
            headers = rows.remove(0);
        }
        return DocNode.builder()
                .type(DocNode.NodeType.TABLE)
                .headers(headers)
                .rows(rows)
                .pageNumber(slideIndex + 1)
                .build();
    }

    /**
     * 提取 PPT 图片宽度（像素，anchor points → px @96dpi），失败返回 null。
     * <p>注意：XSLFPictureShape.getAnchor() 返回 <b>points</b>（POI 内部按 12700 EMU/pt 存储，
     * getAnchor 经 Units.toPoints 换算），并非 EMU，不能除以 9525。</p>
     */
    private Integer extractPptxPictureWidth(XSLFPictureShape pic) {
        try {
            java.awt.geom.Rectangle2D anchor = pic.getAnchor();
            return anchor != null ? (int) (anchor.getWidth() * 96.0 / 72.0) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 提取 PPT 图片高度（像素，anchor points → px @96dpi），失败返回 null
     */
    private Integer extractPptxPictureHeight(XSLFPictureShape pic) {
        try {
            java.awt.geom.Rectangle2D anchor = pic.getAnchor();
            return anchor != null ? (int) (anchor.getHeight() * 96.0 / 72.0) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ========== Excel 解析（xlsx / xls） ==========

    /** Q13: 单个 Sheet 最大行数保护（防止超大 Excel 引发 OOM） */
    private static final int MAX_ROWS_PER_SHEET = 10000;
    /** Q13: 单个 Sheet 最大列数保护 */
    private static final int MAX_COLUMNS_PER_SHEET = 200;
    /** Q8: 表头区域检测的最大扫描深度（标题行 + 列名行，用户场景前 3 行为标题） */
    private static final int MAX_HEADER_DEPTH = 10;
    /** S4: 合并表头空值占比超过该阈值时触发 LLM 兜底 */
    private static final double LLM_HEADER_EMPTY_RATIO = 0.3;
    /** LLM 表头合并：单行上下文最大长度（超长截断，防止 token 溢出） */
    private static final int LLM_CONTEXT_MAX_LINE = 200;
    /** LLM 表头合并：上下文总最大长度 */
    private static final int LLM_CONTEXT_MAX_TOTAL = 4000;

    /**
     * 解析 Excel 文件（.xlsx / .xls），输出结构化 DocNode（HEADING + TABLE）。
     *
     * <p>改进点：DataFormatter 格式化、合并单元格、隐藏行列过滤、多行表头检测与合并、
     * LLM 表头兜底、超大 Sheet 保护、异常分类提示。
     */
    private ParseResult parseExcel(InputStream stream, String extension, KbParseStrategy strategy) throws Exception {
        // 解析 LLM 配置（供复杂多行表头兜底使用）
        String llmModel = strategy != null ? strategy.getLlmModel() : null;
        LlmConfig llmConfig = resolveLlmConfig(strategy);

        try {
            // 委托共享方法解析（独立文件解析：全部 Sheet，启用 LLM 表头兜底）
            List<DocNode> nodes = parseExcelContent(stream, null, llmModel, llmConfig);

            // 序列化为 Markdown 文本
            String text = markdownSerializer.serialize(nodes);

            // 有效 Sheet 数 = HEADING 节点数（隐藏 Sheet 不产生节点）
            int pages = (int) nodes.stream()
                    .filter(n -> n.getType() == DocNode.NodeType.HEADING)
                    .count();

            return ParseResult.builder()
                    .text(text)
                    .pages(pages)
                    .nodes(nodes)
                    .build();
        } catch (EncryptedDocumentException e) {
            // S5: 加密文件给出明确提示
            log.warn("Excel file is encrypted or password-protected: {}", e.getMessage());
            throw new RuntimeException("Excel 文件已加密或受密码保护，无法解析", e);
        } catch (IOException | POIXMLException e) {
            // S5: 损坏/格式异常文件给出明确提示
            if (isEncryptedWorkbookError(e)) {
                throw new RuntimeException("Excel 文件已加密或受密码保护，无法解析", e);
            }
            log.warn("Excel file is corrupted or unreadable: {}", e.getMessage());
            throw new RuntimeException("Excel 文件损坏或格式异常，无法解析", e);
        }
    }

    /**
     * 判断异常链中是否存在加密文件相关错误（POI 对不同格式抛出的异常类型不一致，消息兜底判断）
     */
    private boolean isEncryptedWorkbookError(Throwable e) {
        while (e != null) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("encrypt")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    /**
     * 从 InputStream 解析 Excel 内容为 DocNode 列表（共享方法）。
     * 用于独立 Excel 文件解析，也用于嵌入在 PDF/DOCX/PPTX 中的 Excel。
     *
     * @param stream       Excel 文件流（内部读取并关闭 Workbook）
     * @param sheetFilter  可选 Sheet 名称过滤器（嵌入场景可只提取特定 Sheet），null 表示全部
     * @param llmModel     LLM 模型名（复杂表头兜底用），null 则不启用 LLM
     * @param llmConfig    LLM 配置（apiUrl/apiKey），null 则不启用 LLM
     * @return HEADING(Sheet 名) + TABLE(表格数据) 节点列表
     */
    public List<DocNode> parseExcelContent(InputStream stream, Predicate<String> sheetFilter,
                                           String llmModel, LlmConfig llmConfig) throws Exception {
        try (Workbook workbook = detectAndCreateWorkbook(stream)) {
            DataFormatter formatter = new DataFormatter();
            List<DocNode> nodes = new ArrayList<>();
            int sheetIndex = 0;

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);

                // Q12: 跳过隐藏 Sheet（Workbook 接口方法，不依赖 poi-ooxml-schemas）
                if (workbook.isSheetHidden(i)) continue;

                // 嵌入场景按名称过滤
                if (sheetFilter != null && !sheetFilter.test(sheet.getSheetName())) continue;

                // Q14: Sheet 名称作为 HEADING 节点
                String sheetName = sheet.getSheetName();
                nodes.add(DocNode.builder()
                        .type(DocNode.NodeType.HEADING)
                        .level(1)
                        .title(sheetName)
                        .pageNumber(sheetIndex + 1)
                        .build());

                // Q10: 有效行范围
                int firstRow = sheet.getFirstRowNum();
                int lastRow = sheet.getLastRowNum();
                if (firstRow < 0 || firstRow > lastRow) {
                    sheetIndex++; // 空 Sheet：仅保留标题节点
                    continue;
                }

                // S6/Q13: 超大 Sheet 保护
                int effectiveLastRow = Math.min(lastRow, MAX_ROWS_PER_SHEET);
                if (lastRow > MAX_ROWS_PER_SHEET) {
                    log.warn("Sheet '{}' has {} rows, truncated to {}", sheetName, lastRow + 1, MAX_ROWS_PER_SHEET);
                }

                // M7: 预构建合并区域索引（行号 → 合并区域列表），O(1) 查找
                Map<Integer, List<CellRangeAddress>> mergedRegionIndex = buildMergedRegionIndex(sheet);

                // Q8: 检测表头区域（标题行 + 列名行，最多扫描 MAX_HEADER_DEPTH 行）
                HeaderRegion headerRegion = detectHeaderRegion(sheet, firstRow, effectiveLastRow,
                        formatter, mergedRegionIndex);

                List<String> headers;
                int dataStartRow;
                String tableTitle = null;

                if (headerRegion.isHeaderFound()) {
                    // 合并列名行（单行或跨行合并）
                    headers = mergeMultiRowHeaders(sheet, headerRegion.getHeaderStartRow(),
                            headerRegion.getHeaderDepth(), formatter, mergedRegionIndex);
                    dataStartRow = headerRegion.getHeaderStartRow() + headerRegion.getHeaderDepth();

                // LLM 表头合并：仅列名复杂时调用（多行列名 / 合并空值过多）。
                // title 始终由代码生成（标题行 "-" 拼接，不依赖 LLM）；LLM 只负责列名整理
                boolean needLlm = headerRegion.getHeaderDepth() >= 2
                        || hasTooManyEmptyHeaders(headers);
                if (llmModel != null && llmConfig != null && llmConfig.apiUrl != null && needLlm) {
                    try {
                        LlmHeaderResult result = resolveHeadersWithLlm(sheet,
                                headerRegion.getHeaderStartRow() - headerRegion.getTitleRows().size(),
                                headerRegion.getHeaderStartRow(), headerRegion.getHeaderDepth(),
                                formatter, mergedRegionIndex, llmModel, llmConfig);
                        if (result != null && result.getHeaders() != null && !result.getHeaders().isEmpty()) {
                            // 剥离 LLM 误拼的标题行前缀（用真实标题行内容，确定性剥离）
                            headers = stripCommonHeaderPrefix(result.getHeaders(), headerRegion.getTitleRows());
                        }
                    } catch (Exception e) {
                        // LLM 失败不影响主流程，保留合并结果
                        log.warn("LLM header resolution failed for sheet '{}', using merged headers: {}",
                                sheetName, e.getMessage());
                    }
                }

                // 标题行信息保留：代码生成 title（"-" 拼接），作为 PARAGRAPH 节点放在 TABLE 之前
                if (!headerRegion.getTitleRows().isEmpty()) {
                    tableTitle = String.join(" - ", headerRegion.getTitleRows());
                }
                } else {
                    // 无表头：默认列名，数据从第一行开始
                    Row firstDataRow = sheet.getRow(firstRow);
                    headers = generateDefaultHeaders(firstDataRow, sheet);
                    dataStartRow = firstRow;
                }

                // Q10: 提取数据行（跳过空行/隐藏行/表尾说明行）
                List<List<String>> rows = new ArrayList<>();
                List<String> noteRows = new ArrayList<>();
                for (int r = dataStartRow; r <= effectiveLastRow; r++) {
                    // Q12: 跳过隐藏行
                    if (isRowHidden(sheet, r)) continue;

                    Row row = sheet.getRow(r);
                    if (row == null) continue;

                    List<String> values = extractRowValues(row, formatter, sheet, mergedRegionIndex);
                    if (isRowEmpty(values)) continue;

                    // 表尾说明行（跨列合并导致每列值相同）→ 作为 PARAGRAPH 保留，不进表格数据
                    if (isMergedNoteRow(values)) {
                        noteRows.add(values.stream()
                                .filter(v -> v != null && !v.isBlank())
                                .findFirst().orElse(""));
                        continue;
                    }

                    rows.add(values);
                }

                // Q6: 生成 TABLE DocNode（表格标题 PARAGRAPH 在前，表尾说明 PARAGRAPH 在后）
                if (!rows.isEmpty()) {
                    if (tableTitle != null && !tableTitle.isBlank()) {
                        nodes.add(DocNode.builder()
                                .type(DocNode.NodeType.PARAGRAPH)
                                .content(tableTitle)
                                .pageNumber(sheetIndex + 1)
                                .build());
                    }
                    nodes.add(DocNode.builder()
                            .type(DocNode.NodeType.TABLE)
                            .headers(headers)
                            .rows(rows)
                            .pageNumber(sheetIndex + 1)
                            .build());
                    if (!noteRows.isEmpty()) {
                        nodes.add(DocNode.builder()
                                .type(DocNode.NodeType.PARAGRAPH)
                                .content(String.join("\n", noteRows))
                                .pageNumber(sheetIndex + 1)
                                .build());
                    }
                }

                sheetIndex++;
            }
            return nodes;
        }
    }

    /**
     * 自动检测 Excel 格式并创建 Workbook 实例。
     * 优先尝试 OOXML（.xlsx），失败后回退 BIFF8（.xls）。
     * 读取全部字节后分别尝试，避免流 mark/reset 在部分读取后失效的问题。
     */
    private Workbook detectAndCreateWorkbook(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(bytes));
        } catch (Exception xssfErr) {
            // 非 OOXML 输入时 POI 抛出多种类型：IOException / POIXMLException /
            // UnsupportedFileFormatException(NotOfficeXmlFileException) 等，统一捕获后回退 xls
            // （Exception 不捕获 Error，OOM 等致命错误不受影响）
            try {
                return new HSSFWorkbook(new ByteArrayInputStream(bytes));
            } catch (Exception hssfErr) {
                // 两种格式都失败：优先选加密相关异常作为 cause（供 parseExcel 分类提示），
                // 其余异常以 suppressed 保留完整错误链
                Throwable cause = isEncryptedWorkbookError(hssfErr) ? hssfErr : xssfErr;
                IOException ex = new IOException("无法识别的 Excel 格式（非 .xlsx 也非 .xls）", cause);
                if (hssfErr != cause) ex.addSuppressed(hssfErr);
                if (xssfErr != cause) ex.addSuppressed(xssfErr);
                throw ex;
            }
        }
    }

    /**
     * 预构建合并区域索引：按行号分组，将合并单元格查找从 O(N*M) 降为 O(1)（M7）
     */
    private Map<Integer, List<CellRangeAddress>> buildMergedRegionIndex(Sheet sheet) {
        Map<Integer, List<CellRangeAddress>> index = new HashMap<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress range = sheet.getMergedRegion(i);
            for (int r = range.getFirstRow(); r <= range.getLastRow(); r++) {
                index.computeIfAbsent(r, k -> new ArrayList<>()).add(range);
            }
        }
        return index;
    }

    /**
     * O(1) 查找：通过行号定位该行的合并区域列表，再检查列号是否在范围内（M7）
     */
    private CellRangeAddress findMergedRegion(int rowIdx, int colIdx,
                                              Map<Integer, List<CellRangeAddress>> mergedRegionIndex) {
        List<CellRangeAddress> ranges = mergedRegionIndex.get(rowIdx);
        if (ranges == null) return null;
        for (CellRangeAddress range : ranges) {
            if (range.getFirstColumn() <= colIdx && colIdx <= range.getLastColumn()) {
                return range;
            }
        }
        return null;
    }

    /**
     * 提取一行中所有有效单元格的值。
     * Q1: DataFormatter 按显示格式格式化；Q3: 合并单元格取左上角值；Q12: 跳过隐藏列。
     */
    private List<String> extractRowValues(Row row, DataFormatter formatter, Sheet sheet,
                                          Map<Integer, List<CellRangeAddress>> mergedRegionIndex) {
        if (row == null) return new ArrayList<>();
        int maxCol = Math.min(row.getLastCellNum(), MAX_COLUMNS_PER_SHEET);
        List<String> values = new ArrayList<>();
        for (int c = 0; c < maxCol; c++) {
            // Q12: 跳过隐藏列
            if (isColumnHidden(sheet, c)) continue;

            // Q3: 合并单元格 — O(1) 索引查找，取左上角值
            String value = getMergedCellValue(sheet, row.getRowNum(), c, formatter, mergedRegionIndex);
            values.add(value != null ? value.trim() : "");
        }
        return values;
    }

    /**
     * 获取单元格值：合并区域取左上角值，非合并区域直接取值（Q3）
     */
    private String getMergedCellValue(Sheet sheet, int rowIdx, int colIdx, DataFormatter formatter,
                                      Map<Integer, List<CellRangeAddress>> mergedRegionIndex) {
        CellRangeAddress range = findMergedRegion(rowIdx, colIdx, mergedRegionIndex);
        if (range != null) {
            // 合并区域：取左上角单元格的值
            Row topLeftRow = sheet.getRow(range.getFirstRow());
            if (topLeftRow == null) return "";
            Cell topLeftCell = topLeftRow.getCell(range.getFirstColumn());
            return topLeftCell != null ? formatter.formatCellValue(topLeftCell) : "";
        }
        // 非合并单元格：直接取值
        Row r = sheet.getRow(rowIdx);
        if (r == null) return "";
        Cell cell = r.getCell(colIdx);
        return cell != null ? formatter.formatCellValue(cell) : "";
    }

    /**
     * 检测表头区域（Q8 增强：支持"标题行 + 列名行"结构）。
     *
     * <p>真实 Excel 表格常见的表头结构：前几行是表格标题（如"附件1"、"产数高质量发展情况"、
     * "附表1：2025年1-10月产业数字化收入完成情况"），最后一行（或跨行合并块）才是真正的列名行。
     * 标题行特征：单元格数少（通常 1-2 列）、无数据特征；列名行特征：多列且符合 isHeaderRow，
     * 或位于合并单元格块内。</p>
     *
     * @return 表头区域信息（标题行内容、列名起始行、列名行深度、是否找到列名行）
     */
    private HeaderRegion detectHeaderRegion(Sheet sheet, int firstRow, int lastRow, DataFormatter formatter,
                                            Map<Integer, List<CellRangeAddress>> mergedRegionIndex) {
        List<String> titleRows = new ArrayList<>();
        int scanEnd = Math.min(lastRow, firstRow + MAX_HEADER_DEPTH - 1);
        for (int r = firstRow; r <= scanEnd; r++) {
            Row row = sheet.getRow(r);
            if (row == null) break;

            List<String> values = extractRowValues(row, formatter, sheet, mergedRegionIndex);
            if (isRowEmpty(values)) continue; // 跳过空行

            boolean rowHasMerge = mergedRegionIndex.containsKey(r);
            boolean prevHasMerge = mergedRegionIndex.containsKey(r - 1);

            if (rowHasMerge || prevHasMerge) {
                // 合并块内：判定为列名行（多行合并表头块）
                if (isHeaderRow(values)) {
                    // 计算合并块深度（后续连续满足"有合并/前一行有合并 + 表头特征"的行）
                    int depth = 1;
                    for (int r2 = r + 1; r2 <= scanEnd; r2++) {
                        Row row2 = sheet.getRow(r2);
                        if (row2 == null) break;
                        List<String> values2 = extractRowValues(row2, formatter, sheet, mergedRegionIndex);
                        boolean r2HasMerge = mergedRegionIndex.containsKey(r2);
                        boolean r2PrevMerge = mergedRegionIndex.containsKey(r2 - 1);
                        if ((r2HasMerge || r2PrevMerge) && isHeaderRow(values2)) {
                            depth++;
                        } else {
                            break;
                        }
                    }
                    return new HeaderRegion(titleRows, r, depth, true);
                }
            } else if (values.size() >= 2 && isHeaderRow(values) && nonEmptyCount(values) >= 2) {
                // 单行列名行（无合并单元格，如 "分公司 | 本月完成（万元） | ..."）
                // 要求至少 2 个非空单元格，避免"附件1 + 空样式列"这类标题行误判为列名行
                return new HeaderRegion(titleRows, r, 1, true);
            } else {
                // 非列名行：标题行候选（如 "附件1"、"附表1：xxx"）
                titleRows.add(String.join(" | ", values));
            }
        }
        // 未找到列名行
        return new HeaderRegion(titleRows, firstRow, 0, false);
    }

    /**
     * 将多行表头合并为单行表头列表（Q8）。
     * 策略：同一列的多行值用 "-" 拼接（如 "2024年" + "Q1" → "2024年-Q1"）。
     * 相邻行值相同则跳过（垂直合并单元格取左上角值后出现的重复，如 "指标"+"指标"）。
     */
    private List<String> mergeMultiRowHeaders(Sheet sheet, int startRow, int depth, DataFormatter formatter,
                                              Map<Integer, List<CellRangeAddress>> mergedRegionIndex) {
        int colCount = getMaxColumnInHeaderRange(sheet, startRow, startRow + depth - 1);
        List<String> mergedHeaders = new ArrayList<>();
        for (int c = 0; c < colCount; c++) {
            // Q12: 跳过隐藏列
            if (isColumnHidden(sheet, c)) continue;

            StringBuilder colHeader = new StringBuilder();
            String prevValue = null;
            for (int r = startRow; r < startRow + depth; r++) {
                String value = getMergedCellValue(sheet, r, c, formatter, mergedRegionIndex);
                if (value == null || value.isEmpty()) continue;
                value = value.trim();
                // 垂直合并重复值去重
                if (value.equals(prevValue)) continue;
                if (colHeader.length() > 0) colHeader.append("-");
                colHeader.append(value);
                prevValue = value;
            }
            mergedHeaders.add(colHeader.toString());
        }
        return mergedHeaders;
    }

    /**
     * 获取多行表头范围内的最大列数（m6）
     */
    private int getMaxColumnInHeaderRange(Sheet sheet, int startRow, int endRow) {
        int maxCol = 0;
        for (int r = startRow; r <= endRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            maxCol = Math.max(maxCol, row.getLastCellNum());
        }
        return Math.min(maxCol, MAX_COLUMNS_PER_SHEET);
    }

    /**
     * 合并表头空值占比是否超过阈值（S4: LLM 兜底触发条件）
     */
    private boolean hasTooManyEmptyHeaders(List<String> headers) {
        if (headers == null || headers.isEmpty()) return false;
        long emptyCount = headers.stream().filter(h -> h == null || h.isEmpty()).count();
        return emptyCount > headers.size() * LLM_HEADER_EMPTY_RATIO;
    }

    /**
     * LLM 表头合并（Q8 增强）：让 LLM 分析表头区域前几行数据，合并出完整表头。
     *
     * <p>输入：表头区域（标题行 + 列名行）+ 最多 2 行示例数据，超长截断。
     * 输出：{@link LlmHeaderResult}，title 综合所有标题行内容，headers 为合并后的列名数组，
     * 确保标题行与列名行的信息完整保留（如"附件1 / 产数高质量发展情况 / 附表1：xxx"全部并入）。</p>
     *
     * @param regionStartRow   表头区域起始行（含标题行）
     * @param headerStartRow   列名起始行
     * @param headerDepth      列名行深度（跨行合并时 > 1）
     */
    private LlmHeaderResult resolveHeadersWithLlm(Sheet sheet, int regionStartRow, int headerStartRow,
                                                  int headerDepth, DataFormatter formatter,
                                                  Map<Integer, List<CellRangeAddress>> mergedRegionIndex,
                                                  String model, LlmConfig llmConfig) {
        // 收集表头区域 + 最多 2 行示例数据作为上下文（超长截断）
        StringBuilder context = new StringBuilder();
        int lastRow = sheet.getLastRowNum();
        int sampleRows = Math.min(regionStartRow + MAX_HEADER_DEPTH, lastRow + 1);
        for (int r = regionStartRow; r < sampleRows; r++) {
            List<String> values = extractRowValues(sheet.getRow(r), formatter, sheet, mergedRegionIndex);
            String line = "Row " + r + ": " + String.join(" | ", values);
            if (line.length() > LLM_CONTEXT_MAX_LINE) {
                line = line.substring(0, LLM_CONTEXT_MAX_LINE) + "...";
            }
            context.append(line).append("\n");
            if (context.length() > LLM_CONTEXT_MAX_TOTAL) {
                context.setLength(LLM_CONTEXT_MAX_TOTAL);
                context.append("\n...(已截断)");
                break;
            }
        }

        String prompt = """
                你是数据表格结构分析助手。以下是 Excel 表格的表头区域数据（| 分隔列，Row N 为行号）：

                %s

                任务：
                1. 前几行可能是表格标题行（如"附件1"、"产数高质量发展情况"、"附表1：xxx"），
                   最后一行（或最后几行）才是真正的列名行，列名可能跨行合并（如"本月完成"+"（万元）"）
                2. 将列名行合并为列名数组（跨行列名用"-"连接并整理，如"本月完成（万元）"；
                   单元格内换行整理为单行，如"超欠产\\n（万元）"→"超欠产（万元）"）

                重要约束：
                - 只输出列名数组；禁止把标题行内容拼入任何列名
                - 标题行内容（如"附件1"、"附表1：xxx"）不属于列名，不要出现在输出中
                - 保留原始信息，不遗漏、不臆造、不翻译

                正确示例：
                列名行：分公司 | 本月完成（万元） | 本月进度（%）
                → ["分公司", "本月完成（万元）", "本月进度（%）"]

                错误示例（标题行被拼进列名，禁止）：
                → ["附件1-产数高质量发展情况-附表1：...-分公司", ...]

                只输出 JSON 数组，不要其他文字。
                """.formatted(context);

        log.info("[Excel-Header] Calling model={} via apiUrl={}, context={} chars",
                model, llmConfig.apiUrl, context.length());
        // C2: chatWithTimeout 支持流式收集 + 超时控制（30s）
        String response = llmService.chatWithTimeout(model, prompt, llmConfig.apiUrl, llmConfig.apiKey, false, 30);
        return parseHeaderJson(response);
    }

    /**
     * 解析 LLM 返回的 {"title": "...", "headers": [...]} JSON。
     * 容错：markdown 代码块包裹；兼容旧版纯数组输出（仅 headers）。
     * 防御：剥离 LLM 误拼进列名的标题行公共前缀。
     *
     * @return 解析结果，解析失败返回 null
     */
    private LlmHeaderResult parseHeaderJson(String response) {
        if (response == null || response.isBlank()) {
            log.warn("LLM returned empty response for header resolution");
            return null;
        }
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```\\w*\\n?", "").replaceFirst("\\n?```$", "");
        }
        try {
            cn.hutool.json.JSONObject obj = JSONUtil.parseObj(json);
            String title = obj.getStr("title");
            List<String> headers = obj.getJSONArray("headers").toList(String.class);
            // 注：标题行前缀剥离在调用方进行（stripCommonHeaderPrefix 需要真实的 titleRows）
            return new LlmHeaderResult(title, headers);
        } catch (Exception e) {
            // 兼容旧版纯数组输出：LLM 只返回了列名数组
            try {
                List<String> headers = JSONUtil.toList(json, String.class);
                if (!headers.isEmpty()) {
                    return new LlmHeaderResult(null, headers);
                }
            } catch (Exception ignored) {
                // fall through
            }
            log.warn("Failed to parse LLM header JSON: {}", response, e);
            return null;
        }
    }

    /**
     * 防御：剥离 LLM 误拼进列名的标题行公共前缀。
     * 标题行是整表的元数据，不应出现在每个列名中（如
     * "附件1-产数高质量发展情况-附表1：...-分公司" → "分公司"）。
     * <p>用真实标题行内容（代码已知，非 LLM 返回）做确定性剥离：
     * ① 标题行拼接变体（" - " / "-"）与单行标题行本身，循环剥离；
     * ② 若仍存在长公共前缀（> 20 字符且占首元素 > 30%），整体剥离。</p>
     */
    static List<String> stripCommonHeaderPrefix(List<String> headers, List<String> titleRows) {
        if (headers == null || headers.isEmpty()) return headers;

        // 前缀候选：标题行拼接变体 + 单行标题行本身
        List<String> prefixCandidates = new ArrayList<>();
        if (titleRows != null && !titleRows.isEmpty()) {
            prefixCandidates.add(String.join(" - ", titleRows));
            prefixCandidates.add(String.join("-", titleRows));
            prefixCandidates.addAll(titleRows);
        }

        List<String> result = new ArrayList<>();
        for (String h : headers) {
            String cleaned = h;
            // 循环剥离：LLM 输出可能缺中间段（如"附件1-产数高质量发展情况-信用..."），需逐段剥
            boolean changed = true;
            while (changed) {
                changed = false;
                for (String p : prefixCandidates) {
                    if (cleaned.startsWith(p) && !cleaned.equals(p)) {
                        cleaned = cleaned.substring(p.length()).replaceFirst("^[-\\s]+", "");
                        changed = true;
                        break;
                    }
                }
            }
            result.add(cleaned.isEmpty() ? h : cleaned);
        }

        // 仍存在长公共前缀（LLM 可能用不同于标题行的拼接方式）→ 剥离
        String lcp = longestCommonPrefix(result);
        if (lcp.length() > 20 && lcp.length() > result.get(0).length() * 0.3) {
            result.replaceAll(h -> h.startsWith(lcp)
                    ? h.substring(lcp.length()).replaceFirst("^[-\\s]+", "") : h);
        }
        return result;
    }

    /** 计算字符串列表的最长公共前缀 */
    static String longestCommonPrefix(List<String> strs) {
        if (strs == null || strs.isEmpty()) return "";
        String prefix = strs.get(0);
        for (String s : strs) {
            while (!s.startsWith(prefix)) {
                if (prefix.isEmpty()) return "";
                prefix = prefix.substring(0, prefix.length() - 1);
            }
        }
        return prefix;
    }

    /**
     * 无表头时生成默认列名 ["列1", "列2", ...]（过滤隐藏列，m2）
     */
    private List<String> generateDefaultHeaders(Row firstRow, Sheet sheet) {
        int colCount = firstRow != null ? Math.min(firstRow.getLastCellNum(), MAX_COLUMNS_PER_SHEET) : 0;
        List<String> headers = new ArrayList<>();
        for (int c = 0; c < colCount; c++) {
            if (isColumnHidden(sheet, c)) continue;
            headers.add("列" + (headers.size() + 1));
        }
        return headers;
    }

    /**
     * 判断一行提取出的所有值是否全为空（Q10: 跳过全空行）
     */
    private boolean isRowEmpty(List<String> values) {
        if (values == null || values.isEmpty()) return true;
        return values.stream().allMatch(v -> v == null || v.trim().isEmpty());
    }

    /** 统计非空单元格数量 */
    private long nonEmptyCount(List<String> values) {
        if (values == null) return 0;
        return values.stream().filter(v -> v != null && !v.isBlank()).count();
    }

    /**
     * 判断是否为"表尾说明行"：跨列合并导致每列取左上角值后全部相同，
     * 且文本以"说明/注/备注/注释"开头（如"说明：1、24年审计号码级补收..."）。
     * 这类行应作为 PARAGRAPH 保留，避免说明文本在每一列重复污染表格数据。
     */
    private boolean isMergedNoteRow(List<String> values) {
        if (values == null || values.size() < 3) return false;
        String first = null;
        int nonEmptyCount = 0;
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            nonEmptyCount++;
            if (first == null) {
                first = v.trim();
            } else if (!v.trim().equals(first)) {
                return false; // 该行存在不同值 → 正常数据行
            }
        }
        if (nonEmptyCount < 3 || first == null) return false;
        return first.startsWith("说明") || first.startsWith("注")
                || first.startsWith("备注") || first.startsWith("注释");
    }

    /**
     * 检查指定行是否隐藏（Q12，POI Row 接口 getZeroHeight() 跨格式统一检测）
     */
    private boolean isRowHidden(Sheet sheet, int rowIdx) {
        Row row = sheet.getRow(rowIdx);
        return row != null && row.getZeroHeight();
    }

    /**
     * 检查指定列是否隐藏（Q12，POI Sheet 接口标准方法）
     */
    private boolean isColumnHidden(Sheet sheet, int colIdx) {
        return sheet.isColumnHidden(colIdx);
    }

    private ParseResult parseDefault(InputStream stream, KbParseStrategy strategy, ParseOptions options) throws Exception {
        byte[] bytes = stream.readAllBytes();
        try {
            Charset charset = resolveCharset(bytes, options != null ? options.getEncoding() : null);
            log.debug("Charset for text file: {}", charset.name());
            String text = new String(bytes, charset);
            return ParseResult.builder().text(text).pages(1).build();
        } catch (Exception e) {
            log.warn("Failed to decode with resolved charset, falling back to UTF-8", e);
            String text = new String(bytes, StandardCharsets.UTF_8);
            return ParseResult.builder().text(text).pages(1).build();
        }
    }

    /**
     * 解析文本编码：上传向导显式指定（utf-8/gbk/shift-jis）优先，否则自动检测
     */
    private Charset resolveCharset(byte[] bytes, String encoding) {
        if (encoding != null && !"auto".equalsIgnoreCase(encoding)) {
            return switch (encoding.toLowerCase()) {
                case "utf-8" -> StandardCharsets.UTF_8;
                case "gbk" -> Charset.forName("GBK");
                case "shift-jis" -> Charset.forName("Shift_JIS");
                default -> detectCharset(bytes);
            };
        }
        return detectCharset(bytes);
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
    private ParseResult parseVideo(InputStream stream, String extension, KbParseStrategy strategy, ParseOptions options) throws Exception {
        log.info("Parsing video file with multimodal processing, extension: {}", extension);

        // 读取策略配置（统一入口，兼容新分组结构与旧平铺字段）
        ParseStrategyConfig.ParseConfig parseConfig = configResolver
                .resolve(strategy != null ? strategy.getId() : null).getParse();
        // 关键帧间隔：上传向导一次性覆盖 > 策略 advanced > 默认 10s
        int keyframeInterval = options != null && options.getKeyframeInterval() != null
                ? options.getKeyframeInterval()
                : (parseConfig.getKeyframeIntervalSeconds() != null
                        ? parseConfig.getKeyframeIntervalSeconds() : 10);
        int hashThreshold = parseConfig.getKeyframeHashThreshold() != null
                ? parseConfig.getKeyframeHashThreshold().intValue() : 10;  // 默认汉明距离阈值
        // 视频策略：keyframe_asr（默认）/ asr_only / uniform_sample
        String videoStrategy = options != null && options.getVideoStrategy() != null
                ? options.getVideoStrategy() : "keyframe_asr";
        log.info("Video parse config: videoStrategy={}, keyframeInterval={}s, hashThreshold={}",
                videoStrategy, keyframeInterval, hashThreshold);

        // 读取视频流字节（因为 stream 只能读一次，需要分别传给音频和视觉两条路径）
        byte[] videoBytes = stream.readAllBytes();

        // 时间范围裁剪：仅解析用户指定区间（按文件名匹配）
        byte[] parseBytes = videoBytes;
        double[] timeRange = resolveTimeRange(options);
        if (timeRange != null && timeRange[1] > timeRange[0]) {
            try {
                log.info("Cropping video to range [{}, {}]s", timeRange[0], timeRange[1]);
                parseBytes = mediaExtractor.cropMedia(videoBytes, extension,
                        timeRange[0], timeRange[1] - timeRange[0]);
            } catch (Exception e) {
                log.warn("Video crop failed, parsing full video: {}", e.getMessage());
            }
        }

        // === 音频路：提取音频 → ASR（uniform_sample 策略跳过） ===
        List<AsrResult.AsrSegment> asrSegments = new ArrayList<>();
        String asrFullText = "";
        if (!"uniform_sample".equals(videoStrategy)) {
            try {
                byte[] audioBytes = mediaExtractor.extractAudio(
                        new java.io.ByteArrayInputStream(parseBytes), extension);
                AsrResult asrResult = asrService.transcribe(audioBytes,
                        "audio." + (extension != null ? extension : "mp4"),
                        options != null ? options.getAsrEngine() : null,
                        options != null ? options.getLanguage() : null);
                asrFullText = asrResult.getText();
                if (asrResult.getSegments() != null) {
                    asrSegments.addAll(asrResult.getSegments());
                }
                log.info("Video ASR completed: {} segments, {} chars", asrSegments.size(), asrFullText.length());
            } catch (Exception e) {
                log.error("Video ASR failed, continuing with keyframe-only mode", e);
            }
        }

        // === 视觉路：关键帧提取 → 去重 → OCR（asr_only 策略跳过） ===
        List<KeyframeOcr> keyframeOcrList = new ArrayList<>();
        if (!"asr_only".equals(videoStrategy)) {
            try {
                List<MediaExtractor.Keyframe> keyframes = mediaExtractor.extractKeyframes(
                        new java.io.ByteArrayInputStream(parseBytes), extension, keyframeInterval);
                log.info("Extracted {} raw keyframes from video", keyframes.size());

                // 感知哈希去重
                List<MediaExtractor.Keyframe> deduped = mediaExtractor.deduplicateByHash(keyframes, hashThreshold);

                // 逐帧 OCR
                for (MediaExtractor.Keyframe kf : deduped) {
                    try {
                        String ocrText = ocrService.recognize(kf.getImageBytes(), "jpg",
                                options != null ? options.getOcrEngine() : null);
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
        }

        // === 合并两路结果 ===
        return mergeSegmentsWithKeyframes(asrFullText, asrSegments, keyframeOcrList, strategy, keyframeInterval);
    }

    /**
     * 从解析选项中解析时间裁剪范围（fileName 精确匹配），无匹配返回 null
     */
    private double[] resolveTimeRange(ParseOptions options) {
        if (options == null || options.getTimeRanges() == null || options.getFileName() == null) {
            return null;
        }
        return options.getTimeRanges().get(options.getFileName());
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
            KbParseStrategy strategy,
            int estimatedSegmentDuration) {

        List<ParseResult.ChunkTimeSegment> mergedSegments = new ArrayList<>();

        if (asrSegments.isEmpty()) {
            // ASR 失败（或 uniform_sample 策略）但有关键帧 OCR 结果时，以关键帧构建分段
            log.info("No ASR segments, building segments from keyframes only");
            for (KeyframeOcr kf : keyframeOcrList) {
                mergedSegments.add(ParseResult.ChunkTimeSegment.builder()
                        .text("【画面内容】\n" + kf.getOcrText())
                        .startTime(kf.getTimestampSeconds())
                        .endTime(kf.getTimestampSeconds() + estimatedSegmentDuration) // 按采样间隔估算
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
    private ParseResult parseAudio(InputStream stream, String extension, KbParseStrategy strategy, ParseOptions options) throws Exception {
        log.info("Parsing audio file, extension: {}", extension);
        byte[] audioBytes = stream.readAllBytes();

        // 时间范围裁剪：仅解析用户指定区间（按文件名匹配）
        double[] timeRange = resolveTimeRange(options);
        if (timeRange != null && timeRange[1] > timeRange[0]) {
            try {
                log.info("Cropping audio to range [{}, {}]s", timeRange[0], timeRange[1]);
                audioBytes = mediaExtractor.cropMedia(audioBytes, extension,
                        timeRange[0], timeRange[1] - timeRange[0]);
            } catch (Exception e) {
                log.warn("Audio crop failed, parsing full audio: {}", e.getMessage());
            }
        }

        String filename = "audio." + (extension != null ? extension : "mp3");
        return doAsrParse(audioBytes, filename, strategy, options);
    }

    /**
     * 执行 ASR 解析并构建 ParseResult
     */
    private ParseResult doAsrParse(byte[] audioBytes, String filename, KbParseStrategy strategy, ParseOptions options) {
        AsrResult asrResult = asrService.transcribe(audioBytes, filename,
                options != null ? options.getAsrEngine() : null,
                options != null ? options.getLanguage() : null);

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
    private ParseResult parseImage(InputStream stream, String extension, KbParseStrategy strategy, ParseOptions options) throws Exception {
        log.info("Parsing image file, extension: {}", extension);
        byte[] imageBytes = stream.readAllBytes();
        String text = ocrService.recognize(imageBytes, extension,
                options != null ? options.getOcrEngine() : null);

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

    /** LLM 配置内部 DTO（package-private 便于同包测试构造） */
    @lombok.Data
    static class LlmConfig {
        private final String apiUrl;
        private final String apiKey;
    }

    /** 表头区域检测结果（Q8 增强） */
    @lombok.Data
    private static class HeaderRegion {
        /** 标题行内容（如 "附件1"、"附表1：xxx"），按出现顺序，空列表表示无标题行 */
        private final List<String> titleRows;
        /** 列名起始行号 */
        private final int headerStartRow;
        /** 列名行深度（跨行合并时 > 1，单行表头为 1） */
        private final int headerDepth;
        /** 是否找到列名行（false 表示整表无表头，走默认列名） */
        private final boolean headerFound;
    }

    /** LLM 表头合并结果 */
    @lombok.Data
    private static class LlmHeaderResult {
        /** 合并后的表格标题（综合所有标题行内容），可为 null */
        private final String title;
        /** 合并后的列名数组 */
        private final List<String> headers;
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
