package com.fastrag.module.agent.executor;

import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I5（P1-3，finding gap-6）：单轮 LLM 响应防卡死守卫。
 *
 * <p>三类卡死形态与对策：
 * <ul>
 *   <li>finish_reason=length 且含未完成 toolCall → 丢弃该调用、注入提示重试；连续 3 次 length 终止</li>
 *   <li>空响应（无工具调用且 content 空）→ 注入提示重试，≤2 次</li>
 *   <li>复读（连续 2 轮 content 逐字相同且无工具调用）→ 注入「检测到重复输出」推进（限 1 次）</li>
 * </ul></p>
 *
 * <p>Trace: [Test] → 单元 I/I5 → RoundGuard → AgentEngine 流式/同步双循环</p>
 */
class RoundGuardTest {

    private ChatResponse resp(String content, String finishReason, boolean withToolCalls) {
        ChatResponse r = new ChatResponse();
        r.setContent(content);
        r.setFinishReason(finishReason);
        r.setToolCalls(withToolCalls
                ? List.of(new ChatMessage.ToolCall("c1", "function",
                        new ChatMessage.FunctionCall("t", "{}")))
                : List.of());
        return r;
    }

    @Test
    void normalToolCallRound_proceedsToExecuteTools() {
        RoundGuard guard = new RoundGuard();
        assertThat(guard.evaluate(resp("", "tool_calls", true)))
                .isEqualTo(RoundGuard.Action.EXECUTE_TOOLS);
    }

    @Test
    void normalFinalAnswer_finishes() {
        RoundGuard guard = new RoundGuard();
        assertThat(guard.evaluate(resp("answer", "stop", false)))
                .isEqualTo(RoundGuard.Action.FINISH);
    }

    @Test
    void lengthWithIncompleteToolCalls_retriesWithInjectableMessage() {
        RoundGuard guard = new RoundGuard();
        RoundGuard.Action first = guard.evaluate(resp("", "length", true));
        RoundGuard.Action second = guard.evaluate(resp("", "length", true));

        assertThat(first).isEqualTo(RoundGuard.Action.RETRY_LENGTH);
        assertThat(first.systemMessage).contains("截断");
        assertThat(second).isEqualTo(RoundGuard.Action.RETRY_LENGTH);
    }

    @Test
    void threeConsecutiveLengthRounds_terminate() {
        RoundGuard guard = new RoundGuard();
        assertThat(guard.evaluate(resp("", "length", true))).isEqualTo(RoundGuard.Action.RETRY_LENGTH);
        assertThat(guard.evaluate(resp("", "length", true))).isEqualTo(RoundGuard.Action.RETRY_LENGTH);
        assertThat(guard.evaluate(resp("", "length", true))).isEqualTo(RoundGuard.Action.FINISH);
    }

    @Test
    void lengthStreak_resetsAfterHealthyRound() {
        RoundGuard guard = new RoundGuard();
        guard.evaluate(resp("", "length", true));
        guard.evaluate(resp("", "tool_calls", true)); // 健康轮：正常执行工具
        // 之后再连续 3 次 length 才终止
        assertThat(guard.evaluate(resp("", "length", true))).isEqualTo(RoundGuard.Action.RETRY_LENGTH);
        assertThat(guard.evaluate(resp("", "length", true))).isEqualTo(RoundGuard.Action.RETRY_LENGTH);
        assertThat(guard.evaluate(resp("", "length", true))).isEqualTo(RoundGuard.Action.FINISH);
    }

    @Test
    void emptyResponse_nudgesAtMostTwiceThenFinishes() {
        RoundGuard guard = new RoundGuard();
        RoundGuard.Action first = guard.evaluate(resp("", "stop", false));
        RoundGuard.Action second = guard.evaluate(resp("", "stop", false));
        RoundGuard.Action third = guard.evaluate(resp("", "stop", false));

        assertThat(first).isEqualTo(RoundGuard.Action.RETRY_EMPTY);
        assertThat(first.systemMessage).isNotBlank();
        assertThat(second).isEqualTo(RoundGuard.Action.RETRY_EMPTY);
        assertThat(third).isEqualTo(RoundGuard.Action.FINISH);
    }

    @Test
    void repeatedContent_detectedWhenPreviousRoundProducedSameContent() {
        RoundGuard guard = new RoundGuard();
        // 第 1 轮：content + 工具调用（正常执行，content 进入比较窗）
        assertThat(guard.evaluate(resp("same text", "tool_calls", true)))
                .isEqualTo(RoundGuard.Action.EXECUTE_TOOLS);
        // 第 2 轮：content 逐字相同且无工具调用 → 复读，注入推进
        RoundGuard.Action repeat = guard.evaluate(resp("same text", "stop", false));
        assertThat(repeat).isEqualTo(RoundGuard.Action.RETRY_REPEAT);
        assertThat(repeat.systemMessage).contains("重复输出");
        // 复读重试后若仍复读 → 放行结束（有界，不死循环）
        assertThat(guard.evaluate(resp("same text", "stop", false))).isEqualTo(RoundGuard.Action.FINISH);
    }

    @Test
    void differentContent_finishesNormally() {
        RoundGuard guard = new RoundGuard();
        guard.evaluate(resp("first draft", "tool_calls", true));
        assertThat(guard.evaluate(resp("final answer", "stop", false)))
                .isEqualTo(RoundGuard.Action.FINISH);
    }

    @Test
    void nullFinishReason_treatedAsHealthy() {
        RoundGuard guard = new RoundGuard();
        assertThat(guard.evaluate(resp("", null, true)))
                .isEqualTo(RoundGuard.Action.EXECUTE_TOOLS);
    }
}
