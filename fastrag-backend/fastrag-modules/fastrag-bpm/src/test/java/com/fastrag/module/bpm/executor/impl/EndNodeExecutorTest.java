package com.fastrag.module.bpm.executor.impl;

import com.fastrag.module.bpm.entity.BpmFlowNode;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** EndNodeExecutor:outputTemplate SpEL 渲染 + mergeToVariables */
class EndNodeExecutorTest {

    private final SpelEvaluator spel = new SpelEvaluator();

    private BpmFlowNode node(String config) {
        BpmFlowNode n = new BpmFlowNode();
        n.setNodeType("end");
        n.setNodeKey("end1");
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
    @DisplayName("空 config 不抛错,outputs 为空")
    void emptyConfig() {
        EndNodeExecutor ex = new EndNodeExecutor(spel);
        NodeExecutionResult r = ex.execute(ctx(node(null), Map.of("a", 1), null));
        assertNotNull(r.getOutputs());
        assertTrue(r.getOutputs().isEmpty());
    }

    @Test
    @DisplayName("outputTemplate 求值写入 outputs")
    void templateRender() {
        EndNodeExecutor ex = new EndNodeExecutor(spel);
        String cfg = "{\"outputTemplate\":{\"summary\":\"#query + ' done'\",\"userId\":\"#userId\"}}";
        Map<String, Object> vars = Map.of("query", "退款", "userId", "u1");
        NodeExecutionResult r = ex.execute(ctx(node(cfg), vars, null));
        assertEquals("退款 done", r.getOutputs().get("summary"));
        assertEquals("u1", r.getOutputs().get("userId"));
    }

    @Test
    @DisplayName("mergeToVariables=true 默认把 outputs 同步到 variables")
    void mergeIntoVariables() {
        EndNodeExecutor ex = new EndNodeExecutor(spel);
        String cfg = "{\"outputTemplate\":{\"k\":\"'v'\"}}";
        Map<String, Object> vars = new HashMap<>();
        ExecutionContext c = ctx(node(cfg), vars, null);
        ex.execute(c);
        assertEquals("v", c.getVariables().get("k"));
    }

    @Test
    @DisplayName("mergeToVariables=false 不写回 variables")
    void mergeDisabled() {
        EndNodeExecutor ex = new EndNodeExecutor(spel);
        String cfg = "{\"outputTemplate\":{\"k\":\"'v'\"},\"mergeToVariables\":false}";
        Map<String, Object> vars = new HashMap<>();
        ExecutionContext c = ctx(node(cfg), vars, null);
        NodeExecutionResult r = ex.execute(c);
        assertEquals("v", r.getOutputs().get("k"));
        assertNull(c.getVariables().get("k"));
    }

    @Test
    @DisplayName("validateConfig:outputTemplate 不是 Map 抛 40021")
    void validateConfigBadType() {
        EndNodeExecutor ex = new EndNodeExecutor(spel);
        com.fastrag.common.exception.BusinessException be = assertThrows(
                com.fastrag.common.exception.BusinessException.class,
                () -> ex.validateConfig(Map.of("outputTemplate", "not-a-map")));
        assertEquals(40021, be.getCode());
    }
}