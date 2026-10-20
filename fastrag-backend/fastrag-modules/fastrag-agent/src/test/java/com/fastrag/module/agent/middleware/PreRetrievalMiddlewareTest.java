package com.fastrag.module.agent.middleware;

import com.fastrag.ai.model.ChatMessage;
import com.fastrag.common.service.KbOperationService;
import com.fastrag.module.agent.context.BaseContext;
import com.fastrag.module.tools.executor.KnowledgeToolExecutor;
import com.fastrag.module.tools.executor.ToolContext;
import com.fastrag.module.tools.executor.ToolResult;
import com.fastrag.module.tools.registry.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 并行预检索中间件回归（ADR-PARALLEL-PRE-RETRIEVAL / IF-003，2026-10-20）：
 * 首轮模型调用前同步检索注入 [cN] 句柄块；句柄与 query_kb 工具通道共享连续编号；
 * 失败/空结果/开关关闭 fail-open 不注入。
 */
@ExtendWith(MockitoExtension.class)
class PreRetrievalMiddlewareTest {

    @Mock
    private KbOperationService kbOperationService;

    private BaseContext context;
    private List<ChatMessage> messages;
    private Map<String, Object> handleMap;
    private AtomicInteger seq;

    @BeforeEach
    void setUp() {
        context = new BaseContext();
        context.setKnowledges(new ArrayList<>(List.of("kb-1")));
        context.setPreRetrieveKb(true);
        messages = new ArrayList<>(List.of(
                new ChatMessage("system", "base prompt"),
                new ChatMessage("user", "新办etc")));
        handleMap = new LinkedHashMap<>();
        seq = new AtomicInteger(0);
        context.getRuntimeState().put(PreRetrievalMiddleware.KB_HANDLE_MAP_KEY, handleMap);
        context.getRuntimeState().put(PreRetrievalMiddleware.KB_HANDLE_SEQ_KEY, seq);
    }

    private void runMiddleware() {
        new PreRetrievalMiddleware(kbOperationService).beforeModelCall(context, messages, new ArrayList<>());
    }

    private String systemContent() {
        return messages.get(0).getContent();
    }

    @Test
    void injectsRetrievalBlock_withCnHandles_andRegistersSources() {
        when(kbOperationService.queryKnowledgeBase("kb-1", "新办etc", 10))
            .thenReturn(List.of(Map.of("content", "ETC办理需要设备安装与激活", "score", 0.9,
                    "source", "etc指南.pdf", "file_id", "f-1", "chunk_index", 2)));

        runMiddleware();

        String sys = systemContent();
        assertTrue(sys.startsWith("base prompt"), "原有 system 提示词保留");
        assertTrue(sys.contains("【知识库预检索】"), "应注入预检索块");
        assertTrue(sys.contains("[c1]"), "分片以 [cN] 句柄编号");
        assertTrue(sys.contains("etc指南.pdf"));
        assertTrue(sys.contains("ETC办理需要设备安装与激活"));
        Object ref = handleMap.get("c1");
        assertEquals("f-1", ((Map<?, ?>) ref).get("fileId"), "句柄应注册进共享注册表");
        Object pre = context.getRuntimeState().get(PreRetrievalMiddleware.PRE_RETRIEVAL_SOURCES_KEY);
        assertTrue(pre instanceof List && !((List<?>) pre).isEmpty(), "预检索来源应写入 runtimeState 供编排层合并");
    }

    @Test
    void handleNumbering_continuesIntoQueryKbCalls() {
        when(kbOperationService.queryKnowledgeBase("kb-1", "新办etc", 10))
            .thenReturn(List.of(Map.of("content", "片段", "score", 0.9, "source", "a.pdf")));
        when(kbOperationService.queryKnowledgeBase("kb-1", "换个词再查一次", 10))
            .thenReturn(List.of(Map.of("content", "片段B", "score", 0.8, "source", "b.pdf")));

        runMiddleware();

        // 同请求内模型随后调 query_kb：编号必须从 c2 续（共享 KB_HANDLE_SEQ）
        KnowledgeToolExecutor executor = new KnowledgeToolExecutor(kbOperationService);
        ToolDefinition def = ToolDefinition.builder().name("query_kb").type("knowledge")
                .config(Map.of("action", "query_kb", "kbIds", List.of("kb-1"))).build();
        ToolContext toolCtx = new ToolContext();
        toolCtx.setExtension(PreRetrievalMiddleware.KB_HANDLE_MAP_KEY, handleMap);
        toolCtx.setExtension(PreRetrievalMiddleware.KB_HANDLE_SEQ_KEY, seq);
        ToolResult r = executor.execute(def, Map.of("query", "换个词再查一次"), toolCtx);

        assertTrue(r.isSuccess());
        assertTrue(r.getOutput().contains("[c2]"), "工具检索编号必须与预检索连续（不重置为 c1）");
    }

    @Test
    void multiKb_queriesEveryBoundKb() {
        context.setKnowledges(new ArrayList<>(List.of("kb-1", "kb-2")));
        when(kbOperationService.queryKnowledgeBase(anyString(), eq("新办etc"), anyInt()))
            .thenReturn(List.of(Map.of("content", "片段", "score", 0.5, "source", "x.pdf")));

        runMiddleware();

        verify(kbOperationService).queryKnowledgeBase(eq("kb-1"), eq("新办etc"), anyInt());
        verify(kbOperationService).queryKnowledgeBase(eq("kb-2"), eq("新办etc"), anyInt());
        assertTrue(systemContent().contains("[c1]") && systemContent().contains("[c2]"));
    }

    @Test
    void flagOff_noRetrievalNoInjection() {
        context.setPreRetrieveKb(false);
        runMiddleware();
        verifyNoInteractions(kbOperationService);
        assertEquals("base prompt", systemContent());
    }

    @Test
    void retrievalFailure_failOpen_withoutInjection() {
        when(kbOperationService.queryKnowledgeBase(anyString(), anyString(), anyInt()))
            .thenThrow(new RuntimeException("retrieval down"));

        assertDoesNotThrow(this::runMiddleware);
        assertEquals("base prompt", systemContent(), "检索失败不得注入、不得阻断对话");
    }

    @Test
    void emptyResults_noInjection() {
        when(kbOperationService.queryKnowledgeBase(anyString(), anyString(), anyInt()))
            .thenReturn(List.of());

        runMiddleware();
        assertEquals("base prompt", systemContent(), "空结果不注入（省 token，模型仍可 query_kb 二次检索）");
    }

    @Test
    void injection_truncatedToBudget() {
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            char[] body = new char[300];
            java.util.Arrays.fill(body, 'x');
            chunks.add(Map.of("content", new String(body), "score", 0.5, "source", "f" + i + ".pdf"));
        }
        when(kbOperationService.queryKnowledgeBase(anyString(), anyString(), anyInt()))
            .thenReturn(chunks);

        runMiddleware();

        int injected = systemContent().length() - "base prompt".length();
        assertTrue(injected <= 5400, "注入预算 ≤5000 字符（含块头开销），实际 " + injected);
        assertFalse(systemContent().contains("f29.pdf"), "超预算分片不注入");
    }
}
