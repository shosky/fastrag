package com.fastrag.module.knowledge.chunking;

import com.fastrag.module.knowledge.entity.KbChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * excel_row 切片的图谱构建下游守护测试（REQ-H13，tdd §5.2 S11）。
 *
 * 断言「单个超长分片独占一个抽取单元、不截断」：4800 字符切片在 ~2000 字符单元预算下
 * 独占一个单元且内容完整——excel_row 切片进图谱构建零适配的行为回归钉。
 */
class ExtractionUnitAssemblerOversizeTest {

    private static final Function<String, String> HASH = s -> Integer.toHexString(s.hashCode());

    private static KbChunk chunk(int index, String content) {
        KbChunk c = new KbChunk();
        c.setId("f1_chunk_" + index);
        c.setKbId("kb1");
        c.setFileId("f1");
        c.setChunkIndex(index);
        c.setContent(content);
        return c;
    }

    @Test
    void 超长切片独占抽取单元且不截断() {
        KbChunk slice = chunk(0, "长".repeat(4800));

        List<ExtractionUnitAssembler.ExtractionUnit> units =
                ExtractionUnitAssembler.assemble(List.of(slice), 2000, HASH);

        assertEquals(1, units.size(), "4800 字符切片独占一个抽取单元（超预算不截断）");
        assertEquals(4800, units.get(0).content().length(), "切片内容完整进入抽取单元");
        assertEquals(1, units.get(0).chunks().size());
    }

    @Test
    void 多个超长切片各自独占单元() {
        KbChunk a = chunk(0, "a".repeat(4800));
        KbChunk b = chunk(1, "b".repeat(4800));

        List<ExtractionUnitAssembler.ExtractionUnit> units =
                ExtractionUnitAssembler.assemble(List.of(a, b), 2000, HASH);

        assertEquals(2, units.size(), "两个超长切片各自独占单元，不合并也不截断");
        assertEquals(4800, units.get(0).content().length());
        assertEquals(4800, units.get(1).content().length());
    }
}
