package com.fastrag.module.agent.executor;

import lombok.Builder;
import lombok.Data;

/**
 * 模型运行时配置。
 * 从 ModelRecord 表加载或由 AppServiceImpl 手动构建，
 * 传递给 AgentEngine 用于 LLM 调用。
 */
@Data
@Builder
public class ModelConfig {
    /** 模型标识（如 "deepseek-chat"） */
    private String model;
    /** API 基础 URL（如 "http://localhost:11434"） */
    private String apiUrl;
    /** API 密钥 */
    private String apiKey;
    /** 温度（默认 0.7） */
    @Builder.Default
    private double temperature = 0.7;
    /** 是否启用思考模式（DeepSeek/Qwen3 等） */
    @Builder.Default
    private boolean enableThinking = false;
    /** 最大 token 数 */
    @Builder.Default
    private int maxTokens = 2048;

    /** 使用默认配置的快捷工厂 */
    public static ModelConfig default_(String model) {
        return ModelConfig.builder()
                .model(model)
                .temperature(0.7)
                .enableThinking(false)
                .maxTokens(2048)
                .build();
    }
}
