package com.fastrag.ai.model;

/**
 * 结构化的 LLM 响应模型，支持解析 OpenAI 格式的文本回复和工具调用结果。
 *
 * <p>本类是 {@link com.fastrag.ai.llm.LlmService} 非流式调用的统一返回类型，
 * 同时也用于流式调用收集完所有 chunk 后的最终组装结果。</p>
 *
 * <p>核心字段说明：
 * <ul>
 *   <li>{@code content} - LLM 生成的文本回复内容</li>
 *   <li>{@code toolCalls} - LLM 请求调用的工具列表（当 finishReason 为 "tool_calls" 时有值）</li>
 *   <li>{@code finishReason} - 生成结束原因，如 "stop"（正常结束）、"tool_calls"（需要工具调用）、
 *       "length"（达到 token 上限被截断）</li>
 * </ul>
 *
 * <p>提供两种构建方式：
 * <ul>
 *   <li>{@link #parse(JsonNode)} - 从 OpenAI 格式的 JSON 响应中解析，提取 content 和 tool_calls</li>
 *   <li>{@link #text(String)} - 快速构建纯文本响应的便捷工厂方法</li>
 * </ul>
 *
 * <p>可通过 {@link #hasToolCalls()} 判断 LLM 是否发起了工具调用，
 * 调用方据此决定是否需要执行工具并将结果回传。</p>
 */
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构化的 LLM 响应，支持解析 OpenAI 格式的 content + tool_calls。
 */
@Data
public class ChatResponse {
    /** 文本回复内容 */
    private String content;

    /** LLM 请求调用的工具列表（finish_reason = "tool_calls" 时有值） */
    private List<ChatMessage.ToolCall> toolCalls;

    /** 结束原因: "stop" | "tool_calls" | "length" */
    private String finishReason;

    /** 从 OpenAI JSON 响应中解析 */
    public static ChatResponse parse(JsonNode root) {
        ChatResponse resp = new ChatResponse();
        resp.setToolCalls(new ArrayList<>());

        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            resp.setFinishReason(first.path("finish_reason").asText("stop"));

            JsonNode message = first.path("message");
            resp.setContent(message.path("content").asText(""));

            // 解析 tool_calls
            JsonNode toolCallsNode = message.path("tool_calls");
            if (toolCallsNode.isArray()) {
                for (JsonNode tc : toolCallsNode) {
                    ChatMessage.ToolCall toolCall = new ChatMessage.ToolCall();
                    toolCall.setId(tc.path("id").asText());
                    toolCall.setType(tc.path("type").asText("function"));

                    JsonNode fn = tc.path("function");
                    ChatMessage.FunctionCall funcCall = new ChatMessage.FunctionCall();
                    funcCall.setName(fn.path("name").asText());
                    funcCall.setArguments(fn.path("arguments").asText("{}"));
                    toolCall.setFunction(funcCall);

                    resp.getToolCalls().add(toolCall);
                }
            }
        }

        return resp;
    }

    /** 是否包含工具调用 */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public static ChatResponse text(String content) {
        ChatResponse r = new ChatResponse();
        r.setContent(content);
        r.setFinishReason("stop");
        r.setToolCalls(new ArrayList<>());
        return r;
    }
}
