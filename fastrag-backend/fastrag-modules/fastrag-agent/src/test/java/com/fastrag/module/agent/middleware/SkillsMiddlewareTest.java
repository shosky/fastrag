package com.fastrag.module.agent.middleware;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.tools.entity.Skill;
import com.fastrag.module.tools.registry.ToolDefinition;
import com.fastrag.module.tools.service.SkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillsMiddlewareTest {

    @Mock
    private SkillService skillService;

    private SkillsMiddleware middleware;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        middleware = new SkillsMiddleware(skillService, objectMapper);
    }

    @Test
    void testBeforeModelCall_NoSkills() {
        BaseContext context = new BaseContext();
        context.setSkills(null);
        List<ChatMessage> messages = new ArrayList<>();

        BaseContext result = middleware.beforeModelCall(context, messages, new ArrayList<>());

        assertNull(result.getPromptSkills());
    }

    @Test
    void testBeforeModelCall_WithSkills() {
        Skill skill = new Skill();
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        skill.setContent("Search skill content");
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("web-search"));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "You are a helpful assistant."));

        BaseContext result = middleware.beforeModelCall(context, messages, new ArrayList<>());

        // 验证 promptSkills 被填充
        assertNotNull(result.getPromptSkills());
        assertTrue(result.getPromptSkills().contains("web-search"));

        // 验证 readableSkills 被填充
        assertNotNull(result.getReadableSkills());
        assertTrue(result.getReadableSkills().contains("web-search"));

        // 验证 metadata 被填充
        assertNotNull(result.getRuntimeSkillMetadata());
        assertTrue(result.getRuntimeSkillMetadata().containsKey("web-search"));

        // 验证 system prompt 被注入
        assertTrue(messages.get(0).getContent().contains("联网搜索"));
        assertTrue(messages.get(0).getContent().contains("/home/gem/skills/"));
    }

    @Test
    void testBeforeModelCall_NoSystemMessage() {
        Skill skill = new Skill();
        skill.setSlug("test");
        skill.setName("Test");
        skill.setDescription("Test desc");
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("test"));
        List<ChatMessage> messages = new ArrayList<>();

        middleware.beforeModelCall(context, messages, new ArrayList<>());

        // 应该添加一条 system message
        assertFalse(messages.isEmpty());
        assertEquals("system", messages.get(0).getRole());
        assertTrue(messages.get(0).getContent().contains("Test"));
    }

    @Test
    void testAfterModelCall_DetectSkillActivation() throws JsonProcessingException {
        Skill skill = new Skill();
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search");
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("web-search"));

        // 先执行 before 填充上下文
        List<ChatMessage> messages = new ArrayList<>();
        context = middleware.beforeModelCall(context, messages, new ArrayList<>());

        // 模拟 LLM 返回 read_file 的 tool_call
        ChatResponse response = new ChatResponse();
        ChatMessage.ToolCall tc = new ChatMessage.ToolCall();
        tc.setId("call_1");
        ChatMessage.FunctionCall fc = new ChatMessage.FunctionCall();
        fc.setName("read_file");
        fc.setArguments("{\"file_path\": \"/home/gem/skills/web-search/SKILL.md\"}");
        tc.setFunction(fc);
        response.setToolCalls(List.of(tc));
        response.setContent("");

        context = middleware.afterModelCall(context, response);

        // 验证上下文没有被破坏
        assertNotNull(context);
        assertNotNull(context.getReadableSkills());
    }

    @Test
    void testAfterModelCall_NoToolCalls() {
        BaseContext context = new BaseContext();
        ChatResponse response = new ChatResponse();
        response.setToolCalls(null);
        response.setContent("Final answer");

        BaseContext result = middleware.afterModelCall(context, response);

        assertNotNull(result);
    }

    @Test
    void testExpandSkillClosure() {
        Skill parent = new Skill();
        parent.setSlug("parent");
        parent.setDependencies(List.of("skill:child1", "skill:child2"));

        Skill child1 = new Skill();
        child1.setSlug("child1");
        child1.setDependencies(List.of("skill:grandchild"));

        Skill child2 = new Skill();
        child2.setSlug("child2");

        Skill grandchild = new Skill();
        grandchild.setSlug("grandchild");

        var skillMap = Map.of(
            "parent", parent,
            "child1", child1,
            "child2", child2,
            "grandchild", grandchild
        );

        BaseContext context = new BaseContext();
        context.setSkills(List.of("parent"));

        // 通过 beforeModelCall 间接测试
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(new ArrayList<>(skillMap.values()));

        List<ChatMessage> messages = new ArrayList<>();
        middleware.beforeModelCall(context, messages, new ArrayList<>());

        assertNotNull(context.getPromptSkills());
        // 应该包含 parent + child1 + child2 + grandchild
        assertTrue(context.getPromptSkills().contains("parent"));
        assertTrue(context.getPromptSkills().contains("child1"));
        assertTrue(context.getPromptSkills().contains("child2"));
        assertTrue(context.getPromptSkills().contains("grandchild"));
    }

    @Test
    void testBeforeModelCall_EmptySkillsList() {
        BaseContext context = new BaseContext();
        context.setSkills(List.of());

        List<ChatMessage> messages = new ArrayList<>();
        BaseContext result = middleware.beforeModelCall(context, messages, new ArrayList<>());

        // 空技能列表应返回原始上下文
        assertNull(result.getPromptSkills());
    }

    @Test
    void testBuildSkillsPrompt_FormatsCorrectly() {
        Skill skill = new Skill();
        skill.setSlug("test");
        skill.setName("Test Skill");
        skill.setDescription("A test skill");
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("test"));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "Base prompt."));

        middleware.beforeModelCall(context, messages, new ArrayList<>());

        String sysContent = messages.get(0).getContent();
        assertTrue(sysContent.contains("Test Skill"));
        assertTrue(sysContent.contains("test"));
        assertTrue(sysContent.contains("A test skill"));
        assertTrue(sysContent.contains("/home/gem/skills/test/SKILL.md"));
    }
}
