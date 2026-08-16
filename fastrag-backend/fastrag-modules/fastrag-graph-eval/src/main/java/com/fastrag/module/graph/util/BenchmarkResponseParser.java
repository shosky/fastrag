package com.fastrag.module.graph.util;

/**
 * 基准测试问答对 LLM 响应解析工具类。
 *
 * <p>负责将大语言模型（LLM）返回的原始文本解析为结构化的问答对列表（List&lt;Map&lt;String, Object&gt;&gt;），
 * 是基准测试自动生成流程（{@link com.fastrag.module.graph.service.impl.BenchmarkGenerationHelper}）中的关键环节。
 * 由于 LLM 输出格式不可控，本工具采用多级防御策略确保解析的鲁棒性：</p>
 * <ul>
 *   <li>空响应或错误提示文本（如 "模型调用失败: ..."）→ 直接返回空列表，避免下游 NPE</li>
 *   <li>Markdown 代码块包裹（{@code ```json ... ```}）→ 自动剥离围栏标记</li>
 *   <li>前后夹杂说明性文本（如 "以下是生成的问答对：\n[...]"）→ 截取首个 {@code [} 到末个 {@code ]} 区间</li>
 *   <li>JSON 数组存在但内容截断或损坏 → 抛出 JSONException，由调用方记录响应预览并降级处理</li>
 * </ul>
 *
 * <p>核心方法 {@link #parseQuestions(String)} 返回的 Map 结构通常包含 question、goldChunk、goldAnswer 等字段，
 * 与 {@link com.fastrag.module.graph.entity.KbBenchmarkQuestion} 实体对应。本类为无状态工具类，
 * 仅包含静态方法，由 BenchmarkGenerationHelper 在异步基准生成过程中调用。</p>
 */
import cn.hutool.json.JSONUtil;

import java.util.List;
import java.util.Map;

/**
 * 基准问答对 LLM 响应解析器。
 *
 * <p>LLM 输出不可靠，需防御以下非标准形态：
 * <ul>
 *   <li>空响应 / 错误提示串（如 "模型调用失败: ..."）→ 返回空列表</li>
 *   <li>markdown 代码块包裹（```json ... ```）→ 剥离围栏</li>
 *   <li>前后夹杂说明性文本（如 "以下是生成的问答对：\n[...]"）→ 截取首个 [ 到末个 ] 区间</li>
 *   <li>截断/损坏的 JSON → 抛出异常，由调用方记录响应预览并降级</li>
 * </ul>
 */
public final class BenchmarkResponseParser {

    private BenchmarkResponseParser() {
    }

    /**
     * 解析 LLM 返回的问答对 JSON 数组。
     *
     * @param response LLM 原始响应
     * @return 问答对列表；响应为空或不含 JSON 数组时返回空列表
     * @throws cn.hutool.json.JSONException JSON 数组存在但无法解析（截断/损坏）
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseQuestions(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        String json = extractJsonArray(response);
        if (json == null) {
            return List.of();
        }
        return (List<Map<String, Object>>) (List<?>) JSONUtil.toList(json, Map.class);
    }

    /**
     * 提取 JSON 数组区间：剥离首尾 markdown 围栏；若整体不以 [ 开头，
     * 截取首个 [ 到末个 ] 之间的内容。
     */
    private static String extractJsonArray(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("```\\s*$", "").trim();
        }
        if (trimmed.startsWith("[")) {
            return trimmed;
        }
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return null;
    }
}
