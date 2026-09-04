package com.fastrag.module.bpm.executor.impl;

import com.fastrag.ai.llm.LlmService;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.bpm.entity.BpmFlowNode;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/** IntentNodeExecutor:LLM 分类 + JSON 解析 + 容错 fallback */
@ExtendWith(MockitoExtension.class)
class IntentNodeExecutorTest {

    @Mock LlmService llm;
    private final SpelEvaluator spel = new SpelEvaluator();

    private IntentNodeExecutor newEx() { return new IntentNodeExecutor(spel, llm); }

    private BpmFlowNode node(String config) {
        BpmFlowNode n = new BpmFlowNode();
        n.setNodeType("intent");
        n.setNodeKey("i1");
        n.setConfig(config);
        return n;
    }

    private ExecutionContext ctx(BpmFlowNode n, Map<String, Object> vars) {
        ExecutionContext c = new ExecutionContext();
        c.setFlowDefId("f1");
        c.setCurrentNode(n);
        c.setVariables(vars == null ? new HashMap<>() : new HashMap<>(vars));
        return c;
    }

    @Test
    @DisplayName("正常 JSON 输出解析为 intent+score,passed 由 threshold 决定")
    void happyPath() {
        when(llm.chat(anyString(), anyList(), anyDouble(), any(), any()))
                .thenReturn("{\"intent\":\"退款\",\"score\":0.92}");
        IntentNodeExecutor ex = newEx();
        String cfg = "{\"model\":\"m\",\"labels\":[\"退款\",\"咨询\"],\"confidenceThreshold\":0.5}";
        NodeExecutionResult r = ex.execute(ctx(node(cfg), Map.of("input", "我要退款")));
        assertEquals("退款", r.getOutputs().get("intent"));
        assertEquals(0.92, (double) r.getOutputs().get("score"), 1e-9);
        assertEquals(true, r.getOutputs().get("passed"));
    }

    @Test
    @DisplayName("JSON 被文本包裹,正则提取 {...} 块")
    void jsonInText() {
        when(llm.chat(anyString(), anyList(), anyDouble(), any(), any()))
                .thenReturn("好,我选这个: {\"intent\":\"咨询\",\"score\":0.81} 完毕");
        IntentNodeExecutor ex = newEx();
        String cfg = "{\"model\":\"m\",\"labels\":[\"退款\",\"咨询\"]}";
        NodeExecutionResult r = ex.execute(ctx(node(cfg), Map.of("input", "x")));
        assertEquals("咨询", r.getOutputs().get("intent"));
        assertEquals(0.81, (double) r.getOutputs().get("score"), 1e-9);
    }

    @Test
    @DisplayName("LLM 失败 + failOnError=false → fallback 到第一个 label,passed=false")
    void fallbackOnFail() {
        when(llm.chat(anyString(), anyList(), anyDouble(), any(), any()))
                .thenReturn("模型调用失败: timeout");
        IntentNodeExecutor ex = newEx();
        String cfg = "{\"model\":\"m\",\"labels\":[\"退款\",\"咨询\"]}";
        NodeExecutionResult r = ex.execute(ctx(node(cfg), Map.of("input", "x")));
        assertEquals("退款", r.getOutputs().get("intent"));
        assertEquals(0.0, (double) r.getOutputs().get("score"), 1e-9);
        assertEquals(false, r.getOutputs().get("passed"));
    }

    @Test
    @DisplayName("failOnError=true 时 LLM 失败抛 EXECUTION_FAILED(40041)")
    void failOnErrorThrows() {
        when(llm.chat(anyString(), anyList(), anyDouble(), any(), any()))
                .thenReturn("模型调用失败: x");
        IntentNodeExecutor ex = newEx();
        String cfg = "{\"model\":\"m\",\"labels\":[\"a\",\"b\"],\"failOnError\":true}";
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.execute(ctx(node(cfg), Map.of("input", "x"))));
        assertEquals(40041, be.getCode());
    }

    @Test
    @DisplayName("labels 为空抛 40021")
    void validateEmptyLabels() {
        IntentNodeExecutor ex = newEx();
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.validateConfig(Map.of("model", "m", "labels", List.of())));
        assertEquals(40021, be.getCode());
    }

    @Test
    @DisplayName("纯文本返回命中 label → 高置信(passed=true)")
    void plainTextMatch() {
        when(llm.chat(anyString(), anyList(), anyDouble(), any(), any())).thenReturn("退款");
        IntentNodeExecutor ex = newEx();
        String cfg = "{\"model\":\"m\",\"labels\":[\"退款\",\"咨询\"]}";
        NodeExecutionResult r = ex.execute(ctx(node(cfg), Map.of("input", "x")));
        assertEquals("退款", r.getOutputs().get("intent"));
        assertEquals(0.9, (double) r.getOutputs().get("score"), 1e-9);
        assertEquals(true, r.getOutputs().get("passed"));
    }
}