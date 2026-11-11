package com.fastrag.module.knowledge.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 按行切片器（REQ-H06，ADR-0012 解析期装配的纯函数助手）。
 *
 * <p>把「显式表头 + 数据行」组装为若干切片：每个切片 = 表头行 + 至多切分行数条数据行，
 * 4800 字符为单切片硬上限（含表头）。字符计量与 {@link MarkdownSerializer#serializeNode} 的
 * 表格序列化格式严格同式（{@code | c1 | c2 |} 行 + {@code | --- |} 分隔行），保证
 * 「切片序列化后 ≤ 4800」的断言在分片侧成立。
 *
 * <p>封片规则（软目标 + 硬上限双闸）：
 * <ul>
 *   <li>累计数据行数达到切分行数（软目标）→ 封片；</li>
 *   <li>加入下一行将使切片文本超过 4800 字符（硬上限，含表头）→ 先封片；</li>
 *   <li>单条数据行自身超预算时按字符硬切为多段，每段独占一个切片且重复表头（续段自包含）。</li>
 * </ul>
 *
 * <p>单调用方：DocumentParserImpl 的 excel_row 解析分支。4800 为产品常量，不开放配置。
 */
public final class ExcelRowSlicer {

    /** 单切片字符硬上限（含表头行与分隔行），产品常量不开放配置（REQ-H06） */
    public static final int MAX_SLICE_CHARS = 4800;

    private ExcelRowSlicer() {
    }

    /**
     * 按切分行数与字符硬上限把数据行组装为切片。
     *
     * @param dataRows     数据行（已按单元格展开；允许列数与表头不一致，计数按补齐后的列宽计算）
     * @param headers      显式表头行（每个切片重复携带；允许 null/空 = 无表头模式）
     * @param rowsPerSlice 切分行数软目标（≥1；调用侧已按保存期校验保证，此处再兜底钳位）
     * @return 切片序列；每个元素是一个切片的数据行集合（表头由调用方统一携带，不在返回值内重复）
     */
    public static List<List<List<String>>> slice(List<List<String>> dataRows, List<String> headers, int rowsPerSlice) {
        List<List<List<String>>> slices = new ArrayList<>();
        if (dataRows == null || dataRows.isEmpty()) {
            return slices;
        }
        int targetRows = Math.max(1, rowsPerSlice);
        int cols = headers != null ? headers.size() : 0;
        int headerChars = headerBlockChars(headers, cols);
        int budget = Math.max(1, MAX_SLICE_CHARS - headerChars);

        List<List<String>> current = new ArrayList<>();
        int currentChars = 0;
        for (List<String> row : dataRows) {
            if (row == null || row.isEmpty()) continue;
            int rowLen = lineChars(row, cols);

            // 单行自身超预算：硬切为多段，每段独占切片并重复表头（REQ-H06）
            if (rowLen > budget) {
                if (!current.isEmpty()) {
                    slices.add(current);
                    current = new ArrayList<>();
                    currentChars = 0;
                }
                slices.addAll(splitOversizeRow(row, cols, budget));
                continue;
            }

            if (!current.isEmpty()
                    && (current.size() >= targetRows || currentChars + rowLen > budget)) {
                slices.add(current);
                current = new ArrayList<>();
                currentChars = 0;
            }
            current.add(row);
            currentChars += rowLen;
        }
        if (!current.isEmpty()) {
            slices.add(current);
        }
        return slices;
    }

    /**
     * 单行超预算的字符级硬切：行内各单元格按序列化顺序拼接后切为 budget 长度的段，
     * 每段回填为首单元格所在的行片段（其余列置空），保证每段仍是合法的表格行结构。
     */
    private static List<List<List<String>>> splitOversizeRow(List<String> row, int cols, int budget) {
        List<List<List<String>>> slices = new ArrayList<>();
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < cols; i++) {
            if (i > 0) joined.append(" | ");
            String cell = i < row.size() && row.get(i) != null ? row.get(i) : "";
            joined.append(cell);
        }
        String text = joined.toString();
        for (int from = 0; from < text.length(); from += budget) {
            int to = Math.min(text.length(), from + budget);
            String seg = text.substring(from, to);
            List<String> segRow = new ArrayList<>();
            for (int i = 0; i < cols; i++) {
                segRow.add(i == 0 ? seg : "");
            }
            List<List<String>> slice = new ArrayList<>();
            slice.add(segRow);
            slices.add(slice);
        }
        return slices;
    }

    /** 表头块字符数：表头行 + 分隔行（与 serializeTable 同式，含换行） */
    private static int headerBlockChars(List<String> headers, int cols) {
        if (headers == null || headers.isEmpty() || cols == 0) return 0;
        return lineChars(headers, cols) + (1 + 6 * cols + 1);
    }

    /** 单行序列化字符数：{@code | c1 | c2 |} + 换行；列数不足按空串补齐（与 serializeTable 同式） */
    private static int lineChars(List<String> cells, int cols) {
        if (cols <= 0) {
            cols = cells.size();
        }
        int sum = 2; // 前导 "| "
        for (int i = 0; i < cols; i++) {
            String c = i < cells.size() && cells.get(i) != null ? cells.get(i) : "";
            sum += c.length();
            if (i < cols - 1) sum += 3; // " | "
        }
        return sum + 3; // 尾部 " |\n"
    }
}
