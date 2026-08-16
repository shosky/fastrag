package com.fastrag.ai.model;

/**
 * LLM 对话消息模型，表示 OpenAI Chat Completions API 格式中的一条消息。
 *
 * <p>本类是对话请求和响应中的核心数据结构，被 {@link ChatRequest} 和 {@link ChatResponse} 引用，
 * 同时也是 {@link com.fastrag.ai.llm.LlmService} 各调用方法的主要入参之一。</p>
 *
 * <p>支持多种角色（role）的消息类型：
 * <ul>
 *   <li>{@code user} - 用户消息，携带用户的提问或输入</li>
 *   <li>{@code assistant} - 助手消息，携带 LLM 的回复，可包含 tool_calls</li>
 *   <li>{@code system} - 系统消息，用于设定 LLM 的行为和角色</li>
 *   <li>{@code tool} - 工具执行结果消息，用于将工具调用结果回传给 LLM</li>
 * </ul>
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code role} - 消息角色（user/assistant/system/tool）</li>
 *   <li>{@code content} - 消息文本内容</li>
 *   <li>{@code toolCallId} - 当 role="tool" 时，关联的 tool_call ID</li>
 *   <li>{@code name} - 当 role="tool" 时，工具函数名称</li>
 *   <li>{@code toolCalls} - 当 role="assistant" 时，LLM 请求调用的工具列表</li>
 * </ul>
 *
 * <p>内部类 {@link ToolCall} 表示单条工具调用，包含调用 ID、类型和函数信息；
 * {@link FunctionCall} 表示函数调用详情，包含函数名和 JSON 格式的参数。</p>
 *
 * <p>使用 {@link JsonInclude.Include.NON_NULL} 注解，序列化时自动忽略 null 字段，
 * 减少不必要的网络传输数据量。</p>
 */
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {
    private String role;
    private String content;

    /** role="tool" 时：对应的 tool_call ID */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /** role="tool" 时：工具名称（function name） */
    private String name;

    /** role="assistant" 时：LLM 请求调用的工具列表 */
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    /** 便捷构造：普通消息 */
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    /** 便捷构造：工具结果消息 */
    public ChatMessage(String role, String content, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
    }

    /** LLM 返回的 tool_call 条目 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String type = "function";
        private FunctionCall function;
    }

    /** tool_call 中的 function 信息 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        /** JSON 字符串格式的参数 */
        private String arguments;
    }
}
