package com.fastrag.module.agent.runner;

import com.fastrag.module.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 应用启动后初始化内置 Agent 记录.
 * <p>
 * 监听 {@link ApplicationReadyEvent}，确保在 {@link com.fastrag.config.SchemaInitializer}
 * 建表完成后再执行数据初始化。通过 {@link Order} 控制执行顺序。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentDataInitializer {

    private final AgentService agentService;

    /**
     * 在 ApplicationReadyEvent 阶段初始化内置 Agent.
     * Order 值大于 SchemaInitializer 的默认 Order(0)，
     * 确保表已创建后再插入数据.
     */
    @Order(10)
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("Initializing built-in agents...");

        try {
            // Default chatbot agent
            agentService.ensureBuiltinAgent(
                    "default-chatbot",
                    "ChatbotAgent",
                    "默认助手",
                    "系统内置的默认对话助手，支持知识库检索、工具调用等功能",
                    "global",
                    true,
                    false,
                    buildDefaultChatbotConfig()
            );

            // General purpose agent
            agentService.ensureBuiltinAgent(
                    "general-purpose",
                    "ChatbotAgent",
                    "通用助手",
                    "通用智能助手，可配置多种工具和知识库",
                    "global",
                    false,
                    false,
                    new HashMap<>()
            );

            // Web search agent
            agentService.ensureBuiltinAgent(
                    "web-search",
                    "ChatbotAgent",
                    "网络搜索",
                    "专注于网络搜索和信息检索的智能助手",
                    "global",
                    false,
                    true,
                    buildSubagentConfig("web_search")
            );

            // Deep research agent
            agentService.ensureBuiltinAgent(
                    "deep-research",
                    "ChatbotAgent",
                    "深度研究",
                    "执行深度研究和分析的智能助手，支持多轮调研",
                    "global",
                    false,
                    true,
                    buildSubagentConfig("deep_research")
            );

            // Research explorer agent
            agentService.ensureBuiltinAgent(
                    "research-explorer",
                    "ChatbotAgent",
                    "研究探索",
                    "辅助研究探索的智能助手，帮助整理和归纳研究资料",
                    "global",
                    false,
                    true,
                    buildSubagentConfig("research_explorer")
            );

            // Fact verifier agent
            agentService.ensureBuiltinAgent(
                    "fact-verifier",
                    "ChatbotAgent",
                    "事实核查",
                    "用于事实核查和验证的智能助手",
                    "global",
                    false,
                    true,
                    buildSubagentConfig("fact_verifier")
            );

            log.info("Built-in agent initialization complete.");
        } catch (Exception e) {
            log.error("Failed to initialize built-in agents", e);
        }
    }

    private Map<String, Object> buildDefaultChatbotConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("model", "gpt-4o");
        config.put("systemPrompt", "你是一个智能助手，可以回答用户的问题，帮助用户完成各种任务。");
        config.put("summaryThreshold", 20);
        config.put("modelRetryTimes", 3);
        return config;
    }

    private Map<String, Object> buildSubagentConfig(String type) {
        Map<String, Object> config = new HashMap<>();
        config.put("model", "gpt-4o");
        config.put("systemPrompt", "你是一个子智能体，负责执行特定任务。");
        return config;
    }
}
