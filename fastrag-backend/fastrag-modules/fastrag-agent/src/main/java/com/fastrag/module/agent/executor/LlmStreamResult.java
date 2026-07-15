package com.fastrag.module.agent.executor;

import com.fastrag.ai.model.ChatResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * LLM 流式消费结果。
 * 由 AgentEngine.consumeStream() 返回，包含 LLM 累积响应和完整回答文本。
 */
@Data
@AllArgsConstructor
public class LlmStreamResult {
    /** LLM 累积的结构化响应（content + toolCalls + finishReason） */
    private ChatResponse response;
    /** 是否存在未闭合的 thinking 标签（流结束后回退提取） */
    private boolean hasThinkingTags;
}
