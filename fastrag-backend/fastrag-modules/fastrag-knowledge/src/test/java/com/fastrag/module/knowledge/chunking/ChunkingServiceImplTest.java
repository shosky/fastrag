package com.fastrag.module.knowledge.chunking;

import com.fastrag.ai.embedding.EmbeddingService;
import com.fastrag.module.knowledge.config.StrategyConfigResolver;
import com.fastrag.module.knowledge.entity.KbParseStrategy;
import com.fastrag.module.knowledge.mapper.KbParseStrategyMapper;
import com.fastrag.module.knowledge.parser.DocNode;
import com.fastrag.module.knowledge.parser.MarkdownSerializer;
import com.fastrag.module.platform.entity.SysConfig;
import com.fastrag.module.platform.service.ConfigManageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 结构感知分片单元测试。
 *
 * 覆盖：段落累积（目标长度语义）、标题切分、headingPath 栈、
 * F1（标题合并进首段，无纯标题空壳）、overlap、tableMode=ignore、
 * F3（chunkType/imageKeys）、delimiters（新结构 + 旧平铺兼容）、
 * 递归字符切分（rule_recursive）、语义切片（semantic）。
 */
@ExtendWith(MockitoExtension.class)
class ChunkingServiceImplTest {

    @Mock
    private KbParseStrategyMapper strategyMapper;

    @Mock
    private ConfigManageService configService;

    @Mock
    private EmbeddingService embeddingService;

    private ChunkingServiceImpl chunkingService;

    @BeforeEach
    void setUp() {
        StrategyConfigResolver resolver = new StrategyConfigResolver(strategyMapper, configService);
        chunkingService = new ChunkingServiceImpl(resolver, new MarkdownSerializer(), embeddingService);
    }

    // ========== 测试数据构造 ==========

    private static DocNode heading(int level, String title) {
        return DocNode.builder().type(DocNode.NodeType.HEADING).level(level).title(title).build();
    }

    private static DocNode paragraph(String content) {
        return DocNode.builder().type(DocNode.NodeType.PARAGRAPH).content(content).build();
    }

    private static DocNode table(int rows) {
        java.util.ArrayList<List<String>> rowData = new java.util.ArrayList<>();
        for (int i = 0; i < rows; i++) {
            rowData.add(List.of("r" + i + "c1", "r" + i + "c2"));
        }
        return DocNode.builder().type(DocNode.NodeType.TABLE)
                .headers(List.of("h1", "h2")).rows(rowData).build();
    }

    private static DocNode image(String key) {
        return DocNode.builder().type(DocNode.NodeType.IMAGE).imageKey(key).imageCaption("图").build();
    }

    private static KbParseStrategy strategy(String advanced) {
        KbParseStrategy s = new KbParseStrategy();
        s.setId("s1");
        s.setAdvanced(advanced);
        return s;
    }

    private void mockChunkLength(int chunkLength) {
        SysConfig c = new SysConfig();
        c.setConfigValue(String.valueOf(chunkLength));
        when(configService.getConfig("general_chunk_size")).thenReturn(c);
    }

    private void mockOverlap(int overlap) {
        SysConfig c = new SysConfig();
        c.setConfigValue(String.valueOf(overlap));
        when(configService.getConfig("general_chunk_overlap")).thenReturn(c);
    }

    // ========== 测试用例 ==========

    @Test
    void 短段落累积进同一chunk且标题合并进首段() {
        List<DocNode> nodes = List.of(
                heading(1, "第一章"),
                paragraph("这是第一段内容。"),
                paragraph("这是第二段内容。"));

        List<ChunkData> chunks = chunkingService.structuralChunk(nodes, null);

        assertEquals(1, chunks.size());
        ChunkData chunk = chunks.get(0);
        assertTrue(chunk.getContent().contains("# 第一章"));
        assertTrue(chunk.getContent().contains("这是第一段内容。"));
        assertTrue(chunk.getContent().contains("这是第二段内容。"));
        assertEquals("第一章", chunk.getTitle());
        assertEquals("第一章", chunk.getHeadingPath());
        // F1：不存在只含标题的空壳 chunk
        assertNotEquals("# 第一章", chunk.getContent().trim());
    }

    @Test
    void 标题是切分点且headingPath正确() {
        List<DocNode> nodes = List.of(
                heading(1, "文档"),
                heading(2, "1. 背景"),
                paragraph("背景段落一。"),
                paragraph("背景段落二。"),
                heading(2, "2. 方案"),
                paragraph("方案段落。"));

        List<ChunkData> chunks = chunkingService.structuralChunk(nodes, null);

        assertEquals(2, chunks.size());
        assertEquals("文档 > 1. 背景", chunks.get(0).getHeadingPath());
        assertTrue(chunks.get(0).getContent().contains("# 文档"));
        assertTrue(chunks.get(0).getContent().contains("## 1. 背景"));
        assertEquals("文档 > 2. 方案", chunks.get(1).getHeadingPath());
    }

    @Test
    void headingPath栈在兄弟标题处截断() {
        List<DocNode> nodes = List.of(
                heading(1, "文档"),
                heading(2, "1. 背景"),
                heading(3, "1.1 定义"),
                paragraph("定义内容。"),
                heading(2, "2. 方案"),
                paragraph("方案内容。"));

        List<ChunkData> chunks = chunkingService.structuralChunk(nodes, null);

        assertEquals(2, chunks.size());
        assertEquals("文档 > 1. 背景 > 1.1 定义", chunks.get(0).getHeadingPath());
        // H2 到来时截断深度 >= 2 的元素
        assertEquals("文档 > 2. 方案", chunks.get(1).getHeadingPath());
    }

    @Test
    void 累积超过chunkLength时按目标长度落盘() {
        mockChunkLength(60);
        List<DocNode> nodes = List.of(
                heading(1, "第一章"),
                paragraph("这是第一句话的内容。这是第二句话的内容。这是第三句话的内容。这是第四句话的内容。这是第五句话的内容。这是第六句话的内容。这是第七句话的内容。这是第八句话的内容。这是第九句话的内容。这是第十句话的内容。"));

        List<ChunkData> chunks = chunkingService.structuralChunk(nodes, null);

        assertTrue(chunks.size() >= 2, "长文本应被切成多个 chunk");
        // 每个 chunk 长度接近目标长度（60），而不是一个句子一个 chunk
        for (ChunkData chunk : chunks) {
            assertTrue(chunk.getContent().length() <= 60 + 30,
                    "chunk 不应远超目标长度, actual=" + chunk.getContent().length());
        }
    }

    @Test
    void 落盘时携带overlap尾部() {
        mockChunkLength(60);
        mockOverlap(10);
        List<DocNode> nodes = List.of(
                heading(1, "第一章"),
                paragraph("句子一的内容。句子二的内容。句子三的内容。句子四的内容。句子五的内容。句子六的内容。句子七的内容。句子八的内容。句子九的内容。句子十的内容。句子十一的内容。句子十二的内容。"));

        List<ChunkData> chunks = chunkingService.structuralChunk(nodes, null);

        assertTrue(chunks.size() >= 2);
        // 下一个 chunk 的内容以 10 字符 overlap 尾部开头（单层标题无前缀干扰）
        String prev = chunks.get(0).getContent();
        String expectedTail = prev.substring(prev.length() - 10);
        assertTrue(chunks.get(1).getContent().startsWith(expectedTail),
                "chunk[1] 应以 overlap 尾部开头, expected tail=" + expectedTail
                        + ", actual start=" + chunks.get(1).getContent().substring(0, Math.min(30, chunks.get(1).getContent().length())));
    }

    @Test
    void tableMode_ignore时跳过表格节点() {
        when(strategyMapper.selectById("s1")).thenReturn(strategy("{\"parse\":{\"tableMode\":\"ignore\"}}"));
        List<DocNode> nodes = List.of(
                heading(1, "第一章"),
                paragraph("正文内容。"),
                table(3));

        List<ChunkData> chunks = chunkingService.structuralChunk(nodes, "s1");

        assertEquals(1, chunks.size());
        assertFalse(chunks.get(0).getContent().contains("|"), "表格应被忽略");
    }

    @Test
    void 标题加图片的chunk携带chunkType和imageKeys() {
        List<DocNode> nodes = List.of(
                heading(2, "1.1 架构图"),
                image("img_key_1"));

        List<ChunkData> chunks = chunkingService.structuralChunk(nodes, null);

        assertEquals(1, chunks.size());
        assertEquals("image", chunks.get(0).getChunkType());
        assertNotNull(chunks.get(0).getImageKeys());
        assertEquals(List.of("img_key_1"), chunks.get(0).getImageKeys());
        assertTrue(chunks.get(0).getContent().contains("img_key_1"));
    }

    @Test
    void 标题加表格的chunk携带chunkType_table() {
        List<DocNode> nodes = List.of(
                heading(2, "2.1 参数表"),
                table(5));

        List<ChunkData> chunks = chunkingService.structuralChunk(nodes, null);

        assertEquals(1, chunks.size());
        assertEquals("table", chunks.get(0).getChunkType());
        assertTrue(chunks.get(0).getContent().contains("| h1 | h2 |"));
    }

    @Test
    void 纯文本路径使用策略配置的分隔符() {
        // 策略配置的分隔符 ["。"] 生效：按句号切分，配合小 chunkLength 产生多个 chunk
        mockChunkLength(4);
        mockOverlap(0);
        when(strategyMapper.selectById("s1")).thenReturn(strategy("{\"chunk\":{\"delimiters\":[\"。\"]}}"));
        List<ChunkData> chunks = chunkingService.chunk("aaaa。bbbb。cccc。", "s1");
        assertEquals(3, chunks.size());
        assertEquals("aaaa", chunks.get(0).getContent());
        assertEquals("bbbb", chunks.get(1).getContent());
        assertEquals("cccc", chunks.get(2).getContent());
    }

    @Test
    void 纯文本路径兼容旧平铺delimiter字段() {
        // 旧字段为单数 delimiter（修复字段名 bug 的兼容读取）
        mockChunkLength(4);
        mockOverlap(0);
        when(strategyMapper.selectById("s1")).thenReturn(strategy("{\"delimiter\":\"。\"}"));
        List<ChunkData> chunks = chunkingService.chunk("aaaa。bbbb。cccc。", "s1");
        assertEquals(3, chunks.size());
        assertEquals("aaaa", chunks.get(0).getContent());
        assertEquals("cccc", chunks.get(2).getContent());
    }

    @Test
    void 兼容旧平铺chunkLength字段() {
        when(strategyMapper.selectById("s1")).thenReturn(strategy("{\"chunkLength\":30}"));
        List<DocNode> nodes = List.of(
                paragraph("这是第一句。这是第二句。这是第三句。这是第四句。这是第五句。这是第六句。这是第七句。这是第八句。"));

        List<ChunkData> chunks = chunkingService.structuralChunk(nodes, "s1");

        assertTrue(chunks.size() >= 2, "旧 chunkLength=30 应生效，产生多个 chunk");
        for (ChunkData chunk : chunks) {
            assertTrue(chunk.getContent().length() <= 60, "chunk 不应远超旧配置的 30");
        }
    }

    @Test
    void 新分组结构优先于旧平铺字段() {
        // 新结构 chunk.chunkLength=2000 与旧平铺 chunkLength=30 并存时，新结构优先
        when(strategyMapper.selectById("s1")).thenReturn(
                strategy("{\"chunk\":{\"chunkLength\":2000},\"chunkLength\":30}"));
        List<DocNode> nodes = List.of(paragraph("这是第一句。这是第二句。"));

        List<ChunkData> chunks = chunkingService.structuralChunk(nodes, "s1");

        assertEquals(1, chunks.size(), "新结构 chunkLength=2000 生效，短段落合并为单 chunk");
    }

    // ========== 递归字符切分（rule_recursive） ==========

    @Test
    void 递归切分按首分隔符切分且短片段合并() {
        // chunkLength=30：第一段（超长，降级句子分隔符）+ 第二段单独成块
        mockChunkLength(30);
        mockOverlap(0);
        when(strategyMapper.selectById("s1")).thenReturn(strategy("{\"chunk\":{\"strategy\":\"rule_recursive\",\"delimiters\":[\"\\n\\n\"]}}"));

        String text = "第一句话的内容很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长很长。\n\n第二段很短。";
        List<ChunkData> chunks = chunkingService.recursiveChunk(text, "s1");

        assertTrue(chunks.size() >= 2, "超长首段 + 短第二段应产生多个 chunk");
        // 首个 chunk 不应超过 chunkLength + 少量余量
        assertTrue(chunks.get(0).getContent().length() <= 30 + 30,
                "chunk 不应远超目标长度, actual=" + chunks.get(0).getContent().length());
        assertTrue(chunks.get(chunks.size() - 1).getContent().contains("第二段很短。"));
    }

    @Test
    void 递归切分文本中无分隔符时降级句子边界() {
        // delimiters=["@@"]，文本中不存在 → 降级到句子边界
        mockChunkLength(10);
        mockOverlap(0);
        when(strategyMapper.selectById("s1")).thenReturn(strategy("{\"chunk\":{\"strategy\":\"rule_recursive\",\"delimiters\":[\"@@\"]}}"));

        List<ChunkData> chunks = chunkingService.recursiveChunk("这是第一句。这是第二句。这是第三句。", "s1");

        assertTrue(chunks.size() >= 2, "无匹配分隔符时应按句子边界兜底切分");
        for (ChunkData chunk : chunks) {
            assertTrue(chunk.getContent().length() <= 10 + 10, "每块不应远超 chunkLength");
        }
    }

    @Test
    void 递归切分无任何分隔符时硬切() {
        // delimiters 为空 + 无句子边界（无标点长文本）→ 按 chunkLength 硬切
        mockChunkLength(8);
        mockOverlap(0);
        when(strategyMapper.selectById("s1")).thenReturn(strategy("{\"chunk\":{\"strategy\":\"rule_recursive\",\"delimiters\":[]}}"));

        List<ChunkData> chunks = chunkingService.recursiveChunk("abcdefghijklmnopqrstuvwxyz", "s1");

        assertEquals(4, chunks.size(), "26 字符 / 8 每块 = 至少 4 块");
        for (ChunkData chunk : chunks) {
            assertTrue(chunk.getContent().length() <= 8 + 8, "硬切块不应远超 chunkLength");
        }
    }

    @Test
    void 递归切分携带overlap尾部() {
        mockChunkLength(20);
        mockOverlap(5);
        when(strategyMapper.selectById("s1")).thenReturn(strategy("{\"chunk\":{\"strategy\":\"rule_recursive\"}}"));

        String longText = "段落一的内容。段落二的内容。段落三的内容。段落四的内容。段落五的内容。段落六的内容。段落七的内容。段落八的内容。段落九的内容。段落十的内容。";
        List<ChunkData> chunks = chunkingService.recursiveChunk(longText, "s1");

        assertTrue(chunks.size() >= 2, "长文本应切成多个 chunk");
        String prev = chunks.get(0).getContent();
        String expectedTail = prev.substring(prev.length() - 5);
        assertTrue(chunks.get(1).getContent().startsWith(expectedTail),
                "chunk[1] 应以 overlap 尾部开头, expected=" + expectedTail);
    }

    // ========== 语义切片（semantic） ==========

    @Test
    void 语义切片在低相似度处断开() {
        mockChunkLength(1000); // 避免长度上限干扰
        when(strategyMapper.selectById("s1")).thenReturn(
                strategy("{\"chunk\":{\"strategy\":\"semantic\",\"semanticThreshold\":50,\"embeddingModel\":\"embed-1\"}}"));

        // 句子向量：s0/s1 相似（同主题），s2 与它们迥异（主题突变）
        // [1,0] [0.9,0.1]（与 s0 相似）[0,1]（突变）
        when(embeddingService.embed("embed-1", List.of("这是第一句。", "这是第二句。", "这是第三句。"))).thenReturn(
                List.of(
                        List.of(1f, 0f),
                        List.of(0.9f, 0.1f),
                        List.of(0f, 1f)));

        List<ChunkData> chunks = chunkingService.semanticChunk("这是第一句。这是第二句。这是第三句。", "s1", null);

        assertEquals(2, chunks.size(), "s1↔s2 相似（0.98>0.5）同块；s2↔s3 突变（0.1<0.5）断开");
        assertTrue(chunks.get(0).getContent().contains("这是第一句。"));
        assertTrue(chunks.get(0).getContent().contains("这是第二句。"));
        assertTrue(chunks.get(1).getContent().contains("这是第三句。"));
    }

    @Test
    void 语义切片无模型时回退规则分片() {
        mockChunkLength(1000);
        mockOverlap(0);
        // 策略与 KB 均未配置 embeddingModel
        when(strategyMapper.selectById("s1")).thenReturn(strategy("{\"chunk\":{\"strategy\":\"semantic\"}}"));

        List<ChunkData> chunks = chunkingService.semanticChunk("这是第一句。这是第二句。", "s1", null);

        assertFalse(chunks.isEmpty(), "无模型时不应中断，回退规则分片");
        assertEquals(1, chunks.size(), "短文本规则分片为单块");
    }

    @Test
    void 语义切片embedding失败时回退规则分片() {
        mockChunkLength(1000);
        mockOverlap(0);
        when(strategyMapper.selectById("s1")).thenReturn(
                strategy("{\"chunk\":{\"strategy\":\"semantic\",\"embeddingModel\":\"embed-1\"}}"));
        when(embeddingService.embed("embed-1", List.of("这是第一句。", "这是第二句。"))).thenThrow(new RuntimeException("API down"));

        List<ChunkData> chunks = chunkingService.semanticChunk("这是第一句。这是第二句。", "s1", null);

        assertFalse(chunks.isEmpty(), "embedding 异常应回退规则分片而非失败");
    }

    @Test
    void 语义切片kb级模型兜底() {
        mockChunkLength(1000);
        when(strategyMapper.selectById("s1")).thenReturn(
                strategy("{\"chunk\":{\"strategy\":\"semantic\",\"semanticThreshold\":50}}"));
        // 策略级未配置 → 使用 KB 级模型
        when(embeddingService.embed("kb-embed", List.of("这是第一句。", "这是第二句。"))).thenReturn(
                List.of(List.of(1f, 0f), List.of(0f, 1f)));

        List<ChunkData> chunks = chunkingService.semanticChunk("这是第一句。这是第二句。", "s1", "kb-embed");

        assertEquals(2, chunks.size(), "KB 级模型兜底生效，低相似度断开");
    }
}
