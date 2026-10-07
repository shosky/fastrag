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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    // ===== I5（P1-3）：防卡死守卫（同步路径验证；流式路径共用 RoundGuard 单元覆盖） =====

    @Test
    void executeSync_lengthWithIncompleteToolCalls_discardsCallAndRecovers() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("t"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenReturn(lengthToolCallResponse("t"), finalResponse("recovered"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("t")));

        AgentResult result = engine.executeSync(runOf("hi"), context);

        assertEquals("recovered", result.getAnswer(), "length 截断的工具调用应被丢弃并重试");
        // 未完成调用不得进入工具执行
        verify(executorFactory, never()).getExecutor(any());
        verify(llmService, times(2)).chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any());
    }

    @Test
    void executeSync_threeConsecutiveLengths_terminatesWithFallback() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("t"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenReturn(lengthToolCallResponse("t"), lengthToolCallResponse("t"),
                        lengthToolCallResponse("t"), finalResponse("should not be reached"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("t")));
        context.setMaxSteps(10);

        AgentResult result = engine.executeSync(runOf("hi"), context);

        assertTrue(result.isSuccess());
        assertFalse(result.getAnswer().isEmpty(), "连续 3 次 length 终止后应输出兜底文案");
        verify(llmService, times(3)).chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any());
    }

    @Test
    void executeSync_emptyResponses_nudgedAtMostTwiceThenFallback() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("t"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenReturn(finalResponse(""), finalResponse(""), finalResponse(""),
                        finalResponse("should not be reached"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("t")));
        context.setMaxSteps(10);

        AgentResult result = engine.executeSync(runOf("hi"), context);

        assertTrue(result.isSuccess());
        assertFalse(result.getAnswer().isEmpty(), "空响应 nudge 耗尽后应输出兜底文案");
        // 2 次 nudge + 1 次耗尽判定 = 3 次调用
        verify(llmService, times(3)).chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any());
    }

    @Test
    void executeSync_repeatedContent_injectsAndAdvances() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("missing_tool"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        // 第 1 轮：content X + 工具调用（工具未注册走 not-found 通道，循环继续）
        // 第 2 轮：content X 无工具调用 → 复读 → 注入推进；第 3 轮：content Y → 结束
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenReturn(contentWithToolCalls("X", "missing_tool"),
                        finalResponse("X"),
                        finalResponse("Y"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("missing_tool")));

        AgentResult result = engine.executeSync(runOf("hi"), context);

        assertEquals("Y", result.getAnswer(), "复读应被注入提示推进到新内容");
        verify(llmService, times(3)).chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any());
    }

    // ===== I7（P1-6）：retryConfig 消费——瞬时异常指数退避重试 =====

    @Test
    void executeSync_transient5xx_retriesWithBackoffAndRecovers() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("t"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenThrow(new org.springframework.web.reactive.function.client.WebClientResponseException(
                        503, "Service Unavailable", null, null, null))
                .thenReturn(finalResponse("ok after retry"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("t")));
        context.getRuntimeState().put("retryConfig",
                com.fastrag.module.agent.middleware.ModelRetryMiddleware.RetryConfig.defaultConfig());
        // 指数退避基间隔改小，避免测试拖慢
        com.fastrag.module.agent.middleware.ModelRetryMiddleware.RetryConfig cfg =
                (com.fastrag.module.agent.middleware.ModelRetryMiddleware.RetryConfig)
                        context.getRuntimeState().get("retryConfig");
        cfg.setBaseIntervalMs(1L);

        AgentResult result = engine.executeSync(runOf("hi"), context);

        assertTrue(result.isSuccess());
        assertEquals("ok after retry", result.getAnswer());
        verify(llmService, times(2)).chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any());
    }

    @Test
    void executeSync_nonTransientError_failsFastWithoutRetry() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("t"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenThrow(new IllegalStateException("invalid api key"))
                .thenReturn(finalResponse("never"));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("t")));
        context.getRuntimeState().put("retryConfig",
                com.fastrag.module.agent.middleware.ModelRetryMiddleware.RetryConfig.defaultConfig());

        AgentResult result = engine.executeSync(runOf("hi"), context);

        assertFalse(result.isSuccess(), "非瞬时异常不应重试");
        verify(llmService, times(1)).chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any());
    }

    @Test
    void executeSync_transientErrorWithoutRetryConfig_noRetry() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("t"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenThrow(new org.springframework.web.reactive.function.client.WebClientResponseException(
                        503, "Service Unavailable", null, null, null));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("t"))); // runtimeState 无 retryConfig

        AgentResult result = engine.executeSync(runOf("hi"), context);

        assertFalse(result.isSuccess());
        verify(llmService, times(1)).chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any());
    }

    @Test
    void isTransientLlmFailure_classifiesTimeoutAnd5xx() {
        assertTrue(AgentEngine.isTransientLlmFailure(new java.io.IOException("reset by peer")));
        assertTrue(AgentEngine.isTransientLlmFailure(new java.util.concurrent.TimeoutException("t")));
        assertTrue(AgentEngine.isTransientLlmFailure(new RuntimeException(
                new org.springframework.web.reactive.function.client.WebClientResponseException(
                        500, "ISE", null, null, null))));
        assertFalse(AgentEngine.isTransientLlmFailure(new IllegalStateException("bad")));
        assertFalse(AgentEngine.isTransientLlmFailure(new RuntimeException(
                new org.springframework.web.reactive.function.client.WebClientResponseException(
                        401, "Unauthorized", null, null, null))));
    }

    // ===== I3：事件表只存截断后内容 =====

    @Test
    void publishToolCallEvent_storesTruncatedOutput() {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(def("big_tool"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());
        when(llmService.chatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any()))
                .thenReturn(toolCallResponse("big_tool"), finalResponse("done"));
        com.fastrag.module.tools.executor.ToolExecutor executor =
                mock(com.fastrag.module.tools.executor.ToolExecutor.class);
        when(executorFactory.getExecutor("builtin")).thenReturn(executor);
        when(executor.execute(any(), any(), any()))
                .thenReturn(com.fastrag.module.tools.executor.ToolResult.success("x".repeat(9000), 5));

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("big_tool")));

        engine.executeSync(runOf("hi"), context);

        verify(eventPublisher).pushEvent(eq("run-1"), eq("tool_call"), argThat(payload -> {
            Object out = ((Map<?, ?>) payload).get("output");
            return out instanceof String s
                    && s.length() <= 5000 + "...(truncated)".length()
                    && s.endsWith("...(truncated)");
        }));
    }

    // ===== helpers =====

    private ChatResponse lengthToolCallResponse(String toolName) {
        ChatResponse resp = toolCallResponse(toolName);
        resp.setFinishReason("length");
        return resp;
    }

    private ChatResponse contentWithToolCalls(String content, String toolName) {
        ChatResponse resp = toolCallResponse(toolName);
        resp.setContent(content);
        return resp;
    }

    // ===== I8（P2-1，可选压轴）：只读工具单轮多调用并行执行 =====

    private static ChatMessage.ToolCall toolCall(String id, String name, String argsJson) {
        return new ChatMessage.ToolCall(id, "function", new ChatMessage.FunctionCall(name, argsJson));
    }

    @Test
    void isReadOnlyParallelRound_trueForWhitelistOnlyWithAtLeastTwoCalls() {
        assertTrue(AgentEngine.isReadOnlyParallelRound(List.of(
                toolCall("c1", "read_file", "{}"),
                toolCall("c2", "list_directory", "{}"))));
        assertTrue(AgentEngine.isReadOnlyParallelRound(List.of(
                toolCall("c1", "query_kb", "{}"),
                toolCall("c2", "grep", "{}"),
                toolCall("c3", "list_tables", "{}"))));
    }

    @Test
    void isReadOnlyParallelRound_falseForSingleCallWriteOrMixedRound() {
        // 单调用不并行
        assertFalse(AgentEngine.isReadOnlyParallelRound(List.of(toolCall("c1", "read_file", "{}"))));
        // 含写类工具不并行
        assertFalse(AgentEngine.isReadOnlyParallelRound(List.of(
                toolCall("c1", "read_file", "{}"),
                toolCall("c2", "write_file", "{}"))));
        // 非白名单工具不并行
        assertFalse(AgentEngine.isReadOnlyParallelRound(List.of(
                toolCall("c1", "install_skill", "{}"),
                toolCall("c2", "read_file", "{}"))));
    }

    @Test
    void executeStream_readOnlyRound_executesConcurrentlyAndAlignedByToolCallId() throws Exception {
        when(toolRegistry.resolveTools(anyList()))
                .thenReturn(new ArrayList<>(List.of(
                        def("read_file"), def("list_directory"), def("grep"))));
        when(toolRegistry.toOpenAITools(anyList())).thenReturn(List.of());

        ChatMessage.ToolCall c1 = toolCall("c1", "read_file", "{\"path\":\"a\"}");
        ChatMessage.ToolCall c2 = toolCall("c2", "list_directory", "{\"path\":\"b\"}");
        ChatMessage.ToolCall c3 = toolCall("c3", "grep", "{\"path\":\"c\"}");
        when(llmService.streamChatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any(), anyBoolean()))
                .thenReturn(
                        reactor.core.publisher.Flux.fromIterable(List.of(
                                com.fastrag.ai.model.StreamEvent.toolCallDelta(c1),
                                com.fastrag.ai.model.StreamEvent.toolCallDelta(c2),
                                com.fastrag.ai.model.StreamEvent.toolCallDelta(c3),
                                com.fastrag.ai.model.StreamEvent.finish("tool_calls"))),
                        reactor.core.publisher.Flux.fromIterable(List.of(
                                com.fastrag.ai.model.StreamEvent.content("done"),
                                com.fastrag.ai.model.StreamEvent.finish("stop"))));

        com.fastrag.module.tools.executor.ToolExecutor exec =
                mock(com.fastrag.module.tools.executor.ToolExecutor.class);
        when(executorFactory.getExecutor("builtin")).thenReturn(exec);
        java.util.concurrent.CountDownLatch allArrived = new java.util.concurrent.CountDownLatch(3);
        when(exec.execute(any(), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = (Map<String, Object>) inv.getArgument(1);
            allArrived.countDown();
            // 只有真并行（3 个调用同时在线）才能通过；串行执行会在此超时
            if (!allArrived.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                return com.fastrag.module.tools.executor.ToolResult.error("serial execution detected", 0);
            }
            return com.fastrag.module.tools.executor.ToolResult.success("out-" + args.get("path"), 1);
        });

        BaseContext context = new BaseContext();
        context.setTools(new ArrayList<>(List.of("read_file", "list_directory", "grep")));

        engine.executeStream(context, new SseEmitter(Long.MAX_VALUE),
                ModelConfig.default_("test-model"), runOf("hi"), null);

        verify(llmService, times(2)).streamChatWithTools(any(), anyList(), anyList(), anyDouble(), any(), any(), anyBoolean());

        // 结果按 toolCallId 对齐回填：第 2 次模型调用的消息里 tool 消息与调用一一对应
        org.mockito.ArgumentCaptor<List<ChatMessage>> msgCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(llmService, times(2)).streamChatWithTools(any(), msgCaptor.capture(), anyList(), anyDouble(), any(), any(), anyBoolean());
        List<ChatMessage> round2Messages = msgCaptor.getAllValues().get(1);
        Map<String, String> outputsByCallId = new java.util.HashMap<>();
        for (ChatMessage msg : round2Messages) {
            if ("tool".equals(msg.getRole()) && msg.getToolCallId() != null) {
                outputsByCallId.put(msg.getToolCallId(), msg.getContent());
            }
        }
        assertEquals("out-a", outputsByCallId.get("c1"));
        assertEquals("out-b", outputsByCallId.get("c2"));
        assertEquals("out-c", outputsByCallId.get("c3"));
    }
}
