package com.fastrag.module.bpm.executor.impl;

import cn.hutool.core.util.StrUtil;
import com.fastrag.ai.llm.LlmService;
import com.fastrag.ai.model.ChatMessage;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import com.fastrag.module.bpm.executor.ExecutionContext;
import com.fastrag.module.bpm.executor.NodeExecutionResult;
import com.fastrag.module.bpm.executor.NodeExecutor;
import com.fastrag.module.bpm.executor.SpelEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 节点:渲染 systemPrompt/userPrompt 并调用模型,返回 answer。
 *
 * config:
 *  - model:           必填,模型标识(传给 LlmService.chat)
 *  - userPrompt:      必填,SpEL 表达式,在 (variables + nodeInputs) 上下文求值后作为 user 消息
 *  - systemPrompt:    可选,SpEL 表达式,求值后作为 system 消息
 *  - temperature:     可选,默认 0.7
 *  - apiUrl:          可选,覆盖默认 ai.gateway.url
 *  - apiKey:          可选,覆盖默认 key
 *  - maxTokens:       可选,当前 LlmService 不支持,作为输出参数写到 outputs
 *
 * outputs: { answer, model, promptLength, maxTokens }
 *
 * 失败语义:LlmService 失败或返回 "模型调用失败: ..." 抛 EXECUTION_FAILED,
 * 由 BpmInstanceService 的 on_failure 流程决定是否终止或走分支。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNodeExecutor implements NodeExecutor {

    private final SpelEvaluator spel;
    private final LlmService llm;

    @Override public String type() { return "llm"; }
    @Override public String category() { return "execute"; }

    @Override
    public void validateConfig(Map<String, Object> config) {
        if (config == null) throw BpmErrorCode.NODE_CONFIG_INVALID.of("llm 节点 config 不能为空");
        if (StrUtil.isBlank((String) config.get("userPrompt")))
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("llm 节点 userPrompt 必填");
        if (StrUtil.isBlank((String) config.get("model")))
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("llm 节点 model 必填");
    }

    @Override
    public NodeExecutionResult execute(ExecutionContext ctx) {
        Map<String, Object> cfg = spel.parseConfig(ctx.getCurrentNode().getConfig());
        String model = (String) cfg.get("model");
        String userPromptExpr = (String) cfg.get("userPrompt");
        String sysPromptExpr = (String) cfg.get("systemPrompt");
        Double temperature = cfg.get("temperature") instanceof Number n ? n.doubleValue() : 0.7;
        String apiUrl = (String) cfg.get("apiUrl");
        String apiKey = (String) cfg.get("apiKey");

        Map<String, Object> vars = new HashMap<>(ctx.getVariables() == null ? Map.of() : ctx.getVariables());
        if (ctx.getNodeInputs() != null) vars.putAll(ctx.getNodeInputs());

        String userPrompt = String.valueOf(spel.eval(userPromptExpr, vars));
        String sysPrompt = (sysPromptExpr == null) ? null : String.valueOf(spel.eval(sysPromptExpr, vars));

        List<ChatMessage> messages = new ArrayList<>(2);
        if (StrUtil.isNotBlank(sysPrompt)) messages.add(new ChatMessage("system", sysPrompt));
        messages.add(new ChatMessage("user", userPrompt));

        log.debug("LlmNode: model={}, sysPromptLen={}, userPromptLen={}",
                model,
                sysPrompt == null ? 0 : sysPrompt.length(),
                userPrompt.length());

        String answer;
        try {
            answer = llm.chat(model, messages, temperature, apiUrl, apiKey);
        } catch (Exception ex) {
            throw BpmErrorCode.EXECUTION_FAILED.of("llm 调用失败: " + ex.getMessage());
        }
        if (answer == null || answer.isBlank()) {
            throw BpmErrorCode.EXECUTION_FAILED.of("llm 返回空响应");
        }
        if (answer.startsWith("模型调用失败")) {
            // LlmService 内部把异常吞掉并返回 "模型调用失败: ..." 字串,这里当作失败
            throw BpmErrorCode.EXECUTION_FAILED.of(answer);
        }

        NodeExecutionResult r = new NodeExecutionResult();
        r.getOutputs().put("answer", answer);
        r.getOutputs().put("model", model);
        r.getOutputs().put("promptLength", userPrompt.length());
        Object maxTokens = cfg.get("maxTokens");
        if (maxTokens != null) r.getOutputs().put("maxTokens", maxTokens);
        return r;
    }
}