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
        skill.setId("1001");
        skill.setEnabled(1);
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        skill.setContent("Search skill content");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1001")); // 绑定 = 技能主键 id（M2-2）
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
        assertTrue(messages.get(0).getContent().contains("/workspace/skills/"));
    }

    @Test
    void testBeforeModelCall_NoSystemMessage() {
        Skill skill = new Skill();
        skill.setId("1004");
        skill.setEnabled(1);
        skill.setSlug("test");
        skill.setName("Test");
        skill.setDescription("Test desc");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1004"));
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
        skill.setId("1001");
        skill.setEnabled(1);
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1001"));

        // 先执行 before 填充上下文
        List<ChatMessage> messages = new ArrayList<>();
        context = middleware.beforeModelCall(context, messages, new ArrayList<>());

        // 模拟 LLM 返回 read_file 的 tool_call
        ChatResponse response = new ChatResponse();
        ChatMessage.ToolCall tc = new ChatMessage.ToolCall();
        tc.setId("call_1");
        ChatMessage.FunctionCall fc = new ChatMessage.FunctionCall();
        fc.setName("read_file");
        fc.setArguments("{\"file_path\": \"/workspace/skills/web-search/SKILL.md\"}");
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
        parent.setId("p1");
        parent.setEnabled(1);
        parent.setSlug("parent");
        parent.setDependencies(List.of("skill:child1", "skill:child2"));

        Skill child1 = new Skill();
        child1.setId("c1");
        child1.setEnabled(1);
        child1.setSlug("child1");
        child1.setDependencies(List.of("skill:grandchild"));

        Skill child2 = new Skill();
        child2.setId("c2");
        child2.setEnabled(1);
        child2.setSlug("child2");

        Skill grandchild = new Skill();
        grandchild.setId("g1");
        grandchild.setEnabled(1);
        grandchild.setSlug("grandchild");

        var skillMap = Map.of(
            "parent", parent,
            "child1", child1,
            "child2", child2,
            "grandchild", grandchild
        );

        BaseContext context = new BaseContext();
        context.setSkills(List.of("p1")); // 绑定 = 主键 id

        // 通过 beforeModelCall 间接测试
        when(skillService.listByIds(any())).thenReturn(List.of(parent));
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
        skill.setId("1005");
        skill.setEnabled(1);
        skill.setSlug("test");
        skill.setName("Test Skill");
        skill.setDescription("A test skill");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1005"));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "Base prompt."));

        middleware.beforeModelCall(context, messages, new ArrayList<>());

        String sysContent = messages.get(0).getContent();
        assertTrue(sysContent.contains("Test Skill"));
        assertTrue(sysContent.contains("test"));
        assertTrue(sysContent.contains("A test skill"));
        assertTrue(sysContent.contains("/workspace/skills/test/SKILL.md"));
    }

    // ===== M2-1（D6）：拦截 SKILL.md 读取必须返回全文（含正文指令），不得只回 name+description =====

    @Test
    void testInterceptToolCall_ReturnsFullSkillContent() {
        Skill skill = new Skill();
        skill.setId("1001");
        skill.setEnabled(1);
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        skill.setContent("## 操作步骤\n\n先调用 search 工具检索，再总结答案。");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1001"));
        List<ChatMessage> messages = new ArrayList<>();
        middleware.beforeModelCall(context, messages, new ArrayList<>());

        ChatMessage.ToolCall tc = new ChatMessage.ToolCall();
        tc.setId("call_9");
        ChatMessage.FunctionCall fc = new ChatMessage.FunctionCall();
        fc.setName("read_file");
        fc.setArguments("{\"file_path\": \"/workspace/skills/web-search/SKILL.md\"}");
        tc.setFunction(fc);

        ToolCallInterceptor interceptor = middleware.interceptToolCall(context, tc);

        assertNotNull(interceptor);
        assertTrue(interceptor.isSkip());
        String content = interceptor.getResultMessage().getContent();
        assertTrue(content.startsWith("# 联网搜索"), "应以技能名标题开头: " + content);
        assertTrue(content.contains("Search the web"), "应含描述: " + content);
        assertTrue(content.contains("---"), "应含分隔线: " + content);
        assertTrue(content.contains("先调用 search 工具检索"), "正文指令必须到达模型（D6 短路缺陷修复）: " + content);
    }

    @Test
    void testInterceptToolCall_UnknownSlugReturnsErrorContent() {
        Skill skill = new Skill();
        skill.setId("1001");
        skill.setEnabled(1);
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any()))
            .thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1001"));
        List<ChatMessage> messages = new ArrayList<>();
        middleware.beforeModelCall(context, messages, new ArrayList<>());

        ChatMessage.ToolCall tc = new ChatMessage.ToolCall();
        tc.setId("call_10");
        ChatMessage.FunctionCall fc = new ChatMessage.FunctionCall();
        fc.setName("read_file");
        fc.setArguments("{\"file_path\": \"/workspace/skills/no-such/SKILL.md\"}");
        tc.setFunction(fc);

        ToolCallInterceptor interceptor = middleware.interceptToolCall(context, tc);

        // 不可读技能不拦截（readableSkills 未含该 slug），交由默认文件读取逻辑
        assertNull(interceptor);
    }

    // ===== M2-2（D1，裁决 2A）：context.skills 语义 = 技能主键 id，绑定必须按 id 解析 =====

    @Test
    void testBeforeModelCall_BindingResolvedByIdNotSlug() {
        // id=数字型主键，slug 完全不同——旧行为把绑定当 slug 过滤，id≠slug 的绑定被静默丢弃
        Skill skill = new Skill();
        skill.setId("1001");
        skill.setEnabled(1);
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        skill.setEnabled(1);
        when(skillService.listByIds(List.of("1001"))).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1001"));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "You are helpful."));

        BaseContext result = middleware.beforeModelCall(context, messages, new ArrayList<>());

        assertNotNull(result.getPromptSkills(), "id 绑定应解析出技能进入闭包");
        assertTrue(result.getPromptSkills().contains("web-search"), "id 绑定应解析出 slug: " + result.getPromptSkills());
        assertTrue(result.getReadableSkills().contains("web-search"));
        assertTrue(messages.get(0).getContent().contains("联网搜索"));
    }

    @Test
    void testBeforeModelCall_InvisibleBindingDroppedWithWarn() {
        // 绑定技能存在且启用，但当前用户不可见（不在 listAccessible 结果）→ 丢弃 + warn（含 id）
        Skill bound = new Skill();
        bound.setId("1002");
        bound.setSlug("private-skill");
        bound.setName("Private");
        bound.setEnabled(1);
        when(skillService.listByIds(any())).thenReturn(List.of(bound));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of());

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SkillsMiddleware.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
            new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            BaseContext context = new BaseContext();
            context.setSkills(List.of("1002"));
            List<ChatMessage> messages = new ArrayList<>();
            BaseContext result = middleware.beforeModelCall(context, messages, new ArrayList<>());

            assertTrue(result.getPromptSkills() == null || !result.getPromptSkills().contains("private-skill"),
                "不可见绑定不应进入闭包");
            boolean warned = appender.list.stream()
                .anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                    && e.getFormattedMessage().contains("1002"));
            assertTrue(warned, "被丢绑定应有含 id 的 warn 日志");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void testBeforeModelCall_DisabledBindingSkipped() {
        // 绑定技能被禁用 → 不进入闭包，不注入 prompt
        Skill disabled = new Skill();
        disabled.setId("1003");
        disabled.setSlug("off-skill");
        disabled.setName("Off");
        disabled.setEnabled(0);
        when(skillService.listByIds(any())).thenReturn(List.of(disabled));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of());

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1003"));
        List<ChatMessage> messages = new ArrayList<>();
        BaseContext result = middleware.beforeModelCall(context, messages, new ArrayList<>());

        assertTrue(result.getPromptSkills() == null || !result.getPromptSkills().contains("off-skill"),
            "禁用绑定不应进入闭包");
    }

    // ===== M2-3（D7）：技能激活追加工具依赖后必须置 toolsDirty，供引擎循环内重算 =====

    @Test
    void testInterceptToolCall_SetsToolsDirtyWhenToolDepsAdded() {
        Skill skill = new Skill();
        skill.setId("1006");
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        skill.setEnabled(1);
        skill.setDependencies(List.of("tool:web_search"));
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1006"));
        List<ChatMessage> messages = new ArrayList<>();
        middleware.beforeModelCall(context, messages, new ArrayList<>());
        assertFalse(context.isToolsDirty(), "装配阶段未追加工具，不应置脏");

        ChatMessage.ToolCall tc = new ChatMessage.ToolCall();
        tc.setId("call_11");
        ChatMessage.FunctionCall fc = new ChatMessage.FunctionCall();
        fc.setName("read_file");
        fc.setArguments("{\"file_path\": \"/workspace/skills/web-search/SKILL.md\"}");
        tc.setFunction(fc);

        middleware.interceptToolCall(context, tc);

        assertTrue(context.getTools().contains("web_search"), "激活应追加工具依赖（M3-2 起规范化为裸工具 id）");
        assertTrue(context.isToolsDirty(), "激活追加工具后应置 toolsDirty（D7）");
    }

    // ===== M2-5（D13）：run 级缓存——首轮装配后，后续轮次零 DB 调用、沙箱不重复同步 =====

    @Test
    void testBeforeModelCall_SecondRoundSkipsDbLoads() {
        Skill skill = new Skill();
        skill.setId("1001");
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        skill.setEnabled(1);
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1001"));
        List<ChatMessage> messages = new ArrayList<>();
        middleware.beforeModelCall(context, messages, new ArrayList<>());
        middleware.beforeModelCall(context, messages, new ArrayList<>());
        middleware.beforeModelCall(context, messages, new ArrayList<>());

        verify(skillService, times(1)).listByIds(any());
        verify(skillService, times(1)).listAccessible(any(), any(), any());
    }

    @Test
    void testBeforeModelCall_SecondRoundSkipsSandboxSync() {
        com.fastrag.module.agent.sandbox.SandboxExecutionService sandbox =
            mock(com.fastrag.module.agent.sandbox.SandboxExecutionService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(middleware, "sandboxExecutionService", sandbox);
        when(sandbox.syncSkills(any(), any(), any(), anyList())).thenReturn(Map.of());

        Skill skill = new Skill();
        skill.setId("1001");
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        skill.setEnabled(1);
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1001"));
        List<ChatMessage> messages = new ArrayList<>();
        middleware.beforeModelCall(context, messages, new ArrayList<>());
        middleware.beforeModelCall(context, messages, new ArrayList<>());

        verify(sandbox, times(1)).syncSkills(any(), any(), any(), anyList());
    }

    @Test
    void testInterceptToolCall_GetSkillContentUsesCachedEntity() {
        Skill skill = new Skill();
        skill.setId("1001");
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        skill.setEnabled(1);
        skill.setContent("正文指令");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1001"));
        List<ChatMessage> messages = new ArrayList<>();
        middleware.beforeModelCall(context, messages, new ArrayList<>());

        ChatMessage.ToolCall tc = new ChatMessage.ToolCall();
        tc.setId("call_12");
        ChatMessage.FunctionCall fc = new ChatMessage.FunctionCall();
        fc.setName("read_file");
        fc.setArguments("{\"file_path\": \"/workspace/skills/web-search/SKILL.md\"}");
        tc.setFunction(fc);
        ToolCallInterceptor interceptor = middleware.interceptToolCall(context, tc);

        assertNotNull(interceptor);
        assertTrue(interceptor.getResultMessage().getContent().contains("正文指令"));
        // 技能读取零查库：除装配的两次调用外无其他 skillService 交互（不得 getBySlug）
        verify(skillService, times(1)).listByIds(any());
        verify(skillService, times(1)).listAccessible(any(), any(), any());
        verifyNoMoreInteractions(skillService);
    }

    // ===== M2-6（D8）：注入预算——description 渲染前截断 600 字符（对齐 WeKnora prompts.go:253） =====

    @Test
    void testBuildSkillsPrompt_TruncatesLongDescriptionTo600() {
        String longDesc = "长".repeat(700);
        Skill skill = new Skill();
        skill.setId("1007");
        skill.setSlug("test");
        skill.setName("Test Skill");
        skill.setDescription(longDesc);
        skill.setEnabled(1);
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1007"));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "Base prompt."));

        middleware.beforeModelCall(context, messages, new ArrayList<>());

        String sysContent = messages.get(0).getContent();
        assertTrue(sysContent.contains("长".repeat(600)), "应含截断后的 600 字符前缀");
        assertFalse(sysContent.contains("长".repeat(601)), "不应包含超过 600 字符的描述");
    }

    // ===== F1 @提及技能：pinned must_use 强提示（2026-10-07 裁决） =====

    @Test
    void beforeModelCall_PinnedSkillsInjectMustUseBlockWithSkmdPath() {
        Skill skill = new Skill();
        skill.setId("2001");
        skill.setEnabled(1);
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("2001"));
        context.getRuntimeState().put(SkillsMiddleware.PINNED_SKILL_NAMES_KEY,
                List.of("联网搜索", "web-search"));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "Base prompt."));

        middleware.beforeModelCall(context, messages, new ArrayList<>());

        String sysContent = messages.get(0).getContent();
        assertTrue(sysContent.contains("<must_use>"), "交集非空应追加 must_use 块: " + sysContent);
        assertTrue(sysContent.contains("本轮用户点名了以下技能"), "应含点名指引文案");
        assertTrue(sysContent.contains("read_file"), "应指明经 read_file 读取 SKILL.md");
        assertTrue(sysContent.contains("/workspace/skills/web-search/SKILL.md"),
                "must_use 块应带 SKILL.md 路径供模型直读");
    }

    @Test
    void beforeModelCall_PinnedOutsideAvailableIgnoredWithWarnNoInjection() {
        Skill skill = new Skill();
        skill.setId("2002");
        skill.setEnabled(1);
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("2002"));
        context.getRuntimeState().put(SkillsMiddleware.PINNED_SKILL_NAMES_KEY,
                List.of("ghost-skill", "不存在的技能"));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "Base prompt."));

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SkillsMiddleware.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
            new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            middleware.beforeModelCall(context, messages, new ArrayList<>());

            assertFalse(messages.get(0).getContent().contains("<must_use>"),
                "交集为空不得注入 must_use");
            boolean warned = appender.list.stream()
                .anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                    && e.getFormattedMessage().contains("ghost-skill")
                    && e.getFormattedMessage().contains("不存在的技能"));
            assertTrue(warned, "集外点名应 warn（含被忽略的 name）");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void beforeModelCall_PartialPinInjectsMatchedAndWarnsIgnored() {
        Skill skill = new Skill();
        skill.setId("2003");
        skill.setEnabled(1);
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("2003"));
        context.getRuntimeState().put(SkillsMiddleware.PINNED_SKILL_NAMES_KEY,
                List.of("联网搜索", "ghost-skill"));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "Base prompt."));

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SkillsMiddleware.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
            new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            middleware.beforeModelCall(context, messages, new ArrayList<>());

            String sysContent = messages.get(0).getContent();
            assertTrue(sysContent.contains("<must_use>") && sysContent.contains("web-search"),
                "命中的点名应注入");
            boolean warned = appender.list.stream()
                .anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                    && e.getFormattedMessage().contains("ghost-skill"));
            assertTrue(warned, "集外部分应 warn 且不影响命中注入");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void beforeModelCall_MustUseInjectedOncePerRun() {
        Skill skill = new Skill();
        skill.setId("2004");
        skill.setEnabled(1);
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("2004"));
        context.getRuntimeState().put(SkillsMiddleware.PINNED_SKILL_NAMES_KEY, List.of("web-search"));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "Base prompt."));

        middleware.beforeModelCall(context, messages, new ArrayList<>());
        // 工具循环第二轮模型调用
        middleware.beforeModelCall(context, messages, new ArrayList<>());

        String sysContent = messages.get(0).getContent();
        assertEquals(1, countOccurrences(sysContent, "<must_use>"),
            "must_use 块单 run 只注入一次（INJECTED_FLAG 模式）: " + sysContent);
    }

    @Test
    void beforeModelCall_NoBoundSkillsWithPinsWarnsOnlyNoMessageChange() {
        // 无绑定技能：点名全为集外 → warn，消息零改动（pinned 不新增能力）
        BaseContext context = new BaseContext();
        context.setSkills(null);
        context.getRuntimeState().put(SkillsMiddleware.PINNED_SKILL_NAMES_KEY, List.of("web-search"));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "Base prompt."));

        ch.qos.logback.classic.Logger logger =
            (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SkillsMiddleware.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
            new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            middleware.beforeModelCall(context, messages, new ArrayList<>());

            assertEquals("Base prompt.", messages.get(0).getContent(), "无可用技能时不得注入");
            boolean warned = appender.list.stream()
                .anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                    && e.getFormattedMessage().contains("web-search"));
            assertTrue(warned, "无绑定技能时点名应 warn 静默忽略");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void beforeModelCall_NoPinnedNamesLeavesMessagesUntouched() {
        // runtimeState 无点名键：行为与既有链路完全一致（回归保护）
        Skill skill = new Skill();
        skill.setId("2005");
        skill.setEnabled(1);
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("2005"));
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "Base prompt."));

        middleware.beforeModelCall(context, messages, new ArrayList<>());

        assertFalse(messages.get(0).getContent().contains("<must_use>"));
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }

    // ===== M3-2（D9/M2 衔接）：依赖串规范化——ToolRegistry 可解析形态 =====

    @Test
    void testNormalizeDependencyRef_ToolPrefixedStrippedToBareId() {
        assertEquals("web_search", SkillsMiddleware.normalizeDependencyRef("tool:web_search"),
            "tool: 前缀应剥为裸工具 id（resolveSingleTool 仅识别 mcp:/skill: 前缀与裸 id）");
    }

    @Test
    void testNormalizeDependencyRef_BareIdKept() {
        assertEquals("web_search", SkillsMiddleware.normalizeDependencyRef("web_search"),
            "无前缀引用视为 tool 型裸 id，原样保留");
    }

    @Test
    void testNormalizeDependencyRef_McpAndSkillCanonical() {
        assertEquals("mcp:github", SkillsMiddleware.normalizeDependencyRef("mcp:github"));
        assertEquals("mcp:github", SkillsMiddleware.normalizeDependencyRef("mcp:github"),
            "mcp: 规范形态保持");
        assertEquals("skill:web-search", SkillsMiddleware.normalizeDependencyRef("skill:web-search"),
            "skill: 规范形态保持（闭包展开消费）");
        assertEquals("skill:web-search", SkillsMiddleware.normalizeDependencyRef("skill:web-search"));
    }

    @Test
    void testNormalizeDependencyRef_UnknownPrefixAndNullSafe() {
        assertEquals("weird:thing", SkillsMiddleware.normalizeDependencyRef("weird:thing"),
            "未知前缀原样保留（容错不吞）");
        assertEquals("", SkillsMiddleware.normalizeDependencyRef(null));
        assertEquals("", SkillsMiddleware.normalizeDependencyRef("   "));
    }

    @Test
    void testInterceptToolCall_AppendsNormalizedToolRefsOnly() {
        Skill skill = new Skill();
        skill.setId("1008");
        skill.setSlug("web-search");
        skill.setName("联网搜索");
        skill.setDescription("Search the web");
        skill.setEnabled(1);
        skill.setDependencies(List.of("tool:web_search", "skill:other", "mcp:github"));
        when(skillService.listByIds(any())).thenReturn(List.of(skill));
        when(skillService.listAccessible(any(), any(), any())).thenReturn(List.of(skill));

        BaseContext context = new BaseContext();
        context.setSkills(List.of("1008"));
        List<ChatMessage> messages = new ArrayList<>();
        middleware.beforeModelCall(context, messages, new ArrayList<>());

        ChatMessage.ToolCall tc = new ChatMessage.ToolCall();
        tc.setId("call_13");
        ChatMessage.FunctionCall fc = new ChatMessage.FunctionCall();
        fc.setName("read_file");
        fc.setArguments("{\"file_path\": \"/workspace/skills/web-search/SKILL.md\"}");
        tc.setFunction(fc);
        middleware.interceptToolCall(context, tc);

        assertTrue(context.getTools().contains("web_search"), "tool: 前缀应规范化为裸 id 再入 tools");
        assertFalse(context.getTools().contains("tool:web_search"), "不应残留 tool: 前缀原串");
        assertFalse(context.getTools().contains("skill:other"), "skill 依赖已由闭包展开，不进 tools");
        assertFalse(context.getTools().contains("mcp:github"), "mcp 依赖不经 tools 追加（depMap.mcps 通道）");
        assertTrue(context.isToolsDirty());
    }
}
