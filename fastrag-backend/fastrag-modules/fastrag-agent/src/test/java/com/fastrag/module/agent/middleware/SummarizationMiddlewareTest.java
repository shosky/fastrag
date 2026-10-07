package com.fastrag.module.agent.middleware;

import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.agent.executor.ModelConfig;
import com.fastrag.module.tools.registry.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.Mockito;

/**
 * Unit tests for {@link SummarizationMiddleware}.
 *
 * <p>I6（P1-5）修订：调用点移入每轮循环（beforeRound，经 executeRoundBoundary），
 * 摘要模型从 BaseContext（modelConfig 优先，回落 model 字段）解析，不再硬编码
 * FALLBACK_MODEL；取不到模型跳过并 warn，LLM 失败 warn 不静默吞。</p>
 */
@ExtendWith(MockitoExtension.class)
class SummarizationMiddlewareTest {

    @Mock
    private LlmService llmService;

    private SummarizationMiddleware middleware;

    @BeforeEach
    void setUp() {
        middleware = new SummarizationMiddleware(llmService);
    }

    // ========== getOrder ==========

    @Test
    void testGetOrder() {
        assertEquals(6, middleware.getOrder());
    }

    // ========== I6：beforeModelCall 不再触发摘要（调用点已移入 beforeRound） ==========

    @Test
    void beforeModelCall_noLongerTriggersSummarization() {
        BaseContext context = contextWithModel("test-model");
        context.setSummaryThreshold(100);

        List<ChatMessage> messages = buildExceedingMessages(100);

        middleware.beforeModelCall(context, messages, new ArrayList<>());

        verify(llmService, never()).chat(anyString(), anyList(), anyDouble());
    }

    // ========== below threshold — no LLM call ==========

    @Test
    void testBelowThreshold() {
        BaseContext context = contextWithModel("test-model");
        context.setSummaryThreshold(8000);

        // Create a short message list (well under 8000 tokens)
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "You are helpful."));
        messages.add(new ChatMessage("user", "Hi"));
        messages.add(new ChatMessage("assistant", "Hello!"));

        middleware.beforeRound(context, messages, new ArrayList<>());

        // LLM should NOT have been called
        verify(llmService, never()).chat(anyString(), anyList(), anyDouble());
    }

    // ========== custom summary prompt ==========

    @Test
    void testCustomSummaryPrompt() {
        BaseContext context = contextWithModel("test-model");
        context.setSummaryThreshold(100); // low threshold to force summarization

        // Set a custom summary prompt in runtimeState
        context.getRuntimeState().put("summaryPrompt",
                "Custom: Summarize in exactly one sentence.");

        // Build messages that exceed the low threshold
        List<ChatMessage> messages = buildExceedingMessages(context.getSummaryThreshold());

        when(llmService.chat(anyString(), anyList(), anyDouble()))
                .thenReturn("Summary of conversation.");

        middleware.beforeRound(context, messages, new ArrayList<>());

        // Verify LLM was called with the context model (I6：不再用 FALLBACK_MODEL)
        verify(llmService, times(1)).chat(
                eq("test-model"),
                anyList(),
                eq(0.3)
        );

        // Verify the custom prompt was included in the LLM request
        var llmArgs = captureChatArguments();
        String userContent = llmArgs.get(1).getContent();
        assertTrue(userContent.startsWith("Custom: Summarize in exactly one sentence."),
                "Custom summary prompt should be used as the first line of the LLM request");
    }

    // ========== default summary prompt ==========

    @Test
    void testDefaultSummaryPrompt() {
        BaseContext context = contextWithModel("test-model");
        context.setSummaryThreshold(100); // low threshold

        List<ChatMessage> messages = buildExceedingMessages(context.getSummaryThreshold());

        when(llmService.chat(anyString(), anyList(), anyDouble()))
                .thenReturn("Default summary result.");

        middleware.beforeRound(context, messages, new ArrayList<>());

        verify(llmService, times(1)).chat(
                eq("test-model"),
                anyList(),
                eq(0.3)
        );

        var llmArgs = captureChatArguments();
        String userContent = llmArgs.get(1).getContent();
        assertTrue(userContent.contains("请简洁地总结以下对话的关键信息"),
                "Default Chinese summary prompt should be used");
    }

    // ========== I6：模型解析（modelConfig 优先，回落 model 字段） ==========

    @Test
    void summaryModel_fallsBackToContextModel_whenNoModelConfig() {
        BaseContext context = new BaseContext();
        context.setModel("ctx-model"); // 无 modelConfig，回落 model 字段
        context.setSummaryThreshold(100);

        when(llmService.chat(anyString(), anyList(), anyDouble())).thenReturn("ok");

        middleware.beforeRound(context, buildExceedingMessages(100), new ArrayList<>());

        verify(llmService).chat(eq("ctx-model"), anyList(), eq(0.3));
    }

    @Test
    void summaryModel_prefersModelConfigOverContextModel() {
        BaseContext context = contextWithModel("config-model");
        context.setModel("field-model");
        context.setSummaryThreshold(100);

        when(llmService.chat(anyString(), anyList(), anyDouble())).thenReturn("ok");

        middleware.beforeRound(context, buildExceedingMessages(100), new ArrayList<>());

        verify(llmService).chat(eq("config-model"), anyList(), eq(0.3));
    }

    @Test
    void noModelAvailable_skipsSummarizationWithoutLlmCall() {
        BaseContext context = new BaseContext(); // 既无 modelConfig 也无 model
        context.setSummaryThreshold(100);

        List<ChatMessage> messages = buildExceedingMessages(100);
        int sizeBefore = messages.size();

        middleware.beforeRound(context, messages, new ArrayList<>());

        verify(llmService, never()).chat(anyString(), anyList(), anyDouble());
        assertEquals(sizeBefore, messages.size(), "无可用模型时应跳过摘要、消息列表不变");
    }

    // ========== I6：LLM 失败 warn 不静默吞、消息不变、不抛出 ==========

    @Test
    void llmFailure_keepsOriginalMessagesAndDoesNotThrow() {
        BaseContext context = contextWithModel("test-model");
        context.setSummaryThreshold(100);

        List<ChatMessage> messages = buildExceedingMessages(100);
        List<ChatMessage> snapshot = new ArrayList<>(messages);

        when(llmService.chat(anyString(), anyList(), anyDouble()))
                .thenThrow(new RuntimeException("gateway down"));

        assertDoesNotThrow(() ->
                middleware.beforeRound(context, messages, new ArrayList<>()));

        assertEquals(snapshot, messages, "摘要失败应保留原始消息列表");
    }

    // ========== helpers ==========

    private static BaseContext contextWithModel(String model) {
        BaseContext context = new BaseContext();
        context.setModelConfig(ModelConfig.default_(model));
        return context;
    }

    /**
     * Build enough messages to exceed the given threshold.
     * Uses English text where ~4 chars ≈ 1 token.
     */
    private List<ChatMessage> buildExceedingMessages(int threshold) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "You are a helpful assistant."));

        // Each message ~500 chars ≈ 125 tokens (English)
        // Need at least (threshold / 125) + KEEP_RECENT_COUNT(6) + 2 system messages
        int numMiddleMessages = Math.max(10, threshold / 100 + 10);

        for (int i = 0; i < numMiddleMessages; i++) {
            messages.add(new ChatMessage("user",
                    "This is message number " + i + " with some padding text to ensure "
                            + "the token count exceeds the configured threshold value. "
                            + "AAAAAAA"));
            messages.add(new ChatMessage("assistant",
                    "Response number " + i + " with corresponding padding text. "
                            + "BBBBBBB"));
        }

        return messages;
    }

    /**
     * Capture and return the argument list from the most recent llmService.chat call.
     */
    @SuppressWarnings("unchecked")
    private List<ChatMessage> captureChatArguments() {
        var invocation = Mockito.mockingDetails(llmService).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("chat"))
                .filter(inv -> inv.getArguments().length == 3)
                .reduce((first, second) -> second) // last invocation
                .orElseThrow();
        return (List<ChatMessage>) invocation.getArguments()[1];
    }
}
