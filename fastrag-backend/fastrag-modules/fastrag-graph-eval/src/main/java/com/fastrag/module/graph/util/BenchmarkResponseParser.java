package com.fastrag.module.graph.util;

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
