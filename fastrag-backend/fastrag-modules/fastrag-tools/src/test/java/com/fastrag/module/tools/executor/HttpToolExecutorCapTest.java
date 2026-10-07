package com.fastrag.module.tools.executor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I3（P1-1，finding gap-5）：执行器层输出预算——HTTP 响应体上限 2MB，
 * rune 安全截断（不拆散 emoji 等增补码点）。
 *
 * <p>Trace: [Test] → 单元 I/I3 → HttpToolExecutor.capOutput → 工具输出预算分层</p>
 */
class HttpToolExecutorCapTest {

    @Test
    void capOutput_withinLimit_returnsOriginal() {
        String body = "{\"ok\":true}";
        assertThat(HttpToolExecutor.capOutput(body)).isSameAs(body);
    }

    @Test
    void capOutput_overLimit_truncatesAt2MCharsWithMarker() {
        String big = "x".repeat(3 * 1024 * 1024);

        String out = HttpToolExecutor.capOutput(big);

        assertThat(out.length()).isLessThan(2 * 1024 * 1024 + 128);
        assertThat(out).startsWith("xxx");
        assertThat(out).contains("truncated");
    }

    @Test
    void capOutput_boundaryInsideSurrogatePair_neverProducesBrokenChar() {
        String emoji = "😀";
        String big = emoji.repeat(1_500_000); // 3M chars，全为代理对

        String out = HttpToolExecutor.capOutput(big);

        // 2M 边界落在代理对中间时整体码点丢弃，不产孤立代理项
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (Character.isHighSurrogate(c)) {
                assertThat(i + 1 < out.length() && Character.isLowSurrogate(out.charAt(i + 1)))
                        .as("orphan high surrogate at %d", i).isTrue();
                i++;
            } else {
                assertThat(Character.isLowSurrogate(c)).as("orphan low surrogate at %d", i).isFalse();
            }
        }
    }

    @Test
    void capOutput_nullOrEmpty_safe() {
        assertThat(HttpToolExecutor.capOutput(null)).isEmpty();
        assertThat(HttpToolExecutor.capOutput("")).isEmpty();
    }
}
