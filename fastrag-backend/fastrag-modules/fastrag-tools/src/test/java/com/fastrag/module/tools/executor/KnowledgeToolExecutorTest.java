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

    // ========================================================================
    //  R2（P0-5）请求级句柄协议：模型可见文本用 [cN] 编号（不含 UUID），
    //  cN→真实 id 映射存请求级注册表（runtimeState 共享引用），结构化通道保留真实 id
    // ========================================================================

    /** 向 ctx 预填请求级句柄注册表（模拟 AppServiceImpl 经 AgentEngine 的 runtimeState 浅拷贝共享） */
    private void seedHandleRegistry() {
        ctx.setExtension("kbHandleMap", new java.util.LinkedHashMap<String, Object>());
        ctx.setExtension("kbHandleSeq", new java.util.concurrent.atomic.AtomicInteger(0));
    }

    @Test
    void queryKb_outputUsesNumberedHandles_withoutUuidInModelVisibleText() {
        seedHandleRegistry();
        when(kbOperationService.queryKnowledgeBase(anyString(), anyString(), anyInt()))
            .thenReturn(List.of(
                Map.of("content", "年假政策内容", "score", 0.95, "source", "员工手册.pdf",
                       "file_id", "file-uuid-1111", "kb_id", "kb-uuid-2222", "chunk_index", 3)
            ));

        ToolDefinition def = new ToolDefinition();
        def.setName("query_kb");
        ToolResult result = executor.execute(def, Map.of("kb_id", "kb-uuid-2222", "query", "年假"), ctx);

        String output = result.getOutput();
        assertTrue(result.isSuccess());
        assertTrue(output.contains("[c1]"), "输出应使用 [cN] 编号句柄");
        assertTrue(output.contains("员工手册.pdf"), "句柄行应含文件名");
        assertFalse(output.contains("file-uuid-1111"), "模型可见文本不得含真实 file UUID");
        assertFalse(output.contains("kb-uuid-2222"), "模型可见文本不得含真实 kb UUID");
    }

    @Test
    void queryKb_handleRegistry_recordsCnToRealIds_andContinuesNumberingAcrossCalls() {
        seedHandleRegistry();
        when(kbOperationService.queryKnowledgeBase(anyString(), anyString(), anyInt()))
            .thenReturn(List.of(
                Map.of("content", "片段A", "score", 0.9, "source", "a.pdf", "file_id", "f-a", "chunk_index", 0)
            ));

        ToolDefinition def = new ToolDefinition();
        def.setName("query_kb");
        executor.execute(def, Map.of("kb_id", "kb1", "query", "q1"), ctx);

        when(kbOperationService.queryKnowledgeBase(anyString(), anyString(), anyInt()))
            .thenReturn(List.of(
                Map.of("content", "片段B", "score", 0.8, "source", "b.pdf", "file_id", "f-b", "chunk_index", 2)
            ));
        ToolResult second = executor.execute(def, Map.of("kb_id", "kb1", "query", "q2"), ctx);

        assertTrue(second.getOutput().contains("[c2]"), "同一请求内多次调用应连续编号，不得重置为 c1");

        Map<String, Object> handleMap = ctx.getExtension("kbHandleMap");
        @SuppressWarnings("unchecked")
        Map<String, Object> c1 = (Map<String, Object>) handleMap.get("c1");
        @SuppressWarnings("unchecked")
        Map<String, Object> c2 = (Map<String, Object>) handleMap.get("c2");
        assertEquals("f-a", c1.get("fileId"));
        assertEquals("kb1", c1.get("kbId"));
        assertEquals(0, ((Number) c1.get("chunkIndex")).intValue());
        assertEquals("f-b", c2.get("fileId"));
        assertEquals(2, ((Number) c2.get("chunkIndex")).intValue());
    }

    @Test
    void queryKb_structuredChannel_keepsRealIds_andAddsHandle() {
        seedHandleRegistry();
        when(kbOperationService.queryKnowledgeBase(anyString(), anyString(), anyInt()))
            .thenReturn(List.of(
                Map.of("content", "片段A", "score", 0.9, "source", "a.pdf", "file_id", "f-a", "chunk_index", 0)
            ));

        ToolDefinition def = new ToolDefinition();
        def.setName("query_kb");
        ToolResult result = executor.execute(def, Map.of("kb_id", "kb-9", "query", "q"), ctx);

        Map<String, Object> structured = result.getStructured();
        assertNotNull(structured, "结构化通道应保留（前端 KnowledgeChunksList 依赖）");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) structured.get("chunks");
        assertEquals(1, chunks.size());
        assertEquals("f-a", chunks.get(0).get("file_id"), "结构化通道保留真实 file_id");
        assertEquals("c1", chunks.get(0).get("handle"), "结构化条目应带 handle 供引用解析");
    }

    @Test
    void queryKb_withoutRegistry_degradesToLocalNumbering() {
        // 未预填注册表（如非 App 链路调用）：仍输出编号句柄，编号本调用内自 c1 起
        when(kbOperationService.queryKnowledgeBase(anyString(), anyString(), anyInt()))
            .thenReturn(List.of(
                Map.of("content", "片段", "score", 0.7, "source", "x.pdf")
            ));

        ToolDefinition def = new ToolDefinition();
        def.setName("query_kb");
        ToolResult result = executor.execute(def, Map.of("kb_id", "kb1", "query", "q"), ctx);

        assertTrue(result.isSuccess());
        assertTrue(result.getOutput().contains("[c1]"), "无注册表时降级为本调用内编号");
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
