package com.fastrag.module.knowledge.chunking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 父分片聚合器单元测试。
 *
 * 覆盖：连续同 headingPath 聚合、单分片组不聚合、超长组二次切分、
 * 不同章节分别聚合、无标题上下文（null headingPath）分组、空/单分片输入。
 */
class ParentChunkAssemblerTest {

    private ParentChunkAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ParentChunkAssembler();
    }

    // ========== 测试数据构造 ==========

    private static ChunkData child(int index, String content, String headingPath) {
        return ChunkData.builder()
                .id("chunk_" + index)
                .index(index)
                .content(content)
                .headingPath(headingPath)
                .title(headingPath != null ? headingPath : null)
                .chunkType("text")
                .build();
    }

    // ========== 用例 ==========

    @Test
    void 连续同标题分片聚合为一个父分片() {
        List<ChunkData> chunks = List.of(
                child(0, "第一节内容A", "第一章 > 1.1"),
                child(1, "第一节内容B", "第一章 > 1.1"),
                child(2, "第一节内容C", "第一章 > 1.1"));

        var result = assembler.assemble(chunks, "file1", 4000);

        assertEquals(1, result.parents().size());
        ChunkData parent = result.parents().get(0);
        assertEquals("file1_parent_0", parent.getId());
        assertEquals("parent", parent.getChunkType());
        assertEquals("第一章 > 1.1", parent.getHeadingPath());
        assertEquals(0, parent.getIndex());   // 取首个子分片位置
        assertEquals("第一节内容A\n\n第一节内容B\n\n第一节内容C", parent.getContent());

        // 子分片 parentId 回填
        assertEquals("file1_parent_0", chunks.get(0).getParentId());
        assertEquals("file1_parent_0", chunks.get(1).getParentId());
        assertEquals("file1_parent_0", chunks.get(2).getParentId());
    }

    @Test
    void 单分片组不生成父分片() {
        List<ChunkData> chunks = List.of(child(0, "独立段落", "第一章"));

        var result = assembler.assemble(chunks, "file1", 4000);

        assertTrue(result.parents().isEmpty());
        assertNull(chunks.get(0).getParentId());
    }

    @Test
    void 超长组按子分片边界二次切分为多个父分片() {
        // 每个子分片 60 字符；maxParentLength=130：两个子分片(120)不超，三个(180)超 → 切为 2 组
        List<ChunkData> chunks = List.of(
                child(0, "第一章内容".repeat(12), "第一章"),
                child(1, "第一章内容".repeat(12), "第一章"),
                child(2, "第一章内容".repeat(12), "第一章"),
                child(3, "第一章内容".repeat(12), "第一章"));

        var result = assembler.assemble(chunks, "file1", 130);

        assertEquals(2, result.parents().size());
        assertEquals("file1_parent_0", result.parents().get(0).getId());
        assertEquals("file1_parent_1", result.parents().get(1).getId());
        // 同一章节的多个父分片共享标题上下文
        assertEquals("第一章", result.parents().get(0).getHeadingPath());
        assertEquals("第一章", result.parents().get(1).getHeadingPath());
        // 子分片各自归属正确父分片
        assertEquals("file1_parent_0", chunks.get(0).getParentId());
        assertEquals("file1_parent_0", chunks.get(1).getParentId());
        assertEquals("file1_parent_1", chunks.get(2).getParentId());
        assertEquals("file1_parent_1", chunks.get(3).getParentId());
    }

    @Test
    void 不同章节分别聚合为独立父分片() {
        List<ChunkData> chunks = List.of(
                child(0, "第一章A", "第一章"),
                child(1, "第一章B", "第一章"),
                child(2, "第二章A", "第二章"),
                child(3, "第二章B", "第二章"));

        var result = assembler.assemble(chunks, "file1", 4000);

        assertEquals(2, result.parents().size());
        assertEquals("第一章", result.parents().get(0).getHeadingPath());
        assertEquals("第二章", result.parents().get(1).getHeadingPath());
    }

    @Test
    void 无标题上下文分片归为同一组() {
        List<ChunkData> chunks = List.of(
                child(0, "前言A", null),
                child(1, "前言B", null));

        var result = assembler.assemble(chunks, "file1", 4000);

        assertEquals(1, result.parents().size());
        assertNull(result.parents().get(0).getHeadingPath());
        assertEquals("file1_parent_0", chunks.get(0).getParentId());
    }

    @Test
    void 空输入与单分片输入不聚合() {
        var empty = assembler.assemble(List.of(), "file1", 4000);
        assertTrue(empty.parents().isEmpty());
        assertTrue(empty.children().isEmpty());

        var single = assembler.assemble(List.of(child(0, "仅一分片", "第一章")), "file1", 4000);
        assertTrue(single.parents().isEmpty());
        assertEquals(1, single.children().size());
    }

    @Test
    void 图片分片参与所属章节聚合() {
        ChunkData img = ChunkData.builder()
                .id("chunk_1")
                .index(1)
                .content("OCR 图片文本")
                .headingPath("第三章")
                .chunkType("image")
                .build();
        List<ChunkData> chunks = List.of(child(0, "第三章A", "第三章"), img, child(2, "第三章C", "第三章"));

        var result = assembler.assemble(chunks, "file1", 4000);

        assertEquals(1, result.parents().size());
        assertTrue(result.parents().get(0).getContent().contains("OCR 图片文本"));
        assertEquals("file1_parent_0", img.getParentId());
    }
}
