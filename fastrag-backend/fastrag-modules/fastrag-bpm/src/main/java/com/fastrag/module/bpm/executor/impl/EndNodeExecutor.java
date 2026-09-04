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

/**
 * 结束节点:把 outputTemplate 中定义的字段求值后写入 outputs。
 *
 * config:
 *  - outputTemplate: 可选 Map<String,String>, value 为 SpEL 表达式, 在 (variables + nodeInputs) 上下文中求值;
 *    求值结果写入 outputs。
 *  - mergeToVariables: 可选 boolean, 默认 true。true 时把 outputs 同步到 ctx.variables,
 *    供后续节点(虽然在 end 之后通常没有了)或上层调用方拿到最终变量集。
 *
 * 实例完成/输出参数收集由 BpmInstanceService 在收到 isEnd 标记时统一处理,
 * 这里只负责提供 outputs。
 */
@Component
@RequiredArgsConstructor
public class EndNodeExecutor implements NodeExecutor {

    private final SpelEvaluator spel;

    @Override public String type() { return "end"; }
    @Override public String category() { return "control"; }

    @Override
    public void validateConfig(Map<String, Object> config) {
        if (config == null) return;
        Object tpl = config.get("outputTemplate");
        if (tpl != null && !(tpl instanceof Map)) {
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("end 节点 outputTemplate 必须是 Map<String,String>");
        }
    }

    @Override
    public NodeExecutionResult execute(ExecutionContext ctx) {
        Map<String, Object> cfg = spel.parseConfig(ctx.getCurrentNode().getConfig());
        NodeExecutionResult r = new NodeExecutionResult();

        Map<String, Object> vars = new HashMap<>(ctx.getVariables() == null ? Map.of() : ctx.getVariables());
        if (ctx.getNodeInputs() != null) vars.putAll(ctx.getNodeInputs());

        Object tpl = cfg.get("outputTemplate");
        if (tpl instanceof Map<?, ?> tplMap) {
            for (Map.Entry<?, ?> e : tplMap.entrySet()) {
                if (!(e.getKey() instanceof String key)) continue;
                if (!(e.getValue() instanceof String expr)) continue;
                if (StrUtil.isBlank(expr)) continue;
                try {
                    Object val = spel.eval(expr, vars);
                    r.getOutputs().put(key, val);
                } catch (Exception ex) {
                    // 单个字段失败不阻塞 end,只记录空值
                    r.getOutputs().put(key, null);
                }
            }
        }

        Boolean merge = (Boolean) cfg.get("mergeToVariables");
        if ((merge == null || merge) && !r.getOutputs().isEmpty()
                && ctx.getVariables() != null) {
            ctx.getVariables().putAll(r.getOutputs());
        }
        return r;
    }
}