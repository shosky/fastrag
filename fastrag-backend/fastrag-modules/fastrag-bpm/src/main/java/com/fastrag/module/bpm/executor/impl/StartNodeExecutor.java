package com.fastrag.module.bpm.executor.impl;

import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.NodeExecutor;
import org.springframework.stereotype.Component;

/** 开始节点:把 inputParams 写入 variables,推动流程进入下一节点 */
@Component
public class StartNodeExecutor implements NodeExecutor {
    @Override public String type() { return "start"; }
    @Override public String category() { return "control"; }
    @Override public NodeExecutionResult execute(ExecutionContext ctx) {
        NodeExecutionResult r = new NodeExecutionResult();
        if (ctx.getNodeInputs() != null) r.getOutputs().putAll(ctx.getNodeInputs());
        return r;
    }
}