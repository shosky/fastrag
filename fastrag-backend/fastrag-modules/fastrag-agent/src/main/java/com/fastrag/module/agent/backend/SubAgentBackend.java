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
 * 子智能体Agent后端实现，由主聊天机器人Agent调度执行特定子任务。
 *
 * <p>核心职责：
 * <ul>
 *   <li>使用{@link BaseContext}作为上下文Schema，不包含额外的配置字段（与主Agent共享基础上下文）</li>
 *   <li>不支持任何额外的能力（capabilities为空列表），功能由父Agent的工具配置决定</li>
 *   <li>构建执行图时使用{@link BaseState}作为状态Schema</li>
 *   <li>提供与ChatbotAgentBackend一致的后端信息查询能力</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>后端ID固定为"SubAgentBackend"，显示名称为"子智能体"</li>
 *   <li>通常由主Agent通过SubAgentToolExecutor在运行时动态创建和调用</li>
 *   <li>子Agent的systemPrompt、model、tools等由父Agent在创建时动态指定</li>
 *   <li>buildGraph方法从上下文中提取配置构建AgentGraph，stateSchema使用BaseState</li>
 * </ul></p>
 *
 * @see AgentBackend 后端接口定义
 * @see SubAgentToolExecutor 子Agent工具执行器
 * @see BaseState 子Agent运行状态基类
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
