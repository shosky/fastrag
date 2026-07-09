package com.fastrag.module.publish.aspect;

import com.fastrag.common.annotation.Loggable;
import com.fastrag.common.enums.ActionType;
import com.fastrag.common.enums.LogCategory;
import com.fastrag.module.publish.service.LogService;
import com.fastrag.security.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * 知识库操作日志 AOP 切面
 * <p>拦截标记了 {@link Loggable} 注解的方法，自动记录操作日志。
 *
 * <p>日志写入失败不影响主业务流程（异常被吞掉）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class KbLogAspect {

    private final LogService logService;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    @Around("@annotation(loggable)")
    public Object around(ProceedingJoinPoint pjp, Loggable loggable) throws Throwable {
        String operator = resolveOperator();
        String kbId = resolveKbId(pjp);
        String target = resolveSpelOrFallback(pjp, loggable.target(), this::resolveTargetFallback);
        String detail = resolveSpelOrFallback(pjp, loggable.detail(), ctx -> loggable.detail());

        try {
            Object result = pjp.proceed();
            logService.addLog(kbId, loggable.category(), loggable.action(), target,
                    detail, operator, "success", null);
            return result;
        } catch (Exception e) {
            String errorDetail = detail;
            if (errorDetail == null || errorDetail.isEmpty()) {
                errorDetail = e.getMessage() != null ? e.getMessage() : "unknown error";
            }
            log.warn("[LogAspect] Action {} failed on kbId={}: {}", loggable.action(), kbId, e.getMessage());
            logService.addLog(kbId, loggable.category(), loggable.action(), target,
                    errorDetail, operator, "failed", null);
            throw e;
        }
    }

    /**
     * 如果表达式以 '#' 开头，尝试用 SpEL 求值；否则当作普通字符串直接使用。
     * 求值失败时回退到 fallback 策略。
     */
    private String resolveSpelOrFallback(ProceedingJoinPoint pjp, String expr,
                                         java.util.function.Function<ProceedingJoinPoint, String> fallback) {
        if (expr == null || expr.isEmpty()) {
            return fallback.apply(pjp);
        }
        // 非 SpEL 表达式直接返回
        if (!expr.startsWith("#")) {
            return expr;
        }
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Method method = signature.getMethod();
            Parameter[] parameters = method.getParameters();
            Object[] args = pjp.getArgs();

            EvaluationContext context = new StandardEvaluationContext();
            // 按 #p0, #p1 ... 注册位置参数
            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
            }
            // 按参数名注册（需要编译时 -parameters 选项）
            for (int i = 0; i < parameters.length; i++) {
                context.setVariable(parameters[i].getName(), args[i]);
            }

            Expression spelExpr = spelParser.parseExpression(expr);
            Object value = spelExpr.getValue(context);
            if (value != null) {
                return value.toString();
            }
            return fallback.apply(pjp);
        } catch (Exception e) {
            log.warn("[LogAspect] SpEL evaluation failed for '{}': {}", expr, e.getMessage());
            return fallback.apply(pjp);
        }
    }

    /**
     * 从方法参数中提取 kbId
     * <p>优先查找参数名为 "kbId" 的参数值。
     */
    private String resolveKbId(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            if ("kbId".equals(parameters[i].getName())) {
                if (args[i] instanceof String s) return s;
            }
        }
        // fallback: 第一个 String 参数
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String s && !s.isEmpty()) return s;
        }
        return "unknown";
    }

    /**
     * 解析 target：
     * <p>1. 如果注解指定了非空 target，直接使用；
     * <p>2. 否则自动查找参数名为 fileId/folderId/benchmarkId/evaluationId/strategyId 的值。
     */
    private String resolveTargetFallback(ProceedingJoinPoint pjp) {
        // 自动检测常见 id 参数
        String[] idParamNames = {"fileId", "folderId", "benchmarkId", "evaluationId",
                "strategyId", "knowledgeId", "planId", "id"};
        Object[] args = pjp.getArgs();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            for (String name : idParamNames) {
                if (name.equals(parameters[i].getName()) && args[i] instanceof String s && !s.isEmpty()) {
                    return s;
                }
            }
        }
        return "";
    }

    /**
     * 获取当前操作者用户名，失败时返回 "system"
     */
    private String resolveOperator() {
        try {
            return SecurityUtil.getCurrentUser() != null
                    ? SecurityUtil.getCurrentUser().getUsername() : "system";
        } catch (Exception e) {
            return "system";
        }
    }
}
