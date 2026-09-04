package com.fastrag.module.bpm.executor.impl;

import cn.hutool.core.util.StrUtil;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.NodeExecutor;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 用户输入节点:挂起实例,等待用户提交表单 */
@Component @RequiredArgsConstructor
public class UserInputNodeExecutor implements NodeExecutor {
    private final SpelEvaluator spel;

    @Override public String type() { return "user_input"; }
    @Override public String category() { return "input"; }
    @Override public void validateConfig(Map<String, Object> config) {
        if (config == null) throw BpmErrorCode.NODE_CONFIG_INVALID.of("user_input 节点 config 不能为空");
        Object fields = config.get("fields");
        if (!(fields instanceof java.util.List) || ((java.util.List<?>) fields).isEmpty())
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("user_input 节点 fields 不能为空");
    }
    @Override public NodeExecutionResult execute(ExecutionContext ctx) {
        Map<String, Object> cfg = spel.parseConfig(ctx.getCurrentNode().getConfig());
        NodeExecutionResult r = new NodeExecutionResult();
        // 表单 schema 直接以 JSON 字符串暴露给前端(instance.pending_input_form)
        r.setWaitingInput(true);
        r.setPendingInputForm(StrUtil.isBlank(ctx.getCurrentNode().getConfig()) ? "{}" : ctx.getCurrentNode().getConfig());
        r.getOutputs().put("fields", cfg.get("fields"));
        return r;
    }
}