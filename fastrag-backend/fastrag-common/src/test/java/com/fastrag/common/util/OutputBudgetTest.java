package com.fastrag.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I1（P0-1，finding gap-1）：输出预算工具——rune（code point）安全截断。
 *
 * <p>背景：AgentEngine.truncate / SandboxExecutionService.truncate 原先直接
 * {@code substring(0, maxLen)}，在 emoji 等增补码点（surrogate pair）中间切割会产出
 * 孤立代理项，破坏后续 JSON 序列化与 LLM 上下文。</p>
 *
 * <p>Trace: [Test] → 单元 I/I1 → OutputBudget → AgentEngine.truncate + SandboxExecutionService.truncate</p>
 */
class OutputBudgetTest {

    @Test
    void truncate_nullInput_returnsEmptyString() {
        assertThat(OutputBudget.truncate(null, 10, "...(truncated)")).isEmpty();
    }

    @Test
    void truncate_withinBudget_returnsOriginal() {
        String text = "hello 世界";
        assertThat(OutputBudget.truncate(text, 100, "...(truncated)")).isSameAs(text);
    }

    @Test
    void truncate_exactlyAtBudget_returnsOriginal() {
        String text = "abcdefghij";
        assertThat(OutputBudget.truncate(text, 10, "...(truncated)")).isSameAs(text);
    }

    @Test
    void truncate_overBudget_asciiCutsAtBudgetAndAppendsMarker() {
        String text = "a".repeat(30);
        String result = OutputBudget.truncate(text, 10, "...(truncated)");

        assertThat(result).isEqualTo("a".repeat(10) + "...(truncated)");
        assertThat(result).hasSize(10 + "...(truncated)".length());
    }

    @Test
    void truncate_boundaryInsideSurrogatePair_neverProducesBrokenChar() {
        // 10 个 ASCII + 1 个增补码点（😀 = 2 个 char）+ 10 个 ASCII
        String emoji = "😀";
        String text = "a".repeat(10) + emoji + "b".repeat(10);
        // 边界恰好落在 😀 的高位代理（index 10）上
        String result = OutputBudget.truncate(text, 10, "|");
        assertThat(result).isEqualTo("a".repeat(10) + "|");

        // 任意预算下输出都不含孤立代理项
        for (int budget = 0; budget <= text.length() + 2; budget++) {
            String out = OutputBudget.truncate(text, budget, "!");
            assertThat(hasOrphanSurrogate(out))
                    .as("budget=%d output=%s", budget, out)
                    .isFalse();
        }
    }

    @Test
    void truncate_cjkText_cutsByCharsWithoutBrokenChar() {
        String text = "一二三四五六七八九十";
        String result = OutputBudget.truncate(text, 5, "…");
        assertThat(result).isEqualTo("一二三四五…");
    }

    @Test
    void truncate_zeroBudget_returnsMarkerOnly() {
        assertThat(OutputBudget.truncate("abc", 0, "|")).isEqualTo("|");
    }

    // ===== I3（P1-1）：头尾保留截断——错误信息常在尾部，中间整体省略 =====

    @Test
    void truncateMiddle_withinBudget_returnsOriginal() {
        String text = "short output";
        assertThat(OutputBudget.truncateMiddle(text, 100)).isSameAs(text);
    }

    @Test
    void truncateMiddle_overBudget_keepsHeadAndTailWithOmissionMarker() {
        String text = "H".repeat(1000) + "M".repeat(5000) + "T".repeat(1000);

        String out = OutputBudget.truncateMiddle(text, 2000);

        assertThat(out).startsWith("HHH");
        assertThat(out).endsWith("TTT");
        assertThat(out).contains("省略");
        // 预算控制：结果长度不超过 head(70%) + tail(30%) + 标记
        assertThat(out.length()).isLessThan(2000 + 64);
    }

    @Test
    void truncateMiddle_surrogateBoundaries_neverProduceBrokenChar() {
        String emoji = "😀";
        String text = emoji + "a".repeat(5000) + emoji;

        for (int budget : new int[]{1, 2, 3, 10, 100, 5001}) {
            String out = OutputBudget.truncateMiddle(text, budget);
            assertThat(hasOrphanSurrogate(out))
                    .as("budget=%d outputLen=%d", budget, out.length())
                    .isFalse();
        }
        // 极小预算：只出省略标记 + 尾部，不产坏字符
        String out2 = OutputBudget.truncateMiddle("😀" + "a".repeat(100), 2);
        assertThat(out2).contains("省略");
        assertThat(hasOrphanSurrogate(out2)).isFalse();
    }

    /** 检查字符串是否含未配对的代理项（高位代理后无低位代理，或孤立的低位代理）。 */
    private static boolean hasOrphanSurrogate(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    return true;
                }
                i++; // 跳过配对的低位代理
            } else if (Character.isLowSurrogate(c)) {
                return true;
            }
        }
        return false;
    }
}
