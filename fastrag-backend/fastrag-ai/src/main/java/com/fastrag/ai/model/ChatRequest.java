package com.fastrag.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
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
}
