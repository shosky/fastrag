package com.fastrag.ai.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 非流式 LLM 调用熔断器状态机测试（2026-10-21：ai.gateway.url 指向未启动的本地
 * Ollama，检索增强等旁路调用每请求反复撞死网关）。半开用例用 50ms 冷却 + sleep，
 * 覆盖「探测失败立即重开 / 探测成功归零」。
 */
class LlmGatewayBreakerTest {

    private static final String KEY = "http://127.0.0.1:11434|default";

    @Test
    void failuresBelowThreshold_doNotOpen() {
        LlmGatewayBreaker breaker = new LlmGatewayBreaker(2, 60_000L);

        breaker.recordFailure(KEY);
        assertFalse(breaker.isOpen(KEY), "连续失败未达阈值不熔断");
    }

    @Test
    void consecutiveFailuresAtThreshold_open() {
        LlmGatewayBreaker breaker = new LlmGatewayBreaker(2, 60_000L);

        breaker.recordFailure(KEY);
        breaker.recordFailure(KEY);

        assertTrue(breaker.isOpen(KEY), "连续失败达阈值应打开熔断");
    }

    @Test
    void success_resetsFailureCount() {
        LlmGatewayBreaker breaker = new LlmGatewayBreaker(2, 60_000L);

        breaker.recordFailure(KEY);
        breaker.recordSuccess(KEY);
        breaker.recordFailure(KEY);

        assertFalse(breaker.isOpen(KEY), "成功归零后单次失败不应熔断");
    }

    @Test
    void cooldownExpiry_halfOpen_probeFailure_reopensImmediately() throws InterruptedException {
        LlmGatewayBreaker breaker = new LlmGatewayBreaker(2, 50L);

        breaker.recordFailure(KEY);
        breaker.recordFailure(KEY);
        assertTrue(breaker.isOpen(KEY));

        Thread.sleep(80); // 冷却到期
        assertFalse(breaker.isOpen(KEY), "冷却到期应半开放行");

        breaker.recordFailure(KEY);
        assertTrue(breaker.isOpen(KEY), "半开探测失败应立即重开（计数不清零）");
    }

    @Test
    void cooldownExpiry_halfOpen_probeSuccess_resets() throws InterruptedException {
        LlmGatewayBreaker breaker = new LlmGatewayBreaker(2, 50L);

        breaker.recordFailure(KEY);
        breaker.recordFailure(KEY);
        assertTrue(breaker.isOpen(KEY));

        Thread.sleep(80); // 冷却到期
        assertFalse(breaker.isOpen(KEY), "冷却到期应半开放行");
        breaker.recordSuccess(KEY);
        breaker.recordFailure(KEY);

        assertFalse(breaker.isOpen(KEY), "探测成功归零后回到正常计数");
    }

    @Test
    void keysAreIsolated() {
        LlmGatewayBreaker breaker = new LlmGatewayBreaker(1, 60_000L);

        breaker.recordFailure(KEY);

        assertTrue(breaker.isOpen(KEY));
        assertFalse(breaker.isOpen("http://api.siliconflow.cn|Qwen/Qwen3-8B"), "不同网关/模型互不影响");
    }
}
