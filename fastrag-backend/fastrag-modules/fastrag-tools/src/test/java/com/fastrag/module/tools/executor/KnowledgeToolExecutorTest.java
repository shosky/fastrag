package com.fastrag.module.tools.executor;

import com.fastrag.common.service.KbOperationService;
import com.fastrag.module.tools.registry.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeToolExecutorTest {

    @Mock
    private KbOperationService kbOperationService;

    private KnowledgeToolExecutor executor;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        executor = new KnowledgeToolExecutor(kbOperationService);
        ctx = new ToolContext();
        ctx.setUserId("user1");
        ctx.setQuery("test query");
    }

    @Test
    void testGetType() {
        assertEquals("knowledge", executor.getType());
    }

    @Test
    void testListKnowledgeBases() {
        when(kbOperationService.listKnowledgeBases(anyString()))
            .thenReturn(List.of(
                Map.of("id", "kb1", "name", "知识库1", "description", "测试知识库"),
                Map.of("id", "kb2", "name", "知识库2")
            ));

        ToolDefinition def = new ToolDefinition();
        def.setName("list_kbs");
        def.setType("knowledge");

        ToolResult result = executor.execute(def, Map.of(), ctx);

        assertTrue(result.isSuccess());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().contains("知识库1"));
        assertTrue(result.getOutput().contains("kb1"));
    }

    @Test
    void testListEmptyKnowledgeBases() {
        when(kbOperationService.listKnowledgeBases(anyString()))
            .thenReturn(List.of());

        ToolDefinition def = new ToolDefinition();
        def.setName("list_kbs");

        ToolResult result = executor.execute(def, Map.of(), ctx);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("暂无可用知识库"));
    }

    @Test
    void testQueryKnowledgeBase() {
        when(kbOperationService.queryKnowledgeBase(anyString(), anyString(), anyInt()))
            .thenReturn(List.of(
                Map.of("content", "测试内容片段", "score", 0.95, "source", "doc1.pdf"),
                Map.of("content", "另一个片段", "score", 0.85)
            ));

        ToolDefinition def = new ToolDefinition();
        def.setName("query_kb");

        Map<String, Object> args = Map.of("kb_id", "kb1", "query", "测试", "top_k", 3);
        ToolResult result = executor.execute(def, args, ctx);

        assertTrue(result.isSuccess());
        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().contains("测试内容片段"));
        assertTrue(result.getOutput().contains("0.95"));
    }

    @Test
    void testQueryMissingParameters() {
        ToolDefinition def = new ToolDefinition();
        def.setName("query_kb");

        Map<String, Object> args = Map.of("kb_id", "kb1");
        ToolResult result = executor.execute(def, args, ctx);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("缺少参数"));
    }

    @Test
    void testQueryNullResults() {
        when(kbOperationService.queryKnowledgeBase(anyString(), anyString(), anyInt()))
            .thenReturn(null);

        ToolDefinition def = new ToolDefinition();
        def.setName("query_kb");

        Map<String, Object> args = Map.of("kb_id", "kb1", "query", "test");
        ToolResult result = executor.execute(def, args, ctx);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("未找到相关结果"));
    }

    @Test
    void testUnknownTool() {
        ToolDefinition def = new ToolDefinition();
        def.setName("unknown_kb_tool");

        ToolResult result = executor.execute(def, Map.of(), ctx);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Unknown KB tool"));
    }
}
