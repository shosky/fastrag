package com.fastrag.ai.layout;

import com.fastrag.ai.layout.LayoutAnalysisService.LayoutBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** VLM 版面块 JSON 稳健解析测试（parseBlocks） */
class LayoutAnalysisServiceTest {

    @Test
    void normalJsonInThousandScale() {
        String content = """
                [{"type":"title","bbox":[100,50,900,120],"text":"第一章 产品概述"},
                 {"type":"table","bbox":[80,200,800,500],"text":"表1 参数"},
                 {"type":"image","bbox":[100,600,600,900],"text":"架构图"},
                 {"type":"formula","bbox":[200,950,700,1050],"text":"E=mc^2"}]""";
        List<LayoutBlock> blocks = LayoutAnalysisService.parseBlocks(content);
        assertEquals(4, blocks.size());
        LayoutBlock title = blocks.get(0);
        assertEquals("title", title.type());
        assertEquals(0.10, title.x(), 1e-9);
        assertEquals(0.05, title.y(), 1e-9);
        assertEquals(0.80, title.width(), 1e-9);
        assertEquals(0.07, title.height(), 1e-9);
    }

    @Test
    void handlesContentWrappedInCodeBlock() {
        String content = "好的，输出如下：\n```json\n[{\"type\":\"text\",\"bbox\":[0,0,1000,1000],\"text\":\"全页\"}]\n```";
        List<LayoutBlock> blocks = LayoutAnalysisService.parseBlocks(content);
        assertEquals(1, blocks.size());
        assertEquals("text", blocks.get(0).type());
        assertEquals(1.0, blocks.get(0).width(), 1e-9);
    }

    @Test
    void autoDetectsZeroToOneFloatScale() {
        String content = "[{\"type\":\"image\",\"bbox\":[0.1,0.2,0.4,0.5],\"text\":\"图\"}]";
        List<LayoutBlock> blocks = LayoutAnalysisService.parseBlocks(content);
        assertEquals(1, blocks.size());
        assertEquals(0.1, blocks.get(0).x(), 1e-9);
        assertEquals(0.3, blocks.get(0).width(), 1e-9);
        assertEquals(0.3, blocks.get(0).height(), 1e-9);
    }

    @Test
    void invalidTypeNormalizesAndDegenerateBoxDropped() {
        String content = "[{\"type\":\"unknown\",\"bbox\":[100,100,200,200],\"text\":\"x\"},"
                + "{\"type\":\"text\",\"bbox\":[100,100,101,101],\"text\":\"退化\"}]";
        List<LayoutBlock> blocks = LayoutAnalysisService.parseBlocks(content);
        // unknown→text 保留；退化块（宽高<0.2%）剔除
        assertEquals(1, blocks.size());
        assertEquals("text", blocks.get(0).type());
    }

    @Test
    void clampsOutOfRangeX() {
        String content = "[{\"type\":\"text\",\"bbox\":[-5,20,1100,800],\"text\":\"越界\"}]";
        List<LayoutBlock> blocks = LayoutAnalysisService.parseBlocks(content);
        assertEquals(1, blocks.size());
        LayoutBlock b = blocks.get(0);
        assertEquals(0.0, b.x(), 1e-9);
        assertEquals(0.02, b.y(), 1e-9);
        assertEquals(1.0, b.width(), 1e-9);
        assertEquals(0.78, b.height(), 1e-9);
    }

    @Test
    void fullyOutOfRangeBoxIsDroppedAsDegenerate() {
        // Y 区间整体超出 [0,1] 后塌成零高 → 退化过滤剔除
        String content = "[{\"type\":\"text\",\"bbox\":[100,2000,300,2400],\"text\":\"越界\"}]";
        assertTrue(LayoutAnalysisService.parseBlocks(content).isEmpty());
    }

    @Test
    void invalidContentReturnsEmpty() {
        assertTrue(LayoutAnalysisService.parseBlocks("").isEmpty());
        assertTrue(LayoutAnalysisService.parseBlocks("抱歉，无法识别。").isEmpty());
        assertTrue(LayoutAnalysisService.parseBlocks(null).isEmpty());
        assertTrue(LayoutAnalysisService.parseBlocks("[{\"type\":\"text\"}]").isEmpty());
        assertTrue(LayoutAnalysisService.parseBlocks("not json").isEmpty());
    }
}
