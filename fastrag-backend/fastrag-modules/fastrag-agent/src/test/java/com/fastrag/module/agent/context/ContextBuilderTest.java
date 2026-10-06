package com.fastrag.module.agent.context;

import com.fastrag.module.agent.backend.AgentBackend;
import com.fastrag.module.agent.backend.AgentBackendManager;
import com.fastrag.module.agent.entity.Agent;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.mapper.AgentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ContextBuilder 单元测试（M2-4 装配单点后：只负责配置装载与运行时标识，
 * 技能装配唯一归属 SkillsMiddleware——此处断言不触碰技能装配产物）。
 */
@ExtendWith(MockitoExtension.class)
class ContextBuilderTest {

    @Mock
    private AgentMapper agentMapper;
    @Mock
    private AgentBackendManager backendManager;
    @Mock
    private AgentBackend backend;

    private ContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        contextBuilder = new ContextBuilder(agentMapper, backendManager);
    }

    @Test
    void testBuildContext_Basic() throws Exception {
        Agent agent = new Agent();
        agent.setId("agent-1");
        agent.setBackendId("ChatbotAgent");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("model", "gpt-4");
        config.put("systemPrompt", "You are helpful");
        config.put("skills", List.of("1001"));
        config.put("tools", List.of("tool1"));
        agent.setConfigJson(config);
        when(agentMapper.selectById("agent-1")).thenReturn(agent);

        doReturn(ChatBotContext.class).when(backend).getContextSchema();
        when(backendManager.getBackend("ChatbotAgent")).thenReturn(backend);

        AgentRun run = new AgentRun();
        run.setId("run-1");
        run.setAgentId("agent-1");
        run.setUid("user1");

        BaseContext context = contextBuilder.buildContext(run);

        assertNotNull(context);
        assertEquals("gpt-4", context.getModel());
        assertEquals("You are helpful", context.getSystemPrompt());
        // M2-4：绑定原样保留（技能主键 id），装配延迟到 SkillsMiddleware
        assertNotNull(context.getSkills());
        assertTrue(context.getSkills().contains("1001"));
        assertTrue(context.getTools().contains("tool1"));
        assertEquals("user1", context.getUid());
        assertEquals("run-1", context.getRunId());
        // 装配单点：ContextBuilder 不再触碰技能运行时产物
        assertNull(context.getPromptSkills());
        assertNull(context.getRuntimeSkillMetadata());
    }

    @Test
    void testBuildContext_NoSkills() throws Exception {
        Agent agent = new Agent();
        agent.setId("agent-1");
        agent.setBackendId("ChatbotAgent");
        agent.setConfigJson(Map.of("model", "gpt-4"));
        when(agentMapper.selectById("agent-1")).thenReturn(agent);

        doReturn(ChatBotContext.class).when(backend).getContextSchema();
        when(backendManager.getBackend("ChatbotAgent")).thenReturn(backend);

        AgentRun run = new AgentRun();
        run.setId("run-1");
        run.setAgentId("agent-1");

        BaseContext context = contextBuilder.buildContext(run);

        assertNotNull(context);
        assertNull(context.getSkills());
    }

    @Test
    void testBuildContext_AgentNotFound() {
        when(agentMapper.selectById("nonexistent")).thenReturn(null);

        AgentRun run = new AgentRun();
        run.setId("run-1");
        run.setAgentId("nonexistent");

        assertThrows(RuntimeException.class, () -> contextBuilder.buildContext(run));
    }
}
