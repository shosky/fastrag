package com.fastrag.module.knowledge.chunking;

import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.module.knowledge.config.StrategyConfigResolver;
import com.fastrag.module.knowledge.model.ParseStrategyConfig;
import com.fastrag.module.knowledge.parser.DocNode;
import com.fastrag.module.knowledge.parser.MarkdownSerializer;
import com.fastrag.module.platform.mapper.ModelRecordMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * excel_row 分片侧测试（REQ-H08/H10，ADR-0012 豁免；tdd §5.2 S10 切片）。
 *
 * 断言：切片 TABLE 节点在 excel_row 模式下整片入分片（不被 chunkLength 二次拆碎）、
 * 切片间不回填 overlap（每片自带表头自包含）、元数据沿用（title=Sheet 名/chunkType=table）、
 * tableMode=ignore 不丢片；对照 rule_fixed 模式的整表仍被溢出拆分（豁免仅限 excel_row）。
 */
class ExcelRowChunkingTest {

    private ChunkingServiceImpl buildService(ParseStrategyConfig config) {
        StrategyConfigResolver resolver = mock(StrategyConfigResolver.class);
        when(resolver.resolve(anyString())).thenReturn(config);
        return new ChunkingServiceImpl(resolver, new MarkdownSerializer(), mock(EmbeddingService.class),
                mock(ModelRecordMapper.class));
    }

    private static ParseStrategyConfig config(String strategy, String tableMode) {
        ParseStrategyConfig config = new ParseStrategyConfig();
        config.getChunk().setStrategy(strategy);
        config.getChunk().setChunkLength(1000);
        config.getChunk().setOverlap(100);
        if (tableMode != null) config.getParse().setTableMode(tableMode);
        return config;
    }

    /** 切片 TABLE 节点：count 片，每片 rowsPer 片行（行内 padded 使单行序列化 ~60 字符） */
    private static List<DocNode> sliceNodes(int count, int rowsPer, int pad) {
        List<DocNode> nodes = new ArrayList<>();
        nodes.add(DocNode.builder().type(DocNode.NodeType.HEADING).level(1).title("Sheet1").build());
        for (int s = 0; s < count; s++) {
            List<List<String>> rows = new ArrayList<>();
            for (int r = 0; r < rowsPer; r++) {
                rows.add(List.of("值" + s + "-" + r, "y".repeat(pad)));
            }
            nodes.add(DocNode.builder()
                    .type(DocNode.NodeType.TABLE)
                    .headers(List.of("列A", "列B"))
                    .rows(rows)
                    .pageNumber(1)
                    .build());
        }
        return nodes;
    }

    @Test
    void excelRow模式整片入分片不被chunkLength二次拆碎() {
        // 每片序列化 ~2000 字符 > chunkLength=1000；excel_row 豁免 → 一 TABLE 一分片
        List<ChunkData> chunks = buildService(config("excel_row", null))
                .structuralChunk(sliceNodes(3, 30, 30), "s1");

        assertEquals(3, chunks.size(), "3 个切片节点应产出 3 个分片（豁免溢出拆分）");
        for (int i = 0; i < chunks.size(); i++) {
            ChunkData c = chunks.get(i);
            assertEquals("table", c.getChunkType());
            assertEquals("Sheet1", c.getTitle(), "title=Sheet 名沿用（REQ-H10）");
            assertTrue(c.getContent().contains("| 列A | 列B |"), "每片自带表头行");
            assertTrue(c.getContent().contains("值" + i + "-0"), "每片含自身数据行");
        }
        // 无 overlap 回填污染：第二片不含第一片尾部数据行
        assertFalse(chunks.get(1).getContent().contains("值0-29"),
                "excel_row 不回填 overlap——第二片不应带第一片尾部内容");
    }

    @Test
    void excelRow模式切片间不回填overlap() {
        // overlap=100；若回填，chunk2 会以 chunk1 尾部字符开头而非自身表头
        List<ChunkData> chunks = buildService(config("excel_row", null))
                .structuralChunk(sliceNodes(2, 30, 30), "s1");

        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.get(1).getContent().startsWith("| 列A"),
                "第二片应以自身表头开头（excel_row 不回填 overlap）");
    }

    @Test
    void excelRow模式忽略tableModeIgnore() {
        List<ChunkData> chunks = buildService(config("excel_row", "ignore"))
                .structuralChunk(sliceNodes(2, 5, 10), "s1");

        assertEquals(2, chunks.size(), "excel_row 忽略 tableMode=ignore，切片不丢（REQ-H08）");
    }

    @Test
    void ruleFixed模式整表超长仍被溢出拆分作对照() {
        // 同样的超长 TABLE，rule_fixed（默认分支）仍走 handleAtomicOverflow——豁免仅限 excel_row
        List<ChunkData> chunks = buildService(config("rule_fixed", null))
                .structuralChunk(sliceNodes(1, 30, 30), "s1");

        assertTrue(chunks.size() > 1, "rule_fixed 下超长表格仍按行拆分（既有行为对照）");
    }

    @Test
    void 多Sheet场景标题随切片流转() {
        List<DocNode> nodes = new ArrayList<>(sliceNodes(2, 5, 10));
        nodes.add(DocNode.builder().type(DocNode.NodeType.HEADING).level(1).title("Sheet2").build());
        nodes.addAll(sliceNodes(1, 5, 10).stream().skip(1).toList());

        List<ChunkData> chunks = buildService(config("excel_row", null)).structuralChunk(nodes, "s1");

        assertEquals(3, chunks.size());
        assertEquals("Sheet1", chunks.get(0).getTitle());
        assertEquals("Sheet1", chunks.get(1).getTitle());
        assertEquals("Sheet2", chunks.get(2).getTitle(), "Sheet2 HEADING 后的切片归属新标题");
    }
}
