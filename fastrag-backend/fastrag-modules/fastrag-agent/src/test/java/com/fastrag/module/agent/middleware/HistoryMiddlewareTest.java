package com.fastrag.module.agent.middleware;

import com.fastrag.ai.model.ChatMessage;
import com.fastrag.module.agent.context.BaseContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1（P0-6）历史注入最小恢复版：HistoryMiddleware（order=12）从 BaseContext.runtimeState
 * 读取 AppServiceImpl 预填的最近 N 轮会话历史（key=conversationHistory，条目 {role,content}），
 * 作为 system prompt 附加块注入（「以下是本会话历史，仅供上下文参考」）。
 * 契约：无历史/空历史不注入；同一 run 只注入一次（注入后置 runtimeState 哨兵）。
 */
class HistoryMiddlewareTest {

    private final HistoryMiddleware middleware = new HistoryMiddleware();

    private BaseContext contextWithHistory(List<Map<String, Object>> history) {
        BaseContext context = new BaseContext();
        if (history != null) {
            context.getRuntimeState().put(HistoryMiddleware.HISTORY_KEY, history);
        }
        return context;
    }

    private Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private List<ChatMessage> messagesWithSystem() {
        return new ArrayList<>(List.of(new ChatMessage("system", "你是知识助手。")));
    }

    @Test
    void beforeModelCall_withHistory_appendsHistoryBlockToSystemMessage() {
        BaseContext context = contextWithHistory(List.of(
                msg("user", "公司的年假政策是什么"),
                msg("assistant", "年假为每年 10 天。")));
        List<ChatMessage> messages = messagesWithSystem();

        middleware.beforeModelCall(context, messages, List.of());

        String system = messages.get(0).getContent();
        assertNotEquals("你是知识助手。", system, "system 消息应被追加历史块");
        assertTrue(system.startsWith("你是知识助手。"), "原 system 内容应保留在前");
        assertTrue(system.contains("以下是本会话历史"), "应含历史块引导语");
        assertTrue(system.contains("user: 公司的年假政策是什么"), "历史 user 消息应进入附加块");
        assertTrue(system.contains("assistant: 年假为每年 10 天。"), "历史 assistant 消息应进入附加块");
    }

    @Test
    void beforeModelCall_withoutHistory_keepsSystemMessageUntouched() {
        BaseContext context = contextWithHistory(null);
        List<ChatMessage> messages = messagesWithSystem();

        middleware.beforeModelCall(context, messages, List.of());

        assertEquals("你是知识助手。", messages.get(0).getContent(), "无历史时不应改动 system 消息");
    }

    @Test
    void beforeModelCall_emptyHistory_keepsSystemMessageUntouched() {
        BaseContext context = contextWithHistory(List.of());
        List<ChatMessage> messages = messagesWithSystem();

        middleware.beforeModelCall(context, messages, List.of());

        assertEquals("你是知识助手。", messages.get(0).getContent(), "空历史时不应改动 system 消息");
    }

    @Test
    void beforeModelCall_secondModelCallWithinSameRun_injectsOnlyOnce() {
        BaseContext context = contextWithHistory(List.of(msg("user", "上一轮问题")));
        List<ChatMessage> first = messagesWithSystem();
        List<ChatMessage> second = messagesWithSystem();

        middleware.beforeModelCall(context, first, List.of());
        middleware.beforeModelCall(context, second, List.of());

        assertTrue(first.get(0).getContent().contains("以下是本会话历史"), "首轮应注入");
        assertEquals("你是知识助手。", second.get(0).getContent(),
                "同一 run 的后续模型调用不应重复注入（工具循环多轮）");
    }

    @Test
    void order_isTwelve_avoidingExistingMiddlewareRange() {
        assertEquals(12, middleware.getOrder(), "order=12 避开 0-11 现有中间件区间");
        assertNotNull(middleware.getName());
    }
}
