package com.fastrag.module.knowledge.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内容锚定行分配算法测试（assignLineRanges）：
 * 覆盖正常对齐、跨页合并段（行序列后缀/前缀）、继承标题不占行、前段漂移自愈四个关键场景。
 */
class PdfRectAssignmentTest {

    private static List<String> lines(String... ls) {
        return List.of(ls);
    }

    @Test
    void normalSequenceMatchesExactly() {
        List<String> paras = List.of("第一段第一行\n第一段第二行", "第二段只有一行");
        List<String> lineTexts = lines("第一段第一行", "第一段第二行", "第二段只有一行");
        int[][] r = AiChunkServiceImpl.assignLineRanges(paras, lineTexts);
        assertArrayEquals(new int[]{0, 2}, r[0]);
        assertArrayEquals(new int[]{2, 3}, r[1]);
    }

    @Test
    void crossPageParagraphMatchesSuffixThenPrefix() {
        // 第 15 页：合并段的本页部分是行序列的后 2 行（前 1 行在上一页）
        List<String> paras = List.of("上一页的最后一行\n本页续写第一行\n本页续写第二行", "下一段落");
        List<String> page15 = lines("页眉文字忽略", "本页续写第一行", "本页续写第二行", "下一段落");
        int[][] r15 = AiChunkServiceImpl.assignLineRanges(paras, page15);
        assertArrayEquals(new int[]{1, 3}, r15[0], "跨页段应从行序列中段锚定（跳过上一页的行）");
        assertArrayEquals(new int[]{3, 4}, r15[1]);

        // 第 16 页：同一合并段文本不变，但本页只有它的前 1 行 + 后续段落
        List<String> page16 = lines("上一页的最后一行", "新页第一段");
        List<String> paras16 = List.of("上一页的最后一行\n本页续写第一行\n本页续写第二行", "新页第一段");
        int[][] r16 = AiChunkServiceImpl.assignLineRanges(paras16, page16);
        assertArrayEquals(new int[]{0, 1}, r16[0], "跨页段在下一页只消费本页出现的行");
        assertArrayEquals(new int[]{1, 2}, r16[1]);
    }

    @Test
    void unanchoredHeadingDoesNotConsumeLines() {
        // 继承标题：文本不在页面行中 → 无盒且不挤占后续段落
        List<String> paras = List.of("继承的章节标题", "正文第一行", "正文第二行");
        List<String> lineTexts = lines("正文第一行", "正文第二行");
        int[][] r = AiChunkServiceImpl.assignLineRanges(paras, lineTexts);
        assertArrayEquals(new int[]{-1, -1}, r[0]);
        assertArrayEquals(new int[]{0, 1}, r[1]);
        assertArrayEquals(new int[]{1, 2}, r[2]);
    }

    @Test
    void driftSelfCorrectsViaContentAnchor() {
        // 真实漂移形态：段落文本只含页面行的子集（解析时空白/换行差异丢了一行）
        // → 旧的行数顺序猜测会把后续段落整体顶错位；内容锚定应各自对准
        List<String> paras = List.of("第一段第一行", "第二段第一行\n第二段第二行");
        List<String> lineTexts = lines("第一段第一行", "第一段第二行", "第二段第一行", "第二段第二行");
        int[][] r = AiChunkServiceImpl.assignLineRanges(paras, lineTexts);
        assertArrayEquals(new int[]{0, 1}, r[0], "第一段只锚定自己的一行");
        assertArrayEquals(new int[]{2, 4}, r[1], "第二段从内容处重新锚定，不受前面多出的行影响");
    }

    @Test
    void fuzzyToleranceForWhitespaceAndShortLines() {
        List<String> paras = List.of("产品 概述 与 定位\n面向 AI 时代的知识库");
        List<String> lineTexts = lines("产品  概述 与  定位", "面向 AI 时代的知识库（含注解后缀）");
        int[][] r = AiChunkServiceImpl.assignLineRanges(paras, lineTexts);
        assertArrayEquals(new int[]{0, 2}, r[0], "空白折叠后应相等；长行允许子串包含");
    }

    @Test
    void shortLinesDoNotFalselyAnchor() {
        // 单字符/极短行不允许匹配，避免误锚
        assertFalse(AiChunkServiceImpl.lineMatches("1", "1"));
        assertFalse(AiChunkServiceImpl.lineMatches("", ""));
        // 长度不足的子串包含不匹配
        assertFalse(AiChunkServiceImpl.lineMatches("abcdef", "abc"));
        assertTrue(AiChunkServiceImpl.lineMatches("abcdef", "abcdef"));
        assertTrue(AiChunkServiceImpl.lineMatches("前缀abcdef后缀", "abcdef"));
    }
}
