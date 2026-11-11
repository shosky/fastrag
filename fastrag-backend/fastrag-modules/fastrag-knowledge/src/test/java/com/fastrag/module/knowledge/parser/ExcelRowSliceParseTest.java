package com.fastrag.module.knowledge.parser;

import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * excel_row 解析期装配测试（REQ-H05/H06/H07，tdd §5.2 S8/S9 切片；ADR-0012）。
 *
 * POI 内存构簿构造 Excel 字节（不落盘 fixture）；直调 parseExcelContent 的 excel_row 重载，
 * 断言「显式表头 + 按行切片」的多 TABLE 节点产出；同步覆盖自动路径回归（excelRow=null 不受影响）。
 */
class ExcelRowSliceParseTest {

    private static ParseStrategyConfig.ExcelRow er(int start, int end, int sliceStart, int rows) {
        ParseStrategyConfig.ExcelRow er = new ParseStrategyConfig.ExcelRow();
        er.setHeaderStartRow(start);
        er.setHeaderEndRow(end);
        er.setSliceStartRow(sliceStart);
        er.setRowsPerSlice(rows);
        return er;
    }

    /** 构造 Sheet：headerStart~headerEnd 为表头行，其后 gapRows 行废弃行，再 dataRows 行数据 */
    private byte[] buildXlsx(String sheetName, int headerStart, int headerEnd, int gapRows, int dataRows,
                             String... moreSheets) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet(sheetName);
            int r = headerStart - 1; // POI 0-based
            for (int i = 0; i < headerEnd - headerStart + 1; i++) {
                Row row = s.createRow(r++);
                row.createCell(0).setCellValue("表头" + (i + 1));
                row.createCell(1).setCellValue("列" + (i + 1));
            }
            for (int i = 0; i < gapRows; i++) {
                Row row = s.createRow(r++);
                row.createCell(0).setCellValue("废弃" + i);
                row.createCell(1).setCellValue("跳过" + i);
            }
            for (int i = 1; i <= dataRows; i++) {
                Row row = s.createRow(r++);
                row.createCell(0).setCellValue("值" + i);
                row.createCell(1).setCellValue("n" + i);
            }
            for (String extra : moreSheets) {
                Sheet s2 = wb.createSheet(extra);
                s2.createRow(0).createCell(0).setCellValue("表头A");
                s2.createRow(1).createCell(0).setCellValue("值");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private long countTables(List<DocNode> nodes) {
        return nodes.stream().filter(n -> n.getType() == DocNode.NodeType.TABLE).count();
    }

    @Test
    void 显式表头两行十数据行按四行切片产三片且每片带表头() throws Exception {
        byte[] bytes = buildXlsx("数据", 1, 2, 0, 10);
        List<DocNode> nodes = parse(bytes, er(1, 2, 3, 4));

        assertEquals(1, nodes.stream().filter(n -> n.getType() == DocNode.NodeType.HEADING).count());
        assertEquals(3, countTables(nodes), "10 行按 4 行切片 → 3 片");
        List<Integer> sizes = nodes.stream()
                .filter(n -> n.getType() == DocNode.NodeType.TABLE)
                .map(n -> n.getRows().size()).toList();
        assertEquals(List.of(4, 4, 2), sizes);
        for (DocNode table : nodes) {
            if (table.getType() != DocNode.NodeType.TABLE) continue;
            assertEquals(2, table.getHeaders().size(), "每片自带两行展开的表头");
            assertTrue(table.getHeaders().get(0).contains("表头1"),
                    "多行表头按既有「-」连接语义合并（实际：" + table.getHeaders().get(0) + "）");
        }
    }

    @Test
    void 表头结束与切分起始之间的行被跳过() throws Exception {
        // 表头第 1 行，第 2~3 行为废弃行，切分起始行 = 4
        byte[] bytes = buildXlsx("数据", 1, 1, 2, 5);
        List<DocNode> nodes = parse(bytes, er(1, 1, 4, 10));

        assertEquals(1, countTables(nodes));
        DocNode table = nodes.stream().filter(n -> n.getType() == DocNode.NodeType.TABLE).findFirst().orElseThrow();
        assertEquals(5, table.getRows().size());
        assertTrue(table.getRows().get(0).get(0).equals("值1"), "切片自切分起始行开始");
    }

    @Test
    void 单行超4800字符硬切多段且续段重复表头() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("大数据");
            s.createRow(0).createCell(0).setCellValue("表头A");
            Row big = s.createRow(1);
            big.createCell(0).setCellValue("长".repeat(10000));
            big.createCell(1).setCellValue("v");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            List<DocNode> nodes = parse(out.toByteArray(), er(1, 1, 2, 100));

            int tables = (int) countTables(nodes);
            assertTrue(tables >= 3, "10000 字符行应硬切为多段，实际 " + tables + " 片");
            for (DocNode n : nodes) {
                if (n.getType() == DocNode.NodeType.TABLE) {
                    assertEquals(1, n.getHeaders().size());
                    assertEquals("表头A", n.getHeaders().get(0), "续段重复表头");
                }
            }
        }
    }

    @Test
    void 双Sheet统一按同参切片() throws Exception {
        byte[] bytes = buildXlsx("主表", 1, 1, 0, 6, "副表");
        List<DocNode> nodes = parse(bytes, er(1, 1, 2, 3));

        // 主表 6 行按 3 行 → 2 片；副表 1 数据行 → 1 片
        assertEquals(3, countTables(nodes));
        assertEquals(2, nodes.stream().filter(n -> n.getType() == DocNode.NodeType.HEADING).count());
    }

    @Test
    void 行数不足切分起始行时跳过数据并告警不阻断() throws Exception {
        // Sheet 仅 2 行；sliceStartRow=5 → 无切片，仅 HEADING；整体解析成功
        byte[] bytes = buildXlsx("小表", 1, 1, 0, 1);
        List<DocNode> nodes = parse(bytes, er(1, 1, 5, 10));

        assertEquals(1, nodes.size());
        assertEquals(DocNode.NodeType.HEADING, nodes.get(0).getType());
    }

    @Test
    void excelRow为null时走自动表头路径不受影响() throws Exception {
        byte[] bytes = buildXlsx("数据", 1, 1, 0, 6);
        List<DocNode> nodes = parse(bytes, null);

        // 自动路径：整表单 TABLE 节点（6 数据行合并为一片）
        assertEquals(1, countTables(nodes));
        DocNode table = nodes.stream().filter(n -> n.getType() == DocNode.NodeType.TABLE).findFirst().orElseThrow();
        assertEquals(6, table.getRows().size());
    }

    @Test
    void 表头行含合并单元格时按左上角值展开() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("合并表头");
            // 第 1~2 行 A 列纵向合并（表头跨两行）
            org.apache.poi.ss.util.CellRangeAddress region =
                    new org.apache.poi.ss.util.CellRangeAddress(0, 1, 0, 0);
            s.addMergedRegion(region);
            s.createRow(0).createCell(0).setCellValue("订单");
            s.createRow(1).createCell(1).setCellValue("金额");
            s.createRow(2).createCell(0).setCellValue("A001");
            s.createRow(2).createCell(1).setCellValue(100);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            List<DocNode> nodes = parse(out.toByteArray(), er(1, 2, 3, 10));

            assertEquals(1, countTables(nodes));
            DocNode table = nodes.stream().filter(n -> n.getType() == DocNode.NodeType.TABLE).findFirst().orElseThrow();
            assertEquals("订单", table.getHeaders().get(0), "纵向合并表头取左上角值");
        }
    }

    private List<DocNode> parse(byte[] bytes, ParseStrategyConfig.ExcelRow er) throws Exception {
        DocumentParserImpl parser = new DocumentParserImpl(
                null, null, null, null, null, null, null, null, null);
        return parser.parseExcelContent(new ByteArrayInputStream(bytes), null, null, null, er);
    }
}
