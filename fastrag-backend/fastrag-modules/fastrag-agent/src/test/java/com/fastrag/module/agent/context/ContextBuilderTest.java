package com.fastrag.module.agent.context;

import com.fastrag.module.agent.backend.AgentBackend;
import com.fastrag.module.agent.backend.AgentBackendManager;
import com.fastrag.module.agent.entity.Agent;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.mapper.AgentMapper;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.service.SkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextBuilderTest {

    @Mock
    private AgentMapper agentMapper;
    @Mock
    private SkillService skillService;
    @Mock
    private AgentBackendManager backendManager;
    @Mock
    private AgentBackend backend;

    private ContextBuilder contextBuilder;

    @BeforeEach
    void setUp() {
        contextBuilder = new ContextBuilder(agentMapper, backendManager, skillService);
    }

    @Test
    void testBuildContext_Basic() throws Exception {
        Agent agent = new Agent();
        agent.setId("agent-1");
        agent.setBackendId("ChatbotAgent");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("model", "gpt-4");
        config.put("systemPrompt", "You are helpful");
        config.put("skills", List.of("web-search"));
        config.put("tools", List.of("tool1"));
        agent.setConfigJson(config);
        when(agentMapper.selectById("agent-1")).thenReturn(agent);

        Skill skill = new Skill();
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skill));

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
        assertNotNull(context.getSkills());
        assertTrue(context.getSkills().contains("web-search"));
        assertNotNull(context.getTools());
        assertTrue(context.getTools().contains("tool1"));
        assertEquals("user1", context.getUid());
        assertEquals("run-1", context.getRunId());
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

    @Test
    void testBuildContext_PrepareRuntimeSkills() throws Exception {
        Agent agent = new Agent();
        agent.setId("agent-1");
        agent.setBackendId("ChatbotAgent");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("skills", List.of("skill-a", "skill-b"));
        agent.setConfigJson(config);
        when(agentMapper.selectById("agent-1")).thenReturn(agent);

        Skill skillA = new Skill();
        skillA.setSlug("skill-a");
        skillA.setName("Skill A");
        skillA.setDescription("First skill");
        Skill skillB = new Skill();
        skillB.setSlug("skill-b");
        skillB.setName("Skill B");
        skillB.setDescription("Second skill");

        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skillA, skillB));

        doReturn(ChatBotContext.class).when(backend).getContextSchema();
        when(backendManager.getBackend("ChatbotAgent")).thenReturn(backend);

        AgentRun run = new AgentRun();
        run.setId("run-1");
        run.setAgentId("agent-1");

        BaseContext context = contextBuilder.buildContext(run);

        assertNotNull(context.getPromptSkills());
        assertTrue(context.getPromptSkills().contains("skill-a"));
        assertTrue(context.getPromptSkills().contains("skill-b"));
        assertNotNull(context.getReadableSkills());
        assertNotNull(context.getRuntimeSkillMetadata());
        assertTrue(context.getRuntimeSkillMetadata().containsKey("skill-a"));
        assertNotNull(context.getRuntimeSkillDependencyMap());
    }
}
