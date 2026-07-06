package com.fastrag.module.agent.backend;

import lombok.Data;

import java.util.List;

/**
 * Simple data holder representing an agent execution graph.
 * Describes the model, system prompt, tools, middleware chain, and state schema
 * to be used when executing an agent run.
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
