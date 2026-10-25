package com.fastrag.ai.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 非流式 LLM 调用熔断器（按 apiUrl|model 维度）。
 *
 * <p>背景（2026-10-21 排查）：ai.gateway.url 指向未启动的本地 Ollama 时，检索增强
 * （MultiQuery 改写）、会话标题、摘要等旁路调用每次请求都要撞一遍死网关，除延迟与
 * ERROR 堆栈噪音外没有任何收益。本熔断器在连续失败达阈值后进入打开态，冷却窗口内
 * 直接 fail-fast——调用方拿到与网关宕机同构的失败文案（{@link LlmService#CALL_FAILURE_PREFIX}
 * 前缀），既有 {@link LlmService#isFailureText} 降级链路零改动。</p>
 *
 * <p>仅用于非流式旁路调用（doChat 路径）；主对话流式链路（streamChatWithTools）
 * 不经过本熔断器，网关故障仍由流式路径的既有错误事件语义上报。</p>
 *
 * <p>半开语义：冷却到期放行探测时不清零失败计数——探测再失败立即重新打开（单次探测
 * 即重开），探测成功由 {@link #recordSuccess} 归零。打开态转换只告警一次
 * （{@code openLogged} 仅在成功恢复时复位），避免长时间宕机期间每冷却周期刷屏。</p>
 */
class LlmGatewayBreaker {

    private static final Logger log = LoggerFactory.getLogger(LlmGatewayBreaker.class);

    private final int failureThreshold;
    private final long cooldownMs;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    private static final class State {
        final java.util.concurrent.atomic.AtomicInteger consecutiveFailures =
                new java.util.concurrent.atomic.AtomicInteger();
        volatile long openUntil;
        volatile boolean openLogged;
    }

    LlmGatewayBreaker(int failureThreshold, long cooldownMs) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownMs = Math.max(0, cooldownMs);
    }

    /**
     * 熔断打开（含冷却窗口内）返回 true，调用方应跳过网络调用直接降级；
     * 冷却到期则就地转入半开并放行本次调用。
     */
    boolean isOpen(String key) {
        State st = states.get(key);
        if (st == null) return false;
        long until = st.openUntil;
        if (until == 0) return false;
        if (System.currentTimeMillis() < until) return true;
        // 冷却到期：半开放行探测；失败计数保留（探测失败一次即重开），成功由 recordSuccess 归零
        st.openUntil = 0;
        return false;
    }

    /** 调用成功（网关可达）时归零计数并复位告警标记 */
    void recordSuccess(String key) {
        State st = states.get(key);
        if (st == null) return;
        st.consecutiveFailures.set(0);
        st.openUntil = 0;
        st.openLogged = false;
    }

    /** 调用失败时累计计数；达到阈值即打开熔断并告警一次 */
    void recordFailure(String key) {
        State st = states.computeIfAbsent(key, k -> new State());
        if (st.consecutiveFailures.incrementAndGet() < failureThreshold) return;
        st.openUntil = System.currentTimeMillis() + cooldownMs;
        if (!st.openLogged) {
            st.openLogged = true;
            log.warn("[LLM] 非流式调用熔断打开: key={}, 连续失败 >= {} 次, {}s 内 fail-fast 降级"
                            + "（返回失败文案,消费方按 isFailureText 既有链路降级）",
                    key, failureThreshold, cooldownMs / 1000);
        }
    }
}
