package com.fastrag.module.bpm.executor.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图分类节点:基于 LLM 把用户输入分类到 labels 中的一项。
 *
 * config:
 *  - labels:             必填 List<String>, 候选项
 *  - model:              必填,模型标识
 *  - temperature:        可选,默认 0.3(分类任务低温度)
 *  - systemPrompt:       可选,自定义系统提示,否则用默认
 *  - confidenceThreshold: 可选,默认 0.5;score >= threshold 视为通过(passed=true)
 *  - userInputField:     可选,默认 "input";从 ctx.variables/userInput 取值的字段名
 *  - failOnError:        可选,默认 false。false 时 LLM 失败/JSON 解析失败 → fallback(labels[0], 0.0, passed=false)
 *                       true 时抛 EXECUTION_FAILED
 *
 * outputs: { intent, score, passed, raw }
 *
 * 容错策略:意图分类通常是辅助决策,失败不应阻塞流程;failOnError=true 才升级为异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentNodeExecutor implements NodeExecutor {

    private final SpelEvaluator spel;
    private final LlmService llm;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[^{}]*\"intent\"[^{}]*}", Pattern.DOTALL);

    @Override public String type() { return "intent"; }
    @Override public String category() { return "execute"; }

    @Override
    @SuppressWarnings("unchecked")
    public void validateConfig(Map<String, Object> config) {
        if (config == null) throw BpmErrorCode.NODE_CONFIG_INVALID.of("intent 节点 config 不能为空");
        Object labels = config.get("labels");
        if (!(labels instanceof List) || ((List<?>) labels).isEmpty())
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("intent 节点 labels 必填且非空");
        if (StrUtil.isBlank((String) config.get("model")))
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("intent 节点 model 必填");
    }

    @Override
    public NodeExecutionResult execute(ExecutionContext ctx) {
        Map<String, Object> cfg = spel.parseConfig(ctx.getCurrentNode().getConfig());
        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) cfg.get("labels");
        String model = (String) cfg.get("model");
        double temperature = cfg.get("temperature") instanceof Number n ? n.doubleValue() : 0.3;
        double threshold = cfg.get("confidenceThreshold") instanceof Number n ? n.doubleValue() : 0.5;
        Boolean failOnError = (Boolean) cfg.get("failOnError");
        String inputField = (String) cfg.getOrDefault("userInputField", "input");
        String customSys = (String) cfg.get("systemPrompt");

        // 取用户输入
        Map<String, Object> vars = new HashMap<>(ctx.getVariables() == null ? Map.of() : ctx.getVariables());
        if (ctx.getNodeInputs() != null) vars.putAll(ctx.getNodeInputs());
        Object inputObj = vars.containsKey(inputField) ? vars.get(inputField) : vars.get("query");
        String userInput = inputObj == null ? "" : String.valueOf(inputObj);

        String fallbackIntent = labels.get(0);

        // 构造 prompt
        String labelsJoined = String.join(", ", labels);
        String sysPrompt = StrUtil.isNotBlank(customSys) ? customSys :
                "你是一个意图分类助手。只能从给定候选标签中选择一个,严禁创造新标签。";
        String userPrompt = "候选标签: [" + labelsJoined + "]\n" +
                "用户输入: " + userInput + "\n\n" +
                "只返回 JSON,格式: {\"intent\": \"选中的标签\", \"score\": 0.0-1.0}\n" +
                "不要任何解释。";

        List<ChatMessage> messages = new ArrayList<>(2);
        messages.add(new ChatMessage("system", sysPrompt));
        messages.add(new ChatMessage("user", userPrompt));

        String raw;
        try {
            raw = llm.chat(model, messages, temperature, null, null);
        } catch (Exception ex) {
            return handleFail(failOnError, fallbackIntent, "llm 调用异常: " + ex.getMessage(), threshold);
        }
        if (raw == null || raw.isBlank() || raw.startsWith("模型调用失败")) {
            return handleFail(failOnError, fallbackIntent, raw == null ? "llm 返回空" : raw, threshold);
        }

        // 解析 JSON:先尝试整段解析,失败则提取 {...} 子串
        Parsed parsed = parseAnswer(raw, labels);
        if (parsed == null) {
            return handleFail(failOnError, fallbackIntent, raw, threshold);
        }
        NodeExecutionResult r = new NodeExecutionResult();
        r.getOutputs().put("intent", parsed.intent);
        r.getOutputs().put("score", parsed.score);
        r.getOutputs().put("passed", parsed.score >= threshold);
        r.getOutputs().put("raw", raw);
        return r;
    }

    private NodeExecutionResult handleFail(Boolean failOnError, String fallbackIntent, String raw, double threshold) {
        if (Boolean.TRUE.equals(failOnError)) {
            throw BpmErrorCode.EXECUTION_FAILED.of("intent LLM 解析失败: " + StrUtil.maxLength(raw, 100));
        }
        NodeExecutionResult r = new NodeExecutionResult();
        r.getOutputs().put("intent", fallbackIntent);
        r.getOutputs().put("score", 0.0);
        r.getOutputs().put("passed", 0.0 >= threshold);
        r.getOutputs().put("raw", raw == null ? "" : raw);
        return r;
    }

    /** 尝试两种方式解析 LLM 返回的 {intent, score};成功返回 Parsed,失败 null */
    private Parsed parseAnswer(String raw, List<String> labels) {
        // 1. 直接整段解析
        try {
            JsonNode node = mapper.readTree(raw);
            String intent = node.path("intent").asText(null);
            double score = node.path("score").asDouble(0.0);
            if (StrUtil.isNotBlank(intent)) return new Parsed(intent, score);
        } catch (Exception ignore) {}
        // 2. 提取第一个含 "intent" 的 {...} 块
        Matcher m = JSON_BLOCK.matcher(raw);
        if (m.find()) {
            try {
                JsonNode node = mapper.readTree(m.group());
                String intent = node.path("intent").asText(null);
                double score = node.path("score").asDouble(0.0);
                if (StrUtil.isNotBlank(intent)) return new Parsed(intent, score);
            } catch (Exception ignore) {}
        }
        // 3. 模型直接返回纯文本意图(无 JSON):命中 label 列表则算高置信,否则 fallback
        String trimmed = raw.trim();
        for (String l : labels) {
            if (l.equalsIgnoreCase(trimmed)) return new Parsed(l, 0.9);
            if (trimmed.contains(l)) return new Parsed(l, 0.6);
        }
        return null;
    }

    private record Parsed(String intent, double score) {}
}