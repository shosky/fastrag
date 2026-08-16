package com.fastrag.module.knowledge.parser;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 冒烟测试：验证 parseExcelContent() 的核心行为
 * （多行表头、合并单元格、隐藏行列、DataFormatter、.xls 兼容）
 */
class ExcelParserSmokeTest {

    private final DocumentParserImpl parser = new DocumentParserImpl(
            null, null, null, null, null, null, null, null, null);

    /** 构造 xlsx：含单行表头、多行合并表头、隐藏行/列、合并单元格、空行 */
    private byte[] buildXlsx() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            // Sheet 0: 单行表头 + 数据
            Sheet s0 = wb.createSheet("营收总览");
            Row h = s0.createRow(0);
            h.createCell(0).setCellValue("季度");
            h.createCell(1).setCellValue("营收(万)");
            h.createCell(2).setCellValue("利润(万)");
            s0.createRow(1).createCell(0).setCellValue("Q1");
            s0.getRow(1).createCell(1).setCellValue(1520);
            s0.getRow(1).createCell(2).setCellValue(540);
            s0.createRow(2).createCell(0).setCellValue("Q2");
            s0.getRow(2).createCell(1).setCellValue(1680);
            s0.getRow(2).createCell(2).setCellValue(630);
            // 合并单元格：C3 值为 999
            s0.addMergedRegion(new CellRangeAddress(3, 3, 1, 2));
            Row r3 = s0.createRow(3);
            r3.createCell(0).setCellValue("Q3");
            r3.createCell(1).setCellValue(999);
            // 全空行（应跳过）
            s0.createRow(4);
            // 隐藏行（应跳过）
            Row hiddenRow = s0.createRow(5);
            hiddenRow.createCell(0).setCellValue("Q4-隐藏");
            hiddenRow.setZeroHeight(true);
            // 隐藏列
            s0.setColumnHidden(2, true);

            // Sheet 1: 多行合并表头
            Sheet s1 = wb.createSheet("环比数据");
            Row h1r0 = s1.createRow(0);
            h1r0.createCell(0).setCellValue("指标");
            h1r0.createCell(1).setCellValue("2024年");
            s1.addMergedRegion(new CellRangeAddress(0, 0, 1, 2));
            Row h1r1 = s1.createRow(1);
            h1r1.createCell(0).setCellValue("指标");
            h1r1.createCell(1).setCellValue("Q1");
            h1r1.createCell(2).setCellValue("Q2");
            Row d1 = s1.createRow(2);
            d1.createCell(0).setCellValue("DAU");
            d1.createCell(1).setCellValue(12000);
            d1.createCell(2).setCellValue(13500);

            // Sheet 2: 隐藏 Sheet（应跳过）
            wb.createSheet("_计算辅助");
            wb.setSheetHidden(2, true);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    /** 构造 xls（HSSFWorkbook）验证 .xls 兼容 */
    private byte[] buildXls() throws Exception {
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            Sheet s0 = wb.createSheet("测试");
            Row h = s0.createRow(0);
            h.createCell(0).setCellValue("姓名");
            h.createCell(1).setCellValue("人数");
            s0.createRow(1).createCell(0).setCellValue("研发部");
            s0.getRow(1).createCell(1).setCellValue(45);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * 构造含"标题行 + 列名行"的 xlsx（用户场景）：
     * Row0: 附件1 / Row1: 产数高质量发展情况 / Row2: 附表1：2025年1-10月产业数字化收入完成情况
     * Row3: 真正的列名行（无合并单元格）
     */
    private byte[] buildXlsxWithTitleRows() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("产业数字化");
            s.createRow(0).createCell(0).setCellValue("附件1");
            s.createRow(1).createCell(0).setCellValue("产数高质量发展情况");
            s.createRow(2).createCell(0).setCellValue("附表1：2025年1-10月产业数字化收入完成情况");
            Row header = s.createRow(3);
            header.createCell(0).setCellValue("分公司");
            header.createCell(1).setCellValue("本月完成（万元）");
            header.createCell(2).setCellValue("本月进度（%）");
            Row data1 = s.createRow(4);
            data1.createCell(0).setCellValue("广东");
            data1.createCell(1).setCellValue(1523.4);
            data1.createCell(2).setCellValue(45.2);
            Row data2 = s.createRow(5);
            data2.createCell(0).setCellValue("江苏");
            data2.createCell(1).setCellValue(980.6);
            data2.createCell(2).setCellValue(38.7);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    void parseXlsx_contentAndStructure() throws Exception {
        List<DocNode> nodes = parser.parseExcelContent(
                new ByteArrayInputStream(buildXlsx()), null, null, null);

        // Sheet0(HEADING) + TABLE + Sheet1(HEADING) + TABLE = 4 个节点（隐藏 Sheet 被跳过）
        assertEquals(4, nodes.size());

        // 第一个 HEADING = "营收总览"
        DocNode heading0 = nodes.get(0);
        assertEquals(DocNode.NodeType.HEADING, heading0.getType());
        assertEquals("营收总览", heading0.getTitle());

        // TABLE：表头 + 数据（隐藏列被过滤 → 2 列；隐藏行/空行被跳过）
        DocNode table0 = nodes.get(1);
        assertEquals(DocNode.NodeType.TABLE, table0.getType());
        assertEquals(List.of("季度", "营收(万)"), table0.getHeaders());
        assertEquals(3, table0.getRows().size()); // Q1/Q2/Q3(合并)
        // DataFormatter：整数 1520 → "1520"（非 "1520.0"）
        assertEquals(List.of("Q1", "1520"), table0.getRows().get(0));
        // 合并单元格：C3 行 1 列取左上角值 999
        assertEquals(List.of("Q3", "999"), table0.getRows().get(2));

        // 第二个 HEADING = "环比数据"，多行表头已合并为单行
        DocNode heading1 = nodes.get(2);
        assertEquals("环比数据", heading1.getTitle());
        DocNode table1 = nodes.get(3);
        assertEquals(List.of("指标", "2024年-Q1", "2024年-Q2"), table1.getHeaders());
        assertEquals(List.of("DAU", "12000", "13500"), table1.getRows().get(0));
    }

    @Test
    void parseXlsx_titleRowsAboveHeader() throws Exception {
        // 标题行（附件1/产数高质量发展情况/附表1）+ 列名行 + 数据行
        List<DocNode> nodes = parser.parseExcelContent(
                new ByteArrayInputStream(buildXlsxWithTitleRows()), null, null, null);

        // HEADING + PARAGRAPH(标题) + TABLE = 3 个节点
        assertEquals(3, nodes.size());

        DocNode heading = nodes.get(0);
        assertEquals(DocNode.NodeType.HEADING, heading.getType());
        assertEquals("产业数字化", heading.getTitle());

        // 标题行合并为 PARAGRAPH 节点（"-" 拼接，信息不丢失）
        DocNode title = nodes.get(1);
        assertEquals(DocNode.NodeType.PARAGRAPH, title.getType());
        assertEquals("附件1 - 产数高质量发展情况 - 附表1：2025年1-10月产业数字化收入完成情况",
                title.getContent());

        // 列名行（第 4 行）正确识别为表头，标题行不进入数据
        DocNode table = nodes.get(2);
        assertEquals(DocNode.NodeType.TABLE, table.getType());
        assertEquals(List.of("分公司", "本月完成（万元）", "本月进度（%）"), table.getHeaders());
        assertEquals(2, table.getRows().size());
        // DataFormatter：数值 1523.4 → "1523.4"（未设置百分比格式时为纯数值）
        assertEquals(List.of("广东", "1523.4", "45.2"), table.getRows().get(0));
        assertEquals(List.of("江苏", "980.6", "38.7"), table.getRows().get(1));
    }

    @Test
    void parseXls_compatibility() throws Exception {
        List<DocNode> nodes = parser.parseExcelContent(
                new ByteArrayInputStream(buildXls()), null, null, null);
        assertEquals(2, nodes.size()); // HEADING + TABLE
        DocNode table = nodes.get(1);
        assertEquals(List.of("姓名", "人数"), table.getHeaders());
        assertEquals(List.of("研发部", "45"), table.getRows().get(0));
    }

    @Test
    void parseCorruptedFile_throwsWithMessage() {
        byte[] garbage = "this is not an excel file".getBytes();
        Exception ex = assertThrows(Exception.class, () ->
                parser.parseExcelContent(new ByteArrayInputStream(garbage), null, null, null));
        assertNotNull(ex.getMessage());
    }

    @Test
    void stripCommonHeaderPrefix_removesTitlePrefixFromHeaders() {
        List<String> titleRows = List.of("附件1", "产数高质量发展情况", "附表1：2025年1-10月产业数字化收入完成情况");
        String prefix = "附件1-产数高质量发展情况-附表1：2025年1-10月产业数字化收入完成情况-";
        List<String> polluted = List.of(
                prefix + "分公司", prefix + "本月完成（万元）", prefix + "本月进度（%）", prefix + "环比增幅（%）");

        // title 变体（"-" 分隔）前缀剥离
        List<String> cleaned = DocumentParserImpl.stripCommonHeaderPrefix(polluted, titleRows);
        assertEquals(List.of("分公司", "本月完成（万元）", "本月进度（%）", "环比增幅（%）"), cleaned);
    }

    @Test
    void stripCommonHeaderPrefix_handlesInconsistentPrefixSegments() {
        // LLM 输出不一致：部分列缺"附表1"段、部分列缺"附件1"段 → 逐段循环剥离
        List<String> titleRows = List.of("附件1", "产数高质量发展情况", "附表1：2025年1-10月产业数字化收入完成情况");
        List<String> polluted = List.of(
                "附件1-产数高质量发展情况-附表1：2025年1-10月产业数字化收入完成情况-分公司",
                "附件1-产数高质量发展情况-信用补收未回款金额（万元）",   // 缺"附表1"段
                "产数高质量发展情况-剔除后进度（%）",                  // 缺"附件1"和"附表1"段
                "剔除后增幅（%）");                                    // 无前缀

        List<String> cleaned = DocumentParserImpl.stripCommonHeaderPrefix(polluted, titleRows);
        assertEquals(List.of("分公司", "信用补收未回款金额（万元）", "剔除后进度（%）", "剔除后增幅（%）"), cleaned);
    }

    @Test
    void stripCommonHeaderPrefix_keepsNormalShortPrefixes() {
        // 正常列名共享短前缀（如"本月完成"与"本月进度"），不应被剥离
        List<String> headers = List.of("本月完成（万元）", "本月进度（%）", "累计完成（万元）");
        List<String> cleaned = DocumentParserImpl.stripCommonHeaderPrefix(headers, null);
        assertEquals(headers, cleaned);
    }

    @Test
    void stripCommonHeaderPrefix_removesLongUnknownPrefix() {
        // LLM 用未知拼接方式时：长公共前缀（>20 字符且占首元素 >30%）整体剥离
        String prefix = "某报告标题-某二级标题-某三级标题-汇总说明-";
        List<String> polluted = List.of(prefix + "分公司", prefix + "本月完成（万元）");
        List<String> cleaned = DocumentParserImpl.stripCommonHeaderPrefix(polluted, null);
        assertEquals(List.of("分公司", "本月完成（万元）"), cleaned);
    }

    @Test
    void parseXlsx_noteRowAfterData_isKeptAsParagraph() throws Exception {
        // 表尾说明行（跨列合并，每列值相同）→ 不作为数据行，转为 PARAGRAPH
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("产业数字化");
            Row header = s.createRow(0);
            header.createCell(0).setCellValue("分公司");
            header.createCell(1).setCellValue("本月完成（万元）");
            header.createCell(2).setCellValue("本月进度（%）");
            Row data = s.createRow(1);
            data.createCell(0).setCellValue("长沙");
            data.createCell(1).setCellValue(10737);
            data.createCell(2).setCellValue(9.3);
            // 说明行：跨列合并（真实文件中每列都有单元格，取值相同），以"说明"开头
            Row note = s.createRow(2);
            String noteText = "说明：1、24年审计号码级补收，25年审计整改号码级剔收。\n2、云IDC非业务型手工冲减影响1847万元。";
            note.createCell(0).setCellValue(noteText);
            note.createCell(1).setCellValue(noteText);
            note.createCell(2).setCellValue(noteText);
            s.addMergedRegion(new CellRangeAddress(2, 2, 0, 2));

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);

            List<DocNode> nodes = parser.parseExcelContent(
                    new ByteArrayInputStream(bos.toByteArray()), null, null, null);

            // HEADING + TABLE + PARAGRAPH(说明) = 3 个节点
            assertEquals(3, nodes.size());
            DocNode table = nodes.get(1);
            assertEquals(DocNode.NodeType.TABLE, table.getType());
            // 说明行不进数据行
            assertEquals(1, table.getRows().size());
            assertEquals(List.of("长沙", "10737", "9.3"), table.getRows().get(0));
            // 说明行作为 PARAGRAPH 保留（只出现一次，不重复）
            DocNode noteNode = nodes.get(2);
            assertEquals(DocNode.NodeType.PARAGRAPH, noteNode.getType());
            assertTrue(noteNode.getContent().startsWith("说明：1、24年审计号码级补收"));
            assertFalse(noteNode.getContent().contains("说明：1、24年审计号码级补收。\n说明："),
                    "说明文本不应重复出现");
        }
    }
}
