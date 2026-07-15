package com.fastrag.ai.model;

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
