package com.fastrag.ai.model;

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
