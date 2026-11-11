package com.fastrag.module.application.widget;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 三档保存校验（REQ-WG-A02）：①三件套各自合法 JSON；②模板组件名在白名单内且必填属性齐全；
 * ③数据结构声明的变量与模板引用一致、default 可按数据结构反序列化。
 * 返回逐项行级错误（空列表 = 通过），由服务层聚合成业务异常 message。
 */
@Component
public class WidgetTemplateValidator {

    /** 模板字符串 prop 中的变量插值占位 */
    public static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    /** 单条校验错误 */
    public record Error(String piece, String message) {}

    /** @return 错误列表；空 = 通过 */
    public List<Error> validate(String name, String template, String schemaJson, String defaultJson) {
        List<Error> errors = new ArrayList<>();
        if (name == null || name.isBlank()) {
            errors.add(new Error("name", "名称不能为空白"));
        }
        validatePieceSize("template", template, errors);
        validatePieceSize("schemaJson", schemaJson, errors);
        validatePieceSize("defaultJson", defaultJson, errors);

        // 第一档：JSON 合法性（default 可空）
        JSONObject templateRoot = null;
        JSONObject schemaRoot = null;
        JSONObject defaultRoot = null;
        if (template == null || template.isBlank()) {
            errors.add(new Error("template", "模板不能为空"));
        } else {
            try {
                templateRoot = JSONUtil.parseObj(template);
            } catch (Exception e) {
                errors.add(new Error("template", "模板不是合法 JSON：" + e.getMessage()));
            }
        }
        if (schemaJson == null || schemaJson.isBlank()) {
            errors.add(new Error("schemaJson", "数据结构不能为空"));
        } else {
            try {
                schemaRoot = JSONUtil.parseObj(schemaJson);
            } catch (Exception e) {
                errors.add(new Error("schemaJson", "数据结构不是合法 JSON：" + e.getMessage()));
            }
        }
        if (defaultJson != null && !defaultJson.isBlank()) {
            try {
                defaultRoot = JSONUtil.parseObj(defaultJson);
            } catch (Exception e) {
                errors.add(new Error("defaultJson", "默认值不是合法 JSON 对象：" + e.getMessage()));
            }
        }

        // 第二档：组件白名单 + 必填属性（模板已合法时才做）
        if (templateRoot != null) {
            validateComponentTree(templateRoot, "template", errors);
        }

        // 第三档：变量声明 ↔ 模板引用 ↔ 默认值 一致性（三方齐备时才做）
        if (schemaRoot != null && templateRoot != null) {
            Set<String> declared = declaredVariables(schemaRoot, errors);
            Set<String> referenced = new java.util.HashSet<>();
            collectVars(templateRoot, referenced);
            for (String var : referenced) {
                if (!declared.contains(var)) {
                    errors.add(new Error("schemaJson", "模板引用的变量未在数据结构中声明：" + var));
                }
            }
            if (defaultRoot != null) {
                for (String key : defaultRoot.keySet()) {
                    if (!declared.contains(key)) {
                        errors.add(new Error("defaultJson", "默认值字段未在数据结构中声明：" + key));
                    }
                }
                checkDefaultTypes(schemaRoot, defaultRoot, errors);
            }
        }
        return errors;
    }

    /** 运行时数据校验（工具路径）：data 按 schema 校验，返回错误行（空 = 通过） */
    public List<Error> validateData(String schemaJson, Map<String, Object> data) {
        List<Error> errors = new ArrayList<>();
        JSONObject schemaRoot;
        try {
            schemaRoot = JSONUtil.parseObj(schemaJson == null || schemaJson.isBlank() ? "{}" : schemaJson);
        } catch (Exception e) {
            return List.of(new Error("data", "资产数据结构非法：" + e.getMessage()));
        }
        Set<String> declared = declaredVariables(schemaRoot, errors);
        Map<String, Object> src = data == null ? Map.of() : data;
        for (String var : requiredVariables(schemaRoot)) {
            if (!src.containsKey(var) || src.get(var) == null) {
                errors.add(new Error("data", "缺少必填变量：" + var));
            }
        }
        for (Map.Entry<String, Object> en : src.entrySet()) {
            String key = en.getKey();
            if (!declared.contains(key)) {
                errors.add(new Error("data", "字段未在数据结构中声明：" + key));
                continue;
            }
            String type = typeOf(schemaRoot, key);
            if (type != null && !matchesType(en.getValue(), type)) {
                errors.add(new Error("data", "变量类型不符：" + key + " 应为 " + type));
            }
        }
        return errors;
    }

    // ===== 内部 =====

    private void validatePieceSize(String piece, String json, List<Error> errors) {
        if (json != null && json.getBytes(StandardCharsets.UTF_8).length > WidgetComponentWhitelist.MAX_PIECE_BYTES) {
            errors.add(new Error(piece, "超过单件体积上界 64KB"));
        }
    }

    private void validateComponentTree(JSONObject node, String path, List<Error> errors) {
        String type = node.getStr("type", "");
        if (!WidgetComponentWhitelist.COMPONENTS.contains(type)) {
            errors.add(new Error("template", path + "：未知组件类型 " + type));
            return;
        }
        JSONObject props = node.getJSONObject("props");
        Set<String> required = WidgetComponentWhitelist.REQUIRED_PROPS.getOrDefault(type, Set.of());
        for (String req : required) {
            if ("children".equals(req)) {
                JSONArray children = node.getJSONArray("children");
                if (children == null || children.isEmpty()) {
                    errors.add(new Error("template", path + "：" + type + " 缺少 children"));
                }
                continue;
            }
            String v = props == null ? null : props.getStr(req);
            if (v == null || v.isBlank()) {
                errors.add(new Error("template", path + "：" + type + " 缺少必填属性 " + req));
            }
        }
        JSONArray children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                Object child = children.get(i);
                if (child instanceof JSONObject childObj) {
                    validateComponentTree(childObj, path + ".children[" + i + "]", errors);
                } else if (child != null) {
                    errors.add(new Error("template", path + ".children[" + i + "]：子节点必须为组件对象"));
                }
            }
        }
    }

    /** 声明变量集：标准形 {"properties":{...}}；宽容平铺形 {var:type} */
    private Set<String> declaredVariables(JSONObject schemaRoot, List<Error> errors) {
        JSONObject properties = schemaRoot.getJSONObject("properties");
        Set<String> declared = new java.util.LinkedHashSet<>();
        if (properties != null) {
            declared.addAll(properties.keySet());
        } else {
            for (String key : schemaRoot.keySet()) {
                if (!"type".equals(key) && !"required".equals(key)) {
                    declared.add(key);
                }
            }
        }
        if (declared.isEmpty()) {
            errors.add(new Error("schemaJson", "数据结构未声明任何变量"));
        }
        return declared;
    }

    private Set<String> requiredVariables(JSONObject schemaRoot) {
        JSONArray required = schemaRoot.getJSONArray("required");
        Set<String> out = new java.util.LinkedHashSet<>();
        if (required != null) {
            for (Object r : required) {
                out.add(String.valueOf(r));
            }
        }
        return out;
    }

    private String typeOf(JSONObject schemaRoot, String var) {
        JSONObject properties = schemaRoot.getJSONObject("properties");
        if (properties != null) {
            JSONObject prop = properties.getJSONObject(var);
            return prop == null ? null : prop.getStr("type");
        }
        Object t = schemaRoot.get(var);
        return t == null ? null : String.valueOf(t);
    }

    private void collectVars(JSONObject node, Set<String> out) {
        JSONObject props = node.getJSONObject("props");
        if (props != null) {
            for (String key : props.keySet()) {
                Object v = props.get(key);
                if (v instanceof String s) {
                    Matcher m = VAR_PATTERN.matcher(s);
                    while (m.find()) {
                        out.add(m.group(1));
                    }
                }
            }
        }
        JSONArray children = node.getJSONArray("children");
        if (children != null) {
            for (Object child : children) {
                if (child instanceof JSONObject childObj) {
                    collectVars(childObj, out);
                }
            }
        }
    }

    private void checkDefaultTypes(JSONObject schemaRoot, JSONObject defaultRoot, List<Error> errors) {
        for (String key : defaultRoot.keySet()) {
            String type = typeOf(schemaRoot, key);
            Object value = defaultRoot.get(key);
            if (type != null && value != null && !matchesType(value, type)) {
                errors.add(new Error("defaultJson", "默认值类型不符：" + key + " 应为 " + type));
            }
        }
    }

    private boolean matchesType(Object value, String type) {
        if (value == null) {
            return true;
        }
        return switch (type) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array" -> value instanceof List;
            default -> true;
        };
    }
}
