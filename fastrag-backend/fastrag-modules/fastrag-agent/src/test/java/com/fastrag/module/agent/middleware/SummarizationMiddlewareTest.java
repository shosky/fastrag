package com.fastrag.module.agent.middleware;

import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.agent.context.BaseContext;
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
 * Unit tests for {@link SummarizationMiddleware} — Phase 1.
 *
 * <p>Covers: custom / default summary prompt, getOrder, and below-threshold guard.</p>
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

    // ========== below threshold — no LLM call ==========

    @Test
    void testBelowThreshold() {
        BaseContext context = new BaseContext();
        context.setSummaryThreshold(8000);

        // Create a short message list (well under 8000 tokens)
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", "You are helpful."));
        messages.add(new ChatMessage("user", "Hi"));
        messages.add(new ChatMessage("assistant", "Hello!"));

        List<ToolDefinition> tools = new ArrayList<>();

        middleware.beforeModelCall(context, messages, tools);

        // LLM should NOT have been called
        verify(llmService, never()).chat(anyString(), anyList(), anyDouble());
    }

    // ========== custom summary prompt ==========

    @Test
    void testCustomSummaryPrompt() {
        BaseContext context = new BaseContext();
        context.setSummaryThreshold(100); // low threshold to force summarization

        // Set a custom summary prompt in runtimeState
        context.getRuntimeState().put("summaryPrompt",
                "Custom: Summarize in exactly one sentence.");

        // Build messages that exceed the low threshold
        List<ChatMessage> messages = buildExceedingMessages(context.getSummaryThreshold());

        List<ToolDefinition> tools = new ArrayList<>();

        when(llmService.chat(anyString(), anyList(), anyDouble()))
                .thenReturn("Summary of conversation.");

        middleware.beforeModelCall(context, messages, tools);

        // Verify LLM was called (summarization triggered)
        verify(llmService, times(1)).chat(
                eq("gpt-4o-mini"),
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
        BaseContext context = new BaseContext();
        context.setSummaryThreshold(100); // low threshold

        // No custom prompt in runtimeState — should use default
        List<ChatMessage> messages = buildExceedingMessages(context.getSummaryThreshold());

        List<ToolDefinition> tools = new ArrayList<>();

        when(llmService.chat(anyString(), anyList(), anyDouble()))
                .thenReturn("Default summary result.");

        middleware.beforeModelCall(context, messages, tools);

        // Verify LLM was called
        verify(llmService, times(1)).chat(
                eq("gpt-4o-mini"),
                anyList(),
                eq(0.3)
        );

        // Verify the default prompt was included
        var llmArgs = captureChatArguments();
        String userContent = llmArgs.get(1).getContent();
        assertTrue(userContent.contains("请简洁地总结以下对话的关键信息"),
                "Default Chinese summary prompt should be used");
    }

    // ========== helper: build messages exceeding token threshold ==========

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
