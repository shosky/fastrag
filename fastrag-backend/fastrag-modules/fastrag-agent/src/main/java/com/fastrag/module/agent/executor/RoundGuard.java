package com.fastrag.module.agent.executor;

import com.fastrag.ai.model.ChatResponse;

/**
 * I5（P1-3，finding gap-6）：单轮 LLM 响应防卡死守卫（每 run 一个实例，双执行路径共用）。
 *
 * <p>判定纯函数化：输入单轮 {@link ChatResponse}，输出动作。重试类动作携带需注入的
 * system 消息文本，由引擎（AgentEngine 流式/同步循环）执行「丢弃工具调用/注入/continue」。</p>
 *
 * <ul>
 *   <li><b>length 截断</b>：finish_reason=length 且含未完成 toolCall → 丢弃该调用并注入
 *       「输出被截断」提示重试；连续 3 次 length 终止本轮工具循环（输出兜底文案）。
 *       出现健康轮即重置计数。</li>
 *   <li><b>空响应 nudge</b>：无工具调用且 content 为空 → 注入提示重试，≤2 次。</li>
 *   <li><b>复读检测</b>：连续 2 轮 content 逐字相同且无工具调用 → 注入「检测到重复输出」推进；
 *       限 1 次重试，仍复读则放行结束（有界，不死循环）。</li>
 * </ul>
 *
 * <p>上下文溢出压缩重试本期不做（登记）。</p>
 */
final class RoundGuard {

    /** 守卫裁决。重试类动作带 systemMessage（非 null），由引擎注入后重试本轮。 */
    enum Action {
        /** 正常：含工具调用且非截断，进入工具执行。 */
        EXECUTE_TOOLS(null),
        /** length 截断且工具调用不完整：丢弃调用、注入提示重试。 */
        RETRY_LENGTH("上一轮输出因达到长度上限被截断，工具调用不完整已丢弃。"
                + "请缩减单轮输出长度，或把任务拆分为多次工具调用。"),
        /** 空响应：注入提示重试（≤2 次）。 */
        RETRY_EMPTY("上一轮响应内容为空。请基于已有信息直接回答用户问题，"
                + "或调用工具获取所需数据。"),
        /** 复读：连续两轮内容逐字相同，注入推进。 */
        RETRY_REPEAT("检测到重复输出：本轮内容与上一轮逐字相同。"
                + "请给出与之前不同、能推进任务的回答；如已完成请直接总结收尾。"),
        /** 结束本轮循环（守卫判定不再推进）。 */
        FINISH(null);

        final String systemMessage;

        Action(String systemMessage) {
            this.systemMessage = systemMessage;
        }
    }

    private static final int MAX_LENGTH_STREAK = 3;
    private static final int MAX_EMPTY_NUDGES = 2;

    private int lengthStreak;
    private int emptyNudges;
    private boolean repetitionRetried;
    private String lastContent;

    /**
     * 对单轮响应做防卡死裁决。
     */
    Action evaluate(ChatResponse response) {
        if (response == null) {
            return Action.FINISH;
        }
        String content = response.getContent() == null ? "" : response.getContent();
        boolean truncated = "length".equals(response.getFinishReason());

        if (response.hasToolCalls()) {
            if (truncated) {
                lengthStreak++;
                return lengthStreak >= MAX_LENGTH_STREAK ? Action.FINISH : Action.RETRY_LENGTH;
            }
            lengthStreak = 0;
            if (!content.isEmpty()) {
                lastContent = content;
            }
            return Action.EXECUTE_TOOLS;
        }

        // 无工具调用
        lengthStreak = 0;
        if (content.isEmpty()) {
            if (emptyNudges < MAX_EMPTY_NUDGES) {
                emptyNudges++;
                return Action.RETRY_EMPTY;
            }
            return Action.FINISH;
        }
        if (content.equals(lastContent) && !repetitionRetried) {
            repetitionRetried = true;
            return Action.RETRY_REPEAT;
        }
        return Action.FINISH;
    }
}
