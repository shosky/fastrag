package com.fastrag.module.bpm.executor.impl;

import cn.hutool.core.util.StrUtil;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.NodeExecutor;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 条件节点:按 SpEL 求值结果,从出边中选取 true 边/默认边 */
@Component @RequiredArgsConstructor
public class ConditionNodeExecutor implements NodeExecutor {
    private final SpelEvaluator spel;

    @Override public String type() { return "condition"; }
    @Override public String category() { return "control"; }
    @Override public void validateConfig(Map<String, Object> config) {
        if (config == null || StrUtil.isBlank((String) config.get("expression")))
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("condition 节点 expression 不能为空");
    }
    @Override public NodeExecutionResult execute(ExecutionContext ctx) {
        Map<String, Object> cfg = spel.parseConfig(ctx.getCurrentNode().getConfig());
        String expr = (String) cfg.get("expression");
        // 出边求值:返回 true/false
        Map<String, Object> vars = new HashMap<>(ctx.getVariables() == null ? Map.of() : ctx.getVariables());
        if (ctx.getNodeInputs() != null) vars.putAll(ctx.getNodeInputs());
        // 条件节点本身的 expression 用于当前条件值;具体分支由调度器按 edge.conditionExpr 决定
        NodeExecutionResult r = new NodeExecutionResult();
        boolean cond = spel.evalBool(expr, vars);
        r.getOutputs().put("conditionResult", cond);
        // 不在这里设置 nextHints,让调度器按出边 conditionExpr 求值决定
        return r;
    }
}