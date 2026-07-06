package com.fastrag.module.agent.backend;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.context.ConfigurableItem;
import com.fastrag.module.agent.state.BaseState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend implementation for sub-agents.
 * Sub-agents use the base context (no additional fields beyond the shared context).
 * They are typically spawned by a parent chatbot agent to perform specialized tasks.
 */
@Slf4j
@Component
public class SubAgentBackend implements AgentBackend {

    @Override
    public String getId() {
        return "SubAgentBackend";
    }

    @Override
    public String getName() {
        return "子智能体";
    }

    @Override
    public String getDescription() {
        return "由主智能体调度的子智能体，用于执行特定子任务";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of();
    }

    @Override
    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        metadata.put("category", "subagent");
        return metadata;
    }

    @Override
    public Class<? extends BaseContext> getContextSchema() {
        return BaseContext.class;
    }

    @Override
    public Map<String, Object> getInfo(boolean includeConfigurableItems, String userRole) {
        Map<String, Object> info = new HashMap<>();
        info.put("backend_id", getId());
        info.put("name", getName());
        info.put("description", getDescription());
        info.put("capabilities", getCapabilities());
        info.put("metadata", getMetadata());

        if (includeConfigurableItems) {
            try {
                BaseContext schemaInstance = getContextSchema().getDeclaredConstructor().newInstance();
                List<ConfigurableItem> items = schemaInstance.getConfigurableItems(
                        userRole != null ? userRole : "user");
                info.put("configurable_items", items);
            } catch (Exception e) {
                log.warn("Failed to instantiate context schema for configurable items", e);
                info.put("configurable_items", new ArrayList<>());
            }
        }

        return info;
    }

    @Override
    public AgentGraph buildGraph(BaseContext context) {
        AgentGraph graph = new AgentGraph();
        graph.setModel(context.getModel());
        graph.setSystemPrompt(context.getSystemPrompt());
        graph.setTools(context.getTools() != null
                ? context.getTools().stream().map(t -> (Object) t).toList()
                : List.of());
        graph.setStateSchema(BaseState.class);
        return graph;
    }
}
