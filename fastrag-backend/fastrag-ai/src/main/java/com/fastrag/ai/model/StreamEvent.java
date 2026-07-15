package com.fastrag.ai.model;

import lombok.Data;

/**
 * 统一的流式事件类型，区分 thinking / content / tool_call_delta / finish。
 * 由 LlmService.streamChatWithTools() 返回的 Flux 中的每个元素。
 */
@Data
public class StreamEvent {

    public enum Type {
        /** 思考过程增量（reasoning_content 或 think 标签内容） */
        THINKING,
        /** 回答内容增量（delta.content） */
        CONTENT,
        /** 工具调用增量片段（delta.tool_calls） */
        TOOL_CALL_DELTA,
        /** 流结束标记（含 finish_reason） */
        FINISH
    }

    private Type type;
    /** THINKING 或 CONTENT 的文本增量 */
    private String content;
    /** FINISH 的结束原因 */
    private String finishReason;
    /** TOOL_CALL_DELTA 的工具调用片段 */
    private ChatMessage.ToolCall toolCall;

    private StreamEvent() {}

    // ==================== 便捷工厂方法 ====================

    public static StreamEvent thinking(String text) {
        StreamEvent e = new StreamEvent();
        e.setType(Type.THINKING);
        e.setContent(text);
        return e;
    }

    public static StreamEvent content(String text) {
        StreamEvent e = new StreamEvent();
        e.setType(Type.CONTENT);
        e.setContent(text);
        return e;
    }

    public static StreamEvent toolCallDelta(ChatMessage.ToolCall tc) {
        StreamEvent e = new StreamEvent();
        e.setType(Type.TOOL_CALL_DELTA);
        e.setToolCall(tc);
        return e;
    }

    public static StreamEvent finish(String reason) {
        StreamEvent e = new StreamEvent();
        e.setType(Type.FINISH);
        e.setFinishReason(reason);
        return e;
    }
}
