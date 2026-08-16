package com.fastrag.module.agent.context;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 聊天机器人Agent运行时上下文，继承自{@link BaseContext}，扩展主聊天机器人的专属配置。
 *
 * <p>核心职责：
 * <ul>
 *   <li>在基础上下文之上增加子智能体（subagents）配置，支持主Agent调度子Agent执行特定任务</li>
 *   <li>维护运行时的线程关联关系，包括父线程ID、文件线程ID、技能线程ID</li>
 *   <li>标记当前是否处于子智能体运行模式（subagentRuntime）</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>subagents字段通过@ConfigField(type="list", kind="subagents")注解标记，会被持久化到Agent配置中</li>
 *   <li>parentThreadId用于关联父Agent的会话线程，实现子Agent与父Agent的上下文传递</li>
 *   <li>fileThreadId和skillsThreadId分别用于文件管理和技能执行的独立线程上下文</li>
 *   <li>由{@link com.fastrag.module.agent.backend.ChatbotAgentBackend}作为其上下文Schema使用</li>
 * </ul></p>
 *
 * @see BaseContext 基础上下文
 * @see com.fastrag.module.agent.backend.ChatbotAgentBackend 使用此上下文的后端
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatBotContext extends BaseContext {

    @ConfigField(type = "list", kind = "subagents")
    private List<String> subagents;

    private transient String parentThreadId;

    private transient String fileThreadId;

    private transient String skillsThreadId;

    private transient boolean subagentRuntime;
}
