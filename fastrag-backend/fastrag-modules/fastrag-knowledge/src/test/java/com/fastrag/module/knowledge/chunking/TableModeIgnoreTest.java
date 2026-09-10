package com.fastrag.module.knowledge.chunking;

import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.module.knowledge.config.StrategyConfigResolver;
import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import com.fastrag.module.knowledge.parser.DocNode;
import com.fastrag.module.knowledge.parser.MarkdownSerializer;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C3 修复验证：tableMode=ignore 时，纯表格文档（Excel 类，仅 HEADING+TABLE）
 * 应保留 TABLE 节点避免数据静默丢失；混合文档（DOCX 类）仍跳过表格。
 */
class TableModeIgnoreTest {

    private ChunkingServiceImpl buildService(ParseStrategyConfig config) {
        StrategyConfigResolver resolver = mock(StrategyConfigResolver.class);
        // ChunkingServiceImpl 以 strategyId(String) 调用 resolve，显式指定重载避免 any() 二义性
        when(resolver.resolve(anyString())).thenReturn(config);
        return new ChunkingServiceImpl(resolver, new MarkdownSerializer(), mock(EmbeddingService.class),
                mock(ModelRecordMapper.class));
    }

    private DocNode tableNode(String header, String cellValue) {
        return DocNode.builder()
                .type(DocNode.NodeType.TABLE)
                .headers(List.of("季度", "营收"))
                .rows(List.of(List.of("Q1", cellValue), List.of("Q2", "1680")))
                .build();
    }

    @Test
    void ignoreTableMode_pureTableDocument_keepsTable() {
        // 纯表格文档：HEADING + TABLE（模拟 Excel 解析输出）
        ParseStrategyConfig config = new ParseStrategyConfig();
        config.getParse().setTableMode("ignore");

        List<DocNode> nodes = List.of(
                DocNode.builder().type(DocNode.NodeType.HEADING).level(1).title("营收总览").build(),
                tableNode("季度", "1520"));

        List<ChunkData> chunks = buildService(config).structuralChunk(nodes, "s1");

        assertFalse(chunks.isEmpty(), "纯表格文档不应产生空结果");
        assertTrue(chunks.get(0).getContent().contains("1520"), "TABLE 数据应被保留");
        assertEquals("table", chunks.get(0).getChunkType());
    }

    @Test
    void ignoreTableMode_mixedDocument_stillSkipsTable() {
        // 混合文档：HEADING + PARAGRAPH + TABLE（DOCX 场景），tableMode=ignore 应跳过表格
        ParseStrategyConfig config = new ParseStrategyConfig();
        config.getParse().setTableMode("ignore");

        List<DocNode> nodes = List.of(
                DocNode.builder().type(DocNode.NodeType.HEADING).level(1).title("第一章").build(),
                DocNode.builder().type(DocNode.NodeType.PARAGRAPH).content("这是正文内容。").build(),
                tableNode("季度", "1520"));

        List<ChunkData> chunks = buildService(config).structuralChunk(nodes, "s1");

        assertFalse(chunks.isEmpty());
        assertFalse(chunks.get(0).getContent().contains("1520"), "混合文档中 TABLE 应被跳过");
        assertTrue(chunks.get(0).getContent().contains("这是正文内容"));
    }

    @Test
    void ignoreTableMode_excelWithTitleParagraph_keepsTable() {
        // Excel 场景：HEADING + 表格标题 PARAGRAPH（短文本）+ TABLE，tableMode=ignore 仍应保留表格
        ParseStrategyConfig config = new ParseStrategyConfig();
        config.getParse().setTableMode("ignore");

        List<DocNode> nodes = List.of(
                DocNode.builder().type(DocNode.NodeType.HEADING).level(1).title("产业数字化").build(),
                DocNode.builder().type(DocNode.NodeType.PARAGRAPH)
                        .content("附表1：2025年1-10月产业数字化收入完成情况").build(),
                tableNode("分公司", "1523.4"));

        List<ChunkData> chunks = buildService(config).structuralChunk(nodes, "s1");

        assertFalse(chunks.isEmpty(), "含表格标题的 Excel 文档不应产生空结果");
        assertTrue(chunks.get(0).getContent().contains("1523.4"), "TABLE 数据应被保留");
        assertTrue(chunks.get(0).getContent().contains("附表1"), "表格标题 PARAGRAPH 应被保留");
        assertEquals("table", chunks.get(0).getChunkType());
    }

    @Test
    void structuredMode_tableAlwaysKept() {
        // tableMode=structured（默认）：表格正常保留
        ParseStrategyConfig config = new ParseStrategyConfig(); // 默认 structured

        List<DocNode> nodes = List.of(
                DocNode.builder().type(DocNode.NodeType.HEADING).level(1).title("营收总览").build(),
                tableNode("季度", "1520"));

        List<ChunkData> chunks = buildService(config).structuralChunk(nodes, "s1");

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).getContent().contains("1520"));
        assertEquals("table", chunks.get(0).getChunkType());
    }

    @Test
    void consecutiveHeadings_previousHeadingNotLost() {
        // 连续标题（如 PPT 空标题页 + 下一页正文）：H1 + H2 + P
        // 前一个标题不应被 pendingHeading 覆盖丢失
        ParseStrategyConfig config = new ParseStrategyConfig();

        List<DocNode> nodes = List.of(
                DocNode.builder().type(DocNode.NodeType.HEADING).level(1).title("第 1 页").build(),
                DocNode.builder().type(DocNode.NodeType.HEADING).level(1).title("第 2 页").build(),
                DocNode.builder().type(DocNode.NodeType.PARAGRAPH).content("第二页正文内容。").build());

        List<ChunkData> chunks = buildService(config).structuralChunk(nodes, "s1");

        assertFalse(chunks.isEmpty(), "连续标题场景不应产生空结果");
        String content = chunks.get(0).getContent();
        assertTrue(content.contains("第 1 页"), "前一个标题不应丢失: " + content);
        assertTrue(content.contains("第 2 页"), "后一个标题应保留: " + content);
        assertTrue(content.contains("第二页正文内容"), "正文应保留: " + content);
    }
}
