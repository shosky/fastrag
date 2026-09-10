package com.fastrag.module.knowledge.parser;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 行→节点装配的段落粒度回归：中文正文的无标点换行行不得逐行成 HEADING——
 * 否则解析段落粒度退化为行，AI分片 结构块框选会出现"一行一框"。
 * 标题行判定必须带几何/编号证据（isPdfTitleRow）：行高大于页内中位数，或显式编号。
 */
class PdfParagraphAssemblyTest {

    private Object newParser() throws Exception {
        Constructor<?> ctor = DocumentParserImpl.class.getDeclaredConstructors()[0];
        Class<?>[] pts = ctor.getParameterTypes();
        Object[] args = new Object[pts.length];
        for (int i = 0; i < pts.length; i++) args[i] = pts[i].isPrimitive() ? (pts[i] == boolean.class ? false : 0) : null;
        return ctor.newInstance(args);
    }

    @SuppressWarnings("unchecked")
    private List<DocNode> assemble(Object parser, List<DocumentParserImpl.PageLine> rows) throws Exception {
        Method m = DocumentParserImpl.class.getDeclaredMethod("assemblePageNodes", List.class, int.class, boolean.class);
        m.setAccessible(true);
        return (List<DocNode>) m.invoke(parser, rows, 1, true);
    }

    private static DocumentParserImpl.PageLine line(float y, float h, String text) {
        return new DocumentParserImpl.PageLine(y, h, 50f, 400f, text);
    }

    @Test
    void chineseBodyLinesWithoutPunctuation_mergeIntoParagraphs() throws Exception {
        // 12pt 正文连续换行行（行距 18pt → 行盒间隙 6pt，远小于分段阈值），行尾均无标点
        Object parser = newParser();
        List<DocNode> nodes = assemble(parser, List.of(
                line(100, 12f, "本发明公开了一种基于深度学习的图像处理方法"),
                line(118, 12f, "涉及计算机视觉与模式识别技术领域"),
                line(136, 12f, "包括图像预处理特征提取与分类识别等步骤")
        ));
        assertEquals(1, nodes.size(), "正文换行行应合并为一个段落节点");
        assertEquals(DocNode.NodeType.PARAGRAPH, nodes.get(0).getType());
        assertTrue(nodes.get(0).getContent().contains("图像预处理"));
    }

    @Test
    void largerFontLine_becomesHeading_evenWithoutNumbering() throws Exception {
        Object parser = newParser();
        List<DocNode> nodes = assemble(parser, List.of(
                line(80, 20f, "图像处理方法总述"),           // 20pt：明显大于 12pt 正文中位数
                line(110, 12f, "本发明公开了一种基于深度学习的图像处理方法"),
                line(128, 12f, "涉及计算机视觉与模式识别技术领域")
        ));
        // 标题 1 个节点 + 两行正文合并为 1 个段落节点
        assertEquals(2, nodes.size());
        assertEquals(DocNode.NodeType.HEADING, nodes.get(0).getType());
        assertEquals(DocNode.NodeType.PARAGRAPH, nodes.get(1).getType());
        assertTrue(nodes.get(1).getContent().contains("模式识别"));
    }

    @Test
    void numberedLine_becomesHeading_evenAtBodyFontSize() throws Exception {
        Object parser = newParser();
        List<DocNode> nodes = assemble(parser, List.of(
                line(100, 12f, "1.2 图像预处理流程"),
                line(118, 12f, "首先对输入图像进行灰度化与去噪处理，然后进行几何校正。")
        ));
        assertEquals(2, nodes.size());
        assertEquals(DocNode.NodeType.HEADING, nodes.get(0).getType());
        assertEquals(DocNode.NodeType.PARAGRAPH, nodes.get(1).getType());
    }

    @Test
    void decimalLeadingBodyLine_notMistakenAsNumberedHeading() throws Exception {
        // "1.5万元" 是小数开头的正文行，不是 "1.5" 编号标题
        assertFalse(DocumentParserImpl.isPdfTitleRow(line(100, 12f, "1.5万元的补偿款项已发放完毕"), 12f));
        // 无编号、同字号的正文行不判标题（旧逻辑误判的典型样本）
        assertFalse(DocumentParserImpl.isPdfTitleRow(line(100, 12f, "涉及计算机视觉与模式识别技术领域"), 12f));
        // 真标题：编号 / 大字号
        assertTrue(DocumentParserImpl.isPdfTitleRow(line(100, 12f, "1.2 图像预处理流程"), 12f));
        assertTrue(DocumentParserImpl.isPdfTitleRow(line(80, 20f, "图像处理方法总述"), 12f));
    }
}
