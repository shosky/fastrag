package com.fastrag.module.bpm.executor.impl;

import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.bpm.entity.BpmFlowNode;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** LlmNodeExecutor:渲染 prompt + 调 LlmService + 处理失败 */
@ExtendWith(MockitoExtension.class)
class LlmNodeExecutorTest {

    @Mock LlmService llm;

    private final SpelEvaluator spel = new SpelEvaluator();

    private LlmNodeExecutor newEx() {
        return new LlmNodeExecutor(spel, llm);
    }

    private BpmFlowNode node(String config) {
        BpmFlowNode n = new BpmFlowNode();
        n.setNodeType("llm");
        n.setNodeKey("llm1");
        n.setConfig(config);
        return n;
    }

    private ExecutionContext ctx(BpmFlowNode n, Map<String, Object> vars, Map<String, Object> inputs) {
        ExecutionContext c = new ExecutionContext();
        c.setFlowDefId("f1");
        c.setCurrentNode(n);
        c.setVariables(vars == null ? new HashMap<>() : new HashMap<>(vars));
        c.setNodeInputs(inputs == null ? new HashMap<>() : new HashMap<>(inputs));
        return c;
    }

    @Test
    @DisplayName("正常路径:模型返回 answer,写到 outputs.answer")
    void happyPath() {
        when(llm.chat(eq("gpt-4"), anyList(), eq(0.7), isNull(), isNull())).thenReturn("hello world");
        LlmNodeExecutor ex = newEx();
        String cfg = "{\"model\":\"gpt-4\",\"userPrompt\":\"#query\"}";
        NodeExecutionResult r = ex.execute(ctx(node(cfg), Map.of("query", "hi"), null));
        assertEquals("hello world", r.getOutputs().get("answer"));
        assertEquals("gpt-4", r.getOutputs().get("model"));
    }

    @Test
    @DisplayName("validateConfig:userPrompt 缺失抛 40021")
    void validateMissingUserPrompt() {
        LlmNodeExecutor ex = newEx();
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.validateConfig(Map.of("model", "gpt-4")));
        assertEquals(40021, be.getCode());
    }

    @Test
    @DisplayName("validateConfig:model 缺失抛 40021")
    void validateMissingModel() {
        LlmNodeExecutor ex = newEx();
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.validateConfig(Map.of("userPrompt", "hi")));
        assertEquals(40021, be.getCode());
    }

    @Test
    @DisplayName("LlmService 返回 '模型调用失败:...' 抛 EXECUTION_FAILED(40041)")
    void llmReturnsFailString() {
        when(llm.chat(anyString(), anyList(), anyDouble(), any(), any()))
                .thenReturn("模型调用失败: timeout");
        LlmNodeExecutor ex = newEx();
        String cfg = "{\"model\":\"m\",\"userPrompt\":\"#q\"}";
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.execute(ctx(node(cfg), Map.of("q", "x"), null)));
        assertEquals(40041, be.getCode());
    }

    @Test
    @DisplayName("LlmService 返回 null 抛 EXECUTION_FAILED")
    void llmReturnsNull() {
        when(llm.chat(anyString(), anyList(), anyDouble(), any(), any())).thenReturn(null);
        LlmNodeExecutor ex = newEx();
        String cfg = "{\"model\":\"m\",\"userPrompt\":\"#q\"}";
        BusinessException be = assertThrows(BusinessException.class,
                () -> ex.execute(ctx(node(cfg), Map.of("q", "x"), null)));
        assertEquals(40041, be.getCode());
    }

    @Test
    @DisplayName("systemPrompt + userPrompt 都传入,生成 messages[system, user]")
    void systemAndUserMessages() throws Exception {
        when(llm.chat(eq("m"), anyList(), anyDouble(), isNull(), isNull())).thenReturn("ok");
        LlmNodeExecutor ex = newEx();
        String cfg = "{\"model\":\"m\",\"systemPrompt\":\"'sys'\",\"userPrompt\":\"'usr'\"}";
        ex.execute(ctx(node(cfg), null, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> cap = ArgumentCaptor.forClass(List.class);
        verify(llm).chat(eq("m"), cap.capture(), eq(0.7), isNull(), isNull());
        List<ChatMessage> messages = cap.getValue();
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).getRole());
        assertEquals("sys", messages.get(0).getContent());
        assertEquals("user", messages.get(1).getRole());
        assertEquals("usr", messages.get(1).getContent());
    }
}