package com.fastrag.ai.model;

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
