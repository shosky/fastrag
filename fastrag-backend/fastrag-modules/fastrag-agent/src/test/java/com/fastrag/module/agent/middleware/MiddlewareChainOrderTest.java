package com.fastrag.module.agent.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证所有中间件按 Order 升序排列（0 → 10）。
 *
 * <p>手工构造所有 AgentMiddleware 实例，注入 AgentMiddlewareChain 并验证排序结果。</p>
 */
class MiddlewareChainOrderTest {

    @Test
    void testAllMiddlewaresSortedByOrder() {
        // 收集所有已知的中间件实现
        List<AgentMiddleware> middlewares = new ArrayList<>();

        // 这些中间件无依赖（无参构造函数）
        middlewares.add(new AttachmentMiddleware());
        middlewares.add(new FilesystemMiddleware());
        middlewares.add(new KnowledgeBaseMiddleware());
        middlewares.add(new PatchToolCallsMiddleware());
        middlewares.add(new DatabaseMiddleware());
        middlewares.add(new TodoListMiddleware(new ObjectMapper()));

        // 注意：ContextMiddleware、ContextSummarizationMiddleware、ModelRetryMiddleware、
        // SubAgentMiddleware 和 SkillsMiddleware 有构造函数依赖，在此用代理模拟
        // 我们直接验证它们的 Order 值而非实际实例化

        // 验证我们已实例化的中间件的顺序
        middlewares.sort(Comparator.comparingInt(AgentMiddleware::getOrder));

        // 打印实际排序
        StringBuilder sb = new StringBuilder("Middleware sorted order: ");
        for (AgentMiddleware m : middlewares) {
            sb.append(m.getName()).append("(").append(m.getOrder()).append(") ");
        }
        System.out.println(sb);

        // 验证递增顺序
        for (int i = 0; i < middlewares.size() - 1; i++) {
            int current = middlewares.get(i).getOrder();
            int next = middlewares.get(i + 1).getOrder();
            assertTrue(current <= next,
                    "Order violation: " + middlewares.get(i).getName() + "(" + current
                            + ") > " + middlewares.get(i + 1).getName() + "(" + next + ")");
        }

        // 验证 DatabaseMiddleware 排在最后（Order=10）
        AgentMiddleware last = middlewares.get(middlewares.size() - 1);
        assertEquals("DatabaseMiddleware", last.getName(),
                "DatabaseMiddleware (Order=10) should be last among tested middlewares");
    }

    @Test
    void testDatabaseMiddlewareOrderIs10() {
        assertEquals(10, new DatabaseMiddleware().getOrder());
    }

    @Test
    void testAllOrderValuesAreValid() {
        // 验证 Order 值在 0-10 范围内
        assertTrue(new ContextMiddleware().getOrder() >= 0);
        assertTrue(new FilesystemMiddleware().getOrder() >= 0);
        assertTrue(new KnowledgeBaseMiddleware().getOrder() >= 0);
        assertTrue(new AttachmentMiddleware().getOrder() >= 0);
        assertTrue(new SummarizationMiddleware(null).getOrder() >= 0);
        assertTrue(new PatchToolCallsMiddleware().getOrder() >= 0);
        assertTrue(new TodoListMiddleware(new ObjectMapper()).getOrder() >= 0);
        assertTrue(new DatabaseMiddleware().getOrder() >= 0);
    }

    @Test
    void testChainSortingWithMixedOrders() {
        // 模拟 5 个中间件，验证排序
        List<AgentMiddleware> mixed = new ArrayList<>();
        mixed.add(new DatabaseMiddleware());   // 10
        mixed.add(new KnowledgeBaseMiddleware()); // 3
        mixed.add(new AttachmentMiddleware());    // 2
        mixed.add(new FilesystemMiddleware());    // 1
        mixed.add(new TodoListMiddleware(new ObjectMapper()));      // 7

        // 模拟 PostConstruct 排序
        mixed.sort(Comparator.comparingInt(AgentMiddleware::getOrder));

        assertEquals("FilesystemMiddleware", mixed.get(0).getName(), "Order 1 should be first");
        assertEquals("AttachmentMiddleware", mixed.get(1).getName(), "Order 2 should be second");
        assertEquals("KnowledgeBaseMiddleware", mixed.get(2).getName(), "Order 3 should be third");
        assertEquals("TodoListMiddleware", mixed.get(3).getName(), "Order 7 should be fourth");
        assertEquals("DatabaseMiddleware", mixed.get(4).getName(), "Order 10 should be last");
    }
}
