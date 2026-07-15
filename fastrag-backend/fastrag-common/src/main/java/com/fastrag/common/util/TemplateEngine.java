package com.fastrag.common.util;

import cn.hutool.core.util.StrUtil;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateEngine {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}|\\{\\{([^}]+)\\}\\}");

    /**
     * 渲染模板字符串，将 ${key} 替换为实际值。
     * 支持 inputs.xxx, config.xxx, context.xxx 三种作用域。
     */
    public static String render(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) return template;
        Map<String, String> flat = flatten(variables);
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1) != null ? m.group(1) : m.group(2);
            String val = flat.getOrDefault(key, m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 批量渲染 Map 中的值。
     */
    public static Map<String, String> renderMap(Map<String, String> raw, Map<String, Object> variables) {
        if (raw == null) return Collections.emptyMap();
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(k, render(v, variables)));
        return result;
    }

    /** 将嵌套 Map 展平为 "a.b.c" -> value 格式 */
    private static Map<String, String> flatten(Map<String, Object> map) {
        Map<String, String> result = new LinkedHashMap<>();
        flattenRecursive("", map, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void flattenRecursive(String prefix, Map<String, Object> map, Map<String, String> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenRecursive(key, (Map<String, Object>) value, result);
            } else if (value != null) {
                result.put(key, value.toString());
            }
        }
    }
}
