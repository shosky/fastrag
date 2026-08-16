package com.fastrag.module.knowledge.parser;

import com.fastrag.ai.llm.LlmService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 完整链路测试：LLM 返回"标题行拼进列名"的脏输出时，
 * 真实解析流程（parseExcelContent → resolveHeadersWithLlm → stripCommonHeaderPrefix）必须产出干净列名。
 */
class LlmHeaderFullFlowTest {

    private final LlmService llmService = mock(LlmService.class);

    private DocumentParserImpl buildParser() {
        return new DocumentParserImpl(
                null, null, llmService, null, null, null, null, new MarkdownSerializer(), null);
    }

    /** 构造：3 行标题 + 2 行合并表头 + 1 行数据（多行表头触发 LLM 调用） */
    private byte[] buildXlsx() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet s = wb.createSheet("产业数字化");
            s.createRow(0).createCell(0).setCellValue("附件1");
            s.createRow(1).createCell(0).setCellValue("产数高质量发展情况");
            s.createRow(2).createCell(0).setCellValue("附表1：2025年1-10月产业数字化收入完成情况");
            // 两行合并表头：指标(垂直合并) | 2024年(横跨Q1/Q2)
            Row h0 = s.createRow(3);
            h0.createCell(0).setCellValue("指标");
            h0.createCell(1).setCellValue("2024年");
            s.addMergedRegion(new CellRangeAddress(3, 4, 0, 0));
            s.addMergedRegion(new CellRangeAddress(3, 3, 1, 2));
            Row h1 = s.createRow(4);
            h1.createCell(1).setCellValue("Q1");
            h1.createCell(2).setCellValue("Q2");
            Row d = s.createRow(5);
            d.createCell(0).setCellValue("DAU");
            d.createCell(1).setCellValue(12000);
            d.createCell(2).setCellValue(13500);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    void llmPollutedHeaders_areStrippedInFullFlow() throws Exception {
        // LLM 返回脏输出：标题行拼进每个列名 + 列数正确
        String prefix = "附件1-产数高质量发展情况-附表1：2025年1-10月产业数字化收入完成情况-";
        when(llmService.chatWithTimeout(anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), anyInt()))
                .thenReturn("[\"" + prefix + "指标\", \"" + prefix + "2024年-Q1\", \"" + prefix + "2024年-Q2\"]");

        DocumentParserImpl parser = buildParser();
        List<DocNode> nodes = parser.parseExcelContent(
                new ByteArrayInputStream(buildXlsx()), null, "test-model",
                new DocumentParserImpl.LlmConfig("http://localhost:9999", "test-key"));

        // HEADING + PARAGRAPH(标题) + TABLE = 3 个节点
        assertEquals(3, nodes.size(), () -> "nodes=" + nodes);

        // 标题由代码生成（不依赖 LLM）
        DocNode title = nodes.get(1);
        assertEquals(DocNode.NodeType.PARAGRAPH, title.getType());
        assertEquals("附件1 - 产数高质量发展情况 - 附表1：2025年1-10月产业数字化收入完成情况",
                title.getContent());

        // 列名被剥离为纯列名（LLM 脏前缀已被 stripCommonHeaderPrefix 移除）
        DocNode table = nodes.get(2);
        assertEquals(DocNode.NodeType.TABLE, table.getType());
        assertEquals(List.of("指标", "2024年-Q1", "2024年-Q2"), table.getHeaders());
        assertEquals(List.of("DAU", "12000", "13500"), table.getRows().get(0));
    }

    @Test
    void llmCleanOutput_headersUsedDirectly() throws Exception {
        // LLM 返回干净输出（严格遵守约束）时直接使用
        when(llmService.chatWithTimeout(anyString(), anyString(), anyString(), anyString(),
                anyBoolean(), anyInt()))
                .thenReturn("[\"指标\", \"2024年-Q1\", \"2024年-Q2\"]");

        DocumentParserImpl parser = buildParser();
        List<DocNode> nodes = parser.parseExcelContent(
                new ByteArrayInputStream(buildXlsx()), null, "test-model",
                new DocumentParserImpl.LlmConfig("http://localhost:9999", "test-key"));

        DocNode table = nodes.get(2);
        assertEquals(List.of("指标", "2024年-Q1", "2024年-Q2"), table.getHeaders());
    }
}
