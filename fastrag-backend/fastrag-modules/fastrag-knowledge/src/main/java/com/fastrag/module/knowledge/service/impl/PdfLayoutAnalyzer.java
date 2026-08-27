package com.fastrag.module.knowledge.service.impl;

import com.fastrag.module.knowledge.model.AiChunkLayoutBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * PDFBox 几何版面分析：对<b>有文字层</b>的正规 PDF 页，把精确行盒聚类成内容块并打类型标签，
 * 输出的块坐标与 VLM 版面分析统一（归一化 0~1、顶左原点），供原件渲染分块画框。
 *
 * <p>纯坐标/字体启发式，零 OCR 成本、毫秒级；类型区分能力：
 * <ul>
 *   <li><b>title</b>：字号 ≥ 页中位数 ×1.25，或加粗且字号 ≥ 中位数（短行）</li>
 *   <li><b>table</b>：同 y 带内出现 ≥2 个稳定 x 列的连续行（表格通常每格一个文本对象、x 各自独立）</li>
 *   <li><b>code</b>：等宽字体（字体名匹配 mono/consolas/courier 等）或连续 ≥3 行同 x 同字号</li>
 *   <li><b>image</b>：直接复用 CTM 精确图片盒</li>
 *   <li><b>caption</b>：紧邻 image/table 的下/上方、文本 ≤30 字的 text 块</li>
 *   <li><b>text</b>：其余行按「同列（x 范围重叠）+ 行距 ≤ 1.6×行高」聚类成段（兼顾双栏）</li>
 * </ul>
 * 公式在正规 PDF 无标准标记，几何难稳定识别 → 归 text（扫描/公式密集页走 VLM）。</p>
 */
public final class PdfLayoutAnalyzer {

    /** 一页面行：顶左原点 pt 行盒 + 文本 + 字号 + 字体名（几何分类依据） */
    public record Line(float[] rect, String text, float fontSize, String fontName) {
    }

    /** 同列 x 分桶宽度（pt）：单元格列间隔一般远超，2pt 桶足够区分 */
    private static final float COL_BIN = 5f;
    /** 同行 y 容差：0.5×行高 */
    private static final float ROW_Y_TOL_RATIO = 0.5f;
    /** 段落内最大行距：2.0×行高（中文段落行距可达 1.8×，放宽防同一段被打断） */
    private static final float PARA_GAP_RATIO = 2.0f;
    /** 标题字号下限：页中位数 ×1.25 */
    private static final float TITLE_SIZE_RATIO = 1.25f;
    /** 标题最少比正文大（加粗判定用到） */
    private static final float TITLE_BOLD_SIZE_RATIO = 1.02f;
    /** 图注文本长度上限 */
    private static final int CAPTION_MAX_LEN = 30;
    /** 表格单元格文本长度上限（双栏正文行通常远长于此 → 不会被误判成表格） */
    private static final int TABLE_CELL_MAX_LEN = 32;
    /** 目录行：点线引导（......./……/···）或 行尾为页码数字；行文本长度范围 */
    private static final int TOC_MIN_LEN = 3;
    private static final int TOC_MAX_LEN = 100;
    /** 标题文本长度上限 */
    private static final int TITLE_MAX_LEN = 60;
    /** 等宽字体识别 */
    private static final String MONO_PATTERN = "(?i).*(mono|consolas|courier|menlo|code|ocr).*";

    private PdfLayoutAnalyzer() {
    }

    /**
     * 分析单页版面。lines 为顶左原点 pt 行盒；imagePt 为该页内容图片位置（pt、顶左）。
     *
     * @param page 页码（1-based）
     * @return 归一化 0~1、带 type 的版面块，按 y 自（页首→页尾）排序
     */
    public static List<AiChunkLayoutBlock> analyze(int page, float pageW, float pageH,
                                                   List<Line> lines, List<float[]> imagePt) {
        List<AiChunkLayoutBlock> blocks = new ArrayList<>();
        for (float[] b : imagePt) {
            if (b.length < 4 || b[2] <= 0 || b[3] <= 0 || pageW <= 0 || pageH <= 0) continue;
            blocks.add(AiChunkLayoutBlock.builder().page(page).type("image")
                    .x(b[0] / pageW).y(b[1] / pageH).width(b[2] / pageW).height(b[3] / pageH)
                    .text("[图片]").build());
        }
        if (lines.isEmpty() || pageW <= 0 || pageH <= 0) {
            blocks.sort(Comparator.comparingDouble(AiChunkLayoutBlock::getY));
            return blocks;
        }

        List<Line> ordered = new ArrayList<>(lines);
        ordered.sort(Comparator.comparingDouble((Line l) -> l.rect()[1])
                .thenComparingDouble(l -> l.rect()[0]));
        int n = ordered.size();
        float lineH = medianLineHeight(ordered);
        boolean[] table = new boolean[n];
        boolean[] code = new boolean[n];
        boolean[] title = new boolean[n];
        markTables(ordered, lineH, table);
        markCode(ordered, code);
        markTitles(ordered, medianFontSize(ordered), title);
        // 优先级：table > code > title > toc > text
        for (int i = 0; i < n; i++) {
            if (table[i]) { code[i] = false; title[i] = false; }
            if (code[i]) title[i] = false;
        }
        boolean[] toc = new boolean[n];
        markToc(ordered, toc);
        // 补全：被 toc 行包裹（左右至少一邻为 toc）的短非 toc 行也归 toc
        // → 目录段中无点线/页码的小标题行（如 "1.1 概述"）不会让 run 断开
        for (int i = 0; i < n; i++) {
            if (toc[i]) continue;
            String t = ordered.get(i).text();
            if (t == null || t.length() > 25) continue;
            boolean leftToc = i > 0 && toc[i - 1];
            boolean rightToc = i < n - 1 && toc[i + 1];
            if (leftToc || rightToc) toc[i] = true;
        }
        for (int i = 0; i < n; i++) {
            if (table[i] || code[i] || title[i]) toc[i] = false;
        }

        List<Integer> allIdx = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            allIdx.add(i);
        }
        collectRuns(ordered, allIdx, lineH, table, "table", page, pageW, pageH, blocks,
                lineH * 0.5f, lineH * 0.4f);
        collectRuns(ordered, allIdx, lineH, code, "code", page, pageW, pageH, blocks, 0f, 0f);
        collectRuns(ordered, allIdx, lineH, title, "title", page, pageW, pageH, blocks, 0f, 0f);
        collectToc(ordered, allIdx, lineH, toc, page, pageW, pageH, blocks);
        collectText(ordered, allIdx, lineH, table, code, title, toc, page, pageW, pageH, blocks);
        markCaptions(blocks, lineH, pageH);

        blocks.sort(Comparator.comparingDouble(AiChunkLayoutBlock::getY));
        return blocks;
    }

    // ============================ 行分类 ============================

    /** 表格行：同 y 带内该页出现 ≥2 个不同 x 列，且每个单元格文本短（双栏正文行很长 → 不误判） */
    private static void markTables(List<Line> lines, float lineH, boolean[] flag) {
        int n = lines.size();
        boolean[] tableRow = new boolean[n];
        for (int i = 0; i < n; i++) {
            float[] r = lines.get(i).rect();
            float tol = Math.max(lineH, r[3]) * ROW_Y_TOL_RATIO;
            Set<Float> cols = new TreeSet<>();
            int maxCellLen = 0;
            for (int j = 0; j < n; j++) {
                if (Math.abs(lines.get(j).rect()[1] - r[1]) <= tol) {
                    cols.add(Math.round(lines.get(j).rect()[0] / COL_BIN) * COL_BIN);
                    String t = lines.get(j).text();
                    maxCellLen = Math.max(maxCellLen, t == null ? 0 : t.length());
                }
            }
            if (cols.size() >= 2 && maxCellLen <= TABLE_CELL_MAX_LEN) {
                tableRow[i] = true;
            }
        }
        for (int i = 0; i < n - 1; i++) {
            if (tableRow[i] && tableRow[i + 1] && closeRows(lines, i, i + 1, lineH)) {
                flag[i] = true;
                flag[i + 1] = true;
            }
        }
    }

    private static boolean closeRows(List<Line> lines, int a, int b, float lineH) {
        float gap = lines.get(b).rect()[1] - (lines.get(a).rect()[1] + lines.get(a).rect()[3]);
        return gap <= lineH * 2.5f; // 表格行距可稍大
    }

    /** 代码行：等宽字体（正文同 x 同字号不判定为 code，避免正文段落被误判） */
    private static void markCode(List<Line> lines, boolean[] flag) {
        int n = lines.size();
        for (int i = 0; i < n; i++) {
            String fname = lines.get(i).fontName();
            if (fname != null && fname.matches(MONO_PATTERN)) {
                flag[i] = true;
            }
        }
    }

    /** 标题行：字号 ≥ 中位数 ×1.25，或加粗且字号 ≥ 中位数×1.02；均要求短行 */
    private static void markTitles(List<Line> lines, float median, boolean[] flag) {
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            String t = l.text() == null ? "" : l.text().trim();
            if (t.length() > TITLE_MAX_LEN || t.isEmpty()) continue;
            String fname = l.fontName() == null ? "" : l.fontName().toLowerCase();
            boolean bold = fname.contains("bold") || fname.contains("-b") || fname.contains("_b");
            boolean big = median > 0 && l.fontSize() >= median * TITLE_SIZE_RATIO;
            boolean boldBig = bold && median > 0 && l.fontSize() >= median * TITLE_BOLD_SIZE_RATIO;
            if (big || boldBig) flag[i] = true;
        }
    }

    /** 目录行：点线引导（. · • … 任意点类字符 3+ 个，允许空格间隔）或行尾为页码数字；行文本长度范围 */
    private static void markToc(List<Line> lines, boolean[] flag) {
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).text() == null ? "" : lines.get(i).text().trim();
            if (t.length() < TOC_MIN_LEN || t.length() > TOC_MAX_LEN) continue;
            boolean dotLeader = t.matches(".*([.·•…]\\s*){3,}.*");
            boolean endsWithPage = t.matches(".*\\d{1,4}\\s*$") && !t.matches("^[\\s\\d.]+$");
            if (dotLeader || endsWithPage) flag[i] = true;
        }
    }

    // ============================ 成块 ============================

    /** 连续被标记（且 y 紧邻）同类行聚成一个块；padTop/padBottom 为视觉外扩（pt，如表格罩住网格线） */
    private static void collectRuns(List<Line> lines, List<Integer> idxs, float lineH, boolean[] flag, String type,
                                    int page, float pageW, float pageH, List<AiChunkLayoutBlock> out,
                                    float padTop, float padBottom) {
        int n = idxs.size();
        int i = 0;
        while (i < n) {
            if (!flag[idxs.get(i)]) {
                i++;
                continue;
            }
            int j = i;
            while (j + 1 < n && flag[idxs.get(j + 1)] && closeWithin(lines, idxs.get(j), idxs.get(j + 1), lineH, PARA_GAP_RATIO)) {
                j++;
            }
            out.add(blockOf(lines, idxs, i, j, type, page, pageW, pageH, padTop, padBottom));
            i = j + 1;
        }
    }

    /** 目录段：连续目录行（行距放宽到 2.5×行高）整体归并为一块 text（目录一个框） */
    private static void collectToc(List<Line> lines, List<Integer> idxs, float lineH, boolean[] toc,
                                   int page, float pageW, float pageH, List<AiChunkLayoutBlock> out) {
        int n = idxs.size();
        int i = 0;
        while (i < n) {
            if (!toc[idxs.get(i)]) {
                i++;
                continue;
            }
            int j = i;
            while (j + 1 < n && toc[idxs.get(j + 1)]
                    && closeWithin(lines, idxs.get(j), idxs.get(j + 1), lineH, 2.5f)) {
                j++;
            }
            out.add(blockOf(lines, idxs, i, j, "text", page, pageW, pageH, 0f, 0f));
            i = j + 1;
        }
    }

    /** 未分类行按列分桶（x 起点 2pt 桶），列内按 y 行距聚类成段（双栏页面各栏独立成段、不交错合并） */
    private static void collectText(List<Line> lines, List<Integer> idxs, float lineH, boolean[] table,
                                    boolean[] code, boolean[] title, boolean[] toc,
                                    int page, float pageW, float pageH, List<AiChunkLayoutBlock> out) {
        Map<Float, List<Integer>> byCol = new TreeMap<>();
        for (int idx : idxs) {
            if (table[idx] || code[idx] || title[idx] || toc[idx]) continue;
            float colX = Math.round(lines.get(idx).rect()[0] / COL_BIN) * COL_BIN;
            byCol.computeIfAbsent(colX, k -> new ArrayList<>()).add(idx);
        }
        for (List<Integer> col : byCol.values()) {
            col.sort(Comparator.comparingDouble(i -> lines.get(i).rect()[1]));
            int k = 0;
            while (k < col.size()) {
                int j = k;
                while (j + 1 < col.size()) {
                    int a = col.get(j), b = col.get(j + 1);
                    float[] ra = lines.get(a).rect();
                    float[] rb = lines.get(b).rect();
                    boolean overlap = ra[0] < rb[0] + rb[2] - 1 && rb[0] < ra[0] + ra[2] - 1; // 1pt 余量
                    float gap = rb[1] - (ra[1] + ra[3]);
                    if (overlap && gap <= lineH * PARA_GAP_RATIO) {
                        j++;
                    } else {
                        break;
                    }
                }
                out.add(blockOf(lines, col, k, j, "text", page, pageW, pageH, 0f, 0f));
                k = j + 1;
            }
        }
    }

    /** 行距不超过 maxGapRatio × 行高即视为同一块内相邻 */
    private static boolean closeWithin(List<Line> lines, int a, int b, float lineH, float maxGapRatio) {
        float gap = lines.get(b).rect()[1] - (lines.get(a).rect()[1] + lines.get(a).rect()[3]);
        return gap <= lineH * maxGapRatio;
    }

    /** 短正文块紧邻（≤2×行高，x 重叠）image/table → 图注 */
    private static void markCaptions(List<AiChunkLayoutBlock> blocks, float lineH, float pageH) {
        float normGap = pageH > 0 ? (lineH * 2f) / pageH : 0f;
        for (AiChunkLayoutBlock b : blocks) {
            if (!"text".equals(b.getType())) continue;
            String t = b.getText() == null ? "" : b.getText().trim();
            if (t.length() > CAPTION_MAX_LEN) continue;
            for (AiChunkLayoutBlock o : blocks) {
                if (!"image".equals(o.getType()) && !"table".equals(o.getType())) continue;
                if (b.getX() + b.getWidth() < o.getX() || o.getX() + o.getWidth() < b.getX()) continue;
                float gapBelow = (float) Math.abs(b.getY() - (o.getY() + o.getHeight()));
                float gapAbove = (float) Math.abs(o.getY() - (b.getY() + b.getHeight()));
                if (Math.min(gapBelow, gapAbove) <= normGap) {
                    b.setType("caption");
                    break;
                }
            }
        }
    }

    private static AiChunkLayoutBlock blockOf(List<Line> lines, List<Integer> idxs, int from, int to, String type,
                                              int page, float pageW, float pageH, float padTop, float padBottom) {
        float x1 = Float.MAX_VALUE, y1 = Float.MAX_VALUE, x2 = -1, y2 = -1;
        StringBuilder sb = new StringBuilder();
        for (int k = from; k <= to; k++) {
            int idx = idxs.get(k);
            float[] r = lines.get(idx).rect();
            x1 = Math.min(x1, r[0]);
            y1 = Math.min(y1, r[1]);
            x2 = Math.max(x2, r[0] + r[2]);
            y2 = Math.max(y2, r[1] + r[3]);
            if (lines.get(idx).text() != null) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(lines.get(idx).text().trim());
            }
        }
        String text = sb.length() > 60 ? sb.substring(0, 60) + "…" : sb.toString();
        float top = Math.max(0, y1 - padTop);
        float height = (y2 - y1) + padTop + padBottom;
        return AiChunkLayoutBlock.builder().page(page).type(type)
                .x(x1 / pageW).y(top / pageH).width((x2 - x1) / pageW).height(height / pageH)
                .text(text).build();
    }

    // ============================ 统计辅助 ============================

    private static float medianFontSize(List<Line> lines) {
        List<Float> sizes = new ArrayList<>();
        for (Line l : lines) {
            if (l.fontSize() > 0) sizes.add(l.fontSize());
        }
        if (sizes.isEmpty()) return 0;
        sizes.sort(Float::compareTo);
        return sizes.get(sizes.size() / 2);
    }

    private static float medianLineHeight(List<Line> lines) {
        List<Float> hs = new ArrayList<>();
        for (Line l : lines) {
            if (l.rect()[3] > 0) hs.add(l.rect()[3]);
        }
        if (hs.isEmpty()) return 1f;
        hs.sort(Float::compareTo);
        return hs.get(hs.size() / 2);
    }
}
