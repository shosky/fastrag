package com.fastrag.ai.model;

/**
 * LLM 对话请求模型，封装发送给 OpenAI 兼容 Chat Completions API 的完整请求参数。
 *
 * <p>本类是 {@link com.fastrag.ai.llm.LlmService} 所有对话调用的请求载体，
 * 支持 OpenAI 标准格式，兼容各类大模型网关（如 Ollama、SiliconFlow、DeepSeek 等）。</p>
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code model} - 模型标识，如 "deepseek-chat"、"qwen3-32b" 等</li>
 *   <li>{@code messages} - 对话消息列表，包含完整的多轮对话上下文</li>
 *   <li>{@code temperature} - 采样温度，控制生成随机性，默认 0.7</li>
 *   <li>{@code maxTokens} - 最大生成 token 数，默认 2048</li>
 *   <li>{@code stream} - 是否启用流式输出（SSE）</li>
 *   <li>{@code enableThinking} - 是否启用思考模式（DeepSeek/Qwen3 推理模型支持）</li>
 *   <li>{@code tools} - OpenAI Function Calling 工具定义列表</li>
 *   <li>{@code toolChoice} - 工具选择策略（auto/none/required）</li>
 * </ul>
 *
 * <p>内部类 {@link ToolDefinition} 表示工具定义，包含类型（固定为 "function"）和函数描述；
 * {@link FunctionDef} 描述函数的名称、说明和 JSON Schema 格式的参数定义。</p>
 *
 * <p>使用 {@link JsonInclude.Include.NON_NULL} 注解，序列化时忽略 null 字段，
 * 确保 enableThinking 为 null 时不发送该字段，避免不支持的模型报错。</p>
 */
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
