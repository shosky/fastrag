package com.fastrag.module.agent.backend;

import com.fastrag.module.agent.context.BaseContext;

import java.util.List;
import java.util.Map;

/**
 * Agent后端接口，定义Agent后端的统一契约。
 *
 * <p>每种Agent后端类型（如ChatbotAgent、SubAgent）都需要实现此接口，
 * 提供各自独有的上下文Schema、能力声明、执行图构建器及元数据信息。</p>
 *
 * <p>核心职责：
 * <ul>
 *   <li>声明后端的唯一标识、显示名称、描述和元数据</li>
 *   <li>定义该后端支持的能力列表（如文件上传、Web搜索等）</li>
 *   <li>指定该后端使用的上下文Schema类型，用于从Agent配置JSON中填充运行时上下文</li>
 *   <li>根据已填充的上下文构建Agent执行图（AgentGraph），包含模型、系统提示词、工具、中间件链和状态Schema</li>
 * </ul></p>
 *
 * <p>典型实现类包括：
 * <ul>
 *   <li>{@link ChatbotAgentBackend} - 主聊天机器人后端，支持文件上传和管理能力</li>
 *   <li>{@link SubAgentBackend} - 子智能体后端，由主智能体调度执行特定子任务</li>
 * </ul></p>
 *
 * @see AgentBackendManager 后端注册与发现管理器
 * @see AgentGraph Agent执行图数据载体
 * @see BaseContext 后端上下文基类
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
