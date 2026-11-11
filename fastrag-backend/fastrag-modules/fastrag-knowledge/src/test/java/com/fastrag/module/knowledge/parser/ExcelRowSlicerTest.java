package com.fastrag.module.knowledge.parser;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExcelRowSlicer 纯函数单元测试（REQ-H06，tdd §5.2 S8 切片核心断言）。
 *
 * 覆盖：软目标行数封片、4800 字符硬上限（含表头）提前封片、单行超长硬切续段、空输入、行数钳位。
 */
class ExcelRowSlicerTest {

    private static List<String> row(String... cells) {
        return List.of(cells);
    }

    private static List<List<String>> dataRows(int count, String prefix) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(row(prefix + i, "v" + i));
        }
        return rows;
    }

    @Test
    void 软目标行数封片且每片行数正确() {
        // 10 数据行 + rowsPerSlice=4 → 4/4/2
        List<List<List<String>>> slices = ExcelRowSlicer.slice(dataRows(10, "r"), List.of("列A", "列B"), 4);

        assertEquals(3, slices.size());
        assertEquals(4, slices.get(0).size());
        assertEquals(4, slices.get(1).size());
        assertEquals(2, slices.get(2).size());
    }

    @Test
    void 累计文本达4800字符时提前于行数封片() {
        // 表头较长；每行 ~400 字符 → 11 行即超 4800（含表头），rowsPerSlice=100 不会先触发
        List<String> headers = List.of("订单编号", "商品明细描述字段用于撑宽序列化文本");
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            rows.add(row("ORD-" + i, "x".repeat(380)));
        }

        List<List<List<String>>> slices = ExcelRowSlicer.slice(rows, headers, 100);

        // 每片必须可序列化为 ≤4800 字符（含表头，与 MarkdownSerializer 同式计量）
        for (List<List<String>> slice : slices) {
            int total = ExcelRowSlicerTestUtil.headerBlockChars(headers) + ExcelRowSlicerTestUtil.rowsChars(slice, 2);
            assertTrue(total <= ExcelRowSlicer.MAX_SLICE_CHARS,
                    "切片序列化应 ≤4800，实际 " + total);
        }
        assertTrue(slices.size() > 1, "宽行数据应提前封片而非单片塞满 30 行");
    }

    @Test
    void 单行超4800字符时硬切多段() {
        String huge = "长".repeat(10000);
        List<List<String>> rows = List.of(row(huge, "v"));

        List<List<List<String>>> slices = ExcelRowSlicer.slice(rows, List.of("列A", "列B"), 100);

        assertTrue(slices.size() >= 3, "10000 字符行应硬切为多段");
        int totalCells = 0;
        for (List<List<String>> slice : slices) {
            assertEquals(1, slice.size(), "硬切段每片一行");
            totalCells += slice.get(0).get(0).length();
        }
        assertEquals(10000 + " | ".length() + "v".length(), totalCells, "硬切不丢字符");
    }

    @Test
    void 空数据行返回空切片() {
        assertTrue(ExcelRowSlicer.slice(List.of(), List.of("列A"), 10).isEmpty());
        assertTrue(ExcelRowSlicer.slice(null, List.of("列A"), 10).isEmpty());
    }

    @Test
    void rowsPerSlice小于1时钳位为逐行封片() {
        List<List<List<String>>> slices = ExcelRowSlicer.slice(dataRows(3, "r"), List.of("列A"), 0);
        assertEquals(3, slices.size(), "rowsPerSlice=0 钳位为 1 → 每行一片");
    }

    @Test
    void 表头块字符计入预算使宽表头提前封片() {
        // 宽表头（~4700 字符）挤占预算至 ~76 字符 → 每片只能容 1 行（行宽 ~40 字符）
        List<String> headers = List.of("H".repeat(4700), "列B");
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            rows.add(row("r" + i, "x".repeat(30)));
        }

        List<List<List<String>>> slices = ExcelRowSlicer.slice(rows, headers, 100);

        assertEquals(3, slices.size(), "宽表头挤占预算 → 每片 1 行");
    }
}

/** 序列化计量工具（与 ExcelRowSlicer/MarkdownSerializer 表格同式，供断言复核） */
class ExcelRowSlicerTestUtil {
    static int headerBlockChars(List<String> headers) {
        int cols = headers.size();
        return lineChars(headers, cols) + 1 + 6 * cols + 1;
    }

    static int rowsChars(List<List<String>> rows, int cols) {
        int sum = 0;
        for (List<String> r : rows) sum += lineChars(r, cols);
        return sum;
    }

    private static int lineChars(List<String> cells, int cols) {
        int sum = 2;
        for (int i = 0; i < cols; i++) {
            String c = i < cells.size() && cells.get(i) != null ? cells.get(i) : "";
            sum += c.length();
            if (i < cols - 1) sum += 3;
        }
        return sum + 3;
    }
}
