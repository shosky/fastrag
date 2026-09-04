package com.fastrag.module.bpm.executor;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fastrag.module.bpm.enums.BpmErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/** SpEL 表达式求值器。T() 类型访问被禁用以确保安全 */
@Component @RequiredArgsConstructor
public class SpelEvaluator {
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 解析 + 求值;args[0] 必须是 Map<String,Object>,用于 context variables */
    public Object eval(String expr, Map<String, Object> variables) {
        if (StrUtil.isBlank(expr)) return Boolean.TRUE;
        try {
            Expression e = parser.parseExpression(expr);
            // 拒绝包含 T() 类型访问
            String flat = expr.replaceAll("\\s+", "");
            if (flat.contains("T(") || flat.contains("new ")) throw BpmErrorCode.EXPRESSION_EVAL_ERROR.of("禁止类型访问或 new 表达式");
            EvaluationContext ctx = new StandardEvaluationContext();
            if (variables != null) variables.forEach(ctx::setVariable);
            return e.getValue(ctx);
        } catch (ParseException pe) {
            throw BpmErrorCode.EXPRESSION_PARSE_ERROR.of(pe.getMessage());
        } catch (Exception ex) {
            throw BpmErrorCode.EXPRESSION_EVAL_ERROR.of(ex.getMessage());
        }
    }

    /** 布尔求值(条件边专用) */
    public boolean evalBool(String expr, Map<String, Object> variables) {
        Object v = eval(expr, variables);
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof String s) return !s.isEmpty() && !"false".equalsIgnoreCase(s);
        return Boolean.TRUE.equals(v);
    }

    /** 解析节点 config JSON 为 Map;空值返回空 Map */
    public Map<String, Object> parseConfig(String configJson) {
        if (StrUtil.isBlank(configJson)) return Map.of();
        try {
            return mapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw BpmErrorCode.NODE_CONFIG_INVALID.of("节点 config JSON 解析失败: " + e.getMessage());
        }
    }
}