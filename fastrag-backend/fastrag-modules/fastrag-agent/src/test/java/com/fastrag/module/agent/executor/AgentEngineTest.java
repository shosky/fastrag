package com.fastrag.module.agent.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.entity.AgentRun;
import com.fastrag.module.agent.event.RunEventPublisher;
import com.fastrag.module.agent.middleware.AgentMiddlewareChain;
import com.fastrag.module.tools.executor.ToolExecutorFactory;
import com.fastrag.module.tools.registry.ToolDefinition;
import com.fastrag.module.tools.registry.ToolRegistry;
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

/**
 * AgentEngine 单元测试（M2-3：toolsDirty 循环内重算）。
 */
@ExtendWith(MockitoExtension.class)
class AgentEngineTest {

    @Mock
    private LlmService llmService;
    @Mock
    private ToolRegistry toolRegistry;
    @Mock
    private ToolExecutorFactory executorFactory;
    @Mock
    private RunEventPublisher eventPublisher;
    @Mock
    private AgentMiddlewareChain middlewareChain;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AgentEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AgentEngine(llmService, toolRegistry, executorFactory,
                eventPublisher, middlewareChain, objectMapper);
    }

    private static ToolDefinition def(String name) {
        return ToolDefinition.builder()
                .toolId(name)
                .name(name)
                .description(name + " desc")
                .type("builtin")
                .build();
    }

    private ChatResponse finalResponse(String content) {
        ChatResponse resp = new ChatResponse();
        resp.setContent(content);
        resp.setToolCalls(List.of());
        return resp;
    }

    private AgentRun runOf(String query) {
        AgentRun run = new AgentRun();
        run.setId("run-1");
        run.setInputPayload(Map.of("query", query));
        return run;
    }

    @Test
    void executeSync_recomputesToolsOnceWhenDirty() {
        // 初始解析只含 install_skill；激活置脏后重算应把新增工具（skill_tool）并入
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("install_skill"))))
                .thenReturn(new ArrayList<>(List.of(def("install_skill"), def("skill_tool"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenReturn(finalResponse("done"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("install_skill")));
        context.setToolsDirty(true); // 模拟技能激活已追加依赖

        AgentResult result = engine.executeSync(runOf("hi"), context);

        assertTrue(result.isSuccess());
        // 初始解析 1 次 + dirty 重算 1 次（不能每轮全量重查）
        verify(toolRegistry, times(2)).resolveTools(anyList());
        // 初始 + executeBefore 后 + dirty 重算
        verify(toolRegistry, times(3)).toOpenAITools(anyList());
        assertFalse(context.isToolsDirty(), "重算后脏标志应清零");
    }

    @Test
    void executeSync_notDirty_skipsRecompute() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("install_skill"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenReturn(finalResponse("done"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("install_skill")));
        context.setToolsDirty(false);

        AgentResult result = engine.executeSync(runOf("hi"), context);

        assertTrue(result.isSuccess());
        verify(toolRegistry, times(1)).resolveTools(anyList());
        verify(toolRegistry, times(2)).toOpenAITools(anyList());
    }

    // ===== M3-3（D14，spec REQ-E03 修订）：install_skill 条件注入（引擎侧过滤） =====

    @Test
    void executeSync_filtersInstallSkillWhenNoSkillsAndNoSandbox() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("install_skill"), def("web_search"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenReturn(finalResponse("done"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("install_skill", "web_search")));
        context.setSkills(null); // 未绑定技能、无沙箱

        engine.executeSync(runOf("hi"), context);

        org.mockito.ArgumentCaptor<List<ToolDefinition>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(toolRegistry, atLeastOnce()).toOpenAITools(captor.capture());
        for (List<ToolDefinition> tools : captor.getAllValues()) {
            assertTrue(tools.stream().noneMatch(t -> "install_skill".equals(t.getName())),
                "未绑定技能且无沙箱时不应向模型注入 install_skill");
        }
    }

    @Test
    void executeSync_keepsInstallSkillWhenSkillsBound() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("install_skill"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenReturn(finalResponse("done"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("install_skill")));
        context.setSkills(List.of("1001")); // 绑定了技能

        engine.executeSync(runOf("hi"), context);

        org.mockito.ArgumentCaptor<List<ToolDefinition>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(toolRegistry, atLeastOnce()).toOpenAITools(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(tools -> tools.stream().anyMatch(t -> "install_skill".equals(t.getName()))),
            "绑定技能时应保留 install_skill");
    }

    @Test
    void keepsInstallSkill_trueWhenSandboxConfigPresent() {
        com.fastrag.module.agent.context.ChatBotContext chatBot =
                new com.fastrag.module.agent.context.ChatBotContext();
        chatBot.setSandboxConfigId("sb-1");

        assertTrue(AgentEngine.keepsInstallSkill(chatBot), "沙箱启用（ChatBotContext.sandboxConfigId）应保留");
    }

    @Test
    void keepsInstallSkill_trueWhenSandboxConfigInRuntimeState() {
        BaseContext context = new BaseContext();
        context.getRuntimeState().put("sandboxConfigId", "sb-2");

        assertTrue(AgentEngine.keepsInstallSkill(context), "沙箱启用（runtimeState 路径）应保留");
    }

    @Test
    void keepsInstallSkill_falseForPlainContextWithoutSkills() {
        BaseContext context = new BaseContext();
        context.setSkills(List.of());

        assertFalse(AgentEngine.keepsInstallSkill(context));
    }

    // ===== QF-4（idd-review 快修）：executeSync 轮次耗尽兜底文案 =====

    private ChatResponse toolCallResponse(String toolName) {
        ChatResponse resp = new ChatResponse();
        resp.setContent("");
        resp.setToolCalls(List.of(new ChatMessage.ToolCall("call-1", "function",
                new ChatMessage.FunctionCall(toolName, "{}"))));
        return resp;
    }

    @Test
    void executeSync_maxStepsExhaustedWithoutAnswer_fillsFallbackContent() {
        // 每轮都返回 tool_calls（工具未注册走 not-found 通道），循环耗尽且 finalAnswer 为空
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("loop_tool"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenReturn(toolCallResponse("loop_tool"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("loop_tool")));
        context.setMaxSteps(2);

        AgentResult result = engine.executeSync(runOf("hi"), context);

        assertTrue(result.isSuccess());
        assertFalse(result.getAnswer().isEmpty(), "轮次耗尽不应返回空串");
        assertTrue(result.getAnswer().contains("已达到最大步数限制"), "应为与流式分支一致的兜底文案");
        assertTrue(result.getAnswer().contains("2步"), "兜底文案应带实际轮次上限");
    }

    @Test
    void executeSync_normalAnswer_notOverwrittenByFallback() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("loop_tool"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenReturn(finalResponse("real answer"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("loop_tool")));
        context.setMaxSteps(5);

        AgentResult result = engine.executeSync(runOf("hi"), context);

        assertEquals("real answer", result.getAnswer());
    }
}
