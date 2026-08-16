package com.fastrag.ai.model;

/**
 * 统一的流式事件模型，用于区分和封装 LLM 流式生成过程中的不同事件类型。
 *
 * <p>本类是 {@link com.fastrag.ai.llm.LlmService#streamChatWithTools} 方法返回的
 * {@code Flux<StreamEvent>} 中的元素，提供比 {@link ChatChunk} 更细粒度的事件分类，
 * 支持思考过程提取、工具调用增量推送等高级场景。</p>
 *
 * <p>支持四种事件类型（{@link Type}）：
 * <ul>
 *   <li>{@code THINKING} - 思考过程增量，来源于 DeepSeek 格式的 reasoning_content 字段
 *       或 Qwen3 格式的 &lt;think&gt; 标签内容</li>
 *   <li>{@code CONTENT} - 正式回答内容增量，对应 SSE delta 中的 content 字段</li>
 *   <li>{@code TOOL_CALL_DELTA} - 工具调用增量片段，对应 delta 中的 tool_calls</li>
 *   <li>{@code FINISH} - 流结束标记，携带 finish_reason（stop/tool_calls/length）</li>
 * </ul>
 *
 * <p>核心字段根据事件类型动态使用：
 * <ul>
 *   <li>{@code type} - 事件类型枚举</li>
 *   <li>{@code content} - THINKING 或 CONTENT 事件的文本增量</li>
 *   <li>{@code finishReason} - FINISH 事件的结束原因</li>
 *   <li>{@code toolCall} - TOOL_CALL_DELTA 事件的工具调用片段</li>
 * </ul>
 *
 * <p>提供便捷工厂方法 {@link #thinking(String)}、{@link #content(String)}、
 * {@link #toolCallDelta(ChatMessage.ToolCall)}、{@link #finish(String)} 用于快速创建各类型事件。</p>
 */
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
