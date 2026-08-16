package com.fastrag.ai.model;

/**
 * 流式对话的单个 chunk 数据模型，对应 SSE（Server-Sent Events）流中的一个增量数据片段。
 *
 * <p>本类是 {@link com.fastrag.ai.llm.LlmService} 流式调用返回的 {@code Flux<ChatChunk>} 中的元素，
 * 表示 LLM 在流式生成过程中的一次增量推送。</p>
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code content} - 本次增量推送的文本内容片段</li>
 *   <li>{@code finishReason} - 流结束原因，如 "stop"（正常结束）、"tool_calls"（触发工具调用）、
 *       "length"（达到 max_tokens 上限），为 null 表示流仍在进行中</li>
 *   <li>{@code toolCalls} - 增量式的 tool_calls 信息（流式时逐步累积）</li>
 * </ul>
 *
 * <p>提供了便捷工厂方法 {@link #content(String)} 和 {@link #finish(String)} 用于快速创建特定类型的 chunk。</p>
 */
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 流式对话的单个 chunk，对应 SSE 中的一个 delta。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatChunk {
    /** 文本增量内容 */
    private String content;

    /** 结束原因: "stop" | "tool_calls" | "length" | null(进行中) */
    private String finishReason;

    /** 增量 tool_calls（流式时逐步累积） */
    private List<ChatMessage.ToolCall> toolCalls;

    public static ChatChunk content(String text) {
        ChatChunk c = new ChatChunk();
        c.content = text;
        return c;
    }

    public static ChatChunk finish(String reason) {
        ChatChunk c = new ChatChunk();
        c.finishReason = reason;
        return c;
    }
}
