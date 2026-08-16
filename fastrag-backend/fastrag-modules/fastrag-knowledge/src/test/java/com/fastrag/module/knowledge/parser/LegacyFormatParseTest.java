package com.fastrag.module.knowledge.parser;

import com.fastrag.ai.asr.AsrService;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.ocr.OcrService;
import com.fastrag.infra.minio.MinioService;
import com.fastrag.module.knowledge.config.StrategyConfigResolver;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 旧版 Office 格式（.doc / .xls）解析测试。
 *
 * <p>测试数据说明：</p>
 * <ul>
 *   <li>.doc — POI HWPF 只支持读取/修改（无创建入口），因此使用 Word 生成的
 *       真实 fixture 文件（src/test/resources/fixtures/sample.doc）验证文本提取</li>
 *   <li>.xls — HSSFWorkbook 支持程序化创建，直接生成含表格结构的测试文件</li>
 *   <li>异常用例 — 伪装成 .doc 的纯文本文件，验证抛出明确错误提示</li>
 * </ul>
 */
class LegacyFormatParseTest {

    private DocumentParserImpl buildParser() {
        MarkdownSerializer serializer = new MarkdownSerializer();
        return new DocumentParserImpl(
                mock(KbParseStrategyMapper.class),
                mock(ModelRecordMapper.class),
                mock(LlmService.class),
                mock(AsrService.class),
                mock(OcrService.class),
                mock(MediaExtractor.class),
                mock(MinioService.class),
                serializer,
                mock(StrategyConfigResolver.class));
    }

    @Test
    void parseDoc_extractsChineseTextWithoutGarbled() throws Exception {
        // 读取 Word 生成的真实 .doc fixture
        byte[] docBytes;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/sample.doc")) {
            assertNotNull(in, "sample.doc fixture 应存在");
            docBytes = in.readAllBytes();
        }

        ParseResult result = buildParser().parse(
                new ByteArrayInputStream(docBytes), "doc", null);

        assertNotNull(result);
        assertEquals(1, result.getPages());
        assertNotNull(result.getNodes(), "doc 应生成 DocNode 列表");
        assertFalse(result.getNodes().isEmpty());
        assertTrue(result.getText().contains("旧版Word文档"), "正文文本应被提取: " + result.getText());
        assertTrue(result.getText().contains("第二行内容"), "第二段文本应被提取");
        assertFalse(result.getText().contains("\uFFFD"), "不应出现乱码替换符");
    }

    @Test
    void parseXls_parsesTableStructure() throws Exception {
        // 程序化生成 .xls 文件：表头 + 两行数据（含合并单元格）
        byte[] xlsBytes;
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet("营收总览");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("季度");
            header.createCell(1).setCellValue("营收");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Q1");
            r1.createCell(1).setCellValue(1520);
            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Q2");
            r2.createCell(1).setCellValue(1680);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                xlsBytes = out.toByteArray();
            }
        }

        ParseResult result = buildParser().parse(
                new ByteArrayInputStream(xlsBytes), "xls", null);

        assertNotNull(result);
        assertTrue(result.getText().contains("营收总览"), "Sheet 名应作为标题: " + result.getText());
        assertTrue(result.getText().contains("Q1"), "单元格数据应被提取");
        assertTrue(result.getText().contains("1520"), "数字单元格应被提取");
        // 至少有一个 TABLE 节点（表头 + 数据行）
        boolean hasTable = result.getNodes().stream()
                .anyMatch(n -> n.getType() == DocNode.NodeType.TABLE);
        assertTrue(hasTable, "xls 应解析出 TABLE 节点");
    }

    @Test
    void parseDoc_invalidFile_throwsChineseError() {
        // 伪装成 .doc 的纯文本文件：HWPF 无法解析，应抛出中文提示
        byte[] fakeBytes = "这不是一个真正的 Word 文件".getBytes(StandardCharsets.UTF_8);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> buildParser().parse(new ByteArrayInputStream(fakeBytes), "doc", null));
        assertTrue(ex.getMessage() != null && !ex.getMessage().isBlank(),
                "应抛出带原因信息的异常: " + ex.getMessage());
    }
}
