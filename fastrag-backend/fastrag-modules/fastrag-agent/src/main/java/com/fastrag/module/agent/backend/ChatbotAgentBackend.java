package com.fastrag.module.agent.backend;

import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.context.ChatBotContext;
import com.fastrag.module.agent.context.ConfigurableItem;
import com.fastrag.module.agent.state.ChatBotState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend implementation for the primary chatbot agent.
 * Uses {@link ChatBotContext} as its context schema and supports
 * file upload and file management capabilities.
 */
@Slf4j
@Component
public class ChatbotAgentBackend implements AgentBackend {

    @Override
    public String getId() {
        return "ChatbotAgent";
    }

    @Override
    public String getName() {
        return "智能助手";
    }

    @Override
    public String getDescription() {
        return "通用智能对话助手，支持知识库检索、工具调用、文件上传等功能";
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("file_upload", "files");
    }

    @Override
    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "1.0.0");
        metadata.put("category", "chatbot");
        return metadata;
    }

    @Override
    public Class<? extends BaseContext> getContextSchema() {
        return ChatBotContext.class;
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
                ChatBotContext schemaInstance = (ChatBotContext) getContextSchema().getDeclaredConstructor().newInstance();
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
        graph.setStateSchema(ChatBotState.class);
        // middlewareChain and advanced graph configuration to be wired later
        return graph;
    }
}
