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
 * 主聊天机器人Agent后端实现，是系统默认的智能对话后端。
 *
 * <p>核心职责：
 * <ul>
 *   <li>使用{@link ChatBotContext}作为上下文Schema，支持丰富的配置项（如模型选择、系统提示词、工具开关等）</li>
 *   <li>声明支持file_upload和files能力，即允许用户上传文件和管理文件</li>
 *   <li>构建执行图时使用{@link ChatBotState}作为状态Schema</li>
 *   <li>提供后端信息查询能力，支持按用户角色过滤可配置项</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>后端ID固定为"ChatbotAgent"，显示名称为"智能助手"</li>
 *   <li>buildGraph方法从上下文中提取model、systemPrompt和tools构建AgentGraph</li>
 *   <li>getInfo方法通过反射实例化ChatBotContext来获取可配置项列表，支持按userRole过滤</li>
 *   <li>中间件链配置目前预留扩展接口（middlewareChain待后续接入）</li>
 * </ul></p>
 *
 * @see AgentBackend 后端接口定义
 * @see ChatBotContext 聊天机器人上下文Schema
 * @see ChatBotState 聊天机器人运行状态
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
