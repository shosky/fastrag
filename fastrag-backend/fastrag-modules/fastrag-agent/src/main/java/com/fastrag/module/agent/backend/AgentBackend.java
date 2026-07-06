package com.fastrag.module.agent.backend;

import com.fastrag.module.agent.context.BaseContext;

import java.util.List;
import java.util.Map;

/**
 * Interface defining the contract for an agent backend.
 * Each backend type (e.g., ChatbotAgent, SubAgent) provides its own
 * context schema, capabilities, graph builder, and metadata.
 */
public interface AgentBackend {

    /**
     * Unique identifier for this backend type.
     */
    String getId();

    /**
     * Display name for this backend.
     */
    String getName();

    /**
     * Description of this backend.
     */
    String getDescription();

    /**
     * List of capability strings this backend supports
     * (e.g., "file_upload", "files", "web_search").
     */
    List<String> getCapabilities();

    /**
     * Static metadata about this backend.
     */
    Map<String, Object> getMetadata();

    /**
     * Returns the context schema class that this backend uses.
     * The context is populated from the agent's configJson.
     */
    Class<? extends BaseContext> getContextSchema();

    /**
     * Returns backend info as a map suitable for API responses.
     *
     * @param includeConfigurableItems whether to include configurable items derived from the context schema
     * @param userRole                 the role of the requesting user (used to filter configurable items)
     * @return map containing backend_id, name, description, capabilities, metadata, and optionally configurable_items
     */
    Map<String, Object> getInfo(boolean includeConfigurableItems, String userRole);

    /**
     * Builds the agent execution graph from the given context.
     *
     * @param context the fully-populated context for this agent run
     * @return an AgentGraph describing the model, tools, system prompt, middleware, and state schema
     */
    AgentGraph buildGraph(BaseContext context);
}
