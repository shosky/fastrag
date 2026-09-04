package com.fastrag.module.bpm.executor.impl;

import com.fastrag.common.exception.BusinessException;
import com.fastrag.module.bpm.entity.BpmFlowNode;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StartNodeExecutor / ConditionNodeExecutor 单元测试。
 *
 * 覆盖：
 *  - Start: 把 nodeInputs 透传到 outputs；nodeInputs 为 null 时不抛错
 *  - Condition: validateConfig 缺失 expression 抛错；execute 把 conditionResult 写到 outputs
 *  - 不依赖 Spring 容器，纯 new
 */
class NodeExecutorTest {

    private BpmFlowNode newNode(String type, String config) {
        BpmFlowNode n = new BpmFlowNode();
        n.setNodeType(type);
        n.setNodeKey("n1");
        n.setConfig(config);
        return n;
    }

    private ExecutionContext newCtx(BpmFlowNode node, Map<String, Object> vars, Map<String, Object> nodeInputs) {
        ExecutionContext ctx = new ExecutionContext();
        ctx.setFlowDefId("f1");
        ctx.setCurrentNode(node);
        ctx.setVariables(vars == null ? new HashMap<>() : new HashMap<>(vars));
        ctx.setNodeInputs(nodeInputs == null ? new HashMap<>() : new HashMap<>(nodeInputs));
        return ctx;
    }

    @Test
    @DisplayName("Start: 把 nodeInputs 透传到 outputs")
    void startEchoesInputs() {
        StartNodeExecutor exec = new StartNodeExecutor();
        BpmFlowNode node = newNode("start", null);
        Map<String, Object> inputs = Map.of("query", "你好", "userId", "u1");
        ExecutionContext ctx = newCtx(node, null, inputs);

        NodeExecutionResult r = exec.execute(ctx);

        assertEquals("你好", r.getOutputs().get("query"));
        assertEquals("u1", r.getOutputs().get("userId"));
        assertEquals(2, r.getOutputs().size());
    }

    @Test
    @DisplayName("Start: nodeInputs 为 null 时 outputs 为空 Map，不 N 抛错")
    void startWithNullInputs() {
        StartNodeExecutor exec = new StartNodeExecutor();
        BpmFlowNode node = newNode("start", null);
        ExecutionContext ctx = newCtx(node, null, null);

        NodeExecutionResult r = exec.execute(ctx);

        assertNotNull(r.getOutputs());
        assertTrue(r.getOutputs().isEmpty());
    }

    @Test
    @DisplayName("Condition: validateConfig 缺失 expression 抛 NODE_CONFIG_INVALID")
    void conditionValidateMissingExpression() {
        ConditionNodeExecutor exec = new ConditionNodeExecutor(new com.fastrag.module.bpm.executor.SpelEvaluator());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> exec.validateConfig(Map.of()));
        assertEquals(40021, ex.getCode());
    }

    @Test
    @DisplayName("Condition: validateConfig 空 config 抛 NODE_CONFIG_INVALID")
    void conditionValidateBlankConfig() {
        ConditionNodeExecutor exec = new ConditionNodeExecutor(new com.fastrag.module.bpm.executor.SpelEvaluator());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> exec.validateConfig(null));
        assertEquals(40021, ex.getCode());
    }

    @Test
    @DisplayName("Condition: execute 求 SpEL 并写入 conditionResult=true")
    void conditionExecuteTrue() {
        ConditionNodeExecutor exec = new ConditionNodeExecutor(new com.fastrag.module.bpm.executor.SpelEvaluator());
        BpmFlowNode node = newNode("condition", "{\"expression\":\"#score > 60\"}");
        Map<String, Object> vars = Map.of("score", 80);
        ExecutionContext ctx = newCtx(node, vars, null);

        NodeExecutionResult r = exec.execute(ctx);

        assertEquals(true, r.getOutputs().get("conditionResult"));
    }

    @Test
    @DisplayName("Condition: execute 求 SpEL 并写入 conditionResult=false")
    void conditionExecuteFalse() {
        ConditionNodeExecutor exec = new ConditionNodeExecutor(new com.fastrag.module.bpm.executor.SpelEvaluator());
        BpmFlowNode node = newNode("condition", "{\"expression\":\"#score > 60\"}");
        Map<String, Object> vars = Map.of("score", 30);
        ExecutionContext ctx = newCtx(node, vars, null);

        NodeExecutionResult r = exec.execute(ctx);

        assertEquals(false, r.getOutputs().get("conditionResult"));
    }

    @Test
    @DisplayName("Condition: variables 与 nodeInputs 合并后求值")
    void conditionMergesVarsAndInputs() {
        ConditionNodeExecutor exec = new ConditionNodeExecutor(new com.fastrag.module.bpm.executor.SpelEvaluator());
        BpmFlowNode node = newNode("condition", "{\"expression\":\"#query.contains('退款')\"}");
        Map<String, Object> vars = Map.of("user", "u1");
        Map<String, Object> inputs = Map.of("query", "我要退款");
        ExecutionContext ctx = newCtx(node, vars, inputs);

        NodeExecutionResult r = exec.execute(ctx);

        assertEquals(true, r.getOutputs().get("conditionResult"));
    }
}