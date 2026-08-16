package com.fastrag.module.agent.backend;

import lombok.Data;

import java.util.List;

/**
 * Agent执行图数据载体，描述一次Agent运行所需的完整配置。
 *
 * <p>核心职责：
 * <ul>
 *   <li>承载LLM模型标识（如"gpt-4o"、"claude-3-5-sonnet"）</li>
 *   <li>承载系统提示词（systemPrompt），定义Agent的角色和行为规范</li>
 *   <li>承载Agent可用的工具配置列表（tools）</li>
 *   <li>承载中间件链配置（middlewareChain），用于定义请求/响应的处理管道</li>
 *   <li>承载状态Schema类型（stateSchema），用于持久化运行过程中的状态数据</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>由{@link AgentBackend#buildGraph(BaseContext)}方法根据具体上下文构建</li>
 *   <li>不同的后端实现会设置不同的stateSchema（如ChatbotAgentBackend使用ChatBotState，SubAgentBackend使用BaseState）</li>
 *   <li>构建完成后传递给AgentExecutor进行实际执行</li>
 * </ul></p>
 *
 * @see AgentBackend#buildGraph(BaseContext)
 * @see ChatBotState 聊天机器人状态Schema
 */
@Data
public class AgentGraph {

    /**
     * The LLM model identifier (e.g., "gpt-4o", "claude-3-5-sonnet").
     */
    private String model;

    /**
     * System prompt for the agent.
     */
    private String systemPrompt;

    /**
     * List of tool configurations available to the agent.
     */
    private List<Object> tools;

    /**
     * Middleware chain configuration (e.g., retry, logging, summarization).
     */
    private Object middlewareChain;

    /**
     * The state schema class used for persisting run state.
     */
    private Class<?> stateSchema;
}
