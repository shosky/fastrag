package com.fastrag.ai.model;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    private String model;
    private List<ChatMessage> messages;
    private double temperature = 0.7;
    private int maxTokens = 2048;
    private boolean stream = false;
}
