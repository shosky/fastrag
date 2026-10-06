package com.fastrag.module.agent.context;

import com.fastrag.module.agent.backend.AgentBackend;
import com.fastrag.module.agent.backend.AgentBackendManager;
import com.fastrag.module.agent.entity.Agent;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.mapper.AgentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent运行时上下文构建器，负责从Agent配置和运行时数据组装完整的{@link BaseContext}实例。
 *
 * <p>核心职责：
 * <ul>
 *   <li>根据AgentRun信息加载对应的Agent持久化配置</li>
 *   <li>通过{@link AgentBackendManager}获取对应的后端实现，反射创建正确的上下文实例</li>
 *   <li>将Agent的configJson中的静态配置批量填充到上下文对象中</li>
 *   <li>设置运行时标识字段（runId、uid）</li>
 * </ul></p>
 *
 * <p>关键实现逻辑：
 * <ul>
 *   <li>buildContext方法按步骤执行：加载Agent配置 -> 获取后端 -> 创建上下文实例 -> 填充配置 -> 设置运行时字段</li>
 *   <li>技能运行时装配（元数据/依赖闭包/沙箱物化/prompt 注入）唯一归属
 *       {@link com.fastrag.module.agent.middleware.SkillsMiddleware}（M2-4 装配单点），
 *       本类不再做任何技能装配</li>
 * </ul></p>
 *
 * <p>与其他模块的交互：
 * <ul>
 *   <li>依赖AgentMapper查询Agent持久化配置</li>
 *   <li>依赖AgentBackendManager获取后端实现</li>
 * </ul></p>
 *
 * @see BaseContext 构建的目标上下文对象
 * @see com.fastrag.module.agent.executor.AgentEngine 上下文的使用者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private final AgentMapper agentMapper;
    private final AgentBackendManager backendManager;

    /**
     * 构建 Agent 运行时上下文
     */
    public BaseContext buildContext(AgentRun run) throws Exception {
        // 1. 加载 Agent 配置
        Agent agent = agentMapper.selectById(run.getAgentId());
        if (agent == null) {
            throw new RuntimeException("Agent not found: " + run.getAgentId());
        }

        // 2. 获取 AgentBackend
        AgentBackend backend = backendManager.getBackend(agent.getBackendId());
        if (backend == null) {
            throw new RuntimeException("Backend not found: " + agent.getBackendId());
        }

        // 3. 创建上下文实例
        BaseContext context = backend.getContextSchema().getDeclaredConstructor().newInstance();

        // 4. 从 agent configJson 加载配置
        Map<String, Object> config = agent.getConfigJson();
        if (config != null) {
            context.updateFromMap(config);
        }

        // 5. 设置运行时字段
        context.setRunId(run.getId());
        context.setUid(run.getUid());

        log.info("[ContextBuilder] Context built: runId={}, skills={}, tools={}, mcps={}",
            run.getId(), context.getSkills(), context.getTools(), context.getMcps());

        return context;
    }
}
