package com.fastrag.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatRequest {
    private String model;
    private List<ChatMessage> messages;
    private double temperature = 0.7;
    @JsonProperty("max_tokens")
    private int maxTokens = 2048;
    private boolean stream = false;
    /** 启用思考模式（DeepSeek / Qwen3 等模型支持） */
    @JsonProperty("enable_thinking")
    private Boolean enableThinking;

    /** OpenAI function calling 工具定义列表 */
    private List<ToolDefinition> tools;

    /** 工具选择策略: auto / none / required */
    @JsonProperty("tool_choice")
    private String toolChoice;

    /** OpenAI function calling 工具定义 */
    @Data
    public static class ToolDefinition {
        private String type = "function";
        private FunctionDef function;
    }

    @Data
    public static class FunctionDef {
        private String name;
        private String description;
        /** JSON Schema 对象 */
        private java.util.Map<String, Object> parameters;
    }
}
