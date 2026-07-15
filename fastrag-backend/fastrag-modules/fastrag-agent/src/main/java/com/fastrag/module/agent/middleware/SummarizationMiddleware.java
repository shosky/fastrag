package com.fastrag.module.agent.middleware;

import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.ai.model.ChatResponse;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.tools.registry.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文摘要中间件。当 token 数超过阈值时，对中间消息进行摘要压缩。
 *
 * <p>执行顺序: Order=6。</p>
 *
 * <p>策略：保留 system + 最近 6 条消息，中间消息用 LLM 生成摘要。
 * 默认阈值 8000 tokens，可通过 context.summaryThreshold 自定义。</p>
 *
 * <p>Token 估算规则：
 * <ul>
 *   <li>中文：每 1.5 个字符 ≈ 1 token</li>
 *   <li>英文：每 4 个字符 ≈ 1 token</li>
 * </ul></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummarizationMiddleware implements AgentMiddleware {

    private final LlmService llmService;

    private static final int DEFAULT_TOKEN_THRESHOLD = 8000;
    private static final int KEEP_RECENT_COUNT = 6;
    private static final String FALLBACK_MODEL = "gpt-4o-mini";

    @Override
    public int getOrder() { return 6; }

    @Override
    public BaseContext beforeModelCall(BaseContext context,
                                      List<ChatMessage> messages,
                                      List<ToolDefinition> tools) {
        try {
            int threshold = context.getSummaryThreshold() != null
                    ? context.getSummaryThreshold()
                    : DEFAULT_TOKEN_THRESHOLD;

            int estimatedTokens = estimateTokens(messages);
            if (estimatedTokens <= threshold) {
                log.debug("[SummarizationMiddleware] Tokens ({}) under threshold ({}), no summarization needed",
                        estimatedTokens, threshold);
                return context;
            }

            log.info("[SummarizationMiddleware] Token count {} exceeds threshold {}, triggering summarization",
                    estimatedTokens, threshold);

            doSummarization(context, messages, threshold);
        } catch (Exception e) {
            log.error("[SummarizationMiddleware] Summarization failed: {}", e.getMessage());
            // 不抛出异常，继续使用原始消息
        }

        return context;
    }

    /**
     * 对消息列表执行摘要压缩。
     */
    private void doSummarization(BaseContext context, List<ChatMessage> messages, int threshold) {
        if (messages.size() <= KEEP_RECENT_COUNT + 1) {
            return; // 消息太少，不需要摘要
        }

        // 分离 system 消息和最近消息
        List<ChatMessage> systemMessages = new ArrayList<>();
        List<ChatMessage> middleMessages = new ArrayList<>();
        List<ChatMessage> recentMessages = new ArrayList<>();

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if ("system".equals(msg.getRole())) {
                systemMessages.add(msg);
            } else if (i >= messages.size() - KEEP_RECENT_COUNT) {
                recentMessages.add(msg);
            } else {
                middleMessages.add(msg);
            }
        }

        if (middleMessages.isEmpty()) {
            return;
        }

        // 用 LLM 生成中间消息的摘要
        String summary = generateSummary(context, middleMessages);
        if (summary == null || summary.isEmpty()) {
            log.warn("[SummarizationMiddleware] Summary generation returned empty, skipping compression");
            return;
        }

        // 重建消息列表: system + 摘要 + 最近消息
        messages.clear();
        messages.addAll(systemMessages);
        messages.add(new ChatMessage("system",
                "\n\n--- 以下是对之前对话的摘要 ---\n" + summary + "\n--- 摘要结束 ---\n"));
        messages.addAll(recentMessages);

        int newTokens = estimateTokens(messages);
        log.info("[SummarizationMiddleware] Summarization done: compressed {} messages to summary, "
                        + "tokens reduced from ~{} to ~{}",
                middleMessages.size(), threshold, newTokens);
    }

    /**
     * 调用 LLM 生成消息摘要。
     */
    private String generateSummary(BaseContext context, List<ChatMessage> messages) {
        try {
            // 支持自定义摘要提示词
            String customPrompt = (String) context.getRuntimeState().get("summaryPrompt");
            String summaryPromptText = (customPrompt != null && !customPrompt.isEmpty())
                    ? customPrompt
                    : "请简洁地总结以下对话的关键信息，保留重要的事实、决定和上下文。使用 3-5 个要点，每个要点一行。";

            String summaryPrompt = summaryPromptText + "\n\n";

            StringBuilder chatContent = new StringBuilder(summaryPrompt);
            for (ChatMessage msg : messages) {
                chatContent.append("[").append(msg.getRole()).append("]: ");
                chatContent.append(msg.getContent() != null ? msg.getContent() : "");
                chatContent.append("\n\n");
            }

            List<ChatMessage> summaryMessages = List.of(
                    new ChatMessage("system", "你是一个对话摘要助手。请简洁、准确地总结对话内容。"),
                    new ChatMessage("user", chatContent.toString())
            );

            // 使用 fallback 模型生成摘要
            String result = llmService.chat(FALLBACK_MODEL, summaryMessages, 0.3);
            log.debug("[SummarizationMiddleware] Summary generated using model: {}", FALLBACK_MODEL);
            return result;
        } catch (Exception e) {
            log.error("[SummarizationMiddleware] Failed to generate summary: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 估算消息列表的 token 数。
     * 中文：每 1.5 字 ≈ 1 token；英文：每 4 字符 ≈ 1 token。
     */
    int estimateTokens(List<ChatMessage> messages) {
        int totalTokens = 0;
        for (ChatMessage msg : messages) {
            if (msg.getContent() != null) {
                totalTokens += estimateTokensForText(msg.getContent());
            }
            // tool_calls 也会占用 token
            if (msg.getToolCalls() != null) {
                for (ChatMessage.ToolCall tc : msg.getToolCalls()) {
                    if (tc.getFunction() != null && tc.getFunction().getArguments() != null) {
                        totalTokens += estimateTokensForText(tc.getFunction().getArguments());
                    }
                }
            }
        }
        return totalTokens;
    }

    /**
     * 估算单段文本的 token 数。
     */
    private int estimateTokensForText(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseChars = 0;
        int otherChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }

        // 中文: 每 1.5 字 ≈ 1 token; 英文: 每 4 字符 ≈ 1 token
        int chineseTokens = (int) Math.ceil(chineseChars / 1.5);
        int otherTokens = (int) Math.ceil((double) otherChars / 4.0);
        return chineseTokens + otherTokens;
    }

    /**
     * 判断字符是否为中文。
     */
    private boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    @Override
    public BaseContext afterModelCall(BaseContext context, ChatResponse response) {
        return context;
    }

    @Override
    public ToolCallInterceptor interceptToolCall(BaseContext context,
                                                 ChatMessage.ToolCall toolCall) {
        return null;
    }
}
