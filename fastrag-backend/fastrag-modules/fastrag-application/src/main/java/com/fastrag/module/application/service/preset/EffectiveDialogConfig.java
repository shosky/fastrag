package com.fastrag.module.application.service.preset;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 有效对话配置（new-conversation）：DialogPresetResolver 的唯一产出，
 * 编排执行核只读消费、不感知配置来源（design §2.1）。
 *
 * <p>边界（契约不变式）：止步应用域——不含 agent 域 BaseContext/ModelConfig 的替代物，
 * config→(BaseContext, ModelConfig) 薄映射保留在执行核；webSearchEnabled 不在此对象
 * 出现（B02 演进后唯一死字段）；tool/mcp/skill ID 于直聊消费进 Bindings（2026-09-25 用户裁决）。
 */
@Data
@Builder
public class EffectiveDialogConfig {

    /** ACL 过滤后的唯一知识库范围；空 = 不调检索直接生成（REQ-C01） */
    private List<String> kbScope;

    /** 五类绑定中的四类能力 ID（knowledges 并入 kbScope）；直聊为四个空集 */
    private Bindings bindings;

    /** 模型路由结果（五级回退必有一级命中；apiUrl/apiKey 允许 null=走默认网关地址） */
    private ModelRoute modelRoute;

    private String systemPrompt;
    private double temperature;
    private int maxTokens;
    private int memoryRounds;

    /** 执行预算（可空——直聊不设，引擎侧既有 null 容忍） */
    private Integer summaryThreshold;
    private Integer modelRetryTimes;
    private Integer maxSteps;
    private String summaryPrompt;

    /** 直聊恒 true（安全缺省开，REQ-C04）；挂载应用 = isSafetyEnabled 同语义 */
    private boolean safetyEnabled;

    /** 直聊恒 null（问候短路径自然短路） */
    private String greeting;

    /** 溯源一档：APP_PRESET / DIRECT_CHAT + 挂载应用 ID；不含中间解析痕迹（那些归日志） */
    private PresetSource source;
    private String mountedAppId;

    public enum PresetSource { APP_PRESET, DIRECT_CHAT }

    @Data
    @Builder
    public static class ModelRoute {
        private String model;
        private String apiUrl;
        private String apiKey;
        private boolean enableThinking;
    }

    @Data
    @Builder
    public static class Bindings {
        private List<String> tools;
        private List<String> skills;
        private List<String> mcps;
        private List<String> databases;
    }
}
